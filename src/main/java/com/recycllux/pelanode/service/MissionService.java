package com.recycllux.pelanode.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycllux.pelanode.domain.ContextRecord;
import com.recycllux.pelanode.domain.DatasetSource;
import com.recycllux.pelanode.domain.DeviceStatus;
import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.pack.DemoPack;
import com.recycllux.pelanode.repo.ContextRepository;
import com.recycllux.pelanode.repo.MissionRepository;
import com.recycllux.pelanode.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MissionService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final Duration CONTEXT_MARGIN = Duration.ofHours(24);

    private final MissionRepository missions;
    private final ContextRepository context;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DemoPack pack;

    public List<Mission> list() {
        return missions.findAllMissions();
    }

    public Map<String, Object> get(String id) {
        Mission mission = find(id);
        Map<String, Object> view = new LinkedHashMap<>(mapper.convertValue(mission, MAP));
        view.put("node", missions.findNode(mission.nodeId()).orElse(null));
        view.put("counts", counts(id));
        return view;
    }

    public Map<String, Object> context(String id, String variable, String from, String to) {
        Mission mission = find(id);
        Instant start = from != null ? Times.parse(from) : mission.plannedStart().minus(CONTEXT_MARGIN);
        Instant end = to != null ? Times.parse(to) : mission.plannedEnd().plus(CONTEXT_MARGIN);
        List<ContextRecord> records = context.findRecords(start, end, variable);
        Set<String> used = records.stream().map(ContextRecord::datasetId).collect(Collectors.toSet());
        List<Map<String, Object>> datasets = context.findAllDatasets().stream()
                .filter(d -> used.contains(d.datasetId()))
                .map(d -> datasetView(d, records)).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasets", datasets);
        body.put("records", records);
        return body;
    }

    public List<DatasetSource> datasets() {
        return context.findAllDatasets();
    }

    public DeviceStatus nodeStatus(String nodeId, String missionId) {
        return missions.latestStatus(nodeId, missionId, null)
                .or(() -> pack.deviceStatus().stream()
                        .filter(s -> s.nodeId().equals(nodeId) && s.missionId().equals(missionId))
                        .min(Comparator.comparing(DeviceStatus::observedAt)))
                .orElseThrow(() -> ApiException.notFound("Device status for node", nodeId));
    }

    private Mission find(String id) {
        return missions.findMission(id).orElseThrow(() -> ApiException.notFound("Mission", id));
    }

    private Map<String, Object> counts(String missionId) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("track_points", count("SELECT COUNT(*) FROM track_point WHERE mission_id = ?", missionId));
        c.put("observations", count("SELECT COUNT(*) FROM observation WHERE mission_id = ?", missionId));
        c.put("events", count("SELECT COUNT(*) FROM event WHERE mission_id = ?", missionId));
        c.put("review_required", count("""
                SELECT COUNT(*) FROM event WHERE mission_id = ? AND attention = 'review_required'
                  AND review_status = 'unreviewed'""", missionId));
        c.put("reviewed", count(
                "SELECT COUNT(*) FROM event WHERE mission_id = ? AND review_status <> 'unreviewed'", missionId));
        return c;
    }

    private long count(String sql, String missionId) {
        Long n = jdbc.queryForObject(sql, Long.class, missionId);
        return n == null ? 0 : n;
    }

    private Map<String, Object> datasetView(DatasetSource d, List<ContextRecord> records) {
        Map<String, Object> view = new LinkedHashMap<>(mapper.convertValue(d, MAP));
        ContextRecord first = records.stream().filter(r -> r.datasetId().equals(d.datasetId())).findFirst()
                .orElseThrow();
        view.put("variable", first.variable());
        view.put("unit", first.unit());
        view.put("station_name", first.stationName());
        view.put("lat", first.lat());
        view.put("lon", first.lon());
        view.put("label", label(d.provenance()));
        return view;
    }

    private static String label(String provenance) {
        return switch (provenance) {
            case "organiser" -> "ORGANISER DATA";
            case "public" -> "PUBLIC DATA";
            default -> "SIMULATED CONTEXT";
        };
    }
}

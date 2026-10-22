package com.recycllux.pelanode.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycllux.pelanode.config.JsonConfig;
import com.recycllux.pelanode.domain.ContextRecord;
import com.recycllux.pelanode.domain.DatasetSource;
import com.recycllux.pelanode.domain.DeviceStatus;
import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.pack.DemoPack;
import com.recycllux.pelanode.repo.ContextRepository;
import com.recycllux.pelanode.repo.MissionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Step B-05: read endpoints that do not depend on the replay clock. */
@RestController
@RequestMapping("/api/v1")
public class MissionController {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    /** Default context window around the mission: wide enough for the 24 h temperature rule. */
    private static final Duration CONTEXT_MARGIN = Duration.ofHours(24);

    private final MissionRepository missions;
    private final ContextRepository context;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DemoPack pack;

    public MissionController(MissionRepository missions, ContextRepository context, JdbcTemplate jdbc,
                             ObjectMapper mapper, DemoPack pack) {
        this.missions = missions;
        this.context = context;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.pack = pack;
    }

    @GetMapping("/missions")
    public List<Mission> list() {
        return missions.findAllMissions();
    }

    /** One mission with its node and counts (P0-13 section 8). */
    @GetMapping("/missions/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        Mission mission = missions.findMission(id).orElseThrow(() -> ApiException.notFound("Mission", id));
        Map<String, Object> view = new LinkedHashMap<>(mapper.convertValue(mission, MAP));
        view.put("node", missions.findNode(mission.nodeId()).orElse(null));
        view.put("counts", counts(id));
        return view;
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

    /** Context records for the mission window, grouped by the datasets they come from. */
    @GetMapping("/missions/{id}/context")
    public Map<String, Object> context(@PathVariable String id,
                                       @RequestParam(required = false) String variable,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to) {
        Mission mission = missions.findMission(id).orElseThrow(() -> ApiException.notFound("Mission", id));
        Instant start = from != null ? parse(from) : mission.plannedStart().minus(CONTEXT_MARGIN);
        Instant end = to != null ? parse(to) : mission.plannedEnd().plus(CONTEXT_MARGIN);
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

    /**
     * The registry entry plus what the UI's map and chart need for one station: variable, unit,
     * station name and position (from the dataset's first record) and a display label.
     */
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

    static String label(String provenance) {
        return switch (provenance) {
            case "organiser" -> "ORGANISER DATA";
            case "public" -> "PUBLIC DATA";
            default -> "SIMULATED CONTEXT";
        };
    }

    @GetMapping("/datasets")
    public List<DatasetSource> datasets() {
        return context.findAllDatasets();
    }

    /**
     * Latest device status for the node in a mission. Before the replay has sent any status
     * (state idle), the first status of the demo pack stands in, so the UI has values to show.
     */
    @GetMapping("/nodes/{id}/status")
    public DeviceStatus status(@PathVariable String id, @RequestParam("mission_id") String missionId) {
        return missions.latestStatus(id, missionId, null)
                .or(() -> pack.deviceStatus().stream()
                        .filter(s -> s.nodeId().equals(id) && s.missionId().equals(missionId))
                        .min(Comparator.comparing(DeviceStatus::observedAt)))
                .orElseThrow(() -> ApiException.notFound("Device status for node", id));
    }

    static Instant parse(String text) {
        try {
            return JsonConfig.parse(text);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid(e.getMessage());
        }
    }
}

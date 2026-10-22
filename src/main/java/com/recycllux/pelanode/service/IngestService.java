package com.recycllux.pelanode.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recycllux.pelanode.domain.DeviceStatus;
import com.recycllux.pelanode.domain.ModelOutput;
import com.recycllux.pelanode.domain.Observation;
import com.recycllux.pelanode.domain.TrackPoint;
import com.recycllux.pelanode.repo.MissionRepository;
import com.recycllux.pelanode.repo.ObservationRepository;
import com.recycllux.pelanode.repo.TrackRepository;
import com.recycllux.pelanode.web.ApiException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class IngestService {

    private static final List<String> IGNORED = List.of("schema_version", "imported_at");
    private static final int MAX_BATCH = 1000;

    private final MissionRepository missions;
    private final com.recycllux.pelanode.config.PelanodeProperties props;
    private final TrackRepository track;
    private final ObservationRepository observations;
    private final EventBuilder eventBuilder;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final TransactionTemplate tx;

    public Map<String, Object> trackPoints(JsonNode body) {
        int accepted = 0;
        int duplicates = 0;
        List<Map<String, Object>> rejected = new ArrayList<>();
        for (JsonNode item : items(body)) {
            String id = item.path("track_point_id").asText(null);
            try {
                TrackPoint p = convert(item, TrackPoint.class);
                if (tx.execute(s -> trackPoint(p)) == IngestResult.CREATED) {
                    accepted++;
                } else {
                    duplicates++;
                }
            } catch (ApiException e) {
                rejected.add(Map.of("track_point_id", String.valueOf(id), "reason", e.getMessage()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accepted", accepted);
        out.put("duplicates", duplicates);
        out.put("rejected", rejected);
        return out;
    }

    public Map<String, Object> modelOutputs(JsonNode body) {
        int accepted = 0;
        int duplicates = 0;
        List<String> created = new ArrayList<>();
        for (JsonNode item : items(body)) {
            ModelOutput m = convert(item, ModelOutput.class);
            ModelOutputResult r = tx.execute(s -> modelOutput(m));
            if (r.result() == IngestResult.CREATED) {
                accepted++;
            } else {
                duplicates++;
            }
            r.eventCreated().ifPresent(created::add);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accepted", accepted);
        out.put("duplicates", duplicates);
        out.put("events_created", created);
        return out;
    }

    @Transactional
    public IngestResult deviceStatus(String nodeId, DeviceStatus s) {
        if (!nodeId.equals(s.nodeId())) {
            throw ApiException.invalid("node_id in body does not match the path");
        }
        return deviceStatus(s);
    }

    @Transactional
    public IngestResult trackPoint(TrackPoint p) {
        rejectLive(p.dataOrigin());
        requireMissionAndNode(p.missionId(), p.nodeId());
        return store(p, track.find(p.trackPointId()), track::insert, "Track point " + p.trackPointId());
    }

    @Transactional
    public IngestResult observation(Observation o) {
        rejectLive(o.dataOrigin());
        requireMissionAndNode(o.missionId(), o.nodeId());
        return store(o, observations.findObservation(o.observationId()), observations::insertObservation,
                "Observation " + o.observationId());
    }

    @Transactional
    public IngestResult deviceStatus(DeviceStatus s) {
        rejectLive(s.dataOrigin());
        requireMissionAndNode(s.missionId(), s.nodeId());
        return store(s, missions.findStatus(s.statusId()), missions::insertStatus, "Status " + s.statusId());
    }

    @Transactional
    public ModelOutputResult modelOutput(ModelOutput m) {
        Observation obs = observations.findObservation(m.observationId())
                .orElseThrow(() -> ApiException.notFound("Observation", m.observationId()));
        IngestResult result = store(m, observations.findModelOutput(m.modelOutputId()),
                observations::insertModelOutput, "Model output " + m.modelOutputId());
        if (result == IngestResult.DUPLICATE) {
            return new ModelOutputResult(result, Optional.empty());
        }
        return new ModelOutputResult(result, eventBuilder.onModelOutput(obs, m));
    }

    public record ModelOutputResult(IngestResult result, Optional<String> eventCreated) {
    }

    private void rejectLive(String dataOrigin) {
        if (props.simulatedMode() && "live".equals(dataOrigin)) {
            throw ApiException.invalid("data_origin 'live' is not allowed in simulated mode");
        }
    }

    private void requireMissionAndNode(String missionId, String nodeId) {
        if (missions.findMission(missionId).isEmpty()) {
            throw ApiException.notFound("Mission", missionId);
        }
        if (missions.findNode(nodeId).isEmpty()) {
            throw ApiException.notFound("Node", nodeId);
        }
    }

    private <T> IngestResult store(T incoming, Optional<T> existing, Consumer<T> insert, String label) {
        if (existing.isEmpty()) {
            insert.accept(incoming);
            return IngestResult.CREATED;
        }
        if (sameBody(existing.get(), incoming)) {
            return IngestResult.DUPLICATE;
        }
        throw ApiException.conflict(label + " already exists with a different body");
    }

    private boolean sameBody(Object stored, Object incoming) {
        JsonNode a = mapper.valueToTree(stored);
        JsonNode b = mapper.valueToTree(incoming);
        if (a instanceof ObjectNode oa && b instanceof ObjectNode ob) {
            oa.remove(IGNORED);
            ob.remove(IGNORED);
        }
        return a.equals(b);
    }

    private List<JsonNode> items(JsonNode body) {
        List<JsonNode> list = new ArrayList<>();
        if (body.isArray()) {
            body.forEach(list::add);
        } else {
            list.add(body);
        }
        if (list.size() > MAX_BATCH) {
            throw ApiException.invalid("At most " + MAX_BATCH + " items per request");
        }
        return list;
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        T value;
        try {
            value = mapper.treeToValue(node, type);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            throw ApiException.invalid(root.getMessage());
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw ApiException.invalid(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted().collect(Collectors.joining("; ")));
        }
        return value;
    }
}

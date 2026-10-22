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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step B-06. The single write path for node data, used by the REST API and by the
 * simulator alike (P0-12). Duplicate rule (P0-13 section 9): same ID and same body
 * returns the stored record and creates nothing; same ID with a different body is 409.
 */
@Service
public class IngestService {

    /** Server-managed fields that may differ between two otherwise identical bodies. */
    private static final List<String> IGNORED = List.of("schema_version", "imported_at");

    private final MissionRepository missions;
    private final TrackRepository track;
    private final ObservationRepository observations;
    private final EventBuilder eventBuilder;
    private final ObjectMapper mapper;

    public IngestService(MissionRepository missions, TrackRepository track, ObservationRepository observations,
                         EventBuilder eventBuilder, ObjectMapper mapper) {
        this.missions = missions;
        this.track = track;
        this.observations = observations;
        this.eventBuilder = eventBuilder;
        this.mapper = mapper;
    }

    @Transactional
    public IngestResult trackPoint(TrackPoint p) {
        requireMissionAndNode(p.missionId(), p.nodeId());
        return store(p, track.find(p.trackPointId()), track::insert, "Track point " + p.trackPointId());
    }

    @Transactional
    public IngestResult observation(Observation o) {
        requireMissionAndNode(o.missionId(), o.nodeId());
        return store(o, observations.findObservation(o.observationId()), observations::insertObservation,
                "Observation " + o.observationId());
    }

    @Transactional
    public IngestResult deviceStatus(DeviceStatus s) {
        requireMissionAndNode(s.missionId(), s.nodeId());
        return store(s, missions.findStatus(s.statusId()), missions::insertStatus, "Status " + s.statusId());
    }

    /** Stores a model output and runs the event builder; returns the event created, if any. */
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
}

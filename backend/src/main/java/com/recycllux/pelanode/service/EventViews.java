package com.recycllux.pelanode.service;

import com.recycllux.pelanode.domain.ContextRecord;
import com.recycllux.pelanode.domain.EventContext;
import com.recycllux.pelanode.domain.EventRecord;
import com.recycllux.pelanode.domain.MediaAsset;
import com.recycllux.pelanode.domain.ModelOutput;
import com.recycllux.pelanode.domain.Observation;
import com.recycllux.pelanode.domain.ReviewAction;
import com.recycllux.pelanode.repo.ContextRepository;
import com.recycllux.pelanode.repo.EventRepository;
import com.recycllux.pelanode.repo.MissionRepository;
import com.recycllux.pelanode.repo.ObservationRepository;
import com.recycllux.pelanode.web.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Builds the API read views of an event. The full view keeps observation, model output,
 * context and review as separate blocks (P0-03 section 4.1, P0-13 section 12): the
 * original model output is never replaced by a review.
 */
@Service
public class EventViews {

    private final EventRepository events;
    private final ObservationRepository observations;
    private final ContextRepository context;
    private final MissionRepository missions;

    public EventViews(EventRepository events, ObservationRepository observations, ContextRepository context,
                      MissionRepository missions) {
        this.events = events;
        this.observations = observations;
        this.context = context;
        this.missions = missions;
    }

    public Map<String, Object> full(String eventId) {
        EventRecord e = events.findEvent(eventId).orElseThrow(() -> ApiException.notFound("Event", eventId));
        Observation obs = observations.findObservation(e.observationId()).orElseThrow();
        ModelOutput mo = observations.findModelOutput(e.modelOutputId()).orElseThrow();

        Map<String, Object> v = header(e);
        v.put("observation", observationBlock(obs));
        v.put("paired_observation", e.pairedObservationId() == null ? null
                : observations.findObservation(e.pairedObservationId()).map(this::observationBlock).orElse(null));
        v.put("model_output", modelOutputBlock(mo));
        v.put("context", events.findContext(eventId).stream().map(this::contextBlock).toList());
        v.put("attention", e.attention());
        v.put("attention_reasons", e.attentionReasons());
        v.put("review", reviewBlock(e));
        v.put("data_origin", e.dataOrigin());
        v.put("device", missions.latestStatus(e.nodeId(), e.missionId(), e.capturedAt())
                .map(s -> {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("battery_pct", s.batteryPct());
                    d.put("connectivity", s.connectivity());
                    d.put("storage_pct", s.storagePct());
                    return d;
                }).orElse(null));
        return v;
    }

    /** Summary view for lists and the map. */
    public Map<String, Object> summary(EventRecord e) {
        Map<String, Object> v = header(e);
        ModelOutput mo = observations.findModelOutput(e.modelOutputId()).orElseThrow();
        Double quality = observations.findObservation(e.observationId()).map(Observation::qualityScore).orElse(null);
        v.put("confidence", mo.confidence());
        v.put("quality_score", quality);
        v.put("attention", e.attention());
        v.put("review", reviewBlock(e));
        v.put("data_origin", e.dataOrigin());
        return v;
    }

    private Map<String, Object> header(EventRecord e) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("event_id", e.eventId());
        v.put("schema_version", "1.0");
        v.put("mission_id", e.missionId());
        v.put("node_id", e.nodeId());
        v.put("event_type", e.eventType());
        v.put("perspective", e.perspective());
        v.put("captured_at", e.capturedAt());
        Map<String, Object> pos = new LinkedHashMap<>();
        pos.put("lat", e.lat());
        pos.put("lon", e.lon());
        Observation obs = observations.findObservation(e.observationId()).orElse(null);
        pos.put("h_acc_m", obs == null ? null : obs.hAccM());
        pos.put("source", obs == null ? null : obs.positionSource());
        v.put("position", pos);
        return v;
    }

    private Map<String, Object> observationBlock(Observation o) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("observation_id", o.observationId());
        b.put("source_sensor", o.sourceSensor());
        b.put("perspective", o.perspective());
        b.put("captured_at", o.capturedAt());
        b.put("paired_observation_id", o.pairedObservationId());
        b.put("pair_gap_ms", o.pairGapMs());
        b.put("media_id", o.mediaId());
        b.put("media_status", o.mediaStatus());
        MediaAsset frame = o.mediaId() == null ? null : observations.findMedia(o.mediaId()).orElse(null);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("frame_uri", frame == null ? null : frame.uri());
        evidence.put("thumb_uri", null);
        evidence.put("crop_uri", null);
        evidence.put("width_px", frame == null ? null : frame.widthPx());
        evidence.put("height_px", frame == null ? null : frame.heightPx());
        evidence.put("provenance", frame == null ? null : frame.provenance());
        b.put("evidence", evidence);
        b.put("quality_score", o.qualityScore());
        b.put("quality_flags", o.qualityFlags());
        return b;
    }

    private Map<String, Object> modelOutputBlock(ModelOutput m) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("model_output_id", m.modelOutputId());
        b.put("class", m.className());
        b.put("confidence", m.confidence());
        b.put("bbox", m.bbox());
        b.put("model_id", m.modelId());
        b.put("model_version", m.modelVersion());
        b.put("inference_origin", m.inferenceOrigin());
        b.put("review_required", m.reviewRequired());
        return b;
    }

    private Map<String, Object> contextBlock(EventContext ec) {
        ContextRecord r = context.findRecord(ec.contextRecordId()).orElseThrow();
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("context_record_id", r.contextRecordId());
        b.put("dataset_id", r.datasetId());
        b.put("provider", r.provider());
        b.put("variable", r.variable());
        b.put("value", r.value());
        b.put("unit", r.unit());
        b.put("observed_at", r.observedAt());
        b.put("station_name", r.stationName());
        b.put("distance_km", ec.distanceKm());
        b.put("time_offset_min", ec.timeOffsetMin());
        b.put("relation", ec.relation());
        b.put("rule_id", ec.ruleId());
        b.put("provenance", r.provenance());
        return b;
    }

    private Map<String, Object> reviewBlock(EventRecord e) {
        List<ReviewAction> history = events.findReviews(e.eventId());
        ReviewAction last = history.isEmpty() ? null : history.get(history.size() - 1);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("status", e.reviewStatus());
        b.put("current_label", e.currentLabel());
        b.put("last_action", last == null ? null : last.action());
        b.put("by", last == null ? null : last.reviewer());
        b.put("at", last == null ? null : last.createdAt());
        b.put("note", last == null ? null : last.note());
        return b;
    }
}

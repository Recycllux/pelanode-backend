package com.recycllux.pelanode.service;

import com.recycllux.pelanode.domain.EventRecord;
import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.domain.ModelOutput;
import com.recycllux.pelanode.domain.Observation;
import com.recycllux.pelanode.repo.EventRepository;
import com.recycllux.pelanode.repo.MissionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventBuilder {

    private static final Logger log = LoggerFactory.getLogger(EventBuilder.class);

    private final EventRepository events;
    private final MissionRepository missions;
    private final ContextAssociator associator;
    private final JdbcTemplate jdbc;
    private final Thresholds thresholds = Thresholds.DEFAULT;

    public Optional<String> onModelOutput(Observation obs, ModelOutput m) {
        if (m.confidence() < thresholds.candidate()) {
            return Optional.empty();
        }
        Optional<EventRecord> duplicate = findDuplicate(obs, m);
        if (duplicate.isPresent()) {
            jdbc.update("""
                    INSERT INTO event_evidence (event_id, observation_id, model_output_id)
                    SELECT ?, ?, ? WHERE NOT EXISTS
                      (SELECT 1 FROM event_evidence WHERE event_id = ? AND observation_id = ?)""",
                    duplicate.get().eventId(), obs.observationId(), m.modelOutputId(),
                    duplicate.get().eventId(), obs.observationId());
            return Optional.empty();
        }

        List<String> reasons = new ArrayList<>();
        if (m.confidence() >= thresholds.review()) {
            reasons.add("score_above_threshold");
        }
        if (obs.qualityScore() != null && obs.qualityScore() < thresholds.quality()) {
            reasons.add("low_quality");
        }
        if (Boolean.TRUE.equals(m.reviewRequired())) {
            reasons.add("model_flagged");
        }
        String attention = reasons.isEmpty() ? "informational" : "review_required";

        String eventId = nextEventId(obs.missionId());
        EventRecord event = new EventRecord(eventId, obs.missionId(), obs.nodeId(), m.className(),
                obs.perspective(), obs.observationId(), obs.pairedObservationId(), m.modelOutputId(),
                obs.capturedAt(), obs.lat(), obs.lon(), attention, reasons, "unreviewed", null, null,
                Instant.now(), obs.dataOrigin(), "1.0");
        events.insertEvent(event);
        jdbc.update("INSERT INTO event_evidence (event_id, observation_id, model_output_id) VALUES (?,?,?)",
                eventId, obs.observationId(), m.modelOutputId());
        int associated = associator.associate(event);
        log.info("EVENT_CREATED event={} class={} confidence={} attention={} context={}",
                eventId, m.className(), m.confidence(), attention, associated);
        return Optional.of(eventId);
    }

    private Optional<EventRecord> findDuplicate(Observation obs, ModelOutput m) {
        Duration window = Duration.ofSeconds(thresholds.dedupSeconds());
        return events.findByMission(obs.missionId(), null).stream()
                .filter(e -> e.eventType().equals(m.className()) && e.perspective().equals(obs.perspective()))
                .filter(e -> Duration.between(e.capturedAt(), obs.capturedAt()).abs().compareTo(window) <= 0)
                .filter(e -> e.lat() != null && obs.lat() != null
                        && Geo.km(e.lat(), e.lon(), obs.lat(), obs.lon()) * 1000 <= thresholds.dedupMetres())
                .findFirst();
    }

    private String nextEventId(String missionId) {
        Mission mission = missions.findMission(missionId).orElseThrow();
        int start = mission.eventIdStart() == null ? 1 : mission.eventIdStart();
        int next = events.findByMission(missionId, null).stream()
                .map(e -> e.eventId().replaceAll("\\D", ""))
                .filter(s -> !s.isEmpty())
                .mapToInt(Integer::parseInt)
                .map(n -> n + 1)
                .max().orElse(start);
        return "EV-%04d".formatted(Math.max(next, start));
    }
}

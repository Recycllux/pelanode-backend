package com.recycllux.pelanode.repo;

import static com.recycllux.pelanode.repo.JsonColumns.dbl;
import static com.recycllux.pelanode.repo.JsonColumns.instant;
import static com.recycllux.pelanode.repo.JsonColumns.ts;

import com.recycllux.pelanode.domain.EventContext;
import com.recycllux.pelanode.domain.EventRecord;
import com.recycllux.pelanode.domain.ReviewAction;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EventRepository {

    private final JdbcTemplate jdbc;
    private final JsonColumns json;

    private EventRecord eventRow(ResultSet rs, int i) throws SQLException {
        return new EventRecord(
            rs.getString("event_id"), rs.getString("mission_id"), rs.getString("node_id"),
            rs.getString("event_type"), rs.getString("perspective"), rs.getString("observation_id"),
            rs.getString("paired_observation_id"), rs.getString("model_output_id"),
            instant(rs, "captured_at"), dbl(rs, "lat"), dbl(rs, "lon"), rs.getString("attention"),
            json.strings(rs.getString("attention_reasons")), rs.getString("review_status"),
            rs.getString("current_label"), rs.getString("cluster_id"), instant(rs, "created_at"),
            rs.getString("data_origin"), rs.getString("schema_version"));
    }

    private static final RowMapper<EventContext> CONTEXT_ROW = (rs, i) -> new EventContext(
            rs.getString("event_id"), rs.getString("context_record_id"), rs.getString("relation"),
            dbl(rs, "distance_km"), rs.getDouble("time_offset_min"), rs.getString("rule_id"),
            instant(rs, "associated_at"));

    private static final RowMapper<ReviewAction> REVIEW_ROW = (rs, i) -> new ReviewAction(
            rs.getString("review_id"), rs.getString("event_id"), rs.getString("action"),
            rs.getString("new_label"), rs.getString("note"), rs.getString("reviewer"),
            rs.getString("request_id"), rs.getString("resulting_status"), instant(rs, "created_at"),
            instant(rs, "mission_time"), "1.0");

    public void insertEvent(EventRecord e) {
        jdbc.update("""
                INSERT INTO event (event_id, mission_id, node_id, event_type, perspective, observation_id,
                  paired_observation_id, model_output_id, captured_at, lat, lon, attention, attention_reasons,
                  review_status, current_label, cluster_id, created_at, data_origin, schema_version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                e.eventId(), e.missionId(), e.nodeId(), e.eventType(), e.perspective(), e.observationId(),
                e.pairedObservationId(), e.modelOutputId(), ts(e.capturedAt()), e.lat(), e.lon(),
                e.attention(), json.write(e.attentionReasons()), e.reviewStatus(), e.currentLabel(),
                e.clusterId(), ts(e.createdAt()), e.dataOrigin(), "1.0");
    }

    public Optional<EventRecord> findEvent(String eventId) {
        return jdbc.query("SELECT * FROM event WHERE event_id = ?", this::eventRow, eventId).stream().findFirst();
    }

    public List<EventRecord> findByMission(String missionId, Instant until) {
        if (until == null) {
            return jdbc.query("SELECT * FROM event WHERE mission_id = ? ORDER BY captured_at", this::eventRow, missionId);
        }
        return jdbc.query("""
                SELECT * FROM event WHERE mission_id = ? AND captured_at <= ?
                ORDER BY captured_at""", this::eventRow, missionId, ts(until));
    }

    public void updateReviewState(String eventId, String reviewStatus, String currentLabel) {
        jdbc.update("UPDATE event SET review_status = ?, current_label = ? WHERE event_id = ?",
                reviewStatus, currentLabel, eventId);
    }

    public void insertContext(EventContext c) {
        jdbc.update("""
                INSERT INTO event_context (event_id, context_record_id, relation, distance_km,
                  time_offset_min, rule_id, associated_at)
                VALUES (?,?,?,?,?,?,?)""",
                c.eventId(), c.contextRecordId(), c.relation(), c.distanceKm(), c.timeOffsetMin(),
                c.ruleId(), ts(c.associatedAt()));
    }

    public void deleteContext(String eventId) {
        jdbc.update("DELETE FROM event_context WHERE event_id = ?", eventId);
    }

    public List<EventContext> findContext(String eventId) {
        return jdbc.query("SELECT * FROM event_context WHERE event_id = ? ORDER BY ABS(time_offset_min)",
                CONTEXT_ROW, eventId);
    }

    public void insertReview(ReviewAction r) {
        jdbc.update("""
                INSERT INTO review_action (review_id, event_id, action, new_label, note, reviewer,
                  request_id, resulting_status, created_at, mission_time)
                VALUES (?,?,?,?,?,?,?,?,?,?)""",
                r.reviewId(), r.eventId(), r.action(), r.newLabel(), r.note(), r.reviewer(), r.requestId(),
                r.resultingStatus(), ts(r.createdAt()), ts(r.missionTime()));
    }

    public Optional<ReviewAction> findReviewByRequest(String requestId) {
        return jdbc.query("SELECT * FROM review_action WHERE request_id = ?", REVIEW_ROW, requestId)
                .stream().findFirst();
    }

    public List<ReviewAction> findReviews(String eventId) {
        return jdbc.query("SELECT * FROM review_action WHERE event_id = ? ORDER BY created_at, review_id",
                REVIEW_ROW, eventId);
    }

    public long countReviews() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM review_action", Long.class);
        return n == null ? 0 : n;
    }
}

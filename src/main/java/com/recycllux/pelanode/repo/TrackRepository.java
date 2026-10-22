package com.recycllux.pelanode.repo;

import static com.recycllux.pelanode.repo.JsonColumns.dbl;
import static com.recycllux.pelanode.repo.JsonColumns.instant;
import static com.recycllux.pelanode.repo.JsonColumns.ts;

import com.recycllux.pelanode.domain.TrackPoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Track points. Reads always sort by captured_at, never by arrival (P0-13 section 9). */
@Repository
public class TrackRepository {

    private final JdbcTemplate jdbc;

    public TrackRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<TrackPoint> ROW = (rs, i) -> new TrackPoint(
            rs.getString("track_point_id"), rs.getString("mission_id"), rs.getString("node_id"),
            instant(rs, "captured_at"), rs.getDouble("lat"), rs.getDouble("lon"),
            dbl(rs, "h_acc_m"), dbl(rs, "speed_mps"), dbl(rs, "heading_deg"),
            rs.getString("source"), rs.getString("quality_flag"), rs.getString("data_origin"));

    public void insert(TrackPoint p) {
        jdbc.update("""
                INSERT INTO track_point (track_point_id, mission_id, node_id, captured_at, lat, lon,
                  h_acc_m, speed_mps, heading_deg, source, quality_flag, data_origin)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
                p.trackPointId(), p.missionId(), p.nodeId(), ts(p.capturedAt()), p.lat(), p.lon(),
                p.hAccM(), p.speedMps(), p.headingDeg(), p.source(), p.qualityFlag(), p.dataOrigin());
    }

    public Optional<TrackPoint> find(String id) {
        return jdbc.query("SELECT * FROM track_point WHERE track_point_id = ?", ROW, id).stream().findFirst();
    }

    /** Track of a mission up to {@code until} inclusive (null = whole track). */
    public List<TrackPoint> findByMission(String missionId, Instant until) {
        if (until == null) {
            return jdbc.query("SELECT * FROM track_point WHERE mission_id = ? ORDER BY captured_at", ROW, missionId);
        }
        return jdbc.query("""
                SELECT * FROM track_point WHERE mission_id = ? AND captured_at <= ?
                ORDER BY captured_at""", ROW, missionId, ts(until));
    }
}

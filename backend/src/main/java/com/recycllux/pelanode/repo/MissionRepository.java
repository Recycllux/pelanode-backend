package com.recycllux.pelanode.repo;

import static com.recycllux.pelanode.repo.JsonColumns.dbl;
import static com.recycllux.pelanode.repo.JsonColumns.instant;
import static com.recycllux.pelanode.repo.JsonColumns.integer;
import static com.recycllux.pelanode.repo.JsonColumns.ts;

import com.recycllux.pelanode.domain.DeviceStatus;
import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.domain.Node;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Missions, nodes and device status. */
@Repository
public class MissionRepository {

    private final JdbcTemplate jdbc;
    private final JsonColumns json;

    public MissionRepository(JdbcTemplate jdbc, JsonColumns json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private Mission missionRow(ResultSet rs, int i) throws SQLException {
        return new Mission(
            rs.getString("mission_id"), rs.getString("name"), rs.getString("scenario"),
            rs.getString("region"), rs.getString("node_id"), rs.getString("mode"),
            instant(rs, "planned_start"), instant(rs, "planned_end"),
            json.numbers(rs.getString("area_bbox")), integer(rs, "event_id_start"),
            rs.getString("data_origin"), rs.getString("schema_version"));
    }

    private Node nodeRow(ResultSet rs, int i) throws SQLException {
        return new Node(
            rs.getString("node_id"), rs.getString("node_type"),
            json.strings(rs.getString("camera_ids")), rs.getString("firmware_version"),
            rs.getString("schema_version"));
    }

    private DeviceStatus statusRow(ResultSet rs, int i) throws SQLException {
        return new DeviceStatus(
            rs.getString("status_id"), rs.getString("node_id"), rs.getString("mission_id"),
            instant(rs, "observed_at"), dbl(rs, "battery_pct"), rs.getString("connectivity"),
            rs.getString("gnss_status"), rs.getString("surface_camera"),
            rs.getString("underwater_camera"), dbl(rs, "storage_pct"), rs.getString("data_origin"));
    }

    public void insertMission(Mission m) {
        jdbc.update("""
                INSERT INTO mission (mission_id, name, scenario, region, node_id, mode, planned_start,
                  planned_end, area_bbox, event_id_start, data_origin, schema_version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
                m.missionId(), m.name(), m.scenario(), m.region(), m.nodeId(), m.mode(),
                ts(m.plannedStart()), ts(m.plannedEnd()), json.write(m.areaBbox()),
                m.eventIdStart() == null ? 1 : m.eventIdStart(), m.dataOrigin(), "1.0");
    }

    public Optional<Mission> findMission(String missionId) {
        return jdbc.query("SELECT * FROM mission WHERE mission_id = ?", this::missionRow, missionId)
                .stream().findFirst();
    }

    public List<Mission> findAllMissions() {
        return jdbc.query("SELECT * FROM mission ORDER BY planned_start", this::missionRow);
    }

    public void insertNode(Node n) {
        jdbc.update("""
                INSERT INTO node (node_id, node_type, camera_ids, firmware_version, schema_version)
                VALUES (?,?,?,?,?)""",
                n.nodeId(), n.nodeType(), json.write(n.cameraIds()), n.firmwareVersion(), "1.0");
    }

    public Optional<Node> findNode(String nodeId) {
        return jdbc.query("SELECT * FROM node WHERE node_id = ?", this::nodeRow, nodeId).stream().findFirst();
    }

    public void insertStatus(DeviceStatus s) {
        jdbc.update("""
                INSERT INTO device_status (status_id, node_id, mission_id, observed_at, battery_pct,
                  connectivity, gnss_status, surface_camera, underwater_camera, storage_pct, data_origin)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                s.statusId(), s.nodeId(), s.missionId(), ts(s.observedAt()), s.batteryPct(),
                s.connectivity(), s.gnssStatus(), s.surfaceCamera(), s.underwaterCamera(),
                s.storagePct(), s.dataOrigin());
    }

    public Optional<DeviceStatus> findStatus(String statusId) {
        return jdbc.query("SELECT * FROM device_status WHERE status_id = ?", this::statusRow, statusId)
                .stream().findFirst();
    }

    /** Latest status at or before {@code until} (null = latest overall). */
    public Optional<DeviceStatus> latestStatus(String nodeId, String missionId, Instant until) {
        if (until == null) {
            return jdbc.query("""
                    SELECT * FROM device_status WHERE node_id = ? AND mission_id = ?
                    ORDER BY observed_at DESC LIMIT 1""", this::statusRow, nodeId, missionId).stream().findFirst();
        }
        return jdbc.query("""
                SELECT * FROM device_status WHERE node_id = ? AND mission_id = ? AND observed_at <= ?
                ORDER BY observed_at DESC LIMIT 1""", this::statusRow, nodeId, missionId, ts(until))
                .stream().findFirst();
    }
}

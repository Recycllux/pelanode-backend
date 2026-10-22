package com.recycllux.pelanode.repo;

import static com.recycllux.pelanode.repo.JsonColumns.dbl;
import static com.recycllux.pelanode.repo.JsonColumns.instant;
import static com.recycllux.pelanode.repo.JsonColumns.integer;
import static com.recycllux.pelanode.repo.JsonColumns.ts;

import com.recycllux.pelanode.domain.MediaAsset;
import com.recycllux.pelanode.domain.ModelOutput;
import com.recycllux.pelanode.domain.Observation;
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
public class ObservationRepository {

    private final JdbcTemplate jdbc;
    private final JsonColumns json;

    private Observation observationRow(ResultSet rs, int i) throws SQLException {
        return new Observation(
            rs.getString("observation_id"), rs.getString("mission_id"), rs.getString("node_id"),
            instant(rs, "captured_at"), dbl(rs, "lat"), dbl(rs, "lon"), dbl(rs, "h_acc_m"),
            rs.getString("position_source"), dbl(rs, "speed_mps"), dbl(rs, "heading_deg"),
            dbl(rs, "roll_deg"), dbl(rs, "pitch_deg"), rs.getString("source_sensor"),
            rs.getString("perspective"), rs.getString("camera_id"), integer(rs, "sequence_no"),
            rs.getString("paired_observation_id"), integer(rs, "pair_gap_ms"), rs.getString("media_id"),
            rs.getString("media_status"), dbl(rs, "quality_score"),
            json.strings(rs.getString("quality_flags")), json.object(rs.getString("sensors")),
            rs.getString("data_origin"), rs.getString("schema_version"));
    }

    private static final RowMapper<MediaAsset> MEDIA_ROW = (rs, i) -> new MediaAsset(
            rs.getString("media_id"), rs.getString("observation_id"), rs.getString("kind"),
            rs.getString("uri"), integer(rs, "width_px"), integer(rs, "height_px"),
            rs.getString("mime_type"), rs.getString("provenance"), rs.getString("licence_note"),
            rs.getString("sha256"));

    private ModelOutput outputRow(ResultSet rs, int i) throws SQLException {
        return new ModelOutput(
            rs.getString("model_output_id"), rs.getString("observation_id"), rs.getString("task"),
            rs.getString("taxonomy"), rs.getString("class"), rs.getDouble("confidence"),
            json.numbers(rs.getString("bbox")), rs.getString("mask_uri"), rs.getString("model_id"),
            rs.getString("model_version"), rs.getString("inference_origin"),
            instant(rs, "processed_at"), integer(rs, "processing_ms"),
            rs.getBoolean("review_required"), rs.getString("schema_version"));
    }

    public void insertObservation(Observation o) {
        jdbc.update("""
                INSERT INTO observation (observation_id, mission_id, node_id, captured_at, lat, lon, h_acc_m,
                  position_source, speed_mps, heading_deg, roll_deg, pitch_deg, source_sensor, perspective,
                  camera_id, sequence_no, paired_observation_id, pair_gap_ms, media_id, media_status,
                  quality_score, quality_flags, sensors, data_origin, schema_version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                o.observationId(), o.missionId(), o.nodeId(), ts(o.capturedAt()), o.lat(), o.lon(),
                o.hAccM(), o.positionSource(), o.speedMps(), o.headingDeg(), o.rollDeg(), o.pitchDeg(),
                o.sourceSensor(), o.perspective(), o.cameraId(), o.sequence(), o.pairedObservationId(),
                o.pairGapMs(), o.mediaId(), o.mediaStatus(), o.qualityScore(),
                json.write(o.qualityFlags()), json.write(o.sensors()), o.dataOrigin(), "1.0");
    }

    public Optional<Observation> findObservation(String id) {
        return jdbc.query("SELECT * FROM observation WHERE observation_id = ?", this::observationRow, id)
                .stream().findFirst();
    }

    public void insertMedia(MediaAsset m) {
        jdbc.update("""
                INSERT INTO media_asset (media_id, observation_id, kind, uri, width_px, height_px,
                  mime_type, provenance, licence_note, sha256)
                VALUES (?,?,?,?,?,?,?,?,?,?)""",
                m.mediaId(), m.observationId(), m.kind(), m.uri(), m.widthPx(), m.heightPx(),
                m.mimeType(), m.provenance(), m.licenceNote(), m.sha256());
    }

    public Optional<MediaAsset> findMedia(String mediaId) {
        return jdbc.query("SELECT * FROM media_asset WHERE media_id = ?", MEDIA_ROW, mediaId).stream().findFirst();
    }

    public List<MediaAsset> findMediaByObservation(String observationId) {
        return jdbc.query("SELECT * FROM media_asset WHERE observation_id = ? ORDER BY media_id",
                MEDIA_ROW, observationId);
    }

    public void insertModelOutput(ModelOutput m) {
        jdbc.update("""
                INSERT INTO model_output (model_output_id, observation_id, task, taxonomy, class, confidence,
                  bbox, mask_uri, model_id, model_version, inference_origin, processed_at, processing_ms,
                  review_required, schema_version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                m.modelOutputId(), m.observationId(), m.task(), m.taxonomy(), m.className(), m.confidence(),
                json.write(m.bbox()), m.maskUri(), m.modelId(), m.modelVersion(), m.inferenceOrigin(),
                ts(m.processedAt()), m.processingMs(), m.reviewRequired(), "1.0");
    }

    public Optional<ModelOutput> findModelOutput(String id) {
        return jdbc.query("SELECT * FROM model_output WHERE model_output_id = ?", this::outputRow, id)
                .stream().findFirst();
    }

    public List<ModelOutput> findModelOutputsByObservation(String observationId) {
        return jdbc.query("SELECT * FROM model_output WHERE observation_id = ? ORDER BY processed_at",
                this::outputRow, observationId);
    }
}

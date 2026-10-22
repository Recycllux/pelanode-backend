package com.recycllux.pelanode.repo;

import static com.recycllux.pelanode.repo.JsonColumns.dbl;
import static com.recycllux.pelanode.repo.JsonColumns.instant;
import static com.recycllux.pelanode.repo.JsonColumns.ts;

import com.recycllux.pelanode.domain.ContextRecord;
import com.recycllux.pelanode.domain.DatasetSource;
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
public class ContextRepository {

    private final JdbcTemplate jdbc;
    private final JsonColumns json;

    private DatasetSource datasetRow(ResultSet rs, int i) throws SQLException {
        return new DatasetSource(
            rs.getString("dataset_id"), rs.getString("provider"), rs.getString("name"),
            json.strings(rs.getString("variables")), rs.getString("format"), rs.getString("licence"),
            rs.getString("provenance"), rs.getString("status"));
    }

    private static final RowMapper<ContextRecord> RECORD_ROW = (rs, i) -> new ContextRecord(
            rs.getString("context_record_id"), rs.getString("dataset_id"), rs.getString("provider"),
            rs.getString("variable"), dbl(rs, "norm_value"), rs.getString("unit"),
            rs.getString("source_value"), rs.getString("source_unit"), instant(rs, "observed_at"),
            rs.getString("source_timestamp"), rs.getString("station_id"), rs.getString("station_name"),
            dbl(rs, "lat"), dbl(rs, "lon"), rs.getString("geometry_type"), rs.getString("provenance"),
            rs.getString("licence_note"), instant(rs, "imported_at"));

    public void insertDataset(DatasetSource d) {
        jdbc.update("""
                INSERT INTO dataset_source (dataset_id, provider, name, variables, format, licence,
                  provenance, status)
                VALUES (?,?,?,?,?,?,?,?)""",
                d.datasetId(), d.provider(), d.name(), json.write(d.variables()), d.format(), d.licence(),
                d.provenance(), d.status());
    }

    public Optional<DatasetSource> findDataset(String id) {
        return jdbc.query("SELECT * FROM dataset_source WHERE dataset_id = ?", this::datasetRow, id)
                .stream().findFirst();
    }

    public List<DatasetSource> findAllDatasets() {
        return jdbc.query("SELECT * FROM dataset_source ORDER BY dataset_id", this::datasetRow);
    }

    public void insertRecord(ContextRecord r, Instant importedAt) {
        jdbc.update("""
                INSERT INTO context_record (context_record_id, dataset_id, provider, variable, norm_value,
                  unit, source_value, source_unit, observed_at, source_timestamp, station_id, station_name,
                  lat, lon, geometry_type, provenance, licence_note, imported_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                r.contextRecordId(), r.datasetId(), r.provider(), r.variable(), r.value(), r.unit(),
                r.sourceValue(), r.sourceUnit(), ts(r.observedAt()), r.sourceTimestamp(), r.stationId(),
                r.stationName(), r.lat(), r.lon(), r.geometryType(), r.provenance(), r.licenceNote(),
                ts(importedAt));
    }

    public Optional<ContextRecord> findRecord(String id) {
        return jdbc.query("SELECT * FROM context_record WHERE context_record_id = ?", RECORD_ROW, id)
                .stream().findFirst();
    }

    public List<ContextRecord> findRecords(Instant from, Instant to, String variable) {
        if (variable == null) {
            return jdbc.query("""
                    SELECT * FROM context_record WHERE observed_at BETWEEN ? AND ?
                    ORDER BY dataset_id, observed_at""", RECORD_ROW, ts(from), ts(to));
        }
        return jdbc.query("""
                SELECT * FROM context_record WHERE observed_at BETWEEN ? AND ? AND variable = ?
                ORDER BY dataset_id, observed_at""", RECORD_ROW, ts(from), ts(to), variable);
    }
}

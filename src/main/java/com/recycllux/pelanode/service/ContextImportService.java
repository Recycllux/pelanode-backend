package com.recycllux.pelanode.service;

import com.recycllux.pelanode.config.PelanodeProperties;
import com.recycllux.pelanode.domain.ContextRecord;
import com.recycllux.pelanode.domain.DatasetSource;
import com.recycllux.pelanode.repo.ContextRepository;
import com.recycllux.pelanode.web.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContextImportService {

    private static final Logger log = LoggerFactory.getLogger(ContextImportService.class);
    static final Set<String> PROVENANCES = Set.of("organiser", "public", "simulated");
    static final Set<String> VARIABLES = Set.of("discharge", "water_level", "water_temperature");

    private final ContextRepository context;
    private final ContextAssociator associator;
    private final PelanodeProperties props;

    @Transactional
    public record Request(DatasetSource dataset, List<ContextRecord> records, String missionId) {
    }

    public Map<String, Object> importDataset(Request req) {
        if (req == null) {
            throw ApiException.invalid("body is required");
        }
        return importDataset(req.dataset(), req.records(), req.missionId());
    }

    public Map<String, Object> importDataset(DatasetSource dataset, List<ContextRecord> records, String missionId) {
        if (dataset == null || blank(dataset.datasetId()) || blank(dataset.provider()) || blank(dataset.name())) {
            throw ApiException.invalid("dataset with dataset_id, provider and name is required");
        }
        if (!PROVENANCES.contains(dataset.provenance())) {
            throw ApiException.invalid("dataset provenance must be one of " + PROVENANCES);
        }
        if (records == null || records.isEmpty()) {
            throw ApiException.invalid("records must not be empty");
        }
        String mission = blank(missionId) ? props.demoMissionId() : missionId;

        boolean datasetCreated = context.findDataset(dataset.datasetId()).isEmpty();
        if (datasetCreated) {
            context.insertDataset(new DatasetSource(dataset.datasetId(), dataset.provider(), dataset.name(),
                    dataset.variables() == null ? List.of() : dataset.variables(), dataset.format(),
                    dataset.licence(), dataset.provenance(), blank(dataset.status()) ? "active" : dataset.status()));
        }

        Instant now = Instant.now();
        int imported = 0;
        int unchanged = 0;
        for (int i = 0; i < records.size(); i++) {
            ContextRecord r = normalise(dataset, records.get(i), i);
            var existing = context.findRecord(r.contextRecordId());
            if (existing.isPresent()) {
                if (!sameValue(existing.get(), r)) {
                    throw ApiException.conflict("context record already exists with different values: "
                            + r.contextRecordId());
                }
                unchanged++;
                continue;
            }
            context.insertRecord(r, now);
            imported++;
        }
        int reassociated = associator.reassociateMission(mission);
        log.info("CONTEXT_IMPORTED dataset={} provenance={} imported={} unchanged={} events_with_context={}",
                dataset.datasetId(), dataset.provenance(), imported, unchanged, reassociated);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dataset_id", dataset.datasetId());
        out.put("provenance", dataset.provenance());
        out.put("dataset_created", datasetCreated);
        out.put("imported", imported);
        out.put("unchanged", unchanged);
        out.put("mission_id", mission);
        out.put("events_with_context", reassociated);
        out.put("imported_at", now);
        return out;
    }

    private static ContextRecord normalise(DatasetSource d, ContextRecord r, int index) {
        String where = "records[" + index + "]";
        if (r == null) {
            throw ApiException.invalid(where + " is null");
        }
        if (!VARIABLES.contains(r.variable())) {
            throw ApiException.invalid(where + ".variable must be one of " + VARIABLES);
        }
        if (r.value() == null || blank(r.unit()) || r.observedAt() == null) {
            throw ApiException.invalid(where + " needs value, unit and observed_at");
        }
        if (r.lat() == null || r.lon() == null) {
            throw ApiException.invalid(where + " needs lat and lon (association is by distance)");
        }
        if (r.datasetId() != null && !r.datasetId().equals(d.datasetId())) {
            throw ApiException.invalid(where + ".dataset_id differs from the dataset being imported");
        }
        String station = blank(r.stationId()) ? "NA" : r.stationId();
        String id = blank(r.contextRecordId())
                ? "%s:%s:%s:%s".formatted(d.datasetId(), station, r.variable(), r.observedAt())
                : r.contextRecordId();
        return new ContextRecord(id, d.datasetId(), d.provider(), r.variable(), r.value(), r.unit(),
                blank(r.sourceValue()) ? String.valueOf(r.value()) : r.sourceValue(),
                blank(r.sourceUnit()) ? r.unit() : r.sourceUnit(), r.observedAt(),
                blank(r.sourceTimestamp()) ? r.observedAt().toString() : r.sourceTimestamp(),
                r.stationId(), r.stationName(), r.lat(), r.lon(),
                blank(r.geometryType()) ? "point" : r.geometryType(), d.provenance(),
                r.licenceNote() != null ? r.licenceNote() : d.licence(), null);
    }

    private static boolean sameValue(ContextRecord a, ContextRecord b) {
        return Objects.equals(a.value(), b.value()) && Objects.equals(a.unit(), b.unit())
                && Objects.equals(a.observedAt(), b.observedAt()) && Objects.equals(a.lat(), b.lat())
                && Objects.equals(a.lon(), b.lon());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

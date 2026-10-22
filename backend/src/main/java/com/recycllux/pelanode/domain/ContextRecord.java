package com.recycllux.pelanode.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** One external value. Never overwrites node data. */
public record ContextRecord(
        @NotBlank String contextRecordId,
        @NotBlank String datasetId,
        @NotBlank String provider,
        @NotBlank String variable,
        Double value,
        @NotBlank String unit,
        @NotBlank String sourceValue,
        @NotBlank String sourceUnit,
        @NotNull Instant observedAt,
        @NotBlank String sourceTimestamp,
        String stationId,
        String stationName,
        Double lat,
        Double lon,
        @NotBlank String geometryType,
        @NotBlank String provenance,
        String licenceNote,
        Instant importedAt) {
}

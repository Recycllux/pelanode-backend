package com.recycllux.pelanode.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** A timestamped position. */
public record TrackPoint(
        @NotBlank String trackPointId,
        @NotBlank String missionId,
        @NotBlank String nodeId,
        @NotNull Instant capturedAt,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
        Double hAccM,
        Double speedMps,
        Double headingDeg,
        @NotBlank String source,
        @NotBlank String qualityFlag,
        @NotBlank String dataOrigin) {
}

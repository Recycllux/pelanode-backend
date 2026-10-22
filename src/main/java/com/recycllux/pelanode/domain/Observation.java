package com.recycllux.pelanode.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Primary evidence. No AI field may appear here (P0-13 section 2). */
public record Observation(
        @NotBlank String observationId,
        @NotBlank String missionId,
        @NotBlank String nodeId,
        @NotNull Instant capturedAt,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lon,
        Double hAccM,
        @NotBlank String positionSource,
        Double speedMps,
        Double headingDeg,
        Double rollDeg,
        Double pitchDeg,
        @NotBlank String sourceSensor,
        @NotBlank String perspective,
        String cameraId,
        Integer sequence,
        String pairedObservationId,
        Integer pairGapMs,
        String mediaId,
        @NotBlank String mediaStatus,
        Double qualityScore,
        List<String> qualityFlags,
        Map<String, Object> sensors,
        @NotBlank String dataOrigin,
        String schemaVersion) {
}

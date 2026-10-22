package com.recycllux.pelanode.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** One interpretation of one observation. Immutable once stored. */
public record ModelOutput(
        @NotBlank String modelOutputId,
        @NotBlank String observationId,
        @NotBlank String task,
        @NotBlank String taxonomy,
        @NotBlank @JsonProperty("class") String className,
        @NotNull @DecimalMin("0") @DecimalMax("1") Double confidence,
        List<Double> bbox,
        String maskUri,
        @NotBlank String modelId,
        @NotBlank String modelVersion,
        @NotBlank String inferenceOrigin,
        @NotNull Instant processedAt,
        Integer processingMs,
        @NotNull Boolean reviewRequired,
        String schemaVersion) {
}

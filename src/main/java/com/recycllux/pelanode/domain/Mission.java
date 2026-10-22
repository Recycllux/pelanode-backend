package com.recycllux.pelanode.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** A bounded deployment (P0-13 section 1). */
public record Mission(
        @NotBlank String missionId,
        @NotBlank String name,
        @NotBlank String scenario,
        @NotBlank String region,
        @NotBlank String nodeId,
        @NotBlank String mode,
        @NotNull Instant plannedStart,
        @NotNull Instant plannedEnd,
        List<Double> areaBbox,
        Integer eventIdStart,
        @NotBlank String dataOrigin,
        String schemaVersion) {
}

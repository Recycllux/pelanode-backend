package com.recycllux.pelanode.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** A health message from the node (simulated in H0). */
public record DeviceStatus(
        @NotBlank String statusId,
        @NotBlank String nodeId,
        @NotBlank String missionId,
        @NotNull Instant observedAt,
        Double batteryPct,
        @NotBlank String connectivity,
        @NotBlank String gnssStatus,
        @NotBlank String surfaceCamera,
        @NotBlank String underwaterCamera,
        Double storagePct,
        @NotBlank String dataOrigin) {
}

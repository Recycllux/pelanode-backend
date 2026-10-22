package com.recycllux.pelanode.domain;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** Identity of a physical or simulated device. */
public record Node(
        @NotBlank String nodeId,
        @NotBlank String nodeType,
        List<String> cameraIds,
        String firmwareVersion,
        String schemaVersion) {
}

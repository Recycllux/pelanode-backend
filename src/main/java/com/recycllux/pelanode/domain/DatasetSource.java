package com.recycllux.pelanode.domain;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record DatasetSource(
        @NotBlank String datasetId,
        @NotBlank String provider,
        @NotBlank String name,
        List<String> variables,
        String format,
        String licence,
        @NotBlank String provenance,
        @NotBlank String status) {
}

package com.recycllux.pelanode.domain;

import jakarta.validation.constraints.NotBlank;

/** A frame, thumbnail, crop or clip. */
public record MediaAsset(
        @NotBlank String mediaId,
        @NotBlank String observationId,
        @NotBlank String kind,
        @NotBlank String uri,
        Integer widthPx,
        Integer heightPx,
        @NotBlank String mimeType,
        @NotBlank String provenance,
        String licenceNote,
        String sha256) {
}

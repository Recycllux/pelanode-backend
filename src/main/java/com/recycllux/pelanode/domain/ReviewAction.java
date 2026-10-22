package com.recycllux.pelanode.domain;

import java.time.Instant;

/** Append-only human decision. */
public record ReviewAction(
        String reviewId,
        String eventId,
        String action,
        String newLabel,
        String note,
        String reviewer,
        String requestId,
        String resultingStatus,
        Instant createdAt,
        String schemaVersion) {
}

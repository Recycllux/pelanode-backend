package com.recycllux.pelanode.domain;

import java.time.Instant;
import java.util.List;

/** The stored event row. The API read view is built from it in EventView. */
public record EventRecord(
        String eventId,
        String missionId,
        String nodeId,
        String eventType,
        String perspective,
        String observationId,
        String pairedObservationId,
        String modelOutputId,
        Instant capturedAt,
        Double lat,
        Double lon,
        String attention,
        List<String> attentionReasons,
        String reviewStatus,
        String currentLabel,
        String clusterId,
        Instant createdAt,
        String dataOrigin,
        String schemaVersion) {
}

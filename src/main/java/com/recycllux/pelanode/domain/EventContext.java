package com.recycllux.pelanode.domain;

import java.time.Instant;

/** Association between an event and a context record. Never causal. */
public record EventContext(
        String eventId,
        String contextRecordId,
        String relation,
        Double distanceKm,
        double timeOffsetMin,
        String ruleId,
        Instant associatedAt) {
}

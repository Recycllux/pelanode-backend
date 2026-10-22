package com.recycllux.pelanode.domain;

import java.time.Instant;

public record EventContext(
        String eventId,
        String contextRecordId,
        String relation,
        Double distanceKm,
        double timeOffsetMin,
        String ruleId,
        Instant associatedAt) {
}

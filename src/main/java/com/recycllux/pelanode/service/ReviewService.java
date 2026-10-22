package com.recycllux.pelanode.service;

import com.recycllux.pelanode.domain.EventRecord;
import com.recycllux.pelanode.domain.ReviewAction;
import com.recycllux.pelanode.repo.EventRepository;
import com.recycllux.pelanode.web.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step B-12. Append-only human review (P0-13 section 6). The event's review status and
 * current label change; the model output and the observation never do.
 * Idempotent on the client request_id.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final EventRepository events;
    private final EventViews views;

    public ReviewService(EventRepository events, EventViews views) {
        this.events = events;
        this.views = views;
    }

    public record Request(String action, String newLabel, String note, String reviewer, String requestId) {
    }

    public record Outcome(boolean created, Map<String, Object> body) {
    }

    @Transactional
    public Outcome review(String eventId, Request r) {
        EventRecord event = events.findEvent(eventId).orElseThrow(() -> ApiException.notFound("Event", eventId));
        if (r.requestId() == null || r.requestId().isBlank()) {
            throw ApiException.invalid("request_id is required");
        }
        if (r.reviewer() == null || r.reviewer().isBlank()) {
            throw ApiException.invalid("reviewer is required");
        }
        Optional<ReviewAction> previous = events.findReviewByRequest(r.requestId());
        if (previous.isPresent()) {
            if (!previous.get().eventId().equals(eventId)) {
                throw ApiException.conflict("request_id already used for another event");
            }
            return new Outcome(false, body(previous.get(), eventId));
        }

        String status;
        String label = event.currentLabel();
        switch (r.action() == null ? "" : r.action()) {
            case "confirm" -> status = "confirmed";
            case "reject" -> status = "dismissed";
            case "relabel" -> {
                if (r.newLabel() == null || r.newLabel().isBlank()) {
                    throw ApiException.invalid("relabel needs new_label");
                }
                status = "relabelled";
                label = r.newLabel();
            }
            case "leave_open" -> status = event.reviewStatus();
            default -> throw ApiException.invalid("action must be confirm, reject, relabel or leave_open");
        }

        ReviewAction action = new ReviewAction("RV-%06d".formatted(events.countReviews() + 1), eventId,
                r.action(), "relabel".equals(r.action()) ? r.newLabel() : null, r.note(), r.reviewer(),
                r.requestId(), status, Instant.now(), "1.0");
        events.insertReview(action);
        events.updateReviewState(eventId, status, label);
        log.info("REVIEW_RECORDED event={} action={} status={} reviewer={}", eventId, r.action(), status,
                r.reviewer());
        return new Outcome(true, body(action, eventId));
    }

    private Map<String, Object> body(ReviewAction action, String eventId) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("review", action);
        b.put("event", views.full(eventId));
        return b;
    }
}

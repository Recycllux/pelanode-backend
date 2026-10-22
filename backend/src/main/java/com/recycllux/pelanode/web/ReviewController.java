package com.recycllux.pelanode.web;

import com.recycllux.pelanode.domain.ReviewAction;
import com.recycllux.pelanode.repo.EventRepository;
import com.recycllux.pelanode.service.ReviewService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Step B-12: review endpoints. */
@RestController
@RequestMapping("/api/v1/events/{id}")
public class ReviewController {

    private final ReviewService reviews;
    private final EventRepository events;

    public ReviewController(ReviewService reviews, EventRepository events) {
        this.reviews = reviews;
        this.events = events;
    }

    @PostMapping("/review")
    public ResponseEntity<Map<String, Object>> review(@PathVariable String id, @RequestBody ReviewService.Request r) {
        ReviewService.Outcome out = reviews.review(id, r);
        return ResponseEntity.status(out.created() ? HttpStatus.CREATED : HttpStatus.OK).body(out.body());
    }

    @GetMapping("/reviews")
    public List<ReviewAction> history(@PathVariable String id) {
        events.findEvent(id).orElseThrow(() -> ApiException.notFound("Event", id));
        return events.findReviews(id);
    }
}

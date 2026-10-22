package com.recycllux.pelanode.web;

import com.recycllux.pelanode.service.EventService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventController {

    private final EventService events;

    @GetMapping("/missions/{id}/track")
    public Object track(@PathVariable String id,
                        @RequestParam(required = false) String until,
                        @RequestParam(defaultValue = "json") String format) {
        return events.track(id, until, format);
    }

    @GetMapping("/events")
    public Object list(@RequestParam("mission_id") String missionId,
                       @RequestParam(required = false) String until,
                       @RequestParam(name = "review_status", required = false) String reviewStatus,
                       @RequestParam(required = false) String attention,
                       @RequestParam(required = false) String perspective,
                       @RequestParam(name = "event_type", required = false) String eventType,
                       @RequestParam(defaultValue = "json") String format) {
        return events.list(missionId, until,
                new EventService.Filter(reviewStatus, attention, perspective, eventType), format);
    }

    @GetMapping("/events/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return events.get(id);
    }
}

package com.recycllux.pelanode.web;

import com.recycllux.pelanode.domain.EventRecord;
import com.recycllux.pelanode.domain.TrackPoint;
import com.recycllux.pelanode.repo.EventRepository;
import com.recycllux.pelanode.repo.MissionRepository;
import com.recycllux.pelanode.repo.TrackRepository;
import com.recycllux.pelanode.service.EventViews;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Step B-08: reads cut at a mission time. A backward seek deletes nothing; the UI
 * passes until = mission_time and sees only what had happened by then (P0-12).
 */
@RestController
@RequestMapping("/api/v1")
public class EventController {

    private final TrackRepository track;
    private final EventRepository events;
    private final MissionRepository missions;
    private final EventViews views;

    public EventController(TrackRepository track, EventRepository events, MissionRepository missions,
                           EventViews views) {
        this.track = track;
        this.events = events;
        this.missions = missions;
        this.views = views;
    }

    @GetMapping("/missions/{id}/track")
    public Object track(@PathVariable String id,
                        @RequestParam(required = false) String until,
                        @RequestParam(defaultValue = "json") String format) {
        missions.findMission(id).orElseThrow(() -> ApiException.notFound("Mission", id));
        List<TrackPoint> points = track.findByMission(id, until(until));
        if ("geojson".equals(format)) {
            return Geojson.track(points);
        }
        return points;
    }

    @GetMapping("/events")
    public Object list(@RequestParam("mission_id") String missionId,
                       @RequestParam(required = false) String until,
                       @RequestParam(name = "review_status", required = false) String reviewStatus,
                       @RequestParam(required = false) String attention,
                       @RequestParam(required = false) String perspective,
                       @RequestParam(name = "event_type", required = false) String eventType,
                       @RequestParam(defaultValue = "json") String format) {
        missions.findMission(missionId).orElseThrow(() -> ApiException.notFound("Mission", missionId));
        List<EventRecord> list = events.findByMission(missionId, until(until)).stream()
                .filter(e -> reviewStatus == null || reviewStatus.equals(e.reviewStatus()))
                .filter(e -> attention == null || attention.equals(e.attention()))
                .filter(e -> perspective == null || perspective.equals(e.perspective()))
                .filter(e -> eventType == null || eventType.equals(e.eventType()))
                .toList();
        List<Map<String, Object>> summaries = list.stream().map(views::summary).toList();
        if ("geojson".equals(format)) {
            return Geojson.events(summaries);
        }
        return summaries;
    }

    @GetMapping("/events/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return views.full(id);
    }

    private static Instant until(String until) {
        return until == null || until.isBlank() ? null : MissionController.parse(until);
    }

    /** Minimal GeoJSON writers (RFC 7946: coordinates are [lon, lat]). */
    static final class Geojson {

        private Geojson() {
        }

        static Map<String, Object> track(List<TrackPoint> points) {
            Map<String, Object> line = feature(Map.of("type", "LineString",
                    "coordinates", points.stream().map(p -> List.of(p.lon(), p.lat())).toList()),
                    Map.of("kind", "track", "points", points.size()));
            List<Object> features = new java.util.ArrayList<>();
            features.add(line);
            points.forEach(p -> features.add(feature(Map.of("type", "Point", "coordinates", List.of(p.lon(), p.lat())),
                    Map.of("kind", "track_point", "track_point_id", p.trackPointId(),
                            "captured_at", p.capturedAt().toString(), "data_origin", p.dataOrigin()))));
            return collection(features);
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> events(List<Map<String, Object>> summaries) {
            List<Object> features = summaries.stream().<Object>map(s -> {
                Map<String, Object> pos = (Map<String, Object>) s.get("position");
                Map<String, Object> props = new LinkedHashMap<>(s);
                props.remove("position");
                return feature(Map.of("type", "Point", "coordinates", List.of(pos.get("lon"), pos.get("lat"))), props);
            }).toList();
            return collection(features);
        }

        private static Map<String, Object> feature(Object geometry, Map<String, Object> properties) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("type", "Feature");
            f.put("geometry", geometry);
            f.put("properties", properties);
            return f;
        }

        private static Map<String, Object> collection(List<Object> features) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("type", "FeatureCollection");
            c.put("features", features);
            return c;
        }
    }
}

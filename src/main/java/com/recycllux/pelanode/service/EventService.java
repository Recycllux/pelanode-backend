package com.recycllux.pelanode.service;

import com.recycllux.pelanode.domain.EventRecord;
import com.recycllux.pelanode.domain.TrackPoint;
import com.recycllux.pelanode.pack.DemoPack;
import com.recycllux.pelanode.repo.EventRepository;
import com.recycllux.pelanode.repo.MissionRepository;
import com.recycllux.pelanode.repo.TrackRepository;
import com.recycllux.pelanode.web.ApiException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventService {

    private final TrackRepository track;
    private final EventRepository events;
    private final MissionRepository missions;
    private final EventViews views;
    private final DemoPack pack;

    public record Filter(String reviewStatus, String attention, String perspective, String eventType) {
    }

    public Object track(String missionId, String until, String format) {
        requireMission(missionId);
        List<TrackPoint> points = track.findByMission(missionId, Times.parseOptional(until));
        if ("geojson".equals(format)) {
            return GeoJson.track(points);
        }
        return points;
    }

    /** The whole planned route from the demo pack, independent of replay time (drawn before the replay starts). */
    public List<TrackPoint> route(String missionId) {
        requireMission(missionId);
        return pack.track().stream().filter(p -> missionId.equals(p.missionId())).toList();
    }

    public Object list(String missionId, String until, Filter f, String format) {
        requireMission(missionId);
        List<EventRecord> list = events.findByMission(missionId, Times.parseOptional(until)).stream()
                .filter(e -> f.reviewStatus() == null || f.reviewStatus().equals(e.reviewStatus()))
                .filter(e -> f.attention() == null || f.attention().equals(e.attention()))
                .filter(e -> f.perspective() == null || f.perspective().equals(e.perspective()))
                .filter(e -> f.eventType() == null || f.eventType().equals(e.eventType()))
                .toList();
        List<Map<String, Object>> summaries = list.stream().map(views::summary).toList();
        if ("geojson".equals(format)) {
            return GeoJson.events(summaries);
        }
        return summaries;
    }

    public Map<String, Object> get(String eventId) {
        return views.full(eventId);
    }

    private void requireMission(String missionId) {
        missions.findMission(missionId).orElseThrow(() -> ApiException.notFound("Mission", missionId));
    }
}

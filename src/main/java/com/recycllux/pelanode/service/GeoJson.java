package com.recycllux.pelanode.service;

import com.recycllux.pelanode.domain.TrackPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GeoJson {

    private GeoJson() {
    }

    static Map<String, Object> track(List<TrackPoint> points) {
        Map<String, Object> line = feature(Map.of("type", "LineString",
                "coordinates", points.stream().map(p -> List.of(p.lon(), p.lat())).toList()),
                Map.of("kind", "track", "points", points.size()));
        List<Object> features = new ArrayList<>();
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

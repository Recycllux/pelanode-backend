package com.recycllux.pelanode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.recycllux.pelanode.config.JsonConfig;
import com.recycllux.pelanode.config.PelanodeProperties;
import com.recycllux.pelanode.domain.ContextRecord;
import com.recycllux.pelanode.domain.EventRecord;
import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.domain.ReviewAction;
import com.recycllux.pelanode.domain.TrackPoint;
import com.recycllux.pelanode.repo.ContextRepository;
import com.recycllux.pelanode.repo.EventRepository;
import com.recycllux.pelanode.repo.MissionRepository;
import com.recycllux.pelanode.repo.ObservationRepository;
import com.recycllux.pelanode.repo.TrackRepository;
import com.recycllux.pelanode.web.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Step B-16. Mission summary and the four export formats (P0-13 section 11).
 * Every export carries a simulation note built from the data itself, so it can
 * never claim something is real that is not.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final List<String> CSV_COLUMNS = List.of("event_id", "captured_at", "lat", "lon", "perspective",
            "event_type", "confidence", "model_version", "inference_origin", "attention", "review_status",
            "current_label", "reviewer", "reviewed_at", "context_summary", "data_origin", "evidence_uri");

    private final MissionRepository missions;
    private final TrackRepository track;
    private final EventRepository events;
    private final ObservationRepository observations;
    private final ContextRepository context;
    private final EventViews views;
    private final PelanodeProperties props;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ReportService(MissionRepository missions, TrackRepository track, EventRepository events,
                         ObservationRepository observations, ContextRepository context, EventViews views,
                         PelanodeProperties props, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.missions = missions;
        this.track = track;
        this.events = events;
        this.observations = observations;
        this.context = context;
        this.views = views;
        this.props = props;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * The P0-13 summary. A few extra fields (name, counts, path_length_km, model_versions)
     * are added for the UI's Reports screen; additive fields stay in /api/v1.
     */
    public Map<String, Object> summary(String missionId) {
        Mission m = missions.findMission(missionId).orElseThrow(() -> ApiException.notFound("Mission", missionId));
        List<EventRecord> list = events.findByMission(missionId, null);
        List<TrackPoint> points = track.findByMission(missionId, null);
        double km = Math.round(pathKm(points) * 100) / 100.0;

        Map<String, Long> byType = new TreeMap<>(list.stream()
                .collect(Collectors.groupingBy(EventRecord::eventType, Collectors.counting())));
        Map<String, Long> byStatus = new TreeMap<>(list.stream()
                .collect(Collectors.groupingBy(EventRecord::reviewStatus, Collectors.counting())));
        long open = list.stream()
                .filter(e -> "review_required".equals(e.attention()) && "unreviewed".equals(e.reviewStatus())).count();
        long reviewed = list.stream().filter(e -> !"unreviewed".equals(e.reviewStatus())).count();
        Long obsCount = jdbc.queryForObject("SELECT COUNT(*) FROM observation WHERE mission_id = ?", Long.class,
                missionId);

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("mission_id", missionId);
        s.put("name", m.name());
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("start", m.plannedStart());
        window.put("end", m.plannedEnd());
        s.put("window", window);
        s.put("distance_km", km);
        s.put("path_length_km", km);
        s.put("observations", obsCount);
        s.put("events_total", list.size());
        s.put("events_by_type", byType);
        s.put("events_by_review_status", byStatus);
        s.put("review_required_open", open);
        s.put("reviewed", reviewed);
        s.put("model_versions", modelVersions(list));
        s.put("context_datasets", contextDatasets(list));
        s.put("data_origin", m.dataOrigin());
        s.put("simulation_note", simulationNote(list, points));
        s.put("generated_at", Instant.now());
        s.put("schema_version", "1.0");
        return s;
    }

    /** Writes one export file and records it; each call writes a new file (not idempotent). */
    public Map<String, Object> export(String missionId, String format) {
        Map<String, Object> summary = summary(missionId);
        String stamp = JsonConfig.UTC_MILLIS.format(Instant.now()).replaceAll("[-:.]", "");
        String ext = switch (format) {
            case "csv" -> "csv";
            case "geojson" -> "geojson";
            case "json" -> "json";
            case "html" -> "html";
            default -> throw ApiException.invalid("format must be csv, geojson, json or html");
        };
        String fileName = "%s_%s.%s".formatted(missionId, stamp, ext);
        String content = switch (format) {
            case "csv" -> csv(missionId);
            case "geojson" -> json(geojson(missionId));
            case "json" -> json(fullPackage(missionId, summary));
            default -> html(missionId, summary);
        };
        Path dir = Path.of(props.exportDir());
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM report", Long.class);
        String reportId = "RP-%06d".formatted((n == null ? 0 : n) + 1);
        Instant now = Instant.now();
        String uri = "/exports/" + fileName;
        jdbc.update("""
                INSERT INTO report (report_id, mission_id, format, file_uri, summary, simulation_note, generated_at)
                VALUES (?,?,?,?,?,?,?)""", reportId, missionId, format, uri, json(summary),
                summary.get("simulation_note"), java.sql.Timestamp.from(now));
        log.info("EXPORT_WRITTEN mission={} format={} file={}", missionId, format, uri);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("report_id", reportId);
        report.put("mission_id", missionId);
        report.put("format", format);
        report.put("file_uri", uri);
        report.put("summary", summary);
        report.put("simulation_note", summary.get("simulation_note"));
        report.put("generated_at", now);
        return report;
    }

    // ---------------------------------------------------------------- formats

    private List<Map<String, Object>> rows(String missionId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EventRecord e : events.findByMission(missionId, null)) {
            Map<String, Object> full = views.full(e.eventId());
            @SuppressWarnings("unchecked") Map<String, Object> mo = (Map<String, Object>) full.get("model_output");
            @SuppressWarnings("unchecked") Map<String, Object> rv = (Map<String, Object>) full.get("review");
            @SuppressWarnings("unchecked") Map<String, Object> ob = (Map<String, Object>) full.get("observation");
            @SuppressWarnings("unchecked") List<Map<String, Object>> ctx = (List<Map<String, Object>>) full.get("context");
            @SuppressWarnings("unchecked") Map<String, Object> ev = (Map<String, Object>) ob.get("evidence");
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("event_id", e.eventId());
            r.put("captured_at", e.capturedAt());
            r.put("lat", e.lat());
            r.put("lon", e.lon());
            r.put("perspective", e.perspective());
            r.put("event_type", e.eventType());
            r.put("confidence", mo.get("confidence"));
            r.put("model_version", mo.get("model_version"));
            r.put("inference_origin", mo.get("inference_origin"));
            r.put("attention", e.attention());
            r.put("review_status", e.reviewStatus());
            r.put("current_label", e.currentLabel());
            r.put("reviewer", rv.get("by"));
            r.put("reviewed_at", rv.get("at"));
            r.put("context_summary", ctx.stream().map(c -> "%s %s %s at %s km, %s min (%s, coincides with)".formatted(
                    c.get("variable"), c.get("value"), c.get("unit"), c.get("distance_km"), c.get("time_offset_min"),
                    c.get("provenance"))).collect(Collectors.joining("; ")));
            r.put("data_origin", e.dataOrigin());
            r.put("evidence_uri", ev.get("frame_uri"));
            rows.add(r);
        }
        return rows;
    }

    private String csv(String missionId) {
        StringBuilder sb = new StringBuilder(String.join(",", CSV_COLUMNS)).append('\n');
        for (Map<String, Object> r : rows(missionId)) {
            sb.append(CSV_COLUMNS.stream().map(c -> csvCell(r.get(c))).collect(Collectors.joining(","))).append('\n');
        }
        return sb.toString();
    }

    private static String csvCell(Object v) {
        if (v == null) {
            return "";
        }
        String s = v instanceof Instant i ? JsonConfig.UTC_MILLIS.format(i) : v.toString();
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private Map<String, Object> geojson(String missionId) {
        List<TrackPoint> points = track.findByMission(missionId, null);
        Mission m = missions.findMission(missionId).orElseThrow();
        List<Object> features = new ArrayList<>();
        features.add(feature(Map.of("type", "LineString",
                "coordinates", points.stream().map(p -> List.of(p.lon(), p.lat())).toList()),
                Map.of("kind", "track", "data_origin", m.dataOrigin())));
        for (Map<String, Object> r : rows(missionId)) {
            if (r.get("lat") != null) {
                features.add(feature(Map.of("type", "Point", "coordinates", List.of(r.get("lon"), r.get("lat"))), r));
            }
        }
        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("features", features);
        return fc;
    }

    private static Map<String, Object> feature(Object geometry, Map<String, Object> properties) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("type", "Feature");
        f.put("geometry", geometry);
        f.put("properties", properties);
        return f;
    }

    private Map<String, Object> fullPackage(String missionId, Map<String, Object> summary) {
        Mission m = missions.findMission(missionId).orElseThrow();
        List<EventRecord> list = events.findByMission(missionId, null);
        List<ReviewAction> reviews = list.stream().flatMap(e -> events.findReviews(e.eventId()).stream()).toList();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("mission", m);
        p.put("node", missions.findNode(m.nodeId()).orElse(null));
        p.put("track", track.findByMission(missionId, null));
        p.put("events", list.stream().map(e -> views.full(e.eventId())).toList());
        p.put("context_records", context.findRecords(m.plannedStart().minusSeconds(86400),
                m.plannedEnd().plusSeconds(86400), null));
        p.put("reviews", reviews);
        p.put("summary", summary);
        p.put("simulation_note", summary.get("simulation_note"));
        p.put("generated_at", Instant.now());
        p.put("schema_version", "1.0");
        return p;
    }

    private String html(String missionId, Map<String, Object> s) {
        StringBuilder rowsHtml = new StringBuilder();
        for (Map<String, Object> r : rows(missionId)) {
            rowsHtml.append("<tr><td>").append(esc(r.get("event_id"))).append("</td><td>")
                    .append(esc(r.get("captured_at"))).append("</td><td>").append(esc(r.get("perspective")))
                    .append("</td><td>").append(esc(r.get("event_type"))).append("</td><td>")
                    .append(esc(r.get("confidence"))).append(" (").append(esc(r.get("inference_origin")))
                    .append(")</td><td>").append(esc(r.get("review_status")))
                    .append(r.get("current_label") == null ? "" : " → " + esc(r.get("current_label")))
                    .append("</td><td>").append(esc(r.get("context_summary"))).append("</td><td>")
                    .append(r.get("evidence_uri") == null ? "missing"
                            : "<img src=\"" + esc(r.get("evidence_uri")) + "\" alt=\"evidence\" width=\"160\">")
                    .append("</td></tr>\n");
        }
        @SuppressWarnings("unchecked") Map<String, Object> window = (Map<String, Object>) s.get("window");
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><title>PelaNode mission report %1$s</title>
                <style>
                 body{font-family:Inter,Arial,sans-serif;color:#0b2545;margin:32px}
                 h1{margin:0 0 4px} .badge{display:inline-block;background:#f4a261;color:#0b2545;padding:2px 8px;
                 border-radius:4px;font-weight:700} table{border-collapse:collapse;width:100%%;margin-top:16px}
                 th,td{border:1px solid #c8d3e0;padding:6px;font-size:13px;vertical-align:top;text-align:left}
                 footer{margin-top:24px;font-size:12px;border-top:1px solid #c8d3e0;padding-top:8px}
                </style></head><body>
                <h1>Mission report %1$s</h1>
                <p><span class="badge">SIMULATED</span> %2$s · %3$s to %4$s · %5$s km · %6$s events,
                 %7$s reviewed, %8$s still need review</p>
                <table><thead><tr><th>Event</th><th>Time (UTC)</th><th>Perspective</th><th>Class (model output)</th>
                <th>Confidence</th><th>Human review</th><th>Associated context (not cause)</th><th>Evidence</th></tr></thead>
                <tbody>
                %9$s</tbody></table>
                <footer><strong>Provenance.</strong> %10$s Model versions: %11$s. Generated %12$s.
                 Context values coincide with events in time and place; they do not explain or cause them.</footer>
                </body></html>
                """.formatted(esc(missionId), esc(s.get("name")), esc(window.get("start")), esc(window.get("end")),
                esc(s.get("distance_km")), esc(s.get("events_total")), esc(s.get("reviewed")),
                esc(s.get("review_required_open")), rowsHtml, esc(s.get("simulation_note")),
                esc(s.get("model_versions")), esc(s.get("generated_at")));
    }

    // ---------------------------------------------------------------- helpers

    private static double pathKm(List<TrackPoint> points) {
        double km = 0;
        for (int i = 1; i < points.size(); i++) {
            TrackPoint a = points.get(i - 1);
            TrackPoint b = points.get(i);
            km += Geo.km(a.lat(), a.lon(), b.lat(), b.lon());
        }
        return km;
    }

    private List<String> modelVersions(List<EventRecord> list) {
        Set<String> versions = new LinkedHashSet<>();
        list.forEach(e -> observations.findModelOutput(e.modelOutputId())
                .ifPresent(m -> versions.add(m.modelId() + " " + m.modelVersion() + " (" + m.inferenceOrigin() + ")")));
        return List.copyOf(versions);
    }

    private List<Map<String, Object>> contextDatasets(List<EventRecord> list) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (EventRecord e : list) {
            for (var ec : events.findContext(e.eventId())) {
                context.findRecord(ec.contextRecordId()).ifPresent(r -> out.computeIfAbsent(r.datasetId(), k -> {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("dataset_id", r.datasetId());
                    d.put("provider", r.provider());
                    d.put("provenance", r.provenance());
                    return d;
                }));
            }
        }
        return List.copyOf(out.values());
    }

    /** Names every simulated or replayed layer, derived from the stored data. */
    private String simulationNote(List<EventRecord> list, List<TrackPoint> points) {
        List<String> parts = new ArrayList<>();
        Set<String> trackOrigins = points.stream().map(TrackPoint::dataOrigin).collect(Collectors.toSet());
        if (!trackOrigins.isEmpty()) {
            parts.add("Track is " + String.join("/", trackOrigins));
        }
        Long placeholders = jdbc.queryForObject("SELECT COUNT(*) FROM media_asset WHERE provenance = 'placeholder'",
                Long.class);
        if (placeholders != null && placeholders > 0) {
            parts.add("media are placeholders, not real footage");
        }
        Set<String> inference = new LinkedHashSet<>();
        list.forEach(e -> observations.findModelOutput(e.modelOutputId()).ifPresent(m -> inference.add(m.inferenceOrigin())));
        if (inference.contains("simulated")) {
            parts.add("inference is simulated (hand-authored scores)");
        } else if (inference.contains("precomputed")) {
            parts.add("inference is precomputed on recorded media");
        }
        Set<String> ctx = new LinkedHashSet<>();
        contextDatasets(list).forEach(d -> ctx.add((String) d.get("provenance")));
        if (ctx.contains("simulated")) {
            parts.add("hydrological context is simulated");
        }
        if (ctx.contains("organiser")) {
            parts.add("some context is organiser data");
        }
        parts.add("device status is simulated");
        return String.join("; ", parts) + ".";
    }

    private String json(Object value) {
        try {
            return mapper.copy().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String esc(Object v) {
        if (v == null) {
            return "";
        }
        String s = v instanceof Instant i ? JsonConfig.UTC_MILLIS.format(i) : v.toString();
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

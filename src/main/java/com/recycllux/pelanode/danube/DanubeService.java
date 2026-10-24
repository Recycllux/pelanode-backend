package com.recycllux.pelanode.danube;

import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.repo.MissionRepository;
import com.recycllux.pelanode.scenario.IskarData;
import com.recycllux.pelanode.scenario.ScenarioTimeline;
import com.recycllux.pelanode.service.Geo;
import com.recycllux.pelanode.web.ApiException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DanubeService {

    static final double LOW_PERCENTILE = 10;
    static final double HIGH_PERCENTILE = 90;
    static final double RAPID_CHANGE_CM = 30;
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    static final List<String> WARM_STATIONS = List.of("GIURGIU", "RUSE_BRIDGE");
    static final String PROVENANCE = "organiser";
    static final String SOURCE = "EIT Water Hackathon organiser data: Danube hydrological bulletin and Bulgarian "
            + "automatic gauges, 2021-06-06 to 2021-11-09";

    private final DanubeData data;
    private final LevelForecaster forecaster;
    private final MissionRepository missions;
    private final PropagationAnalyzer propagation;
    private final TemperatureAnomalyDetector anomalies;
    private final IskarData iskar;

    static final double DEFAULT_MAX_KM = 320;

    public record Thresholds(Double lowCm, Double highCm, String basis) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!data.loaded()) {
            return;
        }
        Thread t = new Thread(() -> WARM_STATIONS.stream()
                .filter(forecaster::forecastable)
                .forEach(forecaster::hindcast), "danube-warmup");
        t.setDaemon(true);
        t.start();
    }

    public Map<String, Object> stations() {
        requireData();
        List<Map<String, Object>> out = new ArrayList<>();
        for (DanubeStation s : data.stations()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("station", s);
            m.put("coverage", round(data.coverage(s.stationId()), 2));
            m.put("forecastable", forecaster.forecastable(s.stationId()));
            m.put("thresholds", thresholds(s.stationId()));
            double tc = temperatureCoverage(s.stationId());
            m.put("temperature_coverage", round(tc, 2));
            m.put("has_temperature", tc > 0);
            m.put("chain", PropagationAnalyzer.chain(s));
            m.put("in_propagation_model", PropagationAnalyzer.inModel(s));
            out.add(m);
        }
        Map<String, Object> view = header();
        view.put("stations", out);
        view.put("contextual_sources", contextualSources());
        return view;
    }

    public Map<String, Object> lags(String source, String minClass, Double maxKm) {
        requireData();
        String min = minClass == null ? (source == null ? "C" : "B") : minClass.toUpperCase(Locale.ROOT);
        if (!List.of("A", "B", "C").contains(min)) {
            throw ApiException.invalid("min_class must be A, B or C");
        }
        Map<String, Object> view = header();
        view.put("provenance", "derived_from_organiser");
        view.put("period", period());
        view.put("method", propagation.method());
        if (source != null) {
            DanubeStation s = station(source);
            if (!PropagationAnalyzer.inModel(s)) {
                throw ApiException.invalid(s.stationId() + " is not on a lower-Danube chain (below the Iron Gates, km "
                        + (int) PropagationAnalyzer.IRON_GATES_KM + ")");
            }
            double km = maxKm == null ? DEFAULT_MAX_KM : maxKm;
            List<PropagationAnalyzer.Edge> edges = propagation.downstream(s.stationId(), min, km);
            view.put("source_station", s);
            view.put("chain", PropagationAnalyzer.chain(s));
            view.put("max_distance_km", km);
            view.put("edges", edges);
            view.put("downstream_windows", windows(edges));
        } else {
            Map<String, Object> chains = new LinkedHashMap<>();
            for (String c : List.of(PropagationAnalyzer.RO_CHAIN, PropagationAnalyzer.BG_CHAIN)) {
                chains.put(c, propagation.chainStations(c).stream().map(DanubeStation::stationId).toList());
            }
            view.put("chains", chains);
            view.put("edges", propagation.adjacent(min));
        }
        view.put("label", PropagationAnalyzer.LABEL);
        view.put("note", PropagationAnalyzer.NOTE);
        return view;
    }

    /** Operational windows from the nearest gauge, for /point and event detail. */
    public List<Map<String, Object>> downstreamWindows(String stationId) {
        DanubeStation s = data.station(stationId).orElse(null);
        if (s == null || !PropagationAnalyzer.inModel(s)) {
            return List.of();
        }
        return windows(propagation.downstream(s.stationId(), "B", DEFAULT_MAX_KM));
    }

    public Map<String, Object> anomalies(String stationId, String from, String to, Double threshold,
                                         boolean includeOneSided, boolean includeInfluenced) {
        requireData();
        double th = threshold == null ? TemperatureAnomalyDetector.DEFAULT_THRESHOLD : threshold;
        if (th <= 0) {
            throw ApiException.invalid("threshold must be positive");
        }
        String id = stationId == null ? null : station(stationId).stationId();
        LocalDate a = from == null ? data.firstDate() : clampDate(from);
        LocalDate b = to == null ? data.lastDate() : clampDate(to);
        TemperatureAnomalyDetector.Result r = anomalies.detect(th);
        List<TemperatureAnomalyDetector.StationStats> stats = r.stations().stream()
                .filter(st -> id == null || st.stationId().equals(id))
                .filter(st -> includeOneSided || "two_sided".equals(st.referenceType()) || st.stationId().equals(id))
                .toList();
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (TemperatureAnomalyDetector.Candidate c : r.candidates()) {
            if ((id != null && !c.stationId().equals(id)) || c.date().isBefore(a) || c.date().isAfter(b)
                    || (!includeOneSided && "one_sided".equals(c.referenceType()) && !c.stationId().equals(id))
                    || (!includeInfluenced && "neighbour_influenced".equals(c.role()))) {
                continue;
            }
            candidates.add(candidate(c, th));
        }
        Map<String, Object> method = new LinkedHashMap<>();
        method.put("id", TemperatureAnomalyDetector.METHOD);
        method.put("threshold", th);
        method.put("threshold_status", "proposed_demo_tuning");
        method.put("sigma", "1.4826 x MAD of the station's residual");
        Map<String, Object> view = header();
        view.put("provenance", "derived_from_organiser");
        view.put("period", period());
        view.put("method", method);
        view.put("stations", stats);
        view.put("candidates", candidates);
        view.put("label", TemperatureAnomalyDetector.LABEL);
        view.put("note", TemperatureAnomalyDetector.NOTE);
        return view;
    }

    public Map<String, Object> profile(String river, String date) {
        requireData();
        LocalDate d = date == null ? data.lastDate() : clampDate(date);
        int i = data.index(d);
        String r = river == null ? "Danube" : river;
        List<Map<String, Object>> points = new ArrayList<>();
        data.stations().stream()
                .filter(s -> r.equalsIgnoreCase(s.river()) && s.riverKm() != null)
                .sorted(Comparator.comparing(DanubeStation::riverKm).reversed())
                .forEach(s -> {
                    double[] lv = data.levels(s.stationId());
                    if (Double.isNaN(lv[i])) {
                        return;
                    }
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("station_id", s.stationId());
                    p.put("name", s.name());
                    p.put("country", s.country());
                    p.put("river_km", s.riverKm());
                    p.put("level_cm", lv[i]);
                    p.put("change_24h_cm", i > 0 && !Double.isNaN(lv[i - 1]) ? lv[i] - lv[i - 1] : null);
                    p.put("anomaly_cm", round(lv[i] - median(s.stationId()), 0));
                    p.put("water_temp_c", nanToNull(data.temps(s.stationId())[i]));
                    p.put("lat", s.lat());
                    p.put("lon", s.lon());
                    points.add(p);
                });
        Map<String, Object> view = header();
        view.put("river", r);
        view.put("date", d);
        view.put("scenario_date", ScenarioTimeline.toScenario(d));
        view.put("points", points);
        return view;
    }

    public Map<String, Object> series(String stationId, String from, String to) {
        requireData();
        DanubeStation s = station(stationId);
        int a = from == null ? 0 : data.index(clampDate(from));
        int b = to == null ? data.days() - 1 : data.index(clampDate(to));
        double[] lv = data.levels(s.stationId());
        double[] t = data.temps(s.stationId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = a; i <= b; i++) {
            if (Double.isNaN(lv[i]) && Double.isNaN(t[i])) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", data.date(i));
            row.put("scenario_date", ScenarioTimeline.toScenario(data.date(i)));
            row.put("level_cm", nanToNull(lv[i]));
            row.put("water_temp_c", nanToNull(t[i]));
            rows.add(row);
        }
        Map<String, Object> view = header();
        view.put("station", s);
        view.put("thresholds", thresholds(s.stationId()));
        view.put("series", rows);
        return view;
    }

    public Map<String, Object> forecast(String stationId, String missionId, String asOf, Integer horizon) {
        requireData();
        DanubeStation s = stationId != null ? station(stationId) : nearestForecastable(missionId);
        if (!forecaster.forecastable(s.stationId())) {
            throw ApiException.invalid("no forecast model for station " + s.stationId()
                    + " (needs a Danube station with upstream gauges and 80% data coverage)");
        }
        int h = horizon == null ? LevelForecaster.MAX_HORIZON : Math.max(1, Math.min(LevelForecaster.MAX_HORIZON, horizon));
        LocalDate d = asOf == null ? data.lastDate() : clampDate(asOf);
        int a = data.index(d);
        double[] y = data.filledLevels(s.stationId());
        double[] raw = data.levels(s.stationId());
        if (Double.isNaN(y[a])) {
            throw ApiException.invalid("no observed level at " + s.stationId() + " on " + d);
        }
        Thresholds th = thresholds(s.stationId());

        List<Map<String, Object>> path = new ArrayList<>();
        LevelForecaster.Model last = null;
        for (int k = 1; k <= h; k++) {
            LevelForecaster.Model model = forecaster.fit(s.stationId(), k, a);
            Double dy = forecaster.predictChange(model, a);
            if (dy == null) {
                continue;
            }
            last = model;
            double level = y[a] + dy;
            Double band = forecaster.band(s.stationId(), k, a);
            int target = a + k;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", d.plusDays(k));
            p.put("horizon_days", k);
            p.put("level_cm", round(level, 0));
            p.put("band_low_cm", band == null ? null : round(level - band, 0));
            p.put("band_high_cm", band == null ? null : round(level + band, 0));
            p.put("observed_cm", target < data.days() ? nanToNull(raw[target]) : null);
            path.add(p);
        }
        if (path.isEmpty()) {
            throw ApiException.invalid("not enough history before " + d + " to train a forecast (needs "
                    + LevelForecaster.MIN_TRAINING_ROWS + " days)");
        }

        Map<String, Object> view = header();
        view.put("station", s);
        view.put("as_of", d);
        view.put("observed_level_cm", y[a]);
        view.put("thresholds", th);
        view.put("forecast", path);
        view.put("alerts", alerts(y[a], path, th));
        view.put("drivers", drivers(last, a));
        view.put("model", modelInfo(s.stationId(), last));
        view.put("recommendation", recommendation(view));
        if (missionId != null) {
            view.put("mission_id", missionId);
        }
        return view;
    }

    public Map<String, Object> backtest(String stationId) {
        requireData();
        DanubeStation s = station(stationId);
        if (!forecaster.forecastable(s.stationId())) {
            throw ApiException.invalid("no forecast model for station " + s.stationId());
        }
        double[] y = data.filledLevels(s.stationId());
        double[][] p = forecaster.hindcast(s.stationId()).predicted();
        List<Map<String, Object>> skill = new ArrayList<>();
        for (int h = 1; h <= LevelForecaster.MAX_HORIZON; h++) {
            double model = 0;
            double persistence = 0;
            int n = 0;
            for (int a = 0; a + h < data.days(); a++) {
                if (!Double.isNaN(p[h][a]) && !Double.isNaN(y[a + h])) {
                    model += Math.abs(p[h][a] - y[a + h]);
                    persistence += Math.abs(y[a] - y[a + h]);
                    n++;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("horizon_days", h);
            row.put("n", n);
            row.put("mae_model_cm", n == 0 ? null : round(model / n, 1));
            row.put("mae_persistence_cm", n == 0 ? null : round(persistence / n, 1));
            row.put("improvement_pct", n == 0 || persistence == 0 ? null : round(100 * (1 - model / persistence), 0));
            skill.add(row);
        }

        Thresholds th = thresholds(s.stationId());
        List<Map<String, Object>> crossings = new ArrayList<>();
        for (int i = 1; i < data.days(); i++) {
            String type = null;
            if (y[i - 1] > th.lowCm() && y[i] <= th.lowCm()) {
                type = "low_water";
            } else if (y[i - 1] < th.highCm() && y[i] >= th.highCm()) {
                type = "high_water";
            }
            if (type == null) {
                continue;
            }
            Integer lead = null;
            for (int k = LevelForecaster.MAX_HORIZON; k >= 1 && lead == null; k--) {
                int issued = i - k;
                double v = issued >= 0 ? p[k][issued] : Double.NaN;
                if (!Double.isNaN(v) && ("low_water".equals(type) ? v <= th.lowCm() : v >= th.highCm())) {
                    lead = k;
                }
            }
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("type", type);
            c.put("observed_on", data.date(i));
            c.put("level_cm", y[i]);
            c.put("warned_days_ahead", lead);
            crossings.add(c);
        }

        Map<String, Object> view = header();
        view.put("station", s);
        view.put("method", "walk-forward: each forecast is trained only on data available on its issue date");
        view.put("thresholds", th);
        view.put("skill", skill);
        view.put("threshold_crossings", crossings);
        return view;
    }

    Thresholds thresholds(String stationId) {
        double[] v = Arrays.stream(data.levels(stationId)).filter(x -> !Double.isNaN(x)).sorted().toArray();
        if (v.length < 20) {
            return new Thresholds(null, null, "not enough data");
        }
        return new Thresholds(percentile(v, LOW_PERCENTILE), percentile(v, HIGH_PERCENTILE),
                "demo thresholds: P10 and P90 of the station's 2021 record; replace with the navigation "
                        + "(low-water) and flood-alert levels of the authority");
    }

    private List<Map<String, Object>> alerts(double now, List<Map<String, Object>> path, Thresholds th) {
        List<Map<String, Object>> out = new ArrayList<>();
        Optional<Map<String, Object>> low = path.stream()
                .filter(p -> th.lowCm() != null && (double) p.get("level_cm") <= th.lowCm()).findFirst();
        Optional<Map<String, Object>> high = path.stream()
                .filter(p -> th.highCm() != null && (double) p.get("level_cm") >= th.highCm()).findFirst();
        low.ifPresent(p -> out.add(alert("low_water", p, "Level forecast at or below " + th.lowCm().intValue()
                + " cm: draught limits likely for barges")));
        high.ifPresent(p -> out.add(alert("high_water", p, "Level forecast at or above " + th.highCm().intValue()
                + " cm: high-water pulse arriving")));
        Map<String, Object> end = path.get(path.size() - 1);
        double change = (double) end.get("level_cm") - now;
        if (Math.abs(change) >= RAPID_CHANGE_CM) {
            out.add(alert(change > 0 ? "rapid_rise" : "rapid_fall", end,
                    "%+.0f cm in %d days".formatted(change, (int) end.get("horizon_days"))));
        }
        return out;
    }

    private static Map<String, Object> alert(String type, Map<String, Object> point, String message) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", type);
        a.put("date", point.get("date"));
        a.put("eta_days", point.get("horizon_days"));
        a.put("level_cm", point.get("level_cm"));
        a.put("message", message);
        return a;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> recommendation(Map<String, Object> view) {
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) view.get("alerts");
        DanubeStation s = (DanubeStation) view.get("station");
        Map<String, Object> r = new LinkedHashMap<>();
        Optional<Map<String, Object>> low = alerts.stream().filter(a -> a.get("type").equals("low_water")).findFirst();
        Optional<Map<String, Object>> rise = alerts.stream()
                .filter(a -> a.get("type").equals("high_water") || a.get("type").equals("rapid_rise")).findFirst();
        if (low.isPresent()) {
            r.put("action", "survey_shallows");
            r.put("by", low.get().get("date"));
            r.put("text", "Deploy PelaNode on the " + s.name() + " reach before " + day(low.get().get("date"))
                    + " to map shoals and stranded debris in the fairway while the river falls.");
        } else if (rise.isPresent()) {
            r.put("action", "survey_behind_crest");
            r.put("by", rise.get().get("date"));
            r.put("text", "Deploy PelaNode behind the high-water pulse reaching " + s.name() + " around "
                    + day(rise.get().get("date")) + " to record debris and turbidity the wave carries.");
        } else {
            r.put("action", "routine");
            r.put("by", null);
            r.put("text", "No trigger in the forecast window: keep routine monitoring.");
        }
        return r;
    }

    private static String day(Object date) {
        return ((LocalDate) date).format(DAY);
    }

    private List<Map<String, Object>> drivers(LevelForecaster.Model model, int day) {
        if (model == null) {
            return List.of();
        }
        double[] x = forecaster.row(model.features(), day);
        return model.contributions(x).entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Double> e) -> Math.abs(e.getValue())).reversed())
                .limit(3)
                .map(e -> {
                    DanubeStation st = data.station(e.getKey()).orElseThrow();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("station_id", st.stationId());
                    m.put("name", st.name());
                    m.put("river_km", st.riverKm());
                    m.put("contribution_cm", round(e.getValue(), 1));
                    return m;
                })
                .toList();
    }

    private Map<String, Object> modelInfo(String stationId, LevelForecaster.Model model) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("method", "ridge regression on upstream level changes (1 and 3 days), refit on data up to the as-of date");
        m.put("predictors", forecaster.predictors(stationId));
        m.put("training_rows", model == null ? 0 : model.rows());
        m.put("training_from", model == null ? null : data.date(model.trainFrom()));
        m.put("training_to", model == null ? null : data.date(model.trainTo()));
        m.put("band", "about 80%: 1.6 x the walk-forward mean absolute error verified before the as-of date");
        return m;
    }

    private DanubeStation nearestForecastable(String missionId) {
        if (missionId == null) {
            throw ApiException.invalid("station or mission_id is required");
        }
        Mission mission = missions.findMission(missionId).orElseThrow(() -> ApiException.notFound("Mission", missionId));
        List<Double> bbox = mission.areaBbox();
        if (bbox == null || bbox.size() < 4) {
            throw ApiException.invalid("mission " + missionId + " has no area_bbox");
        }
        double lon = (bbox.get(0) + bbox.get(2)) / 2;
        double lat = (bbox.get(1) + bbox.get(3)) / 2;
        return data.stations().stream()
                .filter(s -> s.lat() != null && forecaster.forecastable(s.stationId()))
                .min(Comparator.comparing(s -> Geo.km(lat, lon, s.lat(), s.lon())))
                .orElseThrow(() -> ApiException.invalid("no forecastable station"));
    }

    private DanubeStation station(String id) {
        return data.station(id).orElseThrow(() -> ApiException.notFound("Danube station", id));
    }

    private LocalDate clampDate(String text) {
        LocalDate d;
        try {
            d = LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException e) {
            throw ApiException.invalid("date must be YYYY-MM-DD: " + text);
        }
        d = ScenarioTimeline.toSource(d, data.firstDate().getYear());
        if (d.isBefore(data.firstDate()) || d.isAfter(data.lastDate())) {
            throw ApiException.invalid("date outside the data window " + data.firstDate() + " to " + data.lastDate());
        }
        return d;
    }

    private Map<String, Object> candidate(TemperatureAnomalyDetector.Candidate c, double th) {
        Map<String, Object> obs = new LinkedHashMap<>();
        obs.put("station_id", c.stationId());
        obs.put("date", c.date());
        obs.put("scenario_date", ScenarioTimeline.toScenario(c.date()));
        obs.put("water_temp_c", c.observedC());
        obs.put("provenance", PROVENANCE);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("method", TemperatureAnomalyDetector.METHOD);
        model.put("neighbours", c.neighbours());
        model.put("expected_temp_c", c.expectedC());
        model.put("residual_c", c.residualC());
        model.put("robust_z", c.robustZ());
        model.put("threshold", th);
        model.put("threshold_status", "proposed_demo_tuning");
        model.put("reference_type", c.referenceType());
        model.put("role", c.role());
        model.put("influenced_by", c.influencedBy());
        model.put("influenced", c.influenced());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("event_type", "temperature_candidate_anomaly");
        m.put("event_id", "tca_" + c.stationId() + "_" + c.date());
        m.put("observation", obs);
        m.put("model_output", model);
        m.put("data_quality", Map.of("station_coverage", round(temperatureCoverage(c.stationId()), 2)));
        m.put("attention", "review_required");
        m.put("label", TemperatureAnomalyDetector.LABEL);
        m.put("review", Map.of("status", "unreviewed"));
        m.put("source_attribution", null);
        return m;
    }

    private static List<Map<String, Object>> windows(List<PropagationAnalyzer.Edge> edges) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PropagationAnalyzer.Edge e : edges) {
            if (!e.operational()) {
                continue;
            }
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("station_id", e.toStation());
            w.put("distance_km", e.distanceKm());
            w.put("window_days", e.monitoringWindowDays());
            w.put("r_delta_h", e.rDeltaH());
            w.put("quality_class", e.qualityClass());
            w.put("label", PropagationAnalyzer.LABEL);
            out.add(w);
        }
        return out;
    }

    private List<Map<String, Object>> contextualSources() {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!iskar.loaded()) {
            return out;
        }
        for (IskarData.Station s : iskar.stations()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("station", s);
            m.put("variable", "discharge_m3s");
            m.put("data_from", iskar.firstDate());
            m.put("data_to", iskar.lastDate());
            m.put("native_overlap_with_danube_days", 0);
            m.put("compatibility_state", "SCENARIO-ALIGNED");
            m.put("label", "No native overlap with the Danube dataset (2021); scenario-aligned tributary on the 2025 "
                    + "timeline");
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> period() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("from", data.firstDate());
        p.put("to", data.lastDate());
        return p;
    }

    private double temperatureCoverage(String stationId) {
        double[] t = data.temps(stationId);
        return data.days() == 0 ? 0 : Arrays.stream(t).filter(v -> !Double.isNaN(v)).count() / (double) data.days();
    }

    private Map<String, Object> header() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provenance", PROVENANCE);
        m.put("source", SOURCE);
        m.put("data_from", data.firstDate());
        m.put("data_to", data.lastDate());
        m.put("scenario", ScenarioTimeline.info(data.firstDate().getYear()));
        return m;
    }

    private void requireData() {
        if (!data.loaded()) {
            throw ApiException.notFound("Danube data pack", "data/danube");
        }
    }

    private double median(String stationId) {
        return percentile(Arrays.stream(data.levels(stationId)).filter(x -> !Double.isNaN(x)).sorted().toArray(), 50);
    }

    static double percentile(double[] sorted, double pct) {
        double pos = pct / 100 * (sorted.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        return round(sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo), 0);
    }

    static double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }

    private static Double nanToNull(double v) {
        return Double.isNaN(v) ? null : v;
    }
}

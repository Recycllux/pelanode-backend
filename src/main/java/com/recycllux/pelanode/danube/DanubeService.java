package com.recycllux.pelanode.danube;

import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.repo.MissionRepository;
import com.recycllux.pelanode.service.Geo;
import com.recycllux.pelanode.web.ApiException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
    static final List<String> WARM_STATIONS = List.of("GIURGIU", "RUSE_BRIDGE");
    static final String PROVENANCE = "organiser";
    static final String SOURCE = "EIT Water Hackathon organiser data: Danube hydrological bulletin and Bulgarian "
            + "automatic gauges, 2021-06-06 to 2021-11-09";

    private final DanubeData data;
    private final LevelForecaster forecaster;
    private final MissionRepository missions;

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
            out.add(m);
        }
        Map<String, Object> view = header();
        view.put("stations", out);
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
            r.put("text", "Deploy PelaNode on the " + s.name() + " reach before " + low.get().get("date")
                    + " to map shoals and stranded debris in the fairway while the river falls.");
        } else if (rise.isPresent()) {
            r.put("action", "survey_behind_crest");
            r.put("by", rise.get().get("date"));
            r.put("text", "Deploy PelaNode behind the high-water pulse reaching " + s.name() + " around "
                    + rise.get().get("date") + " to record debris and turbidity the wave carries.");
        } else {
            r.put("action", "routine");
            r.put("by", null);
            r.put("text", "No trigger in the forecast window: keep routine monitoring.");
        }
        return r;
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
        if (d.isBefore(data.firstDate()) || d.isAfter(data.lastDate())) {
            throw ApiException.invalid("date outside the data window " + data.firstDate() + " to " + data.lastDate());
        }
        return d;
    }

    private Map<String, Object> header() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provenance", PROVENANCE);
        m.put("source", SOURCE);
        m.put("data_from", data.firstDate());
        m.put("data_to", data.lastDate());
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

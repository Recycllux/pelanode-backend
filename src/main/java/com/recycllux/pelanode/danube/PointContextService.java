package com.recycllux.pelanode.danube;

import com.recycllux.pelanode.climate.ClimateGrids;
import com.recycllux.pelanode.scenario.ScenarioTimeline;
import com.recycllux.pelanode.service.Geo;
import com.recycllux.pelanode.web.ApiException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointContextService {

    private final DanubeData data;
    private final DanubeService danube;
    private final LevelForecaster forecaster;
    private final ClimateGrids climate;

    public Map<String, Object> at(Double lat, Double lon, String asOf, Integer month) {
        if (lat == null || lon == null || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            throw ApiException.invalid("lat and lon are required");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lat", lat);
        out.put("lon", lon);
        LocalDate d = null;
        if (data.loaded()) {
            d = asOf == null ? data.lastDate() : parse(asOf);
            out.put("as_of", d);
            out.put("nearest_gauge", gauge(lat, lon, d));
        }
        int m = month != null ? month : d != null ? d.getMonthValue() : 1;
        if (m < 1 || m > 12) {
            throw ApiException.invalid("month must be 1 to 12");
        }
        List<Map<String, Object>> layers = new ArrayList<>();
        for (ClimateGrids.Grid g : climate.grids()) {
            layers.add(cell(g, lat, lon, m));
        }
        out.put("climate", layers);
        out.put("provenance", DanubeService.PROVENANCE);
        out.put("note", "Danube gauges are 2021 organiser data, climate grids are 2024; the climate month is "
                + "matched by calendar month. Gauge positions are approximate.");
        return out;
    }

    private Map<String, Object> gauge(double lat, double lon, LocalDate d) {
        DanubeStation s = data.stations().stream()
                .filter(st -> "Danube".equals(st.river()) && st.lat() != null)
                .min(Comparator.comparing(st -> Geo.km(lat, lon, st.lat(), st.lon())))
                .orElse(null);
        if (s == null) {
            return null;
        }
        int i = data.index(d);
        double level = data.levels(s.stationId())[i];
        double prev = i > 0 ? data.levels(s.stationId())[i - 1] : Double.NaN;
        double temp = data.temps(s.stationId())[i];
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("station", s);
        g.put("distance_km", DanubeService.round(Geo.km(lat, lon, s.lat(), s.lon()), 1));
        g.put("date", d);
        g.put("level_cm", Double.isNaN(level) ? null : level);
        g.put("change_24h_cm", Double.isNaN(level) || Double.isNaN(prev) ? null : level - prev);
        g.put("water_temp_c", Double.isNaN(temp) ? null : temp);
        g.put("thresholds", danube.thresholds(s.stationId()));
        g.put("forecastable", forecaster.forecastable(s.stationId()));
        g.put("downstream_windows", danube.downstreamWindows(s.stationId()));
        g.put("downstream_windows_note", PropagationAnalyzer.NOTE);
        return g;
    }

    private static Map<String, Object> cell(ClimateGrids.Grid g, double lat, double lon, int month) {
        int i = g.nearest(lat, lon);
        String key = g.months().stream().filter(k -> k.endsWith("-%02d".formatted(month))).findFirst().orElse(null);
        int m = key == null ? -1 : g.months().indexOf(key);
        double value = m < 0 ? Double.NaN : g.values()[i][m];
        List<Map<String, Object>> series = new ArrayList<>();
        for (int k = 0; k < g.months().size(); k++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("month", g.months().get(k));
            p.put("value", Double.isNaN(g.values()[i][k]) ? null : DanubeService.round(g.values()[i][k], 3));
            series.add(p);
        }
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("variable", g.variable().id());
        c.put("name", g.variable().name());
        c.put("unit", g.variable().unit());
        c.put("source", g.variable().source());
        c.put("cell_lat", g.lat()[i]);
        c.put("cell_lon", g.lon()[i]);
        c.put("cell_distance_km", DanubeService.round(Geo.km(lat, lon, g.lat()[i], g.lon()[i]), 1));
        c.put("month", key);
        c.put("value", Double.isNaN(value) ? null : DanubeService.round(value, 3));
        c.put("rank_pct", Double.isNaN(value) ? null : DanubeService.round(g.rankPct(m, value), 0));
        c.put("series", series);
        return c;
    }

    private LocalDate parse(String text) {
        try {
            LocalDate d = ScenarioTimeline.toSource(LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text),
                    data.firstDate().getYear());
            if (d.isBefore(data.firstDate()) || d.isAfter(data.lastDate())) {
                throw ApiException.invalid("as_of outside the Danube data window " + data.firstDate() + " to "
                        + data.lastDate());
            }
            return d;
        } catch (DateTimeParseException e) {
            throw ApiException.invalid("as_of must be YYYY-MM-DD: " + text);
        }
    }
}

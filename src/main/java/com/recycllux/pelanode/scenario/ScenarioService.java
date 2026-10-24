package com.recycllux.pelanode.scenario;

import com.recycllux.pelanode.danube.DanubeData;
import com.recycllux.pelanode.danube.DanubeStation;
import com.recycllux.pelanode.danube.PropagationAnalyzer;
import com.recycllux.pelanode.web.ApiException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Common 2025 scenario timeline, dataset compatibility states and the Iskar confluence handoff (P0-19 §4.7, §8.7,
 * §11.4). The Iskar (2025) and Danube (2021) records do not overlap natively; the handoff is a scenario topology link,
 * not measured propagation, and no Iskar–Danube correlation is computed.
 */
@Service
@RequiredArgsConstructor
public class ScenarioService {

    public static final String CONFLUENCE_NODE = "ISKAR_DANUBE_CONFLUENCE";
    public static final double CONFLUENCE_KM = 637;
    public static final double CONFLUENCE_LAT = 43.71;
    public static final double CONFLUENCE_LON = 24.46;
    static final double HANDOFF_MAX_KM = 220;
    static final double TRIGGER_RISE_M3S = 5.0; // PROPOSED — DEMO TUNING (§11.4)
    public static final String HANDOFF_LABEL = "Scenario handoff only. Iskar (2025) and Danube (2021) records do not "
            + "overlap natively; no pollutant travel time or Danube-level impact is inferred.";

    private final DanubeData danube;
    private final IskarData iskar;
    private final PropagationAnalyzer propagation;

    public Map<String, Object> datasets() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(dataset("danube_levels_bg_temperature", "Danube levels + Bulgarian gauge water temperature",
                List.of(2021), window(danube.firstDate(), danube.lastDate()), "DEMONSTRATED",
                "Propagation, monitoring windows, temperature candidates",
                Map.of("temporal", "single source year; analytics in source time",
                        "spatial", "station ID and river km present",
                        "topology", "placed on the Danube station graph",
                        "variable", "level supports propagation; temperature supports anomaly detection",
                        "quality", "Nikopol and Svishtov gaps lower their edge classes",
                        "claim", "estimated hydrological windows; candidate anomalies")));
        out.add(dataset("iskar_discharge", "Iskar daily discharge (Novi Iskar, Orehovitsa)", List.of(2025),
                window(iskar.firstDate(), iskar.lastDate()), "SCENARIO-ALIGNED",
                "Tributary input event + handoff to Danube monitoring windows",
                Map.of("temporal", "native 2025; no native overlap with Danube 2021",
                        "spatial", "station named in the file name only",
                        "topology", "tributary path + confluence scenario node (~km 637)",
                        "variable", "discharge; no Danube discharge to compare with",
                        "quality", "Orehovitsa has no values 4–20 Oct 2025",
                        "claim", "scenario handoff, not measured propagation")));
        out.add(dataset("surface_water_chemistry", "Bulgarian surface-water chemistry (best + weak)",
                List.of(2019, 2020, 2021, 2022, 2023, 2024), null, "CONTEXT ONLY",
                "National/regional distributions; never on the map",
                Map.of("spatial", "no station identity (date + value only)",
                        "claim", "national/regional context, station location unavailable")));
        out.add(dataset("groundwater_chemistry", "Bulgarian groundwater chemistry",
                List.of(2018, 2019, 2020, 2021, 2022, 2023, 2024), null, "CONTEXT ONLY",
                "National/regional distributions; no exchange model",
                Map.of("spatial", "no well identity, no piezometric levels",
                        "claim", "national/regional context only")));
        out.add(dataset("climate_grids", "Monthly precipitation and soil moisture grids", List.of(2024), null,
                "OPTIONAL CONTEXT", "Calendar-month context in /danube/point",
                Map.of("claim", "context, not core evidence")));
        out.add(dataset("pelanode_node", "Physical PelaNode observations, located chemistry, groundwater exchange",
                List.of(), null, "FUTURE", "Not shown as results",
                Map.of("claim", "requires new data or hardware")));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("scenario", ScenarioTimeline.info(danube.loaded() ? danube.firstDate().getYear() : 2021));
        view.put("shared_window", sharedWindow());
        view.put("datasets", out);
        view.put("rule", "Scenario alignment enables integration for the hackathon; it does not convert historical "
                + "records into contemporaneous measurements or prove pollutant transport.");
        return view;
    }

    public Map<String, Object> iskarSeries(String from, String to) {
        requireIskar();
        LocalDate a = from == null ? iskar.firstDate() : date(from, "from");
        LocalDate b = to == null ? iskar.lastDate() : date(to, "to");
        List<Map<String, Object>> stations = new ArrayList<>();
        for (IskarData.Station s : iskar.stations()) {
            NavigableMap<LocalDate, Double> q = iskar.discharge(s.stationId()).subMap(a, true, b, true);
            List<Map<String, Object>> rows = new ArrayList<>();
            q.forEach((d, v) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("date", d);
                row.put("scenario_date", ScenarioTimeline.toScenario(d));
                row.put("discharge_m3s", v);
                rows.add(row);
            });
            long span = b.toEpochDay() - a.toEpochDay() + 1;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("station", s);
            m.put("days_present", rows.size());
            m.put("days_missing", span - rows.size());
            m.put("series", rows);
            stations.add(m);
        }
        Map<String, Object> view = header();
        view.put("from", a);
        view.put("to", b);
        view.put("stations", stations);
        view.put("confluence", confluence());
        view.put("suggested_events", suggestedEvents());
        return view;
    }

    public Map<String, Object> event(String stationId, String scenarioDate) {
        requireIskar();
        IskarData.Station s = iskar.station(stationId == null ? "OREHOVITSA" : stationId)
                .orElseThrow(() -> ApiException.notFound("Iskar station", stationId));
        if (scenarioDate == null) {
            throw ApiException.invalid("scenario_date is required (YYYY-MM-DD on the 2025 scenario timeline)");
        }
        LocalDate d = date(scenarioDate, "scenario_date");
        Map<String, Object> shared = sharedWindow();
        LocalDate from = (LocalDate) shared.get("from");
        LocalDate to = (LocalDate) shared.get("to");
        if (from == null || d.isBefore(from) || d.isAfter(to)) {
            throw ApiException.invalid("scenario_date " + d + " is outside the shared Danube–Iskar scenario window "
                    + from + " to " + to + "; there is no Danube scenario data to hand off to");
        }
        NavigableMap<LocalDate, Double> q = iskar.discharge(s.stationId());
        Double v = q.get(d);
        if (v == null) {
            throw ApiException.invalid("no discharge value at " + s.stationId() + " on " + d + " (data gap)");
        }
        Double prev = q.get(d.minusDays(1));

        Map<String, Object> obs = new LinkedHashMap<>();
        obs.put("tributary", "ISKAR");
        obs.put("station_id", s.stationId());
        obs.putAll(ScenarioTimeline.fields(d, "supplied_dataset", "measured"));
        obs.put("discharge_m3s", round(v, 1));
        obs.put("change_24h_m3s", prev == null ? null : round(v - prev, 1));
        obs.put("provenance", "supplied_dataset");

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("event_type", "tributary_input_event");
        ev.put("event_id", "tie_ISKAR_" + s.stationId() + "_" + d);
        ev.put("observation", obs);
        ev.put("confluence", confluence());
        ev.put("handoff", handoff());
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("source", danube.firstDate() + "/" + danube.lastDate());
        period.put("scenario_assumption", true);
        ev.put("windows_period", period);
        ev.put("claim_scope", "scenario_aligned");
        ev.put("attention", "informational");
        ev.put("label", "Scenario handoff at the Iskar–Danube confluence");
        ev.put("suggested_action", "Prioritise monitoring at the downstream Danube gauges in the indicated windows");
        ev.put("note", HANDOFF_LABEL);
        if ("NOVI_ISKAR".equals(s.stationId())) {
            ev.put("caveat", "Novi Iskar is far upstream (near Sofia); Orehovitsa is the station nearer the mouth.");
        }
        return ev;
    }

    /** Operational windows from the first gauge below the confluence on each lower-Danube chain (§8.7). */
    List<Map<String, Object>> handoff() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String chain : List.of(PropagationAnalyzer.RO_CHAIN, PropagationAnalyzer.BG_CHAIN)) {
            DanubeStation gauge = propagation.chainStations(chain).stream()
                    .filter(st -> st.riverKm() < CONFLUENCE_KM).findFirst().orElse(null);
            if (gauge == null) {
                continue;
            }
            List<Map<String, Object>> windows = new ArrayList<>();
            List<Map<String, Object>> excluded = new ArrayList<>();
            for (PropagationAnalyzer.Edge e : propagation.downstream(gauge.stationId(), "C", HANDOFF_MAX_KM)) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("station_id", e.toStation());
                w.put("distance_km", e.distanceKm());
                w.put("quality_class", e.qualityClass());
                w.put("r_delta_h", e.rDeltaH());
                w.put("n_common_observations", e.nCommonObservations());
                if (e.operational()) {
                    w.put("window_days", e.monitoringWindowDays());
                    windows.add(w);
                } else {
                    w.put("reason", e.reason());
                    excluded.add(w);
                }
            }
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("chain", chain);
            h.put("gauge", gauge.stationId());
            h.put("gauge_name", gauge.name());
            h.put("km_below_confluence", round(CONFLUENCE_KM - gauge.riverKm(), 1));
            h.put("downstream_windows", windows);
            h.put("excluded", excluded);
            out.add(h);
        }
        return out;
    }

    /** Orehovitsa days inside the shared window whose rise over the previous day meets the demo trigger. */
    List<Map<String, Object>> suggestedEvents() {
        Map<String, Object> shared = sharedWindow();
        LocalDate from = (LocalDate) shared.get("from");
        LocalDate to = (LocalDate) shared.get("to");
        List<Map<String, Object>> out = new ArrayList<>();
        if (from == null) {
            return out;
        }
        NavigableMap<LocalDate, Double> q = iskar.discharge("OREHOVITSA");
        q.subMap(from, true, to, true).forEach((d, v) -> {
            Double prev = q.get(d.minusDays(1));
            if (prev != null && v - prev >= TRIGGER_RISE_M3S) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("station_id", "OREHOVITSA");
                m.put("scenario_date", d);
                m.put("discharge_m3s", round(v, 1));
                m.put("change_24h_m3s", round(v - prev, 1));
                out.add(m);
            }
        });
        return out;
    }

    /** Overlap of the Danube window (on the scenario timeline) with the Iskar window. */
    public Map<String, Object> sharedWindow() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!danube.loaded() || !iskar.loaded()) {
            m.put("from", null);
            m.put("to", null);
            return m;
        }
        LocalDate dFrom = ScenarioTimeline.toScenario(danube.firstDate());
        LocalDate dTo = ScenarioTimeline.toScenario(danube.lastDate());
        LocalDate from = max(dFrom, iskar.firstDate());
        LocalDate to = min(dTo, iskar.lastDate());
        m.put("from", from.isAfter(to) ? null : from);
        m.put("to", from.isAfter(to) ? null : to);
        m.put("days", from.isAfter(to) ? 0 : to.toEpochDay() - from.toEpochDay() + 1);
        m.put("note", "Danube " + danube.firstDate().getYear() + " shown on the 2025 timeline; Iskar is native 2025");
        return m;
    }

    public Map<String, Object> confluence() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("river", "DANUBE");
        c.put("river_km", CONFLUENCE_KM);
        c.put("scenario_node", CONFLUENCE_NODE);
        c.put("lat", CONFLUENCE_LAT);
        c.put("lon", CONFLUENCE_LON);
        c.put("coords_approx", true);
        return c;
    }

    private Map<String, Object> header() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provenance", "supplied_dataset");
        m.put("source", "EIT Water Hackathon organiser files: Iskar daily discharge at Novi Iskar and Orehovitsa, 2025");
        m.put("compatibility_state", "SCENARIO-ALIGNED");
        m.put("scenario", ScenarioTimeline.info(ScenarioTimeline.YEAR));
        m.put("shared_window", sharedWindow());
        m.put("label", "No native overlap with the Danube dataset (2021); shown as a scenario-aligned tributary");
        return m;
    }

    private static Map<String, Object> dataset(String id, String name, List<Integer> years,
                                               Map<String, Object> window, String state, String use,
                                               Map<String, String> checks) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dataset_id", id);
        m.put("name", name);
        m.put("source_years", years);
        m.put("scenario_window", window);
        m.put("compatibility_state", state);
        m.put("allowed_use", use);
        m.put("checks", new LinkedHashMap<>(checks));
        return m;
    }

    private static Map<String, Object> window(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_from", from);
        m.put("source_to", to);
        m.put("scenario_from", ScenarioTimeline.toScenario(from));
        m.put("scenario_to", ScenarioTimeline.toScenario(to));
        return m;
    }

    private void requireIskar() {
        if (!iskar.loaded()) {
            throw ApiException.notFound("Iskar data pack", "data/iskar");
        }
    }

    private static LocalDate date(String text, String name) {
        try {
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException e) {
            throw ApiException.invalid(name + " must be YYYY-MM-DD: " + text);
        }
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }
}

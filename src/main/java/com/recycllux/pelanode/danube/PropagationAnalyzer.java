package com.recycllux.pelanode.danube;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Lagged correlation between connected Danube gauges (P0-19 §8). For an ordered pair (upstream, downstream) on the
 * same measurement chain it measures how the river-level signal propagated in the 2021 record: overlap, correlation
 * of levels and of daily level changes (Δh), the lags that maximise each, a monitoring window and a quality class.
 * Statistics use the raw series (no gap filling). The lag is a hydrological signal, never a pollutant travel time.
 */
@Component
@RequiredArgsConstructor
public class PropagationAnalyzer {

    public static final double IRON_GATES_KM = 943;
    public static final int MAX_LAG_DAYS = 6;
    public static final String METHOD = "pearson_lagged_daily_v1";
    public static final String LABEL = "Estimated from historical hydrological signal propagation";
    public static final String NOTE = "Lag = historical propagation of the river-level signal (organiser data, "
            + "Jun–Nov 2021). It is not a pollutant travel time.";
    public static final String RO_CHAIN = "ro_bulletin";
    public static final String BG_CHAIN = "bg_automatic";

    // PROPOSED — DEMO TUNING (P0-19 §8.5)
    static final int MIN_N = 60;
    static final int CLASS_A_N = 120;
    static final double CLASS_A_R = 0.80;
    static final double CLASS_B_R = 0.60;
    static final double CLASS_A_GAP = 0.10;
    static final int MIN_PAIRS_FOR_LAG = 20;

    private final DanubeData data;
    private final Map<String, Edge> cache = new ConcurrentHashMap<>();

    public record Window(int min, int max) {
    }

    public record Edge(
            String fromStation,
            String toStation,
            String chain,
            double distanceKm,
            int nCommonObservations,
            int nCommonDeltaH,
            double dataGapRate,
            Double rLevelLag0,
            Integer optimalLagDaysLevel,
            Double rLevelOptimalLag,
            Integer optimalLagDaysDeltaH,
            Double rDeltaH,
            Double rDeltaHAtLevelLag,
            Window monitoringWindowDays,
            String qualityClass,
            boolean operational,
            String reason,
            double reliability) {
    }

    /** Measurement chain a station belongs to; propagation is only computed inside one chain. */
    public static String chain(DanubeStation s) {
        if (!"Danube".equals(s.river()) || s.riverKm() == null) {
            return "tributary";
        }
        if (s.riverKm() >= IRON_GATES_KM) {
            return "upstream_of_iron_gates";
        }
        return "automatic".equals(s.source()) ? BG_CHAIN : RO_CHAIN;
    }

    public static boolean inModel(DanubeStation s) {
        String c = chain(s);
        return RO_CHAIN.equals(c) || BG_CHAIN.equals(c);
    }

    /** Stations of a chain, upstream to downstream. */
    public List<DanubeStation> chainStations(String chain) {
        return data.stations().stream()
                .filter(s -> chain.equals(chain(s)))
                .sorted(Comparator.comparing(DanubeStation::riverKm).reversed())
                .toList();
    }

    /** Edges between consecutive stations of both lower-Danube chains. */
    public List<Edge> adjacent(String minClass) {
        List<Edge> out = new ArrayList<>();
        for (String c : List.of(RO_CHAIN, BG_CHAIN)) {
            List<DanubeStation> st = chainStations(c);
            for (int i = 0; i + 1 < st.size(); i++) {
                Edge e = edge(st.get(i).stationId(), st.get(i + 1).stationId());
                if (atLeast(e.qualityClass(), minClass)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    /** Edges from a source to every downstream station of its chain within maxKm. */
    public List<Edge> downstream(String sourceId, String minClass, double maxKm) {
        DanubeStation s = data.station(sourceId).orElse(null);
        if (s == null || !inModel(s)) {
            return List.of();
        }
        List<Edge> out = new ArrayList<>();
        for (DanubeStation d : chainStations(chain(s))) {
            if (d.riverKm() < s.riverKm() && s.riverKm() - d.riverKm() <= maxKm) {
                Edge e = edge(s.stationId(), d.stationId());
                if (atLeast(e.qualityClass(), minClass)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    public Edge edge(String from, String to) {
        return cache.computeIfAbsent(from + ">" + to, k -> compute(from, to));
    }

    public Map<String, Object> method() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", METHOD);
        m.put("max_lag_days", MAX_LAG_DAYS);
        m.put("min_n", MIN_N);
        m.put("class_rules", "A: n>=120, r_dh>=0.80, gap<=0.10; B: n>=60, r_dh>=0.60; otherwise C");
        m.put("reliability", "r_dh x min(1, n/120) x (1 - gap_rate); for ranking only, not a probability");
        m.put("status", "proposed_demo_tuning");
        return m;
    }

    static boolean atLeast(String cls, String min) {
        return cls.compareTo(min == null ? "C" : min.toUpperCase()) <= 0;
    }

    private Edge compute(String from, String to) {
        DanubeStation a = data.station(from).orElseThrow();
        DanubeStation b = data.station(to).orElseThrow();
        double[] x = data.levels(a.stationId());
        double[] y = data.levels(b.stationId());
        double[] dx = diff(x);
        double[] dy = diff(y);
        int n = count(x, y, 0);
        double gap = data.days() == 0 ? 1 : 1 - n / (double) data.days();
        Double r0 = pearson(x, y, 0);
        Integer lagLevel = bestLag(x, y);
        Integer lagDh = bestLag(dx, dy);
        Double rLevel = lagLevel == null ? null : pearson(x, y, lagLevel);
        Double rDh = lagDh == null ? null : pearson(dx, dy, lagDh);
        Double rDhAtLevel = lagLevel == null ? null : pearson(dx, dy, lagLevel);
        int nDh = lagLevel == null ? 0 : count(dx, dy, lagLevel);

        String cls;
        String reason;
        double r = rDh == null ? 0 : rDh;
        if (n >= CLASS_A_N && r >= CLASS_A_R && gap <= CLASS_A_GAP) {
            cls = "A";
            reason = null;
        } else if (n >= MIN_N && r >= CLASS_B_R) {
            cls = "B";
            reason = "lower reliability (" + (n < CLASS_A_N ? "n=" + n + " < 120" : gap > CLASS_A_GAP
                    ? "data gaps " + Math.round(gap * 100) + "%" : "r_dh=" + DanubeService.round(r, 2) + " < 0.80") + ")";
        } else {
            cls = "C";
            reason = n < MIN_N ? "insufficient overlap (n=" + n + " < 60)"
                    : "weak Δh coupling (r_dh=" + DanubeService.round(r, 2) + ")";
        }
        Window window = "C".equals(cls) || lagLevel == null || lagDh == null ? null
                : new Window(Math.min(lagLevel, lagDh), Math.max(lagLevel, lagDh));
        double reliability = DanubeService.round(r * Math.min(1, n / (double) CLASS_A_N) * (1 - gap), 3);
        return new Edge(a.stationId(), b.stationId(), chain(a), DanubeService.round(a.riverKm() - b.riverKm(), 1),
                n, nDh, DanubeService.round(gap, 3), round3(r0), lagLevel, round3(rLevel), lagDh, round3(rDh),
                round3(rDhAtLevel), window, cls, !"C".equals(cls), reason, reliability);
    }

    private static Integer bestLag(double[] x, double[] y) {
        Integer best = null;
        double bestR = Double.NEGATIVE_INFINITY;
        for (int k = 0; k <= MAX_LAG_DAYS; k++) {
            if (count(x, y, k) <= MIN_PAIRS_FOR_LAG) {
                continue;
            }
            Double r = pearson(x, y, k);
            if (r != null && r > bestR) {
                bestR = r;
                best = k;
            }
        }
        return best;
    }

    static double[] diff(double[] v) {
        double[] d = new double[v.length];
        d[0] = Double.NaN;
        for (int i = 1; i < v.length; i++) {
            d[i] = v[i] - v[i - 1];
        }
        return d;
    }

    static int count(double[] x, double[] y, int lag) {
        int n = 0;
        for (int i = 0; i + lag < x.length; i++) {
            if (!Double.isNaN(x[i]) && !Double.isNaN(y[i + lag])) {
                n++;
            }
        }
        return n;
    }

    /** Pearson r between x[i] and y[i + lag] over days where both are present. */
    static Double pearson(double[] x, double[] y, int lag) {
        double sx = 0;
        double sy = 0;
        int n = 0;
        for (int i = 0; i + lag < x.length; i++) {
            if (!Double.isNaN(x[i]) && !Double.isNaN(y[i + lag])) {
                sx += x[i];
                sy += y[i + lag];
                n++;
            }
        }
        if (n < 3) {
            return null;
        }
        double mx = sx / n;
        double my = sy / n;
        double cov = 0;
        double vx = 0;
        double vy = 0;
        for (int i = 0; i + lag < x.length; i++) {
            if (!Double.isNaN(x[i]) && !Double.isNaN(y[i + lag])) {
                cov += (x[i] - mx) * (y[i + lag] - my);
                vx += (x[i] - mx) * (x[i] - mx);
                vy += (y[i + lag] - my) * (y[i + lag] - my);
            }
        }
        return vx == 0 || vy == 0 ? null : cov / Math.sqrt(vx * vy);
    }

    private static Double round3(Double v) {
        return v == null ? null : DanubeService.round(v, 3);
    }
}

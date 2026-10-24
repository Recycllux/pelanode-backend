package com.recycllux.pelanode.danube;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Candidate water-temperature anomalies on the Bulgarian automatic gauges (P0-19 §9). Each station is compared with
 * the mean of its nearest upstream and downstream temperature stations; the station's usual offset (median) is
 * removed and the residual is scored with a robust z (σ = 1.4826 × MAD). Days above the threshold are candidates,
 * never diagnoses: no source attribution and no ground truth.
 */
@Component
@RequiredArgsConstructor
public class TemperatureAnomalyDetector {

    public static final String METHOD = "neighbour_mean_robust_z_v1";
    public static final double DEFAULT_THRESHOLD = 4.0; // PROPOSED — DEMO TUNING
    public static final String LABEL = "Candidate temperature anomaly relative to neighbouring stations";
    public static final String NOTE = "Candidate anomalies relative to neighbouring stations. No source attribution. "
            + "Ground truth unavailable.";
    static final double MAD_SCALE = 1.4826;

    private final DanubeData data;
    private volatile Baseline baseline;
    private final Map<Double, Result> results = new ConcurrentHashMap<>();

    public record StationStats(
            String stationId,
            List<String> neighbours,
            String referenceType,
            int n,
            double baselineMedianC,
            double baselineMadC,
            int candidatesPrimary,
            int candidatesInfluenced) {
    }

    public record Candidate(
            String stationId,
            LocalDate date,
            double observedC,
            double expectedC,
            double residualC,
            double robustZ,
            List<String> neighbours,
            String referenceType,
            String role,
            String influencedBy,
            List<String> influenced) {
    }

    public record Result(List<StationStats> stations, List<Candidate> candidates) {
    }

    private record Series(String stationId, List<String> neighbours, String referenceType, double[] expected,
                          double[] residual, double[] z, double median, double mad) {
    }

    private record Baseline(List<Series> chain) {
    }

    public Result detect(double threshold) {
        return results.computeIfAbsent(threshold, this::compute);
    }

    /** Observed, expected and residual series for one station (for the anomaly drawer). */
    public Map<String, double[]> series(String stationId) {
        for (Series s : baseline().chain()) {
            if (s.stationId().equals(stationId)) {
                return Map.of("observed", data.temps(stationId), "expected", s.expected(), "z", s.z());
            }
        }
        return null;
    }

    public List<String> neighbours(String stationId) {
        return baseline().chain().stream().filter(s -> s.stationId().equals(stationId)).findFirst()
                .map(Series::neighbours).orElse(List.of());
    }

    private Baseline baseline() {
        if (baseline == null) {
            synchronized (this) {
                if (baseline == null) {
                    baseline = build();
                }
            }
        }
        return baseline;
    }

    private Baseline build() {
        List<String> chain = data.stations().stream()
                .filter(s -> "automatic".equals(s.source()) && s.riverKm() != null)
                .filter(s -> Arrays.stream(data.temps(s.stationId())).anyMatch(v -> !Double.isNaN(v)))
                .sorted(Comparator.comparing(DanubeStation::riverKm).reversed())
                .map(DanubeStation::stationId)
                .toList();
        List<Series> out = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            List<String> nb = new ArrayList<>();
            if (i > 0) {
                nb.add(chain.get(i - 1));
            }
            if (i + 1 < chain.size()) {
                nb.add(chain.get(i + 1));
            }
            double[] t = data.temps(chain.get(i));
            double[] ref = new double[t.length];
            double[] res = new double[t.length];
            for (int d = 0; d < t.length; d++) {
                double sum = 0;
                for (String n : nb) {
                    sum += data.temps(n)[d];
                }
                ref[d] = sum / nb.size();
                res[d] = t[d] - ref[d];
            }
            double med = median(res);
            double[] dev = Arrays.stream(res).map(v -> Math.abs(v - med)).toArray();
            double mad = median(dev);
            double sigma = MAD_SCALE * mad;
            double[] z = Arrays.stream(res).map(v -> sigma == 0 ? Double.NaN : (v - med) / sigma).toArray();
            out.add(new Series(chain.get(i), List.copyOf(nb), nb.size() == 2 ? "two_sided" : "one_sided", ref, res,
                    z, med, mad));
        }
        return new Baseline(out);
    }

    private Result compute(double threshold) {
        List<Series> chain = baseline().chain();
        Map<String, Integer> primary = new HashMap<>();
        Map<String, Integer> influenced = new HashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        for (int d = 0; d < data.days(); d++) {
            // neighbour-influence rule (§9.3): within a run of adjacent flagged stations, the largest |z| is primary
            // and adjacent flags of opposite sign are neighbour-influenced
            int i = 0;
            while (i < chain.size()) {
                if (!flagged(chain.get(i), d, threshold)) {
                    i++;
                    continue;
                }
                int j = i;
                while (j + 1 < chain.size() && flagged(chain.get(j + 1), d, threshold)) {
                    j++;
                }
                int top = i;
                for (int k = i; k <= j; k++) {
                    if (Math.abs(chain.get(k).z()[d]) > Math.abs(chain.get(top).z()[d])) {
                        top = k;
                    }
                }
                double sign = Math.signum(chain.get(top).z()[d]);
                List<String> lifted = new ArrayList<>();
                for (int k = i; k <= j; k++) {
                    if (k != top && Math.signum(chain.get(k).z()[d]) != sign) {
                        lifted.add(chain.get(k).stationId());
                    }
                }
                for (int k = i; k <= j; k++) {
                    Series s = chain.get(k);
                    boolean isLifted = lifted.contains(s.stationId());
                    String role = isLifted ? "neighbour_influenced" : "primary";
                    (isLifted ? influenced : primary).merge(s.stationId(), 1, Integer::sum);
                    candidates.add(new Candidate(s.stationId(), data.date(d), data.temps(s.stationId())[d],
                            r(s.expected()[d], 2), r(s.residual()[d], 2), r(s.z()[d], 1), s.neighbours(),
                            s.referenceType(), role, isLifted ? chain.get(top).stationId() : null,
                            k == top ? List.copyOf(lifted) : List.of()));
                }
                i = j + 1;
            }
        }
        List<StationStats> stats = new ArrayList<>();
        for (Series s : chain) {
            int n = (int) Arrays.stream(s.residual()).filter(v -> !Double.isNaN(v)).count();
            stats.add(new StationStats(s.stationId(), s.neighbours(), s.referenceType(), n, r(s.median(), 2),
                    r(s.mad(), 3), primary.getOrDefault(s.stationId(), 0), influenced.getOrDefault(s.stationId(), 0)));
        }
        return new Result(stats, candidates);
    }

    private static boolean flagged(Series s, int d, double threshold) {
        double z = s.z()[d];
        return !Double.isNaN(z) && Math.abs(z) > threshold;
    }

    static double median(double[] values) {
        double[] v = Arrays.stream(values).filter(x -> !Double.isNaN(x)).sorted().toArray();
        if (v.length == 0) {
            return Double.NaN;
        }
        int m = v.length / 2;
        return v.length % 2 == 1 ? v[m] : (v[m - 1] + v[m]) / 2;
    }

    private static double r(double v, int digits) {
        return DanubeService.round(v, digits);
    }
}

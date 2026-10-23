package com.recycllux.pelanode.danube;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LevelForecaster {

    public static final int MAX_HORIZON = 6;
    static final double RIDGE_LAMBDA = 20.0;
    static final int MIN_TRAINING_ROWS = 30;
    static final double MIN_PREDICTOR_COVERAGE = 0.9;
    static final double UPSTREAM_MIN_KM = 50;
    static final double UPSTREAM_MAX_KM = 1000;
    static final int[] WINDOWS = {1, 3};
    static final double BAND_PER_MAE = 1.6;

    private final DanubeData data;
    private final Map<String, Hindcast> cache = new ConcurrentHashMap<>();

    public record Feature(String stationId, int window) {
    }

    public record Model(List<Feature> features, double[] mean, double[] scale, double[] weights, int rows,
                        int trainFrom, int trainTo) {

        double predict(double[] x) {
            double y = weights[0];
            for (int j = 0; j < x.length; j++) {
                y += weights[j + 1] * (x[j] - mean[j]) / scale[j];
            }
            return y;
        }

        Map<String, Double> contributions(double[] x) {
            Map<String, Double> byStation = new LinkedHashMap<>();
            for (int j = 0; j < x.length; j++) {
                byStation.merge(features.get(j).stationId(), weights[j + 1] * (x[j] - mean[j]) / scale[j], Double::sum);
            }
            return byStation;
        }
    }

    public record Hindcast(String stationId, List<String> predictors, double[][] predicted) {
    }

    public List<String> predictors(String target) {
        DanubeStation t = data.station(target).orElseThrow();
        if (t.riverKm() == null) {
            return List.of();
        }
        return data.stations().stream()
                .filter(s -> "Danube".equals(s.river()) && s.riverKm() != null)
                .filter(s -> s.riverKm() >= t.riverKm() + UPSTREAM_MIN_KM && s.riverKm() <= t.riverKm() + UPSTREAM_MAX_KM)
                .filter(s -> data.coverage(s.stationId()) >= MIN_PREDICTOR_COVERAGE)
                .sorted(Comparator.comparing(DanubeStation::riverKm))
                .map(DanubeStation::stationId)
                .toList();
    }

    public boolean forecastable(String target) {
        return data.station(target).map(s -> "Danube".equals(s.river())).orElse(false)
                && data.coverage(target) >= 0.8 && !predictors(target).isEmpty();
    }

    List<Feature> features(String target) {
        List<Feature> out = new ArrayList<>();
        for (String s : predictors(target)) {
            for (int w : WINDOWS) {
                out.add(new Feature(s, w));
            }
        }
        for (int w : WINDOWS) {
            out.add(new Feature(target, w));
        }
        return out;
    }

    double[] row(List<Feature> features, int day) {
        double[] x = new double[features.size()];
        for (int j = 0; j < x.length; j++) {
            Feature f = features.get(j);
            double[] s = data.filledLevels(f.stationId());
            x[j] = day - f.window() < 0 ? Double.NaN : s[day] - s[day - f.window()];
        }
        return x;
    }

    static boolean finite(double[] x) {
        return Arrays.stream(x).noneMatch(Double::isNaN);
    }

    public Model fit(String target, int horizon, int asOf) {
        List<Feature> features = features(target);
        double[] y = data.filledLevels(target);
        List<double[]> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        int from = -1;
        int to = -1;
        for (int i = 0; i + horizon <= asOf; i++) {
            double[] x = row(features, i);
            double dy = y[i + horizon] - y[i];
            if (finite(x) && !Double.isNaN(dy)) {
                xs.add(x);
                ys.add(dy);
                from = from < 0 ? i : from;
                to = i + horizon;
            }
        }
        if (xs.size() < MIN_TRAINING_ROWS) {
            return null;
        }
        int p = features.size();
        double[] mean = new double[p];
        double[] scale = new double[p];
        for (int j = 0; j < p; j++) {
            final int col = j;
            mean[j] = xs.stream().mapToDouble(r -> r[col]).average().orElse(0);
            double var = xs.stream().mapToDouble(r -> (r[col] - mean[col]) * (r[col] - mean[col])).sum() / Math.max(1, xs.size() - 1);
            scale[j] = var > 0 ? Math.sqrt(var) : 1;
        }
        int n = p + 1;
        double[][] ata = new double[n][n];
        double[] atb = new double[n];
        double[] a = new double[n];
        for (int r = 0; r < xs.size(); r++) {
            a[0] = 1;
            for (int j = 0; j < p; j++) {
                a[j + 1] = (xs.get(r)[j] - mean[j]) / scale[j];
            }
            for (int i = 0; i < n; i++) {
                atb[i] += a[i] * ys.get(r);
                for (int k = 0; k < n; k++) {
                    ata[i][k] += a[i] * a[k];
                }
            }
        }
        for (int i = 1; i < n; i++) {
            ata[i][i] += RIDGE_LAMBDA;
        }
        return new Model(features, mean, scale, solve(ata, atb), xs.size(), from, to);
    }

    public Double predictChange(Model model, int day) {
        if (model == null) {
            return null;
        }
        double[] x = row(model.features(), day);
        return finite(x) ? model.predict(x) : null;
    }

    public Hindcast hindcast(String target) {
        return cache.computeIfAbsent(target, this::computeHindcast);
    }

    private Hindcast computeHindcast(String target) {
        double[][] predicted = new double[MAX_HORIZON + 1][data.days()];
        double[] y = data.filledLevels(target);
        for (double[] row : predicted) {
            Arrays.fill(row, Double.NaN);
        }
        for (int h = 1; h <= MAX_HORIZON; h++) {
            for (int a = 0; a < data.days(); a++) {
                if (Double.isNaN(y[a])) {
                    continue;
                }
                Double dy = predictChange(fit(target, h, a), a);
                if (dy != null) {
                    predicted[h][a] = y[a] + dy;
                }
            }
        }
        return new Hindcast(target, predictors(target), predicted);
    }

    public Double band(String target, int horizon, int asOf) {
        double[] y = data.filledLevels(target);
        double[] p = hindcast(target).predicted()[horizon];
        double sum = 0;
        int n = 0;
        for (int i = 0; i + horizon <= asOf; i++) {
            if (!Double.isNaN(p[i]) && !Double.isNaN(y[i + horizon])) {
                sum += Math.abs(p[i] - y[i + horizon]);
                n++;
            }
        }
        return n < 10 ? null : BAND_PER_MAE * sum / n;
    }

    static double[] solve(double[][] m, double[] b) {
        int n = b.length;
        double[][] a = new double[n][];
        for (int i = 0; i < n; i++) {
            a[i] = Arrays.copyOf(m[i], n + 1);
            a[i][n] = b[i];
        }
        for (int c = 0; c < n; c++) {
            int pivot = c;
            for (int r = c + 1; r < n; r++) {
                if (Math.abs(a[r][c]) > Math.abs(a[pivot][c])) {
                    pivot = r;
                }
            }
            double[] tmp = a[c];
            a[c] = a[pivot];
            a[pivot] = tmp;
            for (int r = c + 1; r < n; r++) {
                double f = a[r][c] / a[c][c];
                for (int k = c; k <= n; k++) {
                    a[r][k] -= f * a[c][k];
                }
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double s = a[r][n];
            for (int k = r + 1; k < n; k++) {
                s -= a[r][k] * x[k];
            }
            x[r] = s / a[r][r];
        }
        return x;
    }
}

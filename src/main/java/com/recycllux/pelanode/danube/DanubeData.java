package com.recycllux.pelanode.danube;

import com.recycllux.pelanode.config.DataPaths;
import com.recycllux.pelanode.config.PelanodeProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DanubeData {

    private static final Logger log = LoggerFactory.getLogger(DanubeData.class);
    static final int MAX_GAP_DAYS = 3;

    private final Map<String, DanubeStation> stations = new LinkedHashMap<>();
    private final Map<String, double[]> levels = new HashMap<>();
    private final Map<String, double[]> temps = new HashMap<>();
    private final Map<String, double[]> filled = new HashMap<>();
    private final LocalDate first;
    private final int days;

    public DanubeData(PelanodeProperties props) {
        Path dir = DataPaths.resolve(props.danubeDir() == null ? "data/danube" : props.danubeDir());
        Path stationFile = dir.resolve("stations.csv");
        Path levelFile = dir.resolve("levels.csv");
        if (!Files.exists(stationFile) || !Files.exists(levelFile)) {
            log.warn("DANUBE_PACK_MISSING dir={}", dir);
            first = null;
            days = 0;
            return;
        }
        for (List<String> row : read(stationFile)) {
            DanubeStation s = new DanubeStation(row.get(0), row.get(1), row.get(2), row.get(3), row.get(4),
                    number(row.get(5)), number(row.get(6)), number(row.get(7)), Boolean.parseBoolean(row.get(8)),
                    row.get(9));
            stations.put(s.stationId(), s);
        }
        List<List<String>> rows = read(levelFile);
        LocalDate min = null;
        LocalDate max = null;
        for (List<String> row : rows) {
            LocalDate d = LocalDate.parse(row.get(0));
            min = min == null || d.isBefore(min) ? d : min;
            max = max == null || d.isAfter(max) ? d : max;
        }
        first = min;
        days = min == null ? 0 : (int) ChronoUnit.DAYS.between(min, max) + 1;
        for (String id : stations.keySet()) {
            levels.put(id, nanArray(days));
            temps.put(id, nanArray(days));
        }
        for (List<String> row : rows) {
            int i = index(LocalDate.parse(row.get(0)));
            Double level = number(row.get(2));
            Double temp = number(row.get(3));
            if (levels.containsKey(row.get(1))) {
                levels.get(row.get(1))[i] = level == null ? Double.NaN : level;
                temps.get(row.get(1))[i] = temp == null ? Double.NaN : temp;
            }
        }
        levels.forEach((id, series) -> filled.put(id, interpolate(series)));
        log.info("DANUBE_PACK_LOADED stations={} rows={} from={} to={}", stations.size(), rows.size(), first, max);
    }

    public boolean loaded() {
        return days > 0;
    }

    public List<DanubeStation> stations() {
        return List.copyOf(stations.values());
    }

    public Optional<DanubeStation> station(String id) {
        return Optional.ofNullable(id == null ? null : stations.get(id.toUpperCase()));
    }

    public LocalDate firstDate() {
        return first;
    }

    public LocalDate lastDate() {
        return first == null ? null : first.plusDays(days - 1L);
    }

    public int days() {
        return days;
    }

    public LocalDate date(int index) {
        return first.plusDays(index);
    }

    public int index(LocalDate date) {
        return (int) ChronoUnit.DAYS.between(first, date);
    }

    public double[] levels(String stationId) {
        return levels.getOrDefault(stationId, nanArray(days));
    }

    public double[] filledLevels(String stationId) {
        return filled.getOrDefault(stationId, nanArray(days));
    }

    public double[] temps(String stationId) {
        return temps.getOrDefault(stationId, nanArray(days));
    }

    public double coverage(String stationId) {
        double[] s = levels(stationId);
        return days == 0 ? 0 : Arrays.stream(s).filter(v -> !Double.isNaN(v)).count() / (double) days;
    }

    static double[] interpolate(double[] raw) {
        double[] out = raw.clone();
        int last = -1;
        for (int i = 0; i < raw.length; i++) {
            if (Double.isNaN(raw[i])) {
                continue;
            }
            if (last >= 0 && i - last > 1 && i - last - 1 <= MAX_GAP_DAYS) {
                for (int j = last + 1; j < i; j++) {
                    out[j] = raw[last] + (raw[i] - raw[last]) * (j - last) / (double) (i - last);
                }
            }
            last = i;
        }
        return out;
    }

    private static double[] nanArray(int n) {
        double[] a = new double[n];
        Arrays.fill(a, Double.NaN);
        return a;
    }

    private static Double number(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Double.parseDouble(text);
    }

    private static List<List<String>> read(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return Collections.emptyList();
            }
            return lines.subList(1, lines.size()).stream()
                    .filter(l -> !l.isBlank())
                    .map(l -> Arrays.asList(l.split(",", -1)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

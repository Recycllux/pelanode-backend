package com.recycllux.pelanode.scenario;

import com.recycllux.pelanode.config.DataPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Iskar daily discharge (organiser files, native 2025), built by scripts/build_iskar_pack.py. */
@Component
public class IskarData {

    private static final Logger log = LoggerFactory.getLogger(IskarData.class);

    public record Station(String stationId, String name, String nameBg, String river, Double lat, Double lon,
                          boolean coordsApprox) {
    }

    private final Map<String, Station> stations = new LinkedHashMap<>();
    private final Map<String, NavigableMap<LocalDate, Double>> discharge = new LinkedHashMap<>();

    public IskarData() {
        Path dir = DataPaths.resolve("data/iskar");
        Path stationFile = dir.resolve("stations.csv");
        Path valueFile = dir.resolve("discharge.csv");
        if (!Files.exists(stationFile) || !Files.exists(valueFile)) {
            log.warn("ISKAR_PACK_MISSING dir={}", dir);
            return;
        }
        for (List<String> r : read(stationFile)) {
            stations.put(r.get(0), new Station(r.get(0), r.get(1), r.get(2), r.get(3), Double.parseDouble(r.get(4)),
                    Double.parseDouble(r.get(5)), Boolean.parseBoolean(r.get(6))));
            discharge.put(r.get(0), new TreeMap<>());
        }
        for (List<String> r : read(valueFile)) {
            NavigableMap<LocalDate, Double> s = discharge.get(r.get(1));
            if (s != null && !r.get(2).isBlank()) {
                s.put(LocalDate.parse(r.get(0)), Double.parseDouble(r.get(2)));
            }
        }
        log.info("ISKAR_PACK_LOADED stations={} from={} to={}", stations.size(), firstDate(), lastDate());
    }

    public boolean loaded() {
        return !stations.isEmpty();
    }

    public List<Station> stations() {
        return List.copyOf(stations.values());
    }

    public Optional<Station> station(String id) {
        return Optional.ofNullable(id == null ? null : stations.get(id.toUpperCase()));
    }

    public NavigableMap<LocalDate, Double> discharge(String stationId) {
        return discharge.getOrDefault(stationId, new TreeMap<>());
    }

    public LocalDate firstDate() {
        return discharge.values().stream().filter(m -> !m.isEmpty()).map(NavigableMap::firstKey)
                .min(LocalDate::compareTo).orElse(null);
    }

    public LocalDate lastDate() {
        return discharge.values().stream().filter(m -> !m.isEmpty()).map(NavigableMap::lastKey)
                .max(LocalDate::compareTo).orElse(null);
    }

    private static List<List<String>> read(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return lines.subList(1, lines.size()).stream()
                    .filter(l -> !l.isBlank())
                    .map(l -> Arrays.asList(l.split(",", -1)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

package com.recycllux.pelanode.climate;

import com.recycllux.pelanode.config.DataPaths;
import com.recycllux.pelanode.config.PelanodeProperties;
import com.recycllux.pelanode.service.Geo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ClimateGrids {

    private static final Logger log = LoggerFactory.getLogger(ClimateGrids.class);

    public record Variable(String id, String name, String unit, String source) {
    }

    public record Grid(Variable variable, List<String> months, double[] lon, double[] lat, double[][] values) {

        public int nearest(double qLat, double qLon) {
            int best = -1;
            double bestKm = Double.MAX_VALUE;
            for (int i = 0; i < lon.length; i++) {
                double km = Geo.km(qLat, qLon, lat[i], lon[i]);
                if (km < bestKm) {
                    bestKm = km;
                    best = i;
                }
            }
            return best;
        }

        public double rankPct(int month, double value) {
            long below = 0;
            long n = 0;
            for (double[] cell : values) {
                if (!Double.isNaN(cell[month])) {
                    n++;
                    if (cell[month] < value) {
                        below++;
                    }
                }
            }
            return n == 0 ? Double.NaN : 100.0 * below / n;
        }
    }

    static final List<Variable> VARIABLES = List.of(
            new Variable("precipitation", "Precipitation", "mm per month",
                    "Organiser Earth Engine export (gee_output/precipitation), Bulgaria, 2024"),
            new Variable("soil_moisture", "Soil moisture", "m3/m3",
                    "Organiser Earth Engine export (gee_output/soil_moisture), Bulgaria, 2024"));

    private final Map<String, Grid> grids = new LinkedHashMap<>();

    public ClimateGrids(PelanodeProperties props) {
        Path dir = DataPaths.resolve(props.climateDir() == null ? "data/climate" : props.climateDir());
        for (Variable v : VARIABLES) {
            Path file = dir.resolve(v.id() + ".csv");
            if (!Files.exists(file)) {
                log.warn("CLIMATE_GRID_MISSING file={}", file);
                continue;
            }
            grids.put(v.id(), load(v, file));
        }
        log.info("CLIMATE_GRIDS_LOADED variables={}", grids.keySet());
    }

    public List<Grid> grids() {
        return List.copyOf(grids.values());
    }

    public Optional<Grid> grid(String id) {
        return Optional.ofNullable(grids.get(id));
    }

    private static Grid load(Variable v, Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String[] header = lines.get(0).split(",");
            List<String> months = Arrays.asList(header).subList(2, header.length);
            List<String> rows = new ArrayList<>(lines.subList(1, lines.size()));
            rows.removeIf(String::isBlank);
            double[] lon = new double[rows.size()];
            double[] lat = new double[rows.size()];
            double[][] values = new double[rows.size()][months.size()];
            for (int i = 0; i < rows.size(); i++) {
                String[] cols = rows.get(i).split(",", -1);
                lon[i] = Double.parseDouble(cols[0]);
                lat[i] = Double.parseDouble(cols[1]);
                for (int m = 0; m < months.size(); m++) {
                    String c = cols[m + 2];
                    values[i][m] = c.isBlank() ? Double.NaN : Double.parseDouble(c);
                }
            }
            return new Grid(v, List.copyOf(months), lon, lat, values);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

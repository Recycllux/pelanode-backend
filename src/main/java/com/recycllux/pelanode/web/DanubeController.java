package com.recycllux.pelanode.web;

import com.recycllux.pelanode.danube.DanubeService;
import com.recycllux.pelanode.danube.PointContextService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/danube")
@RequiredArgsConstructor
public class DanubeController {

    private final DanubeService danube;
    private final PointContextService points;

    @GetMapping("/stations")
    public Map<String, Object> stations() {
        return danube.stations();
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(@RequestParam(required = false) String river,
                                       @RequestParam(required = false) String date) {
        return danube.profile(river, date);
    }

    @GetMapping("/series")
    public Map<String, Object> series(@RequestParam String station,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to) {
        return danube.series(station, from, to);
    }

    @GetMapping("/forecast")
    public Map<String, Object> forecast(@RequestParam(required = false) String station,
                                        @RequestParam(name = "mission_id", required = false) String missionId,
                                        @RequestParam(name = "as_of", required = false) String asOf,
                                        @RequestParam(required = false) Integer horizon) {
        return danube.forecast(station, missionId, asOf, horizon);
    }

    @GetMapping("/point")
    public Map<String, Object> point(@RequestParam(required = false) Double lat,
                                     @RequestParam(required = false) Double lon,
                                     @RequestParam(name = "as_of", required = false) String asOf,
                                     @RequestParam(required = false) Integer month) {
        return points.at(lat, lon, asOf, month);
    }

    @GetMapping("/backtest")
    public Map<String, Object> backtest(@RequestParam String station) {
        return danube.backtest(station);
    }
}

package com.recycllux.pelanode.web;

import com.recycllux.pelanode.scenario.ScenarioService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scenario")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenario;

    @GetMapping("/datasets")
    public Map<String, Object> datasets() {
        return scenario.datasets();
    }

    @GetMapping("/tributaries/iskar")
    public Map<String, Object> iskar(@RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to) {
        return scenario.iskarSeries(from, to);
    }

    /** Computes a scenario-aligned tributary input event; nothing is stored. */
    @PostMapping("/tributaries/iskar/event")
    public Map<String, Object> iskarEvent(@RequestBody(required = false) Map<String, String> body) {
        Map<String, String> b = body == null ? Map.of() : body;
        return scenario.event(b.get("station_id"), b.get("scenario_date"));
    }
}

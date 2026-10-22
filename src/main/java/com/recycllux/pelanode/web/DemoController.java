package com.recycllux.pelanode.web;

import com.recycllux.pelanode.config.PelanodeProperties;
import com.recycllux.pelanode.pack.SeedLoader;
import com.recycllux.pelanode.replay.Simulator;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final SeedLoader seed;
    private final Simulator simulator;
    private final PelanodeProperties props;
    private final JdbcTemplate jdbc;

    public record ResetRequest(String missionId) {
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestBody(required = false) ResetRequest req) {
        String missionId = req == null || req.missionId() == null ? props.demoMissionId() : req.missionId();
        if (!props.demoMissionId().equals(missionId)) {
            throw ApiException.notFound("Demo mission", missionId);
        }
        long t0 = System.currentTimeMillis();
        synchronized (simulator) {
            simulator.clock().reset();
            seed.clearMission(missionId);
            seed.seed();
            simulator.rewind();
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String table : new String[] {"track_point", "observation", "event", "review_action"}) {
            String where = table.equals("review_action") ? "" : " WHERE mission_id = '" + missionId + "'";
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, Long.class));
        }
        log.info("DEMO_RESET mission={} took_ms={}", missionId, System.currentTimeMillis() - t0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "reset");
        out.put("reseeded_at", Instant.now());
        out.put("counts", counts);
        out.put("replay", simulator.clock().view());
        return out;
    }
}

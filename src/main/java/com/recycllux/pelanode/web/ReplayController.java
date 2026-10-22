package com.recycllux.pelanode.web;

import com.recycllux.pelanode.config.PelanodeProperties;
import com.recycllux.pelanode.replay.ReplayClock;
import com.recycllux.pelanode.replay.Simulator;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/missions/{id}/replay")
public class ReplayController {

    private final Simulator simulator;
    private final PelanodeProperties props;

    public ReplayController(Simulator simulator, PelanodeProperties props) {
        this.simulator = simulator;
        this.props = props;
    }

    public record Command(String action, String to, String speed) {
    }

    @GetMapping
    public Map<String, Object> get(@PathVariable String id) {
        return clock(id).view();
    }

    @PostMapping
    public Map<String, Object> control(@PathVariable String id, @RequestBody Command cmd) {
        ReplayClock clock = clock(id);
        if (cmd.action() == null) {
            throw ApiException.invalid("action is required");
        }
        switch (cmd.action()) {
            case "start" -> {
                if (cmd.speed() != null) {
                    clock.speed(ReplayClock.parseSpeed(cmd.speed()));
                }
                clock.start();
            }
            case "pause" -> clock.pause();
            case "resume" -> clock.resume();
            case "seek" -> {
                if (cmd.to() == null) {
                    throw ApiException.invalid("seek needs 'to'");
                }
                clock.seek(MissionController.parse(cmd.to()));
                simulator.emitUpTo(clock.missionTime());
            }
            case "speed" -> clock.speed(ReplayClock.parseSpeed(cmd.speed()));
            default -> throw ApiException.invalid("unknown action: " + cmd.action());
        }
        return clock.view();
    }

    private ReplayClock clock(String id) {
        if (!props.demoMissionId().equals(id)) {
            throw ApiException.notFound("Replay for mission", id);
        }
        return simulator.clock();
    }
}

package com.recycllux.pelanode.service;

import com.recycllux.pelanode.config.PelanodeProperties;
import com.recycllux.pelanode.replay.ReplayClock;
import com.recycllux.pelanode.replay.Simulator;
import com.recycllux.pelanode.web.ApiException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReplayService {

    private final Simulator simulator;
    private final PelanodeProperties props;

    public record Command(String action, String to, String speed) {
    }

    public Map<String, Object> view(String missionId) {
        return clock(missionId).view();
    }

    public Map<String, Object> control(String missionId, Command cmd) {
        ReplayClock clock = clock(missionId);
        if (cmd == null || cmd.action() == null) {
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
                clock.seek(Times.parse(cmd.to()));
                simulator.emitUpTo(clock.missionTime());
            }
            case "speed" -> clock.speed(ReplayClock.parseSpeed(cmd.speed()));
            default -> throw ApiException.invalid("unknown action: " + cmd.action());
        }
        return clock.view();
    }

    private ReplayClock clock(String missionId) {
        if (!props.demoMissionId().equals(missionId)) {
            throw ApiException.notFound("Replay for mission", missionId);
        }
        return simulator.clock();
    }
}

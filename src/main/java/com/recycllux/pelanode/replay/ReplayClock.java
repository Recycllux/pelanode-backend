package com.recycllux.pelanode.replay;

import com.recycllux.pelanode.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step B-07. One mission clock (P0-12): idle -> running <-> paused -> ended.
 * mission_time = anchor + (wall time since anchor) x speed while running.
 * Speeds 1, 2, 5 and 100 ("demo": 42 minutes in about 25 s).
 */
public class ReplayClock {

    private static final Logger log = LoggerFactory.getLogger(ReplayClock.class);
    public static final double DEMO_SPEED = 100;

    public enum State { idle, running, paused, ended }

    private final String missionId;
    private final Instant missionStart;
    private final Instant missionEnd;
    private final Clock wall;

    private State state = State.idle;
    private double speed;
    private Instant anchorMission;
    private Instant anchorWall;
    private Instant startedAt;

    public ReplayClock(String missionId, Instant missionStart, Instant missionEnd, double speed, Clock wall) {
        this.missionId = missionId;
        this.missionStart = missionStart;
        this.missionEnd = missionEnd;
        this.speed = speed;
        this.wall = wall;
        this.anchorMission = missionStart;
    }

    public static double parseSpeed(String text) {
        if (text == null || text.isBlank() || "demo".equalsIgnoreCase(text)) {
            return DEMO_SPEED;
        }
        double s = Double.parseDouble(text);
        if (s != 1 && s != 2 && s != 5 && s != DEMO_SPEED) {
            throw ApiException.invalid("speed must be 1, 2, 5 or 100 (demo)");
        }
        return s;
    }

    public synchronized Instant missionTime() {
        if (state != State.running) {
            return anchorMission;
        }
        long wallMillis = Duration.between(anchorWall, wall.instant()).toMillis();
        Instant t = anchorMission.plusMillis(Math.round(wallMillis * speed));
        if (!t.isBefore(missionEnd)) {
            anchorMission = missionEnd;
            state = State.ended;
            log.info("REPLAY_STATE mission={} state=ended mission_time={}", missionId, missionEnd);
            return missionEnd;
        }
        return t;
    }

    public synchronized State state() {
        missionTime();
        return state;
    }

    public synchronized void start() {
        if (state != State.idle) {
            throw ApiException.conflict("start is only valid from idle (state is " + state + "); use resume or reset");
        }
        startedAt = wall.instant();
        run();
    }

    public synchronized void pause() {
        missionTime();
        if (state != State.running) {
            throw ApiException.conflict("pause is only valid while running (state is " + state + ")");
        }
        anchorMission = missionTime();
        state = State.paused;
        logState();
    }

    public synchronized void resume() {
        missionTime();
        if (state != State.paused) {
            throw ApiException.conflict("resume is only valid while paused (state is " + state + ")");
        }
        run();
    }

    /** Seek never deletes data; it only moves the cut-off the UI reads with. */
    public synchronized void seek(Instant to) {
        if (to.isBefore(missionStart) || to.isAfter(missionEnd)) {
            throw ApiException.invalid("seek target must be inside the mission window");
        }
        boolean wasRunning = state() == State.running;
        anchorMission = to;
        anchorWall = wall.instant();
        if (startedAt == null) {
            startedAt = anchorWall;
        }
        state = to.equals(missionEnd) ? State.ended : (wasRunning ? State.running : State.paused);
        logState();
    }

    public synchronized void speed(double newSpeed) {
        Instant now = missionTime();
        anchorMission = now;
        anchorWall = wall.instant();
        speed = newSpeed;
        logState();
    }

    public synchronized void reset() {
        state = State.idle;
        anchorMission = missionStart;
        anchorWall = null;
        startedAt = null;
        logState();
    }

    public synchronized Map<String, Object> view() {
        Map<String, Object> v = new LinkedHashMap<>();
        Instant t = missionTime();
        v.put("mission_id", missionId);
        v.put("state", state.name());
        v.put("mission_time", t);
        v.put("speed", speed);
        v.put("started_at", startedAt);
        v.put("mission_start", missionStart);
        v.put("mission_end", missionEnd);
        return v;
    }

    private void run() {
        anchorWall = wall.instant();
        state = State.running;
        logState();
    }

    private void logState() {
        log.info("REPLAY_STATE mission={} state={} mission_time={} speed={}", missionId, state, anchorMission, speed);
    }
}

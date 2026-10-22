package com.recycllux.pelanode.replay;

import com.recycllux.pelanode.config.PelanodeProperties;
import com.recycllux.pelanode.domain.DeviceStatus;
import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.domain.ModelOutput;
import com.recycllux.pelanode.domain.Observation;
import com.recycllux.pelanode.domain.TrackPoint;
import com.recycllux.pelanode.pack.DemoPack;
import com.recycllux.pelanode.service.IngestService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Simulator {

    private static final Logger log = LoggerFactory.getLogger(Simulator.class);

    private record Item(Instant at, int order, String id, Runnable send) {
    }

    private final DemoPack pack;
    private final IngestService ingest;
    private final PelanodeProperties props;

    private ReplayClock clock;
    private List<Item> timeline = List.of();
    private int next;
    private MessageDigest digest;
    private boolean digestLogged;

    public Simulator(DemoPack pack, IngestService ingest, PelanodeProperties props) {
        this.pack = pack;
        this.ingest = ingest;
        this.props = props;
    }

    public synchronized ReplayClock clock() {
        if (clock == null) {
            Mission m = pack.mission();
            clock = new ReplayClock(m.missionId(), m.plannedStart(), m.plannedEnd(),
                    ReplayClock.parseSpeed(props.replaySpeed()), Clock.systemUTC());
            rewind();
        }
        return clock;
    }

    public synchronized void rewind() {
        timeline = buildTimeline();
        next = 0;
        digest = sha256();
        digestLogged = false;
    }

    @Scheduled(fixedDelay = 200)
    public void tick() {
        if (clock != null) {
            emitUpTo(clock.missionTime());
        }
    }

    public synchronized int emitUpTo(Instant t) {
        int sent = 0;
        while (next < timeline.size() && !timeline.get(next).at().isAfter(t)) {
            Item item = timeline.get(next++);
            try {
                item.send().run();
            } catch (RuntimeException e) {
                log.warn("REPLAY_SEND_FAILED id={} reason={}", item.id(), e.getMessage());
            }
            digest.update((item.id() + "\n").getBytes(StandardCharsets.UTF_8));
            sent++;
        }
        if (next == timeline.size() && !timeline.isEmpty() && !digestLogged) {
            log.info("REPLAY_DIGEST mission={} records={} sha256={}", props.demoMissionId(), timeline.size(),
                    digest());
            digestLogged = true;
        }
        return sent;
    }

    public synchronized String digest() {
        try {
            return HexFormat.of().formatHex(((MessageDigest) digest.clone()).digest());
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized int sentCount() {
        return next;
    }

    private List<Item> buildTimeline() {
        List<Item> items = new ArrayList<>();
        for (DeviceStatus s : pack.deviceStatus()) {
            items.add(new Item(s.observedAt(), 0, s.statusId(), () -> ingest.deviceStatus(s)));
        }
        for (TrackPoint p : pack.track()) {
            items.add(new Item(p.capturedAt(), 1, p.trackPointId(), () -> ingest.trackPoint(p)));
        }
        List<Observation> observations = pack.observations();
        for (Observation o : observations) {
            items.add(new Item(o.capturedAt(), 2, o.observationId(), () -> ingest.observation(o)));
        }
        Map<String, Instant> obsTime = observations.stream()
                .collect(Collectors.toMap(Observation::observationId, Observation::capturedAt));
        for (ModelOutput m : pack.modelOutputs()) {
            Instant at = obsTime.get(m.observationId());
            if (at == null) {
                log.warn("Model output {} has no observation in the pack; skipped", m.modelOutputId());
                continue;
            }
            items.add(new Item(at, 3, m.modelOutputId(), () -> ingest.modelOutput(m)));
        }
        items.sort(Comparator.comparing(Item::at).thenComparingInt(Item::order).thenComparing(Item::id));
        return List.copyOf(items);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

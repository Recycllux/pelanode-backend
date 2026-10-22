package com.recycllux.pelanode;

import static org.assertj.core.api.Assertions.assertThat;

import com.recycllux.pelanode.domain.ContextRecord;
import com.recycllux.pelanode.domain.DatasetSource;
import com.recycllux.pelanode.domain.DeviceStatus;
import com.recycllux.pelanode.domain.EventContext;
import com.recycllux.pelanode.domain.EventRecord;
import com.recycllux.pelanode.domain.MediaAsset;
import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.domain.ModelOutput;
import com.recycllux.pelanode.domain.Node;
import com.recycllux.pelanode.domain.Observation;
import com.recycllux.pelanode.domain.ReviewAction;
import com.recycllux.pelanode.domain.TrackPoint;
import com.recycllux.pelanode.repo.ContextRepository;
import com.recycllux.pelanode.repo.EventRepository;
import com.recycllux.pelanode.repo.MissionRepository;
import com.recycllux.pelanode.repo.ObservationRepository;
import com.recycllux.pelanode.repo.TrackRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** B-02: every table can store and read back one row with the P0-13 example values. */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "PN_DB_URL=jdbc:h2:mem:roundtrip")
class RepositoryRoundTripTest {

    @Autowired MissionRepository missions;
    @Autowired TrackRepository track;
    @Autowired ObservationRepository observations;
    @Autowired ContextRepository context;
    @Autowired EventRepository events;

    static final Instant T = Instant.parse("2026-10-24T14:32:00Z");

    @Test
    void storesAndReadsEveryEntity() {
        missions.insertNode(new Node("PN-MVP-001", "simulated", List.of("CAM-SURF", "CAM-UW"), null, "1.0"));
        missions.insertMission(new Mission("HACK-001", "Danube high-flow reconnaissance",
                "Post-high-flow reconnaissance, drift replay", "Lower Danube demonstration zone (synthetic)",
                "PN-MVP-001", "drift", Instant.parse("2026-10-24T14:22:00Z"), Instant.parse("2026-10-24T15:04:00Z"),
                List.of(25.90, 43.88, 25.98, 43.92), 142, "simulated", "1.0"));
        missions.insertStatus(new DeviceStatus("ST-1", "PN-MVP-001", "HACK-001", T, 82.0, "online", "fix",
                "ok", "ok", 41.0, "simulated"));
        track.insert(new TrackPoint("TP-HACK-001-0011", "HACK-001", "PN-MVP-001", T, 43.9012, 25.9341,
                3.0, 0.7, 95.0, "replayed", "demo_only", "replayed"));
        observations.insertObservation(new Observation("OBS-HACK-001-S-0142", "HACK-001", "PN-MVP-001", T,
                43.9012, 25.9341, 3.0, "replayed", 0.7, 95.0, null, null, "camera_surface", "above",
                "CAM-SURF", 3210, "OBS-HACK-001-U-0142", 400, "SURF-003210", "ok", 0.84, List.of(),
                Map.of("water_temp_c", 18.4), "replayed", "1.0"));
        observations.insertMedia(new MediaAsset("SURF-003210", "OBS-HACK-001-S-0142", "frame",
                "/media/surface/SURF-003210.jpg", 1280, 720, "image/jpeg", "placeholder", null, null));
        observations.insertModelOutput(new ModelOutput("MO-HACK-001-0142", "OBS-HACK-001-S-0142", "detection",
                "pn-classes-0.1", "anthropogenic_debris", 0.93, List.of(412.0, 288.0, 96.0, 64.0), null,
                "pn-det", "0.1", "simulated", Instant.parse("2026-10-22T16:00:00Z"), null, false, "1.0"));
        events.insertEvent(new EventRecord("EV-0142", "HACK-001", "PN-MVP-001", "anthropogenic_debris", "above",
                "OBS-HACK-001-S-0142", "OBS-HACK-001-U-0142", "MO-HACK-001-0142", T, 43.9012, 25.9341,
                "review_required", List.of("score_above_threshold"), "unreviewed", null, null, T, "replayed", "1.0"));
        context.insertDataset(new DatasetSource("sim_hydro_discharge", "Simulated (NIMH-style)",
                "Simulated discharge", List.of("discharge"), "csv", null, "simulated", "loaded"));
        context.insertRecord(new ContextRecord("sim:STN-SIM-01:2026-10-24T14:22:00Z", "sim_hydro_discharge",
                "Simulated (NIMH-style)", "discharge", 1173.0, "m3/s", "1173", "m3/s",
                Instant.parse("2026-10-24T14:22:00Z"), "2026-10-24T14:22:00Z", "STN-SIM-01",
                "Simulated station 1", 43.85, 25.82, "point", "simulated", null, null), T);
        events.insertContext(new EventContext("EV-0142", "sim:STN-SIM-01:2026-10-24T14:22:00Z",
                "coincides_with", 11.2, -10, "ASSOC-LEVEL-25KM-6H", T));
        events.insertReview(new ReviewAction("RV-000001", "EV-0142", "confirm", null, "Visible bottle",
                "Duty officer (demo)", "6f1c2a9e-3b7d-4c55-9f0e-1a2b3c4d5e6f", "confirmed", T, "1.0"));

        assertThat(missions.findMission("HACK-001")).get()
                .satisfies(m -> assertThat(m.areaBbox()).containsExactly(25.90, 43.88, 25.98, 43.92));
        assertThat(missions.findNode("PN-MVP-001")).get()
                .satisfies(n -> assertThat(n.cameraIds()).containsExactly("CAM-SURF", "CAM-UW"));
        assertThat(missions.latestStatus("PN-MVP-001", "HACK-001", T)).isPresent();
        assertThat(missions.latestStatus("PN-MVP-001", "HACK-001", T.minusSeconds(1))).isEmpty();
        assertThat(track.findByMission("HACK-001", T)).hasSize(1);
        assertThat(track.findByMission("HACK-001", T.minusSeconds(1))).isEmpty();
        assertThat(observations.findObservation("OBS-HACK-001-S-0142")).get()
                .satisfies(o -> assertThat(o.sensors()).containsEntry("water_temp_c", 18.4));
        assertThat(observations.findMediaByObservation("OBS-HACK-001-S-0142")).hasSize(1);
        assertThat(observations.findModelOutput("MO-HACK-001-0142")).get()
                .satisfies(m -> assertThat(m.bbox()).containsExactly(412.0, 288.0, 96.0, 64.0));
        assertThat(events.findByMission("HACK-001", null)).extracting(EventRecord::eventId).containsExactly("EV-0142");
        assertThat(context.findRecords(T.minusSeconds(3600), T, "discharge")).hasSize(1)
                .first().satisfies(r -> assertThat(r.value()).isEqualTo(1173.0));
        assertThat(events.findContext("EV-0142")).hasSize(1);
        assertThat(events.findReviewByRequest("6f1c2a9e-3b7d-4c55-9f0e-1a2b3c4d5e6f")).isPresent();
    }
}

package com.recycllux.pelanode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recycllux.pelanode.replay.Simulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {"PN_DB_URL=jdbc:h2:mem:e2e",
        "PN_WRITE_API_KEY=test-key"})
class ReplayEndToEndTest {

    @Autowired MockMvc mvc;
    @Autowired Simulator simulator;

    static final String API = "/api/v1";

    MockHttpServletRequestBuilder write(String path, String json) {
        return post(API + path).header("X-PN-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @BeforeEach
    void reset() throws Exception {
        mvc.perform(write("/demo/reset", "{\"mission_id\":\"HACK-001\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.event").value(0))
                .andExpect(jsonPath("$.replay.state").value("idle"));
    }

    @Test
    void writesNeedTheKey() throws Exception {
        mvc.perform(post(API + "/demo/reset").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/missions/HACK-001")).andExpect(status().isOk());
    }

    @Test
    void seekCutsReadsAndBuildsTheThreeEvents() throws Exception {
        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"seek\",\"to\":\"2026-10-24T14:35:00Z\"}"))
                .andExpect(jsonPath("$.state").value("paused"));
        mvc.perform(get(API + "/missions/HACK-001/track").param("until", "2026-10-24T14:35:00Z"))
                .andExpect(jsonPath("$", hasSize(14)));
        mvc.perform(get(API + "/events").param("mission_id", "HACK-001").param("until", "2026-10-24T14:35:00Z"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].event_id").value("EV-0142"));

        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"seek\",\"to\":\"2026-10-24T15:04:00Z\"}"))
                .andExpect(jsonPath("$.state").value("ended"));
        mvc.perform(get(API + "/missions/HACK-001/track")).andExpect(jsonPath("$", hasSize(43)));
        mvc.perform(get(API + "/events").param("mission_id", "HACK-001"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].captured_at").value("2026-10-24T14:32:00.000Z"))
                .andExpect(jsonPath("$[1].event_id").value("EV-0143"))
                .andExpect(jsonPath("$[2].event_id").value("EV-0144"))
                .andExpect(jsonPath("$[2].attention").value("review_required"));

        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"seek\",\"to\":\"2026-10-24T14:22:00Z\"}"));
        mvc.perform(get(API + "/events").param("mission_id", "HACK-001")).andExpect(jsonPath("$", hasSize(3)));

        mvc.perform(get(API + "/events/EV-0143"))
                .andExpect(jsonPath("$.perspective").value("below"))
                .andExpect(jsonPath("$.observation.evidence.frame_uri").value("/media/underwater/UW-003892.jpg"))
                .andExpect(jsonPath("$.model_output.class").value("submerged_debris"))
                .andExpect(jsonPath("$.model_output.inference_origin").value("simulated"))
                .andExpect(jsonPath("$.context[?(@.variable=='discharge')].value").value(1173.0))
                .andExpect(jsonPath("$.context[0].relation").value("coincides_with"))
                .andExpect(jsonPath("$.review.status").value("unreviewed"))
                .andExpect(jsonPath("$.device.battery_pct").value(closeTo(80.6, 0.01)));
        mvc.perform(get("/media/underwater/UW-003892.jpg")).andExpect(status().isOk());
    }

    @Test
    void reviewKeepsModelOutputAndIsIdempotent() throws Exception {
        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"seek\",\"to\":\"2026-10-24T15:04:00Z\"}"));
        String confirm = """
                {"action":"confirm","reviewer":"Duty officer (demo)","note":"Visible bottle",
                 "request_id":"00000000-0000-0000-0000-000000000001"}""";
        mvc.perform(write("/events/EV-0142/review", confirm)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.review.resulting_status").value("confirmed"))
                .andExpect(jsonPath("$.event.review.status").value("confirmed"))
                .andExpect(jsonPath("$.event.review.current_label").value("anthropogenic_debris"))
                .andExpect(jsonPath("$.event.review.mission_time").value("2026-10-24T15:04:00.000Z"))
                .andExpect(jsonPath("$.event.review.at").isString())
                .andExpect(jsonPath("$.event.model_output.class").value("anthropogenic_debris"))
                .andExpect(jsonPath("$.event.model_output.confidence").value(0.93));
        mvc.perform(write("/events/EV-0142/review", confirm)).andExpect(status().isOk());
        mvc.perform(get(API + "/events/EV-0142/reviews")).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(write("/events/EV-0144/review", """
                {"action":"relabel","new_label":"natural_debris","reviewer":"Duty officer (demo)",
                 "request_id":"00000000-0000-0000-0000-000000000002"}"""))
                .andExpect(jsonPath("$.event.review.status").value("relabelled"))
                .andExpect(jsonPath("$.event.review.current_label").value("natural_debris"))
                .andExpect(jsonPath("$.event.event_type").value("surface_anomaly"));
        mvc.perform(get(API + "/missions/HACK-001"))
                .andExpect(jsonPath("$.counts.reviewed").value(2))
                .andExpect(jsonPath("$.counts.events").value(3));
    }

    @Test
    void nodeStatusExistsBeforeStart() throws Exception {
        mvc.perform(get(API + "/nodes/PN-MVP-001/status").param("mission_id", "HACK-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observed_at").value("2026-10-24T14:22:00.000Z"))
                .andExpect(jsonPath("$.battery_pct").isNumber());
        mvc.perform(get(API + "/nodes/PN-NOPE/status").param("mission_id", "HACK-001"))
                .andExpect(status().isNotFound());
    }

    @Test
    void replayIsDeterministicAcrossResets() throws Exception {
        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"seek\",\"to\":\"2026-10-24T15:04:00Z\"}"));
        String first = simulator.digest();
        reset();
        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"seek\",\"to\":\"2026-10-24T15:04:00Z\"}"));
        assertThat(simulator.digest()).isEqualTo(first);
        mvc.perform(get(API + "/events").param("mission_id", "HACK-001")).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void invalidTransitionIs409AndDuplicateIngestIs200() throws Exception {
        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"pause\"}")).andExpect(status().isConflict());
        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"seek\",\"to\":\"2026-10-24T14:23:00Z\"}"));
        String tp = """
                {"track_point_id":"TP-HACK-001-0001","mission_id":"HACK-001","node_id":"PN-MVP-001",
                 "captured_at":"2026-10-24T14:22:00.000Z","lat":43.899,"lon":25.92,"h_acc_m":3.0,
                 "speed_mps":0.67,"heading_deg":83.7,"source":"replayed","quality_flag":"demo_only",
                 "data_origin":"replayed"}""";
        mvc.perform(write("/trackpoints", tp)).andExpect(jsonPath("$.rejected", hasSize(0)));
        mvc.perform(write("/trackpoints", tp.replace("43.899", "44.0")))
                .andExpect(jsonPath("$.rejected", hasSize(1)));
    }
}

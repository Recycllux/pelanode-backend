package com.recycllux.pelanode;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** B-04 + B-05: the pack seeds at startup and the read endpoints return P0-13 shapes. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"PN_DB_URL=jdbc:h2:mem:seedread"})
class SeedAndReadTest {

    @Autowired MockMvc mvc;

    @Test
    void missionHasNodeAndCounts() throws Exception {
        mvc.perform(get("/api/v1/missions/HACK-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mission_id").value("HACK-001"))
                .andExpect(jsonPath("$.planned_start").value("2026-10-24T14:22:00.000Z"))
                .andExpect(jsonPath("$.mode").value("drift"))
                .andExpect(jsonPath("$.node.camera_ids", hasSize(2)))
                .andExpect(jsonPath("$.counts.events").value(0));
    }

    @Test
    void contextHasSimulatedSeries() throws Exception {
        mvc.perform(get("/api/v1/missions/HACK-001/context").param("variable", "discharge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasets", hasSize(1)))
                .andExpect(jsonPath("$.records", hasSize(8)))
                .andExpect(jsonPath("$.records[6].value").value(1173.0))
                .andExpect(jsonPath("$.records[0].provenance").value("simulated"));
    }

    @Test
    void unknownMissionIs404AndBadTimeIs422() throws Exception {
        mvc.perform(get("/api/v1/missions/NOPE")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/missions/HACK-001/context").param("from", "2026-10-24T10:00:00"))
                .andExpect(status().isUnprocessableEntity());
    }
}

package com.recycllux.pelanode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** B-16: report summary and the four export formats, after a full replay. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {"PN_DB_URL=jdbc:h2:mem:report", "PN_DEMO_PACK_DIR=../data/demo/hack-001",
        "PN_MEDIA_ROOT=../data/demo/hack-001/media", "PN_EXPORT_DIR=target/test-exports",
        "PN_WRITE_API_KEY=test-key"})
class ReportExportTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    static final String API = "/api/v1";

    MockHttpServletRequestBuilder write(String path, String body) {
        return post(API + path).header("X-PN-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @BeforeEach
    void replayToEnd() throws Exception {
        mvc.perform(write("/demo/reset", "{\"mission_id\":\"HACK-001\"}")).andExpect(status().isOk());
        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"seek\",\"to\":\"2026-10-24T15:04:00Z\"}"))
                .andExpect(jsonPath("$.state").value("ended"));
    }

    @Test
    void summaryCountsTheMission() throws Exception {
        mvc.perform(write("/events/EV-0143/review",
                        "{\"action\":\"confirm\",\"reviewer\":\"alin\",\"request_id\":\"r-report-1\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get(API + "/missions/HACK-001/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mission_id").value("HACK-001"))
                .andExpect(jsonPath("$.events_total").value(3))
                .andExpect(jsonPath("$.observations").value(6))
                .andExpect(jsonPath("$.reviewed").value(1))
                .andExpect(jsonPath("$.path_length_km").isNumber())
                .andExpect(jsonPath("$.events").value(3))
                .andExpect(jsonPath("$.until").value("2026-10-24T15:04:00.000Z"))
                .andExpect(jsonPath("$.period_start").value("2026-10-24T14:22:00.000Z"))
                .andExpect(jsonPath("$.datasets[0].provenance").value("simulated"))
                .andExpect(jsonPath("$.simulation_note", containsString("simulated")));
    }

    @Test
    void summaryFollowsTheReplayClock() throws Exception {
        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"seek\",\"to\":\"2026-10-24T14:35:00Z\"}"));
        mvc.perform(get(API + "/missions/HACK-001/report"))
                .andExpect(jsonPath("$.until").value("2026-10-24T14:35:00.000Z"))
                .andExpect(jsonPath("$.events").value(1))
                .andExpect(jsonPath("$.observations").value(2));
    }

    @Test
    void contextDatasetsCarryStationAndLabel() throws Exception {
        mvc.perform(get(API + "/missions/HACK-001/context"))
                .andExpect(jsonPath("$.datasets[0].lat").isNumber())
                .andExpect(jsonPath("$.datasets[0].lon").isNumber())
                .andExpect(jsonPath("$.datasets[0].variable").isString())
                .andExpect(jsonPath("$.datasets[0].station_name").isString())
                .andExpect(jsonPath("$.datasets[0].label").value("SIMULATED CONTEXT"));
    }

    @Test
    void everyFormatIsWrittenAndServed() throws Exception {
        for (String format : new String[] {"csv", "geojson", "json", "html"}) {
            String body = mvc.perform(write("/missions/HACK-001/export", "{\"format\":\"" + format + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.report_id", startsWith("RP-")))
                    .andExpect(jsonPath("$.file_uri", startsWith("/exports/HACK-001_")))
                    .andReturn().getResponse().getContentAsString();
            JsonNode report = json.readTree(body);
            String uri = report.get("file_uri").asText();
            assertThat(uri).endsWith("." + format);
            String file = mvc.perform(get(uri)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(file).contains("EV-0143");
        }
    }

    @Test
    void unknownFormatIsRejected() throws Exception {
        mvc.perform(write("/missions/HACK-001/export", "{\"format\":\"pdf\"}"))
                .andExpect(status().is4xxClientError());
    }
}

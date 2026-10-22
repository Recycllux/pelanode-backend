package com.recycllux.pelanode;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** B-17: importing organiser data re-runs the association, and the label stays "organiser". */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {"PN_DB_URL=jdbc:h2:mem:ctximport", "PN_DEMO_PACK_DIR=../data/demo/hack-001",
        "PN_MEDIA_ROOT=../data/demo/hack-001/media", "PN_WRITE_API_KEY=test-key"})
class ContextImportTest {

    @Autowired MockMvc mvc;

    static final String API = "/api/v1";

    static final String DATASET = """
            "dataset": {"dataset_id": "org_hydro_ruse", "provider": "EIT Water Hackathon organiser",
              "name": "Ruse gauge, hourly", "variables": ["discharge"], "provenance": "organiser",
              "licence": "Hackathon use only"}""";

    MockHttpServletRequestBuilder write(String path, String body) {
        return post(API + path).header("X-PN-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void organiserRecordIsImportedAndAssociated() throws Exception {
        mvc.perform(write("/demo/reset", "{\"mission_id\":\"HACK-001\"}")).andExpect(status().isOk());
        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"seek\",\"to\":\"2026-10-24T15:04:00Z\"}"))
                .andExpect(jsonPath("$.state").value("ended"));

        String body = "{" + DATASET + """
                , "records": [{"variable": "discharge", "value": 1190.0, "unit": "m3/s",
                  "observed_at": "2026-10-24T14:30:00Z", "station_id": "RUSE", "station_name": "Ruse",
                  "lat": 43.86, "lon": 25.96}]}""";
        mvc.perform(write("/context/import", body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.dataset_created").value(true))
                .andExpect(jsonPath("$.events_with_context").value(3));

        mvc.perform(get(API + "/events/EV-0143"))
                .andExpect(jsonPath("$.context[*].provenance", hasItem("organiser")))
                .andExpect(jsonPath("$.context[*].dataset_id", hasItem("org_hydro_ruse")))
                .andExpect(jsonPath("$.context[*].relation", hasItem("coincides_with")));
        mvc.perform(get(API + "/datasets")).andExpect(jsonPath("$[*].dataset_id", hasItem("org_hydro_ruse")));

        // Same record again: idempotent. Same id with another value: conflict.
        mvc.perform(write("/context/import", body)).andExpect(jsonPath("$.unchanged").value(1));
        mvc.perform(write("/context/import", body.replace("1190.0", "999.0"))).andExpect(status().isConflict());
    }

    @Test
    void badInputIsRejected() throws Exception {
        mvc.perform(write("/context/import", "{" + DATASET.replace("organiser", "rumour") + ", \"records\": []}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(write("/context/import", "{" + DATASET + """
                , "records": [{"variable": "discharge", "value": 1.0, "unit": "m3/s",
                  "observed_at": "2026-10-24T14:30:00"}]}""")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post(API + "/context/import").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}

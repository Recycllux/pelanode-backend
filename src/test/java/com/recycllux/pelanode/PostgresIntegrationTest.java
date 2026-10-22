package com.recycllux.pelanode;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The storyboard on a real PostgreSQL: Flyway schema, seed, replay, events with context, review,
 * report, export and context import. Runs only when PN_TEST_PG_URL is set, e.g.
 * PN_TEST_PG_URL=jdbc:postgresql://localhost:5432/pelanode_test PN_TEST_PG_USER=postgres ./mvnw test
 * The test database is wiped first: never point it at a database you want to keep.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "PN_TEST_PG_URL", matches = "jdbc:postgresql:.+")
class PostgresIntegrationTest {

    static final String API = "/api/v1";

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("PN_TEST_PG_URL");
        String user = System.getenv().getOrDefault("PN_TEST_PG_USER", "postgres");
        String password = System.getenv().getOrDefault("PN_TEST_PG_PASSWORD", "");
        try (Connection c = DriverManager.getConnection(url, user, password); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS public CASCADE");
            s.execute("CREATE SCHEMA public");
        }
        registry.add("PN_DB_URL", () -> url);
        registry.add("PN_DB_USER", () -> user);
        registry.add("PN_DB_PASSWORD", () -> password);
        registry.add("PN_EXPORT_DIR", () -> "target/test-exports-pg");
        registry.add("PN_WRITE_API_KEY", () -> "test-key");
    }

    @Autowired MockMvc mvc;

    MockHttpServletRequestBuilder write(String path, String body) {
        return post(API + path).header("X-PN-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void storyboardRunsOnPostgres() throws Exception {
        mvc.perform(write("/demo/reset", "{\"mission_id\":\"HACK-001\"}")).andExpect(status().isOk());
        mvc.perform(write("/missions/HACK-001/replay", "{\"action\":\"seek\",\"to\":\"2026-10-24T15:04:00Z\"}"))
                .andExpect(jsonPath("$.state").value("ended"));
        mvc.perform(get(API + "/missions/HACK-001/track")).andExpect(jsonPath("$", hasSize(43)));
        mvc.perform(get(API + "/events").param("mission_id", "HACK-001")).andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get(API + "/events/EV-0143"))
                .andExpect(jsonPath("$.captured_at").value("2026-10-24T14:37:00.000Z"))
                .andExpect(jsonPath("$.context[*].relation", hasItem("coincides_with")))
                .andExpect(jsonPath("$.device.battery_pct").value(80.6));
        mvc.perform(write("/events/EV-0143/review",
                        "{\"action\":\"confirm\",\"reviewer\":\"pg\",\"request_id\":\"pg-1\"}"))
                .andExpect(status().isCreated());
        mvc.perform(write("/events/EV-0143/review",
                        "{\"action\":\"confirm\",\"reviewer\":\"pg\",\"request_id\":\"pg-1\"}"))
                .andExpect(jsonPath("$.event.review.status").value("confirmed"));
        mvc.perform(get(API + "/missions/HACK-001/report"))
                .andExpect(jsonPath("$.events").value(3))
                .andExpect(jsonPath("$.reviewed").value(1));
        mvc.perform(write("/missions/HACK-001/export", "{\"format\":\"csv\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.report_id", startsWith("RP-")));
        mvc.perform(write("/context/import", """
                {"dataset": {"dataset_id": "org_pg", "provider": "Organiser", "name": "PG check",
                  "provenance": "organiser"},
                 "records": [{"variable": "discharge", "value": 1190.0, "unit": "m3/s",
                  "observed_at": "2026-10-24T14:30:00+02:00", "lat": 43.86, "lon": 25.96}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported").value(1));

        // Reset twice in a row on the same database: the demo must come back clean every time.
        mvc.perform(write("/demo/reset", "{\"mission_id\":\"HACK-001\"}"))
                .andExpect(jsonPath("$.counts.event").value(0));
        mvc.perform(write("/demo/reset", "{\"mission_id\":\"HACK-001\"}"))
                .andExpect(jsonPath("$.replay.state").value("idle"));
    }
}

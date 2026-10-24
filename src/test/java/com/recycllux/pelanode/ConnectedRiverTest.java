package com.recycllux.pelanode;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recycllux.pelanode.scenario.ScenarioTimeline;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Pins the demo to the reproducible numbers of P0-19 v1.3 (§8, §9, §4.7, §8.7). */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {"PN_DB_URL=jdbc:h2:mem:connected"})
class ConnectedRiverTest {

    @Autowired MockMvc mvc;

    @Test
    void propagationFromCalafatReproducesTheVerifiedLagsAndWindows() throws Exception {
        mvc.perform(get("/api/v1/danube/lags").param("source", "CALAFAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provenance").value("derived_from_organiser"))
                .andExpect(jsonPath("$.method.status").value("proposed_demo_tuning"))
                .andExpect(jsonPath("$.note", containsString("not a pollutant travel time")))
                .andExpect(jsonPath("$.edges[?(@.to_station=='BECHET')].optimal_lag_days_delta_h", contains(1)))
                .andExpect(jsonPath("$.edges[?(@.to_station=='BECHET')].r_delta_h", contains(closeTo(0.888, 0.001))))
                .andExpect(jsonPath("$.edges[?(@.to_station=='GIURGIU')].optimal_lag_days_level", contains(3)))
                .andExpect(jsonPath("$.edges[?(@.to_station=='GIURGIU')].optimal_lag_days_delta_h", contains(2)))
                .andExpect(jsonPath("$.edges[?(@.to_station=='GIURGIU')].r_delta_h_at_level_lag", contains(closeTo(0.689, 0.001))))
                .andExpect(jsonPath("$.edges[?(@.to_station=='GIURGIU')].monitoring_window_days.min", contains(2)))
                .andExpect(jsonPath("$.edges[?(@.to_station=='GIURGIU')].monitoring_window_days.max", contains(3)))
                .andExpect(jsonPath("$.edges[*].quality_class", everyItem(is("A"))))
                .andExpect(jsonPath("$.downstream_windows[*].station_id", hasItem("TURNU_MAGURELE")));
    }

    @Test
    void gappyBulgarianPairsAreNotOperational() throws Exception {
        mvc.perform(get("/api/v1/danube/lags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chains.bg_automatic[0]").value("NOVO_SELO"))
                .andExpect(jsonPath("$.edges[?(@.from_station=='ZAGRAZHDEN' && @.to_station=='NIKOPOL')].quality_class",
                        contains("C")))
                .andExpect(jsonPath("$.edges[?(@.from_station=='ZAGRAZHDEN' && @.to_station=='NIKOPOL')].operational",
                        contains(false)));
        mvc.perform(get("/api/v1/danube/lags").param("source", "BUDAPEST"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void gorniVadinIsThePrimaryCandidateAndKozloduyIsNeighbourInfluenced() throws Exception {
        mvc.perform(get("/api/v1/danube/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method.threshold").value(4.0))
                .andExpect(jsonPath("$.stations[?(@.station_id=='GORNI_VADIN')].candidates_primary", contains(5)))
                .andExpect(jsonPath("$.stations[?(@.station_id=='KOZLODUY')].candidates_influenced", contains(4)))
                .andExpect(jsonPath("$.stations[?(@.station_id=='KOZLODUY')].candidates_primary", contains(0)))
                .andExpect(jsonPath("$.stations[?(@.station_id=='ZAGRAZHDEN')].candidates_primary", contains(1)))
                .andExpect(jsonPath("$.stations[*].station_id", not(hasItem("MALAK_PRESLAVETS"))))
                .andExpect(jsonPath("$.candidates[*].attention", everyItem(is("review_required"))));
        mvc.perform(get("/api/v1/danube/anomalies").param("station", "GORNI_VADIN").param("from", "2025-10-30")
                        .param("to", "2025-10-30"))
                .andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].observation.date").value("2021-10-30"))
                .andExpect(jsonPath("$.candidates[0].observation.scenario_date").value("2025-10-30"))
                .andExpect(jsonPath("$.candidates[0].observation.water_temp_c").value(9.1))
                .andExpect(jsonPath("$.candidates[0].model_output.expected_temp_c").value(13.2))
                .andExpect(jsonPath("$.candidates[0].model_output.robust_z").value(-7.8))
                .andExpect(jsonPath("$.candidates[0].model_output.role").value("primary"))
                .andExpect(jsonPath("$.candidates[0].model_output.influenced", contains("KOZLODUY", "BAYKAL")))
                .andExpect(jsonPath("$.candidates[0].source_attribution").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void danubeRecordsKeepTheSourceDateAndCarryTheScenarioDate() throws Exception {
        mvc.perform(get("/api/v1/danube/series").param("station", "GORNI_VADIN").param("from", "2025-10-30")
                        .param("to", "2021-10-30"))
                .andExpect(jsonPath("$.scenario.badge").value(ScenarioTimeline.BADGE))
                .andExpect(jsonPath("$.series[0].date").value("2021-10-30"))
                .andExpect(jsonPath("$.series[0].scenario_date").value("2025-10-30"))
                .andExpect(jsonPath("$.series[0].water_temp_c").value(9.1));
        mvc.perform(get("/api/v1/danube/stations"))
                .andExpect(jsonPath("$.stations[?(@.station.station_id=='GORNI_VADIN')].chain", contains("bg_automatic")))
                .andExpect(jsonPath("$.contextual_sources[*].compatibility_state", everyItem(is("SCENARIO-ALIGNED"))))
                .andExpect(jsonPath("$.contextual_sources[*].native_overlap_with_danube_days", everyItem(is(0))));
        org.junit.jupiter.api.Assertions.assertNull(ScenarioTimeline.toScenario(LocalDate.of(2024, 2, 29)));
    }

    @Test
    void iskarEventHandsOffToCorabiaAndZagrazhdenInsideTheSharedWindow() throws Exception {
        mvc.perform(post("/api/v1/scenario/tributaries/iskar/event").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"station_id\":\"OREHOVITSA\",\"scenario_date\":\"2025-11-06\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observation.discharge_m3s").value(33.2))
                .andExpect(jsonPath("$.observation.change_24h_m3s").value(5.4))
                .andExpect(jsonPath("$.claim_scope").value("scenario_aligned"))
                .andExpect(jsonPath("$.note", containsString("Scenario handoff only")))
                .andExpect(jsonPath("$.handoff[*].gauge", contains("CORABIA", "ZAGRAZHDEN")))
                .andExpect(jsonPath("$.handoff[0].downstream_windows[*].station_id",
                        contains("TURNU_MAGURELE", "ZIMNICEA", "GIURGIU", "OLTENITA")))
                .andExpect(jsonPath("$.handoff[1].downstream_windows[*].station_id", not(hasItem("NIKOPOL"))))
                .andExpect(jsonPath("$.handoff[1].excluded[*].station_id", contains("NIKOPOL")));
        mvc.perform(post("/api/v1/scenario/tributaries/iskar/event").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario_date\":\"2025-11-29\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/scenario/tributaries/iskar/event").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario_date\":\"2025-10-10\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/scenario/tributaries/iskar"))
                .andExpect(jsonPath("$.shared_window.days").value(121))
                .andExpect(jsonPath("$.suggested_events[*].scenario_date", contains("2025-08-07", "2025-11-06")));
    }

    @Test
    void datasetsCarryTheirCompatibilityState() throws Exception {
        mvc.perform(get("/api/v1/scenario/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasets[?(@.dataset_id=='danube_levels_bg_temperature')].compatibility_state",
                        contains("DEMONSTRATED")))
                .andExpect(jsonPath("$.datasets[?(@.dataset_id=='iskar_discharge')].compatibility_state",
                        contains("SCENARIO-ALIGNED")))
                .andExpect(jsonPath("$.datasets[?(@.dataset_id=='surface_water_chemistry')].compatibility_state",
                        contains("CONTEXT ONLY")));
    }

    @Test
    void pointContextAddsDownstreamWindowsFromTheNearestGauge() throws Exception {
        mvc.perform(get("/api/v1/danube/point").param("lat", "43.77").param("lon", "23.96").param("as_of", "2021-07-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nearest_gauge.station.station_id").value("BECHET"))
                .andExpect(jsonPath("$.nearest_gauge.downstream_windows[*].station_id", hasItem("GIURGIU")));
    }
}

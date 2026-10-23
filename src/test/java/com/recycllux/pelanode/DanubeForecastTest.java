package com.recycllux.pelanode;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {"PN_DB_URL=jdbc:h2:mem:danube"})
class DanubeForecastTest {

    @Autowired MockMvc mvc;

    static final String API = "/api/v1/danube";

    @Test
    void stationsAndProfileComeFromTheOrganiserPack() throws Exception {
        mvc.perform(get(API + "/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provenance").value("organiser"))
                .andExpect(jsonPath("$.stations[*].station.station_id", hasItem("RUSE_BRIDGE")))
                .andExpect(jsonPath("$.stations[*].station.station_id", hasItem("GIURGIU")));
        mvc.perform(get(API + "/profile").param("date", "2021-06-06"))
                .andExpect(jsonPath("$.date").value("2021-06-06"))
                .andExpect(jsonPath("$.points[0].station_id").value("SCHWABELWEIS"))
                .andExpect(jsonPath("$.points[?(@.station_id=='RUSE_BRIDGE')].level_cm", hasItem(480.0)));
        mvc.perform(get(API + "/series").param("station", "ryahovo").param("to", "2021-06-10"))
                .andExpect(jsonPath("$.series.length()").value(5))
                .andExpect(jsonPath("$.series[0].water_temp_c").isNumber());
    }

    @Test
    void forecastForTheMissionReachHasPathAlertsAndRecommendation() throws Exception {
        mvc.perform(get(API + "/forecast").param("mission_id", "HACK-001").param("as_of", "2021-09-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.station.station_id").value("GIURGIU"))
                .andExpect(jsonPath("$.forecast.length()").value(6))
                .andExpect(jsonPath("$.forecast[5].band_low_cm").isNumber())
                .andExpect(jsonPath("$.drivers.length()").value(3))
                .andExpect(jsonPath("$.recommendation.action").isString())
                .andExpect(jsonPath("$.model.training_rows", greaterThan(29)));
        mvc.perform(get(API + "/forecast").param("station", "GIURGIU").param("as_of", "2021-06-10"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(API + "/forecast").param("station", "NOPE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void walkForwardBacktestBeatsPersistence() throws Exception {
        mvc.perform(get(API + "/backtest").param("station", "GIURGIU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skill.length()").value(6))
                .andExpect(jsonPath("$.skill[*].improvement_pct", everyItem(greaterThan(40.0))))
                .andExpect(jsonPath("$.skill[0].mae_model_cm", lessThan(3.0)));
    }

    @Test
    void pointContextJoinsNearestGaugeAndClimateCell() throws Exception {
        mvc.perform(get(API + "/point").param("lat", "43.8998").param("lon", "25.9306").param("as_of", "2021-10-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nearest_gauge.station.station_id").value(anyOf(is("GIURGIU"), is("RUSE_BRIDGE"))))
                .andExpect(jsonPath("$.nearest_gauge.distance_km", lessThan(15.0)))
                .andExpect(jsonPath("$.nearest_gauge.level_cm").isNumber())
                .andExpect(jsonPath("$.climate.length()").value(2))
                .andExpect(jsonPath("$.climate[0].month").value("2024-10"))
                .andExpect(jsonPath("$.climate[0].series.length()").value(12))
                .andExpect(jsonPath("$.climate[1].value").isNumber());
        mvc.perform(get(API + "/point").param("lat", "43.9"))
                .andExpect(status().isUnprocessableEntity());
    }
}

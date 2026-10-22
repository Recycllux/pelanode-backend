package com.recycllux.pelanode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycllux.pelanode.domain.TrackPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** B-03: snake_case JSON, UTC with milliseconds, offsets converted, no offset rejected. */
@SpringBootTest
@TestPropertySource(properties = "PN_DB_URL=jdbc:h2:mem:timefmt")
class TimeFormatTest {

    @Autowired ObjectMapper mapper;

    static final String POINT = """
            {"track_point_id":"TP-1","mission_id":"HACK-001","node_id":"PN-MVP-001",
             "captured_at":"%s","lat":43.9,"lon":25.9,"h_acc_m":3.0,"source":"replayed",
             "quality_flag":"demo_only","data_origin":"replayed"}""";

    @Test
    void writesUtcWithMillisAndSnakeCase() throws Exception {
        TrackPoint p = mapper.readValue(POINT.formatted("2026-10-24T17:32:00+03:00"), TrackPoint.class);
        String out = mapper.writeValueAsString(p);
        assertThat(out).contains("\"captured_at\":\"2026-10-24T14:32:00.000Z\"").contains("\"h_acc_m\":3.0");
    }

    @Test
    void rejectsTimestampWithoutOffset() {
        assertThatThrownBy(() -> mapper.readValue(POINT.formatted("2026-10-24T14:32:00"), TrackPoint.class))
                .hasMessageContaining("explicit offset");
    }
}

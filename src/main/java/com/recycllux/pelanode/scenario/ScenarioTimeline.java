package com.recycllux.pelanode.scenario;

import java.time.LocalDate;
import java.time.Month;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Common 2025 scenario timeline (P0-19 §4.7). A record dated YYYY-MM-DD is shown on 2025-MM-DD; the value and the
 * original source date are never changed, and all statistics run in source time. 29 February has no 2025 date and is
 * marked invalid instead of being shifted.
 */
public final class ScenarioTimeline {

    public static final int YEAR = 2025;
    public static final String BADGE = "SCENARIO 2025 / SOURCE HISTORICAL";
    public static final String NATIVE = "native_2025";
    public static final String HARMONISED = "year_harmonised_to_2025";
    public static final String NOTE = "Records are shown on one common 2025 scenario timeline for this hackathon "
            + "demonstration. A harmonised record is a historical observation, not a 2025 measurement.";

    private ScenarioTimeline() {
    }

    public static LocalDate toScenario(LocalDate source) {
        if (source == null || (source.getMonth() == Month.FEBRUARY && source.getDayOfMonth() == 29)) {
            return null;
        }
        return source.withYear(YEAR);
    }

    /** Maps a date given on the scenario timeline back to the source year of a dataset; other dates pass through. */
    public static LocalDate toSource(LocalDate given, int sourceYear) {
        return given != null && given.getYear() == YEAR && sourceYear != YEAR ? given.withYear(sourceYear) : given;
    }

    public static String method(LocalDate source) {
        return source.getYear() == YEAR ? NATIVE : HARMONISED;
    }

    /** The scenario fields carried by every dated record (§4.7). */
    public static Map<String, Object> fields(LocalDate source, String dataOrigin, String claimScope) {
        LocalDate scenario = toScenario(source);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_date", source);
        m.put("source_year", source.getYear());
        m.put("scenario_date", scenario);
        m.put("time_alignment_method", scenario == null ? "invalid" : method(source));
        m.put("data_origin", dataOrigin);
        m.put("scenario_assumption", scenario != null && !scenario.equals(source));
        m.put("claim_scope", claimScope);
        return m;
    }

    /** Header block for a dataset whose records all come from one source year. */
    public static Map<String, Object> info(int sourceYear) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("year", YEAR);
        m.put("source_year", sourceYear);
        m.put("time_alignment_method", sourceYear == YEAR ? NATIVE : HARMONISED);
        m.put("scenario_assumption", sourceYear != YEAR);
        m.put("badge", sourceYear == YEAR ? "SCENARIO 2025" : BADGE);
        m.put("note", NOTE);
        return m;
    }
}

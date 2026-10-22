package com.recycllux.pelanode.service;

import com.recycllux.pelanode.config.PelanodeProperties;
import com.recycllux.pelanode.domain.ContextRecord;
import com.recycllux.pelanode.domain.EventContext;
import com.recycllux.pelanode.domain.EventRecord;
import com.recycllux.pelanode.repo.ContextRepository;
import com.recycllux.pelanode.repo.EventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ContextAssociator {

    private final ContextRepository context;
    private final EventRepository events;
    private final PelanodeProperties props;

    public ContextAssociator(ContextRepository context, EventRepository events, PelanodeProperties props) {
        this.context = context;
        this.events = events;
        this.props = props;
    }

    private record Rule(String id, double km, Duration window) {
    }

    private Optional<Rule> ruleFor(String variable) {
        return switch (variable) {
            case "discharge", "water_level" -> Optional.of(new Rule(
                    "ASSOC-LEVEL-%dKM-%dH".formatted((int) props.assocLevelKm(), (int) props.assocLevelHours()),
                    props.assocLevelKm(), hours(props.assocLevelHours())));
            case "water_temperature" -> Optional.of(new Rule(
                    "ASSOC-TEMP-%dKM-%dH".formatted((int) props.assocTempKm(), (int) props.assocTempHours()),
                    props.assocTempKm(), hours(props.assocTempHours())));
            default -> Optional.empty();
        };
    }

    private static Duration hours(double h) {
        return Duration.ofMinutes(Math.round(h * 60));
    }

    public int associate(EventRecord event) {
        events.deleteContext(event.eventId());
        if (event.lat() == null || event.lon() == null) {
            return 0;
        }
        Instant t = event.capturedAt();
        Duration widest = hours(Math.max(props.assocLevelHours(), props.assocTempHours()));
        List<ContextRecord> candidates = context.findRecords(t.minus(widest), t.plus(widest), null);

        Map<String, List<ContextRecord>> byDataset = candidates.stream()
                .filter(r -> r.lat() != null && r.lon() != null && r.value() != null)
                .collect(Collectors.groupingBy(ContextRecord::datasetId));

        int stored = 0;
        Instant now = Instant.now();
        for (List<ContextRecord> records : byDataset.values()) {
            Optional<ContextRecord> best = records.stream()
                    .filter(r -> ruleFor(r.variable()).map(rule -> inside(rule, event, r)).orElse(false))
                    .min(Comparator.comparing(r -> Duration.between(t, r.observedAt()).abs()));
            if (best.isPresent()) {
                ContextRecord r = best.get();
                Rule rule = ruleFor(r.variable()).orElseThrow();
                double km = Geo.km(event.lat(), event.lon(), r.lat(), r.lon());
                double offsetMin = Duration.between(t, r.observedAt()).toSeconds() / 60.0;
                events.insertContext(new EventContext(event.eventId(), r.contextRecordId(), "coincides_with",
                        Math.round(km * 10) / 10.0, Math.round(offsetMin * 10) / 10.0, rule.id(), now));
                stored++;
            }
        }
        return stored;
    }

    private static boolean inside(Rule rule, EventRecord e, ContextRecord r) {
        return Duration.between(e.capturedAt(), r.observedAt()).abs().compareTo(rule.window()) <= 0
                && Geo.km(e.lat(), e.lon(), r.lat(), r.lon()) <= rule.km();
    }

    public int reassociateMission(String missionId) {
        return (int) events.findByMission(missionId, null).stream().filter(e -> associate(e) > 0).count();
    }
}

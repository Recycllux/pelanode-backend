package com.recycllux.pelanode.pack;

import com.recycllux.pelanode.config.PelanodeProperties;
import com.recycllux.pelanode.domain.ContextRecord;
import com.recycllux.pelanode.domain.DatasetSource;
import com.recycllux.pelanode.domain.MediaAsset;
import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.repo.ContextRepository;
import com.recycllux.pelanode.repo.MissionRepository;
import com.recycllux.pelanode.repo.ObservationRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SeedLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final DemoPack pack;
    private final PelanodeProperties props;
    private final MissionRepository missions;
    private final ObservationRepository observations;
    private final ContextRepository context;
    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void seedOnStartup() {
        if (!props.seedOnStartup()) {
            return;
        }
        if (missions.findMission(props.demoMissionId()).isPresent()) {
            log.info("SEED_SKIPPED mission={} already present", props.demoMissionId());
            return;
        }
        seed();
    }

    @Transactional
    public void seed() {
        Mission mission = pack.mission();
        if (missions.findNode(mission.nodeId()).isEmpty()) {
            missions.insertNode(pack.node());
        }
        missions.insertMission(mission);

        List<MediaAsset> media = pack.media();
        media.forEach(observations::insertMedia);

        List<DatasetSource> datasets = pack.datasets();
        List<ContextRecord> records = pack.contextRecords();
        Instant now = Instant.now();
        datasets.stream().filter(d -> context.findDataset(d.datasetId()).isEmpty()).forEach(context::insertDataset);
        records.stream().filter(r -> context.findRecord(r.contextRecordId()).isEmpty())
                .forEach(r -> context.insertRecord(r, now));

        log.info("SEED_LOADED mission={} node={} media={} datasets={} context_records={}",
                mission.missionId(), mission.nodeId(), media.size(), datasets.size(), records.size());
    }

    @Transactional
    public void clearMission(String missionId) {
        jdbc.update("DELETE FROM review_action WHERE event_id IN (SELECT event_id FROM event WHERE mission_id = ?)", missionId);
        jdbc.update("DELETE FROM event_context WHERE event_id IN (SELECT event_id FROM event WHERE mission_id = ?)", missionId);
        jdbc.update("DELETE FROM event_evidence WHERE event_id IN (SELECT event_id FROM event WHERE mission_id = ?)", missionId);
        jdbc.update("DELETE FROM alert WHERE event_id IN (SELECT event_id FROM event WHERE mission_id = ?)", missionId);
        jdbc.update("DELETE FROM event WHERE mission_id = ?", missionId);
        jdbc.update("DELETE FROM model_output WHERE observation_id IN (SELECT observation_id FROM observation WHERE mission_id = ?)", missionId);
        jdbc.update("DELETE FROM media_asset WHERE observation_id IN (SELECT observation_id FROM observation WHERE mission_id = ?)", missionId);
        jdbc.update("DELETE FROM media_asset WHERE media_id IN (" + placeholders(pack.media().size()) + ")",
                pack.media().stream().map(MediaAsset::mediaId).toArray());
        jdbc.update("DELETE FROM observation WHERE mission_id = ?", missionId);
        jdbc.update("DELETE FROM track_point WHERE mission_id = ?", missionId);
        jdbc.update("DELETE FROM device_status WHERE mission_id = ?", missionId);
        jdbc.update("DELETE FROM report WHERE mission_id = ?", missionId);
        jdbc.update("DELETE FROM mission WHERE mission_id = ?", missionId);
    }

    private static String placeholders(int n) {
        return n == 0 ? "NULL" : String.join(",", java.util.Collections.nCopies(n, "?"));
    }
}

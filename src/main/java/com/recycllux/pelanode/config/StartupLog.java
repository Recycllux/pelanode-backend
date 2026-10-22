package com.recycllux.pelanode.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartupLog {

    private static final Logger log = LoggerFactory.getLogger(StartupLog.class);

    private final PelanodeProperties props;
    private final Environment env;

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void started() {
        String url = env.getProperty("spring.datasource.url", "");
        String db = url.startsWith("jdbc:postgresql:") ? "postgresql" : url.startsWith("jdbc:h2:") ? "h2" : "other";
        log.info("SERVICE_STARTED profile={} db={} simulated_mode={} mission={} replay_speed={} ai_mode={} "
                        + "context_adapter={} write_key_set={}",
                String.join(",", env.getActiveProfiles()), db, props.simulatedMode(), props.demoMissionId(),
                props.replaySpeed(), props.aiMode(), props.contextAdapter(),
                props.writeApiKey() != null && !props.writeApiKey().isBlank());
    }
}

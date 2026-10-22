package com.recycllux.pelanode.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** One structured line after seeding, so the runbook (P0-17) can check the mode at a glance. */
@Component
public class StartupLog {

    private static final Logger log = LoggerFactory.getLogger(StartupLog.class);

    private final PelanodeProperties props;
    private final Environment env;

    public StartupLog(PelanodeProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void started() {
        log.info("SERVICE_STARTED profile={} simulated_mode={} mission={} replay_speed={} ai_mode={} "
                        + "context_adapter={} write_key_set={}",
                String.join(",", env.getActiveProfiles()), props.simulatedMode(), props.demoMissionId(),
                props.replaySpeed(), props.aiMode(), props.contextAdapter(),
                props.writeApiKey() != null && !props.writeApiKey().isBlank());
    }
}

package com.recycllux.pelanode.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the PN_* settings (P0-11 section 7). */
@ConfigurationProperties(prefix = "pelanode")
public record PelanodeProperties(
        String demoPackDir,
        String mediaRoot,
        String exportDir,
        String demoMissionId,
        boolean seedOnStartup,
        boolean simulatedMode,
        String replaySpeed,
        boolean replayAutostart,
        String aiMode,
        String aiOutputsFile,
        String contextAdapter,
        String contextDir,
        double assocLevelKm,
        double assocLevelHours,
        double assocTempKm,
        double assocTempHours,
        String writeApiKey,
        String corsOrigins) {
}

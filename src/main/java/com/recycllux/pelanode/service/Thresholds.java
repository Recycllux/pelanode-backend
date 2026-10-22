package com.recycllux.pelanode.service;

/**
 * Event builder thresholds (P0-15). Kept in one place so a thresholds.json per
 * model_version can replace the defaults later without touching the builder.
 */
public record Thresholds(double candidate, double review, double quality, long dedupSeconds, double dedupMetres) {

    public static final Thresholds DEFAULT = new Thresholds(0.35, 0.6, 0.4, 30, 50);
}

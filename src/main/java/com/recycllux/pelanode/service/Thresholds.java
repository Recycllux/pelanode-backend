package com.recycllux.pelanode.service;

public record Thresholds(double candidate, double review, double quality, long dedupSeconds, double dedupMetres) {

    public static final Thresholds DEFAULT = new Thresholds(0.35, 0.6, 0.4, 30, 50);
}

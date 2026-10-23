package com.recycllux.pelanode.danube;

public record DanubeStation(
        String stationId,
        String name,
        String nameBg,
        String country,
        String river,
        Double riverKm,
        Double lat,
        Double lon,
        boolean coordsApprox,
        String source) {
}

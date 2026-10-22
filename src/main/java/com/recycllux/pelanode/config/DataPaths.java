package com.recycllux.pelanode.config;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DataPaths {

    private DataPaths() {
    }

    public static Path resolve(String configured) {
        Path given = Path.of(configured);
        if (given.isAbsolute()) {
            return given.normalize();
        }
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(given);
            if (Files.exists(candidate)) {
                return candidate.normalize();
            }
        }
        return given.toAbsolutePath().normalize();
    }
}

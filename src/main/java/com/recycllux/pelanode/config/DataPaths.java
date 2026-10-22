package com.recycllux.pelanode.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the relative data paths in PN_* settings (e.g. data/demo/hack-001) so the app finds the
 * demo pack whichever folder it is started from: the repository root, a sub-folder, or an IDE's
 * module directory. Absolute paths are used as given.
 */
public final class DataPaths {

    private DataPaths() {
    }

    /** The first existing match walking up from the working directory, else the path as given. */
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

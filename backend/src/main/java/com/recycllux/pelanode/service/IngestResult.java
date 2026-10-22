package com.recycllux.pelanode.service;

/** Outcome of storing one record under the P0-13 duplicate rule. */
public enum IngestResult {
    /** New record stored (201). */
    CREATED,
    /** Same ID and same body already stored; nothing created (200). */
    DUPLICATE
}

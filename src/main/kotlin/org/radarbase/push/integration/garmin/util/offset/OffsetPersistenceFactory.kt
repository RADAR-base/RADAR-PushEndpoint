package org.radarbase.push.integration.garmin.util.offset

import java.nio.file.Path

/**
 * Accesses a OffsetRange file using the CSV format. On construction, this will create the file if
 * not present.
 */
interface OffsetPersistenceFactory {
    /**
     * Read offsets from the persistence store. On error, this will return null.
     */
    fun read(path: String): Offsets?

    /**
     * Add a specific Offset to the provided path.
     */
    fun add(path: Path, offset: UserRouteOffset)
}

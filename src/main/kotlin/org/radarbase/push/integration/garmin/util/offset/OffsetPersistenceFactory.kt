package org.radarbase.push.integration.garmin.util.offset

import java.io.Closeable
import java.io.Flushable
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
     * Create a writer to write offsets to the persistence store.
     * Always close the writer after use.
     */
    fun writer(path: Path, startSet: Offsets? = null): Writer

    /** Offset Writer to given persistence type. */
    interface Writer : Closeable, Flushable {
        /** Current offsets. */
        val offsets: Offsets

        /**
         * Add a single offset to the writer.
         */
        fun add(offset: UserRouteOffset) = offsets.add(offset)

        /**
         * Add all elements in given offset set to the writer.
         */
        fun addAll(offsets: Offsets) = offsets.addAll(offsets)

        /**
         * Trigger an asynchronous write operation. If this is called multiple times before the
         * operation is executed, the operation will be executed only once.
         */
        fun triggerWrite()
    }
}

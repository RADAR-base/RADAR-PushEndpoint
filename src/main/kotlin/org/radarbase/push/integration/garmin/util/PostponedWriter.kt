package org.radarbase.push.integration.garmin.util

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.Flushable
import java.io.IOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

/**
 * File writer where data is written in a separate thread with a timeout.
 */
abstract class PostponedWriter
/**
 * Constructor with timeout.
 * @param name thread name.
 * @param timeout maximum time between triggering a write and actually writing the file.
 * @param timeoutUnit timeout unit.
 */
    (private val name: String, private val timeout: Long, private val timeoutUnit: TimeUnit) : Closeable,
    Flushable {

    private val writeFuture = AtomicReference<Future<*>?>(null)
    private val executor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, name) }

    /**
     * Trigger a write to occur within set timeout. If a write was already triggered earlier but has
     * not yet taken place, the write will occur earlier.
     */
    fun triggerWrite() {
        if (writeFuture.get() == null) {
            executor.schedule(::startWrite, timeout, timeoutUnit)
                .also { newWriteFuture ->
                    if (!writeFuture.compareAndSet(null, newWriteFuture)) {
                        newWriteFuture.cancel(false)
                    }
                }
        }
    }

    /** Start the write in the writer thread.  */
    private fun startWrite() {
        writeFuture.set(null)
        doWrite()
    }

    /** Perform the write.  */
    protected abstract fun doWrite()

    @Throws(IOException::class)
    override fun close() {
        doFlush(true)
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            logger.error("Failed to write {} data: interrupted", name)
        }

    }

    @Throws(IOException::class)
    private fun doFlush(shutdown: Boolean) {
        val localFuture = executor.submit(::startWrite)
            .also { writeFuture.getAndSet(it)?.cancel(false) }

        if (shutdown) {
            executor.shutdown()
        }

        try {
            localFuture.get(30, TimeUnit.SECONDS)
        } catch (ex: CancellationException) {
            logger.debug("File flush for {} was cancelled, another thread executed it", name)
        } catch (ex: ExecutionException) {
            logger.error("Failed to write data for {}", name, ex.cause)
            throw IOException("Failed to write data", ex.cause)
        } catch (e: InterruptedException) {
            logger.error("Failed to write {} data: timeout", name)
        } catch (e: TimeoutException) {
            logger.error("Failed to write {} data: timeout", name)
        }
    }

    @Throws(IOException::class)
    override fun flush() {
        doFlush(false)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PostponedWriter::class.java)
    }
}

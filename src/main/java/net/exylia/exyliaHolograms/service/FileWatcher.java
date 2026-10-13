package net.exylia.exyliaHolograms.service;

import net.exylia.lib.task.TaskScheduler;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Notices a hand edit of {@code holograms.yml} and applies it, so editing the file is as live as
 * editing in game.
 *
 * <p>Saving a file is often several writes in a row, so the reload waits until the file has been
 * quiet for half a second. A write this plugin made itself is recognised by its contents and
 * ignored; reloading after every edit made in game would be a reload for nothing.
 */
public final class FileWatcher {

    /** How long the file must stay unchanged before it is read, in ticks. */
    private static final long QUIET_TICKS = 10L;

    private final Path file;
    private final TaskScheduler tasks;
    private final LongSupplier ownWrite;
    private final Runnable reload;
    private final Logger logger;
    private final AtomicInteger generation = new AtomicInteger();
    private volatile WatchService service;
    private volatile byte[] lastSeen = new byte[0];

    /**
     * @param file     the file to watch
     * @param tasks    where the reload runs
     * @param ownWrite when this plugin last wrote the file, in epoch milliseconds
     * @param reload   what to do with a hand edit, on the main thread
     */
    public FileWatcher(Path file, TaskScheduler tasks, LongSupplier ownWrite, Runnable reload, Logger logger) {
        this.file = file;
        this.tasks = tasks;
        this.ownWrite = ownWrite;
        this.reload = reload;
        this.logger = logger;
    }

    public void start() {
        stop();
        remember();
        try {
            WatchService watching = FileSystems.getDefault().newWatchService();
            file.getParent().register(watching, StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            service = watching;
            Thread thread = new Thread(() -> loop(watching), "ExyliaHolograms-file-watcher");
            thread.setDaemon(true);
            thread.start();
        } catch (IOException | UnsupportedOperationException failure) {
            logger.warning("Could not watch " + file.getFileName() + " for changes (" + failure.getMessage()
                    + "); use /holo reload after editing it.");
        }
    }

    public void stop() {
        WatchService watching = service;
        service = null;
        if (watching != null) {
            try {
                watching.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    /** Takes the file as it is now as already seen, after this plugin wrote or read it. */
    public void remember() {
        lastSeen = read();
    }

    private void loop(WatchService watching) {
        try {
            while (true) {
                WatchKey key = watching.take();
                boolean ours = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.context() instanceof Path changed && changed.equals(file.getFileName())) {
                        ours = true;
                    }
                }
                key.reset();
                if (ours) {
                    int mine = generation.incrementAndGet();
                    tasks.runLater(QUIET_TICKS, () -> settle(mine));
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException stopped) {
            // Stopped on purpose.
        }
    }

    /** Runs once the file has been quiet: reloads it if somebody else changed it. */
    private void settle(int mine) {
        if (mine != generation.get()) {
            return;
        }
        byte[] now = read();
        if (Arrays.equals(now, lastSeen)) {
            return;
        }
        lastSeen = now;
        if (System.currentTimeMillis() - ownWrite.getAsLong() < 1500L) {
            return;
        }
        reload.run();
        remember();
    }

    private byte[] read() {
        try {
            return Files.exists(file) ? Files.readAllBytes(file) : new byte[0];
        } catch (IOException unreadable) {
            return new byte[0];
        }
    }
}

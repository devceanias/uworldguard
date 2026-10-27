package com.tricrotism.uworldguard.blacklist;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Where the {@code log} action writes: the console straight away, and a daily file under
 * {@code logs/}. File lines queue up and are written in one batch every couple of seconds, off the
 * region threads, so a dispenser clock tripping the list never puts disk I/O on a tick.
 */
@NullMarked final class BlacklistLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final long FLUSH_SECONDS = 2L;

    private record Line(long millis, String text) {}

    private final Plugin plugin;
    private final Path folder;
    private final Queue<Line> pending = new ConcurrentLinkedQueue<>();
    private final Object writeLock = new Object();
    private volatile boolean console = true;
    private volatile boolean file = true;
    private @Nullable ScheduledTask flushTask;

    BlacklistLog(final Plugin plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder().toPath().resolve("logs");
    }

    void configure(final boolean console, final boolean file) {
        this.console = console;
        this.file = file;
    }

    void write(final String world, final String text) {
        if (console) {
            plugin.getLogger().info("[Blacklist] [" + world + "] " + text);
        }
        if (file) {
            pending.add(new Line(System.currentTimeMillis(), "[" + world + "] " + text));
        }
    }

    synchronized void start() {
        if (flushTask == null) {
            flushTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                task -> flush(), FLUSH_SECONDS, FLUSH_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * Cancels the batch and writes what is still queued, on the calling thread, so nothing logged
     * just before a shutdown is lost.
     */
    synchronized void stop() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flush();
    }

    void flush() {
        if (pending.isEmpty()) {
            return;
        }
        synchronized (writeLock) {
            final StringBuilder out = new StringBuilder();
            LocalDate day = null;
            Line line;
            while ((line = pending.poll()) != null) {
                final LocalDateTime at = LocalDateTime.ofInstant(Instant.ofEpochMilli(line.millis()), ZoneId.systemDefault());
                if (day != null && !day.equals(at.toLocalDate())) {
                    append(day, out);
                    out.setLength(0);
                }
                day = at.toLocalDate();
                out.append('[').append(TIME.format(at)).append("] ").append(line.text()).append(System.lineSeparator());
            }
            if (day != null) {
                append(day, out);
            }
        }
    }

    private void append(final LocalDate day, final CharSequence text) {
        final Path path = folder.resolve("blacklist-" + day + ".log");
        try {
            Files.createDirectories(folder);
            Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (final IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write the blacklist log " + path.getFileName(), e);
        }
    }
}

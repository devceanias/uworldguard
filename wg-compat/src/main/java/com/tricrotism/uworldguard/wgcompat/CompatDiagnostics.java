// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.tricrotism.uworldguard.wgcompat;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counters behind {@code /uwg compat}. The three a consumer can hit per event are {@link Tally}s and
 * the rest are {@link LongAdder}s. The per-method stub map is only touched on cold (stubbed) paths.
 */
@NullMarked
public final class CompatDiagnostics {

    public static final Tally QUERIES = new Tally();
    public static final Tally REGION_READS = new Tally();
    public static final LongAdder REGION_MUTATIONS = new LongAdder();
    public static final LongAdder WRAPS = new LongAdder();
    public static final LongAdder FLAG_REGISTRATIONS = new LongAdder();
    public static final Tally SESSION_DISPATCHES = new Tally();

    /**
     * A count for paths a consumer can hit on every event, without the compare-and-swap
     * {@link LongAdder} pays per add. Each add is a plain write to a slot picked by thread, one slot
     * per cache line, so two threads on one slot can lose a count and an unlocked sum can trail the
     * writes. That is fine for numbers that only say how busy the layer is.
     */
    public static final class Tally {
        private static final int SLOTS = 32;
        private static final int STRIDE = 8;
        private final long[] counts = new long[SLOTS * STRIDE];

        public void increment() {
            counts[(int) (Thread.currentThread().threadId() & (SLOTS - 1)) * STRIDE]++;
        }

        public long sum() {
            long total = 0;
            for (int i = 0; i < counts.length; i += STRIDE) {
                total += counts[i];
            }
            return total;
        }
    }

    public static final LongAdder EVENTS_FIRED = new LongAdder();
    public static final LongAdder FLAG_PARSE_FAILURES = new LongAdder();

    /**
     * Last rejection reason per bridged flag. Bounded by the number of flags consumers registered,
     * and only the most recent reason is kept — this exists to answer "why won't it take my value",
     * which the previous attempt answers, not to build a history.
     */
    private static final ConcurrentHashMap<String, String> PARSE_ERRORS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, LongAdder> STUB_HITS = new ConcurrentHashMap<>();

    private CompatDiagnostics() {
    }

    /**
     * Record a hit on a stubbed API member, e.g. {@code "RegionManager.load"}. Cold paths only.
     */
    public static void stub(final String member) {
        STUB_HITS.computeIfAbsent(member, key -> new LongAdder()).increment();
    }

    /**
     * Record that a consumer's flag refused an input, and why it said it refused.
     */
    public static void flagParseFailure(final String flag, final @Nullable String reason) {
        FLAG_PARSE_FAILURES.increment();
        PARSE_ERRORS.put(flag, reason == null || reason.isBlank() ? "no reason given" : reason);
    }

    /**
     * Snapshot of the last rejection reason per flag, sorted by flag name.
     */
    public static Map<String, String> flagParseErrors() {
        return new TreeMap<>(PARSE_ERRORS);
    }

    /**
     * Snapshot of stubbed-member hit counts, sorted by member name.
     */
    public static Map<String, Long> stubHits() {
        final Map<String, Long> snapshot = new TreeMap<>();
        STUB_HITS.forEach((member, count) -> snapshot.put(member, count.sum()));
        return snapshot;
    }
}

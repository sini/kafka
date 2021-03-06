/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.streams.kstream;

import org.apache.kafka.streams.processor.TimestampExtractor;
import org.apache.kafka.streams.state.WindowBytesStoreSupplier;

import java.time.Duration;
import java.util.Map;

/**
 * The window specification interface for fixed size windows that is used to define window boundaries and grace period.
 *
 * Grace period defines how long to wait on late events, where lateness is defined as (stream_time - record_timestamp).
 *
 * @param <W> type of the window instance
 * @see TimeWindows
 * @see UnlimitedWindows
 * @see JoinWindows
 * @see SessionWindows
 * @see TimestampExtractor
 */
public abstract class Windows<W extends Window> {

    private long maintainDurationMs = 24 * 60 * 60 * 1000L; // default: one day
    @Deprecated public int segments = 3;

    private Duration grace;

    protected Windows() {}

    /**
     * Reject late events that arrive more than {@code millisAfterWindowEnd}
     * after the end of its window.
     *
     * Lateness is defined as (stream_time - record_timestamp).
     *
     * @param millisAfterWindowEnd The grace period to admit late-arriving events to a window.
     * @return this updated builder
     */
    public Windows<W> grace(final long millisAfterWindowEnd) {
        if (millisAfterWindowEnd < 0) {
            throw new IllegalArgumentException("Grace period must not be negative.");
        }

        grace = Duration.ofMillis(millisAfterWindowEnd);

        return this;
    }

    /**
     * Return the window grace period (the time to admit
     * late-arriving events after the end of the window.)
     *
     * Lateness is defined as (stream_time - record_timestamp).
     */
    @SuppressWarnings("deprecation") // continuing to support Windows#maintainMs/segmentInterval in fallback mode
    public long gracePeriodMs() {
        // NOTE: in the future, when we remove maintainMs,
        // we should default the grace period to 24h to maintain the default behavior,
        // or we can default to (24h - size) if you want to be super accurate.
        return grace != null ? grace.toMillis() : maintainMs() - size();
    }

    /**
     * Set the window maintain duration (retention time) in milliseconds.
     * This retention time is a guaranteed <i>lower bound</i> for how long a window will be maintained.
     *
     * @param durationMs the window retention time in milliseconds
     * @return itself
     * @throws IllegalArgumentException if {@code durationMs} is negative
     * @deprecated since 2.1. Use {@link Materialized#withRetention(long)}
     *             or directly configure the retention in a store supplier and use {@link Materialized#as(WindowBytesStoreSupplier)}.
     */
    @Deprecated
    public Windows<W> until(final long durationMs) throws IllegalArgumentException {
        if (durationMs < 0) {
            throw new IllegalArgumentException("Window retention time (durationMs) cannot be negative.");
        }
        maintainDurationMs = durationMs;

        return this;
    }

    /**
     * Return the window maintain duration (retention time) in milliseconds.
     *
     * @return the window maintain duration
     * @deprecated since 2.1. Use {@link Materialized#retention} instead.
     */
    @Deprecated
    public long maintainMs() {
        return maintainDurationMs;
    }

    /**
     * Return the segment interval in milliseconds.
     *
     * @return the segment interval
     * @deprecated since 2.1. Instead, directly configure the segment interval in a store supplier and use {@link Materialized#as(WindowBytesStoreSupplier)}.
     */
    @Deprecated
    public long segmentInterval() {
        // Pinned arbitrarily to a minimum of 60 seconds. Profiling may indicate a different value is more efficient.
        final long minimumSegmentInterval = 60_000L;
        // Scaled to the (possibly overridden) retention period
        return Math.max(maintainMs() / (segments - 1), minimumSegmentInterval);
    }


    /**
     * Set the number of segments to be used for rolling the window store.
     * This function is not exposed to users but can be called by developers that extend this class.
     *
     * @param segments the number of segments to be used
     * @return itself
     * @throws IllegalArgumentException if specified segments is small than 2
     * @deprecated since 2.1 Override segmentInterval() instead.
     */
    @Deprecated
    protected Windows<W> segments(final int segments) throws IllegalArgumentException {
        if (segments < 2) {
            throw new IllegalArgumentException("Number of segments must be at least 2.");
        }
        this.segments = segments;

        return this;
    }

    /**
     * Create all windows that contain the provided timestamp, indexed by non-negative window start timestamps.
     *
     * @param timestamp the timestamp window should get created for
     * @return a map of {@code windowStartTimestamp -> Window} entries
     */
    public abstract Map<Long, W> windowsFor(final long timestamp);

    /**
     * Return the size of the specified windows in milliseconds.
     *
     * @return the size of the specified windows
     */
    public abstract long size();
}

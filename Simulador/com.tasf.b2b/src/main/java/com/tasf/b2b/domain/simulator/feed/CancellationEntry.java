package com.tasf.b2b.domain.simulator.feed;

import java.time.Instant;

public record CancellationEntry(String flightKey, Instant depTimeUtc) {}

package com.tasf.b2b.domain.simulator.feed;

public interface CancellationFeed {

    /**
     * Returns the next CancellationEntry in chronological order, or null if exhausted.
     * Once null is returned, subsequent calls also return null.
     */
    CancellationEntry next();
}

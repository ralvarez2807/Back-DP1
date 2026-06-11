package com.tasf.b2b.domain.simulator.feed;

import com.tasf.b2b.domain.model.graph.immovable.ShipmentDataDTO;

public interface ShipmentFeed {

    /**
     * Returns the next ShipmentDataDTO in chronological order, or null if exhausted.
     * Once null is returned, subsequent calls also return null.
     */
    ShipmentDataDTO next();
}

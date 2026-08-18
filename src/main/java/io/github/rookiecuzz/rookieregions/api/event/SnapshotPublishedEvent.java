package io.github.rookiecuzz.rookieregions.api.event;

import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Notification emitted after a newer immutable snapshot becomes visible. */
public final class SnapshotPublishedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final RegionSnapshot previous;
    private final RegionSnapshot current;

    public SnapshotPublishedEvent(RegionSnapshot previous, RegionSnapshot current) {
        this.previous = Objects.requireNonNull(
                previous,
                "previous snapshot cannot be null"
        );
        this.current = Objects.requireNonNull(
                current,
                "current snapshot cannot be null"
        );
        if(current.revision() <= previous.revision()){
            throw new IllegalArgumentException(
                    "published snapshot revision must strictly increase"
            );
        }
    }

    public RegionSnapshot previous() {
        return previous;
    }

    public RegionSnapshot current() {
        return current;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

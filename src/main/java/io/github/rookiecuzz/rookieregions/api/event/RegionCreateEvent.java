package io.github.rookiecuzz.rookieregions.api.event;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Notification that a native region has been created. */
public final class RegionCreateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;
    private final Subject actor;

    public RegionCreateEvent(Region region, Subject actor) {
        this.region = Objects.requireNonNull(region, "created region cannot be null");
        this.actor = Objects.requireNonNull(actor, "create actor cannot be null");
    }

    public Region region() {
        return region;
    }

    public Subject actor() {
        return actor;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

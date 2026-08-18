package io.github.rookiecuzz.rookieregions.api.event;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Notification emitted after a subject has left a local region. */
public final class RegionLeaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Subject subject;
    private final Region region;

    public RegionLeaveEvent(Subject subject, Region region) {
        this.subject = Objects.requireNonNull(subject, "leaving subject cannot be null");
        this.region = Objects.requireNonNull(region, "left region cannot be null");
    }

    public Subject subject() {
        return subject;
    }

    public Region region() {
        return region;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

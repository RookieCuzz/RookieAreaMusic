package io.github.rookiecuzz.rookieregions.api.event;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Notification emitted after a subject has entered a local region. */
public final class RegionEnterEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Subject subject;
    private final Region region;

    public RegionEnterEvent(Subject subject, Region region) {
        this.subject = Objects.requireNonNull(subject, "entering subject cannot be null");
        this.region = Objects.requireNonNull(region, "entered region cannot be null");
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

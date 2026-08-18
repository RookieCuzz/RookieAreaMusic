package io.github.rookiecuzz.rookieregions.api.event;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Notification containing both immutable versions of an updated region. */
public final class RegionUpdateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Region previous;
    private final Region current;
    private final Subject actor;

    public RegionUpdateEvent(Region previous, Region current, Subject actor) {
        this.previous = Objects.requireNonNull(previous, "previous region cannot be null");
        this.current = Objects.requireNonNull(current, "current region cannot be null");
        this.actor = Objects.requireNonNull(actor, "update actor cannot be null");
        if(!previous.key().equals(current.key())){
            throw new IllegalArgumentException("updated region key cannot change");
        }
    }

    public Region previous() {
        return previous;
    }

    public Region current() {
        return current;
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

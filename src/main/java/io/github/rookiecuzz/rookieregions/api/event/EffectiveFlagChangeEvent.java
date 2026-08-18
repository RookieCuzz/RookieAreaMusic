package io.github.rookiecuzz.rookieregions.api.event;

import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.rule.Flag;
import io.github.rookiecuzz.rookieregions.rule.RuleResolution;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Notification that a subject's resolved value for one flag has changed. */
public final class EffectiveFlagChangeEvent<T> extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Subject subject;
    private final WorldId world;
    private final RuleResolution<T> previous;
    private final RuleResolution<T> current;

    public EffectiveFlagChangeEvent(Subject subject,
                                    WorldId world,
                                    RuleResolution<T> previous,
                                    RuleResolution<T> current) {
        this.subject = Objects.requireNonNull(subject, "flag subject cannot be null");
        this.world = Objects.requireNonNull(world, "flag world cannot be null");
        this.previous = Objects.requireNonNull(
                previous,
                "previous flag resolution cannot be null"
        );
        this.current = Objects.requireNonNull(
                current,
                "current flag resolution cannot be null"
        );
        if(!previous.flag().equals(current.flag())){
            throw new IllegalArgumentException("effective flag cannot change definition");
        }
    }

    public Subject subject() {
        return subject;
    }

    public WorldId world() {
        return world;
    }

    public Flag<T> flag() {
        return current.flag();
    }

    public RuleResolution<T> previous() {
        return previous;
    }

    public RuleResolution<T> current() {
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

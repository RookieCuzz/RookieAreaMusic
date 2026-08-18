package io.github.rookiecuzz.rookieregions.editor.model;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Thread-safe ownership of one session per actor and one lock per region. */
public final class RegionEditorManager {
    private final Map<UUID, RegionEditSession> sessions = new LinkedHashMap<>();
    private final Map<RegionKey, LockOwner> locks = new LinkedHashMap<>();

    public synchronized void begin(RegionEditSession session) {
        Objects.requireNonNull(session, "editor session cannot be null");
        UUID actor = session.actor();
        if(sessions.containsKey(actor)) {
            throw new IllegalStateException("actor already has an editor session");
        }
        LockOwner owner = locks.get(session.key());
        if(owner != null) {
            throw new IllegalStateException(
                    "region is already locked by actor " + owner.actor()
            );
        }
        sessions.put(actor, session);
        locks.put(session.key(), new LockOwner(actor, session.sessionId()));
    }

    public synchronized void begin(UUID actor, RegionEditSession session) {
        Objects.requireNonNull(actor, "editor actor cannot be null");
        Objects.requireNonNull(session, "editor session cannot be null");
        if(!actor.equals(session.actor())) {
            throw new IllegalArgumentException("actor must match the session actor");
        }
        begin(session);
    }

    public synchronized Optional<RegionEditSession> session(UUID actor) {
        return Optional.ofNullable(sessions.get(actor));
    }

    public synchronized RegionEditSession get(UUID actor) {
        return sessions.get(actor);
    }

    /** Builds a candidate while deliberately retaining both session and lock. */
    public synchronized Region finish(UUID actor) {
        return requireSession(actor).candidate();
    }

    /** Builds a save request while deliberately retaining session and lock. */
    public synchronized RegionSaveRequest finishRequest(UUID actor) {
        return requireSession(actor).saveRequest();
    }

    /** Confirmation retries also retain the same active session and lock. */
    public synchronized RegionSaveRequest finishRequest(UUID actor,
                                                        Optional<String> confirmationToken) {
        return requireSession(actor).saveRequest(confirmationToken);
    }

    public synchronized RegionSaveRequest finishRequest(UUID actor,
                                                        String confirmationToken) {
        return requireSession(actor).saveRequest(confirmationToken);
    }

    /**
     * Completes a confirmed successful save. The session ID guards against a
     * stale asynchronous result releasing a newer session's lock.
     */
    public synchronized RegionEditSession markSaved(UUID actor, String sessionId) {
        RegionEditSession session = requireSession(actor);
        if(sessionId == null || !session.sessionId().equals(sessionId.trim())) {
            throw new IllegalStateException("saved result does not match the active session");
        }
        return removeAndUnlock(session);
    }

    public synchronized RegionEditSession markSaved(UUID actor) {
        RegionEditSession session = requireSession(actor);
        return markSaved(actor, session.sessionId());
    }

    public synchronized RegionEditSession saved(UUID actor, String sessionId) {
        return markSaved(actor, sessionId);
    }

    /** Explicit user abandonment; unlike finish, cancellation releases the lock. */
    public synchronized RegionEditSession cancel(UUID actor) {
        return removeAndUnlock(requireSession(actor));
    }

    public synchronized RegionEditSession abandonAndUnlock(UUID actor) {
        return cancel(actor);
    }

    public synchronized boolean isLocked(RegionKey key) {
        return key != null && locks.containsKey(key);
    }

    public synchronized Optional<UUID> lockOwner(RegionKey key) {
        LockOwner owner = key == null ? null : locks.get(key);
        return owner == null ? Optional.empty() : Optional.of(owner.actor());
    }

    public synchronized int size() {
        return sessions.size();
    }

    public synchronized Map<UUID, RegionEditSession> snapshot() {
        return Map.copyOf(sessions);
    }

    /** Explicit shutdown cleanup, never used as part of finish/save handling. */
    public synchronized void clear() {
        sessions.clear();
        locks.clear();
    }

    private RegionEditSession requireSession(UUID actor) {
        Objects.requireNonNull(actor, "editor actor cannot be null");
        RegionEditSession session = sessions.get(actor);
        if(session == null) {
            throw new IllegalStateException("actor has no active editor session");
        }
        return session;
    }

    private RegionEditSession removeAndUnlock(RegionEditSession session) {
        LockOwner expected = new LockOwner(session.actor(), session.sessionId());
        if(!locks.remove(session.key(), expected)) {
            throw new IllegalStateException("editor region lock invariant is broken");
        }
        if(!sessions.remove(session.actor(), session)) {
            locks.put(session.key(), expected);
            throw new IllegalStateException("editor session invariant is broken");
        }
        return session;
    }

    private record LockOwner(UUID actor, String sessionId) {
    }
}

package io.github.rookiecuzz.rookieregions.editor.model;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionDomain;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveRequest;
import io.github.rookiecuzz.rookieregions.mutation.SaveMode;
import io.github.rookiecuzz.rookieregions.rule.FlagValue;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Editor metadata plus a mutable draft and immutable candidate production. */
public final class RegionEditSession {
    private final String sessionId;
    private final UUID actor;
    private final SaveMode mode;
    private final RegionKey key;
    private final Region base;
    private final long snapshotRevision;
    private final Optional<String> targetFingerprint;
    private final RegionDraft draft;

    public RegionEditSession(String sessionId,
                             UUID actor,
                             SaveMode mode,
                             RegionKey key,
                             Region base,
                             long snapshotRevision,
                             Optional<String> targetFingerprint,
                             RegionDraft draft) {
        this.sessionId = requireText(sessionId, "editor session ID");
        this.actor = Objects.requireNonNull(actor, "editor actor cannot be null");
        this.mode = Objects.requireNonNull(mode, "save mode cannot be null");
        this.key = Objects.requireNonNull(key, "region key cannot be null");
        this.base = base;
        if(snapshotRevision < 0L) {
            throw new IllegalArgumentException("snapshot revision cannot be negative");
        }
        this.snapshotRevision = snapshotRevision;
        this.targetFingerprint = normalizeFingerprint(targetFingerprint);
        this.draft = Objects.requireNonNull(draft, "region draft cannot be null");
        validateConstruction();
    }

    public RegionEditSession(String sessionId,
                             UUID actor,
                             SaveMode mode,
                             RegionKey key,
                             Region base,
                             long snapshotRevision,
                             String targetFingerprint,
                             RegionDraft draft) {
        this(
                sessionId,
                actor,
                mode,
                key,
                base,
                snapshotRevision,
                Optional.ofNullable(targetFingerprint),
                draft
        );
    }

    public String sessionId() {
        return sessionId;
    }

    public UUID actor() {
        return actor;
    }

    public SaveMode mode() {
        return mode;
    }

    public RegionKey key() {
        return key;
    }

    public Optional<Region> base() {
        return Optional.ofNullable(base);
    }

    public long snapshotRevision() {
        return snapshotRevision;
    }

    public Optional<String> targetFingerprint() {
        return targetFingerprint;
    }

    public RegionDraft draft() {
        return draft;
    }

    /** Builds a detached immutable Region from the draft's current state. */
    public Region candidate() {
        validateExactWorld(key.world(), draft.world(), "draft");
        Region.Builder builder = Region.builder(key, draft.buildShape());
        if(base != null) {
            builder.priority(base.priority())
                    .owners(base.owners())
                    .members(base.members());
            base.parent().ifPresent(builder::parent);
            for(FlagValue<?> flag : base.flags().values()) {
                builder.flagValue(flag);
            }
        } else {
            builder.parent(RegionKey.global(key.world()))
                    .owners(RegionDomain.builder().player(actor).build());
        }
        return builder.build();
    }

    public RegionSaveRequest saveRequest(Optional<String> confirmationToken) {
        return new RegionSaveRequest(
                sessionId,
                mode,
                candidate(),
                snapshotRevision,
                targetFingerprint,
                confirmationToken
        );
    }

    public RegionSaveRequest saveRequest() {
        return saveRequest(Optional.empty());
    }

    public RegionSaveRequest saveRequest(String confirmationToken) {
        return saveRequest(Optional.ofNullable(confirmationToken));
    }

    private void validateConstruction() {
        if(key.isGlobal()) {
            throw new IllegalArgumentException("the finite-shape editor cannot edit __global__");
        }
        validateExactWorld(key.world(), draft.world(), "draft");
        if(mode == SaveMode.CREATE) {
            if(base != null) {
                throw new IllegalArgumentException("CREATE session cannot have a base region");
            }
            if(targetFingerprint.isPresent()) {
                throw new IllegalArgumentException(
                        "CREATE session cannot have a target fingerprint"
                );
            }
            return;
        }
        if(base == null) {
            throw new IllegalArgumentException("EDIT session requires a base region");
        }
        if(!base.key().equals(key)) {
            throw new IllegalArgumentException("base region key must match the session key");
        }
        validateExactWorld(key.world(), base.key().world(), "base region");
        base.parent().ifPresent(parent ->
                validateExactWorld(key.world(), parent.world(), "base parent"));
        if(targetFingerprint.isEmpty()) {
            throw new IllegalArgumentException("EDIT session requires a target fingerprint");
        }
    }

    private static Optional<String> normalizeFingerprint(Optional<String> value) {
        if(value == null || value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(requireText(value.orElseThrow(), "target fingerprint"));
    }

    private static void validateExactWorld(WorldId expected,
                                           WorldId actual,
                                           String label) {
        if(!expected.uuid().equals(actual.uuid())
                || !expected.namespacedKey().equals(actual.namespacedKey())) {
            throw new IllegalArgumentException(
                    label + " world must exactly match the session world"
            );
        }
    }

    private static String requireText(String value, String label) {
        if(value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}

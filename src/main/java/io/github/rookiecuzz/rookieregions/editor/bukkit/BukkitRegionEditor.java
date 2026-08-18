package io.github.rookiecuzz.rookieregions.editor.bukkit;

import io.github.rookiecuzz.rookieregions.bukkit.BukkitWorlds;
import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.Point2D;
import io.github.rookiecuzz.rookieregions.editor.model.BlockPoint;
import io.github.rookiecuzz.rookieregions.editor.model.RegionDraft;
import io.github.rookiecuzz.rookieregions.editor.model.RegionEditSession;
import io.github.rookiecuzz.rookieregions.editor.model.RegionEditorManager;
import io.github.rookiecuzz.rookieregions.editor.model.ShapeKind;
import io.github.rookiecuzz.rookieregions.mutation.RegionFingerprints;
import io.github.rookiecuzz.rookieregions.mutation.RegionSaveRequest;
import io.github.rookiecuzz.rookieregions.mutation.SaveMode;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Bukkit-facing lifecycle and wooden-axe selection adapter. */
public final class BukkitRegionEditor {
    private final RegionEditorManager sessions;
    private final BiConsumer<String, String> confirmationInvalidator;

    public BukkitRegionEditor(RegionEditorManager sessions) {
        this(sessions, (actor, session) -> { });
    }

    public BukkitRegionEditor(
            RegionEditorManager sessions,
            BiConsumer<String, String> confirmationInvalidator) {
        this.sessions = Objects.requireNonNull(
                sessions, "editor manager cannot be null"
        );
        this.confirmationInvalidator = Objects.requireNonNull(
                confirmationInvalidator, "confirmation invalidator cannot be null"
        );
    }

    public RegionEditSession beginCreate(Player player,
                                         RegionSnapshot snapshot,
                                         String id,
                                         ShapeKind kind){
        Objects.requireNonNull(player, "editor player cannot be null");
        Objects.requireNonNull(snapshot, "editor snapshot cannot be null");
        Objects.requireNonNull(kind, "editor shape kind cannot be null");
        WorldId world = BukkitWorlds.id(player.getWorld());
        RegionKey key = new RegionKey(world, id);
        if(snapshot.records().containsKey(key)){
            throw new IllegalArgumentException("region already exists: " + key.id());
        }
        int initialY = player.getLocation().getBlockY();
        RegionDraft draft = switch(kind){
            case CUBOID -> RegionDraft.cuboid(world);
            case POLYGON -> RegionDraft.polygon(world)
                    .setPolygonHeights(initialY, (double) initialY + 1.0d);
            case SLICED -> RegionDraft.sliced(world)
                    .setSlicedMinY(initialY)
                    .selectSlice(initialY)
                    .setSlicedMaxY((double) initialY + 1.0d);
        };
        RegionEditSession session = new RegionEditSession(
                UUID.randomUUID().toString(),
                player.getUniqueId(),
                SaveMode.CREATE,
                key,
                null,
                snapshot.revision(),
                Optional.empty(),
                draft
        );
        sessions.begin(session);
        return session;
    }

    public RegionEditSession beginEdit(Player player,
                                       RegionSnapshot snapshot,
                                       Region region){
        Objects.requireNonNull(player, "editor player cannot be null");
        Objects.requireNonNull(snapshot, "editor snapshot cannot be null");
        Objects.requireNonNull(region, "edited region cannot be null");
        WorldId playerWorld = BukkitWorlds.id(player.getWorld());
        if(!playerWorld.equals(region.key().world())){
            throw new IllegalArgumentException(
                    "stand in the region's world before editing it"
            );
        }
        RegionEditSession session = new RegionEditSession(
                UUID.randomUUID().toString(),
                player.getUniqueId(),
                SaveMode.EDIT,
                region.key(),
                region,
                snapshot.revision(),
                Optional.of(RegionFingerprints.region(region)),
                RegionDraft.from(region)
        );
        sessions.begin(session);
        return session;
    }

    public Optional<RegionEditSession> session(UUID actor){
        return sessions.session(actor);
    }

    public RegionSaveRequest finishRequest(UUID actor, String token){
        RegionEditSession session = sessions.session(actor).orElseThrow(() ->
                new IllegalStateException("you have no active editor session")
        );
        return session.saveRequest(Optional.ofNullable(token));
    }

    public RegionEditSession markSaved(UUID actor, String sessionId){
        return sessions.markSaved(actor, sessionId);
    }

    public RegionEditSession cancel(UUID actor){
        RegionEditSession session = require(actor);
        invalidateConfirmation(session);
        return sessions.cancel(actor);
    }

    public Optional<Point2D> undo(UUID actor){
        RegionEditSession session = require(actor);
        Optional<Point2D> result = session.draft().undoPoint();
        if(result.isPresent()) {
            invalidateConfirmation(session);
        }
        return result;
    }

    public int clear(UUID actor){
        RegionEditSession session = require(actor);
        int removed = session.draft().clearPoints();
        if(removed > 0) {
            invalidateConfirmation(session);
        }
        return removed;
    }

    public void selectSlice(UUID actor, double y){
        RegionEditSession session = require(actor);
        session.draft().selectSlice(y);
        invalidateConfirmation(session);
    }

    public void setMaxY(UUID actor, double y){
        RegionEditSession session = require(actor);
        session.draft().setMaxY(y);
        invalidateConfirmation(session);
    }

    public void setMinY(UUID actor, double y){
        RegionEditSession session = require(actor);
        session.draft().setMinY(y);
        invalidateConfirmation(session);
    }

    public SelectionFeedback select(UUID actor,
                                    WorldId world,
                                    BlockPoint point,
                                    SelectionClick click){
        Objects.requireNonNull(world, "selection world cannot be null");
        Objects.requireNonNull(point, "selection point cannot be null");
        Objects.requireNonNull(click, "selection click cannot be null");
        RegionEditSession session = require(actor);
        if(!session.draft().world().equals(world)){
            throw new IllegalArgumentException(
                    "selection must be in "
                            + session.draft().world().namespacedKey()
            );
        }
        RegionDraft draft = session.draft();
        SelectionFeedback feedback = switch(draft.kind()){
            case CUBOID -> {
                if(click == SelectionClick.PRIMARY){
                    draft.setPos1(point);
                    yield new SelectionFeedback("Position 1 set to " + format(point));
                }
                draft.setPos2(point);
                yield new SelectionFeedback("Position 2 set to " + format(point));
            }
            case POLYGON -> {
                draft.addPoint(point);
                yield new SelectionFeedback(
                        "Polygon point " + draft.points().size() + " added at "
                                + point.x() + ", " + point.z()
                );
            }
            case SLICED -> {
                draft.addPoint(point);
                double slice = draft.currentSliceY().orElseThrow(() ->
                        new IllegalStateException("select a slice before adding points")
                );
                yield new SelectionFeedback(
                        "Slice " + slice + " point " + draft.points().size()
                                + " added at " + point.x() + ", " + point.z()
                );
            }
        };
        invalidateConfirmation(session);
        return feedback;
    }

    public RegionEditorManager sessions(){
        return sessions;
    }

    private RegionEditSession require(UUID actor){
        return sessions.session(actor).orElseThrow(() ->
                new IllegalStateException("you have no active editor session")
        );
    }

    private String format(BlockPoint point){
        return point.x() + ", " + point.y() + ", " + point.z();
    }

    private void invalidateConfirmation(RegionEditSession session){
        confirmationInvalidator.accept(
                session.actor().toString(), session.sessionId()
        );
    }
}

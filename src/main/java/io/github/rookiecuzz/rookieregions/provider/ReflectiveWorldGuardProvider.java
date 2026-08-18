package io.github.rookiecuzz.rookieregions.provider;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionDomain;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.core.shape.Point2D;
import io.github.rookiecuzz.rookieregions.core.shape.PolygonPrismShape;
import io.github.rookiecuzz.rookieregions.core.shape.RegionShape;
import io.github.rookiecuzz.rookieregions.core.shape.ShapeRelation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Complete-snapshot WorldGuard adapter with no WorldGuard compile dependency.
 *
 * <p>An explicit refresh performs a complete capture. A failed capture or conversion is
 * never published: the previous successful snapshot remains visible while
 * availability and the failure reason describe the latest attempt.</p>
 */
public final class ReflectiveWorldGuardProvider implements WorldGuardProvider {
    private static final String ENCODED_ID_PREFIX = "wg-encoded-";

    private final WorldGuardReflectionFacade facade;
    private volatile Status status = new Status(
            false,
            RegionSnapshot.empty(),
            Map.of(),
            List.of(),
            "WorldGuard has not been captured"
    );

    public ReflectiveWorldGuardProvider(WorldGuardReflectionFacade facade) {
        this.facade = Objects.requireNonNull(
                facade,
                "WorldGuard reflection facade cannot be null"
        );
        refresh();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return status.available();
    }

    /** Attempts a new all-world capture and returns the newest safe view. */
    @Override
    public RegionSnapshot snapshot() {
        return status.snapshot();
    }

    @Override
    public Optional<String> failureReason() {
        return Optional.ofNullable(status.failureReason());
    }

    @Override
    public List<String> diagnostics() {
        return status.diagnostics();
    }

    @Override
    public Optional<RegionKey> regionKey(WorldId world,
                                         String externalRegionId) {
        return view().regionKey(world, externalRegionId);
    }

    @Override
    public RegionProviderView view() {
        Status captured = status;
        return new RegionProviderView(captured.snapshot(), (world, externalRegionId) -> {
        if(world == null || externalRegionId == null) {
            return Optional.empty();
        }
        String canonical;
        try {
            canonical = canonicalId(externalRegionId);
        } catch(IllegalArgumentException exception) {
            return Optional.empty();
        }
        return Optional.ofNullable(captured.regionKeys().get(
                new ExternalKey(world.uuid(), canonical)
        ));
        });
    }

    @Override
    public synchronized RegionSnapshot refresh() {
        Status before = status;
        try {
            if(before.snapshot().revision() == Long.MAX_VALUE){
                throw new IllegalStateException("WorldGuard snapshot revision is exhausted");
            }
            WorldGuardReflectionFacade.Capture capture = Objects.requireNonNull(
                    facade.capture(),
                    "WorldGuard reflection facade returned null"
            );
            Conversion converted = convert(
                    before.snapshot().revision() + 1L,
                    capture
            );
            status = new Status(
                    true,
                    converted.snapshot(),
                    converted.regionKeys(),
                    converted.diagnostics(),
                    null
            );
            return converted.snapshot();
        } catch(Exception | LinkageError exception){
            status = new Status(
                    false,
                    before.snapshot(),
                    before.regionKeys(),
                    before.diagnostics(),
                    describe(exception)
            );
            return before.snapshot();
        }
    }

    private static Conversion convert(
            long revision,
            WorldGuardReflectionFacade.Capture capture) {
        ArrayList<WorldGuardReflectionFacade.WorldView> worlds =
                new ArrayList<>(capture.worlds());
        worlds.sort(Comparator
                .comparing(WorldGuardReflectionFacade.WorldView::uuid)
                .thenComparing(WorldGuardReflectionFacade.WorldView::namespacedKey));

        ArrayList<Region> converted = new ArrayList<>();
        LinkedHashMap<ExternalKey, RegionKey> regionKeys = new LinkedHashMap<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        Set<WorldId> seenWorlds = new HashSet<>();
        for(WorldGuardReflectionFacade.WorldView worldView : worlds){
            WorldId world = new WorldId(worldView.uuid(), worldView.namespacedKey());
            if(!seenWorlds.add(world)){
                throw new IllegalArgumentException(
                        "WorldGuard capture contains duplicate world UUID " + world.uuid()
                );
            }
            ConvertedWorld convertedWorld = convertWorld(
                    world,
                    worldView.regions()
            );
            converted.addAll(convertedWorld.regions());
            diagnostics.addAll(convertedWorld.diagnostics());
            convertedWorld.regionKeys().forEach((sourceId, key) ->
                    regionKeys.put(new ExternalKey(world.uuid(), sourceId), key)
            );
        }
        return new Conversion(
                RegionSnapshot.of(revision, converted),
                Map.copyOf(regionKeys),
                List.copyOf(diagnostics)
        );
    }

    private static ConvertedWorld convertWorld(
            WorldId world,
            List<WorldGuardReflectionFacade.RegionView> captured) {
        ArrayList<WorldGuardReflectionFacade.RegionView> ordered =
                new ArrayList<>(captured);
        ordered.sort(Comparator.comparing(region -> canonicalId(region.id())));

        Map<String, WorldGuardReflectionFacade.RegionView> byId = new LinkedHashMap<>();
        WorldGuardReflectionFacade.RegionView capturedGlobal = null;
        for(WorldGuardReflectionFacade.RegionView region : ordered){
            String id = canonicalId(region.id());
            if(byId.putIfAbsent(id, region) != null){
                throw new IllegalArgumentException(
                        "duplicate case-insensitive WorldGuard region ID " + region.id()
                );
            }
            if(region.shape() instanceof WorldGuardReflectionFacade.GlobalView){
                if(capturedGlobal != null){
                    throw new IllegalArgumentException(
                            "multiple WorldGuard global regions in " + world.namespacedKey()
                    );
                }
                if(region.parentId() != null){
                    throw new IllegalArgumentException(
                            "WorldGuard global region cannot have a parent"
                    );
                }
                capturedGlobal = region;
            }
        }

        Map<String, RegionKey> allocated = allocateKeys(world, ordered);
        RegionKey globalKey = RegionKey.global(world);
        LinkedHashMap<String, RegionKey> keys = new LinkedHashMap<>(allocated);
        keys.putIfAbsent(RegionKey.GLOBAL_ID, globalKey);
        Region global = capturedGlobal == null
                ? Region.builder(globalKey, GlobalShape.INSTANCE).build()
                : baseBuilder(globalKey, GlobalShape.INSTANCE, capturedGlobal).build();

        ArrayList<Region> result = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        result.add(global);
        for(WorldGuardReflectionFacade.RegionView source : ordered){
            if(source.shape() instanceof WorldGuardReflectionFacade.GlobalView){
                continue;
            }
            RegionKey key = keys.get(canonicalId(source.id()));
            RegionShape shape = toCoreShape(source.shape());
            RegionKey parent = parentKey(
                    source,
                    shape,
                    byId,
                    keys,
                    globalKey,
                    diagnostics,
                    world
            );
            result.add(baseBuilder(key, shape, source)
                    .parent(parent)
                    .build());
        }
        return new ConvertedWorld(
                List.copyOf(result),
                Map.copyOf(keys),
                List.copyOf(diagnostics)
        );
    }

    private static Map<String, RegionKey> allocateKeys(
            WorldId world,
            List<WorldGuardReflectionFacade.RegionView> ordered) {
        LinkedHashMap<String, RegionKey> result = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        used.add(RegionKey.GLOBAL_ID);

        for(WorldGuardReflectionFacade.RegionView region : ordered){
            String sourceId = canonicalId(region.id());
            if(region.shape() instanceof WorldGuardReflectionFacade.GlobalView){
                result.put(sourceId, RegionKey.global(world));
                continue;
            }
            String direct = usableDirectId(world, sourceId) ? sourceId : null;
            if(direct != null && used.add(direct)){
                result.put(sourceId, new RegionKey(world, direct));
            }
        }

        for(WorldGuardReflectionFacade.RegionView region : ordered){
            String sourceId = canonicalId(region.id());
            if(result.containsKey(sourceId)){
                continue;
            }
            String base = ENCODED_ID_PREFIX + HexFormat.of().formatHex(
                    sourceId.getBytes(StandardCharsets.UTF_8)
            );
            String candidate = base;
            int suffix = 1;
            while(!used.add(candidate)){
                candidate = base + "-" + suffix++;
            }
            result.put(sourceId, new RegionKey(world, candidate));
        }
        return result;
    }

    private static boolean usableDirectId(WorldId world, String id) {
        if(RegionKey.GLOBAL_ID.equals(id)){
            return false;
        }
        try {
            new RegionKey(world, id);
            return true;
        } catch(IllegalArgumentException exception){
            return false;
        }
    }

    private static RegionKey parentKey(
            WorldGuardReflectionFacade.RegionView source,
            RegionShape childShape,
            Map<String, WorldGuardReflectionFacade.RegionView> byId,
            Map<String, RegionKey> keys,
            RegionKey globalKey,
            List<String> diagnostics,
            WorldId world) {
        if(source.parentId() == null){
            return globalKey;
        }
        String parentId = canonicalId(source.parentId());
        if(!byId.containsKey(parentId)){
            throw new IllegalArgumentException(
                    "missing WorldGuard parent " + source.parentId()
                            + " for " + source.id()
            );
        }
        RegionKey parent = keys.get(parentId);
        if(parent == null){
            throw new IllegalArgumentException(
                    "WorldGuard parent could not be mapped: " + source.parentId()
            );
        }
        WorldGuardReflectionFacade.RegionView parentSource = byId.get(parentId);
        if(parentSource.shape() instanceof WorldGuardReflectionFacade.GlobalView) {
            return globalKey;
        }
        RegionShape parentShape = toCoreShape(parentSource.shape());
        if(childShape.relationTo(parentShape) != ShapeRelation.INSIDE) {
            diagnostics.add(
                    "flattened WorldGuard parent '" + source.parentId()
                            + "' for '" + source.id() + "' in "
                            + world.namespacedKey()
                            + " because RookieRegions hierarchy requires true containment"
            );
            return globalKey;
        }
        return parent;
    }

    private static Region.Builder baseBuilder(
            RegionKey key,
            io.github.rookiecuzz.rookieregions.core.shape.RegionShape shape,
            WorldGuardReflectionFacade.RegionView source) {
        return Region.builder(key, shape)
                .priority(source.priority())
                .owners(new RegionDomain(source.ownerPlayers(), source.ownerGroups()))
                .members(new RegionDomain(source.memberPlayers(), source.memberGroups()));
    }

    private static io.github.rookiecuzz.rookieregions.core.shape.RegionShape toCoreShape(
            WorldGuardReflectionFacade.ShapeView source) {
        if(source instanceof WorldGuardReflectionFacade.CuboidView cuboid){
            return new CuboidShape(
                    cuboid.minX(),
                    cuboid.minY(),
                    cuboid.minZ(),
                    exclusiveMaximum(cuboid.maxX()),
                    exclusiveMaximum(cuboid.maxY()),
                    exclusiveMaximum(cuboid.maxZ())
            );
        }
        if(source instanceof WorldGuardReflectionFacade.PolygonalView polygon){
            List<Point2D> points = polygon.points().stream()
                    .map(point -> new Point2D(point.x(), point.z()))
                    .toList();
            return new PolygonPrismShape(
                    polygon.minY(),
                    exclusiveMaximum(polygon.maxY()),
                    points
            );
        }
        throw new IllegalArgumentException(
                "WorldGuard local region has an unsupported shape: " + source
        );
    }

    private static double exclusiveMaximum(int inclusiveMaximum) {
        return (double) inclusiveMaximum + 1.0d;
    }

    private static String canonicalId(String id) {
        if(id == null){
            throw new IllegalArgumentException("WorldGuard region ID cannot be null");
        }
        String canonical = id.trim().toLowerCase(Locale.ROOT);
        if(canonical.isEmpty()){
            throw new IllegalArgumentException("WorldGuard region ID cannot be blank");
        }
        return canonical;
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private record Status(boolean available,
                          RegionSnapshot snapshot,
                          Map<ExternalKey, RegionKey> regionKeys,
                          List<String> diagnostics,
                          String failureReason) {
    }

    private record ExternalKey(java.util.UUID world, String regionId) {
    }

    private record Conversion(RegionSnapshot snapshot,
                              Map<ExternalKey, RegionKey> regionKeys,
                              List<String> diagnostics) {
    }

    private record ConvertedWorld(List<Region> regions,
                                  Map<String, RegionKey> regionKeys,
                                  List<String> diagnostics) {
    }
}

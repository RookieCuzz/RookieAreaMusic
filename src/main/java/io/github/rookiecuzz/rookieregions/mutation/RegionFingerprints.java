package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionRelation;
import io.github.rookiecuzz.rookieregions.core.shape.Bounds3D;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.core.shape.Point2D;
import io.github.rookiecuzz.rookieregions.core.shape.PolygonPrismShape;
import io.github.rookiecuzz.rookieregions.core.shape.RegionShape;
import io.github.rookiecuzz.rookieregions.core.shape.SlicedPolygonShape;
import io.github.rookiecuzz.rookieregions.rule.FlagValue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Canonical SHA-256 fingerprints used for optimistic editor checks. */
public final class RegionFingerprints {
    public static String region(Region region){
        if(region == null){
            throw new IllegalArgumentException("region cannot be null");
        }
        StringBuilder canonical = new StringBuilder();
        append(canonical, "region");
        appendKey(canonical, region.key());
        append(canonical, Integer.toString(region.priority()));
        append(canonical, region.parent().map(RegionKey::toString).orElse("<none>"));
        region.owners().players().stream().sorted().forEach(player ->
                append(canonical, "owner-player:" + player));
        region.owners().groups().stream().sorted().forEach(group ->
                append(canonical, "owner-group:" + group));
        region.members().players().stream().sorted().forEach(player ->
                append(canonical, "member-player:" + player));
        region.members().groups().stream().sorted().forEach(group ->
                append(canonical, "member-group:" + group));

        List<Map.Entry<String, FlagValue<?>>> flags =
                new ArrayList<>(region.flags().entrySet());
        flags.sort(Map.Entry.comparingByKey());
        for(Map.Entry<String, FlagValue<?>> entry : flags){
            append(canonical, "flag:" + entry.getKey());
            appendEncoded(canonical, encode(entry.getValue()));
        }
        appendShape(canonical, region.shape());
        return sha256(canonical.toString());
    }

    static String placementPlan(PlanDisposition disposition,
                                List<PlacementOption> options,
                                RegionSaveRejection rejection,
                                List<RegionRelation> relations){
        StringBuilder canonical = new StringBuilder();
        append(canonical, disposition.name());
        append(canonical, rejection == null ? "<none>" : rejection.name());
        List<PlacementOption> orderedOptions = new ArrayList<>(options);
        orderedOptions.sort(Comparator
                .comparing((PlacementOption option) -> option.choice().name())
                .thenComparing(option -> option.parent()
                        .map(RegionKey::toString)
                        .orElse("")));
        for(PlacementOption option : orderedOptions){
            append(canonical, option.choice().name());
            append(canonical, option.parent().map(RegionKey::toString).orElse(""));
        }
        List<RegionRelation> orderedRelations = new ArrayList<>(relations);
        orderedRelations.sort(Comparator.comparing(
                relation -> relation.region().key()
        ));
        for(RegionRelation relation : orderedRelations){
            appendKey(canonical, relation.region().key());
            append(canonical, relation.relation().name());
            append(canonical, region(relation.region()));
        }
        return sha256(canonical.toString());
    }

    private static <T> Object encodeTyped(FlagValue<T> value){
        return value.flag().codec().encode(value.value());
    }

    private static Object encode(FlagValue<?> value){
        return encodeTypedCapture(value);
    }

    private static <T> Object encodeTypedCapture(FlagValue<T> value){
        return encodeTyped(value);
    }

    private static void appendShape(StringBuilder target, RegionShape shape){
        if(shape == GlobalShape.INSTANCE){
            append(target, "shape:global");
            return;
        }
        if(shape instanceof CuboidShape){
            append(target, "shape:cuboid");
            appendBounds(target, shape.bounds());
            return;
        }
        if(shape instanceof PolygonPrismShape polygon){
            append(target, "shape:polygon-prism");
            append(target, number(polygon.minY()));
            append(target, number(polygon.maxY()));
            appendPoints(target, polygon.vertices());
            return;
        }
        if(shape instanceof SlicedPolygonShape sliced){
            append(target, "shape:sliced-polygon");
            append(target, number(sliced.maxY()));
            for(SlicedPolygonShape.Slice slice : sliced.slices()){
                append(target, number(slice.y()));
                appendPoints(target, slice.vertices());
            }
            return;
        }
        throw new IllegalArgumentException(
                "unsupported region shape for fingerprinting: "
                        + shape.getClass().getName()
        );
    }

    private static void appendBounds(StringBuilder target, Bounds3D bounds){
        append(target, number(bounds.minX()));
        append(target, number(bounds.minY()));
        append(target, number(bounds.minZ()));
        append(target, number(bounds.maxX()));
        append(target, number(bounds.maxY()));
        append(target, number(bounds.maxZ()));
    }

    private static void appendPoints(StringBuilder target, List<Point2D> points){
        append(target, Integer.toString(points.size()));
        for(Point2D point : points){
            append(target, number(point.x()));
            append(target, number(point.z()));
        }
    }

    private static void appendEncoded(StringBuilder target, Object value){
        if(value == null){
            append(target, "null");
        } else if(value instanceof String text){
            append(target, "string");
            append(target, text);
        } else if(value instanceof Number number){
            append(target, "number");
            append(target, number.toString());
        } else if(value instanceof Boolean bool){
            append(target, "boolean");
            append(target, bool.toString());
        } else if(value instanceof Enum<?> enumeration){
            append(target, "enum");
            append(target, enumeration.getDeclaringClass().getName());
            append(target, enumeration.name());
        } else if(value instanceof Map<?, ?> map){
            append(target, "map");
            List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
            for(Map.Entry<?, ?> entry : entries){
                appendEncoded(target, entry.getKey());
                appendEncoded(target, entry.getValue());
            }
        } else if(value instanceof Iterable<?> iterable){
            append(target, "list");
            for(Object item : iterable){
                appendEncoded(target, item);
            }
        } else {
            throw new IllegalArgumentException(
                    "unsupported encoded flag value: " + value.getClass().getName()
            );
        }
    }

    private static void appendKey(StringBuilder target, RegionKey key){
        append(target, key.world().uuid().toString());
        append(target, key.id());
    }

    private static void append(StringBuilder target, String value){
        target.append(value.length()).append(':').append(value).append(';');
    }

    private static String number(double value){
        return Double.toHexString(value == 0.0d ? 0.0d : value);
    }

    private static String sha256(String value){
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException impossible){
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private RegionFingerprints() {
    }
}

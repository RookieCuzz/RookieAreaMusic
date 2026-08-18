package io.github.rookiecuzz.rookieregions.persistence.codec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.rookiecuzz.rookieregions.core.shape.Bounds3D;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.core.shape.Point2D;
import io.github.rookiecuzz.rookieregions.core.shape.PolygonPrismShape;
import io.github.rookiecuzz.rookieregions.core.shape.RegionShape;
import io.github.rookiecuzz.rookieregions.core.shape.SlicedPolygonShape;
import io.github.rookiecuzz.rookieregions.persistence.json.StrictJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Strict, deterministic JSON codec for built-in region shapes. */
public final class ShapeJsonCodec {
    public static final ShapeJsonCodec INSTANCE = new ShapeJsonCodec();

    private static final Set<String> CUBOID_FIELDS = Set.of("type", "min", "max");
    private static final Set<String> POLYGON_FIELDS =
            Set.of("type", "minY", "maxY", "vertices");
    private static final Set<String> SLICED_FIELDS = Set.of(
            "type", "minY", "maxY", "slices"
    );
    private static final Set<String> GLOBAL_FIELDS = Set.of("type");
    private static final Set<String> POINT_3D_FIELDS = Set.of("x", "y", "z");
    private static final Set<String> POINT_2D_FIELDS = Set.of("x", "z");
    private static final Set<String> SLICE_FIELDS = Set.of("y", "vertices");

    public RegionShape decode(String json){
        try {
            return decode(StrictJson.parse(json));
        } catch(StrictJson.StrictJsonException exception){
            throw new DocumentFormatException(
                    exception.pointer(),
                    stripLocation(exception.getMessage()),
                    exception
            );
        }
    }

    public RegionShape decode(JsonElement element){
        JsonObject object = requireObject(element, "");
        String type = requireString(requireField(object, "type", ""), "/type");
        return switch(type){
            case "cuboid" -> decodeCuboid(object);
            case "polygon" -> decodePolygon(object);
            case "sliced" -> decodeSliced(object);
            case "global" -> decodeGlobal(object);
            default -> throw error("/type", "unknown shape type '" + type + "'");
        };
    }

    public JsonObject encode(RegionShape shape){
        if(shape == null){
            throw new IllegalArgumentException("shape cannot be null");
        }
        if(shape instanceof CuboidShape cuboid){
            JsonObject result = new JsonObject();
            result.addProperty("type", "cuboid");
            result.add("min", point3(
                    cuboid.bounds().minX(), cuboid.bounds().minY(), cuboid.bounds().minZ()
            ));
            result.add("max", point3(
                    cuboid.bounds().maxX(), cuboid.bounds().maxY(), cuboid.bounds().maxZ()
            ));
            return result;
        }
        if(shape instanceof PolygonPrismShape polygon){
            JsonObject result = new JsonObject();
            result.addProperty("type", "polygon");
            result.addProperty("minY", polygon.minY());
            result.addProperty("maxY", polygon.maxY());
            result.add("vertices", vertices(polygon.vertices()));
            return result;
        }
        if(shape instanceof SlicedPolygonShape sliced){
            JsonObject result = new JsonObject();
            result.addProperty("type", "sliced");
            result.addProperty("minY", sliced.minY());
            result.addProperty("maxY", sliced.maxY());
            JsonArray slices = new JsonArray();
            for(SlicedPolygonShape.Slice slice : sliced.slices()){
                JsonObject encodedSlice = new JsonObject();
                encodedSlice.addProperty("y", slice.y());
                encodedSlice.add("vertices", vertices(slice.vertices()));
                slices.add(encodedSlice);
            }
            result.add("slices", slices);
            return result;
        }
        if(shape instanceof GlobalShape){
            JsonObject result = new JsonObject();
            result.addProperty("type", "global");
            return result;
        }
        throw error("", "unsupported shape class " + shape.getClass().getName());
    }

    public String encodeToString(RegionShape shape){
        return StrictJson.write(encode(shape));
    }

    private RegionShape decodeCuboid(JsonObject object){
        rejectUnknownFields(object, CUBOID_FIELDS, "");
        Point3 min = readPoint3(requireField(object, "min", ""), "/min");
        Point3 max = readPoint3(requireField(object, "max", ""), "/max");
        try {
            return new CuboidShape(new Bounds3D(
                    min.x(), min.y(), min.z(),
                    max.x(), max.y(), max.z()
            ));
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException("", exception.getMessage(), exception);
        }
    }

    private RegionShape decodePolygon(JsonObject object){
        rejectUnknownFields(object, POLYGON_FIELDS, "");
        double minY = requireFiniteNumber(requireField(object, "minY", ""), "/minY");
        double maxY = requireFiniteNumber(requireField(object, "maxY", ""), "/maxY");
        if(maxY <= minY || !Double.isFinite(maxY - minY)){
            throw error("/maxY", "maxY must be greater than minY with finite extent");
        }
        List<Point2D> vertices = readVertices(
                requireField(object, "vertices", ""),
                "/vertices"
        );
        try {
            return new PolygonPrismShape(minY, maxY, vertices);
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException("/vertices", exception.getMessage(), exception);
        }
    }

    private RegionShape decodeSliced(JsonObject object){
        rejectUnknownFields(object, SLICED_FIELDS, "");
        double minY = requireFiniteNumber(requireField(object, "minY", ""), "/minY");
        double maxY = requireFiniteNumber(requireField(object, "maxY", ""), "/maxY");
        if(maxY <= minY || !Double.isFinite(maxY - minY)){
            throw error("/maxY", "maxY must be greater than minY with finite extent");
        }
        JsonArray array = requireArray(requireField(object, "slices", ""), "/slices");
        List<SlicedPolygonShape.Slice> slices = new ArrayList<>();
        double previousY = Double.NEGATIVE_INFINITY;
        for(int index = 0; index < array.size(); index++){
            String pointer = "/slices/" + index;
            JsonObject slice = requireObject(array.get(index), pointer);
            rejectUnknownFields(slice, SLICE_FIELDS, pointer);
            double y = requireFiniteNumber(requireField(slice, "y", pointer), pointer + "/y");
            if(index > 0 && y <= previousY){
                throw error(pointer + "/y", "slice Y coordinates must be strictly increasing");
            }
            List<Point2D> vertices = readVertices(
                    requireField(slice, "vertices", pointer),
                    pointer + "/vertices"
            );
            try {
                slices.add(new SlicedPolygonShape.Slice(y, vertices));
            } catch(IllegalArgumentException exception){
                throw new DocumentFormatException(pointer, exception.getMessage(), exception);
            }
            previousY = y;
        }
        if(slices.isEmpty()){
            throw error("/slices", "at least one slice is required");
        }
        if(Double.compare(slices.getFirst().y(), minY) != 0){
            throw error("/slices/0/y", "first slice Y must equal explicit minY");
        }
        if(maxY <= previousY){
            throw error("/maxY", "maxY must be above all slices with finite extent");
        }
        try {
            return new SlicedPolygonShape(minY, maxY, slices);
        } catch(IllegalArgumentException exception){
            throw new DocumentFormatException("/slices", exception.getMessage(), exception);
        }
    }

    private RegionShape decodeGlobal(JsonObject object){
        rejectUnknownFields(object, GLOBAL_FIELDS, "");
        return GlobalShape.INSTANCE;
    }

    private static Point3 readPoint3(JsonElement element, String pointer){
        JsonObject object = requireObject(element, pointer);
        rejectUnknownFields(object, POINT_3D_FIELDS, pointer);
        return new Point3(
                requireFiniteNumber(requireField(object, "x", pointer), pointer + "/x"),
                requireFiniteNumber(requireField(object, "y", pointer), pointer + "/y"),
                requireFiniteNumber(requireField(object, "z", pointer), pointer + "/z")
        );
    }

    private static List<Point2D> readVertices(JsonElement element, String pointer){
        JsonArray array = requireArray(element, pointer);
        List<Point2D> result = new ArrayList<>();
        for(int index = 0; index < array.size(); index++){
            String itemPointer = pointer + "/" + index;
            JsonObject point = requireObject(array.get(index), itemPointer);
            rejectUnknownFields(point, POINT_2D_FIELDS, itemPointer);
            double x = requireFiniteNumber(
                    requireField(point, "x", itemPointer),
                    itemPointer + "/x"
            );
            double z = requireFiniteNumber(
                    requireField(point, "z", itemPointer),
                    itemPointer + "/z"
            );
            try {
                result.add(new Point2D(x, z));
            } catch(IllegalArgumentException exception){
                throw new DocumentFormatException(itemPointer, exception.getMessage(), exception);
            }
        }
        return result;
    }

    private static JsonElement requireField(JsonObject object,
                                            String field,
                                            String pointer){
        if(!object.has(field)){
            throw error(append(pointer, field), "required field is missing");
        }
        return object.get(field);
    }

    private static JsonObject requireObject(JsonElement element, String pointer){
        if(element == null || !element.isJsonObject()){
            throw error(pointer, "expected an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonElement element, String pointer){
        if(element == null || !element.isJsonArray()){
            throw error(pointer, "expected an array");
        }
        return element.getAsJsonArray();
    }

    private static String requireString(JsonElement element, String pointer){
        if(element == null || !element.isJsonPrimitive()){
            throw error(pointer, "expected a string");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if(!primitive.isString()){
            throw error(pointer, "expected a string");
        }
        return primitive.getAsString();
    }

    private static double requireFiniteNumber(JsonElement element, String pointer){
        if(element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()){
            throw error(pointer, "expected a finite number");
        }
        double result;
        try {
            result = element.getAsDouble();
        } catch(NumberFormatException exception){
            throw new DocumentFormatException(pointer, "expected a finite number", exception);
        }
        if(!Double.isFinite(result)){
            throw error(pointer, "number is outside the finite double range");
        }
        return result == 0.0d ? 0.0d : result;
    }

    private static void rejectUnknownFields(JsonObject object,
                                            Set<String> allowed,
                                            String pointer){
        for(String field : object.keySet()){
            if(!allowed.contains(field)){
                throw error(append(pointer, field), "unknown field '" + field + "'");
            }
        }
    }

    private static JsonObject point3(double x, double y, double z){
        JsonObject result = new JsonObject();
        result.addProperty("x", x);
        result.addProperty("y", y);
        result.addProperty("z", z);
        return result;
    }

    private static JsonArray vertices(List<Point2D> vertices){
        JsonArray result = new JsonArray();
        for(Point2D vertex : vertices){
            JsonObject point = new JsonObject();
            point.addProperty("x", vertex.x());
            point.addProperty("z", vertex.z());
            result.add(point);
        }
        return result;
    }

    private static String append(String pointer, String segment){
        return pointer + "/" + segment.replace("~", "~0").replace("/", "~1");
    }

    private static DocumentFormatException error(String pointer, String message){
        return new DocumentFormatException(pointer, message);
    }

    private static String stripLocation(String message){
        if(message == null){
            return "invalid JSON";
        }
        int separator = message.indexOf(": ");
        return separator < 0 ? message : message.substring(separator + 2);
    }

    private record Point3(double x, double y, double z) {
    }
}

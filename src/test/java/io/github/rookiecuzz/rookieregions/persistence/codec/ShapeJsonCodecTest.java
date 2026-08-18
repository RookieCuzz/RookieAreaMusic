package io.github.rookiecuzz.rookieregions.persistence.codec;

import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.core.shape.Point2D;
import io.github.rookiecuzz.rookieregions.core.shape.PolygonPrismShape;
import io.github.rookiecuzz.rookieregions.core.shape.RegionShape;
import io.github.rookiecuzz.rookieregions.core.shape.ShapeRelation;
import io.github.rookiecuzz.rookieregions.core.shape.SlicedPolygonShape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeJsonCodecTest {
    private final ShapeJsonCodec codec = new ShapeJsonCodec();

    @Test
    void writesCanonicalCuboidJsonAndRoundTrips(){
        RegionShape shape = new CuboidShape(0, 1, 2, 3, 4, 5);
        String json = codec.encodeToString(shape);

        assertEquals(
                "{\"type\":\"cuboid\",\"min\":{\"x\":0.0,\"y\":1.0,\"z\":2.0},"
                        + "\"max\":{\"x\":3.0,\"y\":4.0,\"z\":5.0}}",
                json
        );
        assertRoundTrip(shape);
    }

    @Test
    void writesCanonicalPolygonJsonAndNormalizesClosingVertex(){
        RegionShape shape = new PolygonPrismShape(2, 8, List.of(
                point(0, 0), point(4, 0), point(4, 4), point(0, 4), point(0, 0)
        ));
        String json = codec.encodeToString(shape);

        assertEquals(
                "{\"type\":\"polygon\",\"minY\":2.0,\"maxY\":8.0,\"vertices\":["
                        + "{\"x\":0.0,\"z\":0.0},{\"x\":4.0,\"z\":0.0},"
                        + "{\"x\":4.0,\"z\":4.0},{\"x\":0.0,\"z\":4.0}]}",
                json
        );
        assertRoundTrip(shape);
    }

    @Test
    void writesCanonicalSlicedJsonAndRoundTrips(){
        RegionShape shape = new SlicedPolygonShape(0, 10, List.of(
                new SlicedPolygonShape.Slice(0, square(0, 0, 4, 4)),
                new SlicedPolygonShape.Slice(5, square(10, 10, 12, 12))
        ));
        String json = codec.encodeToString(shape);

        assertTrue(json.startsWith(
                "{\"type\":\"sliced\",\"minY\":0.0,\"maxY\":10.0,\"slices\":["
        ));
        assertTrue(json.contains("{\"y\":0.0,\"vertices\":["));
        assertTrue(json.contains("{\"y\":5.0,\"vertices\":["));
        assertRoundTrip(shape);
    }

    @Test
    void globalHasOneCanonicalRepresentation(){
        assertEquals("{\"type\":\"global\"}", codec.encodeToString(GlobalShape.INSTANCE));
        assertSame(GlobalShape.INSTANCE, codec.decode("{\"type\":\"global\"}"));
    }

    @Test
    void rejectsUnknownTypesAndFieldsAtExactPointers(){
        assertPointer(
                "/type",
                "{\"type\":\"sphere\"}"
        );
        assertPointer(
                "/extra",
                "{\"type\":\"global\",\"extra\":true}"
        );
        assertPointer(
                "/vertices/0/y",
                "{\"type\":\"polygon\",\"minY\":0,\"maxY\":2,"
                        + "\"vertices\":[{\"x\":0,\"z\":0,\"y\":1},"
                        + "{\"x\":2,\"z\":0},{\"x\":0,\"z\":2}]}"
        );
        assertPointer(
                "/slices/0/extra",
                "{\"type\":\"sliced\",\"minY\":0,\"maxY\":2,\"slices\":["
                        + "{\"y\":0,\"vertices\":[{\"x\":0,\"z\":0},"
                        + "{\"x\":2,\"z\":0},{\"x\":0,\"z\":2}],\"extra\":1}]}"
        );
    }

    @Test
    void rejectsMissingWrongAndOutOfRangeValuesAtExactPointers(){
        assertPointer(
                "/maxY",
                "{\"type\":\"polygon\",\"minY\":0,\"vertices\":[]}"
        );
        assertPointer(
                "/min/x",
                "{\"type\":\"cuboid\",\"min\":{\"x\":\"zero\",\"y\":0,\"z\":0},"
                        + "\"max\":{\"x\":1,\"y\":1,\"z\":1}}"
        );
        assertPointer(
                "/min/x",
                "{\"type\":\"cuboid\",\"min\":{\"x\":1e9999,\"y\":0,\"z\":0},"
                        + "\"max\":{\"x\":1,\"y\":1,\"z\":1}}"
        );
        assertPointer(
                "/vertices",
                "{\"type\":\"polygon\",\"minY\":0,\"maxY\":2,\"vertices\":["
                        + "{\"x\":0,\"z\":0},{\"x\":2,\"z\":2},"
                        + "{\"x\":0,\"z\":2},{\"x\":2,\"z\":0}]}"
        );
        assertPointer(
                "/maxY",
                "{\"type\":\"polygon\",\"minY\":2,\"maxY\":2,\"vertices\":["
                        + "{\"x\":0,\"z\":0},{\"x\":2,\"z\":0},{\"x\":0,\"z\":2}]}"
        );
        assertPointer(
                "/minY",
                "{\"type\":\"sliced\",\"maxY\":2,\"slices\":[]}"
        );
        assertPointer(
                "/slices/0/y",
                "{\"type\":\"sliced\",\"minY\":1,\"maxY\":5,\"slices\":["
                        + "{\"y\":2,\"vertices\":[{\"x\":0,\"z\":0},"
                        + "{\"x\":2,\"z\":0},{\"x\":0,\"z\":2}]}]}"
        );
        assertPointer(
                "/slices/1/y",
                "{\"type\":\"sliced\",\"minY\":2,\"maxY\":5,\"slices\":["
                        + "{\"y\":2,\"vertices\":[{\"x\":0,\"z\":0},"
                        + "{\"x\":2,\"z\":0},{\"x\":0,\"z\":2}]},"
                        + "{\"y\":1,\"vertices\":[{\"x\":0,\"z\":0},"
                        + "{\"x\":2,\"z\":0},{\"x\":0,\"z\":2}]}]}"
        );
    }

    @Test
    void duplicateKeysFromStrictJsonBecomeDocumentErrors(){
        DocumentFormatException error = assertThrows(
                DocumentFormatException.class,
                () -> codec.decode("{\"type\":\"global\",\"type\":\"cuboid\"}")
        );

        assertEquals("/type", error.pointer());
        assertTrue(error.getMessage().contains("duplicate"));
    }

    @Test
    void decodedConcreteTypesMatchTheirTypeDiscriminator(){
        assertInstanceOf(
                CuboidShape.class,
                codec.decode("{\"type\":\"cuboid\",\"min\":{\"x\":0,\"y\":0,\"z\":0},"
                        + "\"max\":{\"x\":1,\"y\":1,\"z\":1}}")
        );
        assertInstanceOf(
                PolygonPrismShape.class,
                codec.decode("{\"type\":\"polygon\",\"minY\":0,\"maxY\":1,"
                        + "\"vertices\":[{\"x\":0,\"z\":0},{\"x\":1,\"z\":0},"
                        + "{\"x\":0,\"z\":1}]}")
        );
        assertInstanceOf(
                SlicedPolygonShape.class,
                codec.decode("{\"type\":\"sliced\",\"minY\":0,\"maxY\":1,\"slices\":["
                        + "{\"y\":0,\"vertices\":[{\"x\":0,\"z\":0},"
                        + "{\"x\":1,\"z\":0},{\"x\":0,\"z\":1}]}]}")
        );
    }

    private void assertRoundTrip(RegionShape original){
        RegionShape decoded = codec.decode(codec.encodeToString(original));
        assertEquals(ShapeRelation.EQUAL, original.relationTo(decoded));
        assertEquals(codec.encodeToString(original), codec.encodeToString(decoded));
    }

    private void assertPointer(String expectedPointer, String json){
        DocumentFormatException error = assertThrows(
                DocumentFormatException.class,
                () -> codec.decode(json)
        );
        assertEquals(expectedPointer, error.getPointer());
    }

    private static List<Point2D> square(double minX,
                                        double minZ,
                                        double maxX,
                                        double maxZ){
        return List.of(
                point(minX, minZ), point(maxX, minZ),
                point(maxX, maxZ), point(minX, maxZ)
        );
    }

    private static Point2D point(double x, double z){
        return new Point2D(x, z);
    }
}

package io.github.rookiecuzz.rookieregions.persistence.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictJsonTest {
    @Test
    void rejectsDuplicateKeysAtTheirEscapedJsonPointer(){
        StrictJson.StrictJsonException root = assertThrows(
                StrictJson.StrictJsonException.class,
                () -> StrictJson.parse("{\"type\":1,\"type\":2}")
        );
        StrictJson.StrictJsonException nested = assertThrows(
                StrictJson.StrictJsonException.class,
                () -> StrictJson.parse("{\"outer\":{\"a/b\":1,\"a/b\":2}}")
        );

        assertEquals("/type", root.pointer());
        assertEquals("/outer/a~1b", nested.pointer());
        assertTrue(nested.getMessage().contains("duplicate"));
    }

    @Test
    void strictReaderRejectsNonStandardSyntaxAndNonFiniteNumbers(){
        assertThrows(
                StrictJson.StrictJsonException.class,
                () -> StrictJson.parse("{unquoted:1}")
        );
        assertThrows(
                StrictJson.StrictJsonException.class,
                () -> StrictJson.parse("{/*comment*/\"value\":1}")
        );
        assertThrows(
                StrictJson.StrictJsonException.class,
                () -> StrictJson.parse("{\"value\":NaN}")
        );
        assertThrows(
                StrictJson.StrictJsonException.class,
                () -> StrictJson.parse("{\"value\":Infinity}")
        );
        assertThrows(
                StrictJson.StrictJsonException.class,
                () -> StrictJson.parse("{} {}")
        );
    }

    @Test
    void deterministicWriterPreservesInsertionOrderAndDisablesHtmlEscaping(){
        JsonObject object = new JsonObject();
        object.addProperty("second", "<tag>&=");
        object.addProperty("first", 1);

        assertEquals(
                "{\"second\":\"<tag>&=\",\"first\":1}",
                StrictJson.write(object)
        );
    }

    @Test
    void writerRejectsNonFiniteNumbersWithPointer(){
        JsonObject object = new JsonObject();
        object.add("bad", new JsonPrimitive(Double.NEGATIVE_INFINITY));

        StrictJson.StrictJsonException error = assertThrows(
                StrictJson.StrictJsonException.class,
                () -> StrictJson.write(object)
        );
        assertEquals("/bad", error.pointer());
    }

    @Test
    void parsesACompleteValueTreeWithoutLosingNumberPrecision(){
        JsonObject parsed = StrictJson.parse(
                "{\"number\":12345678901234567890.125,\"array\":[true,null,\"ok\"]}"
        ).getAsJsonObject();

        assertEquals("12345678901234567890.125", parsed.get("number").getAsString());
        assertEquals(3, parsed.getAsJsonArray("array").size());
    }

    @Test
    void arbitraryPrecisionJsonNumbersRemainValidOutsideDoubleRange(){
        assertEquals("1E+9999", StrictJson.write(StrictJson.parse("1e9999")));
    }
}

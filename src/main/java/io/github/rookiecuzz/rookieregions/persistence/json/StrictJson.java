package io.github.rookiecuzz.rookieregions.persistence.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Strict, duplicate-aware Gson streaming JSON utilities. */
public final class StrictJson {
    private static final int MAX_NESTING_DEPTH = 256;

    private StrictJson(){
    }

    public static JsonElement parse(String json){
        if(json == null){
            throw new IllegalArgumentException("JSON source cannot be null");
        }
        return parse(new StringReader(json));
    }

    public static JsonElement parse(Reader source){
        if(source == null){
            throw new IllegalArgumentException("JSON reader cannot be null");
        }
        JsonReader reader = new JsonReader(source);
        reader.setStrictness(Strictness.STRICT);
        JsonElement result = readValue(reader, "", 0);
        try {
            if(reader.peek() != JsonToken.END_DOCUMENT){
                throw error("", "unexpected content after the root JSON value", null);
            }
        } catch(IOException | IllegalStateException exception){
            throw error("", "cannot finish reading JSON", exception);
        }
        return result;
    }

    public static String write(JsonElement element){
        StringWriter target = new StringWriter();
        write(element, target);
        return target.toString();
    }

    public static void write(JsonElement element, Writer target){
        if(element == null){
            throw new IllegalArgumentException("JSON element cannot be null");
        }
        if(target == null){
            throw new IllegalArgumentException("JSON writer cannot be null");
        }
        JsonWriter writer = new JsonWriter(target);
        writer.setStrictness(Strictness.STRICT);
        writer.setHtmlSafe(false);
        writer.setSerializeNulls(true);
        try {
            writeValue(writer, element, "", 0);
            writer.flush();
        } catch(StrictJsonException exception){
            throw exception;
        } catch(IOException | IllegalStateException exception){
            throw error("", "cannot write JSON", exception);
        }
    }

    private static JsonElement readValue(JsonReader reader, String pointer, int depth){
        if(depth > MAX_NESTING_DEPTH){
            throw error(pointer, "JSON nesting exceeds " + MAX_NESTING_DEPTH, null);
        }
        try {
            return switch(reader.peek()){
                case BEGIN_OBJECT -> readObject(reader, pointer, depth);
                case BEGIN_ARRAY -> readArray(reader, pointer, depth);
                case STRING -> new JsonPrimitive(reader.nextString());
                case NUMBER -> readNumber(reader, pointer);
                case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
                case NULL -> {
                    reader.nextNull();
                    yield JsonNull.INSTANCE;
                }
                case END_ARRAY, END_OBJECT, END_DOCUMENT, NAME ->
                        throw error(pointer, "expected a JSON value", null);
            };
        } catch(StrictJsonException exception){
            throw exception;
        } catch(IOException | IllegalStateException exception){
            throw error(pointer, "malformed JSON", exception);
        }
    }

    private static JsonObject readObject(JsonReader reader, String pointer, int depth)
            throws IOException {
        reader.beginObject();
        JsonObject result = new JsonObject();
        Set<String> names = new HashSet<>();
        while(reader.hasNext()){
            String name = reader.nextName();
            String childPointer = append(pointer, name);
            if(!names.add(name)){
                throw error(childPointer, "duplicate object key '" + name + "'", null);
            }
            result.add(name, readValue(reader, childPointer, depth + 1));
        }
        reader.endObject();
        return result;
    }

    private static JsonArray readArray(JsonReader reader, String pointer, int depth)
            throws IOException {
        reader.beginArray();
        JsonArray result = new JsonArray();
        int index = 0;
        while(reader.hasNext()){
            result.add(readValue(reader, append(pointer, Integer.toString(index)), depth + 1));
            index++;
        }
        reader.endArray();
        return result;
    }

    private static JsonPrimitive readNumber(JsonReader reader, String pointer)
            throws IOException {
        String literal = reader.nextString();
        if("NaN".equals(literal)
                || "Infinity".equals(literal)
                || "+Infinity".equals(literal)
                || "-Infinity".equals(literal)){
            throw error(pointer, "non-finite numbers are not valid JSON", null);
        }
        try {
            return new JsonPrimitive(new BigDecimal(literal));
        } catch(NumberFormatException exception){
            throw error(pointer, "invalid JSON number '" + literal + "'", exception);
        }
    }

    private static void writeValue(JsonWriter writer,
                                   JsonElement element,
                                   String pointer,
                                   int depth) throws IOException {
        if(depth > MAX_NESTING_DEPTH){
            throw error(pointer, "JSON nesting exceeds " + MAX_NESTING_DEPTH, null);
        }
        if(element == null || element.isJsonNull()){
            writer.nullValue();
            return;
        }
        if(element.isJsonObject()){
            writer.beginObject();
            for(Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()){
                writer.name(entry.getKey());
                writeValue(writer, entry.getValue(), append(pointer, entry.getKey()), depth + 1);
            }
            writer.endObject();
            return;
        }
        if(element.isJsonArray()){
            writer.beginArray();
            JsonArray array = element.getAsJsonArray();
            for(int index = 0; index < array.size(); index++){
                writeValue(
                        writer,
                        array.get(index),
                        append(pointer, Integer.toString(index)),
                        depth + 1
                );
            }
            writer.endArray();
            return;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if(primitive.isBoolean()){
            writer.value(primitive.getAsBoolean());
        } else if(primitive.isString()){
            writer.value(primitive.getAsString());
        } else if(primitive.isNumber()){
            Number number = primitive.getAsNumber();
            if(isNonFinite(number)){
                throw error(pointer, "non-finite numbers are not valid JSON", null);
            }
            writer.value(number);
        } else {
            throw error(pointer, "unsupported JSON primitive", null);
        }
    }

    private static boolean isNonFinite(Number number){
        if(number instanceof Double doubleValue){
            return !Double.isFinite(doubleValue);
        }
        if(number instanceof Float floatValue){
            return !Float.isFinite(floatValue);
        }
        String literal = number.toString();
        return "NaN".equals(literal)
                || "Infinity".equals(literal)
                || "+Infinity".equals(literal)
                || "-Infinity".equals(literal);
    }

    private static String append(String pointer, String segment){
        return pointer + "/" + segment.replace("~", "~0").replace("/", "~1");
    }

    private static StrictJsonException error(String pointer,
                                             String message,
                                             Throwable cause){
        return new StrictJsonException(pointer, message, cause);
    }

    public static final class StrictJsonException extends IllegalArgumentException {
        private final String pointer;

        private StrictJsonException(String pointer, String message, Throwable cause) {
            super(displayPointer(pointer) + ": " + message, cause);
            this.pointer = pointer;
        }

        public String pointer(){
            return pointer;
        }

        public String getPointer(){
            return pointer;
        }

        private static String displayPointer(String pointer){
            return pointer == null || pointer.isEmpty() ? "/" : pointer;
        }
    }
}

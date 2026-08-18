package io.github.rookiecuzz.rookieregions.persistence.codec;

/** A schema or JSON error located with an RFC 6901 JSON Pointer. */
public final class DocumentFormatException extends IllegalArgumentException {
    private final String pointer;

    public DocumentFormatException(String pointer, String message) {
        this(pointer, message, null);
    }

    public DocumentFormatException(String pointer, String message, Throwable cause) {
        super(displayPointer(pointer) + ": " + message, cause);
        this.pointer = pointer == null ? "" : pointer;
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

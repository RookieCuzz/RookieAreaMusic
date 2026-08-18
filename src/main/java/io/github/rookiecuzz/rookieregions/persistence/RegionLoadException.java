package io.github.rookiecuzz.rookieregions.persistence;

import java.io.IOException;
import java.nio.file.Path;

/** Identifies the file and JSON pointer that prevented an atomic region load. */
public final class RegionLoadException extends IOException {
    private final Path source;
    private final String pointer;

    public RegionLoadException(Path source, String pointer, String message) {
        this(source, pointer, message, null);
    }

    public RegionLoadException(Path source,
                               String pointer,
                               String message,
                               Throwable cause) {
        super(formatMessage(source, pointer, message), cause);
        this.source = source == null ? null : source.toAbsolutePath().normalize();
        this.pointer = pointer == null ? "" : pointer;
    }

    public Path source() {
        return source;
    }

    public Path getSource() {
        return source;
    }

    public Path path() {
        return source;
    }

    public Path getPath() {
        return source;
    }

    public String pointer() {
        return pointer;
    }

    public String getPointer() {
        return pointer;
    }

    private static String formatMessage(Path source,
                                        String pointer,
                                        String message) {
        String location = source == null ? "region load" : source.toString();
        if(pointer != null && !pointer.isEmpty()) {
            location += "#" + pointer;
        }
        return location + ": " + (message == null ? "cannot load regions" : message);
    }
}

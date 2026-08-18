package io.github.rookiecuzz.rookieregions.editor.model;

import io.github.rookiecuzz.rookieregions.core.shape.Point2D;

/** One exact Minecraft block coordinate selected by an editor actor. */
public record BlockPoint(int x, int y, int z) {
    public Point2D horizontal() {
        return new Point2D(x, z);
    }
}

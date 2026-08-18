package io.github.rookiecuzz.rookieregions.core.shape;

/** One finite point in a shape's horizontal X/Z plane. */
public record Point2D(double x, double z) {
    public Point2D {
        if(!Double.isFinite(x) || !Double.isFinite(z)){
            throw new IllegalArgumentException("point coordinates must be finite");
        }
        x = normalizeZero(x);
        z = normalizeZero(z);
    }

    private static double normalizeZero(double value){
        return value == 0.0d ? 0.0d : value;
    }
}

package io.github.rookiecuzz.rookieregions.core.shape;

/** Explicit geometry covering every finite coordinate in a world. */
public enum GlobalShape implements RegionShape {
    INSTANCE;

    @Override
    public Bounds3D bounds(){
        return Bounds3D.unbounded();
    }

    @Override
    public boolean contains(double x, double y, double z){
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}

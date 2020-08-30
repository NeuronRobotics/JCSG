package eu.mihosoft.vrl.v3d;

public interface Transformable {
    /**
     * @param transform the transform
     * @return a transformed version of this object
     */
    Transformable transformed(Transform transform);
    Transformable clone();
}

package eu.mihosoft.vrl.v3d;

public interface MutableTransformable extends Transformable {
    /**
     * Transforms the object in place.
     *
     * @param transform the transform
     * @return this
     */
    MutableTransformable transform(Transform transform);
}

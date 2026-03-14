package eu.mihosoft.vrl.v3d.thumbnail;

import javax.vecmath.Matrix4d;

import eu.mihosoft.vrl.v3d.Transform;

public class TransformConverter {
	public static Transform fromAffine(javafx.scene.transform.Affine rotations) {
		double[] v = new double[]{rotations.getMxx(), rotations.getMxy(), rotations.getMxz(), 0, rotations.getMyx(),
				rotations.getMyy(), rotations.getMyz(), 0, rotations.getMzx(), rotations.getMzy(), rotations.getMzz(),
				0, rotations.getTx(), rotations.getTy(), rotations.getTz(), 1};
		Matrix4d rotation = new Matrix4d(v);
		// eu.mihosoft.vrl.v3d.Transform transform = new
		// eu.mihosoft.vrl.v3d.Transform(rotation);
		eu.mihosoft.vrl.v3d.Transform transform = new eu.mihosoft.vrl.v3d.Transform();
		// com.neuronrobotics.sdk.common.Log.error("Incoming "+rotations);
		// com.neuronrobotics.sdk.common.Log.error("Converted to "+transform);
		return transform;
	}
}

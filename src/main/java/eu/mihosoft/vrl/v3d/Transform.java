/**
 * Transform.java
 *
 * Copyright 2014-2014 Michael Hoffer info@michaelhoffer.de. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY Michael Hoffer info@michaelhoffer.de "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL Michael Hoffer info@michaelhoffer.de OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of Michael Hoffer
 * info@michaelhoffer.de.
 */
package eu.mihosoft.vrl.v3d;

import javax.vecmath.Matrix4d;
import javax.vecmath.Quat4d;

import eu.mihosoft.vvecmath.Vector3d;

// TODO: Auto-generated Javadoc
/**
 * Transform. Transformations (translation, rotation, scale) can be applied to
 * geometrical objects like {@link CSG}, {@link Polygon}, {@link Vertex} and
 * {@link Vector3d}.
 *
 * This transform class uses the builder pattern to define combined
 * transformations.<br>
 * <br>
 *
 * Example:
 *
 * 
 * // t applies rotation and translation Transform t =
 * new Transform().rotX(45).translate(2,1,0);
 * 
 *
 * TODO: use quaternions for rotations.
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public class Transform extends eu.mihosoft.vvecmath.Transform{

	/**
	 * Internal 4x4 matrix.
	 */
	private final Matrix4d m;

	/**
	 * Constructor.
	 *
	 * Creates a unit transform.
	 */
	public Transform() {
		m = new Matrix4d();
		getInternalMatrix().m00 = 1;
		getInternalMatrix().m11 = 1;
		getInternalMatrix().m22 = 1;
		getInternalMatrix().m33 = 1;
	}

	/**
	 * Returns a new unity transform.
	 *
	 * @return unity transform
	 */
	public static Transform unity() {
		return new Transform();
	}

	/**
	 * Constructor.
	 *
	 * @param m
	 *            matrix
	 */
	public Transform(Matrix4d m) {
		this.m = m;
	}

	/**
	 * Applies rotation operation around the x axis to this transform.
	 *
	 * @param degrees
	 *            degrees
	 * @return this transform
	 */
	public Transform rotX(double degrees) {
		double radians = degrees * Math.PI * (1.0 / 180.0);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		double elemenents[] = { 1, 0, 0, 0, 0, cos, sin, 0, 0, -sin, cos, 0, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies rotation operation around the y axis to this transform.
	 *
	 * @param degrees
	 *            degrees
	 *
	 * @return this transform
	 */
	public Transform rotY(double degrees) {
		double radians = degrees * Math.PI * (1.0 / 180.0);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		double elemenents[] = { cos, 0, -sin, 0, 0, 1, 0, 0, sin, 0, cos, 0, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies rotation operation around the z axis to this transform.
	 *
	 * @param degrees
	 *            degrees
	 *
	 * @return this transform
	 */
	public Transform rotZ(double degrees) {
		double radians = degrees * Math.PI * (1.0 / 180.0);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		double elemenents[] = { cos, sin, 0, 0, -sin, cos, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a rotation operation to this transform.
	 *
	 * @param x
	 *            x axis rotation (degrees)
	 * @param y
	 *            y axis rotation (degrees)
	 * @param z
	 *            z axis rotation (degrees)
	 *
	 * @return this transform
	 */
	public Transform rot(double x, double y, double z) {
		return rotX(x).rotY(y).rotZ(z);
	}

	/**
	 * Applies a rotation operation to this transform.
	 *
	 * @param vec
	 *            axis rotation for x, y, z (degrees)
	 *
	 * @return this transform
	 */
	public Transform rot(Vector3d vec) {

		// TODO: use quaternions
		return rotX(vec.getX()).rotY(vec.getY()).rotZ(vec.getZ());
	}

	/**
	 * Applies a translation operation to this transform.
	 *
	 * @param vec
	 *            translation vector (x,y,z)
	 *
	 * @return this transform
	 */
	public Transform translate(Vector3d vec) {
		return translate(vec.getX(), vec.getY(), vec.getZ());
	}

	/**
	 * Applies a translation operation to this transform.
	 *
	 * @param x
	 *            translation (x axis)
	 * @param y
	 *            translation (y axis)
	 * @param z
	 *            translation (z axis)
	 *
	 * @return this transform
	 */
	public Transform translate(double x, double y, double z) {
		double elemenents[] = { 1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a translation operation to this transform.
	 *
	 * @param value
	 *            translation (x axis)
	 *
	 * @return this transform
	 */
	public Transform translateX(double value) {
		double elemenents[] = { 1, 0, 0, value, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	// rotations
	public double getQuataurionX() {
		Matrix4d rotation = getInternalMatrix();
		Quat4d q1 = new Quat4d();
		rotation.get(q1);

		return q1.x;
	}

	public double getQuataurionY() {
		Matrix4d rotation = getInternalMatrix();
		Quat4d q1 = new Quat4d();
		rotation.get(q1);

		return q1.y;
	}

	public double getQuataurionZ() {
		Matrix4d rotation = getInternalMatrix();
		Quat4d q1 = new Quat4d();
		rotation.get(q1);

		return q1.z;
	}

	public double getQuataurionW() {
		Matrix4d rotation = getInternalMatrix();
		Quat4d q1 = new Quat4d();
		rotation.get(q1);

		return q1.w;
	}

	// translations
	public double getX() {
		javax.vecmath.Vector3d t1 = new javax.vecmath.Vector3d();
		getInternalMatrix().get(t1);
		return t1.x;
	}

	public double getY() {
		javax.vecmath.Vector3d t1 = new javax.vecmath.Vector3d();
		getInternalMatrix().get(t1);
		return t1.y;
	}

	public double getZ() {
		javax.vecmath.Vector3d t1 = new javax.vecmath.Vector3d();
		getInternalMatrix().get(t1);
		return t1.z;
	}

	/**
	 * Applies a translation operation to this transform.
	 *
	 * @param value
	 *            translation (y axis)
	 *
	 * @return this transform
	 */
	public Transform translateY(double value) {
		double elemenents[] = { 1, 0, 0, 0, 0, 1, 0, value, 0, 0, 1, 0, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a translation operation to this transform.
	 *
	 * @param value
	 *            translation (z axis)
	 *
	 * @return this transform
	 */
	public Transform translateZ(double value) {
		double elemenents[] = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, value, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a mirror operation to this transform.
	 *
	 * @param plane
	 *            the plane that defines the mirror operation
	 *
	 * @return this transform
	 */
	public Transform mirror(Plane plane) {

		System.err.println("WARNING: I'm too dumb to implement the mirror() operation correctly. Please fix me!");

		double nx = plane.normal.getX();
		double ny = plane.normal.getY();
		double nz = plane.normal.getZ();
		double w = plane.dist;
		double elemenents[] = { (1.0 - 2.0 * nx * nx), (-2.0 * ny * nx), (-2.0 * nz * nx), 0, (-2.0 * nx * ny),
				(1.0 - 2.0 * ny * ny), (-2.0 * nz * ny), 0, (-2.0 * nx * nz), (-2.0 * ny * nz), (1.0 - 2.0 * nz * nz),
				0, (-2.0 * nx * w), (-2.0 * ny * w), (-2.0 * nz * w), 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation to this transform.
	 *
	 * @param vec
	 *            vector that specifies scale (x,y,z)
	 *
	 * @return this transform
	 */
	public Transform scale(Vector3d vec) {

		if (vec.getX() == 0 || vec.getY() == 0 || vec.getZ() == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = { vec.getX(), 0, 0, 0, 0, vec.getY(), 0, 0, 0, 0, vec.getZ(), 0, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation to this transform.
	 *
	 * @param x
	 *            x scale value
	 * @param y
	 *            y scale value
	 * @param z
	 *            z scale value
	 *
	 * @return this transform
	 */
	public Transform scale(double x, double y, double z) {

		if (x == 0 || y == 0 || z == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = { x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation to this transform.
	 *
	 * @param s
	 *            s scale value (x, y and z)
	 *
	 * @return this transform
	 */
	public Transform scale(double s) {

		if (s == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = { s, 0, 0, 0, 0, s, 0, 0, 0, 0, s, 0, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation (x axis) to this transform.
	 *
	 * @param s
	 *            x scale value
	 *
	 * @return this transform
	 */
	public Transform scaleX(double s) {

		if (s == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = { s, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation (y axis) to this transform.
	 *
	 * @param s
	 *            y scale value
	 *
	 * @return this transform
	 */
	public Transform scaleY(double s) {

		if (s == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = { 1, 0, 0, 0, 0, s, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation (z axis) to this transform.
	 *
	 * @param s
	 *            z scale value
	 *
	 * @return this transform
	 */
	public Transform scaleZ(double s) {

		if (s == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, s, 0, 0, 0, 0, 1 };
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies this transform to the specified vector.
	 *
	 * @param vec
	 *            vector to transform
	 *
	 * @return the specified vector
	 */
	public Vector3d transform(Vector3d vec) {
		double x, y;
		x = getInternalMatrix().m00 * vec.getX() + getInternalMatrix().m01 * vec.getY() + getInternalMatrix().m02 * vec.getZ()
				+ getInternalMatrix().m03;
		y = getInternalMatrix().m10 * vec.getX() + getInternalMatrix().m11 * vec.getY() + getInternalMatrix().m12 * vec.getZ()
				+ getInternalMatrix().m13;

		vec = Vector3d.xyz(x, y, getInternalMatrix().m20 * vec.getX() + getInternalMatrix().m21 * vec.getY() + getInternalMatrix().m22 * vec.getZ()
				+ getInternalMatrix().m23);
		return vec;
	}

	/**
	 * Applies this transform to the specified vector.
	 *
	 * @param vec
	 *            vector to transform
	 * @param amount
	 *            transform amount (0 = 0 %, 1 = 100%)
	 *
	 * @return the specified vector
	 */
	public Vector3d transform(Vector3d vec, double amount) {

		double prevX = vec.getX();
		double prevY = vec.getY();
		double prevZ = vec.getZ();

		final double x, y;
		x = getInternalMatrix().m00 * vec.getX() + getInternalMatrix().m01 * vec.getY() + getInternalMatrix().m02 * vec.getZ()
				+ getInternalMatrix().m03;
		y = getInternalMatrix().m10 * vec.getX() + getInternalMatrix().m11 * vec.getY() + getInternalMatrix().m12 * vec.getZ()
				+ getInternalMatrix().m13;
		vec = Vector3d.xyz(x, y, getInternalMatrix().m20 * vec.getX() + getInternalMatrix().m21 * vec.getY() + getInternalMatrix().m22 * vec.getZ()
				+ getInternalMatrix().m23);


		double diffX = vec.getX() - prevX;
		double diffY = vec.getY() - prevY;
		double diffZ = vec.getZ() - prevZ;

		vec = Vector3d.xyz(prevX + (diffX) * amount,
				prevY + (diffY) * amount,
				 prevZ + (diffZ) * amount);

		return vec;
	}

	// // Multiply a CSG.Vector3D (interpreted as 3 column, 1 row) by this matrix
	// // (result = v*M)
	// // Fourth element is taken as 1
	// leftMultiply1x3Vector: function(v) {
	// var v0 = v._x;
	// var v1 = v._y;
	// var v2 = v._z;
	// var v3 = 1;
	// var x = v0 * this.elements[0] + v1 * this.elements[4] + v2 * this.elements[8]
	// + v3 * this.elements[12];
	// var y = v0 * this.elements[1] + v1 * this.elements[5] + v2 * this.elements[9]
	// + v3 * this.elements[13];
	// var z = v0 * this.elements[2] + v1 * this.elements[6] + v2 *
	// this.elements[10] + v3 * this.elements[14];
	// var w = v0 * this.elements[3] + v1 * this.elements[7] + v2 *
	// this.elements[11] + v3 * this.elements[15];
	// // scale such that fourth element becomes 1:
	// if(w != 1) {
	// var invw = 1.0 / w;
	// x *= invw;
	// y *= invw;
	// z *= invw;
	// }
	// return new CSG.Vector3D(x, y, z);
	// },
	/**
	 * Performs an SVD normalization of the underlying matrix to calculate and
	 * return the uniform scale factor. If the matrix has non-uniform scale factors,
	 * the largest of the x, y, and z scale factors distill be returned.
	 *
	 * Note: this transformation is not modified.
	 *
	 * @return the scale factor of this transformation
	 */
	public double getScale() {
		return getInternalMatrix().getScale();
	}

	/**
	 * Indicates whether this transform performs a mirror operation, i.e., flips the
	 * orientation.
	 *
	 * @return <code>true</code> if this transform performs a mirror operation;
	 *         <code>false</code> otherwise
	 */
	public boolean isMirror() {
		return getInternalMatrix().determinant() < 0;
	}

	/**
	 * Applies the specified transform to this transform.
	 *
	 * @param t
	 *            transform to apply
	 *
	 * @return this transform
	 */
	public Transform apply(Transform t) {
		getInternalMatrix().mul(t.getInternalMatrix());
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return getInternalMatrix().toString();
	}

	public Matrix4d getInternalMatrix() {
		return m;
	}

	/**
	 * Return a new transform that is inverted
	 * 
	 * @return
	 */
	public Transform inverse() {
		Transform tr = new Transform().apply(this);

		tr.getInternalMatrix().invert();

		return tr;
	}

	/**
	 * Apply an inversion to this transform
	 * 
	 * @return
	 */
	public Transform invert() {
		getInternalMatrix().invert();

		return this;
	}
	
	public Transform move(Number x, Number y, Number z) {
		return new Transform().translate(x.doubleValue(),y.doubleValue(),z.doubleValue()).apply(this);
	}
	public Transform move(Vertex v) {
		return new Transform().translate(v.getX(),v.getY(),v.getZ()).apply(this);
	}
	public Transform move(Vector3d v) {
		return new Transform().translate(v.getX(),v.getY(),v.getZ()).apply(this);
	}
	public Transform move(Number[] posVector) {
		return move(posVector[0], posVector[1], posVector[2]);
	}

	/**
	 * Movey.
	 *
	 * @param howFarToMove
	 *            the how far to move
	 * @return the csg
	 */
	// Helper/wrapper functions for movement
	public Transform movey(Number howFarToMove) {
		return new Transform().translateY(howFarToMove.doubleValue()).apply(this);
	}

	/**
	 * Movez.
	 *
	 * @param howFarToMove
	 *            the how far to move
	 * @return the csg
	 */
	public Transform movez(Number howFarToMove) {
		return new Transform().translateZ(howFarToMove.doubleValue()).apply(this);
	}

	/**
	 * Movex.
	 *
	 * @param howFarToMove
	 *            the how far to move
	 * @return the csg
	 */
	public Transform movex(Number howFarToMove) {
		return new Transform().translateX(howFarToMove.doubleValue()).apply(this);
	}
	
	/**
	 * mirror about y axis.
	 *

	 * @return the csg
	 */
	// Helper/wrapper functions for movement
	public Transform mirrory() {
		return this.scaleY(-1);
	}

	/**
	 * mirror about z axis.
	 *
	 * @return the csg
	 */
	public Transform mirrorz() {
		return this.scaleZ(-1);
	}

	/**
	 * mirror about  x axis.
	 *
	 * @return the csg
	 */
	public Transform mirrorx() {
		return this.scaleX(-1);
	}

	/**
	 * Rotz.
	 *
	 * @param degreesToRotate
	 *            the degrees to rotate
	 * @return the csg
	 */
	// Rotation function, rotates the object
	public Transform rotz(Number degreesToRotate) {
		return new Transform().rotZ(degreesToRotate.doubleValue()).apply(this);
	}

	/**
	 * Roty.
	 *
	 * @param degreesToRotate
	 *            the degrees to rotate
	 * @return the csg
	 */
	public Transform roty(Number degreesToRotate) {
		return new Transform().rotY(degreesToRotate.doubleValue()).apply(this);
	}

	/**
	 * Rotx.
	 *
	 * @param degreesToRotate
	 *            the degrees to rotate
	 * @return the csg
	 */
	public Transform rotx(Number degreesToRotate) {
		return new Transform().rotX(degreesToRotate.doubleValue()).apply(this);
	}

}

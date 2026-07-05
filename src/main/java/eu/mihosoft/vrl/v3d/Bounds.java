/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.mihosoft.vrl.v3d;

import java.io.Serializable;

import com.google.gson.annotations.Expose;

//  Auto-generated Javadoc
/**
 * Bounding box for CSGs.
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public class Bounds implements Serializable {

	private static final long serialVersionUID = -5067189300959316667L;

	/** The center. */
	@Expose(serialize = true, deserialize = true)
	private final Vector3d center;

	/** The bounds. */
	@Expose(serialize = true, deserialize = true)
	private final Vector3d bounds;

	/** The min. */
	@Expose(serialize = true, deserialize = true)
	private final Vector3d min;

	/** The max. */
	@Expose(serialize = true, deserialize = true)
	private final Vector3d max;

	// /** The csg. */
	// @Expose (serialize = false, deserialize = false)
	// private CSG csg;
	//
	// /** The cube. */
	// @Expose (serialize = false, deserialize = false)
	// private Cube cube;

	/**
	 * Constructor.
	 *
	 * @param min
	 *            min x,y,z values
	 * @param max
	 *            max x,y,z values
	 */
	public Bounds(Vector3d min, Vector3d max) {
		this.center = new Vector3d((max.x + min.x) / 2, (max.y + min.y) / 2, (max.z + min.z) / 2);

		this.bounds = new Vector3d(Math.abs(max.x - min.x), Math.abs(max.y - min.y), Math.abs(max.z - min.z));

		this.min = min.clone();
		this.max = max.clone();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Bounds clone() {
		return new Bounds(min.clone(), max.clone());
	}

	/**
	 * Returns the position of the center.
	 *
	 * @return the center position
	 */
	public Vector3d getCenter() {
		return center;
	}

	/**
	 * Returns the bounds (width,height,depth).
	 *
	 * @return the bounds (width,height,depth)
	 */
	public Vector3d getBounds() {
		return bounds;
	}

	/**
	 * Returns this bounding box as csg.
	 *
	 * @return this bounding box as csg
	 */
	public CSG toCSG() {
		CSG csg = null;
		Cube cube = null;
		if (csg == null) {
			cube = new Cube(center, bounds);
			csg = cube.toCSG();
		}

		return csg;
	}

	/**
	 * Returns this bounding box as cube.
	 *
	 * @return this bounding box as cube
	 */
	public Cube toCube() {
		CSG csg = null;
		Cube cube = null;
		if (cube == null) {
			cube = new Cube(center, bounds);
			csg = cube.toCSG();
		}

		return cube;
	}

	/**
	 * Indicates whether the specified vertex is contained within this bounding box
	 * (check includes box boundary).
	 *
	 * @param v
	 *            vertex to check
	 * @return {@code true} if the vertex is contained within this bounding box;
	 *         {@code false} otherwise
	 */
	public boolean contains(Vertex v) {
		return contains(v.pos);
	}

	/**
	 * Indicates whether the specified point is contained within this bounding box
	 * (check includes box boundary).
	 *
	 * @param v
	 *            vertex to check
	 * @return {@code true} if the point is contained within this bounding box;
	 *         {@code false} otherwise
	 */
	public boolean contains(Vector3d v) {
		boolean inX = min.x <= v.x && v.x <= max.x;
		boolean inY = min.y <= v.y && v.y <= max.y;
		boolean inZ = min.z <= v.z && v.z <= max.z;

		return inX && inY && inZ;
	}

	/**
	 * Indicates whether the specified polygon is contained within this bounding box
	 * (check includes box boundary).
	 *
	 * @param p
	 *            polygon to check
	 * @return {@code true} if the polygon is contained within this bounding box;
	 *         {@code false} otherwise
	 */
	public boolean contains(Polygon p) {
		return p.getVertices().stream().allMatch(v -> contains(v));
	}

	/**
	 * Indicates whether the specified bounding box intersects with this bounding
	 * box (check includes box boundary).
	 *
	 * @param b
	 *            box to check
	 * @return {@code true} if the bounding box intersects this bounding box;
	 *         {@code false} otherwise
	 */
	public boolean intersects(Bounds b) {

		if (b.getMin().x > this.getMax().x || b.getMax().x < this.getMin().x) {
			return false;
		}
		if (b.getMin().y > this.getMax().y || b.getMax().y < this.getMin().y) {
			return false;
		}
		if (b.getMin().z > this.getMax().z || b.getMax().z < this.getMin().z) {
			return false;
		}

		return true;

	}

	/**
	 * Gets the min.
	 *
	 * @return the min x,y,z values
	 */
	public Vector3d getMin() {
		return min;
	}

	/**
	 * Gets the max.
	 *
	 * @return the max x,y,z values
	 */
	public Vector3d getMax() {
		return max;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "[center: " + center + ", bounds: " + bounds + "]";
	}

	public boolean contains(Transform com) {
		return contains(new Vector3d(com.getX(), com.getY(), com.getZ()));
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return CenterX
	 */
	public double getCenterX() {
		return ((getMinX() / 2) + (getMaxX() / 2));
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return CenterY
	 */
	public double getCenterY() {
		return ((getMinY() / 2) + (getMaxY() / 2));
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return CenterZ
	 */
	public double getCenterZ() {
		return ((getMinZ() / 2) + (getMaxZ() / 2));
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return MaxX
	 */
	public double getMaxX() {
		return getMax().x;
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return MaxY
	 */
	public double getMaxY() {
		return getMax().y;
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return MaxZ
	 */
	public double getMaxZ() {
		return getMax().z;
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return MinX
	 */
	public double getMinX() {
		return getMin().x;
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return MinY
	 */
	public double getMinY() {
		return getMin().y;
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return tMinZ
	 */
	public double getMinZ() {
		return getMin().z;
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return MinX
	 */
	public double getTotalX() {
		return (-this.getMinX() + this.getMaxX());
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return MinY
	 */
	public double getTotalY() {
		return (-this.getMinY() + this.getMaxY());
	}

	/**
	 * Helper function wrapping bounding box values
	 *
	 * @return tMinZ
	 */
	public double getTotalZ() {
		return (-this.getMinZ() + this.getMaxZ());
	}

	public boolean isBoundsTouching(Bounds incoming) {
		return this.getMaxX() > incoming.getMinX() && this.getMinX() < incoming.getMaxX()
				&& this.getMaxY() > incoming.getMinY() && this.getMinY() < incoming.getMaxY()
				&& this.getMaxZ() > incoming.getMinZ() && this.getMinZ() < incoming.getMaxZ();
	}

}

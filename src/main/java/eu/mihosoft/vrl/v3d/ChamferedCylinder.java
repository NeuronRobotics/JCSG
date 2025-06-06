package eu.mihosoft.vrl.v3d;

import java.util.List;

public class ChamferedCylinder extends Primitive {
	double r, h, chamferHeight;
	int sides = -1;

	/** The properties. */
	private final PropertyStorage properties = new PropertyStorage();

	public PropertyStorage getProperties() {
		return properties;
	}

	/**
	 * Constructor. Creates a new cuboid with center {@code [0,0,0]} and with the
	 * specified dimensions.
	 *
	 * @param r             radius
	 * @param h             height
	 * @param chamferHeight the chamfer height
	 * @param sides the number of slices the chamfered cylender should have
	 */
	public ChamferedCylinder(double r, double h, double chamferHeight, int sides) {
		this.r = r;
		this.h = h;
		this.chamferHeight = chamferHeight;
		this.sides = sides;
	}

	/**
	 * Constructor. Creates a new cuboid with center {@code [0,0,0]} and with the
	 * specified dimensions.
	 *
	 * @param r             radius
	 * @param h             height
	 * @param chamferHeight the chamfer height
	 */
	public ChamferedCylinder(double r, double h, double chamferHeight) {
		this(r, h, chamferHeight, 16);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see eu.mihosoft.vrl.v3d.Primitive#toPolygons()
	 */
	@Override
	public List<Polygon> toPolygons() {
		CSG cube1 = new Cylinder(r - chamferHeight, r - chamferHeight, h, sides).toCSG();
		CSG cube2 = new Cylinder(r, r, h - chamferHeight * 2, sides).toCSG().movez(chamferHeight);
		return cube1.union(cube2).hull().getPolygons();
	}
}

/**
 * Hexagon.java
 */
package eu.mihosoft.vrl.v3d;

public class Hexagon extends Primitive {

	/**
	 * Hexagon circumscribed radius.
	 */
	private double flatToFlatDistance = 1;

	/**
	 * Hexagon circumscribed radius.
	 */
	private double height = 1;

	/** The properties. */
	private final PropertyStorage properties = new PropertyStorage();
	private double nunRad;
	private CSG head;
	/**
	 * Constructor. Creates a new Hexagon that would fir a wrench of size
	 * flatToFlatDIstance radius {@code size}.
	 *
	 * @param flatToFlatDIstance
	 *            the size of wrench that this nut would fit
	 *
	 */
	public Hexagon(double flatToFlatDIstance, double height) {
		this.flatToFlatDistance = flatToFlatDIstance;
		this.height = height;
		nunRad = ((flatToFlatDIstance / Math.sqrt(3)));

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see eu.mihosoft.vrl.v3d.Primitive#toPolygons()
	 */
	@Override
	public CSG toCSG() {
		head = new Cylinder(nunRad, nunRad, height, (int) 6).toCSG();
		return head;
	}

	/**
	 * Gets the flatToFlatDistance.
	 *
	 * @return the flatToFlatDistance
	 */
	public double getFlatToFlatDistance() {
		return flatToFlatDistance;
	}

	/**
	 * Gets the diameter of the outscribed circle. This is the Point To Point
	 * Distance
	 *
	 * @return the Point To Point Distance
	 */
	public double getPointToPointDistance() {
		return nunRad * 2;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see eu.mihosoft.vrl.v3d.Primitive#getProperties()
	 */
	@Override
	public PropertyStorage getProperties() {
		return properties;
	}

}

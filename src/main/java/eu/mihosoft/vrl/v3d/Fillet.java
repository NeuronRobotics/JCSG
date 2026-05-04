package eu.mihosoft.vrl.v3d;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.paint.Color;

public class Fillet extends Primitive {

	double w, h;
	private double numArcPoints = 12;
	private static double filletOfset = 0.001;
	/** The properties. */
	private final PropertyStorage properties = new PropertyStorage();

	public PropertyStorage getProperties() {
		return properties;
	}

	/**
	 * Constructor. Creates a new cuboid with center {@code [0,0,0]} and with the
	 * specified dimensions.
	 *
	 * @param w
	 *            width
	 * @param h
	 *            height
	 */
	public Fillet(double w, double h) {
		this.w = w;
		this.h = h;
	}

	public static CSG corner(double rad, double angle, double numArcPoints) throws ColinearPointsException {
		// --- Build the concave quarter-circle fillet profile ---
		//
		// Shape (in XY plane, swept around Z):
		//
		// (0,rad) ──arc─── (rad,rad)
		// │ center │
		// │ at (rad,rad) │
		// (0,0) ────────── (rad,0)
		//
		// The arc is concave (bites inward), matching what the
		// old Fillet+Sphere difference was carving out.

		List<Vector3d> pts = new ArrayList<>();

		// Corner at origin
		pts.add(new Vector3d(0, 0, 0));

		// Straight edge along +X
		pts.add(new Vector3d(rad, 0, 0));

		// Concave quarter-circle arc: center=(rad,rad), radius=rad
		// sweeps from 270° → 180° (i.e. (rad,0) → (0,rad))
		for (int i = 1; i < numArcPoints; i++) {
			double a = Math.toRadians(270.0 - 90.0 * ((double)i) / numArcPoints);
			double x = rad + rad * Math.cos(a);
			double y = rad + rad * Math.sin(a);
			if(x<filletOfset)
				x=filletOfset;
			if(y<filletOfset)
				y=filletOfset;
			pts.add(new Vector3d(x, y, 0));
		}
		pts.add(new Vector3d(0, rad, 0));
		// Straight edge back down to origin closes the polygon
		// (fromPoints auto-closes, so no need to re-add (0,0,0))

		Polygon profile = Polygon.fromPoints(pts);

		// --- Sweep the profile around the Z axis ---
		// radius=0 → profile is already positioned relative to the axis
		// z=0 → no axial offset
		// steps=32 → match arc resolution for a smooth result
		return Extrude.sweep(profile, (angle+1) / numArcPoints, 0, 0, (int) numArcPoints).roty(90).rotz(-0.5);
	}
	public static CSG outerChamfer(CSG base, double rad) throws ColinearPointsException {
		return fillet(base, rad, true, 1);
	}
	public static CSG innerChamfer(CSG base, double rad) throws ColinearPointsException {
		return fillet(base, rad, false, 1);
	}
	public static CSG outerFillet(CSG base, double rad) throws ColinearPointsException {
		return fillet(base, rad, true, 16);
	}
	public static CSG innerFillet(CSG base, double rad) throws ColinearPointsException {
		return fillet(base, rad, false, 16);
	}
	public static CSG outerFillet(CSG base, double rad,int faces) throws ColinearPointsException {
		return fillet(base, rad, true, faces);
	}
	public static CSG innerFillet(CSG base, double rad,int faces) throws ColinearPointsException {
		return fillet(base, rad, false, faces);
	}
	public static CSG fillet(CSG base, double rad, boolean outer, int numFaces) throws ColinearPointsException {
		List<Polygon> polys = Slice.slice(base);
		CSG fillet = fillet(polys, rad, outer, numFaces);
		if (outer)
			fillet = fillet.difference(base);
		else
			fillet = fillet.intersect(base);
		return fillet;
	}

	public static CSG fillet(List<Polygon> polys, double rad, boolean outer, double numArcPoints) {

		ArrayList<CSG> parts = new ArrayList<>();

		for (Polygon p : polys) {
			boolean isHole = false;
			try {
				isHole = !Extrude.isCCW(p);
			} catch (ColinearPointsException e) {
				e.printStackTrace();
			}
			// if (isHole)
			// continue;
			System.err.println("Polygon filler " + (isHole ? "hole" : "outside"));

			int size = p.getVertices().size();
			for (int i = 0; i < size; i++) {
				int next = (i + 1) % size;
				int nextNext = (next + 1) % size;

				Vector3d position0 = p.getVertices().get(i).pos;
				Vector3d position1 = p.getVertices().get(next).pos; // corner vertex
				Vector3d position2 = p.getVertices().get(nextNext).pos;

				Vector3d seg1 = position0.minus(position1); // incoming, pointing away from corner
				Vector3d seg2 = position2.minus(position1); // outgoing, pointing away from corner

				double len = seg1.magnitude();
				double theta = Math.toDegrees(seg1.angle(seg2)); // always 0–180
				double crossZ = seg1.cross(seg2).z;

				// Convex vs reflex meaning depends on winding:
				// Outside (CCW): convex corners have crossZ < 0
				// Hole (CW): convex corners have crossZ > 0
				boolean isConvex = isHole ? (crossZ > 0) : (crossZ < 0);
				boolean isReflex = !isConvex;

				// filletAngle = exterior sweep angle of the corner piece
				double filletAngle = 180.0 - theta;

				// Base rotation: align to seg1 direction relative to Y axis
				double baseAngle = Math.toDegrees(seg1.angle(Vector3d.Y_ONE));
				if (seg1.x < 0)
					baseAngle = 360 - baseAngle;
				double edgeAngleAbs = baseAngle;
				if (isHole) {
					edgeAngleAbs += 180;
					baseAngle -= 90;
					if ((!isReflex) && outer) {
						filletAngle -= 90;
						if (filletAngle < 90)
							filletAngle = 0;
					}
					if (isReflex && outer) {

						baseAngle -= filletAngle;
					}
					if (isReflex && (!outer)) {
						filletAngle -= 90;
						if (filletAngle < 90)
							filletAngle = 0;
						baseAngle += 90;
					}
					if ((!isReflex) && (!outer)) {

						baseAngle += 180;
					}

				} else {
					if ((!isReflex) && outer) {
						// default case
					}
					if (isReflex && outer) {
						filletAngle = 180 - filletAngle;
						if (filletAngle < 90)
							filletAngle = 0;
						baseAngle += 90;
					}
					if (isReflex && (!outer)) {// checked

						baseAngle += 90;
					}
					if ((!isReflex) && (!outer)) {
						filletAngle = theta - 90;
						if (filletAngle < 90)
							filletAngle = 0;
						baseAngle += 90;
					}
				}
				// -------------------------------------------------------
				// Edge fillet: always placed, runs along seg1 from position1
				// Holes face inward so flip 180°
				// -------------------------------------------------------

				CSG edgeFillet = new Fillet(rad, len).setNumArcPoints(numArcPoints).toCSG();
				if (isHole)
					edgeFillet = edgeFillet.toYMax();
				else
					edgeFillet = edgeFillet.toYMin().mirrorx();
				if (!outer)
					edgeFillet = edgeFillet.mirrorx();
				edgeFillet = edgeFillet.rotz(edgeAngleAbs).move(position1);
				parts.add(edgeFillet);
				double cornerAngleAbs = baseAngle;
				if (!isHole && outer) {
					cornerAngleAbs = cornerAngleAbs - filletAngle - 90;
				}
				// -------------------------------------------------------
				// Corner fillet: only placed on REFLEX corners
				//
				// Outside reflex: edge fillets don't reach into the notch,
				// corner piece fills it, no extra rotation.
				//
				// Hole reflex: edge fillets each consume 90° at the corner,
				// so the corner piece must start 90° further
				// around to align with where they leave off.
				// -------------------------------------------------------
				if (filletAngle > 0.01 && filletAngle < 359.99) {

					// Outside: no extra offset — logic is reversed (edge fillets
					// run away from the corner, so no 90° consumption occurs)

					try {
						parts.add(corner(rad, filletAngle, (double) numArcPoints).rotz(cornerAngleAbs).move(position1));
					} catch (ColinearPointsException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return CSG.unionAll(parts);
	}

	@Override
	public CSG toCSG() {
		// --- 1. Build the fillet cross-section polygon in the XZ plane (Y = 0) ---
		//
		// Profile vertices (viewed from +Y):
		// • inner corner at origin (0, 0, 0)
		// • straight edge along +X to (w, 0, 0)
		// • concave quarter-circle arc from (w, 0, 0) → (0, 0, w)
		// arc center = (w, 0, w), radius = w
		// angles sweep 270° → 180°
		// • straight edge along -Z back to origin ← polygon auto-closes

		List<Vector3d> profilePoints = new ArrayList<>();

		// Inner corner
		profilePoints.add(new Vector3d(w, 0, 0));
		profilePoints.add(new Vector3d(0, 0, 0));

		// Concave quarter-circle arc
		for (int i = 1; i < getNumArcPoints(); i++) {
			double angle = Math.toRadians(270.0 - 90.0 *( (double)i) / getNumArcPoints());
			double x = w + w * Math.cos(angle);
			double z = w + w * Math.sin(angle);
			if(x<filletOfset)
				x=filletOfset;
			if(z<filletOfset)
				z=filletOfset;
			profilePoints.add(0, new Vector3d(x, 0, z));
		}
		profilePoints.add(new Vector3d(0, 0, w));
		// Last arc point lands exactly at (0, 0, w); polygon closes to (0,0,0)
		try {
			Polygon profile = Polygon.fromPoints(profilePoints);

			// --- 2. Extrude the profile along +Y for the fillet's length ---
			Vector3d extrudeDir = new Vector3d(0, h, 0);
			return Extrude.extrude(extrudeDir, profile).toZMin();
		} catch (ColinearPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new Cube(20).toCSG().setColor(Color.PINK);
		}
	}

	public double getNumArcPoints() {
		return numArcPoints;
	}

	public Fillet setNumArcPoints(double numArcPoints) {
		this.numArcPoints = numArcPoints;
		return this;
	}
}

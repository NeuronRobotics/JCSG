package eu.mihosoft.vrl.v3d;

import java.util.ArrayList;
import java.util.List;

import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;
import javafx.scene.paint.Color;

public class Fillet extends Primitive {

	double w, h;
	private static final double numArcPoints = 12;
	/** The properties. */
	private final PropertyStorage properties = new PropertyStorage();

	public PropertyStorage getProperties() {
		return properties;
	}

	/**
	 * Constructor. Creates a new cuboid with center {@code [0,0,0]} and with the
	 * specified dimensions.
	 *
	 * @param w width
	 * @param h height
	 */
	public Fillet(double w, double h) {
		this.w = w;
		this.h = h;
	}

	public static CSG corner(double rad, double angle) throws ColinearPointsException {
	    // --- Build the concave quarter-circle fillet profile ---
	    //
	    //  Shape (in XY plane, swept around Z):
	    //
	    //  (0,rad) ──arc─── (rad,rad)
	    //     │      center      │
	    //     │   at (rad,rad)   │
	    //  (0,0) ────────── (rad,0)
	    //
	    //  The arc is concave (bites inward), matching what the
	    //  old Fillet+Sphere difference was carving out.

	   
	    List<Vector3d> pts = new ArrayList<>();

	    // Corner at origin
	    pts.add(new Vector3d(0, 0, 0));

	    // Straight edge along +X
	    pts.add(new Vector3d(rad, 0, 0));

	    // Concave quarter-circle arc: center=(rad,rad), radius=rad
	    // sweeps from 270° → 180° (i.e. (rad,0) → (0,rad))
	    for (int i = 0; i <= numArcPoints; i++) {
	        double a = Math.toRadians(270.0 - 90.0 * i / numArcPoints);
	        double x = rad + rad * Math.cos(a);
	        double y = rad + rad * Math.sin(a);
	        pts.add(new Vector3d(x, y, 0));
	    }

	    // Straight edge back down to origin closes the polygon
	    // (fromPoints auto-closes, so no need to re-add (0,0,0))

	    Polygon profile = Polygon.fromPoints(pts);

	    // --- Sweep the profile around the Z axis ---
	    // radius=0  → profile is already positioned relative to the axis
	    // z=0       → no axial offset
	    // steps=32  → match arc resolution for a smooth result
	    return Extrude.sweep(profile, angle/numArcPoints, 0, 0, (int)numArcPoints).roty(90);
	}

	public static CSG outerFillet(CSG base, double rad) throws ColinearPointsException {
		List<Polygon> polys = Slice.slice(base);
		return outerFillet(polys, rad);
	}

	public static CSG outerFillet(List<Polygon> polys, double rad) {

	    ArrayList<CSG> parts = new ArrayList<>();

	    for (Polygon p : polys) {
	        boolean isHole = false;
	        try {
	            isHole = !Extrude.isCCW(p);
	        } catch (ColinearPointsException e) {
	            e.printStackTrace();
	        }
	        System.err.println("Polygon filler " + (isHole ? "hole" : "outside"));

	        int size = p.getVertices().size();
	        for (int i = 0; i < size; i++) {
	            int next     = (i + 1) % size;
	            int nextNext = (next + 1) % size;

	            Vector3d position0 = p.getVertices().get(i).pos;
	            Vector3d position1 = p.getVertices().get(next).pos;  // corner vertex
	            Vector3d position2 = p.getVertices().get(nextNext).pos;

	            Vector3d seg1 = position0.minus(position1); // incoming edge
	            Vector3d seg2 = position2.minus(position1); // outgoing edge

	            double len   = seg1.magnitude();
	            double theta = Math.toDegrees(seg1.angle(seg2));
	            double crossZ = seg1.cross(seg2).z;

	            boolean isConvex = isHole ? (crossZ > 0) : (crossZ < 0);
	            boolean isReflex = !isConvex;

	            double filletAngle = 180.0 - theta;

	            // --- Absolute rotation for the corner (perpendicular to seg1) ---
	            double cornerAngleAbs = Math.toDegrees(seg1.angle(Vector3d.Y_ONE));
	            if (seg1.x < 0)
	                cornerAngleAbs = 360 - cornerAngleAbs;
	            if (isHole)   cornerAngleAbs += 180;
	            if (isReflex) cornerAngleAbs += 180;

	            // --- Absolute rotation for the edge fillet (parallel to seg1) ---
	            double edgeAngleAbs = Math.toDegrees(seg1.angle(Vector3d.Y_ONE));
	            if (seg1.x < 0)
	                edgeAngleAbs = 360 - edgeAngleAbs;
	            if (isHole) {
	                edgeAngleAbs += 180;
	                
	            }
	            System.err.println(
	                "  vertex=" + next +
	                " theta="        + String.format("%.2f", theta) +
	                " filletAngle="  + String.format("%.2f", filletAngle) +
	                " cornerAbs="    + String.format("%.2f", cornerAngleAbs) +
	                " edgeAbs="      + String.format("%.2f", edgeAngleAbs) +
	                " convex="       + isConvex +
	                " reflex="       + isReflex
	            );

	            // --- Straight edge fillet ---
	            // Runs from position1 toward position0 (along seg1 direction)
	            // toYMax() aligns the fillet profile to sit flush on the face
	            CSG edgeFillet = new Fillet(rad, len).toCSG().toYMax()
	                    .rotz(edgeAngleAbs)
	                    .move(position1);
	            //parts.add(edgeFillet);

	            // --- Corner fillet ---
	            if (filletAngle > 0.01 && filletAngle < 359.99) {
	                try {
	                    parts.add(corner(rad, filletAngle)
	                            .rotz(cornerAngleAbs)
	                            .move(position1));
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
		profilePoints.add(new Vector3d(0, 0, 0));

		// Concave quarter-circle arc
		for (int i = 0; i <= numArcPoints; i++) {
			double angle = Math.toRadians(270.0 - 90.0 * i / numArcPoints);
			double x = w + w * Math.cos(angle);
			double z = w + w * Math.sin(angle);
			profilePoints.add(0,new Vector3d(x, 0, z));
		}
		// Last arc point lands exactly at (0, 0, w); polygon closes to (0,0,0)
		try {
			Polygon profile = Polygon.fromPoints(profilePoints);

			// --- 2. Extrude the profile along +Y for the fillet's length ---
			Vector3d extrudeDir = new Vector3d(0, h, 0);
			return Extrude.extrude(extrudeDir, profile);
		} catch (ColinearPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new Cube(20).toCSG().setColor(Color.PINK);
		}
	}
}

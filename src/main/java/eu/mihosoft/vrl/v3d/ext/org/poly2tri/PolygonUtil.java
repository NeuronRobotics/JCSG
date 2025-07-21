/*
 * PolygonUtil.java
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
package eu.mihosoft.vrl.v3d.ext.org.poly2tri;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.Debug3dProvider;
import eu.mihosoft.vrl.v3d.Edge;
import eu.mihosoft.vrl.v3d.Extrude;
import eu.mihosoft.vrl.v3d.IPolygonRepairTool;
import eu.mihosoft.vrl.v3d.Plane;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Vertex;
import javafx.collections.ModifiableObservableListBase;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.vecmath.Matrix4d;
import javax.vecmath.Quat4d;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.triangulate.polygon.ConstrainedDelaunayTriangulator;

//import earcut4j.Earcut;

/**
 * The Class PolygonUtil.
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public class PolygonUtil {
	public static final double triangleScale = 10000;
	private static IPolygonRepairTool repair = concave1 -> {

		ArrayList<Edge> edges = new ArrayList<Edge>();
		ArrayList<Vertex> toRemove = new ArrayList<Vertex>();
		List<Vertex> v1 = concave1.getVertices();
		ArrayList<Vertex> modifiable = new ArrayList<Vertex>();

		for (int i = 0; i < v1.size(); i++) {
			boolean match = false;
			for (int j = 0; j < modifiable.size(); j++) {
				if (v1.get(i).pos.test(modifiable.get(j).pos, 1E-3)) {
					match = true;
				}
			}
			if (!match)
				modifiable.add(v1.get(i));
		}
		for (int i = 0; i < modifiable.size(); i++) {
			Edge e = new Edge(modifiable.get(i), modifiable.get((i + 1) % modifiable.size()));
			double l = e.length();
			if (l < 0.001)
				System.out.println("Length of edge is " + l);
			// System.out.println(e);
			edges.add(e);
		}
		ArrayList<Thread> threads = new ArrayList<Thread>();
		int threadCount = 64;
		if (threadCount >= edges.size()) {
			threadCount = 1;
		}
		int perThread = edges.size() / threadCount;
		for (int k = 0; k < threadCount; k++) {
			int start = k * perThread;

			Thread t = new Thread(() -> {
				for (int i = start; i < edges.size() && i <= (start + perThread); i++) {
					Edge test = edges.get(i);
					if (i < edges.size() - 1) {
						boolean c = test.colinear(edges.get(i + 1));
						if (c) {
							System.out.println("Colinear edged found " + i);
						}
					}
					if (start == 0) {
						CSG.getProgressMoniter().progressUpdate((i * (edges.size() / perThread)), edges.size(),
								" Repairing intersecting polygon (Hint in Inkscape->Path->Simplify): found # "
										+ toRemove.size(),
								null);
//							System.out.println(start+" Testing " +  + " of " +edges.size()+
//									" found bad points# "+toRemove.size());
					}
					for (int j = 0; j < edges.size(); j++) {
						if (i == j)
							continue;
						Edge test2 = edges.get(j);
						boolean b = Edge.falseBoundaryEdgeSharedWithOtherEdge(test, test2);
						Optional<Vector3d> cross = test.getCrossingPoint(test2);
						boolean c = cross.isPresent() && i < j;
						if (c) {
							int x;
							for (x = i + 1; x < j; x++) {
								toRemove.add(modifiable.get(x));
							}
//								System.out
//										.println("Edges cross! " + cross.get() +
//												" pruned "+(x-i));
						}
						if (b) {
							System.out.println("\n\nFalse Boundary " + test + " \n " + test2);
							try {
								Vertex vBad = test.getCommonPoint(test2);
								toRemove.add(vBad);
							} catch (Exception e) {
								// throw new RuntimeException(e);
								return;
							}
						}
					}

				}
			});
			threads.add(t);
			t.start();
		}
		for (int i = 0; i < threads.size(); i++) {
			Thread t = threads.get(i);
			try {
				t.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("Reapir thread finished "+i+" "+threads.size());
		}
		for (Vertex vr : toRemove)
			modifiable.remove(vr);
		ArrayList<Polygon> back = new ArrayList<>();
		if (modifiable.size() > 2) {
			try {
				back.add(new Polygon(modifiable, concave1.getStorage(), false, concave1.getPlane()));
			} catch (ColinearPointsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			if (toRemove.size() > 3) {
				try {
					back.add(new Polygon(toRemove, concave1.getStorage(), false, concave1.getPlane()));
				} catch (ColinearPointsException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				System.out.println("Pruned " + toRemove.size());
			}
		}

		return back;

	};

	/**
	 * Checks if two polygons overlap in 3D space.
	 * 
	 * @param polygon1 First polygon to check
	 * @param polygon2 Second polygon to check
	 * @return true if the polygons overlap, false otherwise
	 */
	public static boolean doPolygonsOverlap(Polygon polygon1, Polygon polygon2) {
		// Check if both polygons are in the same plane
		if (!areCoplanar(polygon1, polygon2)) {
			// If they're not coplanar, check for intersection between edges and faces
			return checkEdgeFaceIntersections(polygon1, polygon2);
		} else {
			// If they're coplanar, check for 2D polygon intersection
			return checkCoplanarIntersection(polygon1, polygon2);
		}
	}

	/**
	 * Checks if two polygons are coplanar (lie in the same plane).
	 */
	private static boolean areCoplanar(Polygon polygon1, Polygon polygon2) {
		// Get the normals of both polygons using the built-in plane.normal
		Vector3d normal1 = polygon1.getPlane().getNormal();
		Vector3d normal2 = polygon2.getPlane().getNormal();

		// Check if normals are parallel (same or opposite direction)
		double dotProduct = normal1.dot(normal2);
		boolean normalsParallel = Math.abs(Math.abs(dotProduct) - 1.0) < Plane.getEPSILON();

		if (!normalsParallel) {
			return false;
		}

		// If normals are parallel, check if polygons are in the same plane
		// by checking if a point from polygon2 lies on the plane of polygon1
		Vector3d point1 = polygon1.getVertices().get(0).pos;
		Vector3d point2 = polygon2.getVertices().get(0).pos;

		// Calculate distance from point2 to the plane of polygon1
		double distance = normal1.dot(point2.minus(point1));

		return Math.abs(distance) < Plane.getEPSILON();
	}

	/**
	 * Checks if two coplanar polygons intersect.
	 */
	private static boolean checkCoplanarIntersection(Polygon polygon1, Polygon polygon2) {
		// Get the plane normal and determine primary projection plane
		Vector3d normal = polygon1.getPlane().getNormal();
		int dominantAxis = getDominantAxis(normal);

		// Project the vertices to the appropriate 2D plane
		List<Vector3d> projected1 = projectPolygonVertices(polygon1, dominantAxis);
		List<Vector3d> projected2 = projectPolygonVertices(polygon2, dominantAxis);

		// Check if any edge of polygon1 intersects with any edge of polygon2
		for (int i = 0; i < projected1.size(); i++) {
			Vector3d p1 = projected1.get(i);
			Vector3d p2 = projected1.get((i + 1) % projected1.size());

			for (int j = 0; j < projected2.size(); j++) {
				Vector3d q1 = projected2.get(j);
				Vector3d q2 = projected2.get((j + 1) % projected2.size());

				if (doLineSegmentsIntersect(p1, p2, q1, q2, dominantAxis)) {
					return true;
				}
			}
		}

		// Check if one polygon is inside the other
		return isPointInPolygon(projected1.get(0), projected2, dominantAxis)
				|| isPointInPolygon(projected2.get(0), projected1, dominantAxis);
	}

	/**
	 * Projects polygon vertices while preserving their 3D positions, but zeroing
	 * out the dominant axis coordinate for 2D intersection testing.
	 */
	private static List<Vector3d> projectPolygonVertices(Polygon polygon, int dominantAxis) {
		List<Vector3d> projectedVertices = new ArrayList<>();

		for (Vertex vertex : polygon.getVertices()) {
			Vector3d pos = vertex.pos;
			projectedVertices.add(pos);
		}

		return projectedVertices;
	}

	/**
	 * Gets the dominant axis of a vector (the axis with the largest absolute
	 * value).
	 */
	private static int getDominantAxis(Vector3d vector) {
		double absX = Math.abs(vector.x);
		double absY = Math.abs(vector.y);
		double absZ = Math.abs(vector.z);

		if (absX >= absY && absX >= absZ) {
			return 0; // X is dominant
		} else if (absY >= absX && absY >= absZ) {
			return 1; // Y is dominant
		} else {
			return 2; // Z is dominant
		}
	}

	/**
	 * Returns the two non-dominant coordinates for a given dominant axis.
	 */
	private static double[] getNonDominantCoordinates(Vector3d v, int dominantAxis) {
		switch (dominantAxis) {
		case 0: // X dominant, return Y,Z
			return new double[] { v.y, v.z };
		case 1: // Y dominant, return X,Z
			return new double[] { v.x, v.z };
		case 2: // Z dominant, return X,Y
		default:
			return new double[] { v.x, v.y };
		}
	}

	/**
	 * Checks if two line segments intersect in the projected 2D plane.
	 */
	private static boolean doLineSegmentsIntersect(Vector3d p1, Vector3d p2, Vector3d q1, Vector3d q2,
			int dominantAxis) {
		// Extract the 2D coordinates based on dominant axis
		double[] p1Coords = getNonDominantCoordinates(p1, dominantAxis);
		double[] p2Coords = getNonDominantCoordinates(p2, dominantAxis);
		double[] q1Coords = getNonDominantCoordinates(q1, dominantAxis);
		double[] q2Coords = getNonDominantCoordinates(q2, dominantAxis);

		// Compute orientations needed for the general case of the algorithm
		int o1 = orientation(p1Coords, p2Coords, q1Coords);
		int o2 = orientation(p1Coords, p2Coords, q2Coords);
		int o3 = orientation(q1Coords, q2Coords, p1Coords);
		int o4 = orientation(q1Coords, q2Coords, p2Coords);

		// General case
		if (o1 != o2 && o3 != o4) {
			return true;
		}

		// Special cases for collinear segments
		if (o1 == 0 && onSegment(p1Coords, q1Coords, p2Coords))
			return true;
		if (o2 == 0 && onSegment(p1Coords, q2Coords, p2Coords))
			return true;
		if (o3 == 0 && onSegment(q1Coords, p1Coords, q2Coords))
			return true;
		if (o4 == 0 && onSegment(q1Coords, p2Coords, q2Coords))
			return true;

		return false;
	}

	/**
	 * Helper function for line segment intersection algorithm. Determines the
	 * orientation of triplet (p, q, r). Returns 0 if collinear, 1 if clockwise, 2
	 * if counterclockwise.
	 */
	private static int orientation(double[] p, double[] q, double[] r) {
		double val = (q[1] - p[1]) * (r[0] - q[0]) - (q[0] - p[0]) * (r[1] - q[1]);

		if (Math.abs(val) < Plane.getEPSILON())
			return 0; // collinear
		return (val > 0) ? 1 : 2; // clockwise or counterclockwise
	}

	/**
	 * Checks if point q lies on line segment pr.
	 */
	private static boolean onSegment(double[] p, double[] q, double[] r) {
		return q[0] <= Math.max(p[0], r[0]) && q[0] >= Math.min(p[0], r[0]) && q[1] <= Math.max(p[1], r[1])
				&& q[1] >= Math.min(p[1], r[1]);
	}

	/**
	 * Checks if a point is inside a polygon using the ray casting algorithm.
	 */
	private static boolean isPointInPolygon(Vector3d point, List<Vector3d> polygon, int dominantAxis) {
		double[] pointCoords = getNonDominantCoordinates(point, dominantAxis);
		boolean inside = false;
		int n = polygon.size();

		for (int i = 0, j = n - 1; i < n; j = i++) {
			double[] iCoords = getNonDominantCoordinates(polygon.get(i), dominantAxis);
			double[] jCoords = getNonDominantCoordinates(polygon.get(j), dominantAxis);

			if (((iCoords[1] > pointCoords[1]) != (jCoords[1] > pointCoords[1]))
					&& (pointCoords[0] < (jCoords[0] - iCoords[0]) * (pointCoords[1] - iCoords[1])
							/ (jCoords[1] - iCoords[1]) + iCoords[0])) {
				inside = !inside;
			}
		}

		return inside;
	}

	/**
	 * Checks for intersections between edges of one polygon and the face of
	 * another.
	 */
	private static boolean checkEdgeFaceIntersections(Polygon polygon1, Polygon polygon2) {
		// Check if any edge of polygon1 intersects the face of polygon2
		List<Vertex> vertices1 = polygon1.getVertices();

		for (int i = 0; i < vertices1.size(); i++) {
			Vector3d v1 = vertices1.get(i).pos;
			Vector3d v2 = vertices1.get((i + 1) % vertices1.size()).pos;

			if (doesLineIntersectPolygon(v1, v2, polygon2)) {
				return true;
			}
		}

		// Check if any edge of polygon2 intersects the face of polygon1
		List<Vertex> vertices2 = polygon2.getVertices();

		for (int i = 0; i < vertices2.size(); i++) {
			Vector3d v1 = vertices2.get(i).pos;
			Vector3d v2 = vertices2.get((i + 1) % vertices2.size()).pos;

			if (doesLineIntersectPolygon(v1, v2, polygon1)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Checks if a line segment intersects with a polygon.
	 */
	private static boolean doesLineIntersectPolygon(Vector3d lineStart, Vector3d lineEnd, Polygon polygon) {
		// Get polygon normal from the plane
		Vector3d normal = polygon.getPlane().getNormal();
		Vector3d polygonPoint = polygon.getVertices().get(0).pos;

		// Calculate the parameters for the line-plane intersection
		Vector3d lineDirection = lineEnd.minus(lineStart);
		double denominatorTerm = normal.dot(lineDirection);

		// If the line is parallel to the plane, no intersection
		if (Math.abs(denominatorTerm) < Plane.getEPSILON()) {
			return false;
		}

		// Calculate the t value at the intersection point
		double t = normal.dot(polygonPoint.minus(lineStart)) / denominatorTerm;

		// Check if the intersection point is on the line segment
		if (t < 0 || t > 1) {
			return false;
		}

		// Calculate the intersection point
		Vector3d intersectionPoint = lineStart.plus(lineDirection.times(t));

		// Check if the intersection point is inside the polygon
		int dominantAxis = getDominantAxis(normal);
		List<Vector3d> polygonVertices = new ArrayList<>();
		for (Vertex vertex : polygon.getVertices()) {
			polygonVertices.add(vertex.pos);
		}

		return isPointInPolygon(intersectionPoint, polygonVertices, dominantAxis);
	}

	/**
	 * Instantiates a new polygon util.
	 */
	private PolygonUtil() {
		throw new AssertionError("Don't instantiate me!", null);
	}

//	/**
//	 * Converts a CSG polygon to a poly2tri polygon (including holes).
//	 *
//	 * @param polygon the polygon to convert
//	 * @return a CSG polygon to a poly2tri polygon (including holes)
//	 */
//	public static eu.mihosoft.vrl.v3d.ext.org.poly2tri.Polygon fromCSGPolygon(eu.mihosoft.vrl.v3d.Polygon polygon) {
//
//		// convert polygon
//		List<PolygonPoint> points = new ArrayList<>();
//		for (Vertex v : polygon.vertices) {
//			PolygonPoint vp = new PolygonPoint(v.pos.x, v.pos.y, v.pos.z);
//			points.add(vp);
//		}
//
//		eu.mihosoft.vrl.v3d.ext.org.poly2tri.Polygon result = new eu.mihosoft.vrl.v3d.ext.org.poly2tri.Polygon(points);
//
//		// convert holes
//		Optional<List<eu.mihosoft.vrl.v3d.Polygon>> holesOfPresult = polygon.getStorage()
//				.getValue(eu.mihosoft.vrl.v3d.Edge.KEY_POLYGON_HOLES);
//		if (holesOfPresult.isPresent()) {
//			List<eu.mihosoft.vrl.v3d.Polygon> holesOfP = holesOfPresult.get();
//
//			holesOfP.stream().forEach((hP) -> {
//				result.addHole(fromCSGPolygon(hP));
//			});
//		}
//
//		return result;
//	}
	/**
	 * Concave to convex.
	 *
	 * @param incoming the concave
	 * @return the list
	 * @throws ColinearPointsException 
	 */
	public static ArrayList<Polygon> triangulatePolygon(Polygon incoming) throws ColinearPointsException {
		return triangulatePolygon(incoming, true);
	}

	/**
	 * Calculates a quaternion-based transform that rotates `from` vector to align
	 * with (0,0,1).
	 * @throws ColinearPointsException 
	 */
	private static Transform calculateQuaternionTransform(Polygon concave) throws ColinearPointsException {
		// Normalize inputs
		Vector3d u = concave.getPlane().getNormal();
		Vector3d v = new Vector3d(0, 0, 1);

		double dot = u.dot(v);
		// If u ≈ v → identity
		if (dot > 1.0 - Plane.getEPSILON()) {
			return new Transform();
		}
		if (dot < -1.0 + Plane.getEPSILON()) {
			return new Transform().rotX(180);
		}
		double aboutZ = Math.toDegrees(Math.atan2(u.y, u.x));

		Transform transform1 = new Transform().rotZ(aboutZ);
		Vector3d u2 = u.transformed(transform1);
		double aboutY = Math.toDegrees(Math.atan2(u2.x, u2.z));
		Transform transform = new Transform().rotY(aboutY).apply(transform1);
		Vector3d u3 = u.transformed(transform).normalized();

		Polygon test = concave.transformed(transform);
		if (1 - Math.abs(test.plane.getNormal().z) > Plane.getEPSILON()) {
			System.out.println("Error with " + test);
			Plane p = Plane.createFromPoints(test.getVertices());
			throw new RuntimeException("Failed to reorent the polygon for processing!");
		}
		return transform;
	}

	/**
	 * Concave to convex.
	 *
	 * @param incoming the concave
	 * @return the list
	 * @throws ColinearPointsException 
	 */
	public static ArrayList<Polygon> triangulatePolygon(Polygon incoming, boolean toCCW) throws ColinearPointsException {
		ArrayList<Polygon> result = new ArrayList<>();

		if (incoming == null)
			return result;
		if (incoming.getVertices().size() < 3)
			return result;
		Polygon tmp = incoming;
		Vector3d normalOfPlane = incoming.getPlane().getNormal().clone();
		normalOfPlane.normalize();
		boolean reorient = Math.abs(normalOfPlane.z - 1.0) > Plane.getEPSILON();
		Transform orientationInv = null;
		boolean debug = false;
		Vector3d normal = tmp.getPlane().getNormal().clone();

		if (reorient) {
			Transform orientation = calculateQuaternionTransform(incoming);
			tmp = incoming.transformed(orientation);
			orientationInv = orientation.inverse();
		}

		boolean cw = !Extrude.isCCW(tmp);
		Polygon concave = (cw && toCCW) ? Extrude.toCCW(tmp) : tmp;
		double zplane = concave.getVertices().get(0).pos.z;
		for (Vector3d v : concave.getPoints()) {
			if (Math.abs(zplane - v.z) > Plane.getEPSILON()) {
				throw new RuntimeException("Failed to triangulate, points must be coplainer");
			}
		}
		try {
			makeTriangles(concave, cw, result, zplane, normal, debug, orientationInv, reorient, incoming.getColor());
		} catch (java.lang.IllegalStateException ex) {

			ArrayList<Polygon> repairedList = repairOverlappingEdges(concave);
			for (Polygon repaired : repairedList) {
				double z = repaired.getVertices().get(0).pos.z;
				for (Vector3d v : repaired.getPoints()) {
					if (Math.abs(z - v.z) > Plane.getEPSILON()) {
						throw new RuntimeException("Failed to triangulate, points must be coplainer");
					}
				}
				int end = repaired.getVertices().size();
				if (end == 3) {
					result.add(repaired);
				} else if (end == 4) {
					fourPointSpecialCase(repaired, cw, result, zplane, normal, debug, orientationInv, reorient,
							incoming.getColor());
				} else
					try {
						makeTriangles(repaired, cw, result, zplane, normal, debug, orientationInv, reorient,
								incoming.getColor());
					} catch (Exception e) {
						makeTrianglesInternal(repaired, cw, result, zplane, normal, debug, orientationInv, reorient,
								incoming.getColor());
					}

				// System.out.println("Rapaired polygon: "+repaired);
				// System.out.println("Repaired the polygon! pruned " + (start - end));
			}
		}

		return result;
	}

	private static ArrayList<Polygon> repairOverlappingEdges(Polygon concave) {

		return getRepair().repairOverlappingEdges(concave);
	}

	private static void fourPointSpecialCase(Polygon concave, boolean cw, List<Polygon> result, double zplane,
			Vector3d normal, boolean debug, Transform orentationInv, boolean reorent, Color color) throws ColinearPointsException {
		List<Vector3d> points = concave.getPoints();
		int size = points.size();
		for (int i = 0; i < size; i++) {
			// Get first two points to establish a direction vector
			Vector3d p1 = points.get(i);
			Vector3d p2 = points.get((i + 1) % size);

			// Calculate the direction vector between first two points
			Vector3d direction = p1.minus(p2);
			// Normalize the direction vector
			double length = direction.length();
			double ep = Plane.getEPSILON();
			if (length < ep) { // If points are effectively identical
				continue;
			}
			direction.normalize();
			Vector3d p3 = points.get((i + 2) % size);

			// Calculate cross product
			Vector3d cross = direction.cross(p1.minus(p3));
			// Calculate magnitude of cross product
			double magnitude = Math.abs(cross.length());

			// If magnitude is not close to zero, points are not collinear
			if (magnitude > ep) {

				Plane normal2 = concave.plane;
				Polygon one = new Polygon(
						new ArrayList<Vertex>(Arrays.asList(new Vertex(p1), new Vertex(p2), new Vertex(p3))),
						concave.getStorage(), true, normal2);
				Polygon two = new Polygon(
						new ArrayList<Vertex>(Arrays.asList(new Vertex(points.get((i + 3) % size)),
								new Vertex(points.get((i + 4) % size)), new Vertex(points.get((i + 5) % size)))),
						concave.getStorage(), true, normal2);
				if (reorent) {
					one = one.transform(orentationInv);
				}
				one.setColor(color);
				result.add(one);
				if (reorent) {
					two = two.transform(orentationInv);
				}
				two.setColor(color);
				result.add(two);
				return;
			}
		}

	}

	private static void makeTrianglesInternal(Polygon concave, boolean cw, List<Polygon> result, double zplane,
			Vector3d normal, boolean debug, Transform orentationInv, boolean reorent, Color color) throws ColinearPointsException {
		ArrayList<Vector3d> points = new ArrayList<>(concave.getPoints());
		double z = concave.getVertices().get(0).pos.z;
		for (Vector3d v : points) {
			if (Math.abs(z - v.z) > Plane.getEPSILON()) {
				throw new RuntimeException("Failed to triangulate, points must be coplainer");
			}
		}
		while (points.size() > 0) {
			int size = points.size();
			for (int i = 0; i < size; i++) {
				// Get first two points to establish a direction vector
				Vector3d p1 = points.get(i);
				Vector3d p2 = points.get((i + 1) % size);
				if (Math.abs(z - p1.z) > Plane.getEPSILON()) {
					throw new RuntimeException("Failed to triangulate, points must be coplainer");
				}
				if (Math.abs(z - p2.z) > Plane.getEPSILON()) {
					throw new RuntimeException("Failed to triangulate, points must be coplainer");
				}
				// Calculate the direction vector between first two points
				Vector3d direction = p1.minus(p2);
				// Normalize the direction vector
				double length = direction.length();
				double ep = Plane.getEPSILON();
				if (length < ep) { // If points are effectively identical
					continue;
				}
				direction.normalize();
				Vector3d p3 = points.get((i + 2) % size);
				if (Math.abs(z - p3.z) > Plane.getEPSILON()) {
					throw new RuntimeException("Failed to triangulate, points must be coplainer");
				}

				// Calculate cross product
				Vector3d cross = direction.cross(p1.minus(p3));
				// Calculate magnitude of cross product
				double magnitude = Math.abs(cross.length());

				// If magnitude is not close to zero, points are not collinear
				if (magnitude > ep) {

					Plane normal2 = concave.plane;
					ArrayList<Vertex> vertices = new ArrayList<Vertex>(
							Arrays.asList(new Vertex(p1.clone()), new Vertex(p2.clone()), new Vertex(p3.clone())));
					Polygon one = new Polygon(vertices, concave.getStorage(), true, normal2.clone());
					points.remove(p2);

					if (reorent) {
						one = one.transform(orentationInv);
					}
					one.setColor(color);
					result.add(one);
					if (points.size() == 2) {
						points.clear();
						return;
					}
					break;
				} else {
					if (points.size() == 3)
						return;// skip a colinear final segment
				}
			}
			if (size == points.size()) {
				if (result.size() == 0)
					throw new RuntimeException("Error! All remaining points are colinear!");
				return;
			}
		}

	}

	private static void makeTriangles(Polygon concave, boolean cw, List<Polygon> result, double zplane, Vector3d normal,
			boolean debug, Transform orentationInv, boolean reorent, Color color) {

		Polygon toTri = concave;
//	if(cw) {
//		toTri=Extrude.toCCW(concave);
//	}
		Coordinate[] coordinates = new Coordinate[toTri.getVertices().size() + 1];
		for (int i = 0; i < toTri.getVertices().size(); i++) {
			Vector3d v = toTri.getVertices().get(i).pos;
			coordinates[i] = new Coordinate(v.x * triangleScale, v.y * triangleScale, v.z * triangleScale);
		}
		Vector3d v = toTri.getVertices().get(0).pos;
		coordinates[toTri.getVertices().size()] = new Coordinate(v.x * triangleScale, v.y * triangleScale,
				v.z * triangleScale);
		// use the default factory, which gives full double-precision
		Geometry geom = new GeometryFactory().createPolygon(coordinates);
		Geometry triangles = ConstrainedDelaunayTriangulator.triangulate(geom);
		ArrayList<Vertex> triPoints = new ArrayList<>();
		Plane p1 = concave.getPlane().clone();
		for (int i = 0; i < triangles.getNumGeometries(); i++) {
			Geometry tri = triangles.getGeometryN(i);
			Coordinate[] coords = tri.getCoordinates();
			int counter = 0;
			if (coords.length != 4)
				throw new RuntimeException("Failed to triangulate");
			for (int j = 0; j < 3; j++) {
				Coordinate tp = coords[j];
				Vector3d pos = new Vector3d(tp.getX() / triangleScale, tp.getY() / triangleScale, zplane);
				triPoints.add(new Vertex(pos));

				if (counter == 2) {
					if (Extrude.isCCW(triPoints) == cw) {
						Collections.reverse(triPoints);
					}
					Polygon poly;
					try {
						poly = new Polygon(triPoints, concave.getStorage(), true, p1);
						// poly = Extrude.toCCW(poly);
						poly.getPlane().setNormal(concave.getPlane().getNormal());

						if (debug) {
							// Debug3dProvider.clearScreen();
							// Debug3dProvider.addObject(concave);
							Debug3dProvider.addObject(poly);
						}

						if (reorent) {
							poly = poly.transform(orentationInv);

							// poly = checkForValidPolyOrentation(normal, poly);
						}
						// poly.plane.setNormal(normalOfPlane);
						poly.setColor(color);
						result.add(poly);
					} catch (ColinearPointsException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					counter = 0;
					triPoints = new ArrayList<>();
				} else {
					counter++;
				}
			}
		}
	}

	public static IPolygonRepairTool getRepair() {

		return repair;
	}

	public static void setRepair(IPolygonRepairTool repair) {
		PolygonUtil.repair = repair;
	}

}

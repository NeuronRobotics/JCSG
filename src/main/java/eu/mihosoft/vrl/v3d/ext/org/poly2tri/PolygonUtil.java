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

import eu.mihosoft.vrl.v3d.Debug3dProvider;
import eu.mihosoft.vrl.v3d.Extrude;
import eu.mihosoft.vrl.v3d.Plane;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Vertex;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.triangulate.polygon.ConstrainedDelaunayTriangulator;
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator;

/**
 * The Class PolygonUtil.
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public class PolygonUtil {
	private static final double triangleScale=1000.0;
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
	 */
	public static List<Polygon> concaveToConvex(Polygon incoming) {
		List<Polygon> result = new ArrayList<>();

		if (incoming == null)
			return result;
		if (incoming.getVertices().size() < 3)
			return result;
		Polygon concave = incoming;
		Vector3d normalOfPlane = incoming.plane.getNormal();
		boolean reorent = normalOfPlane.z < 1.0 - Plane.getEPSILON();
		Transform orentationInv = null;
		boolean debug = false;
		Vector3d normal = concave.plane.getNormal().clone();

		if (reorent) {
			// System.err.println("\n\nIncoming polygon " + incoming);
//			reorent=true;
//			double degreesToRotate = Math.toDegrees(Math.atan2(normalOfPlane.x, normalOfPlane.z));
//			Transform orentation = new Transform().roty(degreesToRotate);
//			Transform orentation_inv = new Transform().roty(-degreesToRotate);
//
//			Polygon tmp = incoming.transformed(orentation);
//			//System.err.println("StInvage 1 polygon "+tmp);
//			Vector3d tmpNorm = tmp.plane.getNormal();
//			double degreesToRotate2 =  Math.toDegrees(Math.atan2( tmpNorm.y,tmpNorm.z));
//			Transform rotx = new Transform().rotx(degreesToRotate2);
//			Transform rotx_inv= new Transform().rotx(-degreesToRotate2);
//
//			Transform orentation2 =rotx.apply(orentation );// th triangulation function needs
//			// the polygon on the xy plane
//			if (debug) {
//				Debug3dProvider.clearScreen();
//				Debug3dProvider.addObject(incoming);
//			}
//			concave = incoming.transformed(orentation2);
//			System.err.println("NormalAdjusted polygon "+concave);
//			if (concave.plane.getNormal().z < 0) {
//				Transform rotx2 = new Transform().rotx(180);
//				Transform rotx2_inv = new Transform().rotx(180);
//
//				orentation2 = rotx2.apply(orentation2);
//				concave = incoming.transformed(orentation2);
//				System.err.println("Flipping Reorenting polygon "+concave);
//
//			}
//			orentationInv  =orentation2.invert();
			double degreesToRotate = Math.toDegrees(Math.atan2(normalOfPlane.x, normalOfPlane.z));
			Transform orentation = new Transform().roty(degreesToRotate);

			Polygon tmp = incoming.transformed(orentation);

			Vector3d tmpnorm = tmp.plane.getNormal();
			double degreesToRotate2 = 90 + Math.toDegrees(Math.atan2(tmpnorm.z, tmpnorm.y));
			Transform orentation2 = orentation.rotx(degreesToRotate2);// th triangulation function needs
			// the polygon on the xy plane
			if (debug) {
				Debug3dProvider.clearScreen();
				Debug3dProvider.addObject(incoming);
			}
			concave = incoming.transformed(orentation2);
			orentationInv = orentation2.inverse();
			if (concave.plane.getNormal().z < 0) {
				Transform orentation3 = orentation2.rotx(180);
				concave = incoming.transformed(orentation3);
				orentationInv = orentation3.inverse();
			}
			// System.err.println("Re-orenting polygon " + concave);
			Polygon transformed = concave.transformed(orentationInv);
			// System.err.println("corrected-orenting polygon " + transformed);
			checkForValidPolyOrentation(normal, transformed);

		}

		boolean cw = !Extrude.isCCW(concave);
		if (cw)
			concave = Extrude.toCCW(concave);
		if (debug) {
			Debug3dProvider.clearScreen();
			Debug3dProvider.addObject(concave);
			// Debug3dProvider.clearScreen();
		}
		Geometry triangles;
		double zplane = concave.getVertices().get(0).pos.z;

		try {
			triangles = makeTriangles(concave, cw);
		} catch (java.lang.IllegalStateException ex) {
			List<Vector3d> points = concave.getPoints();
			List<Vector3d> r = new ArrayList<>(points);
			Collections.reverse(r);
			concave = Polygon.fromPoints(r);
			triangles = makeTriangles(concave, cw);
		}

		ArrayList<Vertex> triPoints = new ArrayList<>();

		for (int i = 0; i < triangles.getNumGeometries(); i++) {
			Geometry tri = triangles.getGeometryN(i);
			Coordinate[] coords = tri.getCoordinates();
			int counter = 0;
			if (coords.length != 4)
				throw new RuntimeException("Failed to triangulate");
			for (int j = 0; j < 3; j++) {
				Coordinate tp = coords[j];
				Vector3d pos = new Vector3d(tp.getX()/triangleScale, tp.getY()/triangleScale, zplane);
				triPoints.add(new Vertex(pos, normal));

				if (counter == 2) {
					if (!cw) {
						Collections.reverse(triPoints);
					}
					Polygon poly = new Polygon(triPoints, concave.getStorage(), true,concave.plane);
					// poly = Extrude.toCCW(poly);
					poly.plane.setNormal(concave.plane.getNormal());
					boolean b = !Extrude.isCCW(poly);
					if (cw != b) {
						// System.err.println("Triangle not matching incoming");
						Collections.reverse(triPoints);
						poly = new Polygon(triPoints, concave.getStorage(), true,concave.plane);
						b = !Extrude.isCCW(poly);
						if (cw != b) {
							// com.neuronrobotics.sdk.common.Log.error("Error, polygon is reversed!");
						}
					}
					if (debug) {
						// Debug3dProvider.clearScreen();
						// Debug3dProvider.addObject(concave);
						Debug3dProvider.addObject(poly);
					}

					if (reorent) {
						poly = poly.transform(orentationInv);

						poly = checkForValidPolyOrentation(normal, poly);
					}
					// poly.plane.setNormal(normalOfPlane);
					poly.setColor(incoming.getColor());
					result.add(poly);
					counter = 0;
					triPoints = new ArrayList<>();
				} else {
					counter++;
				}
			}
		}

		return result;
	}

	private static Polygon checkForValidPolyOrentation(Vector3d normal, Polygon poly) {
		Vector3d normal2 = poly.plane.getNormal();
		Vector3d minus = normal.minus(normal2);
		double length = minus.length();
		double d = 1.0e-3;
		if (length > d * 10 && length < (2 - d)) {
			List<Vector3d> points = poly.getPoints();
			List<Vector3d> r = new ArrayList<>(points);
			Collections.reverse(r);
			Polygon fromPoints = Polygon.fromPoints(r);
			double l = normal.minus( fromPoints.plane.getNormal()).length();
			if (l > d * 10 && l < (2 - d)) {
//				throw new RuntimeException(
//				"Error, the reorentation of the polygon resulted in a different normal than the triangles produced from it");
			}else {
				poly=fromPoints;

			}
		}
		return poly;
	}

	private static Geometry makeTriangles(Polygon concave, boolean cw) {
		Geometry triangles;
		Polygon toTri = concave;
//	if(cw) {
//		toTri=Extrude.toCCW(concave);
//	}
		Coordinate[] coordinates = new Coordinate[toTri.getVertices().size() + 1];
		for (int i = 0; i < toTri.getVertices().size(); i++) {
			Vector3d v = toTri.getVertices().get(i).pos;
			coordinates[i] = new Coordinate(v.x*triangleScale, v.y*triangleScale, v.z*triangleScale);
		}
		Vector3d v = toTri.getVertices().get(0).pos;
		coordinates[toTri.getVertices().size()] = new Coordinate(v.x*triangleScale, v.y*triangleScale, v.z*triangleScale);
		// use the default factory, which gives full double-precision
		Geometry geom = new GeometryFactory().createPolygon(coordinates);
		triangles = ConstrainedDelaunayTriangulator.triangulate(geom);
		return triangles;
	}

//	/**
//	 * Concave to convex.
//	 *
//	 * @param concave the concave
//	 * @return the list
//	 */
//	public static List<eu.mihosoft.vrl.v3d.Polygon> concaveToConvex(eu.mihosoft.vrl.v3d.Polygon incoming) {
//		//incoming = pruneDuplicatePoints(incoming);
//		if (incoming == null)
//			return new ArrayList<>();
//		if (incoming.vertices.size() < 3)
//			return new ArrayList<>();
//		eu.mihosoft.vrl.v3d.Polygon concave;
//		Vector3d normalOfPlane = incoming.plane.normal;
//		boolean reorent = Math.abs(normalOfPlane.z) < 1.0-Plane.EPSILON;
//		Transform orentationInv = null;
//		boolean debug = false;
//		Vector3d normal2;
//		if (reorent) {
//			//debug = true;
//			double degreesToRotate = Math.toDegrees(Math.atan2(normalOfPlane.x,normalOfPlane.z));
//			Transform orentation = new Transform().roty(degreesToRotate);
//
//			eu.mihosoft.vrl.v3d.Polygon tmp = incoming.transformed(orentation);
//			
//			Vector3d normal = tmp.plane.normal;
//			double degreesToRotate2 =90+Math.toDegrees(Math.atan2(normal.z,normal.y));
//			Transform orentation2 = orentation.rotx(degreesToRotate2);// th triangulation function needs
//			// the polygon on the xy plane
//			orentationInv = orentation2.inverse();
//
//			if (debug) {
//				Debug3dProvider.clearScreen();
//				Debug3dProvider.addObject(incoming);
//			}
//			concave = incoming.transformed(orentation2);
//			normal2 = concave.plane.normal;
//			////com.neuronrobotics.sdk.common.Log.error("New vectors "+normal2+" "+normal);
//		} else
//			concave = incoming;
//		if(Math.abs(concave.plane.normal.z) < 1.0-Plane.EPSILON) {
//			throw new RuntimeException("Orentaion of plane misaligned for triangulation");
//		}
//
//		List<eu.mihosoft.vrl.v3d.Polygon> result = new ArrayList<>();
//
//		Vector3d normal = concave.vertices.get(0).normal.clone();
//
//		boolean cw = !Extrude.isCCW(concave);
//		concave = Extrude.toCCW(concave);
//		if (reorent) {
//			// Debug3dProvider.addObject(concave);
//		}
//
//		eu.mihosoft.vrl.v3d.ext.org.poly2tri.Polygon p = fromCSGPolygon(concave);
//
//		eu.mihosoft.vrl.v3d.ext.org.poly2tri.Poly2Tri.triangulate(p);
//
//		List<DelaunayTriangle> triangles = p.getTriangles();
//
//		List<Vertex> triPoints = new ArrayList<>();
//
//		for (DelaunayTriangle t : triangles) {
//
//			int counter = 0;
//			for (TriangulationPoint tp : t.points) {
//
//				triPoints.add(new Vertex(new Vector3d(tp.getX(), tp.getY(), tp.getZ()), normal));
//
//				if (counter == 2) {
//					if (!cw) {
//						Collections.reverse(triPoints);
//					}
//					eu.mihosoft.vrl.v3d.Polygon poly = new eu.mihosoft.vrl.v3d.Polygon(triPoints, concave.getStorage(),true);
//
//					poly.plane.normal = concave.plane.normal;
//					// Debug3dProvider.addObject(poly);
//					if (reorent) {
//						poly = poly.transform(orentationInv);
//						if (reorent) {
//							// Debug3dProvider.addObject(poly);
//						}
//					}
//					Vector3d clone = normalOfPlane.clone();
//
//					// //com.neuronrobotics.sdk.common.Log.error("Updating the normal to " + clone);
//					poly.plane.normal = clone;
//					// Debug3dProvider.addObject(poly);
//					result.add(poly);
//					counter = 0;
//					triPoints = new ArrayList<>();
//
//				} else {
//					counter++;
//				}
//			}
//		}
//
//		return result;
//	}

	public static Polygon pruneDuplicatePoints(Polygon incoming) {
		ArrayList<Vertex> newPoints = new ArrayList<Vertex>();
		for (int i = 0; i < incoming.getVertices().size(); i++) {
			Vertex v = incoming.getVertices().get(i);
			boolean duplicate = false;
			for (Vertex vx : newPoints) {
				if (vx.pos.test(v.pos, Plane.EPSILON_duplicate)) {
					duplicate = true;
				}
			}
			if (!duplicate) {
				newPoints.add(v);
			}

		}
		if (newPoints.size() < 3)
			return null;

		return new Polygon(newPoints);

	}
}

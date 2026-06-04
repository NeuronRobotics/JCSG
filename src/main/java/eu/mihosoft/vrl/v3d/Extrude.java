/**
 * Extrude.java
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

import java.util.ArrayList;

import com.piro.bezier.BezierPath;
import eu.mihosoft.vrl.v3d.svg.*;
import javafx.scene.paint.Color;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eu.mihosoft.vrl.v3d.CSG.OptType;

//import javax.vecmath.Vector3d;

import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;
import eu.mihosoft.vrl.v3d.ext.quickhull3d.HullUtil;

//  Auto-generated Javadoc
/**
 * Extrudes concave and convex polygons.
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public class Extrude {
	private static double MINIMUM_DISTANCE = 0.005;
	private static IExtrusion extrusionEngine = new IExtrusion() {
		/**
		 * Extrudes the specified path (convex or concave polygon without holes or
		 * intersections, specified in CCW) into the specified direction.
		 *
		 * @param dir
		 *            direction
		 * @param points
		 *            path (convex or concave polygon without holes or intersections)
		 *
		 * @return a CSG object that consists of the extruded polygon
		 * @throws ColinearPointsException
		 */
		public CSG points(Vector3d dir, List<Vector3d> points) throws ColinearPointsException {

			List<Vector3d> newList = new ArrayList<>(points);
			Polygon fromPoints = Polygon.fromPoints(toCCW(newList));
			return extrude(dir, fromPoints);
		}

		/**
		 * Extrude.
		 *
		 * @param dir
		 *            the dir
		 * @param polygon1
		 *            the polygon1
		 * @return the csg
		 */
		public CSG extrude(Vector3d dir, Polygon polygon1) {

			try {
				return monotoneExtrude(dir, polygon1);
			} catch (ColinearPointsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return new Cube(10).toCSG().setColor(Color.PINK);
		}

		private CSG monotoneExtrude(Vector3d dir, Polygon polygon1) throws ColinearPointsException {
			ArrayList<Polygon> newPolygons = new ArrayList<>();
			CSG extrude;
			ArrayList<Polygon> triangulatePolygon = PolygonUtil.triangulatePolygon(polygon1);
			for (Polygon p : triangulatePolygon) {
				newPolygons.add(p.flipped());
				newPolygons.add(p.transformed(new Transform().move(dir)));
			}
			Polygon polygon2 = polygon1.transformed(new Transform().move(dir));
			List<Polygon> parts = Extrude.monotoneExtrude(polygon2, polygon1);
			newPolygons.addAll(parts);

			// ArrayList<Polygon> topPolygons = PolygonUtil.triangulatePolygon(polygon2);

			extrude = new CSG(newPolygons);
			if (CSG.getDefaultOptionType() == OptType.Manifold3d) {
				try {
					extrude = CSG.getManifold().calculateAreaAndSurfaceArea(extrude);
				} catch (Throwable e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return extrude;
		}

		@Override
		public CSG extrude(Vector3d dir, List<Vector3d> points) {
			try {
				return points(dir, points);
			} catch (ColinearPointsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return new Cube(10).toCSG().setColor(Color.PINK);
		}
	};

	/**
	 * Instantiates a new extrude.
	 */
	private Extrude() {
		throw new AssertionError("Don't instantiate me!", null);
	}

	public static CSG points(Vector3d dir, List<Vector3d> points) throws ColinearPointsException {

		return getExtrusionEngine().extrude(dir, points);
	}

	/**
	 * Extrudes the specified path (convex or concave polygon without holes or
	 * intersections, specified in CCW) into the specified direction.
	 *
	 * @param dir
	 *            direction
	 * @param points
	 *            path (convex or concave polygon without holes or intersections)
	 *
	 * @return a CSG object that consists of the extruded polygon
	 * @throws ColinearPointsException
	 */
	public static CSG points(Vector3d dir, Vector3d... points) throws ColinearPointsException {

		return points(dir, Arrays.asList(points));
	}

	/**
	 * To ccw.
	 *
	 * @param points
	 *            the points
	 * @return the list
	 * @throws ColinearPointsException
	 */
	public static List<Vector3d> toCCW(List<Vector3d> points) throws ColinearPointsException {

		List<Vector3d> result = new ArrayList<>(points);

		if (!isCCWv3d(result)) {
			Collections.reverse(result);
		}

		return result;
	}

	/**
	 * To cw.
	 *
	 * @param points
	 *            the points
	 * @return the list
	 * @throws ColinearPointsException
	 */
	static List<Vector3d> toCW(List<Vector3d> points) throws ColinearPointsException {

		List<Vector3d> result = new ArrayList<>(points);

		if (isCCWv3d(result)) {
			Collections.reverse(result);
		}

		return result;
	}

	/**
	 * Checks if is ccw.
	 *
	 * @param polygon
	 *            the polygon
	 * @return true, if is ccw
	 * @throws ColinearPointsException
	 */
	public static boolean isCCW(Polygon polygon) throws ColinearPointsException {
		return isCCWv3d(polygon.getPoints());
	}
	/**
	 * Checks if is ccw.
	 *
	 * @param polygon
	 *            the polygon
	 * @return true, if is ccw
	 * @throws ColinearPointsException
	 */
	public static boolean isCCW(List<Vertex> vertices) throws ColinearPointsException {
		return isCCW(vertices, new Vector3d(0, 0, 1));
	}
	/**
	 * Checks if is ccw.
	 *
	 * @param polygon
	 *            the polygon
	 * @param normal
	 *            the normal to check the CCW against.
	 * @return true, if is ccw
	 * @throws ColinearPointsException
	 */
	public static boolean isCCW(List<Vertex> vertices, Vector3d normal) throws ColinearPointsException {
		Plane p = Plane.createFromPoints(vertices);

		double dot = normal.dot(p.getNormal());
		return dot > (1.0 - Plane.getEPSILON());
	}

	/**
	 * Checks if is ccw.
	 *
	 * @param polygon
	 *            the polygon
	 * @return true, if is ccw
	 */
	public static boolean isCCWv3d(List<Vector3d> vertices) throws ColinearPointsException {
		ArrayList<Vertex> v = new ArrayList<Vertex>();
		for (Vector3d vc : vertices)
			v.add(new Vertex(vc));
		return isCCW(v);

		// // thanks to Sepp Reiter for explaining me the algorithm!
		// if (vertices.size() < 3) {
		// throw new IllegalArgumentException("Only polygons with at least 3 vertices
		// are supported!");
		// }
		//
		// // search highest left vertex
		// int highestLeftVertexIndex = 0;
		// Vector3d highestLeftVertex = vertices.get(0);
		// double zSet = highestLeftVertex.z;
		// for (int i = 0; i < vertices.size(); i++) {
		//
		// Vector3d v = vertices.get(i);
		// double abs = Math.abs(zSet - v.z);
		// if (abs > Plane.getEPSILON()) {
		// throw new RuntimeException("isCCW can only be performed on the X Y plane
		// "+abs);
		// }
		// if (v.y > highestLeftVertex.y) {
		// highestLeftVertex = v;
		// highestLeftVertexIndex = i;
		// } else if (v.y == highestLeftVertex.y && v.x < highestLeftVertex.x) {
		// highestLeftVertex = v;
		// highestLeftVertexIndex = i;
		// }
		// }
		//
		// // determine next and previous vertex indices
		// int nextVertexIndex = (highestLeftVertexIndex + 1) % vertices.size();
		// int prevVertexIndex = highestLeftVertexIndex - 1;
		// if (prevVertexIndex < 0) {
		// prevVertexIndex = vertices.size() - 1;
		// }
		// Vector3d nextVertex = vertices.get(nextVertexIndex);
		// Vector3d prevVertex = vertices.get(prevVertexIndex);
		//
		// // edge 1
		// double a1 = normalizedX(highestLeftVertex, nextVertex);
		//
		// // edge 2
		// double a2 = normalizedX(highestLeftVertex, prevVertex);
		//
		// // select vertex with lowest x value
		// int selectedVIndex;
		//
		// if (a2 > a1) {
		// selectedVIndex = nextVertexIndex;
		// } else {
		// selectedVIndex = prevVertexIndex;
		// }
		//
		// if (selectedVIndex == 0 && highestLeftVertexIndex == vertices.size() - 1) {
		// selectedVIndex = vertices.size();
		// }
		//
		// if (highestLeftVertexIndex == 0 && selectedVIndex == vertices.size() - 1) {
		// highestLeftVertexIndex = vertices.size();
		// }
		//
		// // indicates whether edge points from highestLeftVertexIndex towards
		// // the sel index (ccw)
		// return selectedVIndex > highestLeftVertexIndex;

	}

	/**
	 * Normalized x.
	 *
	 * @param v1
	 *            the v1
	 * @param v2
	 *            the v2
	 * @return the double
	 */
	private static double normalizedX(Vector3d v1, Vector3d v2) {
		Vector3d v2MinusV1 = v2.minus(v1);

		return v2MinusV1.dividedBy(v2MinusV1.magnitude()).times(Vector3d.X_ONE).x;
	}

	public static IExtrusion getExtrusionEngine() {
		return extrusionEngine;
	}

	public static void setExtrusionEngine(IExtrusion extrusionEngine) {
		Extrude.extrusionEngine = extrusionEngine;
	}

	public static CSG byPath(List<List<Vector3d>> points, double height) throws ColinearPointsException {

		return byPath(points, height, 200);

	}

	public static CSG byPath(List<List<Vector3d>> points, double height, int resolution)
			throws ColinearPointsException {
		ArrayList<Transform> trPath = pathToTransforms(points, resolution);
		ArrayList<Vector3d> finalPath = new ArrayList<>();
		for (Transform tr : trPath) {
			javax.vecmath.Vector3d t1 = new javax.vecmath.Vector3d();
			tr.getInternalMatrix().get(t1);
			Vector3d finalPoint = new Vector3d(t1.x, t1.y, 0);
			finalPath.add(finalPoint);
		}
		// showEdges(finalPath,(double)0.0,javafx.scene.paint.Color.RED)
		// println "Path size = " +finalPath.size()
		// List<Polygon> p = Polygon.fromConcavePoints(finalPath)
		// for(Polygon pl:p)
		// BowlerStudioController.getBowlerStudio()addObject(pl,null)
		// return new Cube(height).toCSG()
		return Extrude.points(new Vector3d(0, 0, height), finalPath);
	}

	public static ArrayList<Transform> pathToTransforms(List<List<Vector3d>> points, int resolution) {

		Vector3d start = points.get(0).get(0);
		String pathStringA = "M " + start.x + "," + start.y;
		String pathStringB = pathStringA;

		for (List<Vector3d> sections : points) {
			if (sections.size() == 4) {
				Vector3d controlA = sections.get(1);
				Vector3d controlB = sections.get(2);
				Vector3d endPoint = sections.get(3);
				/*
				 * ArrayList<Double> controlA = (ArrayList<Double>)
				 * Arrays.asList(sections.get(1).x - start.get(0), sections.get(1).y -
				 * start.get(1), sections.get(1).z - start.get(2));
				 *
				 * ArrayList<Double> controlB = (ArrayList<Double>)
				 * Arrays.asList(sections.get(2).x - start.get(0), sections.get(2).y -
				 * start.get(1), sections.get(2).z - start.get(2)); ; ArrayList<Double> endPoint
				 * = (ArrayList<Double>) Arrays.asList(sections.get(3).x - start.get(0),
				 * sections.get(3).y - start.get(1), sections.get(3).z - start.get(2)); ;
				 */

				pathStringA += ("C " + controlA.x + "," + controlA.y + " " + controlB.x + "," + controlB.y + " "
						+ endPoint.x + "," + endPoint.y + "\n");
				pathStringB += ("C " + controlA.x + "," + controlA.z + " " + controlB.x + "," + controlB.z + " "
						+ endPoint.x + "," + endPoint.z + "\n");
				// start.set(0, sections.get(3).x);
				// start.set(1, sections.get(3).y);
				// start.set(2,sections.get(3).z);

			} else if (sections.size() == 1) {

				pathStringA += "L " + (double) sections.get(0).x + "," + (double) sections.get(0).y + "\n";
				pathStringB += "L " + (double) sections.get(0).x + "," + (double) sections.get(0).z + "\n";
				// start.set(0, sections.get(0).x);
				// start.set(1, sections.get(0).y);
				// start.set(2, sections.get(0).z);
			}
		}
		// println "A string = " +pathStringA
		// println "B String = " +pathStringB
		BezierPath path = new BezierPath();
		path.parsePathString(pathStringA);
		BezierPath path2 = new BezierPath();
		path2.parsePathString(pathStringB);

		return bezierToTransforms(path, path2, resolution, null, null);
	}

	public static ArrayList<CSG> moveAlongProfile(CSG object, List<List<Vector3d>> points, int resolution) {

		return Extrude.move(object, pathToTransforms(points, resolution));
	}

	public static ArrayList<Transform> bezierToTransforms(Vector3d controlA, Vector3d controlB, Vector3d endPoint,
			int iterations) {
		BezierPath path = new BezierPath();
		path.parsePathString("C " + controlA.x + "," + controlA.y + " " + controlB.x + "," + controlB.y + " "
				+ endPoint.x + "," + endPoint.y);
		BezierPath path2 = new BezierPath();
		path2.parsePathString("C " + controlA.x + "," + controlA.z + " " + controlB.x + "," + controlB.z + " "
				+ endPoint.x + "," + endPoint.z);

		return bezierToTransforms(path, path2, iterations, controlA, controlB);
	}

	public static ArrayList<Transform> bezierToTransforms(List<Vector3d> parts, int iterations) {
		// //com.neuronrobotics.sdk.common.Log.error("Bezier type "+parts.size());
		if (parts.size() == 3)
			return bezierToTransforms(parts.get(0), parts.get(1), parts.get(2), iterations);
		if (parts.size() == 2)
			return bezierToTransforms(parts.get(0), parts.get(0), parts.get(1), parts.get(1), iterations);
		if (parts.size() == 1)
			return bezierToTransforms(new Vector3d(0, 0, 0), new Vector3d(0, 0, 0), parts.get(0), parts.get(0),
					iterations);
		return bezierToTransforms(parts.get(0), parts.get(1), parts.get(2), parts.get(3), iterations);
	}

	private Transform toTransform() {

		return null;
	}

	public static ArrayList<Transform> bezierToTransforms(BezierPath pathA, BezierPath pathB, int iterations,
			Vector3d controlA, Vector3d controlB) {
		double d = 1.0 / (double) iterations;
		ArrayList<Transform> p = new ArrayList<Transform>();
		Vector3d pointAStart = pathA.eval(0);
		Vector3d pointBStart = pathB.eval(0);
		double x = pointAStart.x, y = pointAStart.y, z = pointBStart.y;
		double lastx = x, lasty = y, lastz = z;
		// double min = (double ) 0.0001;
		int startIndex = 0;
		if (controlA != null) {
			startIndex = 1;
			double ydiff = controlA.y - y;
			double zdiff = controlA.z - z;
			double xdiff = controlA.x - x;
			double rise = zdiff;
			double run = Math.sqrt((ydiff * ydiff) + (xdiff * xdiff));
			double rotz = 90 - Math.toDegrees(Math.atan2(xdiff, ydiff));
			// //com.neuronrobotics.sdk.common.Log.error("Rot z = "+rotz+" x="+xdiff+"
			// y="+ydiff);
			double roty = Math.toDegrees(Math.atan2(rise, run));
			Transform t = new Transform();
			t.translateX(x);
			t.translateY(y);
			t.translateZ(z);
			t.rotZ(-rotz);
			t.rotY(roty);
			p.add(t);
		}
		Transform t;
		double ydiff;
		double zdiff;
		double xdiff;
		double rise;
		double run;
		double rotz;
		double roty;
		for (int i = startIndex; i < iterations - 1; i++) {
			double pathFunction = (double) (((double) i) / ((double) (iterations - 1)));

			Vector3d pointA = pathA.eval(pathFunction);
			Vector3d pointB = pathB.eval(pathFunction);

			x = pointA.x;
			y = pointA.y;
			z = pointB.y;

			t = new Transform();
			t.translateX(x);
			t.translateY(y);
			t.translateZ(z);

			Vector3d pointAEst = pathA.eval((double) (pathFunction + d));
			Vector3d pointBEst = pathB.eval((double) (pathFunction + d));
			double xest = pointAEst.x;
			double yest = pointAEst.y;
			double zest = pointBEst.y;
			ydiff = yest - y;
			zdiff = zest - z;
			xdiff = xest - x;
			// t.rotX(45-Math.toDegrees(Math.atan2(zdiff,ydiff)))

			rise = zdiff;
			run = Math.sqrt((ydiff * ydiff) + (xdiff * xdiff));
			rotz = 90 - Math.toDegrees(Math.atan2(xdiff, ydiff));
			// //com.neuronrobotics.sdk.common.Log.error("Rot z = "+rotz+" x="+xdiff+"
			// y="+ydiff);
			roty = Math.toDegrees(Math.atan2(rise, run));

			t.rotZ(-rotz);
			t.rotY(roty);
			// if(i==0)
			// //com.neuronrobotics.sdk.common.Log.error( " Tr = "+x+" "+y+" "+z+" path =
			// "+pathFunction);
			// println "z = "+rotz+" y = "+roty
			p.add(t);
			lastx = x;
			lasty = y;
			lastz = z;
		}

		Vector3d pointA = pathA.eval((double) 1);
		Vector3d pointB = pathB.eval((double) 1);

		x = pointA.x;
		y = pointA.y;
		z = pointB.y;
		t = new Transform();
		t.translateX(x);
		t.translateY(y);
		t.translateZ(z);
		if (controlB != null) {
			lastx = controlB.x;
			lasty = controlB.y;
			lastz = controlB.z;
		}

		ydiff = y - lasty;
		zdiff = z - lastz;
		xdiff = x - lastx;

		rise = zdiff;
		run = Math.sqrt((ydiff * ydiff) + (xdiff * xdiff));
		rotz = 90 - Math.toDegrees(Math.atan2(xdiff, ydiff));
		roty = Math.toDegrees(Math.atan2(rise, run));

		t.rotZ(-rotz);
		t.rotY(roty);
		p.add(t);

		return p;
	}

	public static ArrayList<Transform> bezierToTransforms(Vector3d start, Vector3d controlA, Vector3d controlB,
			Vector3d endPoint, int iterations) {
		String startString = "M " + start.x + "," + start.y + "\n" + "C " + controlA.x + "," + controlA.y + " "
				+ controlB.x + "," + controlB.y + " " + endPoint.x + "," + endPoint.y;
		String b = "M " + start.x + "," + start.z + "\n" + "C " + controlA.x + "," + controlA.z + " " + controlB.x + ","
				+ controlB.z + " " + endPoint.x + "," + endPoint.z;
		// println "Start = "+startString
		BezierPath path = new BezierPath();
		path.parsePathString(startString);
		BezierPath path2 = new BezierPath();
		path2.parsePathString(b);
		// newParts.remove(parts.size()-1)
		// newParts.remove(0)
		// //com.neuronrobotics.sdk.common.Log.error("Parsing "+startString+"
		// \nand\n"+b);
		return bezierToTransforms(path, path2, iterations, controlA, controlB);
	}

	public static CSG sweep(Polygon p, Transform increment, Transform offset, int steps)
			throws ColinearPointsException {
		return sweep(p, increment, offset, steps, (u, d) -> {
			return new Transform();
		});
	}

	public static CSG sweep(Polygon p, Transform increment, Transform offset, int steps, ITransformProvider provider)
			throws ColinearPointsException {
		Polygon offsetP = p.transformed(offset);
		ArrayList<Polygon> newPolygons = new ArrayList<>();
		newPolygons.addAll(PolygonUtil.triangulatePolygon(offsetP));
		Transform running = new Transform();
		Polygon prev = offsetP.transformed(provider.get(0, steps));
		for (int i = 0; i < steps; i++) {
			running.apply(increment);
			double unit = ((double) i) / ((double) steps);
			Polygon step = offsetP.transformed(provider.get(unit, steps)).transformed(running);
			List<Polygon> parts = monotoneExtrude(prev, step);
			prev = step;
			newPolygons.addAll(parts);
		}
		Polygon polygon2 = prev.clone();
		List<Polygon> topPolygons = PolygonUtil.triangulatePolygon(polygon2.flipped());
		newPolygons.addAll(topPolygons);

		CSG csg = new CSG(newPolygons);
		if (CSG.getDefaultOptionType() == OptType.Manifold3d) {
			try {
				csg = CSG.getManifold().calculateAreaAndSurfaceArea(csg);
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return csg;
	}

	public static CSG sweep(Polygon p, double angle, double z, double radius, int steps)
			throws ColinearPointsException {
		return sweep(p, new Transform().rotX(angle).movex(z), new Transform().movey(radius), steps);
	}

	public static List<Polygon> monotoneExtrude(Polygon polygon2, Polygon polygon1) {
		List<Polygon> newPolygons = new ArrayList<>();

		int numvertices = polygon1.getVertices().size();

		for (int i = 0; i < numvertices; i++) {

			int nexti = (i + 1) % numvertices;

			Vector3d bottomV1 = polygon1.getVertices().get(i).pos;
			Vector3d topV1 = polygon2.getVertices().get(i).pos;
			Vector3d bottomV2 = polygon1.getVertices().get(nexti).pos;
			Vector3d topV2 = polygon2.getVertices().get(nexti).pos;
			double distance = bottomV1.minus(bottomV2).magnitude();
			double z1Dist = topV1.minus(bottomV1).magnitude();
			if (Math.abs(distance) > Plane.getEPSILON() && Math.abs(z1Dist) > Plane.getEPSILON()) {
				List<Vector3d> asList = Arrays.asList(bottomV2.clone(), topV1.clone(), bottomV1.clone());
				try {
					newPolygons.add(Polygon.fromPoints(asList, polygon1.getStorage()));
				} catch (ColinearPointsException ex) {
					System.out.println(ex.getMessage() + " Pruning from extrude");
				}
			}
			double distance2 = topV2.minus(topV1).magnitude();
			double z1Dist2 = topV2.minus(bottomV2).magnitude();
			if (Math.abs(distance2) > Plane.getEPSILON() && Math.abs(z1Dist2) > Plane.getEPSILON()) {
				List<Vector3d> asList2 = Arrays.asList(bottomV2.clone(), topV2.clone(), topV1.clone());
				try {
					newPolygons.add(Polygon.fromPoints(asList2, polygon1.getStorage()));
				} catch (ColinearPointsException ex) {
					System.out.println(ex.getMessage() + " Pruning from extrude");
				}
			}
		}
		return newPolygons;
	}

	public static ArrayList<CSG> revolve(CSG slice, double radius, int numSlices) {
		return revolve(slice, radius, 360.0, null, numSlices);
	}

	public static ArrayList<CSG> revolve(Polygon poly, int numSlices) throws ColinearPointsException {
		return revolve(poly, 0, numSlices);
	}

	public static ArrayList<CSG> revolve(Polygon poly, double radius, int numSlices) throws ColinearPointsException {
		Polygon p = poly;
		double angle = 360;
		double z = 0;
		int steps = numSlices;
		CSG result = sweep(p, angle, z, radius, steps);
		return new ArrayList<CSG>(Arrays.asList(result));
	}

	public static ArrayList<CSG> revolve(CSG slice, double radius, double archLen, int numSlices) {
		return revolve(slice, radius, archLen, null, numSlices);
	}

	public static ArrayList<CSG> revolve(CSG slice, double radius, double archLen, List<List<Vector3d>> points,
			int numSlices) {
		ArrayList<CSG> parts = new ArrayList<CSG>();
		double slices = (double) numSlices;
		double increment = archLen / slices;
		CSG slicePRofile = slice.movey(radius);

		for (double i = 0; i < (archLen + increment); i += increment) {
			parts.add(slicePRofile.rotz(i));
		}
		if (points != null) {
			ArrayList<Transform> pathtransforms = pathToTransforms(points, numSlices);
			for (int i = 0; i < parts.size(); i++) {
				CSG sweep = parts.get(i).transformed(pathtransforms.get(i));
				parts.set(i, sweep);
			}
		}
		for (int i = 0; i < parts.size() - 1; i++) {

			CSG sweep = CSG.hullAll(parts.get(i), parts.get(i + 1));
			parts.set(i, sweep);
		}

		return parts;
	}

	public static ArrayList<CSG> bezier(CSG slice, ArrayList<Double> controlA, ArrayList<Double> controlB,
			ArrayList<Double> endPoint, int numSlices) {
		ArrayList<CSG> parts = new ArrayList<CSG>();

		for (int i = 0; i < numSlices; i++) {
			parts.add(0, slice.clone());
		}
		return bezier(parts, controlA, controlB, endPoint);
	}

	public static ArrayList<CSG> bezier(ArrayList<CSG> s, ArrayList<Double> controlA, ArrayList<Double> controlB,
			ArrayList<Double> endPoint) {
		ArrayList<CSG> slice = moveBezier(s, controlA, controlB, endPoint);

		for (int i = 0; i < slice.size() - 1; i++) {
			// Polygon p1 =Slice.slice(slice.get(i), new Transform(), 0).get(0);
			// Polygon p2 =Slice.slice(slice.get(i+1), new Transform(), 0).get(0);
			// CSG sweep = polygons(p1, p2);
			CSG sweep = HullUtil.hull(slice.get(i), slice.get(i + 1));

			slice.set(i, sweep);
		}
		return slice;
	}

	public static ArrayList<CSG> hull(ArrayList<CSG> s, ArrayList<Transform> p) {
		ArrayList<CSG> slice = move(s, p);
		for (int i = 0; i < slice.size() - 1; i++) {
			// Polygon p1 =Slice.slice(slice.get(i), new Transform(), 0).get(0);
			// Polygon p2 =Slice.slice(slice.get(i+1), new Transform(), 0).get(0);
			// CSG sweep = polygons(p1, p2);
			CSG sweep = HullUtil.hull(slice.get(i), slice.get(i + 1));

			slice.set(i, sweep);
		}
		return slice;
	}

	public static ArrayList<CSG> hull(CSG c, ArrayList<Transform> p) {
		ArrayList<CSG> s = new ArrayList<>();
		for (int i = 0; i < p.size(); i++) {
			s.add(c.clone());
		}
		return hull(s, p);
	}

	public static ArrayList<CSG> linear(ArrayList<CSG> s, ArrayList<Double> endPoint) {
		ArrayList<Double> start = (ArrayList<Double>) Arrays.asList(0.0, 0.0, 0.0);
		return bezier(s, start, endPoint, endPoint);
	}

	public static ArrayList<CSG> linear(CSG s, ArrayList<Double> endPoint, int numSlices) {
		ArrayList<Double> start = (ArrayList<Double>) Arrays.asList(0.0, 0.0, 0.0);

		return bezier(s, start, endPoint, endPoint, numSlices);
	}

	public static ArrayList<CSG> move(ArrayList<CSG> slice, ArrayList<Transform> p) {

		return CSG.move(slice, p);
	}

	public static ArrayList<CSG> move(CSG slice, ArrayList<Transform> p) {
		return slice.move(p);
	}

	public static ArrayList<CSG> moveBezier(CSG slice, ArrayList<Double> controlA, ArrayList<Double> controlB,
			ArrayList<Double> endPoint, int numSlices) {
		ArrayList<Transform> p = bezierToTransforms(fromDouble(controlA), fromDouble(controlB), fromDouble(endPoint),
				numSlices);

		return move(slice, p);
	}

	public static ArrayList<CSG> moveBezier(ArrayList<CSG> slice, ArrayList<Double> controlA,
			ArrayList<Double> controlB, ArrayList<Double> endPoint) {

		int numSlices = slice.size();
		ArrayList<Transform> p = bezierToTransforms(fromDouble(controlA), fromDouble(controlB), fromDouble(endPoint),
				numSlices);
		return move(slice, p);

	}

	private static Vector3d fromDouble(ArrayList<Double> controlA) {
		return new Vector3d(controlA.get(0), controlA.get(1), controlA.get(2));
	}

	public static ArrayList<CSG> moveBezier(CSG slice, BezierPath pathA, int numSlices) {
		Vector3d pointA = pathA.eval((double) 1.0);
		String zpath = "C 0,0 " + pointA.x + "," + pointA.y + " " + pointA.x + "," + pointA.y;
		BezierPath pathB = new BezierPath();
		pathB.parsePathString(zpath);

		return moveBezier(slice, pathA, pathB, numSlices);

	}

	public static ArrayList<CSG> moveBezier(CSG slice, BezierPath pathA, BezierPath pathB, int iterations) {

		ArrayList<Transform> p = bezierToTransforms(pathA, pathB, iterations, null, null);
		return move(slice, p);

	}

	// public static Polygon toCCW(Polygon concave) throws ColinearPointsException {
	// if (!isCCW(concave)) {
	//// List<Vector3d> points = concave.getPoints();
	//// List<Vector3d> result = new ArrayList<>(points);
	//// Collections.reverse(result);
	//// return Polygon.fromPoints(result);
	// return concave.flipped();
	// }
	// return concave;
	// }

	public static double getMinimumDIstance() {
		return MINIMUM_DISTANCE;
	}

	public static void setMinimumDIstance(double mINIMUM_DISTANCE) {
		MINIMUM_DISTANCE = mINIMUM_DISTANCE;
	}

	public static CSG extrude(Vector3d extrudeDir, Polygon profile) throws ColinearPointsException {
		return getExtrusionEngine().extrude(extrudeDir, profile);
	}
}

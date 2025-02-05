/**
 * Plane.java
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

// # class Plane
import java.util.ArrayList;
import java.util.List;

import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;

/**
 * Represents a plane in 3D space.
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public class Plane {
	private static IPolygonDebugger debugger = null;
	private static boolean useDebugger = false;
	/**
	 * EPSILON is the tolerance used by
	 * {@link #splitPolygon(eu.mihosoft.vrl.v3d.Polygon, java.util.List, java.util.List, java.util.List, java.util.List) }
	 * to decide if a point is on the plane. public static final double EPSILON =
	 * 0.00000001;
	 */

	public static double EPSILON = 1.0e-9;
	public static double EPSILON_Point = getEPSILON();
	public static double EPSILON_duplicate = 1.0e-4;
	/**
	 * XY plane.
	 */
	public static final Plane XY_PLANE = new Plane(Vector3d.Z_ONE, 1);
	/**
	 * XZ plane.
	 */
	public static final Plane XZ_PLANE = new Plane(Vector3d.Y_ONE, 1);
	/**
	 * YZ plane.
	 */
	public static final Plane YZ_PLANE = new Plane(Vector3d.X_ONE, 1);

	/**
	 * Normal vector.
	 */
	private Vector3d normal;
	/**
	 * Distance to origin.
	 */
	private double dist;

	/**
	 * Constructor. Creates a new plane defined by its normal vector and the
	 * distance to the origin.
	 *
	 * @param normal plane normal
	 * @param dist   distance from origin
	 */
	private Plane(Vector3d normal, double dist) {
		this.setNormal(normal.normalized());
		this.setDist(dist);
	}

	/**
	 * Creates a plane defined by the the specified points.
	 *
	 * @param a first point
	 * @param b second point
	 * @param c third point
	 * @return a plane
	 */
	public static Plane createFromPoints(List<Vertex> vertices) {
		Vector3d a = vertices.get(0).pos;
		Vector3d n = computeNormal(vertices);
		return new Plane(n, n.dot(a));
	}
	/**
	 * Creates a plane defined by the the specified points.
	 *
	 * @param a first point
	 * @param b second point
	 * @param c third point
	 * @return a plane
	 */
	public static Plane createFromPointsVector3d(List<Vector3d> vertices) {
		Vector3d a = vertices.get(0);
		Vector3d n = computeNormalVector3d(vertices);
		return new Plane(n, n.dot(a));
	}
	public static Vector3d computeNormal(List<Vertex> vertices) {
		ArrayList<Vector3d> points = new ArrayList<Vector3d>();
		for(int i=0;i<vertices.size();i++) {
			points.add(vertices.get(i).pos);
		}
		return computeNormalVector3d(points);
	}
	
	public static Vector3d computeNormalVector3d(List<Vector3d> vertices) {
		if (vertices == null || vertices.size() < 3) {
			throw new RuntimeException("Failed to compute normal!");
		}

		// First attempt: Newell's method
		Vector3d normal = new Vector3d(0, 0, 0);
		int n = vertices.size();
		Vector3d lastValid = null;
		for (int i = 0; i < n; i++) {
			Vector3d current = vertices.get(i);
			Vector3d next = vertices.get((i + 1) % n);

			// Correct Newell's Method formulas
			normal.x += (current.y - next.y) * (current.z + next.z); // (y1-y2)(z1+z2)
			normal.y += (current.z - next.z) * (current.x + next.x); // (z1-z2)(x1+x2)
			normal.z += (current.x - next.x) * (current.y + next.y);
			if (i >= 2) {
				Vector3d normalized = normal.normalized();
				if (isValidNormal(normalized, getEPSILON() )) {
					lastValid = normalized;
				}
			}
		}
		if (isValidNormal(lastValid, getEPSILON() )) {
			return lastValid.normalized();
		}
		throw new RuntimeException("Mesh has problems, can not work around it");

	}

	private static boolean isValidNormal(Vector3d normal, double epsilon) {
		if (Double.isFinite(normal.x) && Double.isFinite(normal.y) && Double.isFinite(normal.z)) {
			double lengthSquared = Math.abs(normal.length());
			return Math.abs(lengthSquared - 1)<=epsilon;
		}
		return false;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Plane clone() {
		return new Plane(getNormal().clone(), getDist());
	}

	/**
	 * Flips this plane.
	 */
	public void flip() {
		setNormal(getNormal().negated());
		setDist(-getDist());
	}
	private enum PlaneType{
		COPLANAR,
		FRONT,
		BACK,
		SPANNING
	}
	/**
	 * Splits a {@link Polygon} by this plane if needed. After that it puts the
	 * polygons or the polygon fragments in the appropriate lists ({@code front},
	 * {@code back}). Coplanar polygons go into either {@code coplanarFront},
	 * {@code coplanarBack} depending on their orientation with respect to this
	 * plane. Polygons in front or back of this plane go into either {@code front}
	 * or {@code back}.
	 *
	 * @param polygon       polygon to split
	 * @param coplanarFront "coplanar front" polygons
	 * @param coplanarBack  "coplanar back" polygons
	 * @param front         front polygons
	 * @param back          back polgons
	 */
	public void splitPolygon(Polygon polygon, List<Polygon> coplanarFront, List<Polygon> coplanarBack,
			List<Polygon> front, List<Polygon> back) {
//		final int COPLANAR = 0;
//		final int FRONT = 1;
//		final int BACK = 2;
//		final int SPANNING = 3; // == some in the FRONT + some in the BACK
		if (debugger != null && useDebugger) {
//        	debugger.display(polygon);
//        	debugger.display(coplanarFront);
//        	debugger.display(coplanarBack);
//        	debugger.display(front);
//        	debugger.display(back);
		}
		// search for the epsilon values of the incoming plane
		double negEpsilon =polygon.getNegEpsilon();
		double posEpsilon = polygon.getPosEpsilon();

		PlaneType polygonType = PlaneType.COPLANAR;
		List<PlaneType> types = new ArrayList<>();
		boolean somePointsInfront = false;
		boolean somePointsInBack = false;
		for (int i = 0; i < polygon.size(); i++) {
			double t = this.getNormal().dot(polygon.get(i).pos) - this.getDist();
			PlaneType type = (t < negEpsilon) ? PlaneType.BACK : (t > posEpsilon) ? PlaneType.FRONT : PlaneType.COPLANAR;
			if (type == PlaneType.BACK)
				somePointsInBack = true;
			if (type == PlaneType.FRONT)
				somePointsInfront = true;
			types.add(type);
		}
		if (somePointsInBack && somePointsInfront)
			polygonType = PlaneType.SPANNING;
		else if (somePointsInBack) {
			polygonType = PlaneType.BACK;
		} else if (somePointsInfront)
			polygonType = PlaneType.FRONT;

		// Put the polygon in the correct list, splitting it when necessary.
		switch (polygonType) {
		case COPLANAR:
			(this.getNormal().dot(polygon.plane.getNormal()) > 0 ? coplanarFront : coplanarBack).add(polygon);
			break;
		case FRONT:
			front.add(polygon);
			break;
		case BACK:
			back.add(polygon);
			break;
		case SPANNING:
			ArrayList<Vertex> f = new ArrayList<>();
			ArrayList<Vertex> b = new ArrayList<>();
			for (int i = 0; i < polygon.size(); i++) {
				int j = (i + 1) % polygon.size();
				PlaneType ti = types.get(i);
				PlaneType tj = types.get(j);
				Vertex vi = polygon.get(i);
				Vertex vj = polygon.get(j);
				if (ti != PlaneType.BACK) {
					f.add(vi.clone());
				}
				if (ti != PlaneType.FRONT) {
					b.add(vi.clone());
				}
				if ((ti == PlaneType.FRONT&& tj==PlaneType.BACK)||
					(ti == PlaneType.BACK&& tj==PlaneType.FRONT)	) {
					double t = (this.getDist() - this.getNormal().dot(vi.pos))
							/ this.getNormal().dot(vj.pos.minus(vi.pos));
					if(t>1)
						t=1;
					if(t<0)
						t=0;
					Vertex v = vi.interpolate(vj, t);
					f.add(v);
					b.add(v.clone());
				}
			}
				
			try {
				Polygon frontPoly = new Polygon(f, polygon.getStorage(), true, polygon.plane).setColor(polygon.getColor());
				if(f.size()==3)
					front.add(frontPoly);
				else
					front.addAll(PolygonUtil.concaveToConvex(frontPoly));
			} catch (InvalidNormalException e) {
				//  Auto-generated catch block
				e.printStackTrace();
			} catch (TooFewPointsException e) {
				//  Auto-generated catch block
				e.printStackTrace();
			} catch (PointsColinearException e) {
				//  Auto-generated catch block
				e.printStackTrace();
			} catch (PointsNotCoplainer e) {
				//  Auto-generated catch block
				e.printStackTrace();
			} catch (java.lang.IllegalStateException e) {
				//  Auto-generated catch block
				e.printStackTrace();
			}

			try {
				Polygon backPoly = new Polygon(b, polygon.getStorage(), true, polygon.plane).setColor(polygon.getColor());
				if(b.size()==3)
					back.add(backPoly);
				else
					back.addAll(PolygonUtil.concaveToConvex(backPoly));
			} catch (InvalidNormalException e) {
				//  Auto-generated catch block
				e.printStackTrace();
			} catch (TooFewPointsException e) {
				//  Auto-generated catch block
				e.printStackTrace();
			} catch (PointsColinearException e) {
				//  Auto-generated catch block
				e.printStackTrace();
			} catch (PointsNotCoplainer e) {
				//  Auto-generated catch block
				e.printStackTrace();
			} catch (java.lang.IllegalStateException e) {
				//  Auto-generated catch block
				e.printStackTrace();
			}


			break;
		}
	}

	public static IPolygonDebugger getDebugger() {
		return debugger;
	}

	public static void setDebugger(IPolygonDebugger debugger) {
		Plane.debugger = debugger;
	}

	public static boolean isUseDebugger() {
		return useDebugger;
	}

	public static void setUseDebugger(boolean useDebugger) {
		Plane.useDebugger = useDebugger;
	}

	public Vector3d getNormal() {
		return normal;
	}

	public void setNormal(Vector3d normal) {
		if (Double.isFinite(normal.x) && Double.isFinite(normal.y) && Double.isFinite(normal.z))
			this.normal = normal;
		else {

			NumberFormatException numberFormatException = new NumberFormatException();
			// numberFormatException.printStackTrace();
			throw numberFormatException;
		}
		double length = normal.length();
		if((length-getEPSILON())>1 ||(length+getEPSILON())<1 ) {
			throw new NumberFormatException(" Normal Length must be 1, got "+length);
		}
	}

	public double getDist() {
		return dist;
	}

	public void setDist(double dist) {
		if (Double.isFinite(dist))
			this.dist = dist;
		else {

			NumberFormatException numberFormatException = new NumberFormatException();
			// numberFormatException.printStackTrace();
			throw numberFormatException;
		}
	}

	public static double getEPSILON() {
		return EPSILON;
	}

	public static void setEPSILON(double ePSILON) {
		EPSILON = ePSILON;
	}

	public void transformPlane(Transform transform_in, Vector3d a) {
		Transform trans_rot = transform_in.copy()//.inverse()
								.setToOrigin();
//		Transform trans_dist = new Transform().movex(transform_in.getX())
//												.movey(transform_in.getY())
//												.movez(transform_in.getZ());
		Vector3d newNormal = this.normal.transformed(trans_rot);
		newNormal = newNormal.negated();
		this.setNormal(newNormal.normalized());
		this.setDist(this.normal.dot(a));
		
	}
}

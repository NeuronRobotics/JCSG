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
	public Plane(Vector3d normal, double dist) {
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

	public static Vector3d computeNormal(List<Vertex> vertices) {
		if (vertices == null || vertices.size() < 3) {
			return new Vector3d(0, 0, 1); // Default normal for degenerate cases
		}

		// First attempt: Newell's method
		Vector3d normal = new Vector3d(0, 0, 0);
		int n = vertices.size();
		Vector3d lastValid = null;
		for (int i = 0; i < n; i++) {
			Vector3d current = vertices.get(i).pos;
			Vector3d next = vertices.get((i + 1) % n).pos;

			// Correct Newell's Method formulas
			normal.x += (current.y - next.y) * (current.z + next.z); // (y1-y2)(z1+z2)
			normal.y += (current.z - next.z) * (current.x + next.x); // (z1-z2)(x1+x2)
			normal.z += (current.x - next.x) * (current.y + next.y);
			if (n >= 3) {
				Vector3d normalized = normal.normalized();
				if (isValidNormal(normalized, getEPSILON() / 10)) {
					lastValid = normalized;
				}
			}
		}
		if (isValidNormal(lastValid, getEPSILON() / 10)) {
			return lastValid;
		}
		throw new RuntimeException("Mesh has problems, can not work around it");
//	    // Second attempt: Find three non-colinear points
//	   
//	    normal = findNormalFromNonColinearPoints(vertices);
//	    if (normal != null) {
//	    	 System.err.println("findNormalFromNonColinearPoints ");
//	        return normal;
//	    }
//	    // Third attempt: Find principal direction
//	    normal = findPrincipalDirection(vertices);
//	    if (normal != null) {
//		    System.err.println("findPrincipalDirection ");
//
//	        return normal;
//	    }
//	    System.err.println("determineStatisticalNormal ");
//	    // Final fallback: Use statistical approach
//	    return determineStatisticalNormal(vertices);
	}

	private static boolean isValidNormal(Vector3d normal, double epsilon) {
		if (Double.isFinite(normal.x) && Double.isFinite(normal.y) && Double.isFinite(normal.z)) {
			double lengthSquared = Math.abs(normal.length());
			return lengthSquared >= epsilon;
		}
		return false;
	}

	private static Vector3d findNormalFromNonColinearPoints(List<Vertex> vertices) {
		int n = vertices.size();
		Vector3d firstPoint = vertices.get(0).pos;

		// Try to find two vectors that aren't parallel
		for (int i = 1; i < n; i++) {
			Vector3d v1 = vertices.get(i).pos.minus(firstPoint);
			for (int j = i + 1; j < n; j++) {
				Vector3d v2 = vertices.get(j).pos.minus(firstPoint);
				Vector3d cross = v1.cross(v2);
				if (isValidNormal(cross, getEPSILON())) {
					return cross.normalized();
				}
			}
		}
		return null;
	}

	private static Vector3d findPrincipalDirection(List<Vertex> vertices) {
		// Find the direction with maximum spread
		double maxX = Double.NEGATIVE_INFINITY;
		double minX = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;

		for (Vertex vertex : vertices) {
			Vector3d pos = vertex.pos;
			maxX = Math.max(maxX, pos.x);
			minX = Math.min(minX, pos.x);
			maxY = Math.max(maxY, pos.y);
			minY = Math.min(minY, pos.y);
			maxZ = Math.max(maxZ, pos.z);
			minZ = Math.min(minZ, pos.z);
		}

		double rangeX = maxX - minX;
		double rangeY = maxY - minY;
		double rangeZ = maxZ - minZ;

		// Use the axis with minimum spread as normal direction
		if (rangeX <= rangeY && rangeX <= rangeZ) {
			return new Vector3d(1, 0, 0);
		} else if (rangeY <= rangeX && rangeY <= rangeZ) {
			return new Vector3d(0, 1, 0);
		} else {
			return new Vector3d(0, 0, 1);
		}
	}

	private static Vector3d determineStatisticalNormal(List<Vertex> vertices) {
		// Calculate center of mass
		Vector3d center = new Vector3d(0, 0, 0);
		for (Vertex vertex : vertices) {
			center = center.plus(vertex.pos);
		}
		center = center.times(1.0 / vertices.size());

		// Calculate covariance matrix
		double[][] covariance = new double[3][3];
		for (Vertex vertex : vertices) {
			Vector3d diff = vertex.pos.minus(center);
			covariance[0][0] += diff.x * diff.x;
			covariance[0][1] += diff.x * diff.y;
			covariance[0][2] += diff.x * diff.z;
			covariance[1][1] += diff.y * diff.y;
			covariance[1][2] += diff.y * diff.z;
			covariance[2][2] += diff.z * diff.z;
		}
		covariance[1][0] = covariance[0][1];
		covariance[2][0] = covariance[0][2];
		covariance[2][1] = covariance[1][2];

		// Use the eigenvector corresponding to the smallest eigenvalue
		// For simplicity, we'll use power iteration to find it
		Vector3d normal = new Vector3d(1, 1, 1);
		for (int i = 0; i < 10; i++) {
			normal = multiplyMatrixVector(covariance, normal);
			normal = normal.normalized();
		}

		return normal;
	}

	private static Vector3d multiplyMatrixVector(double[][] matrix, Vector3d vector) {
		return new Vector3d(matrix[0][0] * vector.x + matrix[0][1] * vector.y + matrix[0][2] * vector.z,
				matrix[1][0] * vector.x + matrix[1][1] * vector.y + matrix[1][2] * vector.z,
				matrix[2][0] * vector.x + matrix[2][1] * vector.y + matrix[2][2] * vector.z);
	}
//	public static Vector3d computeNormal(List<Vertex> vertices) {
//		Vector3d normal = new Vector3d(0, 0, 0);
//		int n = vertices.size();
//
//		for (int i = 0; i < n; i++) {
//			Vector3d current = vertices.get(i).pos;
//			Vector3d next = vertices.get((i + 1) % n).pos;
//
//			normal.x += (current.y - next.y) * (current.z + next.z);
//			normal.y += (current.z - next.z) * (current.x + next.x);
//			normal.z += (current.x - next.x) * (current.y + next.y);
//		}
//
//		Vector3d normalized = normal.normalized();
//		// If Newell's method fails, try finding three non-collinear points
//		double lengthSquared = normal.lengthSquared();
//		double d = EPSILON * EPSILON;
//		if (lengthSquared < d) { // Adjust this epsilon as needed
//			for (int i = 0; i < n - 2; i++) {
//				Vector3d a = vertices.get(i).pos;
//				for (int j = i + 1; j < n - 1; j++) {
//					Vector3d b = vertices.get(j).pos;
//					for (int k = j + 1; k < n; k++) {
//						Vector3d c = vertices.get(k).pos;
//						normal = b.minus(a).cross(c.minus(a));
//						lengthSquared = normal.lengthSquared();
//						if (lengthSquared > d) { // Non-zero normal found
//							return normal.normalized();
//						}
//					}
//				}
//			}
//		}
//
//		// If all else fails, return a default normal (e.g., in the z direction)
//		lengthSquared = normal.lengthSquared();
//
//		if (lengthSquared < Double.MIN_VALUE*10) {
//			throw new NumberFormatException("This set of points is not a valid polygon");
//		}
//		if(normalized.lengthSquared()<EPSILON)
//			throw new NumberFormatException("Invalid Normalized Values!");
//		return normalized;
//	}

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
		final int COPLANAR = 0;
		final int FRONT = 1;
		final int BACK = 2;
		final int SPANNING = 3; // == some in the FRONT + some in the BACK
		if (debugger != null && useDebugger) {
//        	debugger.display(polygon);
//        	debugger.display(coplanarFront);
//        	debugger.display(coplanarBack);
//        	debugger.display(front);
//        	debugger.display(back);
		}
		// search for the epsilon values of the incoming plane
		double negEpsilon = -Plane.getEPSILON();
		double posEpsilon = Plane.getEPSILON();
		for (int i = 0; i < polygon.getVertices().size(); i++) {
			double t = polygon.plane.getNormal().dot(polygon.getVertices().get(i).pos) - polygon.plane.getDist();
			if (t > posEpsilon) {
				// com.neuronrobotics.sdk.common.Log.error("Non flat polygon, increasing
				// positive epsilon "+t);
				posEpsilon = t + Plane.getEPSILON();
			}
			if (t < negEpsilon) {
				// com.neuronrobotics.sdk.common.Log.error("Non flat polygon, decreasing
				// negative epsilon "+t);
				negEpsilon = t - Plane.getEPSILON();
			}
		}
		int polygonType = 0;
		List<Integer> types = new ArrayList<>();
		boolean somePointsInfront = false;
		boolean somePointsInBack = false;
		for (int i = 0; i < polygon.getVertices().size(); i++) {
			double t = this.getNormal().dot(polygon.getVertices().get(i).pos) - this.getDist();
			int type = (t < negEpsilon) ? BACK : (t > posEpsilon) ? FRONT : COPLANAR;
			if (type == BACK)
				somePointsInBack = true;
			if (type == FRONT)
				somePointsInfront = true;
			types.add(type);
		}
		if (somePointsInBack && somePointsInfront)
			polygonType = SPANNING;
		else if (somePointsInBack) {
			polygonType = BACK;
		} else if (somePointsInfront)
			polygonType = FRONT;

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
			List<Vertex> f = new ArrayList<>();
			List<Vertex> b = new ArrayList<>();
			for (int i = 0; i < polygon.getVertices().size(); i++) {
				int j = (i + 1) % polygon.getVertices().size();
				int ti = types.get(i);
				int tj = types.get(j);
				Vertex vi = polygon.getVertices().get(i);
				Vertex vj = polygon.getVertices().get(j);
				if (ti != BACK) {
					f.add(vi);
				}
				if (ti != FRONT) {
					b.add(ti != BACK ? vi.clone() : vi);
				}
				if ((ti | tj) == SPANNING) {
					double t = (this.getDist() - this.getNormal().dot(vi.pos))
							/ this.getNormal().dot(vj.pos.minus(vi.pos));
					Vertex v = vi.interpolate(vj, t);
					f.add(v);
					b.add(v.clone());
				}
			}
			if (f.size() >= 3) {
				try {
					front.add(new Polygon(f, polygon.getStorage(),false,polygon.plane).setColor(polygon.getColor()));
				} catch (Exception ex) {
					System.err.println("Pruning bad polygon Plane::splitPolygon");
					// skip adding broken polygon here
				}
			} else {
				// com.neuronrobotics.sdk.common.Log.error("Front Clip Fault!");
			}
			if (b.size() >= 3) {
				try {
					back.add(new Polygon(b, polygon.getStorage(),false,polygon.plane).setColor(polygon.getColor()));
				} catch (Exception ex) {
					// ex.printStackTrace();
					System.err.println("Pruning bad polygon Plane::splitPolygon");
				}
			} else {
				// com.neuronrobotics.sdk.common.Log.error("Back Clip Fault!");
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
		Transform trans_rot = transform_in.copy()// .inverse()
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

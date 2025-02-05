/**
 * Polygon.java
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;
import javafx.scene.paint.Color;

//  Auto-generated Javadoc
/**
 * Represents a convex polygon.
 *
 * Each convex polygon has a {@code shared} property, which is shared between
 * all polygons that are clones of each other or where split from the same
 * polygon. This can be used to define per-polygon properties (such as surface
 * color).
 */
public final class Polygon {

	/** Polygon vertices. */
	private ArrayList<eu.mihosoft.vrl.v3d.Vertex> vertices;
	/**
	 * Shared property (can be used for shared color etc.).
	 */
	private PropertyStorage shared;
	/**
	 * Plane defined by this polygon.
	 *
	 * Note: uses first three vertices to define the plane.
	 */
	public Plane plane=null;
	private boolean isHole = false;
	private boolean allowDegenerate;
	
	public int size() {
		return vertices.size();
	}
	public Vertex get(int i) {
		return vertices.get(i);
	}
	public void add(int index, Vertex vi) {

		int index2 = (index - 1)%vertices.size();
		if(index2<0)
			index2=vertices.size()-1;
		Vertex b = vertices.get(index2);
		if (b.pos.test(vi.pos, Plane.getEPSILON())) {
			System.out.println("Point not added, touching " + vi);
			return;
		}
		int index3 = (index)%vertices.size();
		Vertex a = vertices.get(index3);
		if (b.pos.test(vi.pos, Plane.getEPSILON())) {
			System.out.println("Point not added, touching " + vi);
			return;
		}

		vertices.add(index, vi);
		try {
			validateAndInit();
		} catch (InvalidNormalException | TooFewPointsException | PointsColinearException | PointsNotCoplainer e) {
			throw new RuntimeException(e);
		}
	}

	public void add(Vertex vi) {
		add(vertices.size(), vi);
	}

	/**
	 * Sets the storage.
	 *
	 * @param storage the new storage
	 */
	void setStorage(PropertyStorage storage) {
		this.shared = storage;
	}

	/**
	 * Decomposes the specified concave polygon into convex polygons.
	 *
	 * @param points the points that define the polygon
	 * @return the decomposed concave polygon (list of convex polygons)
	 * @throws PointsNotCoplainer
	 * @throws PointsColinearException
	 * @throws TooFewPointsException
	 * @throws InvalidNormalException
	 */
	public static List<Polygon> fromConcavePoints(Vector3d... points)
			throws InvalidNormalException, TooFewPointsException, PointsColinearException, PointsNotCoplainer {
		Polygon p = fromPoints(points);

		return PolygonUtil.concaveToConvex(p);
	}

	/**
	 * Decomposes the specified concave polygon into convex polygons.
	 *
	 * @param points the points that define the polygon
	 * @return the decomposed concave polygon (list of convex polygons)
	 * @throws PointsNotCoplainer
	 * @throws PointsColinearException
	 * @throws TooFewPointsException
	 * @throws InvalidNormalException
	 */
	public static List<Polygon> fromConcavePoints(List<Vector3d> points)
			throws InvalidNormalException, TooFewPointsException, PointsColinearException, PointsNotCoplainer {
		Polygon p = fromPoints(points);

		return PolygonUtil.concaveToConvex(p);
	}

//	/**
//	 * Constructor. Creates a new polygon that consists of the specified vertices.
//	 *
//	 * Note: the vertices used to initialize a polygon must be coplanar and form a
//	 * convex loop.
//	 *
//	 * @param vertices polygon vertices
//	 * @param shared   shared property
//	 */
//	public Polygon(List<Vertex> vertices, PropertyStorage shared, boolean allowDegenerate) {
//		this.vertices = pruneDuplicatePoints(vertices);
//		this.shared = shared;
//
//		validateAndInit();
//	}

	/**
	 * Constructor. Creates a new polygon that consists of the specified vertices.
	 *
	 * Note: the vertices used to initialize a polygon must be coplanar and form a
	 * convex loop.
	 *
	 * @param vertices polygon vertices
	 * @param shared   shared property
	 * @throws PointsNotCoplainer
	 * @throws PointsColinearException
	 * @throws TooFewPointsException
	 * @throws InvalidNormalException
	 */
	public Polygon(List<Vertex> vertices, PropertyStorage shared, boolean allowDegenerate, Plane p)
			throws InvalidNormalException, TooFewPointsException, PointsColinearException, PointsNotCoplainer {
		this.allowDegenerate = allowDegenerate;
		if(p!=null)
			plane=p.clone();
		this.vertices = pruneDuplicatePoints(vertices);
		this.shared = shared;

		validateAndInit();
	}

//	/**
//	 * Constructor. Creates a new polygon that consists of the specified vertices.
//	 *
//	 * Note: the vertices used to initialize a polygon must be coplanar and form a
//	 * convex loop.
//	 *
//	 * @param vertices polygon vertices
//	 * @param shared   shared property
//	 */
//	public Polygon(List<Vertex> vertices, PropertyStorage shared) {
//		this(vertices, shared, true, null);
//	}

//	/**
//	 * Constructor. Creates a new polygon that consists of the specified vertices.
//	 *
//	 * Note: the vertices used to initialize a polygon must be coplanar and form a
//	 * convex loop.
//	 *
//	 * @param vertices polygon vertices
//	 */
//	public Polygon(List<Vertex> vertices) {
//		this(vertices, new PropertyStorage(), true, null);
//	}

	public void pruneDuplicatePoints() {
		this.vertices = pruneDuplicatePoints(vertices);
	}

	public static ArrayList<Vertex> pruneDuplicatePoints(List<Vertex> incoming) {
		// return incoming;
		ArrayList<Vertex> newPoints = new ArrayList<Vertex>();
		for (int i = 0; i < incoming.size(); i++) {
			Vertex v = incoming.get(i);
			boolean duplicate = false;
			for (Vertex vx : newPoints) {
				if (vx.pos.test(v.pos, Plane.getEPSILON())) {
					duplicate = true;
					break;
				}
			}
			if (!duplicate) {
				// v.pos.roundToEpsilon();
				newPoints.add(v);
			}

		}

		return newPoints;

	}

	public void validateAndInit()
			throws InvalidNormalException, TooFewPointsException, PointsColinearException, PointsNotCoplainer {
		if (plane == null)
			this.plane = Plane.createFromPoints(vertices);
		if (shared == null)
			this.shared = new PropertyStorage();
		for (Vertex v : vertices) {
			v.normal = plane.getNormal();
			// v.pos.roundToEpsilon();
		}
		//setDegenerate(true);
		if (Vector3d.ZERO.equals(plane.getNormal())) {
			valid = false;
			throw new InvalidNormalException(
					"Normal is zero! Probably, duplicate points have been specified!\n\n" + toStlString());
		}

		if (vertices.size() < 3) {
			throw new TooFewPointsException("Invalid polygon: at least 3 vertices expected, got: " + vertices.size());
		}

		if (areAllPointsCollinear(vertices))
			throw new PointsColinearException("This polygon is colinear");

		if(!arePointsCoplanar()) {
			throw new PointsNotCoplainer("All points are not on plane of epsilon "+Plane.getEPSILON());
		}
		//setDegenerate(false);
		setNegEpsilon(-Plane.getEPSILON());
		setPosEpsilon(Plane.getEPSILON());
//		for (int i = 0; i < size(); i++) {
//			double t = computeDistance(i);
//			if (t > getPosEpsilon()) {
//				System.err.println("Non flat polygon, increasing positive epsilon "+t);
//				setPosEpsilon(t + Plane.getEPSILON());
//			}
//			if (t < getNegEpsilon()) {
//				System.err.println("Non flat polygon, decreasing negative epsilon "+t);
//				setNegEpsilon(t - Plane.getEPSILON());
//			}
//		}
//		if(posEpsilon>0.001||negEpsilon<-0.001)
//			throw new RuntimeException("Faulty polygon epsilons!");
	}


	public boolean arePointsCoplanar() {
		if (vertices.size() < 4) {
			// Fewer than 4 points are always coplanar
			return true;
		}
		for (int i = 0; i < vertices.size(); i++) {
			double t = computeDistance( i);
			double d = Plane.getEPSILON();
			if(Math.abs(t)>d) {
				if(t>0)
					setPosEpsilon(t);
				if(t<0)
					setNegEpsilon(t);
//				Vector3d normal = plane.getNormal();
//				Vector3d pos = vertices.get(i).pos;
//				double dist = plane.getDist();
//				double dot = normal.dot(pos);
//				double nt= dot - dist;
//				System.err.println("Coplainer fail: "+this);
//				System.err.println("Non flat polygon! This points distance from the plane is "+nt+" dot:"+dot+" planeDist:"+dist+" computed t "+t);
//				return false;
			}
		}
		double epCheck = 1.0e-6;
		if(posEpsilon>epCheck || negEpsilon < -epCheck)
			return false;
		return true; // All points are coplanar
	}

	double computeDistance(int i) {
		Vector3d normal = plane.getNormal();
		Vector3d pos = vertices.get(i).pos;
		double dist = plane.getDist();
		double dot =  normal.dot(pos);
		return dot - dist;
	}

	public boolean areAllPointsCollinear(ArrayList<eu.mihosoft.vrl.v3d.Vertex> vertices) {
		// If we have 2 or fewer points, they're always collinear
		if (vertices.size() <= 2) {
			return true;
		}

		// Get first two points to establish a direction vector
		Vertex p1 = vertices.get(0);
		Vertex p2 = vertices.get(1);

		// Calculate the direction vector between first two points
		double[] directionVector = { p2.getX() - p1.getX(), p2.getY() - p1.getY(), p2.getZ() - p1.getZ() };

		// Normalize the direction vector
		double length = Math.sqrt(directionVector[0] * directionVector[0] + directionVector[1] * directionVector[1]
				+ directionVector[2] * directionVector[2]);

		if (length < Plane.getEPSILON()) { // If points are effectively identical
			return false;
		}

		directionVector[0] /= length;
		directionVector[1] /= length;
		directionVector[2] /= length;

		// Check each subsequent point
		for (int i = 2; i < vertices.size(); i++) {
			Vertex p = vertices.get(i);

			// Vector from first point to current point
			double[] currentVector = { p.getX() - p1.getX(), p.getY() - p1.getY(), p.getZ() - p1.getZ() };

			// Calculate cross product
			double[] crossProduct = { directionVector[1] * currentVector[2] - directionVector[2] * currentVector[1],
					directionVector[2] * currentVector[0] - directionVector[0] * currentVector[2],
					directionVector[0] * currentVector[1] - directionVector[1] * currentVector[0] };

			// Calculate magnitude of cross product
			double magnitude = Math.sqrt(crossProduct[0] * crossProduct[0] + crossProduct[1] * crossProduct[1]
					+ crossProduct[2] * crossProduct[2]);

			// If magnitude is not close to zero, points are not collinear
			if (magnitude > Plane.getEPSILON()) {
				return false;
			}
		}

		return true;
	}

//	/**
//	 * Constructor. Creates a new polygon that consists of the specified vertices.
//	 *
//	 * Note: the vertices used to initialize a polygon must be coplanar and form a
//	 * convex loop.
//	 *
//	 * @param vertices polygon vertices
//	 *
//	 */
//	public Polygon(Vertex... vertices) {
//		this(Arrays.asList(vertices));
//	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Polygon clone() {
		List<Vertex> newVertices = new ArrayList<>();
		for(int i=0;i<vertices.size();i++) {
			newVertices.add(vertices.get(i).clone());
		}
		try {
			return new Polygon(newVertices, getStorage(), true, this.plane).setColor(color);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} 
		
	}

	/**
	 * Flips this polygon.
	 *
	 * @return this polygon
	 * @throws PointsNotCoplainer 
	 * @throws PointsColinearException 
	 * @throws TooFewPointsException 
	 * @throws InvalidNormalException 
	 */
	public Polygon flip() throws InvalidNormalException, TooFewPointsException, PointsColinearException, PointsNotCoplainer {
		vertices.forEach((vertex) -> {
			vertex.flip();
		});
		Collections.reverse(vertices);

		plane.flip();
		validateAndInit();
		return this;
	}

	/**
	 * Returns a flipped copy of this polygon.
	 *
	 * Note: this polygon is not modified.
	 *
	 * @return a flipped copy of this polygon
	 * @throws PointsNotCoplainer 
	 * @throws PointsColinearException 
	 * @throws TooFewPointsException 
	 * @throws InvalidNormalException 
	 */
	public Polygon flipped() throws InvalidNormalException, TooFewPointsException, PointsColinearException, PointsNotCoplainer {
		return clone().flip();
	}

	/**
	 * Returns this polygon in STL string format.
	 *
	 * @return this polygon in STL string format
	 */
	public String toStlString() {
		return toStlString(new StringBuilder()).toString();
	}

	/**
	 * Returns this polygon in STL string format.
	 *
	 * @param sb string builder
	 *
	 * @return the specified string builder
	 */
	public StringBuilder toStlString(StringBuilder sb) {

		if (this.vertices.size() == 3) {

			String firstVertexStl = this.vertices.get(0).toStlString();

			sb.append("  facet normal ").append(this.plane.getNormal().toStlString()).append("\n")
					.append("    outer loop\n").append("      ").append(firstVertexStl).append("\n").append("      ");
			this.vertices.get(1).toStlString(sb).append("\n").append("      ");
			this.vertices.get(2).toStlString(sb).append("\n").append("    endloop\n").append("  endfacet\n");

		} else {
			throw new RuntimeException("Polygon must be a triangle before STL can be made " + vertices.size());
		}

		return sb;
	}

	/**
	 * Translates this polygon.
	 *
	 * @param v the vector that defines the translation
	 * @return this polygon
	 */
	public Polygon translate(Vector3d v) {
		transform(new Transform().move(v));
		return this;
	}

	/**
	 * Returns a translated copy of this polygon.
	 *
	 * Note: this polygon is not modified
	 *
	 * @param v the vector that defines the translation
	 *
	 * @return a translated copy of this polygon
	 */
	public Polygon translated(Vector3d v) {
		return clone().translate(v);
	}

	/**
	 * Applies the specified transformation to this polygon.
	 *
	 * Note: if the applied transformation performs a mirror operation the vertex
	 * order of this polygon is reversed.
	 *
	 * @param transform the transformation to apply
	 *
	 * @return this polygon
	 */
	public Polygon transform(Transform transform) {

//		this.vertices.stream().forEach((v) -> {
//			v.transform(transform);
//		});
		for(int i=0;i<size();i++) {
			get(i).transform(transform);
		}

		Vector3d a = get(0).pos;

		// old way
//		this.plane.setNormal(Plane.computeNormal(this.vertices));
//		this.plane.setDist(this.plane.getNormal().dot(a));
		Vector3d old = Plane.computeNormal(this.vertices);
		double old_dist = plane.getNormal().dot(a);

		// new way
		this.plane.transformPlane(transform, a);
		this.plane.setDist(plane.getNormal().dot(a));
		
		double tester_dist = Math.abs(old_dist)-Math.abs(this.plane.getDist());
//		if(Math.abs(tester_dist) > plane.getEPSILON())
//			throw new RuntimeException("distance calculation is incorrect");

		boolean mirror = transform.isMirror();
		if (mirror) {
			// the transformation includes mirroring. flip polygon
			try {
				flip();
			} catch (InvalidNormalException | TooFewPointsException | PointsColinearException | PointsNotCoplainer e) {
				throw new RuntimeException(e);
			}
			//this.plane.transformPlane(new Transform().rotX(180), a);

		}

		double ePSILON = plane.EPSILON;
		Vector3d normal = plane.getNormal();
		boolean tester_vect = normal.test(old, ePSILON);
		if(!tester_vect) {
			//throw new RuntimeException("Normal calculation is incorrect");
		}
		try {
			validateAndInit();
		} catch (InvalidNormalException | TooFewPointsException | PointsColinearException | PointsNotCoplainer e) {
			throw new RuntimeException(e);
		}
		return this;
	}

	/**
	 * Returns a transformed copy of this polygon.
	 *
	 * Note: if the applied transformation performs a mirror operation the vertex
	 * order of this polygon is reversed.
	 *
	 * Note: this polygon is not modified
	 *
	 * @param transform the transformation to apply
	 * @return a transformed copy of this polygon
	 */
	public Polygon transformed(Transform transform) {
		return clone().transform(transform);
	}

	/**
	 * Creates a polygon from the specified point list.
	 *
	 * @param points the points that define the polygon
	 * @param shared shared property storage
	 * @return a polygon defined by the specified point list
	 * @throws PointsNotCoplainer
	 * @throws PointsColinearException
	 * @throws TooFewPointsException
	 * @throws InvalidNormalException
	 */
	public static Polygon fromPoints(List<Vector3d> points, PropertyStorage shared)
			throws InvalidNormalException, TooFewPointsException, PointsColinearException, PointsNotCoplainer {
		return fromPoints(points, shared, null, true);
	}

	/**
	 * Creates a polygon from the specified point list.
	 *
	 * @param points the points that define the polygon
	 * @return a polygon defined by the specified point list
	 * @throws PointsNotCoplainer
	 * @throws PointsColinearException
	 * @throws TooFewPointsException
	 * @throws InvalidNormalException
	 */
	public static Polygon fromPoints(List<Vector3d> points)
			throws InvalidNormalException, TooFewPointsException, PointsColinearException, PointsNotCoplainer {
		return fromPoints(points, new PropertyStorage(), null, true);
	}

	/**
	 * Creates a polygon from the specified points.
	 *
	 * @param points the points that define the polygon
	 * @return a polygon defined by the specified point list
	 * @throws PointsNotCoplainer
	 * @throws PointsColinearException
	 * @throws TooFewPointsException
	 * @throws InvalidNormalException
	 */
	public static Polygon fromPoints(Vector3d... points)
			throws InvalidNormalException, TooFewPointsException, PointsColinearException, PointsNotCoplainer {
		return fromPoints(Arrays.asList(points), new PropertyStorage(), null, true);
	}

	public static Polygon fromPointsAllowDegenerate(List<Vector3d> vertices2)
			throws InvalidNormalException, TooFewPointsException, PointsColinearException, PointsNotCoplainer {
		return fromPoints(vertices2, new PropertyStorage(), null, true);
	}

	/**
	 * Creates a polygon from the specified point list.
	 *
	 * @param points the points that define the polygon
	 * @param shared the shared
	 * @param plane  may be null
	 * @return a polygon defined by the specified point list
	 * @throws PointsNotCoplainer
	 * @throws PointsColinearException
	 * @throws TooFewPointsException
	 * @throws InvalidNormalException
	 */
	private static Polygon fromPoints(List<Vector3d> points, PropertyStorage shared, Plane plane,
			boolean allowDegenerate)
			throws InvalidNormalException, TooFewPointsException, PointsColinearException, PointsNotCoplainer {
		if(plane==null){
			plane = Plane.createFromPointsVector3d(points);
		}
		List<Vertex> vertices = new ArrayList<>();

		for (Vector3d p : points) {
			Vector3d vec = p.clone();
			Vertex vertex = new Vertex(vec, plane.getNormal());
			vertices.add(vertex);
		}

		return new Polygon(vertices, shared, allowDegenerate, plane);
	}

	/**
	 * Returns the bounds of this polygon.
	 *
	 * @return bouds of this polygon
	 */
	public Bounds getBounds() {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;

		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;

		for (int i = 0; i < vertices.size(); i++) {

			Vertex vert = vertices.get(i);

			if (vert.pos.x < minX) {
				minX = vert.pos.x;
			}
			if (vert.pos.y < minY) {
				minY = vert.pos.y;
			}
			if (vert.pos.z < minZ) {
				minZ = vert.pos.z;
			}

			if (vert.pos.x > maxX) {
				maxX = vert.pos.x;
			}
			if (vert.pos.y > maxY) {
				maxY = vert.pos.y;
			}
			if (vert.pos.z > maxZ) {
				maxZ = vert.pos.z;
			}

		} // end for vertices

		return new Bounds(new Vector3d(minX, minY, minZ), new Vector3d(maxX, maxY, maxZ));
	}

	/**
	 * Contains.
	 *
	 * @param p the p
	 * @return true, if successful
	 */
	public boolean contains(Vector3d p) {
		// taken from http://www.java-gaming.org/index.php?topic=26013.0
		// and http://www.ecse.rpi.edu/Homepages/wrf/Research/Short_Notes/pnpoly.html
		double px = p.x;
		double py = p.y;
		boolean oddNodes = false;
		double x2 = vertices.get(vertices.size() - 1).pos.x;
		double y2 = vertices.get(vertices.size() - 1).pos.y;
		double x1, y1;
		for (int i = 0; i < vertices.size(); x2 = x1, y2 = y1, ++i) {
			x1 = vertices.get(i).pos.x;
			y1 = vertices.get(i).pos.y;
			if (((y1 < py) && (y2 >= py)) || (y1 >= py) && (y2 < py)) {
				if ((py - y1) / (y2 - y1) * (x2 - x1) < (px - x1)) {
					oddNodes = !oddNodes;
				}
			}
		}
		return oddNodes;
	}

	/**
	 * Contains.
	 *
	 * @param p the p
	 * @return true, if successful
	 */
	public boolean contains(Polygon p) {

		for (Vertex v : p.vertices) {
			if (!contains(v.pos)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Gets the storage.
	 *
	 * @return the shared
	 */
	public PropertyStorage getStorage() {

		if (shared == null) {
			shared = new PropertyStorage();
		}

		return shared;
	}

//	public Exception getCreationEventStackTrace() {
//		return creationEventStackTrace;
//	}

	public List<Vector3d> getPoints() {
		ArrayList<Vector3d> p = new ArrayList<>();
		for (Vertex v : vertices) {
			p.add(v.pos);
		}
		return p;
	}

	/**
	 * Movey.
	 *
	 * @param howFarToMove the how far to move
	 * @return the csg
	 */
	// Helper/wrapper functions for movement
	public Polygon movey(Number howFarToMove) {
		return this.transformed(Transform.unity().translateY(howFarToMove.doubleValue()));
	}

	/**
	 * Movez.
	 *
	 * @param howFarToMove the how far to move
	 * @return the csg
	 */
	public Polygon movez(Number howFarToMove) {
		return this.transformed(Transform.unity().translateZ(howFarToMove.doubleValue()));
	}

	/**
	 * Movex.
	 *
	 * @param howFarToMove the how far to move
	 * @return the csg
	 */
	public Polygon movex(Number howFarToMove) {
		return this.transformed(Transform.unity().translateX(howFarToMove.doubleValue()));
	}

	/**
	 * Rotz.
	 *
	 * @param degreesToRotate the degrees to rotate
	 * @return the csg
	 */
	// Rotation function, rotates the object
	public Polygon rotz(Number degreesToRotate) {
		return this.transformed(new Transform().rotZ(degreesToRotate.doubleValue()));
	}

	/**
	 * Roty.
	 *
	 * @param degreesToRotate the degrees to rotate
	 * @return the csg
	 */
	public Polygon roty(Number degreesToRotate) {
		return this.transformed(new Transform().rotY(degreesToRotate.doubleValue()));
	}

	/**
	 * Rotx.
	 *
	 * @param degreesToRotate the degrees to rotate
	 * @return the csg
	 */
	public Polygon rotx(Number degreesToRotate) {
		return this.transformed(new Transform().rotX(degreesToRotate.doubleValue()));
	}

	/**
	 * Scalez.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 */
	// Scale function, scales the object
	public Polygon scalez(Number scaleValue) {
		return this.transformed(new Transform().scaleZ(scaleValue.doubleValue()));
	}

	/**
	 * Scaley.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 */
	public Polygon scaley(Number scaleValue) {
		return this.transformed(new Transform().scaleY(scaleValue.doubleValue()));
	}

	/**
	 * Scalex.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 */
	public Polygon scalex(Number scaleValue) {
		return this.transformed(new Transform().scaleX(scaleValue.doubleValue()));
	}

	/**
	 * Scale.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 */
	public Polygon scale(Number scaleValue) {
		return this.transformed(new Transform().scale(scaleValue.doubleValue()));
	}

	/**
	 * Indicates whether this polyon is valid, i.e., if it
	 *
	 * @return
	 */
	public boolean isValid() {
		return valid;
	}

	private boolean valid = true;
	private boolean degenerate = false;
	private Color color;
	private double negEpsilon;
	private double posEpsilon;

//	public void setDegenerate(boolean degenerate) {
//		this.degenerate = degenerate;
//	}
//
//	public boolean isDegenerate() {
//
//		return degenerate;
//	}

//	public ArrayList<Vertex> getDegeneratePoints() {
//		ArrayList<Vertex> back = new ArrayList<Vertex>();
//		if (!isDegenerate())
//			return back;
//		Edge longEdge = getLongEdge();
//		for (int i = 0; i < vertices.size(); i++) {
//			Vertex vertex = vertices.get(i);
//			if (vertex != longEdge.getP1() && vertex != longEdge.getP2()) {
//				back.add(vertex);
//			}
//		}
//		if (back.size() == 0)
//			throw new RuntimeException("Failed to find the degenerate point in the polygon");
//		return back;
//	}

	public Edge getLongEdge() {

		ArrayList<Edge> e = edges();
		Edge longEdge = e.get(0);
		for (int i = 1; i < e.size(); i++) {
			Edge edge = e.get(i);
			if (edge.length() > longEdge.length()) {
				longEdge = edge;
			}
		}
		return longEdge;
	}

	public ArrayList<Edge> edges() {
		ArrayList<Edge> e = new ArrayList<Edge>();
		for (int i = 0; i < vertices.size(); i++) {
			int i1 = i;
			int i2 = i + 1;
			if (i2 == vertices.size()) {
				i2 = 0;
			}
			e.add(new Edge(vertices.get(i1), vertices.get(i2)));
		}
		return e;
	}

	public Polygon setColor(Color color) {
		this.color = color;
		return this;
	}

	public Color getColor() {
		return this.color;
	}

	@Override
	public String toString() {
		String ret = "# points=" + vertices.size() + " normal=" + plane.getNormal().toStlString() + " [ ";
		for (Vertex v : vertices) {
			ret += " " + v.pos.toStlString() + " , ";
		}
		return ret + " ] ";
	}

	public boolean isHole() {
		return isHole;
	}

	public void setHole(boolean isHole) {
		this.isHole = isHole;
	}

	public boolean isBoundsTouching(Polygon incoming) {
		return getBounds().isBoundsTouching(incoming.getBounds());
	}
	public double getNegEpsilon() {
		return negEpsilon;
	}
	public void setNegEpsilon(double negEpsilon) {
		this.negEpsilon = negEpsilon;
	}
	public double getPosEpsilon() {
		return posEpsilon;
	}
	public void setPosEpsilon(double posEpsilon) {
		this.posEpsilon = posEpsilon;
	}
}

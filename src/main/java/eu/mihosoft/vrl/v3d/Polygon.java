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

import java.io.Serializable;
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
public final class Polygon implements Serializable {

	private static final long serialVersionUID = 2090322224114633535L;
	/** Polygon vertices. */
	private ArrayList<Vertex> vertices;
	/**
	 * Shared property (can be used for shared color etc.).
	 */
	private PropertyStorage shared;
	/**
	 * Plane defined by this polygon.
	 *
	 * Note: uses first three vertices to define the plane.
	 */
	public Plane plane = null;
	private boolean isHole = false;
	private double r = CSG.getDefaultColor().getRed();
	private double g = CSG.getDefaultColor().getGreen();
	private double b = CSG.getDefaultColor().getBlue();
	private double o = CSG.getDefaultColor().getOpacity();
	private boolean valid = true;
	private boolean degenerate = false;

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
	 */
	public static List<Polygon> fromConcavePoints(Vector3d... points) throws ColinearPointsException {
		Polygon p = fromPoints(points);

		return PolygonUtil.triangulatePolygon(p);
	}

	/**
	 * Decomposes the specified concave polygon into convex polygons.
	 *
	 * @param points the points that define the polygon
	 * @return the decomposed concave polygon (list of convex polygons)
	 */
	public static List<Polygon> fromConcavePoints(List<Vector3d> points)throws ColinearPointsException  {
		Polygon p = fromPoints(points);

		return PolygonUtil.triangulatePolygon(p);
	}

	/**
	 * Constructor. Creates a new polygon that consists of the specified vertices.
	 *
	 * Note: the vertices used to initialize a polygon must be coplanar and form a
	 * convex loop.
	 *
	 * @param vertices polygon vertices
	 * @param shared   shared property
	 */
	public Polygon(List<Vertex> vertices, PropertyStorage shared, boolean allowDegenerate, Plane p) throws ColinearPointsException {
		this.setVertices(pruneDuplicatePoints(vertices));
		this.shared = shared;
		if (p != null)
			setPlane(p.clone());

		validateAndInit(allowDegenerate);
	}

	/**
	 * Constructor. Creates a new polygon that consists of the specified vertices.
	 *
	 * Note: the vertices used to initialize a polygon must be coplanar and form a
	 * convex loop.
	 *
	 * @param vertices polygon vertices
	 * @param shared   shared property
	 */
	public Polygon(List<Vertex> vertices, PropertyStorage shared) throws ColinearPointsException {
		this(vertices, shared, true, null);
	}

	/**
	 * Constructor. Creates a new polygon that consists of the specified vertices.
	 *
	 * Note: the vertices used to initialize a polygon must be coplanar and form a
	 * convex loop.
	 *
	 * @param vertices polygon vertices
	 */
	public Polygon(List<Vertex> vertices) throws ColinearPointsException {
		this(vertices, new PropertyStorage(), true, null);
	}

	public static ArrayList<Vertex> pruneDuplicatePoints(List<Vertex> incoming) {
		// return incoming;
		ArrayList<Vertex> newPoints = new ArrayList<Vertex>();
		for (int i = 0; i < incoming.size(); i++) {
			Vertex v = incoming.get(i);
			for(Vertex vt:newPoints) {
				if(vt.pos.test(v.pos)) {
					v=null;
					break;
				}
			}
			if(v!=null)
				newPoints.add(v);
		}
		try {
			return newPoints;
		} catch (java.lang.IndexOutOfBoundsException ex) {
			return null;
		}
	}

	private void validateAndInit(boolean fixInversions) throws ColinearPointsException {
		vertices = pruneDuplicatePoints(vertices);
		Plane p = Plane.createFromPoints(getVertices());
		if (getPlane() == null) {
			setPlane(p);
		}

		
		if(fixInversions) {
			Vector3d minus = getPlane().getNormal().minus(p.getNormal());
			double magnitude = minus.magnitude();
			if (Math.abs( magnitude)>2-(Plane.getEPSILON()*2) ) {
				Collections.reverse(getVertices());
			}
		}
		if (!getPlane().checkNormal(vertices)) {
			//setPlane(p);
			throw new ColinearPointsException("Failed! the normal provided mismatched to calculated normal");
		}
		if (Vector3d.ZERO.equals(getPlane().getNormal())) {
			valid = false;
			throw new ColinearPointsException(
					"Normal is zero! Probably, duplicate points have been specified!\n\n" + toStlString());
		}

		if (getVertices().size() < 3) {
			throw new ColinearPointsException("Invalid polygon: at least 3 vertices expected, got: " + getVertices().size());
		}

		if( !areAllPointsCollinear())
			return;
		new ColinearPointsException("This polygon is colinear");
		
	}

	public void rotatePoints() {
		Vertex b = getVertices().remove(0);
		getVertices().add(b);
	}

	/**
	 * Constructor. Creates a new polygon that consists of the specified vertices.
	 *
	 * Note: the vertices used to initialize a polygon must be coplanar and form a
	 * convex loop.
	 *
	 * @param vertices polygon vertices
	 *
	 */
	public Polygon(Vertex... vertices) throws ColinearPointsException {
		this(Arrays.asList(vertices));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Polygon clone() {
		List<Vertex> newVertices = new ArrayList<>();
		this.getVertices().forEach((vertex) -> {
			newVertices.add(vertex.clone());
		});
		// TODO figure out why this isnt working
		try {
			//return new Polygon(newVertices, getStorage(), true, plane.clone()).setColor(getColor());
			return new Polygon(newVertices, getStorage(),false,null).setColor(getColor());
		}catch(Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Flips this polygon.
	 *
	 * @return this polygon
	 */
	public Polygon flip() {

		Collections.reverse(getVertices());
		plane.flip();
//		try {
//			plane=Plane.createFromPoints(vertices);
//		} catch (ColinearPointsException e) {
//			plane.flip();
//		}
		if (!getPlane().checkNormal(vertices)) {
	//		getPlane().checkNormal(vertices);
			new RuntimeException("Failed! the normal provided mismatched to calculated normal").printStackTrace();	
		}
		
		return this;
	}

	/**
	 * Returns a flipped copy of this polygon.
	 *
	 * Note: this polygon is not modified.
	 *
	 * @return a flipped copy of this polygon
	 */
	public Polygon flipped() {
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

		if (this.getVertices().size() == 3) {

			// TODO: improve the triangulation?
			//
			// STL requires triangular polygons.
			// If our polygon has more vertices, create
			// multiple triangles:
			String firstVertexStl = this.getVertices().get(0).toStlString();

			sb.append("  facet normal ").append(this.getPlane().getNormal().toStlString()).append("\n")
					.append("    outer loop\n").append("      ").append(firstVertexStl).append("\n").append("      ");
			this.getVertices().get(1).toStlString(sb).append("\n").append("      ");
			this.getVertices().get(2).toStlString(sb).append("\n").append("    endloop\n").append("  endfacet\n");

		} else {
			throw new RuntimeException("Polygon must be a triangle before STL can be made " + getVertices().size());
		}

		return sb;
	}

	/**
	 * Translates this polygon.
	 *
	 * @param v the vector that defines the translation
	 * @return this polygon
	 */
//    public Polygon translate(Vector3d v) {
//        getVertices().forEach((vertex) -> {
//            vertex.pos = vertex.pos.plus(v);
//        });
//
//        Vector3d a = this.getVertices().get(0).pos;
//        Vector3d b = this.getVertices().get(1).pos;
//        Vector3d c = this.getVertices().get(2).pos;
//
//        this.plane.setNormal(b.minus(a).cross(c.minus(a)));
//
//        return this;
//    }

//    /**
//     * Returns a translated copy of this polygon.
//     *
//     *  Note:  this polygon is not modified
//     *
//     * @param v the vector that defines the translation
//     *
//     * @return a translated copy of this polygon
//     */
//    public Polygon translated(Vector3d v) {
//        return clone().translate(v);
//    }

	/**
	 * Applies the specified transformation to this polygon.
	 *
	 * Note: if the applied transformation performs a mirror operation the vertex
	 * order of this polygon is reversed.
	 *
	 * @param transform the transformation to apply
	 *
	 * @return this polygon
	 * @throws ColinearPointsException 
	 */
	public Polygon transform(Transform transform) throws ColinearPointsException {

		this.getVertices().stream().forEach((v) -> {
			v.transform(transform);
		});

		Vector3d a = this.getVertices().get(0).pos;
		
		// Given how the relative locations of the points can change in a scale operations
		// it is nessissary to reacalculated the normal on operation
		this.plane=Plane.createFromPoints(getVertices());
//        
		if (transform.isMirror()) {
			// the transformation includes mirroring. flip polygon
			flip();
		}
//		if (!getPlane().checkNormal(vertices)) {
//			new RuntimeException("Failed! the normal provided mismatched to calculated normal").printStackTrace();
//			;
//		}
		return this;
	}

//    public Vector3d computeNormal(List<Vertex> vertices) {
//        Vector3d normal = new Vector3d(0, 0, 0);
//        int n = vertices.size();
//
//        for (int i = 0; i < n; i++) {
//            Vector3d current = vertices.get(i).pos;
//            Vector3d next = vertices.get((i + 1) % n).pos;
//            
//            normal.x += (current.y - next.y) * (current.z + next.z);
//            normal.y += (current.z - next.z) * (current.x + next.x);
//            normal.z += (current.x - next.x) * (current.y + next.y);
//        }
//
//        return normal.normalized();
//    }
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
	 * @throws ColinearPointsException 
	 */
	public Polygon transformed(Transform transform) throws ColinearPointsException {
		return clone().transform(transform);
	}

	/**
	 * Creates a polygon from the specified point list.
	 *
	 * @param points the points that define the polygon
	 * @param shared shared property storage
	 * @return a polygon defined by the specified point list
	 */
	public static Polygon fromPoints(List<Vector3d> points, PropertyStorage shared) throws ColinearPointsException {
		return fromPoints(points, shared, null, true);
	}

	/**
	 * Creates a polygon from the specified point list.
	 *
	 * @param points the points that define the polygon
	 * @return a polygon defined by the specified point list
	 */
	public static Polygon fromPoints(List<Vector3d> points)throws ColinearPointsException  {
		return fromPoints(points, new PropertyStorage(), null, true);
	}

	/**
	 * Creates a polygon from the specified points.
	 *
	 * @param points the points that define the polygon
	 * @return a polygon defined by the specified point list
	 */
	public static Polygon fromPoints(Vector3d... points)throws ColinearPointsException  {
		return fromPoints(Arrays.asList(points), new PropertyStorage(), null, true);
	}

	public static Polygon fromPointsAllowDegenerate(List<Vector3d> vertices2) throws ColinearPointsException {
		return fromPoints(vertices2, new PropertyStorage(), null, true);
	}

	/**
	 * Creates a polygon from the specified point list.
	 *
	 * @param points the points that define the polygon
	 * @param shared the shared
	 * @param plane  may be null
	 * @return a polygon defined by the specified point list
	 */
	public static Polygon fromPoints(List<Vector3d> points, PropertyStorage shared, Plane plane,
			boolean allowDegenerate)throws ColinearPointsException  {
		List<Vertex> vertices = new ArrayList<>();
		for (Vector3d p : points) {
			Vector3d vec = p.clone();
			Vertex vertex = new Vertex(vec);
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

		for (int i = 0; i < getVertices().size(); i++) {

			Vertex vert = getVertices().get(i);

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
		double x2 = getVertices().get(getVertices().size() - 1).pos.x;
		double y2 = getVertices().get(getVertices().size() - 1).pos.y;
		double x1, y1;
		for (int i = 0; i < getVertices().size(); x2 = x1, y2 = y1, ++i) {
			x1 = getVertices().get(i).pos.x;
			y1 = getVertices().get(i).pos.y;
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

		for (Vertex v : p.getVertices()) {
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
		for (Vertex v : getVertices()) {
			p.add(v.pos.clone());
		}
		return p;
	}

	/**
	 * Movey.
	 *
	 * @param howFarToMove the how far to move
	 * @return the csg
	 * @throws ColinearPointsException 
	 */
	// Helper/wrapper functions for movement
	public Polygon movey(Number howFarToMove) throws ColinearPointsException {
		return this.transformed(Transform.unity().translateY(howFarToMove.doubleValue()));
	}

	/**
	 * Movez.
	 *
	 * @param howFarToMove the how far to move
	 * @return the csg
	 * @throws ColinearPointsException 
	 */
	public Polygon movez(Number howFarToMove) throws ColinearPointsException {
		return this.transformed(Transform.unity().translateZ(howFarToMove.doubleValue()));
	}

	/**
	 * Movex.
	 *
	 * @param howFarToMove the how far to move
	 * @return the csg
	 * @throws ColinearPointsException 
	 */
	public Polygon movex(Number howFarToMove) throws ColinearPointsException {
		return this.transformed(Transform.unity().translateX(howFarToMove.doubleValue()));
	}

	/**
	 * Rotz.
	 *
	 * @param degreesToRotate the degrees to rotate
	 * @return the csg
	 * @throws ColinearPointsException 
	 */
	// Rotation function, rotates the object
	public Polygon rotz(Number degreesToRotate) throws ColinearPointsException {
		return this.transformed(new Transform().rotZ(degreesToRotate.doubleValue()));
	}

	/**
	 * Roty.
	 *
	 * @param degreesToRotate the degrees to rotate
	 * @return the csg
	 * @throws ColinearPointsException 
	 */
	public Polygon roty(Number degreesToRotate) throws ColinearPointsException {
		return this.transformed(new Transform().rotY(degreesToRotate.doubleValue()));
	}

	/**
	 * Rotx.
	 *
	 * @param degreesToRotate the degrees to rotate
	 * @return the csg
	 * @throws ColinearPointsException 
	 */
	public Polygon rotx(Number degreesToRotate) throws ColinearPointsException {
		return this.transformed(new Transform().rotX(degreesToRotate.doubleValue()));
	}

	/**
	 * Scalez.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 * @throws ColinearPointsException 
	 */
	// Scale function, scales the object
	public Polygon scalez(Number scaleValue) throws ColinearPointsException {
		return this.transformed(new Transform().scaleZ(scaleValue.doubleValue()));
	}

	/**
	 * Scaley.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 * @throws ColinearPointsException 
	 */
	public Polygon scaley(Number scaleValue) throws ColinearPointsException {
		return this.transformed(new Transform().scaleY(scaleValue.doubleValue()));
	}

	/**
	 * Scalex.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 * @throws ColinearPointsException 
	 */
	public Polygon scalex(Number scaleValue) throws ColinearPointsException {
		return this.transformed(new Transform().scaleX(scaleValue.doubleValue()));
	}

	/**
	 * Scale.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 * @throws ColinearPointsException 
	 */
	public Polygon scale(Number scaleValue) throws ColinearPointsException {
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

	public void setDegenerate(boolean degenerate) {
		this.degenerate = degenerate;
	}

	public boolean isDegenerate() {

		return degenerate;
	}

	public ArrayList<Vertex> getDegeneratePoints() {
		ArrayList<Vertex> back = new ArrayList<Vertex>();
		if (!isDegenerate())
			return back;
		Edge longEdge = getLongEdge();
		for (int i = 0; i < getVertices().size(); i++) {
			Vertex vertex = getVertices().get(i);
			if (vertex != longEdge.getP1() && vertex != longEdge.getP2()) {
				back.add(vertex);
			}
		}
		if (back.size() == 0)
			throw new RuntimeException("Failed to find the degenerate point in the polygon");
		return back;
	}

	public Edge getLongEdge() {
		if (!isDegenerate())
			return null;
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
		for (int i = 0; i < getVertices().size(); i++) {
			int i1 = i;
			int i2 = i + 1;
			if (i2 == getVertices().size()) {
				i2 = 0;
			}
			e.add(new Edge(getVertices().get(i1), getVertices().get(i2)));
		}
		return e;
	}

	public Polygon setColor(Color color) {
		if (color != null) {
			r = color.getRed();
			g = color.getGreen();
			b = color.getBlue();
			o = color.getOpacity();
		} else {
			setColor(CSG.getDefaultColor());
		}
		return this;
	}

	public Color getColor() {
		return new Color(r, g, b, o);
	}

	@Override
	public String toString() {
		String ret = "# points=" + getVertices().size() + " normal=" + getPlane().getNormal().toStlString() + " [ ";
		for (Vertex v : getVertices()) {
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

	/**
	 * @return the vertices
	 */
	public ArrayList<Vertex> getVertices() {
		return vertices;
	}

	public Polygon add(int index, Vertex v) {
//		for(Vertex vr:vertices)
//			if(vr.pos.test(v.pos, Plane.getEPSILON()))
//				return this;
		getVertices().add(index, v);

		return this;
	}

	private boolean areAllPointsCollinear() {
		// If we have 2 or fewer points, they're always collinear
		if (getVertices().size() <= 2) {
			return true;
		}

		// Get first two points to establish a direction vector
		Vertex p1 = getVertices().get(0);
		Vertex p2 = getVertices().get(1);

		// Calculate the direction vector between first two points
		Vector3d direction = p1.pos.minus(p2.pos);
		// Normalize the direction vector
		double length = direction.length();
		double ep = Plane.getEPSILON();
		if (length < ep) { // If points are effectively identical
			return false;
		}
		direction.normalize();

		// Check each subsequent point
		for (int i = 2; i < getVertices().size(); i++) {
			Vertex p = getVertices().get(i);

			// Calculate cross product
			Vector3d cross = direction.cross(p1.pos.minus(p.pos));
			// Calculate magnitude of cross product
			double magnitude = Math.abs(cross.length());

			// If magnitude is not close to zero, points are not collinear
			if (magnitude > ep) {
				return false;
			}
		}

		return true;
	}

	public Plane getPlane() {
		return plane;
	}

	public void setPlane(Plane plane) {
		if (plane == null)
			throw new RuntimeException("Plane can not be null!");
		this.plane = plane;
	}

	public int size() {

		return getVertices().size();
	}

	public void setVertices(ArrayList<Vertex> vertices) throws ColinearPointsException {
		plane = Plane.createFromPoints(vertices);
		this.vertices = vertices;
	}
}

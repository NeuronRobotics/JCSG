/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.mihosoft.vrl.v3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;

/**
 * The Class Edge.
 *
 * @author miho
 */
public class Edge {

	/** The p1. */
	private Vertex p1;

	/** The p2. */
	private Vertex p2;

	/** The direction. */
	private final Vector3d direction;

	/**
	 * Instantiates a new edge.
	 *
	 * @param p1 the p1
	 * @param p2 the p2
	 */
	public Edge(Vertex p1, Vertex p2) throws CoincidentPoint{
		this.setP1(p1);
		this.setP2(p2);

		Vector3d minus = p2.pos.minus(p1.pos);
		if(minus.magnitude()<Plane.getEPSILON())
			throw new CoincidentPoint();
			
		direction = minus.normalized();
	}

	/**
	 * Gets the p1.
	 *
	 * @return the p1
	 */
	public Vertex getP1() {
		return p1;
	}

//    /**
//     * @param p1 the p1 to set
//     */
//    public void setP1(Vertex p1) {
//        this.p1 = p1;
//    }
	/**
	 * Gets the p2.
	 *
	 * @return the p2
	 */
	public Vertex getP2() {
		return p2;
	}

//    /**
//     * @param p2 the p2 to set
//     */
//    public void setP2(Vertex p2) {
//        this.p2 = p2;
	/**
	 * From polygon.
	 *
	 * @param poly the poly
	 * @return the list
	 */
//    }
	public static List<Edge> fromPolygon(Polygon poly) {
		List<Edge> result = new ArrayList<>();

		for (int i = 0; i < poly.getVertices().size(); i++) {
			Edge e;
			try {
				e = new Edge(poly.getVertices().get(i), poly.getVertices().get((i + 1) % poly.getVertices().size()));
				result.add(e);
			} catch (CoincidentPoint ex) {
				// TODO Auto-generated catch block
				ex.printStackTrace();
			}
		}

		return result;
	}

	/**
	 * To vertices.
	 *
	 * @param edges the edges
	 * @return the list
	 */
	public static List<Vertex> toVertices(List<Edge> edges) {
		return edges.stream().map(e -> e.p1).collect(Collectors.toList());
	}

	/**
	 * To points.
	 *
	 * @param edges the edges
	 * @return the list
	 */
	public static List<Vector3d> toPoints(List<Edge> edges) {
		return edges.stream().map(e -> e.p1.pos).collect(Collectors.toList());
	}

//	/**
//	 * To polygon.
//	 *
//	 * @param points the points
//	 * @param plane  the plane
//	 * @return the polygon
//	 */
//	public static Polygon toPolygon(List<Vector3d> points, Plane plane) throws ColinearPointsException{
//
////        List<Vector3d> points = edges.stream().().map(e -> e.p1.pos).
////                collect(Collectors.toList());
//		Polygon p = Polygon.fromPoints(points);
//
////        // we try to detect wrong orientation by comparing normals
////        if (p.plane.normal.angle(plane.normal) > 0.1) {
////            p.flip();
////        }
//		return p;
//	}

	/**
	 * To polygons.
	 *
	 * @param boundaryEdges the boundary edges
	 * @param plane         the plane
	 * @return the list
	 * @throws ColinearPointsException 
	 */
	public static List<Polygon> toPolygons(List<Edge> boundaryEdges, Plane plane) throws ColinearPointsException {

		List<Vector3d> boundaryPath = new ArrayList<>();

		boolean[] used = new boolean[boundaryEdges.size()];
		Edge edge = boundaryEdges.get(0);
		used[0] = true;
		while (true) {
			Edge finalEdge = edge;

			boundaryPath.add(finalEdge.p1.pos);

			int nextEdgeIndex = boundaryEdges
					.indexOf(boundaryEdges.stream().filter(e -> finalEdge.p2.equals(e.p1)).findFirst().get());

			if (used[nextEdgeIndex]) {
//                //com.neuronrobotics.sdk.common.Log.error("nexIndex: " + nextEdgeIndex);
				break;
			}
//            System.out.print("edge: " + edge.p2.pos);
			edge = boundaryEdges.get(nextEdgeIndex);
//            //com.neuronrobotics.sdk.common.Log.error("-> edge: " + edge.p1.pos);
			used[nextEdgeIndex] = true;
		}

		List<Polygon> result = new ArrayList<>();

		// com.neuronrobotics.sdk.common.Log.error("#bnd-path-length: " +
		// boundaryPath.size());

		try {
			result.addAll(Polygon.fromVector3d(boundaryPath, plane));
		} catch (ColinearPointsException | NonFlatPolygonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * The Class Node.
	 *
	 * @param <T> the generic type
	 */
	private static class Node<T> {

		/** The parent. */
		private Node parent;

		/** The children. */
		private final List<Node> children = new ArrayList<>();

		/** The index. */
		private final int index;

		/** The value. */
		private final T value;

		/** The is hole. */
		private boolean isHole;

		/**
		 * Instantiates a new node.
		 *
		 * @param index the index
		 * @param value the value
		 */
		public Node(int index, T value) {
			this.index = index;
			this.value = value;
		}

		/**
		 * Adds the child.
		 *
		 * @param index the index
		 * @param value the value
		 */
		public void addChild(int index, T value) {
			children.add(new Node(index, value));
		}

		/**
		 * Gets the children.
		 *
		 * @return the children
		 */
		public List<Node> getChildren() {
			return this.children;
		}

		/**
		 * Gets the parent.
		 *
		 * @return the parent
		 */
		public Node getParent() {
			return parent;
		}

		/**
		 * Gets the index.
		 *
		 * @return the index
		 */
		public int getIndex() {
			return index;
		}

		/**
		 * Gets the value.
		 *
		 * @return the value
		 */
		public T getValue() {
			return value;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			int hash = 7;
			hash = 67 * hash + this.index;
			return hash;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final Node<?> other = (Node<?>) obj;
			if (this.index != other.index) {
				return false;
			}
			return true;
		}

		/**
		 * Distance to root.
		 *
		 * @return the int
		 */
		public int distanceToRoot() {
			int dist = 0;

			Node pNode = getParent();

			while (pNode != null) {
				dist++;
				pNode = getParent();
			}

			return dist;
		}

		/**
		 * Checks if is checks if is hole.
		 *
		 * @return the isHole
		 */
		public boolean isIsHole() {
			return isHole;
		}

		/**
		 * Sets the checks if is hole.
		 *
		 * @param isHole the isHole to set
		 */
		public void setIsHole(boolean isHole) {
			this.isHole = isHole;
		}

	}

	/** The Constant KEY_POLYGON_HOLES. */
	public static final String KEY_POLYGON_HOLES = "jcsg:edge:polygon-holes";

	/**
	 * Boundary paths with holes.
	 *
	 * @param boundaryPaths the boundary paths
	 * @return the list
	 */
	public static List<Polygon> boundaryPathsWithHoles(List<Polygon> boundaryPaths) {

		List<Polygon> result = boundaryPaths.stream().map(p -> p.clone()).collect(Collectors.toList());

		List<List<Integer>> parents = new ArrayList<>();
		boolean[] isHole = new boolean[result.size()];

		for (int i = 0; i < result.size(); i++) {
			Polygon p1 = result.get(i);
			List<Integer> parentsOfI = new ArrayList<>();
			parents.add(parentsOfI);
			for (int j = 0; j < result.size(); j++) {
				Polygon p2 = result.get(j);
				if (i != j) {
					if (p2.contains(p1)) {
						parentsOfI.add(j);
					}
				}
			}
			isHole[i] = parentsOfI.size() % 2 != 0;
		}

		int[] parent = new int[result.size()];

		for (int i = 0; i < parent.length; i++) {
			parent[i] = -1;
		}

		for (int i = 0; i < parents.size(); i++) {
			List<Integer> par = parents.get(i);

			int max = 0;
			int maxIndex = 0;
			for (int pIndex : par) {

				int pSize = parents.get(pIndex).size();

				if (max < pSize) {
					max = pSize;
					maxIndex = pIndex;
				}
			}

			parent[i] = maxIndex;

			if (!isHole[maxIndex] && isHole[i]) {

				List<Polygon> holes;

				Optional<List<Polygon>> holesOpt = result.get(maxIndex).getStorage().getValue(KEY_POLYGON_HOLES);

				if (holesOpt.isPresent()) {
					holes = holesOpt.get();
				} else {
					holes = new ArrayList<>();
					result.get(maxIndex).getStorage().set(KEY_POLYGON_HOLES, holes);
				}

				holes.add(result.get(i));
			}
		}

		return result;
	}

	/**
	 * Returns a list of all boundary paths.
	 *
	 * @param boundaryEdges boundary edges (all paths must be closed)
	 * @return the list
	 */
	public static List<Polygon> boundaryPaths(List<Edge> boundaryEdges) throws ColinearPointsException{
		List<Polygon> result = new ArrayList<>();

		boolean[] used = new boolean[boundaryEdges.size()];
		int startIndex = 0;
		Edge edge = boundaryEdges.get(startIndex);
		used[startIndex] = true;

		startIndex = 1;

		while (startIndex > 0) {
			List<Vector3d> boundaryPath = new ArrayList<>();

			while (true) {
				Edge finalEdge = edge;

				boundaryPath.add(finalEdge.p1.pos);

//                System.out.print("edge: " + edge.p2.pos);

				Optional<Edge> nextEdgeResult = boundaryEdges.stream().filter(e -> finalEdge.p2.equals(e.p1))
						.findFirst();

				if (!nextEdgeResult.isPresent()) {
//                    //com.neuronrobotics.sdk.common.Log.error("ERROR: unclosed path:"
//                            + " no edge found with " + finalEdge.p2);
					break;
				}

				Edge nextEdge = nextEdgeResult.get();

				int nextEdgeIndex = boundaryEdges.indexOf(nextEdge);

				if (used[nextEdgeIndex]) {
					break;
				}

				edge = nextEdge;
//                //com.neuronrobotics.sdk.common.Log.error("-> edge: " + edge.p1.pos);
				used[nextEdgeIndex] = true;
			}

			if (boundaryPath.size() < 3) {
				break;
			}

			try {
				result.addAll(Polygon.fromVector3d(boundaryPath));
			} catch (ColinearPointsException | NonFlatPolygonException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			startIndex = nextUnused(used);

			if (startIndex > 0) {
				edge = boundaryEdges.get(startIndex);
				used[startIndex] = true;
			}

		}
//
//        //com.neuronrobotics.sdk.common.Log.error("paths: " + result.size());

		return result;
	}

	/**
	 * Returns the next unused index as specified in the given boolean array.
	 *
	 * @param usage the usage array
	 * @return the next unused index or a value &lt; 0 if all indices are used
	 */
	private static int nextUnused(boolean[] usage) {
		for (int i = 0; i < usage.length; i++) {
			if (usage[i] == false) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * _to polygons.
	 *
	 * @param boundaryEdges the boundary edges
	 * @param plane         the plane
	 * @return the list
	 * @throws ColinearPointsException 
	 */
	public static List<Polygon> _toPolygons(List<Edge> boundaryEdges, Plane plane) throws ColinearPointsException {

		List<Vector3d> boundaryPath = new ArrayList<>();

		boolean[] used = new boolean[boundaryEdges.size()];
		Edge edge = boundaryEdges.get(0);
		used[0] = true;
		while (true) {
			Edge finalEdge = edge;

			boundaryPath.add(finalEdge.p1.pos);

			int nextEdgeIndex = boundaryEdges
					.indexOf(boundaryEdges.stream().filter(e -> finalEdge.p2.equals(e.p1)).findFirst().get());

			if (used[nextEdgeIndex]) {
//                //com.neuronrobotics.sdk.common.Log.error("nexIndex: " + nextEdgeIndex);
				break;
			}
//            System.out.print("edge: " + edge.p2.pos);
			edge = boundaryEdges.get(nextEdgeIndex);
//            //com.neuronrobotics.sdk.common.Log.error("-> edge: " + edge.p1.pos);
			used[nextEdgeIndex] = true;
		}

		List<Polygon> result = new ArrayList<>();

		// com.neuronrobotics.sdk.common.Log.error("#bnd-path-length: " +
		// boundaryPath.size());

		try {
			result.addAll(Polygon.fromVector3d(boundaryPath, plane));
		} catch (ColinearPointsException | NonFlatPolygonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Determines whether the specified point is colinear
	 *
	 * @param p point to check
	 * @return <code>true</code> if the specified point lies on this line segment;
	 *         <code>false</code> otherwise
	 */
	public boolean colinear(Vector3d p) {
		return colinear(p, Plane.getEPSILON_Point());
	}
	
	public boolean colinear(Edge p) {
		return colinear(p.getP1().pos, Plane.getEPSILON_Point()) && colinear(p.getP2().pos, Plane.getEPSILON_Point());
	}
	
	
	

	public boolean colinear(Vector3d p, double TOL) {

		double x = p.x;
		double x1 = this.p1.pos.x;
		double x2 = this.p2.pos.x;

		double y = p.y;
		double y1 = this.p1.pos.y;
		double y2 = this.p2.pos.y;

		double z = p.z;
		double z1 = this.p1.pos.z;
		double z2 = this.p2.pos.z;

		double slopeSelfxy = (x1 - x2) / (y1 - y2);
		double slopeSelfxz = (x1 - x2) / (z1 - z2);
		double slopeSelfyz = (y1 - y2) / (z1 - z2);

		double slopeTestxy = (x - x2) / (y - y2);
		double slopeTestxz = (x - x2) / (z - z2);
		double slopeTestyz = (y - y2) / (z - z2);

		return Math.abs(slopeSelfxy - slopeTestxy) < TOL && Math.abs(slopeSelfxz - slopeTestxz) < TOL
				&& Math.abs(slopeSelfyz - slopeTestyz) < TOL;
	}

	/**
	 * Determines whether the specified point lies on this edge.
	 *
	 * @param p   point to check
	 * @param TOL tolerance
	 * @return <code>true</code> if the specified point lies on this line segment;
	 *         <code>false</code> otherwise
	 */
	public boolean contains(Vector3d p, double TOL) {
	    // Extract coordinates once for better performance
	    double pointX = p.x;
	    double pointY = p.y;
	    double pointZ = p.z;
	    
	    double edge1X = this.p1.pos.x;
	    double edge1Y = this.p1.pos.y;
	    double edge1Z = this.p1.pos.z;
	    
	    double edge2X = this.p2.pos.x;
	    double edge2Y = this.p2.pos.y;
	    double edge2Z = this.p2.pos.z;
	    
	    // Calculate vector components for edge and point-to-edge1 vectors
	    double vEdgeX = edge2X - edge1X;
	    double vEdgeY = edge2Y - edge1Y;
	    double vEdgeZ = edge2Z - edge1Z;
	    
	    double vToPointX = pointX - edge1X;
	    double vToPointY = pointY - edge1Y;
	    double vToPointZ = pointZ - edge1Z;
	    
	    // Calculate squared edge length (avoid sqrt until necessary)
	    double edgeLengthSq = vEdgeX * vEdgeX + vEdgeY * vEdgeY + vEdgeZ * vEdgeZ;
	    
	    // Handle degenerate edge case (zero or near-zero length)
	    if (edgeLengthSq < TOL * TOL) {
	        // For a zero-length edge, check if point is at the edge position
	        double distanceToPointSq = 
	            vToPointX * vToPointX + 
	            vToPointY * vToPointY + 
	            vToPointZ * vToPointZ;
	        
	        return distanceToPointSq < TOL * TOL;
	    }
	    
	    // Calculate cross product for collinearity check
	    double crossX = vToPointY * vEdgeZ - vToPointZ * vEdgeY;
	    double crossY = vToPointZ * vEdgeX - vToPointX * vEdgeZ;
	    double crossZ = vToPointX * vEdgeY - vToPointY * vEdgeX;
	    
	    // Calculate squared magnitude of cross product
	    double crossMagnitudeSq = crossX * crossX + crossY * crossY + crossZ * crossZ;
	    
	    // Normalize by the squared length of the edge to make tolerance scale-independent
	    double normalizedCrossMagnitudeSq = crossMagnitudeSq / edgeLengthSq;
	    
	    // Check collinearity - if not collinear, return false
	    if (normalizedCrossMagnitudeSq > TOL * TOL) {
	        return false;
	    }
	    
	    // Check if the point is within the bounds of the edge using dot product
	    double dotProduct = vEdgeX * vToPointX + vEdgeY * vToPointY + vEdgeZ * vToPointZ;
	    
	    // t represents how far along the edge the closest point to p is (projected position)
	    double t = dotProduct / edgeLengthSq;
	    
	    // If 0 ≤ t ≤ 1, the point is within the bounds of the edge
	    return t > 0 && t < 1;
	}

	/**
	 * Determines whether the specified point lies on tthis edge.
	 *
	 * @param p point to check
	 * @return <code>true</code> if the specified point lies on this line segment;
	 *         <code>false</code> otherwise
	 */
	public boolean contains(Vector3d p) {
		return contains(p, Plane.getEPSILON());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		int hash = 7;
		hash = 71 * hash + Objects.hashCode(this.p1);
		hash = 71 * hash + Objects.hashCode(this.p2);
		return hash;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Edge other = (Edge) obj;
		if (this.p1.pos.test(other.p1.pos, Plane.getEPSILON_Point())
				&& this.p2.pos.test(other.p2.pos, Plane.getEPSILON_Point())) {
			return true;
		}
		if (this.p1.pos.test(other.p2.pos, Plane.getEPSILON_Point())
				&& this.p2.pos.test(other.p1.pos, Plane.getEPSILON_Point())) {
			return true;
		}
		if (!(Objects.equals(this.p1, other.p1) || Objects.equals(this.p2, other.p1))) {
			return false;
		}
		if (!(Objects.equals(this.p2, other.p2) || Objects.equals(this.p1, other.p2))) {
			return false;
		}
		return true;
	}

	public boolean isThisPointOneOfMine(Vertex test, double epsilon) {
		return p1.pos.test(test.pos, epsilon) || p2.pos.test(test.pos, epsilon);
	}

	@Override
	public String toString() {
		return "[[" + p1.toString() + "]" + ", [" + p2.toString()+ "]]";
	}

	/**
	 * Gets the direction.
	 *
	 * @return the direction
	 */
	public Vector3d getDirection() {
		return direction;
	}

	/**
	 * Returns the the point of this edge that is closest to the specified edge.
	 *
	 * NOTE: returns an empty optional if the edges are parallel
	 *
	 * @param e the edge to check
	 * @return the the point of this edge that is closest to the specified edge
	 */
	public Optional<Vector3d> getClosestPoint(Edge e) {

		// algorithm from:
		// org.apache.commons.math3.geometry.euclidean.threed/Line.java.html
		Vector3d ourDir = getDirection();

		double cos = ourDir.dot(e.getDirection());
		double n = 1 - cos * cos;

		if (n < Plane.getEPSILON()) {
			// the lines are parallel
			return Optional.empty();
		}

		final Vector3d thisDelta = p2.pos.minus(p1.pos);
		final double norm2This = thisDelta.magnitudeSq();

		final Vector3d eDelta = e.p2.pos.minus(e.p1.pos);
		final double norm2E = eDelta.magnitudeSq();

		// line points above the origin
		Vector3d thisZero = p1.pos.plus(thisDelta.times(-p1.pos.dot(thisDelta) / norm2This));
		Vector3d eZero = e.p1.pos.plus(eDelta.times(-e.p1.pos.dot(eDelta) / norm2E));

		final Vector3d delta0 = eZero.minus(thisZero);
		final double a = delta0.dot(direction);
		final double b = delta0.dot(e.direction);

		Vector3d closestP = thisZero.plus(direction.times((a - b * cos) / n));

		if (!contains(closestP)) {
			if (closestP.minus(p1.pos).magnitudeSq() < closestP.minus(p2.pos).magnitudeSq()) {
				return Optional.of(p1.pos);
			} else {
				return Optional.of(p2.pos);
			}
		}

		return Optional.of(closestP);
	}

	/**
	 * Returns the intersection point between this edge and the specified edge.
	 *
	 * NOTE: returns an empty optional if the edges are parallel or if the
	 * intersection point is not inside the specified edge segment
	 *
	 * @param e edge to intersect
	 * @return the intersection point between this edge and the specified edge
	 */
	public Optional<Vector3d> getIntersection(Edge e) {
		Optional<Vector3d> closestPOpt = getClosestPoint(e);

		if (!closestPOpt.isPresent()) {
			// edges are parallel
			return Optional.empty();
		}

		Vector3d closestP = closestPOpt.get();

		if (e.contains(closestP, Plane.getEPSILON())) {
			return closestPOpt;
		} else {
			// intersection point outside of segment
			return Optional.empty();
		}
	}
	/**
	 * REturn the crossing point
	 * if they share points, then its not crossing
	 * if the do not touch, they are not crossing
	 * if the intersection is not contained withing the lines, they are not crossing
	 * @param e
	 * @return
	 */
	public Optional<Vector3d> getCrossingPoint(Edge e) {
		try {
			getCommonPoint(e);
			// if a common point exists, they are not crossed
			return Optional.empty();
		}catch(Exception ex) {
			//check the common point now
		}
		return getIntersection(e);
	}

	/**
	 * Boundary polygons.
	 *
	 * @param csg the csg
	 * @return the list
	 * @throws ColinearPointsException 
	 */
	public static List<Polygon> boundaryPolygons(CSG csg) throws ColinearPointsException {
		List<Polygon> result = new ArrayList<>();

		for (List<Polygon> polygonGroup : searchPlaneGroups(csg.getPolygons())) {
			result.addAll(boundaryPolygonsOfPlaneGroup(polygonGroup));
		}

		return result;
	}

	/**
	 * Boundary edges of plane group.
	 *
	 * @param planeGroup the plane group
	 * @return the list
	 */
	public static List<Edge> boundaryEdgesOfPlaneGroup(List<Polygon> planeGroup) {
		List<Edge> edges = new ArrayList<>();

		Stream<Polygon> pStream;

		if (planeGroup.size() > 200) {
			pStream = planeGroup.parallelStream();
		} else {
			pStream = planeGroup.stream();
		}

		pStream.map((p) -> Edge.fromPolygon(p)).forEach((pEdges) -> {
			edges.addAll(pEdges);
		});

		Stream<Edge> edgeStream;

		if (edges.size() > 200) {
			edgeStream = edges.parallelStream();
		} else {
			edgeStream = edges.stream();
		}

		// find potential boundary edges, i.e., edges that occur once (freq=1)
		List<Edge> potentialBoundaryEdges = new ArrayList<>();
		edgeStream.forEachOrdered((e) -> {
			int count = Collections.frequency(edges, e);
			if (count == 1) {
				potentialBoundaryEdges.add(e);
			}
		});

		// now find "false boundary" edges end remove them from the
		// boundary-edge-list
		//
		// thanks to Susanne Höllbacher for the idea :)
		Stream<Edge> bndEdgeStream;

		if (potentialBoundaryEdges.size() > 200) {
			bndEdgeStream = potentialBoundaryEdges.parallelStream();
		} else {
			bndEdgeStream = potentialBoundaryEdges.stream();
		}

		List<Edge> realBndEdges = bndEdgeStream
				.filter(be -> edges.stream().filter(e -> falseBoundaryEdgeSharedWithOtherEdge(be, e)!=null).count() == 0)
				.collect(Collectors.toList());

		//
//        //com.neuronrobotics.sdk.common.Log.error("#bnd-edges: " + realBndEdges.size()
//                + ",#edges: " + edges.size()
//                + ", #del-bnd-edges: " + (boundaryEdges.size() - realBndEdges.size()));
		return realBndEdges;
	}

	/**
	 * Boundary polygons of plane group.
	 *
	 * @param planeGroup the plane group
	 * @return the list
	 * @throws ColinearPointsException 
	 */
	private static List<Polygon> boundaryPolygonsOfPlaneGroup(List<Polygon> planeGroup) throws ColinearPointsException {

		List<Polygon> polygons = boundaryPathsWithHoles(boundaryPaths(boundaryEdgesOfPlaneGroup(planeGroup)));

		// com.neuronrobotics.sdk.common.Log.error("polygons: " + polygons.size());

		List<Polygon> result = new ArrayList<>(polygons.size());

		for (Polygon p : polygons) {

			Optional<List<Polygon>> holesOfPresult = p.getStorage().getValue(Edge.KEY_POLYGON_HOLES);

			if (!holesOfPresult.isPresent()) {
				result.add(p);
			} else {
				try {
					result.addAll(PolygonUtil.triangulatePolygon(p));
				} catch (ColinearPointsException | NonFlatPolygonException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		return result;
	}

	public static Vertex falseBoundaryEdgeSharedWithOtherEdge(Edge fbe, Edge e) {

		// we don't consider edges with shared end-points since we are only
		// interested in "false-boundary-edge"-cases
		boolean test1 = e.getP1().pos.test(fbe.getP1().pos);
		boolean test3 = e.getP1().pos.test(fbe.getP2().pos);
		boolean sharedEndPointsp1 = test1 || test3;
				
		boolean test = e.getP2().pos.test(fbe.getP1().pos);
		boolean test2 = e.getP2().pos.test(fbe.getP2().pos);
		boolean sharedP2= test || test2;

		boolean containsP2 = fbe.contains(e.getP2().pos);
		boolean containsP1 = fbe.contains(e.getP1().pos);

		if(sharedEndPointsp1 && sharedP2) {
			//System.out.println("Edge Contains point!");
		}
		if ((sharedP2) && containsP1) {
			return e.getP2();
		}
		if ((sharedEndPointsp1) && containsP2) {
			return e.getP1();
		}
		return null;
	}
//
//	/** Distance from point r to the infinite line through a → b */
//	private static double distancePointToLine(Vector3d r, Vector3d a, Vector3d b) {
//		Vector3d ab = b.minus(a);
//		Vector3d ar = r.minus(a);
//		Vector3d cross = ab.cross(ar);
//	    return cross.length() / ab.length();
//	}


	/**
	 * Search plane groups.
	 *
	 * @param polygons the polygons
	 * @return the list
	 */
	private static List<List<Polygon>> searchPlaneGroups(List<Polygon> polygons) {
		List<List<Polygon>> planeGroups = new ArrayList<>();
		boolean[] used = new boolean[polygons.size()];
		// com.neuronrobotics.sdk.common.Log.error("#polys: " + polygons.size());
		for (int pOuterI = 0; pOuterI < polygons.size(); pOuterI++) {

			if (used[pOuterI]) {
				continue;
			}

			Polygon pOuter = polygons.get(pOuterI);

			List<Polygon> otherPolysInPlane = new ArrayList<>();

			otherPolysInPlane.add(pOuter);

			for (int pInnerI = 0; pInnerI < polygons.size(); pInnerI++) {

				Polygon pInner = polygons.get(pInnerI);

				if (pOuter.equals(pInner)) {
					continue;
				}

				Vector3d nOuter = pOuter.getPlane().getNormal();
				Vector3d nInner = pInner.getPlane().getNormal();

				double angle = nOuter.angle(nInner);

//                //com.neuronrobotics.sdk.common.Log.error("angle: " + angle + " between " + pOuterI+" -> " + pInnerI);
				if (angle < 0.01 /* && abs(pOuter.plane.dist - pInner.plane.dist) < 0.1 */) {
					otherPolysInPlane.add(pInner);
					used[pInnerI] = true;
					// com.neuronrobotics.sdk.common.Log.error("used: " + pOuterI + " -> " +
					// pInnerI);
				}
			}

			if (!otherPolysInPlane.isEmpty()) {
				planeGroups.add(otherPolysInPlane);
			}
		}
		return planeGroups;
	}

	public double length() {
		// Auto-generated method stub
		return p1.pos.minus(p2.pos).length();
	}

	public void setP1(Vertex p1) {
		this.p1 = p1;
	}

	public void setP2(Vertex p2) {
		this.p2 = p2;
	}
	
	/**
	 * 
	 * @param test2
	 * @return the point the edges have in common
	 * @throws Exception 
	 */
	public Vertex getCommonPoint(Edge test2) throws Exception {
		if(p1.pos.test(test2.getP1().pos) || p1.pos.test(test2.getP2().pos))
			return p1;
		if(p2.pos.test(test2.getP1().pos) || p2.pos.test(test2.getP2().pos))
			return p2;
		throw new Exception("Threse edges do not touch");
	}
	/**
	 * 
	 * @param test2
	 * @return the point the edges have in common
	 * @throws Exception 
	 */
	public Vertex getOppisitePoint(Vertex test) throws Exception {
		if(p1.pos.test(test.pos))
			return p2;
		if(p2.pos.test(test.pos))
			return p1;
		throw new Exception("Threse edges do not touch");
	}
}

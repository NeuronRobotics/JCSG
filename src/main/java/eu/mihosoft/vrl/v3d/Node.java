/**
 * Node.java
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;

//  Auto-generated Javadoc
/**
 * Holds a node in a BSP tree. A BSP tree is built from a collection of polygons
 * by picking a polygon to split along. That polygon (and all other coplanar
 * polygons) are added directly to that node and the other polygons are added to
 * the front and/or back subtrees. This is not a leafy BSP tree since there is
 * no distinction between internal and leaf nodes.
 */
public final class Node {

	/**
	 * Polygons.
	 */
	private ArrayList<Polygon> polygons;
	/**
	 * Plane used for BSP.
	 */
	private Plane plane;
	/**
	 * Polygons in front of the plane.
	 */
	private Node front;
	/**
	 * Polygons in back of the plane.
	 */
	private Node back;

	private long maxDepth = -1;

	/**
	 * Constructor.
	 *
	 * Creates a BSP node consisting of the specified polygons.
	 *
	 * @param polygons polygons
	 * @throws Exception
	 */
	public Node(ArrayList<Polygon> polygons) throws Exception {
		this.polygons = new ArrayList<>();
		if (polygons != null) {
			this.build(polygons);
		}
	}

//	/**
//	 * Constructor. Creates a node without polygons.
//	 */
	private Node() throws Exception {
		this(null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Node clone() {
		Node node;
		try {
			node = new Node();
			node.setPlane(this.getPlane() == null ? null : this.getPlane().clone());
			node.front = this.front == null ? null : this.front.clone();
			node.back = this.back == null ? null : this.back.clone();
//	        node.polygons = new ArrayList<>();
//	        polygons.parallelStream().forEach((Polygon p) -> {
//	            node.polygons.add(p.clone());
//	        });

			Stream<Polygon> polygonStream;

			if (polygons.size() > 200) {
				polygonStream = polygons.parallelStream();
			} else
				polygonStream = polygons.stream();

			node.polygons = polygonStream.map(p -> p.clone()).collect(Collectors.toCollection(ArrayList::new));

			return node;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		throw new RuntimeException("Failed to clone");
	}

	/**
	 * Converts solid space to empty space and vice verca.
	 */
	public void invert() {

		Stream<Polygon> polygonStream;

		if (polygons.size() > 200) {
			polygonStream = polygons.parallelStream();
		} else
			polygonStream = polygons.stream();

		polygonStream.forEach((polygon) -> {
			polygon.flip();
		});

		if (this.getPlane() == null && !polygons.isEmpty()) {
			this.setPlane(polygons.get(0).getPlane().clone());
		} else if (this.getPlane() == null && polygons.isEmpty()) {

			// com.neuronrobotics.sdk.common.Log.error("Please fix me! I don't know what to
			// do?");
			throw new RuntimeException("Please fix me! Plane = " + plane + " and polygons are empty");
			// return;
		}

		this.getPlane().flip();

		if (this.front != null) {
			this.front.invert();
		}
		if (this.back != null) {
			this.back.invert();
		}
		Node temp = this.front;
		this.front = this.back;
		this.back = temp;
	}

	/**
	 * Recursively removes all polygons in the {@link polygons} list that are
	 * contained within this BSP tree.
	 *
	 * Note: polygons are splitted if necessary.
	 *
	 * @param polygons the polygons to clip
	 *
	 * @return the cliped list of polygons
	 * @throws Exception
	 */
	private ArrayList<Polygon> clipPolygons(ArrayList<Polygon> polygons) throws Exception {

		if (this.getPlane() == null) {
			throw new RuntimeException("Plane can not be null");
		}

		ArrayList<Polygon> frontP = new ArrayList<>();
		ArrayList<Polygon> backP = new ArrayList<>();

		splitPolygon(polygons, frontP, backP, frontP, backP);

		if (this.front != null) {
			frontP = this.front.clipPolygons(frontP);
		}
		if (this.back != null) {
			backP = this.back.clipPolygons(backP);
		} else {
			backP = new ArrayList<>(0);
		}

		frontP.addAll(backP);
		return frontP;
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
	 * @throws Exception
	 */
	public void splitPolygon(ArrayList<Polygon> polygons, List<Polygon> coplanarFront, List<Polygon> coplanarBack,
			List<Polygon> front, List<Polygon> back) throws Exception {
		int numberOfPointsTmp = 0;
		int polygonNumber = polygons.size();
		int max = 0;
		int ExtraSpace = 10;
		ArrayList<Vertex> orderedPoints = new ArrayList<Vertex>();
		int[] polygonStartIndex = new int[polygonNumber];
		int[] polygonSize = new int[polygonNumber];
		int[] newPointStartIndex = new int[polygonNumber];
		float[] polygonNormalX = new float[polygonNumber];
		float[] polygonNormalY = new float[polygonNumber];
		float[] polygonNormalZ = new float[polygonNumber];
		float[] polygonNormalDistance = new float[polygonNumber];

		for (int k = 0; k < polygonNumber; k++) {
			Polygon polygon = polygons.get(k);
			List<Vertex> vertices = polygon.getVertices();
			int size = vertices.size();
			if (size > max)
				max = size;
			numberOfPointsTmp += size;
			polygonStartIndex[k] = orderedPoints.size();
			polygonSize[k] = size;
			polygonNormalX[k] = (float) polygon.getPlane().getNormal().x;
			polygonNormalY[k] = (float) polygon.getPlane().getNormal().y;
			polygonNormalZ[k] = (float) polygon.getPlane().getNormal().z;
			polygonNormalDistance[k] = (float) polygon.getPlane().getDist();
			orderedPoints.addAll(vertices);
		}
		int[] coplanarFrontStartIndex = new int[polygonNumber];
		int[] coplanarFrontSize = new int[polygonNumber];

		int[] coplanarBackStartIndex = new int[polygonNumber];
		int[] coplanarBackSize = new int[polygonNumber];

		int[] frontStartIndex = new int[polygonNumber];
		int[] frontSize = new int[polygonNumber];

		int[] backStartIndex = new int[polygonNumber];
		int[] backSize = new int[polygonNumber];
		int pointsNumber = numberOfPointsTmp + 1 + ((ExtraSpace) * (polygonNumber + 2));
		for (int k = 0; k < polygonNumber; k++) {
			newPointStartIndex[k] = numberOfPointsTmp + (k * ExtraSpace);
			coplanarFrontStartIndex[k] = -1;
			coplanarBackStartIndex[k] = -1;
			frontStartIndex[k] = -1;
			backStartIndex[k] = -1;
		}
		int pointsEmptyIndex = 0;
		int[] types = new int[max];
		float[] polygonPointX = new float[pointsNumber];
		float[] polygonPointY = new float[pointsNumber];
		float[] polygonPointZ = new float[pointsNumber];
		for (; pointsEmptyIndex < orderedPoints.size(); pointsEmptyIndex++) {
			Vertex vertex = orderedPoints.get(pointsEmptyIndex);
			polygonPointX[pointsEmptyIndex] = (float) vertex.getX();
			polygonPointY[pointsEmptyIndex] = (float) vertex.getY();
			polygonPointZ[pointsEmptyIndex] = (float) vertex.getZ();
		}
		float planeNormalX = (float) this.plane.getNormal().x;
		float planeNormalY = (float) this.plane.getNormal().y;
		float planeNormalZ = (float) this.plane.getNormal().z;
		float planeNormalDistance = (float) this.plane.getDist();
		float epsilon = (float) Plane.getEPSILON();
		final int COPLANAR = 0;
		final int FRONT = 1;
		final int BACK = 2;
		final int SPANNING = 3; // == some in the FRONT + some in the BACK
		boolean []memoryError = new boolean[] {false};

		final class PolygonListManager {
			final int[] mypolygonStartIndex;
			final int[] mypolygonSize;
			int[] space = new int[polygonNumber];

			PolygonListManager(int[] polygonStartIndex, int[] polygonSize) {
				this.mypolygonStartIndex = polygonStartIndex;
				this.mypolygonSize = polygonSize;
				for (int i = 0; i < polygonNumber; i++) {
					space[i] = ExtraSpace;
				}
			}

			int size(int polygonIndex) {
				return mypolygonSize[polygonIndex];
			}

			int addPolygon(int polygonIndex, int size) {
				int w = polygonIndex;
				mypolygonStartIndex[w] = newPointStartIndex[w];
				newPointStartIndex[w] += size;
				mypolygonSize[w] = size;
				space[w] -= size;
				return space[w];
			}

			void copy(int polygonIndex) {
				mypolygonStartIndex[polygonIndex] = polygonStartIndex[polygonIndex];
				mypolygonSize[polygonIndex] = polygonSize[polygonIndex];
			}

			void clear(int polygonIndex) {
				mypolygonSize[polygonIndex] = 0;
			}

			void incrementSize(int polygonIndex) {
				mypolygonSize[polygonIndex]++;
				int ni = mypolygonSize[polygonIndex];
				if(polygonIndex==mypolygonSize.length-1) {
					if(ni>=pointsNumber) {
						memoryError[0]=true;
					}
				}else {
					if (ni+mypolygonStartIndex[polygonIndex] == mypolygonStartIndex[polygonIndex+1] ) {
						memoryError[0]=true;
					}
				}
			}

			float x(int polygonIndex, int pointIndex) {
				return polygonPointX[getPointIndex(polygonIndex, pointIndex)];
			}

			float y(int polygonIndex, int pointIndex) {
				return polygonPointY[getPointIndex(polygonIndex, pointIndex)];
			}

			float z(int polygonIndex, int pointIndex) {
				return polygonPointZ[getPointIndex(polygonIndex, pointIndex)];
			}

			int writeIncrementPoint(int polygonIndex, int source) {
				int pointInPolygon = mypolygonSize[polygonIndex];
				incrementSize(polygonIndex);
				float x = polygonPointX[source];
				float y = polygonPointY[source];
				float z = polygonPointZ[source];
				return writePoint(polygonIndex, pointInPolygon, x, y, z);
			}

			int interpolate(int polygonIndex, int vi, int vj, float t) {
				int pointInPolygon = mypolygonSize[polygonIndex];
				incrementSize(polygonIndex);
				if(memoryError[0])
					return -1;
				float xa = x(polygonIndex, vi);
				float ya = y(polygonIndex, vi);
				float za = z(polygonIndex, vi);
				float xb = x(polygonIndex, vj);
				float yb = y(polygonIndex, vj);
				float zb = z(polygonIndex, vj);

				float lerp_x = xa + ((xb - xa) * t);
				float lerp_y = ya + ((yb - ya) * t);
				float lerp_z = za + ((zb - za) * t);

				return writePoint(polygonIndex, pointInPolygon, lerp_x, lerp_y, lerp_z);
			}

			int writePoint(int polygonIndex, int pointInPolygon, float x, float y, float z) {
				int pointIndex = getPointIndex(polygonIndex, pointInPolygon);
				polygonPointX[pointIndex] = x;
				polygonPointY[pointIndex] = y;
				polygonPointZ[pointIndex] = z;
				return pointIndex;
			}

			private int getPointIndex(int polygonIndex, int point) {
				return mypolygonStartIndex[polygonIndex] + point;
			}

			float dot(float ax, float ay, float az, float bx, float by, float bz) {
				return ax * bx + ay * by + az * bz;
			}

			float polygonDotPoint(int polygonIndex, int pointIndex) {
				return dot(polygonNormalX[polygonIndex], polygonNormalY[polygonIndex], polygonNormalZ[polygonIndex],
						x(polygonIndex, pointIndex), y(polygonIndex, pointIndex), z(polygonIndex, pointIndex));
			}

			float planeDotPoint(int polygonIndex, int pointIndex) {
				return dot(planeNormalX, planeNormalY, planeNormalZ, x(polygonIndex, pointIndex),
						y(polygonIndex, pointIndex), z(polygonIndex, pointIndex));
			}

			float planeDotPointMinusPoint(int polygonIndex, int vj, int vi) {
				return dot(planeNormalX, planeNormalY, planeNormalZ, x(polygonIndex, vj) - x(polygonIndex, vi),
						y(polygonIndex, vj) - y(polygonIndex, vi), z(polygonIndex, vj) - z(polygonIndex, vi));
			}

			float planeDotPolygonNormal(int polygonIndex) {
				return dot(planeNormalX, planeNormalY, planeNormalZ, polygonNormalX[polygonIndex],
						polygonNormalY[polygonIndex], polygonNormalZ[polygonIndex]);
			}
		}

		PolygonListManager polygonManager = new PolygonListManager(polygonStartIndex, polygonSize);
		PolygonListManager coplanarFrontManager = new PolygonListManager(coplanarFrontStartIndex, coplanarFrontSize);
		PolygonListManager coplanarBackManager = new PolygonListManager(coplanarBackStartIndex, coplanarBackSize);
		PolygonListManager frontManager = new PolygonListManager(frontStartIndex, frontSize);
		PolygonListManager backManager = new PolygonListManager(backStartIndex, backSize);

		for (int polygonIndex = 0; polygonIndex < polygons.size(); polygonIndex++) {
			if (memoryError[0])
				break;
			Polygon polygon = polygons.get(polygonIndex);

			// search for the epsilon values of the incoming plane
			float negEpsilon = -epsilon;
			float posEpsilon = epsilon;
			float polygonNormalDistanceVal = polygonNormalDistance[polygonIndex];
			for (int i = 0; i < polygonManager.size(polygonIndex); i++) {
				// (float) (polygon.getPlane().getNormal().dot(polygon.getVertices().get(i).pos)
				float t = (polygonManager.polygonDotPoint(polygonIndex, i) - polygonNormalDistanceVal);
				if (t > posEpsilon) {
					// com.neuronrobotics.sdk.common.Log.error("Non flat polygon, increasing
					// positive epsilon "+t);
					posEpsilon = t + epsilon;
				}
				if (t < negEpsilon) {
					// com.neuronrobotics.sdk.common.Log.error("Non flat polygon, decreasing
					// negative epsilon "+t);
					negEpsilon = t - epsilon;
				}
			}
			int polygonType = COPLANAR;
			boolean somePointsInfront = false;
			boolean somePointsInBack = false;
			for (int i = 0; i < polygonManager.size(polygonIndex); i++) {

				float t = polygonManager.planeDotPoint(polygonIndex, i) - planeNormalDistance;

//				double tOld = this.getPlane().getNormal().dot(polygon.getVertices().get(i).pos)
//						- this.getPlane().getDist();
//				double delta = Math.abs(tOld-t);
//				if(delta>posEpsilon)
//					throw new RuntimeException("Algorithm fail!");
//				
//				
				int type = (t < negEpsilon) ? BACK : (t > posEpsilon) ? FRONT : COPLANAR;
				if (type == BACK)
					somePointsInBack = true;
				if (type == FRONT)
					somePointsInfront = true;
				types[i] = type;
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
				if (polygonManager.planeDotPolygonNormal(polygonIndex) > 0) {
					coplanarFrontManager.copy(polygonIndex);
				} else {
					coplanarBackManager.copy(polygonIndex);
				}
				break;
			case FRONT:
				frontManager.copy(polygonIndex);
				break;
			case BACK:
				backManager.copy(polygonIndex);
				break;
			case SPANNING:
//				List<Vertex> f = new ArrayList<>();
//				List<Vertex> b = new ArrayList<>();
				int size = polygonManager.size(polygonIndex);
				int retF = frontManager.addPolygon(polygonIndex, size - 1);
				if (retF < 0) {
					memoryError[0] = true;
					break;
				}
				int retB = backManager.addPolygon(polygonIndex, size - 1);
				if (retB < 0) {
					memoryError[0] = true;
					break;
				}
				frontManager.clear(polygonIndex);
				backManager.clear(polygonIndex);

				for (int i = 0; i < size; i++) {
					int j = (i + 1) % size;
					int ti = types[i];
					int tj = types[j];
//					Vertex vi = polygon.getVertices().get(i);
//					Vertex vj = polygon.getVertices().get(j);
					int viIndex = polygonManager.getPointIndex(polygonIndex, i);
					int vjIndex = polygonManager.getPointIndex(polygonIndex, j);
					if (ti != BACK) {
						frontManager.writeIncrementPoint(polygonIndex, viIndex);
						// f.add(vi);
					}
					if (ti != FRONT) {
						// b.add(ti != BACK ? vi.clone() : vi);
						backManager.writeIncrementPoint(polygonIndex, viIndex);
					}
					if ((ti | tj) == SPANNING) {
						// this.getPlane().getNormal().dot(vj.pos.minus(vi.pos))
						float dotMinus = polygonManager.planeDotPointMinusPoint(polygonIndex, vjIndex, viIndex);
						// this.getPlane().getNormal().dot(vi.pos)
						float t = (planeNormalDistance - polygonManager.planeDotPoint(polygonIndex, viIndex))
								/ dotMinus;
						// Vertex v = vi.interpolate(vj, t);
						int v = frontManager.interpolate(polygonIndex, i, j, t);
						if (memoryError[0])
							break;
						backManager.writeIncrementPoint(polygonIndex, v);
//						f.add(v);
//						b.add(v.clone());
					}
				}
//				add(front, f,polygon);
//				add(back, b,polygon);
				break;
			}
		} // outer for loop of all polygons
		if(memoryError[0])
			throw new RuntimeException("Memory error here!");

		// Collect the polygon data into the return structures
		for (int k = 0; k < polygonNumber; k++) {
			Polygon polygon = polygons.get(k);
			add(coplanarFront, k, coplanarFrontStartIndex, coplanarFrontSize, orderedPoints, polygonPointX,
					polygonPointY, polygonPointZ, polygon);
			add(coplanarBack, k, coplanarBackStartIndex, coplanarBackSize, orderedPoints, polygonPointX, polygonPointY,
					polygonPointZ, polygon);
			add(front, k, frontStartIndex, frontSize, orderedPoints, polygonPointX, polygonPointY, polygonPointZ,
					polygon);
			add(coplanarFront, k, coplanarFrontStartIndex, coplanarFrontSize, orderedPoints, polygonPointX,
					polygonPointY, polygonPointZ, polygon);

		}
	}

	private static void add(List<Polygon> l, int polygonIndex, int[] polygonStartIndex, int[] polygonSize,
			ArrayList<Vertex> orderedPoints, float[] polygonX, float[] polygonY, float[] polygonZ, Polygon polygon) {
		int polygonBase = polygonStartIndex[polygonIndex];
		int size = polygonSize[polygonIndex];
		if (polygonBase < 0)
			return;
		List<Vertex> f = new ArrayList<>();
		for (int i = polygonBase; i < polygonBase + size; i++) {
			if (i < orderedPoints.size()) {
				f.add(orderedPoints.get(i).clone());
			} else {
				float x = polygonX[i];
				float y = polygonY[i];
				float z = polygonZ[i];
				Vertex v = new Vertex(new Vector3d(x, y, z), polygon.plane.getNormal());
				f.add(v);
			}
		}

		add(l, f, polygon);
	}

	private static void add(List<Polygon> l, List<Vertex> f, Polygon polygon) {
		if (f.size() < 3)
			return;
		try {
			Polygon fpoly = new Polygon(f, polygon.getStorage(), true, new Plane(polygon.getPlane().getNormal(), f))
					.setColor(polygon.getColor());
			// test triangulation of new polygon before adding
			PolygonUtil.concaveToConvex(fpoly);
			l.add(fpoly);

		} catch (Exception ex) {
			// ex.printStackTrace();
			System.err.println("Pruning bad polygon Node::splitPolygon::add " + l);
		}
	}

	// Remove all polygons in this BSP tree that are inside the other BSP tree
	// `bsp`.
	/**
	 * Removes all polygons in this BSP tree that are inside the specified BSP tree
	 * ({@code bsp}).
	 *
	 * Note: polygons are splitted if necessary.
	 *
	 * @param bsp bsp that shall be used for clipping
	 * @throws Exception
	 */
	public void clipTo(Node bsp) throws Exception {
		this.polygons = bsp.clipPolygons(this.polygons);
		if (this.front != null) {
			this.front.clipTo(bsp);
		}
		if (this.back != null) {
			this.back.clipTo(bsp);
		}
	}

	/**
	 * Returns a list of all polygons in this BSP tree.
	 *
	 * @return a list of all polygons in this BSP tree
	 */
	public ArrayList<Polygon> allPolygons() {
		ArrayList<Polygon> localPolygons = new ArrayList<>(this.polygons);
		if (this.front != null) {
			localPolygons.addAll(this.front.allPolygons());
//            polygons = Utils.concat(polygons, this.front.allPolygons());
		}
		if (this.back != null) {
//            polygons = Utils.concat(polygons, this.back.allPolygons());
			localPolygons.addAll(this.back.allPolygons());
		}

		return localPolygons;
	}

	/**
	 * Build a BSP tree out of {@code polygons}. When called on an existing tree,
	 * the new polygons are filtered down to the bottom of the tree and become new
	 * nodes there. Each set of polygons is partitioned using the first polygon (no
	 * heuristic is used to pick a good split).
	 *
	 * @param polygons polygons used to build the BSP
	 * @throws Exception
	 */
	public final void build(ArrayList<Polygon> polygons) throws Exception {
		build(polygons, 0, polygons.size());
	}

	/**
	 * Build a BSP tree out of {@code polygons}. When called on an existing tree,
	 * the new polygons are filtered down to the bottom of the tree and become new
	 * nodes there. Each set of polygons is partitioned using the first polygon (no
	 * heuristic is used to pick a good split).
	 *
	 * @param polygons polygons used to build the BSP
	 * @throws Exception
	 */
	public final void build(ArrayList<Polygon> polygons, long depth, long maxDepth) throws Exception {
//		if (depth > maxDepth) {
//			throw new RuntimeException("Impossible Node depth " + depth + " with " + polygons.size() + " remaining max = "+maxDepth );
//		}
//		if (depth > 200) {
//			com.neuronrobotics.sdk.common.Log.error("Node depth " + depth + " with " + polygons.size() + " remaining ");
//			Plane.setUseDebugger(true);
//		} else {
//			Plane.setUseDebugger(false);
//		}

		if (polygons.isEmpty()) {

			return;
		}

		if (this.getPlane() == null) {
			this.setPlane(polygons.get(0).getPlane().clone());
		}
		// this.polygons.add(polygons.get(0));

		ArrayList<Polygon> frontP = new ArrayList<>();
		ArrayList<Polygon> backP = new ArrayList<>();

		// parellel version does not work here

		splitPolygon(polygons, this.polygons, this.polygons, frontP, backP);

		if (frontP.size() > 0) {
			if (this.front == null) {
				this.front = new Node();
			}
			this.front.build(frontP, depth + 1, maxDepth);
		}
		if (backP.size() > 0) {
			if (this.back == null) {
				this.back = new Node();
			}
			this.back.build(backP, depth + 1, maxDepth);
		}
	}

	public Plane getPlane() {
		return plane;
	}

	public void setPlane(Plane plane) {
		if (plane == null)
			throw new RuntimeException("Plane can not be null!");
		this.plane = plane;
	}
}

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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.aparapi.Kernel;
import com.aparapi.device.Device;
import com.aparapi.device.OpenCLDevice;
import com.aparapi.internal.kernel.KernelManager;

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
	private static final int LIMIT_FOR_GPU = 10000;
	private static double scale = 1;
	// Fixed point math configuration
	// IEEE 754 double precision constants
	private static final long EXPONENT_MASK_LONG = 0x7FF0000000000000L;
	// IEEE 754 double precision constants
	// IEEE 754 double precision constants
	private static final long SIGN_MASK = 0x8000000000000000L;
	private static final long EXPONENT_MASK = 0x7FF0000000000000L;
	private static final long MANTISSA_MASK = 0x000FFFFFFFFFFFFFL;
	private static final long IMPLICIT_ONE = 0x0010000000000000L;
	private static final int MANTISSA_BITS = 52;
	private static final int EXPONENT_BITS = 11;
	private static final int EXPONENT_BIAS = 1023;
	private static final int EXPONENT_MAX = 2047;

	// Special values
	private static final long POSITIVE_INFINITY = 0x7FF0000000000000L;
	private static final long NEGATIVE_INFINITY = 0xFFF0000000000000L;
	private static final long NAN = 0x7FF8000000000000L;
	private static final long POSITIVE_ZERO = 0x0000000000000000L;
	private static final long NEGATIVE_ZERO = 0x8000000000000000L;

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

	private static void add(List<Polygon> l, int polygonIndex, int[] polygonStartIndex, int[] polygonSize,
			ArrayList<Vertex> orderedPoints, double[] polygonPointX, double[] polygonPointY, double[] polygonPointZ,
			Polygon polygon) {
		int polygonBase = polygonStartIndex[polygonIndex];
		int size = polygonSize[polygonIndex];
		if (polygonBase < 0)
			return;
		List<Vertex> f = new ArrayList<>();
		for (int i = polygonBase; i < polygonBase + size; i++) {
			if (i < orderedPoints.size()) {
				f.add(orderedPoints.get(i).clone());
			} else {
				double x = (polygonPointX[i]);
				double y = (polygonPointY[i]);
				double z = (polygonPointZ[i]);
				Vertex v = new Vertex(new Vector3d(x / scale, y / scale, z / scale), polygon.plane.getNormal());
				addPoint(f, v);
			}
		}

		add(l, f, polygon);
	}

	private static boolean addPoint(List<Vertex> f, Vertex v) {
		if (f.size() > 0) {
			if (Math.abs(v.pos.distance(f.get(0).pos)) < 0.0001) {
				return false;
			}
			if (Math.abs(v.pos.distance(f.get(f.size() - 1).pos)) < 0.0001) {
				return false;
			}
		}
		return f.add(v);
	}

	private static void add(List<Polygon> l, List<Vertex> f, Polygon polygon) {
		if (f.size() < 3)
			return;
		Polygon fpoly = null;
		try {
			fpoly = new Polygon(f, polygon.getStorage(), true, new Plane(polygon.getPlane().getNormal(), f))
					.setColor(polygon.getColor());
			// test triangulation of new polygon before adding
			PolygonUtil.concaveToConvex(fpoly);
			l.add(fpoly);

		} catch (Exception ex) {
//			ex.printStackTrace();
			
			System.err.println("Pruning bad polygon Node::splitPolygon::add " + l.size() + "\n\t" + (fpoly.getPoints().size()<20? fpoly:"# points "+fpoly.getPoints().size()));
		}
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
		if (polygons.size() > LIMIT_FOR_GPU)
			splitPolygonGPU(polygons, coplanarFront, coplanarBack, front, back);
		else
			splitPolygonOriginal(polygons, coplanarFront, coplanarBack, front, back);

	}

	public void splitPolygonGPU(ArrayList<Polygon> polygons, List<Polygon> coplanarFront, List<Polygon> coplanarBack,
			List<Polygon> front, List<Polygon> back) throws Exception {

		// Main conversion of your original code
		int numberOfPointsTmp = 0;
		int polygonNumber = polygons.size();
		int max = 0;

		ArrayList<Vertex> orderedPoints = new ArrayList<Vertex>();
		int[] polygonStartIndex = new int[polygonNumber];
		int[] polygonSize = new int[polygonNumber];
		int[] newPointStartIndex = new int[polygonNumber];

		// Convert to fixed point arrays
		double[] normalPolygonX = new double[polygonNumber];
		double[] normalPolygonY = new double[polygonNumber];
		double[] normalPolygonZ = new double[polygonNumber];
		double[] normalPolygonDistance = new double[polygonNumber];

		for (int k = 0; k < polygonNumber; k++) {
			Polygon polygon = polygons.get(k);
			List<Vertex> vertices = polygon.getVertices();
			int size = vertices.size();
			if (size > max)
				max = size;
			numberOfPointsTmp += size;
			polygonStartIndex[k] = orderedPoints.size();
			polygonSize[k] = size;

			// Convert to fixed point
			normalPolygonX[k] = (polygon.getPlane().getNormal().x);
			normalPolygonY[k] = (polygon.getPlane().getNormal().y);
			normalPolygonZ[k] = (polygon.getPlane().getNormal().z);
			normalPolygonDistance[k] = (polygon.getPlane().getDist());
			orderedPoints.addAll(vertices);
		}
		int ExtraSpace = max + 2;
		int[] coplanarFrontStartIndex = new int[polygonNumber];
		int[] coplanarFrontSize = new int[polygonNumber];
		int[] coplanarBackStartIndex = new int[polygonNumber];
		int[] coplanarBackSize = new int[polygonNumber];
		int[] frontStartIndex = new int[polygonNumber];
		int[] frontspace = new int[polygonNumber];
		int[] frontSize = new int[polygonNumber];
		int[] backStartIndex = new int[polygonNumber];
		int[] backspace = new int[polygonNumber];
		int[] backSize = new int[polygonNumber];

		int pointsNumber = numberOfPointsTmp + 1 + ((ExtraSpace) * (polygonNumber + 2));
		boolean[] memoryError = new boolean[polygonNumber];

		for (int k = 0; k < polygonNumber; k++) {
			newPointStartIndex[k] = numberOfPointsTmp + (k * ExtraSpace);
			coplanarFrontStartIndex[k] = -1;
			coplanarBackStartIndex[k] = -1;
			frontStartIndex[k] = -1;
			backStartIndex[k] = -1;
			memoryError[k] = false;
			frontspace[k] = ExtraSpace;
			backspace[k] = ExtraSpace;
		}

		int pointsEmptyIndex = 0;
		int[] types = new int[max];

		// Convert point arrays to fixed point
		double[] polygonPointX = new double[pointsNumber];
		double[] polygonPointY = new double[pointsNumber];
		double[] polygonPointZ = new double[pointsNumber];

		for (; pointsEmptyIndex < orderedPoints.size(); pointsEmptyIndex++) {
			Vertex vertex = orderedPoints.get(pointsEmptyIndex);
			polygonPointX[pointsEmptyIndex] = (vertex.getX());
			polygonPointY[pointsEmptyIndex] = (vertex.getY());
			polygonPointZ[pointsEmptyIndex] = (vertex.getZ());
		}

		// Convert plane normal to fixed point
		double planeNormalX = (this.plane.getNormal().x);
		double planeNormalY = (this.plane.getNormal().y);
		double planeNormalZ = (this.plane.getNormal().z);
		double planeNormalDistance = (this.plane.getDist());

		double epsilon = Plane.getEPSILON();

		final int COPLANAR = 0;
		final int FRONT = 1;
		final int BACK = 2;
		final int SPANNING = 3;

		int chunkSize = polygonNumber;
		int loops = polygonNumber / chunkSize;
		if (loops < 0)
			loops = 1;
		Kernel splitPolygons = new Kernel() {

			int size(int polygonIndex, int[] mypolygonSize) {
				return mypolygonSize[polygonIndex];
			}

			int addPolygon(int polygonIndex, int size, int[] mypolygonStartIndex, int[] mypolygonSize, int[] space) {
				int w = polygonIndex;
				mypolygonStartIndex[w] = newPointStartIndex[w];
				newPointStartIndex[w] += size;
				mypolygonSize[w] = size;
				space[w] -= size;
				return space[w];
			}

			void copy(int polygonIndex, int[] mypolygonStartIndex, int[] mypolygonSize) {
				mypolygonStartIndex[polygonIndex] = polygonStartIndex[polygonIndex];
				mypolygonSize[polygonIndex] = polygonSize[polygonIndex];
			}

			void clear(int polygonIndex, int[] mypolygonSize) {
				mypolygonSize[polygonIndex] = 0;
			}

			void incrementSize(int polygonIndex, int[] mypolygonStartIndex, int[] mypolygonSize) {
				mypolygonSize[polygonIndex]++;
				int ni = mypolygonSize[polygonIndex];
				if (polygonIndex == polygonNumber - 1) {
					if (ni >= pointsNumber) {
						memoryError[polygonIndex] = true;
					}
				} else {
					if (ni + mypolygonStartIndex[polygonIndex] == mypolygonStartIndex[polygonIndex + 1]) {
						memoryError[polygonIndex] = true;
					}
				}
			}

			int writeIncrementPoint(int polygonIndex, int source, int[] mypolygonStartIndex, int[] mypolygonSize) {
				int pointInPolygon = mypolygonSize[polygonIndex];// get the end of the current list
				incrementSize(polygonIndex, mypolygonStartIndex, mypolygonSize);
				double x = polygonPointX[source];
				double y = polygonPointY[source];
				double z = polygonPointZ[source];
				return writePoint(polygonIndex, pointInPolygon, x, y, z, mypolygonStartIndex);
			}

			int interpolate(int polygonIndex, int vi, int vj, int[] mypolygonStartIndex, int[] mypolygonSize) {
//			        double tol = Plane.getEPSILON();
//			      
//			        double dot = myPlane.getNormal().dot(vi2.pos);
//			        double dist = myPlane.getDist();

				// Fixed point computation of g = planeNormalDistance -
				// planeDotPoint(polygonIndex, vi)
				int globalIndex = getGlobalPointIndex(polygonIndex, vi);
				double planeDot = dotProductFixed(planeNormalX, planeNormalY, planeNormalZ, polygonPointX[globalIndex],
						polygonPointY[globalIndex], polygonPointZ[globalIndex]);

				double g = (planeNormalDistance - planeDot);
//			        double mydot = Double.longBitsToDouble(planeDot);
//			        double mydist = Double.longBitsToDouble(planeNormalDistance);
//			        double myD = mydist - mydot;
//			        double d = dist - dot;
//			        
//			        Vector3d minus = vj2.pos.minus(vi2.pos);
//			        double dotMinusOld = myPlane.getNormal().dot(minus);
//			        double tOld = d / dotMinusOld;
//			        Vector3d times = minus.times(tOld);
//			        Vector3d intrp = vi2.pos.plus(times);

				// Fixed point computation of dotMinus = planeDotPointMinusPoint(polygonIndex,
				// vj, vi)
				int globalVi = getGlobalPointIndex(polygonIndex, vi);
				int globalVj = getGlobalPointIndex(polygonIndex, vj);

				double diff_x = (polygonPointX[globalVj] - polygonPointX[globalVi]);
				double diff_y = (polygonPointY[globalVj] - polygonPointY[globalVi]);
				double diff_z = (polygonPointZ[globalVj] - polygonPointZ[globalVi]);

				double dotMinus = dotProductFixed(planeNormalX, planeNormalY, planeNormalZ, diff_x, diff_y, diff_z);

				// Fixed point division: t = g / dotMinus
				double t = (g / dotMinus);

				int pointInPolygon = mypolygonSize[polygonIndex];
				incrementSize(polygonIndex, mypolygonStartIndex, mypolygonSize);

				// Get fixed point coordinates
				double xvi = polygonPointX[globalVi];
				double yvi = polygonPointY[globalVi];
				double zvi = polygonPointZ[globalVi];

				// Fixed point interpolation: lerp = vi + (vj - vi) * t
				double lerp_x = (xvi + (diff_x * t));
				double lerp_y = (yvi + (diff_y * t));
				double lerp_z = (zvi + (diff_z * t));

				int ret = writePoint(polygonIndex, pointInPolygon, lerp_x, lerp_y, lerp_z, mypolygonStartIndex);

				return ret;
			}

			double dotProductFixed(double ax, double ay, double az, double bx, double by, double bz) {
				return az * bz + ay * by + ax * bx;
			}

			int writePoint(int polygonIndex, int pointInPolygon, double x, double y, double z,
					int[] mypolygonStartIndex) {
				int pointIndex = getPointIndex(polygonIndex, pointInPolygon, mypolygonStartIndex);
				polygonPointX[pointIndex] = x;
				polygonPointY[pointIndex] = y;
				polygonPointZ[pointIndex] = z;
				return pointIndex;
			}

			int getGlobalPointIndex(int polygonIndex, int point) {
				return polygonStartIndex[polygonIndex] + point;
			}

			int getPointIndex(int polygonIndex, int point, int[] mypolygonStartIndex) {
				return mypolygonStartIndex[polygonIndex] + point;
			}

			double polygonPointDistance(int polygonIndex, int pointIndex) {
				int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
				double dotResult = dotProductFixed(normalPolygonX[polygonIndex], normalPolygonY[polygonIndex],
						normalPolygonZ[polygonIndex], polygonPointX[globalIndex], polygonPointY[globalIndex],
						polygonPointZ[globalIndex]);

				double result = (dotResult - normalPolygonDistance[polygonIndex]);
				return (result);
			}

			double planePointDistance(int polygonIndex, int pointIndex) {
				int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
				double dotResult = dotProductFixed(planeNormalX, planeNormalY, planeNormalZ, polygonPointX[globalIndex],
						polygonPointY[globalIndex], polygonPointZ[globalIndex]);

				double result = (dotResult - planeNormalDistance);
				return (result);
			}

			double planeDotPolygonNormal(int polygonIndex) {
				double result = dotProductFixed(planeNormalX, planeNormalY, planeNormalZ, normalPolygonX[polygonIndex],
						normalPolygonY[polygonIndex], normalPolygonZ[polygonIndex]);
				return (result);
			}

			@Override
			public void run() {
				int pi = getGlobalId() * chunkSize;
				int end = pi + chunkSize;
				for (int polygonIndex = pi; (polygonIndex < end) && (polygonIndex < polygonNumber); polygonIndex++) {
					if (memoryError[polygonIndex])
						return;
					// search for the epsilon values of the incoming plane
					double negEpsilon = -epsilon;
					double posEpsilon = epsilon;
					for (int i = 0; i < size(polygonIndex, polygonSize); i++) {
						double t = polygonPointDistance(polygonIndex, i);
						if (t > posEpsilon) {
							posEpsilon = (float) (t + epsilon);
						}
						if (t < negEpsilon) {
							negEpsilon = (float) (t - epsilon);
						}
					}
					int polygonType = COPLANAR;
					boolean somePointsInfront = false;
					boolean somePointsInBack = false;
					for (int i = 0; i < size(polygonIndex, polygonSize); i++) {
						double t = planePointDistance(polygonIndex, i);
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
					} else if (somePointsInfront) {
						polygonType = FRONT;
					}
					if (polygonType == COPLANAR) {
						if (planeDotPolygonNormal(polygonIndex) > 0) {
							copy(polygonIndex, coplanarFrontStartIndex, coplanarFrontSize);
						} else {
							copy(polygonIndex, coplanarBackStartIndex, coplanarBackSize);
						}
					}
					if (polygonType == FRONT) {
						copy(polygonIndex, frontStartIndex, frontSize);
					}
					if (polygonType == BACK) {
						copy(polygonIndex, backStartIndex, backSize);
					}
					if (polygonType == SPANNING) {
						int size = size(polygonIndex, polygonSize);
						int retF = addPolygon(polygonIndex, size + 1, frontStartIndex, frontSize, frontspace);
						if (retF < 0) {
							memoryError[polygonIndex] = true;
							return;
						}
						int retB = addPolygon(polygonIndex, size + 1, backStartIndex, backSize, backspace);
						if (retB < 0) {
							memoryError[polygonIndex] = true;
							return;
						}
						clear(polygonIndex, frontSize);
						clear(polygonIndex, backSize);

						for (int i = 0; i < size; i++) {
							int j = (i + 1) % size;
							int ti = types[i];
							int tj = types[j];
							int viIndex = getGlobalPointIndex(polygonIndex, i);
							if (ti != BACK) {
								writeIncrementPoint(polygonIndex, viIndex, frontStartIndex, frontSize);
							}
							if (ti != FRONT) {
								writeIncrementPoint(polygonIndex, viIndex, backStartIndex, backSize);
							}
							if ((ti == FRONT && tj == BACK) || (ti == BACK && tj == FRONT)) {
								int v = interpolate(polygonIndex, i, j, frontStartIndex, frontSize); // ← Should be
																									// backStartIndex,
																									// backSize
								if (memoryError[polygonIndex])
									return;
								writeIncrementPoint(polygonIndex, v, backStartIndex, backSize); // ← Should be
																									// frontStartIndex,
																									// frontSize
							}
						}

					}
				} // outer for loop of all polygons
			}// run
		};
		splitPolygons.run();
//		CSG.gpuRun(loops, splitPolygons, null, "split ", () -> {
//			return false;
//		}, 1, 1);
		for (int k = 0; k < polygonNumber; k++)
			if (memoryError[k])
				throw new RuntimeException("Memory error here!");
		// Collect the polygon data into the return structures
		for (int k = 0; k < polygonNumber; k++) {
			Polygon polygon = polygons.get(k);
			add(coplanarFront, k, coplanarFrontStartIndex, coplanarFrontSize, orderedPoints, polygonPointX,
					polygonPointY, polygonPointZ,

					polygon);
			add(coplanarBack, k, coplanarBackStartIndex, coplanarBackSize, orderedPoints, polygonPointX, polygonPointY,
					polygonPointZ, polygon);
			add(front, k, frontStartIndex, frontSize, orderedPoints, polygonPointX, polygonPointY, polygonPointZ,
					polygon);
			add(back, k, backStartIndex, backSize, orderedPoints, polygonPointX, polygonPointY, polygonPointZ, polygon);

		}
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
	public void splitPolygonOriginal(List<Polygon> polygons, List<Polygon> coplanarFront, List<Polygon> coplanarBack,
			List<Polygon> front, List<Polygon> back) {
		final int COPLANAR = 0;
		final int FRONT = 1;
		final int BACK = 2;
		final int SPANNING = 3; // == some in the FRONT + some in the BACK
		for (int k = 0; k < polygons.size(); k++) {
			Polygon polygon = polygons.get(k);
			// search for the epsilon values of the incoming plane
			double negEpsilon = -Plane.getEPSILON();
			double posEpsilon = Plane.getEPSILON();
			for (int i = 0; i < polygon.getVertices().size(); i++) {
				double t = polygon.getPlane().getNormal().dot(polygon.getVertices().get(i).pos)
						- polygon.getPlane().getDist();
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
				double t = plane.getNormal().dot(polygon.getVertices().get(i).pos) - plane.getDist();
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
				(plane.getNormal().dot(polygon.getPlane().getNormal()) > 0 ? coplanarFront : coplanarBack).add(polygon);
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
						addPoint(f, vi);
						// f.add(vi);
					}
					if (ti != FRONT) {
						addPoint(b, (ti != BACK ? vi.clone() : vi));
					}
					if ((ti == FRONT && tj == BACK) || (ti == BACK && tj == FRONT)) {
//						double t = (plane.getDist() - plane.getNormal().dot(vi.pos))
//								/ plane.getNormal().dot(vj.pos.minus(vi.pos));
//						Vertex v = vi.interpolate(vj, t);
//						addPoint(f,v);
//						addPoint(b,v.clone());
						double dot = this.plane.getNormal().dot(vi.pos);
						double dist = this.plane.getDist();

						// Fixed point computation of g = planeNormalDistance -
						// planeDotPoint(polygonIndex, vi)
						double d = dist - dot;

//				        Vector3d minus = vj.pos.minus(vi.pos);
//				        double dotMinusOld = this.plane.getNormal().dot(minus);
//				        double tOld = d / dotMinusOld;
//				        Vector3d times = minus.times(tOld);
//				        Vector3d intrp = vi.pos.plus(times);

						// Extract the vector components
						final double xi = vi.pos.x;
						final double yi = vi.pos.y;
						final double zi = vi.pos.z;

						final double xj = vj.pos.x;
						final double yj = vj.pos.y;
						final double zj = vj.pos.z;

						// Compute the difference vector (vj - vi)
						final double dx = xj - xi;
						final double dy = yj - yi;
						final double dz = zj - zi;

						// Assuming plane.getNormal() returns a Vector3d or similar with x, y, z fields
						final double nx = plane.getNormal().x;
						final double ny = plane.getNormal().y;
						final double nz = plane.getNormal().z;

						// Compute dot product
						final double dotMinusOld = nx * dx + ny * dy + nz * dz;

						// Compute scalar tOld
						final double tOld = d / dotMinusOld;

						// Scale difference vector by tOld
						final double sx = dx * tOld;
						final double sy = dy * tOld;
						final double sz = dz * tOld;

						// Compute interpolated point intrp = vi + scaled vector
						final double intrpX = xi + sx;
						final double intrpY = yi + sy;
						final double intrpZ = zi + sz;
						Vector3d intrp = new Vector3d(intrpX, intrpY, intrpZ);
						addPoint(f, new Vertex(intrp, polygon.plane.getNormal()));
						addPoint(b, new Vertex(intrp, polygon.plane.getNormal()));
					}
				}
				add(front, f, polygon);
				add(back, b, polygon);
				break;
			}
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

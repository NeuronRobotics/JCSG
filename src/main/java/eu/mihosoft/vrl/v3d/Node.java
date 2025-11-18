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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.aparapi.Kernel;
import com.aparapi.device.Device;
import com.aparapi.device.OpenCLDevice;
import com.aparapi.internal.kernel.KernelManager;

import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;
import javafx.scene.paint.Color;

//  Auto-generated Javadoc
/**
 * Holds a node in a BSP tree. A BSP tree is built from a collection of polygons
 * by picking a polygon to split along. That polygon (and all other coplanar
 * polygons) are added directly to that node and the other polygons are added to
 * the front and/or back subtrees. This is not a leafy BSP tree since there is
 * no distinction between internal and leaf nodes.
 */
public final class Node {
	//private static final int LIMIT_FOR_GPU = 5000;
	public static final int COPLANAR = 0;
	public static final int FRONT = 1;
	public static final int BACK = 2;
	public static final int SPANNING = 3; // == some in the FRONT + some in the BACK

	/**
	 * Polygons.
	 */
	private ArrayList<Polygon> polygons;
	/**
	 * Plane used for BSP.
	 */
	private final Plane myNodePlane;
	/**
	 * Polygons in front of the plane.
	 */
	private Node front;
	/**
	 * Polygons in back of the plane.
	 */
	private Node back;

	private long maxDepth = -1;

	private long count = 1;
	private static boolean GPUTest = false;;

	/**
	 * Constructor.
	 *
	 * Creates a Binary Space Partition (BSP) node consisting of the specified polygons.
	 *
	 * @param polygons polygons
	 * @throws Exception
	 */
	public Node(ArrayList<Polygon> polygons,Plane p) throws Exception {
		myNodePlane=p.clone();
		this.polygons = new ArrayList<>();
		if (polygons != null) {
			this.build(polygons);
		}
	}

//	/**
//	 * Constructor. Creates a node without polygons.
//	 */
	private Node(Plane p) throws Exception {
		this(null,p);
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
			node = new Node(this.getThisNodePlane().clone());
//			node.setPlane(this.getPlane() == null ? null : this.getPlane().clone());
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
			//  Auto-generated catch block
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

//		if (this.getPlane() == null && !polygons.isEmpty()) {
//			this.setPlane(polygons.get(0).getPlane().clone());
//		} else if (this.getPlane() == null && polygons.isEmpty()) {
//
//			// com.neuronrobotics.sdk.common.Log.error("Please fix me! I don't know what to
//			// do?");
//			throw new RuntimeException("Please fix me! Plane = " + getPlane() + " and polygons are empty");
//			// return;
//		}

		this.getThisNodePlane().flip();

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
//	public void invert() {
//	    // Use ArrayList as a stack to track nodes to process
//	    ArrayList<Node> stack = new ArrayList<>();
//	    stack.add(this);
//	    
//	    while (!stack.isEmpty()) {
//	        // Pop the last node from our stack
//	        Node current = stack.remove(stack.size() - 1);
//	        
//	        // Process polygons for current node
//	        Stream<Polygon> polygonStream;
//	        if (current.polygons.size() > 200) {
//	            polygonStream = current.polygons.parallelStream();
//	        } else {
//	            polygonStream = current.polygons.stream();
//	        }
//	        
//	        polygonStream.forEach((polygon) -> {
//	            polygon.flip();
//	        });
//	        
//	        // Handle plane logic
//	        if (current.getPlane() == null && !current.polygons.isEmpty()) {
//	            current.setPlane(current.polygons.get(0).getPlane().clone());
//	        } else if (current.getPlane() == null && current.polygons.isEmpty()) {
//	            throw new RuntimeException("Please fix me! Plane = " + current.plane + " and polygons are empty");
//	        }
//	        
//	        current.getPlane().flip();
//	        
//	        // Add child nodes to stack for processing (if they exist)
//	        // Note: We add them in reverse order so they're processed in the same order as the recursive version
//	        if (current.back != null) {
//	            stack.add(current.back);
//	        }
//	        if (current.front != null) {
//	            stack.add(current.front);
//	        }
//	        
//	        // Swap front and back
//	        Node temp = current.front;
//	        current.front = current.back;
//	        current.back = temp;
//	    }
//	}
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

		if (this.getThisNodePlane() == null) {
			throw new RuntimeException("Plane can not be null");
		}
		// preallocate the lists so they do not use dynamic memory in split
		ArrayList<Polygon> frontP = new ArrayList<>(polygons.size());
		ArrayList<Polygon> backP = new ArrayList<>(polygons.size());

		splitPolygon(polygons, frontP, backP, frontP, backP);
		if (this.front != null) {
			frontP = this.front.clipPolygons(frontP);
		}
		if (this.back != null) {
			backP = this.back.clipPolygons(backP);
		} else {
			backP=new ArrayList<Polygon>(0);
		}
		frontP.addAll(backP);
		return frontP;
	}

	private static int add(List<Polygon> l, int polygonIndex, int[] polygonStartIndex, int[] polygonSize,
			ArrayList<Vertex> orderedPoints, double[] polygonPointX, double[] polygonPointY, double[] polygonPointZ,
			Polygon polygon, boolean[] isCopy) {
		int polygonBase = polygonStartIndex[polygonIndex];
		int size = polygonSize[polygonIndex];
		if (polygonBase < 0)
			return 0;
		if (isCopy[polygonIndex]) {
			l.add(polygon);
			return 1;
		}
		try {
			testAddPolygon(l, orderedPoints, polygonPointX, polygonPointY, polygonPointZ, polygon, polygonBase, size,
					false);
			return 1;
		} catch (Exception ex) {
			ex.printStackTrace();
			System.err.println("Pruning bad polygon Node::splitPolygon::add " + l.size());
		}
		return 0;
	}

	private static void testAddPolygon(List<Polygon> l, ArrayList<Vertex> orderedPoints, double[] polygonPointX,
			double[] polygonPointY, double[] polygonPointZ, Polygon polygon, int polygonBase, int size, boolean test) {
		List<Vertex> f = new ArrayList<>();
		for (int i = polygonBase; i < polygonBase + size; i++) {
			if (i < orderedPoints.size()) {
				f.add(orderedPoints.get(i).clone());
			} else {
				double x = (polygonPointX[i]);
				double y = (polygonPointY[i]);
				double z = (polygonPointZ[i]);
				Vertex v = new Vertex(new Vector3d(x, y, z));
				addPoint(f, v);
			}
		}

		add(l, f, polygon, test);
	}

	private static boolean addPoint(List<Vertex> f, Vertex v) {
		if (f.size() > 0) {
//			if (Math.abs(v.pos.distance(f.get(0).pos)) < Plane.getEPSILON()) {
//				return false;
//			}
//			if (Math.abs(v.pos.distance(f.get(f.size() - 1).pos)) < Plane.getEPSILON()) {
//				return false;
//			}
		}
		return f.add(v);
	}

	private static void add(List<Polygon> l, List<Vertex> f, Polygon polygon) {
		add(l, f, polygon, false);
	}

	private static void add(List<Polygon> l, List<Vertex> f, Polygon polygon, boolean test) {
		if (f.size() < 3)
			return;
		try {
			if(!Extrude.isCCW(f, polygon.getPlane().getNormal())) {
				Collections.reverse(f);
			}
			Polygon fpoly = new Polygon(f, polygon.getStorage(), true, polygon.getPlane())
					.setColor(polygon.getColor());
			if (!test)
				l.add(fpoly);	
		}catch(ColinearPointsException ex) {
			System.err.println(ex.getMessage()+" Pruned Colinear polygon "+f );
		}
	}
	public static String getOsName() {
		return System.getProperty("os.name");
	}
	public static boolean isWindows() {
		// //com.neuronrobotics.sdk.common.Log.error("OS name: "+getOsName());
		return getOsName().toLowerCase().startsWith("windows")
				|| getOsName().toLowerCase().startsWith("microsoft")
				|| getOsName().toLowerCase().startsWith("ms");
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
	public void splitPolygon(ArrayList<Polygon> polygons, List<Polygon> cf, List<Polygon> cb,
			List<Polygon> f, List<Polygon> b) throws Exception {
//		if (polygons.size() > LIMIT_FOR_GPU && !isWindows()) {
//			splitPolygonGPU(polygons, cf, cb, f, b);
//			return;
//		}
		splitPolygonOriginal(polygons, cf, cb, f, b);

//		List<Polygon> cf1 = new ArrayList<Polygon>();
//		List<Polygon> cb1= new ArrayList<Polygon>();
//		List<Polygon> f1= new ArrayList<Polygon>();
//		List<Polygon> b1= new ArrayList<Polygon>();
//		List<Polygon> cf2= new ArrayList<Polygon>();
//		List<Polygon> cb2= new ArrayList<Polygon>();
//		List<Polygon> f2= new ArrayList<Polygon>(); 
//		List<Polygon> b2= new ArrayList<Polygon>();
//		splitPolygonOriginal(polygons, cf2, cb2, f2, b2);
//		splitPolygonGPU(polygons, cf1, cb1, f1, b1);
//
//		if(cf1.size()!=cf2.size()
//				||cb1.size()!=cb2.size()
//				|| f1.size()!=f2.size()
//				||b1.size()!=b2.size()) {
//			throw new RuntimeException("Node split mismathch");
//		}
//		for (int i = 0; i < cf1.size(); i++) {
//			Polygon p1 = cf1.get(i);
//			Polygon p2 = cf2.get(i);
//			if(p1.size()!=p2.size())
//				throw new RuntimeException("Node slit mismathch, polygon size mismatch");
//
//		}
//		for (int i = 0; i < cb1.size(); i++) {
//			Polygon p1 = cb1.get(i);
//			Polygon p2 = cb2.get(i);
//			if(p1.size()!=p2.size())
//				throw new RuntimeException("Node slit mismathch, polygon size mismatch");
//
//		}
//		for (int i = 0; i < f1.size(); i++) {
//			Polygon p1 = f1.get(i);
//			Polygon p2 = f2.get(i);
//			if(p1.size()!=p2.size())
//				throw new RuntimeException("Node slit mismathch, polygon size mismatch");
//
//		}		
//		for (int i = 0; i < b1.size(); i++) {
//			Polygon p1 = b1.get(i);
//			Polygon p2 = b2.get(i);
//			if(p1.size()!=p2.size())
//				throw new RuntimeException("Node slit mismathch, polygon size mismatch");
//
//		}
//		f.addAll(f1);
//		b.addAll(b1);
//		cb.addAll(cb1);
//		cf.addAll(cf1);
	}

	/**
	 * An attempt to make part of the CSG stack GPu accelerated
	 * 
	 * this is worth exploring in the future
	 * 
	 * @param polygons
	 * @param coplanarFront
	 * @param coplanarBack
	 * @param front
	 * @param back
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
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
		int ExtraSpace = max * 2 + 2;
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

		int pointsNumber = numberOfPointsTmp + 1 + ((ExtraSpace) * (polygonNumber + 1) * 2);
		boolean[] memoryError = new boolean[polygonNumber];
		boolean[] isCopy = new boolean[polygonNumber];

		for (int k = 0; k < polygonNumber; k++) {
			newPointStartIndex[k] = numberOfPointsTmp + (k * ExtraSpace * 2);
			coplanarFrontStartIndex[k] = -1;
			coplanarBackStartIndex[k] = -1;
			frontStartIndex[k] = -1;
			backStartIndex[k] = -1;
			memoryError[k] = false;
			isCopy[k] = false;
			frontspace[k] = ExtraSpace;
			backspace[k] = ExtraSpace;
		}

		int pointsEmptyIndex = 0;
		int[] types = new int[max * (polygonNumber + 1)];
		int maxPolygonSize = max;

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
		for (int i = orderedPoints.size(); i < polygonPointX.length; i++) {
			polygonPointX[i] = -1;
			polygonPointY[i] = -1;
			polygonPointZ[i] = -1;
		}
		// Convert plane normal to fixed point
		double planeNormalX = (this.getThisNodePlane().getNormal().x);
		double planeNormalY = (this.getThisNodePlane().getNormal().y);
		double planeNormalZ = (this.getThisNodePlane().getNormal().z);
		double planeNormalDistance = (this.getThisNodePlane().getDist());

		double epsilon = Plane.getEPSILON();

		int chunkSize =5000;
		int loops = polygonNumber / chunkSize;
		if (loops < 0)
			loops = 1;
		
		//System.out.println("\n\nStarting Kernel "+polygonNumber+" polygons in "+loops+" loops ");
		Kernel splitPolygonsKernel = new Kernel() {

			int size(int polygonIndex, int[] mypolygonSize) {
				return mypolygonSize[polygonIndex];
			}

			int addPolygon(int polygonIndex, int size, int[] mypolygonStartIndex, int[] mypolygonSize, int[] space) {
				int w = polygonIndex;
				mypolygonStartIndex[w] = newPointStartIndex[w];
				newPointStartIndex[w] += size;
				mypolygonSize[w] = 0;
				space[w] -= size;
				double fx = polygonPointX[mypolygonStartIndex[w]];
				double fy = polygonPointY[mypolygonStartIndex[w]];
				double fz = polygonPointZ[mypolygonStartIndex[w]];
				if (Math.abs(fx + 1) > epsilon || Math.abs(fy + 1) > epsilon || Math.abs(fz + 1) > epsilon) {
					memoryError[polygonIndex] = true;
					return -1;
				}
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

				int globalVi = polygonStartIndex[polygonIndex] + vi;
				int globalVj = polygonStartIndex[polygonIndex] + vj;
				double planeDot = dotProductFixed(planeNormalX, planeNormalY, planeNormalZ, polygonPointX[globalVi],
						polygonPointY[globalVi], polygonPointZ[globalVi]);

				double d = (planeNormalDistance - planeDot);
				// Get fixed point coordinates
				double xvi = polygonPointX[globalVi];
				double yvi = polygonPointY[globalVi];
				double zvi = polygonPointZ[globalVi];
				double diff_x = (polygonPointX[globalVj] - xvi);
				double diff_y = (polygonPointY[globalVj] - yvi);
				double diff_z = (polygonPointZ[globalVj] - zvi);

				double dotMinus = dotProductFixed(planeNormalX, planeNormalY, planeNormalZ, diff_x, diff_y, diff_z);
				// Paralell case where one point is slightly infront by the same amount that the
				// other is slightly behind. when summed, they make a point that is exactly on
				// the plane
				// therefor the intersection point is halfway between i and j
				double t = 0.5;
				if (dotMinus != 0)
					t = (d / dotMinus);
				else
					return -1;
				if(t<0||t>1)
					return -1;

				// Fixed point interpolation: lerp = vi + (vj - vi) * t
				double sx = diff_x * t;
				double sy = diff_y * t;
				double sz = diff_z * t;
				
				double lerp_x = (xvi + sx);
				double lerp_y = (yvi + sy);
				double lerp_z = (zvi + sz);

//				new Vertex(new Vector3d(lerp_x , lerp_y , lerp_z ), polygons.get(polygonIndex).plane.getNormal());

				int pointInPolygon = mypolygonSize[polygonIndex];
				incrementSize(polygonIndex, mypolygonStartIndex, mypolygonSize);

				int ret = writePoint(polygonIndex, pointInPolygon, lerp_x, lerp_y, lerp_z, mypolygonStartIndex);

				return ret;
			}

			double dotProductFixed(double ax, double ay, double az, double bx, double by, double bz) {
				return (az * bz) + (ay * by) + (ax * bx);
			}

			int writePoint(int polygonIndex, int pointInPolygon, double x, double y, double z,
					int[] mypolygonStartIndex) {
				int pointIndex = mypolygonStartIndex[polygonIndex] + pointInPolygon;
				double fx = polygonPointX[pointIndex];
				double fy = polygonPointY[pointIndex];
				double fz = polygonPointZ[pointIndex];
				if (Math.abs(fx + 1) > epsilon || Math.abs(fy + 1) > epsilon || Math.abs(fz + 1) > epsilon) {
					memoryError[polygonIndex] = true;
					return -1;
				}
				polygonPointX[pointIndex] = x;
				polygonPointY[pointIndex] = y;
				polygonPointZ[pointIndex] = z;
				return pointIndex;
			}

			double polygonPointDistance(int polygonIndex, int pointIndex) {
				int globalIndex = polygonStartIndex[polygonIndex] + pointIndex;
				double dotResult = dotProductFixed(normalPolygonX[polygonIndex], normalPolygonY[polygonIndex],
						normalPolygonZ[polygonIndex], polygonPointX[globalIndex], polygonPointY[globalIndex],
						polygonPointZ[globalIndex]);

				double result = (dotResult - normalPolygonDistance[polygonIndex]);
				return (result);
			}

			double planePointDistance(int polygonIndex, int pointIndex) {
				int globalIndex = polygonStartIndex[polygonIndex] + pointIndex;
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
			void runOnePolygon(int polygonIndex) {
				// search for the epsilon values of the incoming plane
				double negEpsilon = -epsilon;
				double posEpsilon = epsilon;
//				for (int i = 0; i < size(polygonIndex, polygonSize); i++) {
//					double t = polygonPointDistance(polygonIndex, i);
//					if (t > posEpsilon) {
//						posEpsilon = (float) (t + epsilon);
//					}
//					if (t < negEpsilon) {
//						negEpsilon = (float) (t - epsilon);
//					}
//				}
				int polygonType = COPLANAR;;
//				boolean someF =false;
//				boolean someB=false;
				for (int i = 0; i < size(polygonIndex, polygonSize); i++) {
					double t = planePointDistance(polygonIndex, i);
					int type = (t < negEpsilon) ? BACK : (t > posEpsilon) ? FRONT : COPLANAR;
					types[i + polygonIndex * maxPolygonSize] = type;
					polygonType |= type;
//					if(type==BACK)
//						someB=true;
//					if(type==FRONT)
//						someF=true;
				}
//				polygonType=COPLANAR;
//				if(someF && (!someB) ) {
//					polygonType=FRONT;
//				}
//				if((!someF) && (someB) ) {
//					polygonType=BACK;
//				}
//				if((someF) && (someB) ) {
//					polygonType=SPANNING;
//				}

//				List<Polygon> cf2= new ArrayList<Polygon>();
//				List<Polygon> cb2= new ArrayList<Polygon>();
//				List<Polygon> f2= new ArrayList<Polygon>(); 
//				List<Polygon> b2= new ArrayList<Polygon>();
//				splitSinglePolygon(polygons.get(polygonIndex), cf2, cb2, f2, b2);
				if (polygonType == COPLANAR) {
					isCopy[polygonIndex] = true;
					if (planeDotPolygonNormal(polygonIndex) > 0) {
//						if(cf2.size()!=1) {
//							memoryError[polygonIndex] = true;
//							return;
//						}
						copy(polygonIndex, coplanarFrontStartIndex, coplanarFrontSize);
					} else {
//						if(cb2.size()!=1) {
//							memoryError[polygonIndex] = true;
//							return;
//						}
						copy(polygonIndex, coplanarBackStartIndex, coplanarBackSize);
					}
				} else if (polygonType == FRONT) {
//					if(f2.size()!=1) {
//						memoryError[polygonIndex] = true;
//						return;
//					}
					isCopy[polygonIndex] = true;
					copy(polygonIndex, frontStartIndex, frontSize);
				} else if (polygonType == BACK) {
//					if(b2.size()!=1) {
//						memoryError[polygonIndex] = true;
//						return;
//					}
					isCopy[polygonIndex] = true;
					copy(polygonIndex, backStartIndex, backSize);
				} else if (polygonType == SPANNING) {
//					if(b2.size()!=1 && f2.size()!=1) {
//						memoryError[polygonIndex] = true;
//						return;
//					}
					isCopy[polygonIndex] = false;

					int size = size(polygonIndex, polygonSize);
					int polygonMax = size * 2;
					int retF = addPolygon(polygonIndex, polygonMax, frontStartIndex, frontSize, frontspace);
					if (retF < 0) {
						memoryError[polygonIndex] = true;
						return;
					}
					int retB = addPolygon(polygonIndex, polygonMax, backStartIndex, backSize, backspace);
					if (retB < 0) {
						memoryError[polygonIndex] = true;
						return;
					}
					clear(polygonIndex, frontSize);
					clear(polygonIndex, backSize);

					for (int i = 0; i < size; i++) {
						int j = (i + 1) % size;
						int ti = types[i + polygonIndex * maxPolygonSize];
						int tj = types[j + polygonIndex * maxPolygonSize];
						int sourctPointIndex = polygonStartIndex[polygonIndex] + i;
						if (ti != BACK) {
							writeIncrementPoint(polygonIndex, sourctPointIndex, frontStartIndex, frontSize);
						}
						if (ti != FRONT) {
							writeIncrementPoint(polygonIndex, sourctPointIndex, backStartIndex, backSize);
						}
						if ((ti|tj)==SPANNING) {
							int newPointIndex = interpolate(polygonIndex, i, j, frontStartIndex, frontSize);
							if (newPointIndex > 0) {
								writeIncrementPoint(polygonIndex, newPointIndex, backStartIndex, backSize);
							}
						}
						if (memoryError[polygonIndex])
							return;
						int fsize = frontSize[polygonIndex];
						int bsize =  backSize[polygonIndex];
						if (fsize > (polygonMax) || bsize > (polygonMax)) {
							memoryError[polygonIndex] = true;
							return;
						}
					}
//					ArrayList<Polygon> testF=new ArrayList<Polygon>();
//					ArrayList<Polygon> testB=new ArrayList<Polygon>();
//
//					int size23 = frontSize[polygonIndex];
//					int polygonBase = frontStartIndex[polygonIndex];
//					testAddPolygon(testF, orderedPoints, polygonPointX, polygonPointY, polygonPointZ, polygons.get(polygonIndex), polygonBase, size23,
//							false);
//					int size22 = backSize[polygonIndex];
//					int polygonBase2 = backStartIndex[polygonIndex];
//					
//					testAddPolygon(testB, orderedPoints, polygonPointX, polygonPointY, polygonPointZ, polygons.get(polygonIndex), polygonBase2, size22,
//							false);
//					if(testF.size()!=f2.size()||testB.size()!=b2.size()) {
//						memoryError[polygonIndex] = true;
//						return;
//					}
//					if(testB.size()>0)
//					if(testB.get(0).size()!=b2.get(0).size()) {
//						memoryError[polygonIndex] = true;
//						return;
//					}
//					if(testF.size()>0)
//					if(testF.get(0).size()!=f2.get(0).size()) {
//						memoryError[polygonIndex] = true;
//						return;
//					}
				} else {
					memoryError[polygonIndex] = true;
					return;
				}
				int polygonBase = frontStartIndex[polygonIndex];
				int polygonBase22 = backStartIndex[polygonIndex];
				if (coplanarBackStartIndex[polygonIndex] < 0 && coplanarFrontStartIndex[polygonIndex] < 0
						&& polygonBase < 0 && polygonBase22 < 0) {
					memoryError[polygonIndex] = true;
					return;
				}
				
			}
			@Override
			public void run() {
				int pi = getGlobalId() * chunkSize;
				int end = pi + chunkSize;
				if(end>polygonNumber)
					end=polygonNumber;
				//System.out.println("#"+getGlobalId()+" Start "+pi+" to "+end);
				for (int polygonIndex = pi; (polygonIndex < end); polygonIndex++) {
					if (memoryError[polygonIndex])
						return;
					runOnePolygon(polygonIndex);
				} // outer for loop of all polygons
			}// run
		};
		if(!GPUTest) {
			try {
				splitPolygonsKernel.compile(splitPolygonsKernel.getTargetDevice());
			}catch(Exception ex) {
				System.err.println("GPU missing feature "+ex.getMessage());
				GPUTest=true;
			}
		}
		if(GPUTest)
			splitPolygonsKernel.setExecutionMode(Kernel.EXECUTION_MODE.JTP); // Java Thread Pool

		CSG.gpuRun(loops+3, splitPolygonsKernel, null, "split ", () -> {
			return false;
		}, 1, 1);
		
		for (int k = 0; k < polygonNumber; k++)
			if (memoryError[k])
				throw new RuntimeException("Memory error here!");

		// Collect the polygon data into the return structures
		//int copies = 0;
		for (int k = 0; k < polygonNumber; k++) {
			copyDataIntoPolygon(polygons, coplanarFront, coplanarBack, front, back, orderedPoints,
					coplanarFrontStartIndex, coplanarFrontSize, coplanarBackStartIndex, coplanarBackSize,
					frontStartIndex, frontSize, backStartIndex, backSize, isCopy, polygonPointX, polygonPointY,
					polygonPointZ, k);

		}

	}

	private void copyDataIntoPolygon(ArrayList<Polygon> polygons, List<Polygon> coplanarFront,
			List<Polygon> coplanarBack, List<Polygon> front, List<Polygon> back, ArrayList<Vertex> orderedPoints,
			int[] coplanarFrontStartIndex, int[] coplanarFrontSize, int[] coplanarBackStartIndex,
			int[] coplanarBackSize, int[] frontStartIndex, int[] frontSize, int[] backStartIndex, int[] backSize,
			boolean[] isCopy, double[] polygonPointX, double[] polygonPointY, double[] polygonPointZ, int k) {
		int copies;
		Polygon polygon = polygons.get(k);
		copies = 0;

		copies += add(coplanarFront, k, coplanarFrontStartIndex, coplanarFrontSize, orderedPoints, polygonPointX,
				polygonPointY, polygonPointZ, polygon, isCopy);
		copies += add(coplanarBack, k, coplanarBackStartIndex, coplanarBackSize, orderedPoints, polygonPointX,
				polygonPointY, polygonPointZ, polygon, isCopy);
		copies += add(front, k, frontStartIndex, frontSize, orderedPoints, polygonPointX, polygonPointY, polygonPointZ,
				polygon, isCopy);
		copies += add(back, k, backStartIndex, backSize, orderedPoints, polygonPointX, polygonPointY, polygonPointZ,
				polygon, isCopy);
		if (copies != 1 && copies != 2) {
			throw new RuntimeException("Failed to load all polygons??");
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

		for (int k = 0; k < polygons.size(); k++) {
			Polygon polygon = polygons.get(k);
			splitSinglePolygon(polygon,coplanarFront, coplanarBack, front, back);
		}
		if(Debug3dProvider.isProviderAvailible()) {
//			Debug3dProvider.clearScreen();
//			Debug3dProvider.addObject(polygons.get(0).getVertices().get(0));
//			Debug3dProvider.addObject(front.stream().map(polygon -> polygon.setColor(Color.RED)).collect(Collectors.toList()));
//			List<Polygon> collect = back.stream().map(polygon -> polygon.setColor(Color.WHITE)).collect(Collectors.toList());
//			Debug3dProvider.addObject(collect);
//			Debug3dProvider.addObject(coplanarBack.stream().map(polygon -> polygon.setColor(Color.YELLOW)).collect(Collectors.toList()));
//			Debug3dProvider.addObject(coplanarFront.stream().map(polygon -> polygon.setColor(Color.GREEN)).collect(Collectors.toList()));
//			Debug3dProvider.clearScreen();
		}
	}

	private void splitSinglePolygon(Polygon polygon,List<Polygon> coplanarFront, List<Polygon> coplanarBack, List<Polygon> front,
			List<Polygon> back) {
		// search for the epsilon values of the incoming plane
		double negEpsilon = -Plane.getEPSILON();
		double posEpsilon = Plane.getEPSILON();
		int size = polygon.getVertices().size();
		Vector3d normal = polygon.getPlane().getNormal();
			for (int i = 0; i < size; i++) {
				Vector3d pos = polygon.getVertices().get(i).pos;
				double dot = normal.dot(pos);
				double t = dot
						- polygon.getPlane().getDist();
				if(Math.abs(t)>0.01) {
					throw new RuntimeException("A plane epsilon of "+t+" is impossible");
				}
				if (t > posEpsilon) {
					// com.neuronrobotics.sdk.common.Log.error("Non flat polygon, increasing
					// positive epsilon "+t);
					posEpsilon = t;
				}
				if (t < negEpsilon) {
					// com.neuronrobotics.sdk.common.Log.error("Non flat polygon, decreasing
					// negative epsilon "+t);
					negEpsilon = t;
				}
			}
		int polygonType = 0;
		List<Integer> types = new ArrayList<>();
//			boolean someF =false;
//			boolean someB=false;

		//double distP = polygon.getPlane().getDist();
		for (int i = 0; i < size; i++) {
			Vector3d pos = polygon.getVertices().get(i).pos;
//				double dot = normal.dot(pos);
//				double ep = Math.abs( dot-distP);// this is this points distance from its plane
			double t = getThisNodePlane().getNormal().dot(pos) - getThisNodePlane().getDist();
			int type = (t < negEpsilon) ? BACK : (t > posEpsilon) ? FRONT : COPLANAR;
			types.add(type);
			polygonType = polygonType|type;

//				if(type==BACK)
//					someB=true;
//				if(type==FRONT)
//					someF=true;
		}
//			polygonType=COPLANAR;
//			if(someF && (!someB) ) {
//				polygonType=FRONT;
//			}
//			if((!someF) && (someB) ) {
//				polygonType=BACK;
//			}
//			if((someF) && (someB) ) {
//				polygonType=SPANNING;
//			}
		// Put the polygon in the correct list, splitting it when necessary.
		switch (polygonType) {
		case COPLANAR:
			double cp = getThisNodePlane().getNormal().dot(normal);
			(cp > 0 ? coplanarFront : coplanarBack).add(polygon);
			break;
		case FRONT:
			front.add(polygon);
			break;
		case BACK:
			back.add(polygon);
			break;
		case SPANNING:
			List<Vertex> f = new ArrayList<>(size);
			List<Vertex> b = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				int j = (i + 1) % size;
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
				if ((ti|tj) == SPANNING) {
					double planeDot = this.getThisNodePlane().getNormal().dot(vi.pos);
					double planeNormalDistance = this.getThisNodePlane().getDist();

					double d = planeNormalDistance - planeDot;

					// Extract the vector components
					double xvi = vi.pos.x;
					double yvi = vi.pos.y;
					double zvi = vi.pos.z;

					double xvj = vj.pos.x;
					double yvj = vj.pos.y;
					double zvj = vj.pos.z;

					// Compute the difference vector (vj - vi)
					double diff_x = xvj - xvi;
					double diff_y = yvj - yvi;
					double diff_z = zvj - zvi;

					// Assuming plane.getNormal() returns a Vector3d or similar with x, y, z fields
					double planeNormalX = getThisNodePlane().getNormal().x;
					double planeNormalY = getThisNodePlane().getNormal().y;
					double planeNormalZ = getThisNodePlane().getNormal().z;

					// Compute dot product
					double dotMinus = (planeNormalX * diff_x) + (planeNormalY * diff_y) + (planeNormalZ * diff_z);

					// Compute scalar t
					// Paralell case where one point is slightly infront by the same amount that the
					// other is slightly behind. when summed, they make a point that is exactly on
					// the plane
					// therefor the intersection point is halfway between i and j
					double t = (d / dotMinus);
					if (!Double.isFinite(t) || t < 0 || t > 1.0) {
					    continue;
					}

					// Scale difference vector by tOld
					double sx = diff_x * t;
					double sy = diff_y * t;
					double sz = diff_z * t;

					// Compute interpolated point intrp = vi + scaled vector
					double lerp_x = xvi + sx;
					double lerp_y = yvi + sy;
					double lerp_z = zvi + sz;
					Vector3d intrp = new Vector3d(lerp_x, lerp_y, lerp_z);
//						double distPoly = polygon.getPlane().getDist();
//						double dotNP = normal.dot(intrp);
//						double tnp = dotNP- distPoly;
//						if(Math.abs(tnp)>Plane.getEPSILON()) {
//							throw new RuntimeException("New point doesnt lie on the plane of the split polygon!");
//						}else {
						addPoint(f, new Vertex(intrp));
						addPoint(b, new Vertex(intrp.clone()));
					//}
				}
			}
			add(front, f, polygon);
			add(back, b, polygon);
			break;
		}
	}

	// Remove all polygons in this BSP tree that are inside the other BSP tree
	// `bsp`.
	/**
	 * Removes all polygons in this BSP tree that are inside the specified BSP tree
	 * ({@code bsp}).
	 *
	 * Note: polygons are split if necessary.
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
		long size = count;
		build(polygons, 0, ((long)polygons.size())*size);
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
	public final long build(ArrayList<Polygon> polygons, long depth, long maxDepth) throws Exception {
		if (depth > maxDepth && maxDepth>0) {
			new RuntimeException("Impossible Node depth " + depth + " with " + polygons.size() + " remaining max = "+maxDepth ).printStackTrace();
		}

		
		if (polygons.isEmpty()) {

			return 0;
		}

//		if (this.getPlane() == null) {
//			this.setPlane(polygons.get(0).getPlane());
//		}
		// this.polygons.add(polygons.get(0));

		ArrayList<Polygon> frontP = new ArrayList<>();
		ArrayList<Polygon> backP = new ArrayList<>();

		// parellel version does not work here
		 List<Polygon> coplanarFront=this.polygons;
		 List<Polygon> coplanarBack=this.polygons;
		splitPolygon(polygons, coplanarFront, coplanarBack, frontP, backP);
//		if(this.polygons.size()==0) {
//			throw new RuntimeException("Binary Spacial Partitioning Tree step failed!");
//		}

		if (frontP.size() > 0) {
			if (this.front == null) {
				this.front = new Node(frontP.get(0).getPlane());
			}
			count+=this.front.build(frontP, depth + 1, maxDepth);
		}
		if (backP.size() > 0) {
			if (this.back == null) {
				this.back = new Node(backP.get(0).getPlane());
			}
			count+=this.back.build(backP, depth + 1, maxDepth);
		}
		return count;
	}

	public Plane getThisNodePlane() {
		return myNodePlane;
	}

//	public void setPlane(Plane plane) {
//		if (plane == null)
//			throw new RuntimeException("Plane can not be null!");
//		this.myNodePlane = plane.clone();
//	}
}

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
			ArrayList<Vertex> orderedPoints, 
			double [] polygonPointX,double [] polygonPointY,double [] polygonPointZ,
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
				Vertex v = new Vertex(new Vector3d(x/scale, y/scale, z/scale), polygon.plane.getNormal());
				addPoint(f, v);
			}
		}

		add(l, f, polygon);
	}

	private static boolean addPoint(List<Vertex> f, Vertex v) {
		for(int i=0;i<f.size();i++) {
			if(Math.abs( v.pos.distance(f.get(i).pos))<0.0001) {
				return false;
			}
		}
		return f.add(v);
	}

	private static void add(List<Polygon> l, List<Vertex> f, Polygon polygon) {
		if (f.size() < 3)
			return;
		Polygon fpoly=null;
		try {
			fpoly = new Polygon(f, polygon.getStorage(), true, new Plane(polygon.getPlane().getNormal(), f))
					.setColor(polygon.getColor());
			// test triangulation of new polygon before adding
			PolygonUtil.concaveToConvex(fpoly);
			l.add(fpoly);

		} catch (Exception ex) {
//			ex.printStackTrace();
			System.err.println("Pruning bad polygon Node::splitPolygon::add " + l+"\n\t"+fpoly);
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


		// Main conversion of your original code
		int numberOfPointsTmp = 0;
		int polygonNumber = polygons.size();
		int max = 0;
		int ExtraSpace = 70;
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
		    normalPolygonX[k] =(polygon.getPlane().getNormal().x);
		    normalPolygonY[k] = (polygon.getPlane().getNormal().y);
		    normalPolygonZ[k] = (polygon.getPlane().getNormal().z);
		    normalPolygonDistance[k] = (polygon.getPlane().getDist());
		    
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

		double epsilon =  Plane.getEPSILON();


		final int COPLANAR = 0;
		final int FRONT = 1;
		final int BACK = 2;
		final int SPANNING = 3;
		boolean[] memoryError = new boolean[]{false};
		Plane myPlane = this.plane;

		final class PolygonListManager {
		    // Polygon index data
		    final int[] mypolygonStartIndex;
		    final int[] mypolygonSize;
		    int[] space = new int[polygonNumber];
		    
		    // Plane information - fixed point
		    double planeNormalXInternal = planeNormalX;
		    double planeNormalYInternal = planeNormalY;
		    double planeNormalZInternal = planeNormalZ;
		    double planeNormalDistanceInternal = planeNormalDistance;
		    
		    // Point data - fixed point
		    double[] polygonPointXFixed = polygonPointX;
		    double[] polygonPointYFixed = polygonPointY;
		    double[] polygonPointZFixed = polygonPointZ;
		    
		    // Polygon Normals - fixed point
		    double[] normalPolygonXFixed = normalPolygonX;
		    double[] normalPolygonYFixed = normalPolygonY;
		    double[] normalPolygonZFixed = normalPolygonZ;
		    double[] normalPolygonDistanceFixed = normalPolygonDistance;
		    
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
		        if(polygonIndex == mypolygonSize.length - 1) {
		            if(ni >= pointsNumber) {
		                memoryError[0] = true;
		            }
		        } else {
		            if (ni + mypolygonStartIndex[polygonIndex] == mypolygonStartIndex[polygonIndex + 1]) {
		                memoryError[0] = true;
		            }
		        }
		    }
		    
		    int writeIncrementPoint(int polygonIndex, int source) {
		        int pointInPolygon = mypolygonSize[polygonIndex];
		        incrementSize(polygonIndex);
		        double x = polygonPointXFixed[source];
		        double y = polygonPointYFixed[source];
		        double z = polygonPointZFixed[source];
		        return writePoint(polygonIndex, pointInPolygon, x, y, z);
		    }

		    int interpolate(int polygonIndex, int vi, int vj) {
//		        double tol = Plane.getEPSILON();
//		      
//		        double dot = myPlane.getNormal().dot(vi2.pos);
//		        double dist = myPlane.getDist();
		        
		        // Fixed point computation of g = planeNormalDistanceInternal - planeDotPoint(polygonIndex, vi)
		        int globalIndex = getGlobalPointIndex(polygonIndex, vi);
		        double planeDot = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                      polygonPointXFixed[globalIndex], polygonPointYFixed[globalIndex], polygonPointZFixed[globalIndex]);
		        
		        double g = (planeNormalDistanceInternal - planeDot);
//		        double mydot = Double.longBitsToDouble(planeDot);
//		        double mydist = Double.longBitsToDouble(planeNormalDistanceInternal);
//		        double myD = mydist - mydot;
//		        double d = dist - dot;
//		        
//		        Vector3d minus = vj2.pos.minus(vi2.pos);
//		        double dotMinusOld = myPlane.getNormal().dot(minus);
//		        double tOld = d / dotMinusOld;
//		        Vector3d times = minus.times(tOld);
//		        Vector3d intrp = vi2.pos.plus(times);
		        
		        // Fixed point computation of dotMinus = planeDotPointMinusPoint(polygonIndex, vj, vi)
		        int globalVi = getGlobalPointIndex(polygonIndex, vi);
		        int globalVj = getGlobalPointIndex(polygonIndex, vj);
		        
		        double diff_x = (polygonPointXFixed[globalVj] - polygonPointXFixed[globalVi]);
		        double diff_y = (polygonPointYFixed[globalVj] - polygonPointYFixed[globalVi]);
		        double diff_z = (polygonPointZFixed[globalVj] - polygonPointZFixed[globalVi]);
		        
		        double dotMinus = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                      diff_x, diff_y, diff_z);
		        
		        // Fixed point division: t = g / dotMinus
		        double t = (g/ dotMinus);
		        
		        int pointInPolygon = mypolygonSize[polygonIndex];
		        incrementSize(polygonIndex);
		        
		        // Get fixed point coordinates
		        double xvi = polygonPointXFixed[globalVi];
		        double yvi = polygonPointYFixed[globalVi];
		        double zvi = polygonPointZFixed[globalVi];
		        
		        // Fixed point interpolation: lerp = vi + (vj - vi) * t
		        double lerp_x = (xvi + (diff_x* t));
		        double lerp_y = (yvi + (diff_y* t));
		        double lerp_z = (zvi + (diff_z* t));
		        
		        int ret = writePoint(polygonIndex, pointInPolygon, lerp_x, lerp_y, lerp_z);
		        
//		        double x = Double.longBitsToDouble(polygonPointXFixed[ret]);
//		        double y = Double.longBitsToDouble(polygonPointYFixed[ret]);
//		        double z = Double.longBitsToDouble(polygonPointZFixed[ret]);
		        
//		        double abs2 = Math.abs(intrp.x - (x / scale));
//		        double abs3 = Math.abs(intrp.y - (y / scale));
//		        double abs4 = Math.abs(intrp.z - (z / scale));
//		        
//		        if (abs2 > tol || abs3 > tol || abs4 > tol) {
//		            memoryError[0] = true;
//		            return -1;
//		        }
		        
//		        addPoint(f, new Vertex(intrp, vj2.normal));
//		        addPoint(b, new Vertex(intrp.clone(), vj2.normal));
		        return ret;
		    }
		    
		    // Fixed point dot product
		    private double dotProductFixed(double ax, double ay, double az, double bx, double by, double bz) {
		        double multiply = (az *bz);
				double multiply2 = (ay * by);
				double multiply3 = (ax * bx);
				double aBits = (multiply3 + multiply2);
				double l = (aBits + multiply);
				return l;
		    }
		    
		    int writePoint(int polygonIndex, int pointInPolygon, double x, double y, double z) {
		        int pointIndex = getPointIndex(polygonIndex, pointInPolygon);
		        polygonPointXFixed[pointIndex] = x;
		        polygonPointYFixed[pointIndex] = y;
		        polygonPointZFixed[pointIndex] = z;
		        return pointIndex;
		    }
		    
		    private int getGlobalPointIndex(int polygonIndex, int point) {
		        return polygonStartIndex[polygonIndex] + point;
		    }
		    
		    private int getPointIndex(int polygonIndex, int point) {
		        return mypolygonStartIndex[polygonIndex] + point;
		    }
		    
		    double polygonPointDistance(int polygonIndex, int pointIndex) {
		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
		        double dotResult = dotProductFixed(normalPolygonXFixed[polygonIndex], normalPolygonYFixed[polygonIndex], normalPolygonZFixed[polygonIndex],
		                                       polygonPointXFixed[globalIndex], polygonPointYFixed[globalIndex], polygonPointZFixed[globalIndex]);
		        
		        double result = (dotResult - normalPolygonDistanceFixed[polygonIndex]);
		        return (result);
		    }
		    
		    double planePointDistance(int polygonIndex, int pointIndex) {
		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
		        double dotResult = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                       polygonPointXFixed[globalIndex], polygonPointYFixed[globalIndex], polygonPointZFixed[globalIndex]);
		        
		        double result = (dotResult - planeNormalDistanceInternal);
		        return (result);
		    }
		    
		    double planeDotPolygonNormal(int polygonIndex) {
		    	double result = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                    normalPolygonXFixed[polygonIndex], normalPolygonYFixed[polygonIndex], normalPolygonZFixed[polygonIndex]);
		        return (result);
		    }
		}
		PolygonListManager polygonManager = new PolygonListManager(polygonStartIndex, polygonSize);
		PolygonListManager coplanarFrontManager = new PolygonListManager(coplanarFrontStartIndex, coplanarFrontSize);
		PolygonListManager coplanarBackManager = new PolygonListManager(coplanarBackStartIndex, coplanarBackSize);
		PolygonListManager frontManager = new PolygonListManager(frontStartIndex, frontSize);
		PolygonListManager backManager = new PolygonListManager(backStartIndex, backSize);
		boolean newalgo=true;

		for (int polygonIndex = 0; polygonIndex < polygons.size(); polygonIndex++) {
			if (memoryError[0])
				break;
			Polygon polygon = polygons.get(polygonIndex);

			// search for the epsilon values of the incoming plane
			double negEpsilon = -epsilon;
			double posEpsilon = epsilon;
			for (int i = 0; i < polygonManager.size(polygonIndex); i++) {
//				double tOld= polygon.getPlane().getNormal().dot(polygon.getVertices().get(i).pos);
//				double abs =Math.abs(tOld-t/scale);
//				if(abs>epsilon) {
////					memoryError[0]=true;
////					break;
//				}
				double t = (polygonManager.polygonPointDistance(polygonIndex, i) );
				if (t > posEpsilon) {
					// com.neuronrobotics.sdk.common.Log.error("Non flat polygon, increasing
					// positive epsilon "+t);
					posEpsilon = (float) (t + epsilon);
				}
				if (t < negEpsilon) {
					// com.neuronrobotics.sdk.common.Log.error("Non flat polygon, decreasing
					// negative epsilon "+t);
					negEpsilon = (float) (t - epsilon);
				}
			}
			int polygonType = COPLANAR;
			boolean somePointsInfront = false;
			boolean somePointsInBack = false;
			for (int i = 0; i < polygonManager.size(polygonIndex); i++) {

				double t = polygonManager.planePointDistance(polygonIndex, i);

//				double tOld = this.getPlane().getNormal().dot(polygon.getVertices().get(i).pos)
//						- this.getPlane().getDist();
//				double delta = Math.abs(tOld-t);
//				if(delta>epsilon) {
//					//throw new RuntimeException("Algorithm fail!");
//				}
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
			//newalgo=true;
					
			// Put the polygon in the correct list, splitting it when necessary.
			switch (polygonType) {
			case COPLANAR:
				if (polygonManager.planeDotPolygonNormal(polygonIndex) > 0) {
					coplanarFrontManager.copy(polygonIndex);
					if(!newalgo)coplanarFront.add(polygon);
				} else {
					coplanarBackManager.copy(polygonIndex);
					if(!newalgo)coplanarBack.add(polygon);
				}
				break;
			case FRONT:
				frontManager.copy(polygonIndex);
				if(!newalgo)front.add(polygon);
				break;
			case BACK:
				backManager.copy(polygonIndex);
				if(!newalgo)back.add(polygon);
				break;
			case SPANNING:
//				List<Vertex> f = new ArrayList<>();
//				List<Vertex> b = new ArrayList<>();
				int size = polygonManager.size(polygonIndex);
				int retF = frontManager.addPolygon(polygonIndex, size + 1);
				if (retF < 0) {
					memoryError[0] = true;
					break;
				}
				int retB = backManager.addPolygon(polygonIndex, size + 1);
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
//					int vjIndex = polygonManager.getPointIndex(polygonIndex, j);
					if (ti != BACK) {
						frontManager.writeIncrementPoint(polygonIndex, viIndex);
//						if(!newalgo)addPoint(f, vi);
					}
					if (ti != FRONT) {
//						if(!newalgo)addPoint(b,ti != BACK ? vi.clone() : vi);
						backManager.writeIncrementPoint(polygonIndex, viIndex);
					}
					if ((ti | tj) == SPANNING) {
						int v = frontManager.interpolate(polygonIndex, i, j);						
						if (memoryError[0])
							break;
						backManager.writeIncrementPoint(polygonIndex, v);
					}
				}
//				if(!newalgo)add(front, f,polygon);
//				if(!newalgo)add(back, b,polygon);
				break;
			}
		} // outer for loop of all polygons
		if(memoryError[0])
			throw new RuntimeException("Memory error here!");
		if(newalgo)
		// Collect the polygon data into the return structures
		for (int k = 0; k < polygonNumber; k++) {
			Polygon polygon = polygons.get(k);
			add(coplanarFront, k, coplanarFrontStartIndex, coplanarFrontSize, orderedPoints, 
					polygonPointX,polygonPointY, polygonPointZ,
					
					polygon);
			add(coplanarBack, k, coplanarBackStartIndex, coplanarBackSize, orderedPoints,
					polygonPointX,polygonPointY, polygonPointZ, polygon);
			add(front, k, frontStartIndex, frontSize, orderedPoints, 
					polygonPointX,polygonPointY, polygonPointZ,
					polygon);
			add(back, k,backStartIndex, backSize, orderedPoints,
					polygonPointX,polygonPointY, polygonPointZ, polygon);

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

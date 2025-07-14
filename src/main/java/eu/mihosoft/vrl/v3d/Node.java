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
	private static double scale = 1;
    private static final double DOUBLE_SPLITTER = 134217729.0; // 2^27 + 1
    private static final float FLOAT_SPLITTER = 8193.0f; 
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
	 * Split double array into separate hi and lo float arrays
	 * 
	 * @param doubles Input double array
	 * @param hi      Output high parts array (same length as doubles)
	 * @param lo      Output low parts array (same length as doubles)
	 */
	public static void splitDoubles(double doubles, float[] hi, float[] lo, int index) {

		double x = doubles;
		double temp = DOUBLE_SPLITTER * x;
		double hiPart = temp - (temp - x);
		hi[index] = (float) hiPart;
		lo[index] = (float) (x - hiPart);

	}

	/**
	 * Combine hi and lo float arrays back to double array
	 * 
	 * @param hi      High parts array
	 * @param lo      Low parts array
	 * @param doubles Output double array (same length as hi/lo)
	 */
	public static double combineDoubles(float[] hi, float[] lo, int i) {
		return (double) hi[i] + (double) lo[i];
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
		int ExtraSpace = 70;
		ArrayList<Vertex> orderedPoints = new ArrayList<Vertex>();
		int[] polygonStartIndex = new int[polygonNumber];
		int[] polygonSize = new int[polygonNumber];
		int[] newPointStartIndex = new int[polygonNumber];
		float[] h_NormalPolygonX = new float[polygonNumber];
		float[] h_NormalPolygonY = new float[polygonNumber];
		float[] h_NormalPolygonZ = new float[polygonNumber];
		float[] h_NormalPolygonDistance = new float[polygonNumber];
		float[] l_NormalPolygonX = new float[polygonNumber];
		float[] l_NormalPolygonY = new float[polygonNumber];
		float[] l_NormalPolygonZ = new float[polygonNumber];
		float[] l_NormalPolygonDistance = new float[polygonNumber];
		for (int k = 0; k < polygonNumber; k++) {
			Polygon polygon = polygons.get(k);
			List<Vertex> vertices = polygon.getVertices();
			int size = vertices.size();
			if (size > max)
				max = size;
			numberOfPointsTmp += size;
			polygonStartIndex[k] = orderedPoints.size();
			polygonSize[k] = size;
			splitDoubles(polygon.getPlane().getNormal().x, h_NormalPolygonX, l_NormalPolygonX, k);
			splitDoubles(polygon.getPlane().getNormal().y, h_NormalPolygonY, l_NormalPolygonY, k);
			splitDoubles(polygon.getPlane().getNormal().z, h_NormalPolygonZ, l_NormalPolygonZ, k);
			splitDoubles(polygon.getPlane().getDist(), h_NormalPolygonDistance, l_NormalPolygonDistance, k);
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
		float[] polygonPointX_h = new float[pointsNumber];
		float[] polygonPointY_h = new float[pointsNumber];
		float[] polygonPointZ_h = new float[pointsNumber];
		float[] polygonPointX_l = new float[pointsNumber];
		float[] polygonPointY_l = new float[pointsNumber];
		float[] polygonPointZ_l = new float[pointsNumber];
		for (; pointsEmptyIndex < orderedPoints.size(); pointsEmptyIndex++) {
			Vertex vertex = orderedPoints.get(pointsEmptyIndex);
			splitDoubles(vertex.getX(), polygonPointX_h, polygonPointX_l, pointsEmptyIndex);
			splitDoubles(vertex.getY(), polygonPointY_h, polygonPointY_l, pointsEmptyIndex);
			splitDoubles(vertex.getZ(), polygonPointZ_h, polygonPointZ_l, pointsEmptyIndex);
		}
		float []planeNormalX_h = new float[1];
		float []planeNormalY_h = new float[1];
		float []planeNormalZ_h = new float[1];
		float []planeNormalDistance_h =new float[1]; 
		float []planeNormalX_l = new float[1];
		float []planeNormalY_l = new float[1];
		float []planeNormalZ_l = new float[1];
		float []planeNormalDistance_l =new float[1];
		
		splitDoubles(this.plane.getNormal().x, planeNormalX_h, planeNormalX_l, 0);
		splitDoubles(this.plane.getNormal().y, planeNormalX_h, planeNormalX_l, 0);
		splitDoubles(this.plane.getNormal().z, planeNormalX_h, planeNormalX_l, 0);
		splitDoubles(this.plane.getDist(), planeNormalX_h, planeNormalX_l, 0);
		
		float epsilon = (float) Plane.getEPSILON();
		final int COPLANAR = 0;
		final int FRONT = 1;
		final int BACK = 2;
		final int SPANNING = 3; // == some in the FRONT + some in the BACK
		boolean []memoryError = new boolean[] {false};

		final class PolygonListManager {
		    // Polygon index data
		    final int[] mypolygonStartIndex;
		    final int[] mypolygonSize;
		    int[] space = new int[polygonNumber];
		    
		    // Plane information - high precision
		    float planeNormalXinternal_hi = planeNormalX_h[0];
		    float planeNormalXinternal_lo = planeNormalX_l[0];
		    float planeNormalYinternal_hi = planeNormalY_h[0];
		    float planeNormalYinternal_lo = planeNormalY_l[0];
		    float planeNormalZinternal_hi = planeNormalZ_h[0];
		    float planeNormalZinternal_lo = planeNormalZ_l[0];
		    float planeNormalDistanceInternal_hi = planeNormalDistance_h[0];
		    float planeNormalDistanceInternal_lo = planeNormalDistance_l[0];
		    
		    // Point data - high precision
		    float[] polygonPointX_hi = polygonPointX_h;
		    float[] polygonPointX_lo = polygonPointX_l;
		    float[] polygonPointY_hi = polygonPointY_h;
		    float[] polygonPointY_lo = polygonPointY_l;
		    float[] polygonPointZ_hi = polygonPointZ_h;
		    float[] polygonPointZ_lo = polygonPointZ_l;
		    
		    // Polygon Normals - high precision
		    float[] NormalPolygonX_hi = h_NormalPolygonX;
		    float[] NormalPolygonX_lo = l_NormalPolygonX;
		    float[] NormalPolygonY_hi = h_NormalPolygonY;
		    float[] NormalPolygonY_lo = l_NormalPolygonY;
		    float[] NormalPolygonZ_hi = h_NormalPolygonZ;
		    float[] NormalPolygonZ_lo = l_NormalPolygonZ;
		    float[] NormalPolygonDistance_hi = h_NormalPolygonDistance;
		    float[] NormalPolygonDistance_lo = l_NormalPolygonDistance;
		    		    
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

//		    float gx(int polygonIndex, int pointIndex) {
//		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
//		        return polygonPointX_hi[globalIndex] + polygonPointX_lo[globalIndex];
//		    }
//
//		    float gy(int polygonIndex, int pointIndex) {
//		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
//		        return polygonPointY_hi[globalIndex] + polygonPointY_lo[globalIndex];
//		    }
//
//		    float gz(int polygonIndex, int pointIndex) {
//		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
//		        return polygonPointZ_hi[globalIndex] + polygonPointZ_lo[globalIndex];
//		    }
//		    
//		    float x(int polygonIndex, int pointIndex) {
//		        int index = getPointIndex(polygonIndex, pointIndex);
//		        return polygonPointX_hi[index] + polygonPointX_lo[index];
//		    }
//
//		    float y(int polygonIndex, int pointIndex) {
//		        int index = getPointIndex(polygonIndex, pointIndex);
//		        return polygonPointY_hi[index] + polygonPointY_lo[index];
//		    }
//
//		    float z(int polygonIndex, int pointIndex) {
//		        int index = getPointIndex(polygonIndex, pointIndex);
//		        return polygonPointZ_hi[index] + polygonPointZ_lo[index];
//		    }
		    
		    int writeIncrementPoint(int polygonIndex, int source) {
		        int pointInPolygon = mypolygonSize[polygonIndex];
		        incrementSize(polygonIndex);
		        float x_hi = polygonPointX_hi[source];
		        float x_lo = polygonPointX_lo[source];
		        float y_hi = polygonPointY_hi[source];
		        float y_lo = polygonPointY_lo[source];
		        float z_hi = polygonPointZ_hi[source];
		        float z_lo = polygonPointZ_lo[source];
		        return writePoint(polygonIndex, pointInPolygon, x_hi, x_lo, y_hi, y_lo, z_hi, z_lo);
		    }

		    int interpolate(int polygonIndex, int vi, int vj) {
		        // High-precision computation of dotMinus = planeDotPointMinusPoint(polygonIndex, vj, vi)
		        float[] dotMinus = new float[2];
		        planeDotPointMinusPointHighPrecision(polygonIndex, vj, vi, dotMinus);
		        
		        // High-precision computation of g = planeNormalDistanceInternal - planeDotPoint(polygonIndex, vi)
		        float[] planeDot = new float[2];
		        planeDotPointHighPrecision(polygonIndex, vi, planeDot);
		        
		        float[] g = new float[2];
		        subtractHighPrecision(planeNormalDistanceInternal_hi, planeNormalDistanceInternal_lo, 
		                             planeDot[0], planeDot[1], g);
		        
		        // High-precision division: t = g / dotMinus
		        float[] t = new float[2];
		        divideHighPrecision(g[0], g[1], dotMinus[0], dotMinus[1], t);
		        
		        int pointInPolygon = mypolygonSize[polygonIndex];
		        incrementSize(polygonIndex);
		        if(memoryError[0])
		            return -1;
		            
		        // Get high-precision coordinates
		        int globalVi = getGlobalPointIndex(polygonIndex, vi);
		        int globalVj = getGlobalPointIndex(polygonIndex, vj);
		        
		        float xvi_hi = polygonPointX_hi[globalVi];
		        float xvi_lo = polygonPointX_lo[globalVi];
		        float yvi_hi = polygonPointY_hi[globalVi];
		        float yvi_lo = polygonPointY_lo[globalVi];
		        float zvi_hi = polygonPointZ_hi[globalVi];
		        float zvi_lo = polygonPointZ_lo[globalVi];
		        
		        float xvj_hi = polygonPointX_hi[globalVj];
		        float xvj_lo = polygonPointX_lo[globalVj];
		        float yvj_hi = polygonPointY_hi[globalVj];
		        float yvj_lo = polygonPointY_lo[globalVj];
		        float zvj_hi = polygonPointZ_hi[globalVj];
		        float zvj_lo = polygonPointZ_lo[globalVj];
		        
		        // High-precision interpolation: lerp = vi + (vj - vi) * t
		        float[] diff_x = new float[2];
		        float[] diff_y = new float[2];
		        float[] diff_z = new float[2];
		        
		        subtractHighPrecision(xvj_hi, xvj_lo, xvi_hi, xvi_lo, diff_x);
		        subtractHighPrecision(yvj_hi, yvj_lo, yvi_hi, yvi_lo, diff_y);
		        subtractHighPrecision(zvj_hi, zvj_lo, zvi_hi, zvi_lo, diff_z);
		        
		        float[] mult_x = new float[2];
		        float[] mult_y = new float[2];
		        float[] mult_z = new float[2];
		        
		        multiplyHighPrecision(diff_x[0], diff_x[1], t[0], t[1], mult_x);
		        multiplyHighPrecision(diff_y[0], diff_y[1], t[0], t[1], mult_y);
		        multiplyHighPrecision(diff_z[0], diff_z[1], t[0], t[1], mult_z);
		        
		        float[] lerp_x = new float[2];
		        float[] lerp_y = new float[2];
		        float[] lerp_z = new float[2];
		        
		        addHighPrecision(xvi_hi, xvi_lo, mult_x[0], mult_x[1], lerp_x);
		        addHighPrecision(yvi_hi, yvi_lo, mult_y[0], mult_y[1], lerp_y);
		        addHighPrecision(zvi_hi, zvi_lo, mult_z[0], mult_z[1], lerp_z);
		        
		        return writePoint(polygonIndex, pointInPolygon, lerp_x[0], lerp_x[1], lerp_y[0], lerp_y[1], lerp_z[0], lerp_z[1]);
		    }
		    
		    // High-precision arithmetic helper methods
		    private void addHighPrecision(float a_hi, float a_lo, float b_hi, float b_lo, float[] result) {
		        float s = a_hi + b_hi;
		        float v = s - a_hi;
		        float e = (a_hi - (s - v)) + (b_hi - v);
		        float t = a_lo + b_lo + e;
		        result[0] = s + t;
		        result[1] = t - (result[0] - s);
		    }
		    
		    private void subtractHighPrecision(float a_hi, float a_lo, float b_hi, float b_lo, float[] result) {
		        addHighPrecision(a_hi, a_lo, -b_hi, -b_lo, result);
		    }
		    
		    private void multiplyHighPrecision(float a_hi, float a_lo, float b_hi, float b_lo, float[] result) {
		        float p = a_hi * b_hi;
		        
		        float a_split = FLOAT_SPLITTER * a_hi;
		        float a_hi_hi = a_split - (a_split - a_hi);
		        float a_hi_lo = a_hi - a_hi_hi;
		        
		        float b_split = FLOAT_SPLITTER * b_hi;
		        float b_hi_hi = b_split - (b_split - b_hi);
		        float b_hi_lo = b_hi - b_hi_hi;
		        
		        float e = ((a_hi_hi * b_hi_hi - p) + a_hi_hi * b_hi_lo + a_hi_lo * b_hi_hi) + a_hi_lo * b_hi_lo;
		        e += a_hi * b_lo + a_lo * b_hi;
		        
		        result[0] = p + e;
		        result[1] = e - (result[0] - p);
		    }
		    
		    private void divideHighPrecision(float a_hi, float a_lo, float b_hi, float b_lo, float[] result) {
		        float q = a_hi / b_hi;
		        
		        float[] mult = new float[2];
		        multiplyHighPrecision(b_hi, b_lo, q, 0.0f, mult);
		        
		        float[] remainder = new float[2];
		        subtractHighPrecision(a_hi, a_lo, mult[0], mult[1], remainder);
		        
		        float correction = remainder[0] / b_hi;
		        result[0] = q + correction;
		        result[1] = correction - (result[0] - q);
		    }
		    
		    private void dotHighPrecision(float ax_hi, float ax_lo, float ay_hi, float ay_lo, float az_hi, float az_lo,
		                                 float bx_hi, float bx_lo, float by_hi, float by_lo, float bz_hi, float bz_lo,
		                                 float[] result) {
		        float[] prod_x = new float[2];
		        float[] prod_y = new float[2];
		        float[] prod_z = new float[2];
		        
		        multiplyHighPrecision(ax_hi, ax_lo, bx_hi, bx_lo, prod_x);
		        multiplyHighPrecision(ay_hi, ay_lo, by_hi, by_lo, prod_y);
		        multiplyHighPrecision(az_hi, az_lo, bz_hi, bz_lo, prod_z);
		        
		        float[] temp = new float[2];
		        addHighPrecision(prod_x[0], prod_x[1], prod_y[0], prod_y[1], temp);
		        addHighPrecision(temp[0], temp[1], prod_z[0], prod_z[1], result);
		    }
		    
//		    float dot(float ax, float ay, float az, float bx, float by, float bz) {
//		        // Convert to high precision and compute
//		        float[] result = new float[2];
//		        dotHighPrecision(ax, 0.0f, ay, 0.0f, az, 0.0f, bx, 0.0f, by, 0.0f, bz, 0.0f, result);
//		        return result[0] + result[1];
//		    }
//		    
//		    int writePoint(int polygonIndex, int pointInPolygon, float x, float y, float z) {
//		        int pointIndex = getPointIndex(polygonIndex, pointInPolygon);
//		        polygonPointX_hi[pointIndex] = x;
//		        polygonPointX_lo[pointIndex] = 0.0f;
//		        polygonPointY_hi[pointIndex] = y;
//		        polygonPointY_lo[pointIndex] = 0.0f;
//		        polygonPointZ_hi[pointIndex] = z;
//		        polygonPointZ_lo[pointIndex] = 0.0f;
//		        return pointIndex;
//		    }
		    
		    int writePoint(int polygonIndex, int pointInPolygon, float x_hi, float x_lo, float y_hi, float y_lo, float z_hi, float z_lo) {
		        int pointIndex = getPointIndex(polygonIndex, pointInPolygon);
		        polygonPointX_hi[pointIndex] = x_hi;
		        polygonPointX_lo[pointIndex] = x_lo;
		        polygonPointY_hi[pointIndex] = y_hi;
		        polygonPointY_lo[pointIndex] = y_lo;
		        polygonPointZ_hi[pointIndex] = z_hi;
		        polygonPointZ_lo[pointIndex] = z_lo;
		        return pointIndex;
		    }
		    
		    private int getGlobalPointIndex(int polygonIndex, int point) {
		        return polygonStartIndex[polygonIndex] + point;
		    }
		    
		    private int getPointIndex(int polygonIndex, int point) {
		        return mypolygonStartIndex[polygonIndex] + point;
		    }

//		    float polygonDotPoint(int polygonIndex, int pointIndex) {
//		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
//		        float[] result = new float[2];
//		        dotHighPrecision(NormalPolygonX_hi[polygonIndex], NormalPolygonX_lo[polygonIndex],
//		                        NormalPolygonY_hi[polygonIndex], NormalPolygonY_lo[polygonIndex],
//		                        NormalPolygonZ_hi[polygonIndex], NormalPolygonZ_lo[polygonIndex],
//		                        polygonPointX_hi[globalIndex], polygonPointX_lo[globalIndex],
//		                        polygonPointY_hi[globalIndex], polygonPointY_lo[globalIndex],
//		                        polygonPointZ_hi[globalIndex], polygonPointZ_lo[globalIndex], result);
//		        return result[0] + result[1];
//		    }
		    
		    float polygonPointDistance(int polygonIndex, int pointIndex) {
		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
		        float[] dotResult = new float[2];
		        dotHighPrecision(NormalPolygonX_hi[polygonIndex], NormalPolygonX_lo[polygonIndex],
		                        NormalPolygonY_hi[polygonIndex], NormalPolygonY_lo[polygonIndex],
		                        NormalPolygonZ_hi[polygonIndex], NormalPolygonZ_lo[polygonIndex],
		                        polygonPointX_hi[globalIndex], polygonPointX_lo[globalIndex],
		                        polygonPointY_hi[globalIndex], polygonPointY_lo[globalIndex],
		                        polygonPointZ_hi[globalIndex], polygonPointZ_lo[globalIndex], dotResult);
		        
		        float[] result = new float[2];
		        subtractHighPrecision(dotResult[0], dotResult[1], 
		                             NormalPolygonDistance_hi[polygonIndex], NormalPolygonDistance_lo[polygonIndex], result);
		        return result[0] + result[1];
		    }
		    
//		    float planeDotPoint(int polygonIndex, int pointIndex) {
//		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
//		        float[] result = new float[2];
//		        dotHighPrecision(planeNormalXinternal_hi, planeNormalXinternal_lo,
//		                        planeNormalYinternal_hi, planeNormalYinternal_lo,
//		                        planeNormalZinternal_hi, planeNormalZinternal_lo,
//		                        polygonPointX_hi[globalIndex], polygonPointX_lo[globalIndex],
//		                        polygonPointY_hi[globalIndex], polygonPointY_lo[globalIndex],
//		                        polygonPointZ_hi[globalIndex], polygonPointZ_lo[globalIndex], result);
//		        return result[0] + result[1];
//		    }
		    
		    private void planeDotPointHighPrecision(int polygonIndex, int pointIndex, float[] result) {
		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
		        dotHighPrecision(planeNormalXinternal_hi, planeNormalXinternal_lo,
		                        planeNormalYinternal_hi, planeNormalYinternal_lo,
		                        planeNormalZinternal_hi, planeNormalZinternal_lo,
		                        polygonPointX_hi[globalIndex], polygonPointX_lo[globalIndex],
		                        polygonPointY_hi[globalIndex], polygonPointY_lo[globalIndex],
		                        polygonPointZ_hi[globalIndex], polygonPointZ_lo[globalIndex], result);
		    }
		    
		    float planePointDistance(int polygonIndex, int pointIndex) {
		        float[] dotResult = new float[2];
		        planeDotPointHighPrecision(polygonIndex, pointIndex, dotResult);
		        
		        float[] result = new float[2];
		        subtractHighPrecision(dotResult[0], dotResult[1], 
		                             planeNormalDistanceInternal_hi, planeNormalDistanceInternal_lo, result);
		        return result[0] + result[1];
		    }
		    
//		    float planeDotPointMinusPoint(int polygonIndex, int vj, int vi) {
//		        float[] result = new float[2];
//		        planeDotPointMinusPointHighPrecision(polygonIndex, vj, vi, result);
//		        return result[0] + result[1];
//		    }
		    
		    private void planeDotPointMinusPointHighPrecision(int polygonIndex, int vj, int vi, float[] result) {
		        int globalVi = getGlobalPointIndex(polygonIndex, vi);
		        int globalVj = getGlobalPointIndex(polygonIndex, vj);
		        
		        float[] diff_x = new float[2];
		        float[] diff_y = new float[2];
		        float[] diff_z = new float[2];
		        
		        subtractHighPrecision(polygonPointX_hi[globalVj], polygonPointX_lo[globalVj],
		                             polygonPointX_hi[globalVi], polygonPointX_lo[globalVi], diff_x);
		        subtractHighPrecision(polygonPointY_hi[globalVj], polygonPointY_lo[globalVj],
		                             polygonPointY_hi[globalVi], polygonPointY_lo[globalVi], diff_y);
		        subtractHighPrecision(polygonPointZ_hi[globalVj], polygonPointZ_lo[globalVj],
		                             polygonPointZ_hi[globalVi], polygonPointZ_lo[globalVi], diff_z);
		        
		        dotHighPrecision(planeNormalXinternal_hi, planeNormalXinternal_lo,
		                        planeNormalYinternal_hi, planeNormalYinternal_lo,
		                        planeNormalZinternal_hi, planeNormalZinternal_lo,
		                        diff_x[0], diff_x[1], diff_y[0], diff_y[1], diff_z[0], diff_z[1], result);
		    }
		    
		    float planeDotPolygonNormal(int polygonIndex) {
		        float[] result = new float[2];
		        dotHighPrecision(planeNormalXinternal_hi, planeNormalXinternal_lo,
		                        planeNormalYinternal_hi, planeNormalYinternal_lo,
		                        planeNormalZinternal_hi, planeNormalZinternal_lo,
		                        NormalPolygonX_hi[polygonIndex], NormalPolygonX_lo[polygonIndex],
		                        NormalPolygonY_hi[polygonIndex], NormalPolygonY_lo[polygonIndex],
		                        NormalPolygonZ_hi[polygonIndex], NormalPolygonZ_lo[polygonIndex], result);
		        return result[0] + result[1];
		    }
		}
		PolygonListManager polygonManager = new PolygonListManager(polygonStartIndex, polygonSize);
		PolygonListManager coplanarFrontManager = new PolygonListManager(coplanarFrontStartIndex, coplanarFrontSize);
		PolygonListManager coplanarBackManager = new PolygonListManager(coplanarBackStartIndex, coplanarBackSize);
		PolygonListManager frontManager = new PolygonListManager(frontStartIndex, frontSize);
		PolygonListManager backManager = new PolygonListManager(backStartIndex, backSize);
		boolean newalgo=false;

		for (int polygonIndex = 0; polygonIndex < polygons.size(); polygonIndex++) {
			if (memoryError[0])
				break;
			Polygon polygon = polygons.get(polygonIndex);

			// search for the epsilon values of the incoming plane
			float negEpsilon = -epsilon;
			float posEpsilon = epsilon;
			for (int i = 0; i < polygonManager.size(polygonIndex); i++) {
				double tOld= polygon.getPlane().getNormal().dot(polygon.getVertices().get(i).pos);
				double t = (polygonManager.polygonPointDistance(polygonIndex, i) );
				double abs =Math.abs(tOld-t/scale);
				if(abs>epsilon) {
//					memoryError[0]=true;
//					break;
				}
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

				float t = polygonManager.planePointDistance(polygonIndex, i);

				double tOld = this.getPlane().getNormal().dot(polygon.getVertices().get(i).pos)
						- this.getPlane().getDist();
				double delta = Math.abs(tOld-t);
				if(delta>epsilon) {
					//throw new RuntimeException("Algorithm fail!");
				}
				int type = (tOld < negEpsilon) ? BACK : (tOld > posEpsilon) ? FRONT : COPLANAR;
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
				List<Vertex> f = new ArrayList<>();
				List<Vertex> b = new ArrayList<>();
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
					Vertex vi = polygon.getVertices().get(i);
					Vertex vj = polygon.getVertices().get(j);
					int viIndex = polygonManager.getPointIndex(polygonIndex, i);
					int vjIndex = polygonManager.getPointIndex(polygonIndex, j);
					if (ti != BACK) {
						frontManager.writeIncrementPoint(polygonIndex, viIndex);
						if(!newalgo)addPoint(f, vi);
					}
					if (ti != FRONT) {
						if(!newalgo)addPoint(b,ti != BACK ? vi.clone() : vi);
						backManager.writeIncrementPoint(polygonIndex, viIndex);
					}
					if ((ti | tj) == SPANNING) {
						double tol = 0.001;

						
						double d = this.getPlane().getDist() - this.getPlane().getNormal().dot(vi.pos);
						double dotMinusOld = this.getPlane().getNormal().dot(vj.pos.minus(vi.pos));
						double tOld =0;
							tOld =d / dotMinusOld;

						Vertex vOld = vi.interpolate(vj, tOld);

						int v = frontManager.interpolate(polygonIndex, i, j);
						double x = combineDoubles(polygonPointX_h, polygonPointX_l, v);
						double y = combineDoubles(polygonPointY_h, polygonPointY_l, v);
						double z = combineDoubles(polygonPointZ_h, polygonPointZ_l, v);
						double abs2 = Math.abs(vOld.pos.x-(x/scale));
						double abs3 = Math.abs(vOld.pos.y-(y/scale));
						double abs4 = Math.abs(vOld.pos.z-(z/scale));
						if(	abs2>tol||
							abs3>tol||
							abs4>tol) {
							memoryError[0]=true;
							break;
						}
						
						if (memoryError[0])
							break;
						backManager.writeIncrementPoint(polygonIndex, v);
						if(!newalgo)addPoint(f, vOld);
						if(!newalgo)addPoint(b,vOld.clone());
					}
				}
				if(!newalgo)add(front, f,polygon);
				if(!newalgo)add(back, b,polygon);
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
					polygonPointX_h,polygonPointY_h, polygonPointZ_h,
					polygonPointX_l,polygonPointY_l, polygonPointZ_l,
					polygon);
			add(coplanarBack, k, coplanarBackStartIndex, coplanarBackSize, orderedPoints,
					polygonPointX_h,polygonPointY_h, polygonPointZ_h,
					polygonPointX_l,polygonPointY_l, polygonPointZ_l, polygon);
			add(front, k, frontStartIndex, frontSize, orderedPoints, 
					polygonPointX_h,polygonPointY_h, polygonPointZ_h,
					polygonPointX_l,polygonPointY_l, polygonPointZ_l,
					polygon);
			add(coplanarFront, k, coplanarFrontStartIndex, coplanarFrontSize, orderedPoints,
					polygonPointX_h,polygonPointY_h, polygonPointZ_h,
					polygonPointX_l,polygonPointY_l, polygonPointZ_l, polygon);

		}
	}

	private static void add(List<Polygon> l, int polygonIndex, int[] polygonStartIndex, int[] polygonSize,
			ArrayList<Vertex> orderedPoints, 
			float[] polygonXh, float[] polygonYh, float[] polygonZh,
			float[] polygonXl, float[] polygonYl, float[] polygonZl, 
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
				double x = combineDoubles(polygonXh, polygonXl, i);
				double y = combineDoubles(polygonYh, polygonYl, i);
				double z = combineDoubles(polygonZh, polygonZl, i);
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

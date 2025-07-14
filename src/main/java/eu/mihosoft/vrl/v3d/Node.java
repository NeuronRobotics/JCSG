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
    private static final float FLOAT_SPLITTER = 4097.0f; 

    private static final double DOUBLE_SPLITTER = 134217729.0; // 2^27 + 1
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

	// Fixed point math configuration
	private static final int FIXED_POINT_SCALE = 12; // 16.16 fixed point format
	private static final int FIXED_ONE = 1 << FIXED_POINT_SCALE; // 65536
	private static final int FIXED_HALF = FIXED_ONE >> 1; // 32768

	// Fixed point utility methods
	private static int floatToFixed(double value) {
	    return (int)(Math.round(value * FIXED_ONE ));
	}

	private static double fixedToDouble(int fixed) {
	    return ((double)fixed) / FIXED_ONE;
	}

	// 32-bit only multiplication with overflow protection
	private static int fixedMultiply(int a, int b) {
	    // Split into high and low 16-bit parts
	    int a_hi = a >> 16;
	    int a_lo = a & 0xFFFF;
	    int b_hi = b >> 16;
	    int b_lo = b & 0xFFFF;
	    
	    // Perform partial products
	    int mid1 = a_hi * b_lo;
	    int mid2 = a_lo * b_hi;
	    int low = a_lo * b_lo;
	    
	    // Combine results (the high part a_hi * b_hi is shifted out)
	    int result = (mid1 + mid2) + (low >> 16);
	    return result;
	}

	// 32-bit only division with overflow protection
	private static int fixedDivide(int a, int b) {
	    // Check for division by zero
	    if (b == 0) return (a >= 0) ? Integer.MAX_VALUE : Integer.MIN_VALUE;
	    
	    // Handle signs
	    boolean negative = (a < 0) ^ (b < 0);
	    int abs_a = (a < 0) ? -a : a;
	    int abs_b = (b < 0) ? -b : b;
	    
	    // Shift dividend left by scale bits using 32-bit arithmetic
	    // We need to be careful about overflow when shifting
	    int shifted_a;
	    if (abs_a > (Integer.MAX_VALUE >> FIXED_POINT_SCALE)) {
	        // Would overflow, so we need to do partial division
	        int high_part = abs_a >> (32 - FIXED_POINT_SCALE);
	        int low_part = abs_a & ((1 << (32 - FIXED_POINT_SCALE)) - 1);
	        shifted_a = (high_part / abs_b) << FIXED_POINT_SCALE;
	        shifted_a += (low_part << FIXED_POINT_SCALE) / abs_b;
	    } else {
	        shifted_a = (abs_a << FIXED_POINT_SCALE) / abs_b;
	    }
	    
	    return negative ? -shifted_a : shifted_a;
	}
	private static void add(List<Polygon> l, int polygonIndex, int[] polygonStartIndex, int[] polygonSize,
			ArrayList<Vertex> orderedPoints, 
			int [] polygonPointX,int [] polygonPointY,int [] polygonPointZ,
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
				double x = fixedToDouble(polygonPointX[i]);
				double y = fixedToDouble(polygonPointY[i]);
				double z = fixedToDouble(polygonPointZ[i]);
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
		int[] normalPolygonX = new int[polygonNumber];
		int[] normalPolygonY = new int[polygonNumber];
		int[] normalPolygonZ = new int[polygonNumber];
		int[] normalPolygonDistance = new int[polygonNumber];

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
		    normalPolygonX[k] = floatToFixed(polygon.getPlane().getNormal().x);
		    normalPolygonY[k] = floatToFixed(polygon.getPlane().getNormal().y);
		    normalPolygonZ[k] = floatToFixed(polygon.getPlane().getNormal().z);
		    normalPolygonDistance[k] = floatToFixed(polygon.getPlane().getDist());
		    
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
		int[] polygonPointX = new int[pointsNumber];
		int[] polygonPointY = new int[pointsNumber];
		int[] polygonPointZ = new int[pointsNumber];

		for (; pointsEmptyIndex < orderedPoints.size(); pointsEmptyIndex++) {
		    Vertex vertex = orderedPoints.get(pointsEmptyIndex);
		    polygonPointX[pointsEmptyIndex] = floatToFixed(vertex.getX());
		    polygonPointY[pointsEmptyIndex] = floatToFixed(vertex.getY());
		    polygonPointZ[pointsEmptyIndex] = floatToFixed(vertex.getZ());
		}

		// Convert plane normal to fixed point
		int planeNormalX = floatToFixed(this.plane.getNormal().x);
		int planeNormalY = floatToFixed(this.plane.getNormal().y);
		int planeNormalZ = floatToFixed(this.plane.getNormal().z);
		int planeNormalDistance = floatToFixed(this.plane.getDist());

		float epsilon = (float) Plane.getEPSILON();
		int fixedEpsilon = floatToFixed(epsilon);

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
		    int planeNormalXInternal = planeNormalX;
		    int planeNormalYInternal = planeNormalY;
		    int planeNormalZInternal = planeNormalZ;
		    int planeNormalDistanceInternal = planeNormalDistance;
		    
		    // Point data - fixed point
		    int[] polygonPointXFixed = polygonPointX;
		    int[] polygonPointYFixed = polygonPointY;
		    int[] polygonPointZFixed = polygonPointZ;
		    
		    // Polygon Normals - fixed point
		    int[] normalPolygonXFixed = normalPolygonX;
		    int[] normalPolygonYFixed = normalPolygonY;
		    int[] normalPolygonZFixed = normalPolygonZ;
		    int[] normalPolygonDistanceFixed = normalPolygonDistance;
		    
		    PolygonListManager(int[] polygonStartIndex, int[] polygonSize) {
		        this.mypolygonStartIndex = polygonStartIndex;
		        this.mypolygonSize = polygonSize;
		        for (int i = 0; i < polygonNumber; i++) {
		            space[i] = ExtraSpace;
		        }
		    }
		    float fixedToFloat(int fixed) {
			    return ((float)fixed) / FIXED_ONE;
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
		        int x = polygonPointXFixed[source];
		        int y = polygonPointYFixed[source];
		        int z = polygonPointZFixed[source];
		        return writePoint(polygonIndex, pointInPolygon, x, y, z);
		    }

		    int interpolate(int polygonIndex, int vi, int vj, Vertex vi2, Vertex vj2, List<Vertex> f, List<Vertex> b) {
		        double tol = 0.0001;
		        int fixedTol = floatToFixed((float)tol);
		        
		        double dot = myPlane.getNormal().dot(vi2.pos);
		        double dist = myPlane.getDist();
		        
		        // Fixed point computation of g = planeNormalDistanceInternal - planeDotPoint(polygonIndex, vi)
		        int globalIndex = getGlobalPointIndex(polygonIndex, vi);
		        int planeDot = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                      polygonPointXFixed[globalIndex], polygonPointYFixed[globalIndex], polygonPointZFixed[globalIndex]);
		        
		        int g = planeNormalDistanceInternal - planeDot;
		        
		        double mydist = fixedToDouble(planeNormalDistanceInternal);
		        double mydot = fixedToDouble(planeDot);
		        double myD = mydist - mydot;
		        double d = dist - dot;
		        
		        Vector3d minus = vj2.pos.minus(vi2.pos);
		        double dotMinusOld = myPlane.getNormal().dot(minus);
		        double tOld = d / dotMinusOld;
		        Vector3d times = minus.times(tOld);
		        Vector3d intrp = vi2.pos.plus(times);
		        
		        // Fixed point computation of dotMinus = planeDotPointMinusPoint(polygonIndex, vj, vi)
		        int globalVi = getGlobalPointIndex(polygonIndex, vi);
		        int globalVj = getGlobalPointIndex(polygonIndex, vj);
		        
		        int diff_x = polygonPointXFixed[globalVj] - polygonPointXFixed[globalVi];
		        int diff_y = polygonPointYFixed[globalVj] - polygonPointYFixed[globalVi];
		        int diff_z = polygonPointZFixed[globalVj] - polygonPointZFixed[globalVi];
		        
		        int dotMinus = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                      diff_x, diff_y, diff_z);
		        
		        // Fixed point division: t = g / dotMinus
		        int t = fixedDivide(g, dotMinus);
		        
		        int pointInPolygon = mypolygonSize[polygonIndex];
		        incrementSize(polygonIndex);
		        
		        // Get fixed point coordinates
		        int xvi = polygonPointXFixed[globalVi];
		        int yvi = polygonPointYFixed[globalVi];
		        int zvi = polygonPointZFixed[globalVi];
		        
		        int xvj = polygonPointXFixed[globalVj];
		        int yvj = polygonPointYFixed[globalVj];
		        int zvj = polygonPointZFixed[globalVj];
		        
		        // Fixed point interpolation: lerp = vi + (vj - vi) * t
		        int lerp_x = xvi + fixedMultiply(diff_x, t);
		        int lerp_y = yvi + fixedMultiply(diff_y, t);
		        int lerp_z = zvi + fixedMultiply(diff_z, t);
		        
		        int ret = writePoint(polygonIndex, pointInPolygon, lerp_x, lerp_y, lerp_z);
		        
		        double x = fixedToDouble(polygonPointXFixed[ret]);
		        double y = fixedToDouble(polygonPointYFixed[ret]);
		        double z = fixedToDouble(polygonPointZFixed[ret]);
		        
		        double abs2 = Math.abs(intrp.x - (x / scale));
		        double abs3 = Math.abs(intrp.y - (y / scale));
		        double abs4 = Math.abs(intrp.z - (z / scale));
		        
		        if (abs2 > tol || abs3 > tol || abs4 > tol) {
		            memoryError[0] = true;
		            return -1;
		        }
		        
		        addPoint(f, new Vertex(intrp, vj2.normal));
		        addPoint(b, new Vertex(intrp.clone(), vj2.normal));
		        return ret;
		    }
		    
		    // Fixed point dot product
		    private int dotProductFixed(int ax, int ay, int az, int bx, int by, int bz) {
		        return fixedMultiply(ax, bx) + fixedMultiply(ay, by) + fixedMultiply(az, bz);
		    }
		    
		    int writePoint(int polygonIndex, int pointInPolygon, int x, int y, int z) {
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
		    
		    float polygonPointDistance(int polygonIndex, int pointIndex) {
		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
		        int dotResult = dotProductFixed(normalPolygonXFixed[polygonIndex], normalPolygonYFixed[polygonIndex], normalPolygonZFixed[polygonIndex],
		                                       polygonPointXFixed[globalIndex], polygonPointYFixed[globalIndex], polygonPointZFixed[globalIndex]);
		        
		        int result = dotResult - normalPolygonDistanceFixed[polygonIndex];
		        return fixedToFloat(result);
		    }
		    
		    float planePointDistance(int polygonIndex, int pointIndex) {
		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
		        int dotResult = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                       polygonPointXFixed[globalIndex], polygonPointYFixed[globalIndex], polygonPointZFixed[globalIndex]);
		        
		        int result = dotResult - planeNormalDistanceInternal;
		        return fixedToFloat(result);
		    }
		    
		    float planeDotPolygonNormal(int polygonIndex) {
		        int result = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                    normalPolygonXFixed[polygonIndex], normalPolygonYFixed[polygonIndex], normalPolygonZFixed[polygonIndex]);
		        return fixedToFloat(result);
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
						int v = frontManager.interpolate(polygonIndex, i, j,vi,vj,f,b);						
						if (memoryError[0])
							break;
						backManager.writeIncrementPoint(polygonIndex, v);
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
					polygonPointX,polygonPointY, polygonPointZ,
					
					polygon);
			add(coplanarBack, k, coplanarBackStartIndex, coplanarBackSize, orderedPoints,
					polygonPointX,polygonPointY, polygonPointZ, polygon);
			add(front, k, frontStartIndex, frontSize, orderedPoints, 
					polygonPointX,polygonPointY, polygonPointZ,
					polygon);
			add(coplanarFront, k, coplanarFrontStartIndex, coplanarFrontSize, orderedPoints,
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

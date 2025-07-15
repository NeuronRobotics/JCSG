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
			long [] polygonPointX,long [] polygonPointY,long [] polygonPointZ,
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
				double x = Double.longBitsToDouble(polygonPointX[i]);
				double y = Double.longBitsToDouble(polygonPointY[i]);
				double z = Double.longBitsToDouble(polygonPointZ[i]);
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
		long[] normalPolygonX = new long[polygonNumber];
		long[] normalPolygonY = new long[polygonNumber];
		long[] normalPolygonZ = new long[polygonNumber];
		long[] normalPolygonDistance = new long[polygonNumber];

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
		    normalPolygonX[k] = Double.doubleToLongBits(polygon.getPlane().getNormal().x);
		    normalPolygonY[k] = Double.doubleToLongBits(polygon.getPlane().getNormal().y);
		    normalPolygonZ[k] = Double.doubleToLongBits(polygon.getPlane().getNormal().z);
		    normalPolygonDistance[k] = Double.doubleToLongBits(polygon.getPlane().getDist());
		    
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
		long[] polygonPointX = new long[pointsNumber];
		long[] polygonPointY = new long[pointsNumber];
		long[] polygonPointZ = new long[pointsNumber];

		for (; pointsEmptyIndex < orderedPoints.size(); pointsEmptyIndex++) {
		    Vertex vertex = orderedPoints.get(pointsEmptyIndex);
		    polygonPointX[pointsEmptyIndex] = Double.doubleToLongBits(vertex.getX());
		    polygonPointY[pointsEmptyIndex] = Double.doubleToLongBits(vertex.getY());
		    polygonPointZ[pointsEmptyIndex] = Double.doubleToLongBits(vertex.getZ());
		}

		// Convert plane normal to fixed point
		long planeNormalX = Double.doubleToLongBits(this.plane.getNormal().x);
		long planeNormalY = Double.doubleToLongBits(this.plane.getNormal().y);
		long planeNormalZ = Double.doubleToLongBits(this.plane.getNormal().z);
		long planeNormalDistance = Double.doubleToLongBits(this.plane.getDist());

		float epsilon = (float) Plane.getEPSILON();
		long fixedEpsilon = Double.doubleToLongBits(epsilon);

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
		    long planeNormalXInternal = planeNormalX;
		    long planeNormalYInternal = planeNormalY;
		    long planeNormalZInternal = planeNormalZ;
		    long planeNormalDistanceInternal = planeNormalDistance;
		    
		    // Point data - fixed point
		    long[] polygonPointXFixed = polygonPointX;
		    long[] polygonPointYFixed = polygonPointY;
		    long[] polygonPointZFixed = polygonPointZ;
		    
		    // Polygon Normals - fixed point
		    long[] normalPolygonXFixed = normalPolygonX;
		    long[] normalPolygonYFixed = normalPolygonY;
		    long[] normalPolygonZFixed = normalPolygonZ;
		    long[] normalPolygonDistanceFixed = normalPolygonDistance;
		    
		    PolygonListManager(int[] polygonStartIndex, int[] polygonSize) {
		        this.mypolygonStartIndex = polygonStartIndex;
		        this.mypolygonSize = polygonSize;
		        for (int i = 0; i < polygonNumber; i++) {
		            space[i] = ExtraSpace;
		        }
		    }
		    
		    /**
		     * Extract sign bit from long
		     */
		     boolean getSign(long bits) {
		        return (bits & SIGN_MASK) != 0;
		    }
		    
		    /**
		     * Extract exponent from long
		     */
		    int getExponent(long bits) {
		        return (int)((bits & EXPONENT_MASK_LONG) >>> 52);
		    }
		    
		    /**
		     * Extract mantissa from long
		     */
		     long getMantissa(long bits) {
		        return bits & MANTISSA_MASK;
		    }
		    
		     /**
		      * Create IEEE 754 double from components
		      */
		     long createBits(boolean sign, int exponent, long mantissa) {
		         long result = 0;

		         if (sign) {
		             result |= SIGN_MASK;  // 0x8000000000000000L
		         }

		         // The exponent should be 11 bits (0-2047), not masked with EXPONENT_MASK
		         // EXPONENT_MASK is used for EXTRACTING the exponent, not for creating it
		         result |= ((long)exponent << 52);  // Remove the mask here!
		         result |= (mantissa & MANTISSA_MASK);

		         return result;
		     }
		    
		    /**
		     * Check if value is zero
		     */
		    boolean isZero(long bits) {
		        return (bits & 0x7FFFFFFFFFFFFFFFL) == 0;
		    }
		    
		    /**
		     * Check if value is infinity
		     */
		     boolean isInfinity(long bits) {
		        return ((bits & EXPONENT_MASK_LONG) == EXPONENT_MASK_LONG) && 
		               ((bits & MANTISSA_MASK) == 0);
		    }
		    
//		    /**
//		     * Check if value is NaN
//		     */
//		    boolean isNaN(long bits) {
//		        return ((bits & EXPONENT_MASK_LONG) == EXPONENT_MASK_LONG) && 
//		               ((bits & MANTISSA_MASK) != 0);
//		    }
//		    
		    /**
		     * Normalize mantissa and adjust exponent
		     */
		     int normalize(long[] mantissa, int exponent) {
		        if (mantissa[0] == 0) {
		            return 0; // Zero
		        }
		        
		        // Find leading bit
		        int leadingZeros = 0;
		        long temp = mantissa[0];
		        
		        if ((temp & 0xFFF0000000000000L) == 0) {
		            leadingZeros += 12;
		            temp <<= 12;
		        }
		        if ((temp & 0xFC00000000000000L) == 0) {
		            leadingZeros += 6;
		            temp <<= 6;
		        }
		        if ((temp & 0xF000000000000000L) == 0) {
		            leadingZeros += 4;
		            temp <<= 4;
		        }
		        if ((temp & 0xC000000000000000L) == 0) {
		            leadingZeros += 2;
		            temp <<= 2;
		        }
		        if ((temp & 0x8000000000000000L) == 0) {
		            leadingZeros += 1;
		        }
		        
		        mantissa[0] <<= leadingZeros;
		        return exponent - leadingZeros;
		    }
		    
		    /**
		     * Add two double values represented as long bits
		     */
		    long add(long aBits, long bBits) {
		        //return softFloatAdd(aBits, bBits);
		    	 double a = Double.longBitsToDouble(aBits);
		    	 double b = Double.longBitsToDouble(bBits);
			     //long result = softFloatDiv(aBits, bBits);
		    	 //double res = Double.longBitsToDouble(result);
		         double calc = a+b;
		         long cbits = Double.doubleToLongBits(calc);
//		         double abs = Math.abs(res-calc);
//		         if(result!=cbits) {
//		        	 System.out.println("Failed multiply!");
//		         }
				return cbits;
		    }

			private long softFloatAdd(long aBits, long bBits) {
				// Handle special cases
		        if (isZero(aBits)) {
		            return bBits;
		        }
		        if (isZero(bBits)) {
		            return aBits;
		        }
		        if (isNaN(aBits) || isNaN(bBits)) {
		            return 0x7FF8000000000000L; // NaN
		        }
		        
		        boolean aSign = getSign(aBits);
		        boolean bSign = getSign(bBits);
		        int aExp = getExponent(aBits);
		        int bExp = getExponent(bBits);
		        long aMant = getMantissa(aBits);
		        long bMant = getMantissa(bBits);
		        
		        // Add implicit leading bit for normalized numbers
		        if (aExp != 0) aMant |= IMPLICIT_ONE;
		        if (bExp != 0) bMant |= IMPLICIT_ONE;
		        
		        // Align mantissas
		        int expDiff = aExp - bExp;
		        if (expDiff > 0) {
		            if (expDiff >= 64) {
		                bMant = 0;
		            } else {
		                bMant >>>= expDiff;
		            }
		        } else if (expDiff < 0) {
		            if (-expDiff >= 64) {
		                aMant = 0;
		                aExp = bExp;
		                aSign = bSign;
		            } else {
		                aMant >>>= -expDiff;
		                aExp = bExp;
		            }
		        }
		        
		        // Perform addition or subtraction
		        long resultMant;
		        boolean resultSign;
		        
		        if (aSign == bSign) {
		            // Same sign: add
		            resultMant = aMant + bMant;
		            resultSign = aSign;
		        } else {
		            // Different signs: subtract
		            if (aMant >= bMant) {
		                resultMant = aMant - bMant;
		                resultSign = aSign;
		            } else {
		                resultMant = bMant - aMant;
		                resultSign = bSign;
		            }
		        }
		        
		        // Handle zero result
		        if (resultMant == 0) {
		            return 0L;
		        }
		        
		        // Normalize result
		        int resultExp = aExp;
		        if ((resultMant & 0xFFE0000000000000L) != 0) {
		            // Overflow: shift right
		            resultMant >>>= 1;
		            resultExp++;
		        } else {
		            // Find leading bit and shift left
		            long[] mantArray = {resultMant};
		            resultExp = normalize(mantArray, resultExp);
		            resultMant = mantArray[0];
		        }
		        
		        // Check for exponent overflow/underflow
		        if (resultExp >= 0x7FF) {
		            return createBits(resultSign, 0x7FF, 0); // Infinity
		        }
		        if (resultExp <= 0) {
		            return createBits(resultSign, 0, 0); // Zero (underflow)
		        }
		        
		        // Remove implicit leading bit
		        resultMant &= MANTISSA_MASK;
		        
		        return createBits(resultSign, resultExp, resultMant);
			}
		    
		    /**
		     * Subtract two double values represented as long bits
		     */
		     long subtract(long aBits, long bBits) {
		    	 double a = Double.longBitsToDouble(aBits);
		    	 double b = Double.longBitsToDouble(bBits);
			     //long result = softFloatDiv(aBits, bBits);
		    	 //double res = Double.longBitsToDouble(result);
		         double calc = a-b;
		         long cbits = Double.doubleToLongBits(calc);
//		         double abs = Math.abs(res-calc);
//		         if(result!=cbits) {
//		        	 System.out.println("Failed multiply!");
//		         }
				return cbits;
//		        return softFLoatSub(aBits, bBits);
		    }

			private long softFLoatSub(long aBits, long bBits) {
				// Flip sign of b and add
		        long negBBits = bBits ^ SIGN_MASK;
		        return add(aBits, negBBits);
			}
		    int numberOfLeadingZeros(long i) {
		         // HD, Count leading 0's
		         if (i <= 0)
		             return i == 0 ? 32 : 0;
		         int n = 63;
		         if (i >= 1 << 32) { n -= 32; i >>>= 32; }
		         if (i >= 1 << 16) { n -= 16; i >>>= 16; }
		         if (i >= 1 <<  8) { n -=  8; i >>>=  8; }
		         if (i >= 1 <<  4) { n -=  4; i >>>=  4; }
		         if (i >= 1 <<  2) { n -=  2; i >>>=  2; }
		         return (int) (n - (i >>> 1));
		     }
		     /**
		      * Multiply two double values represented as long bits
		      */
		     long multiply(long aBits, long bBits) {
		    	 double a = Double.longBitsToDouble(aBits);
		    	 double b = Double.longBitsToDouble(bBits);
		   
		         //long result = f64_mulRaw( aBits,  bBits);
		    	 //double res = Double.longBitsToDouble(result);
		         double calc = a*b;
		         long cbits = Double.doubleToLongBits(calc);
//		         double abs = Math.abs(res-calc);
//		         if(result!=cbits) {
//		        	 System.out.println("Failed multiply!");
//		         }
		         return cbits;
		     }

		     long f64_mulRaw(long uiA, long uiB) {
		    	    final int EXP_MASK = 0x7FF;
		    	    final long FRAC_MASK = 0xFFFFFFFFFFFFFL;
		    	    final int BIAS = 0x3FF;
		    	    
		    	    boolean signA = (uiA >>> 63) != 0;
		    	    boolean signB = (uiB >>> 63) != 0;
		    	    boolean signZ = signA ^ signB;
		    	    int expA = (int) ((uiA >>> 52) & EXP_MASK);
		    	    int expB = (int) ((uiB >>> 52) & EXP_MASK);
		    	    long fracA = uiA & FRAC_MASK;
		    	    long fracB = uiB & FRAC_MASK;

		    	    // Special cases: NaN, infinities, zero * inf invalid case
		    	    if (expA == EXP_MASK) {
		    	        if ((fracA != 0) || (expB == EXP_MASK && fracB != 0)) return propagateNaN(uiA, uiB);
		    	        if (expB == 0 && fracB == 0) return defaultNaN();
		    	        return pack(signZ, EXP_MASK, 0);
		    	    }
		    	    if (expB == EXP_MASK) {
		    	        if (fracB != 0) return propagateNaN(uiA, uiB);
		    	        if (expA == 0 && fracA == 0) return defaultNaN();
		    	        return pack(signZ, EXP_MASK, 0);
		    	    }

		    	    // Handle zero operands
		    	    if ((expA == 0 && fracA == 0) || (expB == 0 && fracB == 0)) {
		    	        return pack(signZ, 0, 0);
		    	    }

		    	    // Normalize subnormals and add implicit bit
		    	    if (expA == 0) {
		    	        int shift = Long.numberOfLeadingZeros(fracA) - (64 - 53);
		    	        fracA <<= shift;
		    	        expA = 1 - shift;
		    	    } else {
		    	        fracA |= 1L << 52;
		    	    }
		    	    
		    	    if (expB == 0) {
		    	        int shift = Long.numberOfLeadingZeros(fracB) - (64 - 53);
		    	        fracB <<= shift;
		    	        expB = 1 - shift;
		    	    } else {
		    	        fracB |= 1L << 52;
		    	    }

		    	    // Compute result exponent
		    	    int expZ = expA + expB - BIAS;

		    	    // Multiply significands (53 bits × 53 bits = 106 bits)
		    	    long[] back=multiply128(fracA, fracB);
		    	    long hi = back[0];
		    	    long lo = back[1];

		    	    // The result is in hi:lo (106 bits total)
		    	    // We need to normalize to get the most significant 53 bits
		    	    long zFrac;
		    	    boolean sticky = false;
		    	    
		    	    if ((hi & (1L << 63)) != 0) {
		    	        // Result is already normalized (>= 2.0)
		    	        zFrac = hi;
		    	        sticky = (lo != 0);
		    	    } else {
		    	        // Result needs left shift by 1 (< 2.0)
		    	        zFrac = (hi << 1) | (lo >>> 63);
		    	        sticky = (lo & 0x7FFFFFFFFFFFFFFFL) != 0;
		    	        expZ--;
		    	    }

		    	    // Handle overflow
		    	    if (expZ >= EXP_MASK) {
		    	        return pack(signZ, EXP_MASK, 0);
		    	    }

		    	    // Handle underflow (subnormal results)
		    	    if (expZ <= 0) {
		    	        if (expZ < -52) {
		    	            return pack(signZ, 0, 0);
		    	        }
		    	        
		    	        // Shift right for subnormal
		    	        int shift = 1 - expZ;
		    	        if (shift >= 64) {
		    	            return pack(signZ, 0, 0);
		    	        }
		    	        
		    	        // Preserve sticky bits during right shift
		    	        long shiftMask = (1L << shift) - 1;
		    	        sticky |= (zFrac & shiftMask) != 0;
		    	        zFrac >>>= shift;
		    	        expZ = 0;
		    	    }

		    	    // Round to nearest, ties to even
		    	    // The rounding boundary is at bit 11 (counting from bit 0)
		    	    long roundBit = 1L << 11;
		    	    long roundMask = roundBit - 1;
		    	    boolean guard = (zFrac & roundBit) != 0;
		    	    sticky |= (zFrac & roundMask) != 0;
		    	    
		    	    // Extract the 53-bit significand
		    	    long significand = zFrac >>> 12;
		    	    
		    	    // Apply rounding
		    	    if (guard && (sticky || (significand & 1L) != 0)) {
		    	        significand++;
		    	        
		    	        if (expZ == 0) {
		    	            // Subnormal case - check if we became normal
		    	            if ((significand & (1L << 52)) != 0) {
		    	                expZ = 1;
		    	                significand &= FRAC_MASK;
		    	            }
		    	        } else {
		    	            // Normal case - check for overflow
		    	            if ((significand & (1L << 53)) != 0) {
		    	                significand >>>= 1;
		    	                expZ++;
		    	                if (expZ >= EXP_MASK) {
		    	                    return pack(signZ, EXP_MASK, 0);
		    	                }
		    	            }
		    	        }
		    	    }

		    	    // Final result
		    	    long fracZ = significand & FRAC_MASK;
		    	    return pack(signZ, expZ, fracZ);
		    	}

		    	    // Multiply two 64-bit numbers and return 128-bit result as [hi, lo]
		    	     long[] multiply128(long a, long b) {
		    	        // Split into 32-bit parts
		    	        long a0 = a & 0xFFFFFFFFL;
		    	        long a1 = a >>> 32;
		    	        long b0 = b & 0xFFFFFFFFL;
		    	        long b1 = b >>> 32;
		    	        
		    	        // Compute partial products
		    	        long p0 = a0 * b0;
		    	        long p1 = a0 * b1;
		    	        long p2 = a1 * b0;
		    	        long p3 = a1 * b1;
		    	        
		    	        // Combine partial products
		    	        long middle = (p0 >>> 32) + (p1 & 0xFFFFFFFFL) + (p2 & 0xFFFFFFFFL);
		    	        long hi = p3 + (p1 >>> 32) + (p2 >>> 32) + (middle >>> 32);
		    	        long lo = (middle << 32) + (p0 & 0xFFFFFFFFL);
		    	        
		    	        return new long[]{hi, lo};
		    	    }

		    	long pack(boolean sign, int exp, long frac) {
		    	    return ((sign ? 1L : 0L) << 63)
		    	         | ((long) (exp & 0x7FF) << 52)
		    	         | (frac & 0xFFFFFFFFFFFFFL);
		    	}

		    	long defaultNaN() { 
		    	    return 0x7FF8000000000000L; 
		    	}

		    	boolean isNaN(long ui) {
		    	    return (((ui >>> 52) & 0x7FF) == 0x7FF) && ((ui & 0xFFFFFFFFFFFFFL) != 0);
		    	}

		    	long propagateNaN(long a, long b) {
		    	    if (isNaN(a)) return a | (1L << 51);
		    	    if (isNaN(b)) return b | (1L << 51);
		    	    return defaultNaN();
		    	}
		    /**
		     * Divide two double values represented as long bits
		     */
		     long divide(long aBits, long bBits) {
		    	 double a = Double.longBitsToDouble(aBits);
		    	 double b = Double.longBitsToDouble(bBits);
			     //long result = softFloatDiv(aBits, bBits);
		    	 //double res = Double.longBitsToDouble(result);
		         double calc = a/b;
		         long cbits = Double.doubleToLongBits(calc);
//		         double abs = Math.abs(res-calc);
//		         if(result!=cbits) {
//		        	 System.out.println("Failed multiply!");
//		         }
				return cbits;
		    }

			private long softFloatDiv(long aBits, long bBits) {
				// Handle special cases
		        if (isZero(bBits)) {
		            // Division by zero
		            boolean sign = getSign(aBits) ^ getSign(bBits);
		            return createBits(sign, 0x7FF, 0); // Infinity
		        }
		        if (isZero(aBits)) {
		            boolean sign = getSign(aBits) ^ getSign(bBits);
		            return createBits(sign, 0, 0);
		        }
		        if (isNaN(aBits) || isNaN(bBits)) {
		            return 0x7FF8000000000000L; // NaN
		        }
		        
		        boolean aSign = getSign(aBits);
		        boolean bSign = getSign(bBits);
		        int aExp = getExponent(aBits);
		        int bExp = getExponent(bBits);
		        long aMant = getMantissa(aBits);
		        long bMant = getMantissa(bBits);
		        
		        // Add implicit leading bit for normalized numbers
		        if (aExp != 0) aMant |= IMPLICIT_ONE;
		        if (bExp != 0) bMant |= IMPLICIT_ONE;
		        
		        // Calculate result sign and exponent
		        boolean resultSign = aSign ^ bSign;
		        int resultExp = aExp - bExp + EXPONENT_BIAS;
		        
		        // Divide mantissas using long division
		        long dividend = aMant;
		        long divisor = bMant;
		        long quotient = 0;
		        
		        // Align dividend and divisor for division
		        if (dividend < divisor) {
		            dividend <<= 1;
		            resultExp--;
		        }
		        
		        // Perform long division
		        for (int i = 0; i < 53; i++) {
		            quotient <<= 1;
		            if (dividend >= divisor) {
		                dividend -= divisor;
		                quotient |= 1;
		            }
		            dividend <<= 1;
		        }
		        
		        // Normalize result
		        if ((quotient & 0x8000000000000000L) != 0) {
		            quotient >>>= 1;
		            resultExp++;
		        } else {
		            long[] mantArray = {quotient};
		            resultExp = normalize(mantArray, resultExp);
		            quotient = mantArray[0];
		        }
		        
		        // Check for exponent overflow/underflow
		        if (resultExp >= 0x7FF) {
		            return createBits(resultSign, 0x7FF, 0); // Infinity
		        }
		        if (resultExp <= 0) {
		            return createBits(resultSign, 0, 0); // Zero (underflow)
		        }
		        
		        // Remove implicit leading bit
		        quotient &= MANTISSA_MASK;
		        
		        return createBits(resultSign, resultExp, quotient);
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
		        long x = polygonPointXFixed[source];
		        long y = polygonPointYFixed[source];
		        long z = polygonPointZFixed[source];
		        return writePoint(polygonIndex, pointInPolygon, x, y, z);
		    }

		    int interpolate(int polygonIndex, int vi, int vj) {
//		        double tol = Plane.getEPSILON();
//		      
//		        double dot = myPlane.getNormal().dot(vi2.pos);
//		        double dist = myPlane.getDist();
		        
		        // Fixed point computation of g = planeNormalDistanceInternal - planeDotPoint(polygonIndex, vi)
		        int globalIndex = getGlobalPointIndex(polygonIndex, vi);
		        long planeDot = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                      polygonPointXFixed[globalIndex], polygonPointYFixed[globalIndex], polygonPointZFixed[globalIndex]);
		        
		        long g = subtract(planeNormalDistanceInternal , planeDot);
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
		        
		        long diff_x = subtract(polygonPointXFixed[globalVj] , polygonPointXFixed[globalVi]);
		        long diff_y = subtract(polygonPointYFixed[globalVj] , polygonPointYFixed[globalVi]);
		        long diff_z = subtract(polygonPointZFixed[globalVj] , polygonPointZFixed[globalVi]);
		        
		        long dotMinus = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                      diff_x, diff_y, diff_z);
		        
		        // Fixed point division: t = g / dotMinus
		        long t = divide(g, dotMinus);
		        
		        int pointInPolygon = mypolygonSize[polygonIndex];
		        incrementSize(polygonIndex);
		        
		        // Get fixed point coordinates
		        long xvi = polygonPointXFixed[globalVi];
		        long yvi = polygonPointYFixed[globalVi];
		        long zvi = polygonPointZFixed[globalVi];
		        
		        // Fixed point interpolation: lerp = vi + (vj - vi) * t
		        long lerp_x = add(xvi , multiply(diff_x, t));
		        long lerp_y = add(yvi , multiply(diff_y, t));
		        long lerp_z = add(zvi , multiply(diff_z, t));
		        
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
		    private long dotProductFixed(long ax, long ay, long az, long bx, long by, long bz) {
		        long multiply = multiply(az, bz);
				long multiply2 = multiply(ay, by);
				long multiply3 = multiply(ax, bx);
				long aBits = add(multiply3 , multiply2);
				long l = add(aBits , multiply);
//				double dax= Double.longBitsToDouble(ax);
//				double day= Double.longBitsToDouble(ay);
//				double daz= Double.longBitsToDouble(az);
//				double dbx= Double.longBitsToDouble(bx);
//				double dby= Double.longBitsToDouble(by);
//				double dbz= Double.longBitsToDouble(bz);
//				
//				double m1 =Double.longBitsToDouble(multiply); 
//
//				double m2 =Double.longBitsToDouble(multiply2);
//
//				double m3 =Double.longBitsToDouble(multiply3);
//
//				double a =Double.longBitsToDouble(aBits);
//
//				double ld =Double.longBitsToDouble(l);
				return l;
		    }
		    
		    int writePoint(int polygonIndex, int pointInPolygon, long x, long y, long z) {
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
		        long dotResult = dotProductFixed(normalPolygonXFixed[polygonIndex], normalPolygonYFixed[polygonIndex], normalPolygonZFixed[polygonIndex],
		                                       polygonPointXFixed[globalIndex], polygonPointYFixed[globalIndex], polygonPointZFixed[globalIndex]);
		        
		        long result = subtract(dotResult , normalPolygonDistanceFixed[polygonIndex]);
		        return fixedToFloat(result);
		    }
		    
		    float planePointDistance(int polygonIndex, int pointIndex) {
		        int globalIndex = getGlobalPointIndex(polygonIndex, pointIndex);
		        long dotResult = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                       polygonPointXFixed[globalIndex], polygonPointYFixed[globalIndex], polygonPointZFixed[globalIndex]);
		        
		        long result = subtract(dotResult , planeNormalDistanceInternal);
		        return fixedToFloat(result);
		    }
		    
		    float planeDotPolygonNormal(int polygonIndex) {
		    	long result = dotProductFixed(planeNormalXInternal, planeNormalYInternal, planeNormalZInternal,
		                                    normalPolygonXFixed[polygonIndex], normalPolygonYFixed[polygonIndex], normalPolygonZFixed[polygonIndex]);
		        return fixedToFloat(result);
		    }
		    /**
		     * Convert a stored double (as long bits) to float without using Double/Float classes
		     * Manually implements IEEE 754 double to float conversion
		     */
		    float fixedToFloat(long doubleBits) {
		        // Handle special cases first
		        if (isZero(doubleBits)) {
		            return (doubleBits & SIGN_MASK) != 0 ? -0.0f : 0.0f;
		        }
		        if (isNaN(doubleBits)) {
		            return Float.NaN;
		        }
		        if (isInfinity(doubleBits)) {
		            return (doubleBits & SIGN_MASK) != 0 ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
		        }
		        
		        // Extract double components
		        boolean sign = (doubleBits & SIGN_MASK) != 0;
		        int doubleExp = (int)((doubleBits & EXPONENT_MASK_LONG) >>> 52);
		        long doubleMant = doubleBits & MANTISSA_MASK;
		        
		        // Convert exponent from double bias (1023) to float bias (127)
		        int floatExp = doubleExp - EXPONENT_BIAS + 127;
		        
		        // Handle exponent overflow (too large for float)
		        if (floatExp >= 255) {
		            // Return infinity
		            int floatBits = sign ? 0xFF800000 : 0x7F800000;
		            return Float.intBitsToFloat(floatBits);
		        }
		        
		        // Handle exponent underflow (too small for float)
		        if (floatExp <= 0) {
		            // Handle subnormal numbers
		            if (floatExp > -24) {
		                // Can represent as subnormal
		                long mantissa = doubleMant;
		                if (doubleExp != 0) {
		                    mantissa |= IMPLICIT_ONE; // Add implicit bit for normalized double
		                }
		                
		                // Shift mantissa for subnormal representation
		                int shift = 52 - 23 + (1 - floatExp);
		                mantissa >>>= shift;
		                
		                int floatBits = (sign ? 0x80000000 : 0) | (int)mantissa;
		                return Float.intBitsToFloat(floatBits);
		            } else {
		                // Too small, return zero
		                return sign ? -0.0f : 0.0f;
		            }
		        }
		        
		        // Normal case: convert mantissa from 52 bits to 23 bits
		        long floatMant = doubleMant >>> (52 - 23); // Take top 23 bits
		        
		        // Handle rounding (round to nearest, ties to even)
		        long roundBit = (doubleMant >>> (52 - 23 - 1)) & 1;
		        long stickyBits = doubleMant & ((1L << (52 - 23 - 1)) - 1);
		        
		        if (roundBit != 0 && (stickyBits != 0 || (floatMant & 1) != 0)) {
		            floatMant++;
		            
		            // Check for mantissa overflow
		            if (floatMant >= (1L << 23)) {
		                floatMant = 0;
		                floatExp++;
		                
		                // Check for exponent overflow after rounding
		                if (floatExp >= 255) {
		                    int floatBits = sign ? 0xFF800000 : 0x7F800000;
		                    return Float.intBitsToFloat(floatBits);
		                }
		            }
		        }
		        
		        // Construct float bits
		        int floatBits = 0;
		        if (sign) {
		            floatBits |= 0x80000000;
		        }
		        floatBits |= (floatExp & 0xFF) << 23;
		        floatBits |= (int)(floatMant & 0x7FFFFF);
		        
		        return Float.intBitsToFloat(floatBits);
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

/**
 * Vector3d.java
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

import static java.lang.Math.abs;
import static java.lang.Math.acos;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Random;

import com.google.gson.annotations.Expose;

//  Auto-generated Javadoc
/**
 * 3D Vector3d.
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public class Vector3d extends javax.vecmath.Vector3d {
    

    private static  String exportString = "%.10f";

	private static  double EXPORTEPSILON =1.0e-10;

	/**
	 * 
	 */
	private static final long serialVersionUID = 1878117798187075166L;

	/** The Constant ZERO. */
    public static final Vector3d ZERO = new Vector3d(0, 0, 0);
    
    /** The Constant UNITY. */
    public static final Vector3d UNITY = new Vector3d(1, 1, 1);
    
    /** The Constant X_ONE. */
    public static final Vector3d X_ONE = new Vector3d(1, 0, 0);
    
    /** The Constant Y_ONE. */
    public static final Vector3d Y_ONE = new Vector3d(0, 1, 0);
    
    /** The Constant Z_ONE. */
    public static final Vector3d Z_ONE = new Vector3d(0, 0, 1);

    /**
     * Creates a new vector.
     *
     * @param x x value
     * @param y y value
     * @param z z value
     */
    public Vector3d(double x, double y, double z) {
//    	if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) {
//    		throw new NumberFormatException("Vectors must be real "+x+" "+y+" "+z);
//    	}
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public Vector3d(Number x, Number y, Number z) {
    	this(x.doubleValue(),y.doubleValue(),z.doubleValue());
    }


    /**
     * Creates a new vector with specified {@code x}, {@code y} and
     * {@code z = 0}.
     *
     * @param x x value
     * @param y y value
     */
    public Vector3d(double x, double y) {
    	this(x, y, (double) 0);
    }
    
    public Vector3d(Number x, Number y) {
    	this(x, y, (double) 0);
    }


    /**
     * Creates a new vector with specified {@code x}, {@code y} and
     * {@code z = 0}.
     *
     * @param x x value
     * @param y y value
     */
    public static Vector3d xy(double x, double y) {
        return new Vector3d(x,y);
    }
    public static Vector3d xy(Number x, Number y) {
        return xy(x.doubleValue(),y.doubleValue());
    }

    /**
     * Creates a new vector with specified {@code x}, {@code y} and
     * {@code z}.
     *
     * @param x x value
     * @param y y value
     * @param z z value
     */
    public static Vector3d xyz(double x, double y, double z) {
        return new Vector3d(x,y,z);
    }
    public static Vector3d xyz(Number x, Number y, Number z) {
        return new Vector3d(x,y,z);
    }
    @Override
    public Vector3d clone() {
        return new Vector3d(x, y, z);
    }

    /**
     * Returns a negated copy of this vector.
     *
     *  Note:  this vector is not modified.
     *
     * @return a negated copy of this vector
     */
    public Vector3d negated() {
        return new Vector3d(-x, -y, -z);
    }

    /**
     * Returns the sum of this vector and the specified vector.
     *
     * @param v the vector to add
     *
     *  Note:  this vector is not modified.
     *
     * @return the sum of this vector and the specified vector
     */
    public Vector3d plus(Vector3d v) {
        return new Vector3d(x + v.x, y + v.y, z + v.z);
    }

    /**
     * Returns the difference of this vector and the specified vector.
     *
     * @param v the vector to subtract
     *
     *  Note:  this vector is not modified.
     *
     * @return the difference of this vector and the specified vector
     */
    public Vector3d minus(Vector3d v) {
        return new Vector3d(x - v.x, y - v.y, z - v.z);
    }

    /**
     * Returns the product of this vector and the specified value.
     *
     * @param a the value
     *
     *  Note:  this vector is not modified.
     *
     * @return the product of this vector and the specified value
     */
    public Vector3d times(double a) {
        return new Vector3d(x * a, y * a, z * a);
    }

  
    /**
     * Returns the product of this vector and the specified vector.
     *
     * @param a the vector
     *
     *  Note:  this vector is not modified.
     *
     * @return the product of this vector and the specified vector
     */
    public Vector3d times(Vector3d a) {
        return new Vector3d(x * a.x, y * a.y, z * a.z);
    }

    /**
     * Returns this vector devided by the specified value.
     *
     * @param a the value
     *
     *  Note:  this vector is not modified.
     *
     * @return this vector devided by the specified value
     */
    public Vector3d dividedBy(double a) {
        return new Vector3d(x / a, y / a, z / a);
    }

    /**
     * Returns the dot product of this vector and the specified vector.
     *
     *  Note:  this vector is not modified.
     *
     * @param a the second vector
     *
     * @return the dot product of this vector and the specified vector
     */
    public double dot(Vector3d a) {
        return this.x * a.x + this.y * a.y + this.z * a.z;
    }

    /**
     * Linearly interpolates between this and the specified vector.
     *
     *  Note:  this vector is not modified.
     *
     * @param a vector
     * @param t interpolation value
     *
     * @return copy of this vector if {@code t = 0}; copy of a if {@code t = 1};
     * the point midway between this and the specified vector if {@code t = 0.5}
     */
    public Vector3d lerp(Vector3d a, double t) {
        return this.plus(a.minus(this).times(t));
    }

    /**
     * Returns the magnitude of this vector.
     *
     *  Note:  this vector is not modified.
     *
     * @return the magnitude of this vector
     */
    public double magnitude() {
        return Math.sqrt(this.dot(this));
    }

    /**
     * Returns the squared magnitude of this vector (<code>this.dot(this)</code>).
     *
     *  Note:  this vector is not modified.
     *
     * @return the squared magnitude of this vector
     */
    double magnitudeSq() {
        return this.dot(this);
    }

    /**
     * Returns a normalized copy of this vector with length {@code 1}.
     *
     *  Note:  this vector is not modified.
     *
     * @return a normalized copy of this vector with length {@code 1}
     */
    public Vector3d normalized() {
        return this.dividedBy(this.magnitude());
    }

    /**
     * Returns the cross product of this vector and the specified vector.
     *
     *  Note:  this vector is not modified.
     *
     * @param a the vector
     *
     * @return the cross product of this vector and the specified vector.
     */
    public Vector3d cross(Vector3d a) {
        return new Vector3d(
                this.y * a.z - this.z * a.y,
                this.z * a.x - this.x * a.z,
                this.x * a.y - this.y * a.x
        );
    }

    /**
     * Returns this vector in STL string format.
     *
     * @return this vector in STL string format
     */
    public String toStlString() {
        return toStlString(new StringBuilder()).toString();
    }

    /**
     * Returns this vector in STL string format.
     *
     * @param sb string builder
     * @return the specified string builder
     */
    public StringBuilder toStlString(StringBuilder sb) {
        double ep = getEXPORTEPSILON();
		return sb.append(roundedValue(x, ep)).append(" ").
                append(roundedValue(y, ep)).append(" ").
                append(roundedValue(z, ep));
    }

    
    public Vector3d roundToEpsilon(double ep) {
    	x=roundToEpsilon(x, ep);
    	y=roundToEpsilon(y, ep);
    	z=roundToEpsilon(z,ep);
    	return this;
    }
    
    /**
     * Returns this vector in OBJ string format.
     *
     * @return this vector in OBJ string format
     */
    public String toObjString() {
        return toObjString(new StringBuilder()).toString();
    }

    /**
     * Returns this vector in OBJ string format.
     *
     * @param sb string builder
     * @return the specified string builder
     */
    public StringBuilder toObjString(StringBuilder sb) {
        double ep = getEXPORTEPSILON();
		return sb.append(roundedValue(x, ep)).append(" ").
                append(roundedValue(y, ep)).append(" ").
                append(roundedValue(z, ep));
    }
    /**
     * Rounds a double value to the nearest multiple of epsilon.
     * 
     * @param value The value to round
     * @return The rounded value
     */
    private double roundToEpsilon(double value,double epsilon) {
        // Round to nearest multiple of epsilon
        return  ((double)Math.round(value / epsilon)) * epsilon;
    }
	private String roundedValue(double v,double ep) {
		return String.format(Locale.US,getExportString(), roundToEpsilon(v,ep));
	}

    /**
     * Applies the specified transformation to this vector.
     *
     * @param transform the transform to apply
     *
     * @return this vector
     */
    public Vector3d transform(Transform transform) {
        return transform.transform(this);
    }

    /**
     * Returns a transformed copy of this vector.
     *
     * @param transform the transform to apply
     *
     *  Note:  this vector is not modified.
     *
     * @return a transformed copy of this vector
     */
    public Vector3d transformed(Transform transform) {
        return clone().transform(transform);
    }

    /**
     * Applies the specified transformation to this vector.
     *
     * @param transform the transform to apply
     * @param amount the amount
     * @return this vector
     */
    public Vector3d transform(Transform transform, double amount) {
        return transform.transform(this, amount);
    }

    /**
     * Returns a transformed copy of this vector.
     *
     * @param transform the transform to apply
     * 
     *  Note:  this vector is not modified.
     * @param amount the amount
     * @return a transformed copy of this vector
     */
    public Vector3d transformed(Transform transform, double amount) {
        return clone().transform(transform, amount);
    }

    public String toString() {
    	return "["+toStlString().replaceAll(" ", " , ")+"]";
    }
    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
    	if(this==obj)
    		return true;
//    	if(Vertex.class.isInstance(obj)) {
//    		return equals(((Vertex)obj).pos);
//    	}
    	if (!Vector3d.class.isInstance(obj)) {
    		System.err.println("Test fail, "+obj.getClass()+" is not a Vector3d");
            return false;
        }
        return test((Vector3d)obj,Plane.getEPSILON());
    }
    
    double distance(Vector3d v) {
    	Vector3d diff = v.minus(this);
    	return diff.magnitude();
    }
    public boolean test(Vector3d obj) {
    	return test(obj, Plane.getEPSILON());
    }
	public boolean test(Vector3d obj, double epsilon) {
		if(this==obj)
			return true;
		if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        
        final Vector3d other = (Vector3d) obj;
        Vector3d diff = other.minus(this);
    	double distance= diff.magnitude();
        double abs = Math.abs(distance);
		if(abs>epsilon)
        	return false;
        return true;
	}

    /**
     * Returns the angle between this and the specified vector.
     *
     * @param v vector
     * @return angle in radians
     */
    public double angle(Vector3d v) {
        double val = this.dot(v) / (this.magnitude() * v.magnitude());
        return acos(max(min(val, 1), -1)); // compensate rounding errors
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + (int) (Double.doubleToLongBits(this.x) ^ (Double.doubleToLongBits(this.x) >>> 32));
        hash = 97 * hash + (int) (Double.doubleToLongBits(this.y) ^ (Double.doubleToLongBits(this.y) >>> 32));
        hash = 97 * hash + (int) (Double.doubleToLongBits(this.z) ^ (Double.doubleToLongBits(this.z) >>> 32));
        return hash;
    }

    /**
 * Creates a new vector with specified {@code x}.
 *
 * @param x x value
 * @return a new vector {@code [x,0,0]}
 */
    public static Vector3d x(double x) {
        return new Vector3d(x, 0, 0);
    }

    /**
     * Creates a new vector with specified {@code y}.
     *
     * @param y y value
     * @return a new vector {@code [0,y,0]}
     */
    public static Vector3d y(double y) {
        return new Vector3d(0, y, 0);
    }

    /**
     * Creates a new vector with specified {@code z}.
     *
     * @param z z value
     * @return a new vector {@code [0,0,z]}
     */
    public static Vector3d z(double z) {
        return new Vector3d(0, 0, z);
    }

	public static String getExportString() {
		return exportString;
	}

	 static void setExportString(String exportString) {
		Vector3d.exportString = exportString;
	}

	public static double getEXPORTEPSILON() {
		return EXPORTEPSILON;
	}

	 static void setEXPORTEPSILON(double eXPORTEPSILON) {
		if(eXPORTEPSILON<1.0e-5)
			EXPORTEPSILON = eXPORTEPSILON;
	}

}

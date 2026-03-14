/**
 * Transform.java
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

import javax.vecmath.Matrix4d;
import javax.vecmath.Quat4d;

//  Auto-generated Javadoc
/**
 * Transform. Transformations (translation, rotation, scale) can be applied to
 * geometrical objects like {@link CSG}, {@link Polygon}, {@link Vertex} and
 * {@link Vector3d}.
 *
 * This transform class uses the builder pattern to define combined
 * transformations.<br>
 * <br>
 *
 * Example:
 *
 *
 * // t applies rotation and translation Transform t = new
 * Transform().rotX(45).translate(2,1,0);
 *
 *
 * TODO: use quaternions for rotations.
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public class Transform implements Serializable {

	private static final long serialVersionUID = 3248601462585606936L;
	/**
	 * Internal 4x4 matrix.
	 */
	private final Matrix4d m;

	/**
	 * Constructor.
	 *
	 * Creates a unit transform.
	 */
	public Transform() {
		m = new Matrix4d();
		getInternalMatrix().m00 = 1;
		getInternalMatrix().m11 = 1;
		getInternalMatrix().m22 = 1;
		getInternalMatrix().m33 = 1;
	}

	/**
	 * Returns a new unity transform.
	 *
	 * @return unity transform
	 */
	public static Transform unity() {
		return new Transform();
	}

	/**
	 * Constructor.
	 *
	 * @param m
	 *            matrix
	 */
	public Transform(Matrix4d m) {
		this.m = m;
	}

	/**
	 * Applies rotation operation around the x axis to this transform.
	 *
	 * @param degrees
	 *            degrees
	 * @return this transform
	 */
	public Transform rotX(double degrees) {
		double radians = degrees * Math.PI * (1.0 / 180.0);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		double elemenents[] = {1, 0, 0, 0, 0, cos, sin, 0, 0, -sin, cos, 0, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies rotation operation around the y axis to this transform.
	 *
	 * @param degrees
	 *            degrees
	 *
	 * @return this transform
	 */
	public Transform rotY(double degrees) {
		double radians = degrees * Math.PI * (1.0 / 180.0);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		double elemenents[] = {cos, 0, -sin, 0, 0, 1, 0, 0, sin, 0, cos, 0, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies rotation operation around the z axis to this transform.
	 *
	 * @param degrees
	 *            degrees
	 *
	 * @return this transform
	 */
	public Transform rotZ(double degrees) {
		double radians = degrees * Math.PI * (1.0 / 180.0);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		double elemenents[] = {cos, sin, 0, 0, -sin, cos, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a rotation operation to this transform.
	 *
	 * @param x
	 *            x axis rotation (degrees)
	 * @param y
	 *            y axis rotation (degrees)
	 * @param z
	 *            z axis rotation (degrees)
	 *
	 * @return this transform
	 */
	public Transform rot(double x, double y, double z) {
		return rotX(x).rotY(y).rotZ(z);
	}

	/**
	 * Applies a rotation operation to this transform.
	 *
	 * @param vec
	 *            axis rotation for x, y, z (degrees)
	 *
	 * @return this transform
	 */
	public Transform rot(Vector3d vec) {

		// TODO: use quaternions
		return rotX(vec.x).rotY(vec.y).rotZ(vec.z);
	}

	/**
	 * Applies a translation operation to this transform.
	 *
	 * @param vec
	 *            translation vector (x,y,z)
	 *
	 * @return this transform
	 */
	public Transform translate(Vector3d vec) {
		return translate(vec.x, vec.y, vec.z);
	}

	/**
	 * Applies a translation operation to this transform.
	 *
	 * @param x
	 *            translation (x axis)
	 * @param y
	 *            translation (y axis)
	 * @param z
	 *            translation (z axis)
	 *
	 * @return this transform
	 */
	public Transform translate(double x, double y, double z) {
		double elemenents[] = {1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a translation operation to this transform.
	 *
	 * @param value
	 *            translation (x axis)
	 *
	 * @return this transform
	 */
	public Transform translateX(double value) {
		double elemenents[] = {1, 0, 0, value, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	// rotations
	public double getQuataurionX() {
		Quat4d q1 = getQuat();

		return q1.x;
	}

	public double getQuataurionY() {
		Quat4d q1 = getQuat();

		return q1.y;
	}

	public double getQuataurionZ() {
		Quat4d q1 = getQuat();

		return q1.z;
	}

	public double getQuataurionW() {
		Quat4d q1 = getQuat();

		return q1.w;
	}

	static double max(double a, double b) {
		if (a > b)
			return (a);
		else
			return (b);
	}

	static double min(double a, double b) {
		if (a < b)
			return (a);
		else
			return (b);
	}

	static double d_sign(double a, double b) {
		double x;
		x = (a >= 0 ? a : -a);
		return (b >= 0 ? x : -x);
	}

	static double compute_rot(double f, double g, double[] sin, double[] cos, int index, int first) {
		int i__1;
		double d__1, d__2;
		double cs, sn;
		int i;
		double scale;
		int count;
		double f1, g1;
		double r;
		final double safmn2 = 2.002083095183101E-146;
		final double safmx2 = 4.994797680505588E+145;

		if (g == 0.) {
			cs = 1.;
			sn = 0.;
			r = f;
		} else if (f == 0.) {
			cs = 0.;
			sn = 1.;
			r = g;
		} else {
			f1 = f;
			g1 = g;
			scale = max(Math.abs(f1), Math.abs(g1));
			if (scale >= safmx2) {
				count = 0;
				while (scale >= safmx2) {
					++count;
					f1 *= safmn2;
					g1 *= safmn2;
					scale = max(Math.abs(f1), Math.abs(g1));
				}
				r = Math.sqrt(f1 * f1 + g1 * g1);
				cs = f1 / r;
				sn = g1 / r;
				i__1 = count;
				for (i = 1; i <= count; ++i) {
					r *= safmx2;
				}
			} else if (scale <= safmn2) {
				count = 0;
				while (scale <= safmn2) {
					++count;
					f1 *= safmx2;
					g1 *= safmx2;
					scale = max(Math.abs(f1), Math.abs(g1));
				}
				r = Math.sqrt(f1 * f1 + g1 * g1);
				cs = f1 / r;
				sn = g1 / r;
				i__1 = count;
				for (i = 1; i <= count; ++i) {
					r *= safmn2;
				}
			} else {
				r = Math.sqrt(f1 * f1 + g1 * g1);
				cs = f1 / r;
				sn = g1 / r;
			}
			if (Math.abs(f) > Math.abs(g) && cs < 0.) {
				cs = -cs;
				sn = -sn;
				r = -r;
			}
		}
		sin[index] = sn;
		cos[index] = cs;
		return r;

	}

	static double compute_shift(double f, double g, double h) {
		double d__1, d__2;
		double fhmn, fhmx, c, fa, ga, ha, as, at, au;
		double ssmin;

		fa = Math.abs(f);
		ga = Math.abs(g);
		ha = Math.abs(h);
		fhmn = min(fa, ha);
		fhmx = max(fa, ha);
		if (fhmn == 0.) {
			ssmin = 0.;
			if (fhmx == 0.) {
			} else {
				d__1 = min(fhmx, ga) / max(fhmx, ga);
			}
		} else {
			if (ga < fhmx) {
				as = fhmn / fhmx + 1.;
				at = (fhmx - fhmn) / fhmx;
				d__1 = ga / fhmx;
				au = d__1 * d__1;
				c = 2. / (Math.sqrt(as * as + au) + Math.sqrt(at * at + au));
				ssmin = fhmn * c;
			} else {
				au = fhmx / ga;
				if (au == 0.) {
					ssmin = fhmn * fhmx / ga;
				} else {
					as = fhmn / fhmx + 1.;
					at = (fhmx - fhmn) / fhmx;
					d__1 = as * au;
					d__2 = at * au;
					c = 1. / (Math.sqrt(d__1 * d__1 + 1.) + Math.sqrt(d__2 * d__2 + 1.));
					ssmin = fhmn * c * au;
					ssmin += ssmin;
				}
			}
		}

		return (ssmin);
	}

	static int compute_2X2(double f, double g, double h, double[] single_values, double[] snl, double[] csl,
			double[] snr, double[] csr, int index) {

		double c_b3 = 2.;
		double c_b4 = 1.;

		double d__1;
		int pmax;
		double temp;
		boolean swap;
		double a, d, l, m, r, s, t, tsign, fa, ga, ha;
		double ft, gt, ht, mm;
		boolean gasmal;
		double tt, clt, crt, slt, srt;
		double ssmin, ssmax;
		double EPS = Plane.getEPSILON();
		ssmax = single_values[0];
		ssmin = single_values[1];
		clt = 0.0;
		crt = 0.0;
		slt = 0.0;
		srt = 0.0;
		tsign = 0.0;

		ft = f;
		fa = Math.abs(ft);
		ht = h;
		ha = Math.abs(h);

		pmax = 1;
		if (ha > fa)
			swap = true;
		else
			swap = false;

		if (swap) {
			pmax = 3;
			temp = ft;
			ft = ht;
			ht = temp;
			temp = fa;
			fa = ha;
			ha = temp;

		}
		gt = g;
		ga = Math.abs(gt);
		if (ga == 0.) {

			single_values[1] = ha;
			single_values[0] = fa;
			clt = 1.;
			crt = 1.;
			slt = 0.;
			srt = 0.;
		} else {
			gasmal = true;

			if (ga > fa) {
				pmax = 2;
				if (fa / ga < EPS) {

					gasmal = false;
					ssmax = ga;
					if (ha > 1.) {
						ssmin = fa / (ga / ha);
					} else {
						ssmin = fa / ga * ha;
					}
					clt = 1.;
					slt = ht / gt;
					srt = 1.;
					crt = ft / gt;
				}
			}
			if (gasmal) {

				d = fa - ha;
				if (d == fa) {

					l = 1.;
				} else {
					l = d / fa;
				}

				m = gt / ft;

				t = 2. - l;

				mm = m * m;
				tt = t * t;
				s = Math.sqrt(tt + mm);

				if (l == 0.) {
					r = Math.abs(m);
				} else {
					r = Math.sqrt(l * l + mm);
				}

				a = (s + r) * .5;

				if (ga > fa) {
					pmax = 2;
					if (fa / ga < EPS) {

						gasmal = false;
						ssmax = ga;
						if (ha > 1.) {
							ssmin = fa / (ga / ha);
						} else {
							ssmin = fa / ga * ha;
						}
						clt = 1.;
						slt = ht / gt;
						srt = 1.;
						crt = ft / gt;
					}
				}
				if (gasmal) {

					d = fa - ha;
					if (d == fa) {

						l = 1.;
					} else {
						l = d / fa;
					}

					m = gt / ft;

					t = 2. - l;

					mm = m * m;
					tt = t * t;
					s = Math.sqrt(tt + mm);

					if (l == 0.) {
						r = Math.abs(m);
					} else {
						r = Math.sqrt(l * l + mm);
					}

					a = (s + r) * .5;

					ssmin = ha / a;
					ssmax = fa * a;
					if (mm == 0.) {

						if (l == 0.) {
							t = d_sign(c_b3, ft) * d_sign(c_b4, gt);
						} else {
							t = gt / d_sign(d, ft) + m / t;
						}
					} else {
						t = (m / (s + t) + m / (r + l)) * (a + 1.);
					}
					l = Math.sqrt(t * t + 4.);
					crt = 2. / l;
					srt = t / l;
					clt = (crt + srt * m) / a;
					slt = ht / ft * srt / a;
				}
			}
			if (swap) {
				csl[0] = srt;
				snl[0] = crt;
				csr[0] = slt;
				snr[0] = clt;
			} else {
				csl[0] = clt;
				snl[0] = slt;
				csr[0] = crt;
				snr[0] = srt;
			}

			if (pmax == 1) {
				tsign = d_sign(c_b4, csr[0]) * d_sign(c_b4, csl[0]) * d_sign(c_b4, f);
			}
			if (pmax == 2) {
				tsign = d_sign(c_b4, snr[0]) * d_sign(c_b4, csl[0]) * d_sign(c_b4, g);
			}
			if (pmax == 3) {
				tsign = d_sign(c_b4, snr[0]) * d_sign(c_b4, snl[0]) * d_sign(c_b4, h);
			}
			single_values[index] = d_sign(ssmax, tsign);
			d__1 = tsign * d_sign(c_b4, f) * d_sign(c_b4, h);
			single_values[index + 1] = d_sign(ssmin, d__1);

		}
		return 0;
	}

	static int compute_qr(double[] s, double[] e, double[] u, double[] v) {

		int i, j, k;
		boolean converged;
		double shift, ssmin, ssmax, r;
		double[] cosl = new double[2];
		double[] cosr = new double[2];
		double[] sinl = new double[2];
		double[] sinr = new double[2];
		double[] m = new double[9];

		double utemp, vtemp;
		double f, g;

		final int MAX_INTERATIONS = 10;
		final double CONVERGE_TOL = 4.89E-15;

		double c_b48 = 1.;
		double c_b71 = -1.;
		int first;
		converged = false;

		first = 1;

		if (Math.abs(e[1]) < CONVERGE_TOL || Math.abs(e[0]) < CONVERGE_TOL)
			converged = true;

		for (k = 0; k < MAX_INTERATIONS && !converged; k++) {
			shift = compute_shift(s[1], e[1], s[2]);
			f = (Math.abs(s[0]) - shift) * (d_sign(c_b48, s[0]) + shift / s[0]);
			g = e[0];
			r = compute_rot(f, g, sinr, cosr, 0, first);
			f = cosr[0] * s[0] + sinr[0] * e[0];
			e[0] = cosr[0] * e[0] - sinr[0] * s[0];
			g = sinr[0] * s[1];
			s[1] = cosr[0] * s[1];

			r = compute_rot(f, g, sinl, cosl, 0, first);
			first = 0;
			s[0] = r;
			f = cosl[0] * e[0] + sinl[0] * s[1];
			s[1] = cosl[0] * s[1] - sinl[0] * e[0];
			g = sinl[0] * e[1];
			e[1] = cosl[0] * e[1];

			r = compute_rot(f, g, sinr, cosr, 1, first);
			e[0] = r;
			f = cosr[1] * s[1] + sinr[1] * e[1];
			e[1] = cosr[1] * e[1] - sinr[1] * s[1];
			g = sinr[1] * s[2];
			s[2] = cosr[1] * s[2];

			r = compute_rot(f, g, sinl, cosl, 1, first);
			s[1] = r;
			f = cosl[1] * e[1] + sinl[1] * s[2];
			s[2] = cosl[1] * s[2] - sinl[1] * e[1];
			e[1] = f;

			// update u matrices
			utemp = u[0];
			u[0] = cosl[0] * utemp + sinl[0] * u[3];
			u[3] = -sinl[0] * utemp + cosl[0] * u[3];
			utemp = u[1];
			u[1] = cosl[0] * utemp + sinl[0] * u[4];
			u[4] = -sinl[0] * utemp + cosl[0] * u[4];
			utemp = u[2];
			u[2] = cosl[0] * utemp + sinl[0] * u[5];
			u[5] = -sinl[0] * utemp + cosl[0] * u[5];

			utemp = u[3];
			u[3] = cosl[1] * utemp + sinl[1] * u[6];
			u[6] = -sinl[1] * utemp + cosl[1] * u[6];
			utemp = u[4];
			u[4] = cosl[1] * utemp + sinl[1] * u[7];
			u[7] = -sinl[1] * utemp + cosl[1] * u[7];
			utemp = u[5];
			u[5] = cosl[1] * utemp + sinl[1] * u[8];
			u[8] = -sinl[1] * utemp + cosl[1] * u[8];

			// update v matrices

			vtemp = v[0];
			v[0] = cosr[0] * vtemp + sinr[0] * v[1];
			v[1] = -sinr[0] * vtemp + cosr[0] * v[1];
			vtemp = v[3];
			v[3] = cosr[0] * vtemp + sinr[0] * v[4];
			v[4] = -sinr[0] * vtemp + cosr[0] * v[4];
			vtemp = v[6];
			v[6] = cosr[0] * vtemp + sinr[0] * v[7];
			v[7] = -sinr[0] * vtemp + cosr[0] * v[7];

			vtemp = v[1];
			v[1] = cosr[1] * vtemp + sinr[1] * v[2];
			v[2] = -sinr[1] * vtemp + cosr[1] * v[2];
			vtemp = v[4];
			v[4] = cosr[1] * vtemp + sinr[1] * v[5];
			v[5] = -sinr[1] * vtemp + cosr[1] * v[5];
			vtemp = v[7];
			v[7] = cosr[1] * vtemp + sinr[1] * v[8];
			v[8] = -sinr[1] * vtemp + cosr[1] * v[8];

			m[0] = s[0];
			m[1] = e[0];
			m[2] = 0.0;
			m[3] = 0.0;
			m[4] = s[1];
			m[5] = e[1];
			m[6] = 0.0;
			m[7] = 0.0;
			m[8] = s[2];

			if (Math.abs(e[1]) < CONVERGE_TOL || Math.abs(e[0]) < CONVERGE_TOL)
				converged = true;
		}

		if (Math.abs(e[1]) < CONVERGE_TOL) {
			compute_2X2(s[0], e[0], s[1], s, sinl, cosl, sinr, cosr, 0);

			utemp = u[0];
			u[0] = cosl[0] * utemp + sinl[0] * u[3];
			u[3] = -sinl[0] * utemp + cosl[0] * u[3];
			utemp = u[1];
			u[1] = cosl[0] * utemp + sinl[0] * u[4];
			u[4] = -sinl[0] * utemp + cosl[0] * u[4];
			utemp = u[2];
			u[2] = cosl[0] * utemp + sinl[0] * u[5];
			u[5] = -sinl[0] * utemp + cosl[0] * u[5];

			// update v matrices

			vtemp = v[0];
			v[0] = cosr[0] * vtemp + sinr[0] * v[1];
			v[1] = -sinr[0] * vtemp + cosr[0] * v[1];
			vtemp = v[3];
			v[3] = cosr[0] * vtemp + sinr[0] * v[4];
			v[4] = -sinr[0] * vtemp + cosr[0] * v[4];
			vtemp = v[6];
			v[6] = cosr[0] * vtemp + sinr[0] * v[7];
			v[7] = -sinr[0] * vtemp + cosr[0] * v[7];
		} else {
			compute_2X2(s[1], e[1], s[2], s, sinl, cosl, sinr, cosr, 1);

			utemp = u[3];
			u[3] = cosl[0] * utemp + sinl[0] * u[6];
			u[6] = -sinl[0] * utemp + cosl[0] * u[6];
			utemp = u[4];
			u[4] = cosl[0] * utemp + sinl[0] * u[7];
			u[7] = -sinl[0] * utemp + cosl[0] * u[7];
			utemp = u[5];
			u[5] = cosl[0] * utemp + sinl[0] * u[8];
			u[8] = -sinl[0] * utemp + cosl[0] * u[8];

			// update v matrices

			vtemp = v[1];
			v[1] = cosr[0] * vtemp + sinr[0] * v[2];
			v[2] = -sinr[0] * vtemp + cosr[0] * v[2];
			vtemp = v[4];
			v[4] = cosr[0] * vtemp + sinr[0] * v[5];
			v[5] = -sinr[0] * vtemp + cosr[0] * v[5];
			vtemp = v[7];
			v[7] = cosr[0] * vtemp + sinr[0] * v[8];
			v[8] = -sinr[0] * vtemp + cosr[0] * v[8];
		}

		return (0);
	}

	static void compute_svd(double[] m, double[] outScale, double[] outRot) {
		int i, j;
		double g, scale;
		double[] u1 = new double[9];
		double[] v1 = new double[9];
		double[] t1 = new double[9];
		double[] t2 = new double[9];

		double[] tmp = t1;
		double[] single_values = t2;

		double[] rot = new double[9];
		double[] e = new double[3];
		double[] scales = new double[3];

		int converged, negCnt = 0;
		double cs, sn;
		double c1, c2, c3, c4;
		double s1, s2, s3, s4;
		double cl1, cl2, cl3;

		for (i = 0; i < 9; i++)
			rot[i] = m[i];

		// u1
		double EPS = Plane.getEPSILON();
		if (m[3] * m[3] < EPS) {
			u1[0] = 1.0;
			u1[1] = 0.0;
			u1[2] = 0.0;
			u1[3] = 0.0;
			u1[4] = 1.0;
			u1[5] = 0.0;
			u1[6] = 0.0;
			u1[7] = 0.0;
			u1[8] = 1.0;
		} else if (m[0] * m[0] < EPS) {
			tmp[0] = m[0];
			tmp[1] = m[1];
			tmp[2] = m[2];
			m[0] = m[3];
			m[1] = m[4];
			m[2] = m[5];

			m[3] = -tmp[0]; // zero
			m[4] = -tmp[1];
			m[5] = -tmp[2];

			u1[0] = 0.0;
			u1[1] = 1.0;
			u1[2] = 0.0;
			u1[3] = -1.0;
			u1[4] = 0.0;
			u1[5] = 0.0;
			u1[6] = 0.0;
			u1[7] = 0.0;
			u1[8] = 1.0;
		} else {
			g = 1.0 / Math.sqrt(m[0] * m[0] + m[3] * m[3]);
			c1 = m[0] * g;
			s1 = m[3] * g;
			tmp[0] = c1 * m[0] + s1 * m[3];
			tmp[1] = c1 * m[1] + s1 * m[4];
			tmp[2] = c1 * m[2] + s1 * m[5];

			m[3] = -s1 * m[0] + c1 * m[3]; // zero
			m[4] = -s1 * m[1] + c1 * m[4];
			m[5] = -s1 * m[2] + c1 * m[5];

			m[0] = tmp[0];
			m[1] = tmp[1];
			m[2] = tmp[2];
			u1[0] = c1;
			u1[1] = s1;
			u1[2] = 0.0;
			u1[3] = -s1;
			u1[4] = c1;
			u1[5] = 0.0;
			u1[6] = 0.0;
			u1[7] = 0.0;
			u1[8] = 1.0;
		}

		// u2

		if (m[6] * m[6] < EPS) {
		} else if (m[0] * m[0] < EPS) {
			tmp[0] = m[0];
			tmp[1] = m[1];
			tmp[2] = m[2];
			m[0] = m[6];
			m[1] = m[7];
			m[2] = m[8];

			m[6] = -tmp[0]; // zero
			m[7] = -tmp[1];
			m[8] = -tmp[2];

			tmp[0] = u1[0];
			tmp[1] = u1[1];
			tmp[2] = u1[2];
			u1[0] = u1[6];
			u1[1] = u1[7];
			u1[2] = u1[8];

			u1[6] = -tmp[0]; // zero
			u1[7] = -tmp[1];
			u1[8] = -tmp[2];
		} else {
			g = 1.0 / Math.sqrt(m[0] * m[0] + m[6] * m[6]);
			c2 = m[0] * g;
			s2 = m[6] * g;
			tmp[0] = c2 * m[0] + s2 * m[6];
			tmp[1] = c2 * m[1] + s2 * m[7];
			tmp[2] = c2 * m[2] + s2 * m[8];

			m[6] = -s2 * m[0] + c2 * m[6];
			m[7] = -s2 * m[1] + c2 * m[7];
			m[8] = -s2 * m[2] + c2 * m[8];
			m[0] = tmp[0];
			m[1] = tmp[1];
			m[2] = tmp[2];

			tmp[0] = c2 * u1[0];
			tmp[1] = c2 * u1[1];
			u1[2] = s2;

			tmp[6] = -u1[0] * s2;
			tmp[7] = -u1[1] * s2;
			u1[8] = c2;
			u1[0] = tmp[0];
			u1[1] = tmp[1];
			u1[6] = tmp[6];
			u1[7] = tmp[7];
		}

		// v1

		if (m[2] * m[2] < EPS) {
			v1[0] = 1.0;
			v1[1] = 0.0;
			v1[2] = 0.0;
			v1[3] = 0.0;
			v1[4] = 1.0;
			v1[5] = 0.0;
			v1[6] = 0.0;
			v1[7] = 0.0;
			v1[8] = 1.0;
		} else if (m[1] * m[1] < EPS) {
			tmp[2] = m[2];
			tmp[5] = m[5];
			tmp[8] = m[8];
			m[2] = -m[1];
			m[5] = -m[4];
			m[8] = -m[7];

			m[1] = tmp[2]; // zero
			m[4] = tmp[5];
			m[7] = tmp[8];

			v1[0] = 1.0;
			v1[1] = 0.0;
			v1[2] = 0.0;
			v1[3] = 0.0;
			v1[4] = 0.0;
			v1[5] = -1.0;
			v1[6] = 0.0;
			v1[7] = 1.0;
			v1[8] = 0.0;
		} else {
			g = 1.0 / Math.sqrt(m[1] * m[1] + m[2] * m[2]);
			c3 = m[1] * g;
			s3 = m[2] * g;
			tmp[1] = c3 * m[1] + s3 * m[2]; // can assign to m[1]?
			m[2] = -s3 * m[1] + c3 * m[2]; // zero
			m[1] = tmp[1];

			tmp[4] = c3 * m[4] + s3 * m[5];
			m[5] = -s3 * m[4] + c3 * m[5];
			m[4] = tmp[4];

			tmp[7] = c3 * m[7] + s3 * m[8];
			m[8] = -s3 * m[7] + c3 * m[8];
			m[7] = tmp[7];

			v1[0] = 1.0;
			v1[1] = 0.0;
			v1[2] = 0.0;
			v1[3] = 0.0;
			v1[4] = c3;
			v1[5] = -s3;
			v1[6] = 0.0;
			v1[7] = s3;
			v1[8] = c3;
		}

		// u3

		if (m[7] * m[7] < EPS) {
		} else if (m[4] * m[4] < EPS) {
			tmp[3] = m[3];
			tmp[4] = m[4];
			tmp[5] = m[5];
			m[3] = m[6]; // zero
			m[4] = m[7];
			m[5] = m[8];

			m[6] = -tmp[3]; // zero
			m[7] = -tmp[4]; // zero
			m[8] = -tmp[5];

			tmp[3] = u1[3];
			tmp[4] = u1[4];
			tmp[5] = u1[5];
			u1[3] = u1[6];
			u1[4] = u1[7];
			u1[5] = u1[8];

			u1[6] = -tmp[3]; // zero
			u1[7] = -tmp[4];
			u1[8] = -tmp[5];

		} else {
			g = 1.0 / Math.sqrt(m[4] * m[4] + m[7] * m[7]);
			c4 = m[4] * g;
			s4 = m[7] * g;
			tmp[3] = c4 * m[3] + s4 * m[6];
			m[6] = -s4 * m[3] + c4 * m[6]; // zero
			m[3] = tmp[3];

			tmp[4] = c4 * m[4] + s4 * m[7];
			m[7] = -s4 * m[4] + c4 * m[7];
			m[4] = tmp[4];

			tmp[5] = c4 * m[5] + s4 * m[8];
			m[8] = -s4 * m[5] + c4 * m[8];
			m[5] = tmp[5];

			tmp[3] = c4 * u1[3] + s4 * u1[6];
			u1[6] = -s4 * u1[3] + c4 * u1[6];
			u1[3] = tmp[3];

			tmp[4] = c4 * u1[4] + s4 * u1[7];
			u1[7] = -s4 * u1[4] + c4 * u1[7];
			u1[4] = tmp[4];

			tmp[5] = c4 * u1[5] + s4 * u1[8];
			u1[8] = -s4 * u1[5] + c4 * u1[8];
			u1[5] = tmp[5];
		}

		single_values[0] = m[0];
		single_values[1] = m[4];
		single_values[2] = m[8];
		e[0] = m[1];
		e[1] = m[5];

		if (e[0] * e[0] < EPS && e[1] * e[1] < EPS) {

		} else {
			compute_qr(single_values, e, u1, v1);
		}

		scales[0] = single_values[0];
		scales[1] = single_values[1];
		scales[2] = single_values[2];

		// Do some optimization here. If scale is unity, simply return the rotation
		// matric.
		if (almostEqual(Math.abs(scales[0]), 1.0) && almostEqual(Math.abs(scales[1]), 1.0)
				&& almostEqual(Math.abs(scales[2]), 1.0)) {
			// System.out.println("Scale components almost to 1.0");

			for (i = 0; i < 3; i++)
				if (scales[i] < 0.0)
					negCnt++;

			if ((negCnt == 0) || (negCnt == 2)) {
				// System.out.println("Optimize!!");
				outScale[0] = outScale[1] = outScale[2] = 1.0;
				for (i = 0; i < 9; i++)
					outRot[i] = rot[i];

				return;
			}
		}

		transpose_mat(u1, t1);
		transpose_mat(v1, t2);

		/*
		 * System.out.println("t1 is \n" + t1);
		 * System.out.println("t1="+t1[0]+" "+t1[1]+" "+t1[2]);
		 * System.out.println("t1="+t1[3]+" "+t1[4]+" "+t1[5]);
		 * System.out.println("t1="+t1[6]+" "+t1[7]+" "+t1[8]);
		 *
		 * System.out.println("t2 is \n" + t2);
		 * System.out.println("t2="+t2[0]+" "+t2[1]+" "+t2[2]);
		 * System.out.println("t2="+t2[3]+" "+t2[4]+" "+t2[5]);
		 * System.out.println("t2="+t2[6]+" "+t2[7]+" "+t2[8]);
		 */

		svdReorder(m, t1, t2, scales, outRot, outScale);

	}

	static void svdReorder(double[] m, double[] t1, double[] t2, double[] scales, double[] outRot, double[] outScale) {

		int[] out = new int[3];
		int[] in = new int[3];
		int in0, in1, in2, index, i;
		double[] mag = new double[3];
		double[] rot = new double[9];

		// check for rotation information in the scales
		if (scales[0] < 0.0) { // move the rotation info to rotation matrix
			scales[0] = -scales[0];
			t2[0] = -t2[0];
			t2[1] = -t2[1];
			t2[2] = -t2[2];
		}
		if (scales[1] < 0.0) { // move the rotation info to rotation matrix
			scales[1] = -scales[1];
			t2[3] = -t2[3];
			t2[4] = -t2[4];
			t2[5] = -t2[5];
		}
		if (scales[2] < 0.0) { // move the rotation info to rotation matrix
			scales[2] = -scales[2];
			t2[6] = -t2[6];
			t2[7] = -t2[7];
			t2[8] = -t2[8];
		}

		mat_mul(t1, t2, rot);

		// check for equal scales case and do not reorder
		if (almostEqual(Math.abs(scales[0]), Math.abs(scales[1]))
				&& almostEqual(Math.abs(scales[1]), Math.abs(scales[2]))) {
			for (i = 0; i < 9; i++) {
				outRot[i] = rot[i];
			}
			for (i = 0; i < 3; i++) {
				outScale[i] = scales[i];
			}

		} else {

			// sort the order of the results of SVD
			if (scales[0] > scales[1]) {
				if (scales[0] > scales[2]) {
					if (scales[2] > scales[1]) {
						out[0] = 0;
						out[1] = 2;
						out[2] = 1; // xzy
					} else {
						out[0] = 0;
						out[1] = 1;
						out[2] = 2; // xyz
					}
				} else {
					out[0] = 2;
					out[1] = 0;
					out[2] = 1; // zxy
				}
			} else { // y > x
				if (scales[1] > scales[2]) {
					if (scales[2] > scales[0]) {
						out[0] = 1;
						out[1] = 2;
						out[2] = 0; // yzx
					} else {
						out[0] = 1;
						out[1] = 0;
						out[2] = 2; // yxz
					}
				} else {
					out[0] = 2;
					out[1] = 1;
					out[2] = 0; // zyx
				}
			}

			/*
			 * System.out.println("\nscales="+scales[0]+" "+scales[1]+" "+scales[2]);
			 * System.out.println("\nrot="+rot[0]+" "+rot[1]+" "+rot[2]);
			 * System.out.println("rot="+rot[3]+" "+rot[4]+" "+rot[5]);
			 * System.out.println("rot="+rot[6]+" "+rot[7]+" "+rot[8]);
			 */

			// sort the order of the input matrix
			mag[0] = (m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
			mag[1] = (m[3] * m[3] + m[4] * m[4] + m[5] * m[5]);
			mag[2] = (m[6] * m[6] + m[7] * m[7] + m[8] * m[8]);

			if (mag[0] > mag[1]) {
				if (mag[0] > mag[2]) {
					if (mag[2] > mag[1]) {
						// 0 - 2 - 1
						in0 = 0;
						in2 = 1;
						in1 = 2;// xzy
					} else {
						// 0 - 1 - 2
						in0 = 0;
						in1 = 1;
						in2 = 2; // xyz
					}
				} else {
					// 2 - 0 - 1
					in2 = 0;
					in0 = 1;
					in1 = 2; // zxy
				}
			} else { // y > x 1>0
				if (mag[1] > mag[2]) {
					if (mag[2] > mag[0]) {
						// 1 - 2 - 0
						in1 = 0;
						in2 = 1;
						in0 = 2; // yzx
					} else {
						// 1 - 0 - 2
						in1 = 0;
						in0 = 1;
						in2 = 2; // yxz
					}
				} else {
					// 2 - 1 - 0
					in2 = 0;
					in1 = 1;
					in0 = 2; // zyx
				}
			}

			index = out[in0];
			outScale[0] = scales[index];

			index = out[in1];
			outScale[1] = scales[index];

			index = out[in2];
			outScale[2] = scales[index];

			index = out[in0];
			outRot[0] = rot[index];

			index = out[in0] + 3;
			outRot[0 + 3] = rot[index];

			index = out[in0] + 6;
			outRot[0 + 6] = rot[index];

			index = out[in1];
			outRot[1] = rot[index];

			index = out[in1] + 3;
			outRot[1 + 3] = rot[index];

			index = out[in1] + 6;
			outRot[1 + 6] = rot[index];

			index = out[in2];
			outRot[2] = rot[index];

			index = out[in2] + 3;
			outRot[2 + 3] = rot[index];

			index = out[in2] + 6;
			outRot[2 + 6] = rot[index];
		}
	}

	static void mat_mul(double[] m1, double[] m2, double[] m3) {
		int i;
		double[] tmp = new double[9];

		tmp[0] = m1[0] * m2[0] + m1[1] * m2[3] + m1[2] * m2[6];
		tmp[1] = m1[0] * m2[1] + m1[1] * m2[4] + m1[2] * m2[7];
		tmp[2] = m1[0] * m2[2] + m1[1] * m2[5] + m1[2] * m2[8];

		tmp[3] = m1[3] * m2[0] + m1[4] * m2[3] + m1[5] * m2[6];
		tmp[4] = m1[3] * m2[1] + m1[4] * m2[4] + m1[5] * m2[7];
		tmp[5] = m1[3] * m2[2] + m1[4] * m2[5] + m1[5] * m2[8];

		tmp[6] = m1[6] * m2[0] + m1[7] * m2[3] + m1[8] * m2[6];
		tmp[7] = m1[6] * m2[1] + m1[7] * m2[4] + m1[8] * m2[7];
		tmp[8] = m1[6] * m2[2] + m1[7] * m2[5] + m1[8] * m2[8];

		for (i = 0; i < 9; i++) {
			m3[i] = tmp[i];
		}
	}

	static void transpose_mat(double[] in, double[] out) {
		out[0] = in[0];
		out[1] = in[3];
		out[2] = in[6];

		out[3] = in[1];
		out[4] = in[4];
		out[5] = in[7];

		out[6] = in[2];
		out[7] = in[5];
		out[8] = in[8];
	}

	private static final boolean almostEqual(double a, double b) {
		if (a == b)
			return true;

		final double EPSILON_ABSOLUTE = 1.0e-6;
		final double EPSILON_RELATIVE = 1.0e-4;
		double diff = Math.abs(a - b);
		double absA = Math.abs(a);
		double absB = Math.abs(b);
		double max = (absA >= absB) ? absA : absB;

		if (diff < EPSILON_ABSOLUTE)
			return true;

		if ((diff / max) < EPSILON_RELATIVE)
			return true;

		return false;
	}

	private final void getScaleRotate(double scales[], double rots[]) {
		double[] tmp = new double[9]; // scratch matrix
		tmp[0] = getInternalMatrix().m00;
		tmp[1] = getInternalMatrix().m01;
		tmp[2] = getInternalMatrix().m02;

		tmp[3] = getInternalMatrix().m10;
		tmp[4] = getInternalMatrix().m11;
		tmp[5] = getInternalMatrix().m12;

		tmp[6] = getInternalMatrix().m20;
		tmp[7] = getInternalMatrix().m21;
		tmp[8] = getInternalMatrix().m22;

		compute_svd(tmp, scales, rots);

		return;
	}

	/**
	 * Performs an SVD normalization of q1 matrix in order to acquire the normalized
	 * rotational component; the values are placed into the Quat4d parameter.
	 *
	 * @param q1
	 *            the quaternion into which the rotation component is placed
	 */
	public final void get(Quat4d q1) {
		double[] tmp_rot = new double[9]; // scratch matrix
		double[] tmp_scale = new double[3]; // scratch matrix

		getScaleRotate(tmp_scale, tmp_rot);

		// Convert flat array to matrix notation for clarity
		// tmp_rot[0]=m00, tmp_rot[1]=m01, tmp_rot[2]=m02,
		// tmp_rot[3]=m10, tmp_rot[4]=m11, tmp_rot[5]=m12,
		// tmp_rot[6]=m20, tmp_rot[7]=m21, tmp_rot[8]=m22

		double s = tmp_rot[0] + tmp_rot[4] + tmp_rot[8];
		if (s > -0.19) {
			// compute q.w and deduce q.x, q.y and q.z
			q1.w = 0.5 * Math.sqrt(s + 1.0);
			double inv = 0.25 / q1.w;
			q1.x = (tmp_rot[7] - tmp_rot[5]) * inv;
			q1.y = (tmp_rot[2] - tmp_rot[6]) * inv;
			q1.z = (tmp_rot[3] - tmp_rot[1]) * inv;
		} else {
			s = tmp_rot[0] - tmp_rot[4] - tmp_rot[8];
			if (s > -0.19) {
				// compute q.x and deduce q.w, q.y and q.z
				q1.x = 0.5 * Math.sqrt(s + 1.0);
				double inv = 0.25 / q1.x;
				q1.w = (tmp_rot[7] - tmp_rot[5]) * inv;
				q1.y = (tmp_rot[3] + tmp_rot[1]) * inv;
				q1.z = (tmp_rot[6] + tmp_rot[2]) * inv;
			} else {
				s = tmp_rot[4] - tmp_rot[0] - tmp_rot[8];
				if (s > -0.19) {
					// compute q.y and deduce q.w, q.x and q.z
					q1.y = 0.5 * Math.sqrt(s + 1.0);
					double inv = 0.25 / q1.y;
					q1.w = (tmp_rot[2] - tmp_rot[6]) * inv;
					q1.x = (tmp_rot[3] + tmp_rot[1]) * inv;
					q1.z = (tmp_rot[7] + tmp_rot[5]) * inv;
				} else {
					// compute q.z and deduce q.w, q.x and q.y
					s = tmp_rot[8] - tmp_rot[0] - tmp_rot[4];
					q1.z = 0.5 * Math.sqrt(s + 1.0);
					double inv = 0.25 / q1.z;
					q1.w = (tmp_rot[3] - tmp_rot[1]) * inv;
					q1.x = (tmp_rot[6] + tmp_rot[2]) * inv;
					q1.y = (tmp_rot[7] + tmp_rot[5]) * inv;
				}
			}
		}
	}
	// /**
	// * Performs an SVD normalization of q1 matrix in order to acquire the
	// normalized
	// * rotational component; the values are placed into the Quat4d parameter.
	// *
	// * @param q1 the quaternion into which the rotation component is placed
	// */
	// public final void get(Quat4d q1) {
	// double[] tmp_rot = new double[9]; // scratch matrix
	// double[] tmp_scale = new double[3]; // scratch matrix
	//
	// getScaleRotate(tmp_scale, tmp_rot);
	//
	// double ww;
	//
	// ww = 0.25 * (1.0 + tmp_rot[0] + tmp_rot[4] + tmp_rot[8]);
	// if (!((ww < 0 ? -ww : ww) < 1.0e-30)) {
	// q1.w = Math.sqrt(ww);
	// ww = 0.25 / q1.w;
	// q1.x = (tmp_rot[7] - tmp_rot[5]) * ww;
	// q1.y = (tmp_rot[2] - tmp_rot[6]) * ww;
	// q1.z = (tmp_rot[3] - tmp_rot[1]) * ww;
	// return;
	// }
	//
	// q1.w = 0.0f;
	// ww = -0.5 * (tmp_rot[4] + tmp_rot[8]);
	// if (!((ww < 0 ? -ww : ww) < 1.0e-30)) {
	// q1.x = Math.sqrt(ww);
	// ww = 0.5 / q1.x;
	// q1.y = tmp_rot[3] * ww;
	// q1.z = tmp_rot[6] * ww;
	// return;
	// }
	//
	// q1.x = 0.0;
	// ww = 0.5 * (1.0 - tmp_rot[8]);
	// if (!((ww < 0 ? -ww : ww) < 1.0e-30)) {
	// q1.y = Math.sqrt(ww);
	// q1.z = tmp_rot[7] / (2.0 * q1.y);
	// return;
	// }
	//
	// q1.y = 0.0;
	// q1.z = 1.0;
	// }

	public Quat4d getQuat() {
		Quat4d q1 = new Quat4d();
		get(q1);

		return q1;
	}

	// translations
	public double getX() {
		javax.vecmath.Vector3d t1 = new javax.vecmath.Vector3d();
		getInternalMatrix().get(t1);
		return t1.x;
	}

	public double getY() {
		javax.vecmath.Vector3d t1 = new javax.vecmath.Vector3d();
		getInternalMatrix().get(t1);
		return t1.y;
	}

	public double getZ() {
		javax.vecmath.Vector3d t1 = new javax.vecmath.Vector3d();
		getInternalMatrix().get(t1);
		return t1.z;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		javax.vecmath.Vector3d t1 = new javax.vecmath.Vector3d();
		getInternalMatrix().get(t1);
		Quat4d q1 = getQuat();

		return "X=" + t1.x + " Y=" + t1.y + " Z=" + t1.z + " Qx=" + q1.x + " Qy=" + q1.y + " Qz=" + q1.z + " Qw="
				+ q1.w;
	}

	/**
	 * Applies a translation operation to this transform.
	 *
	 * @param value
	 *            translation (y axis)
	 *
	 * @return this transform
	 */
	public Transform translateY(double value) {
		double elemenents[] = {1, 0, 0, 0, 0, 1, 0, value, 0, 0, 1, 0, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a translation operation to this transform.
	 *
	 * @param value
	 *            translation (z axis)
	 *
	 * @return this transform
	 */
	public Transform translateZ(double value) {
		double elemenents[] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, value, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a mirror operation to this transform.
	 *
	 * @param plane
	 *            the plane that defines the mirror operation
	 *
	 * @return this transform
	 */
	public Transform mirror(Plane plane) {

		// com.neuronrobotics.sdk.common.Log.error("WARNING: I'm too dumb to implement
		// the mirror() operation correctly. Please fix me!");

		double nx = plane.getNormal().x;
		double ny = plane.getNormal().y;
		double nz = plane.getNormal().z;
		double w = plane.getDist();
		double elemenents[] = {(1.0 - 2.0 * nx * nx), (-2.0 * ny * nx), (-2.0 * nz * nx), 0, (-2.0 * nx * ny),
				(1.0 - 2.0 * ny * ny), (-2.0 * nz * ny), 0, (-2.0 * nx * nz), (-2.0 * ny * nz), (1.0 - 2.0 * nz * nz),
				0, (-2.0 * nx * w), (-2.0 * ny * w), (-2.0 * nz * w), 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation to this transform.
	 *
	 * @param vec
	 *            vector that specifies scale (x,y,z)
	 *
	 * @return this transform
	 */
	public Transform scale(Vector3d vec) {

		if (vec.x == 0 || vec.y == 0 || vec.z == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = {vec.x, 0, 0, 0, 0, vec.y, 0, 0, 0, 0, vec.z, 0, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation to this transform.
	 *
	 * @param x
	 *            x scale value
	 * @param y
	 *            y scale value
	 * @param z
	 *            z scale value
	 *
	 * @return this transform
	 */
	public Transform scale(double x, double y, double z) {

		if (x == 0 || y == 0 || z == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = {x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation to this transform.
	 *
	 * @param s
	 *            s scale value (x, y and z)
	 *
	 * @return this transform
	 */
	public Transform scale(double s) {

		if (s == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = {s, 0, 0, 0, 0, s, 0, 0, 0, 0, s, 0, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation (x axis) to this transform.
	 *
	 * @param s
	 *            x scale value
	 *
	 * @return this transform
	 */
	public Transform scaleX(double s) {

		if (s == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = {s, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation (y axis) to this transform.
	 *
	 * @param s
	 *            y scale value
	 *
	 * @return this transform
	 */
	public Transform scaleY(double s) {

		if (s == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = {1, 0, 0, 0, 0, s, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies a scale operation (z axis) to this transform.
	 *
	 * @param s
	 *            z scale value
	 *
	 * @return this transform
	 */
	public Transform scaleZ(double s) {

		if (s == 0) {
			throw new IllegalArgumentException("scale by 0 not allowed!");
		}

		double elemenents[] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, s, 0, 0, 0, 0, 1};
		getInternalMatrix().mul(new Matrix4d(elemenents));
		return this;
	}

	/**
	 * Applies this transform to the specified vector.
	 *
	 * @param vec
	 *            vector to transform
	 *
	 * @return the specified vector
	 */
	public Vector3d transform(Vector3d vec) {
		double x, y;
		x = getInternalMatrix().m00 * vec.x + getInternalMatrix().m01 * vec.y + getInternalMatrix().m02 * vec.z
				+ getInternalMatrix().m03;
		y = getInternalMatrix().m10 * vec.x + getInternalMatrix().m11 * vec.y + getInternalMatrix().m12 * vec.z
				+ getInternalMatrix().m13;
		vec.z = getInternalMatrix().m20 * vec.x + getInternalMatrix().m21 * vec.y + getInternalMatrix().m22 * vec.z
				+ getInternalMatrix().m23;
		vec.x = x;
		vec.y = y;

		return vec;
	}

	/**
	 * Applies this transform to the specified vector.
	 *
	 * @param vec
	 *            vector to transform
	 * @param amount
	 *            transform amount (0 = 0 %, 1 = 100%)
	 *
	 * @return the specified vector
	 */
	public Vector3d transform(Vector3d vec, double amount) {

		double prevX = vec.x;
		double prevY = vec.y;
		double prevZ = vec.z;

		final double x, y;
		x = getInternalMatrix().m00 * vec.x + getInternalMatrix().m01 * vec.y + getInternalMatrix().m02 * vec.z
				+ getInternalMatrix().m03;
		y = getInternalMatrix().m10 * vec.x + getInternalMatrix().m11 * vec.y + getInternalMatrix().m12 * vec.z
				+ getInternalMatrix().m13;
		vec.z = getInternalMatrix().m20 * vec.x + getInternalMatrix().m21 * vec.y + getInternalMatrix().m22 * vec.z
				+ getInternalMatrix().m23;
		vec.x = x;
		vec.y = y;

		double diffX = vec.x - prevX;
		double diffY = vec.y - prevY;
		double diffZ = vec.z - prevZ;

		vec.x = prevX + (diffX) * amount;
		vec.y = prevY + (diffY) * amount;
		vec.z = prevZ + (diffZ) * amount;

		return vec;
	}

	// // Multiply a CSG.Vector3D (interpreted as 3 column, 1 row) by this matrix
	// // (result = v*M)
	// // Fourth element is taken as 1
	// leftMultiply1x3Vector: function(v) {
	// var v0 = v._x;
	// var v1 = v._y;
	// var v2 = v._z;
	// var v3 = 1;
	// var x = v0 * this.elements[0] + v1 * this.elements[4] + v2 * this.elements[8]
	// + v3 * this.elements[12];
	// var y = v0 * this.elements[1] + v1 * this.elements[5] + v2 * this.elements[9]
	// + v3 * this.elements[13];
	// var z = v0 * this.elements[2] + v1 * this.elements[6] + v2 *
	// this.elements[10] + v3 * this.elements[14];
	// var w = v0 * this.elements[3] + v1 * this.elements[7] + v2 *
	// this.elements[11] + v3 * this.elements[15];
	// // scale such that fourth element becomes 1:
	// if(w != 1) {
	// var invw = 1.0 / w;
	// x *= invw;
	// y *= invw;
	// z *= invw;
	// }
	// return new CSG.Vector3D(x, y, z);
	// },
	/**
	 * Performs an SVD normalization of the underlying matrix to calculate and
	 * return the uniform scale factor. If the matrix has non-uniform scale factors,
	 * the largest of the x, y, and z scale factors distill be returned.
	 *
	 * Note: this transformation is not modified.
	 *
	 * @return the scale factor of this transformation
	 */
	public double getScale() {
		return getInternalMatrix().getScale();
	}

	/**
	 * Indicates whether this transform performs a mirror operation, i.e., flips the
	 * orientation.
	 *
	 * @return <code>true</code> if this transform performs a mirror operation;
	 *         <code>false</code> otherwise
	 */
	public boolean isMirror() {
		return getInternalMatrix().determinant() < 0;
	}

	/**
	 * Applies the specified transform to this transform.
	 *
	 * @param t
	 *            transform to apply
	 *
	 * @return this transform
	 */
	public Transform apply(Transform t) {
		getInternalMatrix().mul(t.getInternalMatrix());
		return this;
	}

	public Matrix4d getInternalMatrix() {
		return m;
	}

	/**
	 * Return a new transform that is inverted
	 *
	 * @return
	 */
	public Transform inverse() {
		Transform tr = new Transform().apply(this);

		tr.getInternalMatrix().invert();

		return tr;
	}

	/**
	 * Apply an inversion to this transform
	 *
	 * @return
	 */
	public Transform invert() {
		getInternalMatrix().invert();

		return this;
	}

	public Transform move(Number x, Number y, Number z) {
		return new Transform().translate(x.doubleValue(), y.doubleValue(), z.doubleValue()).apply(this);
	}

	public Transform move(Vertex v) {
		return new Transform().translate(v.getX(), v.getY(), v.getZ()).apply(this);
	}

	public Transform move(Vector3d v) {
		return new Transform().translate(v.x, v.y, v.z).apply(this);
	}

	public Transform move(Number[] posVector) {
		return move(posVector[0], posVector[1], posVector[2]);
	}

	/**
	 * Movey.
	 *
	 * @param howFarToMove
	 *            the how far to move
	 * @return the csg
	 */
	// Helper/wrapper functions for movement
	public Transform movey(Number howFarToMove) {
		return new Transform().translateY(howFarToMove.doubleValue()).apply(this);
	}

	/**
	 * Movez.
	 *
	 * @param howFarToMove
	 *            the how far to move
	 * @return the csg
	 */
	public Transform movez(Number howFarToMove) {
		return new Transform().translateZ(howFarToMove.doubleValue()).apply(this);
	}

	/**
	 * Movex.
	 *
	 * @param howFarToMove
	 *            the how far to move
	 * @return the csg
	 */
	public Transform movex(Number howFarToMove) {
		return new Transform().translateX(howFarToMove.doubleValue()).apply(this);
	}

	/**
	 * mirror about y axis.
	 *
	 *
	 * @return the csg
	 */
	// Helper/wrapper functions for movement
	public Transform mirrory() {
		return this.scaleY(-1);
	}

	/**
	 * mirror about z axis.
	 *
	 * @return the csg
	 */
	public Transform mirrorz() {
		return this.scaleZ(-1);
	}

	/**
	 * mirror about x axis.
	 *
	 * @return the csg
	 */
	public Transform mirrorx() {
		return this.scaleX(-1);
	}

	/**
	 * Rotz.
	 *
	 * @param degreesToRotate
	 *            the degrees to rotate
	 * @return the csg
	 */
	// Rotation function, rotates the object
	public Transform rotz(Number degreesToRotate) {
		return new Transform().rotZ(degreesToRotate.doubleValue()).apply(this);
	}

	/**
	 * Roty.
	 *
	 * @param degreesToRotate
	 *            the degrees to rotate
	 * @return the csg
	 */
	public Transform roty(Number degreesToRotate) {
		return new Transform().rotY(degreesToRotate.doubleValue()).apply(this);
	}

	/**
	 * Rotx.
	 *
	 * @param degreesToRotate
	 *            the degrees to rotate
	 * @return the csg
	 */
	public Transform rotx(Number degreesToRotate) {
		return new Transform().rotX(degreesToRotate.doubleValue()).apply(this);
	}

	public Transform copy() {
		return new Transform().apply(this);
	}

	public Transform setToOrigin() {
		return set(0, 0, 0);
	}

	public Transform setZ(Number z) {
		return set(0, 0, z);
	}

	public Transform setY(Number y) {
		return set(0, y, 0);
	}

	public Transform setX(Number x) {
		return set(x, 0, 0);
	}

	public Transform set(Number x, Number y, Number z) {
		javax.vecmath.Vector3d t1 = new javax.vecmath.Vector3d();
		getInternalMatrix().get(t1);
		return new Transform().translate(x.doubleValue() - t1.x, y.doubleValue() - t1.y, z.doubleValue() - t1.z)
				.apply(this);
	}

}

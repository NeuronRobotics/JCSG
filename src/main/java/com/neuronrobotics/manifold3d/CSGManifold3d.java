package com.neuronrobotics.manifold3d;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.cadoodlecad.manifold.ManifoldBindings;
import com.cadoodlecad.manifold.ManifoldBindings.ManifoldError;
import com.cadoodlecad.manifold.ManifoldBindings.MeshData64;
import com.piro.bezier.BezierPath;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.ICSGProgress;
import eu.mihosoft.vrl.v3d.Plane;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Vertex;
import javax.vecmath.Matrix4d;
import javafx.scene.paint.Color;
public class CSGManifold3d {
	private static final double MembrainTollerence = Plane.getEPSILON() * 10;
	private final ManifoldBindings manifold;
	private static boolean minkowskiMembrainRemoval = false;

	/**
	 * The operation {@link #booleanOp(CSG, CSG, BooleanOp)} applies.
	 */
	public enum BooleanOp {
		/** Fuses the two solids into one. */
		UNION(ManifoldBindings.OPTYPE_UNION),
		/** Subtracts the second solid from the first. */
		DIFFERENCE(ManifoldBindings.OPTYPE_DIFFERENCE),
		/** Keeps only the volume both solids share. */
		INTERSECTION(ManifoldBindings.OPTYPE_INTERSECTION);

		private final int opCode;

		BooleanOp(int opCode) {
			this.opCode = opCode;
		}

		/** The {@code ManifoldOpType} code the native boolean call expects. */
		public int toInt() {
			return opCode;
		}
	}

	// private final Manifold3dExporter exporter;
	// private final Manifold3dImporter importer;

	public CSGManifold3d() throws Exception {
		this.manifold = new ManifoldBindings();
		// exporter = new Manifold3dExporter(manifold);
		// importer = new Manifold3dImporter(manifold);
	}

	public void checkManifold(CSG c) throws Throwable {
		MemorySegment back = c.getManifold().toManifold(c);
		CSG mcsg = c.getManifold().fromManifold(back, c.getColor());
		CSG.getManifold().delete(back);
		c.setVertices(mcsg.getVertices());
		c.setTriangles(mcsg.getTriangles());
	}

	/**
	 * Converts a JCSG {@link CSG} into a native manifold {@link MemorySegment}.
	 *
	 * <p>
	 * The caller is responsible for eventually freeing the returned segment via the
	 * bridge's delete method (e.g. {@code manifold_delete_manifold}).
	 *
	 * @param csg
	 *            the solid to export; must not be null
	 * @return a native manifold segment ready for boolean operations
	 * @throws Throwable
	 *             if the native import call fails
	 * @throws IllegalArgumentException
	 *             if {@code csg} is null or has no polygons
	 */
	public MemorySegment toManifold(CSG csg) throws Throwable {
		if (csg == null)
			throw new IllegalArgumentException("csg must not be null");

		double[] vertices = csg.getVertices();

		long[] triangles = csg.getTriangles();
		MemorySegment ms = manifold.importMeshGL64(vertices, triangles, vertices.length, triangles.length);
		checkResult(ms);
		return ms;
	}

	/**
	 * Converts a native manifold {@link MemorySegment} to a JCSG {@link CSG}.
	 *
	 * <p>
	 * The manifold is exported as a triangle soup via the bridge's
	 * {@code exportMeshGL64} method. Each triangle becomes one JCSG {@link Polygon}
	 * (three {@link Vertex} objects with positions taken from the flat vertex
	 * array). Per-vertex normals are computed as the face normal so that JCSG
	 * downstream tools (BSP, boolean ops) have valid planes.
	 *
	 * @param ms
	 *            native manifold segment returned by the bridge import call
	 * @return a new {@link CSG} representing the same geometry
	 * @throws Throwable
	 *             if the native export call fails
	 * @throws IllegalArgumentException
	 *             if {@code manifold} is null
	 */
	public CSG fromManifold(MemorySegment ms, Color c) throws Throwable {
		if (ms == null)
			throw new IllegalArgumentException("manifold segment must not be null");
		MeshData64 mesh = this.manifold.exportMeshGL64(ms);

		double[] verts = mesh.vertices(); // flat [x0,y0,z0, x1,y1,z1, ...]
		long[] tris = mesh.triangles(); // flat [i0,i1,i2, i3,i4,i5, ...]
		int triCount = mesh.triCount();

		if (triCount == 0)
			return new CSG();

		CSG csg = new CSG(verts, tris, c);
		csg.setVolume(manifold.volume(ms));
		csg.setSurfaceArea(manifold.surfaceArea(ms));
		return csg;
	}

	/**
	 * Slices the given CSG at Z=0 and returns the resulting cross-section as a list
	 * of JCSG {@link Polygon} objects.
	 *
	 * <p>
	 * Each polygon is a flat contour in the Z=0 plane. Outer contours are wound
	 * counter-clockwise; holes are wound clockwise — matching the winding order
	 * that manifold_slice produces.
	 *
	 * <p>
	 * Contours with fewer than 3 vertices are skipped because they cannot form a
	 * valid polygon.
	 *
	 * @param csg
	 *            the solid to slice
	 * @return closed polygon contours of the cross-section at Z=0, never
	 *         {@code null}, may be empty if the plane misses the solid
	 * @throws Throwable
	 * @throws RuntimeException
	 *             wrapping any native call failure
	 */
	public ArrayList<Polygon> sliceAtZero(CSG incoming, Transform slicePlane, double offset) throws Throwable {
		CSG csg = incoming.transformed(slicePlane.inverse());
		MemorySegment csgm = null;
		try {
			csgm = toManifold(csg);
			checkResult(csgm);
			List<double[][]> contours = manifold.sliceWithOffset(csgm, 0.0, offset,
					((Math.abs(offset) < BezierPath.getMaximumInterpolationStep())
							? ManifoldBindings.JoinType.SQUARE
							: ManifoldBindings.JoinType.ROUND),
					Math.abs(BezierPath.getMaximumInterpolationStep()), 16);

			ArrayList<Polygon> result = new ArrayList<>(contours.size());

			for (double[][] contour : contours) {
				// Need at least 3 vertices to define a plane and a valid polygon.
				if (contour.length < 3) {
					continue;
				}

				ArrayList<Vector3d> points = new ArrayList<>(contour.length);
				for (double[] xy : contour) {
					// Z=0 because this is a cross-section at height 0.
					points.add(Vector3d.xyz(xy[0], xy[1], 0.0));
				}
				try {
					Polygon fromPoints = Polygon.fromPoints(points);
					result.add(fromPoints);
				} catch (ColinearPointsException ex) {
					System.err.println("Polygon returned failed normal");
				}
			}

			return result;

		} catch (Throwable e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to slice CSG at Z=0", e);
		} finally {
			if (csgm != null)
				manifold.delete(csgm);
		}
	}

	/**
	 * Slices the solid at the given Z height and returns the cross-section as flat
	 * polygons lying in the Z={@code height} plane. Outer contours wind
	 * counter-clockwise and holes clockwise, matching {@code manifold_slice}.
	 *
	 * <p>Contours with fewer than three vertices are skipped, and so are contours
	 * whose points are colinear, since neither can form a valid polygon.
	 *
	 * @param incoming the solid to slice
	 * @param height   Z coordinate of the cutting plane
	 * @return the cross-section contours; empty when the plane misses the solid
	 */
	public ArrayList<Polygon> slice(CSG incoming, double height) throws Throwable {
		MemorySegment csgm = null;
		try {
			csgm = toManifold(incoming);
			checkResult(csgm);
			List<double[][]> contours = manifold.slice(csgm, height);
			ArrayList<Polygon> result = new ArrayList<>(contours.size());
			for (double[][] contour : contours) {
				if (contour.length < 3)
					continue;
				ArrayList<Vector3d> points = new ArrayList<>(contour.length);
				for (double[] xy : contour)
					points.add(Vector3d.xyz(xy[0], xy[1], height));
				try {
					result.add(Polygon.fromPoints(points));
				} catch (ColinearPointsException ex) {
					System.err.println("Slice contour dropped, normal failed: " + ex.getMessage());
				}
			}
			return result;
		} finally {
			if (csgm != null)
				manifold.delete(csgm);
		}
	}

	/**
	 * Returns the union of two CSG solids. Uses {@code manifold.union(a, b)}
	 * directly (wrapper around {@code manifold_union} in the C library).
	 */
	public CSG minkowski_difference(CSG a, CSG b) throws Throwable {
		MemorySegment ma = toManifold(a);
		MemorySegment mb = toManifold(b);
		try {
			MemorySegment result = manifold.minkowski_difference(ma, mb);
			checkResult(result);
			CSG fromManifold = fromManifold(result, b.getColor());
			manifold.delete(result);
			return fromManifold;
		} finally {
			manifold.delete(ma);
			manifold.delete(mb);
		}
	}

	/**
	 * calculateAreaAndSurfaceArea
	 *
	 * calculate the area and surface area by converting to manifold object and
	 * back. the back step will set the area and volume fileds calculated by
	 * manifold.
	 *
	 * @param in
	 *            an unknown object
	 * @return the calculated manifold object
	 * @throws Throwable
	 *             if the object is not manifold then an error will
	 */
	public CSG calculateAreaAndSurfaceArea(CSG in) throws Throwable {
		MemorySegment man = toManifold(in);
		try {
			return fromManifold(man, in.getColor());
		} finally {
			manifold.delete(man);
		}
	}

	/**
	 * Returns a copy of the solid with the edges and facets below the given tolerance
	 * merged away, as Manifold's own {@code simplify} does. A mesh whose facets are
	 * thinner than a downstream consumer can resolve — a boolean near a seam, an STL
	 * of a part with folded sheet metal — closes the volume but carries edges no BRep
	 * kernel can represent; this is the pass that removes them.
	 *
	 * @param incoming
	 *            the solid to clean; must not be null
	 * @param tolerance
	 *            the merge distance, in model units
	 * @return the simplified solid
	 * @throws Throwable
	 *             if the native calls fail
	 */
	public CSG simplify(CSG incoming, double tolerance) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			MemorySegment original = manifold.asOriginal(man);
			manifold.delete(man);
			MemorySegment smooth = null;
			try {
				// the Manifold simplification loop: each pass collapses the short and
				// redundant edges the tolerance marks, and collapsing one edge can
				// mark the next, so the passes repeat until a pass changes nothing
				smooth = original;
				for (int pass = 0; pass < SIMPLIFY_MAX_PASSES; pass++) {
					long before = manifold.numTri(smooth);
					MemorySegment next = manifold.simplify(smooth, tolerance);
					manifold.delete(smooth);
					smooth = next;
					if (manifold.numTri(next) == before)
						break;
				}
				CSG fromManifold = fromManifold(smooth, incoming.getColor());
				manifold.delete(smooth);
				return fromManifold;
			} catch (Throwable t) {
				if (smooth != null)
					manifold.delete(smooth);
				else
					manifold.delete(original);
				throw t;
			}
		} catch (Throwable t) {
			if (t instanceof RuntimeException)
				throw (RuntimeException) t;
			throw t;
		}
	}

	/** The most Manifold simplify passes one simplify call may take to settle. */
	private static final int SIMPLIFY_MAX_PASSES = 16;

	/**
	 * Returns the union of two CSG solids. Uses {@code manifold.union(a, b)}
	 * directly (wrapper around {@code manifold_union} in the C library).
	 */
	public CSG minkowski_sum(CSG a, CSG b) throws Throwable {
		MemorySegment ma = toManifold(a);
		MemorySegment mb = toManifold(b);
		try {
			MemorySegment result = manifold.minkowski_sum(ma, mb);
			checkResult(result);
			CSG fromManifold = fromManifold(result, b.getColor());
			manifold.delete(result);
			return fromManifold;
		} finally {
			manifold.delete(ma);
			manifold.delete(mb);
		}
	}
	// -------------------------------------------------------------------------
	// Boolean operations
	// -------------------------------------------------------------------------

	/**
	 * Returns the union of two CSG solids. Uses {@code manifold.union(a, b)}
	 * directly (wrapper around {@code manifold_union} in the C library).
	 */
	public CSG union(CSG a, CSG b) throws Throwable {
		MemorySegment ma = toManifold(a);
		MemorySegment mb = toManifold(b);
		try {
			MemorySegment result = manifold.union(ma, mb);
			checkResult(result);
			CSG fromManifold = fromManifold(result, b.getColor());
			manifold.delete(result);
			return fromManifold;
		} finally {
			manifold.delete(ma);
			manifold.delete(mb);
		}
	}

	/**
	 * Returns the difference of two CSG solids (a minus b). Uses
	 * {@code manifold.difference(a, b)}.
	 */
	public CSG difference(CSG a, CSG b) throws Throwable {
		MemorySegment ma = toManifold(a);
		MemorySegment mb = toManifold(b);
		try {
			MemorySegment smooth = null;
			try {
				if (minkowskiMembrainRemoval) {
					MemorySegment tol = null;
					MemorySegment mink = null;
					MemorySegment result = null;
					MemorySegment or = null;
					try {
						tol = manifold.cube(MembrainTollerence, MembrainTollerence, MembrainTollerence, true);
						mink = manifold.minkowski_sum(mb, tol);
						result = manifold.difference(ma, mink);
						or = manifold.asOriginal(result);
						smooth = manifold.simplify(or, MembrainTollerence);
						CSG out = finishDifference(smooth, a);
						smooth = null; // consumed by finishDifference
						return out;
					} finally {
						manifold.safeDelete(tol);
						manifold.safeDelete(mink);
						manifold.safeDelete(result);
						manifold.safeDelete(or);
						// freed here when anything before finishDifference threw
						manifold.safeDelete(smooth);
					}
				} else {
					// the membrane cube must come from the native side: a JCSG Cube
					// this small triangulates to nothing, which would silently skip
					// the loop
					MemorySegment membrane = manifold.cube(MembrainTollerence, MembrainTollerence, MembrainTollerence,
							true);
					ManifoldBindings.MeshData64 membraneMesh;
					try {
						membraneMesh = manifold.exportMeshGL64(membrane);
					} finally {
						manifold.delete(membrane);
					}
					double[] membraneVerts = membraneMesh.vertices();
					smooth = ma;
					try {
						for (int i = 0; i < membraneMesh.vertCount(); i++) {
							MemorySegment bMoved = manifold.translate(mb, membraneVerts[i * 3],
									membraneVerts[i * 3 + 1], membraneVerts[i * 3 + 2]);
							MemorySegment lastOne = smooth;
							smooth = manifold.difference(smooth, bMoved);
							manifold.delete(bMoved);
							manifold.delete(lastOne);
						}
						return finishDifference(smooth, a);
					} catch (Throwable t) {
						manifold.safeDelete(smooth);
						throw t;
					} finally {
						manifold.delete(mb);
					}
				}
			} finally {
				// both branches consumed ma and mb internally; nothing left to free
			}
		} catch (Throwable t) {
			throw t;
		}
	}

	/** Status check, conversion and free shared by both difference branches. */
	private CSG finishDifference(MemorySegment smooth, CSG a) throws Throwable {
		try {
			checkResult(smooth);
			return fromManifold(smooth, a.getColor());
		} finally {
			manifold.delete(smooth);
		}
	}

	/**
	 * Returns the intersection of two CSG solids. Uses
	 * {@code manifold.intersection(a, b)}.
	 */
	public CSG intersection(CSG a, CSG b) throws Throwable {
		MemorySegment ma = toManifold(a);
		MemorySegment mb = toManifold(b);
		try {
			MemorySegment result = manifold.intersection(ma, mb);
			checkResult(result);
			CSG fromManifold = fromManifold(result, a.getColor());
			manifold.delete(result);
			return fromManifold;
		} finally {
			manifold.delete(ma);
			manifold.delete(mb);
		}
	}

	/**
	 * Runs a boolean of the given type without the membrane-removal treatment
	 * {@link #difference(CSG, CSG)} applies.
	 *
	 * @param a      the first solid
	 * @param b      the second solid
	 * @param op     the boolean operation to run
	 * @return the resulting solid, in {@code a}'s colour
	 */
	public CSG booleanOp(CSG a, CSG b, BooleanOp op) throws Throwable {
		MemorySegment ma = toManifold(a);
		MemorySegment mb = toManifold(b);
		try {
			MemorySegment result = manifold.booleanOp(ma, mb, op.toInt());
			checkResult(result);
			CSG fromManifold = fromManifold(result, a.getColor());
			manifold.delete(result);
			return fromManifold;
		} finally {
			manifold.delete(ma);
			manifold.delete(mb);
		}
	}

	/**
	 * Unions an arbitrary number of solids in a single batch operation.
	 *
	 * @param csgs the solids to union; must not be empty
	 * @return the combined solid, in the first solid's colour
	 */
	public CSG batchUnion(CSG... csgs) throws Throwable {
		if (csgs == null || csgs.length == 0)
			throw new IllegalArgumentException("at least one solid is required");
		MemorySegment[] shapes = new MemorySegment[csgs.length];
		for (int i = 0; i < csgs.length; i++) {
			shapes[i] = toManifold(csgs[i]);
		}
		try {
			MemorySegment result = manifold.batchUnion(shapes);
			checkResult(result);
			CSG fromManifold = fromManifold(result, csgs[0].getColor());
			manifold.delete(result);
			return fromManifold;
		} finally {
			for (MemorySegment shape : shapes)
				manifold.delete(shape);
		}
	}

	// -------------------------------------------------------------------------
	// Primitives
	// -------------------------------------------------------------------------

	/**
	 * Returns the unit tetrahedron Manifold generates, converted to a CSG.
	 */
	public CSG tetrahedron() throws Throwable {
		MemorySegment man = manifold.tetrahedron();
		try {
			checkResult(man);
			return fromManifold(man, CSG.getDefaultColor());
		} finally {
			manifold.delete(man);
		}
	}

	/**
	 * Returns an axis-aligned box. Matches {@link Cube#toCSG()} placement: the box
	 * is centred on the origin unless {@code centered} is false, in which case it
	 * sits in the first octant touching it.
	 *
	 * @param width    size along X
	 * @param height   size along Y
	 * @param depth    size along Z
	 * @param centered true to centre the box on the origin
	 * @return the box as a solid
	 */
	public CSG cube(double width, double height, double depth, boolean centered) throws Throwable {
		MemorySegment man = manifold.cube(width, height, depth, centered);
		try {
			checkResult(man);
			return fromManifold(man, CSG.getDefaultColor());
		} finally {
			manifold.delete(man);
		}
	}

	/**
	 * Returns a sphere centred on the origin.
	 *
	 * @param radius   radius of the sphere
	 * @param segments number of circular segments; 0 uses the Manifold quality
	 *                 defaults (see {@link #setCircularSegments(int)})
	 * @return the sphere as a solid
	 */
	public CSG sphere(double radius, int segments) throws Throwable {
		MemorySegment man = manifold.sphere(radius, segments);
		try {
			checkResult(man);
			return fromManifold(man, CSG.getDefaultColor());
		} finally {
			manifold.delete(man);
		}
	}

	/**
	 * Returns a cylinder along Z, matching {@code eu.mihosoft.vrl.v3d.Cylinder}'s
	 * placement: the flat faces are centred on the origin unless {@code centered}
	 * is false, in which case the bottom sits in the Z=0 plane.
	 *
	 * @param height    extent along Z
	 * @param radiusLow radius of the bottom circle
	 * @param radiusHigh radius of the top circle; a different value produces a
	 *                  cone or frustum
	 * @param segments  number of circular segments; 0 uses the quality defaults
	 * @param centered  1 to centre the height on the origin, 0 to sit the bottom
	 *                  face on Z=0
	 * @return the cylinder as a solid
	 */
	public CSG cylinder(double height, double radiusLow, double radiusHigh, int segments, int centered)
			throws Throwable {
		MemorySegment man = manifold.cylinder(height, radiusLow, radiusHigh, segments, centered);
		try {
			checkResult(man);
			return fromManifold(man, CSG.getDefaultColor());
		} finally {
			manifold.delete(man);
		}
	}

	// -------------------------------------------------------------------------
	// Convex hull
	// -------------------------------------------------------------------------

	/**
	 * Returns the convex hull of two CSG solids combined.
	 * <p>
	 * Manifold's {@code manifold_hull} operates on a single manifold, so we first
	 * union the two inputs to combine their point sets, then hull the result via
	 * {@code manifold.hull(MemorySegment)}.
	 */
	public CSG hull(CSG a) throws Throwable {
		MemorySegment ma = toManifold(a);
		checkResult(ma);
		try {
			MemorySegment result = manifold.hull(ma);
			checkResult(result);
			CSG fromManifold = fromManifold(result, a.getColor());
			manifold.delete(result);
			return fromManifold;
		} finally {
			manifold.delete(ma);
		}
	}

	/**
	 * Convenience overload: convex hull over an arbitrary number of CSG solids.
	 * Uses {@code manifold.batchHull(MemorySegment[])} which maps to
	 * {@code manifold_batch_hull}.
	 */
	public CSG hull(CSG... solids) throws Throwable {
		MemorySegment[] segs = new MemorySegment[solids.length];
		for (int i = 0; i < solids.length; i++) {
			segs[i] = toManifold(solids[i]);
		}
		try {
			MemorySegment result = manifold.batchHull(segs);
			checkResult(result);
			try {
				return fromManifold(result, solids[0].getColor());
			} finally {
				manifold.delete(result);
			}
		} finally {
			for (MemorySegment seg : segs)
				manifold.delete(seg);
		}
	}

	private void checkResult(MemorySegment... memorySegments) throws Throwable {
		for (int i = 0; i < memorySegments.length; i++) {
			MemorySegment ms = memorySegments[i];
			ManifoldError err = manifold.status(ms);
			// System.out.println("Status of Manifold Op is "+result);
			if (err != ManifoldError.NO_ERROR) {
				System.out.println("Status: " + err);
				System.out.println("Verts: " + manifold.numVert(ms));
				System.out.println("Tris: " + manifold.numTri(ms));
				System.out.println("Genus: " + manifold.genus(ms));
				throw new NonManifoldShapeError("Error was " + err);
			} else {
				// System.out.println("Manifold check ok!");
			}
		}
	}

	public CSG hull(List<Vector3d> points) throws Throwable {
		ArrayList<double[]> pts = new ArrayList<double[]>();
		for (int i = 0; i < points.size(); i++) {
			Vector3d v = points.get(i);
			double[] p = new double[]{v.x, v.y, v.z};
			pts.add(p);
		}
		MemorySegment mem = null;
		try {
			mem = manifold.hull(pts);
			checkResult(mem);

			CSG fromManifold = fromManifold(mem, CSG.getDefaultColor());
			manifold.delete(mem);
			mem = null;
			return fromManifold;
		} finally {
			if (mem != null)
				manifold.delete(mem);
		}
	}

	// -------------------------------------------------------------------------
	// Transformations
	// -------------------------------------------------------------------------

	/**
	 * Applies the given transform on the native side, avoiding the per-vertex
	 * rework {@link CSG#transformed(Transform)} performs in Java.
	 *
	 * @param incoming  the solid to move
	 * @param transform the transform to apply
	 * @return the transformed solid
	 */
	public CSG transformed(CSG incoming, Transform transform) throws Throwable {
		double[][] cols = toNativeTransformColumns(transform);
		return runSingleManifoldOp(incoming, m -> manifold.transform(m, cols[0][0], cols[0][1], cols[0][2], cols[1][0],
				cols[1][1], cols[1][2], cols[2][0], cols[2][1], cols[2][2], cols[3][0], cols[3][1], cols[3][2]));
	}

	/**
	 * Reads a JCSG transform into the four column triples the native
	 * {@code manifold_transform} expects: each triple is the X, Y, Z multiplier of
	 * one input coordinate, the last carrying the translation.
	 */
	private double[][] toNativeTransformColumns(Transform transform) {
		Matrix4d m = transform.getInternalMatrix();
		return new double[][]{ //
				{m.m00, m.m10, m.m20}, //
				{m.m01, m.m11, m.m21}, //
				{m.m02, m.m12, m.m22}, //
				{m.m03, m.m13, m.m23}};
	}

	/**
	 * Moves the solid by the given offset, computed on the native side.
	 */
	public CSG translate(CSG incoming, double x, double y, double z) throws Throwable {
		return runSingleManifoldOp(incoming, m -> manifold.translate(m, x, y, z));
	}

	/**
	 * Scales the solid about the origin by the given per-axis factors. A uniform
	 * factor keeps the shape; mixed factors distort it; negative factors mirror it
	 * and keep the result manifold.
	 */
	public CSG scale(CSG incoming, double x, double y, double z) throws Throwable {
		return runSingleManifoldOp(incoming, m -> manifold.scale(m, x, y, z));
	}

	/**
	 * Rotates the solid about the X, Y and Z axes, in that order, in degrees.
	 */
	public CSG rotate(CSG incoming, double x, double y, double z) throws Throwable {
		return runSingleManifoldOp(incoming, m -> manifold.rotate(m, x, y, z));
	}

	public CSG rotateX(CSG incoming, double degrees) throws Throwable {
		return rotate(incoming, degrees, 0, 0);
	}

	public CSG rotateY(CSG incoming, double degrees) throws Throwable {
		return rotate(incoming, 0, degrees, 0);
	}

	public CSG rotateZ(CSG incoming, double degrees) throws Throwable {
		return rotate(incoming, 0, 0, degrees);
	}

	/**
	 * Mirrors the solid across the plane through the origin with the given normal.
	 * The normal is normalised inside Manifold; a zero-length normal returns an
	 * empty solid.
	 */
	public CSG mirror(CSG incoming, double nx, double ny, double nz) throws Throwable {
		return runSingleManifoldOp(incoming, m -> manifold.mirror(m, nx, ny, nz));
	}

	/** Functional hook so each op reads as its native call. */
	private interface ManifoldOp {
		MemorySegment apply(MemorySegment m) throws Throwable;
	}

	private CSG runSingleManifoldOp(CSG incoming, ManifoldOp op) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			MemorySegment result = op.apply(man);
			checkResult(result);
			CSG fromManifold = fromManifold(result, incoming.getColor());
			manifold.delete(result);
			return fromManifold;
		} finally {
			manifold.delete(man);
		}
	}

	public void delete(MemorySegment back) throws Throwable {
		manifold.delete(back);
	}

	/**
	 * Returns an independent native handle to the same geometry as the given
	 * handle. Freeing either afterwards leaves the other usable.
	 */
	public MemorySegment copy(MemorySegment m) throws Throwable {
		return manifold.copy(m);
	}

	/**
	 * Rebases the handle's history, marking it an original so later operations
	 * treat it as fresh geometry. Geometry is unchanged.
	 */
	public MemorySegment asOriginal(MemorySegment m) throws Throwable {
		return manifold.asOriginal(m);
	}

	/** Releases the bindings handle; native segments must be freed separately. */
	public void close() {
		manifold.close();
	}

	// -------------------------------------------------------------------------
	// Analysis (all of these measure without changing the solid)
	// -------------------------------------------------------------------------

	/** Returns the enclosed volume, from the native measurement. */
	public double volume(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			double volume = manifold.volume(man);
			incoming.setVolume(volume);
			return volume;
		} finally {
			manifold.delete(man);
		}
	}

	/** Returns the total surface area of all faces. */
	public double surfaceArea(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			double surfaceArea = manifold.surfaceArea(man);
			incoming.setSurfaceArea(surfaceArea);
			return surfaceArea;
		} finally {
			manifold.delete(man);
		}
	}

	/** Returns the precision Manifold believes the geometry carries. */
	public double epsilon(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			return manifold.epsilon(man);
		} finally {
			manifold.delete(man);
		}
	}

	/**
	 * Returns the number of handles in the solid's surface: 0 for a sphere, 1 for
	 * a torus.
	 */
	public int genus(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			return manifold.genus(man);
		} finally {
			manifold.delete(man);
		}
	}

	/** Returns the number of vertices. */
	public long numVert(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			return manifold.numVert(man);
		} finally {
			manifold.delete(man);
		}
	}

	/** Returns the number of triangles. */
	public long numTri(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			return manifold.numTri(man);
		} finally {
			manifold.delete(man);
		}
	}

	/** Returns the number of edges. */
	public long numEdge(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			return manifold.numEdge(man);
		} finally {
			manifold.delete(man);
		}
	}

	/** Returns the number of per-vertex property channels. */
	public long numProp(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			return manifold.numProp(man);
		} finally {
			manifold.delete(man);
		}
	}

	/** Returns the native size of one Manifold handle. */
	public long manifoldSize() throws Throwable {
		return manifold.manifoldSize();
	}

	/** Returns the native size of one MeshGL64 handle. */
	public long meshGL64Size() throws Throwable {
		return manifold.meshGL64Size();
	}

	/** Returns the native size of one manifold vector handle. */
	public long vecSize() throws Throwable {
		return manifold.vecSize();
	}

	/**
	 * Returns the Manifold error classification of the solid,
	 * {@link ManifoldError#NO_ERROR} when it is a valid manifold.
	 */
	public ManifoldError status(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			return manifold.status(man);
		} finally {
			manifold.delete(man);
		}
	}

	/**
	 * Estimates the native memory the solid's geometry occupies, in bytes.
	 */
	public long estimateMemoryBytes(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			return manifold.estimateMemoryBytes(man);
		} finally {
			manifold.delete(man);
		}
	}

	// -------------------------------------------------------------------------
	// Refinement (changes the mesh, never the enclosed shape)
	// -------------------------------------------------------------------------

	/** Splits every edge into {@code n} pieces, giving a finer mesh. */
	public CSG refine(CSG incoming, int n) throws Throwable {
		return runSingleManifoldOp(incoming, m -> manifold.refine(m, n));
	}

	/** Splits every edge longer than {@code length} so no edge exceeds it. */
	public CSG refineToLength(CSG incoming, double length) throws Throwable {
		return runSingleManifoldOp(incoming, m -> manifold.refineToLength(m, length));
	}

	/**
	 * Refines until the faceted mesh stays within {@code tolerance} of the
	 * underlying smooth surface. The smooth surface comes from native tangent
	 * data that a CSG round trip does not carry, so without it the solid returns
	 * with its mesh unchanged.
	 */
	public CSG refineToTolerance(CSG incoming, double tolerance) throws Throwable {
		return runSingleManifoldOp(incoming, m -> manifold.refineToTolerance(m, tolerance));
	}

	/**
	 * Marks edges bent less than {@code minSharpAngle} degrees as smooth and fills
	 * in vertex normal properties; a later refine pass interpolates the curved
	 * surface from them. The properties live only in the native pipeline, so a
	 * solid without property channels (anything that arrived as CSG) passes
	 * through unchanged rather than reading missing normals inside Manifold.
	 */
	public CSG smoothByNormals(CSG incoming, int normalIdx) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			if (manifold.numProp(man) < normalIdx + 3)
				return incoming;
			MemorySegment result = manifold.smoothByNormals(man, normalIdx);
			checkResult(result);
			CSG fromManifold = fromManifold(result, incoming.getColor());
			manifold.delete(result);
			return fromManifold;
		} finally {
			manifold.delete(man);
		}
	}

	/**
	 * Fills in vertex normal properties for edges bent less than
	 * {@code minSharpAngle} degrees; pair with {@link #refine(CSG, int)} to see the
	 * smoothing take effect. Like {@link #smoothByNormals(CSG, int)} this needs
	 * property channels; without them the normals cannot be stored and the solid
	 * passes through unchanged.
	 */
	public CSG calculateNormals(CSG incoming, int normalIdx, double minSharpAngle) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			if (manifold.numProp(man) < normalIdx + 3 && normalIdx == 0)
				return incoming;
			MemorySegment result = manifold.calculateNormals(man, normalIdx, minSharpAngle);
			checkResult(result);
			CSG fromManifold = fromManifold(result, incoming.getColor());
			manifold.delete(result);
			return fromManifold;
		} finally {
			manifold.delete(man);
		}
	}

	/**
	 * Marks edges bent less than {@code minSharpAngle} degrees as sharp, gives
	 * everything else smooth tangents and refines in one native pass.
	 * {@code minSmoothness} blends the preserved sharp edges between fully sharp
	 * (0) and fully smooth (1).
	 */
	public CSG smoothOut(CSG incoming, double minSharpAngle, double minSmoothness) throws Throwable {
		return runSingleManifoldOp(incoming, m -> smoothOut(m, minSharpAngle, minSmoothness));
	}

	/**
	 * Smooths the manifold by eliminating edges whose dihedral angle is below
	 * {@code minSharpAngle} degrees. Edges above that threshold are kept sharp.
	 *
	 * {@code minSmoothness} blends the preserved sharp edges between fully sharp
	 * (0.0) and fully smooth (1.0).
	 *
	 * <p>
	 * Upstream removed {@code Manifold::SmoothOut} together with the
	 * {@code Smoothness} tangent API, so the tangent field is rebuilt here from the
	 * two primitives that survived: {@code CalculateNormals} records a normal per
	 * vertex split by the same dihedral threshold, and {@code SmoothByNormals}
	 * converts those normals into half-edge tangent vectors. The resulting tangent
	 * field matches the threshold semantics; {@code minSmoothness} is applied by
	 * widening the threshold as upstream documented (1.0 is equivalent to a
	 * {@code minSharpAngle} of 180). The temporary normal channels are removed
	 * afterwards so the property count is unchanged.
	 *
	 * Useful post-difference to dissolve thin sliver faces left by near-coincident
	 * surfaces. A {@code minSharpAngle} of 30–60 degrees is a reasonable starting
	 * point; increase it to dissolve more aggressive artifacts.
	 *
	 * @param m             the input manifold
	 * @param minSharpAngle edges sharper than this (degrees) are preserved
	 * @param minSmoothness smoothness at preserved edges [0.0 = sharp, 1.0 =
	 *                      smooth]
	 */
	public MemorySegment smoothOut(MemorySegment m, double minSharpAngle, double minSmoothness) throws Throwable {
		if (m == null)
			throw new IllegalArgumentException("manifold segment must not be null");
		int originalNumProp = (int) manifold.numProp(m);
		double blend = Math.max(0.0, Math.min(1.0, minSmoothness));
		double sharpAngle = minSharpAngle + (180.0 - minSharpAngle) * blend;

		// Normals land in the first free channels so user properties stay put.
		MemorySegment withNormals = manifold.calculateNormals(m, originalNumProp, sharpAngle);
		MemorySegment smoothed;
		try {
			smoothed = manifold.smoothByNormals(withNormals, originalNumProp);
		} finally {
			manifold.safeDelete(withNormals);
		}

		// Drop the temporary normal channels again; tangents live on the Manifold
		// itself and survive property changes.
		try {
			final int kept = originalNumProp;

			return manifold.setProperties(smoothed, kept, kept == 0 ? null : (newProp, position, oldProp) -> {
				MemorySegment src = oldProp.reinterpret(kept * (long) Double.BYTES);
				MemorySegment dst = newProp.reinterpret(kept * (long) Double.BYTES);
				for (int i = 0; i < kept; i++)
					dst.setAtIndex(ValueLayout.JAVA_DOUBLE, i, src.getAtIndex(ValueLayout.JAVA_DOUBLE, i));
			});
		} finally {
			manifold.safeDelete(smoothed);
		}
	}

	// -------------------------------------------------------------------------
	// Split / trim (cut one solid into pieces)
	// -------------------------------------------------------------------------

	/**
	 * Cuts the solid with the plane through the origin normal to (nx, ny, nz) and
	 * keeps the half on the positive-normal side of it.
	 *
	 * @param nx    plane normal X
	 * @param ny    plane normal Y
	 * @param nz    plane normal Z
	 * @param offset distance of the plane from the origin along the normal
	 * @return the kept half; empty when the plane misses the solid
	 */
	public CSG trimByPlane(CSG incoming, double nx, double ny, double nz, double offset) throws Throwable {
		return runSingleManifoldOp(incoming, m -> manifold.trimByPlane(m, nx, ny, nz, offset));
	}

	/**
	 * Cuts the solid with a plane through the origin normal to (nx, ny, nz) and
	 * returns both halves: first the side the normal points to, then the side it
	 * points away from.
	 *
	 * @param nx    plane normal X
	 * @param ny    plane normal Y
	 * @param nz    plane normal Z
	 * @param offset distance of the plane from the origin along the normal
	 * @return the two halves, in the solid's colour
	 */
	public List<CSG> splitByPlane(CSG incoming, double nx, double ny, double nz, double offset) throws Throwable {
		MemorySegment man = toManifold(incoming);
		MemorySegment positive = null;
		MemorySegment negative = null;
		MemorySegment cutter = null;
		try {
			// splitting against the opposite halfspace yields exactly the two
			// complementary halves of the solid
			cutter = manifold.trimByPlane(man, -nx, -ny, -nz, -offset);
			MemorySegment[] pair = manifold.split(man, cutter);
			positive = pair[0];
			negative = pair[1];
			checkResult(positive, negative);
			CSG pos = fromManifold(positive, incoming.getColor());
			CSG neg = fromManifold(negative, incoming.getColor());
			return new ArrayList<>(List.of(pos, neg));
		} finally {
			if (positive != null)
				manifold.delete(positive);
			if (negative != null)
				manifold.delete(negative);
			// split reads the cutter but does not take ownership of it
			if (cutter != null)
				manifold.delete(cutter);
			manifold.delete(man);
		}
	}

	/**
	 * Cuts {@code incoming} with {@code cutter} and returns both pieces: the part
	 * of the solid inside the cutter, then the part outside it.
	 */
	public List<CSG> split(CSG incoming, CSG cutter) throws Throwable {
		MemorySegment ma = toManifold(incoming);
		MemorySegment mb = toManifold(cutter);
		MemorySegment inside = null;
		MemorySegment outside = null;
		try {
			MemorySegment[] pair = manifold.split(ma, mb);
			inside = pair[0];
			outside = pair[1];
			checkResult(inside, outside);
			CSG in = fromManifold(inside, incoming.getColor());
			CSG out = fromManifold(outside, incoming.getColor());
			return new ArrayList<>(List.of(in, out));
		} finally {
			if (inside != null)
				manifold.delete(inside);
			if (outside != null)
				manifold.delete(outside);
			manifold.delete(ma);
			manifold.delete(mb);
		}
	}

	// -------------------------------------------------------------------------
	// Composition / Decomposition
	// -------------------------------------------------------------------------

	/**
	 * Merges the solids into one manifold without booleaning them together — the
	 * pieces may overlap or touch and keep their own surfaces, like an assembly in
	 * one file.
	 *
	 * @param parts the solids to combine; must not be empty
	 * @return the combined solid, in the first part's colour
	 */
	public CSG compose(CSG... parts) throws Throwable {
		if (parts == null || parts.length == 0)
			throw new IllegalArgumentException("at least one solid is required");
		MemorySegment[] segs = new MemorySegment[parts.length];
		for (int i = 0; i < parts.length; i++) {
			segs[i] = toManifold(parts[i]);
		}
		try {
			MemorySegment result = manifold.compose(segs);
			checkResult(result);
			CSG fromManifold = fromManifold(result, parts[0].getColor());
			manifold.delete(result);
			return fromManifold;
		} finally {
			for (MemorySegment seg : segs)
				manifold.delete(seg);
		}
	}

	/**
	 * Splits a solid into its connected components without booleaning anything —
	 * the pieces of a composed assembly come back as separate solids in input
	 * order, each keeping its own surface. An empty solid decomposes to an empty
	 * list.
	 *
	 * @param incoming
	 *            the solid to split; must not be null
	 * @return one solid per connected component, never {@code null}
	 * @throws Throwable
	 *             if the native calls fail or a component is not a valid manifold
	 */
	public List<CSG> decompose(CSG incoming) throws Throwable {
		// a CSG with no triangles is the empty solid this class's fromManifold
		// produces; it has nothing to decompose and no mesh to import
		if (incoming.getTriangles() == null || incoming.getTriangles().length == 0)
			return new ArrayList<>();
		MemorySegment man = toManifold(incoming);
		MemorySegment[] parts = null;
		try {
			parts = manifold.decompose(man);
			checkResult(parts);
			List<CSG> out = new ArrayList<>(parts.length);
			for (MemorySegment part : parts)
				out.add(fromManifold(part, incoming.getColor()));
			return out;
		} finally {
			// every component handle is freed here whether the conversion
			// succeeded or threw, so nothing native outlives the call
			if (parts != null)
				for (MemorySegment part : parts)
					manifold.safeDelete(part);
			manifold.delete(man);
		}
	}

	// -------------------------------------------------------------------------
	// Static quality globals (apply to every primitive built afterwards)
	// -------------------------------------------------------------------------

	/**
	 * Pins the circular segment count every following sphere, cylinder and
	 * revolve uses. A value of 0 releases the pin and returns to the angle and
	 * edge-length defaults. Higher values give smoother curves and more
	 * triangles.
	 */
	public void setCircularSegments(int segments) throws Throwable {
		manifold.setCircularSegments(segments);
	}

	/**
	 * Caps the angle between consecutive circular segments; smaller angles give
	 * smoother curves. Only applies while {@link #setCircularSegments(int)} is 0.
	 */
	public void setMinCircularAngle(double degrees) throws Throwable {
		manifold.setMinCircularAngle(degrees);
	}

	/**
	 * Returns the segment count a circle of the given radius would use under the
	 * current quality settings.
	 */
	public int getCircularSegments(double radius) throws Throwable {
		return manifold.getCircularSegments(radius);
	}

	// -------------------------------------------------------------------------
	// Bounds and centring
	// -------------------------------------------------------------------------

	/**
	 * Returns the axis-aligned bounds of {@code incoming}, measured by Manifold on
	 * the native side.
	 */
	public Bounds getBounds(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			double[] b = manifold.bounds(man);
			return new Bounds(new Vector3d(b[0], b[1], b[2]), new Vector3d(b[3], b[4], b[5]));
		} finally {
			manifold.delete(man);
		}
	}

	/**
	 * Moves the solid so its bounding-box centre sits at the origin.
	 */
	public CSG centerObject(CSG incoming) throws Throwable {
		MemorySegment man = toManifold(incoming);
		try {
			MemorySegment centred = manifold.centerObject(man);
			checkResult(centred);
			CSG fromManifold = fromManifold(centred, incoming.getColor());
			manifold.delete(centred);
			return fromManifold;
		} finally {
			manifold.delete(man);
		}
	}

	public CSG fromSTL(Path path) throws Throwable {
		MemorySegment man = manifold.importSTL(path.toFile());
		try {
			checkResult(man);
			CSG back = fromManifold(man, Color.ALICEBLUE);
			manifold.delete(man);
			return back;
		} catch (Throwable t) {
			if (man != null)
				manifold.delete(man);
			throw t;
		}
	}

	public void toSTL(CSG incoming, Path path) throws Throwable {
		MemorySegment man = null;
		try {
			man = toManifold(incoming);
			manifold.exportSTL(man, path.toFile());
			manifold.delete(man);
		} catch (Throwable e) {
			if (man != null)
				manifold.delete(man);
			throw e;
		}

	}

	/**
	 * Writes the solids to a 3MF file, each as its own build item. Geometry is
	 * written exactly, without booleaning the pieces together — use
	 * {@link #unionAll(List, ICSGProgress)} first when one merged body is wanted.
	 *
	 * @param csgs the solids to write; must not be empty
	 * @param path destination file
	 */
	public void to3MF(List<CSG> csgs, Path path) throws Throwable {
		if (csgs == null || csgs.isEmpty())
			throw new IllegalArgumentException("at least one solid is required");
		ArrayList<MemorySegment> meshes = new ArrayList<>(csgs.size());
		try {
			for (CSG csg : csgs)
				meshes.add(toManifold(csg));
			manifold.export3MF(meshes, path.toFile());
		} finally {
			for (MemorySegment mesh : meshes)
				manifold.delete(mesh);
		}
	}

	/** Writes a single solid to a 3MF file. */
	public void to3MF(CSG incoming, Path path) throws Throwable {
		to3MF(List.of(incoming), path);
	}

	/**
	 * Reads a 3MF file, returning one solid per {@code <object>} element in file
	 * order. Colour is not stored in the format Manifold writes, so every solid
	 * comes back in the default colour.
	 *
	 * @param path source file
	 * @return one solid per object, never {@code null}
	 */
	public List<CSG> from3MF(Path path) throws Throwable {
		ArrayList<MemorySegment> meshes = manifold.import3MF(path.toFile());
		try {
			List<CSG> out = new ArrayList<>(meshes.size());
			for (MemorySegment mesh : meshes) {
				checkResult(mesh);
				out.add(fromManifold(mesh, CSG.getDefaultColor()));
			}
			return out;
		} finally {
			for (MemorySegment mesh : meshes)
				manifold.delete(mesh);
		}
	}

	public static boolean isMinkowskiMembrainRemoval() {
		return minkowskiMembrainRemoval;
	}

	public static void setMinkowskiMembrainRemoval(boolean minkowskiMembrainRemoval) {
		CSGManifold3d.minkowskiMembrainRemoval = minkowskiMembrainRemoval;
	}

	public CSG unionAll(List<CSG> csgs, ICSGProgress progressMoniter) throws Throwable {
		MemorySegment[] shapes = new MemorySegment[csgs.size()];

		for (int i = 0; i < csgs.size(); i++) {
			shapes[i] = toManifold(csgs.get(i));
			if (progressMoniter != null)
				progressMoniter.progressUpdate(i, csgs.size() + 1, "Union All Load", null);
		}
		progressMoniter.progressUpdate(csgs.size(), csgs.size() + 1, "Run Union All", null);
		MemorySegment all = manifold.batchUnion(shapes);
		for (int i = 0; i < csgs.size(); i++) {
			manifold.delete(shapes[i]);
		}
		progressMoniter.progressUpdate(csgs.size() + 1, csgs.size() + 1, "Finishing Union All", null);
		CSG back = fromManifold(all, csgs.get(0).getColor());
		manifold.delete(all);
		return back;
	}

}

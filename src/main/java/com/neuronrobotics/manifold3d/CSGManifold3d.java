package com.neuronrobotics.manifold3d;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.cadoodlecad.manifold.ManifoldBindings;
import com.cadoodlecad.manifold.ManifoldBindings.ManifoldError;
import com.cadoodlecad.manifold.ManifoldBindings.MeshData64;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Vertex;
import javafx.scene.paint.Color;
@SuppressWarnings("preview")
public class CSGManifold3d {
	private final ManifoldBindings manifold;
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

		return new CSG(verts, tris, c);
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
	public ArrayList<Polygon> sliceAtZero(CSG incoming, Transform slicePlane) throws Throwable {
		CSG csg = incoming.transformed(slicePlane.inverse());
		MemorySegment csgm = null;
		try {
			csgm = toManifold(csg);
			checkResult(csgm);
			List<double[][]> contours = manifold.slice(csgm, 0.0);

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

				result.add(Polygon.fromPoints(points));
			}

			return result;

		} catch (Throwable e) {
			if (csgm != null)
				manifold.delete(csgm);
			throw new RuntimeException("Failed to slice CSG at Z=0", e);
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
			MemorySegment result = manifold.difference(ma, mb);
			MemorySegment smooth = manifold.simplify(result, 0.001);
			manifold.delete(result);
			checkResult(smooth);
			CSG fromManifold = fromManifold(smooth, a.getColor());
			manifold.delete(smooth);
			return fromManifold;
		} finally {
			manifold.delete(ma);
			manifold.delete(mb);
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
			return fromManifold(result, solids[0].getColor());
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

	public void delete(MemorySegment back) throws Throwable {
		manifold.delete(back);
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

	public void toStl(CSG incoming, Path path) {
		try {
			MemorySegment man = toManifold(incoming);
			manifold.exportSTL(man, path.toFile());
			manifold.delete(man);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}

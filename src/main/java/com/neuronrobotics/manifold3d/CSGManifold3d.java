package com.neuronrobotics.manifold3d;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cadoodlecad.manifold.ManifoldBindings;
import com.cadoodlecad.manifold.ManifoldBindings.ManifoldError;
import com.cadoodlecad.manifold.ManifoldBindings.MeshData64;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Vertex;
import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;

public class CSGManifold3d {
	private final ManifoldBindings manifold;
	//	private final Manifold3dExporter exporter;
	//	private final Manifold3dImporter importer;

	public CSGManifold3d() throws Exception {
		this.manifold = new ManifoldBindings();
		//		exporter = new Manifold3dExporter(manifold);
		//		importer = new Manifold3dImporter(manifold);
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

		List<Polygon> polygons = csg.getPolygons();
		if (polygons == null || polygons.isEmpty())
			throw new IllegalArgumentException("CSG has no polygons");

		// Build an indexed triangle mesh.
		// Use a tolerance-free exact key so we don't merge
		// numerically-close-but-distinct verts.
		Map<String, Integer> vertexIndex = new HashMap<>();
		List<double[]> vertexList = new ArrayList<>();
		List<Long> triList = new ArrayList<>();

		for (Polygon incoming : polygons) {
			for (Polygon poly : PolygonUtil.triangulatePolygon(incoming)) {
				List<Vertex> pverts = poly.getVertices();
				if (pverts == null || pverts.size() < 3)
					continue;

				// Fan triangulation: (0,1,2), (0,2,3), (0,3,4), ...
				int i0 = intern(pverts.get(0), vertexIndex, vertexList);
				for (int i = 1; i < pverts.size() - 1; i++) {
					int i1 = intern(pverts.get(i), vertexIndex, vertexList);
					int i2 = intern(pverts.get(i + 1), vertexIndex, vertexList);

					// Skip degenerate triangles (two or more identical indices).
					if (i0 == i1 || i1 == i2 || i0 == i2)
						continue;

					triList.add((long) i0);
					triList.add((long) i1);
					triList.add((long) i2);
				}
			}
		}

		if (triList.isEmpty())
			throw new IllegalArgumentException("CSG produced no valid triangles after triangulation");

		long nVerts = vertexList.size();
		long nTris = triList.size() / 3;

		// Flatten vertex list into a primitive array.
		double[] vertices = new double[(int) (nVerts * 3)];
		for (int i = 0; i < nVerts; i++) {
			double[] v = vertexList.get(i);
			vertices[i * 3] = v[0];
			vertices[i * 3 + 1] = v[1];
			vertices[i * 3 + 2] = v[2];
		}

		// Flatten triangle index list.
		long[] triangles = new long[triList.size()];
		for (int i = 0; i < triList.size(); i++) {
			triangles[i] = triList.get(i);
		}

		MemorySegment ms = manifold.importMeshGL64(vertices, triangles, nVerts, nTris);
		checkResult(ms);
		return ms;
	}

	// -------------------------------------------------------------------------
	// helpers


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
	public CSG fromManifold(MemorySegment ms) throws Throwable {
		if (ms == null)
			throw new IllegalArgumentException("manifold segment must not be null");
		MeshData64 mesh = this.manifold.exportMeshGL64(ms);

		double[] verts = mesh.vertices(); // flat [x0,y0,z0, x1,y1,z1, ...]
		long[] tris = mesh.triangles(); // flat [i0,i1,i2, i3,i4,i5, ...]
		int triCount = mesh.triCount();

		if (triCount == 0)
			return new CSG();

		ArrayList<Polygon> polygons = new ArrayList<>(triCount);

		for (int t = 0; t < triCount; t++) {
			int base = t * 3;

			Vector3d p0 = vertexAt(verts, (int) tris[base]);
			Vector3d p1 = vertexAt(verts, (int) tris[base + 1]);
			Vector3d p2 = vertexAt(verts, (int) tris[base + 2]);

			List<Vertex> vertices = Arrays.asList(new Vertex(p0), new Vertex(p1), new Vertex(p2));

			polygons.add(new Polygon(vertices));
		}

		return CSG.fromPolygons(polygons);
	}

	// -------------------------------------------------------------------------
	// helpers
	/**
	 * Returns the index of {@code v} in {@code vertexList}, inserting it if not
	 * already present. The key is an exact string representation of (x, y, z) using
	 * {@link Double#toHexString} so that only bit-identical positions are merged,
	 * matching the BSP's behavior.
	 */
	private static int intern(Vertex v, Map<String, Integer> index, List<double[]> list) {
		String key = Double.toHexString(v.pos.x) + "," + Double.toHexString(v.pos.y) + ","
				+ Double.toHexString(v.pos.z);

		return index.computeIfAbsent(key, k -> {
			int idx = list.size();
			list.add(new double[] { v.pos.x, v.pos.y, v.pos.z });
			return idx;
		});
	}

	private static Vector3d vertexAt(double[] verts, int index) {
		int base = index * 3;
		return new Vector3d(verts[base], verts[base + 1], verts[base + 2]);
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
			if(csgm!=null)
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
			return fromManifold(result);
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
			checkResult(result);
			return fromManifold(result);
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
			return fromManifold(result);
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
			return fromManifold(result);
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
			return fromManifold(result);
		} finally {
			for (MemorySegment seg : segs)
				manifold.delete(seg);
		}
	}

	private void checkResult(MemorySegment... memorySegments) throws Throwable {
		for (int i = 0; i < memorySegments.length; i++) {
			MemorySegment ms = memorySegments[i];
			ManifoldError result = manifold.status(ms);
			//System.out.println("Status of Manifold Op is "+result);
			if (result != ManifoldError.NO_ERROR)
				throw new NonManifoldShapeError("Error was " + result);
		}
	}


	public CSG hull(List<Vector3d> points) throws Throwable {
		ArrayList<double[]> pts = new ArrayList<double[]>();
		for (int i = 0; i < points.size(); i++) {
			Vector3d v = points.get(i);
			double[] p = new double[] { v.x, v.y, v.z };
			pts.add(p);
		}
		MemorySegment mem = null;
		try {
			mem = manifold.hull(pts);
			checkResult(mem);

			return fromManifold(mem);
		} finally {
			manifold.delete(mem);
		}
	}


}

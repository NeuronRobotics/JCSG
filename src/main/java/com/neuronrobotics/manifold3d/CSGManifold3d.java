package com.neuronrobotics.manifold3d;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import com.cadoodlecad.manifold.ManifoldBindings;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Vertex;

public class CSGManifold3d {
	private final ManifoldBindings manifold;
	private final Manifold3dExporter exporter;
	private final Manifold3dImporter importer;

	public CSGManifold3d() throws Exception {
		this.manifold = new ManifoldBindings();
		exporter = new Manifold3dExporter(manifold);
		importer = new Manifold3dImporter(manifold);
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
	private MemorySegment toManifold(CSG csg) throws Throwable {
		return exporter.toManifold(csg);
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
	 * @param manifold
	 *            native manifold segment returned by the bridge import call
	 * @return a new {@link CSG} representing the same geometry
	 * @throws Throwable
	 *             if the native export call fails
	 * @throws IllegalArgumentException
	 *             if {@code manifold} is null
	 */
	private CSG fromManifold(MemorySegment manifold) throws Throwable {
		return importer.fromManifold(manifold);
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
	 * @throws RuntimeException
	 *             wrapping any native call failure
	 */
	public ArrayList<Polygon> sliceAtZero(CSG incoming, Transform slicePlane) {
		CSG csg = incoming.transformed(slicePlane.inverse());
		try {
			MemorySegment csgm = toManifold(csg);
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

		} catch (RuntimeException e) {
			throw e;
		} catch (Throwable e) {
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
			return fromManifold(result);
		} finally {
			manifold.deleteMeshGL64(ma);
			manifold.deleteMeshGL64(mb);
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
			return fromManifold(result);
		} finally {
			manifold.deleteMeshGL64(ma);
			manifold.deleteMeshGL64(mb);
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
			return fromManifold(result);
		} finally {
			manifold.deleteMeshGL64(ma);
			manifold.deleteMeshGL64(mb);
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
		try {
			MemorySegment result = manifold.hull(ma);
			return fromManifold(result);
		} finally {
			manifold.deleteMeshGL64(ma);
		}
	}

	/**
	 * Convenience overload: convex hull over an arbitrary number of CSG solids.
	 * Uses {@code manifold.batchHull(MemorySegment[])} which maps to
	 * {@code manifold_batch_hull}.
	 */
	public CSG hull(CSG... solids) throws Throwable {
		MemorySegment[] segs = new MemorySegment[solids.length];
		for (int i = 0; i < solids.length; i++)
			segs[i] = toManifold(solids[i]);
		try {
			MemorySegment result = manifold.batchHull(segs);
			return fromManifold(result);
		} finally {
			for (MemorySegment seg : segs)
				manifold.deleteMeshGL64(seg);
		}
	}

	// -------------------------------------------------------------------------
	// Slice at a plane → List<Polygon>
	// -------------------------------------------------------------------------

	// /**
	// * Slices a CSG solid at a horizontal plane (Z = height) and returns the
	// * resulting cross-section contours as JCSG {@link Polygon}s.
	// * <p>
	// * Manifold's {@code manifold_slice(mem, m, height)} always cuts perpendicular
	// * to the Z axis and returns a {@code ManifoldPolygons*}. Each contour ring
	// * is reconstructed here as a JCSG {@link Polygon} lying in the XY plane at
	// * {@code z = height}.
	// * <p>
	// * To cut along an arbitrary plane, rotate the solid so that the desired
	// * normal aligns with +Z, call this method, then reverse-rotate the polygons.
	// *
	// * @param csg the solid to slice
	// * @param height Z coordinate of the cutting plane
	// * @return cross-section polygons (may be empty for solids that don't reach
	// * that height)
	// * @throws Throwable if the native call fails
	// */
	// public List<Polygon> sliceAtZ(CSG csg, double height) throws Throwable {
	// MemorySegment m = toManifold(csg);
	// try {
	// // manifold.slice(MemorySegment m, double height) →
	// // ManifoldPolygons* manifold_slice(void* mem, ManifoldManifold* m, double
	// height)
	// MemorySegment polygonsSeg = manifold.slice(m, height);
	// try {
	// return importer.fromManifoldPolygons(polygonsSeg, height);
	// } finally {
	// manifold.deletePolygons(polygonsSeg);
	// }
	// } finally {
	// manifold.deleteMeshGL64(m);
	// }
	// }

	// -------------------------------------------------------------------------
	// STL import / export
	// -------------------------------------------------------------------------

	// /**
	// * Exports a CSG solid to an STL file via Manifold's mesh pipeline.
	// * <p>
	// * The geometry is round-tripped through Manifold's MeshGL64 representation
	// * and written by the {@link Manifold3dSTLExporter} helper.
	// * <p>
	// * <b>Note:</b> STL is lossy – it has no topology encoding. Prefer 3MF for
	// * any round-trip use-case.
	// *
	// * @param csg the solid to export
	// * @param file destination STL file (created or overwritten)
	// * @throws Throwable if export or file-write fails
	// */
	// public void exportSTL(CSG csg, File file) throws Throwable {
	// MemorySegment m = toManifold(csg);
	// try {
	// ManifoldBindings.MeshData64 mesh = manifold.exportMeshGL64(m);
	// Manifold3dSTLExporter.write(mesh, file);
	// } finally {
	// manifold.deleteMeshGL64(m);
	// }
	// }
	//
	// /**
	// * Imports an STL file and returns its geometry as a CSG solid.
	// * <p>
	// * The file is parsed by {@link Manifold3dSTLImporter} into raw
	// vertex/triangle
	// * arrays that are fed into {@code manifold.importMeshGL64(...)}, which merges
	// * duplicate vertices and validates manifoldness before handing back a native
	// * manifold segment that is then converted to CSG.
	// *
	// * @param file the STL file to read
	// * @return a new {@link CSG} representing the imported geometry
	// * @throws Throwable if parsing or the native import fails
	// * @throws IllegalArgumentException if the file does not exist
	// */
	// public CSG importSTL(File file) throws Throwable {
	// if (!file.exists())
	// throw new IllegalArgumentException("STL file not found: " +
	// file.getAbsolutePath());
	// Manifold3dSTLImporter.RawMesh raw = Manifold3dSTLImporter.read(file);
	// MemorySegment m = manifold.importMeshGL64(raw.vertices(), raw.triangles(),
	// raw.vertCount(), raw.triCount());
	// try {
	// return fromManifold(m);
	// } finally {
	// manifold.deleteMeshGL64(m);
	// }
	// }
	//
	// // -------------------------------------------------------------------------
	// // 3MF import / export
	// // -------------------------------------------------------------------------
	//
	// /**
	// * Exports a CSG solid to a 3MF file.
	// * <p>
	// * 3MF preserves topology and is lossless – recommended over STL for any
	// * workflow that re-imports the file.
	// *
	// * @param csg the solid to export
	// * @param file destination 3MF file (created or overwritten)
	// * @throws Throwable if export or file-write fails
	// */
	// public void export3MF(CSG csg, File file) throws Throwable {
	// MemorySegment m = toManifold(csg);
	// try {
	// ManifoldBindings.MeshData64 mesh = manifold.exportMeshGL64(m);
	// Manifold3d3MFExporter.write(mesh, file);
	// } finally {
	// manifold.deleteMeshGL64(m);
	// }
	// }
	//
	// /**
	// * Imports a 3MF file and returns the first body as a CSG solid.
	// *
	// * @param file the 3MF file to read
	// * @return a new {@link CSG} representing the imported geometry
	// * @throws Throwable if parsing or the native import fails
	// * @throws IllegalArgumentException if the file does not exist
	// */
	// public CSG import3MF(File file) throws Throwable {
	// if (!file.exists())
	// throw new IllegalArgumentException("3MF file not found: " +
	// file.getAbsolutePath());
	// Manifold3dSTLImporter.RawMesh raw = Manifold3d3MFImporter.read(file);
	// MemorySegment m = manifold.importMeshGL64(raw.vertices(), raw.triangles(),
	// raw.vertCount(), raw.triCount());
	// try {
	// return fromManifold(m);
	// } finally {
	// manifold.deleteMeshGL64(m);
	// }
	// }

}

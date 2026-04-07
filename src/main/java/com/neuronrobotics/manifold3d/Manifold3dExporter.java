package com.neuronrobotics.manifold3d;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cadoodlecad.manifold.ManifoldBindings;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Vertex;

/**
 * Exports a JCSG {@link CSG} object into a native manifold3d manifold via the bridge API.
 *
 * <p>Usage:
 * <pre>{@code
 * Manifold3dBridge bridge = ...; // your wrapper holding the MethodHandle map
 * CSG csg = ...;
 *
 * MemorySegment manifoldSeg = new Manifold3dExporter(bridge).toManifold(csg);
 * // use manifoldSeg with bridge boolean operations, then clean up via bridge.delete(...)
 * }</pre>
 *
 * <p>JCSG polygons may have more than three vertices (the BSP representation preserves
 * quads and n-gons). This exporter triangulates each polygon using a simple fan from the
 * first vertex (safe for convex polygons, which is guaranteed by the JCSG BSP). The
 * resulting triangle soup is de-duplicated into an indexed mesh before being handed to
 * the bridge so that manifold3d's merge step has shared vertices to work with.
 */
public class Manifold3dExporter {

	private final ManifoldBindings bridge;

	public Manifold3dExporter(ManifoldBindings bridge) {
		if (bridge == null)
			throw new IllegalArgumentException("bridge must not be null");
		this.bridge = bridge;
	}

	/**
	 * Converts a JCSG {@link CSG} into a native manifold {@link MemorySegment}.
	 *
	 * <p>The caller is responsible for eventually freeing the returned segment via the
	 * bridge's delete method (e.g. {@code manifold_delete_manifold}).
	 *
	 * @param csg the solid to export; must not be null
	 * @return a native manifold segment ready for boolean operations
	 * @throws Throwable if the native import call fails
	 * @throws IllegalArgumentException if {@code csg} is null or has no polygons
	 */
	public MemorySegment toManifold(CSG csg) throws Throwable {
		if (csg == null)
			throw new IllegalArgumentException("csg must not be null");

		List<Polygon> polygons = csg.getPolygons();
		if (polygons == null || polygons.isEmpty())
			throw new IllegalArgumentException("CSG has no polygons");

		// Build an indexed triangle mesh.
		// Use a tolerance-free exact key so we don't merge numerically-close-but-distinct verts.
		Map<String, Integer> vertexIndex = new HashMap<>();
		List<double[]> vertexList = new ArrayList<>();
		List<Long> triList = new ArrayList<>();

		for (Polygon poly : polygons) {
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

		return bridge.importMeshGL64(vertices, triangles, nVerts, nTris);
	}

	// -------------------------------------------------------------------------
	// helpers

	/**
	 * Returns the index of {@code v} in {@code vertexList}, inserting it if not already present.
	 * The key is an exact string representation of (x, y, z) using {@link Double#toHexString}
	 * so that only bit-identical positions are merged, matching the BSP's behaviour.
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
}
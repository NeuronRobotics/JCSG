package com.neuronrobotics.manifold3d;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Vertex;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.cadoodlecad.manifold.ManifoldBindings;
import com.cadoodlecad.manifold.ManifoldBindings.MeshData64;

/**
 * Imports a manifold3d mesh (via its native bridge API) into a JCSG {@link CSG}
 * object.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * Manifold3dBridge bridge = ...; // your wrapper holding the MethodHandle map
 * MemorySegment manifoldSeg = bridge.importMeshGL64(vertices, triangles, nVerts, nTris);
 *
 * CSG csg = new Manifold3dImporter(bridge).fromManifold(manifoldSeg);
 * }</pre>
 *
 * <p>
 * The bridge instance must expose {@code exportMeshGL64(MemorySegment)}
 * returning a {@code MeshData64} record with fields {@code double[] vertices},
 * {@code long[] triangles}, {@code int vertCount}, and {@code int triCount}.
 */
public class Manifold3dImporter {

	private ManifoldBindings bridge;

	public Manifold3dImporter(ManifoldBindings bridge) {
		if (bridge == null)
			throw new IllegalArgumentException("bridge must not be null");
		this.bridge = bridge;
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
	public CSG fromManifold(MemorySegment manifold) throws Throwable {
		if (manifold == null)
			throw new IllegalArgumentException("manifold segment must not be null");

		MeshData64 mesh = bridge.exportMeshGL64(manifold);

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

			// Face normal (used as the vertex normal for all three corners).
			Vector3d edge1 = p1.minus(p0);
			Vector3d edge2 = p2.minus(p0);
			Vector3d normal = edge1.cross(edge2).normalized();

			List<Vertex> vertices = Arrays.asList(new Vertex(p0), new Vertex(p1), new Vertex(p2));

			polygons.add(new Polygon(vertices));
		}

		return CSG.fromPolygons(polygons);
	}

	// -------------------------------------------------------------------------
	// helpers

	private static Vector3d vertexAt(double[] verts, int index) {
		int base = index * 3;
		return new Vector3d(verts[base], verts[base + 1], verts[base + 2]);
	}
}

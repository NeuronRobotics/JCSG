package eu.mihosoft.vrl.v3d;

import java.util.ArrayList;

import javafx.scene.shape.TriangleMesh;

public class CSGtoJavafx {

	public static TriangleMesh legacy_meshFromPolygon(CSG source) {
		TriangleMesh mesh = new TriangleMesh();

		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;

		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;

		// One texture pair per mesh (not used currently)
		mesh.getTexCoords().addAll(0, 0);

		int vertexOffset = 0;
		ArrayList<Polygon> poly;
		try {
			poly = source.generatePolygonsFromMesh();
		} catch (ColinearPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return mesh;
		}
		// Process a polygon, triangulate if needed
		for (int j = 0; j < poly.size(); j++) {

			Polygon p = poly.get(j);
			if (p.getVertices().size() >= 3) {

				// Add all polygon vertices to the mesh, and get the bounds min and max
				for (Vertex v : p.getVertices()) {
					mesh.getPoints().addAll((float) v.pos.x, (float) v.pos.y, (float) v.pos.z);

					if (v.pos.x < minX)
						minX = v.pos.x;

					if (v.pos.y < minY)
						minY = v.pos.y;

					if (v.pos.z < minZ)
						minZ = v.pos.z;

					if (v.pos.x > maxX)
						maxX = v.pos.x;

					if (v.pos.y > maxY)
						maxY = v.pos.y;

					if (v.pos.z > maxZ)
						maxZ = v.pos.z;
				} // end for

				// Add the vertex indexes (0, 1, 2) (0, 2, 3) (0, 3, 4) etc.
				for (int i = 0; i < p.getVertices().size() - 2; i++) {
					mesh.getFaces().addAll(vertexOffset + 0, 0, // always first vertex of polygon
							vertexOffset + i + 1, 0, // second vertex
							vertexOffset + i + 2, 0); // third vertex
				}
				vertexOffset += p.getVertices().size();

			} // end if #verts >= 3
		} // end for
		return mesh;
	}
	public static TriangleMesh meshFromPolygon(CSG source) {
		TriangleMesh mesh = new TriangleMesh();
		mesh.getTexCoords().addAll(0, 0);

		long[] triangles = source.getTriangles();
		int triCount = (int) source.getTriCount();

		for (int j = 0; j < triCount; j++) {
			int i0 = (int) triangles[j * 3 + 0];
			int i1 = (int) triangles[j * 3 + 1];
			int i2 = (int) triangles[j * 3 + 2];

			// Duplicate the vertices — don't share them across triangles
			int base = j * 3;

			mesh.getPoints().addAll((float) source.getVertex_X(i0), (float) source.getVertex_Y(i0),
					(float) source.getVertex_Z(i0), (float) source.getVertex_X(i1), (float) source.getVertex_Y(i1),
					(float) source.getVertex_Z(i1), (float) source.getVertex_X(i2), (float) source.getVertex_Y(i2),
					(float) source.getVertex_Z(i2));

			// Each triangle gets its own 3 vertex slots
			mesh.getFaces().addAll(base + 0, 0, base + 1, 0, base + 2, 0);
		}

		return mesh;
	}

}

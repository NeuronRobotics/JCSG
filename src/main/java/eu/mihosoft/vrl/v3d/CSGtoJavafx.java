package eu.mihosoft.vrl.v3d;

import java.util.Arrays;
import java.util.List;

import javafx.scene.shape.TriangleMesh;

public class CSGtoJavafx {

	public static MeshContainer meshFromPolygon(Polygon... poly) {
		return meshFromPolygon(Arrays.asList(poly));
	}

	// Uses fan triangulation, works for convex polygons only!
	public static MeshContainer meshFromPolygon(List<Polygon> poly) {
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

		// Process a polygon, triangulate if needed
		for (int j = 0; j < poly.size(); j++) {

			Polygon p = poly.get(j);
			if (p.getVertices().size() >= 3) {

				// Add all polygon vertices to the mesh, and get the bounds min and max
				for (Vertex v : p.getVertices()) {
					mesh.getPoints().addAll((float)v.pos.x, (float)v.pos.y, (float)v.pos.z);

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
					mesh.getFaces().addAll(
						vertexOffset + 0    , 0,  // always first vertex of polygon
						vertexOffset + i + 1, 0,  // second vertex  
						vertexOffset + i + 2, 0); // third vertex
				}
				vertexOffset += p.getVertices().size();

			} // end if #verts >= 3
		} // end for

		return new MeshContainer(new Vector3d(minX, minY, minZ), new Vector3d(maxX, maxY, maxZ), mesh);
	}

}

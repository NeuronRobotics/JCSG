package eu.mihosoft.vrl.v3d;

import java.util.Arrays;
import java.util.List;

import javafx.scene.shape.TriangleMesh;

public class CSGtoJavafx {

	// Uses fan triangulation, works for convex polygons only!
	public static TriangleMesh meshFromPolygon(CSG source) {
		TriangleMesh mesh = new TriangleMesh();

		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;

		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;

		// One texture pair per mesh (not used currently)
		mesh.getTexCoords().addAll(0, 0);

		// Add all polygon vertices to the mesh, and get the bounds min and max
		for (int i=0;i<source.getVertCount();i++) {

			double vertex_X = source.getVertex_X(i);
			double vertex_Y = source.getVertex_Y(i);
			double vertex_Z = source.getVertex_Z(i);
			mesh.getPoints().addAll((float) vertex_X, (float) vertex_Y, (float) vertex_Z);
			if (vertex_X < minX)
				minX = vertex_X;

			if (vertex_Y < minY)
				minY = vertex_Y;

			if (vertex_Z < minZ)
				minZ = vertex_Z;

			if (vertex_X > maxX)
				maxX = vertex_X;

			if (vertex_Y > maxY)
				maxY = vertex_Y;

			if (vertex_Z > maxZ)
				maxZ = vertex_Z;
		} // end for
		// Process a polygon, triangulate if needed
		for (int j = 0; j < source.getTriCount(); j++) {
				mesh.getFaces().addAll((int)source.getTriangles()[j*3+0], 0, // always first vertex of polygon
						(int)source.getTriangles()[j*3+1], 0, // second vertex
						(int)source.getTriangles()[j*3+2], 0); // third vertex
			
		} // end for

		return mesh;// new MeshContainer(new Vector3d(minX, minY, minZ), new Vector3d(maxX, maxY, maxZ), mesh);
	}

}

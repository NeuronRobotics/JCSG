package eu.mihosoft.vrl.v3d;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.junit.Test;

public class TestFlatData {

	@Test
	public void testFlatData() throws ColinearPointsException, IOException {
		CSG cube = new Cube(20).toCSG();
		cube.toStl(Paths.get("FlatData-cube.stl"));
		ArrayList<Polygon> polygons = cube.generatePolygonsFromMesh();
		CSG fromPoly = new CSG(polygons);
		fromPoly.toStl(Paths.get("FlatData-loaded.stl"));
	}
}

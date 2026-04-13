package eu.mihosoft.vrl.v3d;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.junit.Test;

public class TestFlatData {

	@Test
	public void testFlatData() throws ColinearPointsException, IOException {
		CSG cube = new Cube(20).toCSG();
		FileUtil.write(Paths.get("FlatData-cube.stl"),cube.toStlString());
		ArrayList<Polygon> polygons = cube.generatePolygonsFromMesh();
		CSG fromPoly = new CSG(polygons);
		FileUtil.write(Paths.get("FlatData-loaded.stl"),fromPoly.toStlString());

		
	}
}

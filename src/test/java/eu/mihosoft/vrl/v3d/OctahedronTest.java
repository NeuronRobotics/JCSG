package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;

public class OctahedronTest {

	@Test
	public void test() throws IOException {
		double radius = 10;

		CSG octahedron = new Octahedron(radius).toCSG();
		CSG box = new Cube(3 * radius).toCSG().difference(new Cube(2 * radius).toCSG());
		CSG insphere = new Sphere(Math.sqrt(6) / 6 * radius).toCSG();

		assertTrue(octahedron.intersect(box).getNumberOfTriangles() == 0);
		assertTrue(insphere.difference(octahedron).getNumberOfTriangles() == 0);
		octahedron.toStl(Paths.get("octahedron.stl"));
	}

}

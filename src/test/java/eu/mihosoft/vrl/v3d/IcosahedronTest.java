package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.manifold3d.NonManifoldShapeError;

import java.io.IOException;
import java.nio.file.Paths;

public class IcosahedronTest {

	@Test
	public void test() throws IOException, ColinearPointsException, NonManifoldShapeError {
		double radius = 10;

		CSG icosahedron = new Icosahedron(radius).toCSG();
		CSG box = new Cube(3 * radius).toCSG().difference(new Cube(1.7013016167 * radius).toCSG());
		CSG insphere = new Sphere(0.794654472292 * radius).toCSG();

		// assertTrue(icosahedron.intersect(box).getPolygons().size() == 0);
		// assertTrue(insphere.difference(icosahedron).getPolygons().size() == 0);
		//
		icosahedron.toStl(Paths.get("icosahedron.stl"));
	}

}

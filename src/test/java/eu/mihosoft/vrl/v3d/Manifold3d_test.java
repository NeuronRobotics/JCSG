package eu.mihosoft.vrl.v3d;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG.OptType;
import eu.mihosoft.vrl.v3d.svg.SVGExporter;

public class Manifold3d_test {
	@Test
	public void loadTest() throws Throwable {
		OptType og = CSG.getDefaultOptionType();

		try {
			CSG.setDefaultOptType(OptType.Manifold3d);
			CSG cube = new Cube(50, 50, 50).toCSG();
			CSG sphere = new Sphere(30, 10, 10).toCSG();
			sphere.toStl(Paths.get("Manifole-sphere.stl"));
			List<Polygon> polygons = Slice.slice(sphere, new Transform(), 0);
			SVGExporter.export(polygons, new File("Manifold-SVGExportTest.svg"), false);
			CSG difference = cube.difference(sphere);
			CSG intersect = cube.intersect(sphere);
			CSG union = cube.union(sphere);
			CSG mirrored = union.mirrorx();
			mirrored.toStl(Paths.get("Manifole-mirror.stl"));
			union.toStl(Paths.get("Manifole-union.stl"));
			difference.toStl(Paths.get("Manifole-difference.stl"));
			intersect.toStl(Paths.get("Manifole-intersect.stl"));
			CSG hull = union.hull();
			hull.toStl(Paths.get("Manifole-hull.stl"));
		} catch (Throwable t) {
			t.printStackTrace();
			// Set back to default to complete test and not disrupt other tests
			CSG.setDefaultOptType(og);
			throw t;
		}
		// Set back to default to complete test and not disrupt other tests
		CSG.setDefaultOptType(og);
	}
}

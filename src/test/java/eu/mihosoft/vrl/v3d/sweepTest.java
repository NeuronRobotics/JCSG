package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.svg.SVGLoad;

public class sweepTest {

	@Test
	public void test() throws IOException, ColinearPointsException {

		File svg = new File("Test.SVG");
		if (!svg.exists())
			throw new RuntimeException("Test file missing!" + svg.getAbsolutePath());
		SVGLoad s = new SVGLoad(svg.toURI());
		HashMap<String, List<Polygon>> polys = s.toPolygons();
		Polygon p = polys.get(polys.keySet().toArray()[0]).get(0);
		Bounds b = p.getBounds();
		double sweepTot = 720;
		double d = sweepTot / 360;
		int steps = (int) (30 * d);
		double angle = sweepTot / steps;

		double z = 0 * d / steps;
		double radius = 0;
		if (angle < 0)
			angle = -angle;
		double sprl = 10;
		Transform centerandAllignedPolygon = new Transform().movex(-b.getMinX()).movey(-b.getMinY());
		Transform increment = new Transform().rotY(-angle).movey(z);
		Transform radiusT = new Transform().movex(radius);
		Polygon transformedP = p.transformed(centerandAllignedPolygon);
		ITransformProvider pr = (unit, domain) -> {
			return new Transform().movex(sprl * unit * d);
		};
		CSG text = Extrude.sweep(transformedP, increment, radiusT, steps, pr).rotx(-90);
		text.toStl(Paths.get("exampleSweep.stl"));
	}

}

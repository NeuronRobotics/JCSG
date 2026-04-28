package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Paths;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG.OptType;

public class StlExportTest {

	@Test

	public void makeBadSTL() throws Throwable {
		CSG.setDefaultOptType(OptType.Manifold3d);
		// Vector3d.setEXPORTEPSILON(1.0e-10);
		CSG.setUseGPU(true);
		CSG.setPreventNonManifoldTriangles(true);
		long start = System.currentTimeMillis();
		CSG badExport2 = CSG.text(" A QUICK BROWN fox jumps over the lazy dog", 10, 30, "Serif Regular").toZMin()
				.toXMin().toYMin();
		System.out.println("First text loaded");
		CSG movey = new Cube(badExport2.getTotalX() + 10, badExport2.getTotalY() + 10, 10).toCSG().toZMin().toXMin()
				.toYMin().movey(-5).movex(-5);

		movey.difference(badExport2).scaleToMeasurmentX(180).toStl(Paths.get("4-InMemTextDifferencedStl.stl"));
		System.out.println("Difference " + (System.currentTimeMillis() - start));

		CSG badExport = CSG.text("THis is some ", 10);
		System.out.println("Second text loaded");
		// badExport2=new Cube(20).toCSG().movey(30);
		// badExport=new Cube(20).toCSG();

		badExport = badExport.union(badExport2).scaleToMeasurmentX(160).scaleToMeasurmentY(30);
		CSG inMem = badExport;
		// String filename ="TextStl.stl";

		badExport.toStl(Paths.get("1-TextStl.stl"));
		System.out.println("Load saved stl");
		File file = new File("1-TextStl.stl");
		CSG loaded = STL.file(file.toPath());
		loaded.toStl(Paths.get("2-TextLoadedStl.stl"));
		System.out.println("Perform scale");
		badExport = loaded;
		badExport.toStl(Paths.get("3-TextScaledStl.stl"));
		System.out.println("Perform difference");
		CSG difference = movey.difference(badExport);
		difference.toStl(Paths.get("5-TextDifferencedStl.stl"));
		System.out.println("Perform Rotate");
		badExport = difference.rotx(35).roty(45);
		badExport.toStl(Paths.get("6-TextDiffRotatedStl.stl"));
		double done = System.currentTimeMillis() - start;
		System.out.println("Finished, took " + (done / 1000.0) + " seconds ");
	}

}

package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Test;

public class StlExportTest {

	@Test
	public void makeBadSTL() throws IOException {
		long start = System.currentTimeMillis();
		Plane.setEPSILON(1.0e-7);
//		Vector3d.setEXPORTEPSILON(1.0e-10);
		CSG.setUseGPU(true);
		CSG.setPreventNonManifoldTriangles(true);
		CSG badExport2 = CSG.text(" A QUICK BROWN fox ", 10,30,"Serif Regular").movey(30);
		System.out.println("First text loaded");
		CSG badExport = CSG.text("THis is some ", 10);
		System.out.println("Second text loaded");
//		badExport2=new Cube(20).toCSG().movey(30);
//		badExport=new Cube(20).toCSG();
		
		badExport=badExport.union(badExport2).scaleToMeasurmentX(160).scaleToMeasurmentY(30);
		CSG inMem = badExport;
		//String filename ="TextStl.stl";
		FileUtil.write(Paths.get("1-TextStl.stl"),
				badExport.toStlString());
		
		System.out.println("Load saved stl");
		File file = new File("1-TextStl.stl");
		CSG loaded = STL.file(file.toPath());
		for(Polygon p:loaded.getPolygons()) {
			if(p.getPoints().size()!=3) {
				fail("An STL is composed of triangles only, this must be impossible");
			}
		}
		try {
			loaded.triangulate(true);
		}catch(Exception ex) {
			ex.printStackTrace();
			fail("Manifold courupted data");
		}
		FileUtil.write(Paths.get("2-TextLoadedStl.stl"),
				loaded.toStlString());
		System.out.println("Perform scale");
		badExport=loaded;
		FileUtil.write(Paths.get("3-TextScaledStl.stl"),
				badExport.toStlString());
		System.out.println("Perform difference");
		CSG movey = new Cube(180,40,10).toCSG().toZMin().toXMin().toYMin().movey(-5).movez(-2);
		FileUtil.write(Paths.get("4-InMemTextDifferencedStl.stl"),
				movey.difference(inMem).toStlString());
		CSG difference = movey.difference(badExport);
		FileUtil.write(Paths.get("5-TextDifferencedStl.stl"),
				difference.toStlString());
		System.out.println("Perform Rotate");
		badExport=difference.rotx(35).roty(45);
		FileUtil.write(Paths.get("6-TextDiffRotatedStl.stl"),
				badExport.toStlString());
		double done =  System.currentTimeMillis()-start;
		System.out.println("Finished, took "+(done/1000.0)+" seconds ");
	}

}

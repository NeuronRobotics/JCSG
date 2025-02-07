package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Test;

public class StlExportTest {

	@Test
	public void makeBadSTL() throws IOException {
		Plane.setEPSILON(1.0e-12);
		Vector3d.setEXPORTEPSILON(1.0e-9);
		CSG.setUseGPU(false);
		CSG.setPreventNonManifoldTriangles(true);
		CSG badExport2 = CSG.text(" A QUICK BROWN ", 10,30,"Serif Regular").movey(30);
		System.out.println("First text loaded");
		CSG badExport = CSG.text("THis is some ", 10);
		System.out.println("Second text loaded");
//		badExport2=new Cube(20).toCSG().movey(30);
//		badExport=new Cube(20).toCSG();
		
		badExport=badExport.union(badExport2);
		//String filename ="TextStl.stl";
		FileUtil.write(Paths.get("TextStl.stl"),
				badExport.toStlString());
		System.out.println("Load saved stl");
		File file = new File("TextStl.stl");
		CSG loaded = STL.file(file.toPath());
		System.out.println("Perform difference");
		badExport=loaded.scaleToMeasurmentX(160).scaleToMeasurmentY(30);
		badExport=new Cube(180,40,10).toCSG().toZMin().toXMin().toYMin().movey(-5).difference(badExport).rotx(35).roty(45);
		FileUtil.write(Paths.get("TextStl2.stl"),
				badExport.toStlString());
	}

}

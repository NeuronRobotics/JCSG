package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import eu.mihosoft.vrl.v3d.svg.SVGLoad;
import eu.mihosoft.vrl.v3d.thumbnail.ThumbnailImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.CullFace;

public class SVGLoadTest {
	@Before
	public void setup() {
		CSG.setPreventNonManifoldTriangles(true);
	}
	//Alexes_Bad.svg
	@Test
	public void Alexes_Bad() throws IOException {
		
		JavaFXInitializer.go();
		File svg = new File("Alexes_Bad.svg");
		if (!svg.exists())
			throw new RuntimeException("Test file missing!" + svg.getAbsolutePath());
		SVGLoad s = new SVGLoad(svg.toURI());
		ArrayList<CSG>p =run(s);
		ArrayList<CSG> parts = new ArrayList<CSG>();
		parts.addAll(p);
		
		for (int i = 0; i < p.size(); i++) {
			CSG c = p.get(i);
			System.out.println("Perform difference "+i+" of "+p.size());
			parts.add(c.rotx(180).rotz(5).toZMin().difference(c).movez(30).setColor(Color.YELLOW));
		}
		System.out.println("Difference complete");
		if(parts.size()==0)
			throw new RuntimeException("Failed to load");
		try {
			ThumbnailImage.setCullFaceValue(CullFace.NONE);
			ThumbnailImage.writeImage(parts,new File(svg.getAbsolutePath()+".png")).join();
		} catch (InterruptedException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
		for(int i=0;i<parts.size();i++)
			FileUtil.write(Paths.get(i+"-alex.stl"),
					parts.get(i).toStlString());
	}
	@Test
	public void box() throws IOException {
		JavaFXInitializer.go();
		File svg = new File("box.svg");
		if (!svg.exists())
			throw new RuntimeException("Test file missing!" + svg.getAbsolutePath());
		SVGLoad s = new SVGLoad(svg.toURI());
		ArrayList<CSG>parts =run(s);
		try {
			ThumbnailImage.setCullFaceValue(CullFace.NONE);
			ThumbnailImage.writeImage(parts,new File(svg.getAbsolutePath()+".png")).join();
		} catch (InterruptedException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
		for(int i=0;i<parts.size();i++)
			FileUtil.write(Paths.get(i+"-box.stl"),
					parts.get(i).toStlString());
	}
	@Test
	public void inside() throws IOException {
		JavaFXInitializer.go();
		File svg = new File("InsideOutsideTest.svg");
		if (!svg.exists())
			throw new RuntimeException("Test file missing!" + svg.getAbsolutePath());
		SVGLoad s = new SVGLoad(svg.toURI());
		ArrayList<CSG>parts =new ArrayList<>(Arrays.asList(CSG.unionAll(run(s))));
		try {
			ThumbnailImage.setCullFaceValue(CullFace.NONE);
			ThumbnailImage.writeImage(parts,new File(svg.getAbsolutePath()+".png")).join();
		} catch (InterruptedException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
		for(int i=0;i<parts.size();i++)
			FileUtil.write(Paths.get(i+"-InsideOutsideTest.stl"),
					parts.get(i).toStlString());
	}
	@Test
	public void adversarial() throws IOException {
		JavaFXInitializer.go();
		File svg = new File("Part-Num-0.svg");
		if (!svg.exists())
			throw new RuntimeException("Test file missing!" + svg.getAbsolutePath());
		SVGLoad s = new SVGLoad(svg.toURI());
		ArrayList<CSG>parts =run(s);
		try {
			ThumbnailImage.setCullFaceValue(CullFace.NONE);
			ThumbnailImage.writeImage(parts,new File(svg.getAbsolutePath()+".png")).join();
		} catch (InterruptedException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
		
		// fail("Not yet implemented");
	}

	@Test
	public void test() throws IOException {
		File svg = new File("Test.SVG");
		if (!svg.exists())
			throw new RuntimeException("Test file missing!" + svg.getAbsolutePath());
		SVGLoad s = new SVGLoad(svg.toURI());
		run(s);
		// fail("Not yet implemented");
	}

	private ArrayList<CSG> run(SVGLoad s) {

		ArrayList<Object> p = new ArrayList<>();
		HashMap<String, List<Polygon>> polygons = s.toPolygons();
		for (String key : polygons.keySet()) {
			for (Polygon P : polygons.get(key)) {
				p.add(P);
			}
		}
		ArrayList<CSG> polys = new ArrayList<CSG>();
		List<String> layers = s.getLayers();
		double depth = 5 + (layers.size() * 5);
		for (int i = 0; i < layers.size(); i++) {
			String layerName = layers.get(i);
			HashMap<String, ArrayList<CSG>> extrudeLayerToCSG = s.extrudeLayers(depth, layerName);
			// extrudeLayerToCSG.setColor(Color.web(SVGExporter.colorNames.get(i)));
			for(String key:extrudeLayerToCSG.keySet()) {
				//System.out.println("Adding layer: "+key);
				ArrayList<CSG> csgs = extrudeLayerToCSG.get(key);
				if(csgs.size()>0)
					polys.addAll(csgs);
//				for(CSG c:extrudeLayerToCSG.get(key)) {
//					polys.add(c);
//				}
			}
			depth -= 5;
		}

		return polys;
	}

}

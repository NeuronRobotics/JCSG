package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.svg.SVGLoad;
import eu.mihosoft.vrl.v3d.thumbnail.ThumbnailImage;
import javafx.scene.shape.CullFace;

public class SVGLoadTest {
	//Alexes_Bad.svg
	@Test
	public void Alexes_Bad() throws IOException {
		JavaFXInitializer.go();
//		File svg = new File("Alexes_Bad.svg");
//		if (!svg.exists())
//			throw new RuntimeException("Test file missing!" + svg.getAbsolutePath());
//		SVGLoad s = new SVGLoad(svg.toURI());
//		ArrayList<CSG>parts =run(s);
//		if(parts.size()==0)
//			throw new RuntimeException("Failed to load");
//		try {
//			ThumbnailImage.setCullFaceValue(CullFace.NONE);
//			ThumbnailImage.writeImage(parts,new File(svg.getAbsolutePath()+".png")).join();
//		} catch (InterruptedException e) {
//			// Auto-generated catch block
//			e.printStackTrace();
//		}
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
	}
	@Test
	public void inside() throws IOException {
		JavaFXInitializer.go();
		File svg = new File("InsideOutsideTest.svg");
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
			HashMap<String, ArrayList<CSG>> extrudeLayerToCSG = s.extrudeLayers(depth,0.1, layerName);
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

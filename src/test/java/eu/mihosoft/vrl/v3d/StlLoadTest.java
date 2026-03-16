package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.thumbnail.ThumbnailImageCSG;
import javafx.scene.shape.CullFace;

public class StlLoadTest {
	@Before
	public void init() {
		JavaFXInitializer.go();
	}
	@Test
	@Ignore
	public void tower() throws IOException {
		String filename = "fixedTower.STL";
		File file = new File(filename);
		System.out.println("Loading STL");
		CSG loaded = STL.file(file.toPath());
		System.out.println("differencing STL");

		CSG diff = loaded.difference(new Cube(250).toCSG());

		CSG.setPreventNonManifoldTriangles(false);
		System.out.println("exporting STL");

		FileUtil.write(Paths.get("fixedTower-export.stl"), diff.toStlString());
		try {
			ThumbnailImageCSG.setCullFaceValue(CullFace.NONE);
			new ThumbnailImageCSG().writeImage(CSGDatabase.getInstance(), loaded,
					new File(file.getAbsolutePath() + ".png"));
		} catch (Exception e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
		if (loaded.getPolygons().size() / 2 > diff.getPolygons().size()) {
			fail("Failed perform difference without losing information!");
		}
	}
	@Test
	public void test() throws IOException {
		String filename = "brokenSTL.STL";
		File file = new File(filename);
		CSG loaded = STL.file(file.toPath());
		try {
			ThumbnailImageCSG.setCullFaceValue(CullFace.NONE);
			new ThumbnailImageCSG().writeImage(CSGDatabase.getInstance(), loaded,
					new File(file.getAbsolutePath() + ".png"));
		} catch (Exception e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}

}

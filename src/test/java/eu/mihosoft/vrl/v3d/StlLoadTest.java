package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.thumbnail.ThumbnailImage;
import javafx.scene.shape.CullFace;
import javafx.application.Platform;

public class StlLoadTest {

	@Test
	public void calciferTest() throws IOException {
		JavaFXInitializer.go();
		CSG.setPreventNonManifoldTriangles(false);
		String filename = "calcifer.STL";
		File file = new File(filename);
		CSG loaded = STL.file(file.toPath()).toZMin();
		CSG box = loaded.getBoundingBox().scalez(2).toZMin();
		System.out.println("Performing Calcifer Difference");
		CSG result = box.difference(loaded);
		System.out.println("Writing Thumbnail");
		try {
			ThumbnailImage.setCullFaceValue(CullFace.NONE);
			ThumbnailImage.writeImage(result, new File(file.getAbsolutePath() + ".png")).join();
		} catch (InterruptedException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Exporting STL");
		FileUtil.write(Paths.get("1-calcifer.stl"), result.toStlString());

	}

	@Test
	public void test() throws IOException {
		JavaFXInitializer.go();
		String filename = "brokenSTL.STL";
		File file = new File(filename);
		CSG loaded = STL.file(file.toPath());
		try {
			ThumbnailImage.setCullFaceValue(CullFace.NONE);
			ThumbnailImage.writeImage(loaded, new File(file.getAbsolutePath() + ".png")).join();
		} catch (InterruptedException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}

}

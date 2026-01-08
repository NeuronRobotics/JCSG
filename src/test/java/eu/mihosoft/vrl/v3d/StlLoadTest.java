package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.thumbnail.ThumbnailImageCSG;
import javafx.scene.shape.CullFace;

public class StlLoadTest {

	@Test
	public void test() throws IOException {
		String filename = "brokenSTL.STL";
		File file = new File(filename);
		CSG loaded = STL.file(file.toPath());
		try {
			ThumbnailImageCSG.setCullFaceValue(CullFace.NONE);
			new ThumbnailImageCSG().writeImage(CSGDatabase.getInstance(),loaded,new File(file.getAbsolutePath()+".png"));
		} catch (Exception e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}

}

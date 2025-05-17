package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.parametrics.Parameter;
import eu.mihosoft.vrl.v3d.svg.SVGLoad;
import javafx.scene.text.Font;

public class textTest {

	@Test
	public void test() throws IOException {
		//TextExtrude.text(10.0, "Hello", new Font("Helvedica",  18));
		//TextExtrude.text(10.0, "Hello World!", new Font("Times New Roman", 18));
		
		CSG text = CSG.text("Hello world",10, 10,"Serif Regular");
		text=new Cube(180,40,10).toCSG().toZMin().toXMin().toYMin().movey(-5).difference(text);
		FileUtil.write(Paths.get("exampleText.stl"),
				text.toStlString());
		
	}

}

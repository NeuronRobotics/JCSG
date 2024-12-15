package eu.mihosoft.vrl.v3d;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class Text3dTest {

    @Test
    public void textShouldGenerateCSG() throws IOException {
        Text3d text = new Text3d("Hello world", 10);
        FileUtil.write(Paths.get("exampleText.stl"),
                text.toCSG().triangulate().toStlString());
    }

    @Test
    public void textTriangulateShouldCreateSixFaces() {
        CSG text = new Text3d("-", 1).toCSG().triangulate();
        assertEquals("We should have two triangles on each face", 12, text.getPolygons().size());
    }

    @Test
    public void textWithUnknownFontShouldUseDefault() {
        Text3d text = new Text3d("Hello world", "banana", 10, 10);
        assertFalse(text.toPolygons().isEmpty());
    }

}
package eu.mihosoft.vrl.v3d;

import javafx.scene.paint.Color;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class CSGTest {

    public static final String BLUE_COLOR_AS_STRING = Color.BLUE.getRed() + " " + Color.BLUE.getGreen() + " " + Color.BLUE.getBlue();
    public static final String RED_COLOR_AS_STRING = Color.RED.getRed() + " " + Color.RED.getGreen() + " " + Color.RED.getBlue();

    private static String getColorAsString(Polygon polygon) {
        return polygon.getStorage().getValue(PropertyStorage.PROPERTY_MATERIAL_COLOR)
                .map(Object::toString)
                .orElseThrow(() -> new RuntimeException("Missing property " + PropertyStorage.PROPERTY_MATERIAL_COLOR));
    }

    @Test
    public void setColor_ShouldSetColorToAllPolygons() {
        CSG cube = new Cube()
                .toCSG()
                .setColor(Color.BLUE);
        assertEquals(Color.BLUE, cube.getColor());

        cube.getPolygons().forEach(polygon -> {
            String polygonColorAsString = getColorAsString(polygon);
            assertEquals("Expected the polygon to get the same color as the CSG", BLUE_COLOR_AS_STRING, polygonColorAsString);
        });
    }

    @Test
    public void setColor_OnUnionCSGShouldRetainColorsOnPolygons() {
        CSG cube1 = new Cube(10).toCSG()
                .setColor(Color.BLUE);

        CSG cube2 = new Cube(10).toCSG()
                .setColor(Color.RED)
                .transformed(new Transform().translate(10, 0, 0));

        CSG union = cube1.union(cube2);
        assertEquals(Color.RED, union.getColor());

        union.getPolygons().forEach(polygon -> {
            String polygonColorAsString = getColorAsString(polygon);
            boolean isLeftCube = polygon.getPoints().stream().allMatch(p -> p.x <= 5);
            if (isLeftCube) {
                assertEquals("Expected the left cube polygons to be blue", BLUE_COLOR_AS_STRING, polygonColorAsString);
            } else {
                assertEquals("Expected the right cube polygons to be red", RED_COLOR_AS_STRING, polygonColorAsString);
            }
        });
    }

    @Test
    public void setColor_OnCSGShouldChangeColorsOfAllPolygons() {
        CSG cube = new Cube(10).toCSG()
                .setColor(Color.BLUE);
        assertEquals(Color.BLUE, cube.getColor());

        cube.setColor(Color.RED);

        cube.getPolygons().forEach(polygon -> {
            String polygonColorAsString = getColorAsString(polygon);
            assertEquals("Expected the cube polygons to be another color", RED_COLOR_AS_STRING, polygonColorAsString);
        });
    }

    @Test
    public void setColor_OnUnionedCSGShouldChangeColorsOfAllPolygons() {
        CSG cube1 = new Cube(10).toCSG()
                .setColor(Color.BLUE);

        CSG cube2 = new Cube(10).toCSG()
                .setColor(Color.RED)
                .transformed(new Transform().translate(10, 0, 0));

        CSG union = cube1.union(cube2);
        assertEquals("Expected the new object to inherit the color from the latest unioned object", Color.RED, union.getColor());

        union.setColor(Color.BLUE);
        union.getPolygons().forEach(polygon -> {
            String polygonColorAsString = getColorAsString(polygon);
            assertEquals("Expected the cube polygons to be another color", BLUE_COLOR_AS_STRING, polygonColorAsString);
        });
    }
}

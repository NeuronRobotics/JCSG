package eu.mihosoft.vrl.v3d;

import javafx.scene.paint.Color;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;

public class CSGTest {

    @Test
    public void setColor_ShouldSetColorToAllPolygons() {
        CSG cube = new Cube()
                .toCSG()
                .setColor(Color.BLUE);
        assertEquals(Color.BLUE, cube.getColor());

        cube.getPolygons().forEach(polygon -> {
            assertEquals("Expected the polygon to get the same color as the CSG", Color.BLUE, polygon.getColor());
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
        assertEquals("Expected the new object to get the color from the unioned object", Color.RED, union.getColor());

        union.getPolygons().forEach(polygon -> {
            boolean isLeftCube = polygon.getPoints().stream().allMatch(p -> p.x <= 5);
            if (isLeftCube) {
                assertEquals("Expected the left cube polygons to be blue", Color.BLUE, polygon.getColor());
            } else {
                assertEquals("Expected the right cube polygons to be red", Color.RED, polygon.getColor());
            }
        });
    }

    @Test
    public void setColor_OnUnionedAndTriangulatedCSGShouldRetainColorsOnPolygons() {
        CSG cube1 = new Cube(10).toCSG()
                .setColor(Color.BLUE);

        CSG cube2 = new Cube(10).toCSG()
                .setColor(Color.RED)
                .transformed(new Transform().translate(10, 0, 0));

        CSG union = cube1.union(cube2).triangulate();
        assertEquals("Expected the new object to get the color from the unioned object", Color.RED, union.getColor());

        union.getPolygons().forEach(polygon -> {
            boolean isLeftCube = polygon.getPoints().stream().allMatch(p -> p.x <= 5);
            if (isLeftCube) {
                assertEquals("Expected the left cube polygons to be blue", Color.BLUE, polygon.getColor());
            } else {
                assertEquals("Expected the right cube polygons to be red", Color.RED, polygon.getColor());
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
            assertEquals("Expected the cube polygons to be another color", Color.RED, polygon.getColor());
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
        assertEquals("Expected the new object to get the color from the unioned object", Color.RED, union.getColor());

        union.setColor(Color.BLUE);
        union.getPolygons().forEach(polygon -> {
            assertEquals("Expected the cube polygons to be another color", Color.BLUE, polygon.getColor());
        });
    }

    @Test
    public void setColor_OnDifferenceWithOptTypeBoundsShouldChangeColorsOfIntersectingPolygons() {
        CSG cube1 = new Cube(10).toCSG()
                .move(0, 0, 0)
                .setColor(Color.BLUE);

        CSG cube2 = new Cube(10).toCSG()
                .move(9, 0, 0)
                .setColor(Color.RED);

        assertEquals(CSG.OptType.CSG_BOUND, cube1.getOptType());

        CSG difference = cube1.difference(cube2);
        assertEquals("Unexpected number of faces", 6, difference.getPolygons().size());
        difference.getPolygons().forEach(polygon -> {
            Vector3d center = polygon.getBounds().getCenter();
            if (center.equals(Vector3d.xyz(-5, 0, 0))) {
                assertEquals("Expected the left face to have another color", Color.BLUE, polygon.getColor());
            } else if (center.equals(Vector3d.xyz(-0.5, -5, 0))) {
                assertEquals("Expected the front face to have another color", Color.BLUE, polygon.getColor());
            } else if (center.equals(Vector3d.xyz(-0.5, 5, 0))) {
                assertEquals("Expected the back face to have another color", Color.BLUE, polygon.getColor());
            } else if (center.equals(Vector3d.xyz(-0.5, 0, -5))) {
                assertEquals("Expected the bottom face to have another color", Color.BLUE, polygon.getColor());
            } else if (center.equals(Vector3d.xyz(-0.5, 0, 5))) {
                assertEquals("Expected the top face to have another color", Color.BLUE, polygon.getColor());
            } else if (center.equals(Vector3d.xyz(4, 0, 0))) {
                assertEquals("Expected the right face to have another color", Color.RED, polygon.getColor());
            } else {
                fail("Unknown face with center" + center);
            }
        });
    }

    @Test
    public void setColor_OnDifferenceWithOptTypePolygonShouldChangeColorsOfIntersectingPolygons() {
        CSG cube1 = new Cube(10).toCSG()
                .move(0, 0, 0)
                .setColor(Color.BLUE)
                .setOptType(CSG.OptType.POLYGON_BOUND);

        CSG cube2 = new Cube(10).toCSG()
                .move(9, 0, 0)
                .setColor(Color.RED);

        assertEquals(CSG.OptType.POLYGON_BOUND, cube1.getOptType());

        CSG difference = cube1.difference(cube2);
        assertEquals("Unexpected number of faces", 6, difference.getPolygons().size());
        difference.getPolygons().forEach(polygon -> {
            Vector3d center = polygon.getBounds().getCenter();
            if (center.equals(Vector3d.xyz(-5, 0, 0))) {
                assertEquals("Expected the left face to have another color", Color.BLUE, polygon.getColor());
            } else if (center.equals(Vector3d.xyz(-0.5, -5, 0))) {
                assertEquals("Expected the front face to have another color", Color.BLUE, polygon.getColor());
            } else if (center.equals(Vector3d.xyz(-0.5, 5, 0))) {
                assertEquals("Expected the back face to have another color", Color.BLUE, polygon.getColor());
            } else if (center.equals(Vector3d.xyz(-0.5, 0, -5))) {
                assertEquals("Expected the bottom face to have another color", Color.BLUE, polygon.getColor());
            } else if (center.equals(Vector3d.xyz(-0.5, 0, 5))) {
                assertEquals("Expected the top face to have another color", Color.BLUE, polygon.getColor());
            } else if (center.equals(Vector3d.xyz(4, 0, 0))) {
                assertEquals("Expected the right face to have another color", Color.RED, polygon.getColor());
            } else {
                fail("Unknown face with center" + center);
            }
        });
    }
}

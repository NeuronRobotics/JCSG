package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import eu.mihosoft.vrl.v3d.Vector3d;
import java.util.Arrays;
import java.util.List;
public class PolygonClassificationTest {

private PropertyStorage storage;
    
    @Before
    public void setUp() {
        storage = new PropertyStorage();
    }
    
    @Test
    public void testPolygon1_ValidThinTriangle() {
        // [[187.5318756104, 115.8620758057, 55.0120964050], 
        //  [187.5318756124, 115.8620758065, 55.0120975630], 
        //  [187.5318756204, 115.8620758109, 55.0120975630]]
        // Valid triangle - spans 0.0001 in X and Z
        
        List<Vertex> vertices = Arrays.asList(
            new Vertex(new Vector3d(187.5318756104, 115.8620758057, 55.0120964050)),
            new Vertex(new Vector3d(187.5318756124, 115.8620758065, 55.0120975630)),
            new Vertex(new Vector3d(187.5318756204, 115.8620758109, 55.0120975630))
        );
        
        try {
            Polygon polygon = new Polygon(vertices, storage, true, null);
            assertNotNull("Polygon should be created successfully", polygon);
            assertEquals("Polygon should have 3 vertices", 3, polygon.getVertices().size());
        } catch (Exception e) {
        	e.printStackTrace();
            fail("Should not throw exception for valid thin triangle: " + e.getMessage());
        }
    }
    
 
    
    @Test
    public void testPolygon3_ValidWithSmallYSpan() {
        // [[10.5246505740, 94.3986129767, 35.0306323972], 
        //  [10.5246505737, 94.3986129761, 35.0306320190], 
        //  [10.5246505768, 94.3986129710, 35.0306323972]]
        // Valid - 0.00006 Y span
        
        List<Vertex> vertices = Arrays.asList(
            new Vertex(new Vector3d(10.5246505740, 94.3986129767, 35.0306323972)),
            new Vertex(new Vector3d(10.5246505737, 94.3986129761, 35.0306320190)),
            new Vertex(new Vector3d(10.5246505768, 94.3986129710, 35.0306323972))
        );
        
        try {
            Polygon polygon = new Polygon(vertices, storage, true, null);
            assertNotNull("Polygon should be created successfully", polygon);
            assertEquals("Polygon should have 3 vertices", 3, polygon.getVertices().size());
        } catch (Exception e) {
        	e.printStackTrace();
            fail("Should not throw exception for valid triangle with small Y span: " + e.getMessage());
        }
    }
    
    @Test
    public void testPolygon4_ValidWithXSpan() {
        // [[11.3266067492, 93.7161712649, 35.0305217579], 
        //  [11.3266067505, 93.7161712646, 35.0305213928], 
        //  [11.3266067568, 93.7161712643, 35.0305217579]]
        // Valid - 0.00008 X span
        
        List<Vertex> vertices = Arrays.asList(
            new Vertex(new Vector3d(11.3266067492, 93.7161712649, 35.0305217579)),
            new Vertex(new Vector3d(11.3266067505, 93.7161712646, 35.0305213928)),
            new Vertex(new Vector3d(11.3266067568, 93.7161712643, 35.0305217579))
        );
        
        try {
            Polygon polygon = new Polygon(vertices, storage, true, null);
            assertNotNull("Polygon should be created successfully", polygon);
            assertEquals("Polygon should have 3 vertices", 3, polygon.getVertices().size());
        } catch (Exception e) {
        	e.printStackTrace();
            fail("Should not throw exception for valid triangle with X span: " + e.getMessage());
        }
    }
    
    @Test
    public void testPolygon5_ValidWithYAndZVariation() {
        // [[10.8600692723, 93.9305191077, 55.0305947022],
        //  [10.8600692758, 93.9305191035, 55.0305947022],
        //  [10.8600692749, 93.9305191040, 55.0305938721]]
        // Valid - Y varies by 0.00004, Z by 0.000008
        
        List<Vertex> vertices = Arrays.asList(
            new Vertex(new Vector3d(10.8600692723, 93.9305191077, 55.0305947022)),
            new Vertex(new Vector3d(10.8600692758, 93.9305191035, 55.0305947022)),
            new Vertex(new Vector3d(10.8600692749, 93.9305191040, 55.0305938721))
        );
        
        try {
            Polygon polygon = new Polygon(vertices, storage, true, null);
            assertNotNull("Polygon should be created successfully", polygon);
            assertEquals("Polygon should have 3 vertices", 3, polygon.getVertices().size());
        } catch (Exception e) {
            fail("Should not throw exception for valid triangle with Y and Z variation: " + e.getMessage());
        }
    }
    
    @Test
    public void testPolygon6_ValidWithLargeYZDifferences() {
        // [[209.5015258789, 101.1099853516, 128.0972442627], 
        //  [209.5015327416, 101.1190847868, 128.1390783904]]
        // Valid - large Y/Z differences (0.009/0.042)
        // Note: This appears to be only 2 points in the log, may need a third
        
        List<Vertex> vertices = Arrays.asList(
            new Vertex(new Vector3d(209.5015258789, 101.1099853516, 128.0972442627)),
            new Vertex(new Vector3d(209.5015327416, 101.1190847868, 128.1390783904)),
            new Vertex(new Vector3d(209.5015327416, 101.1190847961, 128.1390783894))
        );
        
        try {
            Polygon polygon = new Polygon(vertices, storage, true, null);
            assertNotNull("Polygon should be created successfully", polygon);
            assertEquals("Polygon should have 3 vertices", 3, polygon.getVertices().size());
        } catch (Exception e) {
            fail("Should not throw exception for valid triangle with large Y/Z differences: " + e.getMessage());
        }
    }
    

    
    /**
	 * Test to verify that truly degenerate polygons ARE correctly rejected
	 * 
	 * @throws ColinearPointsException
	 */
    @Test(expected = ColinearPointsException.class)
    public void testDegeneratePolygon_DuplicatePoints() throws ColinearPointsException {
        // This SHOULD fail - duplicate points
        List<Vertex> vertices = Arrays.asList(
            new Vertex(new Vector3d(10.5175167546, 116.6176943528, 83.1832321598)),
            new Vertex(new Vector3d(10.5175170898, 116.6182632446, 83.1833572388)),
            new Vertex(new Vector3d(10.5175167546, 116.6176943528, 83.1832321598))
        );
        
		Polygon polygon = new Polygon(vertices, storage, true, null);

        fail("Should have thrown exception for duplicate points");
    }
    
    /**
     * Test to verify that collinear points ARE correctly rejected
     * @throws ColinearPointsException 
     */
    @Test(expected = ColinearPointsException.class)
    public void testDegeneratePolygon_Collinear() throws ColinearPointsException {
        // This SHOULD fail - all points on same line (same Y coordinate, collinear in XZ)
        List<Vertex> vertices = Arrays.asList(
            new Vertex(new Vector3d(10.5068817145, 98.4507751465, 35.0306403108)),
            new Vertex(new Vector3d(10.5068817139, 98.4507751465, 35.0306358337)),
            new Vertex(new Vector3d(10.5068817145, 98.4507751465, 35.0306403108))
        );
        
		Polygon polygon = new Polygon(vertices, storage, true, null);

        fail("Should have thrown exception for collinear points");
    }


}

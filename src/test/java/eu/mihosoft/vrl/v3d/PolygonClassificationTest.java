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

	/**
	 * Test to verify that truly degenerate polygons ARE correctly rejected
	 * 
	 * @throws ColinearPointsException
	 * @throws NonFlatPolygonException
	 */
	public void testPolygon() throws ColinearPointsException, NonFlatPolygonException {

		List<Vertex> vertices = Arrays.asList(new Vertex(new Vector3d(38.3418497284, 8.2088108210, 30.3300000000)),
				new Vertex(new Vector3d(36.2203551685, 13.8006760491, 30.3300000000)),
				new Vertex(new Vector3d(38.3418492406, 8.2088109180, 30.3300000000)));
		try {
			Polygon polygon = Polygon.fromVertex(vertices, storage, true, null).get(0);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Should have thrown exception for duplicate points");

		}
	}

	/**
	 * Test to verify that truly degenerate polygons ARE correctly rejected
	 * 
	 * @throws ColinearPointsException
	 * @throws NonFlatPolygonException
	 */
	@Test(expected = ColinearPointsException.class)
	public void testDegeneratePolygon_DuplicatePoints() throws ColinearPointsException, NonFlatPolygonException {
		// This SHOULD fail - duplicate points
		List<Vertex> vertices = Arrays.asList(new Vertex(new Vector3d(10.5175167546, 116.6176943528, 83.1832321598)),
				new Vertex(new Vector3d(10.5175170898, 116.6182632446, 83.1833572388)),
				new Vertex(new Vector3d(10.5175167546, 116.6176943528, 83.1832321598)));

		Polygon polygon = Polygon.fromVertex(vertices, storage, true, null).get(0);

		fail("Should have thrown exception for duplicate points");
	}



}

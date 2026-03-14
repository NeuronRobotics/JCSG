package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

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
	 */
	@Test(expected = ColinearPointsException.class)
	public void testDegeneratePolygon_DuplicatePoints() throws ColinearPointsException {
		// This SHOULD fail - duplicate points
		List<Vertex> vertices = Arrays.asList(new Vertex(new Vector3d(10.5175167546, 116.6176943528, 83.1832321598)),
				new Vertex(new Vector3d(10.5175170898, 116.6182632446, 83.1833572388)),
				new Vertex(new Vector3d(10.5175167546, 116.6176943528, 83.1832321598)));

		Polygon polygon = new Polygon(vertices, storage, true, null);

		fail("Should have thrown exception for duplicate points");
	}

	/**
	 * Test to verify that collinear points ARE correctly rejected
	 *
	 * @throws ColinearPointsException
	 */
	@Test(expected = ColinearPointsException.class)
	public void testDegeneratePolygon_Collinear() throws ColinearPointsException {
		// This SHOULD fail - all points on same line (same Y coordinate, collinear in
		// XZ)
		List<Vertex> vertices = Arrays.asList(new Vertex(new Vector3d(10.5068817145, 98.4507751465, 35.0306403108)),
				new Vertex(new Vector3d(10.5068817139, 98.4507751465, 35.0306358337)),
				new Vertex(new Vector3d(10.5068817145, 98.4507751465, 35.0306403108)));

		Polygon polygon = new Polygon(vertices, storage, true, null);

		fail("Should have thrown exception for collinear points");
	}

}

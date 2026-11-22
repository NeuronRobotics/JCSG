package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG.OptType;

/**
 * Bounds resolution per optimization mode. Manifold mode must measure the box
 * on the native side and agree with the vertex scan that every other mode uses,
 * including after a transform that invalidates a cached box.
 */
public class CSGBoundsTest {

	private OptType original;

	@Before
	public void rememberOptType() {
		original = CSG.getDefaultOptionType();
	}

	@After
	public void restoreOptType() {
		CSG.setDefaultOptType(original);
	}

	private static void assertSameBox(Bounds expected, Bounds actual) {
		assertNotNull(actual);
		assertEquals(expected.getMin().x, actual.getMin().x, 1e-6);
		assertEquals(expected.getMin().y, actual.getMin().y, 1e-6);
		assertEquals(expected.getMin().z, actual.getMin().z, 1e-6);
		assertEquals(expected.getMax().x, actual.getMax().x, 1e-6);
		assertEquals(expected.getMax().y, actual.getMax().y, 1e-6);
		assertEquals(expected.getMax().z, actual.getMax().z, 1e-6);
	}

	@Test
	public void manifoldModeMatchesTheScannedBox() {
		CSG solid = new Cube(10, 20, 30).toCSG().transformed(new Transform().translate(5, -6, 7));

		CSG.setDefaultOptType(OptType.CSG_BOUND);
		Bounds scanned = solid.getBounds();

		CSG.setDefaultOptType(OptType.Manifold3d);
		assertSameBox(scanned, solid.getBounds());
	}

	@Test
	public void manifoldModeMatchesAfterTransformInvalidatesCache() {
		CSG solid = new Cube(10, 10, 10).toCSG();
		solid.getBounds(); // populate the cache that the transform has to drop
		CSG moved = solid.transformed(new Transform().translate(100, 0, 0));

		CSG.setDefaultOptType(OptType.CSG_BOUND);
		Bounds scanned = moved.getBounds();

		CSG.setDefaultOptType(OptType.Manifold3d);
		assertSameBox(scanned, moved.getBounds());
		// Cube(size).toCSG() is centred, so +100 puts the near face at 95
		assertEquals(95, moved.getBounds().getMin().x, 1e-6);
	}
}

package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.manifold3d.CSGManifold3d;
import eu.mihosoft.vrl.v3d.CSG.OptType;

/**
 * Bounds resolution per optimization mode. Manifold mode must measure the box
 * on the native side and agree with the vertex scan that every other mode uses,
 * including after a transform that invalidates a cached box.
 *
 * <p>
 * Off the JavaFX thread the native measurement must be used; on the JavaFX
 * thread it must not be, because mouse handlers reach {@code getBounds()} and a
 * native round-trip there stalls input.
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
	public void fxThreadTakesTheScanInsteadOfTheNativeBounds() throws Throwable {
		// A native measurement here runs the whole mesh through the binding, and
		// mouse handlers reach getBounds(), so the FX thread must stay on the scan.
		// The thread probe is stubbed rather than spinning up a real toolkit, which
		// needs a display.
		CSGManifold3d real = new CSGManifold3d();
		long[] nativeCalls = new long[1];
		CSGManifold3d spy = new CSGManifold3d() {
			@Override
			public Bounds getBounds(CSG incoming) throws Throwable {
				nativeCalls[0]++;
				return real.getBounds(incoming);
			}
		};

		CSG.setDefaultOptType(OptType.Manifold3d);
		CSG.setManifold(spy);
		boolean originalProbeResult = false;
		try {
			// off the FX thread: the native measurement is used
			CSG.fxThreadProbe = () -> false;
			Bounds offFx = new Cube(10, 20, 30).toCSG().getBounds();
			assertEquals("native bounds must be used off the FX thread", 1L, nativeCalls[0]);

			// on the FX thread: it must be skipped entirely
			CSG.fxThreadProbe = () -> true;
			CSG fresh = new Cube(10, 20, 30).toCSG();
			Bounds onFx = fresh.getBounds();
			assertEquals("the FX thread must not touch the native binding", 1L, nativeCalls[0]);

			assertEquals(offFx.getMin().x, onFx.getMin().x, 1e-6);
			assertEquals(offFx.getMax().z, onFx.getMax().z, 1e-6);
			assertEquals(-10, onFx.getMin().y, 1e-6);
		} finally {
			CSG.fxThreadProbe = () -> javafx.application.Platform.isFxApplicationThread();
			CSG.setManifold(real);
		}
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

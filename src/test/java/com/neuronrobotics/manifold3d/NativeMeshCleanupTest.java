package com.neuronrobotics.manifold3d;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;
import javafx.scene.paint.Color;

public class NativeMeshCleanupTest {
	private static CSGManifold3d bridge;
	private static CSGManifold3d previous;

	@BeforeClass
	public static void initializeNativeBackend() throws Exception {
		previous = CSG.getManifold();
		bridge = new CSGManifold3d();
		CSG.setManifold(bridge);
	}

	@AfterClass
	public static void restoreNativeBackend() {
		CSG.setManifold(previous);
		if (bridge != null)
			bridge.close();
	}

	private CSG cube(double size) throws Throwable {
		return bridge.cube(size, size, size, true);
	}

	private CSG booleanOp(CSG a, CSG b, CSGManifold3d.BooleanOp op) throws Throwable {
		return bridge.booleanOp(a, b, op);
	}

	private double volume(CSG solid) throws Throwable {
		return solid.getTriCount() == 0 ? 0 : bridge.volume(solid);
	}

	private void assertSameGeometry(CSG a, CSG b, double tolerance) throws Throwable {
		assertEquals(0, volume(booleanOp(a, b, CSGManifold3d.BooleanOp.DIFFERENCE)), tolerance);
		assertEquals(0, volume(booleanOp(b, a, CSGManifold3d.BooleanOp.DIFFERENCE)), tolerance);
	}

	@Test
	public void cubeKeepsSharpBoundsVolumeAndMetadataWithoutChangingInput() throws Throwable {
		CSG input = cube(10).setName("precision cube").setColor(Color.CORNFLOWERBLUE);
		double[] vertices = input.getVertices().clone();
		long[] triangles = input.getTriangles().clone();
		CSG result = input.simplify();
		assertNotSame(input, result);
		assertEquals(1000, volume(result), 1e-9);
		for (int axis = 0; axis < 3; axis++) {
			double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
			for (int i = axis; i < result.getVertices().length; i += 3) {
				min = Math.min(min, result.getVertices()[i]);
				max = Math.max(max, result.getVertices()[i]);
			}
			assertEquals(-5, min, 1e-12);
			assertEquals(5, max, 1e-12);
		}
		assertSameGeometry(input, result, 1e-9);
		assertEquals(input.getName(), result.getName());
		assertEquals(input.getColor(), result.getColor());
		assertTrue(Arrays.equals(vertices, input.getVertices()));
		assertTrue(Arrays.equals(triangles, input.getTriangles()));
		assertSameGeometry(result, CSG.simplify(input, 1e-7), 1e-9);
	}

	@Test
	public void hollowShapeKeepsItsCavity() throws Throwable {
		CSG inner = cube(8);
		CSG hollow = booleanOp(cube(10), inner, CSGManifold3d.BooleanOp.DIFFERENCE);
		CSG result = hollow.simplify();
		assertEquals(488, volume(result), 1e-9);
		assertEquals(0, volume(booleanOp(result, inner, CSGManifold3d.BooleanOp.INTERSECTION)), 1e-9);
		assertSameGeometry(hollow, result, 1e-9);
	}

	@Test
	public void thinDisconnectedMembraneIsRemovedButSmallerVolumeCubeSurvives() throws Throwable {
		assertThinMembraneRemoved(1e-3, 0.01);
	}

	@Test
	public void defaultPrecisionRemovesThinMembrane() throws Throwable {
		assertThinMembraneRemoved(NativeMeshCleanup.DEFAULT_TOLERANCE, 0.001);
	}

	@Test
	public void resolvedThinWallIsPreserved() throws Throwable {
		CSG wall = bridge.cube(10, 10, 1e-5, true);
		CSG result = wall.simplify();
		assertEquals(0.001, volume(result), 1e-12);
		assertSameGeometry(wall, result, 1e-12);
	}

	@Test
	public void subResolutionMembranesAreRemovedInEveryPlanarOrientation() throws Throwable {
		CSG body = cube(10);
		CSG small = bridge.translate(cube(0.001), -20, 0, 0);
		CSG expected = booleanOp(body, small, CSGManifold3d.BooleanOp.UNION);
		for (int axis = 0; axis < 3; axis++) {
			double[] dimensions = new double[]{2, 2, 2};
			dimensions[axis] = NativeMeshCleanup.DEFAULT_TOLERANCE / 10;
			CSG membrane = bridge.translate(bridge.cube(dimensions[0], dimensions[1], dimensions[2], true), 20, 0, 0);
			CSG input = booleanOp(expected, membrane, CSGManifold3d.BooleanOp.UNION);
			CSG result = bridge.simplify(input, NativeMeshCleanup.DEFAULT_TOLERANCE);
			assertSameGeometry(expected, result, 1e-12);
			assertEquals(volume(small), volume(booleanOp(result, small, CSGManifold3d.BooleanOp.INTERSECTION)), 1e-15);
		}
	}

	@Test
	public void attachedSubResolutionFinIsRemovedWithoutMovingTheBody() throws Throwable {
		CSG body = cube(10);
		CSG fin = bridge.cube(2, 1e-8, 5, true).transformed(new Transform().translateZ(7.5));
		CSG input = booleanOp(body, fin, CSGManifold3d.BooleanOp.UNION);
		assertEquals(volume(fin), volume(booleanOp(input, fin, CSGManifold3d.BooleanOp.INTERSECTION)), 1e-12);
		assertSameGeometry(body, input.simplify(), 1e-12);
	}

	private void assertThinMembraneRemoved(double tolerance, double smallSize) throws Throwable {
		CSG body = cube(10);
		CSG membrane = bridge.cube(2, 2, tolerance / 10, true).transformed(new Transform().translateX(20));
		CSG small = cube(smallSize).transformed(new Transform().translateX(-20));
		CSG expected = booleanOp(body, small, CSGManifold3d.BooleanOp.UNION);
		CSG input = booleanOp(expected, membrane, CSGManifold3d.BooleanOp.UNION);
		assertEquals(volume(membrane), volume(booleanOp(input, membrane, CSGManifold3d.BooleanOp.INTERSECTION)),
				volume(membrane) * 1e-6);
		CSG result = tolerance == NativeMeshCleanup.DEFAULT_TOLERANCE ? input.simplify() : input.simplify(tolerance);
		assertEquals(volume(small), volume(booleanOp(result, small, CSGManifold3d.BooleanOp.INTERSECTION)),
				volume(small) * 1e-6);
		assertSameGeometry(expected, result, 1e-12);
	}

	@Test
	public void cleanupInitializesNativeBackendWithoutChangingBooleanMode() throws Throwable {
		CSG.OptType mode = CSG.getDefaultOptionType();
		CSG.setManifold(null);
		CSGManifold3d initialized;
		try {
			CSG input = cube(1);
			assertSameGeometry(input, input.simplify(), 1e-12);
			assertEquals(mode, CSG.getDefaultOptionType());
		} finally {
			initialized = CSG.getManifold();
			CSG.setManifold(bridge);
			if (initialized != null)
				initialized.close();
		}
	}

	@Test
	public void emptyIsAnIndependentEmptyWithMetadata() throws Throwable {
		CSG input = new CSG().setName("empty").setColor(Color.RED);
		CSG result = input.simplify();
		assertNotSame(input, result);
		assertEquals(0L, result.getTriCount());
		assertEquals(input.getName(), result.getName());
		assertEquals(input.getColor(), result.getColor());
	}

	@Test
	public void invalidTolerancesAreRejectedEvenForEmptyGeometry() throws Throwable {
		for (double tolerance : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
			for (CSG input : new CSG[]{new CSG(), cube(1)}) {
				try {
					input.simplify(tolerance);
					fail("invalid tolerance accepted");
				} catch (IllegalArgumentException expected) {
					// Expected validation before native import;
				}
			}
		}
		assertSameGeometry(cube(1), cube(1).simplify(0), 1e-12);
	}
}

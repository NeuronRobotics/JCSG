package com.neuronrobotics.manifold3d;

import static org.junit.Assert.*;

import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.paint.Color;

public class NativePrecisionAccessTest {
	@Test
	public void scaleMatchesAbsoluteCoordinatesIncludingTranslationAndZero() {
		assertEquals(21, NativePrecisionAccess.coordinateScale(new double[]{-21, 3, 4, -20, 5, 6}), 0);
		assertEquals(0, NativePrecisionAccess.coordinateScale(new double[]{0, 0, 0}), 0);
		assertEquals(0, NativePrecisionAccess.coordinateScale(new double[0]), 0);
		for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
			try {
				NativePrecisionAccess.coordinateScale(new double[]{invalid});
				fail("nonfinite coordinate accepted");
			} catch (IllegalArgumentException expected) {
			}
		}
	}

	@Test
	public void precisionIsRaisedDuringExportAndRestoredAfterSuccessAndError() throws Throwable {
		CSGManifold3d producer = new CSGManifold3d();
		try {
			final CSG input = producer.cube(10, 10, 10, true);
			final double previous = NativePrecisionAccess.getRelativePrecision();
			final double[] observed = new double[1];
			final AssertionError exportFailure = new AssertionError("export failed");
			for (final boolean failExport : new boolean[]{false, true}) {
				CSGManifold3d cleanup = new CSGManifold3d() {
					@Override
					public CSG fromManifold(MemorySegment handle, Color color) throws Throwable {
						observed[0] = NativePrecisionAccess.getRelativePrecision();
						if (failExport)
							throw exportFailure;
						return super.fromManifold(handle, color);
					}
				};
				try {
					try {
						cleanup.simplify(input, 1e-6);
						assertFalse(failExport);
					} catch (AssertionError failure) {
						assertSame(exportFailure, failure);
					}
					assertEquals(Math.max(previous, 1e-6 / 5), observed[0], 0);
					assertEquals(previous, NativePrecisionAccess.getRelativePrecision(), 0);
				} finally {
					cleanup.close();
				}
			}
		} finally {
			producer.close();
		}
	}

	@Test
	public void cleanupExcludesOtherBridgeUntilPrecisionIsRestored() throws Throwable {
		final CountDownLatch exporting = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final CountDownLatch ordinaryStarted = new CountDownLatch(1);
		final CountDownLatch ordinaryDone = new CountDownLatch(1);
		CSGManifold3d cleanup = new CSGManifold3d() {
			@Override
			public CSG fromManifold(MemorySegment handle, Color color) throws Throwable {
				exporting.countDown();
				assertTrue(release.await(10, TimeUnit.SECONDS));
				return super.fromManifold(handle, color);
			}
		};
		CSGManifold3d ordinary = new CSGManifold3d();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			final CSG input = ordinary.cube(10, 10, 10, true);
			final double epsilon = ordinary.epsilon(input);
			Future<?> cleaned = executor.submit(() -> {
				try {
					cleanup.simplify(input, 1e-3);
				} catch (Throwable failure) {
					throw new AssertionError(failure);
				}
			});
			assertTrue(exporting.await(10, TimeUnit.SECONDS));
			Future<?> measured = executor.submit(() -> {
				ordinaryStarted.countDown();
				try {
					assertEquals(epsilon, ordinary.epsilon(input), 0);
				} catch (Throwable failure) {
					throw new AssertionError(failure);
				} finally {
					ordinaryDone.countDown();
				}
			});
			assertTrue(ordinaryStarted.await(10, TimeUnit.SECONDS));
			assertFalse(ordinaryDone.await(100, TimeUnit.MILLISECONDS));
			release.countDown();
			cleaned.get(10, TimeUnit.SECONDS);
			measured.get(10, TimeUnit.SECONDS);
		} finally {
			release.countDown();
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
			cleanup.close();
			ordinary.close();
		}
	}

	@Test
	public void zeroToleranceRetainsNativePrecisionAndFailedImportRestoresIt() throws Throwable {
		CSGManifold3d bridge = new CSGManifold3d();
		try {
			final double previous = NativePrecisionAccess.getRelativePrecision();
			CSGManifold3d observing = new CSGManifold3d() {
				@Override
				public MemorySegment toManifold(CSG input) throws Throwable {
					assertEquals(previous, NativePrecisionAccess.getRelativePrecision(), 0);
					return super.toManifold(input);
				}
			};
			try {
				observing.simplify(bridge.cube(1, 1, 1, true), 0);
			} finally {
				observing.close();
			}
			CSG openTriangle = new CSG(new double[]{0, 0, 0, 1, 0, 0, 0, 1, 0}, new long[]{0, 1, 2}, Color.RED);
			try {
				bridge.simplify(openTriangle, 1e-6);
				fail("open mesh accepted");
			} catch (NonManifoldShapeError expected) {
			}
			assertEquals(previous, NativePrecisionAccess.getRelativePrecision(), 0);
		} finally {
			bridge.close();
		}
	}

	@Test
	public void unionProgressCanReenterCleanup() throws Throwable {
		CSGManifold3d bridge = new CSGManifold3d();
		try {
			CSG cube = bridge.cube(1, 1, 1, true);
			CSG result = bridge.unionAll(Arrays.asList(cube, cube), (current, total, type, solid) -> {
				try {
					assertEquals(1, bridge.volume(bridge.simplify(cube, 1e-6)), 1e-12);
				} catch (Throwable failure) {
					throw new AssertionError(failure);
				}
			});
			assertEquals(1, bridge.volume(result), 1e-12);
		} finally {
			bridge.close();
		}
	}
}

package com.neuronrobotics.manifold3d;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

import com.cadoodlecad.manifold.ManifoldBindings;
import com.cadoodlecad.manifold.ManifoldBindings.ManifoldError;

import eu.mihosoft.vrl.v3d.CSG;

/** Precision cleanup using only Manifold's native topology operations. */
public final class NativeMeshCleanup {
	/** Ten times OCC confusion resolution, expressed in model units. */
	public static final double DEFAULT_TOLERANCE = 1e-6;

	private final CSGManifold3d bridge;
	private final ManifoldBindings nativeApi;

	NativeMeshCleanup(CSGManifold3d bridge, ManifoldBindings nativeApi) {
		this.bridge = bridge;
		this.nativeApi = nativeApi;
	}

	public CSG simplify(CSG incoming, double tolerance) throws Throwable {
		Objects.requireNonNull(incoming, "incoming");
		if (!Double.isFinite(tolerance) || tolerance < 0)
			throw new IllegalArgumentException("tolerance must be finite and nonnegative");
		if (incoming.getTriCount() == 0)
			return new CSG().historySync(incoming);

		double scale = NativePrecisionAccess.coordinateScale(incoming.getVertices());
		try (NativeAccess.Scope ignored = NativeAccess.write()) {
			double previous = NativePrecisionAccess.getRelativePrecision();
			double relative = scale == 0 ? previous : Math.max(previous, tolerance / scale);
			if (!Double.isFinite(relative))
				throw new IllegalArgumentException("tolerance divided by mesh scale must be finite");
			try {
				NativePrecisionAccess.setRelativePrecision(relative);
				return cleanup(incoming, tolerance);
			} finally {
				NativePrecisionAccess.setRelativePrecision(previous);
			}
		}
	}

	private CSG cleanup(CSG incoming, double tolerance) throws Throwable {
		MemorySegment input = bridge.toManifold(incoming);
		try {
			MemorySegment original = nativeApi.asOriginal(input);
			try {
				checkStatus(original);
				MemorySegment result = nativeApi.simplify(original, tolerance);
				try {
					checkStatus(result);
					return bridge.fromManifold(result, incoming.getColor()).historySync(incoming);
				} finally {
					nativeApi.delete(result);
				}
			} finally {
				nativeApi.delete(original);
			}
		} finally {
			nativeApi.delete(input);
		}
	}

	private void checkStatus(MemorySegment solid) throws Throwable {
		ManifoldError status = nativeApi.status(solid);
		if (status != ManifoldError.NO_ERROR)
			throw new NonManifoldShapeError("Native cleanup failed: " + status);
	}
}

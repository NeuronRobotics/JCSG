package com.neuronrobotics.manifold3d;

import java.lang.foreign.MemorySegment;

import com.cadoodlecad.manifold.ManifoldBindings;
import eu.mihosoft.vrl.v3d.CSG;

/** Mesh cleanup and smoothing without losing native normals or tangents between stages. */
public final class MeshProcessing {
	private MeshProcessing() {
	}

	public static final class Settings {
		private final double simplify;
		private final int calculateNormals;
		private final int smoothByNormals;
		private final double normalAngle;
		private final double smoothOut;
		private final double smoothness;
		private final int refine;
		private final double refineToLength;
		private final double refineToTolerance;

		public Settings(double simplify, int calculateNormals, int smoothByNormals, double normalAngle,
				double smoothOut, double smoothness, int refine, double refineToLength, double refineToTolerance) {
			if (!Double.isFinite(simplify) || !Double.isFinite(smoothness) || !Double.isFinite(refineToLength)
					|| !Double.isFinite(refineToTolerance))
				throw new IllegalArgumentException("Mesh settings must be finite");
			if (!Double.isFinite(normalAngle) || !Double.isFinite(smoothOut) || normalAngle < 0 || normalAngle > 180
					|| smoothOut > 180)
				throw new IllegalArgumentException("Normal and smoothing angles must not exceed 180 degrees");
			if (smoothness < 0 || smoothness > 1)
				throw new IllegalArgumentException("Smoothness must be between 0 and 1");
			this.simplify = simplify;
			this.calculateNormals = calculateNormals;
			this.smoothByNormals = smoothByNormals;
			this.normalAngle = normalAngle;
			this.smoothOut = smoothOut;
			this.smoothness = smoothness;
			this.refine = refine;
			this.refineToLength = refineToLength;
			this.refineToTolerance = refineToTolerance;
		}

		public double simplify() {
			return simplify;
		}

		public int calculateNormals() {
			return calculateNormals;
		}

		public int smoothByNormals() {
			return smoothByNormals;
		}

		public double normalAngle() {
			return normalAngle;
		}

		public double smoothOut() {
			return smoothOut;
		}

		public double smoothness() {
			return smoothness;
		}

		public int refine() {
			return refine;
		}

		public double refineToLength() {
			return refineToLength;
		}

		public double refineToTolerance() {
			return refineToTolerance;
		}

		public boolean enabled() {
			return simplify > 0 || calculateNormals > 0 || smoothByNormals > 0 || smoothOut > 0 || refine > 0
					|| refineToLength > 0 || refineToTolerance > 0;
		}
	}

	public static CSG apply(CSG incoming, Settings settings) throws Throwable {
		if (!settings.enabled())
			return incoming;
		CSG clean = settings.simplify() > 0 ? incoming.simplify(settings.simplify()) : incoming;
		if (settings.calculateNormals() <= 0 && settings.smoothByNormals() <= 0 && settings.smoothOut() <= 0
				&& settings.refine() <= 0 && settings.refineToLength() <= 0 && settings.refineToTolerance() <= 0)
			return clean;
		try (NativeAccess.Scope ignored = NativeAccess.read()) {
			CSGManifold3d bridge = new CSGManifold3d();
			ManifoldBindings nativeApi = new ManifoldBindings();
			MemorySegment current = bridge.toManifold(clean);
			try {
				if (settings.calculateNormals() > 0)
					current = replace(nativeApi, current,
							nativeApi.calculateNormals(current, settings.calculateNormals(), settings.normalAngle()));
				if (settings.smoothByNormals() > 0) {
					if (settings.calculateNormals() != settings.smoothByNormals())
						current = replace(nativeApi, current, nativeApi.calculateNormals(current,
								settings.smoothByNormals(), settings.normalAngle()));
					current = replace(nativeApi, current,
							nativeApi.smoothByNormals(current, settings.smoothByNormals()));
				} else if (settings.calculateNormals() > 0) {
					// Bake calculated normals into tangents before CSG discards property channels.
					current = replace(nativeApi, current,
							nativeApi.smoothByNormals(current, settings.calculateNormals()));
				}
				if (settings.smoothOut() > 0)
					current = replace(nativeApi, current,
							bridge.smoothOut(current, settings.smoothOut(), settings.smoothness()));
				if (settings.refine() > 0)
					current = replace(nativeApi, current, nativeApi.refine(current, settings.refine()));
				if (settings.refineToLength() > 0)
					current = replace(nativeApi, current, nativeApi.refineToLength(current, settings.refineToLength()));
				if (settings.refineToTolerance() > 0)
					current = replace(nativeApi, current,
							nativeApi.refineToTolerance(current, settings.refineToTolerance()));
				boolean smoothing = settings.calculateNormals() > 0 || settings.smoothByNormals() > 0
						|| settings.smoothOut() > 0;
				if (smoothing && settings.refine() <= 0 && settings.refineToLength() <= 0
						&& settings.refineToTolerance() <= 0)
					current = replace(nativeApi, current, nativeApi.refine(current, 2));
				return bridge.fromManifold(current, incoming.getColor());
			} finally {
				nativeApi.delete(current);
			}
		}
	}

	private static MemorySegment replace(ManifoldBindings nativeApi, MemorySegment previous, MemorySegment next)
			throws Throwable {
		try {
			if (nativeApi.status(next) != ManifoldBindings.ManifoldError.NO_ERROR)
				throw new NonManifoldShapeError("Mesh processing failed: " + nativeApi.status(next));
		} catch (Throwable failure) {
			nativeApi.delete(next);
			throw failure;
		}
		nativeApi.delete(previous);
		return next;
	}
}

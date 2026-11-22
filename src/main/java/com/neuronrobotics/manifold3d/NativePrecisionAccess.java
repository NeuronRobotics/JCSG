package com.neuronrobotics.manifold3d;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** Access to the native quality API missing from the Java bindings. */
final class NativePrecisionAccess {
	private NativePrecisionAccess() {
	}

	private static final class Symbols {
		private static final MethodHandle GET = bind("manifold_get_relative_precision",
				FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE));
		private static final MethodHandle SET = bind("manifold_set_relative_precision",
				FunctionDescriptor.ofVoid(ValueLayout.JAVA_DOUBLE));

		private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
			MemorySegment symbol = SymbolLookup.loaderLookup().find(name)
					.orElseThrow(() -> new UnsatisfiedLinkError("Native precision cleanup requires " + name));
			return Linker.nativeLinker().downcallHandle(symbol, descriptor);
		}
	}

	static double getRelativePrecision() throws Throwable {
		try (NativeAccess.Scope ignored = NativeAccess.read()) {
			return (double) Symbols.GET.invokeExact();
		}
	}

	// Caller holds the exclusive scope through import, cleanup, export and restoration;
	static void setRelativePrecision(double precision) throws Throwable {
		Symbols.SET.invokeExact(precision);
	}

	/** Matches manifold::Box::Scale, not the box diagonal or longest extent. */
	static double coordinateScale(double[] vertices) {
		double scale = 0;
		for (double coordinate : vertices) {
			if (!Double.isFinite(coordinate))
				throw new IllegalArgumentException("mesh coordinates must be finite");
			scale = Math.max(scale, Math.abs(coordinate));
		}
		return scale;
	}
}

package eu.mihosoft.vrl.v3d;

import java.lang.foreign.MemorySegment;

import org.junit.Test;

import com.cadoodlecad.manifold.ManifoldBindings;

public class Manifold3d_test {
	@Test
	public void loadTest() throws Throwable {
		ManifoldBindings manifold = new ManifoldBindings();
		MemorySegment cube = manifold.cube(10, 10, 10, false);
	}
}

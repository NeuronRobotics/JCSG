package eu.mihosoft.vrl.v3d;

import java.lang.foreign.MemorySegment;

import org.junit.Test;

import com.cadoodlecad.manifold.ManifoldBindings;

import eu.mihosoft.vrl.v3d.CSG.OptType;

public class Manifold3d_test {
	@Test
	public void loadTest() throws Throwable {
		CSG.setDefaultOptType(OptType.Manifold3d);
		
	}
}

package eu.mihosoft.vrl.v3d;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG.OptType;

public class Manifold3d_test {
	@Test
	public void loadTest() throws Throwable {
		OptType og = CSG.getDefaultOptionType();

		try {
			CSG.setDefaultOptType(OptType.Manifold3d);

		} catch (Throwable t) {
			t.printStackTrace();
			// Set back to default to complete test and not disrupt other tests
			CSG.setDefaultOptType(og);
			throw t;
		}
		// Set back to default to complete test and not disrupt other tests
		CSG.setDefaultOptType(og);
	}
}

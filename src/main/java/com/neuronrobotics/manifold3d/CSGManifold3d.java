package com.neuronrobotics.manifold3d;

import com.cadoodlecad.manifold.ManifoldBindings;

public class CSGManifold3d {
	private final ManifoldBindings manifold;
	Manifold3dExporter exporter;
	Manifold3dImporter importer;
	public CSGManifold3d() throws Exception {
		this.manifold = new ManifoldBindings();
		exporter = new Manifold3dExporter(manifold);
		importer = new Manifold3dImporter(manifold);
	}
	
	
}

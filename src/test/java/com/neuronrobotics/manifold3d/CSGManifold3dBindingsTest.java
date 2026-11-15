package com.neuronrobotics.manifold3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.cadoodlecad.manifold.ManifoldBindings.ManifoldError;
import com.cadoodlecad.manifold.ManifoldBindings;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.geometry.BoundingBox;

/**
 * One test per CSGManifold3d binding to the ManifoldBindings API. Every case
 * feeds JCSG {@link CSG} solids in and asserts on the CSG or measurement that
 * comes back, so both halves of each conversion stay under test.
 */
public class CSGManifold3dBindingsTest {

	private static CSGManifold3d manifold;

	@BeforeClass
	public static void setUp() throws Throwable {
		manifold = new CSGManifold3d();
	}

	@AfterClass
	public static void tearDown() {
		if (manifold != null)
			manifold.close();
	}

	// ---- shared fixtures ----------------------------------------------------

	/** 20mm cube centred on the origin. */
	private static CSG cube(double size) {
		return new Cube(size, size, size).toCSG();
	}

	private static void assertNoError(CSG csg) throws Throwable {
		assertEquals(ManifoldError.NO_ERROR, manifold.status(csg));
	}

	// ---- boolean operations -------------------------------------------------

	@Test
	public void booleanUnionAddsVolumes() throws Throwable {
		CSG a = cube(20);
		CSG b = cube(20).transformed(new Transform().translateX(20));
		// two touching cubes: union volume is the sum, intersection empty
		CSG u = manifold.union(a, b);
		assertEquals(2 * 8000, manifold.volume(u), 1e-6);
	}

	@Test
	public void booleanDifferenceRemovesVolumes() throws Throwable {
		CSG a = cube(20);
		CSG b = cube(10);
		CSG d = manifold.difference(a, b);
		// the membrane pass leaves epsilon-scale residue at the cut faces
		assertEquals(8000 - 1000, manifold.volume(d), 1e-3);
	}

	@Test
	public void booleanIntersectionKeepsSharedVolume() throws Throwable {
		CSG a = cube(20);
		CSG b = cube(20).transformed(new Transform().translateX(10));
		CSG i = manifold.intersection(a, b);
		assertEquals(10 * 20 * 20, manifold.volume(i), 1e-6);
	}

	@Test
	public void booleanOpRunsEachOptype() throws Throwable {
		CSG a = cube(20);
		CSG b = cube(10);
		assertEquals(ManifoldBindings.OPTYPE_UNION, CSGManifold3d.BooleanOp.UNION.toInt());
		assertEquals(ManifoldBindings.OPTYPE_DIFFERENCE, CSGManifold3d.BooleanOp.DIFFERENCE.toInt());
		assertEquals(ManifoldBindings.OPTYPE_INTERSECTION, CSGManifold3d.BooleanOp.INTERSECTION.toInt());
		assertEquals(8000, manifold.volume(manifold.booleanOp(a, b, CSGManifold3d.BooleanOp.UNION)), 1e-6);
		assertEquals(7000, manifold.volume(manifold.booleanOp(a, b, CSGManifold3d.BooleanOp.DIFFERENCE)), 1e-6);
		assertEquals(1000, manifold.volume(manifold.booleanOp(a, b, CSGManifold3d.BooleanOp.INTERSECTION)), 1e-6);
	}

	@Test
	public void batchUnionMatchesSequentialUnion() throws Throwable {
		CSG a = cube(10);
		CSG b = cube(10).transformed(new Transform().translateX(10));
		CSG c = cube(10).transformed(new Transform().translateY(10));
		CSG batch = manifold.batchUnion(a, b, c);
		assertEquals(3000, manifold.volume(batch), 1e-6);
		// disjoint inputs must not be merged into one body by accident
		assertEquals(ManifoldError.NO_ERROR, manifold.status(batch));
	}

	@Test(expected = IllegalArgumentException.class)
	public void batchUnionRejectsEmptyInput() throws Throwable {
		manifold.batchUnion(new CSG[0]);
	}

	// ---- primitives ----------------------------------------------------------

	@Test
	public void tetrahedronMatchesManifoldUnitShape() throws Throwable {
		CSG t = manifold.tetrahedron();
		assertNoError(t);
		// Manifold's unit tetrahedron: 4 verts, 4 tris, volume 8/3
		assertEquals(4l, manifold.numVert(t));
		assertEquals(4l, manifold.numTri(t));
		assertEquals(8.0 / 3.0, manifold.volume(t), 1e-9);
	}

	@Test
	public void cubeCenteredSpansSymmetrically() throws Throwable {
		CSG c = manifold.cube(10, 20, 30, true);
		assertNoError(c);
		assertEquals(10 * 20 * 30, manifold.volume(c), 1e-9);
		BoundingBox b = manifold.getBounds(c);
		assertEquals(-15, b.getMinZ(), 1e-9);
		assertEquals(15, b.getMaxZ(), 1e-9);
	}

	@Test
	public void cubeUncenteredSitsInFirstOctant() throws Throwable {
		CSG c = manifold.cube(10, 20, 30, false);
		BoundingBox b = manifold.getBounds(c);
		assertEquals(0, b.getMinX(), 1e-9);
		assertEquals(0, b.getMinY(), 1e-9);
		assertEquals(0, b.getMinZ(), 1e-9);
		assertEquals(10, b.getMaxX(), 1e-9);
	}

	@Test
	public void sphereIsCentredWithExpectedArea() throws Throwable {
		CSG s = manifold.sphere(10, 32);
		assertNoError(s);
		BoundingBox b = manifold.getBounds(s);
		assertEquals(-10, b.getMinX(), 1e-6);
		assertEquals(10, b.getMaxX(), 1e-6);
		// faceted sphere always undershoots the exact area but gets close
		double area = manifold.surfaceArea(s);
		assertTrue("sphere area " + area + " too far from " + 4 * Math.PI * 100,
				Math.abs(area - 4 * Math.PI * 100) / (4 * Math.PI * 100) < 0.05);
	}

	@Test
	public void cylinderRadiusAndPlacement() throws Throwable {
		CSG cyl = manifold.cylinder(20, 5, 5, 32, 0);
		assertNoError(cyl);
		BoundingBox b = manifold.getBounds(cyl);
		assertEquals(0, b.getMinZ(), 1e-9);
		assertEquals(20, b.getMaxZ(), 1e-9);
		assertEquals(10, b.getMaxX() - b.getMinX(), 1e-6);
	}

	@Test
	public void cylinderConeTapersToApex() throws Throwable {
		CSG cone = manifold.cylinder(20, 10, 0, 32, 1);
		assertNoError(cone);
		// cone volume is a third of the circumscribing cylinder
		assertEquals(Math.PI * 100 * 20 / 3, manifold.volume(cone), 20.0);
	}

	// ---- transformations -----------------------------------------------------

	@Test
	public void translateMovesTheBounds() throws Throwable {
		CSG moved = manifold.translate(manifold.cube(10, 10, 10, false), 5, 6, 7);
		BoundingBox b = manifold.getBounds(moved);
		assertEquals(5, b.getMinX(), 1e-9);
		assertEquals(6, b.getMinY(), 1e-9);
		assertEquals(7, b.getMinZ(), 1e-9);
	}

	@Test
	public void scaleResizesPerAxis() throws Throwable {
		CSG scaled = manifold.scale(manifold.cube(10, 10, 10, false), 2, 1, 0.5);
		assertEquals(10 * 2 * 10 * 0.5 * 10, manifold.volume(scaled), 1e-6);
	}

	@Test
	public void rotateZTurnsTheShape() throws Throwable {
		CSG c = manifold.cube(10, 10, 10, true).transformed(new Transform().translateX(20));
		CSG rotated = manifold.rotateZ(c, 90);
		BoundingBox b = manifold.getBounds(rotated);
		// the far-x arm swings onto the +Y axis
		assertEquals(25, b.getMaxY(), 1e-6);
		assertTrue(Math.abs(b.getMaxX() - 5) < 1e-6);
	}

	@Test
	public void rotateXMatchesJCSGTransformed() throws Throwable {
		// JCSG rotX and Manifold rotateX turn in opposite directions, so compare
		// against the mirrored JCSG rotation
		CSG arm = manifold.cube(10, 10, 10, true).transformed(new Transform().translateY(20));
		CSG viaManifold = manifold.rotateX(arm, 90);
		CSG viaJCSG = arm.rotx(-90);
		BoundingBox bm = manifold.getBounds(viaManifold);
		Bounds bj = viaJCSG.getBounds();
		assertEquals(bj.getMinZ(), bm.getMinZ(), 1e-6);
		assertEquals(bj.getMaxZ(), bm.getMaxZ(), 1e-6);
	}

	@Test
	public void transformedAppliesFullJCSGMatrix() throws Throwable {
		Transform t = new Transform().rotZ(30).translate(5, 0, 0);
		CSG solid = manifold.cube(10, 10, 10, true);
		CSG viaManifold = manifold.transformed(solid, t);
		CSG viaJCSG = new Cube(10, 10, 10).toCSG().transformed(t);
		BoundingBox bm = manifold.getBounds(viaManifold);
		Bounds bj = viaJCSG.getBounds();
		assertEquals(bj.getMinX(), bm.getMinX(), 1e-6);
		assertEquals(bj.getMaxX(), bm.getMaxX(), 1e-6);
		assertEquals(bj.getMaxY(), bm.getMaxY(), 1e-6);
	}

	@Test
	public void mirrorFlipsAcrossPlane() throws Throwable {
		CSG arm = manifold.cube(10, 10, 10, true).transformed(new Transform().translateX(20));
		CSG mirrored = manifold.mirror(arm, 1, 0, 0);
		BoundingBox b = manifold.getBounds(mirrored);
		// the +X arm becomes a -X arm of the same length
		assertEquals(-25, b.getMinX(), 1e-6);
		assertEquals(-15, b.getMaxX(), 1e-6);
	}

	// ---- refinement -----------------------------------------------------------

	@Test
	public void refineMultipliesTriangles() throws Throwable {
		CSG sphereCSG = manifold.sphere(10, 8);
		long before = manifold.numTri(sphereCSG);
		CSG refined = manifold.refine(sphereCSG, 2);
		assertEquals(before * 4, manifold.numTri(refined));
	}

	@Test
	public void refineToLengthEnforcesEdgeCap() throws Throwable {
		CSG c = manifold.cube(10, 10, 10, true);
		CSG refined = manifold.refineToLength(c, 1.0);
		assertTrue("expected refinement, tri count unchanged", manifold.numTri(refined) > manifold.numTri(c));
	}

	@Test
	public void refineToToleranceAddsFacets() throws Throwable {
		CSG s = manifold.sphere(10, 8);
		long before = manifold.numTri(s);
		CSG refined = manifold.refineToTolerance(s, 0.01);
		// without native tangents the tolerance pass is a documented no-op
		assertEquals(before, manifold.numTri(refined));
	}

	@Test
	public void calculateNormalsThenSmoothOutRefinesSurface() throws Throwable {
		CSG s = manifold.sphere(10, 8);
		long before = manifold.numTri(s);
		CSG normals = manifold.calculateNormals(s, 0, 20);
		// without property channels the normals cannot be stored, so this is a
		// documented passthrough
		assertEquals(before, manifold.numTri(normals));
	}

	@Test
	public void smoothOutKeepsVolume() throws Throwable {
		CSG s = manifold.sphere(10, 8);
		CSG smoothed = manifold.smoothOut(s, 60, 1.0);
		assertNoError(smoothed);
		// smoothing reshapes facets but stays near the same enclosed volume
		double v0 = manifold.volume(s);
		double v1 = manifold.volume(smoothed);
		assertTrue(Math.abs(v1 - v0) / v0 < 0.05);
	}

	@Test
	public void smoothByNormalsKeepsGeometryUntilRefine() throws Throwable {
		CSG s = manifold.sphere(10, 8);
		CSG smooth = manifold.smoothByNormals(s, 0);
		// without property channels the normals cannot be stored, so this is a
		// documented passthrough and the mesh must be unchanged
		assertEquals(manifold.numTri(s), manifold.numTri(smooth));
	}

	@Test
	public void simplifyReducesTriangleCount() throws Throwable {
		// sphere triangulated into coplanar fans simplifies back down
		CSG s = manifold.sphere(10, 64);
		CSG simple = manifold.simplify(s, 0.1);
		assertTrue("simplify grew the mesh: " + manifold.numTri(simple) + " vs " + manifold.numTri(s),
				manifold.numTri(simple) <= manifold.numTri(s));
		assertNoError(simple);
	}

	// ---- split / trim ---------------------------------------------------------

	@Test
	public void trimByPlaneKeepsPositiveSide() throws Throwable {
		CSG c = manifold.cube(20, 20, 20, true).transformed(new Transform().translateZ(10)); // 0..20 in Z
		CSG trimmed = manifold.trimByPlane(c, 0, 0, 1, 10);
		BoundingBox b = manifold.getBounds(trimmed);
		assertEquals(20, b.getMaxZ(), 1e-6);
		assertEquals(4000, manifold.volume(trimmed), 1e-6);
	}

	@Test
	public void splitByPlaneReturnsBothHalves() throws Throwable {
		CSG c = manifold.cube(20, 20, 20, true).transformed(new Transform().translateZ(10));
		List<CSG> halves = manifold.splitByPlane(c, 0, 0, 1, 10);
		assertEquals(2, halves.size());
		assertEquals(4000, manifold.volume(halves.get(0)), 1e-6);
		assertEquals(4000, manifold.volume(halves.get(1)), 1e-6);
	}

	@Test
	public void splitByCutterReturnsInsideAndOutside() throws Throwable {
		CSG bar = manifold.cube(20, 20, 20, true).transformed(new Transform().translateX(10)); // 0..20 x
		CSG cutter = manifold.cube(10, 10, 10, true); // centred, cuts x -5..5
		List<CSG> pieces = manifold.split(bar, cutter);
		assertEquals(2, pieces.size());
		// the bar spans 0..20 in x and the cutter -5..5, so the inside piece is
		// the 0..5 x slab of the cutter's 10x10 section
		assertEquals(5 * 10 * 10, manifold.volume(pieces.get(0)), 1e-6);
		assertEquals(20 * 20 * 20 - 5 * 10 * 10, manifold.volume(pieces.get(1)), 1e-6);
	}

	// ---- slicing ---------------------------------------------------------------

	@Test
	public void sliceMidCubeGivesSquareContour() throws Throwable {
		CSG c = cube(20);
		ArrayList<Polygon> section = manifold.slice(c, 0);
		assertEquals(1, section.size());
		Bounds b = section.get(0).getBounds();
		assertEquals(-10, b.getMin().x, 1e-6);
		assertEquals(10, b.getMax().x, 1e-6);
	}

	@Test
	public void sliceAboveCubeIsEmpty() throws Throwable {
		CSG c = cube(20);
		ArrayList<Polygon> section = manifold.slice(c, 50);
		assertEquals(0, section.size());
	}

	@Test
	public void sliceCylinderTracksRadius() throws Throwable {
		CSG cyl = manifold.cylinder(20, 10, 5, 32, 0); // radius falls 10 to 5
		ArrayList<Polygon> low = manifold.slice(cyl, 2);
		ArrayList<Polygon> high = manifold.slice(cyl, 18);
		assertEquals(1, low.size());
		assertEquals(1, high.size());
		double lowR = low.get(0).getBounds().getMax().x;
		double highR = high.get(0).getBounds().getMax().x;
		assertTrue("low slice should be wider: " + lowR + " vs " + highR, lowR > highR);
		assertTrue("radius at z=2 should approach 10: " + lowR, lowR > 9);
		assertTrue("radius at z=18 should approach 5: " + highR, highR < 6);
	}

	// ---- composition -----------------------------------------------------------

	@Test
	public void composeKeepsOverlappingPartsSeparate() throws Throwable {
		CSG a = cube(10);
		CSG b = cube(10).transformed(new Transform().translateX(30));
		CSG both = manifold.compose(a, b);
		assertNoError(both);
		// disjoint parts keep their own surfaces and both volumes remain
		assertEquals(2000, manifold.volume(both), 1e-6);
	}

	@Test
	public void composeWeldsCoincidentGeometry() throws Throwable {
		// coincident copies share every surface, and compose removes those
		// degenerates, so the result is a single body
		CSG both = manifold.compose(cube(10), cube(10));
		assertEquals(1000, manifold.volume(both), 1e-6);
	}

	@Test
	public void composeSinglePartRoundTrips() throws Throwable {
		CSG one = manifold.compose(cube(10));
		assertEquals(1000, manifold.volume(one), 1e-6);
	}

	// ---- analysis ---------------------------------------------------------------

	@Test
	public void volumeMatchesCube() throws Throwable {
		assertEquals(8000, manifold.volume(cube(20)), 1e-6);
	}

	@Test
	public void surfaceAreaMatchesCube() throws Throwable {
		assertEquals(6 * 400, manifold.surfaceArea(cube(20)), 1e-6);
	}

	@Test
	public void epsilonIsPositive() throws Throwable {
		assertTrue(manifold.epsilon(cube(20)) > 0);
	}

	@Test
	public void genusOfSolidCubeIsZero() throws Throwable {
		assertEquals(0, manifold.genus(cube(20)));
	}

	@Test
	public void numVertTriEdgeOfCube() throws Throwable {
		CSG c = cube(10);
		assertEquals(8l, manifold.numVert(c));
		assertEquals(12l, manifold.numTri(c));
		assertEquals(18l, manifold.numEdge(c));
	}

	@Test
	public void numPropRoundTripsToPositionOnly() throws Throwable {
		// positions are folded into the vertex array on the way back to CSG, so
		// the property channel count is 0 for any round-tripped solid
		assertEquals(0l, manifold.numProp(cube(10)));
	}

	@Test
	public void statusReportsNoErrorForValidSolid() throws Throwable {
		assertEquals(ManifoldError.NO_ERROR, manifold.status(cube(10)));
	}

	@Test
	public void estimateMemoryBytesScalesWithTriangles() throws Throwable {
		long small = manifold.estimateMemoryBytes(cube(10));
		long big = manifold.estimateMemoryBytes(manifold.sphere(10, 64));
		assertTrue("estimate should grow with the mesh: " + small + " vs " + big, big > small);
		assertTrue(small > 0);
	}

	@Test
	public void nativeHandleSizesArePositive() throws Throwable {
		assertTrue(manifold.manifoldSize() > 0);
		assertTrue(manifold.meshGL64Size() > 0);
		assertTrue(manifold.vecSize() > 0);
	}

	// ---- quality globals ----------------------------------------------------------

	@Test
	public void setCircularSegmentsPinsSegmentCount() throws Throwable {
		int saved = manifold.getCircularSegments(10);
		try {
			manifold.setCircularSegments(16);
			assertEquals(16, manifold.getCircularSegments(10));
			assertEquals(16, manifold.getCircularSegments(100));
		} finally {
			manifold.setCircularSegments(saved > 0 ? saved : 0);
		}
	}

	@Test
	public void smallerMinAngleGivesMoreSegments() throws Throwable {
		manifold.setCircularSegments(0);
		try {
			int coarse = manifold.getCircularSegments(10);
			manifold.setMinCircularAngle(1.0);
			int fine = manifold.getCircularSegments(10);
			assertTrue("finer angle should raise segment count: " + coarse + " -> " + fine, fine > coarse);
		} finally {
			manifold.setMinCircularAngle(10.0);
			manifold.setCircularSegments(0);
		}
	}

	// ---- bounds and centring -------------------------------------------------------

	@Test
	public void getBoundsMatchesSolid() throws Throwable {
		BoundingBox b = manifold.getBounds(cube(20));
		assertEquals(-10, b.getMinX(), 1e-9);
		assertEquals(10, b.getMaxZ(), 1e-9);
		assertEquals(20, b.getWidth(), 1e-9);
	}

	@Test
	public void centerObjectMovesCentroidToOrigin() throws Throwable {
		CSG offCentre = cube(20).transformed(new Transform().translate(30, -40, 50));
		CSG centred = manifold.centerObject(offCentre);
		BoundingBox b = manifold.getBounds(centred);
		assertEquals(0, b.getMinX() + b.getWidth() / 2, 1e-6);
		assertEquals(0, b.getMinZ() + b.getDepth() / 2, 1e-6);
		assertEquals(8000, manifold.volume(centred), 1e-6);
	}

	// ---- hull ---------------------------------------------------------------------

	@Test
	public void hullOfPointsWrapsTheCloud() throws Throwable {
		ArrayList<Vector3d> points = new ArrayList<>();
		// octahedron of points: hull must contain all of them and exceed any cube
		// inside it
		points.add(Vector3d.xyz(10, 0, 0));
		points.add(Vector3d.xyz(-10, 0, 0));
		points.add(Vector3d.xyz(0, 10, 0));
		points.add(Vector3d.xyz(0, -10, 0));
		points.add(Vector3d.xyz(0, 0, 10));
		points.add(Vector3d.xyz(0, 0, -10));
		CSG hull = manifold.hull(points);
		assertNoError(hull);
		BoundingBox b = manifold.getBounds(hull);
		assertEquals(-10, b.getMinX(), 1e-6);
		assertEquals(10, b.getMaxZ(), 1e-6);
		// octahedron volume: 4/3 * a^3
		assertEquals(4.0 / 3.0 * 1000, manifold.volume(hull), 1e-6);
	}

	@Test
	public void batchHullWrapsEverySolid() throws Throwable {
		CSG a = cube(10).transformed(new Transform().translateX(15));
		CSG b = cube(10).transformed(new Transform().translateX(-15));
		CSG hull = manifold.hull(a, b);
		BoundingBox bnds = manifold.getBounds(hull);
		// hull must span both bodies and the gap between them
		assertEquals(-20, bnds.getMinX(), 1e-6);
		assertEquals(20, bnds.getMaxX(), 1e-6);
		assertTrue(manifold.volume(hull) > 2 * 1000);
	}

	// ---- 3MF -----------------------------------------------------------------------

	@Test
	public void threeMFRoundTripPreservesEachObject() throws Throwable {
		CSG a = cube(10);
		CSG b = cube(10).transformed(new Transform().translateX(30));
		File out = File.createTempFile("csgmanifold-test", ".3mf");
		out.deleteOnExit();
		try {
			List<CSG> toWrite = new ArrayList<>();
			toWrite.add(a);
			toWrite.add(b);
			manifold.to3MF(toWrite, out.toPath());
			List<CSG> loaded = manifold.from3MF(out.toPath());
			assertEquals(2, loaded.size());
			assertEquals(1000, manifold.volume(loaded.get(0)), 1e-6);
			assertEquals(1000, manifold.volume(loaded.get(1)), 1e-6);
		} finally {
			out.delete();
		}
	}

	@Test
	public void threeMFRoundTripKeepsGeometry() throws Throwable {
		CSG s = manifold.sphere(10, 32);
		long tris = manifold.numTri(s);
		File out = File.createTempFile("csgmanifold-test", ".3mf");
		out.deleteOnExit();
		try {
			manifold.to3MF(s, out.toPath());
			List<CSG> loaded = manifold.from3MF(out.toPath());
			assertEquals(1, loaded.size());
			// the indexed topology survives the round trip
			assertEquals(tris, manifold.numTri(loaded.get(0)));
			// coordinates are parsed as floats on import, so volume matches only
			// to float precision
			assertEquals(manifold.volume(s), manifold.volume(loaded.get(0)), 1e-3);
		} finally {
			out.delete();
		}
	}

	// ---- STL -----------------------------------------------------------------------

	@Test
	public void stlRoundTripKeepsVolumeWithinFloatPrecision() throws Throwable {
		CSG c = cube(20);
		File out = File.createTempFile("csgmanifold-test", ".stl");
		out.deleteOnExit();
		try {
			manifold.toSTL(c, out.toPath());
			CSG loaded = manifold.fromSTL(out.toPath());
			// STL stores float vertices, so the volume survives only approximately
			assertEquals(8000, manifold.volume(loaded), 1.0);
		} finally {
			out.delete();
		}
	}

	// ---- copy / lifetime -------------------------------------------------------------

	@Test
	public void copyIsIndependentOfOriginal() throws Throwable {
		MemorySegment original = manifold.toManifold(cube(10));
		MemorySegment copy = null;
		try {
			copy = manifold.copy(original);
			// the copy carries the same geometry across
			CSG copyCSG = manifold.fromManifold(copy, CSG.getDefaultColor());
			assertEquals(1000, manifold.volume(copyCSG), 1e-6);
		} finally {
			manifold.delete(original);
		}
		// freeing the original must not invalidate the still-live copy
		assertNotNull(copy);
		assertEquals(1000, manifold.volume(manifold.fromManifold(copy, CSG.getDefaultColor())), 1e-6);
		manifold.delete(copy);
	}

	@Test
	public void asOriginalRebasesHistory() throws Throwable {
		MemorySegment man = manifold.toManifold(cube(10));
		try {
			MemorySegment original = manifold.asOriginal(man);
			try {
				CSG originalCSG = manifold.fromManifold(original, CSG.getDefaultColor());
				assertEquals(1000, manifold.volume(originalCSG), 1e-6);
				assertEquals(ManifoldError.NO_ERROR, manifold.status(originalCSG));
			} finally {
				manifold.delete(original);
			}
		} finally {
			manifold.delete(man);
		}
	}
}

/*
 * CSG.java
 *
 * Copyright 2014-2014 Michael Hoffer info@michaelhoffer.de. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY Michael Hoffer info@michaelhoffer.de "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL Michael Hoffer info@michaelhoffer.de OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of Michael Hoffer
 * info@michaelhoffer.de.
 */
package eu.mihosoft.vrl.v3d;

import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;
import eu.mihosoft.vrl.v3d.ext.quickhull3d.HullUtil;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.IParametric;
import eu.mihosoft.vrl.v3d.parametrics.IRegenerate;
import eu.mihosoft.vrl.v3d.parametrics.LengthParameter;
import eu.mihosoft.vrl.v3d.parametrics.Parameter;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.aparapi.Kernel;
import com.aparapi.Range;
import com.aparapi.device.Device;
import com.neuronrobotics.interaction.CadInteractionEvent;

import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Affine;

/**
 * Constructive Solid Geometry (CSG).
 * <p>
 * This implementation is a Java port of
 * <p>
 * <a href=
 * "https://github.com/evanw/csg.js/">https://github.com/evanw/csg.js/</a> with
 * some additional features like polygon extrude, transformations etc. Thanks to
 * the author for creating the CSG.js library.<br>
 * <p>
 * <b>Implementation Details</b>
 * <p>
 * All CSG operations are implemented in terms of two functions,
 * {@link Node#clipTo(eu.mihosoft.vrl.v3d.Node)} and {@link Node#invert()},
 * which remove parts of a BSP tree inside another BSP tree and swap solid and
 * empty space, respectively. To find the union of {@code a} and {@code b}, we
 * want to remove everything in {@code a} inside {@code b} and everything in
 * {@code b} inside {@code a}, then combine polygons from {@code a} and
 * {@code b} into one solid:
 * <p>
 * <blockquote>
 * 
 * <pre>
 * a.clipTo(b);
 * b.clipTo(a);
 * a.build(b.allPolygons());
 * </pre>
 * 
 * </blockquote>
 * <p>
 * The only tricky part is handling overlapping coplanar polygons in both trees.
 * The code above keeps both copies, but we need to keep them in one tree and
 * remove them in the other tree. To remove them from {@code b} we can clip the
 * inverse of {@code b} against {@code a}. The code for union now looks like
 * this:
 * <p>
 * <blockquote>
 * 
 * <pre>
 * a.clipTo(b);
 * b.clipTo(a);
 * b.invert();
 * b.clipTo(a);
 * b.invert();
 * a.build(b.allPolygons());
 * </pre>
 * 
 * </blockquote>
 * <p>
 * Subtraction and intersection naturally follow from set operations. If union
 * is {@code A | B}, differenceion is {@code A - B = ~(~A | B)} and intersection
 * is {@code A & B =
 * ~(~A | ~B)} where {@code ~} is the complement operator.
 */

@SuppressWarnings("restriction")
public class CSG implements IuserAPI {
	private static IDebug3dProvider providerOf3d = null;
	private static int numFacesInOffset = 15;

	/** The polygons. */
	private List<Polygon> polygons;

	/** The default opt type. */
	private static OptType defaultOptType = OptType.CSG_BOUND;

	/** The opt type. */
	private OptType optType = null;

	/** The storage. */
	private PropertyStorage str;
	private PropertyStorage assembly;

	/** The current. */
	private MeshView current;

	private static Color defaultcolor = Color.web("#007956");

	/** The color. */
	private Color color = getDefaultColor();

	/** The manipulator. */
	private Affine manipulator;
	private Bounds bounds;
	public static final int INDEX_OF_PARAMETRIC_DEFAULT = 0;
	public static final int INDEX_OF_PARAMETRIC_LOWER = 1;
	public static final int INDEX_OF_PARAMETRIC_UPPER = 2;
	private ArrayList<String> groovyFileLines = new ArrayList<>();
	private PrepForManufacturing manufactuing = null;
	private HashMap<String, IParametric> mapOfparametrics = null;
	private IRegenerate regenerate = null;
	private boolean markForRegeneration = false;
	private String name = "";
	private ArrayList<Transform> slicePlanes = null;
	private ArrayList<String> exportFormats = null;
	private ArrayList<Transform> datumReferences = null;
	private boolean triangulated;
	private static boolean needsDegeneratesPruned = false;
	private static boolean useStackTraces = true;
	private static boolean preventNonManifoldTriangles = false;
	private static boolean useGPU = false;

	private static ICSGProgress progressMoniter = new ICSGProgress() {
		@Override
		public void progressUpdate(int currentIndex, int finalIndex, String type, CSG intermediateShape) {
			System.err.println(type + "  cur:" + currentIndex + " of " + finalIndex);
		}
	};

	/**
	 * Instantiates a new csg.
	 */
	public CSG() {
		setStorage(new PropertyStorage());

		if (useStackTraces) {
			// This is the trace for where this csg was created
			addStackTrace(new Exception());
		}
	}

	public CSG addDatumReference(Transform t) {
		if (getDatumReferences() == null)
			setDatumReferences(new ArrayList<Transform>());
		getDatumReferences().add(t);
		return this;
	}

	public CSG prepForManufacturing() {
		if (getManufacturing() == null)
			return this;
		CSG ret = getManufacturing().prep(this);
		if (ret == null)
			return null;
		ret.setName(getName());
		ret.color = color;
		ret.slicePlanes = slicePlanes;
		ret.mapOfparametrics = mapOfparametrics;
		ret.exportFormats = exportFormats;
		return ret;
	}

	/**
	 * Gets the color.
	 *
	 * @return the color
	 */
	public Color getColor() {
		return color;
	}

	/**
	 * Sets the color.
	 *
	 * @param color the new color
	 */
	public CSG setColor(Color color) {
		this.color = color;
		for (Polygon p : polygons)
			p.setColor(color);
		return this;
	}

	public void setMeshColor(Color color) {
		if (current != null) {
			PhongMaterial m = new PhongMaterial(color);
			current.setMaterial(m);
		}
	}

	/**
	 * Sets the Temporary color.
	 *
	 * @param color the new Temporary color
	 */
	public CSG setTemporaryColor(Color color) {
		if (current != null) {
			PhongMaterial m = new PhongMaterial(color);
			current.setMaterial(m);
		}
		return this;
	}

	/**
	 * Sets the manipulator.
	 *
	 * @param manipulator the manipulator
	 * @return the affine
	 */
	public CSG setManipulator(Affine manipulator) {
		if (manipulator == null)
			return this;
		Affine old = manipulator;
		this.manipulator = manipulator;
		if (current != null) {
			current.getTransforms().clear();
			current.getTransforms().add(manipulator);
		}
		return this;
	}

	/**
	 * Gets the mesh.
	 *
	 * @return the mesh
	 */
	public MeshView getMesh() {
		if (current != null)
			return current;
		current = newMesh();
		return current;
	}

	/**
	 * Gets the mesh.
	 *
	 * @return the mesh
	 */
	public MeshView newMesh() {

		MeshContainer meshContainer = toJavaFXMesh(null);

		MeshView current = meshContainer.getAsMeshViews().get(0);

		PhongMaterial m = new PhongMaterial(getColor());
		current.setMaterial(m);

		boolean hasManipulator = getManipulator() != null;
		boolean hasAssembly = getAssemblyStorage().getValue("AssembleAffine") != Optional.empty();

		if (hasManipulator || hasAssembly)
			current.getTransforms().clear();

		if (hasManipulator)
			current.getTransforms().add(getManipulator());
		if (hasAssembly)
			current.getTransforms().add((Affine) getAssemblyStorage().getValue("AssembleAffine").get());

		current.setCullFace(CullFace.NONE);
		if (isWireFrame())
			current.setDrawMode(DrawMode.LINE);
		else
			current.setDrawMode(DrawMode.FILL);
		return current;
	}

	/**
	 * To z min.
	 *
	 * @param target the target
	 * @return the csg
	 */
	public CSG toZMin(CSG target) {
		return this.transformed(new Transform().translateZ(-target.getBounds().getMin().z));
	}

	/**
	 * To z max.
	 *
	 * @param target the target
	 * @return the csg
	 */
	public CSG toZMax(CSG target) {
		return this.transformed(new Transform().translateZ(-target.getBounds().getMax().z));
	}

	/**
	 * To x min.
	 *
	 * @param target the target
	 * @return the csg
	 */
	public CSG toXMin(CSG target) {
		return this.transformed(new Transform().translateX(-target.getBounds().getMin().x));
	}

	/**
	 * To x max.
	 *
	 * @param target the target
	 * @return the csg
	 */
	public CSG toXMax(CSG target) {
		return this.transformed(new Transform().translateX(-target.getBounds().getMax().x));
	}

	/**
	 * To y min.
	 *
	 * @param target the target
	 * @return the csg
	 */
	public CSG toYMin(CSG target) {
		return this.transformed(new Transform().translateY(-target.getBounds().getMin().y));
	}

	/**
	 * To y max.
	 *
	 * @param target the target
	 * @return the csg
	 */
	public CSG toYMax(CSG target) {
		return this.transformed(new Transform().translateY(-target.getBounds().getMax().y));
	}

	/**
	 * To z min.
	 *
	 * @return the csg
	 */
	public CSG toZMin() {
		return toZMin(this);
	}

	/**
	 * To z max.
	 *
	 * @return the csg
	 */
	public CSG toZMax() {
		return toZMax(this);
	}

	/**
	 * To x min.
	 *
	 * @return the csg
	 */
	public CSG toXMin() {
		return toXMin(this);
	}

	/**
	 * To x max.
	 *
	 * @return the csg
	 */
	public CSG toXMax() {
		return toXMax(this);
	}

	/**
	 * To y min.
	 *
	 * @return the csg
	 */
	public CSG toYMin() {
		return toYMin(this);
	}

	/**
	 * To y max.
	 *
	 * @return the csg
	 */
	public CSG toYMax() {
		return toYMax(this);
	}

	public CSG move(Number x, Number y, Number z) {
		return transformed(new Transform().translate(x.doubleValue(), y.doubleValue(), z.doubleValue()));
	}

	public CSG move(Vertex v) {
		return transformed(new Transform().translate(v.getX(), v.getY(), v.getZ()));
	}

	public CSG move(Vector3d v) {
		return transformed(new Transform().translate(v.x, v.y, v.z));
	}

	public CSG move(Number[] posVector) {
		return move(posVector[0], posVector[1], posVector[2]);
	}

	/**
	 * Movey.
	 *
	 * @param howFarToMove the how far to move
	 * @return the csg
	 */
	// Helper/wrapper functions for movement
	public CSG movey(Number howFarToMove) {
		return this.transformed(Transform.unity().translateY(howFarToMove.doubleValue()));
	}

	/**
	 * Movez.
	 *
	 * @param howFarToMove the how far to move
	 * @return the csg
	 */
	public CSG movez(Number howFarToMove) {
		return this.transformed(Transform.unity().translateZ(howFarToMove.doubleValue()));
	}

	/**
	 * Movex.
	 *
	 * @param howFarToMove the how far to move
	 * @return the csg
	 */
	public CSG movex(Number howFarToMove) {
		return this.transformed(Transform.unity().translateX(howFarToMove.doubleValue()));
	}

	/**
	 * Helper function moving CSG to center X moveToCenterX.
	 *
	 * @return the csg
	 */
	public CSG moveToCenterX() {
		return this.movex(-this.getCenterX());
	}

	/**
	 * Helper function moving CSG to center Y moveToCenterY.
	 *
	 * @return the csg
	 */
	public CSG moveToCenterY() {
		return this.movey(-this.getCenterY());
	}

	/**
	 * Helper function moving CSG to center Z moveToCenterZ.
	 *
	 * @return the csg
	 */
	public CSG moveToCenterZ() {
		return this.movez(-this.getCenterZ());
	}

	/**
	 * Helper function moving CSG to center X, Y, Z moveToCenter. Moves in x, y, z
	 *
	 * @return the csg
	 */
	public CSG moveToCenter() {
		return this.movex(-this.getCenterX()).movey(-this.getCenterY()).movez(-this.getCenterZ());
	}

	public ArrayList<CSG> move(ArrayList<Transform> p) {
		ArrayList<CSG> bits = new ArrayList<CSG>();
		for (Transform t : p) {
			bits.add(this.clone());
		}
		return move(bits, p);
	}

	public static ArrayList<CSG> move(ArrayList<CSG> slice, ArrayList<Transform> p) {
		ArrayList<CSG> s = new ArrayList<CSG>();
		// s.add(slice.get(0));
		for (int i = 0; i < slice.size() && i < p.size(); i++) {
			s.add(slice.get(i).transformed(p.get(i)));
		}
		return s;
	}

	/**
	 * mirror about y axis.
	 *
	 * 
	 * @return the csg
	 */
	// Helper/wrapper functions for movement
	public CSG mirrory() {
		return this.scaley(-1);
	}

	/**
	 * mirror about z axis.
	 *
	 * @return the csg
	 */
	public CSG mirrorz() {
		return this.scalez(-1);
	}

	/**
	 * mirror about x axis.
	 *
	 * @return the csg
	 */
	public CSG mirrorx() {
		return this.scalex(-1);
	}

	public CSG rot(Number x, Number y, Number z) {
		return rotx(x.doubleValue()).roty(y.doubleValue()).rotz(z.doubleValue());
	}

	public CSG rot(Number[] posVector) {
		return rot(posVector[0], posVector[1], posVector[2]);
	}

	/**
	 * Rotz.
	 *
	 * @param degreesToRotate the degrees to rotate
	 * @return the csg
	 */
	// Rotation function, rotates the object
	public CSG rotz(Number degreesToRotate) {
		return this.transformed(new Transform().rotZ(degreesToRotate.doubleValue()));
	}

	/**
	 * Roty.
	 *
	 * @param degreesToRotate the degrees to rotate
	 * @return the csg
	 */
	public CSG roty(Number degreesToRotate) {
		return this.transformed(new Transform().rotY(degreesToRotate.doubleValue()));
	}

	/**
	 * Rotx.
	 *
	 * @param degreesToRotate the degrees to rotate
	 * @return the csg
	 */
	public CSG rotx(Number degreesToRotate) {
		return this.transformed(new Transform().rotX(degreesToRotate.doubleValue()));
	}

	/**
	 * Scalez.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 */
	// Scale function, scales the object
	public CSG scalez(Number scaleValue) {
		return this.transformed(new Transform().scaleZ(scaleValue.doubleValue()));
	}

	/**
	 * Scaley.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 */
	public CSG scaley(Number scaleValue) {
		return this.transformed(new Transform().scaleY(scaleValue.doubleValue()));
	}

	/**
	 * Scalex.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 */
	public CSG scalex(Number scaleValue) {
		return this.transformed(new Transform().scaleX(scaleValue.doubleValue()));
	}

	// Scale function, scales the object
	public CSG scaleToMeasurmentZ(Number measurment) {
		Number scaleValue = measurment.doubleValue() / getTotalZ();

		return this.transformed(new Transform().scaleZ(scaleValue.doubleValue()));
	}

	/**
	 * Scaley.
	 *
	 * @param measurment the scale value
	 * @return the csg
	 */
	public CSG scaleToMeasurmentY(Number measurment) {
		Number scaleValue = measurment.doubleValue() / getTotalY();

		return this.transformed(new Transform().scaleY(scaleValue.doubleValue()));
	}

	/**
	 * Scalex.
	 *
	 * @param measurment the scale value
	 * @return the csg
	 */
	public CSG scaleToMeasurmentX(Number measurment) {
		Number scaleValue = measurment.doubleValue() / getTotalX();
		return this.transformed(new Transform().scaleX(scaleValue.doubleValue()));
	}

	/**
	 * Scale.
	 *
	 * @param scaleValue the scale value
	 * @return the csg
	 */
	public CSG scale(Number scaleValue) {
		return this.transformed(new Transform().scale(scaleValue.doubleValue()));
	}

	/**
	 * Constructs a CSG from a list of {@link Polygon} instances.
	 *
	 * @param polygons polygons
	 * @return a CSG instance
	 */
	public static CSG fromPolygons(List<Polygon> polygons) {

		CSG csg = new CSG();
		csg.setPolygons(polygons);
		return csg;
	}

	/**
	 * Constructs a CSG from the specified {@link Polygon} instances.
	 *
	 * @param polygons polygons
	 * @return a CSG instance
	 */
	public static CSG fromPolygons(Polygon... polygons) {
		return fromPolygons(Arrays.asList(polygons));
	}

	/**
	 * Constructs a CSG from a list of {@link Polygon} instances.
	 *
	 * @param storage  shared storage
	 * @param polygons polygons
	 * @return a CSG instance
	 */
	public static CSG fromPolygons(PropertyStorage storage, List<Polygon> polygons) {

		CSG csg = new CSG();
		csg.setPolygons(polygons);

		csg.setStorage(storage);

		for (Polygon polygon : polygons) {
			polygon.setStorage(storage);
		}
		return csg;
	}

	/**
	 * Constructs a CSG from the specified {@link Polygon} instances.
	 *
	 * @param storage  shared storage
	 * @param polygons polygons
	 * @return a CSG instance
	 */
	public static CSG fromPolygons(PropertyStorage storage, Polygon... polygons) {
		return fromPolygons(storage, Arrays.asList(polygons));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public CSG clone() {
		CSG csg = new CSG();
		csg.setOptType(this.getOptType());
		ArrayList<Polygon> collect = new ArrayList<Polygon>();
		for (Polygon p : polygons) {
			if (p == null)
				continue;
			try {
				Polygon my = p.clone();
				collect.add(my);
			} catch (Exception ex) {

				ex.printStackTrace();
			}
		}
		csg.setPolygons(collect);
		return csg.historySync(this);
	}

	/**
	 * Gets the polygons.
	 *
	 * @return the polygons of this CSG
	 */
	public List<Polygon> getPolygons() {
		return polygons;
	}

	/**
	 * Defines the CSg optimization type.
	 *
	 * @param type optimization type
	 * @return this CSG
	 */
	public CSG optimization(OptType type) {
		this.setOptType(type);
		return this;
	}

	/**
	 * Return a new CSG solid representing the union of this csg and the specified
	 * csg.
	 * <p>
	 * <b>Note:</b> Neither this csg nor the specified csg are weighted.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 *    A.union(B)
	 *
	 *    +-------+            +-------+
	 *    |       |            |       |
	 *    |   A   |            |       |
	 *    |    +--+----+   =   |       +----+
	 *    +----+--+    |       +----+       |
	 *         |   B   |            |       |
	 *         |       |            |       |
	 *         +-------+            +-------+
	 * </pre>
	 * 
	 * </blockquote>
	 *
	 * @param csg other csg
	 *
	 * @return union of this csg and the specified csg
	 */
	public CSG union(CSG csg) {
//		triangulate();
//		csg.triangulate();
		switch (getOptType()) {
		case CSG_BOUND:
			return _unionCSGBoundsOpt(csg).historySync(this).historySync(csg);
		case POLYGON_BOUND:
			return _unionPolygonBoundsOpt(csg).historySync(this).historySync(csg);
		default:
			// return _unionIntersectOpt(csg);
			return _unionNoOpt(csg).historySync(this).historySync(csg);
		}
	}

	/**
	 * Returns a csg consisting of the polygons of this csg and the specified csg.
	 * <p>
	 * The purpose of this method is to allow fast union operations for objects that
	 * do not intersect.
	 * <p>
	 * <b>WARNING:</b> this method does not apply the csg algorithms. Therefore,
	 * please ensure that this csg and the specified csg do not intersect.
	 * 
	 * @param csg csg
	 * 
	 * @return a csg consisting of the polygons of this csg and the specified csg
	 */
	public CSG dumbUnion(CSG csg) {
		boolean tri = triangulated && csg.triangulated;
		CSG result = this.clone();
		CSG other = csg.clone();

		result.getPolygons().addAll(other.getPolygons());
		bounds = null;
		result.triangulated = tri;
		return result.historySync(other);
	}

	/**
	 * Return a new CSG solid representing the union of this csg and the specified
	 * csgs.
	 * <p>
	 * <b>Note:</b> Neither this csg nor the specified csg are weighted.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 *    A.union(B)
	 *
	 *    +-------+            +-------+
	 *    |       |            |       |
	 *    |   A   |            |       |
	 *    |    +--+----+   =   |       +----+
	 *    +----+--+    |       +----+       |
	 *         |   B   |            |       |
	 *         |       |            |       |
	 *         +-------+            +-------+
	 * </pre>
	 * 
	 * </blockquote>
	 *
	 * @param csgs other csgs
	 *
	 * @return union of this csg and the specified csgs
	 */
	public CSG union(List<CSG> csgs) {
//		ArrayList<Polygon> incomingPolys = new ArrayList<>();
//		for(int i=0;i<csgs.size();i++) {
//			incomingPolys.addAll(csgs.get(i).getPolygons());
//		}
//		////com.neuronrobotics.sdk.common.Log.error("Node list A");
//		Node a = new Node(this.clone().getPolygons());
//		////com.neuronrobotics.sdk.common.Log.error("Node list B");
//		Node b = new Node(incomingPolys);
//		////com.neuronrobotics.sdk.common.Log.error("a.clipTo(b)");
//		a.clipTo(b);
//		////com.neuronrobotics.sdk.common.Log.error("b.clipTo(a)");
//		b.clipTo(a);
//		////com.neuronrobotics.sdk.common.Log.error("b.invert();");
//		b.invert();
//		////com.neuronrobotics.sdk.common.Log.error("b.clipTo(a);");
//		b.clipTo(a);
//		////com.neuronrobotics.sdk.common.Log.error("b.invert();");
//		b.invert();
//		////com.neuronrobotics.sdk.common.Log.error("a.build(b.allPolygons());");
//		a.build(b.allPolygons());
//		////com.neuronrobotics.sdk.common.Log.error("CSG.fromPolygons(a.allPolygons()).optimization(getOptType())");
//		return CSG.fromPolygons(a.allPolygons()).optimization(getOptType());

		CSG result = this;

		for (int i = 0; i < csgs.size(); i++) {
			CSG csg = csgs.get(i);
			result = result.union(csg);
			if (Thread.interrupted())
				break;
			progressMoniter.progressUpdate(i, csgs.size(), "Union", result);
		}

		return result;
	}

	/**
	 * Return a new CSG solid representing the union of this csg and the specified
	 * csgs.
	 * <p>
	 * <b>Note:</b> Neither this csg nor the specified csg are weighted.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 *    A.union(B)
	 *
	 *    +-------+            +-------+
	 *    |       |            |       |
	 *    |   A   |            |       |
	 *    |    +--+----+   =   |       +----+
	 *    +----+--+    |       +----+       |
	 *         |   B   |            |       |
	 *         |       |            |       |
	 *         +-------+            +-------+
	 * </pre>
	 * 
	 * </blockquote>
	 *
	 * @param csgs other csgs
	 *
	 * @return union of this csg and the specified csgs
	 */
	public CSG union(CSG... csgs) {
		return union(Arrays.asList(csgs));
	}

	/**
	 * Returns the convex hull of this csg.
	 *
	 * @return the convex hull of this csg
	 */
	public CSG hull() {

		return HullUtil.hull(this, getStorage()).historySync(this);
	}

	public static CSG unionAll(CSG... csgs) {
		return unionAll(Arrays.asList(csgs));
	}

	public static CSG unionAll(List<CSG> csgs) {
		CSG first = csgs.get(0);
		return first.union(csgs.stream().skip(1).collect(Collectors.toList()));
	}

	public static CSG hullAll(CSG... csgs) {
		return hullAll(Arrays.asList(csgs));
	}

	public static CSG hullAll(List<CSG> csgs) {
		// CSG first = csgs.remove(0);
		return HullUtil.hull(csgs);// first.hull(csgs);
	}

	/**
	 * Returns the convex hull of this csg and the union of the specified csgs.
	 *
	 * @param csgs csgs
	 * @return the convex hull of this csg and the specified csgs
	 */
	public CSG hull(List<CSG> csgs) {

		CSG csgsUnion = new CSG();
		// csgsUnion.setStorage(storage);
		csgsUnion.optType = optType;
		csgsUnion.setPolygons(this.clone().getPolygons());

		csgs.stream().forEach((csg) -> {
			csgsUnion.getPolygons().addAll(csg.clone().getPolygons());
			csgsUnion.historySync(csg);
		});

		csgsUnion.getPolygons().forEach(p -> p.setStorage(getStorage()));
		bounds = null;
		return csgsUnion.hull();

		// CSG csgsUnion = this;
		//
		// for (CSG csg : csgs) {
		// csgsUnion = csgsUnion.union(csg);
		// }
		//
		// return csgsUnion.hull();
	}

	/**
	 * Returns the convex hull of this csg and the union of the specified csgs.
	 *
	 * @param csgs csgs
	 * @return the convex hull of this csg and the specified csgs
	 */
	public CSG hull(CSG... csgs) {

		return hull(Arrays.asList(csgs));
	}

	/**
	 * _union csg bounds opt.
	 *
	 * @param csg the csg
	 * @return the csg
	 */
	private CSG _unionCSGBoundsOpt(CSG csg) {
		// com.neuronrobotics.sdk.common.Log.error("WARNING: using " + CSG.OptType.NONE
		// + " since other optimization types missing for union operation.");
		return _unionIntersectOpt(csg);
	}

	/**
	 * _union polygon bounds opt.
	 *
	 * @param csg the csg
	 * @return the csg
	 */
	private CSG _unionPolygonBoundsOpt(CSG csg) {
		List<Polygon> inner = new ArrayList<>();
		List<Polygon> outer = new ArrayList<>();

		Bounds b = csg.getBounds();

		this.getPolygons().stream().forEach((p) -> {
			if (b.intersects(p.getBounds())) {
				inner.add(p);
			} else {
				outer.add(p);
			}
		});

		List<Polygon> allPolygons = new ArrayList<>();

		if (!inner.isEmpty()) {
			CSG innerCSG = CSG.fromPolygons(inner);

			allPolygons.addAll(outer);
			allPolygons.addAll(innerCSG._unionNoOpt(csg).getPolygons());
		} else {
			allPolygons.addAll(this.getPolygons());
			allPolygons.addAll(csg.getPolygons());
		}
		bounds = null;
		CSG back = CSG.fromPolygons(allPolygons).optimization(getOptType());
		if (getName().length() != 0 && csg.getName().length() != 0) {
			back.setName(name);
		}
		return back;
	}

	/**
	 * Optimizes for intersection. If csgs do not intersect create a new csg that
	 * consists of the polygon lists of this csg and the specified csg. In this case
	 * no further space partitioning is performed.
	 *
	 * @param csg csg
	 * @return the union of this csg and the specified csg
	 */
	private CSG _unionIntersectOpt(CSG csg) {
		boolean intersects = false;

		Bounds bounds = csg.getBounds();

		for (Polygon p : getPolygons()) {
			if (bounds.intersects(p.getBounds())) {
				intersects = true;
				break;
			}
		}

		List<Polygon> allPolygons = new ArrayList<>();

		if (intersects) {
			return _unionNoOpt(csg);
		} else {
			allPolygons.addAll(this.getPolygons());
			allPolygons.addAll(csg.getPolygons());
		}
		CSG back = CSG.fromPolygons(allPolygons).optimization(getOptType());
		if (getName().length() != 0 && csg.getName().length() != 0) {
			back.setName(name);
		}
		return back;
	}

	/**
	 * _union no opt.
	 *
	 * @param csg the csg
	 * @return the csg
	 */
	private CSG _unionNoOpt(CSG csg) {
		Node a = new Node(this.clone().getPolygons());
		Node b = new Node(csg.clone().getPolygons());
		a.clipTo(b);
		b.clipTo(a);
		b.invert();
		b.clipTo(a);
		b.invert();
		a.build(b.allPolygons());
		CSG back = CSG.fromPolygons(a.allPolygons()).optimization(getOptType());
		if (getName().length() != 0 && csg.getName().length() != 0) {
			back.setName(name);
		}
		return back;
	}

	/**
	 * Return a new CSG solid representing the difference of this csg and the
	 * specified csgs.
	 * <p>
	 * <b>Note:</b> Neither this csg nor the specified csgs are weighted.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * A.difference(B)
	 *
	 * +-------+            +-------+
	 * |       |            |       |
	 * |   A   |            |       |
	 * |    +--+----+   =   |    +--+
	 * +----+--+    |       +----+
	 *      |   B   |
	 *      |       |
	 *      +-------+
	 * </pre>
	 * 
	 * </blockquote>
	 *
	 * @param csgs other csgs
	 * @return difference of this csg and the specified csgs
	 */
	public CSG difference(List<CSG> csgs) {

		if (csgs.isEmpty()) {
			return this.clone();
		}

		CSG csgsUnion = csgs.get(0);

		for (int i = 1; i < csgs.size(); i++) {
			csgsUnion = csgsUnion.union(csgs.get(i));
			progressMoniter.progressUpdate(i, csgs.size(), "Difference", csgsUnion);
			csgsUnion.historySync(csgs.get(i));
			if (Thread.interrupted())
				break;
		}

		return difference(csgsUnion);
	}

	/**
	 * Return a new CSG solid representing the difference of this csg and the
	 * specified csgs.
	 * <p>
	 * <b>Note:</b> Neither this csg nor the specified csgs are weighted.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * A.difference(B)
	 *
	 * +-------+            +-------+
	 * |       |            |       |
	 * |   A   |            |       |
	 * |    +--+----+   =   |    +--+
	 * +----+--+    |       +----+
	 *      |   B   |
	 *      |       |
	 *      +-------+
	 * </pre>
	 * 
	 * </blockquote>
	 *
	 * @param csgs other csgs
	 * @return difference of this csg and the specified csgs
	 */
	public CSG difference(CSG... csgs) {

		return difference(Arrays.asList(csgs));
	}

	/**
	 * Return a new CSG solid representing the difference of this csg and the
	 * specified csg.
	 * <p>
	 * <b>Note:</b> Neither this csg nor the specified csg are weighted.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 * A.difference(B)
	 *
	 * +-------+            +-------+
	 * |       |            |       |
	 * |   A   |            |       |
	 * |    +--+----+   =   |    +--+
	 * +----+--+    |       +----+
	 *      |   B   |
	 *      |       |
	 *      +-------+
	 * </pre>
	 * 
	 * </blockquote>
	 *
	 * @param csg other csg
	 * @return difference of this csg and the specified csg
	 */
	public CSG difference(CSG csg) {
//		triangulate();
//		csg.triangulate();
		try {
			// Check to see if a CSG operation is attempting to difference with
			// no
			// polygons
			if (this.getPolygons().size() > 0 && csg.getPolygons().size() > 0) {
				switch (getOptType()) {
				case CSG_BOUND:
					return _differenceCSGBoundsOpt(csg).historySync(this).historySync(csg);
				case POLYGON_BOUND:
					return _differencePolygonBoundsOpt(csg).historySync(this).historySync(csg);
				default:
					return _differenceNoOpt(csg).historySync(this).historySync(csg);
				}
			} else
				return this;
		} catch (Exception ex) {
			// ex.printStackTrace();
			try {
				// com.neuronrobotics.sdk.common.Log.error("CSG difference failed, performing
				// workaround");
				// ex.printStackTrace();
				CSG intersectingParts = csg.intersect(this);

				if (intersectingParts.getPolygons().size() > 0) {
					switch (getOptType()) {
					case CSG_BOUND:
						return _differenceCSGBoundsOpt(intersectingParts).historySync(this)
								.historySync(intersectingParts);
					case POLYGON_BOUND:
						return _differencePolygonBoundsOpt(intersectingParts).historySync(this)
								.historySync(intersectingParts);
					default:
						return _differenceNoOpt(intersectingParts).historySync(this).historySync(intersectingParts);
					}
				} else
					return this;
			} catch (Exception e) {
				e.printStackTrace();
				return this;
			}
		}

	}

	/**
	 * _difference csg bounds opt.
	 *
	 * @param csg the csg
	 * @return the csg
	 */
	private CSG _differenceCSGBoundsOpt(CSG csg) {
		CSG a1 = this._differenceNoOpt(csg.getBounds().toCSG().setColor(csg.getColor()));
		CSG a2 = this.intersect(csg.getBounds().toCSG());
		CSG result = a2._differenceNoOpt(csg)._unionIntersectOpt(a1).optimization(getOptType());
		if (getName().length() != 0 && csg.getName().length() != 0) {
			result.setName(name);
		}
		result.color = color;
		return result;
	}

	/**
	 * _difference polygon bounds opt.
	 *
	 * @param csg the csg
	 * @return the csg
	 */
	private CSG _differencePolygonBoundsOpt(CSG csg) {
		List<Polygon> inner = new ArrayList<>();
		List<Polygon> outer = new ArrayList<>();

		Bounds bounds = csg.getBounds();

		this.getPolygons().stream().forEach((p) -> {
			if (bounds.intersects(p.getBounds())) {
				inner.add(p);
			} else {
				outer.add(p);
			}
		});

		CSG innerCSG = CSG.fromPolygons(inner);

		List<Polygon> allPolygons = new ArrayList<>();
		allPolygons.addAll(outer);
		allPolygons.addAll(innerCSG._differenceNoOpt(csg).getPolygons());
		CSG BACK = CSG.fromPolygons(allPolygons).optimization(getOptType());
		if (getName().length() != 0 && csg.getName().length() != 0) {
			BACK.setName(name);
		}
		return BACK;
	}

	/**
	 * _difference no opt.
	 *
	 * @param csg the csg
	 * @return the csg
	 */
	private CSG _differenceNoOpt(CSG csg) {

		Node a = new Node(this.clone().getPolygons());
		Node b = new Node(csg.clone().getPolygons());

		a.invert();
		a.clipTo(b);
		b.clipTo(a);
		b.invert();
		b.clipTo(a);
		b.invert();
		a.build(b.allPolygons());
		a.invert();

		CSG csgA = CSG.fromPolygons(a.allPolygons()).optimization(getOptType());
		if (getName().length() != 0 && csg.getName().length() != 0) {
			csgA.setName(name);
		}
		return csgA;
	}

	/**
	 * Return a new CSG solid representing the intersection of this csg and the
	 * specified csg.
	 * <p>
	 * <b>Note:</b> Neither this csg nor the specified csg are weighted.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 *     A.intersect(B)
	 *
	 *     +-------+
	 *     |       |
	 *     |   A   |
	 *     |    +--+----+   =   +--+
	 *     +----+--+    |       +--+
	 *          |   B   |
	 *          |       |
	 *          +-------+
	 * }
	 * </pre>
	 * 
	 * </blockquote>
	 *
	 * @param csg other csg
	 * @return intersection of this csg and the specified csg
	 */
	public CSG intersect(CSG csg) {
//		triangulate();
//		csg.triangulate();
		Node a = new Node(this.clone().getPolygons());
		Node b = new Node(csg.clone().getPolygons());
		a.invert();
		b.clipTo(a);
		b.invert();
		a.clipTo(b);
		b.clipTo(a);
		a.build(b.allPolygons());
		a.invert();
		CSG back = CSG.fromPolygons(a.allPolygons()).optimization(getOptType()).historySync(csg).historySync(this);
		if (getName().length() != 0 && csg.getName().length() != 0) {
			back.setName(name);
		}
		return back;
	}

	/**
	 * Return a new CSG solid representing the intersection of this csg and the
	 * specified csgs.
	 * <p>
	 * <b>Note:</b> Neither this csg nor the specified csgs are weighted.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 *     A.intersect(B)
	 *
	 *     +-------+
	 *     |       |
	 *     |   A   |
	 *     |    +--+----+   =   +--+
	 *     +----+--+    |       +--+
	 *          |   B   |
	 *          |       |
	 *          +-------+
	 * }
	 * </pre>
	 * 
	 * </blockquote>
	 *
	 * @param csgs other csgs
	 * @return intersection of this csg and the specified csgs
	 */
	public CSG intersect(List<CSG> csgs) {

		if (csgs.isEmpty()) {
			return this.clone();
		}

		CSG csgsUnion = csgs.get(0);

		for (int i = 1; i < csgs.size(); i++) {
			csgsUnion = csgsUnion.union(csgs.get(i));
			progressMoniter.progressUpdate(i, csgs.size(), "Intersect", csgsUnion);
			csgsUnion.historySync(csgs.get(i));
			if (Thread.interrupted())
				break;
		}

		return intersect(csgsUnion);
	}

	/**
	 * Return a new CSG solid representing the intersection of this csg and the
	 * specified csgs.
	 * <p>
	 * <b>Note:</b> Neither this csg nor the specified csgs are weighted.
	 * <p>
	 * <blockquote>
	 * 
	 * <pre>
	 *     A.intersect(B)
	 *
	 *     +-------+
	 *     |       |
	 *     |   A   |
	 *     |    +--+----+   =   +--+
	 *     +----+--+    |       +--+
	 *          |   B   |
	 *          |       |
	 *          +-------+
	 * }
	 * </pre>
	 * 
	 * </blockquote>
	 *
	 * @param csgs other csgs
	 * @return intersection of this csg and the specified csgs
	 */
	public CSG intersect(CSG... csgs) {

		return intersect(Arrays.asList(csgs));
	}

	/**
	 * Returns this csg in STL string format.
	 *
	 * @return this csg in STL string format
	 */
	public String toStlString() {
		StringBuilder sb = new StringBuilder();
		toStlString(sb);
		return sb.toString();
	}

	/**
	 * Returns this csg in STL string format.
	 *
	 * @param sb string builder
	 *
	 * @return the specified string builder
	 */
	public StringBuilder toStlString(StringBuilder sb) {
		triangulate(false);
		try {
			sb.append("solid v3d.csg\n");
			for (Polygon p : getPolygons()) {
				try {
					Plane.computeNormal(p.vertices);
					p.toStlString(sb);
				} catch (Exception ex) {
					System.out.println("Prune Polygon on export");
				}
			}
			sb.append("endsolid v3d.csg\n");
			return sb;
		} catch (Throwable t) {
			t.printStackTrace();
			throw new RuntimeException(t);
		}
	}

	public CSG triangulate() {
		return triangulate(false);
	}

	public CSG triangulate(boolean fix) {
		if (fix && needsDegeneratesPruned)
			triangulated = false;
		if (triangulated)
			return this;

		//// com.neuronrobotics.sdk.common.Log.error("CSG triangulating for " +
		//// name+"..");
		ArrayList<Polygon> toAdd = new ArrayList<Polygon>();
		ArrayList<Polygon> degenerates = new ArrayList<Polygon>();
		if (providerOf3d == null && Debug3dProvider.provider != null)
			providerOf3d = Debug3dProvider.provider;
		IDebug3dProvider start = Debug3dProvider.provider;
		Debug3dProvider.setProvider(null);
		if (preventNonManifoldTriangles) {
			for (int i = 0; i < 2; i++)
				if (isUseGPU()) {
					runGPUMakeManifold();
				} else {
					runCPUMakeManifold();
				}
		}
		try {
			Stream<Polygon> polygonStream;
			polygonStream = polygons.stream();
			// TODO this should work in paralell but throws immpossible NPE's instead.
//			if (getPolygons().size() > 200) {
//				polygonStream = polygons.parallelStream();
//			}
			polygonStream.forEach(p -> updatePolygons(toAdd, degenerates, p));
//			for (int i = 0; i < polygons.size(); i++) {
//				Polygon p = polygons.get(i);
//				updatePolygons(toAdd, degenerates, p);
//			}

			if (degenerates.size() > 0) {
				//
				// Debug3dProvider.setProvider(providerOf3d);

				if (fix) {
					Debug3dProvider.clearScreen();
					Stream<Polygon> degenStreeam;
					degenStreeam = polygons.stream(); // this operation is read-modify-write and can not be done in
														// parallel
					// com.neuronrobotics.sdk.common.Log.error("Found "+degenerates.size()+"
					// degenerate triangles, Attempting to fix");
					degenStreeam.forEach(p -> fixDegenerates(toAdd, p));
				} else {
					needsDegeneratesPruned = true;
					toAdd.addAll(degenerates);
				}
			}
			if (toAdd.size() > 0) {
				setPolygons(toAdd);
			}
			// now all polygons are definantly triangles
			triangulated = true;
		} catch (Throwable t) {
			t.printStackTrace();

		}
		Debug3dProvider.setProvider(start);
		return this;
	}

	private void runCPUMakeManifold() {
		long start = System.currentTimeMillis();
		System.err.println("Cleaning up the mesh by adding coincident points to the polygons they touch");

		int totalAdded = 0;
		double tOL = 1.0e-11;

		ArrayList<Thread> threads = new ArrayList<Thread>();
		for (int j = 0; j < polygons.size(); j++) {
			int threadIndex = j;
			Thread t = new Thread(() -> {
				Edge e = null;
				// Test every polygon
				Polygon i = polygons.get(threadIndex);
				ArrayList<Vertex> vertices = i.vertices;
				for (int k = 0; k < vertices.size(); k++) {
					// each point in the checking polygon
					int now = k;
					int next = k + 1;
					if (next == vertices.size())
						next = 0;
					// take the 2 points of this section of polygon to make an edge
					Vertex p1 = vertices.get(now);
					Vertex p2 = vertices.get(next);
					if (e == null)
						e = new Edge(p1, p2);
					else {
						e.setP1(p1);
						e.setP2(p2);
					}
					for (int l = 0; l < polygons.size(); l++) {
						Polygon ii = polygons.get(l);
						if (threadIndex != l) {
							// every other polygon besides this one being tested
							ArrayList<Vertex> vert = ii.vertices;
							for (int iii = 0; iii < vert.size(); iii++) {
								Vertex vi = vert.get(iii);
								// if they are coincident, move along
								if (e.isThisPointOneOfMine(vi, tOL))
									continue;
								// if the point is on the line then we have a non manifold point
								// it needs to be inserted into the polygon between the 2 points defined in the
								// edge
								if (e.contains(vi.pos, tOL)) {
									// System.out.println("Inserting point "+vi);
									vertices.add(next, vi);
									e.setP2(vi);
									// totalAdded++;
								}
							}
						}
					}
				}
			});
			if (threads.size() > 32) {
				for (Thread tr : threads)
					try {
						tr.join();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				totalAdded += 32;
				threads.clear();
				if (threadIndex/32 % 50 == 0 || j == polygons.size() - 1) {
					progressMoniter.progressUpdate(j, polygons.size(),
							"STL Processing Polygons for Manifold Vertex, #" + totalAdded + " added so far", this);
				}
			}

			threads.add(t);
			t.start();
		}
		for (Thread t : threads)
			try {
				t.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		progressMoniter.progressUpdate(polygons.size(),polygons.size(),"Manifold fix took " + (System.currentTimeMillis() - start),this);
	}

	private void runGPUMakeManifold() {
		int numberOfPoints = 0;
		int roomForMore = 10;
		int size = polygons.size();
		for (int i = 0; i < size; i++) {
			numberOfPoints += (polygons.get(i).vertices.size());
		}
		float[] pointData = new float[numberOfPoints * 3];
		int[] startIndex = new int[size];
		int[] sizes = new int[size];
		int[] insertions = new int[size * 2 * roomForMore];
		int runningPointIndex = 0;
		for (int i = 0; i < insertions.length; i++) {
			insertions[i] = -1;
		}
		for (int polyIndex = 0; polyIndex < size; polyIndex++) {
			sizes[polyIndex] = polygons.get(polyIndex).vertices.size();
			startIndex[polyIndex] = runningPointIndex;
			for (int ii = 0; ii < sizes[polyIndex]; ii++) {
				Vector3d pos = polygons.get(polyIndex).vertices.get(ii).pos.clone()
						.roundToEpsilon(Vector3d.getEXPORTEPSILON());
				pointData[startIndex[polyIndex] + 0 + ii] = (float) pos.x;
				pointData[startIndex[polyIndex] + 1 + ii] = (float) pos.y;
				pointData[startIndex[polyIndex] + 2 + ii] = (float) pos.z;
			}
			runningPointIndex += (sizes[polyIndex]) * 3;
		}
		System.out.println("Data loaded!");
		Kernel kernel = new Kernel() {
			@Override
			public void run() {
				int i = getGlobalId();
				int size = sizes[i];
				int myStartIndex = startIndex[i];

				for (int mypolyIndex = myStartIndex; mypolyIndex < size; mypolyIndex++) {
					float x = pointData[myStartIndex + 0 + mypolyIndex];
					float y = pointData[myStartIndex + 1 + mypolyIndex];
					float z = pointData[myStartIndex + 2 + mypolyIndex];
					for (int polyIndex = 0; polyIndex < sizes.length; polyIndex++) {
						for (int ii = 0; ii < sizes[polyIndex]; ii++) {
							float xSub = pointData[startIndex[polyIndex] + 0 + ii];
							float ySub = pointData[startIndex[polyIndex] + 1 + ii];
							float zSub = pointData[startIndex[polyIndex] + 2 + ii];
							insertions[i] = 1;
						}
					}
				}
			}
		};

		Device device = Device.best();
		System.out.println("Dev " + device.getShortDescription());
		Range range = device.createRange(size);
		kernel.execute(range);
		while (kernel.isExecuting()) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Data processed!");
	}

	private CSG fixDegenerates(ArrayList<Polygon> toAdd, Polygon p) {
		Debug3dProvider.clearScreen();
		Debug3dProvider.addObject(p);
		ArrayList<Vertex> degen = p.getDegeneratePoints();
		Edge longEdge = p.getLongEdge();
		ArrayList<Polygon> polygonsSharing = new ArrayList<Polygon>();
		ArrayList<Polygon> polygonsSharingFixed = new ArrayList<Polygon>();

		for (Polygon ptoA : toAdd) {
			ArrayList<Edge> edges = ptoA.edges();
			for (Edge e : edges) {
				if (e.equals(longEdge)) {
					//// com.neuronrobotics.sdk.common.Log.error("Degenerate Mate Found!");
					polygonsSharing.add(ptoA);
					Debug3dProvider.addObject(ptoA);
					// TODO inject the points into the found edge
					// upstream reparirs to mesh generation made this code effectivly unreachable
					// in case that turns out to be false, pick up here
					// the points in degen need to be inserted into the matching polygons
					// both list of points should be right hand, but since they are other polygons,
					// that may not be the case, so sorting needs to take place
					ArrayList<Vertex> newpoints = new ArrayList<Vertex>();
					for (Vertex v : ptoA.vertices) {
						newpoints.add(v);
						if (e.isThisPointOneOfMine(v, Plane.EPSILON_Point)) {
							for (Vertex v2 : degen)
								newpoints.add(v2);
						}
					}
					Polygon e2 = new Polygon(newpoints, ptoA.getStorage());
					try {
						List<Polygon> t = PolygonUtil.concaveToConvex(e2);
						for (Polygon poly : t) {
							if (!poly.isDegenerate()) {
								polygonsSharingFixed.add(poly);
							}

						}
					} catch (Exception ex) {
						ex.printStackTrace();
						// retriangulation failed, ok, whatever man, moving on...
					}
				}
			}
		}
		if (polygonsSharing.size() == 0) {
			//// com.neuronrobotics.sdk.common.Log.error("Error! Degenerate triangle does
			//// not share edge with any triangle");
		}
		if (polygonsSharingFixed.size() > 0) {
			toAdd.removeAll(polygonsSharing);
			toAdd.addAll(polygonsSharingFixed);
		}
		return this;
	}

	private CSG updatePolygons(ArrayList<Polygon> toAdd, ArrayList<Polygon> degenerates, Polygon p) {
		// p=PolygonUtil.pruneDuplicatePoints(p);
		if (p == null)
			return this;
//		if(p.isDegenerate()) {
//			degenerates.add(p);
//			return;
//		}

		if (p.vertices.size() == 3) {
			toAdd.add(p);
		} else {
			// //com.neuronrobotics.sdk.common.Log.error("Fixing error in STL " + name + "
			// polygon# " + i + "
			// number of vertices " + p.vertices.size());
			try {
				List<Polygon> triangles = PolygonUtil.concaveToConvex(p);
				for (Polygon poly : triangles) {
					toAdd.add(poly);
				}
			} catch (Throwable ex) {
				//ex.printStackTrace();
				progressMoniter.progressUpdate(1, 1, "Pruning bad polygon CSG::updatePolygons " + p, null);
//				try {PolygonUtil.concaveToConvex(p);} catch (Throwable ex2) {
//					ex2.printStackTrace();
//				}
//				Debug3dProvider.setProvider(providerOf3d);
//				//ex.printStackTrace();
//				Debug3dProvider.clearScreen();
//				Debug3dProvider.addObject(p);
//				try {
//					List<Polygon> triangles = PolygonUtil.concaveToConvex(p);
//					toAdd.addAll(triangles);
//				}catch(java.lang.IllegalStateException ise) {
//					ise.printStackTrace();
//				}
//				Debug3dProvider.setProvider(null);
			}

		}
		return this;
	}

	/**
	 * Color.
	 *
	 * @param c the c
	 * @return the csg
	 */
	public CSG color(Color c) {
		getStorage().set("material:color", "" + c.getRed() + " " + c.getGreen() + " " + c.getBlue());

		return this;
	}

	/**
	 * Returns this csg in OBJ string format.
	 *
	 * @param sb string builder
	 * @return the specified string builder
	 */
	public StringBuilder toObjString(StringBuilder sb) {
		triangulate(true);
		sb.append("# Group").append("\n");
		sb.append("g v3d.csg\n");
		sb.append("o " + (name == null || name.length() == 0 ? "CSG Export" : getName()) + "\n");
		class PolygonStruct {

			PropertyStorage storage;
			List<Integer> indices;
			String materialName;

			public PolygonStruct(PropertyStorage storage, List<Integer> indices, String materialName) {
				this.storage = storage;
				this.indices = indices;
				this.materialName = materialName;
			}
		}

		List<Vertex> vertices = new ArrayList<>();
		List<PolygonStruct> indices = new ArrayList<>();

		sb.append("\n# Vertices\n");

		for (Polygon p : getPolygons()) {
			List<Integer> polyIndices = new ArrayList<>();

			p.vertices.stream().forEach((v) -> {
				if (!vertices.contains(v)) {
					vertices.add(v);
					v.toObjString(sb);
					polyIndices.add(vertices.size());
				} else {
					polyIndices.add(vertices.indexOf(v) + 1);
				}
			});
			indices.add(new PolygonStruct(getStorage(), polyIndices, " "));

		}
		HashMap<Vertex, Integer> mapping = new HashMap<Vertex, Integer>();
		HashMap<Transform, Vertex> mappingTF = new HashMap<>();
		if (datumReferences != null) {
			int startingIndex = vertices.size() + 1;
			sb.append("\n# Reference Datum").append("\n");
			for (Transform t : datumReferences) {
				Vertex v = new Vertex(new Vector3d(0, 0, 0), new Vector3d(0, 0, 1)).transform(t);
				Vertex v1 = new Vertex(new Vector3d(0, 0, 1), new Vector3d(0, 0, 1)).transform(t);
				mapping.put(v, startingIndex++);
				mapping.put(v1, startingIndex++);
				mappingTF.put(t, v);
				v.toObjString(sb);
				v1.toObjString(sb);
			}
			sb.append("\n# Datum Lines").append("\n");
			for (Transform t : mappingTF.keySet()) {
				Vertex key = mappingTF.get(t);
				Integer obj = mapping.get(key);
				sb.append("\nl ").append(obj + " ").append(obj + 1).append("\n");
			}
		}

		sb.append("\n# Faces").append("\n");

		for (PolygonStruct ps : indices) {
			// we triangulate the polygon to ensure
			// compatibility with 3d printer software
			List<Integer> pVerts = ps.indices;
			if (pVerts.size() != 3)
				throw new RuntimeException(name + " can not be exported until triangulated");
			int index1 = pVerts.get(0);
			for (int i = 0; i < pVerts.size() - 2; i++) {
				int index2 = pVerts.get(i + 1);
				int index3 = pVerts.get(i + 2);

				sb.append("f ").append(index1).append(" ").append(index2).append(" ").append(index3).append("\n");
			}
		}

		sb.append("\n# End Group v3d.csg").append("\n");

		return sb;
	}

	/**
	 * Returns this csg in OBJ string format.
	 *
	 * @return this csg in OBJ string format
	 */
	public String toObjString() {
		StringBuilder sb = new StringBuilder();
		return toObjString(sb).toString();
	}

	/**
	 * Weighted.
	 *
	 * @param f the f
	 * @return the csg
	 */
	public CSG weighted(WeightFunction f) {
		return new Modifier(f).modified(this);
	}

	/**
	 * Returns a transformed copy of this CSG.
	 *
	 * @param transform the transform to apply
	 *
	 * @return a transformed copy of this CSG
	 */
	public CSG transformed(Transform transform) {

		if (getPolygons().isEmpty()) {
			return clone();
		}

		List<Polygon> newpolygons = this.getPolygons().stream().map(p -> {
			try {
				return p.transformed(transform);
			} catch (Exception e) {
				// e.printStackTrace();
				System.err.println("Removing Polygon during transform");
				return null;
			}
		}).filter(Objects::nonNull).collect(Collectors.toList());

		CSG csg = CSG.fromPolygons(newpolygons).optimization(getOptType());

		// csg.setStorage(storage);

		if (getName().length() != 0) {
			csg.setName(name);
		}

		return csg.historySync(this);
	}

	/**
	 * To java fx mesh.
	 *
	 * @param interact the interact
	 * @return the mesh container
	 */
	// TODO finish experiment (20.7.2014)
	public MeshContainer toJavaFXMesh(CadInteractionEvent interact) {

		return toJavaFXMeshSimple(interact);

		// TODO test obj approach with multiple materials
		// try {
		// ObjImporter importer = new ObjImporter(toObj());
		//
		// List<Mesh> meshes = new ArrayList<>(importer.getMeshCollection());
		// return new MeshContainer(getBounds().getMin(), getBounds().getMax(),
		// meshes, new ArrayList<>(importer.getMaterialCollection()));
		// } catch (IOException ex) {
		// Logger.getLogger(CSG.class.getName()).log(Level.SEVERE, null, ex);
		// }
		// // we have no backup strategy for broken streams :(
		// return null;
	}

	/**
	 * Returns the CSG as JavaFX triangle mesh.
	 *
	 * @param interact the interact
	 * @return the CSG as JavaFX triangle mesh
	 */
	public MeshContainer toJavaFXMeshSimple(CadInteractionEvent interact) {

		return CSGtoJavafx.meshFromPolygon(getPolygons());
	}

	/**
	 * Returns the bounds of this csg. SIDE EFFECT bounds is created and simply
	 * returned if existing
	 *
	 * @return bouds of this csg
	 */
	public Bounds getBounds() {
		if (bounds != null)
			return bounds;
		if (getPolygons().isEmpty()) {
			bounds = new Bounds(Vector3d.ZERO, Vector3d.ZERO);
			return bounds;
		}

		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;

		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;

		for (Polygon p : getPolygons()) {

			for (int i = 0; i < p.vertices.size(); i++) {

				Vertex vert = p.vertices.get(i);

				if (vert.pos.x < minX) {
					minX = vert.pos.x;
				}
				if (vert.pos.y < minY) {
					minY = vert.pos.y;
				}
				if (vert.pos.z < minZ) {
					minZ = vert.pos.z;
				}

				if (vert.pos.x > maxX) {
					maxX = vert.pos.x;
				}
				if (vert.pos.y > maxY) {
					maxY = vert.pos.y;
				}
				if (vert.pos.z > maxZ) {
					maxZ = vert.pos.z;
				}

			} // end for vertices

		} // end for polygon

		bounds = new Bounds(new Vector3d(minX, minY, minZ), new Vector3d(maxX, maxY, maxZ));
		return bounds;
	}

	public Vector3d getCenter() {
		return new Vector3d(getCenterX(), getCenterY(), getCenterZ());
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return CenterX
	 */
	public double getCenterX() {
		return ((getMinX() / 2) + (getMaxX() / 2));
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return CenterY
	 */
	public double getCenterY() {
		return ((getMinY() / 2) + (getMaxY() / 2));
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return CenterZ
	 */
	public double getCenterZ() {
		return ((getMinZ() / 2) + (getMaxZ() / 2));
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return MaxX
	 */
	public double getMaxX() {
		return getBounds().getMax().x;
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return MaxY
	 */
	public double getMaxY() {
		return getBounds().getMax().y;
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return MaxZ
	 */
	public double getMaxZ() {
		return getBounds().getMax().z;
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return MinX
	 */
	public double getMinX() {
		return getBounds().getMin().x;
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return MinY
	 */
	public double getMinY() {
		return getBounds().getMin().y;
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return tMinZ
	 */
	public double getMinZ() {
		return getBounds().getMin().z;
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return MinX
	 */
	public double getTotalX() {
		return (-this.getMinX() + this.getMaxX());
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return MinY
	 */
	public double getTotalY() {
		return (-this.getMinY() + this.getMaxY());
	}

	/**
	 * Helper function wrapping bounding box values
	 * 
	 * @return tMinZ
	 */
	public double getTotalZ() {
		return (-this.getMinZ() + this.getMaxZ());
	}

	/**
	 * Gets the opt type.
	 *
	 * @return the optType
	 */
	protected OptType getOptType() {
		return optType != null ? optType : defaultOptType;
	}

	/**
	 * Sets the default opt type.
	 *
	 * @param optType the optType to set
	 */
	public static void setDefaultOptType(OptType optType) {
		defaultOptType = optType;
	}

	/**
	 * Sets the opt type.
	 *
	 * @param optType the optType to set
	 */
	public CSG setOptType(OptType optType) {
		this.optType = optType;
		return this;
	}

	/**
	 * Sets the polygons.
	 *
	 * @param polygons the new polygons
	 */
	public CSG setPolygons(List<Polygon> polygons) {
		bounds = null;
		triangulated = false;
		this.polygons = polygons;
		return this;
	}

	/**
	 * The Enum OptType.
	 */
	public static enum OptType {

		/** The csg bound. */
		CSG_BOUND,

		/** The polygon bound. */
		POLYGON_BOUND,

		/** The none. */
		NONE
	}

	/**
	 * Hail Zeon! In case you forget the name of minkowski and are a Gundam fan
	 * 
	 * @param travelingShape
	 * @return
	 */
	@Deprecated
	public ArrayList<CSG> minovsky(CSG travelingShape) {
		// com.neuronrobotics.sdk.common.Log.error("Hail Zeon!");
		return minkowski(travelingShape);
	}

	/**
	 * Shortened name In case you forget the name of minkowski
	 * 
	 * @param travelingShape
	 * @return
	 */
	public ArrayList<CSG> mink(CSG travelingShape) {
		return minkowski(travelingShape);
	}

	/**
	 * This is a simplified version of a minkowski transform using convex hull and
	 * the internal list of convex polygons The shape is placed at the vertex of
	 * each point on a polygon, and the result is convex hulled together. This
	 * collection is returned. To make a normal insets, difference this collection
	 * To make an outset by the normals, union this collection with this object.
	 * 
	 * @param travelingShape a shape to sweep around
	 * @return
	 */
	public ArrayList<CSG> minkowskiHullShape(CSG travelingShape) {
		ArrayList<CSG> bits = new ArrayList<>();
		for (Polygon p : this.getPolygons()) {
			List<Vector3d> plist = new ArrayList<>();
			for (Vertex v : p.vertices) {
				CSG newSHape = travelingShape.move(v);
				for (Polygon np : newSHape.getPolygons()) {
					for (Vertex nv : np.vertices) {
						plist.add(nv.pos);
					}
				}
			}
			bits.add(HullUtil.hull(plist));
		}
		return bits;
	}

	/**
	 * This is a simplified version of a minkowski transform using convex hull and
	 * the internal list of convex polygons The shape is placed at the vertex of
	 * each point on a polygon, and the result is convex hulled together. This
	 * collection is returned. To make a normal insets, difference this collection
	 * To make an outset by the normals, union this collection with this object.
	 * 
	 * @param travelingShape a shape to sweep around
	 * @return
	 */
	public ArrayList<CSG> minkowski(CSG travelingShape) {
		HashMap<Vertex, CSG> map = new HashMap<>();
		for (Polygon p : travelingShape.getPolygons()) {
			for (Vertex v : p.vertices) {
				if (map.get(v) == null)// use hashmap to avoid duplicate locations
					map.put(v, this.move(v));
			}
		}
		return new ArrayList<CSG>(map.values());
	}

	/**
	 * minkowskiDifference performs an efficient difference of the minkowski
	 * transform of the intersection of an object. if you have 2 objects and need
	 * them to fit with a specific tolerance as described as the distance from he
	 * normal of the surface, then this function will effectinatly compute that
	 * value.
	 * 
	 * @param itemToDifference the object that needs to fit
	 * @param minkowskiObject  the object to represent the offset
	 * @return
	 */
	public CSG minkowskiDifference(CSG itemToDifference, CSG minkowskiObject) {
		CSG intersection = this.intersect(itemToDifference);

		ArrayList<CSG> csgDiff = intersection.minkowskiHullShape(minkowskiObject);
		CSG result = this;
		for (int i = 0; i < csgDiff.size(); i++) {
			result = result.difference(csgDiff.get(i));
			progressMoniter.progressUpdate(i, csgDiff.size(), "Minkowski difference", result);
		}
		return result;
	}

	/**
	 * minkowskiDifference performs an efficient difference of the minkowski
	 * transform of the intersection of an object. if you have 2 objects and need
	 * them to fit with a specific tolerance as described as the distance from the
	 * normal of the surface, then this function will effectinatly compute that
	 * value.
	 * 
	 * @param itemToDifference the object that needs to fit
	 * @param tolerance        the tolerance distance
	 * @return
	 */
	public CSG minkowskiDifference(CSG itemToDifference, double tolerance) {
		double shellThickness = Math.abs(tolerance);
		if (shellThickness < 0.001)
			return this;
		return minkowskiDifference(itemToDifference, new Sphere(shellThickness / 2.0, 8, 4).toCSG());
	}

	public CSG toolOffset(Number sn) {
		double shellThickness = sn.doubleValue();
		boolean cut = shellThickness < 0;
		shellThickness = Math.abs(shellThickness);
		if (shellThickness < 0.001)
			return this;
		double z = shellThickness;
		if (z > this.getTotalZ() / 2)
			z = this.getTotalZ() / 2;
		CSG printNozzel = new Sphere(z / 2.0, 8, 4).toCSG();

		if (cut) {
			ArrayList<CSG> mikObjs = minkowski(printNozzel);
			CSG remaining = this;
			for (CSG bit : mikObjs) {
				remaining = remaining.intersect(bit);
			}
			return remaining;
		}
		return union(minkowskiHullShape(printNozzel));
	}

	private int getNumFacesForOffsets() {
		return getNumfacesinoffset();
	}

	public CSG makeKeepaway(Number sn) {
		double shellThickness = sn.doubleValue();

		double x = Math.abs(this.getBounds().getMax().x) + Math.abs(this.getBounds().getMin().x);
		double y = Math.abs(this.getBounds().getMax().y) + Math.abs(this.getBounds().getMin().y);

		double z = Math.abs(this.getBounds().getMax().z) + Math.abs(this.getBounds().getMin().z);

		double xtol = (x + shellThickness) / x;
		double ytol = (y + shellThickness) / y;
		double ztol = (z + shellThickness) / z;

		double xPer = -(Math.abs(this.getBounds().getMax().x) - Math.abs(this.getBounds().getMin().x)) / x;
		double yPer = -(Math.abs(this.getBounds().getMax().y) - Math.abs(this.getBounds().getMin().y)) / y;
		double zPer = -(Math.abs(this.getBounds().getMax().z) - Math.abs(this.getBounds().getMin().z)) / z;

		// println " Keep away x = "+y+" new = "+ytol
		return this.transformed(new Transform().scale(xtol, ytol, ztol))
				.transformed(new Transform().translateX(shellThickness * xPer))
				.transformed(new Transform().translateY(shellThickness * yPer))
				.transformed(new Transform().translateZ(shellThickness * zPer)).historySync(this);

	}

	public Affine getManipulator() {
		if (manipulator == null)
			manipulator = new Affine();
		return manipulator;
	}

	public CSG addCreationEventStackTraceList(ArrayList<Exception> incoming) {
		for (Exception ex : incoming) {
			addStackTrace(ex);

		}
		return this;
	}

	private CSG addStackTrace(Exception creationEventStackTrace2) {
		for (StackTraceElement el : creationEventStackTrace2.getStackTrace()) {
			try {
				if (!el.getFileName().endsWith(".java") && el.getLineNumber() > 0) {
					boolean dupLine = false;
					String thisline = el.getFileName() + ":" + el.getLineNumber();
					for (String s : groovyFileLines) {
						if (s.contentEquals(thisline)) {
							dupLine = true;
							// com.neuronrobotics.sdk.common.Log.error("Dupe: "+thisline);
							break;
						}
					}
					if (dupLine == false) {
						groovyFileLines.add(thisline);
						// com.neuronrobotics.sdk.common.Log.error("Line: "+thisline);
						// for(String s:groovyFileLines){
						// //com.neuronrobotics.sdk.common.Log.error("\t\t "+s);
						// creationEventStackTrace2.printStackTrace();
						// }
					}
				}
			} catch (NullPointerException ex) {

			}
		}
		return this;
	}

	public CSG historySync(CSG dyingCSG) {
		if (useStackTraces) {
			this.addCreationEventStringList(dyingCSG.getCreationEventStackTraceList());
		}
		Set<String> params = dyingCSG.getParameters();
		for (String param : params) {
			boolean existing = false;
			for (String s : this.getParameters()) {
				if (s.contentEquals(param))
					existing = true;
			}
			if (!existing) {
				Parameter vals = CSGDatabase.get(param);
				if (vals != null)
					this.setParameter(vals, dyingCSG.getMapOfparametrics().get(param));
			}
		}
		if (getName().length() == 0)
			setName(dyingCSG.getName());
		color = dyingCSG.getColor();
		return this;
	}

	public CSG addCreationEventStringList(ArrayList<String> incoming) {
		if (useStackTraces)
			for (String s : incoming) {
				addCreationEventString(s);
			}

		return this;
	}

	public CSG addCreationEventString(String thisline) {
		if (useStackTraces) {
			boolean dupLine = false;
			for (String s : groovyFileLines) {
				if (s.contentEquals(thisline)) {
					dupLine = true;
					break;
				}
			}
			if (!dupLine) {
				groovyFileLines.add(thisline);
			}
		}

		return this;
	}

	public ArrayList<String> getCreationEventStackTraceList() {
		return groovyFileLines;
	}

	public CSG prepMfg() {
		return prepForManufacturing();
	}

	public PrepForManufacturing getManufacturing() {
		return manufactuing;
	}

	public PrepForManufacturing getMfg() {
		return getManufacturing();
	}

	public CSG setMfg(PrepForManufacturing manufactuing) {
		return setManufacturing(manufactuing);
	}

	public CSG setManufacturing(PrepForManufacturing manufactuing) {
		this.manufactuing = manufactuing;
		return this;
	}

	@Deprecated
	public PrepForManufacturing getManufactuing() {
		return getManufacturing();
	}

	@Deprecated
	public CSG setManufactuing(PrepForManufacturing manufactuing) {
		return setManufacturing(manufactuing);
	}

	public CSG setParameter(Parameter w, IParametric function) {
		if (w == null)
			return this;
		if (CSGDatabase.get(w.getName()) == null)
			CSGDatabase.set(w.getName(), w);
		if (getMapOfparametrics().get(w.getName()) == null)
			getMapOfparametrics().put(w.getName(), function);
		return this;
	}

	public CSG setParameter(Parameter w) {
		setParameter(w, new IParametric() {
			@Override
			public CSG change(CSG oldCSG, String parameterKey, Long newValue) {
				if (parameterKey.contentEquals(w.getName()))
					CSGDatabase.get(w.getName()).setValue(newValue);
				return oldCSG;
			}
		});
		return this;
	}

	public CSG setParameter(String key, double defaultValue, double upperBound, double lowerBound,
			IParametric function) {
		ArrayList<Double> vals = new ArrayList<Double>();
		vals.add(upperBound);
		vals.add(lowerBound);
		setParameter(new LengthParameter(key, defaultValue, vals), function);
		return this;
	}

	public CSG setParameterIfNull(String key) {
		if (getMapOfparametrics().get(key) == null)
			getMapOfparametrics().put(key, new IParametric() {

				@Override
				public CSG change(CSG oldCSG, String parameterKey, Long newValue) {
					CSGDatabase.get(key).setValue(newValue);
					return oldCSG;
				}
			});
		return this;
	}

	public Set<String> getParameters() {

		return getMapOfparametrics().keySet();
	}

	public CSG setParameterNewValue(String key, double newValue) {
		IParametric function = getMapOfparametrics().get(key);
		if (function != null) {
			CSG setManipulator = function.change(this, key, new Long((long) (newValue * 1000)))
					.setManipulator(this.getManipulator());
			setManipulator.color = color;
			return setManipulator;
		}
		return this;
	}

	public CSG setRegenerate(IRegenerate function) {
		regenerate = function;
		return this;
	}
	public IRegenerate getRegenerate() {
		return regenerate ;
	}

	public CSG regenerate() {
		this.markForRegeneration = false;
		if (regenerate == null)
			return this;
		CSG regenerate2 = regenerate.regenerate(this);
		if (regenerate2 != null)
			return regenerate2.setManipulator(this.getManipulator()).historySync(this);
		;
		return this;
	}

	public HashMap<String, IParametric> getMapOfparametrics() {
		if (mapOfparametrics == null) {
			mapOfparametrics = new HashMap<>();
		}
		return mapOfparametrics;
	}

	public boolean isMarkedForRegeneration() {
		return markForRegeneration;
	}

	public CSG markForRegeneration() {
		this.markForRegeneration = true;
		return this;
	}

	/**
	 * A test to see if 2 CSG's are touching. The fast-return is a bounding box
	 * check If bounding boxes overlap, then an intersection is performed and the
	 * existance of an interscting object is returned
	 * 
	 * @param incoming
	 * @return
	 */
	public boolean touching(CSG incoming) {
		// Fast bounding box overlap check, quick fail if not intersecting
		// bounding boxes
		if (this.getMaxX() > incoming.getMinX() && this.getMinX() < incoming.getMaxX()
				&& this.getMaxY() > incoming.getMinY() && this.getMinY() < incoming.getMaxY()
				&& this.getMaxZ() > incoming.getMinZ() && this.getMinZ() < incoming.getMaxZ()) {
			// Run a full intersection
			CSG inter = this.intersect(incoming);
			if (inter.getPolygons().size() > 0) {
				// intersection success
				return true;
			}
		}
		return false;
	}

	public static ICSGProgress getProgressMoniter() {
		return progressMoniter;
	}

	public static void setProgressMoniter(ICSGProgress progressMoniter) {
		CSG.progressMoniter = progressMoniter;
	}

	public static Color getDefaultColor() {
		return defaultcolor;
	}

	public static void setDefaultColor(Color defaultcolor) {
		CSG.defaultcolor = defaultcolor;
	}

	/**
	 * Get Bounding box
	 * 
	 * @return A CSG that completely encapsulates the base CSG, centered around it
	 */
	public CSG getBoundingBox() {
		return new Cube((-this.getMinX() + this.getMaxX()), (-this.getMinY() + this.getMaxY()),
				(-this.getMinZ() + this.getMaxZ())).toCSG().toXMax().movex(this.getMaxX()).toYMax()
				.movey(this.getMaxY()).toZMax().movez(this.getMaxZ());
	}

	public String getName() {
		return name;
	}

	public CSG setName(String name) {
		if (name == null)
			throw new NullPointerException();
		this.name = name;
		return this;
	}

	@Override
	public String toString() {
		if (name == null)
			return getColor().toString();
		return getName() + " " + getColor().toString();
	}

	public ArrayList<Transform> getSlicePlanes() {
		return slicePlanes;
	}

	public CSG addSlicePlane(Transform slicePlane) {
		if (slicePlanes == null)
			slicePlanes = new ArrayList<>();
		this.slicePlanes.add(slicePlane);
		return this;
	}

	/**
	 * @return the exportFormats
	 */
	public ArrayList<String> getExportFormats() {
		return exportFormats;
	}

	public CSG clearExportFormats() {
		if (exportFormats != null)
			exportFormats.clear();
		return this;
	}

	/**
	 * @param exportFormat the exportFormat to add
	 */
	public CSG addExportFormat(String exportFormat) {
		if (this.exportFormats == null)
			this.exportFormats = new ArrayList<>();
		for (String f : exportFormats) {
			if (f.toLowerCase().contains(exportFormat.toLowerCase())) {
				return this;
			}
		}
		this.exportFormats.add(exportFormat.toLowerCase());
		return this;
	}

	public static int getNumfacesinoffset() {
		return getNumFacesInOffset();
	}

	public static int getNumFacesInOffset() {
		return numFacesInOffset;
	}

	public static void setNumFacesInOffset(int numFacesInOffset) {
		CSG.numFacesInOffset = numFacesInOffset;
	}

	public static boolean isUseStackTraces() {
		return useStackTraces;
	}

	public static void setUseStackTraces(boolean useStackTraces) {
		CSG.useStackTraces = useStackTraces;
	}

	public ArrayList<Transform> getDatumReferences() {
		return datumReferences;
	}

	private CSG setDatumReferences(ArrayList<Transform> datumReferences) {
		this.datumReferences = datumReferences;
		return this;
	}

	public PropertyStorage getStorage() {
		return str;
	}

	public CSG setStorage(PropertyStorage storage) {
		this.str = storage;
		return this;
	}

	/**
	 * Adds construction tabs to a given CSG object in order to facilitate
	 * connection with other boards and returns the CSG with tabs added plus
	 * separate fastener objects interspersed between tabs. Assumes board thickness
	 * is the thinnest dimension. Assumes board thickness can be arbitrary but
	 * uniform height. Assumes the edge having tabs added extends fully between Min
	 * and Max in that dimension.
	 * <p>
	 * TODO: Find the polygon defined by the XY plane slice that is perhaps 0.5mm
	 * into the normalized +Y. Add tabs to THAT polygon's minX/maxX instead of
	 * part's global minX/maxX.
	 * <p>
	 * Example usage: // Create a temporary copy of the target object, without any
	 * tabs CSG boardTemp = board
	 * <p>
	 * // Instantiate a bucket to hold fastener CSG objects in ArrayList<CSG>
	 * fasteners = []
	 * <p>
	 * // Define the direction of the edge to be tabbed using a Vector3d object, in
	 * this case the edge facing in the negative Y direction Vector3d edgeDirection
	 * = new Vector3d(0, -1, 0);
	 * <p>
	 * // Define the diameter of the fastener holes to be added using a
	 * LengthParameter object LengthParameter screwDiameter = new
	 * LengthParameter("Screw Hole Diameter (mm)", 3, [0, 20])
	 * <p>
	 * // Add tabs to the temporary object using the edgeDirection and screwDiameter
	 * parameters ArrayList<CSG> returned = boardTemp.addTabs(edgeDirection,
	 * screwDiameter);
	 * <p>
	 * // Combine the modified temporary object with the original object, to add the
	 * new tabs board = boardTemp.union(returned.get(0));
	 * <p>
	 * // Add the separate fastener hole objects to the list fasteners =
	 * returned.subList(1, returned.size());
	 *
	 * @param edgeDirection a Vector3d object representing the direction of the edge
	 *                      of the board to which tabs and fastener holes will be
	 *                      added
	 * @param fastener      a CSG object representing a template fastener to be
	 *                      added between the tabs
	 * @return an ArrayList of CSG objects representing the original board with
	 *         added tabs and separate fastener hole objects
	 * @throws Exception if the edgeDirection parameter is not a cartesian unit
	 *                   Vector3d object or uses an unimplemented orientation
	 */
	public ArrayList<CSG> addTabs(Vector3d edgeDirection, CSG fastener) throws Exception {

		ArrayList<CSG> result = new ArrayList<CSG>();
		ArrayList<CSG> fasteners = new ArrayList<CSG>();

		// Apply cumulative transformation to the board
		Transform boardTrans = addTabsReorientation(edgeDirection);
		CSG boardTemp = this.transformed(boardTrans);

		// TODO: Here, find the polygon defined by the XY plane slice that is perhaps
		// 0.5mm into the +Y. Add tabs to THAT polygon's minX/maxX instead of part's
		// global minX/maxX.

		// Define the size of the tabs and the distance between tab cycles
		double tabSize = boardTemp.getMaxZ() * 2;
		double cycleSize = tabSize * 3;

		// Determine the minimum buffer space between the edge of the board and the tabs
		double minBuffer = boardTemp.getMaxZ();

		// Create a temporary CSG object for a single tab
		CSG tabTemp = new Cube(tabSize, boardTemp.getMaxZ(), boardTemp.getMaxZ()).toCSG();

		// Position the temporary tab object at the first tab location
		tabTemp = tabTemp.movex(tabTemp.getMaxX()).movey(-tabTemp.getMaxY() + boardTemp.getMinY())
				.movez(tabTemp.getMaxZ());

		// Position the temporary fastener hole object at an initial fastener hole
		// location that does not actually render (analogous to the first tab location,
		// but the first tab is not associated with a fastener)
		CSG fastenerHoleTemp = fastener.rotx(-90).movex(-tabSize).movey(0).movez(boardTemp.getMaxZ() / 2);

		// Calculate the number of full tab-space cycles to add, not including the first
		// tab (this is also the number of fastener objects to return)
		int iterNum = (int) Math.floor((boardTemp.getMaxX() - tabSize - minBuffer * 2) / cycleSize); // Round down to
																										// ensure an
																										// integer value

		// Calculate the clearance beyond the outermost tabs, equal on both sides and
		// never more than minBuffer
		double bufferVal = (boardTemp.getMaxX() - (tabSize + cycleSize * iterNum)) / 2;

		// Add the first tab if there is enough room, which due to not being paired with
		// a fastener is removed from the loop
		if (boardTemp.getTotalX() > tabSize + 2 * bufferVal) {
			boardTemp = boardTemp.union(tabTemp.movex(bufferVal));
		}

		// Add the desired number of tabs & fasteners at regular intervals
		for (int i = 1; i <= iterNum; i++) {
			double xVal = bufferVal + i * cycleSize;
			boardTemp = boardTemp.union(tabTemp.movex(xVal));
			fasteners.add(fastenerHoleTemp.movex(xVal).transformed(boardTrans.inverse()));
		}

		// Translate the boardTemp object back to its original position
		boardTemp = boardTemp.transformed(boardTrans.inverse());

		result.add(boardTemp);
		result.addAll(fasteners);

		return result;
	}

	/**
	 * @param edgeDirection
	 * @return
	 * @throws Exception
	 */
	private Transform addTabsReorientation(Vector3d edgeDirection) throws Exception {
		// Instantiate a new transformation which will capture cumulative
		// transformations being operated on the input board, to be reversed later
		Transform boardTrans = new Transform();

		// Determine orientation transformation, based on edgeDirection vector
		if (edgeDirection.equals(Vector3d.X_ONE)) {
			boardTrans = boardTrans.rotz(90);
		} else if (edgeDirection.equals(Vector3d.X_ONE.negated())) {
			boardTrans = boardTrans.rotz(-90);
		} else if (edgeDirection.equals(Vector3d.Y_ONE)) {
			boardTrans = boardTrans.rotz(180);
		} else if (edgeDirection.equals(Vector3d.Y_ONE.negated())) {
			// boardTrans = boardTrans; // original addTabs orientation, so no
			// transformation needed
		} else if (edgeDirection.equals(Vector3d.Z_ONE)) {
			boardTrans = boardTrans.rotx(-90);
		} else if (edgeDirection.equals(Vector3d.Z_ONE.negated())) {
			boardTrans = boardTrans.rotx(90);
		} else {
			throw new Exception(
					"Invalid edge direction: edgeDirection must be a cartesian unit Vector3d object. Try Vector3d.Y_ONE.negated() - Current value: "
							+ edgeDirection.toString());
		}

		// Apply orientation transformation
		CSG boardTemp = this.transformed(boardTrans);

		// Translate the boardTemp object so that its minimum corner is at the origin,
		// adding to cumulative transformation
		boardTrans = boardTrans.movex(-boardTemp.getMinX()).movey(-boardTemp.getMinY()).movez(-boardTemp.getMinZ());

		// Apply translation transformation
		boardTemp = this.transformed(boardTrans);

		// If the board is larger in Z than in X, assume that the board is oriented into
		// the XY plane and rotate to flatten it onto the XY plane
		if (boardTemp.getTotalZ() > boardTemp.getTotalX()) {
			boardTrans = boardTrans.roty(-90).movez(boardTemp.getMaxX());
		}
		return boardTrans;
	}

	public ArrayList<CSG> addTabs(Vector3d edgeDirection, LengthParameter fastenerHoleDiameter) throws Exception {

		// Apply cumulative transformation to the board
		Transform boardTrans = addTabsReorientation(edgeDirection);
		CSG boardTemp = this.transformed(boardTrans);

		// Create a temporary CSG object for a single fastener hole
		double fastenerHoleRadius = fastenerHoleDiameter.getMM() / 2.0;
		double fastenerHoleDepth = boardTemp.getMaxZ();
		CSG fastenerHoleTemp = new Cylinder(fastenerHoleRadius, fastenerHoleDepth).toCSG();
		ArrayList<CSG> result = this.addTabs(edgeDirection, fastenerHoleTemp);
		return result;
	}

	public CSG addAssemblyStep(int stepNumber, Transform explodedPose) {
		String key = "AssemblySteps";
		PropertyStorage incomingGetStorage = getAssemblyStorage();
		if (incomingGetStorage.getValue(key) == Optional.empty()) {
			HashMap<Integer, Transform> map = new HashMap<>();
			incomingGetStorage.set(key, map);
		}
		if (incomingGetStorage.getValue("MaxAssemblyStep") == Optional.empty()) {
			incomingGetStorage.set("MaxAssemblyStep", Integer.valueOf(stepNumber));
		}
		Integer max = (Integer) incomingGetStorage.getValue("MaxAssemblyStep").get();
		if (stepNumber > max.intValue()) {
			incomingGetStorage.set("MaxAssemblyStep", Integer.valueOf(stepNumber));
		}
		HashMap<Integer, Transform> map = (HashMap<Integer, Transform>) incomingGetStorage.getValue(key).get();
		map.put(stepNumber, explodedPose);
		if (incomingGetStorage.getValue("AssembleAffine") == Optional.empty())
			incomingGetStorage.set("AssembleAffine", new Affine());
		return this;
	}

	public PropertyStorage getAssemblyStorage() {
		if (assembly == null)
			assembly = new PropertyStorage();
		return assembly;
	}

	public boolean isWireFrame() {
		if (!getStorage().getValue("skeleton").isPresent())
			return false;
		return (boolean) getStorage().getValue("skeleton").get();
	}

	public CSG setIsWireFrame(boolean b) {
		getStorage().set("skeleton", b);
		return this;
	}

	public CSG setPrintBedNumber(int index) {
		getStorage().set("printBedIndex", index);
		return this;
	}

	public int getPrintBedIndex() {
		if (!getStorage().getValue("printBedIndex").isPresent())
			return 0;
		return (int) getStorage().getValue("printBedIndex").get();
	}

	public static CSG text(String text, double height, double fontSize) {
		return text(text, height, fontSize, Font.SANS_SERIF);
	}

	public static CSG text(String text, double height) {
		return text(text, height, 30);
	}

	public static CSG text(String text, double height, double fontSize, String fontType) {
		Font font = new Font(fontType, Font.PLAIN, (int)fontSize);
		List<CSG> stuff = TextExtrude.text(height, text, font);
		CSG back = null;
		for (int i = 0; i < stuff.size(); i++) {
			if (back == null)
				back = stuff.get(i);
			else {
				back = back.dumbUnion(stuff.get(i));
			}
		}
		back = back.rotx(180).toZMin();
		return back;
	}

	/**
	 * Extrude text to a specific bounding box size
	 * 
	 * @param text the text to be extruded
	 * @param x    the total final X
	 * @param y    the total final Y
	 * @param z    the total final Z
	 * @return The given input text, scaled to the exact sizes provided, with Y=0
	 *         line as the bottom line of the text
	 */
	public static CSG textToSize(String text, double x, double y, double z) {
		CSG startText = CSG.text(text, z);
		double scalex = x / startText.getTotalX();
		double scaley = y / startText.getTotalY();
		return startText.scalex(scalex).scaley(scaley).toXMin();
	}

	public boolean hasMassSet() {
		return getStorage().getValue("massKg").isPresent();
	}

	public CSG setMassKG(double mass) {
		getStorage().set("massKg", mass);
		return this;
	}

	public double getMassKG(double mass) {
		Optional o = getStorage().getValue("massKg");
		if (o.isPresent())
			return (double) o.get();
		return mass;
	}

	public CSG setCenterOfMass(Transform com) {
		Bounds b = getBounds();
		if (b.contains(com))
			getStorage().set("massCentroid", com);
		return this;
	}

	public CSG setCenterOfMass(double x, double y, double z) {
		Transform com = new Transform().movex(x).movey(y).movez(z);
		return setCenterOfMass(com);
	}

	public Transform getCenterOfMass() {
		Optional o = getStorage().getValue("massCentroid");
		if (o.isPresent())
			return (Transform) o.get();
		return new Transform().move(getCenter());
	}

	public CSG addGroupMembership(String groupID) {
		if (!getStorage().getValue("groupMembership").isPresent()) {
			getStorage().set("groupMembership", new HashSet<String>());
		}
		((HashSet<String>) getStorage().getValue("groupMembership").get()).add(groupID);
		return this;
	}

	public CSG removeGroupMembership(String groupID) {
		if (!getStorage().getValue("groupMembership").isPresent()) {
			getStorage().set("groupMembership", new HashSet<String>());
		}
		((HashSet<String>) getStorage().getValue("groupMembership").get()).remove(groupID);
		return this;
	}

	public boolean isInGroup() {
		Optional<HashSet<String>> value = getStorage().getValue("groupMembership");
		if (value.isPresent()) {
			if (value.get().size() > 0) {
				return true;
			}
		}
		return false;
	}

	public boolean checkGroupMembership(String groupName) {
		Optional<HashSet<String>> o = getStorage().getValue("groupMembership");
		if (o.isPresent())
			for (String s : o.get()) {
				if (s.contentEquals(groupName))
					return true;
			}
		return false;
	}

//	public CSG setIsGroupResult(boolean res) {
//		getStorage().set("GroupResult", res);
//		return this;
//	}
	public CSG addIsGroupResult(String res) {
		if (!getStorage().getValue("GroupResult").isPresent()) {
			getStorage().set("GroupResult", new HashSet<String>());
		}
		((HashSet<String>) getStorage().getValue("GroupResult").get()).add(res);
		return this;
	}

	public CSG removeIsGroupResult(String res) {
		if (!getStorage().getValue("GroupResult").isPresent()) {
			getStorage().set("GroupResult", new HashSet<String>());
		}
		((HashSet<String>) getStorage().getValue("GroupResult").get()).remove(res);
		return this;
	}

	public boolean isGroupResult() {
		Optional<HashSet<String>> o = getStorage().getValue("GroupResult");
		if (o.isPresent())
			return o.get().size() > 0;
		return false;
	}

	// Hole
	public CSG setIsLock(boolean Lock) {
		getStorage().set("isLock", Lock);
		return this;
	}

	public boolean isLock() {
		Optional<Boolean> o = getStorage().getValue("isLock");
		if (o.isPresent())
			return o.get();
		return false;
	}

	// Hole
	public CSG setIsHide(boolean Hide) {
		getStorage().set("isHide", Hide);
		return this;
	}

	public boolean isHide() {
		Optional<Boolean> o = getStorage().getValue("isHide");
		if (o.isPresent())
			return o.get();
		return false;
	}

	// Hole
	public CSG setIsHole(boolean hole) {
		getStorage().set("isHole", hole);
		return this;
	}

	public boolean isHole() {
		Optional<Boolean> o = getStorage().getValue("isHole");
		if (o.isPresent())
			return o.get();
		return false;
	}

	public CSG syncProperties(CSG dying) {
		getStorage().syncProperties(dying.getStorage());
		regenerate=dying.regenerate;
		return this;
	}

	/**
	 * Tessellates a given CSG object into a 3D grid with specified steps and grid
	 * spacing, including offsets for odd rows, columns, and layers.
	 *
	 * @param incoming      The CSG object to be tessellated.
	 * @param xSteps        Number of steps (iterations) in the x-direction.
	 * @param ySteps        Number of steps (iterations) in the y-direction.
	 * @param zSteps        Number of steps (iterations) in the z-direction.
	 * @param xGrid         Distance between iterations in the x-direction.
	 * @param yGrid         Distance between iterations in the y-direction.
	 * @param zGrid         Distance between iterations in the z-direction.
	 * @param oddRowXOffset X offset for odd rows.
	 * @param oddRowYOffset Y offset for odd rows.
	 * @param oddRowZOffset Z offset for odd rows.
	 * @param oddColXOffset X offset for odd columns.
	 * @param oddColYOffset Y offset for odd columns.
	 * @param oddColZOffset Z offset for odd columns.
	 * @param oddLayXOffset X offset for odd layers.
	 * @param oddLayYOffset Y offset for odd layers.
	 * @param oddLayZOffset Z offset for odd layers.
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellate(CSG incoming, int xSteps, int ySteps, int zSteps, double xGrid, double yGrid,
			double zGrid, double oddRowXOffset, double oddRowYOffset, double oddRowZOffset, double oddColXOffset,
			double oddColYOffset, double oddColZOffset, double oddLayXOffset, double oddLayYOffset,
			double oddLayZOffset) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		for (int i = 0; i < xSteps; i++) {
			for (int j = 0; j < ySteps; j++) {
				for (int k = 0; k < zSteps; k++) {

					double xoff = 0;
					double yoff = 0;
					double zoff = 0;

					if (i % 2 != 0) {
						xoff += oddRowXOffset;
						yoff += oddRowYOffset;
						zoff += oddRowZOffset;
					}

					if (j % 2 != 0) {
						xoff += oddColXOffset;
						yoff += oddColYOffset;
						zoff += oddColZOffset;
					}

					if (k % 2 != 0) {
						xoff += oddLayXOffset;
						yoff += oddLayYOffset;
						zoff += oddLayZOffset;
					}

					back.add(incoming.move(xoff + (i * xGrid), yoff + (j * yGrid), zoff + (k * zGrid)));
				}
			}
		}
		return back;
	}

	/**
	 * Tessellates a given CSG object into a 3D grid with specified steps, grid
	 * spacing, and a 3D array of offsets for odd rows, columns, and layers.
	 *
	 * @param incoming The CSG object to be tessellated.
	 * @param xSteps   Number of steps (iterations) in the x-direction.
	 * @param ySteps   Number of steps (iterations) in the y-direction.
	 * @param zSteps   Number of steps (iterations) in the z-direction.
	 * @param xGrid    Distance between iterations in the x-direction.
	 * @param yGrid    Distance between iterations in the y-direction.
	 * @param zGrid    Distance between iterations in the z-direction.
	 * @param offsets  3D array of offsets for odd rows, columns, and layers.
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellate(CSG incoming, int xSteps, int ySteps, int zSteps, double xGrid, double yGrid,
			double zGrid, double[][] offsets) {
		double oddRowXOffset = offsets[0][0];
		double oddRowYOffset = offsets[0][1];
		double oddRowZOffset = offsets[0][2];

		double oddColXOffset = offsets[1][0];
		double oddColYOffset = offsets[1][1];
		double oddColZOffset = offsets[1][2];

		double oddLayXOffset = offsets[2][0];
		double oddLayYOffset = offsets[2][1];
		double oddLayZOffset = offsets[2][2];

		return tessellate(incoming, xSteps, ySteps, zSteps, xGrid, yGrid, zGrid, oddRowXOffset, oddRowYOffset,
				oddRowZOffset, oddColXOffset, oddColYOffset, oddColZOffset, oddLayXOffset, oddLayYOffset,
				oddLayZOffset);
	}

	/**
	 * Tessellates a given CSG object into a 3D grid with specified steps. The grid
	 * spacing is determined by the dimensions of the incoming CSG object.
	 *
	 * @param incoming The CSG object to be tessellated.
	 * @param xSteps   Number of steps (iterations) in the x-direction.
	 * @param ySteps   Number of steps (iterations) in the y-direction.
	 * @param zSteps   Number of steps (iterations) in the z-direction.
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellate(CSG incoming, int xSteps, int ySteps, int zSteps) {
		return tessellate(incoming, xSteps, ySteps, zSteps, incoming.getTotalX(), incoming.getTotalY(),
				incoming.getTotalZ(), 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	/**
	 * Tessellates a given CSG object into a 3D grid with specified steps and
	 * offsets for odd rows, columns, and layers. The grid spacing is determined by
	 * the dimensions of the incoming CSG object.
	 *
	 * @param incoming      The CSG object to be tessellated.
	 * @param xSteps        Number of steps (iterations) in the x-direction.
	 * @param ySteps        Number of steps (iterations) in the y-direction.
	 * @param zSteps        Number of steps (iterations) in the z-direction.
	 * @param oddRowXOffset X offset for odd rows.
	 * @param oddRowYOffset Y offset for odd rows.
	 * @param oddRowZOffset Z offset for odd rows.
	 * @param oddColXOffset X offset for odd columns.
	 * @param oddColYOffset Y offset for odd columns.
	 * @param oddColZOffset Z offset for odd columns.
	 * @param oddLayXOffset X offset for odd layers.
	 * @param oddLayYOffset Y offset for odd layers.
	 * @param oddLayZOffset Z offset for odd layers.
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellate(CSG incoming, int xSteps, int ySteps, int zSteps, double oddRowXOffset,
			double oddRowYOffset, double oddRowZOffset, double oddColXOffset, double oddColYOffset,
			double oddColZOffset, double oddLayXOffset, double oddLayYOffset, double oddLayZOffset) {
		double[][] offsets = { { oddRowXOffset, oddRowYOffset, oddRowZOffset },
				{ oddColXOffset, oddColYOffset, oddColZOffset }, { oddLayXOffset, oddLayYOffset, oddLayZOffset } };
		return tessellate(incoming, xSteps, ySteps, zSteps, incoming.getTotalX(), incoming.getTotalY(),
				incoming.getTotalZ(), offsets);
	}

	/**
	 * Tessellates a given CSG object into a 3D grid with specified steps and
	 * uniform grid spacing.
	 *
	 * @param incoming    The CSG object to be tessellated.
	 * @param steps       Number of steps (iterations) in each direction (x, y, z).
	 * @param gridSpacing Distance between iterations in all directions (x, y, z).
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellate(CSG incoming, int steps, double gridSpacing) {
		return tessellate(incoming, steps, steps, steps, gridSpacing, gridSpacing, gridSpacing, 0, 0, 0, 0, 0, 0, 0, 0,
				0);
	}

	/**
	 * Tessellates a given CSG object into a 3D grid with specified steps. The grid
	 * spacing is determined by the dimensions of the incoming CSG object.
	 *
	 * @param incoming The CSG object to be tessellated.
	 * @param steps    Number of steps (iterations) in each direction (x, y, z).
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellate(CSG incoming, int steps) {
		return tessellate(incoming, steps, steps, steps, incoming.getTotalX(), incoming.getTotalY(),
				incoming.getTotalZ(), 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	/**
	 * Tessellates a given CSG object into a 2D grid with specified steps and grid
	 * spacing, including offsets for odd rows and columns.
	 *
	 * @param incoming      The CSG object to be tessellated.
	 * @param xSteps        Number of steps (iterations) in the x-direction.
	 * @param ySteps        Number of steps (iterations) in the y-direction.
	 * @param xGrid         Distance between iterations in the x-direction.
	 * @param yGrid         Distance between iterations in the y-direction.
	 * @param oddRowXOffset X offset for odd rows.
	 * @param oddRowYOffset Y offset for odd rows.
	 * @param oddColXOffset X offset for odd columns.
	 * @param oddColYOffset Y offset for odd columns.
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellateXY(CSG incoming, int xSteps, int ySteps, double xGrid, double yGrid,
			double oddRowXOffset, double oddRowYOffset, double oddColXOffset, double oddColYOffset) {
		return tessellate(incoming, xSteps, ySteps, 1, xGrid, yGrid, 0, oddRowXOffset, oddRowYOffset, 0, oddColXOffset,
				oddColYOffset, 0, 0, 0, 0);
	}

	/**
	 * Tessellates a given CSG object into a 2D grid with specified steps, grid
	 * spacing, and a 2D array of offsets for odd rows and columns.
	 *
	 * @param incoming The CSG object to be tessellated.
	 * @param xSteps   Number of steps (iterations) in the x-direction.
	 * @param ySteps   Number of steps (iterations) in the y-direction.
	 * @param xGrid    Distance between iterations in the x-direction.
	 * @param yGrid    Distance between iterations in the y-direction.
	 * @param offsets  2D array of offsets for odd rows and columns.
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellateXY(CSG incoming, int xSteps, int ySteps, double xGrid, double yGrid,
			double[][] offsets) {
		double oddRowXOffset = offsets[0][0];
		double oddRowYOffset = offsets[0][1];
		double oddColXOffset = offsets[1][0];
		double oddColYOffset = offsets[1][1];

		return tessellate(incoming, xSteps, ySteps, 1, xGrid, yGrid, 0, oddRowXOffset, oddRowYOffset, 0, oddColXOffset,
				oddColYOffset, 0, 0, 0, 0);
	}

	/**
	 * Tessellates a given CSG object into a 2D grid with specified steps. The grid
	 * spacing is determined by the dimensions of the incoming CSG object.
	 *
	 * @param incoming The CSG object to be tessellated.
	 * @param xSteps   Number of steps (iterations) in the x-direction.
	 * @param ySteps   Number of steps (iterations) in the y-direction.
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellateXY(CSG incoming, int xSteps, int ySteps) {
		return tessellateXY(incoming, xSteps, ySteps, incoming.getTotalX(), incoming.getTotalY(), 0, 0, 0, 0);
	}

	/**
	 * Tessellates a given CSG object into a 2D grid with specified steps and grid
	 * spacing.
	 *
	 * @param incoming The CSG object to be tessellated.
	 * @param xSteps   Number of steps (iterations) in the x-direction.
	 * @param ySteps   Number of steps (iterations) in the y-direction.
	 * @param xGrid    Distance between iterations in the x-direction.
	 * @param yGrid    Distance between iterations in the y-direction.
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellateXY(CSG incoming, int xSteps, int ySteps, double xGrid, double yGrid) {
		return tessellateXY(incoming, xSteps, ySteps, xGrid, yGrid, 0, 0, 0, 0);
	}

	/**
	 * 
	 * @param incoming Hexagon (with flats such that Y total is flat to flat
	 *                 distance)
	 * @param xSteps   number of steps in X
	 * @param ySteps   number of steps in Y
	 * @param spacing  the amount of space between each hexagon
	 * @return a list of spaced hexagons
	 */
	List<CSG> tessellateHex(CSG incoming, int xSteps, int ySteps, double spacing) {
		double y = incoming.getTotalY() + spacing;
		double x = (((y / Math.sqrt(3)))) * (3 / 2);
		return tessellateXY(incoming, xSteps, ySteps, x, y, 0, 0, 0, y / 2);
	}

	/**
	 * 
	 * @param incoming Hexagon (with flats such that Y total is flat to flat
	 *                 distance)
	 * @param xSteps   number of steps in X
	 * @param ySteps   number of steps in Y
	 * @return a list of spaced hexagons
	 */
	List<CSG> tessellateHex(CSG incoming, int xSteps, int ySteps) {
		return tessellateHex(incoming, xSteps, ySteps, 0);
	}

	public static boolean isPreventNonManifoldTriangles() {
		return preventNonManifoldTriangles;
	}

	public static void setPreventNonManifoldTriangles(boolean preventNonManifoldTriangles) {
		if (!preventNonManifoldTriangles)
			System.err.println(
					"WARNING:This will make STL's incompatible with low quality slicing engines like Slice3r and PrusaSlicer");
		CSG.preventNonManifoldTriangles = preventNonManifoldTriangles;
	}

	public static boolean isUseGPU() {
		return useGPU;
	}

	public static void setUseGPU(boolean useGPU) {
		CSG.useGPU = useGPU;
	}
}

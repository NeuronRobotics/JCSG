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

import org.xml.sax.Attributes;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import eu.mihosoft.vrl.v3d.Slice.DefaultSliceImp;
import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;
import eu.mihosoft.vrl.v3d.ext.quickhull3d.HullUtil;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import eu.mihosoft.vrl.v3d.parametrics.IParametric;
import eu.mihosoft.vrl.v3d.parametrics.IRegenerate;
import eu.mihosoft.vrl.v3d.parametrics.LengthParameter;
import eu.mihosoft.vrl.v3d.parametrics.Parameter;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import com.aparapi.Kernel;
import com.aparapi.Range;
import com.aparapi.internal.kernel.KernelRunner;
import com.neuronrobotics.interaction.CadInteractionEvent;
import com.neuronrobotics.manifold3d.CSGManifold3d;
import com.neuronrobotics.manifold3d.NonManifoldShapeError;

import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.Mesh;
import javafx.scene.shape.MeshView;
import javafx.scene.text.Font;
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
public class CSG implements IuserAPI, Serializable {
	transient private static final double POINTS_CONTACT_DISTANCE = 0.0001;
	transient private static int MinPolygonsForOffloading = 200;
	transient private static final long serialVersionUID = 4071874097772427063L;
	transient private static IDebug3dProvider providerOf3d = null;
	transient private static int numFacesInOffset = 15;
	transient public static final int INDEX_OF_PARAMETRIC_DEFAULT = 0;
	transient public static final int INDEX_OF_PARAMETRIC_LOWER = 1;
	transient public static final int INDEX_OF_PARAMETRIC_UPPER = 2;
	transient private static HashMap<String, PrepForManufacturing> manufactuingMap = new HashMap<String, PrepForManufacturing>();
	transient private static HashMap<String, IRegenerate> regenerate = new HashMap<String, IRegenerate>();
	transient private static HashMap<String, Affine> manipulator = new HashMap<String, Affine>();

	/**
	 * The Enum OptType.
	 */
	public static enum OptType {

		/** The csg bound. */
		CSG_BOUND,

		Manifold3d,

		/** The none. */
		NONE
	}

	transient private static OptType defaultOptType = OptType.CSG_BOUND;
	transient private static String defaultcolor = "#007956";
	// private boolean triangulated;
	transient private static boolean useStackTraces = true;
	transient private static boolean preventNonManifoldTriangles = false;
	transient private static boolean warned = false;
	// GPU processing
	transient private static boolean useGPU = false;
	transient private static int ExtraSpace = 100;
	transient private static ICSGProgress progressMoniter = new ICSGProgress() {
		@Override
		public void progressUpdate(int currentIndex, int finalIndex, String type, CSG intermediateShape) {
			System.err.println(type + "  cur:" + currentIndex + " of " + finalIndex);
		}
	};
	transient private static ForkJoinPool poolGlobal = null;

	/** The polygons. */
	// private ArrayList<Polygon> polygons;

	/** The default opt type. */

	/** The storage. */
	private PropertyStorage str;
	private PropertyStorage assembly;

	/** The current. */
	transient private MeshView current;

	/** The color. */
	// private Color color = getDefaultColor();
	private double r = getDefaultColor().getRed();
	private double g = getDefaultColor().getGreen();
	private double b = getDefaultColor().getBlue();
	private double o = getDefaultColor().getOpacity();
	/** The manipulator. */
	private Bounds bounds;

	private ArrayList<String> groovyFileLines = new ArrayList<>();

	private boolean markForRegeneration = false;
	private String name = "";
	private ArrayList<Transform> slicePlanes = null;
	private ArrayList<String> exportFormats = null;
	private ArrayList<Transform> datumReferences = null;

	private int pointsAdded;
	private String uniqueId = UUID.randomUUID().toString();
	private static CSGManifold3d manifold = null;

	private double[] vertices;
	private long[] triangles;
	private ArrayList<Plane> planes;

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

	public CSG(double[] vertices, long[] triangles, Color c) {
		this();
		this.setVertices(vertices);
		this.setTriangles(triangles);
		setColor(c);
	}

	public CSG(ArrayList<Polygon> polygons) throws ColinearPointsException {
		this();
		processPolygonsToTriangles(polygons);

	}

	public ArrayList<Polygon> generatePolygonsFromMesh() throws ColinearPointsException {
		if (getTriCount() == 0)
			return new ArrayList<>();

		ArrayList<Polygon> polygons = new ArrayList<Polygon>();

		for (long t = 0; t < getTriCount(); t++) {
			try {
				polygons.add(getPolygonByIndex((int) t));
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		return polygons;
	}

	private Vector3d getVertexByIndex(long index) {
		return new Vector3d(getVertex_X((int) index), getVertex_Y((int) index), getVertex_Z((int) index));
	}

	public double getVertex_X(int vertex) {
		return getVertices()[vertex * 3 + 0];
	}

	public double getVertex_Y(int vertex) {
		return getVertices()[vertex * 3 + 1];
	}

	public double getVertex_Z(int vertex) {
		return getVertices()[vertex * 3 + 2];
	}

	public List<Vector3d> getPoints() {
		List<Vector3d> points = new ArrayList<Vector3d>();
		for (int i = 0; i < getVertCount(); i++)
			points.add(getVertexByIndex(i));
		return points;
	}

	public Plane getPlaneByIndex(int fi) {
		if (planes == null) {
			planes = new ArrayList<Plane>();
			for (int faceIndex = 0; faceIndex < getNumberOfTriangles(); faceIndex++) {
				List<Vertex> points = new ArrayList<Vertex>();
				points.add(new Vertex(getVertexByIndex(getTriangles()[faceIndex * 3])));
				points.add(new Vertex(getVertexByIndex(getTriangles()[faceIndex * 3 + 1])));
				points.add(new Vertex(getVertexByIndex(getTriangles()[faceIndex * 3 + 2])));
				try {
					planes.add(new Plane(points, points.get(0).pos));
				} catch (ColinearPointsException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return planes.get(fi);
	}

	public Polygon getPolygonByIndex(int faceIndex) throws ColinearPointsException {
		List<Vertex> points = new ArrayList<Vertex>();
		points.add(new Vertex(getVertexByIndex(getTriangles()[faceIndex * 3])));
		points.add(new Vertex(getVertexByIndex(getTriangles()[faceIndex * 3 + 1])));
		points.add(new Vertex(getVertexByIndex(getTriangles()[faceIndex * 3 + 2])));
		Polygon polygon = new Polygon(points, new PropertyStorage(), true, getPlaneByIndex(faceIndex));
		polygon.setColor(getColor());
		return polygon;
	}

	public CSG processPolygonsToTriangles(ArrayList<Polygon> polygons) throws ColinearPointsException {
		// Build an indexed triangle mesh.
		// Use a tolerance-free exact key so we don't merge
		// numerically-close-but-distinct verts.
		Map<String, Integer> vertexIndex = new HashMap<>();
		List<Vector3d> vertexList = new ArrayList<>();
		List<Long> triList = new ArrayList<>();
		planes = new ArrayList<Plane>();
		for (Polygon incoming : polygons) {
			for (Polygon poly : PolygonUtil.triangulatePolygon(incoming)) {
				List<Vertex> pverts = poly.getVertices();
				if (pverts == null || pverts.size() != 3)
					continue;
				int i0 = intern(pverts.get(0), vertexIndex, vertexList);
				int i1 = intern(pverts.get(1), vertexIndex, vertexList);
				int i2 = intern(pverts.get(2), vertexIndex, vertexList);

				// Skip degenerate triangles (two or more identical indices).
				if (i0 == i1 || i1 == i2 || i0 == i2)
					continue;

				triList.add((long) i0);
				triList.add((long) i1);
				triList.add((long) i2);
				planes.add(poly.getPlane());
			}
		}

		if (triList.isEmpty()) {
			setVertices(new double[0]);
			setTriangles(new long[0]);

			return this;
		}

		// Flatten vertex list into a primitive array.
		setVertices(new double[(int) (vertexList.size() * 3)]);
		for (int i = 0; i < getVertCount(); i++) {
			Vector3d v = vertexList.get(i);
			getVertices()[i * 3] = v.x;
			getVertices()[i * 3 + 1] = v.y;
			getVertices()[i * 3 + 2] = v.z;
		}

		// Flatten triangle index list.
		setTriangles(new long[triList.size()]);
		for (int i = 0; i < getTriangles().length; i++) {
			getTriangles()[i] = triList.get(i);
		}
		return this;
	}

	/**
	 * Returns the index of {@code v} in {@code vertexList}, inserting it if not
	 * already present. The key is an exact string representation of (x, y, z) using
	 * {@link Double#toHexString} so that only bit-identical positions are merged,
	 * matching the BSP's behavior.
	 */
	private static int intern(Vertex v, Map<String, Integer> index, List<Vector3d> list) {

		double precision = 1.0d / POINTS_CONTACT_DISTANCE;// 0.1d/Plane.getEPSILON();

		long x = Math.round(v.pos.x * precision);
		long y = Math.round(v.pos.y * precision);
		long z = Math.round(v.pos.z * precision);

		String key = x + "_" + y + "_" + z;

		return index.computeIfAbsent(key, k -> {
			int idx = list.size();
			list.add(v.pos.clone());
			return idx;
		});
	}

	/**
	 * Gets the polygons.
	 *
	 * @return the polygons of this CSG
	 */
	public ArrayList<Polygon> getPolygons() {
		try {
			return generatePolygonsFromMesh();
		} catch (ColinearPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new ArrayList<>();
		}
	}

	/**
	 * Sets the polygons.
	 *
	 * @param polygons
	 *            the new polygons
	 * @throws ColinearPointsException
	 */
	public CSG setPolygons(ArrayList<Polygon> polygons) throws ColinearPointsException {
		processPolygonsToTriangles(polygons);
		return this;
	}

	public long getNumberOfTriangles() {
		return getTriCount();
	}

	public CSG setID(CSG dying) {
		uniqueId = dying.uniqueId;
		return this;
	}

	@Override
	public boolean equals(Object obj) {
		// Check if same reference
		if (this == obj)
			return true;

		// Check if null or different class
		if (obj == null || getClass() != obj.getClass())
			return false;

		// Cast and compare fields
		CSG test = (CSG) obj;
		return this.getUniqueId().contentEquals(test.getUniqueId());
	}

	@Override
	public int hashCode() {
		return getUniqueId().hashCode();
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
		ret.setColor(getColor());
		ret.slicePlanes = slicePlanes;
		ret.exportFormats = exportFormats;
		return ret;
	}

	/**
	 * Gets the color.
	 *
	 * @return the color
	 */
	public Color getColor() {
		return new Color(r, g, b, o);
	}

	/**
	 * Sets the color.
	 *
	 * @param color
	 *            the new color
	 */
	public CSG setColor(javafx.scene.paint.Color color) {
		r = color.getRed();
		g = color.getGreen();
		b = color.getBlue();
		o = color.getOpacity();
		// for (Polygon p : polygons)
		// p.setColor(color);
		return this;
	}

	public void setMeshColor(javafx.scene.paint.Color color) {
		if (getCurrentMeshView() != null) {
			PhongMaterial m = new PhongMaterial(color);
			getCurrentMeshView().setMaterial(m);
		}
	}

	/**
	 * Sets the Temporary color.
	 *
	 * @param color
	 *            the new Temporary color
	 */
	public CSG setTemporaryColor(Color color) {
		if (getCurrentMeshView() != null) {
			PhongMaterial m = new PhongMaterial(color);
			getCurrentMeshView().setMaterial(m);
		}
		return this;
	}

	/**
	 * Sets the manipulator.
	 *
	 * @param manipulator
	 *            the manipulator
	 * @return the affine
	 */
	public CSG setManipulator(javafx.scene.transform.Affine m) {
		if (manipulator == null)
			return this;
		manipulator.put(getUniqueId(), m);
		if (getCurrentMeshView() != null) {
			getCurrentMeshView().getTransforms().clear();
			getCurrentMeshView().getTransforms().add(m);
		}
		return this;
	}

	/**
	 * Gets the mesh.
	 *
	 * @return the mesh
	 * @throws ColinearPointsException
	 */
	public MeshView getMesh() {
		if (getCurrentMeshView() != null)
			return getCurrentMeshView();
		setCurrentMeshView(newMesh());
		return getCurrentMeshView();
	}

	/**
	 * Gets the mesh.
	 *
	 * @return the mesh
	 * @throws ColinearPointsException
	 */
	public MeshView newMesh() {

		Mesh meshContainer = toJavaFXMesh(null);

		MeshView current = new MeshView(meshContainer);

		Color color = getColor();
		PhongMaterial m = new PhongMaterial(color);
		current.setMaterial(m);

		boolean hasManipulator = hasManipulator();
		boolean hasAssembly = getAssemblyStorage().getValue("AssembleAffine") != Optional.empty();

		if (hasManipulator || hasAssembly)
			current.getTransforms().clear();

		if (hasManipulator)
			try {
				current.getTransforms().add(getManipulator());
			} catch (MissingManipulatorException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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
	 * @param target
	 *            the target
	 * @return the csg
	 */
	public CSG toZMin(CSG target) {
		return this.transformed(new Transform().translateZ(-target.getBounds().getMin().z));
	}

	/**
	 * To z max.
	 *
	 * @param target
	 *            the target
	 * @return the csg
	 */
	public CSG toZMax(CSG target) {
		return this.transformed(new Transform().translateZ(-target.getBounds().getMax().z));
	}

	/**
	 * To x min.
	 *
	 * @param target
	 *            the target
	 * @return the csg
	 */
	public CSG toXMin(CSG target) {
		return this.transformed(new Transform().translateX(-target.getBounds().getMin().x));
	}

	/**
	 * To x max.
	 *
	 * @param target
	 *            the target
	 * @return the csg
	 */
	public CSG toXMax(CSG target) {
		return this.transformed(new Transform().translateX(-target.getBounds().getMax().x));
	}

	/**
	 * To y min.
	 *
	 * @param target
	 *            the target
	 * @return the csg
	 */
	public CSG toYMin(CSG target) {
		return this.transformed(new Transform().translateY(-target.getBounds().getMin().y));
	}

	/**
	 * To y max.
	 *
	 * @param target
	 *            the target
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
	 * @param howFarToMove
	 *            the how far to move
	 * @return the csg
	 */
	// Helper/wrapper functions for movement
	public CSG movey(Number howFarToMove) {
		return this.transformed(Transform.unity().translateY(howFarToMove.doubleValue()));
	}

	/**
	 * Movez.
	 *
	 * @param howFarToMove
	 *            the how far to move
	 * @return the csg
	 */
	public CSG movez(Number howFarToMove) {
		return this.transformed(Transform.unity().translateZ(howFarToMove.doubleValue()));
	}

	/**
	 * Movex.
	 *
	 * @param howFarToMove
	 *            the how far to move
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
		for (int i = 0; i < p.size(); i++) {
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
	 * @param degreesToRotate
	 *            the degrees to rotate
	 * @return the csg
	 */
	// Rotation function, rotates the object
	public CSG rotz(Number degreesToRotate) {
		return this.transformed(new Transform().rotZ(degreesToRotate.doubleValue()));
	}

	/**
	 * Roty.
	 *
	 * @param degreesToRotate
	 *            the degrees to rotate
	 * @return the csg
	 */
	public CSG roty(Number degreesToRotate) {
		return this.transformed(new Transform().rotY(degreesToRotate.doubleValue()));
	}

	/**
	 * Rotx.
	 *
	 * @param degreesToRotate
	 *            the degrees to rotate
	 * @return the csg
	 */
	public CSG rotx(Number degreesToRotate) {
		return this.transformed(new Transform().rotX(degreesToRotate.doubleValue()));
	}

	/**
	 * Scalez.
	 *
	 * @param scaleValue
	 *            the scale value
	 * @return the csg
	 */
	// Scale function, scales the object
	public CSG scalez(Number scaleValue) {
		return this.transformed(new Transform().scaleZ(scaleValue.doubleValue()));
	}

	/**
	 * Scaley.
	 *
	 * @param scaleValue
	 *            the scale value
	 * @return the csg
	 */
	public CSG scaley(Number scaleValue) {
		return this.transformed(new Transform().scaleY(scaleValue.doubleValue()));
	}

	/**
	 * Scalex.
	 *
	 * @param scaleValue
	 *            the scale value
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
	 * @param measurment
	 *            the scale value
	 * @return the csg
	 */
	public CSG scaleToMeasurmentY(Number measurment) {
		Number scaleValue = measurment.doubleValue() / getTotalY();

		return this.transformed(new Transform().scaleY(scaleValue.doubleValue()));
	}

	/**
	 * Scalex.
	 *
	 * @param measurment
	 *            the scale value
	 * @return the csg
	 */
	public CSG scaleToMeasurmentX(Number measurment) {
		Number scaleValue = measurment.doubleValue() / getTotalX();
		return this.transformed(new Transform().scaleX(scaleValue.doubleValue()));
	}

	/**
	 * Scale.
	 *
	 * @param scaleValue
	 *            the scale value
	 * @return the csg
	 */
	public CSG scale(Number scaleValue) {
		return this.transformed(new Transform().scale(scaleValue.doubleValue()));
	}

	// /**
	// * Constructs a CSG from a list of {@link Polygon} instances.
	// *
	// * @param polygons
	// * polygons
	// * @return a CSG instance
	// */
	// public static CSG fromPolygons(ArrayList<Polygon> polygons) {
	//
	// CSG csg = new CSG();
	// csg.setPolygons(polygons);
	// return csg;
	// }
	//
	// /**
	// * Constructs a CSG from the specified {@link Polygon} instances.
	// *
	// * @param polygons
	// * polygons
	// * @return a CSG instance
	// */
	// public static CSG fromPolygons(Polygon... polygons) {
	// return fromPolygons(new ArrayList<>(Arrays.asList(polygons)));
	// }
	//
	// /**
	// * Constructs a CSG from a list of {@link Polygon} instances.
	// *
	// * @param storage
	// * shared storage
	// * @param polygons
	// * polygons
	// * @return a CSG instance
	// */
	// public static CSG fromPolygons(PropertyStorage storage, ArrayList<Polygon>
	// polygons) {
	//
	// CSG csg = new CSG();
	// csg.setPolygons(polygons);
	//
	// csg.setStorage(storage);
	//
	// for (Polygon polygon : polygons) {
	// polygon.setStorage(storage);
	// }
	// return csg;
	// }
	//
	// /**
	// * Constructs a CSG from the specified {@link Polygon} instances.
	// *
	// * @param storage
	// * shared storage
	// * @param polygons
	// * polygons
	// * @return a CSG instance
	// */
	// public static CSG fromPolygons(PropertyStorage storage, Polygon... polygons)
	// {
	// return fromPolygons(storage, new ArrayList<>(Arrays.asList(polygons)));
	// }

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#clone()
	 */
	@Override
	public CSG clone() {
		CSG csg = cloneShallow();
		CSG historySync = csg.historySync(this);
		return historySync;
	}

	public CSG cloneShallow() {
		return new CSG(getVertices().clone(), getTriangles().clone(), getColor());
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
	 * @param csg
	 *            other csg
	 *
	 * @return union of this csg and the specified csg
	 */
	public CSG union(CSG csg) {
		if (this.getNumberOfTriangles() > getMinPolygonsForOffloading()
				|| csg.getNumberOfTriangles() > getMinPolygonsForOffloading())
			if (CSGClient.isRunning()) {
				ArrayList<CSG> go = new ArrayList<CSG>(Arrays.asList(this, csg));
				try {
					return CSGClient.getClient().union(go).get(0);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		// triangulate();
		// csg.triangulate();
		switch (getOptType()) {
			case Manifold3d :
				try {
					return getManifold().union(this, csg);
				} catch (Throwable e) {
					System.err.println("ERROR failing over to Java Union " + e.getMessage());
					e.printStackTrace();
				}
			case CSG_BOUND :
				return _unionCSGBoundsOpt(csg).historySync(this).historySync(csg);
			// case POLYGON_BOUND:
			// return _unionPolygonBoundsOpt(csg).historySync(this).historySync(csg);
			default :
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
	 * @param csg
	 *            csg
	 *
	 * @return a csg consisting of the polygons of this csg and the specified csg
	 * @throws ColinearPointsException
	 */
	public CSG dumbUnion(CSG csg) {
		if (defaultOptType == OptType.Manifold3d) {
			// in manifold mode, take no action that could become non-manifold
			return union(csg);
		}
		// boolean tri = triangulated && csg.triangulated;
		CSG result = this.clone();
		CSG other = csg.clone();

		ArrayList<Polygon> polygonsFromMesh;
		try {
			polygonsFromMesh = result.generatePolygonsFromMesh();
			polygonsFromMesh.addAll(other.generatePolygonsFromMesh());
			bounds = null;
			// result.triangulated = tri;
			return new CSG(polygonsFromMesh).historySync(csg).historySync(this);
		} catch (ColinearPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return this;
		}

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
	 * @param csgs
	 *            other csgs
	 *
	 * @return union of this csg and the specified csgs
	 */
	public CSG union(List<CSG> incoming) {
		if (CSGClient.isRunning()) {
			ArrayList<CSG> go = new ArrayList<CSG>();
			go.add(this);
			go.addAll(incoming);
			try {
				return CSGClient.getClient().union(go).get(0);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		if (defaultOptType == OptType.Manifold3d) {
			ArrayList<CSG> values = new ArrayList<CSG>(incoming);
			values.add(this);
			try {
				return manifold.unionAll(values, progressMoniter);
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		CSG solid = this.isHole() ? null : this;
		CSG hole = this.isHole() ? this : null;
		ArrayList<CSG> csgs = new ArrayList<CSG>();
		CSG dumb = null;
		if (incoming.size() < 10) {
			csgs.addAll(incoming);
		} else {
			for (int i = 0; i < incoming.size(); i++) {
				CSG test = incoming.get(i);
				if (test == null)
					continue;
				boolean touching = false;
				// int t=-1;
				for (int j = 0; j < incoming.size(); j++) {
					if (i == j)
						continue;
					if (test.isBoundsTouching(incoming.get(j))) {
						touching = true;
						// t=j;
						break;
					}
				}
				if (touching) {
					csgs.add(test);
				} else {
					if (dumb == null) {
						dumb = test;
					} else
						dumb = dumb.dumbUnion(test);
				}

			}
		}

		for (int i = 0; i < csgs.size(); i++) {
			CSG csg = csgs.get(i);
			if (!csg.isHole()) {
				if (solid != null)
					solid = solid.union(csg);
				else
					solid = csg;
				if (Thread.interrupted())
					break;
				getProgressMoniter().progressUpdate(i, csgs.size(), "Union solid", solid);
			} else {
				if (hole != null)
					hole = hole.union(csg);
				else
					hole = csg;
				hole.setIsHole(true);
				if (Thread.interrupted())
					break;
				getProgressMoniter().progressUpdate(i, csgs.size(), "Union hole", hole);
			}
		}
		CSG result = null;
		if (solid != null && hole == null)
			result = solid;
		else if (solid == null && hole != null)
			result = hole;
		else
			result = solid.difference(hole);
		if (dumb != null) {
			result = result.dumbUnion(dumb);
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
	 * @param csgs
	 *            other csgs
	 *
	 * @return union of this csg and the specified csgs
	 */
	public CSG union(CSG... csgs) {
		return union(new ArrayList<CSG>(Arrays.asList(csgs)));
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

		if (CSGClient.isRunning()) {
			boolean offload = false;
			for (int i = 0; i < csgs.size(); i++)
				if (csgs.get(i).getNumberOfTriangles() > getMinPolygonsForOffloading()) {
					offload = true;
					break;
				}
			if (offload) {
				List<CSG> back;
				try {
					back = CSGClient.getClient().union(csgs);
					return back.get(0);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		if (defaultOptType == OptType.Manifold3d) {

			try {
				return manifold.unionAll(csgs, progressMoniter);
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
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
	 * @param csgs
	 *            csgs
	 * @return the convex hull of this csg and the specified csgs
	 */
	public CSG hull(List<CSG> csgs) {
		ArrayList<Vector3d> points = new ArrayList<Vector3d>();

		for (CSG c : csgs) {
			for (int i = 0; i < c.getVertCount(); i++) {
				points.add(c.getVertexByIndex(i));
			}
		}

		return HullUtil.hull(points, str);
	}

	/**
	 * Returns the convex hull of this csg and the union of the specified csgs.
	 *
	 * @param csgs
	 *            csgs
	 * @return the convex hull of this csg and the specified csgs
	 */
	public CSG hull(CSG... csgs) {

		return hull(Arrays.asList(csgs));
	}

	/**
	 * _union csg bounds opt.
	 *
	 * @param csg
	 *            the csg
	 * @return the csg
	 */
	private CSG _unionCSGBoundsOpt(CSG csg) {
		// com.neuronrobotics.sdk.common.Log.error("WARNING: using " + CSG.OptType.NONE
		// + " since other optimization types missing for union operation.");
		try {
			return _unionIntersectOpt(csg);
		} catch (ColinearPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return this;
		}
	}

	/**
	 * _union polygon bounds opt.
	 *
	 * @param csg
	 *            the csg
	 * @return the csg
	 */
	// private CSG _unionPolygonBoundsOpt(CSG csg) {
	// ArrayList<Polygon> inner = new ArrayList<>();
	// ArrayList<Polygon> outer = new ArrayList<>();
	//
	// Bounds b = csg.getBounds();
	//
	// this.getPolygons().stream().forEach((p) -> {
	// if (b.intersects(p.getBounds())) {
	// inner.add(p);
	// } else {
	// outer.add(p);
	// }
	// });
	//
	// ArrayList<Polygon> allPolygons = new ArrayList<>();
	//
	// if (!inner.isEmpty()) {
	// CSG innerCSG = CSG.fromPolygons(inner);
	//
	// allPolygons.addAll(outer);
	// allPolygons.addAll(innerCSG._unionNoOpt(csg).getPolygons());
	// } else {
	// allPolygons.addAll(this.getPolygons());
	// allPolygons.addAll(csg.getPolygons());
	// }
	// bounds = null;
	// CSG back = CSG.fromPolygons(allPolygons).optimization(getOptType());
	// if (getName().length() != 0 && csg.getName().length() != 0) {
	// back.setName(name);
	// }
	// return back;
	// }

	/**
	 * Optimizes for intersection. If csgs do not intersect create a new csg that
	 * consists of the polygon lists of this csg and the specified csg. In this case
	 * no further space partitioning is performed.
	 *
	 * @param csg
	 *            csg
	 * @return the union of this csg and the specified csg
	 * @throws ColinearPointsException
	 */
	private CSG _unionIntersectOpt(CSG csg) throws ColinearPointsException {
		boolean intersects = false;

		Bounds bounds = csg.getBounds();

		for (Polygon p : generatePolygonsFromMesh()) {
			if (bounds.intersects(p.getBounds())) {
				intersects = true;
				break;
			}
		}

		if (intersects) {
			return _unionNoOpt(csg);
		} else {
			return dumbUnion(csg);
		}

	}

	/**
	 * _union no opt.
	 *
	 * @param csg
	 *            the csg
	 * @return the csg
	 * @throws Exception
	 */
	private CSG _unionNoOpt(CSG csg) {
		if (this.getNumberOfTriangles() == 0)
			return csg.clone();
		if (csg.getNumberOfTriangles() == 0)
			return this.clone();
		Node a;
		try {
			ArrayList<Polygon> thisPoly = generatePolygonsFromMesh();
			ArrayList<Polygon> otherPoly = csg.generatePolygonsFromMesh();

			a = new Node(thisPoly, thisPoly.get(0).getPlane());
			Node b = new Node(otherPoly, otherPoly.get(0).getPlane());
			a.clipTo(b);
			b.clipTo(a);
			b.invert();
			b.clipTo(a);
			b.invert();
			a.build(b.allPolygons());
			CSG back = new CSG(a.allPolygons());
			if (getName().length() != 0 && csg.getName().length() != 0) {
				back.setName(name);
			}
			return back;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return dumbUnion(csg);
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
	 * @param csgs
	 *            other csgs
	 * @return difference of this csg and the specified csgs
	 */
	public CSG difference(List<CSG> csgs) {
		if (CSGClient.isRunning()) {
			ArrayList<CSG> go = new ArrayList<CSG>();
			go.add(this);
			go.addAll(csgs);
			try {
				return CSGClient.getClient().difference(go).get(0);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
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
	 * @param csgs
	 *            other csgs
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
	 * @param csg
	 *            other csg
	 * @return difference of this csg and the specified csg
	 */
	public CSG difference(CSG csg) {
		if (this.getNumberOfTriangles() > getMinPolygonsForOffloading()
				|| csg.getNumberOfTriangles() > getMinPolygonsForOffloading())
			if (CSGClient.isRunning()) {
				ArrayList<CSG> go = new ArrayList<CSG>(Arrays.asList(this, csg));
				try {
					return CSGClient.getClient().difference(go).get(0);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		// triangulate();
		// csg.triangulate();
		try {
			// Check to see if a CSG operation is attempting to difference with
			// no
			// polygons
			if (this.getNumberOfTriangles() > 0 && csg.getNumberOfTriangles() > 0) {
				switch (getOptType()) {
					case Manifold3d :
						try {
							return getManifold().difference(this, csg);
						} catch (Throwable e) {
							System.err.println("ERROR failing over to Java Difference " + e.getMessage());
							e.printStackTrace();
						}
					case CSG_BOUND :
						return _differenceCSGBoundsOpt(csg).historySync(this).historySync(csg);
					default :
						return _differenceNoOpt(csg).historySync(this).historySync(csg);

				}
			} else
				return this;
		} catch (Exception ex) {
			ex.printStackTrace();
			return this;
		}

	}

	/**
	 * _difference csg bounds opt.
	 *
	 * @param csg
	 *            the csg
	 * @return the csg
	 */
	private CSG _differenceCSGBoundsOpt(CSG csg) {
		CSG a1 = this._differenceNoOpt(csg.getBounds().toCSG());
		CSG a2 = this.intersect(csg.getBounds().toCSG());

		CSG result = null;
		if (a2.getNumberOfTriangles() > 0)
			try {
				result = a2._differenceNoOpt(csg)._unionIntersectOpt(a1);
			} catch (ColinearPointsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				result = this;
			}
		else
			result = a1;
		if (getName().length() != 0 && csg.getName().length() != 0) {
			result.setName(name);
		}
		result.setColor(getColor());
		return result;
	}

	/**
	 * _difference polygon bounds opt.
	 *
	 * @param csg
	 *            the csg
	 * @return the csg
	 */
	// private CSG _differencePolygonBoundsOpt(CSG csg) {
	// ArrayList<Polygon> inner = new ArrayList<>();
	// ArrayList<Polygon> outer = new ArrayList<>();
	//
	// Bounds bounds = csg.getBounds();
	//
	// this.getPolygons().stream().forEach((p) -> {
	// if (bounds.intersects(p.getBounds())) {
	// inner.add(p);
	// } else {
	// outer.add(p);
	// }
	// });
	//
	// CSG innerCSG = CSG.fromPolygons(inner);
	//
	// ArrayList<Polygon> allPolygons = new ArrayList<>();
	// allPolygons.addAll(outer);
	// allPolygons.addAll(innerCSG._differenceNoOpt(csg).getPolygons());
	// CSG BACK = CSG.fromPolygons(allPolygons).optimization(getOptType());
	// if (getName().length() != 0 && csg.getName().length() != 0) {
	// BACK.setName(name);
	// }
	// return BACK;
	// }

	/**
	 * _difference no opt.
	 *
	 * @param csg
	 *            the csg
	 * @return the csg
	 */
	private CSG _differenceNoOpt(CSG csg) {

		Node a;
		try {
			ArrayList<Polygon> thisPoly = generatePolygonsFromMesh();
			ArrayList<Polygon> otherPoly = csg.generatePolygonsFromMesh();
			a = new Node(thisPoly, thisPoly.get(0).getPlane());
			Node b = new Node(otherPoly, otherPoly.get(0).getPlane());

			a.invert();
			a.clipTo(b);
			b.clipTo(a);
			b.invert();
			b.clipTo(a);
			b.invert();
			a.build(b.allPolygons());
			a.invert();

			CSG csgA = new CSG(a.allPolygons());
			if (getName().length() != 0 && csg.getName().length() != 0) {
				csgA.setName(name);
			}
			return csgA;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return this;
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
	 * @param csg
	 *            other csg
	 * @return intersection of this csg and the specified csg
	 */
	public CSG intersect(CSG csg) {
		if (this.getNumberOfTriangles() > getMinPolygonsForOffloading()
				|| csg.getNumberOfTriangles() > getMinPolygonsForOffloading())
			if (CSGClient.isRunning()) {
				ArrayList<CSG> go = new ArrayList<CSG>(Arrays.asList(this, csg));
				try {
					return CSGClient.getClient().intersect(go).get(0);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		if (getNumberOfTriangles() == 0 || csg.getNumberOfTriangles() == 0) {
			Exception ex = new Exception("Error! Intersection is invalid when one CSG has no polygons!");
			ex.printStackTrace();
			return new CSG().historySync(this).historySync(csg);
		}
		if (defaultOptType == OptType.Manifold3d) {
			try {
				return getManifold().intersection(this, csg);
			} catch (Throwable e) {
				System.err.println("ERROR failing over to Java Intersect " + e.getMessage());
				e.printStackTrace();
			}
		}

		Node a;
		try {

			ArrayList<Polygon> thisPoly = generatePolygonsFromMesh();
			ArrayList<Polygon> otherPoly = csg.generatePolygonsFromMesh();
			a = new Node(thisPoly, thisPoly.get(0).getPlane());
			Node b = new Node(otherPoly, otherPoly.get(0).getPlane());
			a.invert();
			b.clipTo(a);
			b.invert();
			a.clipTo(b);
			b.clipTo(a);
			a.build(b.allPolygons());
			a.invert();
			CSG back = new CSG(a.allPolygons()).historySync(csg).historySync(this);
			if (getName().length() != 0 && csg.getName().length() != 0) {
				back.setName(name);
			}
			return back;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return this;
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
	 * @param csgs
	 *            other csgs
	 * @return intersection of this csg and the specified csgs
	 */
	public CSG intersect(List<CSG> csgs) {
		if (CSGClient.isRunning()) {
			ArrayList<CSG> go = new ArrayList<CSG>(csgs);
			try {
				return CSGClient.getClient().intersect(go).get(0);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
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
	 * @param csgs
	 *            other csgs
	 * @return intersection of this csg and the specified csgs
	 */
	public CSG intersect(CSG... csgs) {

		return intersect(Arrays.asList(csgs));
	}

	public CSG makeManifold(boolean repair) throws ColinearPointsException, NonManifoldShapeError {
		if (getNumberOfTriangles() < 4)
			return this;
		if (getOptType() == OptType.Manifold3d) {
			try {
				manifold.checkManifold(this);
				return this;
			} catch (NonManifoldShapeError e) {
				if (repair)
					System.err.println("Shape can not be loaded as manifold, correcting");
				else
					throw e;
			} catch (Throwable e) {
				if (repair)
					e.printStackTrace();
				else
					throw new RuntimeException(e);
			}
		}
		if (this.getNumberOfTriangles() > getMinPolygonsForOffloading() && preventNonManifoldTriangles)
			if (CSGClient.isRunning()) {
				ArrayList<CSG> go = new ArrayList<CSG>(Arrays.asList(this));
				try {
					CSG csg = CSGClient.getClient().triangulate(go).get(0);
					setData(csg);
					// triangulated = true;
					return csg;
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		if (providerOf3d == null && Debug3dProvider.provider != null)
			providerOf3d = Debug3dProvider.provider;
		IDebug3dProvider start = Debug3dProvider.provider;
		Debug3dProvider.setProvider(null);
		// performTriangulation();
		// if (preventNonManifoldTriangles) {
		int added = 0;
		int itr = 1;
		if (preventNonManifoldTriangles) {

			ArrayList<Polygon> polygons = generatePolygonsFromMesh();
			do {
				long numberOfPolygons = getNumberOfTriangles();
				long np = numberOfPolygons * 3;

				int extraSpace = ExtraSpace;
				long longLength = 1 + np + ((numberOfPolygons + 1) * extraSpace);
				if (longLength > Integer.MAX_VALUE)
					new RuntimeException("Mesh too large to process with integers!").printStackTrace();
				else {
					System.err.println("Processing Mesh Manifold with " + longLength * 4 + " byte buffer");
					added = 0;
					try {
						added = runGPUMakeManifold(itr, np, (int) longLength, numberOfPolygons, polygons);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
					if (added >= 0)
						System.out.println("Manifold iteration added " + added + " points ");
					else {
						ExtraSpace += 10;
						System.out.println("Increaasing extra space to " + ExtraSpace);
					}
				}
			} while (added < 0 && itr++ < 51);
			try {
				processPolygonsToTriangles(polygons);
			} catch (ColinearPointsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// }

		// System.out.println("Complete Triangulation \n\n");
		// now all polygons are definantly triangles
		// triangulated = true;
		Debug3dProvider.setProvider(start);
		return this;
	}

	private void setData(CSG csg) {
		setVertices(csg.getVertices().clone());
		setTriangles(csg.getTriangles().clone());
	}

	private int runGPUMakeManifold(int iteration, long np, int longLength, long numPoly, ArrayList<Polygon> polygons) {
		if (iteration < 0 || np <= 0 || longLength <= 0 || numPoly <= 0)
			throw new RuntimeException("Error none of the dataa lengths can be negative nor 0");
		// Flattened approach - more Aparapi-friendly
		int numberOfPolygons = (int) numPoly;
		int extraSpace = ExtraSpace;
		int numberOfPointsWithExtra = longLength;
		int numberOfPoints = (int) np;
		// Flattened arrays instead of objects
		float[] pointDataX = new float[numberOfPoints];
		float[] pointDataY = new float[numberOfPoints];
		float[] pointDataZ = new float[numberOfPoints];
		int[] lookup = new int[numberOfPoints];
		int[] polygonPointOrder = new int[numberOfPointsWithExtra];
		// int[] newPointsForPoly = new int[numberOfPolygons * ExtraSpace];
		Vector3d[] orderedPoints = new Vector3d[numberOfPoints];

		// Polygon structure - flattened
		int[] polyStartIndex = new int[numberOfPolygons];
		int[] polySizes = new int[numberOfPolygons];
		// int[] flatArrayOfAllPointIndexes = new int[numberOfPoints];
		float[] done = new float[numberOfPolygons];
		// Fill the flattened arrays
		int totalIndex = 0;
		for (int ii = 0; ii < numberOfPointsWithExtra; ii++) {
			polygonPointOrder[ii] = -1;
		}
		for (int polyIndex = 0; polyIndex < numberOfPolygons; polyIndex++) {
			int polySize = polygons.get(polyIndex).getPoints().size();
			int i = totalIndex + (polyIndex * extraSpace);
			polyStartIndex[polyIndex] = i;
			polySizes[polyIndex] = polySize;

			for (int ii = 0; ii < polySize; ii++) {
				Vector3d pos = polygons.get(polyIndex).getVertices().get(ii).pos;
				orderedPoints[totalIndex] = pos;
				pointDataX[totalIndex] = (float) pos.x;
				pointDataY[totalIndex] = (float) pos.y;
				pointDataZ[totalIndex] = (float) pos.z;
				int i2 = totalIndex + (polyIndex * extraSpace);
				polygonPointOrder[i2] = totalIndex;
				lookup[totalIndex] = i2;// lookup address in the polygon aray for the origin of each point
				totalIndex++;
			}
		}

		// System.out.println("Data loaded!");
		float eps = (float) POINTS_CONTACT_DISTANCE;
		float epsSq = (float) (eps * eps);
		int[] added = new int[numberOfPolygons];
		int testPointChunk = 1000;
		int snapChunk = 1000;
		int[] tp = new int[]{0, snapChunk};

		// Aparapi-compatible kernel with flattened data
		Kernel snapPointsToDistance = new Kernel() {
			@Override
			public void run() {
				int mePointIndex = getGlobalId();
				int mePolygonIndex = lookup[mePointIndex];
				float meX = pointDataX[mePointIndex];
				float meY = pointDataY[mePointIndex];
				float meZ = pointDataZ[mePointIndex];
				for (int i = tp[0]; (i < tp[1] && i < polySizes.length); i++) {
					int polyStart = polyStartIndex[i];
					int polySize = polySizes[i];

					for (int j = 0; j < polySize; j++) {
						int nowPolygonIndex = polyStart + j;
						int nowPointIndex = polygonPointOrder[nowPolygonIndex];
						if (nowPointIndex == mePointIndex)
							continue;
						float nowX = pointDataX[nowPointIndex];
						float nowY = pointDataY[nowPointIndex];
						float nowZ = pointDataZ[nowPointIndex];

						// Calculate distance squared inline
						float dx = nowX - meX;
						float dy = nowY - meY;
						float dz = nowZ - meZ;
						float distSq = dx * dx + dy * dy + dz * dz;

						// Use simple comparison
						if (distSq < epsSq) {
							if (mePointIndex > nowPointIndex) {
								polygonPointOrder[mePolygonIndex] = nowPointIndex;
								return;
							}
						}
					}
				}
			}
		};

		// gpuRun(numberOfPoints, snapPointsToDistance, done, "Snap Points Itr(" +
		// iteration + ")", () -> {
		// tp[0] += snapChunk;
		// tp[1] += snapChunk;
		// if (tp[1] > polySizes.length)
		// tp[1] = polySizes.length;
		// if (tp[0] < polySizes.length)
		// return true;
		// return false;
		// }, iteration, polySizes.length / snapChunk);
		tp[0] = 0;
		tp[1] = testPointChunk;
		HashSet<Integer> unique = new HashSet<Integer>();
		// int running = 0;
		for (int i = 0; i < numberOfPolygons; i++) {
			int ps = polyStartIndex[i];
			int size = polySizes[i];
			for (int j = ps; j < ps + size; j++) {
				if (polygonPointOrder[j] < 0) {
					throw new RuntimeException("Point unification currupted data");
				} else {
					unique.add(polygonPointOrder[j]);
					// flatArrayOfAllPointIndexes[running++]=polygonPointOrder[j];
				}
			}
		}
		int[] uniquePoints = new int[unique.size()];
		int b = 0;
		for (int val : unique) {
			uniquePoints[b++] = val;
		}

		Kernel findNonManifoldPoints = new Kernel() {
			@Override
			public void run() {

				int mePoly = getGlobalId();

				int polyStart = polyStartIndex[mePoly];
				int originalPolySize = polySizes[mePoly]; // Store original size
				added[mePoly] = 0;
				done[mePoly] = 0;
				// Add bounds check
				int length = polygonPointOrder.length;
				int i = polyStart + originalPolySize + extraSpace;
				if (i >= length) {
					added[mePoly] = -1;
					return;
				}
				boolean skip = false;
				int firstIndex = 0;
				int secondIndex = 0;
				for (int j = 0; j < originalPolySize + extraSpace; j++) {
					skip = false;
					done[mePoly] = (float) j / (float) (originalPolySize + extraSpace);
					if (added[mePoly] < 0 || j >= originalPolySize + added[mePoly]) {
						skip = true;
					} else {

						firstIndex = j;
						secondIndex = firstIndex + 1;
						if (secondIndex == originalPolySize + added[mePoly]) { // Use original size
							secondIndex = 0; // Wrap to start of shifted array
						}
						int aPointIndex = 0;
						int bPointIndex = 0;
						// Bounds check before accessing array
						if (firstIndex + polyStart >= length || secondIndex + polyStart >= length) {
							added[mePoly] = -1;
							skip = true;
						}
						if (!skip) {
							for (int tpInc = tp[0]; tpInc < tp[1] && tpInc < uniquePoints.length; tpInc++) {
								int testPointIndex = uniquePoints[tpInc];
								skip = false;
								for (int px = 0; px < originalPolySize + added[mePoly]; px++) {
									if (polygonPointOrder[polyStart + px] == testPointIndex) {
										skip = true;
									}
								}
								if (added[mePoly] < 0) {
									skip = true;
								}

								if (!skip) {
									aPointIndex = polygonPointOrder[firstIndex + polyStart];
									bPointIndex = polygonPointOrder[secondIndex + polyStart];
								}
								if (!skip) {
									float nowX = pointDataX[testPointIndex];
									float nowY = pointDataY[testPointIndex];
									float nowZ = pointDataZ[testPointIndex];
									float aX = pointDataX[aPointIndex];
									float aY = pointDataY[aPointIndex];
									float aZ = pointDataZ[aPointIndex];

									float bX = pointDataX[bPointIndex];
									float bY = pointDataY[bPointIndex];
									float bZ = pointDataZ[bPointIndex];

									// Vector from point A to point B (line direction)
									float abX = bX - aX;
									float abY = bY - aY;
									float abZ = bZ - aZ;

									// Vector from point A to test point
									float anX = nowX - aX;
									float anY = nowY - aY;
									float anZ = nowZ - aZ;

									// Calculate dot product of AB and AN
									float dotProduct = abX * anX + abY * anY + abZ * anZ;

									// Calculate squared length of AB
									float abLengthSquared = abX * abX + abY * abY + abZ * abZ;

									if (abLengthSquared > epsSq) { // Avoid division by zero
										// Calculate parameter t for closest point on line
										float t = dotProduct / abLengthSquared;

										// Check if the projection falls within the line segment
										if (t >= 0 && t <= 1.0f) {
											// Calculate the point on the line segment
											float linePointX = aX + t * abX;
											float linePointY = aY + t * abY;
											float linePointZ = aZ + t * abZ;

											// Check if test point is essentially the same as the line point
											float diffX = nowX - linePointX;
											float diffY = nowY - linePointY;
											float diffZ = nowZ - linePointZ;

											// Use squared distance to avoid square root calculation
											float distanceSquared = diffX * diffX + diffY * diffY + diffZ * diffZ;
											// Point is touching the line segment
											if (distanceSquared <= eps * eps) {
												// Bounds check before insertion
												if (polyStart + originalPolySize + added[mePoly] + 1 < length) {
													// When a point is found to be on the line
													// move all the items in the array to make room,
													// unless the second index is a wrapping item.
													// In the wrap case we simply set the second
													// index to the new empty space in the buffer.
													// This ensures that the new line segment between first
													// and the new point, as well as the new point and the
													// wrapping condition in the next iteration of the outer
													// polygon segment loop will be checked against all points.
													// the remaining points in the unique points buffer are checked
													// against the line from the first index, and this new added
													// point.
													// As new points are added, closer and closer to this first
													// point, the
													// subsequent vectors will be checked in the longer running of
													// the outer loop.
													// Be careful when changing this!
													if (secondIndex != 0) {
														for (int indexOfTheMovingItem = originalPolySize
																+ added[mePoly]; indexOfTheMovingItem > secondIndex; indexOfTheMovingItem--) {
															int targetPolygonIndex = indexOfTheMovingItem + polyStart;
															int sourcePolygonIndex = targetPolygonIndex - 1;
															polygonPointOrder[targetPolygonIndex] = polygonPointOrder[sourcePolygonIndex];
														}
													} else {
														secondIndex = firstIndex + 1;
													}
													polygonPointOrder[secondIndex + polyStart] = testPointIndex;
													polySizes[mePoly] += 1;
													added[mePoly] += 1;

													if (added[mePoly] >= extraSpace - 1) {
														added[mePoly] = -1;
													}
													// Test for invalid indexes in the polygon
													for (int test = 0; test < polySizes[mePoly]; test++) {
														if (polygonPointOrder[test + polyStart] < 0) {
															added[mePoly] = -1;
														}
													}
												} else {
													// No room for insertion
													added[mePoly] = -1;
												} // check for room in the polygon point buffer
											} // verify the point is on the line
										} // test is the point is between the given points
									} // test if the length of the segment is on the line
								} // skip the comparison
							} // For each Point In Unique points
						} // second skip check
					} // Skip loop check
				} // For loop all points in polygon
			}// Run method
		};
		pointsAdded = 0;

		gpuRun(numberOfPolygons, findNonManifoldPoints, done, "Manifold Itr(" + iteration + ")", () -> {
			// for (int tp = 0; tp < uniquePoints.length; tp++)
			// Iterate through each of the test points in host thread
			tp[0] += testPointChunk;
			tp[1] += testPointChunk;
			if (tp[1] > uniquePoints.length)
				tp[1] = uniquePoints.length;
			if (tp[0] < uniquePoints.length)
				return true;
			pointsAdded = 0;
			String out = "points added report [";
			for (int x = 0; x < added.length; x++) {
				if (added[x] < 0) {
					progressMoniter.progressUpdate(1, 1,
							"\n\nManifold failed after " + x + " of " + numberOfPolygons + " polygons ", this);
					pointsAdded = -1;
					break;
				} else {
					pointsAdded += added[x];
					if (added[x] > 0 && out.length() < 300)
						out += " to " + x + " added " + (added[x]) + " size " + polySizes[x] + " , ";
				}
			}
			out += "]";
			out = "Total added " + pointsAdded + " " + out;
			if (pointsAdded > 0) {
				progressMoniter.progressUpdate(1, 1, out, this);
				// return true;
			}
			return false;
		}, iteration, uniquePoints.length / testPointChunk);

		ArrayList<Polygon> newPoly = new ArrayList<>();
		for (int i = 0; i < getNumberOfTriangles(); i++) {
			Polygon polygon = polygons.get(i);
			Plane pl = polygon.plane;
			ArrayList<Vertex> points = new ArrayList<Vertex>();
			int startIndex = polyStartIndex[i];
			int polySize = polySizes[i];
			// HashSet<Integer> pointIndexSet = new HashSet<Integer>();
			for (int j = 0; j < polySize; j++) {
				int pointIndex = polygonPointOrder[startIndex + j];
				if (pointIndex < 0) {
					new RuntimeException("Algorithm error").printStackTrace();
					continue;
				}
				// if (pointIndexSet.contains(pointIndex)) {
				// System.out.println("ERR polygon " + i + " already has a point " +
				// pointIndex);
				// continue;
				// }
				// pointIndexSet.add(pointIndex);
				Vector3d thispoint = orderedPoints[pointIndex];
				points.add(new Vertex(thispoint));
			}
			if (points.size() < 3) {
				System.out.println("ERR polygon " + i + " pruned because of too few points");
				continue;
			}
			Polygon p;
			try {
				p = new Polygon(points, polygon.getStorage(), true, pl);
				newPoly.add(p);
			} catch (ColinearPointsException e) {
				System.err.println("Pruning " + points);
				e.printStackTrace();
			}
			polygon.getPoints().clear();
		}
		polygons.clear();
		polygons.addAll(newPoly);
		return pointsAdded;
	}

	public static List<ForkJoinWorkerThread> getForkJoinWorkers(ForkJoinPool pool) {
		return Thread.getAllStackTraces().keySet().stream().filter(thread -> thread instanceof ForkJoinWorkerThread)
				.map(thread -> (ForkJoinWorkerThread) thread).collect(Collectors.toList());
	}

	public static void setPrivateThreadPool(KernelRunner kernelRunner, ForkJoinPool newThreadPool) throws Exception {
		// Get the class of the object
		Class<?> clazz = kernelRunner.getClass();

		// Get the private field
		Field threadPoolField = clazz.getDeclaredField("threadPool");

		// Make it accessible
		threadPoolField.setAccessible(true);

		// Set the new value
		threadPoolField.set(kernelRunner, newThreadPool);
	}

	public static ForkJoinPool getPrivateThreadPool(KernelRunner kernelRunner) throws Exception {
		Class<?> clazz = kernelRunner.getClass();
		Field threadPoolField = clazz.getDeclaredField("threadPool");
		threadPoolField.setAccessible(true);
		return (ForkJoinPool) threadPoolField.get(kernelRunner);
	}

	public static void gpuRun(int numberOfPoints, Kernel kernel, float[] done, String type, BooleanSupplier test,
			int itr, int expectedIterations) {

		// progressMoniter.progressUpdate(0, 100, "Start " + typOfCPU(kernel) + type,
		// null);
		String valueOf = String.valueOf(Runtime.getRuntime().availableProcessors());
		System.setProperty("com.aparapi.threadPoolSize", valueOf);
		if (!useGPU) {
			progressMoniter.progressUpdate(0, 100, "CPU mode " + valueOf, null);
			kernel.setExecutionMode(Kernel.EXECUTION_MODE.JTP); // Java Thread Pool
		}
		int[] iteration = new int[]{0};

		long begin = System.currentTimeMillis();
		boolean print = false;
		long timeSinceLastPrint = 0;
		long printLimit = 800;
		String typOfCPU = typOfCPU(kernel);

		try {
			do {
				KernelRunner kernelRunner = new KernelRunner(kernel);
				if (poolGlobal == null)
					poolGlobal = getPrivateThreadPool(kernelRunner);
				else
					setPrivateThreadPool(kernelRunner, poolGlobal);

				kernelRunner.execute("run", Range.create(null, numberOfPoints), 1);
				typOfCPU = typOfCPU(kernel);
				iteration[0] += 1;
				long now = System.currentTimeMillis();
				long sinceStart = now - begin;
				long took = sinceStart / iteration[0];
				long expected = took * (expectedIterations + 2);
				print = expected > printLimit || print;
				if (print) {
					if (now - timeSinceLastPrint > printLimit) {
						timeSinceLastPrint = now;
						long remaining = expected - sinceStart;
						if (done != null)
							for (int i = 0; i < done.length; i++) {
								done[i] = 0;
							}
						if (remaining < 0)
							remaining = 0;
						String dur = makeTimestamp(expected);
						String rem = makeTimestamp(remaining);
						progressMoniter.progressUpdate(iteration[0], expectedIterations,
								"Rem->" + rem + " " + type + typOfCPU + " \nTot->" + dur, null);
					}
				}
				Thread.sleep(16);
			} while (test.getAsBoolean());
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		boolean executing;
		do {
			executing = kernel.isExecuting();
		} while (executing);
		long sinceStart = System.currentTimeMillis() - begin;
		if (print)
			progressMoniter.progressUpdate(100, 100,
					"Took " + makeTimestamp(sinceStart) + " Finished " + type + " on " + typOfCPU, null);

	}

	private static String typOfCPU(Kernel kernel) {
		return useGPU ? "(" + kernel.getTargetDevice().getType().toString() + ")" : "(CPU)";
	}

	private static String makeTimestamp(long expected) {
		Duration duration = Duration.ofMillis(expected);

		long hours = duration.toHours();
		long minutes = duration.toMinutes() % 60;
		long seconds = duration.getSeconds() % 60;
		long ms = duration.getNano() / 1000000;
		if (hours > 0) {
			return String.format(Locale.US, "h%02d:m%02d", hours, minutes);
		}
		if (minutes > 0)
			return String.format(Locale.US, "m%02d:s%02d", minutes, seconds);
		if (seconds > 0)
			return String.format(Locale.US, "s%02d:ms%03d", seconds, ms);
		String dur = String.format(Locale.US, "ms%03d", ms);
		return dur;
	}

	private void updatePolygons(ArrayList<Polygon> toAdd, Polygon p) {

		if (p.getVertices().size() == 3) {
			toAdd.add(p);
		} else {

			List<Polygon> triangles;
			try {
				triangles = PolygonUtil.triangulatePolygon(p);
				for (Polygon poly : triangles) {
					toAdd.add(poly);
				}
			} catch (ColinearPointsException e) {
				System.out.println(e.getMessage() + " Polygon pruned " + p);
			}

		}
		return;
	}

	/**
	 * Color.
	 *
	 * @param c
	 *            the c
	 * @return the csg
	 */
	public CSG color(Color c) {
		getStorage().set("material:color", "" + c.getRed() + " " + c.getGreen() + " " + c.getBlue());

		return this;
	}

	/**
	 * Reverse the winding order of all the triangles
	 */
	private void flip() {
		for (int i = 0; i < getTriCount(); i++) {
			long a = getTriangles()[i * 3 + 1];
			long b = getTriangles()[i * 3 + 2];
			getTriangles()[i * 3 + 1] = b;
			getTriangles()[i * 3 + 2] = a;
		}
	}

	/**
	 * Returns a transformed copy of this CSG.
	 *
	 * @param transform
	 *            the transform to apply
	 *
	 * @return a transformed copy of this CSG
	 */
	public CSG transformed(Transform transform) {
		// if( isMotionLock())
		// return this.clone();
		if (getNumberOfTriangles() == 0) {
			return clone();
		}
		CSG csg = clone();

		for (int i = 0; i < csg.getVertCount(); i++) {
			double vectx = csg.getVertices()[i * 3];
			double vecty = csg.getVertices()[i * 3 + 1];
			double vectz = csg.getVertices()[i * 3 + 2];
			double prevX = vectx;
			double prevY = vecty;
			double prevZ = vectz;

			final double x, y;
			x = transform.getInternalMatrix().m00 * vectx + transform.getInternalMatrix().m01 * vecty
					+ transform.getInternalMatrix().m02 * vectz + transform.getInternalMatrix().m03;
			y = transform.getInternalMatrix().m10 * vectx + transform.getInternalMatrix().m11 * vecty
					+ transform.getInternalMatrix().m12 * vectz + transform.getInternalMatrix().m13;
			vectz = transform.getInternalMatrix().m20 * vectx + transform.getInternalMatrix().m21 * vecty
					+ transform.getInternalMatrix().m22 * vectz + transform.getInternalMatrix().m23;
			vectx = x;
			vecty = y;

			double diffX = vectx - prevX;
			double diffY = vecty - prevY;
			double diffZ = vectz - prevZ;

			csg.getVertices()[i * 3] = prevX + (diffX);
			csg.getVertices()[i * 3 + 1] = prevY + (diffY);
			csg.getVertices()[i * 3 + 2] = prevZ + (diffZ);
		}
		if (transform.isMirror()) {
			csg.flip();
		}

		if (getName().length() != 0) {
			csg.setName(name);
		}

		return csg.historySync(this);
	}

	/**
	 * To java fx mesh.
	 *
	 * @param interact
	 *            the interact
	 * @return the mesh container
	 * @throws ColinearPointsException
	 */
	// TODO finish experiment (20.7.2014)
	public Mesh toJavaFXMesh(CadInteractionEvent interact) {

		return toJavaFXMeshSimple(interact);

	}

	/**
	 * Returns the CSG as JavaFX triangle mesh.
	 *
	 * @param interact
	 *            the interact
	 * @return the CSG as JavaFX triangle mesh
	 * @throws ColinearPointsException
	 */
	public Mesh toJavaFXMeshSimple(CadInteractionEvent interact) {

		return CSGtoJavafx.meshFromPolygon(this);
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

		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;

		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;

		// for (Polygon p : getPolygons()) {

		for (int i = 0; i < getVertCount(); i++) {

			Vector3d vert = getVertexByIndex(i);

			if (vert.x < minX) {
				minX = vert.x;
			}
			if (vert.y < minY) {
				minY = vert.y;
			}
			if (vert.z < minZ) {
				minZ = vert.z;
			}

			if (vert.x > maxX) {
				maxX = vert.x;
			}
			if (vert.y > maxY) {
				maxY = vert.y;
			}
			if (vert.z > maxZ) {
				maxZ = vert.z;
			}

		} // end for vertices

		// } // end for polygon

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
		return defaultOptType;
	}

	/**
	 * Sets the default opt type.
	 *
	 * @param optType
	 *            the optType to set
	 */
	public static void setDefaultOptType(OptType optType) {
		if (optType == OptType.Manifold3d) {
			try {
				setManifold(new CSGManifold3d());
				Slice.setSliceEngine(new ISlice() {
					@Override
					public List<Polygon> slice(CSG incoming, Transform slicePlane, double normalInsetDistance)
							throws ColinearPointsException {
						try {
							return getManifold().sliceAtZero(incoming, slicePlane);
						} catch (Throwable e) {
							System.err.println("Slice failed on manifold, using legacy slice");
							e.printStackTrace();
							Slice.setSliceEngine(null);
							Slice.getSliceEngine();// set the default when the engine is null
							return new DefaultSliceImp().slice(incoming, slicePlane, normalInsetDistance);
						}
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
				optType = defaultOptType;
			}
		} else {
			Slice.setSliceEngine(null);
			Slice.getSliceEngine();// set the default when the engine is null
		}
		defaultOptType = optType;
	}

	/**
	 * Hail Zeon! In case you forget the name of minkowski and are a Gundam fan
	 *
	 * @param travelingShape
	 * @return
	 * @throws ColinearPointsException
	 */
	@Deprecated
	public ArrayList<CSG> minovsky(CSG travelingShape) throws ColinearPointsException {
		// com.neuronrobotics.sdk.common.Log.error("Hail Zeon!");
		return minkowski(travelingShape);
	}

	/**
	 * Shortened name In case you forget the name of minkowski
	 *
	 * @param travelingShape
	 * @return
	 * @throws ColinearPointsException
	 */
	public ArrayList<CSG> mink(CSG travelingShape) throws ColinearPointsException {
		return minkowski(travelingShape);
	}

	/**
	 * This is a simplified version of a minkowski transform using convex hull and
	 * the internal list of convex polygons The shape is placed at the vertex of
	 * each point on a polygon, and the result is convex hulled together. This
	 * collection is returned. To make a normal insets, difference this collection
	 * To make an outset by the normals, union this collection with this object.
	 *
	 * @param travelingShape
	 *            a shape to sweep around
	 * @return
	 * @throws ColinearPointsException
	 */
	public ArrayList<CSG> minkowskiHullShape(CSG travelingShape) throws ColinearPointsException {
		if (CSGClient.isRunning()) {
			ArrayList<CSG> go = new ArrayList<CSG>(Arrays.asList(this, travelingShape));
			try {
				return CSGClient.getClient().minkowskiHullShape(go);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (defaultOptType == OptType.Manifold3d) {
			try {
				CSG mink = manifold.minkowski_sum(this, travelingShape);
				return new ArrayList<CSG>(Arrays.asList(mink));
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		ArrayList<CSG> bits = new ArrayList<>();
		List<Polygon> polygons2 = this.generatePolygonsFromMesh();
		int size3 = polygons2.size();
		for (int i = 0; i < size3; i++) {
			Polygon p = polygons2.get(i);
			List<Vector3d> plist = new ArrayList<>();
			List<Vertex> vertices = p.getVertices();
			int size2 = vertices.size();
			for (int j = 0; j < size2; j++) {
				Vertex v = vertices.get(j);
				CSG newSHape = travelingShape.move(v);
				List<Polygon> polygons3 = newSHape.generatePolygonsFromMesh();
				int size1 = polygons3.size();
				for (int k = 0; k < size1; k++) {
					Polygon np = polygons3.get(k);
					List<Vertex> vertices2 = np.getVertices();
					int size = vertices2.size();
					for (int l = 0; l < size; l++) {
						Vertex nv = vertices2.get(l);
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
	 * @param travelingShape
	 *            a shape to sweep around
	 * @return
	 * @throws ColinearPointsException
	 */
	public ArrayList<CSG> minkowski(CSG travelingShape) throws ColinearPointsException {
		if (defaultOptType == OptType.Manifold3d) {
			try {
				CSG mink = manifold.minkowski_sum(this, travelingShape);
				return new ArrayList<CSG>(Arrays.asList(mink));
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		HashMap<Vertex, CSG> map = new HashMap<>();
		for (Polygon p : travelingShape.generatePolygonsFromMesh()) {
			for (Vertex v : p.getVertices()) {
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
	 * @param itemToDifference
	 *            the object that needs to fit
	 * @param minkowskiObject
	 *            the object to represent the offset
	 * @return
	 * @throws ColinearPointsException
	 */
	public CSG minkowskiDifference(CSG itemToDifference, CSG minkowskiObject) throws ColinearPointsException {
		if (defaultOptType == OptType.Manifold3d) {
			try {
				CSG mink = manifold.minkowski_sum(itemToDifference, minkowskiObject);
				return difference(mink);
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

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
	 * @param itemToDifference
	 *            the object that needs to fit
	 * @param tolerance
	 *            the tolerance distance
	 * @return
	 * @throws ColinearPointsException
	 */
	public CSG minkowskiDifference(CSG itemToDifference, double tolerance) throws ColinearPointsException {
		if (defaultOptType == OptType.Manifold3d) {
			return minkowskiDifference(itemToDifference, new Cube(tolerance).toCSG());
		}
		double shellThickness = Math.abs(tolerance);
		if (shellThickness < 0.001)
			return this.difference(itemToDifference);
		return minkowskiDifference(itemToDifference, new Sphere(shellThickness / 2.0, 8, 4).toCSG());
	}

	public CSG toolOffset(Number sn) throws ColinearPointsException {
		double shellThickness = sn.doubleValue();
		boolean cut = shellThickness < 0;
		shellThickness = Math.abs(shellThickness);
		if (shellThickness < 0.001)
			return this;
		if (defaultOptType == OptType.Manifold3d) {
			try {
				if (!cut)
					return manifold.minkowski_sum(this, new Cube(shellThickness).toCSG());
				else
					return manifold.minkowski_difference(this, new Cube(shellThickness).toCSG());
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
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

	public boolean hasManipulator() {
		return manipulator.get(uniqueId) != null;
	}

	public Affine getManipulator() throws MissingManipulatorException {
		if (!hasManipulator())
			throw new MissingManipulatorException("Can not get a manipulator that does not exist");
		return manipulator.get(uniqueId);
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
		if (getName().length() == 0)
			setName(dyingCSG.getName());
		setColor(dyingCSG.getColor());
		// str.syncProperties(dyingCSG.str);
		syncCadoodleCatagories(dyingCSG);
		return this;
	}

	public CSG syncParameter(CSGDatabaseInstance instance, CSG dyingCSG) {
		syncCadoodleCatagories(dyingCSG);
		Set<String> params = dyingCSG.getParameters(instance);
		for (String param : params) {
			boolean existing = false;
			for (String s : this.getParameters(instance)) {
				if (s.contentEquals(param))
					existing = true;
			}
			if (!existing) {
				Parameter vals = instance.get(param);
				if (vals != null)
					this.setParameter(instance, vals, dyingCSG.getMapOfparametrics(instance).get(param));
			}
		}
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
		if (manufactuingMap.get(this.getUniqueId()) == null) {
			manufactuingMap.put(this.getUniqueId(), new PrepForManufacturing() {
				@Override
				public CSG prep(CSG incoming) {
					return incoming;
				}
			});
		}
		return manufactuingMap.get(this.getUniqueId());
	}

	public PrepForManufacturing getMfg() {
		return getManufacturing();
	}

	public CSG setMfg(PrepForManufacturing manufactuing) {
		return setManufacturing(manufactuing);
	}

	public CSG setManufacturing(PrepForManufacturing manufactuing) {
		manufactuingMap.put(this.getUniqueId(), manufactuing);
		return this;
	}

	public CSG setParameter(CSGDatabaseInstance instance, Parameter w) {
		instance.setParameter(this, w);
		return this;
	}

	public CSG setParameter(CSGDatabaseInstance instance, String key, double defaultValue, double upperBound,
			double lowerBound, IParametric function) {
		instance.setParameter(this, key, defaultValue, upperBound, lowerBound, function);
		return this;
	}

	public CSG setParameter(CSGDatabaseInstance instance, Parameter w, IParametric function) {
		if (w == null)
			return this;
		instance.setParameter(this, w, function);
		return this;
	}

	public CSG setParameterIfNull(CSGDatabaseInstance instance, String key) {
		instance.setParameterIfNull(this, key);
		return this;
	}

	public Set<String> getParameters(CSGDatabaseInstance instance) {
		HashMap<String, IParametric> mapOfparametrics = instance.getMapOfparametrics(this);
		if (mapOfparametrics != null)
			return mapOfparametrics.keySet();
		return new HashSet<String>();
	}

	public CSG setParameterNewValue(CSGDatabaseInstance instance, String key, double newValue) {
		instance.setParameterNewValue(this, key, newValue);
		return this;
	}

	public HashMap<String, IParametric> getMapOfparametrics(CSGDatabaseInstance instance) {
		return instance.getMapOfparametrics(this);
	}

	public CSG setRegenerate(IRegenerate function) {
		regenerate.put(getUniqueId(), function);
		return this;
	}

	public IRegenerate getRegenerate() {
		if (regenerate.get(getUniqueId()) == null) {
			regenerate.put(getUniqueId(), new IRegenerate() {
				@Override
				public CSG regenerate(CSG previous) {
					return previous;
				}
			});

		}
		return regenerate.get(getUniqueId());
	}

	public CSG regenerate() {
		this.markForRegeneration = false;
		if (regenerate == null)
			return this;
		CSG regenerate2 = regenerate.get(getUniqueId()).regenerate(this);
		if (regenerate2 != null) {
			if (hasManipulator())
				try {
					regenerate2.setManipulator(this.getManipulator());
				} catch (MissingManipulatorException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			return regenerate2.historySync(this);
		}
		return this;
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
		if (isBoundsTouching(incoming)) {
			// Run a full intersection
			CSG inter = this.intersect(incoming);
			if (inter.getNumberOfTriangles() > 0) {
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
		return Color.web(defaultcolor);
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
	 * @param exportFormat
	 *            the exportFormat to add
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
	 * @param edgeDirection
	 *            a Vector3d object representing the direction of the edge of the
	 *            board to which tabs and fastener holes will be added
	 * @param fastener
	 *            a CSG object representing a template fastener to be added between
	 *            the tabs
	 * @return an ArrayList of CSG objects representing the original board with
	 *         added tabs and separate fastener hole objects
	 * @throws Exception
	 *             if the edgeDirection parameter is not a cartesian unit Vector3d
	 *             object or uses an unimplemented orientation
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
		return text(text, height, fontSize, Font.getDefault().getName());
	}

	public static CSG text(String text, double height) {
		return text(text, height, 30);
	}

	public static CSG text(String text, double height, double fontSize, String fontType) {
		javafx.scene.text.Font font = new javafx.scene.text.Font(fontType, fontSize);
		if (!font.getName().toLowerCase().contains(fontType.toLowerCase())) {
			String options = "";
			for (String name : javafx.scene.text.Font.getFontNames()) {
				options += name + "\n";
			}
			new Exception(options + "\nIs Not " + fontType + " instead got " + font.getName()).printStackTrace();
		}
		ArrayList<CSG> stuff = TextExtrude.text(height, text, font);
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
	 * @param text
	 *            the text to be extruded
	 * @param x
	 *            the total final X
	 * @param y
	 *            the total final Y
	 * @param z
	 *            the total final Z
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

	// public CSG setIsGroupResult(boolean res) {
	// getStorage().set("GroupResult", res);
	// return this;
	// }
	public CSG setUserDefinedName(String res) {
		getStorage().set("UserDefinedName", res);
		return this;
	}
	public CSG setUserDefinedNameIfMissing(String res) {
		if (!isUserDefinedName())
			getStorage().set("UserDefinedName", res);
		return this;
	}
	public CSG removeUserDefinedName(String res) {
		if (getStorage().getValue("UserDefinedName").isPresent()) {
			getStorage().delete("UserDefinedName");
		}
		return this;
	}

	public boolean isUserDefinedName() {
		Optional<String> o = getStorage().getValue("UserDefinedName");
		return o.isPresent();
	}
	public String getUserDefinedName() {
		Optional<String> o = getStorage().getValue("UserDefinedName");
		if (o.isPresent())
			return o.get();
		return getName();
	}

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
	public CSG setIsMotionLock(boolean Lock) {
		// if(Lock) {
		// new RuntimeException("Motion Lock Enabled here").printStackTrace();
		// }
		getStorage().set("isMotionLock", Lock);
		return this;
	}

	public boolean isMotionLock() {
		Optional<Boolean> o = getStorage().getValue("isMotionLock");
		if (o.isPresent())
			return o.get();
		return false;
	}

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

	// Hide
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

	// NoScale
	public CSG setNoScale(boolean Hide) {
		getStorage().set("NoScale", Hide);
		return this;
	}

	public boolean isNoScale() {
		Optional<Boolean> o = getStorage().getValue("NoScale");
		if (o.isPresent())
			return o.get();
		return false;
	}

	// IsAlwaysShow
	public CSG setIsAlwaysShow(boolean Hide) {
		getStorage().set("isAlwaysShow", Hide);
		return this;
	}

	public boolean isAlwaysShow() {
		Optional<Boolean> o = getStorage().getValue("isAlwaysShow");
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

	private void syncCadoodleCatagories(CSG dyingCSG) {
		setIsHole(dyingCSG.isHole());
		setIsHide(dyingCSG.isHide());
		setIsAlwaysShow(dyingCSG.isAlwaysShow());
		setIsLock(dyingCSG.isLock());
		setIsMotionLock(dyingCSG.isMotionLock());
		setIsWireFrame(dyingCSG.isWireFrame());
		setColor(dyingCSG.getColor());
		setNoScale(dyingCSG.isNoScale());
	}

	public void setDefaultCadoodleCatagories() {
		setIsHole(false);
		setIsHide(false);
		setIsAlwaysShow(false);
		setIsLock(false);
		setIsMotionLock(false);
		setIsWireFrame(false);
		setNoScale(false);
	}

	// Hole
	public CSG setLimbName(String name) {
		getStorage().set("LimbName", name);
		return this;
	}

	public Optional<String> getLimbName() {
		return getStorage().getValue("LimbName");
	}

	public CSG setMobileBaseName(String name) {
		getStorage().set("MobileBaseName", name);
		return this;
	}

	public Optional<String> getMobileBaseName() {
		return getStorage().getValue("MobileBaseName");
	}

	public CSG syncProperties(CSGDatabaseInstance instance, CSG dying) {
		getStorage().syncProperties(dying.getStorage());
		regenerate.put(uniqueId, regenerate.get(dying.uniqueId));
		setManipulator(manipulator.get(dying.uniqueId));
		syncParameter(instance, dying);
		return this;
	}

	/**
	 * Tessellates a given CSG object into a 3D grid with specified steps and grid
	 * spacing, including offsets for odd rows, columns, and layers.
	 *
	 * @param incoming
	 *            The CSG object to be tessellated.
	 * @param xSteps
	 *            Number of steps (iterations) in the x-direction.
	 * @param ySteps
	 *            Number of steps (iterations) in the y-direction.
	 * @param zSteps
	 *            Number of steps (iterations) in the z-direction.
	 * @param xGrid
	 *            Distance between iterations in the x-direction.
	 * @param yGrid
	 *            Distance between iterations in the y-direction.
	 * @param zGrid
	 *            Distance between iterations in the z-direction.
	 * @param oddRowXOffset
	 *            X offset for odd rows.
	 * @param oddRowYOffset
	 *            Y offset for odd rows.
	 * @param oddRowZOffset
	 *            Z offset for odd rows.
	 * @param oddColXOffset
	 *            X offset for odd columns.
	 * @param oddColYOffset
	 *            Y offset for odd columns.
	 * @param oddColZOffset
	 *            Z offset for odd columns.
	 * @param oddLayXOffset
	 *            X offset for odd layers.
	 * @param oddLayYOffset
	 *            Y offset for odd layers.
	 * @param oddLayZOffset
	 *            Z offset for odd layers.
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
	 * @param incoming
	 *            The CSG object to be tessellated.
	 * @param xSteps
	 *            Number of steps (iterations) in the x-direction.
	 * @param ySteps
	 *            Number of steps (iterations) in the y-direction.
	 * @param zSteps
	 *            Number of steps (iterations) in the z-direction.
	 * @param xGrid
	 *            Distance between iterations in the x-direction.
	 * @param yGrid
	 *            Distance between iterations in the y-direction.
	 * @param zGrid
	 *            Distance between iterations in the z-direction.
	 * @param offsets
	 *            3D array of offsets for odd rows, columns, and layers.
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
	 * @param incoming
	 *            The CSG object to be tessellated.
	 * @param xSteps
	 *            Number of steps (iterations) in the x-direction.
	 * @param ySteps
	 *            Number of steps (iterations) in the y-direction.
	 * @param zSteps
	 *            Number of steps (iterations) in the z-direction.
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
	 * @param incoming
	 *            The CSG object to be tessellated.
	 * @param xSteps
	 *            Number of steps (iterations) in the x-direction.
	 * @param ySteps
	 *            Number of steps (iterations) in the y-direction.
	 * @param zSteps
	 *            Number of steps (iterations) in the z-direction.
	 * @param oddRowXOffset
	 *            X offset for odd rows.
	 * @param oddRowYOffset
	 *            Y offset for odd rows.
	 * @param oddRowZOffset
	 *            Z offset for odd rows.
	 * @param oddColXOffset
	 *            X offset for odd columns.
	 * @param oddColYOffset
	 *            Y offset for odd columns.
	 * @param oddColZOffset
	 *            Z offset for odd columns.
	 * @param oddLayXOffset
	 *            X offset for odd layers.
	 * @param oddLayYOffset
	 *            Y offset for odd layers.
	 * @param oddLayZOffset
	 *            Z offset for odd layers.
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellate(CSG incoming, int xSteps, int ySteps, int zSteps, double oddRowXOffset,
			double oddRowYOffset, double oddRowZOffset, double oddColXOffset, double oddColYOffset,
			double oddColZOffset, double oddLayXOffset, double oddLayYOffset, double oddLayZOffset) {
		double[][] offsets = {{oddRowXOffset, oddRowYOffset, oddRowZOffset},
				{oddColXOffset, oddColYOffset, oddColZOffset}, {oddLayXOffset, oddLayYOffset, oddLayZOffset}};
		return tessellate(incoming, xSteps, ySteps, zSteps, incoming.getTotalX(), incoming.getTotalY(),
				incoming.getTotalZ(), offsets);
	}

	/**
	 * Tessellates a given CSG object into a 3D grid with specified steps and
	 * uniform grid spacing.
	 *
	 * @param incoming
	 *            The CSG object to be tessellated.
	 * @param steps
	 *            Number of steps (iterations) in each direction (x, y, z).
	 * @param gridSpacing
	 *            Distance between iterations in all directions (x, y, z).
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
	 * @param incoming
	 *            The CSG object to be tessellated.
	 * @param steps
	 *            Number of steps (iterations) in each direction (x, y, z).
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
	 * @param incoming
	 *            The CSG object to be tessellated.
	 * @param xSteps
	 *            Number of steps (iterations) in the x-direction.
	 * @param ySteps
	 *            Number of steps (iterations) in the y-direction.
	 * @param xGrid
	 *            Distance between iterations in the x-direction.
	 * @param yGrid
	 *            Distance between iterations in the y-direction.
	 * @param oddRowXOffset
	 *            X offset for odd rows.
	 * @param oddRowYOffset
	 *            Y offset for odd rows.
	 * @param oddColXOffset
	 *            X offset for odd columns.
	 * @param oddColYOffset
	 *            Y offset for odd columns.
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
	 * @param incoming
	 *            The CSG object to be tessellated.
	 * @param xSteps
	 *            Number of steps (iterations) in the x-direction.
	 * @param ySteps
	 *            Number of steps (iterations) in the y-direction.
	 * @param xGrid
	 *            Distance between iterations in the x-direction.
	 * @param yGrid
	 *            Distance between iterations in the y-direction.
	 * @param offsets
	 *            2D array of offsets for odd rows and columns.
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
	 * @param incoming
	 *            The CSG object to be tessellated.
	 * @param xSteps
	 *            Number of steps (iterations) in the x-direction.
	 * @param ySteps
	 *            Number of steps (iterations) in the y-direction.
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellateXY(CSG incoming, int xSteps, int ySteps) {
		return tessellateXY(incoming, xSteps, ySteps, incoming.getTotalX(), incoming.getTotalY(), 0, 0, 0, 0);
	}

	/**
	 * Tessellates a given CSG object into a 2D grid with specified steps and grid
	 * spacing.
	 *
	 * @param incoming
	 *            The CSG object to be tessellated.
	 * @param xSteps
	 *            Number of steps (iterations) in the x-direction.
	 * @param ySteps
	 *            Number of steps (iterations) in the y-direction.
	 * @param xGrid
	 *            Distance between iterations in the x-direction.
	 * @param yGrid
	 *            Distance between iterations in the y-direction.
	 * @return A list of tessellated CSG objects.
	 */
	public static List<CSG> tessellateXY(CSG incoming, int xSteps, int ySteps, double xGrid, double yGrid) {
		return tessellateXY(incoming, xSteps, ySteps, xGrid, yGrid, 0, 0, 0, 0);
	}

	/**
	 *
	 * @param incoming
	 *            Hexagon (with flats such that Y total is flat to flat distance)
	 * @param xSteps
	 *            number of steps in X
	 * @param ySteps
	 *            number of steps in Y
	 * @param spacing
	 *            the amount of space between each hexagon
	 * @return a list of spaced hexagons
	 */
	List<CSG> tessellateHex(CSG incoming, int xSteps, int ySteps, double spacing) {
		double y = incoming.getTotalY() + spacing;
		double x = (((y / Math.sqrt(3)))) * (3 / 2);
		return tessellateXY(incoming, xSteps, ySteps, x, y, 0, 0, 0, y / 2);
	}

	/**
	 *
	 * @param incoming
	 *            Hexagon (with flats such that Y total is flat to flat distance)
	 * @param xSteps
	 *            number of steps in X
	 * @param ySteps
	 *            number of steps in Y
	 * @return a list of spaced hexagons
	 */
	List<CSG> tessellateHex(CSG incoming, int xSteps, int ySteps) {
		return tessellateHex(incoming, xSteps, ySteps, 0);
	}

	public static boolean isPreventNonManifoldTriangles() {
		return preventNonManifoldTriangles;
	}

	public static void setPreventNonManifoldTriangles(boolean preventNonManifoldTriangles) {
		if (!preventNonManifoldTriangles) {
			if (!warned)
				System.err.println(
						"WARNING:This will make STL's incompatible with low quality slicing engines like Slice3r and PrusaSlicer");
			warned = true;
		}
		CSG.preventNonManifoldTriangles = preventNonManifoldTriangles;
	}

	public static boolean isUseGPU() {
		return useGPU;
	}

	public static void setUseGPU(boolean useGPU) {
		CSG.useGPU = useGPU;
	}

	public boolean isBoundsTouching(CSG incoming) {
		if (incoming == null)
			return false;
		return getBounds().isBoundsTouching(incoming.getBounds());
	}

	public static int getMinPolygonsForOffloading() {
		return MinPolygonsForOffloading;
	}

	public static void setMinPolygonsForOffloading(int minPolygonsForOffloading) {
		MinPolygonsForOffloading = minPolygonsForOffloading;
	}

	public MeshView getCurrentMeshView() {
		return current;
	}

	public void setCurrentMeshView(MeshView current) {
		this.current = current;
	}

	public String getUniqueId() {
		return uniqueId;
	}

	public static OptType getDefaultOptionType() {
		return defaultOptType;
	}

	public static CSGManifold3d getManifold() {
		return manifold;
	}

	public double[] getVertices() {
		return vertices;
	}

	public long[] getTriangles() {
		return triangles;
	}

	public long getVertCount() {
		if (getVertices() == null)
			return 0;
		return getVertices().length / 3;
	}

	public long getTriCount() {
		if (getTriangles() == null)
			return 0;
		return getTriangles().length / 3;
	}

	public static void setManifold(CSGManifold3d manifold) {
		CSG.manifold = manifold;
	}

	public void setVertices(double[] vertices) {
		this.vertices = vertices;
	}

	public void setTriangles(long[] triangles) {
		this.triangles = triangles;
		planes = null;
	}

	public void toStl(Path path) {
		if (CSG.defaultOptType == OptType.Manifold3d) {
			try {
				manifold.toSTL(this, path);
				return;
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		System.err.println("Running Legacy STL export");
		try {
			FileUtil.write(path, toStlString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * @deprecated use public void toStl(Path path)
	 * @return an ascii stl string
	 */
	@Deprecated
	public String toStlString() {
		try {
			return toStlString(true);
		} catch (ColinearPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NonManifoldShapeError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "";
	}

	/**
	 * Returns this csg in STL string format.
	 *
	 * @param sb
	 *            string builder
	 *
	 * @return the specified string builder
	 * @throws NonManifoldShapeError
	 * @throws ColinearPointsException
	 * @deprecated use public void toStl(Path path)
	 */
	@Deprecated
	public String toStlString(boolean repair) throws ColinearPointsException, NonManifoldShapeError {
		StringBuilder sb = new StringBuilder();
		makeManifold(repair);
		String solidName = (name == null || name.length() == 0) ? "CSG_Export" : getName().replace(' ', '_');
		sb.append("solid ").append(solidName).append("\n");
		double[] verts = getVertices();
		long[] tris = getTriangles();

		for (int i = 0; i < tris.length; i += 3) {
			int i0 = (int) tris[i] * 3;
			int i1 = (int) tris[i + 1] * 3;
			int i2 = (int) tris[i + 2] * 3;

			// Vertex positions
			double ax = verts[i0], ay = verts[i0 + 1], az = verts[i0 + 2];
			double bx = verts[i1], by = verts[i1 + 1], bz = verts[i1 + 2];
			double cx = verts[i2], cy = verts[i2 + 1], cz = verts[i2 + 2];

			// Face normal via cross product of (B-A) x (C-A)
			double ux = bx - ax, uy = by - ay, uz = bz - az;
			double vx = cx - ax, vy = cy - ay, vz = cz - az;
			double nx = uy * vz - uz * vy;
			double ny = uz * vx - ux * vz;
			double nz = ux * vy - uy * vx;
			double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len > 0) {
				nx /= len;
				ny /= len;
				nz /= len;
			}

			sb.append("  facet normal ").append(nx).append(" ").append(ny).append(" ").append(nz).append("\n");
			sb.append("    outer loop\n");
			sb.append("      vertex ").append(ax).append(" ").append(ay).append(" ").append(az).append("\n");
			sb.append("      vertex ").append(bx).append(" ").append(by).append(" ").append(bz).append("\n");
			sb.append("      vertex ").append(cx).append(" ").append(cy).append(" ").append(cz).append("\n");
			sb.append("    endloop\n");
			sb.append("  endfacet\n");
		}

		sb.append("endsolid ").append(solidName).append("\n");
		return sb.toString();
	}

	/**
	 * Returns this csg in OBJ string format.
	 *
	 * @param sb
	 *            string builder
	 * @return the specified string builder
	 * @throws NonManifoldShapeError
	 * @throws ColinearPointsException
	 */
	public StringBuilder toObjString(StringBuilder sb, boolean repair)
			throws ColinearPointsException, NonManifoldShapeError {

		makeManifold(repair);

		sb.append("# Group").append("\n");
		sb.append("g v3d.csg\n");
		sb.append("o ").append(name == null || name.length() == 0 ? "CSG Export" : getName()).append("\n");

		// --- Vertices ---
		sb.append("\n# Vertices\n");
		double[] verts = getVertices();
		int vertCount = (int) getVertCount();
		for (int i = 0; i < vertCount; i++) {
			sb.append("v ").append(verts[i * 3]).append(" ").append(verts[i * 3 + 1]).append(" ")
					.append(verts[i * 3 + 2]).append("\n");
		}

		// --- Datum references (unchanged) ---
		HashMap<Vertex, Integer> mapping = new HashMap<>();
		HashMap<Transform, Vertex> mappingTF = new HashMap<>();
		if (datumReferences != null) {
			int startingIndex = vertCount + 1;
			sb.append("\n# Reference Datum\n");
			for (Transform t : datumReferences) {
				Vertex v = new Vertex(new Vector3d(0, 0, 0)).transform(t);
				Vertex v1 = new Vertex(new Vector3d(0, 0, 1)).transform(t);
				mapping.put(v, startingIndex++);
				mapping.put(v1, startingIndex++);
				mappingTF.put(t, v);
				v.toObjString(sb);
				v1.toObjString(sb);
			}
			sb.append("\n# Datum Lines\n");
			for (Transform t : mappingTF.keySet()) {
				Vertex key = mappingTF.get(t);
				Integer obj = mapping.get(key);
				sb.append("\nl ").append(obj).append(" ").append(obj + 1).append("\n");
			}
		}

		// --- Faces ---
		sb.append("\n# Faces\n");
		long[] tris = getTriangles();
		for (int i = 0; i < tris.length; i += 3) {
			// OBJ indices are 1-based
			sb.append("f ").append(tris[i] + 1).append(" ").append(tris[i + 1] + 1).append(" ").append(tris[i + 2] + 1)
					.append("\n");
		}

		sb.append("\n# End Group v3d.csg\n");
		return sb;
	}

	/**
	 * Returns this csg in OBJ string format.
	 *
	 * @return this csg in OBJ string format
	 * @throws NonManifoldShapeError
	 * @throws ColinearPointsException
	 */
	public String toObjString(boolean repair) throws ColinearPointsException, NonManifoldShapeError {
		StringBuilder sb = new StringBuilder();
		return toObjString(sb, repair).toString();
	}

	/**
	 * Returns this csg in OBJ string format.
	 *
	 * @return this csg in OBJ string format
	 * @throws NonManifoldShapeError
	 * @throws ColinearPointsException
	 */
	public String toObjString() {
		StringBuilder sb = new StringBuilder();
		try {
			return toObjString(sb, true).toString();
		} catch (ColinearPointsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NonManifoldShapeError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return sb.toString();
	}

	/**
	 * Exports a list of CSG objects into a single valid 3MF file. The .3mf is a ZIP
	 * archive containing: _rels/.rels [Content_Types].xml 3D/3dmodel.model
	 *
	 * Each CSG becomes a separate <object> resource and a <item> in <build>.
	 * Indices in <triangle> are 0-based, matching the native long[] directly.
	 *
	 * @param csgs
	 *            the list of CSG objects to export
	 * @param repair
	 *            if true, attempt to repair non-manifold geometry before export
	 * @param output
	 *            stream to write the .3mf ZIP into
	 */
	public static void toThreeMF(List<CSG> csgs, boolean repair, Path destination)
			throws IOException, ColinearPointsException, NonManifoldShapeError {

		try (ZipOutputStream zip = new ZipOutputStream(
				Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
				StandardCharsets.UTF_8)) {

			zip.setLevel(Deflater.BEST_COMPRESSION);

			writeZipEntry(zip, "_rels/.rels",
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
							+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n"
							+ "  <Relationship Type=\"http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel\"\n"
							+ "                Target=\"/3D/3dmodel.model\"\n" + "                Id=\"rel0\"/>\n"
							+ "</Relationships>\n");

			writeZipEntry(zip, "[Content_Types].xml",
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
							+ "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n"
							+ "  <Default Extension=\"rels\"\n"
							+ "          ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n"
							+ "  <Default Extension=\"model\"\n"
							+ "          ContentType=\"application/vnd.ms-package.3dmanufacturing-3dmodel+xml\"/>\n"
							+ "</Types>\n");

			StringBuilder model = new StringBuilder(1 << 20);

			model.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
					.append("<model unit=\"millimeter\" xml:lang=\"en-US\"\n")
					.append("       xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\">\n")
					.append("  <resources>\n");

			// ── basematerials block: one <base> per CSG, id="1" ─────────────────
			// Object IDs start at 2 so that 1 is free for this group.
			model.append("    <basematerials id=\"1\">\n");
			for (CSG csg : csgs) {
				Color c = csg.getColor();
				String hex = c == null
						? "#FFFFFF"
						: String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255),
								(int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
				String matName = (csg.getName() == null || csg.getName().isEmpty())
						? "material"
						: csg.getName().replace('"', '\'');
				model.append("      <base name=\"").append(matName).append("\" displaycolor=\"").append(hex)
						.append("\"/>\n");
			}
			model.append("    </basematerials>\n");

			// ── one <object> per CSG, pid="1" pindex=N (0-based into basematerials)
			for (int objIdx = 0; objIdx < csgs.size(); objIdx++) {
				CSG csg = csgs.get(objIdx);
				csg.makeManifold(repair);

				double[] verts = csg.getVertices();
				long[] tris = csg.getTriangles();
				int vCount = (int) csg.getVertCount();

				String objName = (csg.getName() == null || csg.getName().isEmpty())
						? "CSG_" + (objIdx + 1)
						: csg.getName().replace('"', '\'');

				// id starts at 2; pindex is 0-based index into the basematerials group
				model.append("    <object id=\"").append(objIdx + 2).append("\" type=\"model\"").append(" name=\"")
						.append(objName).append("\"").append(" pid=\"1\" pindex=\"").append(objIdx).append("\">\n")
						.append("      <mesh>\n").append("        <vertices>\n");

				for (int i = 0; i < vCount; i++) {
					model.append("          <vertex x=\"").append(verts[i * 3]).append("\" y=\"")
							.append(verts[i * 3 + 1]).append("\" z=\"").append(verts[i * 3 + 2]).append("\"/>\n");
				}

				model.append("        </vertices>\n").append("        <triangles>\n");

				for (int i = 0; i < tris.length; i += 3) {
					model.append("          <triangle v1=\"").append(tris[i]).append("\" v2=\"").append(tris[i + 1])
							.append("\" v3=\"").append(tris[i + 2]).append("\"/>\n");
				}

				model.append("        </triangles>\n").append("      </mesh>\n").append("    </object>\n");
			}

			model.append("  </resources>\n").append("  <build>\n");

			// item objectids match the object ids above (2-based)
			for (int objIdx = 0; objIdx < csgs.size(); objIdx++) {
				model.append("    <item objectid=\"").append(objIdx + 2).append("\"/>\n");
			}

			model.append("  </build>\n").append("</model>\n");

			writeZipEntry(zip, "3D/3dmodel.model", model.toString());
		}
	}

	/** Writes a single UTF-8 text entry into the ZIP stream. */
	private static void writeZipEntry(ZipOutputStream zip, String entryName, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(entryName));
		zip.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	/**
	 * Reads a .3mf file and returns one CSG per <object> found in the model. Uses a
	 * SAX streaming parser for high performance on large meshes.
	 *
	 * @param source
	 *            path to the .3mf file
	 * @return list of CSG objects, one per <object> in the 3MF resources
	 */
	public static List<CSG> fromThreeMF(Path source) throws IOException {
		try (ZipFile zip = new ZipFile(source.toFile())) {

			ZipEntry modelEntry = null;
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry e = entries.nextElement();
				if (e.getName().toLowerCase().endsWith("3dmodel.model")) {
					modelEntry = e;
					break;
				}
			}
			if (modelEntry == null)
				throw new IOException("No 3dmodel.model found in: " + source);

			ThreeMFHandler handler = new ThreeMFHandler();
			try {
				SAXParserFactory spf = SAXParserFactory.newInstance();
				spf.setNamespaceAware(true);
				spf.newSAXParser().parse(zip.getInputStream(modelEntry), handler);
			} catch (ParserConfigurationException | SAXException e) {
				throw new IOException("Failed to parse 3dmodel.model", e);
			}

			return handler.buildCSGs();
		}
	}

	// ── SAX Handler ──────────────────────────────────────────────────────────────

	private static final class ThreeMFHandler extends DefaultHandler {

		// Material groups: groupId -> (pindex -> Color)
		private final Map<String, Map<Integer, Color>> materialGroups = new HashMap<>();

		// Collected objects during parse
		private final List<ObjectData> objects = new ArrayList<>();

		// Mutable state during parse
		private ObjectData current = null;
		private String currentGroupId = null;
		private int baseIndex = 0;
		private boolean inMesh = false;

		// ── Primitive growable buffers (avoids boxing overhead) ──────────────

		private static final class DoubleList {
			double[] buf = new double[4096];
			int size = 0;

			void add(double v) {
				if (size == buf.length)
					buf = Arrays.copyOf(buf, buf.length * 2);
				buf[size++] = v;
			}

			double[] trim() {
				return Arrays.copyOf(buf, size);
			}
		}

		private static final class LongList {
			long[] buf = new long[4096];
			int size = 0;

			void add(long v) {
				if (size == buf.length)
					buf = Arrays.copyOf(buf, buf.length * 2);
				buf[size++] = v;
			}

			long[] trim() {
				return Arrays.copyOf(buf, size);
			}
		}

		// ── Collected object data before CSG construction ─────────────────────

		private static final class ObjectData {
			String name;
			String pid;
			String pindex;
			DoubleList vertices = new DoubleList();
			LongList triangles = new LongList();
		}

		// ── SAX callbacks ─────────────────────────────────────────────────────

		@Override
		public void startElement(String uri, String local, String qName, Attributes atts) {

			if ("basematerials".equals(local)) {
				currentGroupId = atts.getValue("id");
				baseIndex = 0;
				materialGroups.put(currentGroupId, new HashMap<Integer, Color>());

			} else if ("base".equals(local)) {
				if (currentGroupId != null) {
					String hex = atts.getValue("displaycolor");
					if (hex != null && hex.startsWith("#") && hex.length() >= 7) {
						int r = Integer.parseInt(hex.substring(1, 3), 16);
						int g = Integer.parseInt(hex.substring(3, 5), 16);
						int b = Integer.parseInt(hex.substring(5, 7), 16);
						materialGroups.get(currentGroupId).put(baseIndex, Color.rgb(r, g, b));
					}
					baseIndex++;
				}

			} else if ("object".equals(local)) {
				String type = atts.getValue("type");
				if (type != null && !type.isEmpty() && !type.equals("model"))
					return;

				current = new ObjectData();
				current.name = atts.getValue("name");
				current.pid = atts.getValue("pid");
				current.pindex = atts.getValue("pindex");

			} else if ("mesh".equals(local)) {
				if (current != null)
					inMesh = true;

			} else if ("vertex".equals(local)) {
				if (current == null || !inMesh)
					return;
				current.vertices.add(Double.parseDouble(atts.getValue("x")));
				current.vertices.add(Double.parseDouble(atts.getValue("y")));
				current.vertices.add(Double.parseDouble(atts.getValue("z")));

			} else if ("triangle".equals(local)) {
				if (current == null || !inMesh)
					return;
				current.triangles.add(Long.parseLong(atts.getValue("v1")));
				current.triangles.add(Long.parseLong(atts.getValue("v2")));
				current.triangles.add(Long.parseLong(atts.getValue("v3")));
			}
		}

		@Override
		public void endElement(String uri, String local, String qName) {
			if ("object".equals(local)) {
				if (current != null) {
					objects.add(current);
					current = null;
				}
			} else if ("mesh".equals(local)) {
				inMesh = false;
			} else if ("basematerials".equals(local)) {
				currentGroupId = null;
			}
		}

		// ── Build CSGs after parse ─────────────────────────────────────────────

		List<CSG> buildCSGs() {
			List<CSG> result = new ArrayList<CSG>(objects.size());
			for (ObjectData od : objects) {
				Color color = null;
				if (od.pid != null && od.pindex != null && !od.pid.isEmpty()) {
					Map<Integer, Color> group = materialGroups.get(od.pid);
					if (group != null)
						color = group.get(Integer.parseInt(od.pindex));
				}

				CSG csg = new CSG();
				csg.setName(od.name == null || od.name.isEmpty() ? null : od.name);
				csg.setVertices(od.vertices.trim());
				csg.setTriangles(od.triangles.trim());
				if (color != null)
					csg.setColor(color);
				result.add(csg);
			}
			return result;
		}
	}

}

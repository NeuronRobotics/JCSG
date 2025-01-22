package eu.mihosoft.vrl.v3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.ClosePath;
import javafx.scene.text.Text;
import eu.mihosoft.vrl.v3d.Vector3d;
import java.util.*;

//  Auto-generated Javadoc
/**
 * The Class Text.
 */

@SuppressWarnings("restriction")
public class TextExtrude {
	private static final String default_font = "FreeSerif";
	private final static int POINTS_CURVE = 10;

	private final String text;
	// private List<Vector3d> points;
	// HashSet<Vector3d> unique = new HashSet<Vector3d>();
	private Vector3d p0;
	private final List<LineSegment> polis = new ArrayList<>();
	ArrayList<CSG> sections = new ArrayList<CSG>();
	ArrayList<CSG> holes = new ArrayList<CSG>();
	private double dir;

	class LineSegment {

		/*
		 * Given one single character in terms of Path, LineSegment stores a list of
		 * points that define the exterior of one of its polygons (!isHole). It can
		 * contain reference to one or several holes inside this polygon. Or it can
		 * define the perimeter of a hole (isHole), with no more holes inside.
		 */

		private boolean hole;
		private List<Vector3d> points;
		private Path path;
		private Vector3d origen;
		private List<LineSegment> holes = new ArrayList<>();
		private String letter;

		public LineSegment(String text) {
			letter = text;
		}

		public String getLetter() {
			return letter;
		}

		public void setLetter(String letter) {
			this.letter = letter;
		}

		public boolean isHole() {
			return hole;
		}

		public void setHole(boolean isHole) {
			this.hole = isHole;
		}

		public List<Vector3d> getPoints() {
			return points;
		}

		public void setPoints(List<Vector3d> points) {
			this.points = points;
		}

		public Path getPath() {
			return path;
		}

		public void setPath(Path path) {
			this.path = path;
		}

		public Vector3d getOrigen() {
			return origen;
		}

		public void setOrigen(Vector3d origen) {
			this.origen = origen;
		}

		public List<LineSegment> getHoles() {
			return holes;
		}

		public void setHoles(List<LineSegment> holes) {
			this.holes = holes;
		}

		public void addHole(LineSegment hole) {
			holes.add(hole);
		}

		@Override
		public String toString() {
			return "Poly{" + "points=" + points + ", path=" + path + ", origen=" + origen + ", holes=" + holes + '}';
		}
	}

	private TextExtrude(String text, Font font, double dir) {
		if (dir <= 0)
			throw new NumberFormatException("length can not be negative");
		this.dir = dir;
		// points = new ArrayList<>();
		this.text = text;
		Text textNode = new Text(text);
		textNode.setFont(font);

		// Convert Text to Path
		Path subtract = (Path) (Shape.subtract(textNode, new Rectangle(0, 0)));
		List<List<Vector3d>> outlines = extractOutlines(subtract);
		double zOff = 0;
		for (List<Vector3d> points : outlines) {
			boolean hole = Extrude.isCCW(Polygon.fromPoints(points));
			CSG newLetter = Extrude.points(new Vector3d(0, 0, dir), points).movez(zOff);

			if (!hole)
				sections.add(newLetter);
			else
				holes.add(newLetter);
			// zOff+=dir;

		}
//		// Convert Path elements into lists of points defining the perimeter
//		// (exterior or interior)
//		subtract.getElements().forEach(this::getPoints);

		for (int i = 0; i < sections.size(); i++) {
			for (CSG h : holes) {
				try {
					if (sections.get(i).isBoundsTouching(h)) {
						// println "Hole found "
						CSG nl = sections.get(i).difference(h);

						sections.set(i, nl);
					}
				} catch (Exception e) {

				}
			}
		}
	}

	/**
	 * Extrudes the specified path (convex or concave polygon without holes or
	 * intersections, specified in CCW) into the specified direction.
	 *
	 * @param dir  direction of extrusion
	 * @param text text
	 * @param font font configuration of the text
	 *
	 * @return a CSG object that consists of the extruded polygon
	 */
	@SuppressWarnings("restriction")
	public static ArrayList<CSG> text(double dir, String text, Font font) {

		TextExtrude te = new TextExtrude(text, font, dir);

		return te.sections;
	}

	public List<LineSegment> getLineSegment() {
		return polis;
	}

	public List<Vector3d> getOffset() {
		return polis.stream().sorted((p1, p2) -> (int) (p1.getOrigen().x - p2.getOrigen().x))
				.map(LineSegment::getOrigen).collect(Collectors.toList());
	}

// Below is AI slop
	private static final double CURVE_SEGMENTS = 3; // Number of segments to approximate curves
	private static final double POINT_EPSILON = Plane.getEPSILON(); // Distance threshold for considering points equal

	/**
	 * Converts a JavaFX Text object into a list of cleaned vector lists
	 * representing the outlines
	 */
	public static List<List<Vector3d>> extractOutlines(Path text) {
		List<List<Vector3d>> rawOutlines = extractRawOutlines(text);
		List<List<Vector3d>> cleanedOutlines = new ArrayList<>();

		for (List<Vector3d> outline : rawOutlines) {
			List<Vector3d> cleaned = cleanOutline(outline);
			if (cleaned.size() >= 3) { // Only keep outlines with at least 3 points
				cleanedOutlines.add(cleaned);
			}
		}

		return cleanedOutlines;
	}

	/**
	 * Initial extraction of outlines from text
	 */
	private static List<List<Vector3d>> extractRawOutlines(Path textPath) {
		List<List<Vector3d>> allOutlines = new ArrayList<>();

		List<Vector3d> currentPath = new ArrayList<>();
		Vector3d lastPoint = null;

		for (PathElement element : textPath.getElements()) {
			if (element instanceof MoveTo) {
				if (!currentPath.isEmpty()) {
					allOutlines.add(new ArrayList<>(currentPath));
					currentPath.clear();
				}
				MoveTo move = (MoveTo) element;
				lastPoint = Vector3d.xyz(move.getX(), move.getY(), 0);
				currentPath.add(lastPoint);
			} else if (element instanceof LineTo) {
				LineTo line = (LineTo) element;
				lastPoint = Vector3d.xyz(line.getX(), line.getY(), 0);
				currentPath.add(lastPoint);
			} else if (element instanceof CubicCurveTo) {
				CubicCurveTo curve = (CubicCurveTo) element;
				List<Vector3d> curvePoints = approximateCubicCurve(lastPoint,
						Vector3d.xyz(curve.getControlX1(), curve.getControlY1(), 0),
						Vector3d.xyz(curve.getControlX2(), curve.getControlY2(), 0),
						Vector3d.xyz(curve.getX(), curve.getY(), 0));
				currentPath.addAll(curvePoints);
				lastPoint = curvePoints.get(curvePoints.size() - 1);
			} else if (element instanceof QuadCurveTo) {
				QuadCurveTo curve = (QuadCurveTo) element;
				List<Vector3d> curvePoints = approximateQuadCurve(lastPoint,
						Vector3d.xyz(curve.getControlX(), curve.getControlY(), 0),
						Vector3d.xyz(curve.getX(), curve.getY(), 0));
				currentPath.addAll(curvePoints);
				lastPoint = curvePoints.get(curvePoints.size() - 1);
			}
		}

		if (!currentPath.isEmpty()) {
			allOutlines.add(currentPath);
		}

		return allOutlines;
	}

	/**
	 * Clean an outline by removing duplicate points and ensuring proper closure
	 */
	private static List<Vector3d> cleanOutline(List<Vector3d> outline) {
		if (outline.isEmpty())
			return outline;

		List<Vector3d> cleaned = new ArrayList<>();
		Vector3d prevPoint = null;

		// Process all points
		for (Vector3d point : outline) {
			if (prevPoint == null || !isNearlyEqual(prevPoint, point)) {
				// Only add point if it's significantly different from the previous point
				cleaned.add(point);
				prevPoint = point;
			}
		}
		// Remove redundant points that form zero-area triangles
		return removeRedundantPoints(cleaned);
	}

	/**
	 * Remove points that form zero-area triangles with their neighbors
	 */
	private static List<Vector3d> removeRedundantPoints(List<Vector3d> points) {
		if (points.size() < 3)
			return points;

		List<Vector3d> result = new ArrayList<>();
		int size = points.size();

		for (int i = 0; i < size; i++) {
			Vector3d curr = points.get(i);
			result.add(curr);
		}

		return result;
	}

	private static boolean isNearlyEqual(Vector3d v1, Vector3d v2) {
		return v1.minus(v2).length() < POINT_EPSILON;
	}

	// Bezier curve methods remain the same
	private static List<Vector3d> approximateCubicCurve(Vector3d start, Vector3d control1, Vector3d control2,
			Vector3d end) {
		List<Vector3d> points = new ArrayList<>();
		for (double i = 1; i <= CURVE_SEGMENTS; i+=1) {
			double t = (double) i / CURVE_SEGMENTS;
			double x = cubicBezier(start.x, control1.x, control2.x, end.x, t);
			double y = cubicBezier(start.y, control1.y, control2.y, end.y, t);
			points.add(Vector3d.xyz(x, y, 0));
		}
		return points;
	}

	private static List<Vector3d> approximateQuadCurve(Vector3d start, Vector3d control, Vector3d end) {
		List<Vector3d> points = new ArrayList<>();
		for (double i = 1; i <= CURVE_SEGMENTS; i+=1) {
			double t = (double) i / CURVE_SEGMENTS;
			double x = quadBezier(start.x, control.x, end.x, t);
			double y = quadBezier(start.y, control.y, end.y, t);
			points.add(Vector3d.xyz(x, y, 0));
		}
		return points;
	}

	private static double cubicBezier(double p0, double p1, double p2, double p3, double t) {
		double mt = 1 - t;
		return p0 * mt * mt * mt + 3 * p1 * mt * mt * t + 3 * p2 * mt * t * t + p3 * t * t * t;
	}

	private static double quadBezier(double p0, double p1, double p2, double t) {
		double mt = 1 - t;
		return p0 * mt * mt + 2 * p1 * mt * t + p2 * t * t;
	}

}

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

import com.piro.bezier.BezierPath;

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
	private static final double CURVE_SEGMENTS = 4; // Number of segments to approximate curves

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
		List<List<Vector3d>> outlines = extractOutlines(subtract,font.getSize());
		double zOff = 0;
//		boolean b = CSG.isPreventNonManifoldTriangles();
//		CSG.setPreventNonManifoldTriangles(false);
		for (List<Vector3d> points : outlines) {
			try {
				boolean hole = Extrude.isCCWv3d(points);
				CSG newLetter = Extrude.points(new Vector3d(0, 0, dir), points).movez(zOff);
				//newLetter.triangulate();
				if (!hole)
					sections.add(newLetter);
				else {
					newLetter.setIsHole(true);
					holes.add(newLetter);
				}
			}catch(ColinearPointsException e) {
				e.printStackTrace();
			}
		}
//		CSG.setPreventNonManifoldTriangles(b);
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
	//private static final double POINT_EPSILON = 0.0001; // Distance threshold for considering points equal

	/**
	 * Converts a JavaFX Text object into a list of cleaned vector lists
	 * representing the outlines
	 * @param fontSize 
	 */
	public static List<List<Vector3d>> extractOutlines(Path text, double fontSize) {
		List<List<Vector3d>> rawOutlines = extractRawOutlines(text);
//		List<List<Vector3d>> cleanedOutlines = new ArrayList<>();
//
//		for (List<Vector3d> outline : rawOutlines) {
//			List<Vector3d> cleaned = cleanOutline(outline,fontSize);
//			if (cleaned.size() >= 3) { // Only keep outlines with at least 3 points
//				cleanedOutlines.add(cleaned);
//			}
//		}

		return rawOutlines;
	}

	/**
	 * Initial extraction of outlines from text
	 */
	private static List<List<Vector3d>> extractRawOutlines(Path textPath) {
		List<List<Vector3d>> allOutlines = new ArrayList<>();

		StringBuilder pathBuilder = new StringBuilder();
		//List<Vector3d> currentPath = new ArrayList<>();

		for (PathElement element : textPath.getElements()) {
		    if (element instanceof MoveTo) {
		        // If we have a current path, process it with BezierPath
		        if (pathBuilder.length() > 0) {
		            BezierPath bezierPath = new BezierPath(5);
		            bezierPath.parsePathString(pathBuilder.toString());
		            List<Vector3d> pathPoints = bezierPath.evaluate();
		            if (!pathPoints.isEmpty()) {
		                allOutlines.add(new ArrayList<>(pathPoints));
		            }
		            pathBuilder = new StringBuilder();
		        }
		        
		        MoveTo move = (MoveTo) element;
		        pathBuilder.append("M").append(move.getX()).append(" ").append(move.getY());
		        
		    } else if (element instanceof LineTo) {
		        LineTo line = (LineTo) element;
		        pathBuilder.append("L").append(line.getX()).append(" ").append(line.getY());
		        
		    } else if (element instanceof CubicCurveTo) {
		        CubicCurveTo curve = (CubicCurveTo) element;
		        pathBuilder.append("C")
		            .append(curve.getControlX1()).append(" ").append(curve.getControlY1()).append(" ")
		            .append(curve.getControlX2()).append(" ").append(curve.getControlY2()).append(" ")
		            .append(curve.getX()).append(" ").append(curve.getY());
		            
		    } else if (element instanceof QuadCurveTo) {
		        QuadCurveTo curve = (QuadCurveTo) element;
		        pathBuilder.append("Q")
		            .append(curve.getControlX()).append(" ").append(curve.getControlY()).append(" ")
		            .append(curve.getX()).append(" ").append(curve.getY());
		    }
		}

		// Process the final path if it exists
		if (pathBuilder.length() > 0) {
		    BezierPath bezierPath = new BezierPath(5);
		    bezierPath.parsePathString(pathBuilder.toString());
		    List<Vector3d> pathPoints = bezierPath.evaluate();
		    if (!pathPoints.isEmpty()) {
		        allOutlines.add(pathPoints);
		    }
		}

		return allOutlines;
	}

}

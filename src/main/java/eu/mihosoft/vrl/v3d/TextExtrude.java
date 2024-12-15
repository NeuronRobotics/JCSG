package eu.mihosoft.vrl.v3d;

import static com.neuronrobotics.javacad.JavaCadBuildInfo.isMac;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The Class Text.
 */
@SuppressWarnings("restriction")
public class TextExtrude {
    private static final int CURVE_SEGMENTS = 3;
    private final double dir;
    private final List<CSG> sections = new ArrayList<>();
    private final List<CSG> holes = new ArrayList<>();
    private List<Vector3d> points = new ArrayList<>();

    private TextExtrude(String text, Font font, double dir) {
        if (dir <= 0)
            throw new NumberFormatException("length can not be negative");
        this.dir = dir;

        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        GlyphVector glyphVector = font.createGlyphVector(g2.getFontRenderContext(), text);
        Shape shape = glyphVector.getOutline();


        double[] coords = new double[8];
        PathIterator pathIterator = shape.getPathIterator(null);
        Vector3d startPoint;
        while (!pathIterator.isDone()) {

            int i = pathIterator.currentSegment(coords);

            switch (i) {
                case PathIterator.SEG_MOVETO:
                    startPoint = new Vector3d(round(coords[0]), round(coords[1]), 0);
                    points.add(startPoint);
                    break;

                case PathIterator.SEG_LINETO:
                    points.add(new Vector3d(coords[0], coords[1], 0));
                    break;

                case PathIterator.SEG_QUADTO:
                    expandQuadBezier(coords[0], coords[1], coords[2], coords[3]);
                    break;

                case PathIterator.SEG_CUBICTO:
                    expandCubicBezier(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]);
                    break;

                case PathIterator.SEG_CLOSE:
                    loadPoints();
                    break;

            }
            pathIterator.next();
        }


        for (int i = 0; i < sections.size(); i++) {
            for (CSG h : holes) {
                try {
                    if (sections.get(i).touching(h)) {
                        CSG nl = sections.get(i).difference(h);

                        sections.set(i, nl);
                    }
                } catch (Exception e) {

                }
            }
        }
    }

    private static double round(double value) {
        return Math.round(value * 100000.0) / 100000.0;
    }

    /**
     * Extrudes the specified path (convex or concave polygon without holes or
     * intersections, specified in CCW) into the specified direction.
     *
     * @param dir  direction of extrusion
     * @param text text
     * @param font font configuration of the text
     * @return a CSG object that consists of the extruded polygon
     */
    @SuppressWarnings("restriction")
    public static List<CSG> text(double dir, String text, Font font) {
        TextExtrude te = new TextExtrude(text, font, dir);
        return te.sections;
    }

    private void expandCubicBezier(double x1, double x2, double x3, double y1, double y2, double y3) {
        Vector3d ini1 = points.get(points.size() - 1);
        IntStream.rangeClosed(1, CURVE_SEGMENTS).forEach(index -> points.add(evalCubicBezier(x1, x2, x3, y1, y2, y3, ini1, ((double) index) / CURVE_SEGMENTS)));
    }

    private void expandQuadBezier(double x1, double y1, double x2, double y2) {
        final Vector3d start = points.get(points.size() - 1);
        IntStream.rangeClosed(1, CURVE_SEGMENTS).forEach(index -> points.add(evalQuadBezier(start, x1, y1, x2, y2, ((double) index) / CURVE_SEGMENTS)));
    }

    private void loadPoints() {
        if (points.size() > 3) {
            if (!isMac()) {
                points.remove(points.size() - 1);
            }
            boolean isHole = !Extrude.isCCW(Polygon.fromPoints(points));
            CSG newLetter = Extrude.points(new Vector3d(0, 0, dir), points);

            if (!isHole) {
                sections.add(newLetter);
            } else {
                holes.add(newLetter);
            }
        }
        points = new ArrayList<>();
    }

    private Vector3d evalCubicBezier(double x1, double x2, double x3, double y1, double y2, double y3, Vector3d ini, double t) {
        return new Vector3d((float) (Math.pow(1 - t, 3) * ini.x +
                3 * t * Math.pow(1 - t, 2) * x2 +
                3 * (1 - t) * t * t * x3 +
                Math.pow(t, 3) * x1),
                (float) (Math.pow(1 - t, 3) * ini.y +
                        3 * t * Math.pow(1 - t, 2) * y2 +
                        3 * (1 - t) * t * t * y3 +
                        Math.pow(t, 3) * y1),
                0f);
    }

    private Vector3d evalQuadBezier(Vector3d startPoint, double x1, double y1, double x2, double y2, double t) {
        return new Vector3d((float) (Math.pow(1 - t, 2) * startPoint.x +
                2 * (1 - t) * t * x1 +
                Math.pow(t, 2) * x2),
                (float) (Math.pow(1 - t, 2) * startPoint.y +
                        2 * (1 - t) * t * y1 +
                        Math.pow(t, 2) * y2),
                0f);
    }
}
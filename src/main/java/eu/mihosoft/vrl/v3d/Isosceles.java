package eu.mihosoft.vrl.v3d;

import java.util.List;

import eu.mihosoft.vvecmath.Vector3d;

public class Isosceles extends Primitive {
  double w, h, d;

  /** The properties. */
  private final PropertyStorage properties = new PropertyStorage();

  public PropertyStorage getProperties() {
    return properties;
  }

  /**
   * Constructor. Creates a new cuboid with center {@code [0,0,0]} and with the specified
   * dimensions.
   *
   * @param w width
   * @param h height
   * @param d depth
   */
  public Isosceles(double w, double h, double d) {
    this.w = w;
    this.h = h;
    this.d = d;
  }


  /*
   * (non-Javadoc)
   * 
   * @see eu.mihosoft.vrl.v3d.Primitive#toPolygons()
   */
  @Override
  public List<Polygon> toPolygons() {
      CSG polygon = Extrude.points(Vector3d.xyz(0, 0, w),// This is the  extrusion depth
              Vector3d.xy(0,0),// All values after this are the points in the polygon
              Vector3d.xy(0,-h/2),// upper right corner
              Vector3d.xy(d,0),// Bottom right corner
              Vector3d.xy(0,h/2)// upper right corner
      ).roty(90)
      .rotz(180);
      return polygon.getPolygons();
  }

}

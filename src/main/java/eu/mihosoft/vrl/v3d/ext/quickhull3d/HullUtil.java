/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.mihosoft.vrl.v3d.ext.quickhull3d;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.CSGClient;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.PropertyStorage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * The Class HullUtil.
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public class HullUtil {

	/**
	 * Instantiates a new hull util.
	 */
	private HullUtil() {
		throw new AssertionError("Don't instantiate me!", null);
	}

	/**
	 * Hull.
	 *
	 * @param points
	 *            the points
	 * @return the csg
	 */
	public static CSG hull(List<?> points) {
		List<Vector3d> plist = new ArrayList<>();
		if (Vector3d.class.isInstance(points.get(0))) {
			points.stream().forEach((pobj) -> plist.add((Vector3d) pobj));
			return hull(plist, new PropertyStorage());
		}
		if (CSG.class.isInstance(points.get(0))) {
			for (Object csg : points)
				((CSG) csg).getPolygons().forEach((p) -> p.getVertices().forEach((v) -> plist.add(v.pos)));

			return hull(plist, new PropertyStorage());
		}
		throw new RuntimeException("Objects in list are of unknown type: " + points.get(0).getClass().getName()+"\r\nExpected CSG or Vector3d ");
	}

	/**
	 * Hull.
	 *
	 * @param points
	 *            the points
	 * @param storage
	 *            the storage
	 * @return the csg
	 */
	public static CSG hull(List<Vector3d> points, PropertyStorage storage) {
			if (CSGClient.isRunning()) {
				try {
					CSG csg = CSGClient.getClient().hull(points,new PropertyStorage()).get(0);
					csg.setStorage(storage);
					return csg;
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		Point3d[] hullPoints = points.stream().map((vec) -> new Point3d(vec.x, vec.y, vec.z)).toArray(Point3d[]::new);

		QuickHull3D hull = new QuickHull3D();
		hull.build(hullPoints);
		hull.triangulate();

		int[][] faces = hull.getFaces();

		ArrayList<Polygon> polygons = new ArrayList<>();
//		HashSet<Vector3d> vall = new HashSet<>();;
//		Vector3d hullCenter = new Vector3d(0,0,0);
//		for (int[] verts : faces) {
//			for (int i : verts) {
//				Vector3d e = points.get(hull.getVertexPointIndices()[i]);
//				boolean in = false;
//				for(Vector3d vec:vall)
//					if(vec.test(e)) {
//						in=true;
//						break;
//					}
//				if(!in && !vall.contains(e)) {
//					vall.add(e);
//					hullCenter=hullCenter.plus(e);
//				}
//			}
//		}
//		hullCenter=hullCenter.times(1.0 / vall.size());

		for (int[] verts : faces) {
			ArrayList<Vector3d> vertices = new ArrayList<>();
			for (int i : verts) {
				vertices.add(points.get(hull.getVertexPointIndices()[i]));
			}

			try {
				Polygon fromPoints = Polygon.fromPoints(vertices, storage);
//				Vector3d normal = fromPoints.plane.getNormal();
//		        // face centroid
//		        Vector3d faceCentroid = new Vector3d(0,0,0);
//		        for (Vector3d v : vertices) 
//		        	faceCentroid.add(v);
//		        faceCentroid.scale(1.0 / vertices.size());
//
//		        // direction from hull center to face
//		        Vector3d toFace = faceCentroid.minus( hullCenter);
//
//		        // ensure correct normal orientation
//		        if (toFace.dot(normal) < 0) {
//		        	fromPoints.flip();
//		        }

				polygons.add(fromPoints);
			} catch (ColinearPointsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
		}

		return CSG.fromPolygons(polygons);
	}

	/**
	 * Hull.
	 *
	 * @param csg
	 *            the csg
	 * @param storage
	 *            the storage
	 * @return the csg
	 */
	public static CSG hull(CSG csg, PropertyStorage storage) {

		List<Vector3d> points = new ArrayList<>(csg.getPolygons().size() * 3);

		csg.getPolygons().forEach((p) -> p.getVertices().forEach((v) -> points.add(v.pos)));

		return hull(points, storage);
	}

	/**
	 * Hull.
	 *
	 * @param csgList
	 *            a list of csg
	 * @return the csg
	 */
	public static CSG hull(CSG... csgList) {

		List<Vector3d> points = new ArrayList<>();
		for (CSG csg : csgList)
			csg.getPolygons().forEach((p) -> p.getVertices().forEach((v) -> points.add(v.pos)));

		return hull(points, new PropertyStorage());
	}
}

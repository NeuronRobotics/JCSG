package eu.mihosoft.vrl.v3d;

import java.util.List;

import javafx.scene.paint.Color;

public interface IPolygonRepairTool {
	List<Polygon> repairOverlappingEdges(List<Vertex> vertices, PropertyStorage shared,
			boolean allowDegenerate, Plane p, Color c) throws ColinearPointsException;
}

package eu.mihosoft.vrl.v3d;

import java.util.List;

public interface IPolygonRepairTool {
	List<Polygon> repairOverlappingEdges(Polygon concave) throws ColinearPointsException;
}

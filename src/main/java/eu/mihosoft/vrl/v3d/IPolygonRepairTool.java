package eu.mihosoft.vrl.v3d;

import java.util.ArrayList;

public interface IPolygonRepairTool {
	Polygon repairOverlappingEdges(Polygon concave) throws ColinearPointsException;
}

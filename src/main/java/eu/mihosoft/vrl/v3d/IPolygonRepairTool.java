package eu.mihosoft.vrl.v3d;

public interface IPolygonRepairTool {
	Polygon repairOverlappingEdges(Polygon concave) throws ColinearPointsException;
}

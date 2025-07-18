package eu.mihosoft.vrl.v3d;

import java.util.ArrayList;

public interface IPolygonRepairTool {
	ArrayList<Polygon> repairOverlappingEdges(Polygon concave);
}

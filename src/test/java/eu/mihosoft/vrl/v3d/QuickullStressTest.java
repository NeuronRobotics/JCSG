package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import org.junit.Test;

public class QuickullStressTest {

	@Test
	public void test() {
		double hShortValue = 13.08;
		double lValue = 84.41;
		double wValue = 25.24;
		double hTallValue = 21.45;
		double lTallValue = 43.0;
		double chamferDepthS = hShortValue / 3;
		CSG cornerS = new ChamferedCylinder(chamferDepthS, hShortValue, chamferDepthS).toCSG().toXMin().toYMin();
		CSG sideS = cornerS.union(cornerS.toYMax().movey(lValue)).hull();
		CSG allS = sideS.union(sideS.toXMax().movex(wValue)).hull();

		double chamferDepth = chamferDepthS;
		CSG corner = new ChamferedCylinder(chamferDepth, hTallValue, chamferDepth).toCSG().toXMin().toYMin();
		CSG side = corner.union(corner.toYMax().movey(lTallValue));
		CSG all = side.union(side.toXMax().movex(wValue)).hull().union(allS)
				.setColor(javafx.scene.paint.Color.color(0.3, 0.3, 0.3));

		all.roty(180).moveToCenterX().toZMin().movey(-35.19);
	}

}

package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.parametrics.LengthParameter;

public class ServerClientTest {

	@Test
	public void test() throws Exception {
		int port = 3742;

		
		File f = new File("/opt/File.txt");
		CSGServer server = new CSGServer(port, f);

		Thread serverThread = new Thread(()->{
			try {
				server.start();
			} catch (Exception e) {
				fail();
			}
		});
		serverThread.start();
		while(!server.isRunning()) {
			Thread.sleep(500);
			System.out.println("Waiting for server to start...");
		}

		String hostname = "localhost";
		// Create client with try-with-resources for automatic cleanup
		try {

			CSG a = new Cube(20).toCSG().movex(100).movez(100);
			a.getBounds();
			a.setManipulator(new javafx.scene.transform.Affine());
			a.setManufacturing(new PrepForManufacturing() {
				@Override
				public CSG prep(CSG incoming) {
					// TODO Auto-generated method stub
					return incoming;
				}
			});
			LengthParameter param = new LengthParameter("parameter", (double) 35, new ArrayList<Double>());
			a.setParameter(param);
			CSG b = new Cube(20, 30, 5).toCSG();
			b.getBounds();
			CSG c = new Cube(10, 10, 10).toCSG();
			c.getBounds();
			
			int apoly1 = a.getPolygons().size();
			int bpoly1 =b.getPolygons().size();
			
			CSG u1 = a.union( b,c);
			CSG d1 = a.difference(b);
			CSG t1 = d1.clone().triangulate(true);
			ArrayList<CSG> m1 = a.minkowskiHullShape(b);
			CSG h1 = u1.hull();
			
			CSGClient.start(hostname, port, f);
			// Set a low number to ensure the Server is used. this defaults to 200
			CSG.setMinPolygonsForOffloading(4);
			// Connect to server
			System.out.println("Client info: " + CSGClient.getClient().getServerInfo());
			int apoly = a.getPolygons().size();
			int bpoly =b.getPolygons().size();
			CSG u =a.union( b,c);
			if(testPoly(u1,u))
				fail();
			
			CSG d = a.difference(b);
			if(testPoly(d1,d))
				fail("Difference Step fail , expected "+d1.getPolygons().size()+" got "+d.getPolygons().size());
			CSG t = d.clone().triangulate(true);
			if(testPoly(t1,t))
				fail();
			ArrayList<CSG> m = a.minkowskiHullShape(b);
			if(m.size()!=m1.size()) {
				fail("Minkowski expected "+m1.size()+" but got "+m.size());
			}
			for(int i=0;i<m1.size();i++) {
				if(testPoly(
						m1.get(i),m.get(i)
						)) {
					fail();
				}
			}
			CSG h = u1.hull();
			if(testPoly(h,h1))
				fail();
			CSGClient.close();
		} catch (Exception e) {
			System.err.println("Communication error: " + e.getMessage());
			e.printStackTrace();
			fail();
		}

		server.stop();
		serverThread.interrupt();
		serverThread.join();
		System.out.println("\nClient example completed.");
	}
	
	boolean testPoly(CSG p1, CSG p2) {
		int size1 = p1.getPolygons().size();
		int size2 = p2.getPolygons().size();
		if(size1!=size2) {
			System.err.println("Mismatched number of polygons expected "+size1+" but got "+size2);
			return true;
		}
		for(int i=0;i<size1;i++) {
			Polygon poly1 = p1.getPolygons().get(i);
			Polygon poly2 = p2.getPolygons().get(i);
			int size = poly1.getPoints().size();
			if(size!=poly2.getPoints().size()) {
				System.err.println("Number of Points mismatch ");
				return true;
			}
			for(int j=0;j<size;j++) {
				Vector3d vector3d = poly1.getPoints().get(j);
				Vector3d obj = poly2.getPoints().get(j);
				if(!vector3d.test(obj, 0.000001)) {
					System.err.println("Point distance "+vector3d.distance(obj));
					return true;
				}else {
					//System.out.println("Point match ");
				}
			}
		}
		return false;
	}

}

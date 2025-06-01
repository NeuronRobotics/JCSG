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
			
			CSG u1 = CSG.unionAll(a, b, c);
			CSG d1 = a.difference(b);
			CSG t1 = d1.clone().triangulate(true);
			ArrayList<CSG> m1 = a.minkowskiHullShape(b);
			CSG h1 = u1.hull();
			
			CSGClient.start(hostname, port, f);
			// Set a low number to ensure the Server is used. this defaults to 200
			CSG.setMinPolygonsForOffloading(4);
			// Connect to server
			System.out.println("Client info: " + CSGClient.getClient().getServerInfo());

			CSG u = CSG.unionAll(a, b, c);
			if(u.getPolygons().size()!=u1.getPolygons().size())
				fail();
			CSG d = a.difference(b);
			if(d.getPolygons().size()!=d1.getPolygons().size())
				fail("Difference Step fail , expected "+d1.getPolygons().size()+" got "+d.getPolygons().size());
			CSG t = d.clone().triangulate(true);
			if(t.getPolygons().size()!=t1.getPolygons().size())
				fail();
			ArrayList<CSG> m = a.minkowskiHullShape(b);
			if(m.size()!=m1.size()) {
				fail();
			}
			for(int i=0;i<m.size();i++) {
				if(m.get(i).getPolygons().size()!=m1.get(i).getPolygons().size()) {
					fail();
				}
			}
			CSG h = u1.hull();
			if(h.getPolygons().size()!=h1.getPolygons().size())
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

}

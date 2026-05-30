package eu.mihosoft.vrl.v3d;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import eu.mihosoft.vrl.v3d.parametrics.LengthParameter;

public class ServerClientTest {
	int port = 3742;
	File f = new File("/opt/File.txt");

	CSGServer server = new CSGServer(port, f);
	Thread serverThread;
	@Before
	public void before() {
		serverThread = new Thread(() -> {
			try {
				server.start();
			} catch (InterruptedException e) {
				// return
			} catch (Exception e) {
				fail();
			}
		});
		serverThread.start();
		while (!server.isRunning()) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("Waiting for server to start...");
		}

	}
	@After
	public void after() {

		try {
			server.stop();
			serverThread.interrupt();
			serverThread.join();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		CSGClient.close();

	}

	@Test
	public void test() throws Exception {

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
			CSGDatabaseInstance instance = CSGDatabase.getInstance();

			LengthParameter param = new LengthParameter(instance, "parameter", (double) 35, new ArrayList<Double>());

			a.setParameter(CSGDatabase.getInstance(), param);
			instance.saveDatabase();
			CSG b = new Cube(20, 30, 5).toCSG();
			b.getBounds();
			CSG c = new Cube(10, 10, 10).toCSG();
			c.getBounds();
			CSG dif = new Cube(100, 100, 1).toCSG();
			dif.getBounds();

			long apoly1 = a.getNumberOfTriangles();
			long bpoly1 = b.getNumberOfTriangles();

			CSG u1 = a.union(b, c);
			CSG i1 = c.intersect(b);
			CSG d1 = a.difference(b, dif);
			CSG t1 = d1.clone().makeManifold(true);
			ArrayList<CSG> m1 = a.minkowskiHullShape(b);
			CSG h1 = u1.hull();

			CSGClient.start(hostname, port, f);
			// Set a low number to ensure the Server is used. this defaults to 200
			CSG.setMinPolygonsForOffloading(4);
			// Connect to server
			System.out.println("Client info: " + CSGClient.getClient().getServerInfo());
			long apoly = a.getNumberOfTriangles();
			long bpoly = b.getNumberOfTriangles();
			CSG u = a.union(b, c);
			if (testPoly(u1, u))
				throw new Exception();
			CSG i0 = c.intersect(b);
			if (testPoly(i1, i0))
				throw new Exception();
			CSG d = a.difference(b, dif);
			if (testPoly(d1, d))
				throw new Exception();
			CSG t = d.clone().makeManifold(true);
			if (testPoly(t1, t))
				throw new Exception();
			ArrayList<CSG> m = a.minkowskiHullShape(b);
			if (m.size() != m1.size()) {
				throw new Exception();

			}
			for (int i = 0; i < m1.size(); i++) {
				if (testPoly(m1.get(i), m.get(i))) {
					fail();
				}
			}
			CSG h = u1.hull();
			if (testPoly(h, h1))
				fail();
			CSGClient.close();
		} catch (Exception e) {
			System.err.println("Communication error: " + e.getMessage());
			e.printStackTrace();
			fail();
		}

		System.out.println("\nClient example completed.");
	}

	boolean testPoly(CSG p1, CSG p2) throws ColinearPointsException {
		long size1 = p1.getNumberOfTriangles();
		long size2 = p2.getNumberOfTriangles();
		if (size1 != size2) {
			System.err.println("Mismatched number of polygons expected " + size1 + " but got " + size2);
			return true;
		}
		if (p1.getNumberOfTriangles() != p2.getNumberOfTriangles())
			return true;
		// for(int i=0;i<p1.getTriangles().length;i++) {
		// if(p1.getTriangles()!=p2.getTriangles())
		// return true;
		// }
		return false;
	}

}

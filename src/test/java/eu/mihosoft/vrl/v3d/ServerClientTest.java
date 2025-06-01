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

		
		File f = new File("file.txt");
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
			CSGClient.start(hostname, port, f);
			// Set a low number to ensure the Server is used. this defaults to 200
			CSG.setMinPolygonsForOffloading(4);
			// Connect to server
			System.out.println("Client info: " + CSGClient.getClient().getServerInfo());

			CSG a = new Cube(20).toCSG();
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
			CSG u = CSG.unionAll(a, b, c);
			CSG d = a.difference(b);
			CSG t = d.triangulate(true);
			ArrayList<CSG> m = a.minkowskiHullShape(b);
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

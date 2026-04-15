package eu.mihosoft.vrl.v3d;

import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.*;
import java.io.*;
import java.security.cert.X509Certificate;

//CSG Client class that maintains connection and provides clean API
public class CSGClient {
	// statics
	private static CSGClient client = null;

	// Class Vars

	private String hostname;
	private int port;

	private String key = null;

	private static boolean serverCall = false;

	private SSLSocketFactory factory;
	private ArrayList<ICSGClientEvent> listeners = new ArrayList<>();
	public void addListener(ICSGClientEvent e) {
		if (listeners.contains(e))
			return;
		listeners.add(e);
	}
	public void removeListener(ICSGClientEvent e) {
		if (!listeners.contains(e))
			return;
		listeners.remove(e);
	}

	public CSGClient(String hostname, int port, File f) throws Exception {
		this.hostname = hostname;
		this.port = port;
		if (f == null)
			throw new NullPointerException("API key file can not be null");
		if (f.exists())
			key = Files.readAllLines(f.toPath()).toArray(new String[0])[0];
		else {
			System.err.println("Error! API key file does not exist! " + f.getAbsolutePath());
		}
		if (key == null || key.length() == 0)
			System.err.println("Key error, no key provided by " + f.getAbsolutePath());
		else
			System.out.println("API Key Loaded from " + f.getAbsolutePath());
		Socket socket = new Socket(hostname, port);
		socket.close();
		SSLContext sslContext = SSLContext.getInstance("TLS");

		// For development: trust all certificates (use proper truststore in production)
		TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		}};
		sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
		factory = sslContext.getSocketFactory();
	}

	/**
	 * Perform union operations on consecutive CSG pairs
	 *
	 * @param csgList
	 *            List of CSG objects to perform union on
	 * @return List of union results
	 * @throws IOException
	 *             if communication error occurs
	 * @throws CSGOperationException
	 *             if server returns an error
	 */
	public ArrayList<CSG> union(List<CSG> csgList) throws Exception {
		return performOperation(csgList, CSGRemoteOperation.UNION);
	}
	public ArrayList<CSG> hull(List<Vector3d> points, PropertyStorage storage) throws Exception {
		return performOperation(new ArrayList<CSG>(), CSGRemoteOperation.hull, points, storage);
	}
	/**
	 * Perform difference operations on consecutive CSG pairs
	 *
	 * @param csgList
	 *            List of CSG objects to perform difference on
	 * @return List of difference results
	 * @throws IOException
	 *             if communication error occurs
	 * @throws CSGOperationException
	 *             if server returns an error
	 */
	public ArrayList<CSG> difference(ArrayList<CSG> csgList) throws Exception {
		return performOperation(csgList, CSGRemoteOperation.DIFFERENCE);
	}

	/**
	 * Perform intersect operations on consecutive CSG pairs
	 *
	 * @param csgList
	 *            List of CSG objects to perform intersection on
	 * @return List of intersection results
	 * @throws IOException
	 *             if communication error occurs
	 * @throws CSGOperationException
	 *             if server returns an error
	 */
	public ArrayList<CSG> intersect(ArrayList<CSG> csgList) throws Exception {
		return performOperation(csgList, CSGRemoteOperation.INTERSECT);
	}

	/**
	 * Perform minkowskiHullShape operations on consecutive CSG pairs
	 *
	 * @param csgList
	 *            List of CSG objects to perform minkowskiHullShape on
	 * @return List of intersection results
	 * @throws IOException
	 *             if communication error occurs
	 * @throws CSGOperationException
	 *             if server returns an error
	 */
	public ArrayList<CSG> minkowskiHullShape(ArrayList<CSG> csgList) throws Exception {
		return performOperation(csgList, CSGRemoteOperation.minkowskiHullShape);
	}

	/**
	 * Perform triangulation on each CSG object
	 *
	 * @param csgList
	 *            List of CSG objects to triangulate
	 * @return List of triangulated CSG objects
	 * @throws IOException
	 *             if communication error occurs
	 * @throws CSGOperationException
	 *             if server returns an error
	 */
	public ArrayList<CSG> triangulate(ArrayList<CSG> csgList) throws Exception {
		return performOperation(csgList, CSGRemoteOperation.TRIANGULATE);
	}
	/**
	 * Internal method to perform operations and handle request/response
	 */
	private ArrayList<CSG> performOperation(List<CSG> csgList, CSGRemoteOperation operation) throws Exception {
		return performOperation(csgList, operation, null, null);
	}
	/**
	 * Internal method to perform operations and handle request/response
	 */
	private ArrayList<CSG> performOperation(List<CSG> csgList, CSGRemoteOperation operation, List<Vector3d> points,
			PropertyStorage storage) throws Exception {
		if (javafx.application.Platform.isFxApplicationThread()) {
			RuntimeException runtimeException = new RuntimeException("Network trafic can not run on UI thread");
			runtimeException.printStackTrace();
			throw runtimeException;
		}
		ArrayList<CSG> back = null;
		SSLSocket socket = (SSLSocket) factory.createSocket(hostname, port);
		try {
			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
			//
			// Create and send request

			ArrayList<CSG> toSend = new ArrayList<CSG>();
			for (CSG c : csgList) {

				if (c.getNumberOfTriangles() == 0) {
					Exception ex = new Exception("No Polygons In Incoming geometry here!");
					ex.printStackTrace();
					throw ex;
				}
				CSG tmp = c.cloneShallow();
				tmp.setOptType(c.getOptType());
				toSend.add(tmp);
			}

			CSGRequest request = new CSGRequest(toSend, operation, points, storage);
			for (ICSGClientEvent e : listeners) {
				try {
					e.toSend(request);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
			if (key != null)
				request.setAPIKEY(key);
			oos.writeObject(request);
			oos.flush();

			// Receive response
			CSGResponse response = (CSGResponse) ois.readObject();
			socket.close();
			for (ICSGClientEvent e : listeners) {
				try {
					e.response(response, request);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
			if (response.getState() != ServerActionState.SUCCESS)
				throw new RuntimeException(response.getMessage());
			// Return results as ArrayList
			back = new ArrayList<CSG>();
			for (CSG c : response.getCsgList()) {
				if (c.getNumberOfTriangles() == 0) {
					System.out.println("Running Operation on server: " + hostname + " " + operation);
					RuntimeException runtimeException = new RuntimeException(
							"Network CSG op resulted in no polygons here ");
					runtimeException.printStackTrace();
					throw runtimeException;
				}
				CSG historySync = c.cloneShallow();
				back.add(historySync);
				for (CSG s : csgList) {
					historySync.historySync(s);
				}
			}
		} catch (Throwable t) {
			socket.close();
			throw t;
		}
		return back;
	}

	/**
	 * Get server connection info
	 */
	public String getServerInfo() {
		return hostname + ":" + port + " (connected: " + ")";
	}

	public static boolean start(String hostname, int port, File f) throws Exception {
		if (getClient() != null)
			return false;
		setClient(new CSGClient(hostname, port, f));
		return true;
	}

	public static void close() {
		client = null;
	}

	public static boolean isRunning() {
		try {
			if (javafx.application.Platform.isFxApplicationThread()) {
				new Exception("ERROR! CSG operation detected on UI thread, this is a BAD idea!");
				return false;// do not run operation on UI thread
			}
		} catch (Exception ex) {
			// can not be a UI thread if ui toolkit is not running
		}
		if (isServerCall())
			return false;
		if (getClient() == null)
			return false;
		return true;
	}

	public static void main(String[] args) {

		String hostname = "localhost";
		int port = 3742;

		// Create client with try-with-resources for automatic cleanup
		try {
			File f = new File("/opt/File.txt");
			CSGClient.start(hostname, port, f);
			// Set a low number to ensure the Server is used. this defaults to 200
			CSG.setMinPolygonsForOffloading(4);
			// Connect to server
			System.out.println("Client info: " + CSGClient.getClient().getServerInfo());

			CSG a = new Cube(20).toCSG();
			CSG b = new Cube(20, 30, 5).toCSG();
			CSG c = new Cube(10, 10, 10).toCSG();
			CSG u = CSG.unionAll(a, b, c);
			CSG d = a.difference(b);
			ArrayList<CSG> m = a.minkowskiHullShape(b);
			CSGClient.close();
		} catch (Exception e) {
			System.err.println("Communication error: " + e.getMessage());
			e.printStackTrace();
		}

		System.out.println("\nClient example completed.");
	}

	public static CSGClient getClient() {
		return client;
	}

	public static void setClient(CSGClient client) {
		CSGClient.client = client;
	}

	public static boolean isServerCall() {
		return serverCall;
	}

	public static void setServerCall(boolean serverCall) {
		CSGClient.serverCall = serverCall;
	}
}

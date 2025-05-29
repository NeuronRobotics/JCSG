package eu.mihosoft.vrl.v3d;

import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.*;
import java.io.*;
import java.security.cert.X509Certificate;

//CSG Client class that maintains connection and provides clean API
class CSGClient {
	// statics
	private static CSGClient client = null;

	// Class Vars

	private String hostname;
	private int port;

	private String key = null;
	
	private static boolean serverCall = false;

	public CSGClient(String hostname, int port, File f) throws Exception {
		this.hostname = hostname;
		this.port = port;
		if (f != null)
			if(f.exists())
				key = Files.readAllLines(f.toPath()).toArray(new String[0])[0];

		Socket socket = new Socket(hostname, port);
		socket.close();

	}

	/**
	 * Perform union operations on consecutive CSG pairs
	 * 
	 * @param csgList List of CSG objects to perform union on
	 * @return List of union results
	 * @throws IOException           if communication error occurs
	 * @throws CSGOperationException if server returns an error
	 */
	public ArrayList<CSG> union(List<CSG> csgList) throws Exception {
		return performOperation(csgList, CSGRemoteOperation.UNION);
	}

	/**
	 * Perform difference operations on consecutive CSG pairs
	 * 
	 * @param csgList List of CSG objects to perform difference on
	 * @return List of difference results
	 * @throws IOException           if communication error occurs
	 * @throws CSGOperationException if server returns an error
	 */
	public ArrayList<CSG> difference(ArrayList<CSG> csgList) throws Exception {
		return performOperation(csgList, CSGRemoteOperation.DIFFERENCE);
	}

	/**
	 * Perform intersect operations on consecutive CSG pairs
	 * 
	 * @param csgList List of CSG objects to perform intersection on
	 * @return List of intersection results
	 * @throws IOException           if communication error occurs
	 * @throws CSGOperationException if server returns an error
	 */
	public ArrayList<CSG> intersect(ArrayList<CSG> csgList) throws Exception {
		return performOperation(csgList, CSGRemoteOperation.INTERSECT);
	}

	/**
	 * Perform minkowskiHullShape operations on consecutive CSG pairs
	 * 
	 * @param csgList List of CSG objects to perform minkowskiHullShape on
	 * @return List of intersection results
	 * @throws IOException           if communication error occurs
	 * @throws CSGOperationException if server returns an error
	 */
	public ArrayList<CSG> minkowskiHullShape(ArrayList<CSG> csgList) throws Exception {
		return performOperation(csgList, CSGRemoteOperation.minkowskiHullShape);
	}

	/**
	 * Perform triangulation on each CSG object
	 * 
	 * @param csgList List of CSG objects to triangulate
	 * @return List of triangulated CSG objects
	 * @throws IOException           if communication error occurs
	 * @throws CSGOperationException if server returns an error
	 */
	public ArrayList<CSG> triangulate(ArrayList<CSG> csgList) throws Exception {
		return performOperation(csgList, CSGRemoteOperation.TRIANGULATE);
	}

	/**
	 * Internal method to perform operations and handle request/response
	 */
	private ArrayList<CSG> performOperation(List<CSG> csgList, CSGRemoteOperation operation) throws Exception {
		SSLContext sslContext = SSLContext.getInstance("TLS");

		// For development: trust all certificates (use proper truststore in production)
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} };
		sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
		SSLSocketFactory factory = sslContext.getSocketFactory();

		try (SSLSocket socket = (SSLSocket) factory.createSocket(hostname, port);
				ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
			System.out.println("Running Operation on server: " + hostname + " " + operation);
			// Create and send request
			CSGRequest request = new CSGRequest(csgList, operation);
			if (key != null)
				request.setAPIKEY(key);
			oos.writeObject(request);
			oos.flush();

			// Receive response
			CSGResponse response = (CSGResponse) ois.readObject();
			socket.close();
			if (response.getState() != ServerActionState.SUCCESS)
				throw new RuntimeException(response.getMessage());
			// Return results as ArrayList
			return new ArrayList<>(response.getCsgList());

		} catch (ClassNotFoundException e) {
			throw new IOException("Invalid response from server", e);
		} catch (IOException e) {
			// Connection might be broken, mark as disconnected
			throw e;
		}
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
		if(isServerCall())
			return false;
		if (getClient() == null)
			return false;
		return true;
	}

	public static void main(String[] args) {

		String hostname = "10.246.200.88";
		int port = 8080;

		// Create client with try-with-resources for automatic cleanup
		try {
			File f = new File("file.txt");
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
			CSG t = d.triangulate(true);
			ArrayList<CSG> m = a.minkowskiHullShape(b);

		} catch (Exception e) {
			System.err.println("Communication error: " + e.getMessage());
			e.printStackTrace();
		}

		System.out.println("\nClient example completed.");
	}

	public static CSGClient getClient() {
		return client;
	}

	private static void setClient(CSGClient client) {
		CSGClient.client = client;
	}

	public static boolean isServerCall() {
		return serverCall;
	}

	public static void setServerCall(boolean serverCall) {
		CSGClient.serverCall = serverCall;
	}
}

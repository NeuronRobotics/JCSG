package eu.mihosoft.vrl.v3d;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//CSG Client class that maintains connection and provides clean API
class CSGClient {
	//statics
	private static CSGClient client=null;
	
	// Class Vars

	private String hostname;
	private int port;
	

	public CSGClient(String hostname, int port) {
		this.hostname = hostname;
		this.port = port;
		try {
			Socket socket = new Socket(hostname, port);
			socket.close();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}


	/**
	 * Perform union operations on consecutive CSG pairs
	 * 
	 * @param csgList List of CSG objects to perform union on
	 * @return List of union results
	 * @throws IOException           if communication error occurs
	 * @throws CSGOperationException if server returns an error
	 */
	public ArrayList<CSG> union(List<CSG> csgList) throws IOException {
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
	public ArrayList<CSG> difference(ArrayList<CSG> csgList) throws IOException {
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
	public ArrayList<CSG> intersect(ArrayList<CSG> csgList) throws IOException {
		return performOperation(csgList, CSGRemoteOperation.INTERSECT);
	}

	/**
	 * Perform triangulation on each CSG object
	 * 
	 * @param csgList List of CSG objects to triangulate
	 * @return List of triangulated CSG objects
	 * @throws IOException           if communication error occurs
	 * @throws CSGOperationException if server returns an error
	 */
	public ArrayList<CSG> triangulate(ArrayList<CSG> csgList) throws IOException {
		return performOperation(csgList, CSGRemoteOperation.TRIANGULATE);
	}

	/**
	 * Internal method to perform operations and handle request/response
	 */
	private ArrayList<CSG> performOperation(List<CSG> csgList, CSGRemoteOperation operation) throws IOException {
		try (Socket socket = new Socket(hostname, port);
				ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {

			// Create and send request
			CSGRequest request = new CSGRequest(csgList, operation);
			oos.writeObject(request);
			oos.flush();

			// Receive response
			CSGRequest response = (CSGRequest) ois.readObject();
			socket.close();
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
	public static boolean start(String hostname,int port) throws IOException {
		if(getClient()!=null)
			return false;
		setClient(new CSGClient(hostname, port));
		return true;
	}
	public static void close() {
		client=null;
		
	}
	public static boolean isRunning() {
		if(getClient()==null)
			return false;
		return true;
	}
	public static void main(String[] args) {

		String hostname = "localhost";
		int port = 8080;

		// Create client with try-with-resources for automatic cleanup
		try  {
			CSGClient.start(hostname, port);
			// Connect to server
			System.out.println("Client info: " + getClient().getServerInfo());

			CSG a=new Cube(20).toCSG();
			CSG b =new Cube(20, 30, 5).toCSG();
			CSG c=new Cube(10, 10, 10).toCSG();
			CSG u=CSG.unionAll(a,b,c);
			CSG d= a.difference(b);
			CSG t=d.triangulate(true);
			
		} catch (IOException e) {
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
}

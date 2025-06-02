package eu.mihosoft.vrl.v3d;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocket;

import eu.mihosoft.vrl.v3d.ext.quickhull3d.HullUtil;

class CSGServerHandler implements Runnable {
	private SSLSocket clientSocket;

	private String[] APIKEY = null;

	public CSGServerHandler(SSLSocket socket, String[] lines) {
		this.clientSocket = socket;
		APIKEY = lines;
	}

	@Override
	public void run() {
		try (ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream());
				ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream())) {

			System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

			// Read the CSG request
			CSGRequest request = (CSGRequest) ois.readObject();
			System.out.println("Received request: " + request.getOperation());
			boolean APIPass = true;
			String apiKey2 = request.getAPIKey();
			if (getAPIKEYs() != null) {
				APIPass = false;
				if (apiKey2!=null)
					for (int i = 0; i < getAPIKEYs().length; i++)
						if (apiKey2.contentEquals(getAPIKEYs()[i])) {
							APIPass = true;
							System.out.println("API Key Match");
							break;
						}
			}

			// Process the request
			CSGResponse response = null;
			if (APIPass) {
				try {
					response = processCSGRequest(request);
				} catch (Throwable t) {
					t.printStackTrace();
					response = new CSGResponse();
					response.setState(ServerActionState.ERROR);
					response.setMessage(t);
				}
			} else {
				response = new CSGResponse();
				response.setState(ServerActionState.BADAPIKEY);
				response.setMessage("Your API key " + apiKey2 + " Does not match server's key");
			}
			// Send back the response
			oos.writeObject(response);
			oos.flush();

			System.out.println("Sent response: " + response);

		} catch (IOException | ClassNotFoundException e) {
			System.err.println("client disconnected: ");
		} finally {
			close();
		}
	}

	private void close() {
		System.out.println("Closing Handler socket");
		try {
			if (!clientSocket.isClosed()) {
				clientSocket.close();
			}
		} catch (IOException e) {
			System.err.println("Error closing client socket: " + e.getMessage());
		}
	}

	private CSGResponse processCSGRequest(CSGRequest request) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		CSGClient.setServerCall(true);
		try {
			List<CSG> csgList = request.getCsgList();
			switch (request.getOperation()) {
			case DIFFERENCE:
				CSG first = csgList.remove(0);
				if(csgList.size()==1) {
					back.add(first.difference(csgList.get(0)));
				}else
					back.add(first.difference(csgList));
				break;
			case INTERSECT:
				CSG f = csgList.remove(0);
				back.add(f.intersect(csgList));
				break;
			case TRIANGULATE:
				CSG.setPreventNonManifoldTriangles(true);
				for (CSG c : csgList)
					back.add(c.triangulate(true));
				break;
			case UNION:
				if(csgList.size()==2) {
					back.add(csgList.get(0).union(csgList.get(1)));
				}else
					back.add(CSG.unionAll(csgList));
				break;
			case minkowskiHullShape:
				CSG m1 = csgList.remove(0);
				CSG t = csgList.remove(0);
				back.addAll(m1.minkowskiHullShape(t));
				break;
			case hull:
				back.add(HullUtil.hull(request.getPoints(), request.getStorage()));
				break;
			default:
				throw new RuntimeException("No Such Operation " + request.getOperation());
			}
		} catch (Throwable t) {
			CSGClient.setServerCall(false);
			throw t;
		}
		CSGClient.setServerCall(false);
		return new CSGResponse(back, request.getOperation());
	}

	public String[] getAPIKEYs() {
		return APIKEY;
	}

	public void setAPIKEYs(String[] aPIKEY) {
		APIKEY = aPIKEY;
	}

}

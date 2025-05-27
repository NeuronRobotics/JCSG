package eu.mihosoft.vrl.v3d;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

import javax.net.ssl.SSLSocket;

class CSGClientHandler implements Runnable {
	private SSLSocket clientSocket;

	public CSGClientHandler(SSLSocket socket) {
		this.clientSocket = socket;
	}

	@Override
	public void run() {
		try (ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream());
				ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream())) {

			System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

			// Read the CSG request
			CSGRequest request = (CSGRequest) ois.readObject();
			System.out.println("Received request: " + request.getOperation());

			// Process the request
			CSGRequest response =  processCSGRequest(request);

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

	private CSGRequest processCSGRequest(CSGRequest request) {
		ArrayList<CSG> back  = new ArrayList<CSG>();
		switch(request.getOperation()) {
		case DIFFERENCE:
			CSG first = request.getCsgList().remove(0);
			back.add(first.difference(request.getCsgList()));
			break;
		case INTERSECT:
			CSG f = request.getCsgList().remove(0);
			back.add(f.intersect(request.getCsgList()));
			break;
		case TRIANGULATE:
			CSG.setPreventNonManifoldTriangles(true);
			for(CSG c:request.getCsgList())
				back.add(c.triangulate(true));
			break;
		case UNION:
			back.add(CSG.unionAll(request.getCsgList()));
			break;
		default:
			break;
		
		}
		return new CSGRequest(back, request.getOperation());
	}

}

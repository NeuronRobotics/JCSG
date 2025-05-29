package eu.mihosoft.vrl.v3d;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

import javax.net.ssl.SSLSocket;

class CSGServerHandler implements Runnable {
	private SSLSocket clientSocket;
	
	private String []APIKEY=null;

	public CSGServerHandler(SSLSocket socket, String[] lines) {
		this.clientSocket = socket;
		APIKEY=lines;
	}

	@Override
	public void run() {
		try (ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream());
				ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream())) {

			System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

			// Read the CSG request
			CSGRequest request = (CSGRequest) ois.readObject();
			System.out.println("Received request: " + request.getOperation());
			boolean APIPass=true;
			if(getAPIKEYs()!=null) {
				APIPass=false;
				for(int i=0;i<getAPIKEYs().length;i++)
					if(request.getAPIKey().contentEquals(getAPIKEYs()[i])) {
						APIPass=true;
						System.out.println("API Key Match");
						break;
					}
			}
			
			// Process the request
			CSGResponse response =null;
			if(APIPass) {
				try {
					response =  processCSGRequest(request);
				}catch(Throwable t) {
					response = new CSGResponse();
					response.setState(ServerActionState.ERROR);
					response.setMessage(t);
				}
			}else {
				response = new CSGResponse();
				response.setState(ServerActionState.BADAPIKEY);
				response.setMessage("Your API key "+request.getAPIKey()+" Does not match server's key");
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
		return new CSGResponse(back, request.getOperation());
	}

	public String [] getAPIKEYs() {
		return APIKEY;
	}

	public void setAPIKEYs(String [] aPIKEY) {
		APIKEY = aPIKEY;
	}

}

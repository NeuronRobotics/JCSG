package eu.mihosoft.vrl.v3d;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

//Main TCP Server class
public class CSGServer {
	private final int port;
	private final ExecutorService threadPool;
	private ServerSocket serverSocket;
	private volatile boolean running = false;

	public CSGServer(int port) {
		this.port = port;
		this.threadPool = Executors.newCachedThreadPool();
	}

	public void start() throws IOException {
		serverSocket = new ServerSocket(port);
		running = true;

		System.out.println("CSG TCP Server started on port " + port);
		System.out.println("Waiting for clients...");

		while (running) {
			try {
				Socket clientSocket = serverSocket.accept();
				threadPool.execute(new CSGClientHandler(clientSocket));
			} catch (IOException e) {
				if (running) {
					System.err.println("Error accepting client connection: " + e.getMessage());
				}
			}
		}
	}

	public void stop() throws IOException {
		running = false;
		if (serverSocket != null && !serverSocket.isClosed()) {
			serverSocket.close();
		}
		threadPool.shutdown();
		try {
			if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
				threadPool.shutdownNow();
			}
		} catch (InterruptedException e) {
			threadPool.shutdownNow();
			Thread.currentThread().interrupt();
		}
		System.out.println("CSG TCP Server stopped");
	}

	public static void main(String[] args) {
		int port = 8080;

		// Parse command line arguments
		if (args.length > 0) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				System.err.println("Invalid port number. Using default port 8080");
			}
		}

		CSGServer server = new CSGServer(port);

		// Add shutdown hook for graceful shutdown
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				server.stop();
			} catch (IOException e) {
				System.err.println("Error during server shutdown: " + e.getMessage());
			}
		}));

		try {
			server.start();
		} catch (IOException e) {
			System.err.println("Failed to start server: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
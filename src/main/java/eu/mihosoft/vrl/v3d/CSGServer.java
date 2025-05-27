package eu.mihosoft.vrl.v3d;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import java.io.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;

//Main TCP Server class
public class CSGServer {
	private final int port;
	private final ExecutorService threadPool;
	private ServerSocket serverSocket;
	private volatile boolean running = false;
	private static final String KEYSTORE_PATH = "serverCredentials.jks";
	private static final String KEYSTORE_PASSWORD = "password";

	public static void ensureKeystoreExists(String keystorePath, String keystorePassword, String alias,
			String commonName) {
		File keystoreFile = new File(keystorePath);

		if (!keystoreFile.exists()) {
			System.out.println("Keystore not found. Generating new keystore: " + keystorePath);
			try {
				if (isKeytoolAvailable()) {
					generateKeystoreWithKeytool(keystorePath, keystorePassword, alias, commonName);
					System.out.println("Keystore generated successfully using keytool.");
				} else {
					throw new RuntimeException(
							"keytool command not found. Please install JDK or create keystore manually using: keytool -genkeypair -alias "
									+ alias + " -keyalg RSA -keysize 2048 -keystore " + keystorePath + " -storepass "
									+ keystorePassword + " -keypass " + keystorePassword + " -dname \"CN=" + commonName
									+ ",OU=Auto,O=Dev,C=US\" -validity 365");
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to generate keystore: " + e.getMessage(), e);
			}
		} else {
			System.out.println("Using existing keystore: " + keystorePath);
		}
	}

	private static void generateKeystoreWithKeytool(String keystorePath, String keystorePassword, String alias,
			String commonName) throws Exception {

		System.out.println("Generating keystore using keytool command...");

		String[] command = { "keytool", "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048",
				"-keystore", keystorePath, "-storepass", keystorePassword, "-keypass", keystorePassword, "-dname",
				"CN=" + commonName + ",OU=Auto-Generated,O=Development,L=Unknown,ST=Unknown,C=US", "-validity", "365" };

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);
		Process process = pb.start();

// Read output
		StringBuilder output = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append("\n");
				System.out.println("keytool: " + line);
			}
		}

		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new RuntimeException("keytool failed with exit code " + exitCode + ". Output: " + output.toString());
		}
	}

	/**
	 * Check if keytool is available on the system
	 */
	public static boolean isKeytoolAvailable() {
		try {
			ProcessBuilder pb = new ProcessBuilder("keytool", "-help");
			pb.redirectErrorStream(true);
			Process process = pb.start();

// Consume output to prevent blocking
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				while (reader.readLine() != null) {
// Just consume the output
				}
			}

			int exitCode = process.waitFor();
			return exitCode == 0;
		} catch (Exception e) {
			return false;
		}
	}

	public CSGServer(int port) {
		this.port = port;
		this.threadPool = Executors.newCachedThreadPool();
	}

	public void start() throws Exception {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				stop();
			} catch (IOException e) {
				System.err.println("Error during server shutdown: " + e.getMessage());
			}
		}));
		// Load the keystore
		KeyStore keyStore = KeyStore.getInstance("JKS");
		ensureKeystoreExists(KEYSTORE_PATH, KEYSTORE_PASSWORD, "server", "localhost");
		keyStore.load(new FileInputStream(KEYSTORE_PATH), KEYSTORE_PASSWORD.toCharArray());

		// Create KeyManagerFactory
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

		// Create SSLContext
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(kmf.getKeyManagers(), null, null);

		// Create SSL server socket
		SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
		SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(port);

		// serverSocket = new ServerSocket(port);
		running = true;

		System.out.println("CSG TCP Server started on port " + port);
		System.out.println("Waiting for clients...");

		while (running) {
			try {
				SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
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

	public static void main(String[] args) throws Exception {
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
		server.start();
	}
}
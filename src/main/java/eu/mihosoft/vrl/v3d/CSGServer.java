package eu.mihosoft.vrl.v3d;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import java.io.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.io.FileOutputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

//Main TCP Server class
public class CSGServer {
	private final int port;
	private final ExecutorService threadPool;
	private volatile boolean running = false;
	private static String KEYSTORE_PATH = "servername";
	private static File directory = new File(System.getProperty("java.io.tmpdir"));
	private static final String KEYSTORE_NAME = "CSGSelfSign";
	private String[] lines = null;
	private SSLServerSocket serverSocket2;

	public CSGServer(int port, File APIKEYS) throws IOException {
		this.port = port;
		this.threadPool = Executors.newCachedThreadPool();
		
		if (APIKEYS == null) {
			throw new NullPointerException("API Key file can not be null");
		}
		if(APIKEYS.exists())
			lines = Files.readAllLines(APIKEYS.toPath()).toArray(new String[0]);
		if(lines!=null) {
			System.out.println("Starting server with "+lines.length+" keys from "+APIKEYS.getAbsolutePath());
		}else {
			System.err.println("NO API KEYFILE Provided: "+APIKEYS.getAbsolutePath());
		}
	}

	public static void ensureKeystoreExists(String keystorePath, String keystorePassword, String alias,
			String commonName) {
		File keystoreFile = new File(keystorePath);

		if (!keystoreFile.exists()) {
			System.out.println("Keystore not found. Generating new keystore: " + keystorePath);
			try {
				generateKeystoreWithBouncyCastle(keystorePath, keystorePassword, alias, commonName);
				System.out.println("Keystore generated successfully using keytool.");

			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException("Failed to generate keystore: " + e.getMessage(), e);
			}
		} else {
			System.out.println("Using existing keystore: " + keystorePath);
		}
	}

// Alternative implementation using Bouncy Castle (if available)
	private static void generateKeystoreWithBouncyCastle(String keystorePath, String keystorePassword, String alias,
			String commonName) throws Exception {

		System.out.println("Generating keystore using Bouncy Castle...");
		// Generate RSA key pair
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048, new SecureRandom());
		KeyPair keyPair = keyPairGenerator.generateKeyPair();

		// Create self-signed certificate
		X509Certificate certificate = createSelfSignedCertificateBC(keyPair, commonName);

		// Create keystore and add the key pair with certificate
		KeyStore keyStore = KeyStore.getInstance("JKS");
		keyStore.load(null, null); // Initialize empty keystore

		Certificate[] certificateChain = { certificate };
		keyStore.setKeyEntry(alias, keyPair.getPrivate(), keystorePassword.toCharArray(), certificateChain);

		// Save keystore to file
		try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
			keyStore.store(fos, keystorePassword.toCharArray());
		}

		System.out.println("Keystore generated successfully at: " + keystorePath);
	}

	private static X509Certificate createSelfSignedCertificateBC(KeyPair keyPair, String commonName) throws Exception {

		PrivateKey privateKey = keyPair.getPrivate();
		PublicKey publicKey = keyPair.getPublic();

		// Certificate validity period (365 days)
		Instant now = Instant.now();
		Date notBefore = Date.from(now);
		Date notAfter = Date.from(now.plus(365, ChronoUnit.DAYS));

		// Create X.500 distinguished name
		String distinguishedName = String.format("CN=%s,OU=Auto-Generated,O=Development,L=Unknown,ST=Unknown,C=US",
				commonName);
		org.bouncycastle.asn1.x500.X500Name x500Name = new org.bouncycastle.asn1.x500.X500Name(distinguishedName);

		// Generate serial number
		BigInteger serialNumber = new BigInteger(64, new SecureRandom());

		// Create certificate builder using BC 1.80 constructor
		SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());

		// Convert dates to Time objects for BC 1.80
		Time notBeforeTime = new Time(notBefore);
		Time notAfterTime = new Time(notAfter);

		X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(x500Name, // issuer (X500Name)
				(BigInteger) serialNumber, // serial (BigInteger)
				(Time) notBeforeTime, // notBefore (Time)
				(Time) notAfterTime, // notAfter (Time)
				x500Name, // subject (X500Name) - same as issuer for self-signed
				(SubjectPublicKeyInfo) subjectPublicKeyInfo // publicKeyInfo (SubjectPublicKeyInfo)
		);

		// Create content signer
		ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);

		// Build and sign certificate
		X509CertificateHolder certHolder = certBuilder.build(contentSigner);

		// Convert to X509Certificate
		JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter();

		return certConverter.getCertificate(certHolder);
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
		String path = getDirectory().getAbsolutePath()+"/"+KEYSTORE_PATH;
		ensureKeystoreExists(path, KEYSTORE_NAME, "server", "localhost");
		keyStore.load(new FileInputStream(path), KEYSTORE_NAME.toCharArray());

		// Create KeyManagerFactory
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(keyStore, KEYSTORE_NAME.toCharArray());

		// Create SSLContext
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(kmf.getKeyManagers(), null, null);

		// Create SSL server socket
		SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
		serverSocket2 = (SSLServerSocket) factory.createServerSocket(port);

		// serverSocket = new ServerSocket(port);
		setRunning(true);

		System.out.println("CSG TCP Server started on port " + port);
		System.out.println("Waiting for clients...");

		while (isRunning()) {
			try {
				SSLSocket clientSocket = (SSLSocket) serverSocket2.accept();
				threadPool.execute(new CSGServerHandler(clientSocket,lines));
			} catch (IOException e) {
				if (isRunning()) {
					System.err.println("Error accepting client connection: " + e.getMessage());
				}
			}
		}
	}

	public void stop() throws IOException {
		setRunning(false);
		if (serverSocket2 != null && !serverSocket2.isClosed()) {
			serverSocket2.close();
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
		int port = 3742;

		// Parse command line arguments
		if (args.length > 0) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				System.err.println("Invalid port number. Using default port 8080");
			}
		}
		File f = new File("file.txt");

		CSGServer server = new CSGServer(port, f);
		server.start();
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean running) {
		this.running = running;
	}

	public static File getDirectory() {
		return directory;
	}

	public static void setDirectory(File directory) {
		CSGServer.directory = directory;
	}
}
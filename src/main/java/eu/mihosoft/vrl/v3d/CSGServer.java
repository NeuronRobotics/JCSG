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
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Date;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.io.FileOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.security.auth.x500.X500Principal;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateIssuerName;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateSubjectName;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

//Main TCP Server class
public class CSGServer {
	private final int port;
	private final ExecutorService threadPool;
	private ServerSocket serverSocket;
	private volatile boolean running = false;
	private static final String KEYSTORE_PATH = "serverCredentials2.jks";
	private static final String KEYSTORE_PASSWORD = "password";

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

	private static void generateKeystoreWithPureJava(String keystorePath, String keystorePassword, String alias,
			String commonName) throws Exception {

		System.out.println("Generating keystore using pure Java APIs...");

// Generate RSA key pair
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048, new SecureRandom());
		KeyPair keyPair = keyPairGenerator.generateKeyPair();

// Create self-signed certificate
		X509Certificate certificate = createSelfSignedCertificate(keyPair, commonName);

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

	private static X509Certificate createSelfSignedCertificate(KeyPair keyPair, String commonName) throws Exception {

		PrivateKey privateKey = keyPair.getPrivate();
		PublicKey publicKey = keyPair.getPublic();

// Certificate validity period (365 days)
		Date notBefore = new Date();
		LocalDateTime notAfterLocal = LocalDateTime.now().plusDays(365);
		Date notAfter = Date.from(notAfterLocal.atZone(ZoneId.systemDefault()).toInstant());

// Create X.509 certificate info
		X509CertInfo certInfo = new X509CertInfo();

// Set certificate version (V3)
		certInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));

// Set serial number
		BigInteger serialNumber = new BigInteger(64, new SecureRandom());
		certInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(serialNumber));

// Set algorithm ID
		AlgorithmId algorithmId = new AlgorithmId(AlgorithmId.SHA256_oid);
		certInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algorithmId));

// Set subject and issuer (same for self-signed)
		String distinguishedName = String.format("CN=%s,OU=Auto-Generated,O=Development,L=Unknown,ST=Unknown,C=US",
				commonName);
		X500Name x500Name = new X500Name(distinguishedName);
		certInfo.set(X509CertInfo.SUBJECT, new CertificateSubjectName(x500Name));
		certInfo.set(X509CertInfo.ISSUER, new CertificateIssuerName(x500Name));

// Set validity period
		CertificateValidity validity = new CertificateValidity(notBefore, notAfter);
		certInfo.set(X509CertInfo.VALIDITY, validity);

// Set public key
		certInfo.set(X509CertInfo.KEY, new CertificateX509Key(publicKey));

// Create and sign the certificate
		X509CertImpl certificate = new X509CertImpl(certInfo);
		certificate.sign(privateKey, "SHA256withRSA");

// Update algorithm ID after signing
		algorithmId = (AlgorithmId) certificate.get(X509CertImpl.SIG_ALG);
		certInfo.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algorithmId);
		certificate = new X509CertImpl(certInfo);
		certificate.sign(privateKey, "SHA256withRSA");

		return certificate;
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
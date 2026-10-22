/*******************************************************************************
 * Copyright RTR-GmbH
 * Copyright 2014-2016 SPECURE GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package at.rtr.rmbt.qos.testserver;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import at.rtr.rmbt.qos.testserver.entity.TestCandidate;
import at.rtr.rmbt.qos.testserver.servers.AbstractUdpServer;
import at.rtr.rmbt.qos.testserver.service.EventJob;
import at.rtr.rmbt.qos.testserver.service.ServiceManager;
import at.rtr.rmbt.qos.testserver.tcp.TcpMultiClientServer;
import at.rtr.rmbt.qos.testserver.tcp.TcpWatcherRunnable;
import at.rtr.rmbt.qos.testserver.udp.NioUdpMultiClientServer;
import at.rtr.rmbt.qos.testserver.udp.UdpMultiClientServer;
import at.rtr.rmbt.qos.testserver.udp.UdpTestCandidate;
import at.rtr.rmbt.qos.testserver.udp.UdpWatcherRunnable;
import at.rtr.rmbt.qos.testserver.util.LoggingService;
import at.rtr.rmbt.qos.testserver.util.RuntimeGuardService;
import at.rtr.rmbt.qos.testserver.util.TestServerConsole;
import at.rtr.rmbt.qos.testserver.ServerPreferences.ServiceSetting;
import at.rtr.rmbt.qos.testserver.ServerPreferences.TestServerServiceEnum;
import at.rtr.rmbt.qos.testserver.ServerPreferences.UdpPort;
import at.rtr.rmbt.util.Randomizer;

/**
 * 
 * @author lb
 *
 */
public class TestServerImpl {

	public final ConcurrentHashMap<Integer, List<AbstractUdpServer<?>>> udpServerMap = new ConcurrentHashMap<Integer, List<AbstractUdpServer<?>>>();
	public final ConcurrentHashMap<Integer, List<TcpMultiClientServer>> tcpServerMap = new ConcurrentHashMap<Integer, List<TcpMultiClientServer>>();
	
	/**
	 * server socket list (=awaiting client test requests)
	 */
    public volatile List<ServerSocket> serverSocketList = new ArrayList<ServerSocket>();
	
	public SSLServerSocketFactory sslServerSocketFactory;
	public ServerPreferences serverPreferences;
	public final ServiceManager serviceManager = new ServiceManager();
	
	public final Set<ClientHandler> clientHandlerSet = ConcurrentHashMap.newKeySet();
	
	/**
	 * number of open control connections per client IP (see {@link ServerPreferences#getMaxConnectionsPerIp()})
	 */
	private final ConcurrentHashMap<InetAddress, Integer> connectionsPerIp = new ConcurrentHashMap<>();
	
	/**
	 * registers a new control connection
	 * @return false if the client IP already has the max. number of open connections
	 */
	public boolean acquireConnection(final InetAddress addr) {
		final int max = serverPreferences.getMaxConnectionsPerIp();
		final boolean[] accepted = {false};
		connectionsPerIp.compute(addr, (k, count) -> {
			final int current = count == null ? 0 : count;
			if (current >= max) {
				return count;
			}
			accepted[0] = true;
			return current + 1;
		});
		return accepted[0];
	}
	
	/**
	 * releases a control connection registered with {@link #acquireConnection(InetAddress)}
	 */
	public void releaseConnection(final InetAddress addr) {
		connectionsPerIp.computeIfPresent(addr, (k, count) -> count <= 1 ? null : count - 1);
	}
	
	/**
	 * 
	 */
	public final Randomizer randomizer = new Randomizer(8000, 12000, 3);
	
	/**
	 * is used for all control connection threads
	 */
    private ExecutorService mainServerPool;
    
    /**
     * is used for all other threads
     */
    private final ExecutorService COMMON_THREAD_POOL = Executors.newCachedThreadPool();
    
    /**
     * 
     */
    private final TestServerConsole console;
    
	/**
	 *     
	 */
    private QoSService qosService = null;
    
    /**
     * 
     */
    private AtomicBoolean isShutdownHookEnabled = new AtomicBoolean(true);
    
    /**
     * 
     */
    private AtomicBoolean isAlreadyShuttingDown = new AtomicBoolean(false);
    
	/**
	 * 
	 * @param port
	 * @param isSsl
	 * @param inetAddress
	 * @return
	 * @throws IOException
	 */
	public ServerSocket createServerSocket(int port, boolean isSsl, InetAddress inetAddress) throws IOException {
		ServerSocket socket = null;
		SocketAddress sa = new InetSocketAddress(inetAddress, port);
		if (!isSsl || !serverPreferences.useSsl()) {
			socket = new ServerSocket();
		}
		else {
			socket = sslServerSocketFactory.createServerSocket();
		}
		
		try {
			socket.bind(sa);
		}
		catch (Exception e) {
			TestServerConsole.errorReport("TCP " + port, "TCP Socket on port " + port, e, 0, TestServerServiceEnum.TCP_SERVICE);
			throw e;
		}
		
		socket.setReuseAddress(true);
		return socket;
	}
	
	final PrintStream tempErr;
	final PrintStream tempOut;
	
	public TestServerImpl() {
		console = createTestServerConsole();
		
		tempErr = System.err;
		tempOut = System.out;
		
		System.setErr(console);
		System.setOut(console);
	}
	
	public TestServerConsole createTestServerConsole() {
		return new TestServerConsole(this);
	}
	
	public void run() throws Exception {
		run(new String[0]);
	}

	public void run(final String[] args ) throws Exception {
		// errors are reported by TestServer.main()
		final ServerPreferences sp = args.length > 0 ? new ServerPreferences(args) : new ServerPreferences();
		run(sp);
	}
	
	
	/**
	 * 
	 * @return
	 */
	public QoSService getQoSService() {
		return this.qosService;
	}

	/**
	 * Loads the TLS keystore: from the file configured with <code>server.ssl.keystore</code> or,
	 * if none is configured, from the classpath resource {@link TestServer#QOS_KEY_FILE}.
	 * @throws TestServerException if no keystore can be found
	 */
	public static KeyStore loadKeyStore(final ServerPreferences serverPreferences) throws Exception {
		if (serverPreferences.getSslCertificate() != null || serverPreferences.getSslKey() != null) {
			return loadPemKeyStore(serverPreferences);
		}
		final KeyStore ks = KeyStore.getInstance(serverPreferences.getSslKeystoreType());
		final String keystoreFile = serverPreferences.getSslKeystore();
		try (InputStream fis = keystoreFile != null ? new FileInputStream(keystoreFile) 
				: TestServer.class.getResourceAsStream(TestServer.QOS_KEY_FILE)) {
			if (fis == null) {
				throw new TestServerException("ERROR: SSL is enabled but no keystore was found. Set '" 
						+ ServerPreferences.PARAM_SERVER_SSL_KEYSTORE + "' (and '" + ServerPreferences.PARAM_SERVER_SSL_KEYSTORE_PASSWORD 
						+ "') in the config file or embed a keystore at " + TestServer.QOS_KEY_FILE + " in the jar.", null);
			}
			ks.load(fis, serverPreferences.getSslKeystorePassword().toCharArray());
		}
		return ks;
	}

	/**
	 * Builds an in-memory keystore from a PEM certificate chain (<code>server.ssl.certificate</code>, e.g. a
	 * Let's Encrypt fullchain.pem) and a PEM PKCS#8 private key (<code>server.ssl.key</code>, e.g. privkey.pem).
	 */
	private static KeyStore loadPemKeyStore(final ServerPreferences serverPreferences) throws Exception {
		if (serverPreferences.getSslCertificate() == null || serverPreferences.getSslKey() == null) {
			throw new TestServerException("ERROR: both '" + ServerPreferences.PARAM_SERVER_SSL_CERTIFICATE + "' and '" 
					+ ServerPreferences.PARAM_SERVER_SSL_KEY + "' have to be set.", null);
		}
		
		final List<Certificate> chain = new ArrayList<>();
		try (InputStream in = new FileInputStream(serverPreferences.getSslCertificate())) {
			chain.addAll(CertificateFactory.getInstance("X.509").generateCertificates(in));
		}
		if (chain.isEmpty()) {
			throw new TestServerException("ERROR: no certificate found in " + serverPreferences.getSslCertificate(), null);
		}
		
		final String pem = new String(Files.readAllBytes(Paths.get(serverPreferences.getSslKey())), StandardCharsets.US_ASCII);
		if (!pem.contains("-----BEGIN PRIVATE KEY-----")) {
			throw new TestServerException("ERROR: " + serverPreferences.getSslKey() 
					+ " is not an unencrypted PKCS#8 PEM key (-----BEGIN PRIVATE KEY-----). Convert it with: openssl pkcs8 -topk8 -nocrypt", null);
		}
		final byte[] der = Base64.getMimeDecoder().decode(pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").trim());
		final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(der);
		PrivateKey privateKey = null;
		for (String algorithm : new String[] {chain.get(0).getPublicKey().getAlgorithm(), "EC", "RSA", "Ed25519"}) {
			try {
				privateKey = KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
				break;
			}
			catch (Exception e) {
				// try next algorithm
			}
		}
		if (privateKey == null) {
			throw new TestServerException("ERROR: unsupported private key in " + serverPreferences.getSslKey(), null);
		}
		
		final KeyStore ks = KeyStore.getInstance("PKCS12");
		ks.load(null, null);
		ks.setKeyEntry("qos", privateKey, serverPreferences.getSslKeystorePassword().toCharArray(), chain.toArray(new Certificate[0]));
		return ks;
	}

	/**
	 * @throws TestServerException 
	 * 
	 */
	public void run(final ServerPreferences serverPreferences) throws Exception {
		this.serverPreferences = serverPreferences;
		LoggingService.init(this);
		
		if (!LoggingService.IS_LOGGING_AVAILABLE) {
			throw new TestServerException("ERROR: All logging disabled! Cannot start server. Please enable at least one logging target [syslog, console, file]", null);
		}
		
	    TestServerConsole.log("\nStarting QoSTestServer " + TestServer.VERSION_REVISION + " (" + TestServer.TEST_SERVER_VERSION_NAME + ") with settings: \n" + serverPreferences.toString() + "\n\n", 
	    		-1, TestServerServiceEnum.TEST_SERVER);
	    
	    if (!ClientHandler.isTokenCheckEnabled()) {
	    	TestServerConsole.log("WARNING: client token verification is DISABLED - every client is accepted without authentication!", 
	    			-1, TestServerServiceEnum.TEST_SERVER);
	    }
	    else if (serverPreferences.getSecretKeys().isEmpty()) {
	    	throw new TestServerException("client token verification is enabled but no key is configured (set '" + ServerPreferences.PARAM_SERVER_SECRET_KEY 
	    			+ "' / -k, or '" + ServerPreferences.PARAM_SERVER_TOKEN_CHECK + "=false' / -ntc for testing only)", null);
	    }
	    else {
	    	TestServerConsole.log("Client token verification enabled (" + (serverPreferences.isTokenV2Only() ? "v2 tokens only" : "v1 and v2 tokens") 
	    			+ ") with " + serverPreferences.getSecretKeys().size() + " key(s): " + serverPreferences.getSecretKeys(), 
	    			-1, TestServerServiceEnum.TEST_SERVER);
	    }
	    
		console.start();
	    
	    if (!TestServer.USE_FIXED_THREAD_POOL) {
	    	mainServerPool = Executors.newCachedThreadPool();
	    } 
	    else {
	    	mainServerPool = Executors.newFixedThreadPool(serverPreferences.getMaxThreads());
	    }
	    
	    try {
	    	
		    if (serverPreferences.useSsl()) {
			    /*******************************
			     * initialize SSLContext and SSLServerSocketFactory:
			     */

	            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
	            
	            // Load the key chain
	            final KeyStore ks;
	            try {
	            	ks = loadKeyStore(serverPreferences);
	            }
	            catch (TestServerException e) {
	            	throw e;
	            }
	            catch (Exception e) {
	            	throw new TestServerException("cannot load the TLS certificate/keystore", e);
	            }
	            kmf.init(ks, serverPreferences.getSslKeystorePassword().toCharArray());
			    final SSLContext sslContext = SSLContext.getInstance("TLS");
	            // Initialize the SSL context
	            // no client certificates are requested (clients authenticate with the token), so the
	            // JDK default trust managers are sufficient
	            sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
	            
				sslServerSocketFactory = (SSLServerSocketFactory) sslContext.getServerSocketFactory();    	
		    }
		    
			for (InetAddress addr : serverPreferences.getInetAddrBindToSet()) {
				ServerSocket serverSocket;
				if (serverPreferences.useSsl()) {
			    	serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket();
				}
				else {
					serverSocket = new ServerSocket();
				}
			
				serverSocket.setReuseAddress(true);
				try {
					serverSocket.bind(new InetSocketAddress(addr, serverPreferences.getServerPort()));
				}
				catch (IOException e) {
					throw new TestServerException("cannot bind the control port " + addr.getHostAddress() + ":" + serverPreferences.getServerPort(), e);
				}
				serverSocketList.add(serverSocket);
				
				this.qosService = new QoSService(mainServerPool, serverSocket, null);
				Thread mainThread = new Thread(this.qosService);
			    mainThread.start();
			}

		    Iterator<UdpPort> portIterator = serverPreferences.getUdpPortSet().iterator();

		    while (portIterator.hasNext()) {
		    	final List<AbstractUdpServer<?>> udpServerList = new ArrayList<AbstractUdpServer<?>>();
	    		final UdpPort udpPort = portIterator.next();
		    	for (InetAddress addr : serverPreferences.getInetAddrBindToSet()) {
		    		AbstractUdpServer<?> udpServer = null;
		    		try {
		    			//udpServer = new UdpMultiClientServer(port, addr);
		    			udpServer = udpPort.isNio ? new NioUdpMultiClientServer(udpPort.port, addr) : new UdpMultiClientServer(udpPort.port, addr);
		    		}
		    		catch (Exception e) { 
		    			TestServerConsole.error("TestServer INIT; Opening UDP Server on port: " + udpPort, e, 0, TestServerServiceEnum.TEST_SERVER); 
		    		}
		    		
		    		if (udpServer != null) {
		    			udpServerList.add(udpServer);
		    			getCommonThreadPool().execute(udpServer);
		    		}
		    	}
			    
		    	if (udpServerList.size() > 0) {
		    		udpServerMap.put(udpPort.port, udpServerList);
		    	}
		    }
		    
		    //start UDP watcher service:
		    final UdpWatcherRunnable udpWatcherRunnable = new UdpWatcherRunnable();
		    serviceManager.addService(udpWatcherRunnable);
		    //start TCP watcher service:
		    final TcpWatcherRunnable tcpWatcherRunnable = new TcpWatcherRunnable();
		    serviceManager.addService(tcpWatcherRunnable);
		    //register runtime guard service:
		    final RuntimeGuardService runtimeGuardService = new RuntimeGuardService();
		    serviceManager.addService(runtimeGuardService);
		    
		    //dispatch onStart event:
		    serviceManager.dispatchEvent(EventJob.EventType.ON_TEST_SERVER_START);

		    //finally, start all plugins & services:
		    for (ServiceSetting service : serverPreferences.getPluginMap().values()) {
		    	TestServerConsole.log("Starting plugin '" + service.getName() + "'...", 0, TestServerServiceEnum.TEST_SERVER);
		    	service.start();
		    }
		    
		    //add shutdown hook (ctrl+c):
		    Runtime.getRuntime().addShutdownHook(
		    		new Thread() {
		    			public void run() {
		    				if (isShutdownHookEnabled.get()) {
			    				TestServerConsole.log("CTRL-C. Close! ", -1, TestServerServiceEnum.TEST_SERVER);
			    				shutdown();
		    				}
		    			}
		    		});
		} 
	    catch (TestServerException e) {
	    	throw e;  // reported by TestServer.main()
	    }
	    catch (Exception e) {
	    	throw new TestServerException("cannot start the server", e);
	    }
	}
	
	/**
	 * 
	 */
	public void shutdown() 
	{
		if (qosService != null) {
			qosService.stop();
		}

		if (isAlreadyShuttingDown.getAndSet(true)) {
			return;
		}
		
		TestServerConsole.log("Shutting down QoS TestServer...", 0, TestServerServiceEnum.TEST_SERVER);
		
	    //stop all plugins & services:
	    for (ServiceSetting service : serverPreferences.getPluginMap().values()) {
	    	service.stop();
	    	TestServerConsole.log("Plugin '" + service.getName() + "' stopped.", 0, TestServerServiceEnum.TEST_SERVER);
	    }
		
		//dispatch onStop event:
		serviceManager.dispatchEvent(EventJob.EventType.ON_TEST_SERVER_STOP);

		Map<String, Object> serviceResultMap = serviceManager.shutdownAll(true);
		if (serviceResultMap != null) {
			for (Entry<String, Object> entry : serviceResultMap.entrySet()) {
				if (entry.getValue() != null) {
					TestServerConsole.log("Service '" + entry.getKey() + "' result: " + entry.getValue(), 0, TestServerServiceEnum.TEST_SERVER);
				}
			}
		}
		
		mainServerPool.shutdownNow(); 
		try {
			mainServerPool.awaitTermination(4L, TimeUnit.SECONDS);
		} 
		catch (InterruptedException e) { 
			//e.printStackTrace();
		}
	    finally {
	    	System.setErr(tempErr);
	    	System.setOut(tempOut);
	    	
	    	for (ServerSocket serverSocket : serverSocketList) {
				if (serverSocket != null && !serverSocket.isClosed()) {
					try {
						serverSocket.close();
						System.out.println("\nServerSocket: " + serverSocket + " closed.");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
	    	}
	    }
	}

	/**
	 * 
	 * @param port
	 * @return
	 * @throws Exception
	 */
	public DatagramSocket createDatagramSocket(int port, InetAddress addr) throws Exception {
		if (addr == null) {
			return new DatagramSocket(port);
		}
		else {
			return new DatagramSocket(port, addr);
		}
	}
	
	/**
	 * 
	 * @param port
	 * @param addr
	 * @return
	 * @throws Exception
	 */
	public DatagramChannel createDatagramChannel(int port, InetAddress addr) throws Exception {
		final DatagramChannel channel = DatagramChannel.open();
		if (addr == null) {
			channel.bind(new InetSocketAddress(port));
		}
		else {
			channel.bind(new InetSocketAddress(addr, port));
		}
		
		return channel;
	}
	
    /**
     * 
     * @return
     */
    public ConcurrentHashMap<Integer, List<TcpMultiClientServer>> getTcpServerMap() {
    	return tcpServerMap;
    }
    
    /**
     * 
     * @param port
     */
    public synchronized void unregisterTcpServer(Integer port) {
    	if (tcpServerMap.remove(port) != null) {
	    	TestServerConsole.log("Removed TCP server object on port: " + port, 1, TestServerServiceEnum.TCP_SERVICE);	
    	}
    }
    
    /**
     * 
     * @param port
     * @param server
     * @throws Exception 
     */
    public List<TcpMultiClientServer> registerTcpCandidate(Integer port, Socket socket) throws Exception {
    	List<TcpMultiClientServer> tcpServerList;

    	synchronized(tcpServerMap) {
	    	//if port not mapped create complete tcp server list
			if ((tcpServerList = tcpServerMap.get(port)) == null) {
				tcpServerList = new ArrayList<>();
				for (InetAddress addr : serverPreferences.getInetAddrBindToSet()) {
					tcpServerList.add(new TcpMultiClientServer(port, addr));
				}
				tcpServerMap.put(port, tcpServerList);
			}
			else {
				//there are some tcp servers active on this port, compare ip list with tcp server list and create missing servers
				tcpServerList = tcpServerMap.get(port);

				Set<InetAddress> inetAddrSet = new HashSet<>();
				for (TcpMultiClientServer tcpServer : tcpServerList) {
					inetAddrSet.add(tcpServer.getInetAddr());
				}
				Set<InetAddress> inetAddrBindToSet = new HashSet<>(serverPreferences.getInetAddrBindToSet());
				inetAddrBindToSet.removeAll(inetAddrSet);
				for (InetAddress addr : inetAddrBindToSet) {
					tcpServerList.add(new TcpMultiClientServer(port, addr));
				}
			}

			//if ip check is enabled register the candidate's ip on the server's whitelist
			if (serverPreferences.isIpCheck()) {
				for (TcpMultiClientServer tcpServer : tcpServerList) {
					tcpServer.registerCandidate(socket.getInetAddress(), TestCandidate.DEFAULT_TTL);
				}
			}
			
			//prepare all servers on this port for incoming connections (open sockets/refresh ttl)
			for (TcpMultiClientServer tcpServer : tcpServerList) {
				tcpServer.prepare();
			}
    	}
    	
		return tcpServerList;
    }
    
    /**
     * 
     * @param <T>
     * @param localAddr
     * @param port
     * @param uuid
     * @param udpData
     * @return
     */
    @SuppressWarnings("unchecked")
	public synchronized <T extends Closeable> AbstractUdpServer<T> registerUdpCandidate(final InetAddress localAddr, final int port, final String uuid, final UdpTestCandidate udpData) {
		try {
			TestServerConsole.log("Trying to register UDP Candidate on " + localAddr + ":" + port , 0, TestServerServiceEnum.UDP_SERVICE);
			for (AbstractUdpServer<?> udpServer : udpServerMap.get(port)) {
				TestServerConsole.log("Comparing: " + localAddr + " <-> " + udpServer.getAddress(), 0, TestServerServiceEnum.UDP_SERVICE);
				if (udpServer.getAddress().equals(localAddr) || (udpServer.getAddress() != null && udpServer.getAddress().isAnyLocalAddress())) {
					TestServerConsole.log("Registering UDP Candidate on " + localAddr + ":" + port , 0, TestServerServiceEnum.UDP_SERVICE);
					TestServerConsole.log("Registering UDP Candidate for UdpServer: " + udpServer.toString(), 2, TestServerServiceEnum.UDP_SERVICE);
					((AbstractUdpServer<T>) udpServer).getIncomingMap().put(uuid, udpData);
					return (AbstractUdpServer<T>) udpServer;
				}
			}				
		}
		catch (Exception e) {
			TestServerConsole.error("Register UDP candidate on port: " + port, e, 1, TestServerServiceEnum.UDP_SERVICE);
		}
		
		return null;
	}	

    /**
     * 
     * @return
     */
	public ExecutorService getCommonThreadPool() {
		return COMMON_THREAD_POOL;
	}
	
	public synchronized void setShutdownHookEnabled(boolean enabled) {
		this.isShutdownHookEnabled.set(enabled);
	}
	
	public synchronized boolean isShutdownHookEnabled() {
		return isShutdownHookEnabled.get();
	}

	/**
	 * 
	 * @return original error PrintStream
	 */
	public PrintStream getTempErr() {
		return tempErr;
	}

	/**
	 * 
	 * @return original output PrintStream
	 */
	public PrintStream getTempOut() {
		return tempOut;
	}
}

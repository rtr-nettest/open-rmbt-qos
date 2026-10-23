/*******************************************************************************
 * Copyright RTR-GmbH
 * Copyright 2013-2015 Rundfunk und Telekom Regulierungs-GmbH (RTR-GmbH)
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import at.rtr.rmbt.qos.testserver.entity.SecretKey;
import at.rtr.rmbt.qos.testserver.plugin.rest.RestService;

/**
 * 
 * @author lb
 *
 */
public class ServerPreferences {
	public static enum TestServerServiceEnum {
		TEST_SERVER("TEST SERVER"),
		TCP_SERVICE("TCP MONITOR"),
		UDP_SERVICE("UDP MONITOR"),
		RUNTIME_GUARD_SERVICE("RUNTIME GUARD SERVICE");
		
		protected String name;
		private TestServerServiceEnum(String name) {
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
	}
	
	public class UdpPort  {
		final boolean isNio;
		final int port;
		
		public UdpPort(final boolean isNio, final int port) {
			this.isNio = isNio;
			this.port = port;
		}

		@Override
		public String toString() {
			return "UdpPort [isNio=" + isNio + ", port=" + port + "]";
		}	
	}
	
	public class TcpCompetence {
		boolean hasSipCompetence;
		
		public boolean hasSipCompetence() {
			return hasSipCompetence;
		}

		public TcpCompetence setHasSipCompetence(boolean hasSipCompetence) {
			this.hasSipCompetence = hasSipCompetence;
			return this;
		}
	}
	
	public final static int TEST_SERVER_ID = 0;
	public final static int TCP_SERVICE_ID = 1;
	public final static int UDP_SERVICE_ID = 2;
	
	public static final String ARG_SERVER_PORT = "-P";
	public static final String ARG_UDP_PORT_RANGE = "-U";
	public static final String ARG_MAX_THREADS = "-T";
	public static final String ARG_HELP = "-H";
	public static final String ARG_IP_CHECK = "-IC";
	public static final String ARG_IP = "-IP";
	public static final String ARG_CONFIG_FILE = "-F";
	public static final String ARG_SECRET_KEY = "-K";
	public static final String ARG_NO_TOKEN_CHECK = "-NTC";
	public static final String ARG_TOKEN_V2_ONLY = "-V2ONLY";
	public static final String ARG_USE_SSL = "-S";
	public static final String ARG_TLS13_ONLY = "-TLS13ONLY";
	public static final String ARG_LOG_FULL_IP = "-FULLIP";
	public static final String ARG_VERBOSE = "-V";
	public static final String ARG_VERBOSE2 = "-VV";
	public static final String ARG_LOG = "-L";
	
	public static final String PARAM_SERVER_PORT = "server.port";
	public static final String PARAM_SERVER_THREADS = "server.threads";
	public static final String PARAM_SERVER_MAX_CONNECTIONS_PER_IP = "server.connections.perip";
	public static final String PARAM_SERVER_SECRET_KEY = "server.secret";
	public static final String PARAM_SERVER_TOKEN_CHECK = "server.token.check";
	public static final String PARAM_SERVER_TOKEN_V2_ONLY = "server.token.v2only";
	public static final String PARAM_SERVER_USE_SSL = "server.ssl";
	public static final String PARAM_SERVER_SSL_KEYSTORE = "server.ssl.keystore";
	public static final String PARAM_SERVER_SSL_KEYSTORE_PASSWORD = "server.ssl.keystore.password";
	public static final String PARAM_SERVER_SSL_KEYSTORE_TYPE = "server.ssl.keystore.type";
	public static final String PARAM_SERVER_SSL_CERTIFICATE = "server.ssl.certificate";
	public static final String PARAM_SERVER_SSL_KEY = "server.ssl.key";
	public static final String PARAM_SERVER_SSL_TLS13_ONLY = "server.ssl.tls13only";
	public static final String PARAM_SERVER_LOG_FULL_IP = "server.log.fullip";
	public static final String PARAM_SERVER_VERBOSE = "server.verbose";
	public static final String PARAM_SERVER_UDP_MIN_PORT = "server.udp.minport";
	public static final String PARAM_SERVER_UDP_MAX_PORT = "server.udp.maxport";
	public static final String PARAM_SERVER_UDP_PORT_LIST = "server.udp.ports";
	public static final String PARAM_SERVER_UDP_NIO_PORT_LIST = "server.udp.nio.ports";
	public static final String PARAM_SERVER_LOGGING = "server.logging";
	public static final String PARAM_SERVER_LOGGING_PATTERN = "server.log.pattern";
	public static final String PARAM_SERVER_SYSLOG = "server.syslog";
	public static final String PARAM_SERVER_SYSLOG_HOST = "server.syslog.host";
	public static final String PARAM_SERVER_SYSLOG_PATTERN = "server.syslog.pattern";
	public static final String PARAM_SERVER_LOG_CONSOLE = "server.log.console";
	public static final String PARAM_SERVER_COMMAND_CONSOLE = "server.console";
	public static final String PARAM_SERVER_LOG_FILE = "server.log";
	public static final String PARAM_SERVER_UDP_SERVICE_LOG_FILE = "server.log.udp";
	public static final String PARAM_SERVER_TCP_SERVICE_LOG_FILE = "server.log.tcp";
	public static final String PARAM_SERVER_TCP_IP_CHECK = "server.ip.check";
	public static final String PARAM_SERVER_TCP_COMPETENCE_SIP = "server.tcp.competence.sip";
	public static final String PARAM_SERVER_IP = "server.ip";
	
	public static final String REGEX_PORT_LIST = "([0-9]+)[,]?";
	public static final String REGEX_IP_LIST = "([a-fA-F0-9.:]+)[,]?";
	
	private int serverPort = 5234;
	private int udpPortMin = 0;
	private int udpPortMax = 0;
	private final Set<UdpPort> udpPortSet = new TreeSet<>(new Comparator<UdpPort>() {
		@Override
		public int compare(UdpPort o1, UdpPort o2) {
			return Integer.compare(o1.port, o2.port);
		}
	});
	
	private final Map<Integer, TcpCompetence> tcpCompetenceMap = new HashMap<>();
	
	private int maxThreads = 100;
	/** max concurrent control connections per client IP (newer clients open one connection per test) */
	private int maxConnectionsPerIp = 50;
	private boolean useSsl = false;
	private String sslKeystore = null;
	private String sslKeystorePassword = TestServer.QOS_KEY_PASSWORD;
	private String sslKeystoreType = null;
	private String sslCertificate = null;
	private String sslKey = null;
	private boolean isTls13Only = false;
	private boolean isLogFullIp = false;
	private int verboseLevel = 0;
	/** keys used to verify client tokens; a token is valid if it was signed with any of them */
	private final List<SecretKey> secretKeys = new ArrayList<>();
	private boolean isTokenV2Only = false;
	private boolean isIpCheck = false;
	private boolean isTokenCheck = true;
	private boolean isLoggingEnabled = true;
	private String loggingPattern = "%p %d{ISO8601} - %m%n";
	private boolean isSyslogEnabled = true;
	private String syslogHost = "localhost";
	private String syslogPattern = "%p %d{ISO8601} %c - %m%n";
	private boolean isConsoleLog = false;
	private boolean isCommandConsoleEnabled = false;
	private final TreeMap<TestServerServiceEnum, String> logFileMap = new TreeMap<>();
	private final Set<InetAddress> inetAddrBindToSet = new HashSet<>();
	private final long startTimestamp = System.currentTimeMillis();

	///////////////////////////
	// Plugins/Services:
	///////////////////////////
	public final static String PLUGIN_REST_SERVICE = "REST";
	private final Map<String, ServiceSetting> pluginMap = new HashMap<>();
	
	/**
	 * 
	 * @throws TestServerException
	 * @throws UnknownHostException 
	 */
	public ServerPreferences() throws TestServerException {
		loadFromConfigFile("config.properties");
		setUdpPortSet();
	    checkConstraints();
	}
	
	/**
	 * 
	 * @param is
	 * @throws TestServerException
	 */
	public ServerPreferences(final InputStream is) throws TestServerException {
		loadFromConfigFile(is);
		setUdpPortSet();
	    checkConstraints();
	}
	
	/**
	 * 
	 * @param args
	 * @throws TestServerException
	 * @throws UnknownHostException 
	 */
	public ServerPreferences(String[] args) throws TestServerException {
		if (args == null || args.length < 0) {
			loadFromConfigFile("config.properties");
			setUdpPortSet();
		    checkConstraints();
			return;
		}
		
		try {
		    for (int i = 0; i < args.length; i++) {
		    	String arg = args[i].toUpperCase();
		    	
		    	if (arg.equals(ARG_SERVER_PORT)) {
		    		serverPort = Integer.parseInt(args[++i]);
		    	}
		    	else if (arg.equals(ARG_IP)) {
		    		inetAddrBindToSet.add(InetAddress.getByName(args[++i]));
		    	}
		    	else if (arg.equals(ARG_IP_CHECK)) {
		    		isIpCheck = true;
		    	}
		    	else if (arg.equals(ARG_UDP_PORT_RANGE)) {
		    		udpPortMin = Integer.parseInt(args[++i]);
		    		udpPortMax = Integer.parseInt(args[++i]);
		    	}
		    	else if (arg.equals(ARG_MAX_THREADS)) {
		    		maxThreads = Integer.parseInt(args[++i]);
		    		if (maxThreads <= 10) {
		    			writeWarningNumThreadsTooLowString(maxThreads);
		    		}
		    	}
		    	else if (arg.equals(ARG_CONFIG_FILE)) {
		    		loadFromConfigFile(args[++i]);
		    	}
		    	else if (arg.equals(ARG_SECRET_KEY)) {
		    		addSecretKeys("-k", args[++i]);
		    	}
		    	else if (arg.equals(ARG_NO_TOKEN_CHECK)) {
		    		isTokenCheck = false;
		    	}
		    	else if (arg.equals(ARG_TOKEN_V2_ONLY)) {
		    		isTokenV2Only = true;
		    	}
		    	else if (arg.equals(ARG_USE_SSL)) {
		    		useSsl = true;
		    	}
		    	else if (arg.equals(ARG_TLS13_ONLY)) {
		    		isTls13Only = true;
		    	}
		    	else if (arg.equals(ARG_LOG_FULL_IP)) {
		    		isLogFullIp = true;
		    	}
		    	else if (arg.equals(ARG_VERBOSE)) {
		    		verboseLevel = 1;
		    	}
		    	else if (arg.equals(ARG_VERBOSE2)) {
		    		verboseLevel = 2;
		    	}
		    	else if (arg.equals(ARG_HELP)) {
		    		writeErrorString();
		    		System.exit(0);
		    	}
		    	else if (arg.equals(ARG_LOG)) {
	    			logFileMap.put(TestServerServiceEnum.TEST_SERVER, args[++i]);
		    	}
		    	else {
		    		throw new TestServerException("unknown argument: " + args[i] + " (-h for help)", null);
		    	}
		    }
		    
		    setUdpPortSet();
		}
		catch (TestServerException e) {
			throw e;
		}
		catch (ArrayIndexOutOfBoundsException e) {
			throw new TestServerException("missing value for the last argument (-h for help)", null);
		}
		catch (Exception e) {
			throw new TestServerException("invalid arguments: " + e.getMessage() + " (-h for help)", e);
		}
		
	    checkConstraints();
	}
	
	/**
	 * 
	 */
	private void setUdpPortSet() {
		if (udpPortMin > 0) {
			for (int port = udpPortMin; port <= udpPortMax; port++) {
				udpPortSet.add(new UdpPort(false, port));
			}
		}
	}

	/**
	 * 
	 * @param fileName
	 * @throws TestServerException
	 */
	private void loadFromConfigFile(String fileName) throws TestServerException {
		final InputStream is;
		try {
			if (new File(fileName).exists()) {
				is = new FileInputStream(fileName);
			}
			else {
				is = getClass().getResourceAsStream(fileName.startsWith("/") ? fileName : "/" + fileName);
			}
		}
		catch (final IOException ex) {
			throw new TestServerException("cannot read config file '" + fileName + "': " + ex.getMessage(), ex);	
		}
		
		if (is == null) {
			throw new TestServerException("config file '" + fileName + "' not found in " + new File("").getAbsolutePath()
					+ " - create it (see config.properties in the repository) or start with arguments (-h for help)", null);
		}

		try (InputStream in = is) {
			loadFromConfigFile(in);
		}
		catch (final IOException ex) {
			throw new TestServerException("cannot read config file '" + fileName + "': " + ex.getMessage(), ex);
		}
	}

	/**
	 * 
	 * @param is
	 * @throws TestServerException
	 */
	private void loadFromConfigFile(final InputStream is) throws TestServerException {
		Properties prop = new Properties();
	   	try {
	   		prop.load(is);
	   		
			String param = prop.getProperty(PARAM_SERVER_PORT);
	   		if (param!=null) {
		   		serverPort = Integer.parseInt(param.trim());	   			
	   		}

	   		param = prop.getProperty(PARAM_SERVER_IP);
	   		if (param!=null) {
	   			for (String ip : param.split(",")) {
	   				ip = ip.trim();
	   				if (ip.isEmpty()) {
	   					continue;
	   				}
	   				// IP addresses only (no host names), IPv6 optionally in brackets
	   				if (!ip.matches("\\[?[0-9a-fA-F.:]+\\]?")) {
	   					throw new TestServerException("invalid IP address in " + PARAM_SERVER_IP + ": '" + ip + "' (IP addresses only, comma-separated)", null);
	   				}
	   				inetAddrBindToSet.add(InetAddress.getByName(ip.replace("[", "").replace("]", "")));
	   			}
	   		}
	   		
	   		param = prop.getProperty(PARAM_SERVER_TCP_IP_CHECK);
	   		if (param!=null) {
		   		isIpCheck = Boolean.parseBoolean(param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_THREADS);
	   		if (param!=null) {
		   		maxThreads = Integer.parseInt(param.trim());	   			
	   		}
	   		
	   		param = prop.getProperty(PARAM_SERVER_UDP_MIN_PORT);
	   		if (param!=null) {
		   		udpPortMin = Integer.parseInt(param.trim()); 			
	   		}
	   		
	   		param = prop.getProperty(PARAM_SERVER_UDP_MAX_PORT);
	   		if (param!=null) {
		   		udpPortMax = Integer.parseInt(param.trim());
	   		}
	   		
	   		param = prop.getProperty(PARAM_SERVER_USE_SSL);
	   		if (param!=null) {
		   		useSsl = Boolean.parseBoolean(param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_SSL_KEYSTORE);
	   		if (param!=null && !param.trim().isEmpty()) {
		   		sslKeystore = param.trim();
	   		}

	   		param = prop.getProperty(PARAM_SERVER_SSL_KEYSTORE_PASSWORD);
	   		if (param!=null) {
		   		sslKeystorePassword = param.trim();
	   		}

	   		param = prop.getProperty(PARAM_SERVER_SSL_KEYSTORE_TYPE);
	   		if (param!=null && !param.trim().isEmpty()) {
		   		sslKeystoreType = param.trim();
	   		}

	   		param = prop.getProperty(PARAM_SERVER_SSL_CERTIFICATE);
	   		if (param!=null && !param.trim().isEmpty()) {
		   		sslCertificate = param.trim();
	   		}

	   		param = prop.getProperty(PARAM_SERVER_SSL_KEY);
	   		if (param!=null && !param.trim().isEmpty()) {
		   		sslKey = param.trim();
	   		}

	   		param = prop.getProperty(PARAM_SERVER_VERBOSE);
	   		if (param!=null) {
		   		verboseLevel = Integer.parseInt(param.trim());
	   		}
	   		
	   		param = prop.getProperty(PARAM_SERVER_SECRET_KEY);
	   		if (param!=null) {
		   		addSecretKeys(PARAM_SERVER_SECRET_KEY, param);
	   		}
	   		
	   		//additional named keys: server.secret.<name>=<key>
	   		for (String name : new TreeSet<>(prop.stringPropertyNames())) {
	   			if (name.startsWith(PARAM_SERVER_SECRET_KEY + ".")) {
	   				addSecretKeys(name.substring(PARAM_SERVER_SECRET_KEY.length() + 1), prop.getProperty(name));
	   			}
	   		}

	   		param = prop.getProperty(PARAM_SERVER_MAX_CONNECTIONS_PER_IP);
	   		if (param!=null) {
		   		maxConnectionsPerIp = Integer.parseInt(param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_TOKEN_CHECK);
	   		if (param!=null) {
		   		isTokenCheck = Boolean.parseBoolean(param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_TOKEN_V2_ONLY);
	   		if (param!=null) {
		   		isTokenV2Only = Boolean.parseBoolean(param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_SSL_TLS13_ONLY);
	   		if (param!=null) {
		   		isTls13Only = Boolean.parseBoolean(param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_LOG_FULL_IP);
	   		if (param!=null) {
		   		isLogFullIp = Boolean.parseBoolean(param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_COMMAND_CONSOLE);
	   		if (param!=null) {
	   			isCommandConsoleEnabled = Boolean.parseBoolean(param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_SYSLOG);
	   		if (param!=null) {
	   			isSyslogEnabled = Boolean.parseBoolean(param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_SYSLOG_HOST);
	   		if (param!=null) {
	   			syslogHost = param.trim();
	   		}

	   		param = prop.getProperty(PARAM_SERVER_SYSLOG_PATTERN);
	   		if (param!=null) {
	   			syslogPattern = param.trim();
	   		}

	   		param = prop.getProperty(PARAM_SERVER_LOGGING);
	   		if (param!=null) {
	   			isLoggingEnabled = Boolean.parseBoolean(param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_LOGGING_PATTERN);
	   		if (param!=null) {
	   			loggingPattern = param.trim();
	   		}

	   		param = prop.getProperty(PARAM_SERVER_LOG_CONSOLE);
	   		if (param!=null) {
	   			isConsoleLog = Boolean.parseBoolean(param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_LOG_FILE);
	   		if (param!=null) {
	   			logFileMap.put(TestServerServiceEnum.TEST_SERVER, param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_UDP_SERVICE_LOG_FILE);
	   		if (param!=null) {
	   			logFileMap.put(TestServerServiceEnum.UDP_SERVICE, param.trim());
	   		}

	   		param = prop.getProperty(PARAM_SERVER_TCP_SERVICE_LOG_FILE);
	   		if (param!=null) {
	   			logFileMap.put(TestServerServiceEnum.TCP_SERVICE, param.trim());
	   		}
	   		
	   		param = prop.getProperty(PARAM_SERVER_UDP_PORT_LIST);
	   		if (param!=null) {
	   			Pattern p = Pattern.compile(REGEX_PORT_LIST);
	   			Matcher m = p.matcher(param);
   				while (m.find()) {
   					udpPortSet.add(new UdpPort(false, Integer.valueOf(m.group(1))));
   				}
	   		}

	   		param = prop.getProperty(PARAM_SERVER_TCP_COMPETENCE_SIP);
	   		if (param!=null) {
	   			Pattern p = Pattern.compile(REGEX_PORT_LIST);
	   			Matcher m = p.matcher(param);
   				while (m.find()) {
   					final Integer port = Integer.valueOf(m.group(1));
   					TcpCompetence tcpCompetence = tcpCompetenceMap.get(port);
   					if (tcpCompetence == null) {
   						tcpCompetence = new TcpCompetence();
   						tcpCompetenceMap.put(port, tcpCompetence);
   					}
   					tcpCompetence.setHasSipCompetence(true);
   				}
	   		}

	   		param = prop.getProperty(PARAM_SERVER_UDP_NIO_PORT_LIST);
	   		if (param!=null) {
	   			Pattern p = Pattern.compile(REGEX_PORT_LIST);
	   			Matcher m = p.matcher(param);
   				while (m.find()) {
   					final UdpPort udpPort = new UdpPort(true, Integer.valueOf(m.group(1)));
   					if (udpPortSet.contains(udpPort)) {
   						throw new TestServerException("Cannot create non blocking datagram channel. UDP port: " + udpPort.port + " already defined as blocking.", null);
   					}
   					else {
   						udpPortSet.add(udpPort);
   					}
   				}
	   		}

	   		/////////////////////////////////
	   		//	Services/Plugins:
	   		/////////////////////////////////
	   		
	   		pluginMap.put("REST", new RestService(prop, this));

	   	}
	   	catch (NumberFormatException ex) {
	   		throw new TestServerException("invalid number in the config file (" + ex.getMessage() + ")", null);
	   	}
	   	catch (java.net.UnknownHostException ex) {
	   		throw new TestServerException("invalid IP address in the config file (" + ex.getMessage() + ")", null);
	   	}
	   	catch (IOException ex) {
			throw new TestServerException("cannot read the config file: " + ex.getMessage(), ex);
	   	}
	}
	
	/**
	 * 
	 * @throws TestServerException
	 * @throws UnknownHostException 
	 */
	private void checkConstraints() throws TestServerException {
		if (udpPortMin > udpPortMax) {
			throw new TestServerException("UDP MIN PORT (=" + udpPortMin + ") MUST BE LOWER THAN UDP MAX PORT (=" + udpPortMax + ")", null);
		}
		if (maxThreads < 5) {
			throw new TestServerException("NUMBER OF THREADS TOO LOW (" + maxThreads + ")", null);
		}

		//create log paths:
		for (String fileName : logFileMap.values()) {
			File f = new File(fileName);
			File dir = new File(f.getParent());
			if (!dir.exists()) {
				dir.mkdir();
			}
		}
		
		if (inetAddrBindToSet.isEmpty()) {
			try {
				inetAddrBindToSet.add(InetAddress.getByName("0.0.0.0"));
			}
			catch (Exception e) {
				throw new TestServerException(e.getLocalizedMessage(), e);
			}
		}
	}
	
	/**
	 * 
	 * @return
	 */
	public int getServerPort() {
		return serverPort;
	}
	
	/**
	 * 
	 * @param serverPort
	 */
	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}
	
	/**
	 * 
	 * @return
	 */
	public int getUdpPortMin() {
		return udpPortMin;
	}
	
	/**
	 * 
	 * @param udpPortMin
	 */
	public void setUdpPortMin(int udpPortMin) {
		this.udpPortMin = udpPortMin;
	}
	
	/**
	 * 
	 * @return
	 */
	public int getMaxThreads() {
		return maxThreads;
	}

	/**
	 * 
	 * @param maxThreads
	 */
	public void setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
	}

	/**
	 * 
	 * @return
	 */
	public int getUdpPortMax() {
		return udpPortMax;
	}
	
	/**
	 * 
	 * @param udpPortMax
	 */
	public void setUdpPortMax(int udpPortMax) {
		this.udpPortMax = udpPortMax;
	}
	
	/**
	 * 
	 * @return the first configured secret key or null
	 */
	public String getSecretKey() {
		return secretKeys.isEmpty() ? null : secretKeys.get(0).key();
	}
	
	/**
	 * 
	 * @return all keys a client token may be signed with
	 */
	public List<SecretKey> getSecretKeys() {
		return Collections.unmodifiableList(secretKeys);
	}
	
	/**
	 * replaces all configured keys
	 * @param secretKey a single key or a comma separated list of keys
	 */
	public void setSecretKey(String secretKey) {
		secretKeys.clear();
		addSecretKeys(PARAM_SERVER_SECRET_KEY, secretKey);
	}
	
	/**
	 * adds one key or a comma separated list of keys (duplicates and empty entries are ignored)
	 * @param label name of the key(s) used in log messages; numbered if a list is given
	 * @param keys
	 */
	public void addSecretKeys(String label, String keys) {
		if (keys == null) {
			return;
		}
		final String[] keyArray = keys.split(",");
		for (int i = 0; i < keyArray.length; i++) {
			final String key = keyArray[i].trim();
			if (!key.isEmpty() && secretKeys.stream().noneMatch(k -> k.key().equals(key))) {
				secretKeys.add(new SecretKey(keyArray.length > 1 ? label + "[" + i + "]" : label, key));
			}
		}
	}
	
	/**
	 * @return true if only v2 tokens (IP bound) are accepted (<code>server.token.v2only</code> or <code>-v2only</code>)
	 */
	public boolean isTokenV2Only() {
		return isTokenV2Only;
	}

	public void setTokenV2Only(boolean isTokenV2Only) {
		this.isTokenV2Only = isTokenV2Only;
	}
	
	/**
	 * @return true if TLS 1.2 is disabled and only TLS 1.3 is offered (<code>server.ssl.tls13only</code> or <code>-tls13only</code>)
	 */
	public boolean isTls13Only() {
		return isTls13Only;
	}

	public void setTls13Only(boolean isTls13Only) {
		this.isTls13Only = isTls13Only;
	}
	
	/**
	 * @return true if client IP addresses are logged in full (<code>server.log.fullip</code> or <code>-fullip</code>);
	 * 		by default the last IPv4 octet and all but the first three IPv6 groups (/48) are removed
	 */
	public boolean isLogFullIp() {
		return isLogFullIp;
	}

	public void setLogFullIp(boolean isLogFullIp) {
		this.isLogFullIp = isLogFullIp;
	}
	
	/**
	 * 
	 * @return
	 */
	public String getSslKeystore() {
		return sslKeystore;
	}

	/**
	 * @return PEM file with the certificate chain (leaf first), or null
	 */
	public String getSslCertificate() {
		return sslCertificate;
	}

	/**
	 * @return PEM file with the PKCS#8 private key, or null
	 */
	public String getSslKey() {
		return sslKey;
	}

	public String getSslKeystorePassword() {
		return sslKeystorePassword;
	}

	/**
	 * @return the configured keystore type, or a type derived from the keystore file name
	 * (PKCS12 for .p12/.pfx, otherwise JKS)
	 */
	public String getSslKeystoreType() {
		if (sslKeystoreType != null) {
			return sslKeystoreType;
		}
		if (sslKeystore != null && (sslKeystore.toLowerCase().endsWith(".p12") || sslKeystore.toLowerCase().endsWith(".pfx"))) {
			return "PKCS12";
		}
		return TestServer.QOS_KEY_TYPE;
	}

	/**
	 * 
	 * @return
	 */
	public boolean useSsl() {
		return useSsl;
	}
	
	/**
	 * 
	 * @param useSsl
	 */
	public void setUseSsl(boolean useSsl) {
		this.useSsl = useSsl;
	}
	
	/**
	 * 
	 * @return
	 */
	public int getVerboseLevel() {
		return verboseLevel;
	}
	
	/**
	 * 
	 * @param verboseLevel
	 */
	public void setVerboseLevel(int verboseLevel) {
		this.verboseLevel = verboseLevel;
	}

	/**
	 * 
	 * @return
	 */
	public boolean isLoggingEnabled() {
		return isLoggingEnabled;
	}

	/**
	 * 
	 * @param isLoggingEnabled
	 */
	public void setLoggingEnabled(boolean isLoggingEnabled) {
		this.isLoggingEnabled = isLoggingEnabled;
	}

	/**
	 * 
	 * @return
	 */
	public Map<String, ServiceSetting> getPluginMap() {
		return pluginMap;
	}

	/**
	 * 
	 */
	public static void writeErrorString() {
		System.out.println("Supported parameters:");
		System.out.println("-h => shows this help.");
		System.out.println("-s => ssl connection.");
		System.out.println("-ip => the ip, the test server will be bound to.");
		System.out.println("-ic => register all TCP test candidates and make an ip check before responding.");
		System.out.println("-v => verbose level 1, more debug output.");
		System.out.println("-vv => verbose level 2, even more debug output.");
		System.out.println("-f [file_name] => initializes the server by using a config file (default: config.properties).");
		System.out.println("-p [port_number] => initializes the server on this port number (default: 5234).");
		System.out.println("-u [min_port_number] [max_port_number] => supported udp port range by this server: from min_port_number to max_port_number (both inclusive).");
		System.out.println("-t [max_threads] => The max number of threads (clients) this server can handle at the same time (default: 100).");
		System.out.println("-k [secret_key] => A secret key used to verify client tokens (may be repeated or a comma separated list).");
		System.out.println("-fullip => log full client IP addresses (default: anonymized to 77.116.45.x / 2001:db8:1234:x; config: server.log.fullip=true).");
		System.out.println("-tls13only => offer only TLS 1.3 on the control port, no TLS 1.2 (config: server.ssl.tls13only=true).");
		System.out.println("-v2only => accept only v2 tokens (bound to the client IP), reject legacy v1 tokens (config: server.token.v2only=true).");
		System.out.println("-ntc => do not verify client tokens (INSECURE, lab/testing only; config: server.token.check=false).");
		System.out.println("\nExample: -p 5234 -u 10000 20000 => makes the server listen on port 5234 for incoming test requests and let it accept udp test connections on ports 10000 to 20000 (inclusive).");
		System.out.println("\nWithout parameters the server reads config.properties from the working directory (it must exist).");
		System.out.println("With parameters only the parameters are used, plus a config file given with -f.");
		System.out.println("---------------------------------------------");
	}

	/**
	 * 
	 * @param threads
	 */
	public void writeWarningNumThreadsTooLowString(int threads) {
		System.out.println("---------------------------------------------");
		System.out.println("Warning: number of threads is low (" + threads + ")\nThe TestServer can handle only this amount of clients at the same time. Waiting times may occure.");
		System.out.println("---------------------------------------------\n");
	}

	/**
	 * 
	 * @return
	 */
	public Set<UdpPort> getUdpPortSet() {
		return udpPortSet;
	}

	/**
	 * 
	 * @return
	 */
	public TreeMap<TestServerServiceEnum, String> getLogFileMap() {
		return logFileMap;
	}

	/**
	 * 
	 * @return
	 */
	public Set<InetAddress> getInetAddrBindToSet() {
		return inetAddrBindToSet;
	}

	/**
	 * 
	 * @return
	 */
	/**
	 * @return false if client token (HMAC) verification has been disabled (<code>server.token.check=false</code> or <code>-ntc</code>)
	 */
	public boolean isTokenCheck() {
		return isTokenCheck;
	}

	public void setTokenCheck(boolean isTokenCheck) {
		this.isTokenCheck = isTokenCheck;
	}

	public int getMaxConnectionsPerIp() {
		return maxConnectionsPerIp;
	}

	public void setMaxConnectionsPerIp(int maxConnectionsPerIp) {
		this.maxConnectionsPerIp = maxConnectionsPerIp;
	}

	public boolean isIpCheck() {
		return isIpCheck;
	}

	/**
	 * 
	 * @param isIpCheck
	 */
	public void setIpCheck(boolean isIpCheck) {
		this.isIpCheck = isIpCheck;
	}

	public boolean isConsoleLog() {
		return isConsoleLog;
	}

	public void setConsoleLog(boolean isConsoleLog) {
		this.isConsoleLog = isConsoleLog;
	}

	public boolean isCommandConsoleEnabled() {
		return isCommandConsoleEnabled;
	}

	public void setCommandConsoleEnabled(boolean isCommandConsoleEnabled) {
		this.isCommandConsoleEnabled = isCommandConsoleEnabled;
	}
	
	public long getStartTimestamp() {
		return startTimestamp;
	}
	
	public boolean isSyslogEnabled() {
		return isSyslogEnabled;
	}

	public void setSyslogEnabled(boolean isSyslogEnabled) {
		this.isSyslogEnabled = isSyslogEnabled;
	}

	public String getSyslogHost() {
		return syslogHost;
	}

	public void setSyslogHost(String syslogHost) {
		this.syslogHost = syslogHost;
	}
	
	public String getSyslogPattern() {
		return syslogPattern;
	}

	public void setSyslogPattern(String syslogPattern) {
		this.syslogPattern = syslogPattern;
	}
	
	public String getLoggingPattern() {
		return loggingPattern;
	}

	public void setLoggingPattern(String loggingPattern) {
		this.loggingPattern = loggingPattern;
	}

	public Map<Integer, TcpCompetence> getTcpCompetenceMap() {
		return tcpCompetenceMap;
	}

	@Override
	public String toString() {
		return "ServerPreferences [serverPort=" + serverPort + ", udpPortMin="
				+ udpPortMin + ", udpPortMax=" + udpPortMax + ", udpPortSet="
				+ udpPortSet + ", maxThreads=" + maxThreads + ", maxConnectionsPerIp=" + maxConnectionsPerIp + ", useSsl="
				+ useSsl + ", sslKeystore=" + (sslCertificate != null ? sslCertificate + " + " + sslKey 
						: sslKeystore != null ? sslKeystore : "classpath:" + TestServer.QOS_KEY_FILE)
				+ ", isTls13Only=" + isTls13Only + ", isLogFullIp=" + isLogFullIp
				+ ", verboseLevel=" + verboseLevel + ", secretKey="
				+ secretKeys + ", isTokenCheck=" + isTokenCheck + ", isTokenV2Only=" + isTokenV2Only + ", isIpCheck=" + isIpCheck
				+ ", isLoggingEnabled=" + isLoggingEnabled
				+ ", loggingPattern=" + loggingPattern + ", isSyslogEnabled="
				+ isSyslogEnabled + ", syslogHost=" + syslogHost
				+ ", syslogPattern=" + syslogPattern + ", isConsoleLog="
				+ isConsoleLog + ", isCommandConsoleEnabled="
				+ isCommandConsoleEnabled + ", logFileMap=" + logFileMap
				+ ", inetAddrBindToSet=" + inetAddrBindToSet
				+ ", startTimestamp=" + startTimestamp + ", pluginMap="
				+ pluginMap + "]"
				+ "\n\tOnline since: " + (new java.util.Date(startTimestamp)).toString();
	}
	
	
	
	/////////////////////////////////////////////////////////
	//
	//		Server Services
	//
	/////////////////////////////////////////////////////////
	
	public static abstract class ServiceSetting {
		protected boolean isEnabled;
		protected final String name;
		
		public ServiceSetting(String name, boolean isEnabled) {
			this.isEnabled = isEnabled;
			this.name = name;
		}
		
		public boolean isEnabled() {
			return isEnabled;
		}

		public void setEnabled(boolean isEnabled) {
			this.isEnabled = isEnabled;
		}
		
		public String getName() {
			return name;
		}

		public abstract void start() throws UnknownHostException;
		public abstract void stop();
		public abstract void setParam(Properties properties);
	}	
}

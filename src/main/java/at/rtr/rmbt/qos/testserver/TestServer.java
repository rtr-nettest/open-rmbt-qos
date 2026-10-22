/*******************************************************************************
 * Copyright RTR-GmbH
 * Copyright 2013-2016 Rundfunk und Telekom Regulierungs-GmbH (RTR-GmbH)
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

import java.io.InputStream;
import java.util.Properties;


/**
 * 
 * @author lb
 *
 */
public class TestServer {
	/**
	 * major version number
	 */
	public final static String TEST_SERVER_VERSION_MAJOR = "3";
	
	/**
	 * minor version number
	 */
	public final static String TEST_SERVER_VERSION_MINOR = "1";
	
	/**
	 * patch version number
	 */
	public final static String TEST_SERVER_VERSION_PATCH = "1";
	
	/**
	 * server code name
	 * <ul>
	 * 	<li><b>0.01 - 0.61</b> - ANONYMOUS PROXY</li>
	 * 	<li><b>0.61 - 0.91</b> - BANDWIDTH THROTTLING
	 * 		<p>major changes: support for multiple tests with single connection (client and server side)</p>  
	 * 	</li>
	 * 	<li><b>1.00 - 1.04</b> - CENSORSHIP'N CONTROL
	 * 		<p>major changes: implement rest services</p>
	 * 	</li>
	 * 	<li><b>2.00 - 2.19</b> - DROPPED PACKET
	 * 		<p>major changes: voip test, rmbtutil</p></li>
	 * 	<li><b>2.20 - 2.30</b> - DROPPED PACKET
	 * 		<p>major changes: non blocking udp server for udp and voip tests</p>
	 * 	</li>
	 * 	<li><b>3.0.0 - x.x.x</b> - ???
	 * 		<p>major changes: syslog support, logging framework: log4j</p>
	 * 		<p>major fixes: udp nio empty array fix</p>
	 * 	</li>
	 * </ul>
	 */
	public final static String TEST_SERVER_CODENAME = "DROPPED PACKET";
	
	/**
	 * build version from git: full describe with commit hash, e.g. "v2.0.0-4-ged32b3d" ("v2.0.0" on the tag)
	 */
	public final static String VERSION_FULL = loadVersionProp("version.full");
	
	/**
	 * build version from git plus branch, e.g. "v2.0.0-4-ged32b3d-master"
	 */
	public final static String VERSION_REVISION = loadVersionProp("version.revision");
	
	private static String loadVersionProp(final String key) {
		try (InputStream is = TestServer.class.getResourceAsStream("/version.properties")) {
			if (is == null) {
				return "dev";
			}
			final Properties props = new Properties();
			props.load(is);
			return props.getProperty(key, "dev").trim();
		}
		catch (Exception e) {
			return "dev";
		}
	}
	
	/**
	 * full version string
	 */
	public final static String TEST_SERVER_VERSION_NAME = "QoSTS \"" + TEST_SERVER_CODENAME + "\" " + TEST_SERVER_VERSION_MAJOR + "." + TEST_SERVER_VERSION_MINOR;
	
	/**
	 * key path
	 */
	public final static String QOS_KEY_FILE = "/crt/qosserver.jks";
	
	/**
	 * key type
	 */
	public final static String QOS_KEY_TYPE = "JKS";
	
	/**
	 * key password
	 */
	public final static String QOS_KEY_PASSWORD = "123456qwertz"; 
	
	public final static String RMBT_ENCRYPTION_STRING = "TLS";
	
	public final static boolean USE_FIXED_THREAD_POOL = true;
	public final static int MAX_THREADS = 200;
	
	private static TestServerImpl instance; 
	
	public static TestServerImpl newInstance() {
		instance = new TestServerImpl();
		return instance;
	}
	
	public static TestServerImpl getInstance() {
		if (instance == null) {
			instance = new TestServerImpl();
		}
		return instance;
	}
	
	/**
	 * 
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		getInstance().run(args);
	}
}

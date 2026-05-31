/*******************************************************************************
 * Copyright 2019 alladin-IT GmbH
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import at.rtr.rmbt.qos.testserver.ServerPreferences.UdpPort;

/**
 *
 * @author Lukasz Budryk (lb@alladin.at)
 *
 */
public class ServerPreferencesTest {

	public void assertDefaultSeverPreferences(final ServerPreferences sp) {
		assertNotNull(sp, "ServerPreferences != null");
		assertEquals(1, sp.getInetAddrBindToSet().size(), "Amount of interfaces to bind to = 1");
		final Iterator<InetAddress> ifSet = sp.getInetAddrBindToSet().iterator();
		assertEquals("127.0.0.1", ifSet.next().getHostAddress(), "Interface address [0] to bind to = 127.0.0.1");
		assertEquals(5233, sp.getServerPort(), "Server port = 5233");
		assertTrue(sp.useSsl(), "SSL/TLS flag is set to true");
		assertNotNull(sp.toString());
	}

	@Test
	public void loadPreferencesFromDefaultResourcesPath() throws TestServerException {
		ServerPreferences sp = new ServerPreferences();
		assertDefaultSeverPreferences(sp);
	}

	@Test
	public void loadPreferencesFromDefaultResourcesPathProvidingNullArgumentsArray() throws TestServerException {
		ServerPreferences sp = new ServerPreferences((String[]) null);
		assertDefaultSeverPreferences(sp);
	}

	@Test
	public void loadPreferencesFromInputStream() throws TestServerException {
		ServerPreferences sp = new ServerPreferences(getClass().getResourceAsStream("config.properties"));
		assertNotNull(sp, "ServerPreferences != null");
		assertEquals(1, sp.getInetAddrBindToSet().size(), "Amount of interfaces to bind to = 1");
		final Iterator<InetAddress> ifSet = sp.getInetAddrBindToSet().iterator();
		assertEquals("127.0.0.1", ifSet.next().getHostAddress(), "Interface address [0] to bind to = 127.0.0.1");
		assertEquals(25001, sp.getServerPort(), "Server port = 25001");
		assertFalse(sp.useSsl(), "SSL/TLS flag is set to false");
	}

	@Test
	public void loadPreferencesFromSpecificPath() throws TestServerException, URISyntaxException {
		final ServerPreferences sp = new ServerPreferences(new String[] {
			"-f", "config.properties"});
		assertDefaultSeverPreferences(sp);
	}

	public void loadPreferencesWithNonExistingConfigFileAndFallbackToDefaultSettings() throws TestServerException {
		ServerPreferences sp = new ServerPreferences(new String[] {
				"-f","nonExisitingConfigFile_r7j94dz0kxt35uihz5guiuih32uqi.properties"});
		assertDefaultSeverPreferences(sp);
	}

	@Test
	public void loadPreferencesWithArguments() throws TestServerException {
		ServerPreferences sp = new ServerPreferences(new String[] {
				"-ip", "0.0.0.0",
				"-ip", "0.0.0.1",
				"-p", "8080",
				"-s",
				"-v",
				"-vv",
				"-k", "SECRET_KEY",
				"-ic",
				"-u", "10000", "10010",
				"-t", "111"});
		assertNotNull(sp, "ServerPreferences != null");
		assertEquals(2, sp.getInetAddrBindToSet().size(), "Amount of interfaces to bind to != 2");
		final Iterator<InetAddress> ifSet = sp.getInetAddrBindToSet().iterator();
		assertEquals("0.0.0.0", ifSet.next().getHostAddress(), "Interface address [0] to bind to != 0.0.0.0");
		assertEquals("0.0.0.1", ifSet.next().getHostAddress(), "Interface address [1] to bind to != 0.0.0.1");
		assertEquals(8080, sp.getServerPort(), "Server port != 8080");
		assertTrue(sp.useSsl(), "SSL/TLS flag is set to false");
		assertEquals(2, sp.getVerboseLevel(), "Verbose level != 2");
		assertTrue(sp.isIpCheck(), "IP check is false");
		assertEquals(10000, sp.getUdpPortMin(), "UDP port range min port != 10000");
		assertEquals(10010, sp.getUdpPortMax(), "UDP port range max port != 10010");
		assertEquals(111, sp.getMaxThreads(), "Max threads != 111");
		assertEquals("SECRET_KEY", sp.getSecretKey(), "Secret key != 'SECRET_KEY'");
	}

	@Test
	public void loadPreferencesWithDifferentUdpPortsIncludingNio() throws TestServerException {
		ServerPreferences sp = new ServerPreferences(getClass().getResourceAsStream("config_udp.properties"));
		assertNotNull(sp, "ServerPreferences != null");

		assertEquals(2, sp.getUdpPortSet().size(), "Amount of UDP ports to bind to != 2");

		final Iterator<UdpPort> ifSet = sp.getUdpPortSet().iterator();

		UdpPort port = ifSet.next();
		assertEquals(1, port.port, "UDP Port number [0] != 1");
		assertFalse(port.isNio, "UDP Port number [0] == nio");
		assertNotNull(port.toString());

		port = ifSet.next();
		assertEquals(2, port.port, "UDP Port number [1] != 2");
		assertTrue(port.isNio, "UDP Port number [1] != nio");
		assertNotNull(port.toString());
	}

	@Test
	public void loadPreferencesWithIllegalArgumentsThenThrowTestServerException() {
		assertThrows(TestServerException.class, () -> new ServerPreferences(new String[] {
				"-illegalArgument1,", "-illegalArgument2"}));
	}

	@Test
	public void loadIllegalPreferencesWithDifferentUdpPortsIncludingNioThenThrowTestServerException() {
		assertThrows(TestServerException.class,
				() -> new ServerPreferences(getClass().getResourceAsStream("config_udp_err.properties")));
	}

	@Test
	public void loadIllegalPreferencesWithZeroThreadsSetThenThrowTestServerException() {
		assertThrows(TestServerException.class, () -> new ServerPreferences(new String[] {
				"-t","0"}));
	}

	@Test
	public void loadIllegalPreferencesWithMaxUdpPortLowerThanMinUdpPortThenThrowTestServerException() {
		assertThrows(TestServerException.class, () -> new ServerPreferences(new String[] {
				"-u","10", "1"}));
	}

	@Test
	public void loadPreferencesWithoutValidInterfaceThenSetDefaultInterface() throws TestServerException {
		ServerPreferences sp = new ServerPreferences(new String[] {
				"-vv"});

		assertNotNull(sp, "ServerPreferences != null");
		assertEquals(1, sp.getInetAddrBindToSet().size(), "Amount of interfaces to bind to != 1");
		final Iterator<InetAddress> ifSet = sp.getInetAddrBindToSet().iterator();
		assertEquals("0.0.0.0", ifSet.next().getHostAddress(), "Interface address [0] to bind to != 0.0.0.0");
	}

	@Test
	public void loadPreferencesWithDifferentTcpSipCompetences() throws TestServerException {
		ServerPreferences sp = new ServerPreferences(getClass().getResourceAsStream("config_tcp_sip.properties"));
		assertNotNull(sp, "ServerPreferences != null");

		assertEquals(3, sp.getTcpCompetenceMap().size(), "Amount of additional TCP competences != 3");
		assertTrue(sp.getTcpCompetenceMap().get(5060).hasSipCompetence(), "Port 5060 is missing SIP competence");
		assertTrue(sp.getTcpCompetenceMap().get(5061).hasSipCompetence(), "Port 5061 is missing SIP competence");
		assertTrue(sp.getTcpCompetenceMap().get(5062).hasSipCompetence(), "Port 5062 is missing SIP competence");
		assertNull(sp.getTcpCompetenceMap().get(5063), "Port 5063 has additional competences");
	}

	@Test
	public void testWriteErrorString() {
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		final PrintStream ps = new PrintStream(os);
		final PrintStream temp = System.out;
		System.setOut(ps);
		ServerPreferences.writeErrorString();
		System.setOut(temp);
		final String errString = os.toString();
		assertNotNull(errString);
	}
}

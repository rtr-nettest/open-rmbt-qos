/*******************************************************************************
 * Copyright RTR-GmbH
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import at.rtr.rmbt.qos.testserver.servers.AbstractUdpServer;
import at.rtr.rmbt.qos.testserver.udp.UdpTestCandidate;
import at.rtr.rmbt.qos.testserver.util.LineReader;

/**
 * Limits on untrusted client input: line length, unsolicited UDP packets, connections per IP.
 */
public class InputLimitsTest {

	private static final String UUID = "8723358c-2037-4029-a70c-91e5d9d35cf3";

	@Test
	public void lineReaderBehavesLikeReadLine() throws IOException {
		final BufferedReader reader = new BufferedReader(new StringReader("a\nb\r\nc\rd"));
		assertEquals("a", LineReader.readLine(reader));
		assertEquals("b", LineReader.readLine(reader));
		assertEquals("c", LineReader.readLine(reader));
		assertEquals("d", LineReader.readLine(reader));
		assertNull(LineReader.readLine(reader));
	}

	@Test
	public void lineReaderRejectsOverlongLines() {
		final BufferedReader reader = new BufferedReader(new StringReader("x".repeat(LineReader.MAX_LINE_LENGTH + 1) + "\n"));
		assertThrows(IOException.class, () -> LineReader.readLine(reader));
		assertEquals("abc", assertDoesNotThrow("x".repeat(LineReader.MAX_LINE_LENGTH - 3) + "abc\n").substring(LineReader.MAX_LINE_LENGTH - 3));
	}

	private static String assertDoesNotThrow(final String input) {
		try {
			return LineReader.readLine(new BufferedReader(new StringReader(input)));
		}
		catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	public void sanitizeRemovesControlCharacters() {
		assertEquals("a?b?c", LineReader.sanitize("a\nb\u001bc"));
	}

	/** minimal UDP server to test the shared packet dispatching */
	private static final class TestUdpServer extends AbstractUdpServer<java.io.Closeable> {
		TestUdpServer() {
			super(Object.class);
		}
		void receive(final byte[] data) throws Exception {
			dispatchPacket(new DatagramPacket(data, data.length, InetAddress.getLoopbackAddress(), 12345));
		}
		@Override public java.io.Closeable getSocket() { return null; }
		@Override public void send(DatagramPacket dp) { }
		@Override public boolean getIsRunning() { return true; }
		@Override public InetAddress getAddress() { return InetAddress.getLoopbackAddress(); }
		@Override public int getLocalPort() { return 0; }
		@Override public void run() { }
		@Override public boolean isHealthy() { return true; }
		@Override public String getStatusMessage() { return ""; }
	}

	private static byte[] udpTestPacket(final String uuid) {
		final byte[] data = new byte[46];
		data[0] = 3;
		data[1] = 0;
		System.arraycopy(uuid.getBytes(StandardCharsets.ISO_8859_1), 0, data, 2, 36);
		return data;
	}

	@Test
	public void unsolicitedUdpPacketsCreateNoState() throws Exception {
		final TestUdpServer server = new TestUdpServer();
		server.receive(udpTestPacket(UUID));
		server.receive(new byte[] {3, 0, 1, 2, 3});
		server.receive(new byte[] {(byte) 0x80, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 42});
		assertTrue(server.getIncomingMap().isEmpty(), "packets without a registered test must not create candidates");
	}

	@Test
	public void udpPacketsForRegisteredCandidateAreDispatched() throws Exception {
		final TestUdpServer server = new TestUdpServer();
		final CountDownLatch received = new CountDownLatch(1);
		final UdpTestCandidate candidate = new UdpTestCandidate();
		candidate.setOnUdpPacketReceivedCallback((dp, uuid, udpServer) -> {
			assertEquals(UUID, uuid);
			received.countDown();
			return true;
		});
		server.getIncomingMap().put(UUID, candidate);
		server.receive(udpTestPacket(UUID));
		assertTrue(received.await(5, TimeUnit.SECONDS));
	}

	@Test
	public void connectionsPerIpAreLimited() throws Exception {
		final ServerPreferences previous = TestServer.getInstance().serverPreferences;
		try {
			final ServerPreferences sp = new ServerPreferences((String[]) null);
			sp.setMaxConnectionsPerIp(2);
			TestServer.getInstance().serverPreferences = sp;
			final InetAddress a = InetAddress.getByName("192.0.2.1");
			final InetAddress b = InetAddress.getByName("192.0.2.2");
			assertTrue(TestServer.getInstance().acquireConnection(a));
			assertTrue(TestServer.getInstance().acquireConnection(a));
			assertFalse(TestServer.getInstance().acquireConnection(a));
			assertTrue(TestServer.getInstance().acquireConnection(b));
			TestServer.getInstance().releaseConnection(a);
			assertTrue(TestServer.getInstance().acquireConnection(a));
			TestServer.getInstance().releaseConnection(a);
			TestServer.getInstance().releaseConnection(a);
			TestServer.getInstance().releaseConnection(b);
		}
		finally {
			TestServer.getInstance().serverPreferences = previous;
		}
	}
}

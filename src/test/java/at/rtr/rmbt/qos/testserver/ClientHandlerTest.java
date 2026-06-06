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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import at.rtr.rmbt.qos.testserver.entity.ClientToken;
import at.rtr.rmbt.qos.testserver.util.TestServerConsole;

/**
 * Unit tests for {@link ClientHandler}.
 *
 * <p>Migrated from JUnit 4 + JMockit to JUnit 5 + Mockito. Instead of globally mocking JDK socket
 * and stream classes (JMockit {@code @Mocked}/{@code MockUp}, which Mockito's inline maker cannot
 * apply to bootstrap classes such as {@link Socket}), a lightweight real {@link Socket} subclass
 * supplies controllable in/out streams. {@code ClientHandler} wraps the socket output in a
 * {@code FilterOutputStream}, which writes straight through, so bytes sent to the client land in the
 * capture buffer. Only the application's own static {@link TestServerConsole} logging is mocked.</p>
 *
 * @author Lukasz Budryk (RTR-GmbH)
 */
public class ClientHandlerTest {

	public final String TOKEN = "bbd1ee96-0779-4619-b993-bb4bf7089754";

	public final String TOKEN_COMMAND = "TOKEN " + TOKEN + "_1528136454_3gr2gw9lVhtVONV0XO62Vamu/uw=";

	/** A real Socket whose streams are in-memory and whose address is the loopback address. */
	private static final class CapturingSocket extends Socket {
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		private final byte[] input;

		CapturingSocket(final String input) {
			this.input = input == null ? new byte[0] : input.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public InputStream getInputStream() {
			return new ByteArrayInputStream(input);
		}

		@Override
		public OutputStream getOutputStream() {
			return output;
		}

		@Override
		public InetAddress getInetAddress() {
			return InetAddress.getLoopbackAddress();
		}
	}

	private static String drain(final ByteArrayOutputStream sink) {
		final String content = sink.toString();
		sink.reset();
		return content;
	}

	@Test
	public void testSendMessage() throws IOException {
		final CapturingSocket socket = new CapturingSocket(null);
		try (ServerSocket serverSocket = new ServerSocket();
		     MockedStatic<TestServerConsole> console = mockStatic(TestServerConsole.class)) {

			final ClientHandler ch = new ClientHandler(serverSocket, socket);

			ch.sendCommand("TEST");
			assertEquals("TEST\n", drain(socket.output), "command received by client != 'TEST\\n'");

			ch.sendCommand("TEST", null);
			assertEquals("TEST\n", drain(socket.output), "command received by client != 'TEST\\n'");

			ch.sendCommand("TEST", "TEST REQUEST");
			assertEquals("TEST\n", drain(socket.output), "command received by client != 'TEST\\n'");
		}
	}

	@Test
	public void testSendMessageContainingIDAppendix() throws IOException {
		final CapturingSocket socket = new CapturingSocket(null);
		try (ServerSocket serverSocket = new ServerSocket();
		     MockedStatic<TestServerConsole> console = mockStatic(TestServerConsole.class)) {

			final ClientHandler ch = new ClientHandler(serverSocket, socket);

			ch.sendCommand("TEST", "TEST +ID0");
			assertEquals("TEST +ID0\n", drain(socket.output), "command received by client != 'TEST +ID0\\n'");
		}
	}

	@Test
	public void testCheckTokenWithBadToken() throws IOException {
		final CapturingSocket socket = new CapturingSocket(null);
		try (ServerSocket serverSocket = new ServerSocket();
		     MockedStatic<TestServerConsole> console = mockStatic(TestServerConsole.class)) {

			final ClientHandler ch = new ClientHandler(serverSocket, socket);
			assertThrows(IOException.class, () -> ch.checkToken("TOKEN 1234"));
		}
	}

	@Test
	public void testCheckTokenParsesWhenVerificationDisabled() throws Exception {
		// This test covers token PARSING, not authentication, so it explicitly disables the
		// (secure-by-default) HMAC verification introduced for security review C1.
		final CapturingSocket socket = new CapturingSocket(null);
		final String previous = System.getProperty("qos.checkToken");
		System.setProperty("qos.checkToken", "false");
		try (ServerSocket serverSocket = new ServerSocket();
		     MockedStatic<TestServerConsole> console = mockStatic(TestServerConsole.class)) {

			final ClientHandler ch = new ClientHandler(serverSocket, socket);
			final ClientToken token =
					ch.checkToken("TOKEN bbd1ee96-0779-4619-b993-bb4bf7089754_1528136454_3gr2gw9lVhtVONV0XO62Vamu/uw=\n");

			assertNotNull(token, "Token is null");
			assertEquals(1528136454, token.getTimeStamp(), "token timestamp != 1528136454");
			assertEquals("bbd1ee96-0779-4619-b993-bb4bf7089754", token.getUuid(),
					"token UUID != 'bbd1ee96-0779-4619-b993-bb4bf7089754'");
			assertEquals("3gr2gw9lVhtVONV0XO62Vamu/uw=", token.getHmac(),
					"token Hmac != '3gr2gw9lVhtVONV0XO62Vamu/uw='");
		}
		finally {
			restoreProperty("qos.checkToken", previous);
		}
	}

	@Test
	public void testCheckTokenRejectsUnverifiedTokenWhenVerificationEnabled() throws Exception {
		// Secure default (C1): an unsigned/stale token must be rejected when verification is on.
		final CapturingSocket socket = new CapturingSocket(null);
		final String previous = System.getProperty("qos.checkToken");
		System.setProperty("qos.checkToken", "true");
		try (ServerSocket serverSocket = new ServerSocket();
		     MockedStatic<TestServerConsole> console = mockStatic(TestServerConsole.class)) {

			final ClientHandler ch = new ClientHandler(serverSocket, socket);
			assertThrows(IOException.class, () ->
					ch.checkToken("TOKEN bbd1ee96-0779-4619-b993-bb4bf7089754_1528136454_3gr2gw9lVhtVONV0XO62Vamu/uw=\n"));
		}
		finally {
			restoreProperty("qos.checkToken", previous);
		}
	}

	private static void restoreProperty(final String key, final String value) {
		if (value == null) {
			System.clearProperty(key);
		} else {
			System.setProperty(key, value);
		}
	}
}

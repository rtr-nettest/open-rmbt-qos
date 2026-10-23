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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;

import org.junit.jupiter.api.Test;

public class TlsConfigTest {

	@Test
	public void onlyAeadSuitesAndModernProtocols() throws Exception {
		final SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, null, null);
		try (SSLServerSocket socket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket()) {
			TestServerImpl.configureTls(socket, false);
			assertArrayEquals(new String[] {"TLSv1.3", "TLSv1.2"}, socket.getEnabledProtocols());
			assertTrue(socket.getEnabledCipherSuites().length > 0);
			for (String suite : socket.getEnabledCipherSuites()) {
				// CBC suites are padding oracles (POODLE-TLS, GOLDENDOODLE), RSA key exchange has no forward secrecy
				assertFalse(suite.contains("CBC"), suite);
				assertTrue(suite.startsWith("TLS_AES_") || suite.startsWith("TLS_CHACHA20_") || suite.startsWith("TLS_ECDHE_"), suite);
			}
			assertTrue(socket.getSSLParameters().getUseCipherSuitesOrder());
		}
	}

	@Test
	public void tls13Only() throws Exception {
		final SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, null, null);
		try (SSLServerSocket socket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket()) {
			TestServerImpl.configureTls(socket, true);
			assertArrayEquals(new String[] {"TLSv1.3"}, socket.getEnabledProtocols());
		}
	}
}

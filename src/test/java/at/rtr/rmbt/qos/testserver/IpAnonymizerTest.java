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

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

import at.rtr.rmbt.qos.testserver.util.IpAnonymizer;

public class IpAnonymizerTest {

	private static String a(String s) {
		return IpAnonymizer.anonymize(s);
	}

	@Test
	public void ipv4() throws Exception {
		assertEquals("received UDP from: /77.116.45.x:38201", a("received UDP from: /77.116.45.207:38201"));
		assertEquals("[ClientHandler /77.116.45.x] rejected", a("[ClientHandler /77.116.45.207] rejected"));
		assertEquals("a 1.2.3.x and 10.0.0.x.", a("a 1.2.3.4 and 10.0.0.255."));
		assertEquals("/77.116.45.x", a(InetAddress.getByName("77.116.45.207").toString()));
		// not addresses
		assertEquals("version 1.2.3, 300.1.1.1, 1.2.3.4.5", a("version 1.2.3, 300.1.1.1, 1.2.3.4.5"));
	}

	@Test
	public void ipv6() throws Exception {
		assertEquals("from /2001:4bb8:1234:x", a("from /2001:4bb8:1234:5678:9abc:def0:1:2"));
		assertEquals("from 2001:db8:0:x end", a("from 2001:db8::1 end"));
		assertEquals("[2001:4bb8:1234:x]:38201", a("[2001:4bb8:1234:5678::1]:38201"));
		assertEquals("/2a01:190:1700:x", a(InetAddress.getByName("2a01:190:1700:38::100").toString()));
		assertEquals(new InetSocketAddress("2001:db8:1:2::1", 5060).toString().replace("2001:db8:1:2:0:0:0:1", "2001:db8:1:x"),
				a(new InetSocketAddress("2001:db8:1:2::1", 5060).toString()));
		assertEquals("::ffff:77.116.45.x", a("::ffff:77.116.45.207"));
		assertEquals("received UDP from: /2a01:190:1700:x:37852 (on local port :5060)", 
				a("received UDP from: /2a01:190:1700:38:0:0:0:100:37852 (on local port :5060)"));
		assertEquals("/2001:db8:0:x", a("/2001:db8:0:0:0:0:0:1"));
		assertEquals("/2a01:190:1700:x: UDP Test received packet", a("/2a01:190:1700:38:0:0:0:100: UDP Test received packet"));
		assertEquals("2001:db8:0:x and 2001:db8:0:x", a("2001:db8:: and 2001:db8::1"));
		// not addresses
		assertEquals("at 08:24:50,799 :: x::y", a("at 08:24:50,799 :: x::y"));
		assertEquals("2026-10-23 08:24:50,799 [pool-3-thread-3] INFO", a("2026-10-23 08:24:50,799 [pool-3-thread-3] INFO"));
	}
}

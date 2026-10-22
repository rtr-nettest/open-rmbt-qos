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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import at.rtr.rmbt.qos.testserver.TokenVerifier.Status;
import at.rtr.rmbt.qos.testserver.entity.SecretKey;
import at.rtr.rmbt.shared.Helperfunctions;

/**
 * Mirrors the token tests of the RMBT measurement server (open-rmbt-server/src/protocol/token.rs),
 * including its golden vectors, so both implementations accept the same tokens.
 */
public class TokenVerifierTest {

	private static final String SEED = "topsecret";
	private static final String UUID = "8723358c-2037-4029-a70c-91e5d9d35cf3";
	private static final List<SecretKey> KEYS = List.of(new SecretKey("test", SEED));

	/** golden vectors from the Python reference (makeToken.py) / RmbtUdpTokenFactory: seed=topsecret, time=1700000000, ip=62.1.2.3 */
	private static final String V2_GOLDEN = "ZVPxAEzErM6+VBk3HmTzPw==";
	private static final String V1_GOLDEN = UUID + "_1700000000_Q+55pZ/JOQgjMe49FlelxsfCxqA=";

	private static final long NOW_MS = System.currentTimeMillis();
	private static final long NOW_SEC = NOW_MS / 1000;

	private static InetAddress ip(final String ip) throws Exception {
		return InetAddress.getByName(ip);
	}

	/** bare v2 token, built like makeToken.py / RmbtUdpTokenFactory */
	private static String v2Bare(final String seed, final InetAddress ip, final long time) {
		final byte[] timeBytes = ByteBuffer.allocate(4).putInt((int) time).array();
		final byte[] key = seed.getBytes(StandardCharsets.UTF_8);
		final ByteBuffer token = ByteBuffer.allocate(16);
		token.put(timeBytes);
		token.put(Arrays.copyOf(TokenVerifier.hmacSha256(key, timeBytes), 8));
		token.put(Arrays.copyOf(TokenVerifier.hmacSha256(key, timeBytes, TokenVerifier.toMappedIpv6(ip)), 4));
		return Base64.getEncoder().encodeToString(token.array());
	}

	/** combined token as issued by the control server: &lt;v1&gt;_#v2#&lt;v2&gt; */
	private static String v2(final String seed, final InetAddress ip, final long time) {
		return v1(seed, time) + "_#v2#" + v2Bare(seed, ip, time);
	}

	private static String v1(final String seed, final long time) {
		return UUID + "_" + time + "_" + Helperfunctions.calculateHMAC(seed, UUID + "_" + time);
	}

	private static Status verify(final String token, final InetAddress sourceIp, final boolean v2Only) {
		return TokenVerifier.verify(token, KEYS, sourceIp, v2Only, NOW_MS).status();
	}

	@Test
	public void v2GoldenVectorIsByteCompatibleWithReference() throws Exception {
		assertEquals(V2_GOLDEN, v2Bare(SEED, ip("62.1.2.3"), 1_700_000_000L));
	}

	@Test
	public void goldenTokensAreCryptographicallyValidButExpired() throws Exception {
		// signature (v2: and IP) are checked before the time window, so TOO_LATE proves they were accepted
		assertEquals(Status.TOO_LATE, verify("garbage_#v2#" + V2_GOLDEN, ip("62.1.2.3"), false));
		assertEquals(Status.TOO_LATE, verify(V1_GOLDEN, null, false));
	}

	@Test
	public void v2CorrectIpAccepted() throws Exception {
		assertEquals(Status.ACCEPTED, verify(v2(SEED, ip("62.1.2.3"), NOW_SEC), ip("62.1.2.3"), false));
		assertEquals(Status.ACCEPTED, verify(v2(SEED, ip("2001:db8::1"), NOW_SEC), ip("2001:db8::1"), false));
		assertEquals(Status.ACCEPTED, verify(v2(SEED, ip("62.1.2.3"), NOW_SEC), ip("::ffff:62.1.2.3"), false));
	}

	@Test
	public void v2WrongIpRejected() throws Exception {
		assertEquals(Status.INVALID_IP, verify(v2(SEED, ip("62.1.2.3"), NOW_SEC), ip("9.9.9.9"), false));
		assertEquals(Status.INVALID_IP, verify(v2(SEED, ip("2001:db8::1"), NOW_SEC), ip("2001:db8::2"), false));
		assertEquals(Status.INVALID_IP, verify(v2(SEED, ip("62.1.2.3"), NOW_SEC), ip("2001:db8::1"), false));
		assertEquals(Status.INVALID_IP, verify(v2(SEED, ip("62.1.2.3"), NOW_SEC), null, false));
	}

	@Test
	public void v2WrongKeyRejected() throws Exception {
		assertEquals(Status.INVALID_HMAC, verify(v2("othersecret", ip("62.1.2.3"), NOW_SEC), ip("62.1.2.3"), false));
	}

	@Test
	public void v2IgnoresV1Part() throws Exception {
		// only the v2 part is validated if the marker is present, even if the v1 part is invalid
		final String token = UUID + "_" + NOW_SEC + "_invalid=_#v2#" + v2Bare(SEED, ip("62.1.2.3"), NOW_SEC);
		assertEquals(Status.ACCEPTED, verify(token, ip("62.1.2.3"), false));
		// ... and a valid v1 part does not help an invalid v2 part
		final String token2 = v1(SEED, NOW_SEC) + "_#v2#" + v2Bare("othersecret", ip("62.1.2.3"), NOW_SEC);
		assertEquals(Status.INVALID_HMAC, verify(token2, ip("62.1.2.3"), false));
	}

	@Test
	public void timeWindow() throws Exception {
		final InetAddress ip = ip("62.1.2.3");
		assertEquals(Status.ACCEPTED, verify(v2(SEED, ip, NOW_SEC - 300), ip, false));
		assertEquals(Status.ACCEPTED, verify(v2(SEED, ip, NOW_SEC + 30), ip, false));
		assertEquals(Status.TOO_LATE, verify(v2(SEED, ip, NOW_SEC - 1000), ip, false));
		assertEquals(Status.TOO_EARLY, verify(v2(SEED, ip, NOW_SEC + 1000), ip, false));
		assertEquals(Status.ACCEPTED, verify(v1(SEED, NOW_SEC - 300), null, false));
		assertEquals(Status.TOO_LATE, verify(v1(SEED, NOW_SEC - 1000), null, false));
		assertEquals(Status.TOO_EARLY, verify(v1(SEED, NOW_SEC + 1000), null, false));
	}

	@Test
	public void v1AndV2Only() throws Exception {
		assertEquals(Status.ACCEPTED, verify(v1(SEED, NOW_SEC), null, false));
		assertEquals(Status.V2_REQUIRED, verify(v1(SEED, NOW_SEC), null, true));
		assertEquals(Status.ACCEPTED, verify(v2(SEED, ip("62.1.2.3"), NOW_SEC), ip("62.1.2.3"), true));
		assertEquals(Status.INVALID_HMAC, verify(v1("othersecret", NOW_SEC), null, false));
	}

	@Test
	public void anyKeyMatches() throws Exception {
		final List<SecretKey> keys = List.of(new SecretKey("a", "first"), new SecretKey("b", SEED), new SecretKey("c", "third"));
		final TokenVerifier.Result v2 = TokenVerifier.verify(v2(SEED, ip("62.1.2.3"), NOW_SEC), keys, ip("62.1.2.3"), false, NOW_MS);
		assertEquals(Status.ACCEPTED, v2.status());
		assertEquals("b", v2.keyLabel());
		final TokenVerifier.Result v1 = TokenVerifier.verify(v1(SEED, NOW_SEC), keys, null, false, NOW_MS);
		assertEquals(Status.ACCEPTED, v1.status());
		assertEquals("b", v1.keyLabel());
	}

	@Test
	public void malformedTokensRejected() throws Exception {
		assertEquals(Status.PARSE_ERROR, verify(UUID + "_" + NOW_SEC + "_x_#v2#not-base64!", ip("62.1.2.3"), false));
		assertEquals(Status.PARSE_ERROR, verify(UUID + "_" + NOW_SEC + "_x_#v2#AAAA", ip("62.1.2.3"), false));
		assertEquals(Status.PARSE_ERROR, verify("1234", null, false));
		assertEquals(Status.PARSE_ERROR, verify("", null, false));
	}

	@Test
	public void timeReconstructionHandlesWrap() {
		final long now = (1L << 32) + 10;
		assertEquals((1L << 32) - 5, TokenVerifier.reconstructTime((1L << 32) - 5, now));
		assertEquals((1L << 32) + 3, TokenVerifier.reconstructTime(3, now));
		assertEquals(1_700_000_000L, TokenVerifier.reconstructTime(1_700_000_000L, 1_700_000_100L));
	}

	@Test
	public void ipv4IsMappedToIpv6() throws Exception {
		assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, 62, 1, 2, 3}, TokenVerifier.toMappedIpv6(ip("62.1.2.3")));
	}
}

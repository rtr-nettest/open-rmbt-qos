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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import at.rtr.rmbt.qos.testserver.entity.SecretKey;
import at.rtr.rmbt.shared.Helperfunctions;

/**
 * Verifies client tokens issued by the control server (same scheme as the RMBT measurement server,
 * see open-rmbt-server/src/protocol/token.rs and token-migration.md).
 *
 * <p>The control server issues a combined token <code>&lt;v1&gt;_#v2#&lt;base64 v2&gt;</code>:</p>
 * <ul>
 * <li><b>v1</b> (legacy): <code>UUID_TIMESTAMP_BASE64(HMAC-SHA1(key, "UUID_TIMESTAMP"))</code>, TIMESTAMP in epoch seconds.
 * Not bound to the client address. A token without the <code>#v2#</code> marker is validated as v1.</li>
 * <li><b>v2</b>: base64 of 16 bytes <code>time(4, big endian) | HMAC-SHA256(key, time)[0..8] | HMAC-SHA256(key, time | ip16)[0..4]</code>,
 * where time is the low 32 bits of the epoch start time and ip16 the client address as IPv4-mapped IPv6.
 * If the marker is present only the v2 part is validated; the client address is checked as well.</li>
 * </ul>
 * <p>Several keys may be configured (one per measurement server / key rotation); a token is valid if it was
 * signed with any of them.</p>
 */
public class TokenVerifier {

	public final static String V2_MARKER = "#v2#";

	/**
	 * max time a client may connect after the token's start time; the QoS test runs after the speed test,
	 * so this has to be considerably longer than for the measurement server
	 */
	public final static long MAX_ACCEPT_LATE_MS = QoSServiceProtocol.TOKEN_LEGAL_TIME;

	/**
	 * max time a client may connect before the token's start time (clock skew)
	 */
	public final static long MAX_ACCEPT_EARLY_MS = 60000;

	private final static Pattern V1_PATTERN = Pattern.compile("^([\\d\\w-]+)_(\\d{1,13})_([^_\\s]+)");

	private final static int V2_TOKEN_LENGTH = 16;

	public enum Status {
		ACCEPTED,
		/** the signature does not match any key (v1, or v2 time HMAC) */
		INVALID_HMAC,
		/** v2: the time HMAC matches a key, but the token was issued for another client IP (or the IP is unknown) */
		INVALID_IP,
		/** signature ok, but the token is not valid yet */
		TOO_EARLY,
		/** signature ok, but the token has expired */
		TOO_LATE,
		/** malformed token */
		PARSE_ERROR,
		/** a v1 token was presented but only v2 tokens are accepted */
		V2_REQUIRED
	}

	/**
	 * @param status
	 * @param version "v1" or "v2"
	 * @param keyLabel label of the matching key (null if none matched)
	 * @param reason human readable details for the log (never contains key material)
	 */
	public record Result(Status status, String version, String keyLabel, String reason) {
		public boolean isAccepted() {
			return status == Status.ACCEPTED;
		}
	}

	/**
	 * @param token the token (without the leading "TOKEN ")
	 * @param keys the keys the token may be signed with
	 * @param sourceIp the address the control connection comes from (required for v2)
	 * @param v2Only reject v1 tokens
	 * @param nowMs current time in ms
	 * @return
	 */
	public static Result verify(final String token, final List<SecretKey> keys, final InetAddress sourceIp, final boolean v2Only, final long nowMs) {
		final String trimmedToken = token == null ? "" : token.trim();
		final int markerPos = trimmedToken.indexOf(V2_MARKER);
		if (markerPos >= 0) {
			return verifyV2(trimmedToken.substring(markerPos + V2_MARKER.length()), keys, sourceIp, nowMs);
		}
		if (v2Only) {
			return new Result(Status.V2_REQUIRED, "v1", null, "no v2 part (" + V2_MARKER + ") and only v2 tokens are accepted");
		}
		return verifyV1(trimmedToken, keys, nowMs);
	}

	private static Result verifyV1(final String token, final List<SecretKey> keys, final long nowMs) {
		final Matcher m = V1_PATTERN.matcher(token);
		if (!m.find()) {
			return new Result(Status.PARSE_ERROR, "v1", null, "malformed v1 token");
		}
		final String data = m.group(1) + "_" + m.group(2);
		final byte[] hmac = m.group(3).getBytes(StandardCharsets.US_ASCII);

		String keyLabel = null;
		for (SecretKey key : keys) {
			// constant time comparison; check all keys to not leak which one matched
			if (MessageDigest.isEqual(Helperfunctions.calculateHMAC(key.key(), data).getBytes(StandardCharsets.US_ASCII), hmac) && keyLabel == null) {
				keyLabel = key.label();
			}
		}
		if (keyLabel == null) {
			return new Result(Status.INVALID_HMAC, "v1", null, "HMAC does not match any key");
		}

		long startTimeMs = Long.parseLong(m.group(2));
		// the control server issues the start time in epoch seconds; accept milliseconds as well
		if (startTimeMs < 100_000_000_000L) {
			startTimeMs *= 1000;
		}
		return checkTimeWindow("v1", keyLabel, startTimeMs, nowMs);
	}

	private static Result verifyV2(final String v2Part, final List<SecretKey> keys, final InetAddress sourceIp, final long nowMs) {
		final byte[] token;
		try {
			token = Base64.getDecoder().decode(v2Part.trim());
		}
		catch (IllegalArgumentException e) {
			return new Result(Status.PARSE_ERROR, "v2", null, "v2 part is not valid base64");
		}
		if (token.length != V2_TOKEN_LENGTH) {
			return new Result(Status.PARSE_ERROR, "v2", null, "v2 part has " + token.length + " bytes (expected " + V2_TOKEN_LENGTH + ")");
		}

		final byte[] time = Arrays.copyOfRange(token, 0, 4);
		final byte[] timeHash = Arrays.copyOfRange(token, 4, 12);
		final byte[] ipHash = Arrays.copyOfRange(token, 12, 16);

		if (sourceIp == null) {
			return new Result(Status.INVALID_IP, "v2", null, "client IP unknown");
		}
		final byte[] ip16 = toMappedIpv6(sourceIp);

		// the (keyed) time HMAC identifies the key, the IP HMAC binds the client address
		for (SecretKey key : keys) {
			final byte[] keyBytes = key.key().getBytes(StandardCharsets.UTF_8);
			if (!MessageDigest.isEqual(Arrays.copyOf(hmacSha256(keyBytes, time), 8), timeHash)) {
				continue;
			}
			if (!MessageDigest.isEqual(Arrays.copyOf(hmacSha256(keyBytes, time, ip16), 4), ipHash)) {
				return new Result(Status.INVALID_IP, "v2", key.label(), "token was not issued for client IP " + sourceIp.getHostAddress());
			}
			final long startTimeMs = reconstructTime(((time[0] & 0xffL) << 24) | ((time[1] & 0xffL) << 16) | ((time[2] & 0xffL) << 8) | (time[3] & 0xffL), nowMs / 1000) * 1000;
			return checkTimeWindow("v2", key.label(), startTimeMs, nowMs);
		}
		return new Result(Status.INVALID_HMAC, "v2", null, "HMAC does not match any key");
	}

	private static Result checkTimeWindow(final String version, final String keyLabel, final long startTimeMs, final long nowMs) {
		if (startTimeMs - nowMs > MAX_ACCEPT_EARLY_MS) {
			return new Result(Status.TOO_EARLY, version, keyLabel, "token not valid yet: client is " + (startTimeMs - nowMs) / 1000 + "s early (max " + MAX_ACCEPT_EARLY_MS / 1000 + "s)");
		}
		if (nowMs - startTimeMs > MAX_ACCEPT_LATE_MS) {
			return new Result(Status.TOO_LATE, version, keyLabel, "token expired: client is " + (nowMs - startTimeMs) / 1000 + "s late (max " + MAX_ACCEPT_LATE_MS / 1000 + "s)");
		}
		return new Result(Status.ACCEPTED, version, keyLabel, "accepted by key '" + keyLabel + "'");
	}

	/**
	 * maps the 32 bit token time to the absolute epoch time (in s) nearest to now (handles the 2^32 s wrap)
	 */
	static long reconstructTime(final long timeU32, final long nowSec) {
		final long period = 1L << 32;
		long t = (nowSec & ~0xFFFFFFFFL) | timeU32;
		if (t - nowSec > period / 2) {
			t -= period;
		}
		else if (nowSec - t > period / 2) {
			t += period;
		}
		return t;
	}

	/**
	 * @return the address as 16 bytes, IPv4 as IPv4-mapped IPv6 (::ffff:a.b.c.d)
	 */
	static byte[] toMappedIpv6(final InetAddress address) {
		final byte[] addr = address.getAddress();
		if (address instanceof Inet4Address) {
			final byte[] mapped = new byte[16];
			mapped[10] = (byte) 0xff;
			mapped[11] = (byte) 0xff;
			System.arraycopy(addr, 0, mapped, 12, 4);
			return mapped;
		}
		return addr;
	}

	static byte[] hmacSha256(final byte[] key, final byte[]... parts) {
		try {
			final Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			for (byte[] part : parts) {
				mac.update(part);
			}
			return mac.doFinal();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}

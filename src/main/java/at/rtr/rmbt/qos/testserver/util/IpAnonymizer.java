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
package at.rtr.rmbt.qos.testserver.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.net.InetAddresses;

/**
 * Anonymizes IP addresses in log messages: IPv4 addresses keep the first three octets
 * (77.116.45.207 -> 77.116.45.x), IPv6 addresses keep the first three groups, i.e. the /48 prefix
 * (2001:db8:1234:5678::1 -> 2001:db8:1234:x). Ports and surrounding text are kept.
 */
public final class IpAnonymizer {

	private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?![\\d]|\\.\\d)");
	
	/**
	 * candidates for IPv6 addresses (hex digits, colons and an optional embedded IPv4 address), up to
	 * 9 parts for a full address followed by ":port" without brackets (as in "/2001:db8:0:0:0:0:0:1:5060");
	 * validated with {@link InetAddresses#forString(String)}, which never does a DNS lookup
	 */
	private static final Pattern IPV6 = Pattern.compile("(?<![0-9a-fA-F:.])[0-9a-fA-F]{0,4}(?::[0-9a-fA-F]{0,5}){2,8}(?::\\d{1,3}(?:\\.\\d{1,3}){3})?(?![0-9a-fA-F:]|\\.\\d)");
	
	private IpAnonymizer() {
	}

	/**
	 * @param message
	 * @return the message with all IPv4 and IPv6 addresses anonymized
	 */
	public static String anonymize(final String message) {
		if (message == null || (message.indexOf('.') < 0 && message.indexOf(':') < 0)) {
			return message;
		}
		return anonymizeIpv4(anonymizeIpv6(message));
	}
	
	/**
	 * @param address
	 * @return the anonymized form of the address (IPv4-mapped IPv6 addresses are treated as IPv4)
	 */
	public static String anonymize(final InetAddress address) {
		final byte[] b = address.getAddress();
		if (address instanceof Inet4Address) {
			return (b[0] & 0xff) + "." + (b[1] & 0xff) + "." + (b[2] & 0xff) + ".x";
		}
		return String.format("%x:%x:%x:x", ((b[0] & 0xff) << 8) | (b[1] & 0xff), ((b[2] & 0xff) << 8) | (b[3] & 0xff), 
				((b[4] & 0xff) << 8) | (b[5] & 0xff));
	}
	
	private static String anonymizeIpv4(final String message) {
		final Matcher m = IPV4.matcher(message);
		final StringBuilder sb = new StringBuilder();
		while (m.find()) {
			boolean valid = true;
			for (int i = 1; i <= 4; i++) {
				valid &= Integer.parseInt(m.group(i)) <= 255;
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(valid ? m.group(1) + "." + m.group(2) + "." + m.group(3) + ".x" : m.group()));
		}
		m.appendTail(sb);
		return sb.toString();
	}
	
	private static String anonymizeIpv6(final String message) {
		if (message.indexOf(':') < 0) {
			return message;
		}
		final Matcher m = IPV6.matcher(message);
		final StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String replacement = m.group();
			String candidate = replacement;
			String port = "";
			final int lastColon = candidate.lastIndexOf(':');
			if (!InetAddresses.isInetAddress(candidate) && candidate.chars().filter(c -> c == ':').count() == 8 
					&& candidate.substring(lastColon + 1).matches("\\d{0,5}")) {
				// full address followed by ":port" or by ":" as separator ("/2001:db8:0:0:0:0:0:1: text")
				port = candidate.substring(lastColon);
				candidate = candidate.substring(0, lastColon);
			}
			// "::" alone or times like 08:24:50 are no addresses (forString rejects them)
			if (candidate.length() > 2 && InetAddresses.isInetAddress(candidate)) {
				final InetAddress address = InetAddresses.forString(candidate);
				if (address instanceof Inet6Address) {
					replacement = anonymize(address) + port;
				}
				else {
					// IPv4-mapped (::ffff:1.2.3.4) is returned as Inet4Address
					replacement = "::ffff:" + anonymize(address) + port;
				}
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(sb);
		return sb.toString();
	}
}

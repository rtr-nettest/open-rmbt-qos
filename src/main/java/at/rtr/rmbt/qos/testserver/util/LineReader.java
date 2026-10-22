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

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Length-limited replacement for {@link BufferedReader#readLine()}, which buffers an unlimited amount
 * of data until it sees a line break and therefore lets a client exhaust the heap by never sending one.
 */
public final class LineReader {

	/**
	 * max length of a protocol line (control channel, TCP test ports, SIP header lines)
	 */
	public final static int MAX_LINE_LENGTH = 4096;

	private LineReader() {
	}

	/**
	 * Reads a line terminated by "\n", "\r" or "\r\n" (like {@link BufferedReader#readLine()}).
	 * @return the line without terminator, or null at the end of the stream
	 * @throws IOException if the line is longer than {@link #MAX_LINE_LENGTH}
	 */
	public static String readLine(final BufferedReader reader) throws IOException {
		return readLine(reader, MAX_LINE_LENGTH);
	}

	/**
	 * @see #readLine(BufferedReader)
	 */
	public static String readLine(final BufferedReader reader, final int maxLength) throws IOException {
		final StringBuilder sb = new StringBuilder();
		int c;
		while ((c = reader.read()) != -1) {
			if (c == '\n') {
				return sb.toString();
			}
			if (c == '\r') {
				reader.mark(1);
				if (reader.read() != '\n') {
					reader.reset();
				}
				return sb.toString();
			}
			if (sb.length() >= maxLength) {
				throw new IOException("line exceeds " + maxLength + " characters");
			}
			sb.append((char) c);
		}
		return sb.length() > 0 ? sb.toString() : null;
	}

	/**
	 * @return the text with control characters replaced, safe to be written to a single log line
	 */
	public static String sanitize(final String text) {
		if (text == null) {
			return null;
		}
		final StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			sb.append(Character.isISOControl(c) ? '?' : c);
		}
		return sb.toString();
	}
}

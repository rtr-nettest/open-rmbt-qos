/*******************************************************************************
 * Copyright 2016 Specure GmbH
 * Copyright RTR-GmbH
 * Copyright 2016 Rundfunk und Telekom Regulierungs-GmbH (RTR-GmbH)
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
 *******************************************************************************/
package at.rtr.rmbt.qos.testserver.util;

import java.util.EnumMap;
import java.util.Map;

import at.rtr.rmbt.qos.testserver.ServerPreferences.TestServerServiceEnum;
import at.rtr.rmbt.qos.testserver.TestServerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging service for the test server.
 *
 * <p>Backed by SLF4J / Logback (see {@code logback.xml}), mirroring the logging setup of the
 * RMBTControlServer (console plus optional Logstash via the {@code LOG_HOST} environment variable).
 * The previous programmatic log4j2 appender configuration (console/file/syslog driven by CLI flags)
 * has been replaced by the declarative {@code logback.xml}; appender wiring now lives there.</p>
 *
 * @author lb
 */
public class LoggingService {

	/**
	 * Per-service loggers. Logger names are kept stable ("QOS.*") so they can be targeted
	 * individually from logback.xml if desired.
	 */
	public final static Map<TestServerServiceEnum, Logger> LOGGER_MAP = new EnumMap<>(TestServerServiceEnum.class);

	static {
		LOGGER_MAP.put(TestServerServiceEnum.RUNTIME_GUARD_SERVICE, LoggerFactory.getLogger("QOS.DEBUG"));
		LOGGER_MAP.put(TestServerServiceEnum.TCP_SERVICE, LoggerFactory.getLogger("QOS.TCP"));
		LOGGER_MAP.put(TestServerServiceEnum.UDP_SERVICE, LoggerFactory.getLogger("QOS.UDP"));
		LOGGER_MAP.put(TestServerServiceEnum.TEST_SERVER, LoggerFactory.getLogger("QOS.SERVER"));
	}

	/**
	 * With SLF4J/Logback logging is always available (at minimum the console appender). Retained
	 * for backwards compatibility with existing call sites.
	 */
	public static boolean IS_LOGGING_AVAILABLE = true;

	private static Logger logger(final TestServerServiceEnum service) {
		final Logger l = service != null ? LOGGER_MAP.get(service) : null;
		return l != null ? l : LOGGER_MAP.get(TestServerServiceEnum.TEST_SERVER);
	}

	/**
	 * Kept for source compatibility. Logback is configured declaratively via {@code logback.xml},
	 * so there is nothing to wire up programmatically here.
	 */
	public static boolean isLoggingAvailable(final TestServerImpl testServerImpl) {
		return IS_LOGGING_AVAILABLE;
	}

	/**
	 * No-op: appenders are configured via {@code logback.xml}. Retained so existing callers
	 * ({@code TestServerImpl}) continue to compile and run unchanged.
	 */
	public static void init(final TestServerImpl testServerImpl) {
		// Logback is initialised from logback.xml on first logger use; nothing to do here.
	}

	/**
	 * fatal level logging (mapped to SLF4J error, which has no FATAL level)
	 */
	public static void fatal(Throwable t, String message, TestServerServiceEnum service) {
		final Logger l = logger(service);
		if (t != null) {
			l.error("[" + t.getClass().getCanonicalName() + ": " + t.getLocalizedMessage() + "] " + message, t);
		} else {
			l.error("[unknown Exception] " + message);
		}
	}

	/**
	 * error level logging
	 */
	public static void error(Throwable t, String message, TestServerServiceEnum service) {
		final Logger l = logger(service);
		if (t != null) {
			l.error("[" + t.getClass().getCanonicalName() + ": " + t.getLocalizedMessage() + "] " + message, t);
		} else {
			l.error("[unknown Exception] " + message);
		}
	}

	/**
	 * warn level logging
	 */
	public static void warn(String message, TestServerServiceEnum service) {
		logger(service).warn(message);
	}

	/**
	 * info level logging
	 */
	public static void info(String message, TestServerServiceEnum service) {
		logger(service).info(message);
	}

	/**
	 * debug level logging
	 */
	public static void debug(String message, TestServerServiceEnum service) {
		logger(service).debug(message);
	}
}

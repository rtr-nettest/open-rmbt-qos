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

import java.io.File;
import java.net.InetAddress;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import at.rtr.rmbt.qos.testserver.ServerPreferences.TestServerServiceEnum;
import at.rtr.rmbt.qos.testserver.TestServerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.Status;
import net.logstash.logback.appender.LogstashTcpSocketAppender;
import net.logstash.logback.encoder.LogstashEncoder;

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
	/**
	 * Applies the logging configuration (see {@link #configure()}). If the LOG_HOST environment variable
	 * (or system property) is set, JSON logs are additionally sent to Logstash at LOG_HOST:LOG_PORT (default port 4560).
	 * This is configured here rather than with a logback.xml conditional, which needs Janino / changed between logback versions.
	 */
	public static void init(final TestServerImpl testServerImpl) {
		configure();
		
		final String logHost = getSetting("LOG_HOST");
		if (logHost == null || logHost.isBlank()) {
			return;
		}
		final String logPort = getSetting("LOG_PORT");
		final String destination = logHost.trim() + ":" + (logPort == null || logPort.isBlank() ? "4560" : logPort.trim());
		
		final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		final LogstashEncoder encoder = new LogstashEncoder();
		encoder.setContext(loggerContext);
		// same fields as the statistics server: app_name and host (LOGGING_HOST, default: the host name)
		final String host = getLoggingHost();
		encoder.setCustomFields("{\"app_name\":\"qos-service\",\"host\":\"" + jsonEscape(host) + "\"}");
		encoder.start();
		
		final LogstashTcpSocketAppender appender = new LogstashTcpSocketAppender();
		appender.setName("logstash");
		appender.setContext(loggerContext);
		appender.addDestination(destination);
		appender.setEncoder(encoder);
		appender.start();
		
		loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
		logger(TestServerServiceEnum.TEST_SERVER).info("Sending logs to Logstash at " + destination + " (host=" + host + ")");
	}
	
	/**
	 * Name of a Logback configuration file in the working directory that is used if the system property
	 * <code>logback.configurationFile</code> is not set.
	 */
	public final static String LOCAL_CONFIG_FILE = "logback.xml";
	
	/**
	 * Applies the logging configuration and prints which one is used: the file given with
	 * -Dlogback.configurationFile (loaded by Logback itself), else ./logback.xml, else the built-in
	 * configuration (console, INFO). Logback silently falls back to the built-in configuration if the
	 * configured file is missing or unreadable, so this is reported here.
	 */
	private static void configure() {
		final String configured = System.getProperty("logback.configurationFile");
		if (configured != null) {
			final File file = new File(configured);
			if (configured.contains(":/") || file.canRead()) {
				System.out.println("Logging configuration: " + (configured.contains(":/") ? configured : file.getAbsolutePath()));
			}
			else {
				System.err.println("WARNING: logging configuration " + file.getAbsolutePath() 
						+ " not found or not readable, using the built-in configuration (console, INFO)");
			}
			return;
		}
		
		final File file = new File(LOCAL_CONFIG_FILE);
		if (!file.isFile()) {
			System.out.println("Logging configuration: built-in (console, INFO)");
			return;
		}
		
		final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		try {
			loggerContext.reset();
			final JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(loggerContext);
			configurator.doConfigure(file);
		}
		catch (JoranException e) {
			// reported below
		}
		final List<Status> errors = loggerContext.getStatusManager().getCopyOfStatusList().stream()
				.filter(status -> status.getLevel() == Status.ERROR).toList();
		if (errors.isEmpty()) {
			System.out.println("Logging configuration: " + file.getAbsolutePath());
		}
		else {
			errors.forEach(status -> System.err.println("ERROR in " + file.getAbsolutePath() + ": " + status.getMessage()));
		}
	}
	
	/**
	 * @return the value for the "host" field of the Logstash events: LOGGING_HOST or, if not set, the host name
	 */
	private static String getLoggingHost() {
		final String loggingHost = getSetting("LOGGING_HOST");
		if (loggingHost != null && !loggingHost.isBlank()) {
			return loggingHost.trim();
		}
		try {
			return InetAddress.getLocalHost().getHostName();
		}
		catch (Exception e) {
			return "";
		}
	}
	
	private static String jsonEscape(final String s) {
		final StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (c == '"' || c == '\\') {
				sb.append('\\').append(c);
			}
			else if (c >= 0x20) {
				sb.append(c);
			}
		}
		return sb.toString();
	}
	
	private static String getSetting(final String name) {
		final String value = System.getProperty(name);
		return value != null ? value : System.getenv(name);
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

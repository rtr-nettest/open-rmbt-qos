# RMBT QoS Server

The QoS (Quality of Service) test server for **RMBT / Open-RMBT** (the engine behind RTR-Netztest and
similar measurement systems). Main class: `at.rtr.rmbt.qos.testserver.TestServer`.

This repository was extracted from the original multi-module `open-rmbt` project and modernized
(Java 17, Maven, Logback logging, updated dependencies).

---

## What the server does

It is a standalone, long-running Java server that mobile/desktop probes (RMBT clients) connect to in
order to run **QoS measurements**. Concretely, on startup it (see `TestServerImpl.run`):

1. Binds a **TCP control port** (default `5233` via `config.properties`) on the configured
   interface(s), optionally wrapped in **TLS/SSL**. Each accepted connection is handled by a
   `ClientHandler` that speaks the line-based **QoS Testserver Protocol (QTP)** — see
   [`PROTOCOL.md`](PROTOCOL.md). After a handshake (greeting + identity token) the client requests
   individual QoS tests:
   - **TCP** connectivity tests (incoming / outgoing) on arbitrary ports,
   - **UDP** packet tests (incoming / outgoing) measuring packet loss and duplicates,
   - **VoIP / RTP** tests (jitter, loss, sequence) over non-blocking UDP ports,
   - **SIP** tests on TCP "competence" ports,
   - **Non-transparent proxy** (NTP) detection,
   - UDP port discovery and result queries.
2. Opens the configured **UDP test ports** (a port range, an explicit list, and non-blocking "NIO"
   ports required for VoIP) using `UdpMultiClientServer` / `NioUdpMultiClientServer`.
3. Starts background **watcher services** (TCP/UDP) and a **runtime guard** (see
   [guard.properties](#guardproperties)).
4. Optionally starts a **REST monitoring interface** (see [REST interface](#rest-monitoring-interface)).
5. Installs a Ctrl-C shutdown hook that closes sockets and records a clean shutdown.

> The full wire protocol (commands, responses, appendices) is documented in
> [`PROTOCOL.md`](PROTOCOL.md).

---

## Building

This is a standard Maven project producing a single runnable "fat" jar.

```bash
mvn package
# -> target/RMBTQoSServer.jar   (Main-Class: at.rtr.rmbt.qos.testserver.TestServer)
```

Run the unit tests only:

```bash
mvn test
```

### Supported JDKs

The project targets **Java 17** bytecode (`maven.compiler.release = 17`) and is verified to build and
test cleanly on **JDK 17, 21, 24 and 25** — i.e. it requires JDK 17 and builds on everything up to and
including JDK 25.

### Running

```bash
# Uses ./config.properties if present, otherwise the bundled defaults / CLI args
java -jar target/RMBTQoSServer.jar

# Or with command-line options (see below)
java -jar target/RMBTQoSServer.jar -p 5233 -u 10000 10050 -t 200
```

---

## Configuration (`config.properties`)

Settings are read by `ServerPreferences`. Precedence:

- **No command-line arguments** → `config.properties` is loaded (from the working directory, falling
  back to the classpath).
- **Any command-line arguments are given** → `config.properties` is **not** loaded automatically;
  only the arguments apply, unless you explicitly pass `-f config.properties`.

> ⚠️ The keys below are documented from the **actual code**, not from the historical comments in
> `config.properties`. Several keys are now **inert** (parsed but ignored) after the migration from
> log4j to Logback — they are marked accordingly.

### Networking & test parameters (active)

| Key | Meaning |
|---|---|
| `server.ip` | Interface IP(s) to bind to. Comma-separated list, IPv4/IPv6. Omit ⇒ `0.0.0.0` (all interfaces). |
| `server.port` | TCP control port clients connect to. (Code default if unset: `5234`; the shipped `config.properties` sets `5233`.) |
| `server.ssl` | `true`/`false`. Wrap the control port in TLS using the bundled keystore `/crt/qosserver.jks` (see [TLS](#tls)). |
| `server.threads` | Size of the fixed worker thread pool ≈ max simultaneous clients. Minimum 5 (startup fails below that); a warning is printed at ≤ 10. |
| `server.ip.check` | `true`/`false`. If true, only client IPs registered during test setup receive responses (a per-IP candidate map is enforced). |
| `server.udp.minport` / `server.udp.maxport` | Inclusive UDP port range opened for UDP tests. (Note: the `-h` help text wrongly calls the upper bound "exclusive".) |
| `server.udp.ports` | Additional explicit blocking UDP ports (comma list), e.g. `53,123,500,...`. |
| `server.udp.nio.ports` | Non-blocking (NIO) UDP ports (comma list). **Required for VoIP tests** (bidirectional streams). A port may not appear in both the blocking and NIO sets. |
| `server.tcp.competence.sip` | TCP ports (comma list) that additionally speak the **SIP** test protocol, e.g. `5060`. |
| `server.secret` | HMAC secret for client token verification. **Currently inert:** token-HMAC checking is compiled off (`ClientHandler.CHECK_TOKEN = false`), so the token is parsed but its signature is not verified. |
| `server.verbose` | `0`/`1`/`2`. **Largely inert now:** it no longer controls general log output (Logback does). It still affects the interactive console and a few explicit `verbose >= 1` code paths. |

### REST monitoring service (active)

| Key | Meaning |
|---|---|
| `server.service.rest` | `true`/`false` — enable the REST monitoring interface. |
| `server.service.rest.port` | TCP port for the REST interface (e.g. `10080`). Required if REST is enabled. |
| `server.service.rest.ip` | Bind IP for REST (default `127.0.0.1`). |
| `server.service.rest.ssl` | `true`/`false` — serve REST over HTTPS (uses the same keystore as above). |

See [REST monitoring interface](#rest-monitoring-interface).

### Console (active, but terminal-only)

| Key | Meaning |
|---|---|
| `server.console` | `true`/`false` — enable the **interactive admin console** on stdin. See [Interactive server console](#interactive-server-console). Requires a real terminal (`System.console()`), so it is effectively a no-op under systemd/Docker/redirected input. |

### Logging keys — **INERT** (superseded by Logback)

These were used by the old log4j-based `LoggingService` to build appenders programmatically. After the
migration to SLF4J + Logback, `LoggingService.init()` is a no-op and these keys have **no effect**.
Configure logging via `logback.xml` instead (see [Logging](#logging)).

| Inert key | Was |
|---|---|
| `server.logging` | enable/disable file logging |
| `server.log`, `server.log.udp`, `server.log.tcp` | per-service log file paths |
| `server.log.pattern` | log4j pattern for files |
| `server.log.console` | log4j console appender on/off |
| `server.syslog`, `server.syslog.host`, `server.syslog.pattern` | log4j syslog appender |

---

## Command-line options

Parsed by `ServerPreferences` (case-insensitive). When any option is present, `config.properties` is
not auto-loaded (use `-f` to load one).

| Option | Meaning |
|---|---|
| `-f <file>` | Load settings from this config file. |
| `-p <port>` | Control port. |
| `-u <min> <max>` | UDP test port range (inclusive). |
| `-t <threads>` | Worker thread pool size. |
| `-ip <ip>` | Bind interface IP (repeatable). |
| `-ic` | Enable the client IP check (`server.ip.check`). |
| `-s` | Enable TLS on the control port. |
| `-k <secret>` | HMAC secret key (subject to the inert `CHECK_TOKEN` note above). |
| `-v` / `-vv` | Verbose level 1 / 2. |
| `-l <file>` | Set the main log file. **Inert** (Logback-controlled now). |
| `-h` | Print help and exit. |

> The built-in `-h` help text is partly outdated (e.g. it states a default port of `5233` and an
> "exclusive" UDP upper bound). The tables here reflect the actual code.

---

## Logging

Logging was migrated from **log4j2** to **SLF4J + Logback**, matching the RMBTControlServer. log4j is
no longer a dependency.

- Configuration lives in [`src/main/resources/logback.xml`](src/main/resources/logback.xml) (bundled
  into the jar). To override at runtime, point Logback at an external file:
  ```bash
  java -Dlogback.configurationFile=/etc/qos/logback.xml -jar target/RMBTQoSServer.jar
  ```
- **Default behavior:** everything logs to the **console** at `INFO`.
- **Logstash shipping (optional):** if the environment variable `LOG_HOST` is set, a
  `LogstashTcpSocketAppender` is added that sends JSON logs to `LOG_HOST:LOG_PORT` (default port
  `4560`) tagged with `"app_name":"qos-service"`. If `LOG_HOST` is unset, the server stays
  console-only (it cannot fail to start over logging).
  ```bash
  LOG_HOST=logs.example.com LOG_PORT=4560 java -jar target/RMBTQoSServer.jar
  ```
- **Per-service loggers:** the server logs under four named loggers — `QOS.SERVER`, `QOS.TCP`,
  `QOS.UDP`, `QOS.DEBUG`. You can set levels individually in `logback.xml`, e.g.:
  ```xml
  <logger name="QOS.UDP" level="DEBUG"/>
  <logger name="QOS.TCP" level="WARN"/>
  ```
- **Adding a file appender** (replacing the old `server.log*` keys) is done the standard Logback way:
  ```xml
  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
      <file>/var/log/qos/qos.log</file>
      <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
          <fileNamePattern>/var/log/qos/qos.%d{yyyy-MM-dd}.log</fileNamePattern>
      </rollingPolicy>
      <encoder><pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern></encoder>
  </appender>
  <root level="INFO">
      <appender-ref ref="CONSOLE"/>
      <appender-ref ref="FILE"/>
  </root>
  ```

> Note: the application's own `verbose` level no longer gates log volume — use Logback log levels.

---

## Interactive server console

When `server.console=true` **and** the process has a real controlling terminal, an admin console runs
on stdin (`TestServerConsole.start()`). It is purely operational (it does not affect logging) and
supports:

| Command | Action |
|---|---|
| `show clients` | List active client connections. |
| `show info` | Show server information. |
| `show opened tcp ports` | List currently open TCP test sockets. |
| `show opened udp ports` | List currently open UDP test ports. |
| `settings set verbose <0..2>` | Change the verbose level at runtime. |
| `list services` | List registered background services. |
| `shutdown` | Shut the server down. |
| `help` | Command help. |
| `exit` | Leave the console prompt. |

Under systemd/Docker/redirected stdin `System.console()` is `null`, so the console is silently
disabled — operate the server via signals (Ctrl-C/SIGINT triggers a clean shutdown) and the REST
interface instead. The old **`server.log.console`** key (a log4j console appender) is **obsolete** —
console output is now produced by Logback's `CONSOLE` appender.

---

## REST monitoring interface

Enabled with `server.service.rest=true` (+ `.port`, optional `.ip`, `.ssl`). It exposes a small
read-only JSON API (Restlet) for health/monitoring:

| Method & path | Returns |
|---|---|
| `GET /` | Server status: `{ "starttime": <epoch_ms>, "version": "<major.minor.patch>" }`. If the server has recorded internal errors, an `errors` array is added **and the HTTP status is 500** — useful as a health probe. |
| `GET /info/udp` | UDP servers grouped by port: `{ "protocol_type":"udp", "servers":[ { "port":N, "server_list":[ { "address":..., "running":bool, "clients":[ { "client":..., "rcv":N, "dup":N } ] } ] } ] }`. |
| `GET /info/tcp` | TCP servers grouped by port: `{ "protocol_type":"tcp", "servers":[ { "port":N, "server_list":[ { "address":..., "ttl":<epoch_ms> } ] } ] }`. |
| `GET /info` or any other type | `{ "protocol_type":"unknown", "errors":["unknown protocol","allowed protocols: 'udp', 'tcp'"] }`. |
| anything else | `{ "errors":["resource not found"] }`. |

Example:

```bash
curl http://127.0.0.1:10080/            # status / health
curl http://127.0.0.1:10080/info/udp    # live UDP test servers + per-client packet counts
curl http://127.0.0.1:10080/info/tcp    # live TCP test servers
```

---

## `guard.properties`

A small **watchdog / health marker file** maintained automatically by `RuntimeGuardService` in the
working directory. It records whether the server believes it is running and detects unclean shutdowns:

```properties
last_startup=Wed Mar 01 16:31:01 CET 2023
status=up
```

- On **startup** the service reads the previous `status`. If it was not `down` (i.e. the last run did
  not shut down cleanly / crashed), it logs *"Test server shutdown not executed correctly!"*. It then
  sets `status=up` and `last_startup=<now>`.
- On **shutdown** it sets `status=down` and `last_shutdown=<now>` and logs the uptime.
- If the file is missing on startup it is created.

Keys: `status` (`up`/`down`), `last_startup`, `last_shutdown`. **Do not edit it manually** — it is
machine-generated. External monitoring can read `status` to alert on crashes.

---

## TLS

When `server.ssl=true` (or `server.service.rest.ssl=true`) the server loads the JKS keystore from the
classpath at **`/crt/qosserver.jks`** (type `JKS`). This keystore is **not** included in the
repository; provide your own at build/packaging time.

> The built-in TLS stack is dated. For production, terminating TLS with a wrapper such as **stunnel**
> in front of the plain control port is recommended.

Generate a keystore with:

```bash
keytool -genkey -keyalg RSA -alias Qos -keystore qosserver.jks \
        -storepass [STORE_PW] -keypass [ALIAS_PW]
```

---

## Repository layout notes

- `src/main/java` — server sources (incl. the extracted `at.rtr.rmbt.util.*` and
  `at.rtr.rmbt.shared.*` classes the server depends on).
- `src/test/java` — active JUnit 5 + Mockito tests.
- `legacy-tests/` — original JUnit 4 + JMockit tests, **kept for reference only and excluded from the
  build** (see `legacy-tests/README.md`).
- `PROTOCOL.md` — the full client/server wire protocol.

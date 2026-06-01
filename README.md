# RMBT QoS Server

The QoS (Quality of Service) test server for **Open-RMBT** (the engine behind RTR-Netztest). 
**This code is outdated and not ready for production. Please use it as lab reference only.**

Main class: `at.rtr.rmbt.qos.testserver.TestServer`.

Open RMBT QoS is open source software, licensed under the **Apache License 2.0** — see
[`LICENSE`](LICENSE).

---

## What it does

It is a standalone, long-running Java server that measurement probes (RMBT clients) connect to in
order to run **QoS measurements**. On startup it:

1. Binds a **TCP control port** on the configured interface(s), optionally wrapped in **TLS**. Each
   accepted connection is handled by a `ClientHandler` that speaks the line-based **QoS Testserver
   Protocol (QTP)** — see [`PROTOCOL.md`](PROTOCOL.md). After a handshake (greeting + identity token)
   the client requests individual QoS tests:
   - **TCP** connectivity tests (incoming / outgoing) on arbitrary ports,
   - **UDP** packet tests (incoming / outgoing) measuring packet loss and duplicates,
   - **VoIP / RTP** tests (jitter, loss, sequence) over non-blocking UDP ports,
   - **SIP** tests on TCP "competence" ports,
   - **Non-transparent proxy** (NTP) detection,
   - UDP port discovery and result queries.
2. Opens the configured **UDP test ports** (a port range, an explicit list, and non-blocking "NIO"
   ports used for VoIP).
3. Runs background **watcher services** (TCP/UDP) and a **runtime guard** (see
   [`guard.properties`](#guardproperties)).
4. Optionally serves a **REST monitoring interface** (see [REST monitoring interface](#rest-monitoring-interface)).
5. Installs a Ctrl-C shutdown hook that closes sockets and records a clean shutdown.

The full wire protocol (commands, responses, appendices) is documented in [`PROTOCOL.md`](PROTOCOL.md).

---

## Building and running

Standard Maven project producing a single runnable "fat" jar:

```bash
mvn package
# -> target/RMBTQoSServer.jar   (Main-Class: at.rtr.rmbt.qos.testserver.TestServer)
```

Run the unit tests:

```bash
mvn test
```

Run the server:

```bash
# Uses ./config.properties if present, otherwise command-line args
java -jar target/RMBTQoSServer.jar

# Or with command-line options (see below)
java -jar target/RMBTQoSServer.jar -p 5234 -u 10000 10050 -t 200
```

### Java versions

Requires **Java 17**. It compiles and runs on **JDK 17 through 25** (`maven.compiler.release = 17`).

---

## Configuration (`config.properties`)

Settings are read by `ServerPreferences`. Precedence:

- **No command-line arguments** → `config.properties` is loaded (from the working directory, falling
  back to the classpath).
- **Any command-line arguments are present** → `config.properties` is not loaded automatically; only
  the arguments apply, unless you explicitly pass `-f config.properties`.

### Networking and test parameters

| Key | Meaning |
|---|---|
| `server.ip` | Interface IP(s) to bind to. Comma-separated list, IPv4/IPv6. Omit ⇒ `0.0.0.0` (all interfaces). |
| `server.port` | TCP control port clients connect to (default `5234`; the shipped `config.properties` sets `5233`). |
| `server.ssl` | `true`/`false`. Wrap the control port in TLS using the keystore `/crt/qosserver.jks` (see [TLS](#tls)). |
| `server.threads` | Size of the fixed worker thread pool ≈ max simultaneous clients. Minimum 5 (startup fails below that); a warning is printed at ≤ 10. |
| `server.ip.check` | `true`/`false`. If true, only client IPs registered during test setup receive responses (a per-IP candidate map is enforced). |
| `server.udp.minport` / `server.udp.maxport` | Inclusive UDP port range opened for UDP tests. |
| `server.udp.ports` | Additional explicit blocking UDP ports (comma list), e.g. `53,123,500,...`. |
| `server.udp.nio.ports` | Non-blocking (NIO) UDP ports (comma list). Required for VoIP tests (bidirectional streams). A port may not appear in both the blocking and NIO sets. |
| `server.tcp.competence.sip` | TCP ports (comma list) that additionally speak the SIP test protocol, e.g. `5060`. |
| `server.secret` | HMAC secret intended for client-token verification. Note: the server does not verify the token signature (`ClientHandler.CHECK_TOKEN` is `false`), so this key is not currently used. |
| `server.verbose` | `0`/`1`/`2`. Does not control log output (Logback log levels do); it affects the interactive console and a few explicit `verbose >= 1` code paths. |

### REST monitoring service

| Key | Meaning |
|---|---|
| `server.service.rest` | `true`/`false` — enable the REST monitoring interface. |
| `server.service.rest.port` | TCP port for the REST interface (e.g. `10080`). Required when REST is enabled. |
| `server.service.rest.ip` | Bind IP for REST (default `127.0.0.1`). |
| `server.service.rest.ssl` | `true`/`false` — serve REST over HTTPS (uses the same keystore as above). |

See [REST monitoring interface](#rest-monitoring-interface).

### Console

| Key | Meaning |
|---|---|
| `server.console` | `true`/`false` — enable the interactive admin console on stdin. Requires a real terminal (`System.console()`), so it is a no-op under systemd/Docker/redirected input. See [Interactive console](#interactive-console). |

### Ignored logging keys

Logging is configured through `logback.xml` (see [Logging](#logging)), not through `config.properties`.
The following keys are still parsed but have **no effect**; configure the equivalent behavior in
`logback.xml` instead:

`server.log.console`, `server.logging`, `server.log`, `server.log.udp`, `server.log.tcp`,
`server.log.pattern`, `server.syslog`, `server.syslog.host`, `server.syslog.pattern`.

---

## Command-line options

Parsed by `ServerPreferences` (case-insensitive). When any option is present, `config.properties` is
not loaded automatically (use `-f` to load one).

| Option | Meaning |
|---|---|
| `-f <file>` | Load settings from this config file. |
| `-p <port>` | Control port. |
| `-u <min> <max>` | UDP test port range (inclusive). |
| `-t <threads>` | Worker thread pool size. |
| `-ip <ip>` | Bind interface IP (repeatable). |
| `-ic` | Enable the client IP check (`server.ip.check`). |
| `-s` | Enable TLS on the control port. |
| `-k <secret>` | HMAC secret key (see the `server.secret` note above). |
| `-v` / `-vv` | Verbose level 1 / 2. |
| `-l <file>` | Set the main log file. No effect (logging is controlled by `logback.xml`). |
| `-h` | Print help and exit. |

---

## Logging

Logging uses **SLF4J + Logback**, configured in
[`src/main/resources/logback.xml`](src/main/resources/logback.xml) (bundled into the jar). To use an
external configuration at runtime:

```bash
java -Dlogback.configurationFile=/etc/qos/logback.xml -jar target/RMBTQoSServer.jar
```

- By default everything logs to the **console** at `INFO`.
- If the environment variable `LOG_HOST` is set, a `LogstashTcpSocketAppender` also sends JSON logs to
  `LOG_HOST:LOG_PORT` (default port `4560`), tagged with `"app_name":"qos-service"`. If `LOG_HOST` is
  unset, the server stays console-only.
  ```bash
  LOG_HOST=logs.example.com LOG_PORT=4560 java -jar target/RMBTQoSServer.jar
  ```
- The server logs under four named loggers — `QOS.SERVER`, `QOS.TCP`, `QOS.UDP`, `QOS.DEBUG` — whose
  levels can be set individually:
  ```xml
  <logger name="QOS.UDP" level="DEBUG"/>
  <logger name="QOS.TCP" level="WARN"/>
  ```
- A rolling file appender is added the standard Logback way:
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

---

## Interactive console

When `server.console=true` and the process has a real controlling terminal, an admin console runs on
stdin. It is purely operational and supports:

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

Under systemd/Docker/redirected stdin, `System.console()` is `null` and the console is disabled;
operate the server via signals (Ctrl-C / SIGINT triggers a clean shutdown) and the REST interface.

---

## REST monitoring interface

Enabled with `server.service.rest=true` (+ `.port`, optional `.ip`, `.ssl`). It exposes a small
read-only JSON API (Restlet) for health/monitoring:

| Method & path | Returns |
|---|---|
| `GET /` | Server status: `{ "starttime": <epoch_ms>, "version": "<major.minor.patch>" }`. If the server has recorded internal errors, an `errors` array is added and the HTTP status is `500` — useful as a health probe. |
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
  not shut down cleanly), it logs *"Test server shutdown not executed correctly!"*, then sets
  `status=up` and `last_startup=<now>`.
- On **shutdown** it sets `status=down` and `last_shutdown=<now>` and logs the uptime.
- If the file is missing on startup it is created.

Keys: `status` (`up`/`down`), `last_startup`, `last_shutdown`. The file is machine-generated on every
start/stop and is **not** kept in version control (it is in `.gitignore`). External monitoring can
read `status` to alert on crashes.

---

## TLS

When `server.ssl=true` (or `server.service.rest.ssl=true`) the server loads a `JKS` keystore from the
classpath at **`/crt/qosserver.jks`**. This keystore is not included in the repository; provide your
own at build/packaging time.

The TLS context is created with `SSLContext.getInstance("TLS")` and no explicit protocol or cipher
configuration, so the JDK negotiates the protocol version (TLS 1.2/1.3 on JDK 17–25) and default
cipher suites. The keystore password is hard-coded in the source (`TestServer.QOS_KEY_PASSWORD`), the
keystore must be embedded in the jar, the server uses a trust-all client `TrustManager` (clients are
authenticated by the HMAC token, not by client certificates), and protocols/ciphers/cert rotation are
not configurable without code changes. For production, terminating TLS with a wrapper such as
**stunnel** (or a reverse proxy) in front of the plain control port is recommended.

Generate a keystore with:

```bash
keytool -genkey -keyalg RSA -alias Qos -keystore qosserver.jks \
        -storepass [STORE_PW] -keypass [ALIAS_PW]
```

---

## Repository layout

- `src/main/java` — server sources, including the `at.rtr.rmbt.util.*` and `at.rtr.rmbt.shared.*`
  helper classes the server depends on.
- `src/main/resources/logback.xml` — logging configuration.
- `src/test/java` — JUnit 5 + Mockito tests.
- `legacy-tests/` — JUnit 4 + JMockit tests, excluded from the build (see `legacy-tests/README.md`).
- `config.properties` — server configuration.
- `PROTOCOL.md` — the client/server wire protocol.
- `LICENSE` — Apache License 2.0.

---

## License

Open RMBT QoS is open source software released under the **Apache License, Version 2.0**. The full
license text is in [`LICENSE`](LICENSE), or at
<https://www.apache.org/licenses/LICENSE-2.0>.

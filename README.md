# RMBT QoS Server

The QoS (Quality of Service) test server for **Open-RMBT** (the engine behind RTR-Netztest).

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

Standard Maven project producing a single runnable "fat" jar. A plain `mvn` builds, tests and
packages it (default goal `package`):

```bash
mvn
# -> target/RMBTQoSServer.jar           runnable jar incl. all libraries (Main-Class: at.rtr.rmbt.qos.testserver.TestServer)
#    target/RMBTQoSServer-classes.jar   the project classes only (intermediate build product, not needed to run)
```

Run only the unit tests: `mvn test`; start from scratch: `mvn clean package`.

Run the server:

```bash
# Without arguments ./config.properties is required
java -jar target/RMBTQoSServer.jar

# Or with command-line options (see below)
java -jar target/RMBTQoSServer.jar -p 5234 -u 10000 10050 -t 200
```

> **Client-token verification is enabled by default.** Configure the keys the Control Server signs
> tokens with (`server.secret`, `server.secret.<name>` or `-k <secret>`), or every client is rejected.
> For testing only, verification can be disabled with `server.token.check=false` / `-ntc`:
>
> ```bash
> # token verification on (default) — keys must match the Control Server
> java -jar target/RMBTQoSServer.jar -k <key1>,<key2> -p 5233 -u 10100 10200
>
> # testing only: no authentication
> java -jar target/RMBTQoSServer.jar -ntc -p 5233 -u 10100 10200
> ```
>
> See [Client tokens](#client-tokens) for the v1/v2 token formats.

### Version and releases

The version is derived from git at build time (`git describe --tags --always`, same schema as
`open-rmbt-client-cli`): `v2.0.0` on a tagged commit, `v2.0.0-4-ged32b3d` four commits later (just the
commit hash if there is no tag). It is written to `version.properties` in the jar and

- printed at startup (`Starting QoSTestServer v2.0.0-4-ged32b3d-master ...`, i.e. plus the branch),
- appended to the command prompt a client gets after a successful login:
  `ACCEPT [UDPTEST IN/OUT int int] ... [QUIT] QoS v2.0.0-4-ged32b3d` (the `QoSSP0.1` greeting is unchanged,
  clients compare it exactly),
- returned as `build` by the REST status resource.

The GitHub workflow (`.github/workflows/build.yml`) builds and tests the jar on every push. Pushing a tag
(e.g. `git tag v2.0.0 && git push origin v2.0.0`) additionally creates a GitHub release with
`RMBTQoSServer.jar` and a source archive.

### Java versions

Requires **Java 17**. It compiles and runs on **JDK 17 through 25** (`maven.compiler.release = 17`).

---

## Installation

### Requirements

| Purpose | Needed |
|---|---|
| **Run** the server | a **Java Runtime (JRE) 17 or newer** (tested with 25), e.g. `apt install openjdk-25-jre-headless` |
| **Build** the server | a **JDK 17 or newer** and **Maven 3.6.3+**, e.g. `apt install openjdk-25-jdk-headless maven`, plus `git` (the version is derived from git tags) |

Instead of building, the jar can be taken from a GitHub release (`RMBTQoSServer.jar`).

### Network

The server needs public IPv4 and/or IPv6 addresses that are reachable without NAT and without port
filtering (a filtering firewall would distort the test results). Open/allow:

- the **control port** (`server.port`, e.g. TCP `443`),
- the **UDP test ports** (`server.udp.minport`–`server.udp.maxport`, `server.udp.ports`, `server.udp.nio.ports`),
- the **TCP test ports** the QoS objectives use (e.g. 21, 22, 25, 53, 80, 110, 143, 465, 554, 585, 587, 993,
  995, 5060, 6881, 9001) plus the random high ports of the non-transparent-proxy test, i.e. in practice all
  TCP ports on the test addresses. No other service may listen on these ports on the test addresses
  (e.g. bind `sshd` only to a management address, otherwise the port-22 test fails).

### Setup

```bash
# 1. service user and directory
useradd --system --home-dir /opt/qos-server --shell /usr/sbin/nologin qos
mkdir -p /opt/qos-server

# 2. jar (built with "mvn" or downloaded from a release) and configuration
cp target/RMBTQoSServer.jar config.properties /opt/qos-server/
chown -R qos:qos /opt/qos-server          # the server writes guard.properties there
chmod 600 /opt/qos-server/config.properties   # contains the token keys

# 3. TLS certificate readable by the service user, e.g. via a group
groupadd -f ssl-cert && usermod -aG ssl-cert qos
chgrp -R ssl-cert /etc/letsencrypt/live /etc/letsencrypt/archive
chmod -R g+rX /etc/letsencrypt/live /etc/letsencrypt/archive

# 4. systemd unit (grants CAP_NET_BIND_SERVICE for ports < 1024, no root needed)
cp qos-server.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now qos-server
journalctl -u qos-server -f
```

Edit `/opt/qos-server/config.properties` before starting:

1. `server.ip` — the public test addresses (IPv4 and IPv6, comma-separated),
2. `server.port`, `server.ssl=true` and `server.ssl.certificate` / `server.ssl.key` (see [TLS](#tls)),
3. `server.secret` / `server.secret.<name>` — the keys of **all measurement servers** the control server
   assigns, as the client presents the token of its speed test (see [Client tokens](#client-tokens)),
4. the UDP/TCP test ports (`server.udp.*`, `server.tcp.competence.sip`),
5. `server.threads=200`, `server.connections.perip=50`.

On startup the log shows the version, the settings and `Client token verification enabled ... with N key(s)`.
A quick check from another host: `openssl s_client -connect <ip>:443` must show `QoSSP0.1` and
`ACCEPT [TOKEN string]`; without a valid token the server closes the connection after 10 s.

After a certificate renewal restart the service (`systemctl restart qos-server`, e.g. as a certbot
deploy hook).

---

## Configuration (`config.properties`)

Settings are read by `ServerPreferences`. Precedence:

- **No command-line arguments** → `config.properties` is loaded (from the working directory, falling
  back to the classpath).
- **Any command-line arguments are present** → `config.properties` is not loaded automatically; only
  the arguments apply, unless you explicitly pass `-f config.properties`.

On startup the server prints its version. Configuration and startup problems (missing or invalid config
file, unknown arguments, missing token keys, unreadable certificate, port in use, …) are reported as a
single `FATAL: …` line and the process exits with code `1` (so systemd and scripts notice the failure);
`-h` prints the help and exits with `0`.

### Networking and test parameters

| Key | Meaning |
|---|---|
| `server.ip` | Interface IP(s) to bind to. Comma-separated list, IPv4/IPv6. Omit ⇒ `0.0.0.0` (all interfaces). |
| `server.port` | TCP control port clients connect to (default `5234`; the shipped `config.properties` sets `5233`). |
| `server.ssl` | `true`/`false`. Wrap the control port in TLS (certificate source: see the next rows) (see [TLS](#tls)). |
| `server.ssl.certificate` / `server.ssl.key` | PEM certificate chain (e.g. Let's Encrypt `fullchain.pem`) and unencrypted PKCS#8 PEM private key (`privkey.pem`). Takes precedence over a keystore. Restart the server after a certificate renewal. |
| `server.ssl.keystore` / `server.ssl.keystore.password` / `server.ssl.keystore.type` | Alternatively a JKS or PKCS12 keystore file (type derived from the `.p12`/`.pfx` extension if not set). If neither PEM files nor a keystore are configured, the keystore is loaded from `/crt/qosserver.jks` inside the jar. |
| `server.threads` | Size of the fixed worker thread pool = max simultaneous control connections. Minimum 5 (startup fails below that); a warning is printed at ≤ 10. Newer clients open one connection per test (~25 per test run), so use e.g. `200`. |
| `server.connections.perip` | Max simultaneous control connections from one client IP (default `50`); further connections are closed right away. Keep it well below `server.threads` so a single host cannot occupy all workers. |
| `server.ip.check` | `true`/`false`. If true, only client IPs registered during test setup receive responses (a per-IP candidate map is enforced). |
| `server.udp.minport` / `server.udp.maxport` | Inclusive UDP port range opened for UDP tests. |
| `server.udp.ports` | Additional explicit blocking UDP ports (comma list), e.g. `53,123,500,...`. |
| `server.udp.nio.ports` | Non-blocking (NIO) UDP ports (comma list). Required for VoIP tests (bidirectional streams). A port may not appear in both the blocking and NIO sets. |
| `server.tcp.competence.sip` | TCP ports (comma list) that additionally speak the SIP test protocol, e.g. `5060`. |
| `server.secret` | HMAC key(s) for client-token verification; a comma-separated list is allowed. Additional keys can be given as `server.secret.<name>=<key>` (e.g. one per measurement server, since the control server signs each test token with the key of the measurement server it assigned). A token is accepted if it was signed with **any** configured key. Verification is on by default (`server.token.check`). |
| `server.token.check` | `true` (default) / `false`. `false` accepts every client token without verification (lab/testing only). Same as the `-ntc` argument; `-Dqos.checkToken=true/false` overrides both. A warning is logged at startup when verification is off. See [Client tokens](#client-tokens). |
| `server.token.v2only` | `true` / `false` (default). Accept only v2 tokens (bound to the client IP) and reject legacy v1 tokens. Same as `-v2only`. |
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
| `-v2only` | Accept only v2 tokens (`server.token.v2only=true`). |
| `-ntc` | Disable client-token verification (`server.token.check=false`). Insecure, lab/testing only. |
| `-k <secret>` | HMAC secret key(s); may be repeated or a comma-separated list — required by default for token verification (see the `server.secret` note above). |
| `-v` / `-vv` | Verbose level 1 / 2. |
| `-l <file>` | Set the main log file. No effect (logging is controlled by `logback.xml`). |
| `-h` | Print help and exit. |

---

## Client tokens

Clients authenticate with the test token issued by the control server — the same token the measurement
server gets, verified with the same scheme as the RMBT measurement server (`open-rmbt-server`,
`token-migration.md`). Current control servers issue a combined token:

```
<v1>_#v2#<base64 v2>
e.g. 8723358c-2037-4029-a70c-91e5d9d35cf3_1700000000_oFAdP8+Cw9TqvJOgNc5ABOQRxss=_#v2#ZVPxAEzErM6+VBk3HmTzPw==
```

- **v1** (legacy, no `#v2#` marker): `UUID_TIMESTAMP_BASE64(HMAC-SHA1(key, "UUID_TIMESTAMP"))`, timestamp in epoch
  seconds. Not bound to the client address.
- **v2** (after `#v2#`): base64 of 16 bytes `time(4, big endian) | HMAC-SHA256(key, time)[0..8] |
  HMAC-SHA256(key, time | ip16)[0..4]`; `time` = low 32 bits of the epoch start time, `ip16` = client IP as
  IPv4-mapped IPv6. The server checks the signature **and that the control connection comes from the IP the token
  was issued for**. If the marker is present only the v2 part is validated.
- The control server signs each token with the key of the measurement server it assigned, so configure the keys of
  all measurement servers (`server.secret`, `server.secret.<name>`); a token is valid if any key matches.
- Accept window (v1 and v2): up to 600 s after and 60 s before the token's start time (the QoS test runs after
  the speed test, hence the longer window than on the measurement server).
- The log names the matching key, or the reason for a rejection (`INVALID_HMAC`, `INVALID_IP`, `TOO_LATE`,
  `TOO_EARLY`, `PARSE_ERROR`, `V2_REQUIRED`).
- **Caveat:** the v2 IP binding fails if the client reaches the control server and this server with different
  public addresses (e.g. IPv6 to one and IPv4 to the other, or NAT pools with different public IPs). Check the
  rejection reasons in the log before enabling `server.token.v2only`.

## Logging

Logging uses **SLF4J + Logback**. The configuration bundled in the jar
([`src/main/resources/logback.xml`](src/main/resources/logback.xml)) logs everything at `INFO` to the
console (stdout) — under systemd that ends up in the journal (`journalctl -u qos-server`).

### Loggers

| Logger | Content |
|---|---|
| `QOS.SERVER` | startup/settings, control connections, tokens, commands and answers |
| `QOS.TCP` | TCP test ports (TCP/NTP/SIP tests) |
| `QOS.UDP` | UDP and VoIP tests |
| `QOS.DEBUG` | runtime guard |

The bundled configuration logs at `INFO`; set a logger to `DEBUG`/`WARN` in your own configuration to get
more/less detail.

### Own configuration file

To change levels or add appenders, put a Logback configuration next to the jar and pass it with
`-Dlogback.configurationFile` — e.g. `/opt/qos-server/logback.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true" scanPeriod="60 seconds">

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- daily rotated log file, kept for 30 days -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>/var/log/qos-server/qos.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>/var/log/qos-server/qos.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- less noise from the TCP test ports, more detail for UDP -->
    <logger name="QOS.TCP" level="WARN"/>
    <logger name="QOS.UDP" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

With `scan="true"` Logback re-reads the file at runtime, so log levels can be changed without a restart.
In the systemd unit:

```ini
ExecStart=/usr/bin/java -Dlogback.configurationFile=/opt/qos-server/logback.xml -jar RMBTQoSServer.jar
# the unit uses ProtectSystem=strict, so the log directory must be writable explicitly:
ReadWritePaths=/opt/qos-server /var/log/qos-server
LogsDirectory=qos-server
```

### Logstash

If the environment variable `LOG_HOST` (or the system property of that name) is set, the server
additionally sends JSON logs to Logstash at `LOG_HOST:LOG_PORT` (default port `4560`), tagged with
`"app_name":"qos-service"`. This is done by the server itself, independently of the Logback
configuration file, and logged at startup (`Sending logs to Logstash at ...`):

```bash
LOG_HOST=logstash.example.org LOG_PORT=4560 java -jar RMBTQoSServer.jar
```

In the systemd unit: `Environment=LOG_HOST=logstash.example.org`.

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

With `server.ssl=true` the control port speaks TLS. The certificate is read, in this order, from

1. `server.ssl.certificate` + `server.ssl.key` — **PEM files**, e.g. directly from Let's Encrypt (the
   recommended way; no conversion needed):
   ```properties
   server.ssl.certificate=/etc/letsencrypt/live/example.org/fullchain.pem
   server.ssl.key=/etc/letsencrypt/live/example.org/privkey.pem
   ```
   The key has to be an unencrypted PKCS#8 PEM key (`-----BEGIN PRIVATE KEY-----`, as issued by
   Let's Encrypt; RSA, EC and Ed25519 are supported) and readable by the service user.
2. `server.ssl.keystore` + `server.ssl.keystore.password` (+ optional `server.ssl.keystore.type`) — a
   PKCS12 or JKS keystore, if you already have one,
3. the legacy classpath resource `/crt/qosserver.jks` inside the jar (only if nothing is configured).

Restart the server after a certificate renewal (e.g. from a certbot deploy hook). Protocol versions and
cipher suites are the JDK defaults (TLS 1.2/1.3). Clients are authenticated by their token, not by
client certificates, so none are requested.

The REST interface (`server.service.rest.ssl=true`) only supports a keystore file (`server.ssl.keystore`).

---

## Security and limits

The server is reachable from the internet and treats all client input as untrusted:

- **Authentication:** every control connection must present a valid token (HMAC, age, and for v2 tokens
  the client IP) within **10 s** after connecting, otherwise it is closed (absolute deadline, also for
  clients that keep sending data). See [Client tokens](#client-tokens).
- **Connections:** at most `server.connections.perip` (default 50) simultaneous control connections per
  client IP; connections idle for 15 s (or the client-requested timeout, max 60 s) are closed.
- **Input size:** protocol lines (control channel, TCP test ports, SIP headers) are limited to 4096
  characters, SIP messages to 64 lines, requests on a TCP test port to 10 per connection.
- **Test parameters** requested by an authenticated client are bounded: max 100 UDP packets per test,
  VoIP call duration ≤ 30 s, packet delay 10–1000 ms, sample rate ≤ 96 kHz, ≤ 32 bits per sample,
  RTP payload ≤ 1400 bytes, at most 64 tests per control connection.
- **UDP:** packets are only processed for tests registered over an authenticated control connection;
  unsolicited (or spoofed) packets are dropped without creating any state or log output. UDP/RTP
  responses are only sent to the IP of the control connection (no reflection to third parties).
- **Logs:** client-supplied strings are logged with control characters replaced (no log injection);
  tokens' HMACs are never echoed to the client.
- **Operation:** run as an unprivileged user with `CAP_NET_BIND_SERVICE` only (see `qos-server.service`),
  keep the token keys in a `chmod 600` config file.

The limits are constants in `ClientHandler`, `TcpClientHandler`, `SipCompetence` and `LineReader`.

---

## Repository layout

- `src/main/java` — server sources, including the `at.rtr.rmbt.util.*` and `at.rtr.rmbt.shared.*`
  helper classes the server depends on.
- `src/main/resources/logback.xml` — logging configuration.
- `src/test/java` — JUnit 6 + Mockito tests.
- `legacy-tests/` — JUnit 4 + JMockit tests, excluded from the build (see `legacy-tests/README.md`).
- `config.properties` — server configuration.
- `qos-server.service` — systemd unit (see [Installation](#installation)).
- `qos_server.sh` — legacy SysV init script (superseded by the systemd unit).
- `.github/workflows/build.yml` — build/test on every push, GitHub release on tags.
- `PROTOCOL.md` — the client/server wire protocol.
- `LICENSE` — Apache License 2.0.

---

## License

Open RMBT QoS is open source software released under the **Apache License, Version 2.0**. The full
license text is in [`LICENSE`](LICENSE), or at
<https://www.apache.org/licenses/LICENSE-2.0>.

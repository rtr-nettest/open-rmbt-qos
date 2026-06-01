# Legacy tests (not built, not run)

These are the original JUnit 4 + JMockit tests from the source repository. They are kept here for
reference only.

- This folder is **outside `src/`**, so Maven never compiles or runs it — it has no effect on the
  build or on `mvn test`.
- They have **not** been migrated to JUnit 5 + Mockito. They rely on JMockit features (global
  `@Mocked`/`MockUp` redefinition of JDK classes such as `Socket`/`ServerSocket`/`DataInputStream`,
  partial mocking of the class under test) and several bind real sockets/start servers, which do not
  port 1:1 to Mockito on modern JDKs.
- The migrated, active tests live under `src/test/java` (`ServerPreferencesTest`, `ClientHandlerTest`).

Do not add this folder to the build. If these tests are ever needed, they require either a
dependency-injection refactor of `ClientHandler`/the servers, or `mockStatic`/`mockConstruction`
scaffolding around `TestServer`.

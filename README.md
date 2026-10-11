# Modern Java Lab

One evolving **payments domain** used to learn what Java has shipped since JDK 8. Each
feature appears where it belongs in real code, and each type's `///` Markdown doc comment
explains the feature, why it exists, and its traps.

Chapters so far: **Project Amber** and **Project Loom**. Core-library APIs, Panama,
Leyden/Lilliput and Valhalla will be added to the same domain in later lessons.

## Build and run

| Command                                                            | JDK | What it does                                  |
|--------------------------------------------------------------------|-----|-----------------------------------------------|
| `mvn verify`                                                       | 25  | Main build: final features only               |
| `mvn -Ppreview verify`                                             | 27  | Adds `src/preview/**` with `--enable-preview` |
| `mvn -q compile && java -cp target/classes scripts/AmberTour.java` | 25  | Runs the compact-source-file tour             |
| `mvn -q compile && java -cp target/classes scripts/LoomTour.java`  | 25  | 10 000 blocking captures: pool vs virtual     |
| `mvn javadoc:javadoc`                                              | 25  | Renders the `///` docs as HTML                |

## Project Amber feature map

| Feature                                   | JEP      | Final in      | Where                                                                   |
|-------------------------------------------|----------|---------------|-------------------------------------------------------------------------|
| Local-variable type inference (`var`)     | 286, 323 | 10, 11        | throughout; rules in `PaymentGateway#process`                           |
| Switch expressions                        | 361      | 14            | `DeclineReason#isRetryable`                                             |
| Text blocks                               | 378      | 15            | `ReceiptRenderer`, `ReceiptRendererTest`                                |
| Records                                   | 395      | 16            | `Money`, `PaymentId`, `RiskPolicy`, `PaymentRequest`                    |
| Pattern matching for `instanceof`         | 394      | 16            | `WebhookPayloadParser`                                                  |
| Sealed classes                            | 409      | 17            | `PaymentMethod` (incl. `non-sealed`), `PaymentStatus`, `RiskDecision`   |
| Record patterns                           | 440      | 21            | `Payment#apply`, `ReceiptRenderer#authLine` (nested)                    |
| Pattern matching for `switch`             | 441      | 21            | `Payment#apply`, `RiskEngine#evaluate`, `WebhookPayloadParser#toAmount` |
| Unnamed variables and patterns `_`        | 456      | 22            | `Payment`, `RiskEngine`, `SettlementReport`                             |
| Markdown doc comments `///`               | 467      | 23            | every file                                                              |
| Module import declarations                | 511      | 25            | `SettlementReport`                                                      |
| Compact source files and instance `main`  | 512      | 25            | `scripts/AmberTour.java`                                                |
| Flexible constructor bodies               | 513      | 25            | `IllegalTransitionException`                                            |
| Primitive types in patterns (**preview**) | 532      | preview in 27 | `src/preview/.../PrimitivePatterns`                                     |

Still upcoming in Amber (not in this code yet): derived record creation / withers (JEP 468), deconstruction for ordinary
classes, constant patterns.

## Project Loom feature map

| Feature                                                 | JEP | Final in      | Where                                               |
|---------------------------------------------------------|-----|---------------|-----------------------------------------------------|
| Virtual threads                                         | 444 | 21            | `CaptureRun`, `scripts/LoomTour.java`               |
| `ExecutorService` as `AutoCloseable`                    | n/a | 19            | `CaptureRun#captureAll`                             |
| `Future.state()`, `resultNow()`, `exceptionNow()`       | n/a | 19            | `CaptureRun#captureAll`                             |
| `Thread.ofVirtual()` builders, `Thread.sleep(Duration)` | n/a | 21, 19        | `CaptureRun`, `SimulatedIssuer`                     |
| Synchronize virtual threads without pinning             | 491 | 24            | `SimulatedIssuer` (contended `synchronized` ledger) |
| Scoped values                                           | 506 | 25            | `RequestContext`, read by `SimulatedIssuer`         |
| Structured concurrency (**preview**)                    | 533 | preview in 27 | `src/preview/.../ParallelRiskSignals`, `FxQuotes`   |

What the lesson shows, in one line each:

- **Blocking is cheap again.** `IssuerClient` is a plain synchronous interface. `LoomTour` captures 10 000
  payments with 200 ms latency in ~20 s on 100 platform threads and in ~0.3 s with a virtual thread each.
- **Limit the resource, not the threads.** `CaptureRun` uses a `Semaphore` as a bulkhead instead of a pool size.
- **Scoped values replace `ThreadLocal` for context**, but plain executors don't inherit them: `CaptureRun`
  rebinds per task, while `StructuredTaskScope` forks inherit automatically.
- **Structured vs unstructured.** Independent jobs (capture run) use an executor and per-task outcomes. One
  request split into subtasks (risk signals, FX quotes) uses `StructuredTaskScope`, so a failure or timeout
  cancels the siblings and nothing outlives the block.
- **JDK 27's structured concurrency API** adds a third type parameter, so a joiner chooses the exception
  `join()` throws (`FxQuotes` throws `FxUnavailableException`, not `ExecutionException`).

## Package layout

```
com.ajays.modernjava.payments
├── domain    Money, IDs, PaymentMethod, PaymentStatus, PaymentCommand, Payment (state machine)
├── wallets   PartnerWallet: extends the non-sealed branch from outside the domain package
├── context   RequestContext: per-request data carried by a ScopedValue
├── issuer    IssuerClient (blocking), AuthorizationResult, SimulatedIssuer (latency, idempotency)
├── capture   CaptureRun: end-of-day captures, a virtual thread each, semaphore bulkhead
├── risk      RiskEngine (pattern-matching rules), RiskDecision, RiskPolicy, CustomerProfile
├── ingest    WebhookPayloadParser (untyped → typed), PaymentRequest DTO, Validated<T>
├── report    ReceiptRenderer (text blocks), SettlementReport (module import, local records)
└── PaymentGateway   validate → initiate → risk → issuer authorize/decline

com.ajays.modernjava.preview   (src/preview, JDK 27 + --enable-preview)
├── PrimitivePatterns     primitive types in patterns (JEP 532)
├── ParallelRiskSignals   builds a CustomerProfile from three services in one StructuredTaskScope
└── FxQuotes              hedged FX quotes: anySuccessfulOrThrow with a custom exception
```

## Conventions

- **Records first.** Data is modelled as records. Lombok is used only for what Java still
  lacks: `@With` (no withers yet), `@Builder` (records have no named or optional
  parameters), and `@Slf4j`/`@RequiredArgsConstructor` on service classes.
- **Nulls are rejected at construction with Lombok `@NonNull`** on record components, not with
  `Objects.requireNonNull` boilerplate. A null is a caller bug (`NullPointerException`); a bad value
  is bad data (`IllegalArgumentException`). Settings live in `lombok.config`. The one deliberate
  exception is `IllegalTransitionException`, whose explicit checks demonstrate a constructor prologue.
- **Two tiers of validation.** Jakarta annotations on boundary DTOs collect every input
  error. Compact constructors make invalid domain objects impossible to construct.
- **No `default` in switches over types we own.** Exhaustiveness is what makes sealing
  worth it.
- **Preview features stay in `src/preview`.** They never leak into the LTS build.
- **Blocking code, virtual threads.** Services expose synchronous methods. Callers choose the threads; no
  `CompletableFuture` chains for I/O.
- **Request context lives in `RequestContext`**, a scoped value bound once at the edge. Code that calls the
  issuer must run inside `RequestContext.call/run`.

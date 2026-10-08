# Modern Java Lab

One evolving **payments domain** used to learn what Java has shipped since JDK 8. Each
feature appears where it belongs in real code, and each type's `///` Markdown doc comment
explains the feature, why it exists, and its traps.

The first chapter is **Project Amber**. Loom, core-library APIs, Panama, Leyden/Lilliput and
Valhalla will be added to the same domain in later lessons.

## Build and run

| Command                                                            | JDK | What it does                                  |
|--------------------------------------------------------------------|-----|-----------------------------------------------|
| `mvn verify`                                                       | 25  | Main build: final features only               |
| `mvn -Ppreview verify`                                             | 27  | Adds `src/preview/**` with `--enable-preview` |
| `mvn -q compile && java -cp target/classes scripts/AmberTour.java` | 25  | Runs the compact-source-file tour             |
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

## Package layout

```
com.ajays.modernjava.payments
├── domain    Money, IDs, PaymentMethod, PaymentStatus, PaymentCommand, Payment (state machine)
├── wallets   PartnerWallet: extends the non-sealed branch from outside the domain package
├── risk      RiskEngine (pattern-matching rules), RiskDecision, RiskPolicy, CustomerProfile
├── ingest    WebhookPayloadParser (untyped → typed), PaymentRequest DTO, Validated<T>
├── report    ReceiptRenderer (text blocks), SettlementReport (module import, local records)
└── PaymentGateway   validate → initiate → risk → authorize/decline
```

## Conventions

- **Records first.** Data is modelled as records. Lombok is used only for what Java still
  lacks: `@With` (no withers yet), `@Builder` (records have no named or optional
  parameters), and `@Slf4j`/`@RequiredArgsConstructor` on service classes.
- **Two tiers of validation.** Jakarta annotations on boundary DTOs collect every input
  error. Compact constructors make invalid domain objects impossible to construct.
- **No `default` in switches over types we own.** Exhaustiveness is what makes sealing
  worth it.
- **Preview features stay in `src/preview`.** They never leak into the LTS build.

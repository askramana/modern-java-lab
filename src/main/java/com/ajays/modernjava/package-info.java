/// # Modern Java Lab
///
/// One evolving payments domain that exercises the Java features delivered after JDK 8.
/// Features are introduced where they *naturally belong* in real code, not as isolated toy
/// snippets. Every type's doc comment says which feature it demonstrates, what problem
/// that feature solves, and the traps around it.
///
/// ## How the lab grows
///
/// | OpenJDK project | Theme                                   | Status in this lab |
/// |-----------------|-----------------------------------------|--------------------|
/// | **Amber**       | Language productivity, data-oriented programming | This module |
/// | Loom            | Virtual threads, structured concurrency, scoped values | Next |
/// | Core libraries  | HttpClient, sequenced collections, gatherers | Later |
/// | Panama          | Foreign Function & Memory API           | Later |
/// | Leyden / Lilliput | Startup, footprint, AOT cache         | Later |
/// | Valhalla        | Value objects (preview in JDK 28)       | Later |
///
/// ## Reading order for Project Amber
///
/// 1. [com.ajays.modernjava.payments.domain.Money]: records as value objects, compact constructors
/// 2. [com.ajays.modernjava.payments.domain.PaymentMethod]: sealed hierarchies, `non-sealed` extension points
/// 3. [com.ajays.modernjava.payments.domain.Payment]: exhaustive pattern-matching state machine,
///    Lombok `@With` filling the "withers" gap
/// 4. [com.ajays.modernjava.payments.risk.RiskEngine]: record patterns, guards, unnamed patterns
/// 5. [com.ajays.modernjava.payments.ingest.WebhookPayloadParser]: type patterns over untyped data
/// 6. [com.ajays.modernjava.payments.report.ReceiptRenderer]: text blocks, switch expressions
/// 7. [com.ajays.modernjava.payments.domain.IllegalTransitionException]: flexible constructor bodies
/// 8. [com.ajays.modernjava.payments.report.SettlementReport]: module imports, local records
/// 9. `scripts/AmberTour.java`: compact source files and instance `main`
///
/// ## Design stance: data-oriented programming
///
/// Classic Java puts behaviour on objects (polymorphism). Amber adds a second, complementary
/// style for code that is mostly *about data*:
///
/// - **Model data as data**: immutable [Record]s whose API *is* their state.
/// - **Make illegal states unrepresentable**: `sealed` hierarchies close the set of variants.
/// - **Put the operation next to the decision, not inside the type**: pattern-matching
///   `switch` takes the data apart, and the compiler proves every variant is handled.
///
/// Use OOP when the set of *operations* is closed and the set of *types* is open.
/// Use data-oriented style when the set of *types* is closed and *operations* keep growing.
/// Payment states, payment methods and risk decisions are the second kind.
package com.ajays.modernjava;

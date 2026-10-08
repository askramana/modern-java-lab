package com.ajays.modernjava.payments.domain;

import java.util.Objects;

/// A [PaymentCommand] was applied in a [PaymentStatus] where it isn't allowed.
///
/// ## Feature: flexible constructor bodies (JEP 513, final in Java 25)
///
/// Until Java 25, `super(...)` or `this(...)` had to be the **first statement** of a
/// constructor. To validate arguments or compute what to pass up, you had to cram it into a
/// static helper call inside the `super(...)` argument list:
///
/// ```
/// // Java 8 – 24
/// public IllegalTransitionException(PaymentId id, PaymentStatus from, PaymentCommand cmd) {
///     super(message(Objects.requireNonNull(id), Objects.requireNonNull(from), Objects.requireNonNull(cmd)));
///     this.paymentId = id;
///     ...
/// }
/// ```
///
/// Now a constructor may have a **prologue**: statements before the explicit constructor
/// call. The prologue may:
///
/// - validate arguments and fail fast, *before* the superclass does any work;
/// - compute local variables to pass to `super(...)`;
/// - **assign this class's own fields** (only fields without an initializer).
///
/// It may **not** read `this`, call instance methods, or read fields, because the object
/// isn't initialized yet. The compiler enforces this.
///
/// ### Why early field assignment matters
///
/// If a superclass constructor calls an overridable method, the override used to run while
/// the subclass's fields were still `null`/`0`, a classic source of NPEs. Assigning fields in
/// the prologue means they're already set when the superclass calls back into the subclass.
/// (`Throwable`'s constructor calls the overridable `fillInStackTrace()`, so exceptions are a
/// realistic case.)
///
/// Records don't need this: their compact constructor already runs before field
/// assignment, and they have no superclass to call.
public final class IllegalTransitionException extends IllegalStateException {

    private final PaymentId paymentId;
    private final PaymentStatus from;
    private final PaymentCommand command;

    public IllegalTransitionException(PaymentId paymentId, PaymentStatus from, PaymentCommand command) {
        // ---- prologue: runs before the superclass constructor ----
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(command, "command");

        this.paymentId = paymentId;   // early assignment of our own fields is allowed
        this.from = from;
        this.command = command;

        var message = "Payment %s: cannot %s a payment that is %s".formatted(
                paymentId.value(),
                command.getClass().getSimpleName().toLowerCase(),
                from.getClass().getSimpleName().toUpperCase());
        // ---- explicit constructor invocation ----
        super(message);
        // ---- epilogue: normal constructor code; `this` is fully usable ----
    }

    public PaymentId paymentId() { return paymentId; }

    public PaymentStatus from() { return from; }

    public PaymentCommand command() { return command; }
}

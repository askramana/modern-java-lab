package com.ajays.modernjava.preview;

import java.util.OptionalInt;

/// **PREVIEW**: primitive types in patterns, `instanceof` and `switch`
/// (JEP 532, fifth preview in JDK 27). Compiled only by `mvn -Ppreview verify` on JDK 27.
///
/// ## The problem
///
/// In final Java, patterns work only with reference types, and `switch` works only on
/// `int`-like types, `String` and enums. Primitive conversions are either implicit or forced
/// with a cast, and casts **lose information silently**:
///
/// ```
/// long minorUnits = 3_000_000_000L;
/// int i = (int) minorUnits;    // -1294967296: no error, just wrong
/// ```
///
/// ## The idea
///
/// Make primitive type patterns mean **"does this value convert exactly?"**
///
/// - `minorUnits instanceof int i` is `true` only if the `long` fits in an `int` without
///   loss, and then binds it. Safe narrowing becomes a test, not a hope.
/// - `switch` accepts `long`, `float`, `double` and `boolean` selectors, and primitive type
///   patterns with guards (`case int s when s >= 90`).
/// - Record patterns can narrow components: `case Reading(int exact)` matches a
///   `Reading(double value)` only when the value is a whole number in `int` range.
///
/// This completes the "patterns everywhere" story: primitives and references follow the
/// same rules, which Valhalla's value types will rely on.
///
/// ## Why it's still in preview after five rounds
///
/// Exact-conversion semantics touch corner cases (`-0.0`, `NaN`, `float` → `int` rounding,
/// boxing and unboxing interplay). Five previews means the design is being shaken out, not
/// abandoned. Don't use it in production code until it's final.
public final class PrimitivePatterns {

    private PrimitivePatterns() {}

    /// Safe narrowing: empty instead of a silently wrapped value.
    public static OptionalInt toIntExactly(long minorUnits) {
        return minorUnits instanceof int i ? OptionalInt.of(i) : OptionalInt.empty();
    }

    /// A primitive `switch` with type patterns and guards. Still exhaustive: the last case
    /// (`int s`) is unconditional.
    public static String riskBand(int score) {
        return switch (score) {
            case 0 -> "NO_SIGNAL";
            case int s when s >= 90 -> "BLOCK";
            case int s when s >= 60 -> "REVIEW";
            case int s -> "ALLOW";
        };
    }

    /// `switch` on a `double` selector: not allowed at all in final Java.
    public static String fxRateSanity(double rate) {
        return switch (rate) {
            case double r when Double.isNaN(r) || r <= 0 -> "INVALID";
            case double r when r > 1_000 -> "SUSPICIOUS";
            case double r -> "OK";
        };
    }

    /// `switch` on `boolean`, exhaustive with just two constants.
    public static String flag(boolean enabled) {
        return switch (enabled) {
            case true -> "ON";
            case false -> "OFF";
        };
    }

    /// A sensor-style reading delivered as `double`.
    public record Reading(double value) {}

    /// Record pattern with a narrowing primitive component pattern.
    public static String describe(Reading reading) {
        return switch (reading) {
            case Reading(int exact) -> "whole number " + exact;
            case Reading(double d) -> "fractional " + d;
        };
    }
}

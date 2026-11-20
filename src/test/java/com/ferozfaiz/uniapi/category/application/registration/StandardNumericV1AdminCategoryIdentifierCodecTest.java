package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StandardNumericV1AdminCategoryIdentifierCodecTest {

  private final AdminCategoryIdentifierCodec<Long> codec =
    new StandardNumericV1AdminCategoryIdentifierCodec();

  @Test
  void reportsTheExactNumericLongProfile() {
    assertThat(codec.identifierProfile()).isSameAs(AdminCategoryIdentifierProfile.NUMERIC_LONG_V1);
  }

  @Test
  void parsesAndSerializesTheCanonicalPositiveBoundaries() {
    assertThat(codec.parse("1")).isEqualTo(1L);
    assertThat(codec.serialize(1L)).isEqualTo("1");
    assertThat(codec.parse(Long.toString(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);
    assertThat(codec.serialize(Long.MAX_VALUE)).isEqualTo(Long.toString(Long.MAX_VALUE));
  }

  @Test
  void roundTripsCanonicalPositiveLongIdentifiersWithoutNormalization() {
    String canonical = "8675309";

    assertThat(codec.serialize(codec.parse(canonical))).isEqualTo(canonical);
  }

  @ParameterizedTest(name = "invalid numeric identifier [{index}]")
  @MethodSource("invalidNumericText")
  void rejectsEveryNoncanonicalMalformedAndOverflowingNumericText(String value) {
    assertInvalidIdentifier(() -> codec.parse(value));
  }

  @Test
  void rejectsNullZeroAndNegativeTypedIdentifiers() {
    assertInvalidIdentifier(() -> codec.serialize(null));
    assertInvalidIdentifier(() -> codec.serialize(0L));
    assertInvalidIdentifier(() -> codec.serialize(-1L));
    assertInvalidIdentifier(() -> codec.serialize(Long.MIN_VALUE));
  }

  private static Stream<String> invalidNumericText() {
    return Stream.concat(
      Stream.of((String) null),
      Stream.of(
        "",
        " ",
        "0",
        "-1",
        "+1",
        "01",
        " 1",
        "1 ",
        "1.0",
        "1_000",
        "١",
        "9223372036854775808",
        "9".repeat(100)
      )
    );
  }

  private static void assertInvalidIdentifier(Executable executable) {
    AdminCategoryRegistrationValidationException failure = assertThrows(
      AdminCategoryRegistrationValidationException.class,
      executable
    );

    assertThat(failure.diagnostic().safeSystemKey()).isEqualTo("<unavailable>");
    assertThat(failure.diagnostic().category()).isSameAs(
      AdminCategoryValidationCategory.INVALID_IDENTIFIER
    );
    assertThat(failure.diagnostic().component()).contains(
      AdminCategoryValidationComponent.IDENTIFIER_CODEC
    );
    assertThat(failure.diagnostic().capability()).isEmpty();
    assertThat(failure.diagnostic().policyReference()).isEmpty();
  }
}

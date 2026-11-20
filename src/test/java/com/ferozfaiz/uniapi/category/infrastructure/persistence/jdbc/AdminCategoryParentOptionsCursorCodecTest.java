package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminCategoryParentOptionsCursorCodecTest {

  private static final String DESCRIPTOR_CURSOR_LITERAL =
    "MQlkZXNjcmlwdG9yLWEJd29qR3pNSXp2UE5TSWdkSjgxSDBfQzJoNVRDQVpjNFQ3MWR4cXNwRW50NAkyCXRydWUJWjNKaGNHaHBZM01nWTJGeVpITQk0NA";

  private final AdminCategoryParentOptionsCursorCodec codec =
    new AdminCategoryParentOptionsCursorCodec();

  @Test
  void literalDescriptorCursorRemainsByteCompatible() {
    var context = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      "gpu",
      true,
      9L,
      List.of(2L, 5L)
    );
    var position = new AdminCategoryParentOptionsCursorCodec.Position(
      2,
      true,
      "graphics cards",
      44L
    );

    assertThat(codec.encode("descriptor-a", context, position)).isEqualTo(
      DESCRIPTOR_CURSOR_LITERAL
    );
    assertThat(codec.decode("descriptor-a", context, DESCRIPTOR_CURSOR_LITERAL)).isEqualTo(
      position
    );
    assertThat(decodedPayload(DESCRIPTOR_CURSOR_LITERAL).split("\\t", -1)).containsExactly(
      "1",
      "descriptor-a",
      "wojGzMIzvPNSIgdJ81H0_C2h5TCAZc4T71dxqspEnt4",
      "2",
      "true",
      "Z3JhcGhpY3MgY2FyZHM",
      "44"
    );
    assertCursorInvalid(DESCRIPTOR_CURSOR_LITERAL, () ->
      codec.decode("descriptor-b", context, DESCRIPTOR_CURSOR_LITERAL)
    );
    assertCursorInvalid(DESCRIPTOR_CURSOR_LITERAL, () ->
      codec.decode(
        "descriptor-a",
        new AdminCategoryParentOptionsCursorCodec.RequestContext("cpu", true, 9L, List.of(2L, 5L)),
        DESCRIPTOR_CURSOR_LITERAL
      )
    );
  }

  @Test
  void encodesUrlSafeDeterministicCursorForSameContext() {
    var context = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      "gpu",
      true,
      9L,
      List.of(5L, 2L)
    );
    var position = new AdminCategoryParentOptionsCursorCodec.Position(
      2,
      true,
      "graphics cards",
      44L
    );

    String first = codec.encode("data", context, position);
    String second = codec.encode("data", context, position);

    assertThat(first).isEqualTo(second);
    assertThat(first).doesNotContain("+", "/", "=");
    assertThat(codec.decode("data", context, first)).isEqualTo(position);

    String[] payload = decodedPayload(first).split("\\t", -1);
    assertThat(payload).hasSize(7);
    assertThat(payload[0]).isEqualTo("1");
    assertThat(payload[1]).isEqualTo("data");
  }

  @Test
  void rejectsInvalidEncodingVersionSystemAndContext() {
    var context = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      "gpu",
      false,
      null,
      List.of()
    );
    String cursor = codec.encode(
      "data",
      context,
      new AdminCategoryParentOptionsCursorCodec.Position(1, true, "gpu", 10L)
    );
    String malformedOuterBase64 = "not-base64!";
    String wrongVersionCursor = encodedPayload("2\tdata\tbad\t1\ttrue\tgpu\t10");

    assertCursorInvalid(malformedOuterBase64, () ->
      codec.decode("data", context, malformedOuterBase64)
    );
    assertCursorInvalid(cursor, () -> codec.decode("blog", context, cursor));
    assertCursorInvalid(cursor, () ->
      codec.decode(
        "data",
        new AdminCategoryParentOptionsCursorCodec.RequestContext("cpu", false, null, List.of()),
        cursor
      )
    );
    assertCursorInvalid(wrongVersionCursor, () ->
      codec.decode("data", context, wrongVersionCursor)
    );
  }

  @Test
  void rejectsMalformedEnvelopeAndScalarsWithoutEchoingRawCursorOrCause() {
    var context = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      "gpu",
      true,
      9L,
      List.of(2L, 5L)
    );
    String[] literalParts = decodedPayload(DESCRIPTOR_CURSOR_LITERAL).split("\\t", -1);
    String tooFewFields = encodedPayload(
      String.join("\t", Arrays.copyOf(literalParts, literalParts.length - 1))
    );
    String tooManyFields = encodedPayload(decodedPayload(DESCRIPTOR_CURSOR_LITERAL) + "\textra");
    String nonnumericSearchRank = replacePayloadPart(DESCRIPTOR_CURSOR_LITERAL, 3, "not-a-rank");
    String nonnumericId = replacePayloadPart(DESCRIPTOR_CURSOR_LITERAL, 6, "not-an-id");
    String invalidNormalizedNameBase64 = replacePayloadPart(
      DESCRIPTOR_CURSOR_LITERAL,
      5,
      "not-base64!"
    );
    String blankDecodedName = replacePayloadPart(DESCRIPTOR_CURSOR_LITERAL, 5, "");

    assertCursorInvalid(tooFewFields, () -> codec.decode("descriptor-a", context, tooFewFields));
    assertCursorInvalid(tooManyFields, () -> codec.decode("descriptor-a", context, tooManyFields));
    assertCursorInvalid(nonnumericSearchRank, () ->
      codec.decode("descriptor-a", context, nonnumericSearchRank)
    );
    assertCursorInvalid(nonnumericId, () -> codec.decode("descriptor-a", context, nonnumericId));
    assertCursorInvalid(invalidNormalizedNameBase64, () ->
      codec.decode("descriptor-a", context, invalidNormalizedNameBase64)
    );
    assertCursorInvalid(blankDecodedName, () ->
      codec.decode("descriptor-a", context, blankDecodedName)
    );
  }

  @Test
  void bindsNormalizedSearchInactiveSubtreeAndIncludeMembership() {
    var encodedContext = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      "  GPU  ",
      true,
      9L,
      List.of(5L, 2L, 5L)
    );
    var position = new AdminCategoryParentOptionsCursorCodec.Position(
      3,
      true,
      "graphics cards",
      44L
    );
    String cursor = codec.encode("data", encodedContext, position);

    var equivalentContext = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      "gpu",
      true,
      9L,
      List.of(2L, 5L)
    );
    assertThat(codec.decode("data", equivalentContext, cursor)).isEqualTo(position);

    assertCursorInvalid(cursor, () ->
      codec.decode(
        "data",
        new AdminCategoryParentOptionsCursorCodec.RequestContext("cpu", true, 9L, List.of(2L, 5L)),
        cursor
      )
    );
    assertCursorInvalid(cursor, () ->
      codec.decode(
        "data",
        new AdminCategoryParentOptionsCursorCodec.RequestContext("gpu", false, 9L, List.of(2L, 5L)),
        cursor
      )
    );
    assertCursorInvalid(cursor, () ->
      codec.decode(
        "data",
        new AdminCategoryParentOptionsCursorCodec.RequestContext("gpu", true, 10L, List.of(2L, 5L)),
        cursor
      )
    );
    assertCursorInvalid(cursor, () ->
      codec.decode(
        "data",
        new AdminCategoryParentOptionsCursorCodec.RequestContext("gpu", true, 9L, List.of(2L, 6L)),
        cursor
      )
    );
  }

  @Test
  void leavesLimitUnboundFromTheRequestContext() {
    assertThat(
      Arrays.stream(
        AdminCategoryParentOptionsCursorCodec.RequestContext.class.getRecordComponents()
      ).map(component -> component.getName())
    ).containsExactly("q", "includeInactive", "excludeSubtreeId", "includeIds");
  }

  @Test
  void normalizesNullAndBlankSearchContextEquivalently() {
    var nullQueryContext = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      null,
      true,
      null,
      List.of()
    );
    var position = new AdminCategoryParentOptionsCursorCodec.Position(0, true, "root", 44L);
    String cursor = codec.encode("descriptor-a", nullQueryContext, position);

    var blankQueryContext = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      "   ",
      true,
      null,
      List.of()
    );
    assertThat(codec.decode("descriptor-a", blankQueryContext, cursor)).isEqualTo(position);
  }

  @Test
  void preservesTheCompleteTieTupleAndAcceptsFabricatedStalePositions() {
    var context = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      "gpu",
      true,
      null,
      List.of()
    );
    var firstTie = new AdminCategoryParentOptionsCursorCodec.Position(7, true, "same name", 100L);
    var secondTie = new AdminCategoryParentOptionsCursorCodec.Position(7, true, "same name", 101L);
    var fabricatedStalePosition = new AdminCategoryParentOptionsCursorCodec.Position(
      Integer.MAX_VALUE,
      false,
      "deleted row",
      Long.MAX_VALUE
    );

    String firstCursor = codec.encode("data", context, firstTie);
    String secondCursor = codec.encode("data", context, secondTie);
    String staleCursor = codec.encode("data", context, fabricatedStalePosition);

    assertThat(firstCursor).isNotEqualTo(secondCursor);
    assertThat(codec.decode("data", context, firstCursor)).isEqualTo(firstTie);
    assertThat(codec.decode("data", context, secondCursor)).isEqualTo(secondTie);
    assertThat(codec.decode("data", context, staleCursor)).isEqualTo(fabricatedStalePosition);
  }

  @Test
  void preservesPermissiveBooleanDecodingForNonTruePositionText() {
    var context = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      null,
      true,
      null,
      List.of()
    );
    var position = new AdminCategoryParentOptionsCursorCodec.Position(
      0,
      true,
      "graphics cards",
      44L
    );
    String cursor = codec.encode("data", context, position);
    String nonBooleanCursor = replacePayloadPart(cursor, 4, "not-true");

    assertThat(codec.decode("data", context, nonBooleanCursor)).isEqualTo(
      new AdminCategoryParentOptionsCursorCodec.Position(
        position.searchRank(),
        false,
        position.normalizedName(),
        position.id()
      )
    );
  }

  @Test
  void rejectsInvalidPositionTuplesWithoutEchoingTheRawCursor() {
    var context = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      "gpu",
      true,
      null,
      List.of()
    );
    String cursor = codec.encode(
      "data",
      context,
      new AdminCategoryParentOptionsCursorCodec.Position(1, true, "gpu", 10L)
    );

    String negativeRankCursor = replacePayloadPart(cursor, 3, "-1");
    String nonpositiveIdCursor = replacePayloadPart(cursor, 6, "0");

    assertCursorInvalid(negativeRankCursor, () ->
      codec.decode("data", context, negativeRankCursor)
    );
    assertCursorInvalid(nonpositiveIdCursor, () ->
      codec.decode("data", context, nonpositiveIdCursor)
    );

    String rawCursor = "secret-parent-options-token!";
    assertCursorInvalid(rawCursor, () -> codec.decode("data", context, rawCursor));
  }

  private static void assertCursorInvalid(String rawCursor, ThrowingCallable callable) {
    assertThatThrownBy(callable::call).isInstanceOfSatisfying(
      AdminCategoryException.class,
      failure -> {
        assertThat(failure.code()).isEqualTo("CATEGORY_PARENT_OPTIONS_CURSOR_INVALID");
        assertThat(failure.getMessage()).isEqualTo("parent-options cursor is invalid");
        assertThat(failure.getMessage()).doesNotContain(rawCursor);
        assertThat(failure.messageArgs()).isEmpty();
        assertThat(failure.getCause()).isNull();
      }
    );
  }

  private static String decodedPayload(String cursor) {
    return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
  }

  private static String encodedPayload(String payload) {
    return Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  private static String replacePayloadPart(String cursor, int index, String replacement) {
    String[] parts = decodedPayload(cursor).split("\\t", -1);
    parts[index] = replacement;
    return Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(String.join("\t", parts).getBytes(StandardCharsets.UTF_8));
  }

  @FunctionalInterface
  private interface ThrowingCallable {
    void call();
  }
}

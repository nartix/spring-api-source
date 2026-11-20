package com.ferozfaiz.uniapi.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextTest {

  @Test
  void trimToNullReturnsNullForMissingOrBlankText() {
    assertThat(Text.trimToNull(null)).isNull();
    assertThat(Text.trimToNull("")).isNull();
    assertThat(Text.trimToNull("   ")).isNull();
  }

  @Test
  void trimToNullReturnsTrimmedText() {
    assertThat(Text.trimToNull("  feroz  ")).isEqualTo("feroz");
  }
}

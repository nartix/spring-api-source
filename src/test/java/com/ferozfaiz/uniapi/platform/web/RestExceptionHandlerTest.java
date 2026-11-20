package com.ferozfaiz.uniapi.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;

class RestExceptionHandlerTest {

  private final RecordingMessageResolver messages = new RecordingMessageResolver();
  private final RestExceptionHandler handler = new RestExceptionHandler(messages);

  @Test
  void untypedIllegalArgumentUsesGenericMessageWithoutExposingInternalDetail() {
    String internalDetail = "database password is internal-only";
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/example");
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages/platform");
    RestExceptionHandler frenchHandler = new RestExceptionHandler((code, args) ->
      source.getMessage(code, args, Locale.FRENCH)
    );

    var response = frenchHandler.handleIllegalArgument(
      new IllegalArgumentException(internalDetail),
      request
    );

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    assertThat(response.getBody().path()).isEqualTo("/api/v1/example");
    assertThat(response.getBody().message())
      .isEqualTo("La requete est invalide")
      .doesNotContain(internalDetail);
  }

  @Test
  void localizedIllegalArgumentKeepsItsSpecificMessageKeyAndArguments() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/example");

    var response = handler.handleIllegalArgument(
      new InvalidRequestException("shared.request.invalid", "internal fallback", "field"),
      request
    );

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("resolved:shared.request.invalid:[field]");
    assertThat(messages.lastCode).isEqualTo("shared.request.invalid");
    assertThat(messages.lastArguments).containsExactly("field");
  }

  private static final class RecordingMessageResolver implements MessageResolver {

    private String lastCode;
    private Object[] lastArguments = new Object[0];

    @Override
    public String get(String code, Object... args) {
      lastCode = code;
      lastArguments = args.clone();
      return args.length == 0
        ? "resolved:" + code
        : "resolved:" + code + ":" + Arrays.toString(args);
    }
  }
}

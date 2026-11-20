package com.ferozfaiz.uniapi.platform.i18n;

import com.ferozfaiz.uniapi.shared.i18n.MessageResolver;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SpringMessageResolver implements MessageResolver {

  private final MessageSource messages;

  SpringMessageResolver(MessageSource messages) {
    this.messages = messages;
  }

  @Override
  public String get(String code, Object... args) {
    return messages.getMessage(code, args, code, LocaleContextHolder.getLocale());
  }
}

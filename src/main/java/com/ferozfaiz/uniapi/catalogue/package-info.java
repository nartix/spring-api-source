@org.springframework.modulith.ApplicationModule(
  displayName = "Catalogue",
  allowedDependencies = {
    "category :: registration",
    "category :: command",
    "category :: query",
    "category :: model",
    "category :: error",
    "category :: policy",
    "platform :: hierarchy",
    "shared :: error",
    "shared :: i18n",
    "shared :: pagination",
    "shared :: text",
  }
)
package com.ferozfaiz.uniapi.catalogue;

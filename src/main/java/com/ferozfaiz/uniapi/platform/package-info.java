@org.springframework.modulith.ApplicationModule(
  displayName = "Platform",
  allowedDependencies = {
    "iam :: api",
    "iam :: auth",
    "session :: api",
    "shared :: error",
    "shared :: i18n",
  }
)
package com.ferozfaiz.uniapi.platform;

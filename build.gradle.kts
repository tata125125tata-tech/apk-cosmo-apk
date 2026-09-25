// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// Automatically restore debug.keystore from debug.keystore.base64 in clean environments like CI
val debugKeystore = file("debug.keystore")
val debugKeystoreB64 = file("debug.keystore.base64")
if (!debugKeystore.exists() && debugKeystoreB64.exists()) {
  try {
    val decoded = java.util.Base64.getDecoder().decode(debugKeystoreB64.readText().trim())
    debugKeystore.writeBytes(decoded)
  } catch (e: Exception) {
    logger.warn("Could not decode debug.keystore.base64: ${e.message}")
  }
}


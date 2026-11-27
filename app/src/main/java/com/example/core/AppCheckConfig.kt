package com.example.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

object AppCheckDiagnostics {
    @Volatile
    var statusLabel: String = "Initializing..."
        internal set

    @Volatile
    var providerName: String = "Pending"
        internal set
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() ensures DebugAppCheckProviderFactory reads the secret immediately
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
                AppCheckDiagnostics.statusLabel = "Active (Debug Token Injected)"
            } else {
                Log.w(TAG, "No debug token found in Intent extra; Debug provider will emit token in Logcat.")
                AppCheckDiagnostics.statusLabel = "Active (Debug Provider)"
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            AppCheckDiagnostics.providerName = "DebugAppCheckProviderFactory"
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    AppCheckDiagnostics.providerName = "RecaptchaAppCheckProviderFactory"
                    AppCheckDiagnostics.statusLabel = "Active (Release Attestation)"
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    AppCheckDiagnostics.statusLabel = "Missing release site key"
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                AppCheckDiagnostics.statusLabel = "Release key resource absent"
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        AppCheckDiagnostics.statusLabel = "Init error: ${e.localizedMessage ?: "unknown"}"
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

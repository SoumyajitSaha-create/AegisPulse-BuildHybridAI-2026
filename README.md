# AegisPulse — Offline-First Emergency, First-Aid & Field Triage Companion

Built for **Google's #BuildHybridAI 2026** hackathon, **AegisPulse** is a native Android emergency first-aid, multimodal hazard inspection, and field triage companion engineered around the **Firebase AI Logic Hybrid Inference SDK** (on-device **Gemini Nano** via AICore + cloud **Gemini 3.5 Flash-Lite** fallback), **Firebase App Check**, **Cloud Firestore** (offline persistent cache), **Firebase Authentication** (Google Sign-In via Jetpack Credential Manager), and **Firebase Remote Config**.

---

## 1. Problem Solved & Value Proposition

During disasters, remote trail injuries, severe weather outages, or sudden medical emergencies, users face two competing realities:
1. **Connectivity is unreliable or absent**, yet guidance must arrive in milliseconds without waiting on a cell tower.
2. **Sensitive medical records (ICE profiles)** shouldn't leave the device silently, whereas **complex visual hazard scans** and **multi-incident responder handoffs** benefit from full cloud multimodal reasoning.

**AegisPulse** solves both by orchestrating real-time hybrid AI routing transparently:
- **Zero-friction emergency triage**: Instant access to 6 high-contrast crisis checklists, voice dictation, hands-free offline Text-to-Speech readout, and an Offline SOS Phrase Translator with full-screen high-contrast flashcards.
- **Verifiable hybrid provenance on every response**: Every AI card pins a live telemetry badge showing the real `response.inferenceSource` (`ON_DEVICE` vs. `IN_CLOUD`), actual measured wall-clock latency (`ms`), active model, and tappable routing rationale.
- **Privacy-first ICE Medical Vault**: Stores personal medical data strictly in local Jetpack DataStore and enforces `InferenceMode.ONLY_ON_DEVICE`, blocking cloud transmission unless the user grants explicit one-time consent.
- **Interactive Judge's Hybrid Lab**: Lets evaluators inspect AICore/Gemini Nano availability, trigger on-device model downloads, refresh live Remote Config routing thresholds, toggle manual `InferenceMode` overrides, and run side-by-side short vs. long prompt routing benchmarks.

---

## 2. Firebase AI Logic Hybrid Architecture

```text
User Action (Triage / SOS / Scene Scan / ICE Vault / Responder Brief / Hybrid Lab)
                                     │
                                     ▼
                         ┌───────────────────────┐
                         │    HybridAIRouter     │
                         │  (Firebase.ai SDK)    │
                         └───────────┬───────────┘
         ┌───────────────────────────┼───────────────────────────┐
         ▼                           ▼                           ▼
┌──────────────────┐      ┌─────────────────────┐     ┌─────────────────────┐
│ On-Device Probe  │      │   Task-Aware Mode   │     │   Remote Config     │
│ checkStatus()    │      │ PREFER_ON_DEVICE    │     │ cloud_fallback_model│
│ download() Flow  │      │ ONLY_ON_DEVICE      │     │ threshold_chars     │
└────────┬─────────┘      │ PREFER_IN_CLOUD     │     │ disclaimer_text     │
         │                │ ONLY_IN_CLOUD       │     └──────────┬──────────┘
         │                └──────────┬──────────┘                │
         └───────────────────────────┼───────────────────────────┘
                                     ▼
                ┌─────────────────────────────────────────┐
                │ Firebase AI Logic + Firebase App Check  │
                │  • Local: Gemini Nano (AICore)          │
                │  • Cloud: Gemini 3.5 Flash-Lite (Fallback)│
                └────────────────────┬────────────────────┘
                                     ▼
            Real Telemetry: response.inferenceSource + Latency (ms)
            Persisted Locally (DataStore) + Synced (Cloud Firestore)
```

### How All Four `InferenceMode` Options Are Used
| Feature | `InferenceMode` | Behavior & Fallback Strategy |
| :--- | :--- | :--- |
| **Field Triage, Voice Triage, Offline SOS Translator** | `PREFER_ON_DEVICE` | Runs locally on Gemini Nano with 0 bytes sent and streams via `generateContentStream`; automatically falls back to cloud `gemini-3.5-flash-lite` (with automatic 2s backoff retry / `gemini-3.1-flash-lite` fallback on HTTP 429) when AICore is unavailable or when prompt length exceeds the Remote Config `long_context_cloud_threshold_chars` threshold (default `1200`). |
| **Private ICE Medical Vault** | `ONLY_ON_DEVICE` | Enforces zero cloud egress for allergies, blood type, medications, and emergency contacts. If Gemini Nano is unavailable on the device/emulator, blocks cloud transmission and displays a transparent consent dialog before allowing a one-time fallback. |
| **Multimodal Scene Scan (Hazard & Medicine Labels)** | `PREFER_IN_CLOUD` | Sends safely downscaled camera/Photo Picker images via `content { image(bitmap); text(prompt) }` for cloud vision analysis. When offline, displays a non-blocking Offline Fallback card offering immediate `PREFER_ON_DEVICE` text-guided assessment. |
| **Multi-Incident Responder Handoff Brief** | `ONLY_IN_CLOUD` | Synthesizes multiple Cloud Firestore incident logs into a structured Situation / Timeline / Actions Taken / Priority Needs report with one-tap Android Share Sheet export. |

### Firebase Services Integration
- **Firebase AI Logic (`firebase-ai` + `firebase-ai-ondevice`)**: Managed by Firebase Android BoM (`35.1.0`), initialized with `Firebase.ai(backend = GenerativeBackend.googleAI())` and `OnDeviceConfig`.
- **Firebase App Check**: Wired inside `MainActivity.onCreate()` via `configureAppCheck(this, intent)` before UI/ViewModels initialize—using `DebugAppCheckProviderFactory` (with synchronous `.commit()` debug token persistence) in debug builds and `RecaptchaAppCheckProviderFactory` in release builds (Play Integrity can also be enabled in production via Firebase Console).
- **Cloud Firestore (Offline Persistence)**: Targets the provisioned named database (`R.string.firestore_database_id`) with persistent 50MB local cache, live `Synced` vs. `Pending Offline Sync` status chips (`metadata.hasPendingWrites()`), `"platform": "ANDROID"` tagging on every incident for multi-platform Web Command Center display, and hardened per-user security rules (`firestore.rules`) validated with local emulator tests (`firestore.test.js`).
- **Firebase Authentication**: Zero-friction emergency triage on launch, with **Google Sign-In via Jetpack Credential Manager** (`GetSignInWithGoogleOption` + silent `GetGoogleIdOption`) gating all Firestore incident sync and responder brief features.
- **Firebase Remote Config**: Dynamically controls `cloud_fallback_model`, `hybrid_default_mode`, `long_context_cloud_threshold_chars`, and `emergency_disclaimer_text`, refreshable live in the Hybrid Lab tab.

---

## 3. Setup & Running Locally

### Prerequisites
- Android Studio (Ladybug / Meerkat or newer) with JDK 11+
- Android SDK 36 (`minSdk = 26`, `targetSdk = 36`)
- A Firebase project with **Firebase AI Logic (Gemini Developer API)**, **Firebase App Check**, **Cloud Firestore**, **Firebase Auth (Google provider)**, and **Firebase Remote Config** enabled.

### Step-by-Step Setup
1. **Add Firebase Configuration**:
   - Place your Firebase `google-services.json` into `app/google-services.json`.
   - Ensure `app/src/main/res/values/firebase_applet_config.xml` defines `firestore_database_id` (your Firestore database ID) and `recaptcha_site_key` (for release builds).
2. **Register App Check Debug Token**:
   - Run the debug build on an emulator or physical device.
   - Check Logcat for `DebugAppCheckProvider` to obtain your debug secret UUID, and register it in **Firebase Console → App Check → Apps → Manage debug tokens** (or pass `FIREBASE_APPCHECK_DEBUG_TOKEN` via launch intent extras). Never commit debug secrets to source control.
3. **Deploy Firestore Security Rules**:
   - Validate rules locally against the Firestore emulator:
     ```bash
     FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
     ```
   - Deploy `firestore.rules` to your Firebase project.
4. **Build & Run**:
   ```bash
   gradle :app:assembleDebug
   ```
   On Pixel / AICore-enabled Android devices with Gemini Nano support, open the **Hybrid Lab** tab to verify `AVAILABLE` / `DOWNLOADABLE` status and test zero-egress on-device execution. On standard emulators without AICore, AegisPulse cleanly reports `UNAVAILABLE (Cloud Fallback Active)` and executes real cloud Gemini inference seamlessly.

---

## 4. Safety Disclaimer & License

AegisPulse is an AI-assisted first-aid and preparedness companion and **does not replace professional medical advice, diagnosis, treatment, or official emergency services (911 / 112)**.

Licensed under the **Apache License, Version 2.0** — see [LICENSE](LICENSE) for full terms.

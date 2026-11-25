# NULLARCH Launcher + Assistant — Architecture Decision

**Status:** Agreed direction; implementation not started.  
**Repository:** `Stahij/jarvis-android`  
**Default branch:** `main`

## 1. Product decision

NULLARCH will consist of two independently installable Android applications:

- **NULLARCH Launcher** — a standalone home-screen / launcher experience. It must remain usable when Assistant is absent, stopped, crashed, or being updated.
- **NULLARCH Assistant** — a standalone assistant application with its own chat, local model, conversation history, settings, and lifecycle. It must remain usable without Launcher.

They may be installed separately from Google Play. Neither app may force-install the other.

If the companion app is absent:
- Launcher may offer to install Assistant when the user invokes an Assistant-only action (for example, double-tapping the core).
- Assistant may offer to install Launcher where relevant.
- These are optional user-approved Play Store links, never automatic installation.

When both are installed, they can discover each other and exchange explicitly supported commands or shared data through a secured IPC contract. Failure or shutdown of either app must not take down the other.

## 2. Repository/module layout

Target Gradle structure:

```text
:launcher   -> NULLARCH Launcher APK
:assistant  -> NULLARCH Assistant APK
:core       -> shared Android/Kotlin library (contracts, models, IPC helpers)
```

The current `:app` module is the legacy combined application. It must be migrated deliberately; do not simply delete or replace it before the two new application modules are buildable and data/signing migration is understood.

Each application owns its:
- `applicationId`, manifest, launcher Activity and task stack;
- process and lifecycle;
- preferences and app-specific storage;
- Play Store listing, release track, versioning and APK/AAB artifact.

The shared `:core` module is a library, not a third installable app. Keep it limited to stable shared contracts, data models, validation and IPC helpers. Do not put UI ownership or one app's lifecycle into the shared module.

## 3. Application identity, upgrades and user data

**Critical migration constraint:** the existing installed application identity and signing certificate must not be broken.

The existing combined app contains Assistant functionality (local model, chat and history). The recommended migration is to preserve the current `com.stahij.jarvis` applicationId and its existing signing identity for **NULLARCH Assistant**, subject to verification against the actual current Gradle/manifest/release configuration before implementation.

NULLARCH Launcher should receive a new, distinct applicationId (provisional example: `com.stahij.nullarch.launcher`; final value must be approved before coding).

Do not assume Android can transfer private app data between different applicationIds. Plan and test any required export/import explicitly. Preserve the Assistant's model files, chat history, settings, update mechanism and in-place upgrade path. Never change the release keystore or signing identity casually.

## 4. Inter-app communication and security

Use Android IPC, not shared private storage or hidden cross-process coupling.

Preferred approach:
- explicit Intents for small, one-shot commands and navigation;
- a bound service for request/response operations or a controlled command channel;
- a ContentProvider only if structured data access is genuinely needed.

Security requirements:
- define versioned request/response contracts in `:core`;
- protect exported components with signature-level permissions where feasible;
- validate caller identity/signing certificate and every incoming action;
- expose only the minimum required operations and data;
- handle missing, outdated, stopped, or incompatible companion apps gracefully;
- use timeouts and failure-safe fallbacks; never block Launcher startup on Assistant.

Do not expose internal chat databases, model files, credentials, or unrestricted filesystem access to Launcher.

## 5. Discovery and installation

Each app may check whether its companion is installed using PackageManager with narrowly declared package visibility queries. It should validate the installed package before trusting it.

Installation prompts must:
- be contextual and optional;
- open the correct Google Play listing (or a clearly documented fallback if not published);
- never attempt silent installation.

## 6. Lifecycle and reliability rules

- Launcher must start, render, handle gestures, show app shortcuts and remain functional with Assistant not installed.
- Assistant must launch and provide chat/model functionality without Launcher installed.
- Stopping, force-stopping, crashing, updating or uninstalling one app must not crash or disable the other.
- Cross-app calls must be asynchronous or bounded and must fail gracefully.
- Neither app may use the other as a required foreground/background service.

Android may independently reclaim either process under memory pressure; the architecture must tolerate that rather than promise that either process is permanently resident.

## 7. Migration sequence (planned, not yet authorized as implementation)

1. Audit the current `:app` module: applicationId, signing, data locations, local model storage, chat/history, updater, manifest and dependencies.
2. Confirm which legacy identity and signing configuration will remain with Assistant. The current recommendation is Assistant.
3. Extract stable IPC/data contracts into `:core` without changing runtime behavior.
4. Create the new Launcher application module and move launcher-only UI/runtime into it. Keep the existing combined app installable during development.
5. Create/establish the Assistant application module while preserving the legacy applicationId, signing identity and user data.
6. Implement and test secured discovery/IPC in both directions, including companion absent, old version, stopped process and permission-denied cases.
7. Build and test both APKs independently and together on a physical device. Verify update-over-install and data retention.
8. Publish separate Play Store listings and release pipelines only after signing, package IDs and migration tests pass.

## 8. Non-negotiable guardrails

- One repository; work on `main` unless the owner explicitly approves otherwise. Do not create extra branches without permission.
- Do not begin destructive module splitting until the migration plan and package/signing decisions are verified.
- Do not remove or regress the existing working plasma core, launcher interactions, Assistant chat, local model, history or updater as collateral damage.
- Keep Launcher visually and operationally independent from Assistant.
- Treat this document as the durable architecture decision; update it when the owner explicitly changes a decision.

# Jarvis Launcher: 3D Workspace, Elita Language and Team Space

Status: concept and implementation roadmap  
Target repository: `Stahij/jarvis-android`  
Current baseline: `v1.6.0`

## Product principles

- The workspace starts empty. Installing an app never places it on the desktop automatically.
- The user explicitly adds apps, folders and widgets from a drawer or creation flow.
- The Jarvis core is the only workspace motion control: tap to freeze the orbital motion and return objects to their saved coordinates; tap again to resume.
- No on-screen page arrows or separate freeze button. Horizontal swipes change pages; a swipe beyond the last page creates a new page.
- The Android system owns Home, Recents and Back navigation gestures. The launcher must not intercept the system gesture regions.
- Saved positions are persistent user data. Animation and physics must never overwrite them.

## 3D workspace roadmap

### Stage 1 — Workspace behavior
- Empty first-run workspace and explicit app placement.
- User-controlled pages with automatic page creation.
- Swipe-based page navigation, no arrow controls.
- Core tap toggles motion.
- Persist positions independently from transient animation state.

### Stage 2 — Real 3D renderer
- Evaluate SceneView / Google Filament integration against the existing Android Views application and Kotlin 2.0.21 toolchain.
- SceneView 4.x currently exposes a Jetpack Compose Android API; integration may require Compose dependencies and a ComposeView host. Do not add the dependency until a minimal build and device-performance spike succeeds.
- Alternative: use Filament Android bindings directly if Compose integration adds unacceptable complexity.
- Prototype a single core sphere, emissive materials, orbital rings, perspective camera and a small number of icon billboards.
- Keep a Canvas fallback until the 3D path is proven stable.

### Stage 3 — Interaction and physics
- Real depth, camera parallax and optional gyroscope input.
- App icons as selectable objects with saved 3D transforms.
- Collision/impulse behavior and a soft attraction toward assigned orbital zones.
- Physics state is transient; user-defined transforms remain authoritative.
- Adaptive quality/FPS, pause rendering in background and reduce work while idle.

### Stage 4 — Launcher completeness
- Folders, widgets, app drawer search and placement workflow.
- Drag between pages, page ordering and home-page selection.
- Gesture conflict testing on Xiaomi HyperOS / Android 13.

## Elita: original team language

Working name only. The goal is a designed, learnable language—not merely decorative glyphs.

Potential components:
- phonology and pronunciation rules;
- grammar and word-building rules;
- a growing dictionary with versioning;
- a consistent writing system and keyboard/input method;
- transliteration and translation tools;
- optional machine-readable grammar so Jarvis and other assistants can parse and generate it;
- ordinary-language fallback for accessibility and interoperability.

The language must be developed deliberately with human-readable documentation and tests. No secret or security-critical information should rely on it being obscure.

## Jarvis Team Space

A future in-launcher collaboration area for a development team:
- direct and group conversations;
- project channels (engineering, design, testing, releases);
- tasks, decisions and release notes linked to repository commits;
- file and image sharing;
- notifications and presence;
- optional Elita mode with inline translation and normal-language display;
- offline drafts and reliable synchronization.

### Architecture principles
- Keep the launcher usable without a team account or network connection.
- Use explicit authentication and role-based permissions.
- Encrypt transport and protect locally stored credentials.
- Do not couple the core launcher to a specific messaging backend.
- Evaluate a self-hostable backend only when collaboration becomes an active milestone.

## Performance and safety gates

- Preserve the existing application ID and release signing certificate for in-place updates.
- Keep the current main branch as the integration line; avoid unnecessary branches.
- Run CI after each meaningful change.
- Do not call a Canvas illustration “real 3D”.
- Do not ship a 3D renderer until it passes startup, background/foreground, memory, thermal and battery checks on the target Xiaomi Snapdragon 732G device.

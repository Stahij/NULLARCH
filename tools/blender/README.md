# Jarvis 3D core — Blender source

This folder contains a procedural Blender scene generator for the first Jarvis energy-core asset.

## Requirements
- Blender 4.x with the built-in glTF 2.0 exporter
- No paid add-ons or online generators

## Generate the GLB

From the repository root:

```bash
mkdir -p assets/3d
blender --background --python tools/blender/create_jarvis_core.py -- --output assets/3d/jarvis_core.glb
```

The script creates a blue energy sphere, a subtle inner glow, suspended sparks and broken organic plasma filaments. It intentionally does not create closed orbital rings. Materials use glTF-compatible Principled BSDF values; the filament curves are converted to meshes before export so they survive GLB export.

## Current integration status

The script is source for the Blender asset; it does not mean the GLB has already been generated or that Android is rendering it yet. The current launcher still uses its Canvas renderer. The next renderer spike must load this GLB in a real-time Android 3D view, then pass lifecycle, memory, thermal and frame-rate checks on the Xiaomi Snapdragon 732G before replacing the Canvas fallback.

The build container used for the current source work does not have Blender installed, so the scene export itself has not been executed or validated here.

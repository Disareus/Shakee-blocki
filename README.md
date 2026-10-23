# Shakee Blocki

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.1.2–26.3%20%28unified%29%20%7C%20NeoForge%201.21.1-brightgreen.svg)](https://minecraft.net/)
[![Platform](https://img.shields.io/badge/Platform-Fabric%20%7C%20NeoForge-blue.svg)](https://fabricmc.net/)
[![Side](https://img.shields.io/badge/Side-Client-purple.svg)](https://modrinth.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A lightweight, highly optimized client-side Fabric and NeoForge mod that adds juicy, physics-like wobbling, tilting, expanding, and bouncing animations to Minecraft blocks whenever you place or mine them.

---

## Features

### Block Breaking Animations
* **Progressive Intensity:** Blocks vibrate, tilt, and twist dynamically as breaking progress increases ($0\% \to 100\%$).
* **Squash & Stretch / Shrink:** Visual feedback adapts to breaking stress with configurable easing.
* **Smooth Cancellation:** If you stop mining midway, the block smoothly returns to rest instead of snapping back instantly.

### Block Placement Animations
* **Diverse Placement Modes:**
  * **Expand:** Blocks scale up smoothly with an optional elastic overshoot.
  * **Squash & Stretch:** Bouncy cartoonish physics impact upon landing.
  * **Fall:** Blocks drop down from above with gravity and settle into place.
  * **Spin:** Playful rotational twist into position.
  * **Tilt / Tilt & Expand:** Angle-based orientation relative to the interacted face.
  * **Material Based:** Automatically chooses the most fitting animation mode based on the block's sound/material (e.g. wood, stone, metal).
* **Player Momentum (Velocity Bias):** Blocks dynamically lean in the direction of your movement when placing on the run.
* **Neighbor Ripples (Shockwaves):** Placing a block triggers a gentle bounce wave across adjacent blocks.
* **Waterlogged Support:** Waterlogged blocks (slabs, stairs, fences) tilt seamlessly without leaving hollow air voids in water.

### Smart Filters & Custom Blacklist
* **Exclude Technical Blocks:** One-click toggle to bypass animations for redstone dust, repeaters, comparators, torches, buttons, levers, rails, and pistons.
* **Exclude Foliage & Plants:** Automatically skips flowers, saplings, crops, tall grass, and mushrooms.
* **Custom Block Blacklist:** Easily type or paste block IDs separated by commas or spaces (`dirt, stone, oak_sapling`) to exclude any vanilla or modded blocks.

### Performance & Optimization
* **Reusable runtime state:** Uses Fastutil primitive maps and reusable render states to keep animation and meshing overhead low.
* **Fast-Building Throttle:** Rapid building (bridging, holding right-click in creative) automatically throttles neighbor ripples, reducing chunk meshing load by ~85% and preventing FPS stutter.
* **Dynamic Hit Outlines:** The block selection box transforms smoothly with the block instead of popping into view abruptly.
* **Multi-Block Sync:** Double chests, beds, and doors animate in perfect harmony without splitting apart.

---

## Configuration

Shakee Blocki stores its settings in `.minecraft/config/shakee_blocki.json`.
* Enable/disable placement, breaking, or ripple animations independently.
* Customize durations, maximum tilt angles, rotation cycles, and easing curves.
* Config file location: `.minecraft/config/shakee_blocki.json`.

### Requirements
* **Cloth Config is required on all supported platforms** — the in-game configuration screen is built entirely on Cloth Config, so the mod will not launch without it (declared as a hard dependency).
* Fabric 1.21.11 — **Cloth Config 21.11.x (Fabric)** and **Mod Menu 17.x**.
* Fabric 26.1.2 / 26.3 — **Cloth Config 26.x (Fabric)** and a matching **Mod Menu** version.
* NeoForge 1.21.1 — **Cloth Config 15.x+ (NeoForge)**; the config screen is registered on the NeoForge Mods list when present.

---

## Project Structure

The project uses a clean multi-version architecture with a unified universal core:
* [`common/`](common/) — Universal animation math, configuration, and shared cross-version mixins.
* [`common26/`](common26/) — Shared 26.x render pipeline (renderer, mixins, submit adapter interface) used by the unified 26.x jar.
* [`fabric-1.21.11/`](fabric-1.21.11/) — Subproject for Minecraft 1.21.11 (Java 21, Loom-remap, `WorldRenderEvents`).
* [`fabric-26.1.2/`](fabric-26.1.2/) — Compat classes for Minecraft 26.1.x (`compat261`: legacy `renderHitOutline` mixin + 2-arg `submitMovingBlock` adapter). Also feeds the unified jar.
* [`fabric-26.2/`](fabric-26.2/) — Compile-time compatibility probe: verifies that the 26.3-variant code also compiles against Minecraft 26.2.
* [`fabric-26.3/`](fabric-26.3/) — Base module for the unified 26.x jar (`compat262`: `submitBlockOutline` mixin + 3-arg `submitMovingBlock` adapter) plus its release jar.
* [`neoforge-1.21.1/`](neoforge-1.21.1/) — Subproject for NeoForge on Minecraft 1.21.1 (Java 21, ModDevGradle).

### Unified 26.x jar

A single `shakee_blocki-26.x-<version>.jar` covers **Minecraft 26.1.2, 26.2 and 26.3**:
```bash
./gradlew :fabric-26.3:unifiedJar
# -> fabric-26.3/build/libs/shakee_blocki-26.x-<version>.jar
```
It merges the 26.3-based build with the `compat261` classes from `:fabric-26.1.2:jar`. At runtime,
`ShakeeMixinPlugin` (in `common/`) and `ShakeeSubmitAdapter` (in `common26/`) pick the correct
variant per version — 26.1.x uses the legacy `renderHitOutline` form and the two-argument
`submitMovingBlock`; 26.2+ use the `submitBlockOutline` form and the three-argument call.
Minecraft 26.x ships unobfuscated, so no mapping remap step is needed between 26.x builds.

---

## Building from Source

Building all targets requires **JDK 25**; the 1.21.11 target can also be built with JDK 21.

To build release JARs for all supported versions simultaneously:
```bash
./gradlew buildAll
```

Or build a specific version:
```bash
./gradlew :fabric-1.21.11:build        # Fabric 1.21.11
./gradlew :neoforge-1.21.1:build       # NeoForge 1.21.1
./gradlew :fabric-26.3:build           # standalone 26.3 jar
./gradlew :fabric-26.3:unifiedJar      # single jar for 26.1.2, 26.2 and 26.3
```

Compiled `.jar` artifacts will be located in:
* `fabric-1.21.11/build/libs/shakee_blocki-1.21.11-1.0.2.jar`
* `neoforge-1.21.1/build/libs/shakee_blocki-neoforge-1.0.2.jar`
* `fabric-26.1.2/build/libs/shakee_blocki-26.1.2-1.0.2.jar`
* `fabric-26.3/build/libs/shakee_blocki-26.3-1.0.2.jar`
* `fabric-26.3/build/libs/shakee_blocki-26.x-1.0.2.jar` (unified 26.1.2–26.3)

---

## 📜 Credits & License

* **Author & Developer:** **Disareus**
* **Inspiration:** Inspired by dynamic block animation concepts in the Minecraft modding community.
* **License:** Licensed under the [MIT License](LICENSE). Free to use, modify, and redistribute!




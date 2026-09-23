# Shakee Blocki

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11%20%7C%2026.1.2%20%7C%2026.3-brightgreen.svg)](https://minecraft.net/)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue.svg)](https://fabricmc.net/)
[![Side](https://img.shields.io/badge/Side-Client-purple.svg)](https://modrinth.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A lightweight, highly optimized client-side Fabric mod that adds juicy, physics-like wobbling, tilting, expanding, and bouncing animations to Minecraft blocks whenever you place or mine them.

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

Shakee Blocki stores its settings in `.minecraft/config/shakee_blocki.json`. The in-game screen is available when **Mod Menu** and **Cloth Config** are installed.
* Enable/disable placement, breaking, or ripple animations independently.
* Customize durations, maximum tilt angles, rotation cycles, and easing curves.
* Config file location: `.minecraft/config/shakee_blocki.json`.

---

## Project Structure

The project uses a clean multi-version architecture with a unified universal core:
* [`common/`](common/) — Universal animation math, configuration, and shared cross-version mixins.
* [`fabric-1.21.11/`](fabric-1.21.11/) — Subproject for Minecraft 1.21.11 (Java 21, Loom-remap, `WorldRenderEvents`).
* [`fabric-26.1.2/`](fabric-26.1.2/) — Subproject for Minecraft 26.1.2 (Java 25, `SubmitNodeCollector`).
* [`fabric-26.3/`](fabric-26.3/) — Subproject for Minecraft 26.3 (Java 25, `SubmitNodeCollector`, `submitBlockOutline`).

---

## Building from Source

Building all targets requires **JDK 25**; the 1.21.11 target can also be built with JDK 21.

To build release JARs for all supported versions simultaneously:
```bash
./gradlew buildAll
```

Or build a specific version:
```bash
./gradlew :fabric-1.21.11:build
./gradlew :fabric-26.1.2:build
./gradlew :fabric-26.3:build
```

Compiled `.jar` artifacts will be located in:
* `fabric-1.21.11/build/libs/shakee_blocki-1.21.11-1.0.0.jar`
* `fabric-26.1.2/build/libs/shakee_blocki-26.1.2-1.0.0.jar`
* `fabric-26.3/build/libs/shakee_blocki-26.3-1.0.0.jar`

---

## 📜 Credits & License

* **Author & Developer:** **Disareus**
* **Inspiration:** Inspired by dynamic block animation concepts in the Minecraft modding community.
* **License:** Licensed under the [MIT License](LICENSE). Free to use, modify, and redistribute!




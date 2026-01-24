# TapSyncWatch

![CI](https://github.com/gammagoblin-DD/TapSyncWatch/actions/workflows/android.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/gammagoblin-DD/TapSyncWatch)
![License](https://img.shields.io/github/license/gammagoblin-DD/TapSyncWatch)
![Platform](https://img.shields.io/badge/platform-Wear%20OS-blue)
![Protocol](https://img.shields.io/badge/protocol-OSC-purple)
![Status](https://img.shields.io/badge/status-final-brightgreen)

**TapSyncWatch** is a Wear OS app for **direct tempo control of Resolume
via OSC**.

> **Resolume is the master clock.\
> The watch is a deterministic button.**

------------------------------------------------------------------------

## Table of Contents

-   [Project Goal](#project-goal)
-   [Architecture](#architecture)
-   [Golden Rules](#golden-rules)
-   [Screenshots & GIFs](#screenshots--gifs)
-   [Build & Install](#build--install)
-   [Features](#features)
-   [ActionEngine](#actionengine)
-   [Gestures](#gestures)
-   [MainActivity](#mainactivity)
-   [Contributing](#contributing)
-   [Changelog](#changelog)
-   [License](#license)

------------------------------------------------------------------------

## Project Goal

TapSyncWatch provides **reliable, tactile tempo control** for Resolume.

The watch is deliberately **not**: - a clock - a BPM calculator - a
musical timing system

It sends **precise OSC button impulses only**.

------------------------------------------------------------------------

## Architecture

    TapScreen (MotionEvent gestures)
            ↓
        ActionEngine
            ↓
         OscSender
            ↓
          Resolume

-   Resolume owns timing and repeat logic
-   The watch only presses buttons
-   No abstraction above Resolume semantics

------------------------------------------------------------------------

## Golden Rules

These rules are **non-negotiable**.

1.  **Never repeat Nudge on the watch**
    -   DOWN → sendInt(1)
    -   HOLD → Resolume repeats internally
    -   UP → sendInt(0)
2.  **Tap is always momentary**
    -   `1 → 40 ms → 0`
3.  **Multiply / Divide are latch buttons**
    -   `sendInt(1)` only
    -   no reset (`0`)
    -   no floats
4.  **Gestures use raw MotionEvents**
    -   `pointerInteropFilter`
    -   no `detectDragGestures`

> If something feels "smarter" than before, it is wrong.

------------------------------------------------------------------------

## Screenshots & GIFs

> 📸 Place your media files in `docs/media/` and update the links below.

### Tap Gesture

![Tap Gesture](docs/media/tap.gif)

### Nudge Hold & Release

![Nudge Gesture](docs/media/nudge.gif)

### Multiply / Divide Swipe

![Multiply Divide](docs/media/multiply_divide.gif)

------------------------------------------------------------------------

## Build & Install

### Requirements

-   Android Studio (latest stable)
-   Android SDK 33+
-   Wear OS device or emulator
-   Resolume Arena/Avenue with OSC enabled

### Clone

``` bash
git clone https://github.com/gammagoblin-DD/TapSyncWatch.git
cd TapSyncWatch
```

### Build (Debug)

``` bash
./gradlew assembleDebug
```

### Install via ADB

``` bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Wear OS (Wireless Debugging)

``` bash
adb pair <IP>:<PORT>
adb connect <IP>:<PORT>
adb install app-debug.apk
```

------------------------------------------------------------------------

## Features

### Tap

-   `/composition/tempocontroller/tempotap`
-   Momentary button (1 → 40 ms → 0)

### Multiply ×2

-   `/composition/tempocontroller/tempo/multiply`
-   One-shot latch

### Divide ÷2

-   `/composition/tempocontroller/tempo/divide`
-   One-shot latch

### Nudge Push / Pull

-   `/composition/tempocontroller/tempopush`
-   `/composition/tempocontroller/tempopull`

```{=html}
<!-- -->
```
    DOWN  → 1
    HOLD  → Resolume repeats
    UP    → 0

### Resync

-   `/composition/tempocontroller/resync`
-   Momentary button

------------------------------------------------------------------------

## ActionEngine

The **ActionEngine** is intentionally dumb.

``` kotlin
tap()
multiply()
divide()
resync()
nudgePushStart()
nudgePushEnd()
nudgePullStart()
nudgePullEnd()
```

-   no clock
-   no BPM state
-   no gesture knowledge
-   no optimizations

------------------------------------------------------------------------

## Gestures

-   implemented with `pointerInteropFilter`
-   raw `MotionEvent` handling
-   exclusive zones:
    -   **CENTER** → tap / swipe / resync
    -   **LEFT BEZEL** → nudge

Reason: \> Musical control requires deterministic input.

------------------------------------------------------------------------

## MainActivity

-   creates exactly one `ActionEngine`
-   owns exactly one OSC coroutine scope
-   contains no timing or gesture logic
-   only wires UI to actions

------------------------------------------------------------------------

## Contributing

Contributions are welcome, but **behavior must remain identical**.

### Guidelines

1.  Do **not** introduce a clock or BPM logic
2.  Do **not** add internal repeat timers
3.  Do **not** replace MotionEvent gestures
4.  Keep ActionEngine behavior unchanged

Please open an issue before major changes.

------------------------------------------------------------------------

## Changelog

See [CHANGELOG.md](CHANGELOG.md)

v1.0.0 FINAL → v1.0.1 FINAL

### v1.0.0 -- FINAL

-   Behavior identical to legacy implementation
-   Deterministic ActionEngine introduced
-   Raw MotionEvent gesture port completed
-   Architecture locked

------------------------------------------------------------------------

## License

Specify your license here (e.g. MIT).\
If omitted, the project is considered **All Rights Reserved**.

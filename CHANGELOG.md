# Changelog

## [0.3.2] – 2026-01-12

### Added
- Partial bezel interaction (fixed left-side ring segment)
- Angular restriction to prevent false rotary triggers

### Fixed
- Accidental bezel activation during swipes
- Rotary triggering outside intended interaction area

### Changed
- Bezel interaction now requires ACTION_DOWN inside the allowed ring sector

### Notes
- Center gesture logic remains unchanged from v0.3.1
- `/composition/tempocontroller/tempo` is intentionally ignored

## [0.3.1] – 2026-01-12

### Fixed
- Final stabilization of center gesture state machine
- Guaranteed single-fire behavior for tap, swipe and resync

### Notes
- This release freezes the gesture baseline after extended real-device testing.
- `/composition/tempocontroller/tempo` is intentionally ignored and treated as a Resolume-internal side effect.

## [0.3.0] – 2026-01-12

### Added
- Deterministic swipe direction locking
- One-shot gesture triggering (tap, swipe, resync)
- Explicit gesture consumption state to prevent duplicates

### Fixed
- Multiple OSC messages per gesture
- Swipe direction inversion under slow movement
- Accidental tap firing during swipe
- Accidental resync retriggering
- Gesture overlap between swipe and tap

### Changed
- Gesture state machine clarified and simplified
- Bezel interaction restricted to explicit start zone

### Notes
- `/composition/tempocontroller/tempo` is intentionally ignored.
  Resolume emits this internally when tempo-related commands are received.


## [BASELINE_GREEN] – 2026-01-12

### Added
- Stabiler Referenzstand für TapSync Watch

### Fixed
- Nicht-deterministische Build-Fehler
- Package-/Import-Inkonsistenzen (`OscSender`)
- Coroutine-Verstöße bei Animationsaufrufen

### Guarantees
- `:app:assembleDebug` ist reproduzierbar grün
- Kein Feature-Verlust
- Keine Gesture-Regressionen

### Notes
Dieser Commit ist der offizielle technische Fixpunkt des Projekts.

## [0.3.0] – 2026-01-11

### ✨ Added
- Virtuelle Lünette (circular edge gesture)
  - Nudge + / − via Uhrzeigersinn-Geste
- Stabile BPM-Anzeige (optional)
- Vollständige Gestensteuerung ohne Hardware-Lünette

### 🛠 Fixed
- Build-Fehler durch experimentelle Compose APIs
- Doppelte OSC-Sends bei Tap
- Konflikte zwischen Gesten (Tap / Swipe / Lünette)
- Long-Press zuverlässig für Settings

### 🧹 Changed
- MainActivity refaktoriert und stabilisiert
- Gesture Handling robuster & besser priorisiert

---

## [0.2.x]
- Erste Gestenexperimente
- OSC-Basisfunktionalität

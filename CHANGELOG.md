# Changelog

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

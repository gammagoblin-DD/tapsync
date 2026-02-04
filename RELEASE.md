# TapSyncWatch – Release Notes

Dieses Dokument fasst **Release-Stände** zusammen (grobe Highlights).
Detailänderungen stehen im `CHANGELOG.md`.

---

## v2.2.4-alpha — Modern Settings UI (2026-02-04)

### Highlights
- **Modernes watch-like Settings-Menü**
  - klappbare Sektionen (Accordion)
  - klare Gruppierung (Network/OSC, Visuals, Animations, Feedback, Clock)
- **OSC Target Presets zurück**
  - Preset **A/B/C** (Name + IP + Port)
  - Quick-Switch des aktiven Targets
  - Inline-Edit, persistent via DataStore
- **Stabiler Settings-Flow**
  - kein Crosshair-Trigger
  - Settings öffnen via **Long-Press** (Center)
  - kein “App → Hintergrund” beim Öffnen

### Notes
- Transport-Semantik bleibt TouchOSC-konform (Momentary/Latch/Held).
- Änderungen sind UI-/Settings-orientiert, ohne “smarte” Timing-Logik.

---

## v2.0.0 — Architecture Stable Release

Diese Version etablierte den **v2.x Architektur-Kontrakt**:
- klare Trennung von UI / Domain / Transport
- deterministisches, live-taugliches Verhalten
- keine implizite Clock/Transport-Kopplung

Siehe Details in `CHANGELOG.md` und `ARCHITECTURE.md`.

---

## v1.0.1 — FINAL (2026-01-24)

Eingefrorener Referenzstand für das **TouchOSC-konforme Transport-Verhalten**:
- Tap, Multiply, Divide, Nudge, Resync verifiziert
- keine Mehrfach-Trigger
- Resolume ist die einzige Clock
- reproduzierbar grüner Build

---

## Historische Fixpunkte

Tags wie `BASELINE_GREEN`, `PHASE_2A_FREEZE` oder `wear-osc-stable-*`
sind **Engineering-Anker** (Rollback/Debug), nicht zwingend “User Releases”.

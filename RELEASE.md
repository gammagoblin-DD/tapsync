# TapSync Watch – Releases

## 🟢 Aktueller Release

### v1.0.1 — FINAL (2026-01-24)

Dies ist der **offizielle, eingefrorene FINAL-Release** von TapSyncWatch.

#### Status
- Verhalten vollständig verifiziert
- Identisch zum Legacy-Stand
- Architektur abgeschlossen und eingefroren

#### Kernprinzip
**Resolume ist die Clock.  
Die Watch ist ein deterministischer Taster.**

#### Enthaltene Features
- Tap → Tempo Tap (Momentary)
- Swipe Up → Tempo ×2
- Swipe Down → Tempo ÷2
- Swipe → Resync
- Bezel (linker Ring):
  - CW → Tempo Push
  - CCW → Tempo Pull
- Long Press → Settings

#### Technische Garantien
- Keine Clock / kein BPM-State in der Watch
- Keine eigenen Repeats
- Eine Geste → genau ein OSC-Event
- Reproduzierbar grüner Build

---

## 🟡 Historische Fixpunkte

### v0.3.4 — Phase 2A Gesture Isolation
Eingefrorener Gesten-Fixpunkt.
Diente als Grundlage für v1.x, ist aber **kein aktueller Release** mehr.


Dieses Dokument beschreibt **offizielle Release-Stände** von TapSync Watch.
Ein Release ist ein **eingefrorener, getesteter und reproduzierbarer Zustand**,
der als stabiler Referenzpunkt dient.

Detailänderungen sind im `CHANGELOG.md` dokumentiert.

---

## 🟢 Aktueller Release

### v0.3.4 — Phase 2A: Gesture-Isolation  
**Release-Datum:** 2026-01-12

Dies ist der **aktuelle stabile Release**.

#### Status
- Gestenlogik vollständig abgeschlossen und eingefroren
- Deterministisches, beweisbares Verhalten
- Stabiler Ausgangspunkt für alle weiteren Features

#### Enthaltene Features
- Wear OS App (Samsung Galaxy Watch)
- Tap → Tempo Tap (Momentary)
- Swipe Up → Tempo ×2
- Swipe Down → Tempo ÷2
- Swipe Right → Left → Resync
- Bezel / Nudge (linker Ringbereich):
  - Clockwise → Tempo Push
  - Counter-Clockwise → Tempo Pull
- Long Press → Settings
- Visuelles Tap-Feedback

#### Technische Garantien
- Eine Geste → genau ein OSC-Event
- Keine konkurrierenden Pointer-Events
- Bezel, Swipe und Tap sind logisch exklusiv
- Reproduzierbar grüner Build
- Keine Feature-Regressionen

#### Einschränkungen
- Keine BPM-Clock
- Keine Phase-Anzeige
- Kein haptisches Feedback
- Keine erweiterten Performance-Controls

Diese Punkte sind **bewusst nicht Teil dieses Releases**.

---

## 🔒 Fixpunkte

### BASELINE_GREEN — Technischer Referenzstand  
**Datum:** 2026-01-12

- Erster reproduzierbar grüner Build
- Build- & Coroutine-Stabilität sichergestellt
- Dient als Rollback-Anker
- Nicht als User-Release gedacht, sondern als Entwicklungsbasis

---

## 🧭 Release-Philosophie

TapSync verfolgt eine **Fixpunkt-orientierte Release-Strategie**:

- Ein Release wird **nicht** veröffentlicht, solange:
  - Verhalten nicht deterministisch ist
  - Gesten konkurrieren
  - Seiteneffekte möglich sind
- Releases sind:
  - klein
  - fokussiert
  - eindeutig beschreibbar

Neue Features entstehen **immer auf Basis eines eingefrorenen Releases**.

---

## 🔜 Nächste geplante Releases (Ausblick)

Diese Punkte sind **nicht umgesetzt** und dienen nur der Orientierung:

- Phase 2B: BPM-Clock & Phase-State
- BPM- & Phase-Anzeige
- Optisches Nudge-Feedback
- Haptisches Feedback
- Erweiterte Settings
- Zusätzliche Performance-Controls (z. B. FFT Gain)

---

## ⚠️ Hinweis

Nur die hier aufgeführten Versionen gelten als **offizielle Releases**.  
Zwischenstände, Branches oder experimentelle Commits sind **kein Release**,
auch wenn sie lokal funktionieren.

Für Details siehe:
- `README.md` (Was kann die App?)
- `CHANGELOG.md` (Was hat sich geändert?)

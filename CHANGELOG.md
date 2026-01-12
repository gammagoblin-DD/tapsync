# Changelog

Alle relevanten Änderungen an TapSync Watch werden hier dokumentiert.  
Der Fokus liegt auf **Verhalten, Stabilität und garantierten Eigenschaften** – nicht auf internen Refactors.

---

## [v0.3.4] – 2026-01-12  
### Phase 2A – Gesture-Isolation (eingefroren)

### Fixed
- Deterministische Gesture-Priorisierung (Tap / Swipe / Bezel / LongPress)
- Exklusives MOVE-Handling (keine konkurrierenden Pfade)
- Abgesicherte UP-Phase (Tap ist echter Fallback)
- Explizite Gesture-Fallback-Regeln

### Guarantees
- Eine Geste → genau ein Effekt
- Bezel, Swipe und Tap sind logisch exklusiv
- Keine Mehrfach-Trigger
- Keine Gesture-Regressionen
- Kein Feature-Verlust gegenüber v0.3.3

### Notes
Dieser Release friert **Phase 2A (Gesture-Isolation)** vollständig ein  
und definiert den finalen Gesten-Fixpunkt für alle weiteren Features.

---

## [v0.3.3] – Stable Gesture & OSC Semantics

### Fixed
- Multiply / Divide triggert stabil ×2 / ÷2 (kein ×4 / ÷4 mehr)
- Keine Mehrfach-Trigger bei Gesten
- BPM-Drift beseitigt
- Tap auf Bezel vollständig deaktiviert
- Dauerleuchten in Resolume behoben (Tap / Resync)

### Improved
- Nudge (Bezel) als echtes Momentary-Control:
  - Start bei Drehimpuls
  - Halten solange Finger liegt
  - Sauberes Release beim Loslassen
- Bezel-Trefferzone verfeinert:
  - links positioniert
  - < 180°
  - robuster gegen Fehltrigger
- Gesture-Isolation weiter verbessert

### Technical
- TouchOSC-konforme OSC-Semantik
- Klare Trennung von:
  - Momentary Buttons (Tap, Resync)
  - One-Shot Actions (Multiply, Divide)
  - Held Controls (Nudge)

---

## [v0.3.2] – 2026-01-12

### Added
- Partielle Bezel-Interaktion (linkes Ringsegment)
- Winkelbegrenzung zur Vermeidung von Fehltriggern

### Fixed
- Unbeabsichtigte Bezel-Aktivierung während Swipes
- Rotary-Trigger außerhalb der vorgesehenen Zone

### Changed
- Bezel-Interaktion erfordert ACTION_DOWN innerhalb des Rings

### Notes
- Center-Gestenlogik unverändert gegenüber v0.3.1
- `/composition/tempocontroller/tempo` wird bewusst ignoriert

---

## [v0.3.1] – 2026-01-12

### Fixed
- Finale Stabilisierung der Center-Gesten-State-Machine
- Garantiertes Single-Fire-Verhalten für:
  - Tap
  - Swipe
  - Resync

### Notes
Dieser Release friert die **erste stabile Gesten-Baseline**  
nach ausgiebigem Real-Device-Testing ein.

---

## [v0.3.0] – 2026-01-11

### Added
- Deterministische Swipe-Richtungserkennung
- One-Shot-Gesten (Tap, Swipe, Resync)
- Explizite Gesture-Consumption-States

### Fixed
- Mehrfach-OSC-Sends pro Geste
- Swipe-Richtungsfehler bei langsamer Bewegung
- Unbeabsichtigtes Tap-Feuern während Swipes
- Mehrfach-Resyncs
- Gesture-Überlagerungen (Tap / Swipe / Bezel)

### Changed
- Gesten-State-Machine vereinfacht und explizit gemacht
- Bezel-Interaktion auf klar definierte Startzone beschränkt

### Notes
- `/composition/tempocontroller/tempo` wird nicht aktiv gesendet  
  (Resolume behandelt dies intern als Side-Effect)

---

## [BASELINE_GREEN] – 2026-01-12  
### Technischer Fixpunkt

### Added
- Erster reproduzierbarer, stabiler Referenzstand

### Fixed
- Nicht-deterministische Build-Fehler
- Package-/Import-Inkonsistenzen (`OscSender`)
- Coroutine-Verstöße bei Animationsaufrufen

### Guarantees
- `:app:assembleDebug` ist reproduzierbar grün
- Kein Feature-Verlust
- Keine Gesture-Regressionen

### Notes
Dieser Commit ist der **offizielle technische Fixpunkt** des Projekts  
und dient als Rollback-Anker.

---

## [0.2.x] – Frühphase

- Erste Gestenexperimente
- OSC-Basisfunktionalität
- Exploration verschiedener Interaktionsmodelle

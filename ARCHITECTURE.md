# TapSyncWatch – Architecture (FINAL)

Dieses Dokument beschreibt die **finale Architektur** von TapSyncWatch
ab **v1.0.1 FINAL**.

Ziel ist **deterministisches, live-taugliches Verhalten**
bei direkter OSC-Steuerung von Resolume.

---

## 🧭 Architektur-Prinzip (FINAL)

**Resolume ist die einzige Clock.**

Die Watch:
- berechnet kein BPM
- hält keinen Phasen-State
- erzeugt keine Repeats
- trifft keine musikalischen Entscheidungen

Sie verhält sich wie ein **Hardware-Taster**.

---

## 🧩 Architektur-Überblick

User (Touch / Bezel)
↓
TapScreen (MotionEvent, deterministisch)
↓
ActionEngine (dumm, zustandslos)
↓
OscSender (UDP, stateless)
↓
Resolume (Clock + Repeat + Tempo)


---

## 🖐️ Gesture Layer (TapScreen)

- rohe `MotionEvent`s via `pointerInteropFilter`
- exklusive Gesture-Zonen:
  - CENTER → Tap / Swipe / Resync
  - LEFT BEZEL → Nudge
- **eine Geste → genau ein Effekt**
- keine konkurrierenden Pfade

Tap ist **immer Fallback**, nie Seiteneffekt.

---

## 🎛️ Action Layer (ActionEngine)

Die ActionEngine enthält **keine Logik**, sondern nur **exakte Abbildung**
auf OSC-Controls:

- Tap → Momentary (1 → 0)
- Multiply / Divide → Latch (nur 1)
- Nudge → Held Button (DOWN=1, UP=0)
- Resync → Momentary (1 → 0)

**Kein Timing außer Momentary-Delay.**

---

## 🔌 OSC Layer

- vollständig stateless
- strikt TouchOSC-Semantik
- **/composition/tempocontroller/tempo wird niemals gesendet**

Tempo entsteht ausschließlich als **Resolume-interner Side-Effect**.

---

## 🔒 Architektur-Garantie

Ab **v1.0.1 FINAL** gilt:

- keine Clock in der Watch
- keine BPM-Logik
- keine zukünftige „Phase 2B Clock“
- Erweiterungen nur außerhalb der Tempo-Gesten

Diese Architektur ist **eingefroren**.

# TapSync Watch – Architecture

Dieses Dokument beschreibt die **Architektur, Zuständigkeiten und Designentscheidungen**
der TapSync Watch App.

Ziel ist **Determinismus, Stabilität und Live-Tauglichkeit** – nicht maximale Abstraktion.

---

## 🧭 Architektur-Ziele

TapSync ist eine **Live-Performance-App**.  
Daraus ergeben sich klare Prioritäten:

1. **Deterministisches Verhalten**
2. **Eine Geste → genau ein Effekt**
3. **Keine impliziten Zustände**
4. **Keine konkurrierenden Event-Pfade**
5. **Vorhersagbarkeit > UX-Spielereien**

Die Architektur ist bewusst **flach**, **explizit** und **nachvollziehbar** gehalten.

---

## 🧩 High-Level-Überblick

┌─────────────┐
│ User │
│ (Touch) │
└─────┬───────┘
│
▼
┌─────────────────────────┐
│ Gesture Layer │
│ (TapScreen) │
│ │
│ - TouchZone │
│ - Gesture Priority │
│ - Deterministic Routing │
└─────┬───────────────────┘
│
▼
┌─────────────────────────┐
│ Action Layer │
│ (Callbacks) │
│ │
│ - onTap │
│ - onSwipeUp / Down │
│ - onResync │
│ - onNudgePush / Pull │
└─────┬───────────────────┘
│
▼
┌─────────────────────────┐
│ OSC Layer │
│ (OscSender) │
│ │
│ - UDP │
│ - Stateless │
│ - TouchOSC Semantics │
└─────────────────────────┘


---

## 🖐️ Gesture Layer (TapScreen)

### Verantwortlichkeiten
- Roh-Input (`MotionEvent`)
- Gesture-Erkennung
- **Exklusive Entscheidung pro Event**
- Konsumation von Pointer-Events

### Zentrale Konzepte

#### TouchZone
```kotlin
CENTER | RING

    Wird bei ACTION_DOWN festgelegt

    Ändert sich während einer Geste nicht

    Verhindert „Hereinziehen“ in andere Gesten

Gesture-Priorität (Phase 2A)

Verbindliche Reihenfolge:

    Bezel (RING)

    Swipe (CENTER)

    LongPress (CENTER)

    Tap (Fallback)

Diese Priorität ist:

    explizit

    im Code sichtbar

    durch Early-Returns abgesichert

ACTION_MOVE – Exklusivität

Für jedes MOVE-Event gilt:

    entweder Bezel

    oder Swipe

    oder nichts

Nie mehrere Pfade gleichzeitig.
ACTION_UP – Fallback-Regel

Beim Loslassen gilt:

    Bezel-Ende hat Vorrang

    Swipe blockiert Tap vollständig

    Nur wenn nichts anderes aktiv war:

        LongPress oder

        Tap

Tap ist immer der letzte Fallback, nie ein Nebeneffekt.
🎛️ Action Layer (Callbacks)

Die Gesture Layer führt keine Logik aus, sondern ruft Callbacks auf:

onTap()
onSwipeUp()
onSwipeDown()
onResync()
onNudgePushStart()
onNudgePushEnd()
onNudgePullStart()
onNudgePullEnd()

Vorteile:

    klare Trennung von Erkennung & Wirkung

    einfache Testbarkeit

    spätere Erweiterungen ohne Gesture-Rewrite

🔌 OSC Layer (OscSender)
Prinzipien

    Stateless

    Kein internes Timing

    Kein Caching

    Kein Retry-Management

Semantik

    Orientiert sich strikt an TouchOSC

    Klare Typen:

        Int → Momentary / Held Controls

        Float → kontinuierliche Parameter

Wichtige Entscheidung

Die App sendet niemals:

/composition/tempocontroller/tempo

Resolume erzeugt /tempo intern als Side-Effect.
Das ist bewusst so und Teil des Designs.
🧠 State-Management
Aktueller Stand (Phase 2A)

    Kein globaler Clock-State

    Keine BPM-Berechnung

    Kein Phase-State

Warum?

    Gesture-Stabilität hatte Priorität

    Clock-Logik wird erst auf stabiler Input-Basis eingeführt

🧱 Zukünftige Architektur-Erweiterungen
Phase 2B – Clock / BPM / Phase

    Zentrale Clock als Single Source of Truth

    BPM ableitbar aus:

        Tap

        Multiply / Divide

        Nudge

    Phase-Reset bei Resync

Phase 3 – Performance Controls

    Sekundäre Controls (z. B. FFT Gain)

    Separate Zonen oder explizite UI-Elemente

    Keine Vermischung mit Tempo-Gesten

Phase 4 – Settings

    Konfigurierbare Bezel-Zonen

    Feedback-Toggles (Visual / Haptics)

    Display- & Power-Optionen

🧪 Architektur-Garantien

    Keine versteckten Zustände

    Keine impliziten Gesture-Fallbacks

    Kein „best guess“-Verhalten

    Klare, lesbare Entscheidungsbäume

    Jeder Effekt ist rückverfolgbar zu einer Geste

⚠️ Nicht-Ziele

Bewusst nicht Teil der Architektur:

    Reactive Gesture-Streams

    Komplexe State-Machines

    Overengineering (Redux, MVI etc.)

    Automatische Gesture-Erkennung

Einfachheit ist hier ein Feature.
🧾 Dokument-Hierarchie

    README.md → Was ist das Projekt?

    ARCHITECTURE.md → Wie ist es gebaut?

    CHANGELOG.md → Was hat sich geändert?

    RELEASE.md → Welche Version ist offiziell?

🔒 Hinweis

Diese Architektur gilt für den eingefrorenen Phase-2A-Stand.
Änderungen an der Gesture Layer dürfen nur bewusst und versioniert erfolgen.


---

## ✅ Ergebnis

Du hast jetzt eine **vollständige, professionelle Dokumentation**:

- README → Überblick  
- CHANGELOG → Historie  
- RELEASE → Fixpunkte  
- ARCHITECTURE → technische Wahrheit  

Damit ist das Projekt **außergewöhnlich gut aufgestellt**.

Wenn du willst, können wir als Nächstes:
- **Phase 2B (Clock / BPM / Phase) designen**
- oder eine **DEVELOPMENT.md** für Build & Setup ergänzen
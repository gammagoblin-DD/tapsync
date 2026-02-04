# TapSyncWatch – Architektur (v2.x)

Dieses Dokument beschreibt die Architektur und Zuständigkeiten von **TapSyncWatch**.
Fokus: **Determinismus, Stabilität, Live-Tauglichkeit** (Wear OS + Resolume via OSC).

> **Resolume ist die Clock.**  
> Die Watch verhält sich wie ein **deterministischer Hardware-Controller**.

---

## Architektur-Kontrakt

Die Watch:
- **berechnet kein BPM** (kein “smarter” Tempo-Algorithmus)
- hält **keinen eigenen musikalischen Phasen-State** als Quelle der Wahrheit
- erzeugt **keine Repeats** (Nudge hält nur den Button, Resolume wiederholt intern)
- sendet OSC **TouchOSC-konform** (Momentary / Latch / Held)

Resolume:
- ist die **einzige Autorität** für Tempo/Repeat/Transportverhalten

---

## Layer & Zuständigkeiten

### UI Layer
- **TapScreen**
  - Roh-Input via `pointerInteropFilter` (`MotionEvent`)
  - deterministische Gesten-Routing-Logik (exklusive Zonen + Early-Returns)
  - triggert ausschließlich *Actions* (keine OSC-Details)
- **SettingsScreen** (v2.2.4-alpha)
  - watch-like UI: **klappbare Sektionen**, Toggles, Edit-Felder
  - verwaltet OSC-Targets (Presets A/B/C) und UI/Feedback-Optionen
- **WatchUI / MainActivity**
  - routet zwischen TapScreen und SettingsScreen
  - hält UI-State wie “Settings offen/zu”
  - keine Gesture-Logik, keine Timing-Logik

### Domain Layer
- **ActionEngine**
  - absichtlich “dumm”: nur Mapping von Actions → Transport-Aufrufe
  - keine Clock, kein BPM-State, keine Heuristiken

### Transport Layer
- **OscSender / OscInputReceiver**
  - UDP OSC Send/Receive
  - stateless Transport (keine Business-Logik)
  - Health/Debug/External BPM sind UI-Monitore (optional)

### Persistence Layer
- **SettingsStore (DataStore)**
  - persisted SettingsState mit **Default State** (UI ist immer sofort renderbar)
  - OSC Target Presets:
    - Preset A / B / C: `Name + IP + Port`
    - `activePreset` bestimmt den aktiven OSC-Target

---

## Signalfluss (Happy Path)

User (Touch / Bezel)
→ TapScreen (Gesture → Action)
→ ActionEngine (Action → OSC Semantik)
→ OscSender (UDP)
→ Resolume (Clock + Repeat + Tempo)

---

## Gesten-Regeln (TapScreen)

**Exklusive Zonen** (bei `ACTION_DOWN` festgelegt, während der Geste konstant):
- **CENTER**: Tap, Swipes (Multiply/Divide/Resync), Long-Press (Settings)
- **LEFT BEZEL**: Nudge Push/Pull

**Priorität (vereinfachte Sicht):**
1) Bezel/Nudge (wenn Zone LEFT)
2) Swipe/Resync (CENTER)
3) Long-Press (CENTER, nur wenn nicht bewegt)
4) Tap (Fallback)

Wichtig: **eine Geste → genau ein Effekt** (keine konkurrierenden Pfade).

---

## Settings & Presets (v2.2.4-alpha)

- Settings öffnen: **Long-Press im Center**
- OSC Targets:
  - drei Presets (A/B/C)
  - Quick-Switch des aktiven Presets
  - Inline-Edit von Name/IP/Port (persistiert via DataStore)
- UI/Feedback:
  - Visual-Monitore (External BPM, OSC Debug)
  - Phase Visualizer / Spiral Mode
  - Animations (global + remote/ghost + ripple/pulse/flash)
  - Haptics (global + Downbeat + Transport-Send)

---

## “Wenn es smarter wirkt, ist es wahrscheinlich falsch.”

TapSyncWatch ist absichtlich simpel:
- deterministische Eingabe
- klare, überprüfbare Semantik
- keine versteckten Seiteneffekte


## Link Health: Ping/Pong Heartbeat

Resolume sendet bei stabilem Tempo nicht permanent BPM-Updates. Daher wird Link-Health via Heartbeat bestimmt:

- Watch → Resolume: `/tapsync/ping <nonce>`
- Resolume → Watch: `/tapsync/pong <nonce>`

UI/Logik basiert auf `lastPongMs` + `signalGraceMs`. Optional kann der Heartbeat auf "Foreground only" begrenzt werden.

# TapSync Watch



## Current Status (v0.3.1)


## Gesture Overview

| Gesture                  | Area     | OSC Message                                      |
|--------------------------|----------|--------------------------------------------------|
| Tap                      | Center   | /composition/tempocontroller/tempotap            |
| Swipe Up                 | Center   | /composition/tempocontroller/tempo/multiply      |
| Swipe Down               | Center   | /composition/tempocontroller/tempo/divide        |
| Swipe Right → Left       | Center   | /composition/tempocontroller/resync              |
| Bezel Rotate Clockwise   | Bezel    | /composition/tempocontroller/tempopush           |
| Bezel Rotate Counter-CW  | Bezel    | /composition/tempocontroller/tempopull           |

The project has reached a stable interaction baseline.

### Gesture State
- Tap: stable, exactly one OSC message per tap
- Swipe up: exactly one `/tempo/multiply` per swipe
- Swipe down: exactly one `/tempo/divide` per swipe
- Swipe right → left: exactly one `/resync`
- No gesture produces duplicate OSC messages
- Gesture direction is locked on first threshold crossing

### Rotary / Bezel
- Bezel interaction is spatially separated from center gestures
- Rotation is only triggered when the touch starts inside the defined bezel ring
- Entering the bezel area during another gesture does NOT trigger rotation

### OSC Behavior
- `/composition/tempocontroller/tempo` is intentionally ignored
- Resolume emits `/tempo` internally as a side effect of tap/multiply/divide
- The watch never sends `/tempo` explicitly

### Stability
- No unintended app navigation
- No system gestures triggered
- No multi-fire gestures

## Status
🟢 **BASELINE_GREEN – eingefrorene stabile Version**

Diese Version ist der erste technisch stabile Fixpunkt des Projekts.

## Features (Stand BASELINE_GREEN)
- Wear OS App (Samsung Galaxy Watch)
- 1-Finger Tap → OSC `/composition/tempocontroller/tempotap`
- Swipe Up → Tempo ×2
- Swipe Down → Tempo ÷2
- Virtuelle Lünette (rotational gesture)
  - Links: Tempo Pull
  - Rechts: Tempo Push
- Long Press → Settings
- Optional BPM-Anzeige
- Visuelles Tap-Feedback (Goblin Flash)

## Technische Garantien
- Build reproduzierbar **grün**
- Keine Experimental APIs
- Keine konkurrierenden PointerEvents
- OSC wird **kontrolliert** gesendet
- Coroutine-Regeln eingehalten
- Kein Feature-Verlust gegenüber vorherigen Ständen

## Hinweis
Dieser Stand ist **eingefroren**.  
Weiterentwicklung erfolgt nur auf Basis dieses Commits.

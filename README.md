# TapSync Watch

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

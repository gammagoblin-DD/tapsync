# TapSync Watch

TapSync ist eine **Wear-OS-App zur präzisen Tempo-Steuerung von Resolume über OSC**.  
Die App ist für **Live-Performance** optimiert und verhält sich bewusst wie **TouchOSC**:
deterministisch, direkt, ohne Mehrfach-Trigger oder versteckte Zustände.

---

## 🟢 Aktueller Status

**Phase 2A abgeschlossen – Gestenlogik eingefroren**

- Gesten sind vollständig **isoliert**
- Jede Geste erzeugt **genau einen definierten Effekt**
- Keine konkurrierenden Pointer-Events
- Stabiler Referenzstand für alle weiteren Features

Dieser Stand ist ein **technischer Fixpunkt** und dient als Ausgangsbasis für:
BPM-Clock, Phase-Anzeige, visuelles Feedback und weitere Performance-Features.

---

## 🎛️ Features

### Tap (Center)
- Tap auf die Mitte
- OSC:

/composition/tempocontroller/tempotap

- **Momentary-Verhalten**
- Genau **ein OSC-Event pro Tap**

---

### Swipe (Center)

- **Swipe Up** → Tempo ×2  

/composition/tempocontroller/tempo/multiply


- **Swipe Down** → Tempo ÷2  

/composition/tempocontroller/tempo/divide


- **Swipe Right → Left** → Resync  

/composition/tempocontroller/resync


Eigenschaften:
- Richtung wird beim ersten Threshold festgelegt
- Keine Mehrfach-Trigger
- Swipe blockiert Tap zuverlässig

---

### Bezel / Nudge (linker Ringbereich)

- Begrenzt auf **linkes Ring-Segment (< 180°)**
- Rotation nur, wenn der Touch **im Ring startet**
- Keine Aktivierung durch „Hereinziehen“

**OSC-Mapping:**
- Uhrzeigersinn → Tempo Push  

/composition/tempocontroller/tempopush

- Gegen Uhrzeigersinn → Tempo Pull  

/composition/tempocontroller/tempopull


Verhalten:
- Start bei Drehimpuls
- Halten, solange Finger liegt
- Sauberes Release beim Loslassen
- Vollständig isoliert von Tap & Swipe

---

### Long-Press
- Öffnet die App-Einstellungen

---

## 🧠 Design-Prinzipien

- **TouchOSC-kompatible OSC-Semantik**
- Eine Geste → genau ein Effekt
- Keine BPM-Drifts
- Keine impliziten Zustände
- Performance-first (Live-Betrieb)
- Vorhersagbares Verhalten > „magisches“ UX-Tuning

---

## 🔌 OSC-Setup

- Ziel-IP: **konfigurierbar**
- Ziel-Port: **konfigurierbar** (z. B. 7002)

Getestet mit:
- Resolume Arena
- Resolume Avenue

Hinweis:
- Die Watch sendet **kein**

/composition/tempocontroller/tempo

- Resolume kann `/tempo` intern als Side-Effect erzeugen – das ist beabsichtigt.

---

## 🧩 Gesture-Übersicht

| Geste                    | Bereich | OSC-Message                                  |
|--------------------------|--------|-----------------------------------------------|
| Tap                      | Center | `/composition/tempocontroller/tempotap`       |
| Swipe Up                 | Center | `/composition/tempocontroller/tempo/multiply` |
| Swipe Down               | Center | `/composition/tempocontroller/tempo/divide`   |
| Swipe Right → Left       | Center | `/composition/tempocontroller/resync`         |
| Bezel CW                 | Ring   | `/composition/tempocontroller/tempopush`      |
| Bezel CCW                | Ring   | `/composition/tempocontroller/tempopull`      |

---

## 🧪 Technische Garantien

- Reproduzierbar **grüner Build**
- Keine Experimental APIs
- Keine konkurrierenden Pointer-Events
- Coroutine-Regeln eingehalten
- Kein Feature-Verlust gegenüber früheren Ständen
- Stabiler Rollback-Anker (Phase 2A)

---

## 🧭 Roadmap (kurz)

Nächste geplante Schritte (nicht Teil dieses Stands):

- Phase 2B: BPM-Clock & Phase-State
- Visuelles Nudge-Feedback
- BPM- & Phase-Anzeige
- Haptisches Feedback
- Erweiterte Settings
- Weitere Performance-Controls

---

## 📦 Versionierung

- **BASELINE_GREEN**: erster stabiler technischer Fixpunkt
- **v0.3.4**: Phase 2A – Gesture-Isolation vollständig abgeschlossen

---

## ⚠️ Hinweis

Dieser Stand ist **eingefroren**.  
Weiterentwicklung erfolgt **ausschließlich** auf Basis dieses Commits.
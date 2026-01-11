# TapSync Watch

TapSync Watch ist eine Wear-OS-App (optimiert für Samsung Galaxy Watch),
mit der sich das Tempo in Resolume per OSC direkt vom Handgelenk steuern lässt.

Die App ist für den **Live-Einsatz** konzipiert:
robust, fehlertolerant und ohne riskante Gesten.

---

## 🎛️ Features

- **Tap Tempo** per Touch
- **Tempo Multiply / Divide** per Swipe
- **Nudge Push / Pull**
- **Resync**
- **BPM-Anzeige** (optional)
- **OSC über WLAN direkt zu Resolume**
- **Konfigurierbare IP & Port**
- **Live-taugliche Gestenpriorisierung**
- **Kein Feature-Loss bei Updates (inkrementelle Entwicklung)**

---

## 🕹️ Gesten- & Input-Matrix

| Eingabe | Aktion | OSC |
|------|------|------|
| Tap | Tap Tempo | `/composition/tempocontroller/tempotap` |
| Long Press | Settings öffnen | — |
| Swipe Up | Tempo ×2 | `/composition/tempocontroller/tempo/multiply` |
| Swipe Down | Tempo ÷2 | `/composition/tempocontroller/tempo/divide` |
| Kreisbewegung (Rand, Uhrzeigersinn) | Nudge + | `/composition/tempocontroller/tempopush` |
| Kreisbewegung (Rand, gegen Uhrzeigersinn) | Nudge − | `/composition/tempocontroller/tempopull` |
| Hardware Button | Resync | `/composition/tempocontroller/resync` |

---

## ⚙️ Settings

- Ziel-IP (Resolume-Host)
- OSC-Port
- BPM-Anzeige ein/aus
- Netzwerk-Autodiscovery (optional / experimentell)

---

## 🧠 Design-Prinzipien

- **Live-Sicherheit vor Feature-Menge**
- Keine Double-Tap-Abhängigkeit
- Keine Multi-Finger-Gesten
- Gesten schließen sich gegenseitig aus
- Keine blockierenden Netzwerk-Operationen im UI-Thread

---

## 🛠️ Tech Stack

- Kotlin
- Jetpack Compose (Wear OS)
- OSC (UDP)
- Android DataStore
- Coroutines

---

## 🚀 Status

**Stable – Live-ready**

Weiterentwicklung erfolgt ausschließlich über **kleine, nachvollziehbare Commits**.

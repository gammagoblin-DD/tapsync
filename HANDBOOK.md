# TapSyncWatch – Handbuch

Dieses Dokument erklärt **Funktionen**, **alle Settings** und die Nutzung des **Resolume Wire Heartbeat Plugins**.

## 1. Überblick

TapSyncWatch ist eine WearOS-App zur OSC-Steuerung von **Resolume**. Die Watch ist **Resolume-first**: BPM/Phase-Visuals basieren auf Resolume-Rückmeldungen. Bei fehlender Verbindung zeigt die UI **NO SIGNAL**.

## 2. TapScreen

### 2.1 BPM/NO SIGNAL
- BPM wird als **Ganzzahl** im schwarzen Bereich unter dem Goblin-Kinn angezeigt.
- Wenn kein Link: **NO SIGNAL**

### 2.2 Statuszeile (optional)
Zeigt oben: **Presetname • IP:Port • Link-Status**.

### 2.3 Aktionen
- Tap, Multiply/Divide, Resync, Nudge: senden OSC an Resolume.
- Haptics optional pro Event.

## 3. Link-Erkennung ohne BPM-Timeout

Resolume sendet BPM nicht permanent, daher nutzt TapSyncWatch einen Heartbeat:

- Watch → Resolume: `/tapsync/ping <nonce>`
- Resolume → Watch: `/tapsync/pong <nonce>`

Wenn der letzte Pong älter als **Signal Grace** ist → **NO SIGNAL**.

## 4. Settings

### 4.1 Network Presets
- Presets speichern Ziel-IP + Port (Resolume OSC Input).
- Active Preset bestimmt, wohin Kommandos gesendet werden.

### 4.2 OSC Heartbeat (Link Check)
- **Link Check**: Heartbeat an/aus
- **Fast / Normal / Slow**: Presets
- **Advanced**
  - **Nur Vordergrund**: Heartbeat nur wenn App sichtbar
  - **Adaptive recovery**: schneller nur bei Link-Down
  - Interval/Grace Slider

### 4.3 UI / Visuals
- OSC Statuspunkt
- External BPM Monitor
- OSC Debug Overlay
- Phase Ring / Phase Spiral
- Animation Toggles

### 4.4 Haptics
- Haptics, Downbeat Haptic, Transport Haptic

## 5. Resolume Wire Heartbeat Plugin

### 5.1 Installation
- Kompiliere/Installiere den Wire Effect (oder `.wired`).
- Ziehe den Effekt in **Composition Effects** (nicht zwingend in einen Layer).

### 5.2 OSC Ports in Resolume (Beispiel)
Resolume → Preferences → OSC:

- **Input Port**: `7002`  (hierhin sendet die Watch `/tapsync/ping`)
- **Output Address**: Watch IP (z.B. `192.168.178.80`)
- **Output Port**: `7000` (Watch empfängt `/tapsync/pong`)

### 5.3 Test
Im Resolume OSC Monitor:
- IN: `/tapsync/ping ...`
- OUT: `/tapsync/pong ...`

Wenn OUT fehlt: Wire Effect aktiv? Nonce ändert sich? Ports korrekt?

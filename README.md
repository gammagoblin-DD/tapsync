# TapSyncWatch ⌚🎛️

Wear OS App für **Tap-basierte Synchronisation** via Bluetooth Low Energy (BLE).

Die App läuft auf einer Wear-OS-Smartwatch (z. B. Galaxy Watch) und sendet
bei einem Tap auf das Display ein BLE-Signal an ein verbundenes Gerät
(Smartphone / Bridge / TouchDesigner / Resolume).

---

## ✨ Aktueller Funktionsumfang

- ✅ Watch App bleibt stabil sichtbar (XML-Layout, kein Compose)
- ✅ BLE Scan mit Service-UUID-Filter
- ✅ Runtime-Permission Handling (`BLUETOOTH_SCAN`)
- ✅ Automatische Verbindung zu erstem gefundenen Gerät
- ✅ UI-Tap-Erkennung (Display-Tap)
- 🚧 BLE GATT Write (in Arbeit)

---

## 🧱 Architektur (Stand jetzt)

watchApp/
└── app/
├── src/main/java/com/example/tapsyncwatch/
│ └── presentation/
│ └── MainActivity.kt
├── src/main/res/
│ ├── layout/activity_main.xml
│ └── drawable/goblin.xml|png


- **UI:** XML (`setContentView`)
- **Bluetooth:** BLE Scan + GATT (Android API)
- **Ziel:** Tap → BLE → Bridge → OSC / MIDI / Resolume

---

## 🔐 Berechtigungen

Benötigt (Wear OS / Android 12+):

- `android.permission.BLUETOOTH_SCAN`
- (später) `BLUETOOTH_CONNECT`

Permissions werden **zur Laufzeit** abgefragt.

---

## 🚀 Roadmap (kurz)

- [ ] Tap → BLE GATT Write
- [ ] Unterschiedliche Commands (TAP /2 / *2 / NUDGE)
- [ ] Vibration Feedback
- [ ] Reconnect / Timeout Handling
- [ ] Phone-Bridge (BLE → OSC)
- [ ] TouchDesigner / Resolume Integration

---

## 🛠️ Tech Stack

- Kotlin
- Wear OS
- Android BLE API
- Android Studio

---

## 🧠 Projektstatus

**Experimentell / Prototyp**  
Fokus: Live-Performance, Sync-Control, Show-Automation.

---

## 📄 Lizenz

Private / Experimental  
(bei Bedarf später definieren)

# Changelog

Alle relevanten Änderungen an **TapSyncWatch** werden hier dokumentiert.

Format orientiert sich an:
https://keepachangelog.com/de/1.0.0/

---

## [0.1.0] – 2026-01-09

### Added
- Initiale Wear-OS-App-Struktur
- Stabiles XML-Layout (`activity_main.xml`)
- BLE Scan mit Service-UUID-Filter
- Runtime-Permission Handling (`BLUETOOTH_SCAN`)
- Automatische BLE-Verbindung (GATT)
- Tap-Erkennung auf dem Watch-Display
- Logging zur Laufzeit-Analyse (Logcat)

### Changed
- Entfernt: Jetpack Compose (zugunsten stabiler XML-UI)

### Known Issues
- Noch kein BLE GATT Write
- Keine Reconnect-Logik
- Keine visuelle Statusanzeige

---

## [Unreleased]

### Planned
- Tap → BLE Write
- Unterschiedliche BLE Commands
- Haptisches Feedback
- OSC-Bridge auf dem Phone

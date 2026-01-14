package com.example.tapsyncwatch.osc

/**
 * DEPRECATED / DISABLED
 *
 * Dieser Listener war ursprünglich für OSC-Auto-Discovery gedacht.
 * Er öffnete jedoch denselben UDP-Port wie der aktive OscInputReceiver
 * (z. B. 7000) und verursachte dadurch eine stille Port-Kollision,
 * bei der die Watch keinerlei OSC mehr empfangen konnte.
 *
 * Ab Phase 2C ist OscInputReceiver der EINZIGE erlaubte OSC-IN-Pfad.
 *
 * Gründe für Deaktivierung:
 * - UDP-Port-Kollision auf Wear OS
 * - Mehrfache Socket-Binds durch Lifecycle / Compose
 * - Instabile Discovery-Logik im Live-Betrieb
 *
 * Diese Datei bleibt bewusst im Projekt:
 * - als Dokumentation der Entscheidung
 * - um versehentliche Reaktivierung zu verhindern
 *
 * Nutzung führt absichtlich zu Compile-Fehlern.
 */

@Deprecated(
    message = "OscListener is disabled. Do NOT use. Conflicts with OscInputReceiver on port 7000.",
    level = DeprecationLevel.ERROR
)
object OscListener {
    // intentionally empty
}

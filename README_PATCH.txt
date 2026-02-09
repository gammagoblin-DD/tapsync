TapSyncWatch Patch (Debug: Preflight + Timeline + Targets + Back)

Anwendung (Windows):
1) Zip in das Repo-Root entpacken (dort wo der .git Ordner liegt).
2) Im Repo-Root ausführen:

   git apply TapSync_debug_preflight_timeline_targets.patch

3) Dann: ./gradlew assembleDebug

Rollback:
- git apply -R TapSync_debug_preflight_timeline_targets.patch

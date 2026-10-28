# Notes for coding agents

Build and test locally with:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Things that look odd and must stay:

- The release build has no fallback signing config. Signing with the debug key "to make it
  work" produces APKs no installed copy can update to.
- `versionCode` comes from `GITHUB_RUN_NUMBER`. Never hard-code it: a number that does not
  rise makes Android refuse the update.
- The workflow reads the tag's number from the built APK (aapt2). Do not compute it a second
  way in the workflow.
- `UpdateCheck.decide` returns `Unavailable` for an answer it can't read. "Couldn't check"
  is never "up to date".
- `gradlew` must be committed executable (mode 100755).
- `namespace` stays `releasekit.app`; owners change `applicationId` in
  `release-kit.properties` instead, so the Kotlin package never has to move.

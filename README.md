# android-release-kit

*An Android app you install from GitHub, that updates itself from GitHub, set up right on day one.*

This is a template for apps you sideload: no Play Store, no developer account, no fee. You
push code; GitHub builds a signed APK and publishes it; the app on your phone sees the new
version and offers the download.

Three things are easy to get wrong in that setup, and costly to fix later:

1. **The signing key.** Android only installs an update signed with the *same key* as the
   app already on the phone. Builds signed with a debug key (the default) use a different
   key on every computer and every CI run, so each one fails with "App not installed"
   until the user uninstalls, losing the app's data. Here, one release key lives in GitHub
   secrets, and CI refuses to publish anything else.
2. **Version numbers.** Every build gets a higher `versionCode` automatically (the CI run
   number), so every release installs as an update. Nobody bumps anything by hand.
3. **An update channel.** A sideloaded app never hears about new versions. This one checks
   GitHub Releases and shows a Download button.

## Make it yours (easiest way)

About 15 minutes, all in a browser apart from step 3. You need a GitHub account and, for
step 3, a Windows computer with [Android Studio](https://developer.android.com/studio)
installed. Android Studio is only used for its `keytool` program.

### 1. Copy the template

On this repository's GitHub page, click **Use this template → Create a new repository**.
Choose a name, such as `my-app`. The name becomes the APK's file name. Choose **Public** if
you want the in-app update check to work: GitHub only answers it for public repositories.

### 2. Name the app

In your new repository, open `release-kit.properties`, click the pencil icon, and change:

- `appName`: the name under the icon on the phone.
- `applicationId`: the app's permanent identity, for example `io.github.yourname.myapp`.
  **Pick it once.** A different id is a different app.

Click **Commit changes**. The build that starts will fail at "Check the release key is set
up". That is expected: there is no key yet.

### 3. Make the signing key (once, ever)

In PowerShell (press Start, type `PowerShell`, press Enter), run these one at a time. The
first asks you to choose a password. Write it down.

```powershell
cd $HOME\Documents
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore my-app-release.p12 -storetype PKCS12 -alias my-app `
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=My App"
```

Then copy the key to the clipboard as text:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\Documents\my-app-release.p12")) | Set-Clipboard
```

The key is made in your Documents folder on purpose: **never put it inside the repository
folder**. This repository is public, and a key pushed here can't be taken back. The build
refuses to run if one is committed, and `.gitignore` leaves `.p12` files out.

**Back up `my-app-release.p12` and its password somewhere safe that isn't this repository**,
such as a password manager. If you lose them, no phone can ever update the app again: every
user has to uninstall it (losing its data) and install a new one.

### 4. Give the key to GitHub

In your repository: **Settings → Secrets and variables → Actions → New repository secret**.
Add four:

| Name | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | paste (the clipboard from step 3) |
| `SIGNING_STORE_PASSWORD` | the password you chose |
| `SIGNING_KEY_ALIAS` | `my-app` (the `-alias` from step 3) |
| `SIGNING_KEY_PASSWORD` | the same password |

### 5. Build it

**Actions → Build APK → Run workflow → Run workflow**. After about 5 minutes, open
**Releases** on your phone and tap the first `.apk`. Android asks once to allow installs
from your browser; allow it.

From now on every push to `main` publishes a new version, and the app shows a Download
button when one is out.

## How it works, in short

- `release-kit.properties` holds everything you'd change. `app/build.gradle.kts` reads it.
- **versionCode** is the GitHub Actions run number (plus `versionCodeOffset`), so it only goes
  up. **versionName** is `versionPrefix` and the run number: `1.0.17`.
- `.github/workflows/build.yml` runs the tests and builds the app. If the secrets are set, it
  builds a signed release. It prints which certificate signed the APK, reads the version
  *from the APK* and, on `main`, publishes it as `build-<versionCode>`.
- `UpdateCheck.kt` reads `api.github.com/repos/<you>/<repo>/releases`. It takes the highest
  `build-N` that has an APK attached and offers it if N is above the installed versionCode.
  The repository is filled in at build time, so there is nothing to edit.
- `MainActivity.kt` is a placeholder screen with the version and update card. Replace the
  screen with your app, and keep `VersionCard()` somewhere the user can find it.

## Already have an app on phones?

If people have a hand-built version installed, two things matter:

- **The key.** If you have the keystore that signed it, use that in step 4 instead of making
  a new one, and updates will install over it. If it was signed with a debug key, each phone
  has to uninstall once.
- **The version number.** Set `versionCodeOffset` above the installed app's `versionCode`.
  Otherwise Android sees CI builds as older and refuses them.

## Things that go wrong

| symptom | cause |
|---|---|
| "App not installed" when updating | The APK was signed with a different key. Compare the SHA-256 that "Show which key signed the APK" prints for each build. |
| The app says "Update checks work once the repository is public" | The repository is private. GitHub answers 404 to apps without a login. |
| Build fails at "Check the release key is set up" | One of the four secrets is missing or misnamed. |
| `./gradlew: Permission denied` on Linux or a Pi | The file lost its executable bit. Run `git update-index --chmod=+x gradlew`, then commit. |

## What is not verified

- Built and tested locally on 2026-10-28 with a throwaway key: `testDebugUnitTest` and
  `assembleRelease` with `GITHUB_RUN_NUMBER=5`. The APK read `versionCode='5'` and
  `versionName='1.0.5'` and was signed by the throwaway certificate. The workflow itself has
  not yet run on GitHub.
- The PowerShell steps are copied from a project where they were used. They haven't been
  re-run for this template.
- The update check has not been tried against a live public repository from this template.

## For the maintainer of this template

The **Use this template** button appears once **Settings → General → Template repository**
is ticked.

## License

MIT

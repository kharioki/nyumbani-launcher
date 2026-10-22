# Releasing Nyumbani

Releases are built and published by GitHub Actions (`.github/workflows/release.yml`) when you
push a tag that starts with `v`. The version comes from the tag; nothing in the code needs editing.

## One-time setup

### 1. Create the release keystore

```bash
keytool -genkeypair -v \
  -keystore nyumbani-release.jks \
  -alias nyumbani \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Nyumbani, O=kharioki"
```

It asks for a keystore password (and a key password; using the same one is fine).

> **Back it up now.** Put `nyumbani-release.jks` and both passwords in your password manager
> and one other safe place, and never commit it. Android only installs an update if it's
> signed with the same key as the installed app. Lose the key and every existing install
> has to be uninstalled (losing its settings) before it can take a new version.

### 2. Add the GitHub secrets

The workflow needs four repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the keystore, base64-encoded |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | `nyumbani` (or whatever you passed to `-alias`) |
| `KEY_PASSWORD` | the key password |

With the GitHub CLI, from the folder with the keystore:

```bash
base64 -i nyumbani-release.jks | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD     # paste when prompted
gh secret set KEY_ALIAS --body nyumbani
gh secret set KEY_PASSWORD          # paste when prompted
```

The workflow never prints these, and deletes the decoded keystore when it finishes.

### 3. Make the releases reachable

The in-app update check and the Downloader link
(`https://github.com/kharioki/nyumbani-launcher/releases/latest/download/nyumbani.apk`) use
GitHub without logging in, so they only work when the repository is **public**. With a private
repo, About shows "No releases published yet" and Downloader gets a 404.

### 4. Optional: signed builds on your Mac

To build release-signed APKs locally (for example to sideload onto your own TV between releases),
add to `~/.gradle/gradle.properties`:

```properties
NYUMBANI_KEYSTORE_FILE=/Users/tony/keys/nyumbani-release.jks
NYUMBANI_KEYSTORE_PASSWORD=...
NYUMBANI_KEY_ALIAS=nyumbani
NYUMBANI_KEY_PASSWORD=...
# Pre-fill "Hey Tony" / Nairobi on first run (only your own builds; public builds start blank)
nyumbaniSeedOwner=true
```

Without these, `./gradlew assembleRelease` still works but signs with the debug key and prints a
warning.

### 5. Moving your TV to release builds

The copy on your TV today is signed with the debug key, so a release-signed update can't
replace it (the installer reports a signature conflict). Once, before installing the first
release:

```bash
adb uninstall dev.kharioki.nyumbani
adb install nyumbani.apk
```

Then go through first-run setup (or build it yourself with `nyumbaniSeedOwner=true`) and set
it as Home again (`adb shell cmd package set-home-activity dev.kharioki.nyumbani/.MainActivity`).
From then on, updates install from About.

## Making a release

1. Move the entries under **[Unreleased]** in `CHANGELOG.md` into a new section,
   `## [1.2.0] - YYYY-MM-DD`, and update the links at the bottom.
2. Commit and push to `main`.
3. Tag and push the tag:

   ```bash
   git tag -a v1.2.0 -m "Nyumbani 1.2.0"
   git push origin v1.2.0
   ```

4. Watch **Actions → Release**. When it's green, the release page has:
   - `nyumbani-1.2.0.apk`: the versioned file
   - `nyumbani.apk`: the same file under a fixed name, for the Downloader link and the in-app updater
   - `SHA256SUMS.txt`: checksums of both (the app checks downloads against this)

   The release notes are generated from merged PRs and commits; edit them on GitHub if you like.
   The in-app updater shows the first few lines.

TVs pick it up within a day (dot on the gear), or straight away with **About → Check for updates**.

### Version numbers

`versionName` is the tag without the `v`. `versionCode` is `major*10000 + minor*100 + patch`:

| Tag | versionName | versionCode |
|---|---|---|
| `v1.0.0` | 1.0.0 | 10000 |
| `v1.2.3` | 1.2.3 | 10203 |
| `v1.3.0-beta.2` | 1.3.0-beta.2 | 10300 − (100 − 2) = 10202 |
| `v1.3.0` | 1.3.0 | 10300 |
| no tag (local build) | 0.0.0-dev | 1 |

Keep minor and patch below 100. A beta's code sits in the previous minor's patch range, so
`1.3.0-beta.2` (10202) and a `1.2.2` hotfix would get the same code. Android won't install a
version over the same or higher code, so don't ship a 1.2.x hotfix after a 1.3.0 beta has gone out.

## Betas

Tag with a pre-release suffix, for example `v1.3.0-beta.1`. The workflow marks any tag containing
`-` as a **pre-release**:

- GitHub's `releases/latest` skips pre-releases, so the Downloader link and the in-app update
  check keep pointing at the last full release. Nobody gets a beta by accident.
- Testers install the beta from its release page (or with Downloader and the versioned URL,
  `https://github.com/kharioki/nyumbani-launcher/releases/download/v1.3.0-beta.1/nyumbani.apk`).
- Betas have lower version codes than the final release, so `v1.3.0` installs over any 1.3.0 beta.

## If a release build fails

Open the failed run under **Actions → Release** and find the first red step.

| Step | Likely cause | Fix |
|---|---|---|
| Decode signing key | `KEYSTORE_BASE64` missing | Add the secret (step 2 above) |
| Build signed APK, "keystore password was incorrect" | Wrong `KEYSTORE_PASSWORD` or file encoded wrongly | Check locally: `keytool -list -keystore nyumbani-release.jks`; re-encode with `base64 -i` |
| Build signed APK, "No key with alias" | `KEY_ALIAS` doesn't match | `keytool -list` shows the alias |
| Check it isn't debug-signed | One of the password/alias secrets is missing, so Gradle fell back to the debug key | Set all four secrets |
| Build signed APK, compile error | The code on the tagged commit doesn't build | Fix on `main`, then re-tag (below) |

To build the exact release locally: `RELEASE_VERSION=1.2.0 ./gradlew assembleRelease`.

**Re-running:** for a flaky failure, use **Re-run jobs** on the run. If the tagged commit itself
needs fixing, delete the tag (and the release, if one was created), fix, and tag again:

```bash
git push --delete origin v1.2.0
git tag -d v1.2.0
# fix, commit, push
git tag -a v1.2.0 -m "Nyumbani 1.2.0"
git push origin v1.2.0
```

Only do this before anyone has installed the release. Once a version is out, ship a new patch
version instead.

## Pull requests and branches

`build-apk.yml` builds a debug-signed APK on every push to a branch and every pull request, runs
the unit tests, and keeps the APK as a workflow artifact for a few days. It never publishes a release.

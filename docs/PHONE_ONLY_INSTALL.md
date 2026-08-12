# TMLN phone-only build and install guide

This guide lets you build and test TMLN with only a Xiaomi 14T Pro. GitHub builds the app in the cloud, then the phone downloads and installs the resulting debug APK. No Android Studio, cable, or second Android device is required.

## Choose the right route

| Approach | When it fits | Trade-offs | Cost and setup |
|---|---|---|---|
| **Cloud APK build from this repository** | You want the actual current TMLN project on the Xiaomi without a computer. | Requires one Mapbox setup and a short build wait; each APK is a debug build intended for personal testing. | Usually included in GitHub's standard Actions allowance for a small personal project; medium one-time setup. |
| **Borrow a computer briefly** | You want live code debugging, device logs, and rapid changes later. | Requires access to a computer and a USB cable or wireless pairing. | No cloud setup; easiest for active development. |
| **Install an APK shared by somebody else** | Someone you trust can build a specific version for you. | You cannot independently rebuild or inspect that app; do not use this for personal Timeline data unless you trust the source. | Low setup, but not recommended as the long-term TMLN workflow. |

The first route is the best phone-only workflow because the APK is built directly from your own GitHub repository and you can trigger a new build whenever TMLN changes.

## One-time credential setup from the Xiaomi

Open the repository in Chrome on the Xiaomi. If a GitHub setting is not visible in the GitHub mobile app, choose **Open in browser** and use Chrome's **Desktop site** option.

First create two Mapbox tokens in the Mapbox account console:

| GitHub repository secret name | Value to store | Purpose |
|---|---|---|
| `MAPBOX_ACCESS_TOKEN` | A public Mapbox token beginning with `pk.` | Embedded into the personal debug APK so maps can load at runtime. |
| `MAPBOX_DOWNLOADS_TOKEN` | A secret Mapbox token beginning with `sk.` that has only the `Downloads:Read` scope | Lets GitHub download the Mapbox Android dependencies while compiling. |

In GitHub, open the `TMLN` repository and go to **Settings > Secrets and variables > Actions > New repository secret**. Add both names exactly as shown above. GitHub hides the values after you save them. Never paste either token into a source file, GitHub issue, commit, or chat; the public token is safe inside the installed app but the `sk.` download token must remain private.

## Build the APK in GitHub

1. Open the repository's **Actions** tab in Chrome.
2. Select **Build TMLN debug APK**.
3. Tap **Run workflow**, leave the branch as `main`, and confirm **Run workflow**.
4. Open the running job and wait for the green success check. A small app normally completes in several minutes.
5. On the completed job page, open **Artifacts** and download `TMLN-debug-apk`. GitHub provides a ZIP file, which is expected.

The workflow uses the two secrets only while building. It does not write them into the repository or print them in the job log.

## Install on the Xiaomi 14T Pro

1. In the Xiaomi Downloads/File Manager app, locate the downloaded `TMLN-debug-apk` ZIP and extract it.
2. Open the extracted `app-debug.apk` file.
3. If HyperOS blocks the install, choose **Settings** when prompted and allow **Install unknown apps** only for the app that opened the APK, such as Chrome or File Manager. Return to the installer and confirm installation.
4. Open **TMLN**. Keep the phone online for this first launch so Mapbox can load its initial styles and map tiles.
5. After testing, you can turn the **Install unknown apps** permission off again for the browser or file manager.

If Android reports that an app with the same package is already installed but signed differently, uninstall the older TMLN debug app first, then install the newly downloaded APK. Your personal imported journeys are stored inside the app, so export or record anything important before uninstalling a test build.

## First validation on the phone

Start with a seeded journey and verify that the library, story cards, map, route line, stop markers, Cinematic/Satellite style toggle, and playback deck all work. Then import a small GeoJSON or Google Timeline JSON file, beginning with a short date range. Finally, over Wi-Fi, use **Download for offline**, wait until the card says **Map available offline**, turn on airplane mode, force-close/reopen TMLN, and replay the same journey in both map styles.

When reporting an issue, include the TMLN commit or Actions run, whether the phone was online or offline, the journey used, your action immediately before the problem, and a screenshot or screen recording. This is enough for focused fixes even without a computer.

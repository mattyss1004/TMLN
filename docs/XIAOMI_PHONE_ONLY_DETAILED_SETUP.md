# TMLN on a Xiaomi 14T Pro: complete phone-only setup

> **Goal:** Build the current TMLN app in GitHub, install it on this Xiaomi, and perform the first safe map, import, playback, and offline tests without using a computer.

## What you need before starting

Use a reliable Wi-Fi connection, charge the phone above 40%, and have access to the GitHub account that owns the `mattyss1004/TMLN` repository. You will also need a Mapbox account. You do **not** need Android Studio, a USB cable, or a second Android device.

| Item | Why it is needed | Keep private? |
|---|---|---|
| GitHub account | Starts the cloud build and provides the APK download. | Yes, use your normal account security. |
| Mapbox public token (`pk.`) | Allows the installed app to load its maps. | It is embedded in the debug app, so do not treat it as a password. |
| Mapbox secret token (`sk.`) with `Downloads:Read` only | Lets the cloud builder fetch the Mapbox Android SDK. | **Yes. Never send it in chat or commit it.** |
| Xiaomi File Manager or Chrome | Downloads, extracts, and installs the APK. | No special setup until Android requests permission. |

## Part A — create the two Mapbox tokens

### A1. Sign in to Mapbox

1. Open **Chrome** on the Xiaomi.
2. Go to [Mapbox’s access-token page](https://console.mapbox.com/account/access-tokens/).
3. Sign in or create a Mapbox account. Keep this Chrome tab open.

### A2. Obtain the public application token

For the first personal test, use the **Default public token** already shown on the token page. It starts with `pk.`. Tap its copy button; it is a long line of text. Save it temporarily in a private draft or password manager and label it **TMLN public token**.

Do not post this token into a GitHub issue, a source-code file, or chat. Although it is designed to be used inside a mobile app, keeping it only in the GitHub secret prevents accidental reuse elsewhere.

### A3. Create the secret build token

1. On the same Mapbox access-token page, tap **Create a token**.
2. Give it a recognizable name, such as **TMLN GitHub build**.
3. In the **Secret scopes** section, select **`Downloads:Read`** and leave unrelated scopes off.
4. Tap **Create token** and confirm with your Mapbox password if asked.
5. Copy the resulting value beginning with `sk.` immediately. Mapbox shows a secret token only once. Save it in a private temporary location until Part B is complete, then remove that temporary copy.

## Part B — add the Mapbox values to GitHub securely

> GitHub protects repository secrets: the values are available to the build workflow but are not stored in the repository’s visible files. Use Chrome, not necessarily the GitHub mobile app; if a setting is missing, open Chrome’s menu and turn on **Desktop site**.

### B1. Open the repository-secret page

1. In Chrome, sign in to GitHub with the account that owns TMLN.
2. Open this direct address: [TMLN Actions secrets](https://github.com/mattyss1004/TMLN/settings/secrets/actions).
3. If GitHub asks for your password or a verification code, complete it.
4. Tap **New repository secret**.

### B2. Save the public token

1. In **Name**, type exactly: `MAPBOX_ACCESS_TOKEN`
2. In **Secret**, paste the public value beginning with `pk.`.
3. Tap **Add secret**.
4. Confirm that `MAPBOX_ACCESS_TOKEN` appears in the secrets list. GitHub will not show its value again, which is normal.

### B3. Save the secret build token

1. Tap **New repository secret** again.
2. In **Name**, type exactly: `MAPBOX_DOWNLOADS_TOKEN`
3. In **Secret**, paste the Mapbox value beginning with `sk.`.
4. Tap **Add secret**.
5. Confirm that `MAPBOX_DOWNLOADS_TOKEN` appears in the secrets list.
6. Delete any temporary note that held the `sk.` token. Do not share it with anyone, including this chat.

The two names must match exactly. Spelling, underscores, and capital letters matter.

## Part C — start the cloud build

1. Open the [TMLN Actions page](https://github.com/mattyss1004/TMLN/actions) in Chrome.
2. If GitHub displays an **I understand my workflows, go ahead and enable them** button, tap it once.
3. In the workflow list, select **Build TMLN debug APK**.
4. Tap **Run workflow**. A small panel opens.
5. Leave the branch as **`main`** and tap the final **Run workflow** button.
6. Wait a few seconds, then tap the newest run at the top of the list. It should be named **Build TMLN debug APK**.
7. Wait until the run has a green check mark. Do not close the browser tab permanently, but it is safe to switch apps while the job runs.

A successful build page has a section called **Artifacts** near the bottom of its summary. Tap **TMLN-debug-apk** to download it. The phone downloads a ZIP file; that is expected.

## Part D — install the APK on HyperOS

1. Open **File Manager** or **Downloads** on the Xiaomi.
2. Find the newly downloaded file named similar to `TMLN-debug-apk.zip`.
3. Tap it and choose **Extract**. Open the extracted folder.
4. Tap `app-debug.apk`.
5. If Android says that Chrome or File Manager is not allowed to install unknown apps, tap **Settings** in that prompt. Turn on **Allow from this source**, return, and tap the APK again.
6. Tap **Install**, then **Open**.

After TMLN is installed, return to the same Android permission screen later and turn **Allow from this source** back off. This permission is only needed to install APKs downloaded outside Google Play.

If the installer reports that another package conflicts with this app, you have an earlier TMLN build signed differently. Before uninstalling it, copy or test any important imported journeys because an uninstall removes the app’s local database. Then uninstall the earlier TMLN app and repeat the APK installation.

## Part E — first-run test, in order

Keep Wi-Fi on for the entire initial test.

| Step | In TMLN | Expected result | Stop and report if |
|---|---|---|---|
| 1 | Open the app. | Journey library opens, including sample journeys. | The app crashes or only shows a permanent Mapbox-setup message. |
| 2 | Open a seeded journey, such as Paris or Tokyo. | The route appears on the 3D map. | The map is blank after about one minute on working Wi-Fi. |
| 3 | Switch **Cinematic** and **Satellite**. | Both styles load; the route and stop markers remain visible. | One style produces an error or a fully blank map. |
| 4 | Press Play and select Follow. | Traveller moves along the route and the camera follows smoothly. | Playback freezes, controls overlap, or camera jumps badly. |
| 5 | Return to the library and test a preset journey/import. | A new journey is created and appears in the list. | The import is rejected or creates an empty route. |

## Part F — import your own Timeline data carefully

Begin with a short period, such as one day or one weekend. Do **not** start with your full lifetime Timeline archive on the first phone test.

Google exports are usually downloaded as a ZIP file. In Xiaomi File Manager, extract it first. In TMLN, open the import control, choose the extracted `.json` or `.geojson` file, and give it a clear title. If the file picker cannot see the export, it is usually because the file is still inside the ZIP archive or was saved in an unsupported cloud location; move the extracted JSON file to **Downloads** first.

After import, verify the route has a sensible starting point, ending point, and length before trusting the story highlights. Your imported data remains local to the installed app; do not upload the raw export to public places.

## Part G — verify offline maps

1. Reopen a journey with a route you have already viewed online.
2. On Wi-Fi, scroll below the playback deck and tap **Download for offline**.
3. Keep TMLN open and wait until the card states **Map available offline** and shows 100%.
4. Turn on **Airplane mode**.
5. Swipe TMLN away from the recent-apps screen, then reopen it.
6. Open the same journey, test **Cinematic** and **Satellite**, then play the route in Follow mode.

The downloaded route corridor and normal map zoom should remain available. Areas far away from the route, or zoom levels far beyond the normal playback view, are intentionally not downloaded.

## Troubleshooting guide

| What you see | Likely cause | What to do next |
|---|---|---|
| **Build workflow is not listed** | You are on an old page, wrong branch, or GitHub Actions is not enabled. | Refresh the Actions page, choose `main`, and use Chrome Desktop site. Confirm the workflow file appears in the repository. |
| **Build fails at “Verify required Mapbox secrets”** | A secret is missing or named incorrectly. | Return to GitHub Actions secrets and check the exact two uppercase names from Part B. Re-add rather than guessing the hidden values. |
| **Build fails while downloading Mapbox dependencies** | The `sk.` token is invalid or lacks `Downloads:Read`. | Create a new secret Mapbox token with exactly that scope, replace only `MAPBOX_DOWNLOADS_TOKEN` in GitHub, and run again. |
| **APK will not install** | The file is still inside the ZIP, installation-from-source is blocked, or a prior build conflicts. | Extract the ZIP, follow the installer’s Settings link, or uninstall the previous TMLN test app after protecting important local data. |
| **App opens but says Mapbox configuration is required** | The public token was missing when the APK was built. | Check `MAPBOX_ACCESS_TOKEN`, then run a new GitHub build and install its new APK. |
| **Map is blank but app is online** | The public token may be incorrect, revoked, or unavailable to the build. | Re-create/copy a valid `pk.` public token, update the GitHub secret, build a fresh APK, and retry. |
| **Offline card never reaches 100%** | Weak network, the app was closed during download, or Mapbox resources could not load. | Stay on Wi-Fi with the app open, retry once, and note the exact message. |
| **Import cannot find a file** | The Google export is still zipped or not local on the phone. | Extract it and move the `.json`/`.geojson` file into Downloads before using TMLN’s document picker. |

## What to send after your first attempt

Reply with exactly one of these, plus a screenshot if there is an error: **“I am at Part A/B/C/D”**, **“the workflow failed at [step]”**, **“the APK installed but [problem]”**, or **“the first test passed.”** Do not send Mapbox token values or full Timeline export files.

## Official references

1. [Mapbox access tokens](https://docs.mapbox.com/help/dive-deeper/access-tokens/)
2. [GitHub Actions secrets](https://docs.github.com/actions/security-guides/using-secrets-in-github-actions)
3. [GitHub workflow-dispatch runs](https://docs.github.com/actions/managing-workflow-runs/manually-running-a-workflow)
4. [Xiaomi developer-options guidance](https://www.mi.com/global/support/faq/details/KA-168765/)

# TMLN real-device testing: Xiaomi 14T Pro

This guide uses the Xiaomi 14T Pro as the primary TMLN test device. The project supports Android 8.0 and above, while the Xiaomi 14T Pro is a modern Android device, so no compatibility workaround is expected for the first install.

## 1. Prepare the development computer

Install the current stable Android Studio release and open the `TMLN` project. When Android Studio asks to install missing components, accept the Android SDK Platform 35, Build Tools, Platform Tools, and the bundled JDK. Let Gradle Sync complete before connecting the phone.

Copy `local.properties.example` to `local.properties` in the repository root. This local file is ignored by Git and must never be committed.

```properties
# Public token embedded in the debug app at runtime.
MAPBOX_ACCESS_TOKEN=pk.YOUR_TMLN_PUBLIC_TOKEN

# Secret token used only by Gradle to download Mapbox Android dependencies.
MAPBOX_DOWNLOADS_TOKEN=sk.YOUR_DOWNLOADS_READ_TOKEN
```

Create a dedicated Mapbox public token named `TMLN Android Debug` and a separate secret token with only the `Downloads:Read` scope. Do not send the secret token in chat, add it to GitHub, or paste it into source code.

## 2. Prepare the Xiaomi 14T Pro

On the phone, open **Settings > About phone > Detailed info and specs**, then tap **OS/MIUI version** repeatedly to enable Developer options. Open **Settings > Additional settings > Developer options** and turn on **USB debugging**. If HyperOS offers **USB debugging (Security settings)** or **Install via USB**, turn it on for the initial debug installation as well. Connect the phone with a data-capable USB-C cable, unlock it, select the appropriate USB connection mode if prompted, and accept the RSA debugging fingerprint from the computer.

If USB is inconvenient, Android supports wireless debugging on Android 11 and above. Keep the phone and computer on the same trusted Wi-Fi network, enable **Wireless debugging** in Developer options, and pair the device from Android Studio's Device Manager.

## 3. Install TMLN

In Android Studio, select the Xiaomi 14T Pro from the device selector and press **Run**. Choose the `app` debug configuration. Android Studio builds, installs, and opens TMLN on the phone. For the first run, keep the phone online so Mapbox can load map styles and initial tiles.

If the device does not appear, use **Tools > Troubleshoot Device Connections** in Android Studio. Confirm the phone is unlocked, the cable transfers data, USB debugging is enabled, and the RSA authorization was accepted. Wireless pairing is a good fallback after a successful USB connection.

## 4. First real-device validation session

Start with an easy visual pass. Open each seeded journey, move the timeline, switch between Cinematic and Satellite styles, use Overview, Follow, and Orbit camera modes, and rotate the phone once. Confirm the map remains responsive, the route line is clean, and the playback deck does not overlap the map or system navigation.

Next, test data import with a small JSON or GeoJSON file. Confirm the new journey appears in the library, has sensible route geometry, identifies at least one meaningful stop where expected, and can be deleted. Only after this passes, try a real Google Timeline export; begin with a small date range, not the entire personal archive.

Finally, test the offline pack. On Wi-Fi, open a journey and use **Download for offline**. Wait for the status to become **Map available offline**, then switch on airplane mode, fully close and reopen TMLN, and replay the same journey in both Cinematic and Satellite styles. The route and downloaded geographic corridor should remain available. Remove the offline pack afterwards and confirm the journey itself remains safely stored in the library.

## 5. Report test feedback

For each issue, record the journey name, whether the phone was online or offline, the map style, the action just before the issue, and a screenshot or short screen recording. The most useful first feedback is whether import, 3D map loading, follow-camera motion, playback, and offline replay feel natural on the actual Xiaomi screen.

## Security and privacy

Google Timeline exports are deeply personal. Keep export files on your own computer, import only the date range you need, and avoid uploading exports to external services. TMLN stores the journey data locally on the device. Mapbox map resources are downloaded only when you choose a map or offline pack.

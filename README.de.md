[🇬🇧 English](README.md) | [🇧🇷 Português](README.pt-BR.md) | [🇪🇸 Español](README.es.md) | [🇫🇷 Français](README.fr.md) | 🇩🇪 **Deutsch**

<p align="center">
  <img src="docs/logo.png" alt="KidsHome-Logo" width="128" height="128" />
</p>

<h1 align="center">KidsHome</h1>

<p align="center"><strong>Ein schlanker Kinder-Startbildschirm für Android-Handys, Tablets und Amazon Fire OS.</strong></p>

<p align="center">
  <a href="https://github.com/appstudio42/KidsHome/releases/latest"><img src="https://img.shields.io/github/v/release/appstudio42/KidsHome?label=latest%20release" alt="Neueste Version" /></a>
  <a href="https://github.com/appstudio42/KidsHome/releases"><img src="https://img.shields.io/github/downloads/appstudio42/KidsHome/total" alt="Downloads" /></a>
  <a href="https://github.com/appstudio42/KidsHome/actions/workflows/android.yml"><img src="https://img.shields.io/github/actions/workflow/status/appstudio42/KidsHome/android.yml?branch=master&label=CI" alt="CI-Status" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/appstudio42/KidsHome" alt="Lizenz" /></a>
  <img src="https://img.shields.io/badge/min%20Android-5.1%20(API%2022)-green" alt="Min. Android 5.1 API 22" />
</p>

<p align="center">
  <a href="https://github.com/appstudio42/KidsHome/releases/latest"><strong>⬇ APK herunterladen (neueste Version)</strong></a>
</p>

KidsHome (v1.5.1) macht aus einem Ersatzhandy oder -tablet einen kinderfreundlichen Startbildschirm: nur erlaubte Apps und Websites, mit täglichem Bildschirmzeitlimit, Bettzeit-Sperre, Notausstieg per Eltern-PIN und optionaler LAN-Fernbedienung im Telefonbrowser. Es wählt den besten Enforcement-Modus für das Gerät (oder Sie überschreiben ihn unter **Geräteeinrichtung**). Gepflegt von [App Studio 42](https://appstudio42.github.io).

<p align="center">
  <img src="docs/media/setup.gif" alt="KidsHome-Einrichtung: PIN erstellen, Apps wählen, Bildschirmzeit setzen, Kindermodus starten" width="720" />
</p>

<p align="center"><em>Einrichtungsablauf — PIN erstellen → Apps wählen → Bildschirmzeit setzen → Kindermodus starten</em></p>

## Screenshots

Echte Aufnahmen von einem Android-Tablet-Emulator (1280×800) mit dem Demo-Kindernamen **Theo**.

| Kinder-Home | Zeit abgelaufen | Eltern-PIN |
|:---:|:---:|:---:|
| ![Kinder-Home](docs/screenshots/01-kid-home.png) | ![Zeit abgelaufen](docs/screenshots/02-time-up.png) | ![PIN](docs/screenshots/03-pin-entry.png) |
| Kinder-Home-Raster | Bildschirmzeit-Sperre | Notausstieg per PIN |

| Kindermodus | Bildschirmzeit | Geräteeinrichtung |
|:---:|:---:|:---:|
| ![Kindermodus](docs/screenshots/04-parent-kid-mode.png) | ![Bildschirmzeit](docs/screenshots/05-screen-time-settings.png) | ![Einrichtung](docs/screenshots/06-device-setup.png) |
| Eltern-Einstellungen | Limits & Bettzeit | Modus-Checkliste |

| Erlaubte Apps | Hauptansicht |
|:---:|:---:|
| ![Erlaubte Apps](docs/screenshots/07-allowed-apps.png) | ![Hauptansicht](docs/screenshots/08-main-hub.png) |
| Apps für das Raster wählen | KidsHome-Hauptbildschirm |

| Zeitpläne | Limits pro App | Zeit verdienen |
|:---:|:---:|:---:|
| ![Schedules](docs/screenshots/09-schedules.png) | ![App limits](docs/screenshots/10-app-limits.png) | ![Earn time](docs/screenshots/11-earn-time.png) |
| Routinen & Kategorien | Tägliche Minuten pro App | Aufgaben & Bonusminuten |

| Wochenbericht | Feier | Bettzeit |
|:---:|:---:|:---:|
| ![Report](docs/screenshots/12-weekly-report.png) | ![Celebration](docs/screenshots/13-star-celebration.png) | ![Bedtime](docs/screenshots/14-bedtime.png) |
| Lokales Nutzungsdiagramm | Stern verdient | Freundliche Sperre |

## Funktionen

- **Drei Enforcement-Modi** (automatisch erkannt, manuell überschreibbar):
  - Accessibility-Home-Hijack — Amazon-Fire-Tablets
  - Standard-Launcher — normales Android
  - Device Owner + Lock Task — stärkster Kiosk
- **Kinder-Home** — großes Icon-Raster erlaubter Apps + Website-Shortcuts, oder Sperre auf eine einzelne App
- **Bildschirmzeit** — Tageslimit, Bettzeitfenster, Warnung, freundliche Sperrbildschirm, PIN zum Verlängern / Deaktivieren für heute
- **Zeitpläne / Routinen** — Wochentagsfenster (Schule, Lesen, Schlafen) mit App-Kategorien
- **Limits pro App** — tägliche Minuten pro erlaubter App zusätzlich zum Gesamttageslimit; gesperrte Kacheln bei Erschöpfung
- **Zeit verdienen / Sterne** — Bonusminuten per PIN und Aufgabenliste mit Feier + Sternzähler
- **Wochenbericht** — lokaler Verlauf pro App/Tag (~4 Wochen), Canvas-Diagramm, löschen / CSV-Export
- **Fernbedienung (LAN)** — lokale Webseite im Tablet-WLAN (Port 8642); QR/Token oder PIN; Zeit hinzufügen, sperren/entsperren; standardmäßig aus; keine Cloud
- **Notausstieg per PIN** — 5 Tipps oben rechts (Overlay) oder Zurück ~3 s halten; Device-Owner-Pfad ruft `stopLockTask` sauber auf
- Überlebt Neustarts; warnt, wenn Accessibility aus ist und der Modus sie braucht
- Optionale Accessibility-Sperre nicht erlaubter Apps als **zusätzliche Schicht** in Launcher- / Device-Owner-Modi

## Fernbedienung

Standardmäßig aus. Im Eltern-Hub **Fernbedienung** öffnen, aktivieren und den QR scannen (oder `http://<tablet-ip>:8642`) vom Telefon im **selben WLAN**. Die Seite zeigt heutige Nutzung, Sterne und Status; +15/+30/+60 Min., **Jetzt sperren** oder **Entsperren**. Der QR-Link tauscht das Token einmal gegen ein Sitzungscookie und leitet auf eine URL ohne Token um. Danach gilt das Cookie; bei abgelaufener Sitzung den QR erneut scannen oder die PIN eingeben (PIN wird nie angezeigt). Keine Cloud. Aktionen erscheinen im Wochenbericht als **Fernbedienung**.

| Einstellungen | Telefonseite | Nach +Zeit | Fernsperre |
|:---:|:---:|:---:|:---:|
| ![Remote settings](docs/screenshots/1.5.0/01-remote-settings-qr.png) | ![Remote page](docs/screenshots/1.5.0/02-remote-phone-page.png) | ![After add](docs/screenshots/1.5.0/03-remote-after-add-time.png) | ![Remote lock](docs/screenshots/1.5.0/04-remote-lock-tablet.png) |

## Unterstützte Geräte

| Modus | Am besten für | Funktionsweise |
|------|---------------|----------------|
| **Accessibility-Home-Hijack** | Amazon-Fire-Tablets | AccessibilityService leitet Home / Fire-Launcher um |
| **Standard-Launcher** | Android-Handys und -Tablets | KidsHome als Home-App (`CATEGORY_HOME`) |
| **Device Owner + Lock Task** | Stärkster Kiosk | Einmalig `dpm set-device-owner`, dann native Lock-Task-Whitelist |

> Primäres Testgerät: Amazon Fire 7 (9. Gen., `mustang`), Fire OS 7.3.x = Android 9 (SDK 28). **iOS ist außerhalb des Umfangs.**

GPL-3-Fork von [Meems33/LauncherHijack](https://github.com/Meems33/LauncherHijack) (Launcher Replacer), basierend auf [BaronKiko/LauncherHijack](https://github.com/BaronKiko/LauncherHijack).

## Schnellstart

1. Laden Sie die APK von der [neuesten Version](https://github.com/appstudio42/KidsHome/releases/latest) herunter (oder das GitHub-Actions-Artefakt `KidsHome-release-apk`).
2. Installieren (gleiche CI-Testsignatur → Upgrades behalten Daten):

   ```bash
   adb install -r KidsHome-release.apk
   ```

3. KidsHome öffnen → Menü → **Geräteeinrichtung** und Checkliste für Ihren Modus abschließen.
4. Menü → **Kindermodus** → PIN erstellen, Apps / Bildschirmzeit konfigurieren, aktivieren.

### A) Amazon Fire OS (Accessibility-Hijack)

Fire OS erlaubt **keinen** Austausch des Standard-Launchers. KidsHome behält den klassischen AccServ-Home-Hijack.

1. Accessibility aktivieren (UI **oder** ADB).

   **UI:** Einstellungen → Bedienungshilfen → **KidsHome home detection** → aktivieren.

   **ADB-Komponente:**

   ```text
   io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
   ```

   ```bash
   adb shell settings put secure enabled_accessibility_services io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
   adb shell settings put secure accessibility_enabled 1
   ```

2. (Optional, für den Ecken-Ausstieg)

   ```bash
   adb shell appops set io.github.marcelsavegnago.kidshome SYSTEM_ALERT_WINDOW allow
   ```

3. In KidsHome zu **Kindermodus** gehen (oder einen Fallback-Launcher wählen, wenn Sie den Hijack ohne Kindermodus nutzen).
4. Einmal neu starten. Home drücken — Kinder-Home / gewähltes Ziel sollte öffnen (kurzer Flash des Fire-Launchers möglich).

### B) Normales Android (Standard-Launcher)

1. **Geräteeinrichtung** öffnen → Modus **Automatisch** oder **Standard-Launcher**.
2. **Als Standard-Home festlegen** tippen (`RoleManager.ROLE_HOME` ab Android 10, sonst Home-Einstellungen).
3. **KidsHome** im Systemdialog wählen.
4. Overlay- / Akku-Ausnahme wie aufgefordert gewähren.
5. Optional Accessibility als **zusätzliche Schicht** aktivieren, um unerlaubte Apps zurückzuwerfen.
6. **Kindermodus** aktivieren. Home sollte das Kinder-Raster öffnen.

### C) Device Owner (stärkster Modus)

Device Owner lässt sich nur setzen, wenn das Gerät **keine Konten** hat (Google-/Amazon-Konten entfernen) oder nach einem **Werksreset**, und typischerweise bevor andere Geräteadmins aktiv sind.

1. KidsHome installieren.
2. Device Owner provisionieren (Komponente muss exakt passen):

   ```bash
   adb shell dpm set-device-owner io.github.marcelsavegnago.kidshome/.KidsHomeDeviceAdminReceiver
   ```

   Derselbe Befehl wird in **Geräteeinrichtung** angezeigt / ist kopierbar.

3. In **Geräteeinrichtung** Device Owner = ja bestätigen. Optional Statusleiste / Keyguard während der Sperre deaktivieren.
4. **Kindermodus** aktivieren. KidsHome wird:
   - `setLockTaskPackages` für erlaubte Apps + Browser + sich selbst setzen
   - `addPersistentPreferredActivity` für HOME → `KidHomeActivity`
   - `startLockTask` vom Kinder-Home aus starten (Kiosk)
5. Der PIN-Notausstieg ruft `stopLockTask` auf und kann den Kindermodus deaktivieren.

## Bildschirmzeit, Zeitpläne & Zeit verdienen

Im **Eltern-Hub** (PIN-geschützt) können Sie konfigurieren:

- **Tägliches Bildschirmzeitlimit** (Minuten) — das Home zeigt die Restzeit; bei Erreichen erscheint die Sperre „Zeit abgelaufen“.
- **Bettzeit-Sperre** — Schlaffenster (z. B. 20:00–07:00); während der Bettzeit lautet die Meldung „Schlafenszeit“.
- **Zeitpläne / Routinen** — Wochentagsfenster, die nur bestimmte Kategorien zeigen oder alles bis zum Morgen sperren.
- **Limits pro App** — z. B. 30 Min/Tag für Video, unbegrenzt für Bildung.
- **Zeit verdienen** — Bonusminuten gewähren (gesamt oder pro App) oder Aufgaben abhaken, um Sterne zu vergeben.
- **Wochenbericht** — lokale Nutzung nach Tag/App, Summen vs. Limits, Verlauf löschen, optional CSV.

Eltern entsperren mit dem PIN, um 15/30 Minuten hinzuzufügen, das Limit für heute oder die Bettzeit für diese Nacht zu deaktivieren. Alles bleibt auf dem Gerät (kein Netz / Analytics / Cloud).

## Notausstieg per PIN

Funktioniert in **allen** Enforcement-Modi:

- **5× schnell oben rechts tippen** (braucht „Über anderen Apps einblenden“), oder
- **Zurück ca. 3 Sekunden halten**

Danach den Eltern-PIN eingeben, um den Kindermodus zu deaktivieren, KidsHome-Einstellungen zu öffnen oder im Kindermodus zu bleiben.

## FAQ & Fehlerbehebung

### Wie verlasse ich den Kindermodus per ADB?

```bash
adb shell am force-stop io.github.marcelsavegnago.kidshome
# Oder Einstellungen zurücksetzen:
adb shell pm clear io.github.marcelsavegnago.kidshome
```

Wenn Device-Owner-Lock-Task aktiv ist, zuerst Device Owner entfernen (siehe unten) oder in der UI mit dem PIN entsperren.

### Aktualisieren ohne Einstellungsverlust

Dieselbe Signaturschlüssel (standardmäßig CI-Test-Keystore) verwenden und mit Replace neu installieren:

```bash
adb install -r KidsHome-release.apk
```

Ein Wechsel der Signaturschlüssel erfordert vorher eine Deinstallation.

### Wie entferne ich Device Owner?

Unter **Geräteeinrichtung** → **Device Owner entfernen** (PIN, wenn Kindermodus an ist), oder die App ruft `clearDeviceOwnerApp` auf. Danach:

```bash
adb uninstall io.github.marcelsavegnago.kidshome
```

Ohne vorheriges Löschen von Device Owner verweigert Android die Deinstallation.

### Rückgängig / Deinstallieren (ohne Device Owner)

```bash
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0
adb uninstall io.github.marcelsavegnago.kidshome
```

### Bekannte Einschränkungen

- Fire: `com.amazon.firelauncher` lässt sich oft nicht deaktivieren; Hijack ist die einzige Home-Option.
- Accessibility- / Launcher-Modi sind **kein** vollständiges MDM — ADB / Erzwingen des Beendens können umgehen.
- Device Owner ist stark, braucht aber ein sauberes Gerät und einmalig ADB; Owner muss vor der Deinstallation entfernt werden.
- Nach Low-Memory-Kills auf Fire kann der erste Home-Druck langsam sein — ggf. erneut drücken.

## Aus dem Quellcode bauen

```bash
./gradlew assembleRelease
```

Benötigt **JDK 17+** und Android-SDK (`compileSdk` / `targetSdk` 34). `minSdk 22` (Fire 7 / SDK 28 voll unterstützt). Ohne `keystore.properties` oder `-PciSignRelease=true` bleibt das Release-APK unsigniert (so baut F-Droid). Store-Texte liegen in `fastlane/metadata/android/`. Ein fdroiddata-Entwurf liegt in `docs/fdroid/`.

### CI-Signierung

| Modus | Wann | Signatur |
|------|------|----------|
| **Unsigniert** | Keine `keystore.properties` und kein `-PciSignRelease=true` | Keine — F-Droid signiert dieses APK |
| **Stabiler CI-Testschlüssel** | `-PciSignRelease=true` und keine `SIGNING_*`-Secrets | `keystore/debug.keystore` (im Repo) — gleiche Signatur über CI-Läufe, daher behält `adb install -r` Einstellungen |
| **Release-Schlüssel** | `keystore.properties` vorhanden (CI schreibt sie, wenn `SIGNING_*`-Secrets gesetzt sind) | Ihr privater Keystore |

| Secret | Zweck |
|--------|-------|
| `SIGNING_KEYSTORE_BASE64` | Base64-kodiertes `.jks` / `.keystore` |
| `SIGNING_KEY_ALIAS` | Schlüsselalias |
| `SIGNING_KEY_STORE_PASSWORD` | Keystore-Passwort |
| `SIGNING_KEY_PASSWORD` | Schlüsselpasswort |

Siehe `keystore/README.md`.

## Veröffentlichen

1. `versionName` / `versionCode` in `app/build.gradle` erhöhen.
2. Tag `vX.Y.Z` pushen:

   ```bash
   git tag v1.5.1
   git push origin v1.5.1
   ```

3. GitHub Actions (`.github/workflows/android.yml`) baut die Release-APK und hängt sie an die GitHub Release.

## Mitwirken

Issues und Pull Requests sind willkommen. Änderungen fokussiert halten, bei Enforcement / Accessibility auf einem echten Fire-Tablet oder Emulator testen und Englisch für Code und Kommentare bevorzugen.

Screenshot-/GIF-Regenerierung (optional): siehe [`.github/workflows/screenshots.yml`](.github/workflows/screenshots.yml) und [`docs/capture-screenshots.sh`](docs/capture-screenshots.sh).

## Lizenz

KidsHome steht unter der **GNU GPL v3** — siehe [LICENSE](LICENSE).

Credits: BaronKiko (LauncherHijack), Meems33 / WhiteRaven777 (Launcher Replacer).

## Changelog

Siehe [CHANGELOG.md](CHANGELOG.md) für Versionshinweise (aktuell: **1.5.1**).

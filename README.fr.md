[🇬🇧 English](README.md) | [🇧🇷 Português](README.pt-BR.md) | [🇪🇸 Español](README.es.md) | 🇫🇷 **Français** | [🇩🇪 Deutsch](README.de.md)

<p align="center">
  <img src="docs/logo.png" alt="Logo KidsHome" width="128" height="128" />
</p>

<h1 align="center">KidsHome</h1>

<p align="center"><strong>Un écran d’accueil léger pour enfants sur téléphones Android, tablettes et Amazon Fire OS.</strong></p>

<p align="center">
  <a href="https://github.com/appstudio42/KidsHome/releases/latest"><img src="https://img.shields.io/github/v/release/appstudio42/KidsHome?label=latest%20release" alt="Dernière version" /></a>
  <a href="https://github.com/appstudio42/KidsHome/releases"><img src="https://img.shields.io/github/downloads/appstudio42/KidsHome/total" alt="Téléchargements" /></a>
  <a href="https://github.com/appstudio42/KidsHome/actions/workflows/android.yml"><img src="https://img.shields.io/github/actions/workflow/status/appstudio42/KidsHome/android.yml?branch=master&label=CI" alt="Statut CI" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/appstudio42/KidsHome" alt="Licence" /></a>
  <img src="https://img.shields.io/badge/min%20Android-5.1%20(API%2022)-green" alt="Android minimum 5.1 API 22" />
</p>

<p align="center">
  <a href="https://github.com/appstudio42/KidsHome/releases/latest"><strong>⬇ Télécharger l’APK (dernière version)</strong></a>
</p>

KidsHome (v1.5.1) transforme un téléphone ou une tablette de rechange en écran d’accueil adapté aux enfants : uniquement les apps et sites que vous autorisez, avec limite quotidienne de temps d’écran, verrouillage au coucher, sortie d’urgence par PIN parental et contrôle distant optionnel sur le réseau local depuis le navigateur du téléphone. Il choisit le meilleur mode d’application pour l’appareil (ou vous le définissez dans **Configuration de l’appareil**). Maintenu par [App Studio 42](https://appstudio42.github.io).

<p align="center">
  <img src="docs/media/setup.gif" alt="Configuration KidsHome : créer un PIN, choisir les apps, définir le temps d’écran, entrer en mode enfant" width="720" />
</p>

<p align="center"><em>Parcours de configuration — créer le PIN → choisir les apps → définir le temps d’écran → entrer en mode enfant</em></p>

## Captures d’écran

Captures réelles depuis un émulateur Android tablette (1280×800) avec le prénom d’exemple **Theo**.

| Accueil enfant | Temps écoulé | PIN parental |
|:---:|:---:|:---:|
| ![Accueil enfant](docs/screenshots/01-kid-home.png) | ![Temps écoulé](docs/screenshots/02-time-up.png) | ![PIN](docs/screenshots/03-pin-entry.png) |
| Grille d’accueil | Verrouillage temps d’écran | Sortie d’urgence PIN |

| Mode enfant | Temps d’écran | Configuration de l’appareil |
|:---:|:---:|:---:|
| ![Mode enfant](docs/screenshots/04-parent-kid-mode.png) | ![Temps d’écran](docs/screenshots/05-screen-time-settings.png) | ![Configuration](docs/screenshots/06-device-setup.png) |
| Réglages parents | Limites et coucher | Liste de contrôle |

| Apps autorisées | Hub principal |
|:---:|:---:|
| ![Apps autorisées](docs/screenshots/07-allowed-apps.png) | ![Hub](docs/screenshots/08-main-hub.png) |
| Choisir les apps de la grille | Écran principal KidsHome |

| Horaires | Limites par app | Gagner du temps |
|:---:|:---:|:---:|
| ![Schedules](docs/screenshots/09-schedules.png) | ![App limits](docs/screenshots/10-app-limits.png) | ![Earn time](docs/screenshots/11-earn-time.png) |
| Routines et catégories | Minutes quotidiennes par app | Tâches et minutes bonus |

| Rapport hebdomadaire | Célébration | Coucher |
|:---:|:---:|:---:|
| ![Report](docs/screenshots/12-weekly-report.png) | ![Celebration](docs/screenshots/13-star-celebration.png) | ![Bedtime](docs/screenshots/14-bedtime.png) |
| Graphique d’usage local | Étoile gagnée | Verrouillage bienveillant |

## Fonctionnalités

- **Trois modes d’application** (détection auto, avec surcharge manuelle) :
  - Détournement Home par accessibilité — tablettes Amazon Fire
  - Lanceur par défaut — Android classique
  - Device Owner + lock task — kiosque le plus strict
- **Accueil enfant** — grande grille d’apps autorisées + raccourcis web, ou verrouillage sur une seule app
- **Temps d’écran** — limite quotidienne, plage de coucher, avertissement, écran de verrouillage bienveillant, PIN pour ajouter du temps / désactiver pour aujourd’hui
- **Horaires / routines** — créneaux par jour (école, lecture, coucher) avec catégories d’apps
- **Limites par app** — minutes quotidiennes par app autorisée en plus de la limite totale ; tuiles verrouillées une fois épuisées
- **Gagner du temps / étoiles** — minutes bonus avec PIN et liste de tâches avec célébration + compteur d’étoiles
- **Rapport hebdomadaire** — historique local par app/jour (~4 semaines), graphique Canvas, effacer / exporter CSV
- **Contrôle distant (LAN)** — page web sur le Wi‑Fi de la tablette (port 8642) ; QR/jeton ou PIN ; ajouter du temps, verrouiller/déverrouiller ; désactivé par défaut ; pas de cloud
- **Sortie d’urgence PIN** — 5 taps en haut à droite (overlay) ou maintien de Retour ~3 s ; en Device Owner appelle `stopLockTask` proprement
- Survive au redémarrage ; avertit si l’accessibilité est désactivée quand le mode en a besoin
- Blocage optionnel par accessibilité des apps non autorisées comme **couche supplémentaire** en modes lanceur / Device Owner

## Contrôle distant

Désactivé par défaut. Dans le hub parental, ouvrez **Contrôle distant**, activez-le, puis scannez le QR (ou ouvrez `http://<ip-tablette>:8642`) depuis un téléphone sur le **même Wi‑Fi**. La page affiche l’usage du jour, les étoiles et l’état ; ajoutez +15/+30/+60 min, **Verrouiller maintenant** ou **Déverrouiller**. Le lien du QR échange le jeton une seule fois contre un cookie de session, puis redirige vers une URL sans jeton. Ensuite la page utilise le cookie ; si la session est terminée, scannez à nouveau le QR ou saisissez le PIN (le PIN n’est jamais affiché). Pas de cloud. Les actions apparaissent dans le rapport hebdomadaire comme **contrôle distant**.

| Réglages | Page téléphone | Après +temps | Verrou distant |
|:---:|:---:|:---:|:---:|
| ![Remote settings](docs/screenshots/1.5.0/01-remote-settings-qr.png) | ![Remote page](docs/screenshots/1.5.0/02-remote-phone-page.png) | ![After add](docs/screenshots/1.5.0/03-remote-after-add-time.png) | ![Remote lock](docs/screenshots/1.5.0/04-remote-lock-tablet.png) |

## Appareils pris en charge

| Mode | Idéal pour | Fonctionnement |
|------|------------|----------------|
| **Détournement Home (accessibilité)** | Tablettes Amazon Fire | AccessibilityService redirige Home / lanceur Fire |
| **Lanceur par défaut** | Téléphones et tablettes Android | KidsHome comme app Home (`CATEGORY_HOME`) |
| **Device Owner + lock task** | Kiosque le plus strict | `dpm set-device-owner` une fois + liste blanche native |

> Appareil de test principal : Amazon Fire 7 (9e gén., `mustang`), Fire OS 7.3.x = Android 9 (SDK 28). **iOS hors périmètre.**

Fork GPL-3 de [Meems33/LauncherHijack](https://github.com/Meems33/LauncherHijack) (Launcher Replacer), basé sur [BaronKiko/LauncherHijack](https://github.com/BaronKiko/LauncherHijack).

## Démarrage rapide

1. Téléchargez l’APK de la [dernière version](https://github.com/appstudio42/KidsHome/releases/latest) (ou l’artefact `KidsHome-release-apk` de GitHub Actions).
2. Installez (la même signature de test CI conserve les données lors des mises à jour) :

   ```bash
   adb install -r KidsHome-release.apk
   ```

3. Ouvrez KidsHome → menu → **Configuration de l’appareil** et terminez la checklist de votre mode.
4. Menu → **Mode enfant** → créez le PIN, configurez apps / temps d’écran, activez.

### A) Amazon Fire OS (détournement accessibilité)

Fire OS **ne permet pas** de remplacer le lanceur par défaut. KidsHome conserve le détournement classique via AccServ.

1. Activez l’accessibilité (UI **ou** ADB).

   **UI :** Paramètres → Accessibilité → **KidsHome home detection** → activer.

   **Composant ADB :**

   ```text
   io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
   ```

   ```bash
   adb shell settings put secure enabled_accessibility_services io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
   adb shell settings put secure accessibility_enabled 1
   ```

2. (Optionnel, pour la sortie par coin)

   ```bash
   adb shell appops set io.github.marcelsavegnago.kidshome SYSTEM_ALERT_WINDOW allow
   ```

3. Dans KidsHome, allez dans **Mode enfant** (ou choisissez un lanceur de secours si vous utilisez le détournement sans mode enfant).
4. Redémarrez une fois. Appuyez sur Home — l’accueil enfant / la cible choisie devrait s’ouvrir (un bref flash du lanceur Fire est possible).

### B) Android classique (lanceur par défaut)

1. Ouvrez **Configuration de l’appareil** → mode **Automatique** ou **Lanceur par défaut**.
2. Appuyez sur **Définir comme Home par défaut** (`RoleManager.ROLE_HOME` sur Android 10+, sinon les paramètres Home).
3. Choisissez **KidsHome** dans le sélecteur système.
4. Accordez overlay / exemption batterie si demandé.
5. Activez éventuellement l’accessibilité comme **couche supplémentaire** pour renvoyer les apps non autorisées.
6. Activez le **Mode enfant**. Home devrait ouvrir la grille enfant.

### C) Device Owner (le plus strict)

Device Owner ne peut être défini que si l’appareil **n’a aucun compte** (retirez les comptes Google/Amazon) ou après une **réinitialisation d’usine**, et en général avant d’autres admins d’appareil.

1. Installez KidsHome.
2. Provisionnez Device Owner (le composant doit correspondre exactement) :

   ```bash
   adb shell dpm set-device-owner io.github.marcelsavegnago.kidshome/.KidsHomeDeviceAdminReceiver
   ```

   La même commande est affichée / copiable dans **Configuration de l’appareil**.

3. Dans **Configuration de l’appareil**, confirmez Device Owner = oui. Optionnellement désactivez la barre d’état / le keyguard pendant le verrouillage.
4. Activez le **Mode enfant**. KidsHome va :
   - `setLockTaskPackages` pour apps autorisées + navigateur + lui-même
   - `addPersistentPreferredActivity` pour HOME → `KidHomeActivity`
   - `startLockTask` depuis l’accueil enfant (kiosque)
5. La sortie d’urgence PIN appelle `stopLockTask` et peut désactiver le mode enfant.

## Temps d’écran, horaires et gagner du temps

Dans le **hub parental** (protégé par PIN) vous pouvez configurer :

- **Limite quotidienne de temps d’écran** (minutes) — l’accueil affiche le temps restant ; une fois écoulé, le verrouillage « Temps écoulé » apparaît.
- **Blocage au coucher** — plage de sommeil (ex. 20:00–07:00) ; pendant cette période le message est « C’est l’heure de dormir ».
- **Horaires / routines** — créneaux par jour qui n’affichent que certaines catégories ou bloquent tout jusqu’au matin.
- **Limites par app** — ex. 30 min/jour pour la vidéo, illimité pour l’éducation.
- **Gagner du temps** — accorder des minutes bonus (total ou par app) ou cocher des tâches pour donner des étoiles.
- **Rapport hebdomadaire** — usage local par jour/app, totaux vs limites, effacer l’historique, CSV optionnel.

Un parent déverrouille avec le PIN pour ajouter 15/30 minutes, désactiver la limite pour aujourd’hui ou le coucher pour cette nuit. Tout reste sur l’appareil (pas de réseau / analytics / cloud).

## Sortie d’urgence PIN

Fonctionne dans **tous** les modes :

- Tapez **5 fois rapidement le coin supérieur droit** (nécessite « Afficher par-dessus les autres apps »), ou
- **Maintenez Retour** environ 3 secondes

Puis saisissez le PIN parental pour désactiver le mode enfant, ouvrir les réglages KidsHome ou rester en mode enfant.

## FAQ et dépannage

### Comment quitter le mode enfant via ADB ?

```bash
adb shell am force-stop io.github.marcelsavegnago.kidshome
# Ou effacer les préférences (réinitialise les réglages) :
adb shell pm clear io.github.marcelsavegnago.kidshome
```

Si le lock-task Device Owner est actif, retirez d’abord Device Owner (voir ci-dessous) ou déverrouillez avec le PIN dans l’interface.

### Mettre à jour sans perdre les réglages

Utilisez la même clé de signature (keystore de test CI par défaut) et réinstallez avec replace :

```bash
adb install -r KidsHome-release.apk
```

Changer de clé de signature exige une désinstallation préalable.

### Comment retirer Device Owner ?

Dans **Configuration de l’appareil** → **Retirer Device Owner** (PIN si le mode enfant est actif), ou l’app appelle `clearDeviceOwnerApp`. Puis :

```bash
adb uninstall io.github.marcelsavegnago.kidshome
```

Sans retirer Device Owner, Android refuse de désinstaller l’app.

### Annuler / désinstaller (sans Device Owner)

```bash
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0
adb uninstall io.github.marcelsavegnago.kidshome
```

### Limitations connues

- Fire : `com.amazon.firelauncher` ne peut souvent pas être désactivé ; le détournement est la seule option Home.
- Les modes accessibilité / lanceur **ne sont pas** un MDM complet — ADB / forcer l’arrêt peuvent contourner.
- Device Owner est puissant mais demande un appareil propre et ADB une fois ; retirer l’owner est obligatoire pour désinstaller.
- Après des kills mémoire sur Fire, le premier Home peut être lent — appuyez à nouveau si besoin.

## Compiler depuis les sources

```bash
./gradlew assembleRelease
```

Nécessite **JDK 17+** et le SDK Android (`compileSdk` / `targetSdk` 35). `minSdk 22` (Fire 7 / SDK 28 pleinement pris en charge). Sans `keystore.properties` ni `-PciSignRelease=true`, l’APK release n’est pas signé (c’est ainsi que F-Droid compile). Les textes de fiche sont dans `fastlane/metadata/android/`. Un brouillon de recette fdroiddata est dans `docs/fdroid/`.

### Signature CI

| Mode | Quand | Signature |
|------|-------|-----------|
| **Non signé** | Pas de `keystore.properties` et pas de `-PciSignRelease=true` | Aucune — F-Droid signe cet APK |
| **Clé de test CI stable** | `-PciSignRelease=true` et sans secrets `SIGNING_*` | `keystore/debug.keystore` (dans le dépôt) — même signature entre builds, donc `adb install -r` conserve les données |
| **Clé de release** | `keystore.properties` présent (le CI l’écrit quand les secrets `SIGNING_*` existent) | Votre keystore privé |

| Secret | Rôle |
|--------|------|
| `SIGNING_KEYSTORE_BASE64` | `.jks` / `.keystore` en Base64 |
| `SIGNING_KEY_ALIAS` | Alias de la clé |
| `SIGNING_KEY_STORE_PASSWORD` | Mot de passe du keystore |
| `SIGNING_KEY_PASSWORD` | Mot de passe de la clé |

Voir `keystore/README.md`.

## Publier une version

1. Mettez à jour `versionName` / `versionCode` dans `app/build.gradle`.
2. Poussez une étiquette `vX.Y.Z` :

   ```bash
   git tag v1.5.1
   git push origin v1.5.1
   ```

3. GitHub Actions (`.github/workflows/android.yml`) construit l’APK de release et l’attache à la GitHub Release.

## Contribuer

Issues et pull requests sont les bienvenus. Gardez les changements ciblés, testez sur une vraie tablette Fire ou un émulateur pour l’enforcement / l’accessibilité, et privilégiez l’anglais pour le code et les commentaires.

Régénération des captures / GIF (optionnel) : voir [`.github/workflows/screenshots.yml`](.github/workflows/screenshots.yml) et [`docs/capture-screenshots.sh`](docs/capture-screenshots.sh).

## Licence

KidsHome est sous **GNU GPL v3** — voir [LICENSE](LICENSE).

Crédits : BaronKiko (LauncherHijack), Meems33 / WhiteRaven777 (Launcher Replacer).

## Changelog

Voir [CHANGELOG.md](CHANGELOG.md) pour les notes de version (actuel : **1.5.1**).

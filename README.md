# KidsHome

**KidsHome** (v1.2.0) is a lightweight kid-mode home for **Android phones/tablets** and **Amazon Fire OS**. It can enforce kid mode in three ways (auto-detected, with a manual override in parent settings):

| Mode | Best for | How it works |
|------|----------|--------------|
| **Accessibility Home hijack** | Amazon Fire tablets | AccessibilityService redirects Home / Fire launcher (same approach as v1.1) |
| **Default launcher** | Regular Android | KidsHome registers as `CATEGORY_HOME` and is set as the default Home app |
| **Device Owner + lock task** | Strongest kiosk | One-time `dpm set-device-owner`, then native lock-task whitelist |

Optional accessibility blocking of non-allowed apps can still run as an **extra layer** in launcher / Device Owner modes.

This project is a GPL-3 fork of [Meems33/LauncherHijack](https://github.com/Meems33/LauncherHijack) (Launcher Replacer), itself based on [BaronKiko/LauncherHijack](https://github.com/BaronKiko/LauncherHijack).

> Primary low-end test device: Amazon Fire 7 (9th gen, `mustang`), Fire OS 7.3.x = Android 9 (SDK 28), armeabi-v7a, 1 GB RAM. Regular Android phones/tablets are also supported. **iOS is out of scope.**

---

## English

### What it does
- Detects the device at runtime and picks the best enforcement mode (override in **Device setup**).
- **Kid home**: large-icon grid of allowed apps + website shortcuts, or lock to a single app.
- **Screen time**: daily limit + bedtime window, warning, friendly lock screen, PIN to add time / disable for today.
- **Emergency PIN exit**: 5 taps top-right (overlay) or hold Back ~3s — works in all modes; Device Owner path calls `stopLockTask` cleanly.
- Survives reboot; warns if accessibility is off when that mode needs it.

### Install (ADB)
1. Download the APK from [Releases](https://github.com/marcelsavegnago/KidsHome/releases) or the GitHub Actions artifact `KidsHome-release-apk`.
2. Install (same CI test signature across builds → upgrades keep data):
   ```bash
   adb install -r KidsHome-release.apk
   ```
3. Open KidsHome → menu → **Device setup** and finish the checklist for your mode.
4. Menu → **Kid mode** → create PIN, configure apps / screen time, enable.

---

### Setup by device

#### A) Amazon Fire OS (accessibility hijack)
Fire OS does **not** allow replacing the default launcher. KidsHome keeps the classic AccServ Home hijack.

1. Enable accessibility (UI **or** ADB).

##### UI
Settings → Accessibility → **KidsHome home detection** → enable.

##### ADB
```text
io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
```

```bash
adb shell settings put secure enabled_accessibility_services io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
adb shell settings put secure accessibility_enabled 1
```

2. (Optional, for corner-tap escape)
```bash
adb shell appops set io.github.marcelsavegnago.kidshome SYSTEM_ALERT_WINDOW allow
```
3. In KidsHome, pick a fallback launcher if you use non-kid Home hijack, or go straight to **Kid mode**.
4. Reboot once. Press Home — kid home / chosen target should open (a brief Fire launcher flash may appear).

#### B) Regular Android (default launcher mode)
1. Open **Device setup** → mode **Automatic** or **Default launcher**.
2. Tap **Set as default Home** (uses `RoleManager.ROLE_HOME` on Android 10+, otherwise Home settings).
3. Choose **KidsHome** in the system picker.
4. Grant overlay / battery exemption as prompted.
5. Optionally enable accessibility as an **extra layer** to bounce non-allowed apps.
6. Enable **Kid mode**. Home should open the kid grid.

#### C) Device Owner mode (strongest)
Device Owner can only be set when the device has **no accounts** (remove Google/Amazon accounts) or after a **factory reset**, and typically before other device admins are active.

1. Install KidsHome.
2. Provision Device Owner (component must match exactly):
   ```bash
   adb shell dpm set-device-owner io.github.marcelsavegnago.kidshome/.KidsHomeDeviceAdminReceiver
   ```
   The same command is shown / copyable in **Device setup**.
3. In **Device setup**, confirm Device Owner = yes. Optionally disable status bar / keyguard while locked.
4. Enable **Kid mode**. KidsHome will:
   - `setLockTaskPackages` for allowed apps + browser + itself
   - `addPersistentPreferredActivity` for HOME → `KidHomeActivity`
   - `startLockTask` from kid home (kiosk)
5. Emergency PIN exit calls `stopLockTask` and can disable kid mode.

##### Remove Device Owner (required before uninstall)
In **Device setup** → **Remove Device Owner** (PIN if kid mode is on), or the app calls `clearDeviceOwnerApp`. Then:
```bash
adb uninstall io.github.marcelsavegnago.kidshome
```

If you skip clearing Device Owner, Android will refuse to uninstall the app.

---

### Undo / uninstall (non–Device Owner)
```bash
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0
adb uninstall io.github.marcelsavegnago.kidshome
```

### Kid mode & emergency exit
Same as v1.1: kid home grid or single app; screen time / bedtime; PIN exit via corner taps or hold Back. All of these work in every enforcement mode.

### Known limitations
- Fire: `com.amazon.firelauncher` often cannot be disabled; hijack is the only Home option.
- Accessibility / launcher modes are **not** full MDM — ADB / force-stop can bypass.
- Device Owner is strong but needs a clean device and ADB once; clearing owner is required to uninstall.
- After low-memory kills on Fire, the first Home press can be slow — press again if needed.

### Build
```bash
./gradlew assembleRelease
```
Requires JDK 17+ and Android SDK (`compileSdk` / `targetSdk` 34). `minSdk 22` (Fire 7 / SDK 28 fully supported).

### CI signing
| Mode | When | Signature |
|------|------|-----------|
| **Stable CI test key** | No `SIGNING_*` secrets | `keystore/debug.keystore` (committed) — same across CI runs, so `adb install -r` upgrades keep settings |
| **Release key** | Secrets set | Your private keystore |

| Secret | Purpose |
|--------|---------|
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_PASSWORD` | Key password |

Switching signing keys requires one uninstall. See `keystore/README.md`.

### Credits & license
- BaronKiko — original LauncherHijack
- Meems33 / WhiteRaven777 — Launcher Replacer fork
- KidsHome — GPL-3 (see [LICENSE](LICENSE))

---

## Português (pt-BR)

### O que é
O **KidsHome** (v1.2.0) é um home infantil leve para **celulares/tablets Android** e **Amazon Fire OS**. Escolhe automaticamente o melhor modo de enforcement (com override nas configurações):

| Modo | Melhor para | Como funciona |
|------|-------------|---------------|
| **Sequestro de Home por acessibilidade** | Tablets Amazon Fire | AccServ redireciona Home / launcher da Fire |
| **Launcher padrão** | Android comum | KidsHome como app Home (`CATEGORY_HOME`) |
| **Device Owner + lock task** | Kiosk mais forte | `dpm set-device-owner` uma vez + whitelist nativa |

Bloqueio por acessibilidade de apps não permitidos pode continuar como **camada extra** nos modos launcher / Device Owner.

Fork GPL-3 de [Meems33/LauncherHijack](https://github.com/Meems33/LauncherHijack), baseado em [BaronKiko/LauncherHijack](https://github.com/BaronKiko/LauncherHijack). **iOS fora do escopo.**

### Instalação (ADB)
```bash
adb install -r KidsHome-release.apk
```
Abra o KidsHome → menu → **Configuração do dispositivo** e conclua o checklist. Depois **Modo criança**.

### Por tipo de aparelho

#### A) Amazon Fire OS
1. Ative a acessibilidade (UI ou ADB — mesmo componente da seção em inglês).
2. Overlay opcional para saída pelo canto:
   ```bash
   adb shell appops set io.github.marcelsavegnago.kidshome SYSTEM_ALERT_WINDOW allow
   ```
3. Ative o modo criança e reinicie uma vez.

#### B) Android comum (launcher)
1. **Configuração do dispositivo** → Automatic / Launcher padrão.
2. **Definir como Home padrão** e escolha KidsHome.
3. Conceda overlay / isenção de bateria.
4. Acessibilidade opcional como camada extra.
5. Ative o modo criança.

#### C) Device Owner (mais forte)
Exige aparelho **sem contas** ou após **reset de fábrica**.

```bash
adb shell dpm set-device-owner io.github.marcelsavegnago.kidshome/.KidsHomeDeviceAdminReceiver
```

Com o modo criança ativo: lock task, HOME persistente, whitelist de apps. A saída com PIN chama `stopLockTask`.

##### Remover Device Owner (obrigatório antes de desinstalar)
Em **Configuração do dispositivo** → **Remover Device Owner**, depois:
```bash
adb uninstall io.github.marcelsavegnago.kidshome
```

### Desfazer (sem Device Owner)
```bash
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0
adb uninstall io.github.marcelsavegnago.kidshome
```

### Modo criança
Home infantil ou app único; tempo de tela / hora de dormir; saída de emergência (5 toques no canto ou segurar Voltar) — funciona em todos os modos.

### Assinatura do CI
Sem secrets `SIGNING_*`, o CI assina com `keystore/debug.keystore` (chave de teste fixa — `adb install -r` preserva dados). Com secrets, usa a chave de release. Trocar de chave exige desinstalar uma vez.

### Créditos e licença
BaronKiko, Meems33 / WhiteRaven777. KidsHome — GPL-3 ([LICENSE](LICENSE)).

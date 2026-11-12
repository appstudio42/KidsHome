# KidsHome

**KidsHome** is a lightweight Android accessibility helper that detects the Home button and opens a launcher (or a single kid app) of your choice. It is aimed at **Amazon Fire tablets** where the default launcher cannot be changed — notably Fire OS 7.x where `com.amazon.firelauncher` is protected and `cmd package set-home-activity` is ignored.

This project is a GPL-3 fork of [Meems33/LauncherHijack](https://github.com/Meems33/LauncherHijack) (Launcher Replacer), itself based on [BaronKiko/LauncherHijack](https://github.com/BaronKiko/LauncherHijack).

> Target test device: Amazon Fire 7 (9th gen, `mustang`), Fire OS 7.3.x = Android 9 (SDK 28), armeabi-v7a, 1 GB RAM.

---

## English

### What it does
- Replaces the Fire launcher experience by watching Home / launcher window events and immediately opening your chosen app.
- Survives reboot (boot receiver) and tries to recover after process kills.
- Warns you with a banner + notification if the accessibility service is turned off.
- Falls back to KidsHome itself if the chosen launcher is uninstalled, so you are never left without a home.
- Optional **Kid mode** (PIN-protected): keep Home/Recents bouncing back to the chosen launcher or a single app (e.g. YouTube Kids).

**Honest limits:** Kid mode is accessibility-based, **not** a device-owner / MDM lock. ADB, force-stop, or a determined child can still bypass it.

### Install (ADB)
1. Download the APK from [Releases](https://github.com/marcelsavegnago/KidsHome/releases) or from the GitHub Actions artifact `KidsHome-release-apk`.
2. Install:
   ```bash
   adb install -r KidsHome-release.apk
   ```
3. Enable the accessibility service (UI **or** ADB).

#### Enable accessibility via UI
Settings → Accessibility → **KidsHome home detection** → enable.

#### Enable accessibility via ADB
Exact component name:
```text
io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
```

```bash
adb shell settings put secure enabled_accessibility_services io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
adb shell settings put secure accessibility_enabled 1
```

4. Open KidsHome → pick your launcher (Nova, Lawnchair, etc.).
5. Reboot once.
6. Press Home — your launcher should open (a brief Fire launcher flash may still appear; KidsHome reacts as early as it can).

### Undo / uninstall
```bash
# Disable accessibility (clears all enabled a11y services — re-enable others afterwards if needed)
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0

# Uninstall
adb uninstall io.github.marcelsavegnago.kidshome
```

### Kid mode
1. Open KidsHome → menu → **Kid mode**.
2. Create a 4–8 digit PIN.
3. Lock to the chosen launcher **or** pick a single app.
4. Save. Home / Recents will keep returning to that target.
5. Opening KidsHome settings or disabling kid mode requires the PIN.

#### Emergency exit (when locked to a single app)
You may not be able to open KidsHome from the locked app. Use one of these parent escapes:

1. **5 quick taps** in the **top-right corner** of the screen (needs overlay permission), or
2. **Hold Back for ~3 seconds**.

Enter the parent PIN. Then choose **Disable kid mode**, **Open KidsHome settings**, or stay locked.

Grant overlay permission (for corner taps) via Settings → Apps → KidsHome → Display over other apps, or:
```bash
adb shell appops set io.github.marcelsavegnago.kidshome SYSTEM_ALERT_WINDOW allow
```
Back-hold works even without overlay permission. Escape options are configurable on the Kid mode screen.

Wrong PINs trigger a progressive delay (1s → 30s). Disabling kid mode is written synchronously to storage so a service restart cannot re-enable a stale lock.

### Known limitations on Fire OS
- `com.amazon.firelauncher` cannot be disabled on many firmwares; hijack is the only option.
- The original `com.baronkiko.launcherhijack` package is denylisted by Amazon — KidsHome uses a new application id.
- After the process is killed (low memory), the **first** Home press can be slow (~cold start). KidsHome warms the target on boot/service connect; if needed, press Home again.
- Overlay / “Display over other apps” is required for the corner-tap escape (and for optional overlay Home detection).
- Not a parental-control MDM solution.

### Build
```bash
./gradlew assembleRelease
```
Requires JDK 17+ and Android SDK (compileSdk 34). `minSdk 22`, `targetSdk 28` (sideload-friendly on Fire OS 7 / Android 9, still installable on newer devices).

### CI signing secrets (optional)
| Secret | Purpose |
|--------|---------|
| `SIGNING_KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` file |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_PASSWORD` | Key password |

If these are missing, CI still publishes a **debug-signed** installable APK.

### Credits & license
- BaronKiko — original LauncherHijack
- Meems33 / WhiteRaven777 — Launcher Replacer fork
- KidsHome — GPL-3 (see [LICENSE](LICENSE))

---

## Português (pt-BR)

### O que é
O **KidsHome** é um app leve de acessibilidade que detecta o botão Home e abre o launcher (ou um único app infantil) escolhido por você. Foi feito para **tablets Amazon Fire** em que o launcher padrão não pode ser trocado — em especial Fire OS 7.x, onde `com.amazon.firelauncher` é protegido e `cmd package set-home-activity` é ignorado.

Fork GPL-3 de [Meems33/LauncherHijack](https://github.com/Meems33/LauncherHijack) (Launcher Replacer), por sua vez baseado em [BaronKiko/LauncherHijack](https://github.com/BaronKiko/LauncherHijack).

### Instalação (ADB)
1. Baixe o APK em [Releases](https://github.com/marcelsavegnago/KidsHome/releases) ou no artefato `KidsHome-release-apk` do GitHub Actions.
2. Instale:
   ```bash
   adb install -r KidsHome-release.apk
   ```
3. Ative o serviço de acessibilidade (pela UI **ou** ADB).

#### Pela interface
Configurações → Acessibilidade → **Detecção de Home do KidsHome** → ativar.

#### Via ADB
Componente exato:
```text
io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
```

```bash
adb shell settings put secure enabled_accessibility_services io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
adb shell settings put secure accessibility_enabled 1
```

4. Abra o KidsHome → escolha o launcher.
5. Reinicie o aparelho uma vez.
6. Pressione Home.

### Desfazer / desinstalar
```bash
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0
adb uninstall io.github.marcelsavegnago.kidshome
```

### Modo criança
1. KidsHome → menu → **Modo criança**.
2. Crie um PIN de 4–8 dígitos.
3. Trave no launcher escolhido **ou** em um único app.
4. Salvar. Home / Recentes voltam para esse destino.
5. Abrir configurações ou desativar o modo exige o PIN.

#### Saída de emergência (travado em um único app)
1. **5 toques rápidos** no **canto superior direito**, ou
2. **Segurar Voltar ~3 segundos**.

Digite o PIN. Depois escolha desativar o modo, abrir o KidsHome, ou continuar travado.

```bash
adb shell appops set io.github.marcelsavegnago.kidshome SYSTEM_ALERT_WINDOW allow
```
Segurar Voltar funciona sem overlay. PIN errado aplica atraso progressivo. Desativar o modo é gravado de forma síncrona para não “voltar a travar” após reinício do serviço.

**Limite:** baseado em acessibilidade — **não** é bloqueio de dono do dispositivo. ADB ou forçar parada ainda podem contornar.

### Limitações no Fire OS
- Em muitos firmwares o Fire launcher não pode ser desabilitado.
- O pacote antigo `com.baronkiko.launcherhijack` está na denylist da Amazon.
- Após o sistema matar o processo (pouca RAM), o primeiro Home pode demorar; pressione de novo se necessário.
- Não substitui um MDM / controle parental enterprise.

### Créditos e licença
BaronKiko, Meems33 / WhiteRaven777. KidsHome — GPL-3 ([LICENSE](LICENSE)).

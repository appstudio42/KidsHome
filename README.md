# KidsHome

**KidsHome** (v1.1.0) is a lightweight Android accessibility helper that detects the Home button and opens a kid home grid or a single kid app. It is aimed at **Amazon Fire tablets** where the default launcher cannot be changed — notably Fire OS 7.x where `com.amazon.firelauncher` is protected and `cmd package set-home-activity` is ignored.

This project is a GPL-3 fork of [Meems33/LauncherHijack](https://github.com/Meems33/LauncherHijack) (Launcher Replacer), itself based on [BaronKiko/LauncherHijack](https://github.com/BaronKiko/LauncherHijack).

> Target test device: Amazon Fire 7 (9th gen, `mustang`), Fire OS 7.3.x = Android 9 (SDK 28), armeabi-v7a, 1 GB RAM.

---

## English

### What it does
- Replaces the Fire launcher experience by watching Home / launcher window events and immediately opening your chosen app.
- Survives reboot (boot receiver) and tries to recover after process kills.
- Warns you with a banner + notification if the accessibility service is turned off.
- Falls back to KidsHome itself if the chosen launcher is uninstalled, so you are never left without a home.
- Optional **Kid mode** (PIN-protected): **kid home** (large-icon grid of allowed apps + website shortcuts) or lock to a single app.
- **Screen time**: daily limit + bedtime window, 5‑minute warning, friendly lock screen, PIN to add +15/+30 min or disable for today. Usage persists across reboot/service kills.

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
3. Choose **Kid home** (app grid) **or** lock to a single app.
4. For kid home: set the child’s name, pick allowed apps, website shortcuts (Poki Kids / PBS Kids / Coolmath suggested), and a browser (auto-allowlisted).
5. Optional screen time: daily minutes + bedtime window (device timezone; resets at midnight).
6. Save. Home / Recents return to kid home or the single app. Settings are only reachable via the emergency PIN exit.

#### Emergency exit
1. **5 quick taps** in the **top-right corner** (needs overlay permission), or
2. **Hold Back for ~3 seconds**.

Enter the parent PIN → disable kid mode, open settings, or stay locked.

```bash
adb shell appops set io.github.marcelsavegnago.kidshome SYSTEM_ALERT_WINDOW allow
```

#### Screen time lock
When the daily limit or bedtime hits, a friendly full-screen message appears (“Time’s up for today!” / bedtime). Parent PIN can add **+15 / +30 min**, disable the limit for today, or open settings. Usage is flushed often so reboot / process death do not wipe the count.

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
3. Escolha **Home infantil** (grade) **ou** um único app.
4. Na home: nome da criança, apps permitidos, atalhos de sites (Poki / PBS / Coolmath) e navegador.
5. Opcional: limite diário de tela + horário de dormir (fuso do aparelho; zera à meia-noite).
6. Salvar. Configurações só pela saída de emergência com PIN.

#### Saída de emergência
1. **5 toques rápidos** no **canto superior direito**, ou
2. **Segurar Voltar ~3 segundos**.

```bash
adb shell appops set io.github.marcelsavegnago.kidshome SYSTEM_ALERT_WINDOW allow
```

#### Bloqueio de tempo de tela
Ao esgotar o limite ou no horário de dormir, aparece a tela amigável. Com PIN: +15/+30 min, desativar por hoje, ou abrir configurações. A contagem persiste após reboot/kill do serviço.

**Limite:** baseado em acessibilidade — **não** é bloqueio de dono do dispositivo. ADB ou forçar parada ainda podem contornar.

### Limitações no Fire OS
- Em muitos firmwares o Fire launcher não pode ser desabilitado.
- O pacote antigo `com.baronkiko.launcherhijack` está na denylist da Amazon.
- Após o sistema matar o processo (pouca RAM), o primeiro Home pode demorar; pressione de novo se necessário.
- Não substitui um MDM / controle parental enterprise.

### Créditos e licença
BaronKiko, Meems33 / WhiteRaven777. KidsHome — GPL-3 ([LICENSE](LICENSE)).

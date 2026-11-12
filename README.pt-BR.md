[🇬🇧 English](README.md) | 🇧🇷 **Português** | [🇪🇸 Español](README.es.md) | [🇫🇷 Français](README.fr.md) | [🇩🇪 Deutsch](README.de.md)

<p align="center">
  <img src="docs/logo.png" alt="Logo KidsHome" width="128" height="128" />
</p>

<h1 align="center">KidsHome</h1>

<p align="center"><strong>Um home infantil leve para celulares Android, tablets e Amazon Fire OS.</strong></p>

<p align="center">
  <a href="https://github.com/marcelsavegnago/KidsHome/releases/latest"><img src="https://img.shields.io/github/v/release/marcelsavegnago/KidsHome?label=latest%20release" alt="Última versão" /></a>
  <a href="https://github.com/marcelsavegnago/KidsHome/releases"><img src="https://img.shields.io/github/downloads/marcelsavegnago/KidsHome/total" alt="Downloads" /></a>
  <a href="https://github.com/marcelsavegnago/KidsHome/actions/workflows/android.yml"><img src="https://img.shields.io/github/actions/workflow/status/marcelsavegnago/KidsHome/android.yml?branch=master&label=CI" alt="Status do CI" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/marcelsavegnago/KidsHome" alt="Licença" /></a>
  <img src="https://img.shields.io/badge/min%20Android-5.1%20(API%2022)-green" alt="Android mínimo 5.1 API 22" />
</p>

<p align="center">
  <a href="https://github.com/marcelsavegnago/KidsHome/releases/latest"><strong>⬇ Baixar APK (última versão)</strong></a>
</p>

O KidsHome (v1.4.0) transforma um celular ou tablet reserva em uma tela inicial amigável para crianças: só os apps e sites que você permitir, com limite diário de tempo de tela, bloqueio na hora de dormir e saída de emergência com PIN dos pais. Ele escolhe o melhor modo de enforcement para o aparelho (ou você redefine em **Configuração do dispositivo**).

<p align="center">
  <img src="docs/media/setup.gif" alt="Configuração do KidsHome: criar PIN, escolher apps, definir tempo de tela, entrar no modo criança" width="720" />
</p>

<p align="center"><em>Fluxo de configuração — criar PIN → escolher apps → definir tempo de tela → entrar no modo criança</em></p>

## Capturas de tela

Capturas reais de um emulador Android tablet (1280×800) com o nome de exemplo **Theo**.

| Home infantil | Tempo esgotado | PIN dos pais |
|:---:|:---:|:---:|
| ![Home infantil](docs/screenshots/01-kid-home.png) | ![Tempo esgotado](docs/screenshots/02-time-up.png) | ![PIN](docs/screenshots/03-pin-entry.png) |
| Grade do home infantil | Bloqueio de tempo de tela | Saída de emergência com PIN |

| Modo criança | Tempo de tela | Configuração do dispositivo |
|:---:|:---:|:---:|
| ![Modo criança](docs/screenshots/04-parent-kid-mode.png) | ![Tempo de tela](docs/screenshots/05-screen-time-settings.png) | ![Configuração](docs/screenshots/06-device-setup.png) |
| Configurações dos pais | Limites e hora de dormir | Checklist de modo |

| Apps permitidos | Hub principal |
|:---:|:---:|
| ![Apps permitidos](docs/screenshots/07-allowed-apps.png) | ![Hub](docs/screenshots/08-main-hub.png) |
| Escolher apps da grade | Tela principal do KidsHome |

| Horários | Limites por app | Ganhar tempo |
|:---:|:---:|:---:|
| ![Schedules](docs/screenshots/09-schedules.png) | ![App limits](docs/screenshots/10-app-limits.png) | ![Earn time](docs/screenshots/11-earn-time.png) |
| Rotinas e categorias | Minutos diários por app | Tarefas e minutos bônus |

| Relatório semanal | Celebração | Hora de dormir |
|:---:|:---:|:---:|
| ![Report](docs/screenshots/12-weekly-report.png) | ![Celebration](docs/screenshots/13-star-celebration.png) | ![Bedtime](docs/screenshots/14-bedtime.png) |
| Gráfico de uso local | Estrela ganha | Bloqueio amigável |

## Recursos

- **Três modos de enforcement** (detectados automaticamente, com override manual):
  - Sequestro de Home por acessibilidade — tablets Amazon Fire
  - Launcher padrão — Android comum
  - Device Owner + lock task — kiosk mais forte
- **Home infantil** — grade com ícones grandes de apps e atalhos de sites, ou travar em um único app
- **Tempo de tela** — limite diário, janela de sono, aviso, tela de bloqueio amigável, PIN para adicionar tempo / desativar por hoje
- **Horários / rotinas** — janelas por dia da semana (escola, leitura, dormir) com categorias de apps
- **Limites por app** — minutos diários por app permitido além do limite total; tiles bloqueados quando esgotados
- **Ganhar tempo / estrelas** — minutos bônus com PIN e checklist simples com celebração + contador de estrelas
- **Relatório semanal** — histórico local por app/dia (~4 semanas), gráfico Canvas, limpar / exportar CSV
- **Saída de emergência com PIN** — 5 toques no canto superior direito (overlay) ou segurar Voltar ~3s; no Device Owner chama `stopLockTask` corretamente
- Sobrevive a reinicialização; avisa se a acessibilidade estiver desligada quando o modo precisar dela
- Bloqueio opcional por acessibilidade de apps não permitidos como **camada extra** nos modos launcher / Device Owner

## Dispositivos suportados

| Modo | Melhor para | Como funciona |
|------|-------------|---------------|
| **Sequestro de Home por acessibilidade** | Tablets Amazon Fire | AccessibilityService redireciona Home / launcher da Fire |
| **Launcher padrão** | Celulares e tablets Android | KidsHome como app Home (`CATEGORY_HOME`) |
| **Device Owner + lock task** | Kiosk mais forte | `dpm set-device-owner` uma vez + whitelist nativa |

> Aparelho de teste principal: Amazon Fire 7 (9ª geração, `mustang`), Fire OS 7.3.x = Android 9 (SDK 28). **iOS fora do escopo.**

Fork GPL-3 de [Meems33/LauncherHijack](https://github.com/Meems33/LauncherHijack) (Launcher Replacer), baseado em [BaronKiko/LauncherHijack](https://github.com/BaronKiko/LauncherHijack).

## Início rápido

1. Baixe o APK na [última versão](https://github.com/marcelsavegnago/KidsHome/releases/latest) (ou o artefato `KidsHome-release-apk` do GitHub Actions).
2. Instale (a mesma assinatura de teste do CI preserva os dados nos upgrades):

   ```bash
   adb install -r KidsHome-release.apk
   ```

3. Abra o KidsHome → menu → **Configuração do dispositivo** e conclua o checklist do seu modo.
4. Menu → **Modo criança** → crie o PIN, configure apps / tempo de tela e ative.

### A) Amazon Fire OS (sequestro por acessibilidade)

O Fire OS **não** permite trocar o launcher padrão. O KidsHome mantém o sequestro clássico via AccServ.

1. Ative a acessibilidade (UI **ou** ADB).

   **UI:** Configurações → Acessibilidade → **KidsHome home detection** → ativar.

   **Componente ADB:**

   ```text
   io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
   ```

   ```bash
   adb shell settings put secure enabled_accessibility_services io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
   adb shell settings put secure accessibility_enabled 1
   ```

2. (Opcional, para saída pelo canto)

   ```bash
   adb shell appops set io.github.marcelsavegnago.kidshome SYSTEM_ALERT_WINDOW allow
   ```

3. No KidsHome, vá em **Modo criança** (ou escolha um launcher de fallback se usar sequestro sem modo criança).
4. Reinicie uma vez. Pressione Home — o home infantil / alvo escolhido deve abrir (pode haver um flash breve do launcher da Fire).

### B) Android comum (launcher padrão)

1. Abra **Configuração do dispositivo** → modo **Automático** ou **Launcher padrão**.
2. Toque em **Definir como Home padrão** (`RoleManager.ROLE_HOME` no Android 10+, senão as configurações de Home).
3. Escolha **KidsHome** no seletor do sistema.
4. Conceda overlay / isenção de bateria conforme solicitado.
5. Opcionalmente ative a acessibilidade como **camada extra** para bloquear apps não permitidos.
6. Ative o **Modo criança**. O Home deve abrir a grade infantil.

### C) Device Owner (mais forte)

O Device Owner só pode ser definido em aparelho **sem contas** (remova contas Google/Amazon) ou após **reset de fábrica**, e em geral antes de outros administradores de dispositivo.

1. Instale o KidsHome.
2. Provisione o Device Owner (o componente precisa bater exatamente):

   ```bash
   adb shell dpm set-device-owner io.github.marcelsavegnago.kidshome/.KidsHomeDeviceAdminReceiver
   ```

   O mesmo comando aparece / pode ser copiado em **Configuração do dispositivo**.

3. Em **Configuração do dispositivo**, confirme Device Owner = sim. Opcionalmente desative barra de status / keyguard enquanto travado.
4. Ative o **Modo criança**. O KidsHome irá:
   - `setLockTaskPackages` para apps permitidos + navegador + ele mesmo
   - `addPersistentPreferredActivity` para HOME → `KidHomeActivity`
   - `startLockTask` a partir do home infantil (kiosk)
5. A saída de emergência com PIN chama `stopLockTask` e pode desativar o modo criança.

## Tempo de tela, horários e ganhar tempo

No **hub dos pais** (protegido por PIN) você pode configurar:

- **Limite diário de tempo de tela** (minutos) — o home mostra o tempo restante; ao esgotar, aparece o bloqueio “Tempo esgotado”.
- **Bloqueio na hora de dormir** — janela de sono (ex.: 20:00–07:00); durante esse período a mensagem é “Hora de dormir”.
- **Horários / rotinas** — janelas por dia da semana que mostram só certas categorias ou bloqueiam tudo até de manhã.
- **Limites por app** — ex.: 30 min/dia para vídeo, ilimitado para educação.
- **Ganhar tempo** — conceder minutos bônus (total ou por app) ou marcar tarefas da lista para dar estrelas.
- **Relatório semanal** — uso local por dia/app, totais vs limites, limpar histórico, CSV opcional.

Os pais desbloqueiam com o PIN para adicionar 15/30 minutos, desativar o limite por hoje ou a hora de dormir nesta noite. Tudo fica no aparelho (sem rede / analytics / nuvem).

## Saída de emergência com PIN

Funciona em **todos** os modos de enforcement:

- Toque **5 vezes rapidamente no canto superior direito** (precisa de “Exibir sobre outros apps”), ou
- **Segure Voltar** por cerca de 3 segundos

Em seguida digite o PIN dos pais para desativar o modo criança, abrir as configurações do KidsHome ou permanecer no modo criança.

## FAQ e solução de problemas

### Como sair do modo criança via ADB?

```bash
adb shell am force-stop io.github.marcelsavegnago.kidshome
# Ou limpar as preferências (reinicia as configurações):
adb shell pm clear io.github.marcelsavegnago.kidshome
```

Se o lock-task do Device Owner estiver ativo, remova o Device Owner primeiro (veja abaixo) ou desbloqueie com o PIN na interface.

### Atualizar sem perder configurações

Use a mesma chave de assinatura (keystore de teste do CI por padrão) e reinstale com replace:

```bash
adb install -r KidsHome-release.apk
```

Trocar para outra chave de assinatura exige desinstalar uma vez antes.

### Como remover o Device Owner?

Em **Configuração do dispositivo** → **Remover Device Owner** (PIN se o modo criança estiver ativo), ou o app chama `clearDeviceOwnerApp`. Depois:

```bash
adb uninstall io.github.marcelsavegnago.kidshome
```

Se você não limpar o Device Owner, o Android se recusa a desinstalar o app.

### Desfazer / desinstalar (sem Device Owner)

```bash
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0
adb uninstall io.github.marcelsavegnago.kidshome
```

### Limitações conhecidas

- Fire: `com.amazon.firelauncher` muitas vezes não pode ser desativado; o sequestro é a única opção de Home.
- Modos acessibilidade / launcher **não** são MDM completo — ADB / forçar parada podem contornar.
- Device Owner é forte, mas exige aparelho limpo e ADB uma vez; limpar o owner é obrigatório para desinstalar.
- Após kills por pouca memória na Fire, o primeiro Home pode demorar — pressione de novo se precisar.

## Compilar a partir do código

```bash
./gradlew assembleRelease
```

Requer **JDK 17+** e Android SDK (`compileSdk` / `targetSdk` 34). `minSdk 22` (Fire 7 / SDK 28 totalmente suportado).

### Assinatura no CI

| Modo | Quando | Assinatura |
|------|--------|------------|
| **Chave de teste estável do CI** | Sem secrets `SIGNING_*` | `keystore/debug.keystore` (no repositório) — mesma assinatura entre builds, então `adb install -r` preserva dados |
| **Chave de release** | Secrets definidos | Seu keystore privado |

| Secret | Função |
|--------|--------|
| `SIGNING_KEYSTORE_BASE64` | `.jks` / `.keystore` em Base64 |
| `SIGNING_KEY_ALIAS` | Alias da chave |
| `SIGNING_KEY_STORE_PASSWORD` | Senha do keystore |
| `SIGNING_KEY_PASSWORD` | Senha da chave |

Veja `keystore/README.md`.

## Publicar uma versão

1. Atualize `versionName` / `versionCode` em `app/build.gradle`.
2. Envie uma tag `vX.Y.Z`:

   ```bash
   git tag v1.4.0
   git push origin v1.4.0
   ```

3. O GitHub Actions (`.github/workflows/android.yml`) gera o APK de release e anexa ao GitHub Release.

## Contribuindo

Issues e pull requests são bem-vindos. Mantenha as mudanças focadas, teste em um tablet Fire real ou emulador ao mexer em enforcement / acessibilidade, e use inglês para código e comentários.

Regeneração de capturas / GIF (opcional): veja [`.github/workflows/screenshots.yml`](.github/workflows/screenshots.yml) e [`docs/capture-screenshots.sh`](docs/capture-screenshots.sh).

## Licença

KidsHome está sob a **GNU GPL v3** — veja [LICENSE](LICENSE).

Créditos: BaronKiko (LauncherHijack), Meems33 / WhiteRaven777 (Launcher Replacer).

## Changelog

Veja [CHANGELOG.md](CHANGELOG.md) para as notas da versão (atual: **1.4.0**).

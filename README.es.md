[🇬🇧 English](README.md) | [🇧🇷 Português](README.pt-BR.md) | 🇪🇸 **Español** | [🇫🇷 Français](README.fr.md) | [🇩🇪 Deutsch](README.de.md)

<p align="center">
  <img src="docs/logo.png" alt="Logo de KidsHome" width="128" height="128" />
</p>

<h1 align="center">KidsHome</h1>

<p align="center"><strong>Un inicio infantil ligero para móviles Android, tablets y Amazon Fire OS.</strong></p>

<p align="center">
  <a href="https://github.com/marcelsavegnago/KidsHome/releases/latest"><img src="https://img.shields.io/github/v/release/marcelsavegnago/KidsHome?label=latest%20release" alt="Última versión" /></a>
  <a href="https://github.com/marcelsavegnago/KidsHome/releases"><img src="https://img.shields.io/github/downloads/marcelsavegnago/KidsHome/total" alt="Descargas" /></a>
  <a href="https://github.com/marcelsavegnago/KidsHome/actions/workflows/android.yml"><img src="https://img.shields.io/github/actions/workflow/status/marcelsavegnago/KidsHome/android.yml?branch=master&label=CI" alt="Estado del CI" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/marcelsavegnago/KidsHome" alt="Licencia" /></a>
  <img src="https://img.shields.io/badge/min%20Android-5.1%20(API%2022)-green" alt="Android mínimo 5.1 API 22" />
</p>

<p align="center">
  <a href="https://github.com/marcelsavegnago/KidsHome/releases/latest"><strong>⬇ Descargar APK (última versión)</strong></a>
</p>

KidsHome (v1.3.0) convierte un móvil o tablet de repuesto en una pantalla de inicio pensada para niños: solo las apps y sitios que tú permitas, con límite diario de tiempo de uso, bloqueo a la hora de dormir y salida de emergencia con PIN parental. Elige el mejor modo de aplicación para el dispositivo (o lo defines en **Configuración del dispositivo**).

<p align="center">
  <img src="docs/media/setup.gif" alt="Configuración de KidsHome: crear PIN, elegir apps, definir tiempo de pantalla, entrar en modo niño" width="720" />
</p>

<p align="center"><em>Flujo de configuración — crear PIN → elegir apps → definir tiempo de pantalla → entrar en modo niño</em></p>

## Capturas de pantalla

Capturas reales de un emulador Android tablet (1280×800) con el nombre de ejemplo **Theo**.

| Inicio infantil | Tiempo agotado | PIN parental |
|:---:|:---:|:---:|
| ![Inicio infantil](docs/screenshots/01-kid-home.png) | ![Tiempo agotado](docs/screenshots/02-time-up.png) | ![PIN](docs/screenshots/03-pin-entry.png) |
| Cuadrícula del inicio | Bloqueo de tiempo de uso | Salida de emergencia con PIN |

| Modo niño | Tiempo de pantalla | Configuración del dispositivo |
|:---:|:---:|:---:|
| ![Modo niño](docs/screenshots/04-parent-kid-mode.png) | ![Tiempo de pantalla](docs/screenshots/05-screen-time-settings.png) | ![Configuración](docs/screenshots/06-device-setup.png) |
| Ajustes parentales | Límites y hora de dormir | Lista de comprobación |

| Apps permitidas | Panel principal |
|:---:|:---:|
| ![Apps permitidas](docs/screenshots/07-allowed-apps.png) | ![Panel](docs/screenshots/08-main-hub.png) |
| Elegir apps de la cuadrícula | Pantalla principal de KidsHome |

## Funciones

- **Tres modos de aplicación** (detección automática, con anulación manual):
  - Secuestro de Home por accesibilidad — tablets Amazon Fire
  - Lanzador predeterminado — Android normal
  - Device Owner + lock task — quiosco más fuerte
- **Inicio infantil** — cuadrícula de iconos grandes de apps y accesos a sitios, o bloquear en una sola app
- **Tiempo de pantalla** — límite diario, franja de sueño, aviso, bloqueo amable, PIN para añadir tiempo / desactivar por hoy
- **Salida de emergencia con PIN** — 5 toques en la esquina superior derecha (overlay) o mantener Atrás ~3 s; en Device Owner llama a `stopLockTask` correctamente
- Sobrevive al reinicio; avisa si la accesibilidad está desactivada cuando el modo la necesita
- Bloqueo opcional por accesibilidad de apps no permitidas como **capa extra** en modos lanzador / Device Owner

## Dispositivos compatibles

| Modo | Ideal para | Cómo funciona |
|------|------------|---------------|
| **Secuestro de Home por accesibilidad** | Tablets Amazon Fire | AccessibilityService redirige Home / lanzador de Fire |
| **Lanzador predeterminado** | Móviles y tablets Android | KidsHome como app Home (`CATEGORY_HOME`) |
| **Device Owner + lock task** | Quiosco más fuerte | `dpm set-device-owner` una vez + lista blanca nativa |

> Dispositivo de prueba principal: Amazon Fire 7 (9.ª gen, `mustang`), Fire OS 7.3.x = Android 9 (SDK 28). **iOS fuera de alcance.**

Fork GPL-3 de [Meems33/LauncherHijack](https://github.com/Meems33/LauncherHijack) (Launcher Replacer), basado en [BaronKiko/LauncherHijack](https://github.com/BaronKiko/LauncherHijack).

## Inicio rápido

1. Descarga el APK de la [última versión](https://github.com/marcelsavegnago/KidsHome/releases/latest) (o el artefacto `KidsHome-release-apk` de GitHub Actions).
2. Instala (la misma firma de prueba del CI conserva los datos al actualizar):

   ```bash
   adb install -r KidsHome-release.apk
   ```

3. Abre KidsHome → menú → **Configuración del dispositivo** y completa la lista de tu modo.
4. Menú → **Modo niño** → crea el PIN, configura apps / tiempo de pantalla y actívalo.

### A) Amazon Fire OS (secuestro por accesibilidad)

Fire OS **no** permite sustituir el lanzador predeterminado. KidsHome mantiene el secuestro clásico vía AccServ.

1. Activa la accesibilidad (UI **o** ADB).

   **UI:** Ajustes → Accesibilidad → **KidsHome home detection** → activar.

   **Componente ADB:**

   ```text
   io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
   ```

   ```bash
   adb shell settings put secure enabled_accessibility_services io.github.marcelsavegnago.kidshome/io.github.marcelsavegnago.kidshome.AccServ
   adb shell settings put secure accessibility_enabled 1
   ```

2. (Opcional, para la salida por esquina)

   ```bash
   adb shell appops set io.github.marcelsavegnago.kidshome SYSTEM_ALERT_WINDOW allow
   ```

3. En KidsHome, ve a **Modo niño** (o elige un lanzador de reserva si usas el secuestro sin modo niño).
4. Reinicia una vez. Pulsa Home: debería abrirse el inicio infantil / destino elegido (puede haber un destello breve del lanzador de Fire).

### B) Android normal (lanzador predeterminado)

1. Abre **Configuración del dispositivo** → modo **Automático** o **Lanzador predeterminado**.
2. Pulsa **Establecer como Home predeterminado** (`RoleManager.ROLE_HOME` en Android 10+; si no, ajustes de Home).
3. Elige **KidsHome** en el selector del sistema.
4. Concede overlay / exención de batería según se pida.
5. Opcionalmente activa la accesibilidad como **capa extra** para bloquear apps no permitidas.
6. Activa el **Modo niño**. Home debería abrir la cuadrícula infantil.

### C) Device Owner (más fuerte)

Device Owner solo se puede configurar si el dispositivo **no tiene cuentas** (quita cuentas de Google/Amazon) o tras un **restablecimiento de fábrica**, y normalmente antes de otros administradores.

1. Instala KidsHome.
2. Aprovisiona Device Owner (el componente debe coincidir exactamente):

   ```bash
   adb shell dpm set-device-owner io.github.marcelsavegnago.kidshome/.KidsHomeDeviceAdminReceiver
   ```

   El mismo comando se muestra / se puede copiar en **Configuración del dispositivo**.

3. En **Configuración del dispositivo**, confirma Device Owner = sí. Opcionalmente desactiva la barra de estado / keyguard mientras esté bloqueado.
4. Activa el **Modo niño**. KidsHome hará:
   - `setLockTaskPackages` para apps permitidas + navegador + él mismo
   - `addPersistentPreferredActivity` para HOME → `KidHomeActivity`
   - `startLockTask` desde el inicio infantil (quiosco)
5. La salida de emergencia con PIN llama a `stopLockTask` y puede desactivar el modo niño.

## Tiempo de pantalla y hora de dormir

En los ajustes de **Modo niño** puedes activar:

- **Límite diario de tiempo de pantalla** (minutos) — el inicio muestra el tiempo restante; al agotarse aparece el bloqueo “Tiempo agotado”.
- **Bloqueo a la hora de dormir** — franja de sueño (p. ej. 20:00–07:00); durante ese periodo el mensaje es “Es hora de dormir”.

Los padres desbloquean con el PIN para añadir 15/30 minutos, desactivar el límite por hoy o la hora de dormir esta noche.

## Salida de emergencia con PIN

Funciona en **todos** los modos:

- Toca **5 veces rápido la esquina superior derecha** (necesita “Mostrar sobre otras apps”), o
- **Mantén Atrás** unos 3 segundos

Luego introduce el PIN parental para desactivar el modo niño, abrir los ajustes de KidsHome o permanecer en modo niño.

## Preguntas frecuentes y solución de problemas

### ¿Cómo salir del modo niño por ADB?

```bash
adb shell am force-stop io.github.marcelsavegnago.kidshome
# O borrar preferencias (reinicia la configuración):
adb shell pm clear io.github.marcelsavegnago.kidshome
```

Si el lock-task de Device Owner está activo, quita Device Owner primero (ver abajo) o desbloquea con el PIN en la interfaz.

### Actualizar sin perder ajustes

Usa la misma clave de firma (keystore de prueba del CI por defecto) y reinstala con replace:

```bash
adb install -r KidsHome-release.apk
```

Cambiar a otra clave de firma exige desinstalar una vez antes.

### ¿Cómo quitar Device Owner?

En **Configuración del dispositivo** → **Quitar Device Owner** (PIN si el modo niño está activo), o la app llama a `clearDeviceOwnerApp`. Después:

```bash
adb uninstall io.github.marcelsavegnago.kidshome
```

Si no limpias Device Owner, Android se niega a desinstalar la app.

### Deshacer / desinstalar (sin Device Owner)

```bash
adb shell settings put secure enabled_accessibility_services ''
adb shell settings put secure accessibility_enabled 0
adb uninstall io.github.marcelsavegnago.kidshome
```

### Limitaciones conocidas

- Fire: a menudo no se puede desactivar `com.amazon.firelauncher`; el secuestro es la única opción de Home.
- Los modos accesibilidad / lanzador **no** son un MDM completo: ADB / forzar detención pueden saltárselos.
- Device Owner es fuerte, pero necesita un dispositivo limpio y ADB una vez; hay que limpiar el owner para desinstalar.
- Tras cierres por poca memoria en Fire, el primer Home puede ser lento: pulsa otra vez si hace falta.

## Compilar desde el código fuente

```bash
./gradlew assembleRelease
```

Requiere **JDK 17+** y Android SDK (`compileSdk` / `targetSdk` 34). `minSdk 22` (Fire 7 / SDK 28 totalmente compatible).

### Firma en CI

| Modo | Cuándo | Firma |
|------|--------|-------|
| **Clave de prueba estable del CI** | Sin secrets `SIGNING_*` | `keystore/debug.keystore` (en el repo) — misma firma entre builds, así `adb install -r` conserva datos |
| **Clave de release** | Secrets definidos | Tu keystore privado |

| Secret | Uso |
|--------|-----|
| `SIGNING_KEYSTORE_BASE64` | `.jks` / `.keystore` en Base64 |
| `SIGNING_KEY_ALIAS` | Alias de la clave |
| `SIGNING_KEY_STORE_PASSWORD` | Contraseña del keystore |
| `SIGNING_KEY_PASSWORD` | Contraseña de la clave |

Consulta `keystore/README.md`.

## Publicar una versión

1. Actualiza `versionName` / `versionCode` en `app/build.gradle`.
2. Sube una etiqueta `vX.Y.Z`:

   ```bash
   git tag v1.3.0
   git push origin v1.3.0
   ```

3. GitHub Actions (`.github/workflows/android.yml`) genera el APK de release y lo adjunta al GitHub Release.

## Contribuir

Issues y pull requests son bienvenidos. Mantén los cambios acotados, prueba en un tablet Fire real o un emulador al tocar enforcement / accesibilidad, y usa inglés para código y comentarios.

Regeneración de capturas / GIF (opcional): ver [`.github/workflows/screenshots.yml`](.github/workflows/screenshots.yml) y [`docs/capture-screenshots.sh`](docs/capture-screenshots.sh).

## Licencia

KidsHome se publica bajo la **GNU GPL v3** — ver [LICENSE](LICENSE).

Créditos: BaronKiko (LauncherHijack), Meems33 / WhiteRaven777 (Launcher Replacer).

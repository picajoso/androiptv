# AndroIPTV — guía para continuar el proyecto

Este archivo describe el estado del proyecto para quien lo retome más adelante, sea Claude Code, Codex u otro asistente. Léelo junto con `README.md` antes de modificar la app.

## Objetivo y alcance

AndroIPTV es una aplicación Android independiente inspirada en MacIPTV. Su objetivo actual son teléfonos y tabletas. Android TV queda expresamente fuera del alcance por ahora.

La app permite configurar una lista M3U por URL o archivo, o una cuenta Xtream Codes; explorar grupos y canales; buscar, marcar favoritos, consultar recientes y guía XMLTV; reproducir vídeo y recordar el último canal. Los datos del proveedor se guardan cifrados en el dispositivo.

Este repositorio contiene el proyecto Android, separado del proyecto macOS MacIPTV. No mezcles los dos proyectos ni edites el repositorio macOS para implementar funciones Android.

## Estado actual

- Proyecto Android independiente, paquete `es.maciptv.android`, versión `0.1.0`.
- Compila con `assembleDebug`; el APK de desarrollo está en `app/build/outputs/apk/debug/app-debug.apk`.
- Instalado en el AVD `Medium_Phone_API_37.0` de Android Studio.
- Se cargó una fuente Xtream de uso del propietario en el emulador. No copies aquí su URL, usuario, contraseña ni URLs de canales: pueden incluir credenciales.
- Se comprobó reproducción de canales reales del proveedor en el emulador, incluida la categoría de deportes. El canal se reanuda después de volver a abrir la app.
- Se implementó pantalla completa: al activarla fuerza apaisado, oculta barras del sistema y paneles, muestra controles Media3 al tocar el vídeo y ofrece «Ver sin recortar» / «Rellenar pantalla» más «Salir».
- Una lista grande llegó a provocar `OutOfMemoryError` porque se creaba una vista Android por cada canal. La lista se cambió a `ListView` con `BaseAdapter`, que crea y reutiliza filas visibles. No volver a materializar todos los canales en vistas individuales.
- La última compilación e instalación incluyó el modo pantalla completa y la lista virtualizada. Se verificó en el emulador que la reproducción seguía activa al cambiar de orientación y que el ajuste sin recorte conservaba el marcador del partido.

El emulador puede desconectarse de ADB o cerrarse; eso no equivale por sí solo a un fallo de la app. Comprueba `adb devices` y el proceso antes de atribuirle un cierre. El emulador API 37 también produjo una traza nativa transitoria de `MediaCodec`; en otra ocasión el evento fue un `OutOfMemoryError` de la UI. La virtualización de filas corrigió esta segunda causa.

## Entorno y compilación

- Android Gradle Plugin: `9.4.0`.
- Gradle Wrapper: `9.6.0`.
- `compileSdk`: 37; `targetSdk`: 35; `minSdk`: 26 (Android 8.0).
- Java/Kotlin: toolchain Java 17; AGP 9 usa Kotlin integrado.
- Media3: `1.11.1` (ExoPlayer, HLS, UI y OkHttp DataSource).
- OkHttp: `4.12.0`; AndroidX Activity `1.13.0`; AndroidX Core `1.17.0`.
- Android SDK: usa la ruta configurada en `ANDROID_HOME` o en el Android Studio local.

Desde el directorio del proyecto:

```sh
./gradlew --no-daemon assembleDebug
```

Si Gradle no encuentra el SDK, abre el directorio en Android Studio o establece `ANDROID_HOME` a la ruta del SDK. Si necesita descargar Gradle o dependencias, la máquina debe tener acceso a los repositorios de Google/Maven Central y `services.gradle.org`.

Para instalar y abrir manualmente en el AVD:

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n es.maciptv.android/.MainActivity
```

El emulador disponible en el equipo se inicia con:

```sh
emulator -avd Medium_Phone_API_37.0
```

No hay una suite de pruebas Android creada todavía. Hasta ahora la comprobación ha sido compilación y uso manual en el emulador; no describas una compilación como prueba de todos los flujos ni como prueba en un móvil físico.

## Mapa del código

Código Kotlin: `app/src/main/java/es/maciptv/android/`.

| Archivo | Responsabilidad |
| --- | --- |
| `MainActivity.kt` | Interfaz programática, fuente y selector de archivos, grupos/búsqueda, lista virtualizada, navegación, guía, pantalla completa, estados de carga y reanudación. Es el punto de entrada y actualmente concentra bastante lógica. |
| `Models.kt` | `SourceConfig`, `Channel`, `Programme`, construcción de URLs Xtream e identificadores persistentes de canal. |
| `SecureStore.kt` | Cifrado AES/GCM de la configuración de proveedor con clave Android Keystore; preferencias para último canal, favoritos y recientes. |
| `SafeNetwork.kt` | Cliente OkHttp, validación HTTP/HTTPS, redirecciones, límites de respuesta y bloqueo DNS de IP privadas salvo autorización de servidor local. |
| `PlaylistParsers.kt` | Parseo de M3U y XMLTV con límites de tamaño/canales/programas. |
| `PlaybackController.kt` | ExoPlayer/Media3, reproducción HLS y progresiva, User-Agent compatible con el proveedor, reintentos y fallback de Xtream HLS a la URL TS original en respuestas 4xx. |
| `DemoContent.kt` | Canales HLS públicos y programación ficticia para la demo. |
| `AndroidManifest.xml` | Permiso de red, etiqueta, tema, backup desactivado, actividad principal y cambios de configuración de orientación. |

Los ajustes del proyecto están en `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` y `app/build.gradle.kts`. No hay layouts XML de pantalla: la interfaz se construye desde Kotlin.

## Flujo principal de datos

1. `MainActivity` recupera `SourceConfig` desde `SecureStore` al iniciar.
2. La fuente se descarga/importa en un executor de fondo. Un contador de generación descarta respuestas de cargas antiguas si el usuario cambia de fuente o actualiza varias veces.
3. `PlaylistParsers.m3u` convierte entradas válidas en canales. Xtream usa `get.php` con `type=m3u_plus&output=ts`; la guía se obtiene de `xmltv.php`.
4. La lista se filtra por grupo, favoritos, recientes y texto de búsqueda. `ListView`/`BaseAdapter` recicla filas; `displayedChannels` contiene los resultados y no debe convertirse en decenas de miles de `View` persistentes.
5. Al elegir canal, se persiste su ID y se actualizan recientes. Al volver a abrir se busca ese ID en la lista importada y se reanuda.
6. `PlaybackController` crea una instancia de red y entrega HLS o TS a Media3. En Xtream, las rutas `live/.../<id>.ts` se prueban primero como `.m3u8`; ante HTTP 4xx se intenta la URL original.

## Decisiones y límites técnicos

### Identidad de canal

`stableId` usa SHA-256 sobre esquema, host, puerto, ruta, `tvg-id`, nombre y grupo; no incluye query ni fragmento para sobrevivir a tokens variables. Cambiar esas reglas afecta a favoritos, recientes y reanudación. Evita imprimir URLs de IPTV, que suelen llevar usuario/contraseña en query o ruta.

### Almacenamiento

La configuración completa de la fuente se cifra con AES/GCM y una clave no exportable de Android Keystore. Favoritos, recientes y último ID se guardan en `SharedPreferences`; no guardes secretos en esas claves, en logs, en excepciones visibles ni en este documento. `allowBackup=false` evita restaurar datos cifrados sin la clave original.

### Red

El tráfico HTTP requiere que el usuario lo permita en el formulario de esa fuente. `SafeNetwork` desactiva redirecciones automáticas y valida cada salto; rechaza HTTPS→HTTP. El resolvedor bloquea direcciones privadas, loopback, link-local y multicast, salvo que el usuario marque explícitamente servidor local. Revisa esta política antes de añadir otro cliente HTTP o cambiar la carga de segmentos HLS: toda solicitud de manifiesto, segmento y clave debe mantener las mismas protecciones.

En fuentes por URL, la autorización local se limita al host configurado. Para M3U local se permite el tráfico privado solo si se activó la opción local al importarlo. No conviertas este permiso en una excepción global.

### Reproductor

Media3 usa `VLC/3.0.21 LibVLC/3.0.21` como User-Agent de vídeo, alineado con el perfil que MacIPTV necesita para algunos proveedores. La descarga de la lista usa `AndroIPTV/1.0`. Los errores HTTP 4xx no se reintentan a ciegas; Xtream prueba primero la variante HLS y luego la fuente TS original.

El reproductor Android no comparte el relay HLS de MacIPTV. Si una fuente falla en manifiestos/segmentos o renueva tokens entre solicitudes, investiga primero la cadena de causas de `PlaybackException` y la respuesta HTTP, sin registrar URLs completas; después evalúa un relay seguro Android. No supongas que un canal HLS que funciona en Mac se comportará igual en Media3.

### Adaptación de pantalla

El botón ⛶ activa `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, oculta paneles y barras del sistema y asigna `RESIZE_MODE_ZOOM` por defecto para ocupar la pantalla. «Ver sin recortar» cambia a `RESIZE_MODE_FIT`; volver a «Rellenar pantalla» cambia a zoom. El botón «Salir» o el botón Atrás restaura la interfaz. Conserva el canal durante rotaciones y evita recrear el reproductor.

Al cambiar orientación, `onConfigurationChanged` reconstruye la interfaz y vuelve a conectar el mismo `PlayerView`. Si se modifica el ciclo de vida, garantiza que el player quede adjunto a una sola vista y se libere en `onDestroy`.

## Progreso funcional

- Configuración Xtream Codes, M3U por URL y M3U por selector de documentos.
- Opción de XMLTV explícita para M3U; URL XMLTV de Xtream automática.
- Parseo con límites para listas y guías.
- Búsqueda, grupos, favoritos, recientes, canal anterior/siguiente y refresh.
- Reanudación del último canal.
- Reproductor Media3 con vídeo HLS/progresivo, reintentos de fallos transitorios y fallback TS Xtream.
- Guía de programación básica por canal; guía demo ficticia.
- Aviso antes de pasar una URL potencialmente secreta a una app externa.
- Demo de reproducción y EPG.
- Paneles plegables y pantalla completa apaisada, con ajuste zoom/fit.
- Almacenamiento cifrado del proveedor y restricciones de red explícitas.

## Pendiente y posibles siguientes pasos

Prioriza estas tareas según los problemas que aparezcan en el emulador o en el móvil:

1. Añadir pruebas unitarias para M3U, XMLTV, generación de URLs Xtream, identidad estable y cifrado/descifrado.
2. Separar estado, carga de datos y lógica de reproducción de `MainActivity` hacia clases con ciclo de vida claro; valorar ViewModel y repositorio.
3. Mejorar el parser M3U: actualmente extrae el nombre con la primera coma y los atributos mediante regex; nombres con comas entrecomilladas y sintaxis inusual merecen cobertura compatible con el parser macOS.
4. Endurecer XMLTV frente a DTD/entidades y aceptar variantes de zona horaria que aparezcan en proveedores reales.
5. Añadir imágenes/logos de canales y una guía visual más cercana a MacIPTV.
6. Valorar un relay HLS Android con validación de cada manifiesto, segmento, clave y redirección si los proveedores muestran fallos de token/segmento.
7. Probar API 26 y un dispositivo físico, además del AVD API 37; comprobar rotación, listas grandes, HEVC, sonido, cambios rápidos de canal y reanudación.
8. Añadir una experiencia de tableta más pulida y persistir preferencias visuales como el modo zoom/fit si el usuario lo solicita.

No se ha implementado Android TV, reproducción en segundo plano, Picture-in-Picture ni control remoto específico.

## Reglas para quien continúe

- Preserva la separación con el proyecto macOS.
- No guardes ni muestres credenciales reales en archivos, logs, capturas compartidas o mensajes.
- Para cambios de reproducción, diferencia errores de URL/proveedor, HTTP, manifiesto HLS, segmento, decodificador y OOM. No diagnostiques solo a partir de una pantalla negra.
- Para listas grandes, usa vistas recicladas y trabajo de red/parseo fuera del hilo principal.
- Después de cambios Kotlin, compila con `./gradlew --no-daemon assembleDebug`. Para afirmar que un fallo está arreglado, reproduce el flujo afectado en el emulador/dispositivo y registra qué se verificó.
- Actualiza este documento y `README.md` cuando cambien arquitectura, requisitos o estado relevante.

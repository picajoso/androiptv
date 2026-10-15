# Handoff: rediseño de AndroIPTV (móvil, vertical y horizontal)

> **Actualización v2:** lee también `CAMBIOS-v2.md`. Pasa Canales y Guía en retrato a dos pasos, añade la tabla EPG y completa el reproductor vertical. Donde se contradigan, manda `CAMBIOS-v2.md`.

## Overview
Nueva interfaz para AndroIPTV (`picajoso/androiptv`). Sustituye la barra de 7 botones grises, los paneles de grupos/canales como botones y los diálogos de guía y fuente por:
- **Vertical**: previsualización del canal arriba, lista de canales con "ahora" y progreso, pestañas inferiores Canales / Guía / Ajustes, reproductor con guía del día.
- **Horizontal con paneles**: tres columnas (grupos | canales | reproductor), cabecera roja.
- **Horizontal a pantalla completa**: superposición oscura con brillo, volumen, anterior/siguiente con nombre, barra de acciones y panel lateral de canales.

## About the Design Files
Los archivos de esta carpeta son **referencias de diseño hechas en HTML**: prototipos que muestran el aspecto y el comportamiento previstos, no código de producción. La tarea es **recrearlos en el proyecto Android existente** (Kotlin + Views creadas por código + Media3), siguiendo sus patrones. 1 px CSS del prototipo = **1 dp**; tamaños de texto en px = **sp**.

## Fidelity
**Alta fidelidad.** Colores, tipografía, medidas y comportamiento son finales. El vídeo y los logos del prototipo son marcadores: en la app se usa `PlayerView` y `Channel.logo` (tvg-logo).

---

## Design Tokens (sistema "Modernist")

### Colores
| Token | Hex | Uso |
| --- | --- | --- |
| bg | `#F3F2F2` | Fondo general (modo claro) |
| surface | `#EAE9E9` | Campos de texto, diálogos |
| text | `#201E1D` | Texto, fila/celda seleccionada (fondo) |
| accent | `#EC3013` | Acción principal, pestaña activa, progreso, DIRECTO, favorito activo |
| accent-100 | `#FFF2EF` | Fondo de la fila del canal en emisión |
| accent-400 | `#FF9783` | Acento sobre fondo oscuro, pulsado en oscuro |
| accent-600 | `#DD2B0F` | Pulsado sobre claro |
| accent-700 | `#AE1800` | Texto pequeño en rojo (etiquetas "FUENTES", "AHORA ·") |
| divider | `#201E1D` al 40 % (`#66201E1D`) | Todas las reglas |
| neutral-200 | `#EAE7E7` | Etiquetas ES/FHD, hover/pressed de filas |
| neutral-300 | `#D7D3D3` | Pista de la barra de progreso |
| neutral-400 | `#BAB6B6` | Interruptor apagado |
| neutral-600 | `#7D7979` | Corazón inactivo, programas pasados |
| neutral-700 | `#605D5D` | Texto secundario |
| neutral-800 | `#444141` | Texto de etiqueta |
| neutral-900 | `#2D2B2B` | Velo de diálogos (al 50 %), tiles en oscuro |
| dark-bg | `#1A1817` | Panel lateral de pantalla completa |
| dark-row-cur | `#3A1A14` | Canal en emisión en el panel oscuro |
| on-dark | `#F3F2F2`; secundario `#D7D3D3` / `#BDB9B8`; reglas `#F3F2F2` al 15–30 % | Pantalla completa |
| video-placeholder | `#000000` | Fondo del `PlayerView` |

### Tipografía
Una sola familia: **Archivo** (Google Fonts; pesos 400, 600, 800). Añádela en `res/font/` (archivos TTF o fuente descargable).
| Rol | Tamaño / peso |
| --- | --- |
| Título de pantalla ("Canales", "Guía", "Ajustes") | 22–26 sp / 800, tracking −0.02 |
| Nombre de canal en reproductor | 22 sp / 800 |
| Nombre de canal en lista | 15 sp / 800 (13 sp en horizontal) |
| Cuerpo / programa actual | 12.5–13.5 sp / 400 |
| Botones, pestañas, chips de grupo | 13 sp / 600 (800 si activo) |
| Kicker (texto en mayúsculas sobre títulos) | 10 sp / 400, mayúsculas, tracking +0.1, color accent-700 |
| Etiquetas ES/FHD | 9.5 sp, tracking +0.06, padding 1×5 dp, fondo neutral-200, texto neutral-800 |
| Etiqueta DIRECTO | 9.5–10 sp, tracking +0.1, fondo accent, texto blanco |
| Horas, metadatos | 10.5–11 sp / 400, neutral-700 |

### Forma y espaciado
- **Radio de esquina: 0 dp en todo.** Ni botones, ni tarjetas, ni diálogos redondeados.
- Reglas: **2 dp** entre secciones principales (bajo cabeceras, sobre pestañas, alrededor de rejillas), **1 dp** entre filas y celdas. Color: divider.
- Escala: 4, 8, 12, 16, 24, 32 dp. Margen lateral estándar: 16 dp.
- Objetivo táctil mínimo: **44 dp**.
- Sombras: solo en avisos (toast) y diálogos: `0 3dp 10dp #2D2B2B @16 %` / `0 12dp 32dp @22 %` (en Android: elevation 4 dp / 12 dp).
- **Alineación**: todo a la izquierda, también la etiqueta dentro de botones anchos.

### Iconos
**Lucide** (lucide.dev), trazo de 2 px, como vector drawables (descarga el SVG y usa Android Studio > Vector Asset). Usados: `search, x, heart, audio-lines, play, pause, maximize, arrow-left, skip-back, skip-forward, cast, external-link, bell, bell-ring, tv, calendar-days, settings, refresh-cw, trash-2, link, folder-open, server, circle-play, check, list, sun, volume-2, moon, scan, history, lock, signal, wifi, battery-full`. Tamaños: 16–22 dp (28 dp para anterior/siguiente en pantalla completa).

### Estados
- Hover/pressed de filas: fondo neutral-200. Botón principal pulsado: accent-600 (accent-400 en oscuro).
- Foco de teclado: contorno de 2 dp en accent.
- Deshabilitado: opacidad 45 %.

---

## Limpieza de nombres de canal (nuevo `ChannelName.kt`)
Entrada M3U → nombre + etiquetas:
- `|ES| M+ DEPORTES FHD` → nombre **M+ Deportes**, país **ES**, calidad **FHD**
- `|ES| DAZN LALIGA FHD (HEVC)` → **DAZN LaLiga**, ES, HEVC (si hay códec, la etiqueta es el códec)
- `US - ESPN HD` → **ESPN**, US, HD

Reglas: prefijo de país `|XX|`, `XX:` o `XX - ` → país. Sufijos `UHD|4K|FHD|HD|SD|HEVC|H265` (con o sin paréntesis) → calidad. El resto pasa a "Title Case", conservando en mayúsculas los acrónimos y marcas conocidos (`M+, DAZN, ESPN, TV, F1, MLB, NBA, CNN, BBC, HBO, TNT`) y la lista `LaLiga`. Si no se reconoce nada, se muestra el nombre original. **La búsqueda busca tanto en el nombre limpio como en el original.**

---

## Screens / Views

### Comunes (vertical)
- Barra de estado del sistema: oscura (`#111` con iconos claros) en la pestaña Canales porque arriba hay vídeo; clara en Guía y Ajustes.
- **Pestañas inferiores** (siempre visibles salvo en el reproductor): 3 celdas iguales de 56 dp de alto, regla superior de 2 dp, separadores verticales de 1 dp. Cada celda: icono de 18 dp + texto 13 sp, alineados a la izquierda con 14 dp de padding. Activa: fondo accent, texto bg, peso 800. Inactiva: transparente, texto text, peso 600. Etiquetas: **Canales** (tv), **Guía** (calendar-days), **Ajustes** (settings).

### 1. Canales (vertical)
De arriba a abajo:
1. **Previsualización** (solo si hay canal actual): ancho completo, 16:9 (191 dp con 340 dp de ancho). `PlayerView` real reproduciendo el canal actual. Encima:
   - Arriba a la derecha: botón de 44×44 dp con fondo negro al 35 % e icono maximize → pantalla completa horizontal.
   - Abajo, sobre un degradado de transparente a negro al 75 %: etiqueta DIRECTO + nombre del canal (15 sp/800, blanco); programa actual (12 sp, `#D7D3D3`); barra de progreso de 2 dp (pista blanca al 30 %, relleno accent) + hora de fin (10.5 sp). A la derecha, botón cuadrado de 44 dp en accent con play/pausa.
   - En pausa: cuadrado accent de 52 dp con icono de reproducir en el centro.
   - Tocar el vídeo (fuera de los botones) abre el **Reproductor**.
2. **Cabecera**: padding 10/16 dp, regla inferior de 2 dp. A la izquierda (tocable → Ajustes): kicker `Mi lista · URL M3U · 1240` (nombre de la fuente · tipo · número de canales) y el título **Canales** (22 sp/800). A la derecha, un botón de búsqueda de 44 dp con borde de 1 dp; si está activo, fondo text e icono bg.
3. **Campo de búsqueda** (si está activa): 48 dp, fondo surface, regla inferior de 1 dp; icono, `EditText` con el texto de ayuda "Buscar canales o grupos" y botón x (limpia y cierra).
4. **Grupos**: fila horizontal desplazable de 44 dp de alto. Cada chip: padding de 14 dp, texto 13 sp/600 más el recuento (10.5 sp, opacidad 65 %), separador derecho de 1 dp. Orden: **Todos, Favoritos, Recientes** y luego los grupos de la lista en su orden de aparición. Activo: fondo text, texto bg.
5. **Lista** (`RecyclerView`/`ListView`): fila con columnas `52 dp | flexible | 44 dp`, separación de 12 dp, padding `10 4 10 16`, regla inferior de 1 dp.
   - Logo: 52×52 dp, fondo blanco, borde de 1 dp en divider. Imagen de `channel.logo` en modo fitCenter; si no hay, las iniciales en 13 sp/800.
   - Línea 1: nombre limpio (15 sp/800, una línea con elipsis) + etiquetas país y calidad + icono `audio-lines` en accent (14 dp) si es el canal actual.
   - Línea 2: programa actual de la guía (12.5 sp, una línea).
   - Línea 3: barra de progreso de 2 dp (pista neutral-300, relleno accent) + hora de fin (10.5 sp, neutral-700).
   - Corazón de 44 dp: accent si es favorito y neutral-600 si no. Al tocarlo cambia el favorito y aparece el aviso "Añadido a Favoritos" / "Quitado de Favoritos".
   - Fila del canal actual: fondo accent-100.
   - Tocar la fila: reproduce el canal y abre el Reproductor.
   - Sin guía para un canal: se ocultan las líneas 2 y 3.
6. **Estados vacíos** (padding 40/24 dp; icono de 26 dp en accent, título 17 sp/800, texto 13 sp neutral-700):
   - Favoritos: "Sin favoritos" / "Toca el corazón de un canal para guardarlo aquí."
   - Recientes: "Nada reciente" / "Los canales que veas aparecerán aquí."
   - Búsqueda: "Sin resultados" / `Nada coincide con "<q>" en <grupo>.`
   - Sin fuente: "Sin canales" / "Conecta un proveedor o prueba la demo"

### 2. Reproductor (vertical)
Se abre desde una fila o desde la previsualización. Oculta las pestañas inferiores.
1. **Vídeo** 16:9 a ancho completo (`PlayerView`, `useController=false`; los controles son propios). Botón atrás de 44 dp arriba a la izquierda (fondo negro al 35 %) → vuelve a Canales; maximize arriba a la derecha → pantalla completa horizontal. Abajo: DIRECTO + progreso + hora de fin. Tocar el vídeo: play/pausa.
2. **Información** (padding `14 4 12 16`, regla inferior de 2 dp): nombre (22 sp/800) + etiqueta de calidad; programa actual (13 sp); `10:00–12:00 · quedan 1 h 29 min · ES - Deportes` (11 sp, neutral-700). A la derecha, corazón de 44 dp con borde de 1 dp.
3. **Acciones**: 4 celdas iguales de 64 dp de alto, separadores verticales de 1 dp, regla inferior de 2 dp. Icono de 18 dp arriba y texto 11.5 sp/600 abajo, a la izquierda: **Anterior**, **Siguiente**, **Enviar** (cast), **Abrir fuera**.
4. **Guía · hoy** (16 sp/800) + fecha a la derecha (11 sp). Tabla: columnas `60 dp | flexible | 44 dp`, reglas de 1 dp, separador vertical entre hora y título.
   - Pasado: texto neutral-600. **Ahora**: fondo text, texto bg. Futuro: texto normal + campana (bell / bell-ring en accent si hay recordatorio).
   - Si el título empieza por "DIRECTO", se quita del texto y se muestra la etiqueta DIRECTO encima del título.

### 3. Guía (vertical)
1. Título **Guía** (26 sp/800), regla de 2 dp.
2. Selector de canal: fila horizontal de celdas de 68×60 dp (iniciales 12 sp/800 + `ES · FHD` 10 sp). Activa: fondo text, texto bg.
3. Selector de día: 2 celdas iguales de 40 dp: "Hoy · mar 14" / "Mañana · mié 15". Activa: fondo accent, texto bg.
4. Cabecera del canal: nombre (18 sp/800) + grupo; botón **Ver canal** (40 dp, fondo accent, 13 sp/800, icono play) → reproduce y va a Canales > Reproductor.
5. Tabla de programación igual que en el Reproductor. En "Mañana" todo es futuro.

### 4. Ajustes (vertical)
1. Título **Ajustes**, regla de 2 dp.
2. Kicker **FUENTES**. Filas: nombre (15 sp/800) + etiqueta **ACTIVA** (en accent) + `URL M3U · 1.240 canales · hace 1 h`; botones refresh y trash de 44 dp. La fila activa tiene fondo accent-100. Tocar una fila la activa y la carga.
3. **Añadir fuente**: rejilla de 2×2 celdas de 84 dp (icono de 20 dp en accent arriba; título 13.5 sp/700 y descripción 11 sp abajo): **URL M3U** ("Enlace + guía XMLTV"), **Archivo M3U** ("Desde el teléfono" → selector de documentos actual), **Xtream Codes** ("Servidor y usuario"), **Demostración** ("Canales de prueba").
4. Kicker **REPRODUCCIÓN**.
   - "Imagen a pantalla completa": control segmentado **Rellenar | Sin recortar** (`RESIZE_MODE_ZOOM` / `FIT`).
   - "Temporizador de apagado": **No | 30 min | 60 min | 90 min**.
   - Segmentado: borde de 1 dp, opciones iguales con texto a la izquierda (13 sp, padding 9×12 dp); la activa en accent con texto bg.
   - "Reanudar último canal al abrir" con el nombre del canal debajo: interruptor cuadrado de 40×22 dp (accent encendido, neutral-400 apagado) con un mando cuadrado de 16 dp.

### 5. Hoja "Configurar proveedor" (sustituye a `editSource`)
Hoja inferior a ancho completo con fondo bg, regla superior de 2 dp en text y velo neutral-900 al 50 %.
- Cabecera: kicker "CONFIGURAR PROVEEDOR" + título **URL M3U** o **Xtream Codes**, y botón x.
- Campos (alto 44 dp, fondo surface, borde de 1 dp en divider que pasa a accent con el foco, etiqueta de 12 sp encima):
  - URL M3U: Nombre, URL M3U, URL XMLTV (opcional).
  - Xtream: Nombre, URL del servidor, Usuario, Contraseña.
- Casillas cuadradas de 18 dp (accent con check al marcarlas): "Permitir HTTP para esta fuente", "Servidor local".
- Pie de 2 celdas de 56 dp: **Cancelar** (transparente) | **Guardar** (accent).
- Validación: URL vacía → aviso "Introduce la URL del proveedor". En Xtream, usuario vacío → "Introduce el usuario".

### 6. Diálogos (sustituyen a AlertDialog)
Fondo surface, sin esquinas, elevación de 12 dp, título 20 sp/800, texto 14 sp neutral-800, pie de 2 celdas de 52 dp (Cancelar | acción en accent).
- **Abrir en otra app**: "La URL del canal puede incluir credenciales. La otra app recibirá la URL completa." → Abrir.
- **Quitar fuente**: "Se eliminará la fuente guardada." → Quitar.

### 7. Aviso (sustituye a Toast)
Franja con fondo text, texto bg de 13.5 sp/600, padding 12×16 dp, 12 dp de margen lateral, encima de las pestañas. Dura 2 s.

### 8. Horizontal con paneles (`isWide()` y sin pantalla completa)
Rejilla: columnas de **170 dp | 250 dp | resto**, cabecera de 44 dp.
- **Cabecera** en accent, texto bg, con separadores de 1 dp en bg al 50 % alineados con las columnas: "AndroIPTV" (18 sp/800) | campo de búsqueda (comparte texto con el vertical) | `Mi lista · URL M3U · 1240` + botones refresh y settings de 44 dp.
- **Grupos**: filas de 40 dp, nombre a la izquierda y recuento a la derecha (10.5 sp, opacidad 65 %), regla de 1 dp. Activo: fondo text, peso 800. Regla derecha de 2 dp.
- **Canales**: filas con columnas `38 dp | flexible | 36 dp` (logo 38 dp; nombre 13 sp/800 + programa 11 sp; corazón 16 dp). Actual: accent-100. Tocar = reproducir sin salir de la vista. Regla derecha de 2 dp.
- **Reproductor**: vídeo de 190 dp de alto. Barra inferior sobre degradado: icono play/pausa, DIRECTO, progreso, cast y maximize (40 dp). Debajo, 2 columnas **AHORA · 10:00–12:00** (kicker accent-700 + título 13 sp/800) | **DESPUÉS · 12:00** (neutral-700). Regla de 2 dp. Acciones: 5 celdas de 44 dp (icono de 15 dp + 11.5 sp/600): Anterior, Siguiente, Favorito (accent si está activo), Apagado / `30 min` (accent si está activo), Abrir fuera.

### 9. Horizontal a pantalla completa (oscuro)
`PlayerView` a pantalla completa, barras del sistema ocultas (como ahora). Los controles aparecen y desaparecen al tocar el vídeo; se ocultan solos a los 4 s si se está reproduciendo.
- **Degradado**: negro al 70 % arriba → transparente entre el 35 % y el 60 % → negro al 80 % abajo.
- **Arriba** (24 dp lateral, 14 dp arriba): botón atrás de 44 dp (sale de pantalla completa) | nombre (17 sp/800) + etiqueta de calidad (fondo blanco al 18 %) y programa actual (12.5 sp, `#D7D3D3`) | botón **Canales** (44 dp, borde de 1 dp en blanco al 40 %, icono list).
- **Brillo** (izquierda, a 30 dp del borde) y **volumen** (derecha): icono de 20 dp + pista vertical de 4 dp (blanco al 25 %) con relleno blanco desde abajo, unos 150 dp de alto. Se ajustan tocando o arrastrando en vertical. Brillo = `window.attributes.screenBrightness`; volumen = `AudioManager.STREAM_MUSIC`.
- **Centro**: anterior (icono de 28 dp + nombre del canal anterior en 11 sp) · botón cuadrado de 64 dp en accent con play/pausa · siguiente (icono + nombre). Separación de 48 dp.
- **Progreso** (a 60 dp del borde inferior): DIRECTO, hora de inicio, pista de 3 dp (blanco al 30 %, relleno accent), hora de fin.
- **Acciones** (abajo, 6 celdas iguales de 44 dp con regla superior de 1 dp en blanco al 30 %; icono de 17 dp + 12 sp/600; las activas en accent-400):
  - **Enviar** (cast).
  - **Favorito**.
  - **Apagado**: muestra `30 min` cuando está activo y alterna No → 30 → 60 → 90.
  - **Rellenar** / **Sin recortar**: cambia el modo de imagen.
  - **Último**: vuelve al canal anterior, como el "←" actual.
  - **Bloquear**: oculta todos los controles y deja solo el botón "Pantalla bloqueada · toca para desbloquear" (48 dp, abajo a la izquierda, fondo negro al 60 %, borde blanco al 40 %). El resto de toques se ignoran.
- **Panel de canales**: a la derecha, 300 dp de ancho, fondo `#1A1817`, regla izquierda de 2 dp. Título = grupo actual ("Todos los canales") + x. Filas: tile de 36 dp `#2D2B2B` + nombre (13 sp/800) + programa (11 sp, `#BDB9B8`). Fila actual: `#3A1A14`. Tocar cambia de canal sin cerrar la pantalla completa.

---

## Interactions & Behavior
- **Anterior/Siguiente**: recorren `visibleChannels()` (grupo y búsqueda actuales) y se quedan en los extremos, como ahora. En los extremos aparece el aviso "Primer canal de la lista" / "Último canal de la lista".
- **Seleccionar canal**: actualiza `previous`, `lastChannel` y `recents`, como `selectChannel()` hoy.
- **Pantalla completa**: se entra con maximize (vertical o paneles) y se sale con atrás (botón o gesto del sistema). Cambiar la orientación a horizontal en el reproductor vertical también entra. La previsualización y el reproductor usan **un único** `PlayerView`, que se mueve de contenedor como ya hace `buildUi()`.
- **Temporizador**: `Handler.postDelayed` que pausa el reproductor al terminar. Se cancela al elegir "No".
- **Recordatorios** de la guía: Fase 3, con `AlarmManager` + notificación. Antes de eso, la campana solo guarda el estado y muestra "Te avisaremos a las HH:MM".
- **Enviar (Cast)**: Fase 3, con el módulo `media3-cast` (dependencia nueva; pide permiso). Hasta entonces, el aviso "Buscando dispositivos…".
- **Carga**: mientras carga, el kicker de la cabecera dice "Cargando canales…" y la lista muestra 6 filas vacías (bloques neutral-200). Los errores van por el aviso, con los textos actuales de `showError`.

## State Management
Ya existe: `source, channels, guide, selected, previous, group, fullscreen, fillVideo`, favoritos, recientes y último canal en `SecureStore`.
Nuevo:
- `tab` (canales | guia | ajustes) y `portraitScreen` (lista | reproductor).
- `searchOpen` y el texto de búsqueda, compartido entre vertical y paneles.
- `guideChannelId` y `guideDay` (0 = hoy, 1 = mañana).
- `sleepTimerMin` (0/30/60/90), `resumeLastChannel` (Boolean, **persistido**) y `fillVideo` (**persistido**).
- `controlsVisible`, `locked`, `channelDrawerOpen` (pantalla completa).
- `reminders: Set<String>` con claves `channelId|startMillis` (persistido).
- **Fuentes múltiples (Fase 2)**: `SecureStore` guarda hoy una sola `SourceConfig`. Pasa a `List<SavedSource(id, name, config, channelCount, updatedAt)>` + `activeSourceId`, cifrado igual que ahora. Migra la fuente existente como "Mi lista".

## Plan de implementación
1. **Fase 1, visual sin cambiar datos**: `Theme.kt` (colores, dp, fuente Archivo, fondos con reglas), `ChannelName.kt`, la nueva fila de canal, las pestañas inferiores, Canales (previsualización, cabecera, búsqueda, grupos y lista), Reproductor vertical con guía en tabla, Guía y paneles en horizontal. Elimina la barra superior de 7 botones y la línea de estado inferior.
2. **Fase 2**: controles de pantalla completa propios (brillo, volumen, acciones, bloqueo, panel de canales, temporizador), Ajustes, hoja de proveedor, diálogos y avisos propios, fuentes múltiples.
3. **Fase 3**: recordatorios con notificación, Cast y logos con caché (cargador ligero propio o Coil, que hay que consultar).

## Assets
- Fuente **Archivo**: Google Fonts, licencia OFL.
- Iconos **Lucide**: licencia ISC, SVG → vector drawable.
- Logos de canal: `Channel.logo` (tvg-logo). Si no hay, iniciales del nombre limpio.
- No hay imágenes propias.

## Files
- `IPTV Prototipo.dc.html`: prototipo interactivo final. Ábrelo en un navegador con esta misma estructura de carpetas (necesita `support.js` y `_ds/`).
- `_ds/…/styles.css`: los tokens del sistema Modernist (la fuente de los valores de arriba).
- `referencia/IPTV Directions.dc.html`: las tres direcciones exploradas. Se eligió 1a (vertical), 1c (paneles horizontales) y 1b (pantalla completa).
- `PROMPT.md`: el texto para pegar en el asistente de código (primera iteración).
- `CAMBIOS-v2.md` + `PROMPT-v2.md`: la segunda iteración (retrato en dos pasos, tabla EPG y reproductor vertical completo).

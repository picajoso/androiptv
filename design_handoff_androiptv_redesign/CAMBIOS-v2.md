# Cambios v2: retrato en dos pasos, guía EPG en tabla y reproductor vertical completo

Este documento **amplía y corrige** `README.md`. Donde se contradigan, manda este. Abre `IPTV Prototipo.dc.html` en un navegador (con `support.js` y `_ds/` al lado) para ver el comportamiento exacto. Valores en dp/sp; los tokens de color son los del README.

Afecta **solo al modo retrato**. La vista horizontal con paneles y la pantalla completa **no cambian**.

---

## A. Canales en retrato: dos pasos (Listas → Canales)

Se elimina el carrusel horizontal de grupos bajo el título "Canales". Lo sustituye una navegación en dos pasos.

### Estado nuevo
- `portraitStep: GROUPS | CHANNELS` (por defecto `GROUPS` al abrir la app; si "Reanudar último canal" está activo, se puede abrir directamente en `CHANNELS` con el grupo del último canal).
- `group` sigue siendo el mismo estado que usan los paneles horizontales. Elegir lista en retrato cambia también el grupo del panel horizontal y al revés.

### Estructura de la pestaña Canales (de arriba abajo)
1. Previsualización del canal actual (igual que ahora, sin cambios).
2. Cabecera: padding `10 16`, regla inferior de 2 dp, `flex` alineado abajo con 8 dp de separación.
   - **Solo en `CHANNELS`**: botón atrás de 44×44 dp (`arrow-left`, 22 dp, `contentDescription` "Volver a las listas"), margen izquierdo de −12 dp para que el icono quede alineado con el borde de 16 dp.
   - Bloque de texto (flexible, una línea con elipsis en cada fila):
     - Kicker (10 sp, mayúsculas, tracking +0.1, accent-700):
       - `GROUPS`: `Mi lista · URL M3U · 1240` (fuente · tipo · total)
       - `GROUPS` con búsqueda escrita: `Resultados en todas las listas`
       - `CHANNELS`: `4 canales` (número de canales visibles)
     - Título (22 sp / 800, tracking −0.02):
       - `GROUPS`: **Listas**
       - `CHANNELS`: nombre de la lista (`ES - Fútbol`), o **Todos los canales** si es "Todos".
   - Botón de búsqueda (sin cambios).
3. Campo de búsqueda (si está activo, sin cambios).
4. Cuerpo desplazable:
   - `GROUPS` sin texto de búsqueda → **lista de listas** (ver abajo).
   - `GROUPS` con texto de búsqueda → canales de **todas** las listas que coinciden (filas de canal normales).
   - `CHANNELS` → canales de la lista elegida, filtrados por la búsqueda si la hay (filas de canal normales, como hasta ahora).

### Fila de lista (componente compartido con la Guía)
- Alto 62 dp, ancho completo, regla inferior de 1 dp, padding `0 12 0 16`.
- Columnas: `22 dp | flexible | auto | 18 dp`, separación 12 dp, centradas en vertical.
  1. Icono 18 dp: `list` (Todos), `heart` en **accent** (Favoritos), `history` (Recientes), `folder` (cada grupo).
  2. Texto: nombre 15 sp / 800 (una línea, elipsis) y debajo una línea de 11.5 sp:
     - Si la lista contiene el canal en emisión (excepto Todos y Recientes): `En emisión · M+ Deportes` en accent-700, y **fondo de la fila accent-100**.
     - Si no: los tres primeros nombres limpios separados por coma (`M+ Deportes, Eurosport 1, M+ Deportes 2`) en neutral-700; si está vacía: `Vacía`.
  3. Número de canales, 12 sp, neutral-700.
  4. `chevron-right` 18 dp, neutral-700.
- Pulsado: fondo neutral-200.
- Orden: **Todos los canales, Favoritos, Recientes**, después un encabezado y los grupos de la lista M3U en su orden de aparición.
- Encabezado antes del primer grupo: texto `GRUPOS` (10 sp, mayúsculas, tracking +0.1, accent-700), padding `16 16 6`, regla inferior de 1 dp.
- Para listas largas (cientos de grupos) usa `RecyclerView`/`ListView` con dos tipos de vista (encabezado y fila).

### Navegación
- Tocar una lista → `group = lista`, `portraitStep = CHANNELS`, lista de canales desde arriba.
- Botón atrás de la cabecera → `portraitStep = GROUPS`; limpia y cierra la búsqueda.
- **Botón/gesto atrás del sistema**: en `CHANNELS` vuelve a `GROUPS`; en `GROUPS` se comporta como siempre. Usa `OnBackPressedDispatcher`.
- **Volver a tocar la pestaña Canales** estando ya en ella (y en la lista, no en el reproductor) → vuelve a `GROUPS`.
- Abrir el reproductor desde una fila y volver atrás → regresa a `CHANNELS` de la misma lista, con la posición de desplazamiento conservada.
- La rotación conserva `portraitStep`, `group`, la búsqueda y la posición de la lista (ya pedido en la especificación).

---

## B. Guía EPG en retrato: dos pasos (Listas → Tabla EPG)

Sustituye por completo la pestaña Guía anterior (selector de un canal + lista de programas).

### Estado nuevo
- `guideStep: GROUPS | GRID` (por defecto `GROUPS`).
- `guideDay: Int` de −1 a 3 (Ayer, Hoy, Mañana, +2, +3). Por defecto 0.
- `guideSelection: (channelId, startMillis, day)?`: programa abierto en la hoja de detalle.
- La lista elegida usa el mismo `group` compartido.

### Paso 1: GROUPS
- Cabecera: kicker `Elige una lista`, título **Guía** (24 sp / 800). Sin botón atrás ni botón Ahora.
- Cuerpo: la **misma lista de listas** del apartado A (mismo componente). Tocar → `group = lista`, `guideStep = GRID`.

### Paso 2: GRID
1. **Cabecera**: botón atrás de 44 dp (vuelve a `GROUPS`; también con el atrás del sistema y al volver a tocar la pestaña Guía). Kicker `14 canales con guía`; título = nombre de la lista o **Todos los canales** (24 sp / 800, elipsis). A la derecha, botón **Ahora** (44 dp de alto, padding 14 dp, borde de 1 dp en divider, icono `clock` 16 dp + "Ahora" 13 sp / 600): pasa a Hoy y desplaza la tabla a la hora actual con animación.
2. **Selector de días**: fila horizontal desplazable, regla inferior de 2 dp. Celdas de ancho mínimo 84 dp y 48 dp de alto, padding 14 dp, separador derecho de 1 dp. Dos líneas alineadas a la izquierda: nombre (13 sp, 800 activo / 600 inactivo) y fecha corta (10.5 sp, opacidad 75 %). Activa: fondo accent, texto bg.
   - Etiquetas: `Ayer`, `Hoy`, `Mañana`, y después el día de la semana (`Jueves`, `Viernes`), con fechas `lun 13`, `mar 14`… **generadas con el idioma del dispositivo**.
   - Mostrar solo los días para los que hay datos XMLTV.
   - Al cambiar de día: si es Hoy, desplaza a la hora actual; si no, a las 19:30.
3. **Tabla EPG** (ocupa el resto, se desplaza en los dos ejes):
   - **Escala: 3 dp por minuto** (180 dp por hora). Franja visible: de 06:00 a 24:00 del día elegido (3.240 dp de ancho). Los programas que empiezan antes o terminan después se recortan a la franja.
   - **Cabecera de horas** (fija arriba al desplazar en vertical): 30 dp de alto, fondo bg, regla inferior de 2 dp. Marca cada 30 min: línea vertical izquierda de 1 dp en divider y la hora `10:30` (10.5 sp, neutral-700, 6 dp de padding).
   - **Esquina** (fija en los dos ejes): 92 dp de ancho, texto `CANAL` (10 sp, mayúsculas, tracking +0.1, neutral-700), regla derecha de 2 dp.
   - **Columna de canales** (fija a la izquierda al desplazar en horizontal): 92 dp de ancho, filas de **64 dp**, regla derecha de 2 dp, regla inferior de 1 dp, padding `0 8 0 12`. Contenido apilado a la izquierda: logo de 24 dp de alto (ancho mínimo 34 dp, fondo blanco, borde de 1 dp; o las iniciales en 10.5 sp / 800) y nombre limpio (11.5 sp / 800, una línea con elipsis). Fondo bg, o accent-100 si es el canal en emisión. Tocar → reproduce el canal y abre el Reproductor vertical (pestaña Canales, `portraitStep = CHANNELS`).
   - **Bloques de programa** (posición absoluta en su fila): `left = (inicio − 06:00) × 3 dp`, `width = duración × 3 dp`. Altura completa de la fila, padding `7 8`, regla derecha de 1 dp, sin esquinas redondeadas ni huecos entre bloques.
     - Línea 1 (solo si el bloque mide ≥ 70 dp): `10:15–12:15` (10.5 sp) + etiqueta DIRECTO (8.5 sp, tracking +0.1, padding `1 4`, fondo accent, texto blanco) si lo es **y no ha terminado** + `bell-ring` de 11 dp en accent si tiene recordatorio.
     - Línea 2: título 12.5 sp, interlineado 1.25, **máximo 2 líneas**, recortado sin elipsis forzada. Peso 600; 800 si está en emisión o seleccionado.
     - Colores:
       - **Pasado**: texto neutral-600.
       - **En emisión**: fondo accent-100 + barra de 2 dp en accent en el borde inferior con el porcentaje transcurrido.
       - **Futuro**: fondo transparente, texto normal.
       - **Seleccionado** (con la hoja abierta): fondo text, texto bg, línea de hora neutral-300.
     - Pulsado: contorno interior de 2 dp en accent.
   - **Línea de "ahora"** (solo en Hoy): línea vertical de 2 dp en accent a lo alto de toda la tabla, por encima de los bloques y por debajo de la columna de canales y de la cabecera de horas. En la cabecera de horas, una etiqueta `10:31` (10 sp / 600, padding `1 5`, fondo accent, texto bg) en la posición de la línea. Se mueve cada minuto (ya está pedido el refresco por minuto).
   - **Desplazamiento inicial**: al entrar en GRID, desplaza en horizontal de modo que la hora actual quede **40 minutos** a la derecha del borde de la columna de canales (`scrollX = (ahora − 06:00 − 40 min) × 3 dp`). Sin animación al entrar; con animación al pulsar Ahora o cambiar de día.
   - **Sin canales** en la lista: icono `calendar-x` 26 dp en accent, título "Sin canales en este grupo" (17 sp / 800), texto "Elige otro grupo para ver su programación." (13 sp, neutral-700), padding `40 24`.
   - Canal sin datos de guía: su fila se muestra con un único bloque gris "Sin información de guía" que ocupa toda la franja.

### Implementación recomendada de la tabla (vistas Android)
- **No uses un RecyclerView por fila.** Usa un **`View` personalizado** (`EpgGridView`) que dibuja en `onDraw` con `Canvas` solo lo visible: fondos, reglas, texto con `StaticLayout` (máx. 2 líneas) y la línea de ahora. Gestiona el desplazamiento en dos ejes con `OverScroller` + `GestureDetector` (arrastre y fling), y los toques con `onSingleTapUp` → índice de fila y búsqueda binaria del programa por minuto.
- La cabecera de horas, la esquina y la columna de canales se dibujan en el mismo `onDraw` **después** de los bloques, con su desplazamiento fijado: horas solo en X, canales solo en Y, esquina fija. Así se consigue el efecto de cabecera y columna fijas sin vistas anidadas.
- Logos de la columna: el mismo cargador de logos (OkHttp + SafeNetwork + caché acotada) que en la lista; invalida solo la fila afectada al llegar el bitmap.
- Precalcula por canal y día la lista ordenada de programas recortada a la franja (en segundo plano, al cargar la guía o cambiar de día). Nunca recorras todo el XMLTV en `onDraw`.
- Accesibilidad: expón cada bloque visible como nodo virtual con `ExploreByTouchHelper` ("M+ LaLiga TV 2, 10:15 a 12:15, LaLiga Hypermotion · Racing – Sporting, en directo").

### Hoja de detalle de programa
Se abre al tocar un bloque. Hoja inferior a ancho completo: fondo bg, regla superior de 2 dp en text, velo neutral-900 al 50 %, elevación de 12 dp. Tocar el velo la cierra.
1. Cabecera (padding `10 4 10 16`, regla inferior de 2 dp). Columnas `44 dp | flexible | 44 dp`:
   - logo de 44 dp;
   - nombre del canal con país y calidad (`M+ LaLiga TV 2 · ES · HD`, 15 sp / 800) y debajo `HOY · 10:15–12:15` (10 sp, mayúsculas, tracking +0.1, accent-700);
   - botón `x`.
2. Cuerpo (padding `16 16 18`, separación de 10 dp):
   - Etiquetas: `AHORA` (fondo text, texto bg) si está en emisión; `DIRECTO` (fondo accent) si es en directo y no ha terminado; duración (`2 h`, `1 h 30 min`, `45 min`; fondo neutral-200).
   - Título 21 sp / 800, interlineado 1.15.
   - Si está en emisión: barra de progreso de 3 dp (pista neutral-300, relleno accent) + `quedan 1 h 44 min` (11 sp, neutral-700).
   - Descripción XMLTV (`<desc>`) en 13.5 sp, interlineado 1.5, neutral-800. Si no hay, se omite.
3. Pie de dos celdas de 56 dp, regla superior de 2 dp:
   - Izquierda (transparente, separador derecho de 1 dp): en un programa futuro, `bell` + **Recordar** (o `bell-off` + **Quitar aviso** si ya tiene recordatorio); en uno pasado o en emisión, `x` + **Cerrar**.
   - Derecha (fondo accent, texto bg): **Ver ahora** si está en emisión o **Ver canal** en otro caso, más un icono `play`. Reproduce el canal y abre el Reproductor vertical.
- Mientras la hoja está abierta, el bloque correspondiente se pinta como "seleccionado".
- **Capas**: la hoja, los diálogos y los avisos deben quedar **por encima** de la cabecera y la columna fijas de la tabla.

---

## C. Reproductor vertical: completar lo que hay bajo el vídeo

Ahora mismo, bajo el vídeo solo están el nombre, el programa y una fila de iconos, y el resto de la pantalla queda vacío. Debe quedar como en la sección 2 del README, con estos detalles:

1. **Vídeo** 16:9 a ancho completo, **fijo** (no se desplaza). Sobre el vídeo:
   - Arriba a la izquierda: botón **atrás** de 44 dp con fondo negro al 35 % (vuelve a la lista de canales de la misma lista).
   - Arriba a la derecha: botón **pantalla completa** de 44 dp con fondo negro al 35 %.
   - Abajo, sobre un degradado: DIRECTO + barra de progreso de 2 dp + hora de fin.
   - **Tocar el vídeo = play/pausa**. En pausa se muestra un cuadrado accent de 56 dp con `play` en el centro.
   - **Se elimina la fila de iconos actual** (atrás, anterior, pausa, siguiente, DIRECTO, pantalla completa) bajo el título.
2. **Todo lo demás se desplaza en vertical** en un `NestedScrollView`/`RecyclerView` bajo el vídeo:
   1. **Información** (padding `14 4 12 16`, regla inferior de 2 dp). Columnas `flexible | 44 dp`:
      - nombre (22 sp / 800) + etiqueta de calidad;
      - programa actual (13 sp);
      - `10:15–12:15 · quedan 1 h 44 min · ES - Fútbol` (11 sp, neutral-700);
      - a la derecha, el **corazón** de 44 dp con borde de 1 dp en divider (accent si es favorito).
   2. **Acciones**: 4 celdas iguales de 64 dp de alto, separadores verticales de 1 dp, regla inferior de 2 dp. Icono de 18 dp arriba y texto de 11.5 sp / 600 abajo, alineados a la izquierda: **Anterior**, **Siguiente**, **Enviar**, **Abrir fuera** (con el diálogo de credenciales).
   3. **Guía · hoy**: título 16 sp / 800 y a la derecha la fecha (`mar 14 oct`, 11 sp, neutral-700, con el idioma del dispositivo); padding `14 16 6`.
   4. **Tabla de programas del canal** (regla superior de 1 dp). Empieza **dos programas antes del actual** y llega hasta el final del día. Columnas `60 dp | flexible | 44 dp`, reglas de 1 dp y separador vertical entre la hora y el título:
      - hora 12.5 sp / 600;
      - título 13.5 sp, interlineado 1.3, con la etiqueta DIRECTO encima si procede;
      - campana de 44 dp solo en los futuros (`bell` neutral-600, o `bell-ring` accent si hay recordatorio; tocar = aviso "Te avisaremos a las 12:15" / "Recordatorio quitado").
      - Pasados: texto neutral-600. **Actual: fondo text, texto bg.** Futuros: texto normal.
      - **La etiqueta DIRECTO no se muestra nunca en programas ya terminados**, ni aquí ni en la tabla EPG.
3. La tabla del reproductor y la tabla EPG de la pestaña Guía **usan los mismos datos** (mismo cálculo de programas por canal y día). Un recordatorio puesto en un sitio aparece en el otro (clave `canal|inicio`).

---

## D. Criterios de aceptación

1. Retrato → Canales abre en **Listas**. Tocar "ES - Fútbol" muestra solo sus canales con el título "ES - Fútbol" y la flecha atrás. La flecha atrás, el atrás del sistema o volver a tocar la pestaña Canales regresan a Listas.
2. En Listas, escribir "dazn" muestra canales de todas las listas; dentro de una lista, solo de esa.
3. La lista que contiene el canal en emisión aparece con fondo accent-100 y "En emisión · …".
4. Guía abre en **Listas**. Al elegir una lista se ve la tabla con todos sus canales, la línea roja de ahora visible y 40 min de margen a su izquierda. La columna de canales y la cabecera de horas quedan fijas al desplazar.
5. Cambiar de día funciona. Ayer no muestra programas "en emisión" y en días distintos de Hoy no hay línea de ahora.
6. Al tocar un bloque se abre la hoja de detalle por encima de todo. "Recordar" muestra la campana en el bloque y en la tabla del reproductor.
7. El reproductor vertical muestra información, acciones y la guía del día bajo el vídeo, y ya no queda espacio vacío. El vídeo no se recarga al abrir, cerrar ni rotar (regla de la especificación).
8. Con 300 canales en una lista y 18 h de guía, la tabla se desplaza sin tirones (60 fps en un dispositivo de gama media) y la memoria se mantiene estable.
9. La vista horizontal con paneles y la pantalla completa son idénticas a las de antes.

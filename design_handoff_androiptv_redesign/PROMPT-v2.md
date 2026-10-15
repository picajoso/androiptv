Pega esto en tu asistente de código. Adjunta (o copia en el repositorio) la carpeta `design_handoff_androiptv_redesign/` actualizada.

---

Hay una actualización del diseño de AndroIPTV. En `design_handoff_androiptv_redesign/` encontrarás:
- `CAMBIOS-v2.md`: **la especificación de esta iteración** (léela entera; amplía y corrige el README).
- `IPTV Prototipo.dc.html`: el prototipo actualizado. Ábrelo en un navegador para ver el comportamiento (es una referencia de diseño, no código para copiar).
- `README.md`: la especificación base con los tokens (colores, tipografía, medidas), que sigue vigente.

Cambios, **solo en modo retrato** (la vista horizontal con paneles y la pantalla completa no se tocan):
- **A.** Canales en dos pasos: primero la lista de listas (Todos, Favoritos, Recientes, después los grupos M3U), luego los canales de la lista elegida. Se elimina el carrusel de grupos.
- **B.** Guía EPG en dos pasos: primero la lista de listas, luego una tabla EPG con todos los canales de esa lista (canales en filas, horas en columnas, 3 dp por minuto, línea de ahora, cabecera y columna fijas), con selector de días y hoja de detalle de programa. Sustituye a la guía actual de un solo canal.
- **C.** Reproductor vertical: bajo el vídeo fijo van el bloque de información con favorito, la rejilla de 4 acciones y la tabla "Guía · hoy" del canal, todo desplazable. Se elimina la fila de iconos bajo el título; el play/pausa pasa a tocar el vídeo. La etiqueta DIRECTO nunca aparece en programas ya terminados.

Instrucciones:
1. Lee `CAMBIOS-v2.md` y compáralo con lo que ya hay implementado. Antes de tocar código, dime en pocas líneas qué archivos vas a crear o modificar y cualquier duda o conflicto con la especificación aprobada. Espera mi confirmación.
2. Impleméntalo en este orden, con un commit por bloque y un resumen breve tras cada uno: **C** (reproductor), **A** (Canales en dos pasos), **B** (tabla EPG y hoja de detalle).
3. Para la tabla EPG usa un único `View` personalizado que dibuje con `Canvas`, como se describe en el apartado B. Nada de un `RecyclerView` por fila ni de `HorizontalScrollView` anidados.
4. Comparte la lista de listas entre Canales y Guía (un único componente) y los datos de programación entre la tabla EPG y la tabla del reproductor (un único cálculo).
5. Gestiona el atrás del sistema con `OnBackPressedDispatcher` según la sección de navegación.
6. Mantén las reglas ya acordadas: un solo `PlayerView` sin recargar el vídeo, `stableId()` intacto, nombres limpios solo al mostrar, sin dependencias nuevas, textos alineados a la izquierda, esquinas a 0 dp, objetivos táctiles de 44 dp o más y descripciones de accesibilidad en español.
7. Al terminar, comprueba uno a uno los criterios de aceptación de la sección D y dime cuáles has verificado en el emulador.

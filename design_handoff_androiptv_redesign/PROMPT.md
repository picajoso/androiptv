Pega esto en tu asistente de código junto con la carpeta `design_handoff_androiptv_redesign/` y el repositorio `picajoso/androiptv`.

---

Quiero rediseñar la interfaz de mi app Android **AndroIPTV** (repositorio actual: Kotlin, vistas creadas por código en `MainActivity.kt`, Media3 `PlayerView`, sin XML de layouts ni Compose).

Te adjunto `design_handoff_androiptv_redesign/`:
- `README.md`: la especificación completa (pantallas, medidas en dp, colores, tipografía, iconos, comportamiento y estado).
- `IPTV Prototipo.dc.html`: el prototipo interactivo de referencia. Ábrelo en un navegador para ver el comportamiento. **Es una referencia de diseño, no código para copiar.**

Tarea:
1. Lee `README.md` entero y el código actual (`MainActivity.kt`, `Models.kt`, `SecureStore.kt`, `PlaybackController.kt`, `PlaylistParsers.kt`, `DemoContent.kt`).
2. Implementa el rediseño **por fases** (ver la sección "Plan de implementación" del README). Haz la Fase 1 completa y compilable antes de pasar a la siguiente; después de cada fase, dime qué has cambiado y espera mi confirmación.
3. Mantén toda la funcionalidad existente (fuentes M3U URL/archivo/Xtream/demo, HTTP y servidor local, guía XMLTV, favoritos, recientes, canal anterior, reanudar último canal, abrir fuera con aviso de credenciales, rellenar/sin recortar, controles de pantalla completa).
4. Sigue con vistas de Android creadas por código, como ahora. Divide `MainActivity.kt` en archivos pequeños (`ui/Theme.kt`, `ui/ChannelRow.kt`, `ui/PortraitScreens.kt`, `ui/LandscapePanels.kt`, `ui/FullscreenOverlay.kt`, `ChannelName.kt`…). No migres a Compose salvo que te lo pida.
5. Esquinas siempre a 0 dp, sin sombras salvo las indicadas, todos los textos alineados a la izquierda (también dentro de los botones), objetivos táctiles de 44 dp como mínimo.
6. No inventes colores ni tamaños: usa los tokens del README.
7. No añadas dependencias nuevas sin preguntarme. Las únicas previstas son el tipo de letra Archivo y los iconos Lucide como vector drawables.

# AndroIPTV

Proyecto Android independiente de MacIPTV para móvil y tableta (Android 8.0 o posterior). Android TV queda fuera de esta versión.

## Abrir y ejecutar

Abre el directorio del proyecto en Android Studio, espera a que termine la sincronización de Gradle y ejecuta `app` en un móvil o emulador. El APK de depuración se genera con `./gradlew assembleDebug` en `app/build/outputs/apk/debug/app-debug.apk`.

## Funciones

- Importación de M3U desde URL o archivo y proveedores Xtream Codes.
- Reproducción integrada con Media3, lista de canales, grupos y búsqueda.
- Favoritos, recientes, canal anterior y reanudación del último canal al abrir la app.
- Guía XMLTV, actualización de listas, demo y opción de abrir el canal en otra app con aviso sobre credenciales.
- En retrato, Canales y Guía comienzan en Listas. La Guía muestra una tabla EPG de desplazamiento bidireccional y una ficha inferior por programa.
- El reproductor vertical muestra la programación del día bajo el vídeo. «Recordar» programa una notificación al inicio del programa; la marca se comparte con la Guía y persiste al reiniciar el teléfono.
- «Enviar» abre el selector de dispositivos Cast. Media3 cambia entre el reproductor local y el remoto; el receptor predeterminado descarga el flujo directamente del proveedor.
- Paneles de grupos y canales plegables; vista adaptable a móvil y tableta.
- En apaisado, paneles de grupos y canales junto al reproductor; pestañas de Guía y Ajustes.
- Controles propios en vertical, apaisado y pantalla completa, con brillo, volumen, bloqueo, temporizador, modo de imagen y panel lateral de canales.
- Varias fuentes, logos con fallback a iniciales, información de programación y temporizador de apagado.
- Fuente cifrada mediante Android Keystore; HTTP y servidores locales requieren autorización en la configuración de la fuente.

La guía XMLTV es opcional para M3U. En Xtream se obtiene automáticamente del proveedor. La demo utiliza un flujo HLS público de Apple y programación ficticia.

## Estado de esta versión

Las Fases 1, 2 y 3 y la iteración v2 de retrato están implementadas. La pantalla completa usa el mismo reproductor que las otras vistas; los controles se ocultan a los cuatro segundos durante la reproducción y el brillo de la ventana vuelve a su valor previo al salir. El selector Cast se abrió en el emulador API 37 y encontró dispositivos; la transferencia y reproducción remota aún requieren una comprobación en un televisor real. Los avisos exactos requieren permiso de alarmas; sin él se programa un aviso aproximado. En Android 13 o posterior también hay que autorizar las notificaciones. Algunos proveedores no permiten Cast porque su servidor exige cabeceras especiales o no es accesible desde el televisor. La especificación y el prototipo están en `design_handoff_androiptv_redesign/`; consulta `CLAUDE.md` para arquitectura y decisiones.

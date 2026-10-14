# AndroIPTV

Proyecto Android independiente de MacIPTV para móvil y tableta (Android 8.0 o posterior). Android TV queda fuera de esta versión.

## Abrir y ejecutar

Abre el directorio del proyecto en Android Studio, espera a que termine la sincronización de Gradle y ejecuta `app` en un móvil o emulador. El APK de depuración se genera con `./gradlew assembleDebug` en `app/build/outputs/apk/debug/app-debug.apk`.

## Funciones

- Importación de M3U desde URL o archivo y proveedores Xtream Codes.
- Reproducción integrada con Media3, lista de canales, grupos y búsqueda.
- Favoritos, recientes, canal anterior y reanudación del último canal al abrir la app.
- Guía XMLTV, actualización de listas, demo y opción de abrir el canal en otra app con aviso sobre credenciales.
- Paneles de grupos y canales plegables; vista adaptable a móvil y tableta.
- En apaisado, paneles de grupos y canales junto al reproductor; pestañas de Guía y Ajustes.
- Controles propios en vertical, apaisado y pantalla completa, con brillo, volumen, bloqueo, temporizador, modo de imagen y panel lateral de canales.
- Varias fuentes, logos con fallback a iniciales, información de programación y temporizador de apagado.
- Fuente cifrada mediante Android Keystore; HTTP y servidores locales requieren autorización en la configuración de la fuente.

La guía XMLTV es opcional para M3U. En Xtream se obtiene automáticamente del proveedor. La demo utiliza un flujo HLS público de Apple y programación ficticia.

## Estado de esta versión

Las Fases 1 y 2 del rediseño están implementadas. La pantalla completa usa el mismo reproductor que las otras vistas; los controles se ocultan a los cuatro segundos durante la reproducción y el brillo de la ventana vuelve a su valor previo al salir. Se compiló y se revisó en el emulador API 37. Queda pendiente una revisión manual completa de Guía, Ajustes y migración de fuentes, además de probar en un dispositivo físico. La Fase 3 (recordatorios y Cast) sigue pendiente. Consulta `CLAUDE.md` para arquitectura, decisiones y siguientes pasos.

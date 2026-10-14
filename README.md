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
- Reproductor apaisado a pantalla completa con controles superpuestos y opción para rellenar la pantalla o conservar la imagen completa.
- Fuente cifrada mediante Android Keystore; HTTP y servidores locales requieren autorización en la configuración de la fuente.

La guía XMLTV es opcional para M3U. En Xtream se obtiene automáticamente del proveedor. La demo utiliza un flujo HLS público de Apple y programación ficticia.

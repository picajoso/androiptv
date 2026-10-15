# Fase 3 — avisos y Cast

La especificación de rediseño aprobada define esta fase. La guía conserva la clave de marca `fuenteId|canalId|inicioEpochMillis` y la interfaz horizontal.

1. **Avisos**. Crear un planificador de `AlarmManager` y un receptor para entregar notificaciones en el inicio del programa. Solicitar permiso de notificación al marcar por primera vez. Usar alarma exacta si está autorizada; si no, una alarma aproximada sin bloquear la marca. Reprogramar al reiniciar el teléfono y tras actualizar la app. Cancelar al quitar marca o fuente, y depurar marcas terminadas. Abrir la app y el canal desde la notificación. Commit independiente.
2. **Cast**. Añadir Media3 Cast 1.11.1 y el receptor predeterminado. Sustituir las acciones «Enviar» por un selector de dispositivos real, con reproducción local/remota sincronizada y controles sobre el reproductor activo. Mantener la validación de URL de la fuente. Documentar que el receptor descarga directamente y puede rechazar fuentes que exijan cabeceras especiales. Commit independiente.
3. **Integración**. Actualizar README y CLAUDE.md, compilar APK debug y comprobar inicio, selección de canal y navegación en emulador. Probar alarmas con un programa futuro si hay guía y permisos disponibles. La prueba real de Cast requiere dispositivo compatible en la misma red.

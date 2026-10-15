# Casa Familias 4.3.1 — corrección del arranque

Versión **4.3.1**, código **5**, Android **8.0 o posterior**, paquete `com.casafamilias.app`. APK firmado para depuración, destinado a presentar y probar.

## Qué cambia

- Pantalla de carga visible desde el HTML, incluso si falla JavaScript. Los fallos de arranque muestran Reintentar y los WebView antiguos indican cómo actualizarlos.
- Los archivos de la interfaz se cargan con la versión del APK y sin reutilizar la caché anterior. No se borra la sesión ni el almacenamiento local.
- Recuperación nativa si no se abre la página principal o Android cierra el proceso de la pantalla.

- Interfaz azul con tarjetas, botones visibles y acciones escritas: Ver comprobante, Ver turno y tareas, Consultar saldo, Cerrar y Activar repetición semanal.
- Navegación inferior con cuatro botones, encabezados sin flechas y formularios con botón Cerrar. Los filtros de meses usan un selector y botones Mes anterior / Mes siguiente.
- Textos que se ajustan al espacio, nombres largos y montos grandes que no se enciman, y controles que crecen con la letra ampliada.
- Calendario de limpieza en orden de fechas. El administrador puede revisar el orden de todas las familias y repetirlo cada semana con un botón.
- Activación y pausa de la repetición, vista previa de las semanas y conservación de turnos ya asignados. Supabase Cron completa el calendario cada lunes.
- Se mantienen el QR, los nombres de familias y la reasignación de usuarios de la versión anterior.

## Instalar y probar

1. Instala `CasaFamilias-4.3.1-pruebas.apk` sobre la versión anterior, sin desinstalarla ni borrar sus datos. Esta entrega usa el mismo certificado que los APK 4.2 y 4.3. Si tu APK fue compilado en otra computadora, actualízalo desde el Android Studio que lo firmó.
2. Revisa Inicio, Pagos, Más y Mi cuenta. Los botones de los registros abren el detalle y los formularios se cierran con **Cerrar**. El botón Atrás del teléfono sigue disponible.
3. Como administrador, abre **Limpieza → Repetir el orden por semana**.
4. Revisa las posiciones, el lunes inicial y la vista previa; pulsa **Guardar orden y continuar** para guardar ajustes, o **Activar repetición semanal** si estaba pausado. Cada familia de limpieza debe aparecer una vez; las cuentas de una misma familia comparten posición y turno.
5. Para detener la generación posterior, pulsa **Pausar nuevos turnos automáticos**. Los turnos ya creados se conservan.
6. Prueba tu QR y los comprobantes desde el teléfono, con letra normal y ampliada.

Las migraciones del QR, familias y limpieza ya están instaladas en Supabase. El plan semanal ya quedó activo con el orden confirmado y con las dos cuentas de una misma familia agrupadas solo para limpieza. El botón permite revisar o pausar el plan. Consulta [el funcionamiento semanal](supabase/LIMPIEZA_SEMANAL.md) y [la configuración del QR y familias](supabase/ACTIVAR.md).

## Actualizar la carpeta de tu computadora

En GitHub Desktop: **Fetch origin → Pull origin**. Abre `CasaFamiliasAndroid/` en Android Studio y pulsa Run. El ZIP original del repositorio es histórico; el nuevo `CasaFamilias-4.3.1-proyecto.zip` contiene el código actualizado y las migraciones.

Para compilar: JDK 17, Android SDK Platform 35 y Gradle Wrapper 8.9. Desde `CasaFamiliasAndroid`, ejecuta `gradlew.bat assembleDebug` en Windows o `./gradlew assembleDebug` en macOS/Linux. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Comprobaciones

- Suite de interfaz: 20 comprobaciones de arranque (instalación nueva, sesión guardada, scripts ausentes o inválidos, WebView antiguo, carga bloqueada y recuperación sin borrar almacenamiento), 11 comprobaciones de autenticación, Restaurante, 36 de administración, 42 de repetición semanal y 26 vistas en los tres roles principales.
- Base de datos aislada (verificada en 4.3; sin cambios SQL en esta corrección): 59 comprobaciones previas más 41 de repetición semanal y 26 de cuentas compartidas. Las migraciones de lógica se aplican dos veces; los archivos de fixture son solo locales.
- Chromium: 270 combinaciones de rol, pantalla, tamaño y letra, a 320/360/390/430/768 píxeles; nombres largos, montos grandes y comprobación de que el texto permanece dentro de los botones.
- Formularios, letra ampliada y controles de cierre; sin errores de JavaScript en las revisiones visuales.
- Supabase real (verificado en 4.3; sin cambios del servidor en esta corrección): instalación de funciones y Cron, permisos por rol, tarea semanal única y conservación de datos existentes.
- Chromium: seis escenarios de arranque con el SDK real incluido, archivos ausentes o inválidos y recuperación mediante Reintentar conservando el almacenamiento.
- Compilación Android y firma del APK. No se ha probado este APK en un teléfono físico ni se ha publicado en Google Play.

Pruebas de interfaz: con Node.js 24, desde `CasaFamiliasAndroid/validacion`: `npm install --package-lock=false` y `npm test`. Pruebas SQL: con Python 3 y Docker, desde esa misma carpeta: `python test_backend.py`. El validador usa un contenedor desechable sin puertos publicados; pg_cron se valida por separado en Supabase.

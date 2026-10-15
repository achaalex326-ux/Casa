# Casa Familias 4.3 — interfaz y limpieza semanal

Versión **4.3**, código **4**, Android **8.0 o posterior**, paquete `com.casafamilias.app`. APK firmado para depuración, destinado a presentar y probar.

## Qué cambia

- Interfaz azul con tarjetas, botones visibles y acciones escritas: Ver comprobante, Ver turno y tareas, Consultar saldo, Cerrar y Activar repetición semanal.
- Navegación inferior con cuatro botones, encabezados sin flechas y formularios con botón Cerrar. Los filtros de meses usan un selector y botones Mes anterior / Mes siguiente.
- Textos que se ajustan al espacio, nombres largos y montos grandes que no se enciman, y controles que crecen con la letra ampliada.
- Calendario de limpieza en orden de fechas. El administrador puede revisar el orden de todas las familias y repetirlo cada semana con un botón.
- Activación y pausa de la repetición, vista previa de las semanas y conservación de turnos ya asignados. Supabase Cron completa el calendario cada lunes.
- Se mantienen el QR, los nombres de familias y la reasignación de usuarios de la versión anterior.

## Instalar y probar

1. Instala `CasaFamilias-4.3-pruebas.apk` e ingresa con tu cuenta. Si Android informa una firma distinta, compila desde el Android Studio con el que instalaste la versión anterior.
2. Revisa Inicio, Pagos, Más y Mi cuenta. Los botones de los registros abren el detalle y los formularios se cierran con **Cerrar**. El botón Atrás del teléfono sigue disponible.
3. Como administrador, abre **Limpieza → Repetir el orden por semana**.
4. Revisa las posiciones, el lunes inicial y la vista previa; pulsa **Guardar orden y continuar** para guardar ajustes, o **Activar repetición semanal** si estaba pausado. Cada familia de limpieza debe aparecer una vez; las cuentas de una misma familia comparten posición y turno.
5. Para detener la generación posterior, pulsa **Pausar nuevos turnos automáticos**. Los turnos ya creados se conservan.
6. Prueba tu QR y los comprobantes desde el teléfono, con letra normal y ampliada.

Las migraciones del QR, familias y limpieza ya están instaladas en Supabase. El plan semanal ya quedó activo con el orden confirmado y con las dos cuentas de una misma familia agrupadas solo para limpieza. El botón permite revisar o pausar el plan. Consulta [el funcionamiento semanal](supabase/LIMPIEZA_SEMANAL.md) y [la configuración del QR y familias](supabase/ACTIVAR.md).

## Actualizar la carpeta de tu computadora

En GitHub Desktop: **Fetch origin → Pull origin**. Abre `CasaFamiliasAndroid/` en Android Studio y pulsa Run. El ZIP original del repositorio es histórico; el nuevo `CasaFamilias-4.3-proyecto.zip` contiene el código actualizado y las migraciones.

Para compilar: JDK 17, Android SDK Platform 35 y Gradle Wrapper 8.9. Desde `CasaFamiliasAndroid`, ejecuta `gradlew.bat assembleDebug` en Windows o `./gradlew assembleDebug` en macOS/Linux. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Comprobaciones

- Suite de interfaz: primer inicio, 11 comprobaciones de autenticación, Restaurante, 36 de administración, 42 de repetición semanal y 26 vistas en los tres roles principales.
- Base de datos aislada: 59 comprobaciones previas más 41 de repetición semanal y 26 de cuentas compartidas. Las migraciones de lógica se aplican dos veces; los archivos de fixture son solo locales.
- Chromium: 270 combinaciones de rol, pantalla, tamaño y letra, a 320/360/390/430/768 píxeles; nombres largos, montos grandes y comprobación de que el texto permanece dentro de los botones.
- Formularios, letra ampliada y controles de cierre; sin errores de JavaScript en las revisiones visuales.
- Supabase real: instalación de funciones y Cron, permisos por rol, tarea semanal única y conservación de datos existentes.
- Compilación Android y firma del APK. No se ha probado este APK en un teléfono físico ni se ha publicado en Google Play.

Pruebas de interfaz: con Node.js 24, desde `CasaFamiliasAndroid/validacion`: `npm install --package-lock=false` y `npm test`. Pruebas SQL: con Python 3 y Docker, desde esa misma carpeta: `python test_backend.py`. El validador usa un contenedor desechable sin puertos publicados; pg_cron se valida por separado en Supabase.

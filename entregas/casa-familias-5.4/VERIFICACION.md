# Verificación de Casa Familias 5.4

Versión5.4/código10, paquete com.casafamilias.app, mínimo Android8/API26 y destino Android16/API36. APK `presentation` optimizado con R8, no depurable, con la firma de pruebas compatible con5.3. La preparación de GooglePlay sigue pendiente y no se incluye un AAB nuevo en esta entrega.

## Comprobaciones

- 54 pruebas unitarias: 24 reglas existentes, 13 contratos de repositorio/sesión, 7 bloqueo local y 10 reglas de recordatorios. Sin fallos, errores ni omisiones.
- 18 pruebas Android aprobadas: 8 navegación, 6 bloqueo biométrico y 4 notificaciones del sistema con datos ficticios. Sin fallos en la ejecución final.
- Android Lint: 0 errores y24avisos; no se desactivaron comprobaciones.
- Compilaciones debug, AndroidTest y presentación mediante Gradle8.11.1/JDK17.
- Reglas verificadas: hogar y semana correctos, alias compartido sin duplicados, ausencia de recordatorios de tareas completadas o deudas cero, importe actualizado tras un abono, recordatorio diario, filtrado de publicaciones históricas/leídas y límite de ocho avisos por lote.
- Contratos HTTP: renovación compartida del refresh token, reintento de401, cuenta anterior incapaz de borrar la sesión nueva y consultas de notificaciones filtradas por usuario. El lector en segundo plano no invoca funciones para generar deudas, modificar pagos ni enviar correos.

Los resultados de Android, la firma, la alineación y la instalación del APK optimizado se guardan en `evidencias/` y `verificacion.json`. Las pruebas de notificaciones usan un trabajador con fuente de datos ficticia inyectada, una sesión ficticia local y el NotificationManager real de Android. No hay una opción de Intent ni una URL de configuración para omitir la autenticación en el APK.

El conjunto comprueba publicación real de una notificación sin actividad reanudada, deduplicación, importe privado con versión pública genérica, retirada de una deuda saldada, cuenta incorrecta/sesión cerrada sin consultas ni avisos, y una sola tarea periódica por teléfono. La retirada se espera con un límite de5segundos porque Android aplica las cancelaciones de forma asíncrona. Se mantienen las pruebas de navegación, texto ampliado, modo oscuro y bloqueo biométrico.

## Límites

El dispositivo de prueba es un emulador Android11/API30. No se ha probado un teléfono físico, Android13/16 ni las restricciones de batería de cada fabricante. El permiso de Android13 se implementa con el contrato nativo POST_NOTIFICATIONS y requiere prueba en el celular del usuario. La huella se conserva con sus comprobaciones de callback; el lector físico debe probarse en cada teléfono.

Las pruebas del trabajador ejecutan su sincronización sin esperar los15minutos de un ciclo real y usan una fuente local ficticia. Comprueban reglas, estado y notificación real del sistema; no demuestran el tiempo de llegada desde Supabase a todos los modelos o durante Doze. WorkManager tiene intervalo mínimo15minutos y puede retrasarse; **no se implementó FCM ni push instantáneo**. Forzar detención bloquea los trabajos hasta reabrir la app.

No se ejecutaron migraciones ni se crearon deudas, pagos, turnos o avisos reales para estas pruebas. El ajuste previo a cero permanece independiente de esta versión. Comprueba un aviso legítimo y el botón de prueba en cada teléfono sin registrar deudas ficticias en producción.

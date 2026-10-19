# Casa Familias 5.4 — notificaciones del celular

APK para Android8 o posterior, versión5.4/código10, optimizado y con el certificado de pruebas compatible con5.3. Conserva las cuentas, los datos y el acceso con huella. No necesita publicarse en GooglePlay para instalarlo. Requiere Internet y una cuenta autorizada de la casa.

## Instalar y activar en cada teléfono

1. Descarga y abre `CasaFamilias-5.4-pruebas.apk`. Elige **Actualizar** si tienes una entrega compatible. Si Android pide permiso para instalar desde el navegador o gestor de archivos, concédelo para abrir este APK.
2. Inicia sesión. Entra en **Mi perfil → Activar notificaciones**.
3. Permite las notificaciones cuando Android lo solicite. Si el sistema las bloquea, usa **Ajustes de notificaciones**, permítelas y vuelve a la app para activarlas.
4. Pulsa **Probar notificación** y despliega la barra del celular. Esa prueba es local; confirma que Android puede mostrar el aviso, no la conexión con Supabase.
5. Mantén la sesión iniciada y el teléfono con Internet. Puedes salir normalmente de la app.

La opción se activa por cuenta y teléfono. Una cuenta diferente debe activarla de nuevo. Cerrar sesión, quitar el permiso o pulsar **Desactivar notificaciones** detiene su entrega local. No desinstales ni borres datos como primera solución ante un error de certificado: un APK generado en otro equipo puede tener otra firma.

## Qué avisos llegan

- **Limpieza:** un recordatorio del turno pendiente de la semana actual. Salinas/Salinas2 comparten el mismo hogar de limpieza. El administrador puede ver el turno de la casa. Se cancela el recordatorio si deja de corresponder o se completa.
- **Deudas nuevas:** concepto e importe pendiente de la cuenta. El saldo cero no genera recordatorios.
- **Vencimientos:** recordatorio diario desde tres días antes del vencimiento, mientras exista saldo. Cada mensaje refleja el saldo al enviarse; los siguientes recordatorios usan el importe actualizado. Consulta la app para ver cambios posteriores al mensaje. Una deuda saldada o condonada retira su aviso anterior en la siguiente revisión.
- **Servicios publicados y avisos nuevos:** publicaciones no leídas de la cuenta posteriores a la activación. No se reenvían todos los avisos históricos de la campana.

Se evitan repeticiones en cada consulta y se limita cada lote a ocho avisos. Los mensajes aparecen en la barra de notificaciones de Android, con canales para Limpieza, Deudas y servicios, y Avisos. Tocarlos abre la aplicación respetando la sesión y el bloqueo biométrico. Se configura un texto público genérico para la pantalla bloqueada; Android y sus ajustes determinan qué se muestra.

## Tiempo de llegada y límites

Usa **WorkManager con revisión periódica, de intervalo mínimo15minutos**. No es push instantáneo de Firebase. Android puede retrasar la revisión por ahorro de batería, falta de Internet o restricciones del fabricante; no se garantiza un plazo máximo. Una revisión inmediata se solicita al activar la opción y al sincronizar dentro de la app, también sujeta al sistema.

Cerrar la actividad o quitarla de recientes permite que WorkManager siga trabajando según Android. **Forzar detención** desde Ajustes impide los trabajos hasta que abras otra vez la app. Reiniciar el teléfono conserva el trabajo programado; el sistema decide cuándo ejecutarlo después de desbloquear. Si tu fabricante restringe la actividad en segundo plano, revisa sus ajustes de batería para Casa Familias. No se solicita una exclusión automática del ahorro de batería.

Activar notificaciones autoriza leer los datos mínimos de avisos en segundo plano incluso si la interfaz está bloqueada con huella. La interfaz sigue protegida y el servidor mantiene los permisos de la cuenta. No se registraron deudas ni pagos reales para probar esta función.

## Comprobar en tu celular

Después de la prueba local, prueba con un aviso nuevo de la casa y un turno real de limpieza. Evita crear deudas ficticias en el sistema real. Comprueba el permiso en Android13 o posterior y la entrega al salir de la app; anota cualquier demora o restricción de batería. Las pruebas automáticas usan un emulador y datos ficticios, no prueban el comportamiento de todos los teléfonos.

Consulta [VERIFICACION.md](VERIFICACION.md), `verificacion.json` y `SHA256SUMS.txt`. La publicación en GooglePlay conserva los pendientes descritos en su guía.

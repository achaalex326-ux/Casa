# Casa Familias 5.5

Instala `CasaFamilias-5.5-pruebas.apk` sobre la versión anterior. Paquete com.casafamilias.app, código11, Android8 o posterior. Se conserva el certificado de pruebas anterior; no hace falta desinstalar ni borrar la sesión. Si Android avisa que el origen es desconocido, autoriza la instalación para el navegador o administrador de archivos que estés usando.

## Calendario de limpieza

En **Inicio**, el ingeniero/administrador y el encargado de pagos ven **¿Quién limpia esta semana?**, la familia asignada, las fechas de lunes a domingo y el estado. **Calendario de todas las familias** abre Limpieza: elige el mes, consulta todas las familias o filtra tus familias. Las semanas que cruzan dos meses aparecen en ambos. La tarjeta de esta semana siempre corresponde a la semana actual, aunque consultes otro mes.

El calendario conserva las fechas y el orden existentes: Choque → Galarza → Salinas → David → Acha. Salinas/Salinas2 comparten únicamente el turno de limpieza. Las cuentas familiares conservan la vista de sus propios turnos y el selector de posibles intercambios. Consultar el turno de otra familia no concede al encargado permiso para completarlo.

## Intercambios entre usuarios

1. Abre un turno pendiente de una de tus familias y pulsa **Solicitar intercambio**.
2. Selecciona el turno de otra familia y envía la solicitud.
3. Una **persona distinta de la familia receptora** entra en **Limpieza → Solicitudes de intercambio → Recibidos** y pulsa **Aceptar intercambio** o **Rechazar intercambio**.
4. Al aceptar, Supabase cambia las asignaciones de los dos turnos de forma atómica. Al rechazar o mientras esté pendiente, conserva las asignaciones.

Solo puedes solicitar un cambio para un hogar al que tu cuenta esté asignada. La familia Salinas y su segunda cuenta representan el mismo hogar. Tener rol de administrador o encargado no permite aceptar, rechazar ni iniciar intercambios de otras familias. La cuenta del ingeniero no tiene familia asignada y consulta los acuerdos. Un encargado sí puede participar por sus familias, con la misma aceptación de la otra persona.

La actualización aplica estas comprobaciones en Supabase, además de ocultar los botones sin permiso. La migración008 cambia funciones de intercambios y añade un verificador de pertenencia; no cambia filas de turnos, solicitudes, rotación, familias, cuentas, deudas ni pagos.

## Funciones conservadas

Huella opcional y notificaciones del celular en **Mi perfil**. Los avisos de5.4 usan WorkManager con intervalo mínimo de15 minutos, sujetos a internet, batería y restricciones del sistema; no son push instantáneo. La consulta del calendario no amplía las familias de los recordatorios personales del encargado. La app sigue para la casa actual y sus cuentas autorizadas.

Este APK es para instalar y probar; no está publicado en Google Play y usa el certificado de pruebas. La versión definitiva requiere la clave del propietario, completar privacidad/eliminación de cuentas y la revisión de Google. No se ha probado esta versión en un celular físico ni el permiso de notificaciones de Android13.

Actualiza tu carpeta local con **GitHub Desktop → Fetch origin → Pull origin**. Abre CasaFamiliasAndroid en Android Studio; el ZIP de la raíz es histórico. El ZIP de esta entrega contiene el proyecto actual, sin cachés ni claves privadas.

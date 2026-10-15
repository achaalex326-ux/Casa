# Repetición semanal de limpieza — versión 4.3

Las migraciones `202610150003_cleaning_rotation.sql` y `202610150004_cleaning_cron.sql`, junto con `202610150005_shared_cleaning_family.sql`, ya están instaladas en el proyecto appcasa. El plan ya está activo con el orden confirmado por el usuario. El administrador puede revisarlo o pausarlo desde el APK 4.3.

## Configurar desde la aplicación

1. Ingresa como administrador y abre **Limpieza** desde Inicio o Más.
2. Pulsa **Repetir el orden por semana**.
3. Revisa las posiciones de las familias de limpieza. Las cuentas que pertenecen a una misma familia comparten una sola posición; el aviso del formulario indica la agrupación. Cuando no existe un plan guardado, la app propone el orden del tramo semanal más reciente. Puedes elegir una familia diferente en cada posición; deben aparecer todas una sola vez.
4. Revisa **Primer lunes de este orden**. Esa fecha fija la semana de la primera familia y permite continuar correctamente el ciclo.
5. Revisa las tareas para familias que no tienen una lista anterior. Para las demás se conserva su lista más reciente.
6. Si ajustaste el plan activo, pulsa **Guardar orden y continuar**. Si está pausado, pulsa **Activar repetición semanal**. La vista previa indica las semanas ya registradas y cómo continúa el calendario.

Al completar la última posición, el ciclo vuelve a la primera. El servidor cubre la semana actual y las 11 siguientes, sin generar fechas anteriores al lunes inicial. Cada lunes a las **00:05 de Bolivia** vuelve a completar ese horizonte mediante la tarea `casa-cleaning-weekly` de Supabase Cron.

## Datos existentes y cambios posteriores

- Cada semana que ya tenga un turno se conserva completa: familia, estado, tareas y comprobante. El generador no la reemplaza ni añade un segundo turno a esa semana.
- Los turnos históricos siguen registrados.
- **Guardar orden y continuar** cambia el patrón de las nuevas semanas; las semanas ya asignadas se conservan.
- **Pausar nuevos turnos automáticos** detiene la generación. Los turnos que ya aparecen en el calendario permanecen.
- Las cuentas de una misma familia ven y completan un único turno compartido y pueden abrir la foto de ese turno. La agrupación solo afecta a limpieza; no fusiona perfiles, familias contables ni pagos.
- Los intercambios se gestionan desde las cuentas de administración y encargado.
- Solo el administrador puede configurar el plan. El generador no se puede ejecutar directamente desde cuentas normales y deja de generar si el administrador que lo configuró pierde ese rol.

La pantalla muestra el plan al administrador; las familias siguen viendo sus turnos según los permisos existentes del sistema.

## Validación

- 41 comprobaciones del ciclo en PostgreSQL aislado: orden, repetición, fechas de lunes, tareas, semanas manuales, ejecución repetida, pausa, permisos y conservación de datos.
- 42 comprobaciones de interfaz: orden inicial, posiciones repetidas, vista previa, guardado, reintento, actualización en segundo plano, activación y pausa.
- 26 comprobaciones adicionales de cuentas compartidas, fotos, permisos y conservación de pagos.
- En Supabase real se verificaron las funciones, los rechazos por rol y la tarea de Cron activa con la expresión `5 4 * * 1` en GMT. Reaplicar la migración conserva un único trabajo.

La prueba local no instala pg_cron; su instalación y configuración se comprobaron por separado en el servidor real. La tarea consulta el plan ya activado. Se añadieron las semanas futuras que faltaban; los turnos existentes y todos los datos financieros se conservaron.

## Instalar en otro proyecto compatible

Con las migraciones 001 y 002 ya aplicadas, ejecutar primero `202610150003_cleaning_rotation.sql` y después `202610150004_cleaning_cron.sql`. La migración 004 necesita pg_cron, disponible en Supabase. Después ejecutar 005 para admitir cuentas que comparten limpieza. Las agrupaciones son configuración del proyecto en `cleaning_family_aliases`; cada cuenta secundaria apunta al identificador representante de su familia de limpieza. Nunca ejecutar los archivos `backend_fixture.sql`, `backend_checks.sql`, `rotation_checks.sql` o `shared_cleaning_checks.sql` en el servidor real.

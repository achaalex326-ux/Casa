# Activar QR y administración de familias (versión 4.2)

Este paso debe hacerlo una persona con acceso de administración al proyecto de Supabase. El APK por sí solo no puede crear almacenamiento ni funciones del servidor.

1. Abre [SQL Editor del proyecto de Casa Familias](https://supabase.com/dashboard/project/rffkvxpybtojfxjhprgr/sql/new) e inicia sesión con la cuenta que administra ese proyecto.
2. Abre el archivo [202610150001_qr_familias.sql](migrations/202610150001_qr_familias.sql), copia **todo su contenido** y pégalo en una consulta nueva. No uses los archivos `backend_fixture.sql` o `backend_checks.sql`: son únicamente para pruebas locales.
3. Pulsa **Run**. La consulta debe terminar correctamente. Está dentro de una transacción y se puede ejecutar de nuevo sin borrar datos.
4. En **Storage**, comprueba que existe **payment-qr** y que es **privado**. Conserva privado el almacenamiento de comprobantes.
5. Instala el APK 4.2 e ingresa como administrador. Abre **Mi cuenta → Configurar QR**, elige un PNG, JPG o WebP de hasta 5 MB y pulsa **Guardar QR**. Desde una cuenta de familia, revisa **Pagos → Registrar pago** para ver la imagen.
6. En **Mi cuenta → Nombres de familias**, selecciona una familia, escribe su nombre y guarda.
7. En **Más → Usuarios**, toca una cuenta de familia o encargado, elige su familia y guarda. También puedes hacerlo desde **Mi cuenta → Cambiar familia de un usuario**. Una cuenta de familia tiene una familia; un encargado tiene dos diferentes. Restaurante y Administrador no necesitan asignaciones.
8. En la cuenta del usuario afectado, pulsa **Actualizar datos**. La aplicación también consulta los cambios cada 15 segundos mientras está visible y al volver a ella.

## Qué cambia en el servidor

- Crea el bucket privado `payment-qr` con imágenes de hasta 5 MB.
- Las cuentas autenticadas pueden leer el QR. Solo los perfiles con rol `sysadmin` pueden cargarlo, reemplazarlo o eliminarlo mediante estas políticas.
- Añade dos funciones con comprobación de administrador para cambiar nombres y asignaciones. No concede actualización directa sobre tablas a los usuarios.
- Conserva los identificadores de familias, las cuentas, sus roles y sus credenciales. Los pagos y las deudas permanecen con su familia original. Las nuevas asignaciones del encargado se usan para próximos registros de servicios.
- Conserva las funciones existentes, incluida `save_payment_qr_settings`, y las políticas de otros buckets. No modifica el bucket `receipts`.

La migración necesita las tablas existentes `public.families` y `public.profiles`, incluidas sus columnas `family_id` y `second_family_id`. Si SQL Editor muestra un error, conserva el mensaje completo para revisarlo antes de continuar. No se debe cambiar una cuenta a `sysadmin` para superar errores.

## Estado de esta entrega

El error «Bucket not found» fue reproducido mediante una consulta de lectura al servidor. La configuración y sus permisos pasaron 50 comprobaciones en PostgreSQL aislado, incluida su ejecución repetida. **Esta migración todavía no se ejecutó en el Supabase real**, porque el entorno de desarrollo no tiene sus credenciales de administración. El guardado real del QR y de las nuevas opciones queda pendiente de este paso y de probarlo con tus cuentas.

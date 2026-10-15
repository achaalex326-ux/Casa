# QR y administración de familias (versión 4.2)

## Estado del proyecto appcasa

**Las dos migraciones ya están aplicadas en el Supabase real de Casa Familias** (`rffkvxpybtojfxjhprgr`). No hace falta pegarlas otra vez para usar esta entrega.

Se verificaron el bucket privado `payment-qr`, sus cuatro políticas y los permisos de las funciones. Las cuentas Familia y Encargado no pueden ejecutar los cambios de administrador; también se rechazan sesiones ausentes y cuentas sin perfil.

Se probó con las tablas reales el guardado de nombres, la reasignación de una cuenta y el guardado de la configuración del QR. Esa prueba se deshizo mediante una transacción: no quedaron nombres, asignaciones ni un QR ficticio. Se comprobaron los datos antes y después; las familias, usuarios, pagos, gastos, cuotas, créditos, turnos y metadatos de comprobantes existentes se conservan.

## Usarlo desde la aplicación

1. Instala o abre el **APK 4.2** e ingresa como administrador. Si ya lo instalaste, vuelve a abrir la aplicación y actualiza los datos.
2. En **Mi cuenta → Configurar QR**, selecciona un PNG, JPG o WebP de hasta 5 MB y pulsa **Guardar QR**. Todavía debes elegir tu imagen bancaria: no se inventó ni cargó un QR de pago.
3. Desde una cuenta de familia, revisa **Pagos → Registrar pago** para comprobar que se ve tu imagen.
4. En **Mi cuenta → Nombres de familias**, selecciona una familia, escribe su nombre y guarda.
5. En **Más → Usuarios**, toca una cuenta de familia o encargado, elige sus familias y guarda. También puedes hacerlo desde **Mi cuenta → Cambiar familia de un usuario**. Una cuenta de familia tiene una familia; un encargado tiene dos diferentes.
6. En la cuenta afectada, pulsa **Actualizar datos**. La aplicación consulta los cambios cada 15 segundos mientras está visible y al volver a ella.

Los pagos y las deudas permanecen en su familia original. Las nuevas asignaciones del encargado se usan para próximos registros de servicios. Las cuentas conservan su rol y sus credenciales.

La comprobación final de seleccionar y subir tu imagen desde el teléfono queda para esta prueba. Las pruebas del servidor no sustituyen esa comprobación del APK en el dispositivo.

## Reinstalar la configuración en otro proyecto compatible

Solo para una instalación nueva o para revisar la configuración con el ingeniero:

1. Comprueba que el proyecto tenga las tablas existentes `public.families`, `public.profiles`, `public.payment_qr_settings` y la función `public.audit`, con el esquema de Casa Familias.
2. En SQL Editor, ejecuta [202610150001_qr_familias.sql](migrations/202610150001_qr_familias.sql).
3. Después ejecuta [202610150002_qr_admin_guard.sql](migrations/202610150002_qr_admin_guard.sql). Esta segunda migración conserva el guardado y la auditoría del QR y exige una cuenta de administrador con perfil válido.
4. Comprueba que `payment-qr` sea privado. Las cuentas autenticadas pueden leer el QR y solo el administrador puede modificarlo mediante estas políticas. El bucket `receipts` y sus políticas se conservan.

Las migraciones se pueden ejecutar de nuevo sin borrar datos. Los archivos `backend_fixture.sql` y `backend_checks.sql` son exclusivamente de pruebas locales: nunca deben ejecutarse en el servidor real.

La configuración pasó además **59 comprobaciones en PostgreSQL aislado**, incluida la ejecución repetida de ambas migraciones.

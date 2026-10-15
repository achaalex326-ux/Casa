# Casa Familias 4.2 — entrega para presentar y probar

Versión **4.2**, código de versión **3**, Android **8.0 o posterior**. Paquete `com.casafamilias.app`. APK firmado para depuración.

## Cambios de esta versión

- Configuración del almacenamiento privado del QR, con permisos para que solo el administrador lo modifique.
- Vista previa del QR, formatos PNG/JPG/WebP de hasta 5 MB, mensajes de error persistentes y protección contra guardados repetidos. Los datos del formulario se conservan cuando falla la conexión.
- El administrador puede renombrar familias desde **Mi cuenta → Nombres de familias**.
- Puede cambiar la familia de cada usuario desde **Más → Usuarios → tocar una cuenta**, o desde **Mi cuenta → Cambiar familia de un usuario**. El encargado admite dos familias diferentes.
- La cuenta afectada actualiza su asignación al refrescar los datos; también se consulta periódicamente mientras está abierta. Los pagos y deudas anteriores permanecen en su familia original.
- Se mantienen la interfaz, recuperación de contraseña y consulta de Restaurante de la versión 4.1.

## Supabase ya activado

**La configuración ya se aplicó al proyecto appcasa.** El bucket privado del QR y las funciones de administración están activos. Se verificaron los permisos y se probó el guardado en las tablas reales dentro de una transacción que se deshizo para conservar tus datos. Consulta [la configuración y los pasos de uso](supabase/ACTIVAR.md).

El paquete de entrega incluye `Activar-QR-y-familias.sql` y `ACTIVAR-SUPABASE.md`, que contienen las dos migraciones y los pasos de uso. Los archivos de pruebas locales nunca deben ejecutarse en el servidor.

## Instalar y comprobar

1. Abre `CasaFamilias-4.2-pruebas.apk` en el teléfono e instala la actualización. Android puede pedir permiso para instalar desde la aplicación con la que abriste el archivo.
2. Ingresa con una cuenta existente de administrador. No se crearon usuarios ni contraseñas en el servidor.
3. Carga tu imagen del QR y comprueba su imagen desde una cuenta de familia.
4. Cambia el nombre de una familia y revisa que aparezca en los selectores y en la lista de usuarios.
5. Reasigna una cuenta, actualiza los datos desde ella y comprueba que ve su nueva familia. Revisa que los pagos anteriores sigan en su familia original.
6. Comprueba también la selección de comprobantes, descargas de PDF/Excel y el botón Atrás en el teléfono.

Si Android rechaza la actualización por una firma diferente, compila el proyecto desde el Android Studio con el que instalaste la versión anterior. No es necesario borrar tus carpetas.

## Actualizar los archivos de tu computadora

En GitHub Desktop: **Fetch origin → Pull origin**. Abre `CasaFamiliasAndroid/` en Android Studio y pulsa **Run** con el teléfono conectado. Los cambios se descargan a la carpeta del repositorio clonado.

`CasaFamiliasAndroid.zip` se conserva como archivo histórico. El nuevo `CasaFamilias-4.2-proyecto.zip` contiene el proyecto actual y la configuración SQL, sin cachés ni claves de firma.

Para compilar se requiere JDK 17, Android SDK Platform 35 y Gradle Wrapper 8.9. Desde `CasaFamiliasAndroid`, ejecuta `gradlew.bat assembleDebug` en Windows o `./gradlew assembleDebug` en macOS/Linux. El resultado está en `app/build/outputs/apk/debug/app-debug.apk`.

## Validación

- Primer inicio, 11 comprobaciones de autenticación, consulta de Restaurante y 26 vistas de los tres perfiles principales.
- 36 comprobaciones nuevas de administración e imágenes con backend simulado: persistencia, errores, reintento, asignaciones, sesiones abiertas y nombres tratados como texto.
- 59 comprobaciones en PostgreSQL aislado: permisos por rol, bucket privado, lectura del QR, rechazo de escrituras no autorizadas, ejecución repetida de la migración y conservación de pagos.
- Chromium sin errores de JavaScript: 50 aperturas/restauraciones de formularios en cuatro roles a 320 y 390 píxeles, texto ampliado, teclado y revisión general a cinco anchos.
- Compilación Android, integridad del contenido del APK y verificación de su firma de depuración.

Estas comprobaciones no sustituyen la prueba del APK en el teléfono y con tus cuentas reales. La activación SQL y el guardado en las tablas reales ya fueron verificados; queda seleccionar tu imagen del QR y comprobar su carga y lectura desde el teléfono. La recuperación de contraseña necesita que `casafamilias://auth` esté permitido en Supabase.

Para ejecutar las pruebas de interfaz, desde `CasaFamiliasAndroid/validacion` con Node.js 24: `npm install --package-lock=false` y `npm test`. Para las pruebas SQL, con Python 3 y Docker disponibles: `python test_backend.py`; este comando crea un PostgreSQL aislado sin puertos publicados y lo elimina al terminar.

# Borrador de política de privacidad — Casa Familias

**No publicar este texto hasta completar la identidad, el contacto, los plazos y el procedimiento real de eliminación. No sustituye la revisión de las prácticas del servicio y sus proveedores.**

## Responsable y contacto

Nombre del responsable: **PENDIENTE**.
Correo de soporte y privacidad: **PENDIENTE**.
Fecha de vigencia de la política definitiva: **PENDIENTE**.

## Datos utilizados

La aplicación utiliza información de la cuenta —nombre, usuario, correo, rol y familia asignada— para autenticar al usuario y determinar las funciones y los datos a los que puede acceder. Registra gastos, cuotas, pagos, aportes, saldos y comprobantes; también turnos, fotos de limpieza, propuestas, votos, avisos y registros de auditoría de las operaciones.

Las fotografías se seleccionan mediante los controles del dispositivo para adjuntar facturas o comprobantes. El proyecto incluye lectura de texto mediante ML Kit. Revisar su implementación y las divulgaciones del SDK para describir por separado el procesamiento local y cualquier dato técnico transmitido por el proveedor.

Desde la versión 5.3, el usuario puede activar un bloqueo biométrico local. Android verifica la biometría ya registrada en el dispositivo; Casa Familias no recibe, almacena ni envía imágenes o plantillas de huellas. La preferencia de activación se conserva localmente para esa cuenta y ese teléfono. Cualquier biometría compatible registrada en Android puede autorizar el acceso. Se mantiene el ingreso con correo y contraseña y sigue siendo necesaria una sesión válida de Supabase.

## Uso y acceso

Los datos permiten organizar las obligaciones de la casa, revisar aportes, coordinar limpieza y mantener el historial. Las cuentas de familia consultan la información permitida para su familia. El encargado y el administrador tienen funciones adicionales según su rol. No incluir contraseñas, claves de administración ni datos reales en las capturas de la tienda.

## Almacenamiento y proveedores

Se utiliza Supabase para autenticación, base de datos y almacenamiento de imágenes. Los archivos privados se consultan según los permisos del servicio. La sesión nativa se guarda cifrada con una clave de Android Keystore. El proyecto usa servicios y bibliotecas de Google/ML Kit; completar el análisis de sus prácticas y divulgaciones antes de declarar las categorías de datos en Play Console.

Región de alojamiento, otros destinatarios y transferencias aplicables: **PENDIENTE de confirmar con la configuración y los contratos vigentes**.

## Conservación y eliminación

Se debe indicar cuánto tiempo se conserva cada categoría de datos y por qué. Si hay registros que deban conservarse por obligaciones contables, explicar qué datos se retienen, durante cuánto tiempo y cómo se limitan o anonimizan los demás.

Canal dentro de la aplicación para solicitar la eliminación: **PENDIENTE de implementar**.
Página web pública para solicitar la eliminación: **PENDIENTE de implementar y publicar**.
Procedimiento de verificación de identidad y plazo de respuesta: **PENDIENTE**.

Desactivar una cuenta, cerrar sesión o borrar solo los datos del teléfono no elimina los datos guardados en el servidor. No afirmar que existe un borrado automático mientras no se haya implementado y comprobado.

## Cambios y solicitudes

Completar el mecanismo real de comunicación de cambios y el contacto para ejercer los derechos que correspondan. La versión final debe publicarse en una URL pública accesible y enlazarse desde la aplicación y la ficha de Google Play.

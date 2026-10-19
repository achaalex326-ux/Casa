# Casa Familias 5.3 — instalar y activar la huella

Android 8 o posterior. APK optimizado de presentación, versión 5.3/código 9. No requiere Google Play para instalarse; necesita Internet para autenticar y sincronizar. Conserva las cuentas y los datos de la casa actual.

1. Descarga `CasaFamilias-5.3-pruebas.apk` en cada teléfono y abre el archivo.
2. Si Android lo solicita, permite instalar desde el navegador o gestor que abrió el APK. Elige **Actualizar** si ya tenías la entrega compatible. No desinstales ni borres datos como primer paso si aparece un error de firma: puede provenir de otro equipo o certificado.
3. Ingresa con el correo y la contraseña de la cuenta correspondiente.
4. Registra una huella en Ajustes → Seguridad de Android si todavía no hay ninguna.
5. En Casa Familias abre **Mi perfil → Activar huella** y confirma en el diálogo del sistema.

La activación se hace por cuenta y teléfono. No se habilita a distancia para todos los dispositivos. Si no hay lector compatible o no está disponible, sigue funcionando el acceso con contraseña. La comprobación utiliza biometría fuerte de Android; algunos equipos también pueden aceptar reconocimiento facial fuerte.

Al reiniciar el proceso, la app muestra el bloqueo antes de consultar datos. Al volver después de 30 segundos fuera de la app solicita otra comprobación. Una visita breve al selector de imágenes conserva el formulario. Cancelar, fallar o cerrar el diálogo no autoriza el acceso. **Usar correo y contraseña** exige verificar las credenciales; no reutiliza silenciosamente la sesión bloqueada. Si la sesión del servidor expiró o fue revocada, hay que iniciar sesión de nuevo.

En Mi perfil puedes **Desactivar huella**. Cerrar sesión también la desactiva localmente. Cambiar de cuenta requiere una nueva activación. Android verifica cualquiera de las biometrías compatibles registradas en ese teléfono; úsalo en dispositivos de confianza. La app no captura, almacena ni envía huellas.

Mientras la opción está activa se bloquean capturas y la vista de contenido en aplicaciones recientes mediante FLAG_SECURE. Este acceso protege la interfaz local y complementa la sesión AES-GCM/Android Keystore existente; la clave de la sesión no se ha convertido en una clave criptográfica que exija huella para cada operación.

Los otros celulares siguen necesitando cuentas autorizadas. El APK es para Android, no para iPhone. La distribución en Google Play y la creación pública de casas independientes continúan pendientes. Esta entrega usa certificado de pruebas; la firma definitiva de publicación pertenece al propietario.

Ver [la verificación](VERIFICACION.md) y `SHA256SUMS.txt` antes de distribuir.

# Casa Familias 4.1 — entrega para presentar y probar

Aplicación Android para revisar con el ingeniero. Versión **4.1**, código de versión **2**, Android **8.0 o posterior**. El paquete es `com.casafamilias.app`.

## Qué incluye

- Acceso con ilustración propia, fuente local, etiquetas visibles y control para mostrar la contraseña.
- Paneles de Familia, Encargado y Administrador con tarjetas, filtros, formularios y navegación por rol.
- Consulta de servicios para la cuenta Restaurante, sin controles para registrar gastos ni administrar usuarios.
- Recuperación de contraseña con confirmación, control de envíos repetidos y mensajes de reintento.
- Formularios que conservan sus datos cuando falla la conexión y navegación con texto ampliado y teclado.

## Probar el APK

1. Copia `CasaFamilias-4.1-pruebas.apk` a tu teléfono y ábrelo. Android puede pedirte autorización para instalar desde la aplicación con la que abriste el archivo.
2. Ingresa con una cuenta existente de Casa Familias. No se crearon usuarios ni contraseñas nuevos en el servidor.
3. Revisa el inicio, el menú y las pantallas de tu perfil. Para registrar movimientos, utiliza las cuentas y los datos de prueba acordados con el ingeniero.
4. Comprueba la selección de una imagen de comprobante, el guardado de PDF/Excel y el botón Atrás en el teléfono.

El APK está firmado para depuración. Si Android rechaza una actualización por una firma diferente, genera e instala el APK desde el Android Studio con el que instalaste tu versión anterior. No es necesario borrar tus carpetas del proyecto.

## Abrir el proyecto actualizado

En GitHub Desktop: **Fetch origin → Pull origin**. Abre **CasaFamiliasAndroid/** en Android Studio y pulsa **Run** con el teléfono conectado.

El ZIP original del repositorio se conserva como archivo histórico. La carpeta `CasaFamiliasAndroid` contiene la versión actual. El paquete de entrega `CasaFamilias-4.1-proyecto.zip` contiene el código actual sin cachés, claves de firma ni carpetas de compilación.

Requisitos para compilar: JDK 17, Android SDK Platform 35 y las dependencias descargadas por Gradle Wrapper 8.9. El APK queda en `CasaFamiliasAndroid/app/build/outputs/apk/debug/app-debug.apk`.

## Revisar el diseño sin cuenta

Dentro de `CasaFamiliasAndroid/app/src/main/assets/web/`, sirve los archivos con `python -m http.server 5500` y abre `demo.html` desde ese servidor en tu navegador. La demostración ofrece Familia, Encargado y Administrador con datos de ejemplo; no guarda cambios en Supabase.

## Comprobaciones realizadas

- Primer inicio y formulario de acceso.
- Once comprobaciones de autenticación con backend simulado: recuperación, enlace vencido, confirmación, envíos repetidos, perfil ausente y errores de conexión.
- Consulta de Restaurante: saldo parcial, servicio pagado, acceso al historial y navegación de solo consulta.
- Veintiséis vistas en los tres perfiles principales.
- Cuarenta y seis aperturas/restauraciones de formularios en cuatro perfiles a 320 y 390 píxeles.
- Revisión en Chromium a 320, 360, 390, 430 y 768 píxeles, texto ampliado, contraseña visible/oculta, diálogos y teclado.
- Compilación Android y firma del APK de depuración.

Las pruebas automatizadas usan datos simulados. La validación en el teléfono y con cuentas reales, el correo de recuperación y las funciones del servidor quedan para la prueba conjunta. La URL `casafamilias://auth` debe estar permitida en la configuración de recuperación de Supabase. No se hicieron escrituras ni despliegues en el servidor durante esta entrega.

## Ejecutar las pruebas de código

Con Node.js 24, desde `CasaFamiliasAndroid/validacion`:

```bash
npm install --package-lock=false
npm test
```

En el entorno de Codex, las dependencias ya están preparadas:

```bash
source /workspace/casa-environment/activate.sh
cd "$CASA_PROJECT/validacion"
npm test
```

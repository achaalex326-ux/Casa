# Casa Familias: preparación para Google Play

Revisado el **18 de octubre de 2026**. Esta entrega conserva el uso para tu casa. La posibilidad de que distintas casas se registren y tengan espacios independientes queda para una etapa posterior; no se ha abierto el registro de usuarios ni el acceso a los datos de tu familia.

## Qué quedó preparado

- Proyecto Android 5.2, código 8, con destino Android 16/API 36. Sigue siendo compatible con Android 8 o posterior.
- Compilación Android App Bundle (`.aab`), el formato que Google Play solicita para nuevas aplicaciones.
- `release` deja de utilizar automáticamente la firma de pruebas. La firma definitiva se configura con tu propia clave de subida, guardada fuera de Git.
- Un APK `presentation`, optimizado y firmado con el certificado de pruebas anterior, permite seguir probando y actualizar las entregas anteriores de este entorno. No es el archivo que se sube a Play.
- Las cuotas de servicios de las dos familias configuradas para el encargado quedan cubiertas porque pagan las facturas. El servidor conserva cuotas, pagos previos, créditos y deudas de las otras familias. Los gastos extraordinarios conservan sus propias reglas.

El `.aab` incluido como **SIN-FIRMAR** es una comprobación de compilación. Todavía **no se puede subir a Play Console**: genera uno firmado con tu clave siguiendo el paso 3. No se ha publicado la aplicación ni creado una cuenta de Google Play.

## Lo que falta antes de enviar a revisión

1. Tu cuenta de desarrollador de Google Play, con identidad y dispositivo verificados según lo que pida tu cuenta.
2. La clave de subida que crearás y respaldarás tú. No pegues contraseñas en el chat ni guardes claves en GitHub.
3. Una política de privacidad pública con el nombre del responsable y un contacto real; su enlace debe poder abrirse sin iniciar sesión y estar accesible también desde la aplicación. El borrador adjunto necesita esos datos y revisión antes de usarse.
4. Definir y ofrecer el procedimiento de eliminación de cuenta y datos que corresponda a esta aplicación. Como hay creación de cuentas desde la administración, revisar la política y preparar las vías dentro de la aplicación y en una página web. No existe todavía un flujo de eliminación implementado en esta entrega. Desactivar la cuenta no equivale a eliminarla; no borrar deudas o comprobantes contables automáticamente sin determinar qué conservación corresponde y explicarla.
5. Completar Seguridad de los datos y las declaraciones de contenido, incluidas las prácticas de los SDK. Se usan identificación de usuarios, datos financieros y fotos de comprobantes. No declarar “no recopila datos”.
6. Un correo de soporte, icono de tienda de 512 × 512, gráfico de funciones de 1024 × 500 y al menos dos capturas de teléfono. Usar cuentas ficticias y evitar mostrar datos de las familias reales.
7. Una cuenta de revisión de alcance limitado para que Google pueda entrar, con instrucciones y acceso a las funciones que deba revisar. Nunca dar una cuenta personal real.
8. Probar en teléfonos físicos, especialmente Android 16/API 36, la instalación, acceso, recuperación de contraseña, guardado, carga de QR, pagos, ampliación de texto y persistencia. La compilación y las comprobaciones de bibliotecas no sustituyen una prueba física.

## Paso 1. Crear tu cuenta de desarrollador

Entra en <https://play.google.com/console/signup> con la cuenta de Google que será propietaria de la aplicación. Elige el tipo de cuenta que te corresponda y completa los datos de identidad y contacto. Google informa una tarifa de registro única de **25 USD**; confirma el importe y los requisitos al registrarte. Ese pago se hace a Google desde tu cuenta.

## Paso 2. Crear la aplicación

En Play Console pulsa **Crear aplicación**. Nombre: **Casa Familias**. Idioma principal: español. Tipo: aplicación. Selecciona gratuita si esa es tu decisión comercial; esta entrega no incluye anuncios ni suscripciones contratadas por el desarrollador. Los pagos por QR corresponden a aportes y servicios de la casa, no a compras de funciones digitales.

El identificador técnico del proyecto es `com.casafamilias.app`. Comprueba en Play Console que está disponible antes de la primera publicación. Después de publicar no puede cambiarse para esa misma ficha.

Para la ficha de la tienda puedes usar el texto preparado en `FICHA_TIENDA.md`, después de revisar que coincida con tu servicio.

## Paso 3. Generar tu archivo AAB firmado en Android Studio

1. En GitHub Desktop pulsa **Fetch origin → Pull origin** y abre `CasaFamiliasAndroid` en Android Studio actualizado, con JDK 17 y Android SDK Platform 36. Espera a que termine la sincronización de Gradle.
2. Abre **Build → Generate Signed App Bundle or APK**.
3. Elige **Android App Bundle** y el módulo `app`.
4. Si no tienes una clave de subida propia, pulsa **Create new**. Guarda `casa-upload.jks` en una carpeta privada fuera del repositorio; utiliza contraseñas propias, alias `casa-upload` y una validez de al menos 25 años. Respalda el archivo y sus contraseñas en lugares seguros bajo tu control.
5. Selecciona esa clave, completa los datos en Android Studio y elige la variante **release**. No selecciones `debug` ni `presentation`.
6. Genera el bundle y utiliza el enlace **Locate** que muestra Android Studio. Ese `.aab` firmado es el que subirás.

Si después quieres compilar por terminal, copia `casa-upload.properties.example` como `casa-upload.properties`, rellénalo localmente y ejecuta desde la carpeta Android, en PowerShell:

```powershell
.\gradlew.bat bundleRelease
```

El resultado estará en `app\build\outputs\bundle\release\app-release.aab`. El archivo local de propiedades y los formatos de claves están excluidos de Git. No uses las contraseñas del ejemplo. Sin configuración de firma, Gradle genera un bundle sin firmar para comprobaciones técnicas.

La versión 5.2 tiene código 8. Cada subida posterior necesita un `versionCode` mayor que cualquier código ya subido a esa aplicación, incluso en canales de prueba.

## Paso 4. Completar el contenido y la privacidad

Sigue las tareas que muestra el panel de Play Console: acceso a la aplicación, anuncios, clasificación de contenido, público objetivo, política de privacidad, seguridad de los datos y declaraciones adicionales que aparezcan. Las respuestas deben describir el comportamiento real. Un borrador o una compilación correcta no garantizan la aprobación.

Si Google solicita datos de acceso, proporciona una cuenta de prueba vigente. Aclara que las cuentas las asigna el administrador y que esta versión corresponde a una comunidad concreta. Cualquiera podría descargar una publicación pública, pero solo una cuenta autorizada debe poder consultar información de esa comunidad.

## Paso 5. Empezar por una prueba interna

En **Pruebas → Prueba interna**, crea una versión, activa Play App Signing cuando se solicite y sube el AAB firmado. Revisa los avisos y el informe previo al lanzamiento. Añade a los usuarios que probarán con sus cuentas de Google y comparte el enlace del canal cuando esté disponible. Una prueba interna no hace que la aplicación sea pública para todos.

La clave de subida identifica tus envíos. Con Play App Signing, Google administra la firma con la que se distribuye la aplicación. El APK de presentación usa otro certificado: no se garantiza una actualización directa sobre él desde Play. Antes de cambiar de instalación, confirma que los datos están sincronizados y conserva lo necesario; no desinstales como primer paso de diagnóstico.

## Paso 6. Prueba cerrada y acceso a producción

Para cuentas personales creadas después del 13 de noviembre de 2023, Google exige una prueba cerrada con **al menos 12 testers que participen durante 14 días continuos**, antes de solicitar acceso a producción. La prueba interna no reemplaza ese requisito. Cumplir los números permite solicitar acceso; Google también evalúa la calidad y la información de las pruebas.

Revisa el requisito exacto que muestre tu cuenta: los flujos y las condiciones pueden cambiar.

## Paso 7. Publicación pública

Cuando tengas acceso a producción, los pendientes resueltos y la versión aprobada, crea el lanzamiento de producción, elige los países donde ofrecerás la aplicación y envíalo a revisión. Será descargable públicamente en las regiones y dispositivos admitidos cuando Google apruebe y active ese lanzamiento. La disponibilidad no es inmediata ni automática por subir el archivo.

## Fuentes oficiales

- [Cuenta de desarrollador y tarifa](https://support.google.com/googleplay/android-developer/answer/6112435?hl=es).
- [Nivel de API de destino: API 36 desde el 31 de agosto de 2026](https://support.google.com/googleplay/android-developer/answer/11926878?hl=es).
- [Requisitos de pruebas para cuentas personales nuevas](https://support.google.com/googleplay/android-developer/answer/14151465?hl=es).
- [Preparar y subir un Android App Bundle](https://developer.android.com/studio/publish/upload-bundle?hl=es-419).
- [Política de datos de usuario, privacidad y eliminación](https://support.google.com/googleplay/android-developer/answer/10144311?hl=es).
- [Bibliotecas nativas y páginas de memoria de 16 KB](https://developer.android.com/guide/practices/page-sizes).
- [Divulgación de datos de los SDK de ML Kit](https://developers.google.com/ml-kit/android-data-disclosure).

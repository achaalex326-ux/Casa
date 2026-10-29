# Casa Familias 5.9

## Instalar en el celular

1. Descarga **CasaFamilias-5.9-pruebas.apk** en el teléfono.
2. Abre el archivo y permite instalar desde ese navegador o gestor de archivos si Android lo pide.
3. Pulsa **Actualizar** si ya tienes una versión anterior. No hace falta desinstalarla.
4. Abre Casa Familias con internet e inicia sesión con tu correo habitual.

Necesita Android 8 o posterior. La firma coincide con los APK de pruebas entregados previamente desde este entorno. Si Android indica que las firmas son incompatibles, comprueba primero qué APK tienes instalado; una compilación hecha en otro equipo puede tener otra clave. No desinstales sin considerar tus ajustes y sesión locales.

## Usarla sin complicaciones

| Quiero… | Botón |
| --- | --- |
| Saber cuánto falta pagar y hasta qué mes hay registros | Inicio o Deudas |
| Pagar varias deudas con una sola foto | Inicio → Pagar → importe → foto → revisar |
| Saber si aprobaron mi comprobante | Mis pagos |
| Ver mi turno y las tareas | Limpieza |
| Registrar agua, luz, gas e internet | Registrar facturas, con cuenta autorizada |
| Revisar un comprobante | Revisar pagos, con cuenta autorizada |
| Ver deudas de otras familias | Ver deudas, con cuenta autorizada |
| Cambiar tamaño de letra, tema o activar huella/avisos | Cuenta o Más → Mi cuenta |

El saldo baja cuando se aprueba el comprobante. La revisión muestra los meses que cubre antes de enviarlo. Los detalles de cada mes se abren con **Ver detalle**. No se consideran pagados los meses que todavía no tienen registros.

La huella y las notificaciones deben activarse en cada teléfono. Los recordatorios nativos necesitan conexión y Android puede retrasarlos; no son push instantáneo. La cámara necesita una aplicación de cámara disponible en el teléfono. El sitio web adapta cámara y notificaciones a lo que permita el navegador.

## Actualizar tu misma página de Netlify

Tu sitio es **https://casafliagalarza.netlify.app/**. Como lo publicaste arrastrando archivos, haz lo siguiente:

1. Descarga **CasaFamilias-5.9-web-Netlify.zip** en la computadora.
2. Haz clic derecho → **Extraer todo**. Dentro deben aparecer `index.html`, `app.js`, `simple.css` y las demás carpetas. No edites ni pegues claves.
3. Entra en Netlify y abre el **sitio existente** cuya dirección es `casafliagalarza.netlify.app`.
4. Abre **Deploys**. En el área de despliegue manual, arrastra la carpeta descomprimida que contiene directamente `index.html`. También puedes elegir esa carpeta con la opción de buscar archivos. No crees otro sitio.
5. Espera a que el despliegue aparezca publicado. Si Netlify no publica automáticamente las cargas de producción, abre el despliegue y usa **Publish deploy**.
6. Abre tu dirección habitual y recarga con **Ctrl+F5**. En el celular cierra y vuelve a abrir la pestaña.

Comprueba con tu cuenta que aparecen los botones nuevos. No hace falta crear cuentas, volver a cargar deudas ni ejecutar SQL: la web utiliza el mismo Supabase. El ZIP no contiene APK ni pruebas; `index.html` está en su raíz.

**Estado de entrega:** archivos preparados y probados localmente. El sitio público no ha sido actualizado desde este entorno; esa carga manual es el paso pendiente. Conserva el despliegue anterior en Netlify para poder volver a publicarlo si lo necesitas.

## Proyecto para Android Studio

**CasaFamilias-5.9-proyecto.zip** contiene el código actual. También está en GitHub. Actualiza con GitHub Desktop: **Fetch origin → Pull origin**. Abre la carpeta `CasaFamiliasAndroid`, usa JDK 17 y SDK 36; el Wrapper fija Gradle 8.11.1.

Esta entrega conserva el cálculo corregido en 5.8 y no cambia los datos reales. Las capturas y pruebas de 5.9 usan cuentas ficticias. El APK se puede instalar sin Google Play; no está publicado allí. Para publicación futura se necesita la firma propia de subida y completar los requisitos de la tienda.

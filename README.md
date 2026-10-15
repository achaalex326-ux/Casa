# Casa Familias

Entrega actual: **4.1 para presentar y probar**. Consulta [la guía de entrega](ENTREGA.md).

El proyecto Android está en **CasaFamiliasAndroid/**. Esta carpeta contiene los archivos que se editan y se sincronizan con GitHub.

`CasaFamiliasAndroid.zip` se conserva como archivo original. Los cambios posteriores se guardan en la carpeta del proyecto; ese ZIP no se actualiza automáticamente.

## Tener el proyecto en tu computadora

La opción más sencilla es GitHub Desktop:

1. Instala [GitHub Desktop](https://desktop.github.com/) e inicia sesión con tu cuenta de GitHub.
2. Elige **File → Clone repository → URL** y pega `https://github.com/achaalex326-ux/Casa.git`.
3. Elige una carpeta nueva para guardar el repositorio. Conserva tu carpeta anterior mientras compruebas que tienes todos tus archivos.
4. Abre la carpeta `CasaFamiliasAndroid` del repositorio clonado en Android Studio.
5. Cuando haya cambios hechos desde Codex o GitHub, pulsa **Fetch origin** y luego **Pull origin** en GitHub Desktop. Se actualizarán los archivos de esta carpeta.
6. Cuando edites archivos en tu computadora, escribe un resumen del cambio, pulsa **Commit to main** y después **Push origin** para subirlos a GitHub. Si tienes cambios locales pendientes, guárdalos con un commit antes de hacer Pull y resuelve cualquier conflicto que GitHub Desktop indique.

Si tienes archivos adicionales en tu carpeta anterior que no venían en el ZIP, copia solo esos archivos de tu proyecto al repositorio clonado, comprueba los cambios en GitHub Desktop y haz Commit y Push. No copies carpetas `.git`, cachés, contraseñas ni claves de firma.

Si prefieres la terminal y tienes Git instalado:

```bash
git clone https://github.com/achaalex326-ux/Casa.git
cd Casa
```

Para descargar cambios de GitHub, desde esa carpeta y con tus cambios locales guardados:

```bash
git pull --ff-only origin main
```

## Compilar y probar

Consulta [las instrucciones de Android Studio](CasaFamiliasAndroid/README_ABRIR_EN_ANDROID_STUDIO.md). El proyecto requiere JDK 17 y Android SDK Platform 35.

En macOS/Linux, desde `CasaFamiliasAndroid`:

```bash
./gradlew assembleDebug
```

En Windows:

```bat
gradlew.bat assembleDebug
```

La validación DOM está en `CasaFamiliasAndroid/validacion`. Con Node.js 24 o posterior, desde esa carpeta:

```bash
npm install --package-lock=false
npm test
```

En el entorno de Codex, las herramientas y las dependencias ya preparadas se activan así:

```bash
source /workspace/casa-environment/activate.sh
cd "$CASA_PROJECT"
gradle --no-daemon --max-workers=2 --console=plain assembleDebug
```

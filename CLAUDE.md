# FotosTV: guía para Claude

App Android TV / Fire TV / Shield que muestra álbumes compartidos de Google Fotos como salvapantallas y como visor con mosaico. Paquete `com.yuskilabs.fotostv`, marca Yuski Labs. Lee las páginas públicas de los enlaces compartidos (sin API oficial). Funcionalidad completa en `README.md`; historial en `CHANGELOG.md`; notas de publicación en `DESARROLLO.md`.

## Reglas de trabajo (obligatorias)

- Hablar siempre en **español**, conciso y sin jerga.
- **No compilar, publicar ni hacer commit hasta que el dueño diga «compila».** Mientras tanto se acumulan los cambios pedidos en el código sin tocar el APK ni el repo publicado. Para comprobar la sintaxis sí se puede usar solo `javac` (script de comprobación: `aapt` para generar R + `javac -source 8 -target 8 -bootclasspath $SDK` sobre `src` y `build/gen`, sin dex ni firma).
- Al decir «compila»: `./tools/test/run.sh` (pruebas) → `./build.sh` → `python3 tools/publish.py` → `git add -A` → comprobar que `git ls-files | grep -c keystore` da 0 → commit + push → enviar el APK.
- **`release.keystore` no se sube nunca** (ya está en `.gitignore`). La clave la pasa el dueño en cada sesión nueva, copiada junto a `build.sh`; alias `album`. Sin ella no se puede publicar una actualización que Android acepte. No hace falta recordárselo.
- Cada versión actualiza: `CHANGELOG.md` (sección `## x.y`), `README.md`, y en `AndroidManifest.xml` `versionCode` (+1) y `versionName`.
- Numeración: arreglo pequeño = tercer número (7.25.1); novedad mediana = segundo (7.26); cambio muy grande = primero (8.0).
- APK que se entrega: copiar `dist/FotosTV.apk` a `FotosTV_v<versión>.apk`, comprobar `aapt dump badging` (versionName) y enviarlo como archivo. Siempre con ese nombre.
- Commits terminan con las líneas de atribución que indique la sesión (`Co-Authored-By` y `Claude-Session`).
- En los resúmenes finales: breve, qué cambia para el usuario y qué no se ha podido comprobar de ese arreglo concreto. **No repetir** que la app no está probada en una tele real (ya lo sabe) ni mencionar la clave.
- Los botones y menús deben tener texto que **quepa**; revisar longitudes al cambiar nombres.
- La copia de seguridad (web `/export`/`importJson`), «Copiar de otra tele» y el export/import deben cubrir **todos** los ajustes. Las claves nuevas de funciones extra empiezan por `fx_`.
- Mando de la tele del dueño (LG): solo cruceta, OK, Atrás, Play y Pausa. Todo debe poder manejarse solo con eso.
- Cada pantalla: iconos **no repetidos** entre los que se ven a la vez.
- Nombres de lugar: «Localidad, Provincia» en España (solo la ciudad si es capital), «Localidad, País» fuera, siempre en español y siempre una localidad (no aldea ni comarca).
- La web de configuración (puerto 8080, PIN de 4 dígitos que cambia en cada arranque) se queda solo con: enlace de álbum, nombre, reordenar, marcar/mostrar, eliminar, alta en lote, importar/exportar. El resto de ajustes va en la app.
- El README puede actualizarse sin pedir permiso cuando convenga. GitHub Releases solo para versiones con muchos cambios importantes.
- Si hay que pedir permisos (Drive, etc.), pedirlos todos de una vez al principio.

## Restricciones técnicas

- **Sin Gradle.** `build.sh` usa `aapt`, `javac`, `dalvik-exchange`, `zipalign`, `apksigner` y `android.jar` de la API 23 (`/usr/lib/android-sdk/platforms/android-23/android.jar`).
- Java 8, **sin lambdas ni AndroidX**; minSdk 21, targetSdk 23.
- Iconos: vectores `res/drawable/ic_*.xml` (los nuevos con viewport 24).
- `build.sh` genera `appassets/novedades.txt` a partir de las 8 últimas secciones de `CHANGELOG.md`.
- Pruebas: lógica pura en `FxLogic` probada en JVM con `tools/test/Tests.java` (`tools/test/run.sh`, 43 comprobaciones). Lo que dependa de Android no se prueba así.

## Mapa del código (`src/com/yuskilabs/fotostv/`)

- **Pantallas**: `SettingsActivity` (principal), `AjustesActivity` (todos los ajustes por temas), `BrowserActivity` + piezas (`BrowserActions`, `BrowserUi`, `Tiles`, `Tile`, `Entry`, `EntryOps`, `Dialogs`, `BusyBar`, `DupeScan`, `BrowseUtil`), `PlayerActivity`, `PreviewActivity`, `PhotoDream` (salvapantallas), `InfoPanel` (ficha), `PlacesActivity` + `MapView`/`MapTiles`, `SearchActivity`, `StatsActivity`, `TripsActivity`, `DiagActivity`, `ProjectivyActivity`.
- **Motor del pase**: `Slideshow` (precarga, fundidos, vídeo, mando), `Mosaic`, `Credit`, `VideoCache`, `VideoProxy` (servidor local para ver vídeos mientras se descargan), `Music`.
- **Datos**: `AlbumFetcher` (lee el álbum público), `MetaFetcher` (EXIF, vídeo, lugar), `GeoIndex` (SQLite: tablas `photos` y `cams`, la cámara `""` = sin cámara en el EXIF), `GeoService`, `CamScan`/`CamScanService`, `Thumbs`, `Guest` (invitados 2 h), `Search`.
- **Ajustes**: `Prefs.sp(c)` (perfil en uso: `cfg` para «Familia», `cfgx_<id>` para los demás) y `Prefs.meta(c)` (cachés aparte). `Extras` (claves `fx_*`, todas apagadas por defecto), `FxLogic` (lógica pura), `Presets` (estilos Tranquilo / Fiesta / Solo recuerdos, con «Deshacer estilo»).
- **Red y copia**: `WebConfigServer` (web local, `/export` con cookie `fp=<pin>`, `importJson`), `TvCopy.pull` (copia de otra tele por la red usando ese `/export`), `Updater` (lee `update.json` del repo), `Novedades`.
- **Otros**: `IdleService` (inicio automático en Fire TV, servicio de accesibilidad), `ProjectivyService` (fondo para Projectivy Launcher), `Weather` (Open-Meteo).

## Decisiones tomadas (no deshacer sin preguntar)

- La transición «cortinilla» usa límites de recorte (`setClipBounds`), no traslación, porque el movimiento Ken Burns ya usa traslación de la vista.
- Los tres botones de mosaicos se fusionaron en uno solo, «Mosaicos» (no, pocos, normal, muchos); la probabilidad se escala con `FxLogic.mosaicOdds`. Los usuarios con los antiguos activados empiezan en «normal».
- Se quitó «Reloj grande»; «Fecha» incluye «con día y hora».
- Se dice «Estilo» (no «Preajuste») para que el texto quepa en el botón.
- En «Copiar de otra tele», el campo de dirección usa teclado de texto/URI (el numérico de la tele no tiene punto) y empieza con `192.168.1.`.
- La cámara de cada foto se cachea por tele; desde 7.25 va también en la copia entre teles y en la copia de seguridad (`cams`). «Leer cámaras» ofrece «Leer las que faltan» y «Releer las de sin cámara».
- «Novedades» ya no sale sola al abrir tras actualizar (el actualizador ya enseña las notas); queda el botón en Ajustes › Ayuda y mantenimiento.
- El fondo de Projectivy y las sugerencias de búsqueda se mantienen como están.
- La cámara/autor de las marcas de agua se decide por reglas editables en la web.

## Pendiente (solo si el dueño lo pide)

- Ideas numeradas que se dejaron sin hacer: 7 (tarjeta de capítulo en el pase), 8 (mapa del viaje), 11 y 12 (pedir la lista al dueño).
- Seguir repartiendo `BrowserActivity` en piezas más pequeñas.

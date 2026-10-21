# FotosTV

Tus álbumes compartidos de Google Fotos en la tele: como **salvapantallas** y como **visor** con mosaico, sin descargar nada y sin cuenta de Google en el dispositivo.

Pensada para **Android TV** (probada en una Nvidia Shield TV 2017 con Android TV 11) y, con alguna limitación, para **Fire TV**.

> **Aviso.** FotosTV no está afiliada a Google ni usa su API oficial (que ya no permite este uso). Lee las páginas públicas de los enlaces compartidos, así que si Google cambia esas páginas la app puede dejar de funcionar hasta que se actualice.

## Qué hace

- **Salvapantallas** que muestra fotos y vídeos de uno o varios álbumes compartidos, directamente desde la nube.
- **Explorador en la tele**: mosaico de álbumes → mosaico de fotos → pantalla completa, como una galería normal.
- **Orden** de cada álbum desde el propio mosaico: aleatorio, más antiguas primero o más recientes primero, y un botón de **pase de diapositivas**.
- **Vídeos** con sonido opcional y fundidos suaves entre fotos horizontales, verticales y vídeos.
- **Información en pantalla**: nombre del álbum, fecha de captura, lugar (si la foto tiene GPS) y hora.
- **Configuración desde el móvil** mediante una página web local que sirve la propia tele: añadir enlaces (uno a uno o en lote), ajustes, copia de seguridad.
- **Precarga**: las siguientes fotos y vídeos se preparan por adelantado para que el pase no se pare.
- **Cachés con límite** configurable, para no llenar el dispositivo.
- **Actualización desde la propia app** (botón «Buscar actualización»).

## Instalación

1. Descarga la última [`FotosTV.apk`](https://github.com/yuskilabs/fotostv/raw/main/dist/FotosTV.apk) (la versión actual está en [`update.json`](update.json)).
2. Instálala en la tele (por ejemplo con *Send Files to TV*, una memoria USB o `adb install FotosTV.apk`).
3. Abre **FotosTV**.

### Elegir FotosTV como salvapantallas

- **Android TV / Google TV**: botón **«Elegir salvapantallas»** de la app, o Ajustes → Preferencias del dispositivo → Salvapantallas.
- **Fire TV**: Amazon no permite elegir salvapantallas de terceros en los ajustes. Se puede activar por ADB (en algunas versiones de Fire OS no funciona o se revierte):

  ```
  adb shell settings put secure screensaver_components com.yuskilabs.fotostv/.PhotoDream
  ```

## Uso

### Añadir álbumes

1. En el móvil, abre el álbum en Google Fotos → **Compartir** → **Crear enlace** y copia el enlace.
2. En la pantalla principal de FotosTV aparece una dirección del tipo `http://192.168.1.x:8080`. Ábrela en el móvil (misma wifi).
3. Pega el enlace en **Añadir álbum**, o varios enlaces (uno por línea) en **Añadir varios álbumes a la vez**.

Los álbumes se añaden al instante, sin pulsar «Guardar». Desde la misma página se eligen el orden, el intervalo, el encuadre, los vídeos, la información en pantalla, las fotos ocultas y la caché.

### Mando a distancia

| Dónde | Tecla | Acción |
|---|---|---|
| Salvapantallas / pase | ◀ ▶ | foto anterior / siguiente |
| | ▲ | pausar / continuar |
| | ▼ | ocultar esa foto para siempre |
| | cualquier otra | salir |
| Visor (desde el mosaico) | ◀ ▶ | anterior / siguiente en el orden del mosaico |
| | ▲ u OK | activar el pase automático (▲ otra vez, vuelve al modo álbum) |
| | Atrás | volver al mosaico |
| Mosaico de fotos | Atrás | 1.ª vez: sube a los botones · 2.ª vez: vuelve a los álbumes |

### Copia de seguridad

En la página de ajustes del móvil, «Exportar» genera un texto con álbumes, ajustes y fotos ocultas; en otro dispositivo se pega en el mismo cuadro y se pulsa «Importar».

### Actualizar

Pantalla principal → **Buscar actualización**. Compara con la última versión publicada en este repositorio, muestra las novedades y descarga la APK. La primera vez, Android pedirá permitir a FotosTV «instalar apps desconocidas».
Todas las APK deben ir firmadas con la misma clave para que Android acepte la actualización encima de la instalada.

## Privacidad

- Los enlaces de los álbumes y los ajustes se guardan **solo en el dispositivo**.
- La app se conecta a: Google Fotos (para leer los álbumes compartidos), GitHub (para buscar actualizaciones) y, si la foto tiene GPS y el dispositivo no resuelve el lugar, a OpenStreetMap Nominatim (se le envían las coordenadas de esa foto).
- La página de configuración es accesible desde cualquier dispositivo de tu red local mientras la pantalla principal está abierta. No uses la app en redes que no sean de confianza.
- Quien tenga el enlace de un álbum puede verlo: no compartas enlaces de álbumes privados.

## Limitaciones conocidas

- Depende del formato de las páginas públicas de Google Fotos (sin API oficial).
- Las portadas de los álbumes son fotos al azar: la portada elegida en Google no viaja en el enlace compartido.
- Solo se ha probado en una Nvidia Shield TV; en Fire TV puede haber diferencias (salvapantallas, almacenamiento reducido).

## Numeración de versiones

- Cambio muy pequeño (arreglo): tercer número, p. ej. 5.4 → 5.4.1.
- Cambio mediano (novedad): segundo número, p. ej. 5.4 → 5.5.
- Cambio muy grande: primer número, p. ej. 5.x → 6.0.

## Publicar una versión

```
./build.sh && python3 tools/publish.py
git add -A && git commit -m "FotosTV x.y" && git push
```

`publish.py` copia la APK a `dist/` y genera `update.json` con las notas de esa versión tomadas de `CHANGELOG.md`.

## Compilar

No usa Gradle. Hace falta `aapt`, `javac`, `dalvik-exchange`, `zipalign`, `apksigner` y el `android.jar` de la API 23 (la ruta está al principio de `build.sh`).

```
./build.sh                 # genera FotosTV.apk
KEYSTORE=mi.keystore KS_PASS=secreto ./build.sh   # con tu propia clave de firma
```

Si no existe la clave, el script crea una nueva. **La clave de firma no se sube al repositorio**: guárdala a buen recaudo, porque sin ella no se puede publicar una actualización que Android acepte sobre la versión instalada.

## Estructura

```
AndroidManifest.xml
build.sh
res/                         iconos, banner y textos
src/com/yuskilabs/fotostv/
  SettingsActivity           pantalla principal
  BrowserActivity            explorador de álbumes (mosaicos)
  PlayerActivity             visor y pase desde el explorador
  PhotoDream, PreviewActivity   salvapantallas y vista previa
  Slideshow                  motor del pase: precarga, fundidos, vídeo, mando
  AlbumFetcher, MetaFetcher  lectura de álbumes y metadatos (EXIF, vídeo, lugar)
  Thumbs                     caché de miniaturas (memoria y disco)
  WebConfigServer            página de ajustes servida desde la tele
  Updater                    búsqueda e instalación de actualizaciones
tools/publish.py             publica la APK en dist/ y genera update.json
  Prefs, Item, Session, Ui   utilidades
```

## Historial de cambios

Consulta [CHANGELOG.md](CHANGELOG.md).

## Licencia

Por ahora sin licencia: el código es visible, pero no se concede permiso para reutilizarlo ni redistribuirlo.

---

<p align="center">
  <img src="assets/yuskilabs-logo.png" alt="Yuski Labs" width="120"><br>
  <b>yuskilabs</b>
</p>

<p align="center">
  <img src="assets/fotostv-icon.png" alt="FotosTV" width="140"><br>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/fotostv-wordmark.png">
    <img src="assets/fotostv-wordmark-claro.png" alt="FotosTV" width="280">
  </picture>
</p>

# FotosTV

Tus álbumes compartidos de Google Fotos en la tele: como **salvapantallas** y como **visor** con mosaico, sin descargar nada y sin cuenta de Google en el dispositivo.

Pensada para **Android TV** (probada en una Nvidia Shield TV 2017 con Android TV 11) y, con alguna limitación, para **Fire TV**.

> **Aviso.** FotosTV no está afiliada a Google ni usa su API oficial (que ya no permite este uso). Lee las páginas públicas de los enlaces compartidos, así que si Google cambia esas páginas la app puede dejar de funcionar hasta que se actualice.

## Qué hace

- **Salvapantallas** que muestra fotos y vídeos de uno o varios álbumes compartidos, directamente desde la nube.
- **Explorador en la tele**: mosaico de álbumes → mosaico de fotos → pantalla completa, como una galería normal. Álbumes especiales: **Todas**, **Favoritos** y **Ocultas** (con la miniatura difuminada).
- **Orden** de cada álbum desde el propio mosaico: aleatorio, más antiguas primero o más recientes primero, y un botón de **pase de diapositivas**.
- **Vídeos en directo, sin esperas**: empiezan al momento mientras se descargan, con búfer (como en YouTube) y línea de tiempo con el tiempo transcurrido y el que queda. Calidad automática, alta o media (en Fire TV la automática usa 720p). Con la barra azul (▼) se pausa y se salta ±5 s. Los vídeos se guardan en una caché con límite.
- **Fundidos suaves** entre fotos horizontales, verticales y vídeos, con sonido opcional. En el pase, un vídeo solo entra cuando ya tiene unos segundos listos, para que no se pare.
- **Información en pantalla**: nombre del álbum, fecha de captura, lugar (si la foto tiene GPS) y hora. Con ▼ se abre la ficha completa (archivo, cámara, exposición, resolución…) y un **mapa** con el punto exacto.
- **Fotos favoritas y ocultas**: se marcan desde el mando y quedan guardadas en sus álbumes.
- **Configuración desde el móvil** mediante una página web local que sirve la propia tele, con **código QR** para abrirla al instante: añadir enlaces (uno a uno o en lote), ajustes, copia de seguridad.
- **Inicio automático** del pase tras 1, 2, 5, 10, 15 o 30 minutos sin tocar el mando (pensado sobre todo para Fire TV) y **cierre automático** del pase pasado un rato (5 min a 2 h, o nunca) por si la tele se apaga.
- **Fondo de pantalla para Projectivy Launcher** con fotos (y opcionalmente vídeos) de tus álbumes.
- **Precarga**: las siguientes fotos y vídeos se preparan por adelantado para que el pase no se pare.
- **Paso manual instantáneo** en el visor: sin fundido, con decenas de fotos descargadas por adelantado para que ◀ ▶ respondan al momento.
- **Cachés con límite** configurable, para no llenar el dispositivo.
- **Actualización desde la propia app** (botón «Actualizar»).

## Instalación

1. Descarga la última [`FotosTV.apk`](https://github.com/yuskilabs/fotostv/raw/main/dist/FotosTV.apk) (la versión actual está en [`update.json`](update.json)).
2. Instálala en la tele (por ejemplo con *Send Files to TV*, una memoria USB o `adb install FotosTV.apk`).
3. Abre **FotosTV**.

### Inicio automático (Fire TV con FireOS 8 y otros)

FireOS 8 sustituye el salvapantallas de Android por su «Fondo ambiental», y no admite salvapantallas de terceros. Como alternativa, FotosTV puede abrir el pase de fotos por sí sola tras unos minutos sin usar el mando (y sin nada sonando). Se activa una vez por ADB:

```
adb shell settings put secure enabled_accessibility_services com.yuskilabs.fotostv/com.yuskilabs.fotostv.IdleService
adb shell settings put secure accessibility_enabled 1
adb shell appops set com.yuskilabs.fotostv SYSTEM_ALERT_WINDOW allow
```

Atención: ese primer comando **sustituye** la lista de servicios de accesibilidad que tuvieras activos. Si usas otros, añade el de FotosTV separado por `:` a los existentes.

Opcional, para que sobreviva a las actualizaciones de la app (Android desactiva el servicio al actualizar): concede una sola vez este permiso y FotosTV volverá a activar el servicio sola al abrirse.

```
adb shell pm grant com.yuskilabs.fotostv android.permission.WRITE_SECURE_SETTINGS
```

Después, en la pantalla principal, el botón **Inicio automático** elige el tiempo (1, 2, 5, 10, 15 o 30 minutos). Si falta algún permiso, la propia app muestra los pasos y los comandos con la IP de tu tele. El tiempo de FotosTV debe ser menor que el del reposo de la Fire TV. El servicio solo observa que se pulsa una tecla: no lee la pantalla ni guarda nada.

El botón **Cerrar el pase tras** (5, 10, 15 o 30 min, 1 h, 2 h, nunca; 10 min por defecto) cierra el pase si nadie toca el mando, para no dejar la pantalla encendida si la tele se apaga. Las flechas y OK reinician la cuenta.

Para quitar el inicio automático: `adb shell settings put secure enabled_accessibility_services ""` (esto también desactiva el resto de servicios de accesibilidad).

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

Los álbumes se añaden al instante, sin pulsar «Guardar». Se pueden ordenar a mano con «Subir» / «Bajar» (y «Guardar»), o automáticamente por la fecha de la última foto de cada álbum. Desde la misma página se eligen el orden, el intervalo, el encuadre, los vídeos, la información en pantalla, las fotos ocultas y la caché.

### Mando a distancia

| Dónde | Tecla | Acción |
|---|---|---|
| Salvapantallas / pase | ◀ ▶ | foto anterior / siguiente |
| | OK | pausar / continuar |
| | ▲ | ocultar esa foto (queda en el álbum «Ocultas», donde se puede volver a mostrar) |
| | ▼ | pausa y muestra la información de la foto, con el mapa si tiene GPS; cualquier botón la cierra y continúa |
| | cualquier otra | salir |
| Mientras suena un vídeo | ▼ | barra azul de control: OK pausa / continúa el vídeo, ◀ ▶ saltan 5 s (hasta donde esté descargado) |
| | ▲ (con la barra azul) | vuelve a los controles normales |
| | ▼ (con la barra azul) | información del vídeo |
| Visor (desde el mosaico) | ◀ ▶ | anterior / siguiente en el orden del mosaico, al instante y sin fundido |
| | OK | activar el pase automático (OK otra vez, vuelve al modo álbum) |
| | ▲ / ▼ | ocultar la foto / información con mapa |
| | Atrás | volver al mosaico |
| Mosaico de fotos | mantener OK | ocultar o mostrar · ver información · añadir o quitar de favoritos |
| Mosaico de fotos | Atrás | 1.ª vez: sube a los botones · 2.ª vez: vuelve a los álbumes |

### Fondo de pantalla en Projectivy Launcher (experimental)

FotosTV incluye un proveedor de fondos para [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu), que necesita su versión **Premium** para usar proveedores externos.

1. En FotosTV, pulsa **Fondo para Projectivy** y marca los álbumes que quieras (o **Todas**, que incluye también los que añadas después). El orden es siempre aleatorio.
2. Elige cómo encajan las fotos (rellenar, originales u originales con fondo difuminado), si quieres incluir fotos verticales (con o sin desenfoque) y si quieres vídeos.
3. En Projectivy, en los ajustes de fondos de pantalla, elige FotosTV como proveedor.

Aún no está probado en todos los equipos. Projectivy decide cada cuánto cambia la foto; FotosTV no puede cambiar ese intervalo.

### Copia de seguridad

En la página de ajustes del móvil, «Exportar» genera un texto con álbumes, ajustes y fotos ocultas; en otro dispositivo se pega en el mismo cuadro y se pulsa «Importar».

### Actualizar

Pantalla principal → **Actualizar**. Compara con la última versión publicada en este repositorio, muestra las novedades y descarga la APK. Android pedirá confirmar la instalación y, si hace falta, permitir a FotosTV «instalar apps desconocidas».
GitHub tarda unos minutos en refrescar los archivos tras publicar una versión, así que conviene esperar unos 5 minutos. Tras actualizar, la app intenta abrirse sola; Android puede impedirlo en algunos dispositivos.
Todas las APK deben ir firmadas con la misma clave para que Android acepte la actualización encima de la instalada.

## Privacidad

- Los enlaces de los álbumes y los ajustes se guardan **solo en el dispositivo**.
- La app se conecta a: Google Fotos (para leer los álbumes compartidos), GitHub (para buscar actualizaciones) y, si la foto tiene GPS y el dispositivo no resuelve el lugar, a OpenStreetMap Nominatim (se le envían las coordenadas de esa foto).
- La página de configuración es accesible desde cualquier dispositivo de tu red local mientras la pantalla principal está abierta. No uses la app en redes que no sean de confianza.
- Quien tenga el enlace de un álbum puede verlo: no compartas enlaces de álbumes privados.

## Limitaciones conocidas

- Depende del formato de las páginas públicas de Google Fotos (sin API oficial).
- La precarga del visor usa hasta 100 MB de memoria (según el dispositivo); en equipos muy justos puede notarse menos fluida.
- Las portadas de los álbumes son fotos al azar: la portada elegida en Google no viaja en el enlace compartido.
- Solo se ha probado a fondo en una Nvidia Shield TV; en Fire TV puede haber diferencias (salvapantallas, almacenamiento reducido, vídeos algo más lentos).
- El inicio automático y el fondo de Projectivy son novedades poco probadas fuera de unos pocos equipos.
- Los vídeos dependen de que Google tenga ya preparada la versión de vídeo; los recién subidos pueden tardar en estar disponibles.

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
  VideoProxy                 servidor local para ver vídeos mientras se descargan
  InfoPanel                  ficha de información con mapa
  IdleService                inicio automático (servicio de accesibilidad)
  ProjectivyService, ProjectivyActivity   fondo de pantalla para Projectivy
  AlbumFetcher, MetaFetcher  lectura de álbumes y metadatos (EXIF, vídeo, lugar)
  Thumbs                     caché de miniaturas (memoria y disco, con desenfoque)
  WebConfigServer            página de ajustes servida desde la tele
  Updater                    búsqueda e instalación de actualizaciones
  Prefs, Item, Session, Ui   utilidades
  io/nayuki/qrcodegen        generador del código QR (Project Nayuki, MIT)
tools/publish.py             publica la APK en dist/ y genera update.json
```

## Historial de cambios

Consulta [CHANGELOG.md](CHANGELOG.md).

Notas de compilación y publicación: [DESARROLLO.md](DESARROLLO.md).

## Licencia

Por ahora sin licencia: el código es visible, pero no se concede permiso para reutilizarlo ni redistribuirlo.

---

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/yuskilabs-horizontal-oscuro.png">
    <img src="assets/yuskilabs-horizontal-claro.png" alt="yuskilabs" width="240">
  </picture>
</p>

## Créditos

La tipografía es [Figtree](https://github.com/erikdkennedy/figtree) (licencia SIL OFL). El código QR se genera con [QR Code generator](https://github.com/nayuki/QR-Code-generator) de Project Nayuki (licencia MIT).

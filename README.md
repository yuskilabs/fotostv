<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/fotostv-logo.png">
    <img src="assets/fotostv-logo-claro.png" alt="FotosTV" width="420">
  </picture>
</p>

# FotosTV

Tus álbumes compartidos de Google Fotos en la tele: como **salvapantallas** y como **visor** con mosaico, sin descargar nada y sin cuenta de Google en el dispositivo.

Pensada para **Android TV** (probada en una Nvidia Shield TV 2017 con Android TV 11) y, con alguna limitación, para **Fire TV**.

> **Aviso.** FotosTV no está afiliada a Google ni usa su API oficial (que ya no permite este uso). Lee las páginas públicas de los enlaces compartidos, así que si Google cambia esas páginas la app puede dejar de funcionar hasta que se actualice.

## Qué hace

### Ver tus álbumes
- **Salvapantallas** con fotos y vídeos de uno o varios álbumes compartidos, directamente desde la nube.
- **Explorador en la tele**: mosaico de álbumes, mosaico de fotos y pantalla completa, como una galería normal.
- **Álbumes especiales**: **Todas** (todas las fotos de todos tus álbumes), **Favoritos** y **Ocultas** (con la miniatura difuminada).
- **Orden** de cada álbum desde su mosaico: más recientes primero (por defecto), más antiguas primero o aleatorio. El pase de un álbum respeta el orden que elijas; si no eliges ninguno, el pase va aleatorio.
- **Pase de diapositivas** desde el mosaico. Los botones del mosaico son los mismos en todos los álbumes y ocupan todo el ancho.
- **Formato** de las fotos: auto, llenar o completo, con fondo difuminado opcional en las verticales.
- **Fundidos suaves** entre fotos horizontales, verticales y vídeos. En el pase, un vídeo solo entra cuando ya tiene unos segundos listos, para que no se pare.
- **Paso manual instantáneo** en el visor, sin fundido: decenas de fotos se descargan por adelantado para que ◀ ▶ respondan al momento.
- **Precarga**: las siguientes fotos y vídeos se preparan antes de que toquen.
- **Seguir donde se quedó**: el álbum se reanuda en la última foto vista (opcional).

### Vídeos
- Empiezan al momento mientras se descargan, con búfer (como en YouTube) y línea de tiempo con tiempo transcurrido y restante.
- Calidad automática, alta o media (en Fire TV la automática usa 720p).
- Con la barra azul (▼) se pausa y se salta ±5 s.
- Sonido opcional y caché de vídeos con límite.

### Información de cada foto
- **Rótulo en el pase**: nombre del álbum, fecha, hora y lugar (cada dato se puede activar o no en Ajustes).
- **Ficha completa** (▼): archivo, fecha y hora, lugar, coordenadas y altitud, cámara, exposición, resolución y álbum, y un **mapa** de OpenStreetMap con el punto exacto.
- **Botones de la ficha**, en dos columnas:
  - Bajo la información: *Ir al álbum* (se abre en esa foto), *Pase de este álbum*, *Añadir o quitar de favoritos* y *Ocultar*.
  - Bajo el mapa: *Más de (localidad)* y *Más de (provincia, isla o país)*; en capitales o sitios con un solo nombre, *Más de la zona (radio de 1 km)* y *Más de (el sitio)*; *Más de este día* (fotos hechas el mismo día del calendario) y *Recargar lugar*.
  - Dentro del mapa, **Acercar** y **Alejar**. El mapa se maneja con el mando como en Lugares; Atrás devuelve el foco a los botones.
- **Selección múltiple** en un álbum: con una pulsación larga, «Seleccionar» permite marcar varias fotos (o todas) y ocultarlas, mostrarlas, o añadirlas y quitarlas de favoritos de una vez.
- **Favoritas y ocultas**: se marcan desde el mando y quedan guardadas en sus álbumes.

### Lugares y mapa
- **Índice de ubicaciones** en segundo plano: la app lee dónde se hizo cada foto de todos tus álbumes y le pone nombre al sitio, sin que las abras. Funciona con un servicio en primer plano (con aviso en la barra), trabaja por tandas y continúa donde se quedó si se interrumpe. Solo cuenta con las fotos cuya ubicación entrega Google (el álbum debe haberse compartido con ubicación).
- **Nombres de lugar**: se usa la **localidad**, no la aldea ni el barrio; si solo hay aldea, la localidad o municipio al que pertenece (Estepona y no Voladilla). En España sale «Municipio, Provincia» (o «Municipio, Isla» en Baleares y Canarias; solo la ciudad si es capital) y en el extranjero «Ciudad, País», en castellano y con letras latinas. Las zonas con más fotos se nombran primero y las que no consiguen nombre tras varios intentos quedan como «Sin nombre».
- **Pantalla Lugares**: lista a la izquierda (países, provincias e islas con su número de fotos; al elegir uno, sus localidades) y **mapa de calor** a la derecha. Al moverte por la lista, el mapa te lleva a ese sitio.
- **Mapa**: flechas para mover, OK para ver las fotos de la mira (o acercar), Play acerca, Pausa aleja.
- **Mosaico de lugar**: al elegir una localidad, una zona del mapa o «Todas las fotos de…», se abre un mosaico como el de un álbum (con pase, orden y formato). Atrás vuelve a Lugares.
- **Revisar ubicaciones** (Ajustes, menú largo de un álbum o de una foto): vuelve a mirar solo lo pendiente. Pulsación larga sobre el contador: rehacer los nombres.

### Música
- Apagada, **emisora de internet** o **carpeta** de canciones (de la tele o de un USB), con **volumen propio**.
- Lista de emisoras por estilos y épocas (relax, pop, años 80 y 90, rock, Navidad…), con **escucha previa**: al abrir la lista suena la elegida y, al bajar, suena cada emisora sobre la que te posas. También se puede pegar la dirección de una emisora propia desde la web.
- Suena en los pases que abres tú y, si quieres, en el salvapantallas. Se calla mientras suena el audio de un vídeo.

### Invitados
- Botón **Invitado** con un código QR para que una visita enseñe su álbum de Google Fotos en tu tele al momento, sin tocar tu cuenta ni tus álbumes.
- Dura 2 horas (puede haber varios a la vez), sale al principio de «Ver álbumes» y, al caducar, se borra todo rastro.

### Configuración
- **Pantalla Ajustes** en la tele, con tarjetas por secciones: presentación (orden, tiempo por foto, encuadre, continuar, vídeos y sonido), información en pantalla, música, vídeo, caché y fotos ocultas, ubicaciones y salvapantallas.
- **Página web local** que sirve la propia tele, con **código QR** (con OK sobre el código se ve en grande) y PIN: añadir enlaces (uno a uno o en lote), nombre, reordenar o ocultar álbumes, quitarlos, emisora propia y copia de seguridad.
- **Copia de seguridad**: exportar e importar, copiando y pegando o con archivo, con álbumes, ajustes, fotos ocultas, favoritas y coordenadas ya revisadas.
- **Pulsación larga** en un álbum o foto: menú con iconos (ocultar, información, favoritos, seleccionar, revisar ubicación…).
- **Cachés con límite** configurable, para no llenar el dispositivo.
- **Actualización desde la propia app** (botón «Actualizar»).

### Dispositivos y extras
- **Inicio automático** del pase tras 1, 2, 5, 10, 15 o 30 minutos sin tocar el mando (pensado para Fire TV) y **cierre automático** del pase pasado un rato (5 min a 2 h, o nunca), por si la tele se apaga.
- **Fondo de pantalla para Projectivy Launcher** con fotos (y opcionalmente vídeos) de tus álbumes.

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

El botón **Cerrar pase** (5, 10, 15 o 30 min, 1 h, 2 h, nunca; 10 min por defecto) cierra el pase si nadie toca el mando, para no dejar la pantalla encendida si la tele se apaga. Las flechas y OK reinician la cuenta.

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

Los álbumes se añaden al instante, sin pulsar «Guardar». Se pueden ordenar a mano con «Subir» / «Bajar» (y «Guardar»), o automáticamente por la fecha de la última foto de cada álbum. El resto de ajustes (orden, tiempo por foto, encuadre, vídeos, información en pantalla, música, ubicaciones, fotos ocultas y caché) se hacen en la pantalla **Ajustes** de la tele.

### Mando a distancia

| Dónde | Tecla | Acción |
|---|---|---|
| Salvapantallas / pase | ◀ ▶ | foto anterior / siguiente |
| | OK | pausar / continuar |
| | ▲ | ocultar esa foto (queda en el álbum «Ocultas», donde se puede volver a mostrar) |
| | ▼ | pausa y abre la ficha de la foto (con mapa y botones si tiene GPS); Atrás la cierra y continúa |
| | cualquier otra | salir |
| Mientras suena un vídeo | ▼ | barra azul de control: OK pausa / continúa el vídeo, ◀ ▶ saltan 5 s (hasta donde esté descargado) |
| | ▲ (con la barra azul) | vuelve a los controles normales |
| | ▼ (con la barra azul) | información del vídeo |
| Ficha | ▲ ▼ ◀ ▶ | moverse por los botones; si el foco entra en el mapa, flechas para moverlo, OK para acercar, Atrás para salir del mapa |
| Visor (desde el mosaico) | ◀ ▶ | anterior / siguiente en el orden del mosaico, al instante y sin fundido |
| | OK | activar el pase automático (OK otra vez, vuelve al modo álbum) |
| | ▲ / ▼ | ocultar la foto / información con mapa |
| | Atrás | volver al mosaico |
| Mosaico de fotos | mantener OK | ocultar o mostrar · ver información · favoritos · seleccionar · revisar ubicación |
| Mosaico de fotos | Atrás | 1.ª vez: sube a los botones · 2.ª vez: vuelve a los álbumes |

### Fondo de pantalla en Projectivy Launcher (experimental)

FotosTV incluye un proveedor de fondos para [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu), que necesita su versión **Premium** para usar proveedores externos.

1. En FotosTV, pulsa **Fondo para Projectivy** y marca los álbumes que quieras (o **Todas**, que incluye también los que añadas después). El orden es siempre aleatorio.
2. Elige cómo encajan las fotos (rellenar, originales u originales con fondo difuminado), si quieres incluir fotos verticales (con o sin desenfoque) y si quieres vídeos.
3. En Projectivy, en los ajustes de fondos de pantalla, elige FotosTV como proveedor.

Aún no está probado en todos los equipos. Projectivy decide cada cuánto cambia la foto; FotosTV no puede cambiar ese intervalo.

### Copia de seguridad

En la página web del móvil, «Exportar» copia al portapapeles (o «Exportar a archivo» guarda un archivo) con álbumes, ajustes, fotos ocultas, favoritas y coordenadas revisadas; en otro dispositivo se pega en el mismo cuadro (o se elige «Importar archivo») y se pulsa «Importar».

### Actualizar

Pantalla principal → **Actualizar**. Compara con la última versión publicada en este repositorio, muestra las novedades y descarga la APK. Android pedirá confirmar la instalación y, si hace falta, permitir a FotosTV «instalar apps desconocidas».
GitHub tarda unos minutos en refrescar los archivos tras publicar una versión, así que conviene esperar unos 5 minutos. Tras actualizar, la app intenta abrirse sola; Android puede impedirlo en algunos dispositivos.
Todas las APK deben ir firmadas con la misma clave para que Android acepte la actualización encima de la instalada.

## Privacidad

- Los enlaces de los álbumes y los ajustes se guardan **solo en el dispositivo**.
- La app se conecta a: la emisora de música que elijas (si la activas), Google Fotos (para leer los álbumes compartidos), GitHub (para buscar actualizaciones) y, para poner nombre a los lugares y dibujar el mapa, a OpenStreetMap (Nominatim y sus teselas; se le envían las coordenadas de las fotos con GPS).
- La página de configuración es accesible desde cualquier dispositivo de tu red local mientras la pantalla principal está abierta, y pide un **PIN** de 4 cifras que sale en la tele (el código QR ya lo lleva). No uses la app en redes que no sean de confianza.
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
  Music                      música de fondo (emisora o carpeta)
  Slideshow                  motor del pase: precarga, fundidos, vídeo, mando
  VideoProxy                 servidor local para ver vídeos mientras se descargan
  InfoPanel                  ficha de información con mapa y botones
  Nav                        saltos desde la ficha (álbum, pase del álbum, lugar, zona o día)
  PlacesActivity, MapView, MapTiles   pantalla Lugares y mapa de OpenStreetMap
  GeoIndex, GeoService       índice de ubicaciones en segundo plano
  AjustesActivity            pantalla de ajustes
  Guest                      álbumes de invitados
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

Las tipografías son [Figtree](https://github.com/erikdkennedy/figtree) y [Caveat](https://github.com/googlefonts/caveat) (licencia SIL OFL); los iconos, [Material Symbols](https://github.com/google/material-design-icons) de Google (licencia Apache 2.0). El código QR se genera con [QR Code generator](https://github.com/nayuki/QR-Code-generator) de Project Nayuki (licencia MIT).

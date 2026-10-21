# Historial de cambios

Todas las versiones de FotosTV, de la más reciente a la más antigua.
El formato sigue la idea de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## 5.2
- **Botón «Buscar actualización»** en la pantalla principal: consulta la última versión publicada en este repositorio (`update.json`), muestra las novedades y, si quieres, descarga la APK y abre el instalador de Android.
- La pantalla principal muestra la versión instalada y avisa en silencio cuando hay una nueva.
- Se añade el permiso de instalación de paquetes (necesario para actualizar desde la propia app).
- Botones de la pantalla principal algo más compactos para que quepan los seis.

## 5.1
- Nuevo identificador interno de la app: `com.yuskilabs.fotostv`.
  Android la trata como una app distinta, así que hay que desinstalar la versión anterior y volver a configurar (con la copia de seguridad es un solo pegado).

## 5.0
- **Copia de seguridad**: en la web de ajustes, «Exportar» genera un texto con álbumes, ajustes y fotos ocultas, y «Importar» lo restaura en otro dispositivo.
- **Caché configurable** desde la web de ajustes: máximo de miniaturas (100 MB – 2 GB) y de vídeos temporales (300 MB – 3 GB).
  En dispositivos con poco almacenamiento (por ejemplo, un Fire Stick de 8 GB) los valores por defecto son menores (150 MB y 500 MB).
- Identificador provisional `es.yus.fotostv` (sustituido en la 5.1).

## 4.5
- Límite de la caché de miniaturas subido de 120 MB a 500 MB.

## 4.4
- **Miniaturas más rápidas**: al abrir un álbum se descargan por detrás las 150 miniaturas siguientes a la que se está viendo; lo que está en pantalla tiene siempre prioridad. 4 descargas simultáneas.
- **Pantalla principal rediseñada**: dos columnas, logo, degradado azul y tarjetas; los botones ya no se cortan.

## 4.3
- **Ordenar los álbumes en el mosaico**: «Aleatorio», «Más antiguas primero» y «Más recientes primero» (con ✓ en el activo).
- Botón independiente **«▶ Pase de diapositivas»**, que reproduce el álbum en el orden elegido (aleatorio si no se ha elegido ninguno).
- **«Atrás» en dos pasos** dentro de un álbum: primero sube a los botones, después vuelve a los álbumes.
- **Portadas**: cada álbum muestra una foto al azar (la portada elegida en Google Fotos no viaja en el enlace compartido).
- La pantalla principal muestra el **tamaño de la caché**, y «Vaciar caché» borra también los vídeos temporales.

## 4.2
- Se elimina la marca de vídeo propia sobre las miniaturas (Google ya pone la suya).
- **Visor a pantalla completa como un álbum normal**: ◀ ▶ van a la anterior/siguiente en el orden del mosaico y, si es un vídeo, se ve al instante su miniatura con «Cargando vídeo…» hasta que está listo. ▲ u OK activa el pase automático.

## 4.1
- **Precarga robusta en el pase de diapositivas**: hay varias diapositivas preparadas por delante y los vídeos se descargan aparte, así que un vídeo largo ya no congela el pase. Si un vídeo no está listo en su turno, pasa la siguiente foto y el vídeo entra cuando termine de bajar.
- Vídeos en la máxima calidad disponible; se conservan los de las últimas 10 fotos vistas (con tope de espacio) para poder volver atrás sin descargar de nuevo.
- **Importar álbumes en lote** desde la web de ajustes: un cuadro donde se pegan los enlaces, uno por línea, con progreso y resumen.

## 4.0
- **Explorador de álbumes en la tele**: mosaico de álbumes → mosaico de fotos → pantalla completa.
- Reproducir un álbum concreto desde el explorador (aleatorio, más antiguas o más recientes primero).
- Marca de vídeo sobre las miniaturas (retirada después en la 4.2).
- Caché de miniaturas con límite de tamaño (memoria y disco) y cola de descarga que descarta lo que ya no se ve, para que no se cuelgue ni llene la Shield.

## Antes de la 4.0
Primeras versiones, agrupadas por orden de aparición:

- **Salvapantallas** (DreamService) que lee álbumes compartidos de Google Fotos directamente desde la nube, sin descargarlos.
- Información en pantalla: nombre del álbum, fecha de captura (metadatos EXIF), lugar (GPS de la foto) y reloj.
- **Reproducción de vídeos**, con sonido opcional (silenciados por defecto). Detección de vídeos por el nombre del archivo y reproducción desde una copia temporal.
- **Varios álbumes**: añadir, quitar, renombrar, mostrar/ocultar, «marcar todos / desmarcar todos» y «quitar todos» con aviso. Elegir entre todos los álbumes o solo los marcados.
- Los álbumes ocultos se indican claramente en la tele (círculo vacío y «(oculto)»).
- **Configuración desde el móvil** mediante una página web local servida por la propia Shield; los álbumes se añaden al instante, sin pulsar «Guardar».
- **Orden de reproducción**: aleatorio (por defecto), aleatorio sin repetir, por fecha ascendente o descendente, y «seguir donde se quedó».
- Intervalos de cambio de foto desde 5 segundos hasta 10 minutos.
- **Paginación completa** de los álbumes: ya no se queda en las primeras 300 fotos.
- **Control con el mando**: ◀ ▶ cambian de foto, ▲ pausa y reanuda, ▼ oculta la foto para siempre; cualquier otra tecla sale del salvapantallas. Lista de fotos ocultas con opción de volver a mostrarlas.
- **Transiciones con fundido** entre fotos horizontales, verticales y vídeos, sin parpadeos ni fotogramas verdes.
- Botón seleccionado bien visible en la pantalla principal.
- Nombre **FotosTV**, nuevo logo y banner transparente para el lanzador (Projectivy).

# Historial de cambios

Todas las versiones de FotosTV, de la más reciente a la más antigua.
El formato sigue la idea de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## 5.17.3
- Con la barra azul, OK pausa/reanuda solo el vídeo (ya no toca el pase).
- Nuevo botón «Vídeos: calidad automática / alta / media». En Fire TV la automática usa calidad media (720p) y espera a tener unos segundos descargados antes de empezar.

## 5.17.2
- Vídeos en directo: ahora se ven desde un servidor local que lee lo ya descargado, así el búfer es todo lo bajado y se reducen los tirones.
- Los saltos de 5 s solo llegan hasta donde ya está descargado, para que no se bloquee.
- La parte pendiente de la barra de progreso es más transparente.

## 5.17.1
- Línea de tiempo del vídeo: blanca y más pegada al borde inferior.
- Con un vídeo en marcha desaparecen hora, fecha, álbum y demás textos.
- Controles del vídeo: ▼ pone la barra azul (OK pausa/continúa, ◀▶ ±5 s), ▲ vuelve a los controles normales, ▼ otra vez muestra la información.
- La miniatura del álbum «Ocultas» sale difuminada.

## 5.17.0
- Salvapantallas: los vídeos también se reproducen en directo con búfer mientras se guardan en la caché.
- Línea de tiempo al pie de los vídeos: tiempo transcurrido a la izquierda, tiempo restante a la derecha.

## 5.16.4
- Vídeos del visor: se reproducen en directo a máxima calidad con búfer (como YouTube) mientras se guardan en la caché; ya no hay salto de baja a alta calidad.
- Si el directo falla, se usa el vídeo guardado. Quitados los avisos de calidad.

## 5.16.3
- Esquinas de botones y recuadros menos redondeadas y recuadro blanco de los botones más fino.
- El control de columnas vuelve a la fila de botones; botones más compactos para que quepa todo.
- Vídeos del visor: la versión ligera se reproduce en directo (sin descargarla antes) mientras baja la de máxima calidad, y luego se cambia a ella por donde iba.

## 5.16.2
- Botones del álbum: menos espacio entre ellos y el control de columnas pasa a la cabecera, para que quepa todo.
- Panel de información más compacto: caben todos los datos (coordenadas y altitud en una sola línea).
- Al subir a los botones, el recuadro blanco de la foto desaparece.
- Vídeos del visor: la versión ligera se pide a la vez que la buena y arranca al instante; después pasa a la buena por donde iba.
- La caché de vídeos se conserva: solo se borra lo más antiguo cuando se supera el espacio configurado.

## 5.16.1
- Vídeos en el visor: si la alta calidad no llega en un par de segundos, empieza enseguida con una versión ligera y, cuando la buena está lista, cambia a ella por donde iba («Baja calidad por ahora; mejorando…» / «Calidad máxima»). Antes, si Google tardaba, caía en silencio a la calidad más baja.
- La alta calidad espera más (hasta 2 min) a que Google la prepare, y la baja calidad se guarda aparte, para que nunca se confunda con la buena en reproducciones posteriores.

## 5.16.0
- Dentro de un álbum, botón «Todo» / «Vídeos»: muestra solo los vídeos del álbum (se buscan por lotes y van apareciendo; lo ya detectado queda guardado).

## 5.15.0
- Información de la foto (▼): pantalla con los metadatos (archivo, fecha y hora, lugar, coordenadas, altitud, cámara, exposición, resolución) y, si tiene GPS, un mapa de OpenStreetMap con el punto exacto. Cualquier botón la cierra y el pase continúa por donde estaba.
- Nuevos gestos: ▲ oculta la foto (también en el visor), ▼ información, OK pausa / continúa en todos los casos.
- Mantener pulsado OK sobre una foto del mosaico: ocultar/mostrar, ver información, favoritas.
- Álbumes nuevos: «Favoritos» (con estrella, aparece al marcar la primera) y «Ocultas» (al final, con el ojo tachado, donde se pueden volver a mostrar). «Todas» lleva ahora un icono de álbumes. Las fotos ocultas ya no salen en los demás mosaicos.
- Un solo botón de orden (Aleatorio → Antiguas primero → Recientes primero), «Miniaturas» (cuadradas, apaisadas, completas) y nuevo «Pantalla» (automática, rellenar, original: el mismo ajuste «Encuadre» de la web); las columnas pasan al final.
- Fecha en pantalla algo más pequeña y sin cortarse nunca con «…».
- Logo: la «o» y la «s» un poco más separadas.

## 5.14.2
- Botones de la pantalla principal alineados a la izquierda, junto a su icono.
- Un solo botón «Antiguas» / «Recientes» que alterna al pulsarlo (junto a «Aleatorio»).
- Cabeceras de álbumes y de fotos exactamente iguales (misma altura, logo y título) con una barra gris vertical tras el logo.

## 5.14.1
- Dentro de un álbum, el nombre va en blanco y negrita (como «Álbumes»).

## 5.14.0
- Iconos en todos los botones de la app (y emojis en los de la web).
- Dentro de un álbum: cabecera del mismo tamaño que la de álbumes, el nombre en rosa, «Pase» el primer botón.
- Columnas: botones «−» y «+» con la etiqueta «Columnas: N» en medio (sin foco).
- Web: «yuskilabs» (centrado) enlaza con el léeme de GitHub.

## 5.13.0
- Explorador rediseñado: logo de la app a la izquierda en las cabeceras, «Álbumes» en negrita, sin textos de ayuda y con más espacio para las fotos.
- Mosaico de álbumes: botón «Columnas» (2–8); el nombre de cada álbum, más pequeño y pegado al número y al borde de abajo; la primera casilla se llama «Todas».
- Dentro de un álbum: nombre en negrita y color, botones más compactos, botón «Columnas» (3–12) y botón «Formato» de las miniaturas (cuadrada, apaisada o completa).
- Arreglo: un álbum con más de 300 fotos podía quedarse a veces con solo 300 en el mosaico (descarga recortada); ahora una descarga incompleta no sustituye a la lista completa.

## 5.12.3
- La web de configuración lleva el logo arriba y yuskilabs con su logo al final.
- La caché se muestra en el propio botón («Vaciar caché (103 MB)») y ya no ocupa sitio en la lista de álbumes.
- «Buscar actualización» pasa a llamarse «Actualizar».

## 5.12.2
- Web de configuración reordenada: primero los ajustes generales, y al final los álbumes (añadir uno, añadir varios, opciones y lista). Hay un botón «Guardar» también antes de los álbumes.

## 5.12.1
- La caché se muestra solo como total (sin el desglose de vídeos temporales, que siempre salía a 0).

## 5.12.0
- Pantalla principal rediseñada: logo completo y grande arriba; los botones ocupan la misma altura que la caja «Tus álbumes»; la tarjeta «Configúralo desde el móvil» es más pequeña.
- Lista de álbumes: línea en blanco tras el total, solo el número de elementos y el nombre en negrita y de color.
- «Comprobando álbumes…» parpadea suavemente con los colores del logo.
- yuskilabs pasa a la línea de la versión (sin reservar espacio abajo).

## 5.11.7
- Logo: letras más altas, menos achatadas.

## 5.11.6
- Logo: "Fotos" en Bold (algo más fino), con las letras juntas.

## 5.11.5
- Banner: margen lateral algo menor.

## 5.11.4
- Banner: el logo deja un margen a los lados.

## 5.11.3
- Logo: letras del nombre más juntas.

## 5.11.2
- Logo: la parte "Fotos" del nombre es algo más negrita.

## 5.11.1
- Nombre del logo con una letra negrita más ligera y la «o» de «Fotos» convertida en un botón de «play» con el degradado, a juego con el sol del icono.

## 5.11
- **Logo nuevo**: icono con foto apilada, pantalla de tele con montañas y un botón de «play» como sol, y el nombre en una letra mucho más gruesa (con «TV» en degradado). Cambia el icono, el banner del lanzador y el logo de la pantalla principal.

## 5.10.1
- La fecha (y el nombre del álbum y el lugar) ocupan siempre una sola línea y se vuelve a calcular su tamaño al cambiar el texto, para que no salga cortada («18 de diciembre de» sin el año) en las primeras fotos.

## 5.10
- **Ordenar los álbumes**: en la web de ajustes cada álbum tiene botones «↑ Subir» y «↓ Bajar» para ordenarlos a mano (se guarda con «Guardar»), y un nuevo selector **«Orden de los álbumes en el explorador de la tele»**: manual, o por la fecha de captura de la última foto de cada álbum (más reciente primero o más antiguo primero).

## 5.9.2
- Inicio automático: Android desactiva el servicio al actualizar la app. Si se concede una vez el permiso `WRITE_SECURE_SETTINGS` por ADB, la propia app vuelve a activarlo sola al abrirse (ver README).

## 5.9.1
- Pantalla principal: los siete botones de la izquierda caben ahora sin desplazarse (logo, título y botones algo más compactos).
- La lista «Tus álbumes» se puede recorrer con el mando: pulsa ▶ para entrar en ella (se marca con un recuadro azul) y ▲ ▼ para bajar. Con más de 3 álbumes muestra arriba el total de álbumes y elementos.

## 5.9
- **Arreglo de la pantalla negra con muchos álbumes** (probado el fallo con ~30 álbumes y ~30.000 fotos): la caché de todos los álbumes estaba guardada como un único texto gigante dentro de los ajustes y la pantalla principal lo releía entero una vez por cada álbum, bloqueándose. Ahora cada álbum tiene su propio archivo y la lista se prepara en segundo plano. La caché antigua se convierte sola la primera vez, sin perder álbumes ni ajustes.
- La página de configuración del móvil también carga mucho más rápido con muchos álbumes.

## 5.8.1
- Inicio automático más pulido: entra con un fundido suave y sale con otro, y comprueba la inactividad cada 5 segundos (antes 15), así que salta casi en el minuto exacto.

## 5.8
- **Inicio automático (experimental)**, pensado para la Fire TV con FireOS 8, donde Amazon no deja usar salvapantallas de terceros. Un servicio de accesibilidad detecta que no se usa el mando durante unos minutos (y que no hay nada sonando) y abre el pase de fotos. Se elige el tiempo con el nuevo botón «Inicio automático» de la pantalla principal. Hay que activarlo una vez por ADB (ver README).

## 5.7.2
- **Web de configuración más robusta**: atiende varias conexiones a la vez. Antes, una conexión vacía «de reserva» del navegador podía dejar la página cargando hasta agotar su tiempo (visto en un Fire TV Stick).

## 5.7.1
- Mosaicos: el recuadro de selección tiene ahora esquinas rectas, a juego con las miniaturas cuadradas (antes sus esquinas redondeadas sobresalían).

## 5.7
- **La dirección de la web de configuración ya no cambia**: se usa siempre el puerto 8080 (antes saltaba al 8081 si el anterior tardaba en liberarse). Solo usa otro si sigue ocupado tras varios intentos.
- **«Elegir salvapantallas»** abre directamente el ajuste de salvapantallas de Android TV (si el dispositivo no lo permite, abre el ajuste estándar de Android).

## 5.6.7
- El pin del lugar es ahora un dibujo propio, blanco y sencillo, que no depende de la fuente del dispositivo.

## 5.6.6
- El lugar de la foto se indica con un pin 📍 en vez del símbolo de GPS.

## 5.6.5
- Visor: la precarga vuelve a empezar al instante (sin la espera de la 5.6.4, que empeoraba el paso muy rápido). Se mantiene la subida anticipada a la GPU y la prioridad baja.

## 5.6.4
- Visor: quitado el pequeño tirón al pasar hacia delante. Las fotos precargadas se suben a la GPU por detrás, la precarga empieza un instante después de pintar la foto y trabaja con prioridad baja para no competir con la pantalla.

## 5.6.3
- Visor: antes de pasar, la app averigua de golpe cuáles de las siguientes fotos son vídeos. En mitad de un álbum, donde el mosaico no lo había comprobado, cada foto hacía su propia consulta y se notaba más lenta.

## 5.6.2
- Más precarga en el visor: hasta 27 fotos por delante y 3 por detrás, con 4 descargas a la vez. La memoria usada para esto se ajusta al dispositivo (entre 40 y 100 MB).

## 5.6.1
- **Paso manual más rápido**: se precargan por detrás hasta 12 fotos por delante y 2 por detrás (descarga en paralelo), y las 3 más cercanas ya decodificadas.
- Arreglo: el letrero «Cargando vídeo…» se quedaba en pantalla si se seguía pasando sin esperar; ahora desaparece al cambiar de foto.

## 5.6
- **Paso manual instantáneo en el visor**: al ir con ◀ ▶ por un álbum no hay fundido, y la app deja preparadas por detrás las siguientes fotos (y la anterior) para que aparezcan al momento. El pase de diapositivas conserva sus fundidos.

## 5.5.1
- Vídeos verticales: el ajuste al tamaño de la pantalla se hace redimensionando la vista del vídeo (centrada) en vez de con una transformación, para que los lados queden negros y no verdes.

## 5.5
- Tras actualizar desde «Buscar actualización», la app intenta **volver a abrirse sola**. Android puede impedirlo en algunos dispositivos; en ese caso hay que abrirla a mano.

## 5.4.3
- Pie de la pantalla principal: ahora solo el logo seguido de «yuskilabs» en minúsculas, sin «Hecho por».

## 5.4.2
- **Arreglo de «Buscar actualización»**: ya no se queda pidiendo el permiso de instalar aunque esté activado. Ahora se intenta instalar directamente y solo se abre el ajuste si Android bloquea la instalación.

## 5.4.1
- **Arreglo del fotograma verde al abrir un vídeo desde el mosaico**: el vídeo no se muestra hasta que se ha pintado un fotograma real, y si el primero sale verde se espera al siguiente.

## 5.4
- **Arreglo de la actualización desde la app**: ahora se instala con el instalador de paquetes de Android (`PackageInstaller`), sin pasar el fichero a otra app. En Android 11 el instalador no podía leerlo desde la carpeta de la app y fallaba con «Permission denied»; en algunos dispositivos lo abría un gestor de archivos.
- La primera vez que se pulsa «Buscar actualización» sin permiso para instalar, la app abre directamente el ajuste de Android «Permitir de esta fuente» y avisa de que hay que volver a pulsar.
- Muestra un aviso si la instalación falla, con el motivo.

## 5.3
- Logo de **Yuski Labs** en la pantalla principal («Hecho por Yuski Labs»).

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

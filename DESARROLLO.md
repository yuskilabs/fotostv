# Desarrollo de FotosTV

Notas para quien compile o publique versiones. La app y su uso se explican en el [léeme](README.md).

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

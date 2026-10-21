#!/usr/bin/env python3
"""Publica la APK compilada: la copia a dist/ y genera update.json (lo que lee el botón «Buscar actualización»).

Uso:  ./build.sh && python3 tools/publish.py
Las notas se toman de la sección de la versión en CHANGELOG.md.
"""
import json, os, re, shutil, sys

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
manifest = open(os.path.join(root, "AndroidManifest.xml"), encoding="utf-8").read()
version = re.search(r'versionName="([^"]+)"', manifest).group(1)

changelog = open(os.path.join(root, "CHANGELOG.md"), encoding="utf-8").read()
m = re.search(r"^## " + re.escape(version) + r"\s*\n(.*?)(?=^## |\Z)", changelog, re.S | re.M)
if not m:
    sys.exit("No hay sección «## %s» en CHANGELOG.md" % version)
notes = m.group(1).strip()
notes = re.sub(r"\*\*(.+?)\*\*", r"\1", notes)      # sin negritas
notes = re.sub(r"`(.+?)`", r"\1", notes)

apk = os.path.join(root, "FotosTV.apk")
if not os.path.exists(apk):
    sys.exit("Falta FotosTV.apk: ejecuta antes ./build.sh")
os.makedirs(os.path.join(root, "dist"), exist_ok=True)
shutil.copyfile(apk, os.path.join(root, "dist", "FotosTV.apk"))

info = {
    "version": version,
    "apk": "https://raw.githubusercontent.com/yuskilabs/fotostv/main/dist/FotosTV.apk",
    "size": os.path.getsize(apk),
    "notes": notes,
}
with open(os.path.join(root, "update.json"), "w", encoding="utf-8") as f:
    json.dump(info, f, ensure_ascii=False, indent=2)
    f.write("\n")
print("Publicada la versión", version, "(%d bytes)" % info["size"])

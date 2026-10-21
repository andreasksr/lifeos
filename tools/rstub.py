#!/usr/bin/env python3
"""Erzeugt eine R-Klasse (nur Namen, Werte 0) aus den Ressourcen eines Android-Moduls – für tools/typecheck.sh."""
import pathlib, re, sys

mod = pathlib.Path(sys.argv[1])
gradle = (mod / "build.gradle.kts").read_text(encoding="utf-8")
m = re.search(r'namespace\s*=\s*"([^"]+)"', gradle)
res = mod / "src/main/res"
if not m or not res.is_dir():
    sys.exit(0)
types = {}
for d in res.iterdir():
    if not d.is_dir():
        continue
    base = d.name.split("-")[0]
    if base == "values":
        for f in d.glob("*.xml"):
            for kind, name in re.findall(r'<(string|color|dimen|style|bool|integer|plurals|array|string-array)\s+name="([^"]+)"', f.read_text(encoding="utf-8")):
                kind = {"string-array": "array"}.get(kind, kind)
                types.setdefault(kind, set()).add(name.replace(".", "_"))
    else:
        for f in d.iterdir():
            types.setdefault(base, set()).add(f.name.split(".")[0])
print(f"package {m.group(1)}\n")
print("@Suppress(\"ClassName\", \"unused\")\nobject R {")
for kind, names in sorted(types.items()):
    print(f"    object {kind} {{")
    for n in sorted(names):
        print(f"        const val {n}: Int = 0")
    print("    }")
print("}")

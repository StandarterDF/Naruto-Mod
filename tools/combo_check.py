import re, pathlib, sys

p = pathlib.Path("src/main/java/com/sekwah/narutomod/abilities/NarutoAbilities.java")
text = p.read_text(encoding="utf-8")

# Find ability class blocks: COMBO = <number>L
pattern = re.compile(r"public static final long COMBO\s*=\s*(\d+)L;")
# But combos are in ability classes, not here. Instead scan all ability files.
import glob, os

combos = {}
for f in glob.glob("src/main/java/com/sekwah/narutomod/abilities/jutsus/*.java"):
    t = open(f, encoding="utf-8").read()
    m = re.search(r"public static final long COMBO\s*=\s*(\d+)L;", t)
    if m:
        combo = m.group(1)
        name = os.path.basename(f)
        combos.setdefault(combo, []).append(name)

dup = {k: v for k, v in combos.items() if len(v) > 1}
print("Total abilities with combo:", len(combos))
print("Duplicates:", len(dup))
for k, v in dup.items():
    print(k, v)
# also check 0 combos
print("Done")

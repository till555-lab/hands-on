import os
import re
import sys

BUNDLE = "bundle.txt"
if not os.path.exists(BUNDLE):
    print("no bundle")
    sys.exit(0)

text = open(BUNDLE, encoding="utf-8").read().replace("\r\n", "\n")
parts = re.split(r"^=== (FILE|DELETE): (.+?) ===\n", text, flags=re.M)

count = 0
for i in range(1, len(parts), 3):
    kind = parts[i]
    path = parts[i + 1].strip()
    body = parts[i + 2]
    if path.startswith("/") or ".." in path.split("/"):
        raise SystemExit("bad path: " + path)
    if kind == "DELETE":
        if os.path.exists(path):
            os.remove(path)
        count += 1
        continue
    folder = os.path.dirname(path)
    if folder:
        os.makedirs(folder, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(body.rstrip("\n") + "\n")
    count += 1

os.remove(BUNDLE)
print("applied", count, "files")

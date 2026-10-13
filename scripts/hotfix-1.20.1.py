"""Declare the existing Mixin refmap in the published 0.2.0 Forge/NeoForge JAR.

Requires Python 3.9+ only. The input hash pins this recovery to the audited
release; the original port's mixin configuration needs the same declaration.
"""

import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import tempfile
import zipfile

INPUT_SHA256 = "059c278dd269c4bc65025c3813192cb4990290511d318ad6041f63b17ba84fed"
VERSION = "0.2.0-hotfix.1"
CONFIG = "gamblingitems.client.mixins.json"
REFMAP = "gamblingitems.refmap.json"


def patch(source: Path, destination: Path) -> str:
    source = source.resolve()
    destination = destination.resolve()
    if source == destination:
        raise ValueError("The original release must be preserved; choose another output path.")
    if destination.exists():
        raise ValueError(f"Output already exists: {destination}")
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    if digest != INPUT_SHA256:
        raise ValueError(f"Unexpected input SHA-256: {digest}; expected {INPUT_SHA256}")

    with zipfile.ZipFile(source) as original:
        config = json.loads(original.read(CONFIG))
        if config.get("refmap"):
            raise ValueError("This input already declares a refmap.")
        refmap = json.loads(original.read(REFMAP))
        expected = {
            "dev/gamblingitems/fabric/client/mixin/MouseGrabMixin": {
                "grabMouse": "Lnet/minecraft/client/MouseHandler;m_91601_()V"
            },
            "dev/gamblingitems/fabric/client/mixin/MouseHandlerAccessor": {
                "xpos": "f_91507_:D", "ypos": "f_91508_:D"
            },
        }
        if refmap.get("mappings") != expected or refmap.get("data", {}).get("searge") != expected:
            raise ValueError("Unexpected embedded refmap; audit the new release first.")
        if config["client"] != ["MouseHandlerAccessor", "MouseGrabMixin"]:
            raise ValueError("Unexpected client mixins; audit the new release first.")
        metadata = original.read("META-INF/mods.toml").decode("utf-8")
        metadata, replacements = re.subn(
            r'(?m)^version = "0\.2\.0"$', f'version = "{VERSION}"', metadata
        )
        if replacements != 1:
            raise ValueError("Expected exactly one mod version declaration.")
        config["refmap"] = REFMAP
        replacements = {
            CONFIG: (json.dumps(config, indent=2) + "\n").encode(),
            "META-INF/mods.toml": metadata.encode(),
        }
        destination.parent.mkdir(parents=True, exist_ok=True)
        # A temporary output prevents leaving a partial JAR after a failed write.
        with tempfile.TemporaryDirectory(dir=destination.parent) as temporary:
            output = Path(temporary) / destination.name
            with zipfile.ZipFile(output, "w") as fixed:
                fixed.comment = original.comment
                for entry in original.infolist():
                    fixed.writestr(copy.copy(entry), replacements.get(entry.filename, original.read(entry)))
            with zipfile.ZipFile(output) as fixed:
                if fixed.testzip() is not None:
                    raise ValueError("Output ZIP integrity check failed.")
                # Every existing entry other than metadata/config must stay byte-identical.
                for entry in original.infolist():
                    if entry.filename not in replacements:
                        if fixed.read(entry.filename) != original.read(entry):
                            raise ValueError(f"Unexpected change to {entry.filename}")
            output.rename(destination)
    return hashlib.sha256(destination.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="Published 1.20.1 NeoForge 0.2.0 JAR (CurseForge 9085897)")
    parser.add_argument("output", type=Path, help="New JAR path, usually under build/hotfix/")
    args = parser.parse_args()
    try:
        digest = patch(args.input, args.output)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Hotfix failed: {error}\n")
    print(f"Created {args.output.resolve()}\nVersion: {VERSION}\nSHA-256: {digest}")


if __name__ == "__main__":
    main()

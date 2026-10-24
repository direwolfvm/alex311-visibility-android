#!/usr/bin/env python3
"""Export the pinned Gradle dependency graph as a reproducible CycloneDX SBOM."""
import argparse
import json
from pathlib import Path
from urllib.parse import quote


def components(lockfile):
    packages = set()
    for line in lockfile.splitlines():
        if not line or line.startswith("#") or line.startswith("empty="):
            continue
        coordinate = line.split("=", 1)[0].split(":")
        if len(coordinate) != 3:
            raise ValueError(f"Invalid Gradle lock entry: {line}")
        packages.add(tuple(coordinate))
    if not packages:
        raise ValueError("The lockfile must contain resolved dependencies")
    return [{"type": "library", "group": group, "name": name, "version": version,
             "purl": f"pkg:maven/{quote(group, safe='')}/{quote(name, safe='')}@{quote(version, safe='')}",
             "bom-ref": f"{group}:{name}:{version}"}
            for group, name, version in sorted(packages)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lockfile", type=Path, default=Path("app/gradle.lockfile"))
    parser.add_argument("--output", type=Path, default=Path("app/build/reports/sbom.cdx.json"))
    args = parser.parse_args()
    document = {"$schema": "https://cyclonedx.org/schema/bom-1.6.schema.json",
                "bomFormat": "CycloneDX", "specVersion": "1.6", "version": 1,
                "components": components(args.lockfile.read_text())}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2) + "\n")
    print(f"Wrote {len(document['components'])} locked dependencies to {args.output}")


if __name__ == "__main__":
    main()

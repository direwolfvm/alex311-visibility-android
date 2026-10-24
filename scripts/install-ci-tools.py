#!/usr/bin/env python3
"""Install pinned CI binaries after verifying their committed SHA-256 digests."""
import argparse
import hashlib
import io
import json
import platform
import tarfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def verified_binary(data, expected_digest, name, archive):
    if hashlib.sha256(data).hexdigest() != expected_digest:
        raise ValueError(f"Checksum mismatch for {name}; refusing to install")
    if not archive:
        return data
    # Read only the named binary; never extract archive paths into the filesystem.
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as package:
        member = package.getmember(name)
        if not member.isfile():
            raise ValueError(f"Expected a regular file for {name}")
        return package.extractfile(member).read()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tools", nargs="+", choices=["actionlint", "gitleaks", "osv-scanner"])
    parser.add_argument("--bin-dir", type=Path, default=ROOT / ".ci-tools")
    args = parser.parse_args()
    config = json.loads((ROOT / "config/ci-tools.json").read_text())
    machine = {"aarch64": "arm64", "amd64": "x86_64"}.get(platform.machine().lower(), platform.machine().lower())
    target = f"{platform.system().lower()}-{machine}"
    args.bin_dir.mkdir(parents=True, exist_ok=True)
    for name in args.tools:
        tool = config[name]
        if target not in tool["platforms"]:
            parser.error(f"No pinned {name} binary for {target}; use Linux/WSL or an official release")
        release = tool["platforms"][target]
        request = urllib.request.Request(release["url"], headers={"User-Agent": "Alex311-CI"})
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
        binary = verified_binary(data, release["sha256"], name, tool["archive"])
        destination = args.bin_dir / name
        destination.write_bytes(binary)
        destination.chmod(0o755)
        print(f"Installed {name} {tool['version']} (SHA-256 verified)")


if __name__ == "__main__":
    main()

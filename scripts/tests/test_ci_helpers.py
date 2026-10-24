"""Protect security-sensitive helper behavior without downloading tools or using a backend."""
import hashlib
import importlib.util
import io
import tarfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


installer = module("ci_tools", "install-ci-tools.py")
sbom = module("sbom", "dependency-sbom.py")


class InstallerTests(unittest.TestCase):
    def test_rejects_an_altered_binary_before_installation(self):
        with self.assertRaisesRegex(ValueError, "Checksum mismatch"):
            installer.verified_binary(b"tampered", hashlib.sha256(b"trusted").hexdigest(), "scanner", False)

    def test_rejects_a_symlink_in_a_verified_archive(self):
        data = io.BytesIO()
        with tarfile.open(fileobj=data, mode="w:gz") as archive:
            entry = tarfile.TarInfo("scanner")
            entry.type = tarfile.SYMTYPE
            entry.linkname = "/etc/passwd"
            archive.addfile(entry)
        raw = data.getvalue()
        with self.assertRaisesRegex(ValueError, "regular file"):
            installer.verified_binary(raw, hashlib.sha256(raw).hexdigest(), "scanner", True)

    def test_extracts_only_the_named_binary_never_archive_paths(self):
        data = io.BytesIO()
        with tarfile.open(fileobj=data, mode="w:gz") as archive:
            for path, content in [("scanner", b"trusted binary"), ("../../unexpected", b"do not extract")]:
                entry = tarfile.TarInfo(path)
                entry.size = len(content)
                archive.addfile(entry, io.BytesIO(content))
        raw = data.getvalue()
        self.assertEqual(b"trusted binary", installer.verified_binary(raw, hashlib.sha256(raw).hexdigest(), "scanner", True))


class SbomTests(unittest.TestCase):
    def test_preserves_transitives_and_separate_versions_without_duplicates(self):
        lock = "# comment\na.b:library:1.0=compile\na.b:library:1.0=runtime\na.b:library:2.0=test\nx.y:transitive:3.0=runtime\nempty=emptyConfig\n"
        result = sbom.components(lock)
        self.assertEqual(["a.b:library:1.0", "a.b:library:2.0", "x.y:transitive:3.0"], [x["bom-ref"] for x in result])
        self.assertEqual("pkg:maven/x.y/transitive@3.0", result[-1]["purl"])

    def test_rejects_an_empty_inventory(self):
        with self.assertRaises(ValueError):
            sbom.components("# no dependencies\nempty=configuration\n")

    def test_rejects_a_malformed_inventory(self):
        with self.assertRaises(ValueError):
            sbom.components("not-a-coordinate=runtime")


if __name__ == "__main__":
    unittest.main()

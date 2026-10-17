"""Synthetic fixtures for the version-independent stable gates; never presented as Minecraft measurements."""
import json, pathlib, tempfile, unittest
from stable_release_acceptance import GL_CHECKS, gl_checks, unit_tests, validate


class StableGates(unittest.TestCase):
    def gl(self, directory, **override):
        data = {key: 'pass' for key in GL_CHECKS} | {'glError': 0} | override
        (directory / 'gl-results.json').write_text(json.dumps(data), encoding='utf-8')

    def junit(self, build, tests=27, failures=0):
        folder = build / 'test-results/test'; folder.mkdir(parents=True)
        (folder / 'TEST-a.xml').write_text(f'<testsuite name="a" tests="{tests}" failures="{failures}" errors="0" skipped="0"/>', encoding='utf-8')

    def test_basic_checks_accept_any_version_directory(self):
        with tempfile.TemporaryDirectory() as t:
            root = pathlib.Path(t); evidence = root / 'validation/9.9.9'; evidence.mkdir(parents=True); self.gl(evidence); self.junit(root / 'build')
            self.assertEqual(gl_checks(evidence)['glError'], 0)
            self.assertEqual(unit_tests(root / 'build')['tests'], 27)

    def test_basic_checks_reject_failures_and_missing_files(self):
        with tempfile.TemporaryDirectory() as t:
            root = pathlib.Path(t); evidence = root / 'e'; evidence.mkdir()
            with self.assertRaises(ValueError): gl_checks(evidence)
            self.gl(evidence, computeVanillaAndIris54ByteOutput='fail')
            with self.assertRaises(ValueError): gl_checks(evidence)
            self.junit(root / 'build', failures=1)
            with self.assertRaises(ValueError): unit_tests(root / 'build')
            with self.assertRaises(ValueError): unit_tests(root / 'empty')

    def test_full_acceptance_requires_evidence_for_this_version(self):
        with tempfile.TemporaryDirectory() as t:
            evidence = pathlib.Path(t)
            with self.assertRaisesRegex(ValueError, 'No stable acceptance evidence'): validate(evidence, 'hash', '0.2.2')
            (evidence / 'summary.json').write_text(json.dumps({'jarSha256': 'hash', 'version': '0.2.1'}), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'does not identify'): validate(evidence, 'hash', '0.2.2')


if __name__ == '__main__':
    unittest.main()

import hashlib, json, pathlib, tempfile, unittest
from release_acceptance import validate, SCENARIOS

class ReleaseAcceptanceTest(unittest.TestCase):
    def fixture(self, root):
        def save(path, value):
            path.parent.mkdir(parents=True, exist_ok=True); path.write_text(json.dumps(value), encoding='utf-8')
        identity = {'candidate': 'candidate', 'baseline': 'baseline', 'driver': 'driver'}
        save(root / 'matrix.json', {'complete': True, 'performancePass': True, 'identity': identity})
        for scenario, counts in SCENARIOS.items():
            rows = []
            for count in counts:
                for repeat in (1, 2, 3):
                    for version in ('alpha.2', 'alpha.3'):
                        factor = 1.2 if version == 'alpha.3' else 1
                        path = root / scenario / f'{count}-{repeat}-{version}.gz'; path.parent.mkdir(exist_ok=True); path.write_bytes(b'fixture')
                        rows.append({'dragonPlayers': count, 'scenario': scenario, 'pairRepetition': repeat, 'versionLabel': version,
                                     'jarSha256': identity['candidate' if version == 'alpha.3' else 'baseline'], 'driverSha256': 'driver',
                                     'warmupSeconds': 60, 'samplingSeconds': 300,
                                     'metrics': {'capturedSeconds': 300, 'averageFps': 100 * factor, 'low1Fps': 50 * factor, 'low01Fps': 25 * factor, 'frameTimeMs': {'p95': 10 / factor, 'p99': 20 / factor}},
                                     'stats': {'totals': {'GPU_PASS': 100}, 'fallbacks': {}},
                                     'archivedRawFrames': path.relative_to(root).as_posix(), 'archivedRawSha256': hashlib.sha256(b'fixture').hexdigest()})
            save(root / scenario / 'runs.json', rows); save(root / scenario / 'summary.json', {'formal': True})
        save(root / 'visual-review.json', {'pass': True, 'candidateSha256': 'candidate', 'shaderPackCount': 8, 'modelPoseCases': 30})
        save(root / 'functional/lifecycle-stress/probe-result.json', {'pass': True, 'afterClearTextureBytes': 0, 'afterClearMeshBytes': 0})
        save(root / 'functional/lifecycle-stress/lifecycle-stress.json', [{'stage': stage, 'textureBytes': 0, 'meshAndPoseBytes': 0} for _ in range(4) for stage in ('connected', 'after-six-mode-switches', 'after-resource-reload', 'disconnected')])
        return save

    def test_complete_evidence_passes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); self.fixture(root)
            self.assertTrue(validate(root, 'candidate')['pass'])

    def test_duplicate_pair_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); save = self.fixture(root)
            rows = json.loads((root / 'fixed/runs.json').read_text()); rows[-1] = rows[0]
            save(root / 'fixed/runs.json', rows)
            with self.assertRaisesRegex(ValueError, 'duplicate'): validate(root, 'candidate')

    def test_unresolved_crash_blocks_release(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); save = self.fixture(root)
            save(root / 'incidents/incident.json', {'resolved': False})
            with self.assertRaisesRegex(ValueError, 'Unresolved'): validate(root, 'candidate')

    def test_disconnect_leak_blocks_release(self):
        with tempfile.TemporaryDirectory() as temp:
            root = pathlib.Path(temp); save = self.fixture(root)
            save(root / 'functional/lifecycle-stress/lifecycle-stress.json', [{'stage': 'disconnected', 'textureBytes': 1, 'meshAndPoseBytes': 0}] * 16)
            with self.assertRaisesRegex(ValueError, 'cleanup'): validate(root, 'candidate')

if __name__ == '__main__': unittest.main()

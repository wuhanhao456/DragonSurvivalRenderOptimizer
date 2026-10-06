"""Synthetic fixtures test acceptance gates, never presented as Minecraft measurements."""
import json, pathlib, subprocess, sys, tempfile, unittest

class BenchmarkAcceptance(unittest.TestCase):
    def records(self, players=1, repetition=1):
        return [dict(mode=mode, sceneDragonPlayers=players, repetition=repetition, warmupSeconds=60, samplingSeconds=300,
                     width=1280, height=720, renderDistance=6, fpsLimit=0, gpu='fixture', textureBytes=1024, meshBytes=1024,
                     stats=dict(frameSamples=100, frameTimeMs=dict(p50=4, p95=5, p99=6), fallbacks={},
                                totals={'CPU_VERTEX_SUBMIT_NANOS': 10_000_000 if mode != 'GPU' else 0,
                                        'GPU_BONE_SUBMIT_NANOS': 1_000_000 if mode == 'GPU' else 0, 'GPU_PASS': 100 if mode == 'GPU' else 0}))
                for mode in ('VANILLA', 'TEXTURES', 'GPU')]
    def summarize(self, directory):
        output = pathlib.Path(directory) / 'summary.json'
        run = subprocess.run([sys.executable, str(pathlib.Path(__file__).with_name('compare_benchmarks.py')), directory, '--output', str(output)], capture_output=True, text=True)
        return run, json.loads(output.read_text()) if output.exists() else None
    def write(self, directory, records, name='run'):
        target = pathlib.Path(directory) / name; target.mkdir(); (target / 'comparison.json').write_text(json.dumps(records))
    def test_full_matrix_and_missing_matrix_are_distinct(self):
        with tempfile.TemporaryDirectory() as root:
            self.write(root, self.records()); run, result = self.summarize(root)
            self.assertEqual(0, run.returncode); self.assertFalse(result['completeMatrix']); self.assertFalse(result['allCriteriaPass'])
            for p in (1, 4, 12):
                for r in (1, 2, 3):
                    if (p, r) != (1, 1): self.write(root, self.records(p, r), f'{p}-{r}')
            run, result = self.summarize(root); self.assertTrue(result['completeMatrix']); self.assertTrue(result['allCriteriaPass'])
    def test_cpu_only_fallback_cannot_pass_gpu_acceptance(self):
        with tempfile.TemporaryDirectory() as root:
            rows = self.records(); rows[2]['stats']['totals']['GPU_PASS'] = 0; self.write(root, rows)
            run, result = self.summarize(root); self.assertEqual(0, run.returncode); self.assertFalse(result['runs'][0]['criteriaPass'])
    def test_readback_or_sustained_generation_fails(self):
        for counter in ('READBACK', 'GENERATED'):
            with tempfile.TemporaryDirectory() as root:
                rows = self.records(); rows[1]['stats']['totals'][counter] = 1; self.write(root, rows)
                run, result = self.summarize(root); self.assertFalse(result['runs'][0]['criteriaPass'])
    def test_changed_resolution_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            rows = self.records(); rows[2]['width'] = 1920; self.write(root, rows)
            run, result = self.summarize(root); self.assertNotEqual(0, run.returncode); self.assertIsNone(result)

if __name__ == '__main__': unittest.main()

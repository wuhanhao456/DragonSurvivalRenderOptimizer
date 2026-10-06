import tempfile, pathlib, unittest
from low_metrics import metrics
class LowMetricsTest(unittest.TestCase):
    def write(self, values):
        p=pathlib.Path(self.tmp.name)/'frames.csv'; elapsed=0; lines=['frame,elapsed_ns,duration_ns']
        for i,v in enumerate(values): elapsed+=v; lines.append(f'{i},{elapsed},{v}')
        p.write_text('\n'.join(lines)); return p
    def setUp(self): self.tmp=tempfile.TemporaryDirectory()
    def tearDown(self): self.tmp.cleanup()
    def test_tail_mean(self):
        m=metrics(self.write([1_000_000]*998+[50_000_000,150_000_000]))
        self.assertAlmostEqual(m['low1Fps'],1000/20.8); self.assertEqual(m['frameTimeMs']['p99'],1)
        self.assertAlmostEqual(m['low01Fps'],1000/150)
    def test_invalid_interval(self):
        with self.assertRaises(ValueError): metrics(self.write([1,0]))
    def test_ceil_tail(self): self.assertEqual(metrics(self.write([1_000_000,10_000_000]))['low1Fps'],100)
if __name__=='__main__': unittest.main()

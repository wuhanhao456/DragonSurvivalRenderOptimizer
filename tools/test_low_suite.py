import unittest
from run_low_suite import acceptance

class PairedAcceptanceTest(unittest.TestCase):
    def row(self, version, repetition, factor):
        return {'scenario':'fixed','dragonPlayers':12,'versionLabel':version,'pairRepetition':repetition,
                'metrics':{'averageFps':100*factor,'low1Fps':50*factor,'low01Fps':25*factor,'frameTimeMs':{'p95':10/factor,'p99':20/factor}}}
    def test_empty_and_missing_pair_are_invalid(self):
        self.assertFalse(acceptance([])['complete'])
        self.assertFalse(acceptance([self.row('alpha.2',1,1)])['complete'])
    def test_median_of_paired_changes_not_ratio_of_medians(self):
        rows=[]
        for repetition,factor in enumerate([1.11,1.2,.9],1):
            rows += [self.row('alpha.2',repetition,1),self.row('alpha.3',repetition,factor)]
        result=acceptance(rows)
        self.assertTrue(result['pass'])
        self.assertAlmostEqual(result['groups'][0]['medianPairedChangePercent']['low1Fps'],11)
    def test_rejects_low_target_miss(self):
        rows=[self.row(version,repetition,1.05 if version=='alpha.3' else 1) for repetition in (1,2,3) for version in ('alpha.2','alpha.3')]
        self.assertFalse(acceptance(rows)['pass'])

if __name__=='__main__': unittest.main()

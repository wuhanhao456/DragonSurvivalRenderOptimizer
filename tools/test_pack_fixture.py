import unittest
from pack_fixture import in_range
class FixtureRangesTest(unittest.TestCase):
    def test_bounds(self):
        self.assertTrue(in_range('1.21.1','[1.21.1]'))
        self.assertFalse(in_range('1.21.1','[1.21, 1.21.1)'))
        self.assertTrue(in_range('1.21','[1.21, 1.21.1)'))
        self.assertTrue(in_range('21.1.248','*'))
        self.assertTrue(in_range('21.1.248','[21.0.143, )'))
        self.assertTrue(in_range('1.21.1','${minecraft_version_range}'))
        self.assertFalse(in_range('1.21.1','[1.20.1,1.20.2)'))
if __name__=='__main__': unittest.main()

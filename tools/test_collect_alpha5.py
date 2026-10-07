import json,pathlib,tempfile,unittest
from collect_alpha5 import archive_runtime

class RuntimeArchiveTest(unittest.TestCase):
    def test_only_test_records_are_exported(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory)/'instance';out=pathlib.Path(directory)/'out';root.mkdir();(root/'mods').mkdir()
            for name in ('optimizer.jar','validation.jar'):(root/'mods'/name).write_bytes(b'fixture')
            for name in ('npc-smoke-result.json','usercache.json','usernamecache.json','fixture-provenance.json','patchouli_data.json'):(root/name).write_text('{}')
            (root/'launch-args.txt').write_text('private');(root/'latest.log').write_text('private')
            archive_runtime(root,'own-instance',out)
            names={p.name for p in (out/'runtime/own-instance').iterdir()}
            self.assertEqual(names,{'npc-smoke-result.json','identity.json','actual-mods.json'})

if __name__=='__main__':unittest.main()

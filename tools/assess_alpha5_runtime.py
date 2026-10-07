"""Assess archived checks without rewriting any incomplete or failed raw run."""
import argparse,json,pathlib
PROJECT=pathlib.Path(__file__).resolve().parents[1]
FINAL='db1f8a395b2f8228420c0c0ecf78747de5731702d695973aefe7278c35763c09'
def read(path):return json.loads(path.read_text(encoding='utf-8'))
def assess(out,ds_actions):
    cases={'no-core':'dsbr-validation-plain-20261007-022940','absent':'dsbr-validation-plain-20261007-023051',
        'mismatch':'dsbr-validation-iris-20261007-023412','npc-visual':'dsbr-validation-iris-20261007-023558',
        'npc-lifecycle':'dsbr-validation-iris-20261007-024730','ds-actions':ds_actions}
    evidence={}
    for name,folder in cases.items():
        root=out/'runtime'/folder;identity=read(root/'identity.json');assert identity['jarSha256']==FINAL
        evidence[name]={'path':'runtime/'+folder,'identity':identity}
        if name=='npc-visual':
            rows=read(root/'npc-smoke-scenes.json');assert len(rows)==28
            assert len({r['shader'] for r in rows if r['case'].endswith('-gpu')})==9
            assert {r['case'] for r in rows}.issuperset({'action-'+a for a in ('idle','walk','run','fly','attack','dance')})
            assert not (root/'npc-smoke-result.json').exists() # Exit was incomplete, not relabeled.
            evidence[name].update(completedAssertionsAndScreenshots=28,wholeRunPassed=False,exitAssessedSeparately=True)
        else:
            file='probe-result.json' if name in ('no-core','absent') else 'alpha5-result.json' if name=='mismatch' else 'npc-smoke-result.json' if name=='npc-lifecycle' else 'single-round-result.json'
            result=read(root/file);assert result['pass'];evidence[name]['result']=result
    dsroot=out/evidence['ds-actions']['path'];rows=read(dsroot/'single-round-scenes.json')
    actions=[r for r in rows if str(r.get('scenario','')).startswith('animation-')];assert len(actions)==9
    for row in actions:
        expected=set(row['expectedSoulActors']);assert len(expected)==4
        actors={r['actor'] for r in row['samples']};assert actors.issuperset(expected|{'player'})
    before=read(out/'runtime/dsbr-validation-iris-20261007-024924/single-round-scenes.json')
    soul_rows=[r for r in before if str(r.get('scenario','')).startswith('souls-')];assert len(soul_rows)==6
    for row in soul_rows:
        total=row['stats']['totals'];assert total['SOUL_GPU_PASS']>0 and not total.get('READBACK',0)
        if row['souls']>1:assert total['SOUL_GPU_DRAW_CALLS']<total['SOUL_GPU_PASS']
    performance=read(out/'summary.json');assert performance['performanceGuardPass']
    assert performance['unitTests']=={'tests':23,'failures':0,'errors':0,'skipped':0}
    assert read(out/'optional-dependency-audit.json')['pass']
    gl=read(out/'gl-results.json');assert gl['glError']==0 and all(gl[k]=='pass' for k in ('gpuTextureCopyAndLazyReadback','computeVanillaAndIris54ByteOutput','threeIndependentAnimatedInstances','batchBindingsRestoredAfterException'))
    report={'version':'0.2.0-alpha.5','finalJarSha256':FINAL,'shortReleaseChecksPass':True,'formalAcceptanceComplete':False,
        'runtimeEvidence':evidence,'soulBatchingChecks':{'path':'runtime/dsbr-validation-iris-20261007-024924','completedScenes':6,'originalWholeRunPassed':False},
        'manualVisualReview':{'npcCpuGpuPairs':9,'packs':'disabled, BSL, Bliss, Reimagined, Unbound, MakeUp, Sildur, Solas, Photon','observation':'No missing/exploded model or obvious pose sharing in inspected screenshots. Animations continue between CPU/GPU snapshots; not a pixel-equality test.'},
        'toolTests':{'tests':16,'failures':0},'reloadWarning':'reload-warning.json',
        'limitations':['Original NPC visual run stopped at a driver logout bug; 28 completed assertions are retained, cleanup proved separately.',
            'Earlier DS action driver failures and all replacements remain in INVALID_RUNS.md; no raw pass flag was rewritten.',
            'The mixed mode/resource reload statistics can contain explicit lazy pixel reads; steady GPU paired samples and no-core preview samples have zero readback.',
            'Full Curios/held-item/barrel-roll/first-person/skill-particle combinations and every translucent material have not been exhaustively tested.',
            'Dedicated server startup is not part of this client-only validation; client absence and optional dependency cases were actually launched.',
            'One short performance pair per scene, not original-server A/B or formal 3 x 300 s Low acceptance.']}
    (out/'runtime-summary.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    return report
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--out',type=pathlib.Path,default=PROJECT/'validation/alpha5');p.add_argument('--ds-actions-instance',required=True);a=p.parse_args()
    print(json.dumps({'shortReleaseChecksPass':assess(a.out,a.ds_actions_instance)['shortReleaseChecksPass']}))

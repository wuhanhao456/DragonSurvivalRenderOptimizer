"""Compare a renamed jar to the single-run jar without executing another runtime test."""
import argparse, hashlib, json, struct, zipfile
from pathlib import Path

BRANDING_CLASSES = {
    'top/wu949/dsbr/DragonSurvivalBedrockRenderer.class',
    'top/wu949/dsbr/client/DSBRClient.class',
    'top/wu949/dsbr/optimizer/RenderOptimizer.class',
}
BRANDING_RESOURCES = {
    'META-INF/neoforge.mods.toml', 'pack.mcmeta',
    'assets/dsbr/lang/en_us.json', 'assets/dsbr/lang/zh_cn.json',
}

def normalize_text(data):
    return data.replace(b'Dragon Survival Render Optimizer', b'Dragon Survival Bedrock Renderer').replace(b'DSRO', b'DSBR')

def normalize_class(data):
    if data[:4] != b'\xca\xfe\xba\xbe': raise ValueError('Invalid class file')
    count = struct.unpack_from('>H', data, 8)[0]
    output = bytearray(data[:10]); offset = 10; index = 1
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[offset]; offset += 1; output.append(tag)
        if tag == 1:
            size = struct.unpack_from('>H', data, offset)[0]; offset += 2
            value = normalize_text(data[offset:offset + size]); offset += size
            output.extend(struct.pack('>H', len(value))); output.extend(value)
        else:
            size = sizes[tag]; output.extend(data[offset:offset + size]); offset += size
            if tag in (5, 6): index += 1
        index += 1
    output.extend(data[offset:])
    return bytes(output)

def compare(tested, renamed):
    changed = []
    with zipfile.ZipFile(tested) as before, zipfile.ZipFile(renamed) as after:
        names = {n for n in before.namelist() if not n.endswith('/')}
        if names != {n for n in after.namelist() if not n.endswith('/')}:
            raise ValueError('Jar members changed beyond branding')
        for name in sorted(names):
            a, b = before.read(name), after.read(name)
            if a == b: continue
            if name in BRANDING_CLASSES:
                if normalize_class(a) != normalize_class(b): raise ValueError('Non-branding bytecode change: ' + name)
                kind = 'UTF-8 constant names only; all method code and attributes identical'
            elif name in BRANDING_RESOURCES:
                if normalize_text(a).replace(b'\r\n', b'\n') != normalize_text(b).replace(b'\r\n', b'\n'):
                    raise ValueError('Non-branding resource change: ' + name)
                kind = 'Display names only; line endings ignored'
            else: raise ValueError('Unexpected artifact change: ' + name)
            changed.append({'entry': name, 'difference': kind})
    digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
    return {'pass': True, 'testedJarSha256': digest(tested), 'releaseJarSha256': digest(renamed),
            'unchangedEntries': len(names) - len(changed), 'changedEntries': changed,
            'verificationMethod': 'Jar content and class constant comparison', 'modId': 'dsbr', 'newName': 'Dragon Survival Render Optimizer'}

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--tested', type=Path, required=True); p.add_argument('--renamed', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args(); result = compare(args.tested, args.renamed)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))

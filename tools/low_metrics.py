"""Exact Low metrics from complete display-loop intervals; no percentile-to-FPS shortcut."""
import csv, math, statistics

def metrics(path):
    with open(path, newline='') as f:
        rows = list(csv.DictReader(f))
    if not rows or [int(r['frame']) for r in rows] != list(range(len(rows))):
        raise ValueError('Incomplete frame sequence')
    frames = [int(r['duration_ns']) / 1e6 for r in rows]
    if any(not math.isfinite(f) or f <= 0 for f in frames): raise ValueError('Invalid frame interval')
    if int(rows[-1]['elapsed_ns']) != sum(int(r['duration_ns']) for r in rows): raise ValueError('Invalid timeline')
    ordered = sorted(frames)
    quantile = lambda q: ordered[max(0, math.ceil(len(ordered)*q)-1)]
    tail = lambda q: 1000 / statistics.mean(ordered[-max(1, math.ceil(len(ordered)*q)):])
    return {'samples':len(frames), 'capturedSeconds':sum(frames)/1000, 'averageFps':1000/statistics.mean(frames),
            'low1Fps':tail(.01), 'low01Fps':tail(.001), 'frameTimeMs':{f'p{label}':quantile(q) for label,q in [(50,.5),(95,.95),(99,.99),(999,.999)]},
            'longFramesAboveMs':{str(t):sum(f>t for f in frames) for t in (16.7,33.3,50)},
            'maximumFrameMs':max(frames), 'definition':'1000 / mean slowest ceil(samples * fraction) frame times in ms'}

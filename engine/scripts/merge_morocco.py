"""Merge Western Sahara (ESH) into Morocco (MAR) in data/world.json: one shape, no inner border.

    py -3 engine/scripts/merge_morocco.py

Rule from stickman-studio: any map of Morocco includes the Sahara.
Both shapes come from the same Natural Earth file, so the shared border uses identical integer
vertices. Every edge used by both shapes (in opposite directions) is dropped, then the remaining
edges are chained back into closed rings. No shapely needed. Safe to run twice (no ESH -> no-op).
"""
import json
from collections import defaultdict
from pathlib import Path

D = Path(__file__).resolve().parent.parent / 'data'
W = D / 'world.json'
data = json.loads(W.read_text(encoding='utf-8'))
cs = data['countries']
mar = next(c for c in cs if c['iso'] == 'MAR')
esh = next((c for c in cs if c['iso'] == 'ESH'), None)
if esh is None:
    print('ESH already merged'); raise SystemExit


def pts(r):
    p = [(r[i], r[i + 1]) for i in range(0, len(r), 2)]
    if p[0] == p[-1]: p = p[:-1]
    return p


edges = []
for c in (mar, esh):
    for r in c['r']:
        p = pts(r)
        edges += [(p[i], p[(i + 1) % len(p)]) for i in range(len(p))]
es = set(edges)
keep = [e for e in edges if (e[1], e[0]) not in es]
print('edges', len(edges), 'shared dropped', len(edges) - len(keep))

nxt = defaultdict(list)
for a, b in keep: nxt[a].append(b)
rings = []
while True:
    start = next((a for a in nxt if nxt[a]), None)
    if start is None: break
    ring = [start]; cur = nxt[start].pop()
    while cur != start:
        ring.append(cur); cur = nxt[cur].pop()
    flat = [v for q in ring for v in q]; flat += ring[0]
    if len(ring) >= 3: rings.append(flat)

xs = [r[i] for r in rings for i in range(0, len(r), 2)]; ys = [r[i] for r in rings for i in range(1, len(r), 2)]
mar['r'] = rings
mar['bb'] = [min(xs) / 1e4, min(ys) / 1e4, max(xs) / 1e4, max(ys) / 1e4]
data['countries'] = [c for c in cs if c is not esh]
data['note'] = 'MAR includes Western Sahara (merged by merge_morocco.py)'
W.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
print('MAR rings', len(rings), 'bb', mar['bb'], '->', W)

#!/usr/bin/env python3
import json, sys

if len(sys.argv) > 1:
    text = open(sys.argv[1], 'r', encoding='utf-8').read()
else:
    text = sys.stdin.read()

items = json.loads(text)
chosen = {}
order = []

def priority(x):
    domain = x.get('domain', '')
    if x.get('hostOnly') and domain == 'www.zhixue.com': return 3
    if domain == 'www.zhixue.com': return 2
    if domain.endswith('zhixue.com'): return 1
    return 0

for x in items:
    name = str(x.get('name', '')).strip()
    if not name or 'value' not in x:
        continue
    if name not in chosen:
        order.append(name)
        chosen[name] = x
    elif priority(x) >= priority(chosen[name]):
        chosen[name] = x

print('; '.join(f"{name}={chosen[name].get('value','')}" for name in order))

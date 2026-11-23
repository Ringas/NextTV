#!/usr/bin/env python3
"""Read-only resource reachability audit; qualified variants share an Android resource ID.
Inspect results alongside dynamic/reflection usage; absence alone is not permission to delete.
"""
from pathlib import Path
import json,re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1];main=root/'app/src/main';res=main/'res'
def refs(s):return set(re.findall(r'@(?:\+)?(?!android:)([A-Za-z_]+/[A-Za-z0-9_.]+)',s))
nodes={};files={}
for p in res.rglob('*'):
 if not p.is_file():continue
 typ=p.parent.name.split('-')[0]
 if typ=='values':
  for e in ET.parse(p).getroot():
   name=e.get('name')
   if not name:continue
   t=e.get('type')if e.tag=='item'else 'array'if e.tag in ['string-array','integer-array']else e.tag
   key=t+'/'+name;deps=refs(ET.tostring(e,encoding='unicode'))
   if e.tag=='style'and e.get('parent')and not e.get('parent').startswith(('android:','@android:')):deps.add('style/'+e.get('parent').removeprefix('@style/'))
   nodes.setdefault(key,set()).update(deps);files.setdefault(key,[]).append(str(p.relative_to(root)))
 else:
  key=typ+'/'+p.stem;files.setdefault(key,[]).append(str(p.relative_to(root)));nodes.setdefault(key,set()).update(refs(p.read_text())if p.suffix=='.xml'else set())
code='\n'.join(p.read_text()for p in (main/'java').rglob('*')if p.suffix in ['.kt','.java'])
roots={t+'/'+n for t,n in re.findall(r'(?<!android\.)\bR\.(\w+)\.(\w+)',code)}|refs((main/'AndroidManifest.xml').read_text())
live=set();todo=list(roots)
while todo:
 k=todo.pop()
 if k in live:continue
 live.add(k);todo+=list(nodes.get(k,set())-live)
print(json.dumps(dict(main_kotlin_files=len(list((main/'java').rglob('*.kt'))),resource_files=sum(p.is_file()for p in res.rglob('*')),unreachable_resources={k:files[k]for k in sorted(set(nodes)-live)},dynamic_lookup_present='getIdentifier('in code,roots=sorted(roots)),ensure_ascii=False,indent=2))

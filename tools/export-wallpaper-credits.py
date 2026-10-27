"""Export photographer names/links only; never publish API keys or photo descriptions."""
from pathlib import Path
import argparse,json,datetime
p=argparse.ArgumentParser();p.add_argument('--data',type=Path,default=Path(__file__).resolve().parent.parent/'wallpaper-metadata');args=p.parse_args()
repo=Path(__file__).resolve().parent.parent
rows=json.loads((args.data/'inventory.json').read_text(encoding='utf-8'))
def safe(s):return str(s or '').replace('|','/').replace('\n',' ').replace('[','').replace(']','')
def url(s):return s if isinstance(s,str) and s.startswith('https://unsplash.com/') and not any(c in s for c in '() <>\n\r') else ''
lines=['# Wallpaper photographer credits','','This is a metadata-only credit list for the owner\'s separately downloaded collection.','No photographs or photographer descriptions are distributed here. Photographer copyright','and the [Unsplash license](https://unsplash.com/license) remain separate from the code license.','Names come from verified API records; pending names are deliberately not inferred from filenames.','','Regenerate using `python tools/export-wallpaper-credits.py --data PATH_TO_LOCAL_METADATA`.','','| Photo | Photographer |','|---|---|']
verified=0
for row in sorted(rows,key=lambda x:x['id']):
 id=row['id'];cache=args.data/'cache'/(id+'.json');m=json.loads(cache.read_text(encoding='utf-8')) if cache.exists() else {}
 name=safe(m.get('photographer')) or 'Metadata pending';profile=url(m.get('photographer_url'));photo=url(m.get('photo_url')) or 'https://unsplash.com/photos/'+id
 if m.get('photographer'):verified+=1
 author='['+name+']('+profile+')' if profile else name
 lines.append('| ['+safe(id)+']('+photo+') | '+author+' |')
lines+=['',str(verified)+' of '+str(len(rows))+' photographer names verified at export. Missing metadata can be added without changing the images.','']
(repo/'docs/WALLPAPER_CREDITS.md').write_text('\n'.join(lines),encoding='utf-8')
print('Exported',len(rows),'photo credits;',verified,'verified photographer names.')

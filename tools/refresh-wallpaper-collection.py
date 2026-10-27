import os
"""Stage a corrected collection at launcher resolution. Never modify source crops."""
from pathlib import Path
from PIL import Image,ImageOps
import argparse,json,re,hashlib,shutil
parser=argparse.ArgumentParser();parser.add_argument('collection',choices=['nature_cropped','landscapes_cropped']);args=parser.parse_args()
root=Path(__file__).resolve().parent.parent;data=root/'wallpaper-metadata';source=Path(os.environ.get('HOME_WALLPAPER_SOURCE_DIR',str(Path.home()/'Downloads')))/args.collection
stage=data/('refresh-'+args.collection);stage.mkdir(exist_ok=True)
inventory=json.loads((data/'inventory.json').read_text(encoding='utf-8'));prepared=json.loads((data/'prepared.json').read_text(encoding='utf-8'))
updates=[];new_inventory=[]
for index,file in enumerate(sorted(source.glob('*.jpg'))):
 match=re.search(r'-([A-Za-z0-9_-]{11})-unsplash(?:_crop)?\.',file.name)
 if not match:raise ValueError('Cannot identify photo: '+file.name)
 id=match.group(1);webp=stage/(id+'-webp90.webp')
 with Image.open(file) as raw:
  image=ImageOps.exif_transpose(raw).convert('RGB');w,h=image.size
  if abs((w/h)/(16/9)-1)>0.001:raise ValueError('Crop is not 16:9: '+file.name)
  if image.size!=(1920,1080):image=image.resize((1920,1080),Image.Resampling.LANCZOS)
  image.save(webp,'WEBP',quality=90,method=6)
 variants={'webp90':{'file':webp.name,'bytes':webp.stat().st_size,'sha256':hashlib.sha256(webp.read_bytes()).hexdigest()}}
 updates.append({'id':id,'collection':args.collection,'source_filename':file.name,'variants':variants})
 new_inventory.append({'collection':args.collection,'filename':file.name,'bytes':file.stat().st_size,'id':id})
 if (index+1)%25==0:print('Prepared',index+1,'corrected photos',flush=True)
# Keep other collections intact even while their source crops are being edited.
plan={'collection':args.collection,'stage':str(stage),'updates':updates,
 'inventory':[x for x in inventory if x['collection']!=args.collection]+new_inventory,
 'prepared':[x for x in prepared if x['collection']!=args.collection]+updates}
(stage/'refresh-plan.json').write_text(json.dumps(plan,indent=2),encoding='utf-8')
print(json.dumps({'photos':len(updates),'webp90_MiB':round(sum(x['variants']['webp90']['bytes'] for x in updates)/1048576,1)}),flush=True)

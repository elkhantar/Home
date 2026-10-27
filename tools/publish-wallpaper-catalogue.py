import os
"""Publish signed metadata independently of images; no TV UI input or app restart."""
from pathlib import Path
import json,subprocess,sys
ROOT=Path(__file__).resolve().parent.parent
DATA=ROOT/'wallpaper-metadata';DEVICE=DATA/'device';REPO=ROOT
prepared=json.loads((DATA/'prepared.json').read_text(encoding='utf-8'))
photos=[]
for item in prepared:
 path=DATA/'cache'/(item['id']+'.json')
 meta=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
 caption=meta.get('photographer_caption')
 if not caption and meta.get('description_source')=='photographer_description':caption=meta.get('description')
 photos.append({'id':item['id'],'collection':item['collection'],'variants':{'webp90':item['variants']['webp90']},
  'photographer':meta.get('photographer') or '', 'photographer_caption':caption or '',
  'date_published':meta.get('date_published') or '', 'location':meta.get('location') or ''})
manifest=DEVICE/'catalogue.json';manifest.write_text(json.dumps({'schema_version':1,'photos':photos},ensure_ascii=False,separators=(',',':')),encoding='utf-8')
jdk=next((ROOT.parent/'.toolchain/jdk').glob('jdk-17*'));classes=DATA/'signer';classes.mkdir(exist_ok=True)
helper=REPO/'tools/SignWallpaperCatalogue.java';compiled=classes/'SignWallpaperCatalogue.class'
if not compiled.exists() or helper.stat().st_mtime>compiled.stat().st_mtime:
 subprocess.run([str(jdk/'bin/javac.exe'),'-d',str(classes),str(helper)],check=True)
subprocess.run([str(jdk/'bin/java.exe'),'-cp',str(classes),'SignWallpaperCatalogue',str(REPO/'local/personal.jks'),str(manifest),str(DEVICE/'catalogue.sig')],check=True)
if '--push' in sys.argv:
 adb=os.environ.get('ADB','adb');target=os.environ['TV_HOST'];remote='/sdcard/Android/data/com.privs.tvhome/files/wallpapers'
 status=subprocess.run([adb,'-s',target,'get-state'],capture_output=True,text=True)
 if status.returncode!=0 or status.stdout.strip()!='device':
  print('Signed metadata ready locally; TV unavailable.');sys.exit(2)
 for name in ['catalogue.json','catalogue.sig']:
  subprocess.run([adb,'-s',target,'push',str(DEVICE/name),remote+'/'+name+'.tmp'],check=True)
 for name in ['catalogue.json','catalogue.sig']:
  subprocess.run([adb,'-s',target,'shell','mv',remote+'/'+name+'.tmp',remote+'/'+name],check=True)
 print('Signed metadata updated on TV. No screen input sent.')
else:print('Signed metadata generated:',len(photos),'photos')

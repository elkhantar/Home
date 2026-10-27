import os
"""Deploy one staged collection, verifying bytes; never send TV navigation inputs."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,subprocess,uuid
parser=argparse.ArgumentParser();parser.add_argument('collection',choices=['nature_cropped','landscapes_cropped']);args=parser.parse_args()
root=Path(__file__).resolve().parent.parent;data=root/'wallpaper-metadata';stage=data/('refresh-'+args.collection)
plan=json.loads((stage/'refresh-plan.json').read_text(encoding='utf-8'))
assert plan['collection']==args.collection and Path(plan['stage']).resolve()==stage.resolve()
files={}
for item in plan['updates']:
 for v in [item['variants']['webp90']]:
  name=v['file'];assert re.fullmatch(r'[A-Za-z0-9_-]{11}-(original\.jpg|webp90\.webp)',name)
  local=stage/name;assert local.parent.resolve()==stage.resolve()
  if hashlib.sha256(local.read_bytes()).hexdigest()!=v['sha256']:raise ValueError('Local hash mismatch')
  files[name]=v
adb=os.environ.get('ADB','adb');tv=os.environ['TV_HOST'];base='/sdcard/Android/data/com.privs.tvhome/files'
def run(*args,capture=False):
 return subprocess.run([adb,'-s',tv,*args],check=True,capture_output=capture,text=True,encoding='utf-8',errors='replace')
payload=sum(v['bytes'] for v in files.values())
upload=stage/('upload-'+uuid.uuid4().hex);upload.mkdir()
for name in files:
 target=upload/name
 if target.exists():target.unlink()
 os.link(stage/name,target)
marker=stage/'remote-stage.txt'
if marker.exists():
 remote=marker.read_text().strip()
 if not re.fullmatch(re.escape(base)+r'/wallpaper-refresh-[a-f0-9]{32}',remote):raise RuntimeError('Invalid staging location')
else:
 free=int(run('shell','df','-k','/data',capture=True).stdout.splitlines()[-1].split()[3])*1024
 if free-payload<128*1048576:raise RuntimeError('Insufficient staging space with 128 MiB reserve')
 remote=base+'/wallpaper-refresh-'+uuid.uuid4().hex
 marker.write_text(remote)
 run('push',str(upload),remote)
def shell_script(script,capture=False):
 return subprocess.run([adb,'-s',tv,'shell','sh'],input=script+'\n',check=True,capture_output=capture,text=True,encoding='utf-8',errors='replace')
script='set -e\n'+'\n'.join('mv '+remote+'/'+name+' '+base+'/wallpapers/'+name for name in files)
shell_script(script)
checks=shell_script('\n'.join('sha256sum '+base+'/wallpapers/'+name for name in files),capture=True).stdout
actual={Path(line.split()[-1]).name:line.split()[0] for line in checks.splitlines() if line.strip()}
for name,v in files.items():
 if actual.get(name)!=v['sha256']:raise RuntimeError('Uploaded image hash mismatch: '+name)
print('Byte-verified',len(files),'uploaded images',flush=True)
run('shell','rmdir',remote)
marker.unlink()
for name in files:shutil.copyfile(stage/name,data/'device'/name)
for filename,value in [('prepared.json',plan['prepared']),('inventory.json',plan['inventory'])]:
 temp=data/(filename+'.tmp');temp.write_text(json.dumps(value,indent=2),encoding='utf-8');temp.replace(data/filename)
subprocess.run(['node','tools/fetch-unsplash-metadata.mjs','--catalogue-only'],cwd=root,check=True)
subprocess.run(['python','tools/publish-wallpaper-catalogue.py','--push'],cwd=root,check=True)
print('Updated',len(plan['updates']),'photos from',args.collection,'without changing other collections.',flush=True)

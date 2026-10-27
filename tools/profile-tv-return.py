import subprocess,time,json,sys
from pathlib import Path
adb=['adb','-s','192.168.1.40:5555'];label=sys.argv[1]
def run(*args):return subprocess.check_output(adb+list(args),text=True,encoding='utf-8',errors='replace')
results=[]
for i in range(2):
 run('shell','am','start','-W','-n','com.netflix.ninja/.MainActivity');time.sleep(2)
 run('shell','dumpsys','gfxinfo','com.privs.tvhome','reset')
 results.append(run('shell','am','start','-W','-n','com.privs.tvhome/com.gathin.tvhome.MainActivity'));time.sleep(3)
 Path(f'local/performance/{label}-return-{i+1}.txt').write_text(run('shell','dumpsys','gfxinfo','com.privs.tvhome'),encoding='utf-8')
Path(f'local/performance/{label}-returns.json').write_text(json.dumps(results,indent=2))
print(json.dumps(results))

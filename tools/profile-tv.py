"""Repeatable on-TV navigation sample; intentionally controls the screen."""
import subprocess,time,json,re,sys
from pathlib import Path
adb=['adb','-s','192.168.1.40:5555']
def run(*args): return subprocess.check_output(adb+list(args),text=True,encoding='utf-8',errors='replace')
label=sys.argv[1]; out=Path('local/performance');out.mkdir(exist_ok=True)
run('shell','input','keyevent','3')
run('shell','uiautomator','dump','/sdcard/personal-perf-wake.xml')
run('shell','am','force-stop','com.privs.tvhome')
start=run('shell','am','start','-W','-n','com.privs.tvhome/com.gathin.tvhome.MainActivity')
time.sleep(3)
keys=[22]*5+[21]*5+[20,22,22,21,21,20,19,19]
script='\n'.join('input keyevent '+str(k)+'\nsleep 0.65' for k in keys)
# Same navigation warmup, then two measured samples.
run('shell',script)
results=[]
for i in range(2):
 run('shell','input','keyevent','4');time.sleep(1)
 run('shell','dumpsys','gfxinfo','com.privs.tvhome','reset')
 run('shell',script);time.sleep(1)
 stats=run('shell','dumpsys','gfxinfo','com.privs.tvhome')
 (out/f'{label}-navigation-{i+1}.txt').write_text(stats,encoding='utf-8')
 results.append({k:re.search(p,stats).group(1) if re.search(p,stats) else None for k,p in {
 'frames':r'Total frames rendered: (\d+)', 'jank':r'Janky frames: ([^\n]+)',
 'p50_ms':r'50th percentile: (\d+)ms','p90_ms':r'90th percentile: (\d+)ms',
 'p95_ms':r'95th percentile: (\d+)ms','p99_ms':r'99th percentile: (\d+)ms'}.items()})
 print(json.dumps({'sample':i+1,**results[-1]}),flush=True)
mem=run('shell','dumpsys','meminfo','com.privs.tvhome');(out/f'{label}-memory.txt').write_text(mem,encoding='utf-8')
(out/f'{label}.json').write_text(json.dumps({'launch':start,'navigation':results},indent=2))

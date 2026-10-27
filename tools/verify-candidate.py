"""Verify the exact selected APK locally; artifact-only mode never rewrites approval metadata."""
from pathlib import Path
import argparse,hashlib,io,json,re,subprocess,zipfile
import xml.etree.ElementTree as E
root=Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser()
parser.add_argument('--apk',type=Path,default=root/'launcher/app/build/outputs/apk/release/app-release.apk')
parser.add_argument('--expected-sha256')
parser.add_argument('--artifact-only',action='store_true')
parser.add_argument('--allow-debug',action='store_true')
parser.add_argument('--output',type=Path)
args=parser.parse_args()
apk=args.apk.resolve()
def require(condition,message):
    if not condition: raise ValueError(message)
def output(*command): return subprocess.check_output([str(c) for c in command],text=True,encoding='utf-8')
data=apk.read_bytes()
digest=hashlib.sha256(data).hexdigest()
if args.expected_sha256:
    require(re.fullmatch('[a-fA-F0-9]{64}',args.expected_sha256),'Invalid approved SHA-256')
    require(digest==args.expected_sha256.lower(),'APK does not match the approved SHA-256')
aapt=root.parent/'.toolchain/sdk/build-tools/34.0.0/aapt.exe'
permissions=output(aapt,'dump','permissions',apk)
allowed={'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','android.permission.ACCESS_WIFI_STATE','android.permission.REQUEST_DELETE_PACKAGES','android.permission.READ_TV_LISTINGS'}
actual=set(re.findall(r"uses-permission: name='([^']+)'",permissions))
require(actual==allowed,'Unexpected permissions: '+str(actual))
badging=output(aapt,'dump','badging',apk)
identity=re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",badging)
require(identity is not None and identity[1]=='com.privs.tvhome','Unexpected package identity')
manifest=output(aapt,'dump','xmltree',apk,'AndroidManifest.xml')
require('E: service ' not in manifest,'Unexpected service')
debuggable=bool(re.search(r'android:debuggable.*0xffffffff',manifest))
require(args.allow_debug or not debuggable,'Debug APK requires explicit --allow-debug')
with zipfile.ZipFile(io.BytesIO(data)) as z:
    require(not any(n.lower().endswith(('.jks','.keystore','google-oauth.json')) for n in z.namelist()),'Private build material packaged')
    for name in z.namelist():
        if name.endswith('.dex'):
            code=z.read(name).lower()
            require(b'api.aladhan.com' not in code,'Removed network dependency remains')
            require(b'oauth2.googleapis.com' not in code,'Unexpected OAuth code')
java=next((root.parent/'.toolchain/jdk').glob('jdk-17*/bin/java.exe'))
signer=output(java,'-jar',aapt.parent/'lib/apksigner.jar','verify','--print-certs',apk)
certificate=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',signer).group(1)
require(certificate=='c69121bc8d2716bf3c35318798f201c05c877de415da1dca9f279c228972bdd6','Unexpected signing key')
require(hashlib.sha256(apk.read_bytes()).hexdigest()==digest,'APK changed during verification')
variant='Debug' if debuggable else 'Release'
report={'package':identity[1],'version_code':identity[2],'version_name':identity[3],
        'apk':str(apk),'bytes':len(data),'sha256':digest,'permissions':sorted(actual),'debuggable':debuggable,
        'services':0,'signature_verified':True,'signer_sha256':certificate,'device_accessed':False}
if not args.artifact_only:
    results=[]
    for path in (root/f'launcher/app/build/test-results/test{variant}UnitTest').glob('TEST-*.xml'):
        attrs=E.parse(path).getroot().attrib
        results.append({k:attrs[k] for k in ('name','tests','failures','errors','skipped')})
    require(results and all(int(r['failures'])==0 and int(r['errors'])==0 for r in results),'Tests missing or failed')
    issues=E.parse(root/f'launcher/app/build/reports/lint-results-{variant.lower()}.xml').getroot().findall('issue')
    require(not any(i.attrib.get('severity') in ('Error','Fatal') for i in issues),'Lint errors remain')
    report.update(test_count=sum(int(r['tests']) for r in results),tests=results,lint_errors=0,lint_warnings=len(issues))
    target=args.output or root/('local/candidate-debug.json' if debuggable else 'local/candidate.json')
    target.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,indent=2))

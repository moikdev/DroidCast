#!/usr/bin/env python3
from pathlib import Path
import subprocess,json,hashlib,re,shutil,os,argparse
from android_test_cleanup import Cleanup
p=argparse.ArgumentParser(description='Verify modifier-tap configuration and local dispatch on an explicitly owned emulator')
p.add_argument('--serial',required=True);p.add_argument('--avd',required=True)
p.add_argument('--layout',choices=('tablet','phone'),required=True);p.add_argument('--output',type=Path,required=True)
p.add_argument('--classes',default='dev.tinycast.android.ModifierTapDeviceTest,dev.tinycast.android.ModifierTapPaletteDeviceTest')
p.add_argument('--expected',type=int,default=10)
a=p.parse_args()
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:sdk=next(line.removeprefix('sdk.dir=') for line in Path('local.properties').read_text().splitlines() if line.startswith('sdk.dir='))
adb=[str(Path(sdk)/'platform-tools/adb'),'-s',a.serial];pkg='dev.tinycast.android'
def call(*a,data=None):
 for attempt in range(3):
  r=subprocess.run(adb+list(a),input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
  if r.returncode==0:return r.stdout
  if attempt==2 or not any(x in r.stderr.lower() for x in (b'offline',b'closed',b'not found')):raise subprocess.CalledProcessError(r.returncode,r.args,r.stdout,r.stderr)
  subprocess.run(adb+['wait-for-device'],check=True,timeout=15)
def text(*a):return call(*a).decode().strip()
def sha(data):return hashlib.sha256(data).hexdigest()
layout,output,classes=a.layout,a.output,a.classes
assert a.serial.startswith('emulator-')
assert a.expected>0
assert re.fullmatch('[A-Za-z0-9_.,#]+',classes)
assert text('shell','getprop','ro.boot.qemu.avd_name')==a.avd
assert text('shell','id').startswith('uid=2000')
assert 'ActiveInstrumentation{' not in text('shell','dumpsys','activity','processes')
assert 'Override' not in text('shell','wm','size')
assert 'Override' not in text('shell','wm','density')
root=Path(output);root.mkdir(exist_ok=False)
keys=['assistant','voice_interaction_service','voice_recognition_service','navigation_mode','assist_touch_gesture_enabled',
 'assist_long_press_home_enabled','search_all_entrypoints_enabled','enabled_accessibility_services','accessibility_enabled','show_ime_with_hard_keyboard']
settings={k:text('shell','settings','get','secure',k) for k in keys}
assert settings['enabled_accessibility_services']=='null' and settings['accessibility_enabled']=='0'
roles=text('shell','cmd','role','get-role-holders','android.app.role.ASSISTANT').splitlines()
overlays=re.findall(r'^\[x\] (com\.android\.internal\.systemui\.navbar\.\w+)$',text('shell','cmd','overlay','list'),re.M);assert len(overlays)==1
call('shell','am','force-stop',pkg)
prefs=call('exec-out','run-as',pkg,'cat','shared_prefs/palette.xml')
(root/'private-palette-backup.xml').write_bytes(prefs);(root/'private-palette-backup.xml').chmod(0o600)
record={'classes':classes,'layout':layout,'serial':a.serial,'avd':a.avd,'expected':a.expected,'completed':False,
 'runner_sha256':sha(Path(__file__).read_bytes()),'original_size':text('shell','wm','size'),'original_density':text('shell','wm','density')}
try:
 for name in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
  p=Path(name);shutil.copy2(p,root/p.name);call('install','-r',name)
 record['apk_sha256']=sha((root/'app-debug.apk').read_bytes());record['test_apk_sha256']=sha((root/'app-debug-androidTest.apk').read_bytes())
 installed=text('shell','pm','path',pkg).removeprefix('package:');assert sha(call('exec-out','cat',installed))==record['apk_sha256']
 record['installed_matches']=True
 if layout=='phone':call('shell','wm','size','1080x2340');call('shell','wm','density','420')
 call('shell','run-as',pkg,'rm','-rf','files/modifier-tap-evidence')
 call('shell','run-as',pkg,'mkdir','-p','files/modifier-tap-evidence')
 with (root/'tests.log').open('wb') as log:
  subprocess.run(adb+['shell','am','instrument','-w','-r','-e','dedicatedAvd',a.avd,'-e','class',classes,pkg+'.test/androidx.test.runner.AndroidJUnitRunner'],stdout=log,stderr=subprocess.STDOUT,check=True)
 for name in text('shell','run-as',pkg,'ls','files/modifier-tap-evidence').splitlines():
  assert re.fullmatch(r'[a-z0-9-]+\.(xml|png|json|txt)',name)
  raw=call('exec-out','run-as',pkg,'cat','files/modifier-tap-evidence/'+name)
  assert len(raw)==int(text('shell','run-as',pkg,'stat','-c','%s','files/modifier-tap-evidence/'+name))
  (root/name).write_bytes(raw)
 log=(root/'tests.log').read_text();print(log[-3500:],flush=True)
 record['completed']=f'OK ({a.expected} test' in log and 'FAILURES!!!' not in log
 record['pass_statuses']=len(re.findall(r'^INSTRUMENTATION_STATUS_CODE: 0$',log,re.M))
 record['fail_statuses']=len(re.findall(r'^INSTRUMENTATION_STATUS_CODE: -2$',log,re.M))
 assert record['completed'] and record['pass_statuses']==a.expected and record['fail_statuses']==0
finally:
 cleanup=Cleanup(record)
 cleanup.attempt('stop-app',lambda:call('shell','am','force-stop',pkg))
 def restore_roles():
  current=text('shell','cmd','role','get-role-holders','android.app.role.ASSISTANT').splitlines()
  for i,holder in enumerate(current):
   if holder not in roles:cleanup.attempt('remove-role-'+str(i),lambda h=holder:call('shell','cmd','role','remove-role-holder','android.app.role.ASSISTANT',h))
  for i,holder in enumerate(roles):
   if holder not in current:cleanup.attempt('add-role-'+str(i),lambda h=holder:call('shell','cmd','role','add-role-holder','android.app.role.ASSISTANT',h))
 cleanup.attempt('roles',restore_roles)
 cleanup.attempt('navigation-overlay',lambda:call('shell','cmd','overlay','enable-exclusive','--category',overlays[0]))
 for k,v in settings.items():
  cleanup.attempt('secure-'+k,lambda k=k,v=v:call('shell','settings','delete','secure',k) if v=='null' else call('shell','settings','put','secure',k,v))
 cleanup.attempt('preference-backup-removal',lambda:call('shell','run-as',pkg,'rm','-f','shared_prefs/palette.xml.bak'))
 cleanup.attempt('preferences',lambda:call('shell','run-as',pkg,'tee','shared_prefs/palette.xml',data=prefs))
 if layout=='phone':
  cleanup.attempt('viewport-size',lambda:call('shell','wm','size','reset'))
  cleanup.attempt('viewport-density',lambda:call('shell','wm','density','reset'))
 def verify(name,actual,expected):
  value=actual();record[name]=value==expected
  cleanup.require(record[name],name)
 record.update(settings_restored=False,roles_restored=False,palette_preferences_restored=False,navigation_overlay_restored=False)
 cleanup.attempt('verify-settings',lambda:verify('settings_restored',lambda:{k:text('shell','settings','get','secure',k) for k in keys},settings))
 cleanup.attempt('verify-roles',lambda:verify('roles_restored',lambda:text('shell','cmd','role','get-role-holders','android.app.role.ASSISTANT').splitlines(),roles))
 cleanup.attempt('verify-preferences',lambda:verify('palette_preferences_restored',lambda:call('exec-out','run-as',pkg,'cat','shared_prefs/palette.xml'),prefs))
 cleanup.attempt('verify-overlay',lambda:verify('navigation_overlay_restored',lambda:re.findall(r'^\[x\] (com\.android\.internal\.systemui\.navbar\.\w+)$',text('shell','cmd','overlay','list'),re.M),overlays))
 def metadata(name,action):record[name]=action()
 for name,action in [('size_restored',lambda:text('shell','wm','size')),('density_restored',lambda:text('shell','wm','density')),('adbd',lambda:text('shell','id').split(' groups=')[0]),('app_uid',lambda:text('shell','run-as',pkg,'id').split(' groups=')[0]),('api',lambda:text('shell','getprop','ro.build.version.sdk')),('abi',lambda:text('shell','getprop','ro.product.cpu.abi'))]:
  cleanup.attempt('read-'+name,lambda n=name,f=action:metadata(n,f))
 cleanup.attempt('verify-geometry',lambda:cleanup.require(record.get('size_restored')==record['original_size'] and record.get('density_restored')==record['original_density'],'geometry'))
 cleanup.attempt('verify-adbd',lambda:cleanup.require(record.get('adbd','').startswith('uid=2000'),'adbd'))
 record['tests_completed']=record['completed'];record['completed']=record['completed'] and not cleanup.errors
 (root/'record.json').write_text(json.dumps(record,indent=2)+'\n')
 cleanup.finish()

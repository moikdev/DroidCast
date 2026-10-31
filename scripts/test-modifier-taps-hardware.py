#!/usr/bin/env python3
"""Guarded Linux input verification. Root is restricted to an explicitly named owned emulator."""
from pathlib import Path
import argparse,subprocess,hashlib,json,re,time,xml.etree.ElementTree as E,os,struct,shutil
p=argparse.ArgumentParser()
p.add_argument('--serial',required=True);p.add_argument('--avd',required=True);p.add_argument('--output',type=Path,required=True)
p.add_argument('--allow-adbd-root',action='store_true');p.add_argument('--apk',type=Path,default=Path('app/build/outputs/apk/debug/app-debug.apk'))
p.add_argument('--test-apk',type=Path,default=Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
a=p.parse_args();assert a.allow_adbd_root and a.serial.startswith('emulator-')
sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or next(x[8:] for x in Path('local.properties').read_text().splitlines() if x.startswith('sdk.dir='))
adb=[str(Path(sdk)/'platform-tools/adb'),'-s',a.serial];APP='dev.tinycast.android';SERVICE=APP+'/'+APP+'.PaletteShortcutService'
def raw(*args,data=None):return subprocess.check_output(adb+list(args),input=data,stderr=subprocess.STDOUT)
def text(*args):return raw(*args).decode().strip()
def wait(test,message,timeout=12):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  if test():return
  time.sleep(.1)
 raise AssertionError(message)
def focus():return next((x for x in text('shell','dumpsys','window').splitlines() if 'mCurrentFocus=' in x),'')
def bound():return any('Bound services:' in x and 'Service[label=Droidcast' in x for x in text('shell','dumpsys','accessibility').splitlines())
def ready():
 wait(bound,'Keyboard service not bound');wait(lambda:'InputFilterEnabled: true' in text('shell','dumpsys','input'),'Input filter unavailable');time.sleep(.2)
def tree():
 raw('shell','uiautomator','dump','/sdcard/droidcast-modifier-hardware.xml')
 return raw('exec-out','cat','/sdcard/droidcast-modifier-hardware.xml')
def snapshot(name,palette=True,query=None):
 data=tree();nodes=list(E.fromstring(data).iter('node'))
 if palette:assert APP in focus() and any(n.get('class')=='android.widget.EditText' and n.get('package')==APP for n in nodes)
 if query is not None:assert any(n.get('class')=='android.widget.EditText' and n.get('text')==query for n in nodes)
 (a.output/(name+'.xml')).write_bytes(data);(a.output/(name+'.png')).write_bytes(raw('exec-out','screencap','-p'))
def set_setting(key,value):raw('shell','settings','delete','secure',key) if value=='null' else raw('shell','settings','put','secure',key,value)
assert text('shell','getprop','ro.kernel.qemu')=='1'
assert text('shell','getprop','ro.boot.qemu.avd_name')==a.avd
assert text('shell','getprop','ro.product.cpu.abi')=='arm64-v8a'
assert text('shell','id').startswith('uid=2000')
assert 'ActiveInstrumentation{' not in text('shell','dumpsys','activity','processes')
assert 'Override' not in text('shell','wm','size')+text('shell','wm','density')
a.output.mkdir(exist_ok=False,parents=True)
shutil.copyfile(Path(__file__),a.output/'runner.py')
keys=['enabled_accessibility_services','accessibility_enabled','show_ime_with_hard_keyboard']
settings={k:text('shell','settings','get','secure',k) for k in keys}
assert settings['enabled_accessibility_services']=='null' and settings['accessibility_enabled']=='0'
raw('shell','am','force-stop',APP)
prefs=raw('exec-out','run-as',APP,'cat','shared_prefs/palette.xml')
(a.output/'private-palette-backup.xml').write_bytes(prefs);(a.output/'private-palette-backup.xml').chmod(0o600)
record={'avd':a.avd,'serial':a.serial,'cases':[],'capabilities':[],'completed':False,'root_for_injection_only':True,'runner_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
rooted=False;keyboard=None;clipboard_saved=False
LINUX=[29,97,56,100,42,54,125,126,30,46,47,51,1]
def emit(events):
 # One binary stream avoids a fork/ADB round trip for every SYN event.
 # Linux arm64 input_event: two 64-bit timeval fields, type/code/value.
 process=subprocess.Popen(adb+['shell','-T',f'cat > {keyboard}'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 try:
  for code,value,delay in events:
   process.stdin.write(struct.pack('<qqHHi',0,0,1,int(code),int(value))+struct.pack('<qqHHi',0,0,0,0,0));process.stdin.flush()
   if delay:time.sleep(delay)
  process.stdin.close();process.stdin=None
  stdout,stderr=process.communicate(timeout=15)
  assert process.returncode==0,(stdout,stderr)
 finally:
  if process.poll() is None:process.kill();process.communicate()
def double(key,hold=.04,gap=.08):
 ready();emit([(key,1,hold),(key,0,gap),(key,1,hold),(key,0,0)])
def press(key):emit([(key,1,.03),(key,0,0)])
def chord(key):
 ready();emit([(29,1,.02),(key,1,.03),(key,0,.02),(29,0,0)])
def open_main():
 raw('shell','am','start','-W','-n',APP+'/.MainActivity');wait(lambda:APP in focus(),'MainActivity not focused')
def destination(settings_page=False):
 if settings_page:raw('shell','am','start','-W','-a','android.settings.SETTINGS','-f','0x14000000')
 else:raw('shell','input','keyevent','KEYCODE_HOME')
 wait(lambda:APP not in focus(),'Palette did not leave foreground');ready()
def config(family=113,side='ANY',command=False):
 bindings={'global:palette':{'keyCode':family,'modifiers':0,'trigger':'DOUBLE_TAP','side':side}}
 if command:
  bindings['global:palette']['side']='LEFT'
  bindings['new-note']={'keyCode':113,'modifiers':0,'trigger':'DOUBLE_TAP','side':'RIGHT'}
 value=json.dumps({'version':2,'bindings':bindings},separators=(',',':'))
 doc=E.fromstring(prefs);entry=next((e for e in doc if e.get('name')=='shortcuts'),None)
 if entry is None:entry=E.SubElement(doc,'string',{'name':'shortcuts'})
 entry.text=value
 set_setting('enabled_accessibility_services','null');set_setting('accessibility_enabled','0')
 raw('shell','am','force-stop',APP);raw('shell','run-as',APP,'rm','-f','shared_prefs/palette.xml.bak')
 raw('shell','run-as',APP,'tee','shared_prefs/palette.xml',data=E.tostring(doc,encoding='utf-8',xml_declaration=True))
 open_main();set_setting('enabled_accessibility_services',SERVICE);set_setting('accessibility_enabled','1');ready()
def passed(name):
 record['cases'].append({'name':name,'passed':True});print('PASS '+name,flush=True)
 (a.output/'results.json').write_text(json.dumps(record,indent=2)+'\n')
def bridge(mode):
 output=raw('shell','am','instrument','-w','-r','-e','dedicatedAvd',a.avd,'-e','clipboardMode',mode,'-e','class',APP+'.ModifierHardwareClipboardTest',APP+'.test/androidx.test.runner.AndroidJUnitRunner')
 (a.output/('clipboard-'+mode+'.log')).write_bytes(output)
 assert b'OK (1 test)' in output and b'FAILURES!!!' not in output
try:
 for file in [a.apk,a.test_apk]:
  shutil.copyfile(file,a.output/file.name);raw('install','-r',str(file))
 record['apk_sha256']=hashlib.sha256(a.apk.read_bytes()).hexdigest();record['test_apk_sha256']=hashlib.sha256(a.test_apk.read_bytes()).hexdigest()
 installed=text('shell','pm','path',APP).removeprefix('package:');assert hashlib.sha256(raw('exec-out','cat',installed)).hexdigest()==record['apk_sha256']
 record['installed_matches']=True
 bridge('save');clipboard_saved=True
 rooted=True;raw('root');raw('wait-for-device');assert text('shell','id').startswith('uid=0')
 set_setting('enabled_accessibility_services',SERVICE);set_setting('accessibility_enabled','1');open_main();ready()
 capabilities=text('shell','getevent','-pl');(a.output/'input-capabilities.txt').write_text(capabilities)
 match=re.search(r'add device \d+: (/dev/input/event\d+)\s+name:\s+"qwerty2"',capabilities);assert match
 keyboard=match.group(1)
 record['app_uid']=text('shell','run-as',APP,'id').split(' groups=')[0];assert not record['app_uid'].startswith(('uid=0(','uid=1000(','uid=2000('))
 config();destination();double(29);wait(lambda:APP in focus(),'Left double Ctrl did not open palette')
 # Keep this ownership/toggle pair free of UiAutomation, whose hierarchy
 # client can temporarily suppress/rebind the keyboard service.
 (a.output/'global-home-left.png').write_bytes(raw('exec-out','screencap','-p'))
 record['first_launch_focus']=focus()
 passed('Double left Ctrl opens from Home')
 ready();double(29);wait(lambda:APP not in focus(),'Double Ctrl did not close palette');time.sleep(.7);assert APP not in focus();passed('Connected service and foreground do not dispatch twice')
 destination(True);double(29);wait(lambda:APP in focus(),'Double Ctrl did not open from Settings');snapshot('global-settings');passed('Double Ctrl opens from Android Settings')
 destination();double(97);wait(lambda:APP in focus(),'Either-side binding did not accept right Ctrl');snapshot('global-home-right');passed('Either-side binding accepts right Ctrl')
 config(side='RIGHT');destination();double(29);time.sleep(.4);assert APP not in focus()
 double(97);wait(lambda:APP in focus(),'Right-only binding did not open');snapshot('right-only-activated');passed('Right-only ignores left pair and accepts right pair')
 config();raw('shell','input','text','linuxcopy731492');ready();chord(30);chord(46)
 raw('shell','input','text','replacement');snapshot('ordinary-replaced',query='replacement')
 ready();chord(30);chord(47);time.sleep(.2)
 snapshot('ordinary-copy-paste',query='linuxcopy731492');passed('Linux Ctrl+A/C/V pass through while double Ctrl is configured')
 destination();ready();emit([(29,1,.4),(29,0,.08),(29,1,.04),(29,0,0)]);time.sleep(.4);assert APP not in focus()
 double(29);wait(lambda:APP in focus(),'Valid pair after hold did not open');passed('Held Ctrl does not activate and next valid pair recovers')
 destination(True);ready();emit([(29,1,.02),(46,1,.03),(46,0,.02),(29,0,.08),(29,1,.04),(29,0,0)]);time.sleep(.4);assert APP not in focus()
 double(29);wait(lambda:APP in focus(),'Valid pair after intervening key did not open');passed('Intervening Ctrl+C cancels tap and next valid pair recovers')
 config(command=True);destination();double(97);wait(lambda:APP in focus(),'Double Right Ctrl command did not open')
 data=tree();assert b'Create a note' in data and b'Give it a name' in data
 snapshot('right-ctrl-note-command');passed('Double Right Ctrl runs the normal new-note command from Home')
 config();chord(51);wait(lambda:'SettingsActivity' in focus(),'Ctrl+comma did not open Settings')
 for attempt in range(6):
  nodes=list(E.fromstring(tree()).iter('node'))
  change=next((n for n in nodes if n.get('text')=='Change' and n.get('package')==APP),None)
  if change is not None:break
  raw('shell','input','swipe','1500','900','1500','400','300')
 assert change is not None,'Global shortcut Change control not visible'
 bounds=list(map(int,re.findall(r'\d+',change.get('bounds'))));raw('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2))
 assert b'Record a shortcut' in tree();ready();double(29);time.sleep(.4)
 assert 'SettingsActivity' in focus(),'Global tap escaped the active recorder'
 snapshot('recording-pauses-global',palette=False)
 paused=(a.output/'recording-pauses-global.xml').read_bytes()
 assert b'Record a shortcut' in paused and b'Double Ctrl' in paused
 ready();press(1);double(29);wait(lambda:'MainActivity' in focus(),'Global tap did not resume after recorder cancellation')
 passed('Keyboard service pauses double Ctrl during recording and resumes after Escape')
 for linux,key,name in [(29,113,'Left Ctrl'),(97,113,'Right Ctrl'),(56,57,'Left Alt'),(100,57,'Right Alt'),(42,59,'Left Shift'),(54,59,'Right Shift'),(125,117,'Left Meta'),(126,117,'Right Meta')]:
  config(family=key);destination(True);double(linux)
  end=time.monotonic()+3
  while APP not in focus() and time.monotonic()<end:time.sleep(.1)
  activated=APP in focus();record['capabilities'].append({'modifier':name,'linux_code':linux,'activated':activated,'focus_after':focus()})
  snapshot('capability-'+name.lower().replace(' ','-'),palette=activated)
  print('CAPABILITY '+name+': '+str(activated),flush=True)
 record['completed']=True
except BaseException:
 (a.output/'failure.png').write_bytes(raw('exec-out','screencap','-p'))
 (a.output/'failure-window.txt').write_text(text('shell','dumpsys','window'))
 raise
finally:
 try:
  if rooted and keyboard:emit([(code,0,0) for code in LINUX])
  set_setting('enabled_accessibility_services','null');set_setting('accessibility_enabled','0')
  if clipboard_saved:bridge('restore');record['clipboard_restored']=True
  raw('shell','am','force-stop',APP);raw('shell','run-as',APP,'rm','-f','shared_prefs/palette.xml.bak')
  raw('shell','run-as',APP,'tee','shared_prefs/palette.xml',data=prefs)
  for k,v in settings.items():set_setting(k,v)
  record['preferences_restored']=prefs==raw('exec-out','run-as',APP,'cat','shared_prefs/palette.xml');assert record['preferences_restored']
  record['settings_restored']=settings=={k:text('shell','settings','get','secure',k) for k in keys};assert record['settings_restored']
 finally:
  if rooted:
   subprocess.run(adb+['unroot'],capture_output=True);raw('wait-for-device')
  record['adbd_restored']=text('shell','id').startswith('uid=2000');assert record['adbd_restored']
  record['size']=text('shell','wm','size');record['density']=text('shell','wm','density')
  (a.output/'results.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({'completed':record['completed'],'cases':len(record['cases']),'capabilities':record['capabilities'],'restored':record['adbd_restored']}))

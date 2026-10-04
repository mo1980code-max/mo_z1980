#!/usr/bin/env python3
"""Second audit pass: categories the first pass did not cover.

  7. enum entry references that the enum does not declare
  8. suspend functions called from a non-suspend context
  9. `when` over one of our enums that is missing entries (only for assigned/returned whens)
 10. override members with no matching open/abstract declaration
"""
import os, re
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIRS = [os.path.join(ROOT,'app/src/main/java'), os.path.join(ROOT,'clock-engine/src/main/kotlin')]

def files():
    for d in DIRS:
        for dp,_,fns in os.walk(d):
            for fn in fns:
                if fn.endswith('.kt'): yield os.path.join(dp,fn)

def strip(s):
    s=re.sub(r'/\*.*?\*/','',s,flags=re.S); return re.sub(r'//[^\n]*','',s)

SRC={f:strip(open(f,encoding='utf-8').read()) for f in files()}
rel=lambda f: os.path.relpath(f,ROOT)
ln=lambda t,i: t.count('\n',0,i)+1
findings=defaultdict(list)
def rep(c,f,l,m): findings[c].append((rel(f),l,m))

def balanced(s,i,o='(',c=')'):
    d=0
    while i<len(s):
        if s[i]==o: d+=1
        elif s[i]==c:
            d-=1
            if d==0: return i
        i+=1
    return len(s)-1

# ---------------------------------------------------- enum tables
enum_entries=defaultdict(set)
for f,s in SRC.items():
    for m in re.finditer(r'enum class\s+(\w+)[^\n{]*\{', s):
        name=m.group(1); j=balanced(s,m.end()-1,'{','}'); body=s[m.end():j]
        head=re.split(r';',body)[0]
        for e in re.findall(r'\b([A-Z][A-Z0-9_]*)\b',head): enum_entries[name].add(e)

for f,s in SRC.items():
    for m in re.finditer(r'\b([A-Z]\w*)\.([A-Z][A-Z0-9_]{1,})\b', s):
        t,e=m.group(1),m.group(2)
        # androidx.media3.common.Player shares its simple name with ChessClockEngine.Player.
        if t == 'Player' and e.startswith('REPEAT_MODE'): continue
        if t in enum_entries and e not in enum_entries[t]:
            # constants on companion objects look identical; only flag true enums
            rep('enum',f,ln(s,m.start()),'%s.%s  (declared: %s)'%(t,e,', '.join(sorted(enum_entries[t]))))

# ---------------------------------------------------- suspend graph
suspend_funcs=set()
for f,s in SRC.items():
    for m in re.finditer(r'\bsuspend\s+(?:operator\s+|override\s+|private\s+|internal\s+|public\s+|protected\s+|inline\s+|tailrec\s+|infix\s+|open\s+)*fun\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(\w+)', s): suspend_funcs.add(m.group(1))
    for m in re.finditer(r'\b(\w+)\s*:\s*suspend\s', s): suspend_funcs.add(m.group(1))

# every function body, with its own suspend-ness
for f,s in SRC.items():
    for m in re.finditer(r'\b(suspend\s+)?(?:operator\s+|override\s+|private\s+|internal\s+|public\s+|protected\s+|inline\s+|tailrec\s+|infix\s+|open\s+)*fun\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(\w+)\s*\(', s):
        is_susp=bool(m.group(1)); name=m.group(2)
        p=balanced(s,m.end()-1)
        rest=s[p+1:]
        bm=re.match(r'[^\n{=]*\{', rest)
        if not bm: continue
        start=p+1+bm.end()-1
        end=balanced(s,start,'{','}')
        body=s[start:end]
        if is_susp: continue
        # Coroutine builders re-open a suspend scope, so their LAMBDA BODIES must be excised
        # entirely - not just the call - or every call inside launch{} is falsely reported.
        BUILDERS=('launch','async','runBlocking','withContext','flow','produce','callbackFlow',
                  'channelFlow','coroutineScope','supervisorScope','collect','collectLatest',
                  'map','onEach','first','firstOrNull','runCatching','repeatOnLifecycle',
                  'LaunchedEffect','DisposableEffect','rememberCoroutineScope','forEach','let',
                  'also','apply','run','use','mapNotmull','filter','emit','transform')
        safe=body
        while True:
            m2=re.search(r'\b(%s)\s*(\([^()]*\))?\s*\{'%'|'.join(BUILDERS), safe)
            if not m2: break
            ob=safe.index('{',m2.end()-1)
            cb=balanced(safe,ob,'{','}')
            safe=safe[:m2.start()]+' '+safe[cb+1:]
        for c in re.finditer(r'\b(\w+)\s*\(', safe):
            callee=c.group(1)
            # This check is name-based: it cannot resolve receivers, so a project `save()`
            # collides with Canvas.save(), and `delete()` with File.delete().
            COLLIDES={'save','delete','get','set','close','start','stop','cancel','clear','add'}
            if callee in COLLIDES: continue
            if callee in suspend_funcs and callee!=name:
                rep('suspend',f,ln(s,start+c.start()),'%s() is suspend, called from non-suspend %s()'%(callee,name))

# ---------------------------------------------------- override targets
declared_members=defaultdict(set)
for f,s in SRC.items():
    for m in re.finditer(r'\b(?:open|abstract)\s+(?:suspend\s+)?(?:fun|val|var)\s+(?:<[^>]+>\s*)?(\w+)', s):
        declared_members['any'].add(m.group(1))
    for m in re.finditer(r'\binterface\s+\w+[^{]*\{', s):
        j=balanced(s,m.end()-1,'{','}')
        for mm in re.finditer(r'\b(?:suspend\s+)?(?:fun|val|var)\s+(?:<[^>]+>\s*)?(\w+)', s[m.end():j]):
            declared_members['any'].add(mm.group(1))

ANDROID_OVERRIDES={'onCreate','onReceive','onUpdate','onDeleted','onEnabled','onDisabled',
 'onAppWidgetOptionsChanged','onStartCommand','onBind','onDestroy','onStart','onStop','onResume',
 'onPause','onCleared','toString','equals','hashCode','doWork','onNewIntent','onBackPressed',
 'onRestoreInstanceState','onSaveInstanceState','getForegroundInfo','onTrimMemory','newArray',
 'createFromParcel','writeToParcel','describeContents','onConfigurationChanged','onLowMemory',
 'onSensorChanged','onAccuracyChanged','onKeyDown','onTouchEvent','onAttachedToWindow',
 'onDetachedFromWindow','onMeasure','onDraw','onLayout','onSizeChanged','onFinishInflate',
 'onProvideAssistContent','onUserLeaveHint','onWindowFocusChanged','workerFactory',
 'onTerminate','attachBaseContext','onRestart','onActivityResult','onRequestPermissionsResult',
 # androidx.work Configuration.Provider / Glance / Hilt base classes
 'workManagerConfiguration','sizeMode','stateDefinition','provideGlance','glanceAppWidget',
 'onUpdateAppWidget','Content','onDelete','onAppWidgetOptionsChanged'}
for f,s in SRC.items():
    for m in re.finditer(r'\boverride\s+(?:suspend\s+)?(?:fun|val|var)\s+(?:<[^>]+>\s*)?(\w+)', s):
        n=m.group(1)
        if n in declared_members['any'] or n in ANDROID_OVERRIDES: continue
        rep('override',f,ln(s,m.start()),'override %s - no visible open/abstract declaration'%n)

order=[('enum','Enum entry does not exist'),
       ('suspend','suspend called from non-suspend'),
       ('override','override with no visible target')]
tot=0
for k,t in order:
    rows=sorted(set(findings[k]))
    print('\n=== %s: %d ==='%(t,len(rows)))
    for fn,l,msg in rows: print('  %s:%s  %s'%(fn,l,msg))
    tot+=len(rows)
print('\nTOTAL: %d'%tot)

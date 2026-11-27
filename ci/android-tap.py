"""Tap a fully visible Android control by its actual accessibility text."""
import sys,re,subprocess,xml.etree.ElementTree as E,pathlib
wanted=sys.argv[1].casefold()
out=pathlib.Path('smoke/control-window.xml')
for attempt in range(8):
    subprocess.run(['adb','shell','uiautomator','dump','/sdcard/control.xml'],check=True,stdout=subprocess.DEVNULL)
    subprocess.run(['adb','pull','/sdcard/control.xml',str(out)],check=True,stdout=subprocess.DEVNULL)
    nodes=list(E.parse(out).iter('node'))
    height=max(int(re.findall(r'\d+',n.get('bounds','[0,0][0,0]'))[-1]) for n in nodes)
    for n in nodes:
        if n.get('text','').casefold().startswith(wanted):
            x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
            if y2-y1>=40 and y1>=60 and y2<=height-100:
                subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
                sys.exit(0)
    subprocess.run(['adb','shell','input','swipe','400','1450','400','400','400'],check=True)
raise SystemExit('Control not fully visible: '+wanted)

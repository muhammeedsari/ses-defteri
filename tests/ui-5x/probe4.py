from playwright.sync_api import sync_playwright
import json
P='file:///home/claude/ses-defteri-repo/web/page_t.html'
def clip(i,tr,at,dur,kind='speech',ch=1,**k):
    d={'id':i,'tr':tr,'path':'/proj/%s.wav'%i,'ch':ch,'dur':dur,'in':0,'out':dur,'at':at,'gain':0,'fi':0,'fo':0,'name':'Parça '+i,'kind':kind}; d.update(k); return d
proj={'tracks':[{'vol':0,'mute':False}],'clips':[clip('c1',0,0,10,rk=[{'k':'reverb','on':True,'v':{'mix':30,'room':50,'soft':30}},{'k':'delay','on':True,'v':{'mix':35,'ms':300,'fb':35}},{'k':'widen','on':True,'v':{'amt':60}}])]}
INIT="localStorage.setItem('sd:lang','\"tr\"');localStorage.setItem('sd:lastVer','\"5.13\"');localStorage.setItem('sd:introDone','true');localStorage.setItem('sd:coachOff','true');localStorage.setItem('sd:coachMarks','true');localStorage.setItem('sd:tab','\"edit\"');localStorage.setItem('sd:proj',%s);" % json.dumps(json.dumps(proj))
with sync_playwright() as p:
    b=p.chromium.launch(); pg=b.new_page(viewport={'width':390,'height':844}); ev=pg.evaluate; w=pg.wait_for_timeout
    pg.add_init_script(INIT); pg.goto(P); w(1200)
    ev("document.querySelectorAll('#modal [data-x]').forEach(b=>b.click())")
    ev("document.querySelector('[data-tab=edit]').click()"); w(600)
    # rack opened 3 times (cancel twice), then delete one effect
    for i in range(3):
        ev("__T.tlRack(__T.TL.p.clips[0])"); w(200)
        if i<2: ev("document.querySelector('#modalCard [data-x]').click()"); w(200)
    ev("document.querySelector('#modalCard [data-rdel=\"0\"]').click()"); w(300)
    print('rack after deleting 1 of 3:', [f['k'] for f in ev("__T.TL.p.clips[0].rk||[]")])
    ev("document.querySelector('#modalCard [data-x]').click()"); w(200)
    # practice opened twice, chords toggled once
    ev("__T.S.items.get('m3').smap={ch:[[0,'Am']],sec:[[0,10,'verse',0]]}; __T.S.items.get('m3').chOn=true")
    for i in range(2):
        ev("void __T.openPractice(__T.S.items.get('m3'))"); w(200)
        if i<1: ev("document.querySelector('#modalCard [data-x]').click()"); w(200)
    ev("document.querySelector('#modalCard [data-psm]').click()"); w(300)
    print('chOn after one tap (expected False):', ev("__T.S.items.get('m3').chOn"))
    b.close()

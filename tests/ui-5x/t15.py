exec(open('mk.py').read().split("from playwright")[0])
from playwright.sync_api import sync_playwright
import json
P='file:///home/claude/ses-defteri-repo/web/page_t.html'
INIT="localStorage.setItem('sd:lang','\"tr\"');localStorage.setItem('sd:lastVer','\"5.3\"');localStorage.setItem('sd:news4101','true');localStorage.setItem('sd:introDone','true');localStorage.setItem('sd:coachOff','true');localStorage.setItem('sd:coachMarks','true');"
def ok(n,c,i=''): print('PASS' if c else 'FAIL', n, '|', str(i)[:260])
with sync_playwright() as p:
    b=p.chromium.launch(); pg=b.new_page(viewport={'width':390,'height':844}); errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.add_init_script(INIT); pg.goto(P); pg.wait_for_timeout(1200); ev=pg.evaluate; w=pg.wait_for_timeout
    ev("window.__r = {id:'s1', title:'Şarkı', src:{uri:'content://x'}, duration:200, kind:'music'}; __T.openPractice(window.__r)"); w(300)
    ok('pratik penceresi açıldı', ev("!!document.querySelector('#prS')"))
    ev("const s=document.querySelector('#prP'); s.value=-2; s.dispatchEvent(new Event('input',{bubbles:true}))"); w(100)
    ok('perde gönderildi', ev("window.__calls.some(c=>c[0]==='AND.plPitch'&&c[1][0]===-2)"))
    ev("const s=document.querySelector('#prS'); s.value=0.75; s.dispatchEvent(new Event('input',{bubbles:true}))"); w(100)
    ok('hız gönderildi', ev("window.__calls.some(c=>c[0]==='AND.plRate'&&c[1][0]===0.75)"))
    ev("document.querySelector('[data-pk]').click()"); w(400)
    ok('ton/tempo bulundu', ev("document.querySelector('#prKey').textContent")=='Am · 92 BPM', ev("document.querySelector('#prKey').textContent"))
    ev("Object.defineProperty(__T.audio,'currentTime',{configurable:true,get:()=>1.2,set:()=>{}}); document.querySelector('[data-pa=\"a\"]').click()"); w(100)
    ok('A vuruşa oturdu (1.2→1.3)', ev("__T.AB.a")==1.3, ev("__T.AB.a"))
    ev("Object.defineProperty(__T.audio,'currentTime',{configurable:true,get:()=>3.0,set:()=>{}}); document.querySelector('[data-pa=\"b\"]').click()"); w(100)
    ok('döngü kuruldu (B=3.25)', ev("window.__calls.some(c=>c[0]==='AND.plLoop'&&c[1][0]===1300&&c[1][1]===3250)"), ev("window.__calls.filter(c=>c[0]==='AND.plLoop')"))
    pg.screenshot(path='s15a.png')
    ev("document.querySelector('[data-prg]').click()"); w(300)
    ok('ses aralığı açıldı', ev("!!document.querySelector('#rgL')"))
    ev("for (const f of [130.8,130.8,130.8,130.8,130.8,130.8,130.8, 440,440,440,440,440,440,440, 196,196,196]) window.__sdTune(f,-20)"); w(100)
    ok('aralık: Do3 – La4', ev("[document.querySelector('#rgL').textContent, document.querySelector('#rgH').textContent]")==['Do3','La4'], ev("[document.querySelector('#rgL').textContent, document.querySelector('#rgH').textContent, document.querySelector('#rgT').textContent]"))
    ev("window.__sdTune(442,-20); window.__sdTune(442,-20); window.__sdTune(442,-20)"); w(100)
    ok('akort: La4 +8 cent', ev("document.querySelector('#tuC').textContent").startswith('+8'), ev("document.querySelector('#tuN').textContent+' '+document.querySelector('#tuC').textContent"))
    pg.screenshot(path='s15b.png')
    ev("document.querySelector('.modal [data-x]').click()"); w(200)
    ok('kapanınca akort durdu', ev("window.__calls.some(c=>c[0]==='AND.tunerStop')"))
    ok('JS hatası yok', not errs, errs)
    b.close()

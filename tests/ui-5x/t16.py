exec(open('mk.py').read().split("from playwright")[0])
from playwright.sync_api import sync_playwright
import json
P='file:///home/claude/ses-defteri-repo/web/page_t.html'
INIT="localStorage.setItem('sd:lang','\"tr\"');localStorage.setItem('sd:lastVer','\"5.5\"');localStorage.setItem('sd:news4101','true');localStorage.setItem('sd:introDone','true');localStorage.setItem('sd:coachOff','true');localStorage.setItem('sd:coachMarks','true');"
def ok(n,c,i=''): print('PASS' if c else 'FAIL', n, '|', str(i)[:260])
with sync_playwright() as p:
    b=p.chromium.launch(); pg=b.new_page(viewport={'width':390,'height':844}); errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.add_init_script(INIT); pg.goto(P); pg.wait_for_timeout(1200); ev=pg.evaluate; w=pg.wait_for_timeout
    ev("window.__r = {id:'s1', title:'Şarkı', src:{uri:'content://x'}, duration:8, kind:'music'}; window.__T.S.asrSt={base:true}; void __T.karaoke(window.__r)"); w(400)
    ev("document.querySelector('[data-value=\"low\"], .choice [data-v=\"low\"]') ? 0 : 0"); 
    btns=ev("[...document.querySelectorAll('#modalCard button')].map(b=>b.textContent.trim())"); print(btns)
    ev("[...document.querySelectorAll('#modalCard button')].find(b=>b.textContent.includes('Kısık')).click()"); w(1500)
    T=ev("({names:__T.TL.p.clips.map(c=>c.name+':'+c.gain), lyr:(__T.TL.p.lyrics||[]).length, name:__T.TL.p.name})")
    ok('karaoke projesi: vokal kısık, müzik açık, sözler var', T['lyr']==3 and any(':-14' in n for n in T['names']), T)
    ev("__T.TL.pos=3.5; __T.drawTl()"); w(100)
    l=ev("[document.querySelector('#tlLyr b').textContent, document.querySelector('#tlLyr span').textContent, document.querySelector('#tlLyr').hidden]")
    ok('zaman çizelgesi altında söz satırı', l[0]=='İkinci cümle' and l[2]==False, l)
    pg.screenshot(path='s16a.png')
    ev("window.__r2 = {id:'s2', title:'Şarkı 2', src:{uri:'content://y'}, duration:8, kind:'music'}; void __T.lyricsGet(window.__r2)"); w(800)
    ok('sözler çıkarıldı ve saklandı', ev("(window.__r2.lyrics||[]).length")==3, ev("window.__r2.lyrics"))
    ok('JS hatası yok', not errs, errs)
    b.close()

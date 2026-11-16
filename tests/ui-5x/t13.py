exec(open('mk.py').read().split("from playwright")[0])
from playwright.sync_api import sync_playwright
import json
P='file:///home/claude/ses-defteri-repo/web/page_t.html'
def clip(i,tr,at,dur): return {'id':i,'tr':tr,'path':'/proj/%s.wav'%i,'ch':1,'dur':dur,'in':0,'out':dur,'at':at,'gain':0,'fi':0,'fo':0,'name':i,'kind':'speech'}
proj={'tracks':[{'vol':0}],'clips':[clip('c1',0,0,10)]}
INIT="localStorage.setItem('sd:lang','\"tr\"');localStorage.setItem('sd:lastVer','\"5.1\"');localStorage.setItem('sd:news4101','true');localStorage.setItem('sd:introDone','true');localStorage.setItem('sd:coachOff','true');localStorage.setItem('sd:coachMarks','true');localStorage.setItem('sd:proj',%s);" % json.dumps(json.dumps(proj))
def ok(n,c,i=''): print('PASS' if c else 'FAIL', n, '|', str(i)[:260])
with sync_playwright() as p:
    b=p.chromium.launch(); pg=b.new_page(viewport={'width':390,'height':844}); errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.add_init_script(INIT); pg.goto(P); pg.wait_for_timeout(1000); ev=pg.evaluate; w=pg.wait_for_timeout
    ev("document.querySelector('[data-tab=\"edit\"]').click()"); w(500); ev("__T.TL.sel='c1'; __T.renderTlBar()")
    ev("window.__calls.length=0; void __T.tlEffect(__T.TL.p.clips[0],'denoise',{strength:1})"); w(600)
    c=ev("__T.TL.p.clips[0]")
    ok('gürültü azaltma listeye girdi, kaynak saklandı', c.get('rk',[{}])[0].get('k')=='denoise' and c.get('src')=='/proj/c1.wav' and c['path']!=c['src'], {k:c.get(k) for k in ('rk','src','path','hcOut')})
    calls=ev("window.__calls.filter(x=>x[0]==='AND.job'&&x[1]==='wsOp').length"); ok('bir kez hesaplandı', calls==1, calls)
    ev("__T.tlRack(__T.TL.p.clips[0])"); w(200)
    secs=ev("[...document.querySelectorAll('.rk-sec')].map(x=>x.textContent)"); ok('Hesaplanan bölümü görünüyor', secs==['Hesaplanan efektler'], secs)
    ev("const s=document.querySelector('[data-rv=\"0:strength\"]'); s.value=1.4; s.dispatchEvent(new Event('input',{bubbles:true})); s.dispatchEvent(new Event('change',{bubbles:true}))"); w(700)
    c=ev("__T.TL.p.clips[0]"); ok('şiddet değişince yeniden hesaplandı', len(c['hc'])>=2 and c['path']==c['hcOut'], c['hc'])
    ev("const s=document.querySelector('#rkAdd'); s.value='reverb'; s.dispatchEvent(new Event('change',{bubbles:true}))"); w(200)
    secs=ev("[...document.querySelectorAll('.rk-sec')].map(x=>x.textContent)"); ok('iki bölüm', secs==['Hesaplanan efektler','Canlı efektler'], secs)
    m=ev("__T.tlMix()[0].fx"); ok('karışıma sadece canlı efekt gidiyor', [f['k'] for f in m]==['reverb'], m)
    # back to strength 1 -> cached, no new job
    ev("window.__calls.length=0; const s=document.querySelector('[data-rv=\"0:strength\"]'); s.value=1; s.dispatchEvent(new Event('change',{bubbles:true}))"); w(500)
    calls=ev("window.__calls.filter(x=>x[0]==='AND.job'&&x[1]==='wsOp').length"); ok('eski ayara dönüş hazırdan geldi (yeni iş yok)', calls==0, calls)
    pg.screenshot(path='s13a.png')
    ev("document.querySelector('.modal [data-ok]').click()"); w(200)
    ev("void __T.tlEffect(__T.TL.p.clips[0],'cut',{keep:[[0,4000],[6000,10000]]})"); w(700)
    c=ev("__T.TL.p.clips[0]"); ok('kesme: hesaplananlar sese işlendi, canlı kaldı', [f['k'] for f in c.get('rk',[])]==['reverb'] and 'src' not in c, {k:c.get(k) for k in ('rk','src','path')})
    ev("document.querySelector('[data-tl=\"undo\"]').click()"); w(300)
    c=ev("__T.TL.p.clips[0]"); ok('geri al: liste ve kaynak geri geldi', c.get('src')=='/proj/c1.wav' and len(c['rk'])==2, {k:c.get(k) for k in ('rk','src')})
    ok('JS hatası yok', not errs, errs)
    b.close()

exec(open('mk.py').read().split("from playwright")[0])
from playwright.sync_api import sync_playwright
import json
P='file:///home/claude/ses-defteri-repo/web/page_t.html'
def clip(i,tr,at,dur): return {'id':i,'tr':tr,'path':'/proj/%s.wav'%i,'ch':1,'dur':dur,'in':0,'out':dur,'at':at,'gain':0,'fi':0,'fo':0,'name':i,'kind':'speech','fx':[{'op':'denoise','r':'x'}]}
proj={'tracks':[{'vol':0}],'clips':[clip('c1',0,0,10)]}
INIT="localStorage.setItem('sd:lang','\"tr\"');localStorage.setItem('sd:lastVer','\"5.0\"');localStorage.setItem('sd:news4101','true');localStorage.setItem('sd:introDone','true');localStorage.setItem('sd:coachOff','true');localStorage.setItem('sd:coachMarks','true');localStorage.setItem('sd:proj',%s);" % json.dumps(json.dumps(proj))
def ok(n,c,i=''): print('PASS' if c else 'FAIL', n, '|', str(i)[:240])
with sync_playwright() as p:
    b=p.chromium.launch(); pg=b.new_page(viewport={'width':390,'height':844}); errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.add_init_script(INIT); pg.goto(P); pg.wait_for_timeout(1000); ev=pg.evaluate; w=pg.wait_for_timeout
    ev("document.querySelector('[data-tab=\"edit\"]').click()"); w(500); ev("__T.TL.sel='c1'; __T.renderTlBar()")
    ev("document.querySelector('[data-tl=\"cat:grp_fx\"]').click()"); w(150)
    tiles=ev("[...document.querySelectorAll('#tlBar .tc-grid.g4 span')].map(s=>s.textContent)")
    ok('Efekt grubunda Efekt listesi ilk sırada', tiles[:1]==['Efekt listesi'], tiles)
    ev("void __T.tlFxRun(__T.TL.p.clips[0],'reverb')"); w(300)
    n=ev("document.querySelectorAll('.rk').length"); ok('Yankı listeye eklendi, modal açık', n==1, n)
    ok('düzenleme geçmişi bozulmadı', ev("__T.TL.p.clips[0].fx.length")==1)
    ev("const s=document.querySelector('[data-rv=\"0:mix\"]'); s.value=60; s.dispatchEvent(new Event('input',{bubbles:true}))"); w(100)
    ok('kaydırıcı ayarı değiştiriyor', ev("__T.TL.p.clips[0].rk[0].v.mix")==60)
    ev("const s=document.querySelector('#rkAdd'); s.value='eightd'; s.dispatchEvent(new Event('change',{bubbles:true}))"); w(100)
    ok('ikinci efekt eklendi', ev("__T.TL.p.clips[0].rk.map(f=>f.k)")==['reverb','eightd'])
    ev("document.querySelector('[data-rup=\"1\"]').click()"); w(100)
    ok('sıra değişti', ev("__T.TL.p.clips[0].rk.map(f=>f.k)")==['eightd','reverb'])
    ev("const s=document.querySelector('[data-ron=\"1\"]'); s.checked=false; s.dispatchEvent(new Event('change',{bubbles:true}))"); w(100)
    m=ev("__T.tlMix()[0].fx"); ok('karışıma fx gidiyor (kapalı olan işaretli)', m and m[1]['on']==False, m)
    pg.screenshot(path='s12a.png')
    ev("document.querySelector('.modal [data-ok]').click()"); w(200)
    ok('Tamam sonrası kalıcı', len(ev("__T.TL.p.clips[0].rk"))==2)
    ev("__T.tlMaster()"); w(200)
    ev("document.querySelector('[data-ms=\"loud\"]').click()"); w(100)
    mx=ev("__T.tlMix()"); ok('mastering karışımın sonunda', mx[-1].get('master',{}).get('style')=='loud', mx[-1])
    ev("document.querySelector('[data-mab]').click()"); w(100)
    ok('önce/sonra: mastering geçici kapalı', 'master' not in ev("__T.tlMix()")[-1])
    pg.screenshot(path='s12b.png')
    ev("document.querySelector('.modal [data-ok]').click()"); w(200)
    ok('Tamam: mastering açık kaldı', ev("__T.tlMix()")[-1].get('master',{}).get('on')==True)
    ok('JS hatası yok', not errs, errs)
    b.close()

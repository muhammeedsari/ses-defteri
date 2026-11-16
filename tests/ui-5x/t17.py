from playwright.sync_api import sync_playwright
import json
P='file:///home/claude/ses-defteri-repo/web/page_t.html'
def clip(i,tr,at,dur,kind='speech',ch=1):
    return {'id':i,'tr':tr,'path':'/proj/%s.wav'%i,'ch':ch,'dur':dur,'in':0,'out':dur,'at':at,'gain':0,'fi':0,'fo':0,'name':'P '+i,'kind':kind}
proj={'tracks':[{'vol':0,'mute':False}],'clips':[clip('c1',0,0,10,'music',2)]}
INIT="localStorage.setItem('sd:lang','\"tr\"');localStorage.setItem('sd:lastVer','\"5.14\"');localStorage.setItem('sd:introDone','true');localStorage.setItem('sd:news4101','true');localStorage.setItem('sd:coachOff','true');localStorage.setItem('sd:coachMarks','true');localStorage.setItem('sd:tab','\"edit\"');localStorage.setItem('sd:proj',%s);" % json.dumps(json.dumps(proj))
def ok(n,c,i=''): print('PASS' if c else 'FAIL',n,'|',str(i)[:200])
with sync_playwright() as p:
    b=p.chromium.launch(); pg=b.new_page(viewport={'width':390,'height':844}); ev=pg.evaluate; w=pg.wait_for_timeout
    errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)))
    pg.add_init_script(INIT); pg.goto(P); w(1200)
    ev("document.querySelector('[data-tab=edit]').click()"); w(500)
    ev("__T.TL.sel='c1'; __T.renderTlBar()"); w(200)
    row=ev("[...document.querySelectorAll('.tc-grid')][0] ? [...[...document.querySelectorAll('.tc-grid')][0].querySelectorAll('[data-tl]')].map(b=>b.dataset.tl) : []")
    ok('ilk satır: Geçiş yok, Çoğalt var', 'dup' in row and 'fade' not in row, row)
    ev("document.querySelector('.tc-grid [data-tl=dup]').click()"); w(300)
    ok('Çoğalt çalıştı', ev("__T.TL.p.clips.length")==2)
    ev("__T.TL.sel=null; __T.TL.pos=2; __T.renderTlBar()"); w(100)
    ev("__T.tlAll()"); w(200); ev("document.querySelector('[data-dr=marker]').click()"); w(300)
    ok('işaret eklendi', len(ev("__T.TL.p.markers||[]"))==1)
    ev("__T.tlAll()"); w(200)
    lab=ev("document.querySelector('[data-dr=marker]').textContent")
    ok('işaretin üstünde araç adı: İşareti kaldır', 'kaldır' in lab, lab)
    ev("document.querySelector('[data-dr=marker]').click()"); w(300)
    ev("document.querySelector('#modalCard [data-ok]').click()"); w(300)
    ok('işaret kaldırıldı', len(ev("__T.TL.p.markers||[]"))==0)
    ev("__T.TL.pos=1; document.querySelectorAll('#modal [data-x]').forEach(b=>b.click())"); w(100)
    for t in (1,3,5):
        ev("__T.TL.pos=%d"%t); ev("__T.tlAll()"); w(150); ev("document.querySelector('[data-dr=marker]').click()"); w(200)
    ev("__T.tlAll()"); w(200)
    ok('birden çok işaret: Tümünü sil aracı var', ev("!!document.querySelector('[data-dr=markersX]')"))
    ev("document.querySelector('[data-dr=markersX]').click()"); w(300); ev("document.querySelector('#modalCard [data-ok]').click()"); w(300)
    ok('hepsi silindi', len(ev("__T.TL.p.markers||[]"))==0)
    ev("__T.tlAll()"); w(200)
    ok('kopya yokken Yapıştır listede yok', not ev("!!document.querySelector('[data-dr=paste]')"))
    ev("document.querySelectorAll('#modal [data-x]').forEach(b=>b.click())"); w(100)
    news=ev("['n5rack','n5master','n5practice','n5chords','n5hq','n5pitch','n5fix'].map(k=>__T.t('news_'+k)).join(' | ')")
    ok('yenilikler kartı güncel', 'Mastering' in news and 'Akorlar' in news and 'news_' not in news, news[:120])
    ok('JS hatası yok', not errs, errs)
    b.close()

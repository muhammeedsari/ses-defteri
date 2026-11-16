from playwright.sync_api import sync_playwright
import json
P='file:///home/claude/ses-defteri-repo/web/page_t.html'
INIT="localStorage.setItem('sd:lang','\"tr\"');localStorage.setItem('sd:lastVer','\"5.13\"');localStorage.setItem('sd:introDone','true');localStorage.setItem('sd:coachOff','true');localStorage.setItem('sd:coachMarks','true');"
log=[]
with sync_playwright() as p:
    b=p.chromium.launch(); pg=b.new_page(viewport={'width':390,'height':844})
    errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)[:200])); pg.on('console',lambda m: errs.append('console:'+m.text[:200]) if m.type=='error' else None)
    pg.add_init_script(INIT); pg.goto(P); pg.wait_for_timeout(1500); ev=pg.evaluate; w=pg.wait_for_timeout
    def rec(name):
        toast=ev("(()=>{const t=document.querySelector('#toast'); return t&&!t.hidden?t.textContent:''})()")
        h=ev("(()=>{const m=document.querySelector('#modalCard'); return m&&m.offsetParent? (m.querySelector('h3')||{}).textContent||'(modal)':''})()")
        log.append((name,h,toast[:80],list(errs))); errs.clear()
    def closeall():
        for _ in range(4):
            ev("document.querySelectorAll('#modal [data-x]').forEach(b=>{ if (b.offsetParent) b.click(); })"); w(80)
    for tab in ['rec','music','edit','rec']:
        ev("document.querySelector('[data-tab=\"%s\"]').click()"%tab); w(500); rec('tab:'+tab)
    items=ev("[...__T.S.items.values()].map(r=>[r.id,r.kind||'',r.title])"); log.append(('items',items,'',[]))
    for iid in [items[0][0], [i for i in items if 'caz' in i[2]][0][0]]:
        ev("void __T.itemMenu(__T.S.items.get('%s'))"%iid); w(300)
        opts=ev("[...document.querySelectorAll('#modalCard [data-v]')].map(b=>b.dataset.v)"); closeall()
        for o in opts:
            if o in ('del',): continue
            ev("document.querySelector('[data-tab=rec]').click()"); w(150)
            ev("window.__calls.length=0; void __T.itemMenu(__T.S.items.get('%s'))"%iid); w(250)
            ev("document.querySelector('#modalCard [data-v=\"%s\"]')?.click()"%o); w(500)
            jobs=ev("window.__calls.map(c=>String(c[0]))").__str__()[:120]
            rec('item %s %s %s'%(iid,o,jobs)); closeall()
    # player
    ev("__T.playList([__T.S.items.get('%s')],0)"%items[0][0]); w(800); rec('play rec')
    for sel in ['#plSpeed','#plAB','#plMore','#plEq','#plSleep','#plBm']:
        try:
            if ev("!!document.querySelector('%s')"%sel):
                ev("document.querySelector('%s').click()"%sel); w(400); rec('player '+sel); closeall()
        except Exception as e: log.append(('player '+sel,'ERR',str(e)[:100],[]))
    # practice
    caz=[i for i in items if 'caz' in i[2]][0][0]
    ev("void __T.openPractice(__T.S.items.get('%s'))"%caz); w(400); rec('practice')
    for btn in ['[data-pk]','[data-psm]']:
        ev("document.querySelector('#modalCard %s')?.click()"%btn); w(800); rec('practice '+btn)
    closeall()
    # clean
    ev("void __T.openClean(__T.S.items.get('%s'))"%items[0][0]); w(1200); rec('clean open')
    ev("(()=>{const b=document.querySelector('#modalCard .primary, #clGo, [data-clgo]'); b&&b.click()})()"); w(1500); rec('clean go')
    closeall()
    # settings
    ev("__T.openSettings()"); w(400); rec('settings')
    ids=ev("[...document.querySelectorAll('#modalCard input[type=checkbox]')].map(x=>x.id)")
    for i in ids:
        if not i: continue
        ev("__T.openSettings()") if not ev("!!document.querySelector('#%s')"%i) else None; w(200)
        ev("document.querySelector('#%s').click()"%i); w(300); rec('settings toggle '+i)
        ev("document.querySelector('#%s') && document.querySelector('#%s').click()"%(i,i)); w(200)
    btns=ev("[...document.querySelectorAll('#modalCard button')].map(b=>b.id||b.textContent.trim().slice(0,25))")
    log.append(('settings buttons',btns,'',[]))
    if ev("!!document.querySelector('#accTest')"):
        ev("document.querySelector('#accTest').click()"); w(800); rec('accTest')
    b.close()
for l in log: print(json.dumps(l,ensure_ascii=False))

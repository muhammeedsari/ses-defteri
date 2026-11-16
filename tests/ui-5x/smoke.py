exec(open('mk.py').read().split("from playwright")[0]) if False else None
from playwright.sync_api import sync_playwright
import json, sys
P='file:///home/claude/ses-defteri-repo/web/page_t.html'
def clip(i,tr,at,dur,kind='speech',ch=1,**k):
    d={'id':i,'tr':tr,'path':'/proj/%s.wav'%i,'ch':ch,'dur':dur,'in':0,'out':dur,'at':at,'gain':0,'fi':0,'fo':0,'name':'Parça '+i,'kind':kind}; d.update(k); return d
proj={'tracks':[{'vol':0,'mute':False},{'vol':0,'mute':False}],'clips':[clip('c1',0,0,10),clip('c2',1,0,20,'music',2)]}
INIT="localStorage.setItem('sd:lang','\"tr\"');localStorage.setItem('sd:lastVer','\"5.13\"');localStorage.setItem('sd:introDone','true');localStorage.setItem('sd:coachOff','true');localStorage.setItem('sd:coachMarks','true');localStorage.setItem('sd:tab','\"edit\"');localStorage.setItem('sd:proj',%s);" % json.dumps(json.dumps(proj))
out=[]
with sync_playwright() as p:
    b=p.chromium.launch(); pg=b.new_page(viewport={'width':390,'height':844})
    errs=[]; pg.on('pageerror',lambda e:errs.append(str(e)[:200])); pg.on('console',lambda m: errs.append('console:'+m.text[:200]) if m.type=='error' else None)
    pg.add_init_script(INIT); pg.goto(P); pg.wait_for_timeout(1500); ev=pg.evaluate; w=pg.wait_for_timeout
    def closeall():
        for _ in range(4):
            ev("document.querySelectorAll('#modal [data-x], #modal .modal-actions .btn:not(.primary)').forEach(b=>{ if (b.offsetParent) b.click(); })"); w(80)
        ev("try{closeOverlay('modal')}catch(e){}")
        ev("document.querySelectorAll('.overlay:not([hidden]) .close, [data-close]').forEach(b=>{if(b.offsetParent)b.click()})")
    def reset(sel):
        ev("__T.TL.busy=false; __T.TL.p=%s; __T.TL.sel=%s; __T.TL.pos=1; __T.TL.undo=[]; __T.renderEdit(); __T.renderTlBar(); __T.drawTl()" % (json.dumps(proj), json.dumps(sel))); w(150)
    def drive():
        seen=[]
        for _ in range(4):
            st=ev("""(()=>{const m=document.querySelector('#modal:not([hidden]) .modal, #modalCard'); if(!m||!m.offsetParent) return null;
              const h=m.querySelector('h3'); const ch=m.querySelector('.choice [data-v], [data-v]');
              return {h:h?h.textContent:'', choice:!!(ch&&ch.offsetParent), ok:!!(m.querySelector('[data-ok]')&&m.querySelector('[data-ok]').offsetParent), prim:!!(m.querySelector('.modal-actions .primary')&&m.querySelector('.modal-actions .primary').offsetParent)}})()""")
            if not st: break
            seen.append(st['h'])
            if st['choice']: ev("(()=>{const c=document.querySelector('#modalCard .choice [data-v], #modalCard [data-v]'); c&&c.click()})()")
            elif st['ok']: ev("document.querySelector('#modalCard [data-ok]').click()")
            elif st['prim']: ev("document.querySelector('#modalCard .modal-actions .primary').click()")
            else: break
            w(500)
        return seen
    for sel in ['c1','c2',None]:
        reset(sel); ev("__T.tlAll()"); w(300)
        tools=ev("[...document.querySelectorAll('[data-dr]')].map(b=>b.dataset.dr)")
        closeall()
        for tl in tools:
            if tl in ('new',): continue
            reset(sel); errs.clear(); ev("window.__calls.length=0"); ev("document.querySelector('#toast') && (document.querySelector('#toast').hidden=true)")
            ev("__T.tlAll()"); w(200)
            try: ev("document.querySelector('[data-dr=\"%s\"]').click()" % tl)
            except Exception as e: out.append((sel,tl,'CLICKFAIL',str(e)[:100])); continue
            w(600)
            seen=drive()
            w(400)
            jobs=ev("window.__calls.filter(c=>!String(c[0]).startsWith('AND.')).map(c=>c[0]+(c[1]&&c[1].op?':'+c[1].op:''))")
            toast=ev("(()=>{const t=document.querySelector('#toast'); return t&&!t.hidden?t.textContent:''})()")
            out.append((sel,tl,seen,jobs[:6],toast[:80],list(errs)))
            closeall(); w(100)
    b.close()
for o in out: print(json.dumps(o,ensure_ascii=False))

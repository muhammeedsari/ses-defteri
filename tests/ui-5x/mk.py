import sys, importlib.util
spec=importlib.util.spec_from_file_location('b','/home/claude/ses-defteri-repo/android/build_apk.py'); b=importlib.util.module_from_spec(spec); spec.loader.exec_module(b)
s=b.web_page(local_fonts=False)
S='/tmp/claude-0/-home-claude/f627c3b4-50c9-535f-ab8e-ff706c4816ce/scratchpad/t/'
hook="window.__T = { itemMenu, playList, openClean, tlAll, openSettings, CAPS, TL, S, R, ls, t, tlCommit, tlSnap, tlDo, tlSel, drawTl, renderEdit, renderTlBar, tlEffect, tlUnstack, tlTrackMenu, tlLayout, tlHdr, render, setMode, renderPurpose, openRecorder, tlIsMusic, tlFillers, tlSep, openEq, tlLoad, tlMix, tlRack, tlRackAdd, tlMaster, tlFxRun, tlHistory, openPractice, openTuner, karaoke, lyricsGet, lyrShow, PR, AB, get audio() { return audio; }, get ROW() { return TL_ROW; }, get ADD() { return TL_ADD; } };\n"
a="boot();\n\nif (!IN_FRAME"; assert s.count(a)==1; s=s.replace(a,hook+a)
s=s.replace('<head>\n','<head>\n<script>'+open(S+'mock.js').read()+'</script>\n',1)
open('/home/claude/ses-defteri-repo/web/page_t.html','w').write(s); print('built')

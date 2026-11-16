(function(){
  window.__calls=[];
  const items=[
   {id:'r1',src:{uri:'content://r1'},title:'Ses kaydı · 7 Kas',name:'a.m4a',rec:true,own:true,dur:4000,date:Date.now()-1000},
   {id:'r2',src:{uri:'content://r2'},title:'Toplantı',name:'b.m4a',rec:true,own:true,dur:60000,date:Date.now()-90000000},
   {id:'m1',src:{uri:'content://m1'},title:'01-konusma-temiz',name:'x.mp3',music:true,dur:51000,folder:'A'},
   {id:'m2',src:{uri:'content://m2'},title:'01-konusma-temiz',name:'x.mp3',music:true,dur:51000,folder:'B'},
   {id:'m3',src:{uri:'content://m3'},title:'13-caz-enstrumantal',name:'c.mp3',music:true,dur:61000,folder:'C',artist:'X'}];
  let jid=0;
  const res=(k,a)=>{
    switch(k){
      case 'tlAdd': return {path:'/proj/add'+(++jid)+'.wav',ch:1,dur:8000};
      case 'tlSep': return {voc:{path:'/proj/voc.wav',ch:1,dur:8000},acc:{path:'/proj/acc.wav',ch:2,dur:8000}};
      case 'wsInit': return {path:'/ws/w.wav',dur:8000};
      case 'asrStatus': return {base:true};
      case 'wsOp': return (a.op==='pauses'||a.op==='aipauses') ? {path:'/proj/op'+(++jid)+'.wav',ch:1,dur:8000,report:{cuts:[[2.0,1.0],[5.0,1.0]],count:2,saved:2}} : {path:'/proj/op'+(++jid)+'.wav',ch:1,dur:a.op==='cut'?5000:10000,report:{}};
      case 'tlRender': return {path:'/proj/mix'+(++jid)+'.wav',ch:2,dur:(a.clips&&a.clips.length===1?a.clips[0].len*1000:10000)};
      case 'transcribe': return {segments:[[0,2.5,'Merhaba dünya bu bir deneme cümlesidir ve biraz uzun olabilir ki ekranda bölünsün diye yazıldı'],[3,5,'İkinci cümle'],[6,8,'İkinci cümle']]};
      case 'fillers': return {ranges:[[1,1.4],[4,4.5]]};
      case 'musicKey': return {key:9,minor:true,conf:0.8,bpm:92,bconf:0.7};
      case 'beats': return {bpm:92,confidence:0.8,times:[0,0.65,1.3,1.95,2.6,3.25,3.9,4.55,5.2]};
      case 'clean': return {path:'/cache/clean.wav',dur:10000};
      case 'audiogram': return {gallery:true,src:{uri:'x'},name:'v.mp4'};
      case 'vad': return {segs:[[0.1,3.6],[4.0,7.5],[9.2,12.0]]};
      case 'tlSpk': return {n:2,segs:[[0.1,3.5,0],[4,7.4,1],[9.2,12,0]],db:[-24,-31]};
      case 'peaks': { const p=(a.src&&a.src.path)||''; const n=1000; let s=''; for(let i=0;i<n;i++){ const v=(p.indexOf('/proj/op')>=0 && i>=100 && i<150)?30:200; s+=String.fromCharCode(v);} return {data:btoa(s),rate:50}; }
      case 'ttsStatus': return {tr:true,en:false};
      default: return {};
    }};
  window.__res=res;
  window.SesDefteriAndroid=new Proxy({},{get(o,name){
    if(name==='caps') return ()=>JSON.stringify({native:true,sep:true,asr:true,media:true,spk:true,mic:true,hd:true,tag:true,uvr:false,uvrOk:true,sdk:34,ramMb:8000,cores:8});
    if(name==='listMedia') return ()=>JSON.stringify(items);
    if(name==='isDark') return ()=>false;
    if(name==='tunerStart') return ()=>true;
    if(name==='getLang') return ()=>'';
    if(name==='job') return (k,a)=>{const id='j'+(++jid); const args=JSON.parse(a); window.__calls.push([k,args]); setTimeout(()=>window.__sdJob(id,true,res(k,args)),15); return id;};
    return function(){ window.__calls.push(['AND.'+String(name),[...arguments]]); return ''; };
  }});
})();

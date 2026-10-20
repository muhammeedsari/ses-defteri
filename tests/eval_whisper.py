import sherpa_onnx, soundfile as sf, json, time, re, numpy as np, scipy.signal as ss, sys
LANG=__import__('os').environ.get('WL','')
V='/home/claude/vendor/'
meta=json.load(open('wav/sentences.json'))
def norm(s): s=s.replace('I','ı').replace('İ','i').lower(); s=re.sub(r"[^\w\s]","",s); return s.split()
def wer(r,h):
    d=np.zeros((len(r)+1,len(h)+1),int); d[:,0]=range(len(r)+1); d[0,:]=range(len(h)+1)
    for i in range(1,len(r)+1):
        for j in range(1,len(h)+1): d[i,j]=min(d[i-1,j]+1,d[i,j-1]+1,d[i-1,j-1]+(r[i-1]!=h[j-1]))
    return d[-1,-1]
for name in sys.argv[1:]:
    p=V+f'sherpa-onnx-whisper-{name}/{name}-'
    rec=sherpa_onnx.OfflineRecognizer.from_whisper(encoder=p+'encoder.int8.onnx',decoder=p+'decoder.int8.onnx',tokens=p+'tokens.txt',language=LANG,task='transcribe',num_threads=2)
    E=N=0; t0=time.time(); dur=0; ex=[]
    for m in meta:
        x,sr=sf.read('wav/'+m['file'],dtype='float32'); x=ss.resample_poly(x,16000,sr).astype(np.float32); dur+=len(x)/16000
        s=rec.create_stream(); s.accept_waveform(16000,x); rec.decode_stream(s)
        h=s.result.text; r=norm(m['text']); E+=wer(r,norm(h)); N+=len(r)
        if len(ex)<4: ex.append((m['text'],h))
    el=time.time()-t0
    print(name,'WER %.1f%%'%(100*E/N),'RTF %.2f'%(el/dur))
    for a,b in ex: print('  REF',a,'\n  HYP',b)

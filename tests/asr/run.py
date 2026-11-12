import re, sys, time, subprocess, json, numpy as np, sherpa_onnx, jiwer
T='/mnt/project-files/test-sesleri-3/'; M='/tmp/claude-0/m/'
VAD='/home/user/ses-defteri/vendor/asr/silero_vad.onnx'
FILES=['01-tr-kadin-temiz.wav','02-tr-erkek-temiz.wav','03-tr-iki-kisi-biri-kisik.wav','04-tr-uc-kisi.wav','08-tr-supurge-gurultusu.wav','12-tr-yankili-oda.wav','14-tr-telefon-8khz.wav','21-tr-sesli-mesaj.opus']
REFKEY={'21-tr-sesli-mesaj.opus':'02-tr-erkek-temiz.wav'}
# references
refs={}; cur=None
for l in open(T+'METINLER.txt',encoding='utf-8'):
    m=re.match(r'== (\S+)',l)
    if m: cur=m.group(1); refs[cur]=[]; continue
    if cur and l.startswith('- '):
        s=l[2:].strip(); s=re.sub(r'^(Kadın|Erkek|\d\. kadın|\d\. erkek|Kadın \d|Erkek \d)\s*:\s*','',s,flags=re.I); refs[cur].append(s)
ONES='sıfır bir iki üç dört beş altı yedi sekiz dokuz'.split(); TENS=['','on','yirmi','otuz','kırk','elli','altmış','yetmiş','seksen','doksan']
def n2w(n):
    if n<10: return ONES[n]
    out=[]
    for v,w in ((10**9,'milyar'),(10**6,'milyon'),(1000,'bin')):
        if n>=v:
            q=n//v; n%=v
            if not(v==1000 and q==1): out.append(n2w(q))
            out.append(w)
    if n>=100:
        q=n//100; n%=100
        if q>1: out.append(ONES[q])
        out.append('yüz')
    if n>=10: out.append(TENS[n//10]); n%=10
    if n: out.append(ONES[n])
    return ' '.join(out)
def norm(s):
    s=s.replace('İ','i').replace('I','ı').lower()
    s=re.sub(r"(\d+),(\d+)",lambda m:n2w(int(m.group(1)))+' virgül '+n2w(int(m.group(2))),s)
    s=re.sub(r"\d+",lambda m:' '+n2w(int(m.group()))+' ',s)
    s=re.sub(r"[’'`´]", '', s)
    s=re.sub(r"[^\w\s]|_",' ',s)
    s=s.replace('â','a').replace('î','i').replace('û','u')
    return ' '.join(s.split())
def load(f):
    b=subprocess.run(['ffmpeg','-v','quiet','-i',T+f,'-ac','1','-ar','16000','-f','f32le','-'],capture_output=True).stdout
    return np.frombuffer(b,dtype=np.float32).copy()
def vad_segments(x):
    c=sherpa_onnx.VadModelConfig(); c.silero_vad.model=VAD; c.silero_vad.threshold=0.5; c.silero_vad.min_silence_duration=0.45
    c.silero_vad.min_speech_duration=0.25; c.silero_vad.max_speech_duration=20; c.silero_vad.window_size=512; c.sample_rate=16000; c.num_threads=1
    v=sherpa_onnx.VoiceActivityDetector(c,buffer_size_in_seconds=60); segs=[]
    for i in range(0,len(x),512):
        v.accept_waveform(x[i:i+512])
        while not v.empty(): segs.append(np.array(v.front.samples,dtype=np.float32)); v.pop()
    v.flush()
    while not v.empty(): segs.append(np.array(v.front.samples,dtype=np.float32)); v.pop()
    return segs
def whisper(name):
    p=M+f'sherpa-onnx-whisper-{name}/{name}'
    r=sherpa_onnx.OfflineRecognizer.from_whisper(encoder=p+'-encoder.int8.onnx',decoder=p+'-decoder.int8.onnx',tokens=p+'-tokens.txt',language='tr',task='transcribe',num_threads=1,tail_paddings=1000)
    def run(x):
        out=[]
        for s in vad_segments(x):
            st=r.create_stream(); st.accept_waveform(16000,s); r.decode_stream(st); out.append(st.result.text)
        return ' '.join(out)
    return run
def seda(name):
    p=M+name+'/'
    r=sherpa_onnx.OnlineRecognizer.from_transducer(tokens=p+'tokens.txt',encoder=p+'encoder.int8.onnx',decoder=p+'decoder.onnx',joiner=p+'joiner.int8.onnx',num_threads=1,sample_rate=16000,feature_dim=80,decoding_method='greedy_search')
    def run(x):
        st=r.create_stream(); out=[]
        for i in range(0,len(x),1600):   # 0.1 s pieces, as live
            st.accept_waveform(16000,x[i:i+1600])
            while r.is_ready(st): r.decode_stream(st)
        st.accept_waveform(16000,np.zeros(16000,dtype=np.float32)); st.input_finished()
        while r.is_ready(st): r.decode_stream(st)
        return r.get_result(st)
    return run
MODELS={'whisper-base':lambda:whisper('base'),'whisper-small':lambda:whisper('small'),'seda-v0.1':lambda:seda('seda-v0.1'),'seda-v0.2':lambda:seda('seda-v0.2'),'seda-v0.2-tiny':lambda:seda('seda-v0.2-tiny')}
audio={f:load(f) for f in FILES}
res={}
for mn in sys.argv[1:]:
    run=MODELS[mn](); res[mn]={}
    for f in FILES:
        x=audio[f]; t0=time.time(); hyp=run(x); dt=time.time()-t0
        ref=norm(' '.join(refs[REFKEY.get(f,f)])); h=norm(hyp)
        o=jiwer.process_words(ref,h)
        res[mn][f]={'wer':o.wer,'ins':o.insertions,'n':len(ref.split()),'cer':jiwer.cer(ref,h),'rtf':dt/(len(x)/16000),'hyp':hyp}
        print(mn,f,f"WER {o.wer*100:.1f} CER {res[mn][f]['cer']*100:.1f} RTF {res[mn][f]['rtf']:.3f}",flush=True)
    json.dump(res[mn],open(f'/tmp/claude-0/eval/{mn}.json','w'),ensure_ascii=False,indent=1)

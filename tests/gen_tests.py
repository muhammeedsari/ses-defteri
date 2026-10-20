import numpy as np, soundfile as sf, json, random, scipy.signal as ss
R=np.random.default_rng(7); random.seed(7); SR=48000
meta=json.load(open('wav/sentences.json'))
def rd(f): x,_=sf.read('wav/'+f,dtype='float32'); return x
def db(x): return 10**(x/20)
# long speech: 20 sentences
order=list(range(len(meta))); random.shuffle(order); order=order[:20]
parts=[np.zeros(int(2.0*SR),np.float32)]; segs=[]; t=2.0
for i in order:
    x=rd(meta[i]['file']); x=x/np.max(np.abs(x))*0.35
    parts.append(x); segs.append([t,t+len(x)/SR,meta[i]['text']]); t+=len(x)/SR
    g=random.choice([0.4,0.7,1.0,1.6,2.5]); parts.append(np.zeros(int(g*SR),np.float32)); t+=g
parts.append(np.zeros(int(3.0*SR),np.float32))
clean=np.concatenate(parts).astype(np.float32)
floor=R.normal(0,db(-72),len(clean)).astype(np.float32)
clean_f=clean+floor
sf.write('wav/t_clean.wav',clean_f,SR,subtype='FLOAT')
json.dump(segs,open('wav/t_segments.json','w'),ensure_ascii=False)
# pink noise
w=R.normal(0,1,len(clean)); b,a=[0.049922035,-0.095993537,0.050612699,-0.004408786],[1,-2.494956002,2.017265875,-0.522189400]
pink=ss.lfilter(b,a,w); pink=pink/np.sqrt(np.mean(pink**2))
sp=clean[np.abs(clean)>0.01]; prms=np.sqrt(np.mean(clean[np.abs(clean)>1e-3]**2))
noise=(pink*prms*db(-15)).astype(np.float32)
hum=(0.004*np.sin(2*np.pi*50*np.arange(len(clean))/SR)).astype(np.float32)
sf.write('wav/t_noisy.wav',clean+noise+hum,SR,subtype='FLOAT')
sf.write('wav/t_noise_only.wav',noise+hum,SR,subtype='FLOAT')
# clicks
xc=clean_f.copy(); pos=sorted(R.integers(SR*2,len(clean)-SR,40)); 
for p in pos:
    n=R.integers(1,4); xc[p:p+n]+=R.choice([-1,1])*R.uniform(0.3,0.8)
sf.write('wav/t_clicks.wav',xc,SR,subtype='FLOAT'); json.dump([int(p) for p in pos],open('wav/t_clicks.json','w'))
# plosive LF bursts
xp=clean_f.copy(); pp=[]
for s in segs[:15]:
    p=int((s[0]+0.05)*SR); L=int(0.06*SR); tt=np.arange(L)/SR
    burst=0.45*np.sin(2*np.pi*55*tt)*np.exp(-tt/0.015); xp[p:p+L]+=burst; pp.append(p)
sf.write('wav/t_plosive.wav',xp,SR,subtype='FLOAT'); json.dump(pp,open('wav/t_plosive.json','w'))
# sibilant harsh
sos=ss.butter(4,[4500,9500],'bandpass',fs=SR,output='sos'); hi=ss.sosfilt(sos,clean).astype(np.float32)
sf.write('wav/t_sibilant.wav',clean_f+hi*db(12)-hi,SR,subtype='FLOAT')
# tonal variants
sos_lp=ss.butter(2,2500,'lowpass',fs=SR,output='sos'); muff=ss.sosfilt(sos_lp,clean_f).astype(np.float32)
sf.write('wav/t_muffled.wav',muff,SR,subtype='FLOAT')
sos_hp=ss.butter(1,3000,'highpass',fs=SR,output='sos'); bright=(clean_f+2.0*ss.sosfilt(sos_hp,clean_f)).astype(np.float32)
sf.write('wav/t_bright.wav',bright/np.max(np.abs(bright))*0.5,SR,subtype='FLOAT')
# stereo variants
sf.write('wav/t_dualmono.wav',np.stack([clean_f,clean_f],1),SR,subtype='FLOAT')
sf.write('wav/t_onesilent.wav',np.stack([clean_f,R.normal(0,db(-90),len(clean)).astype(np.float32)],1),SR,subtype='FLOAT')
sf.write('wav/t_onebroken.wav',np.stack([R.normal(0,0.2,len(clean)).astype(np.float32),clean_f],1),SR,subtype='FLOAT')
sf.write('wav/t_inverted.wav',np.stack([clean_f,-clean_f],1),SR,subtype='FLOAT')
# music: drums at bpm
def music(bpm,secs,seed):
    r=np.random.default_rng(seed); n=int(secs*SR); y=np.zeros(n,np.float32); beat=60/bpm; beats=[]
    k=0; t=0.5
    while t<secs-0.5:
        p=int(t*SR); beats.append(t); L=int(0.25*SR); tt=np.arange(L)/SR
        if k%2==0: y[p:p+L]+=0.8*np.sin(2*np.pi*(50+80*np.exp(-tt/0.03))*tt)*np.exp(-tt/0.12)
        else:
            y[p:p+L]+=0.35*r.normal(0,1,L)*np.exp(-tt/0.05)
        hp=int((t+beat/2)*SR); Lh=int(0.05*SR)
        if hp+Lh<n: y[hp:hp+Lh]+=0.08*r.normal(0,1,Lh)*np.exp(-np.arange(Lh)/SR/0.01)
        k+=1; t+=beat
    tt=np.arange(n)/SR; chords=[220,174.6,261.6,196]
    for i,f in enumerate(chords*int(secs/(beat*16)+1)):
        s=int(i*beat*4*SR); e=min(n,int((i+1)*beat*4*SR))
        if s>=n: break
        seg=tt[s:e]; y[s:e]+=0.06*(np.sin(2*np.pi*f*seg)+np.sin(2*np.pi*f*1.26*seg)+np.sin(2*np.pi*f*1.5*seg))
    return y, beats
for bpm in [120,93]:
    y,b=music(bpm,30,bpm); sf.write(f'wav/t_music{bpm}.wav',np.stack([y,y*0.9],1)*0.6,SR,subtype='FLOAT'); json.dump(b,open(f'wav/t_music{bpm}.json','w'))
y,b=music(100,90,5); sf.write('wav/t_bed.wav',np.stack([y,y],1)*0.6,SR,subtype='FLOAT')
print('len s',len(clean)/SR)

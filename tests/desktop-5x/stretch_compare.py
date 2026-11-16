import numpy as np, soundfile as sf, subprocess, sys
from scipy.signal import resample_poly, stft
fs=44100
def raw(x,p): x.astype('<f4').tofile(p)
def rd(p): return np.fromfile(p,'<f4')
sp,_=sf.read('/home/claude/vendor/sherpa-onnx-whisper-small/test_wavs/0.wav'); sp=resample_poly(sp,441,160)
mu,_=sf.read('/home/claude/vendor/sep/qi-feng-le-zh.wav'); mu=mu.mean(1)[:fs*20]
t=np.arange(fs*8)/fs; dr=np.zeros_like(t); rng=np.random.default_rng(1)
for k in range(32): s=int(k*0.25*fs); n=np.arange(2000); dr[s:s+2000]+=rng.standard_normal(2000)*np.exp(-n/300)*0.5
dr+=0.2*np.sin(2*np.pi*110*t)+0.1*np.sin(2*np.pi*165*t)
sigs={'konusma':sp,'muzik':mu,'davul':dr}
def eng(name,x,speed,st,fc=0):
    raw(x,'i.raw')
    if name=='java': cmd=['java','-cp','cls','TP','i.raw','o.raw',str(fs),str(speed),str(st),str(fc)]
    else: cmd=['./drv','i.raw','o.raw',str(fs),str(1/speed),str(st),str(fc)]
    r=subprocess.run(cmd,capture_output=True,text=True); ms=float([l for l in r.stderr.split('\n') if l.endswith(' ms')][-1].split()[0])
    return rd('o.raw'),ms
def lsd(a,b):
    n=min(len(a),len(b)); a=a[:n]; b=b[:n]
    _,_,A=stft(a,nperseg=2048); _,_,B=stft(b,nperseg=2048)
    A=20*np.log10(np.abs(A)+1e-5); B=20*np.log10(np.abs(B)+1e-5); m=(np.maximum(A,B)>-60)
    return np.sqrt(np.mean(((A-B)**2)[m]))
def align(y,x):
    # best lag within +-4000 samples
    n=min(len(x),len(y),fs*6); c=np.correlate(y[:n+8000],x[4000:n-4000],'valid'); l=np.argmax(c)-4000
    return y[l:] if l>0 else np.concatenate([np.zeros(-l),y])
print('sinyal test motor LSD(dB, düşük=iyi) süre(ms)')
for sn,x in sigs.items():
  for tn,(a,b) in {'perde+4/-4':((1,4),(1,-4)),'hız0.75/geri':((0.75,0),(1/0.75,0)),'perde+7/-7':((1,7),(1,-7))}.items():
    for e in ['java','ss']:
      y,m1=eng(e,x,*a); z,m2=eng(e,y,*b); z=align(z,x)
      print(sn,tn,e,round(lsd(z,x),2),round(m1+m2))

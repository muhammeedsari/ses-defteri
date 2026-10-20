import sherpa_onnx, numpy as np, soundfile as sf, json, scipy.signal as ss
V='/home/claude/vendor/'
S = [
 "Bugün toplantıda yeni ürünün satış planını konuştuk.",
 "Kars Kağızman yaylasından gelen balın siparişleri bu hafta iki katına çıktı.",
 "Videonun giriş kısmını biraz daha kısa tutmamız gerekiyor.",
 "Felsefede varoluşçuluk, insanın kendi anlamını yaratması gerektiğini söyler.",
 "Yarın sabah saat dokuzda müşteriyle telefon görüşmesi yapacağım.",
 "Kargo firmasıyla yeni bir anlaşma imzalamayı düşünüyoruz.",
 "Bu bölümde psikolojinin temel kavramlarını anlatacağım.",
 "Sesin seviyesi çok düşük olduğu için kaydı tekrar almamız lazım.",
 "Sessiz sahnelerde sesler, sözler ve sıcak sohbetler süslü sözcüklerle sürdü.",
 "Önümüzdeki ay reklam bütçesini artırmayı planlıyoruz.",
 "Şehrin şenlikli şarkıları, şiirleri ve şakaları şimdi şaşırtıcı.",
 "Toplantının sonunda herkes görevlerini not aldı.",
]
voices = ['vits-piper-tr_TR-dfki-medium','vits-piper-tr_TR-fettah-medium','vits-piper-tr_TR-fahrettin-medium']
out=[]; meta=[]
for vi,v in enumerate(voices):
    d=V+v
    import glob
    onnx=glob.glob(d+'/*.onnx')[0]
    cfg=sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=onnx,tokens=d+'/tokens.txt',data_dir=d+'/espeak-ng-data'),num_threads=2))
    tts=sherpa_onnx.OfflineTts(cfg)
    for i,s in enumerate(S):
        a=tts.generate(s,sid=0,speed=1.0)
        x=np.array(a.samples,dtype=np.float32); sr=a.sample_rate
        y=ss.resample_poly(x,48000,sr).astype(np.float32)
        sf.write(f'wav/s{vi}_{i}.wav',y,48000)
        meta.append({'file':f's{vi}_{i}.wav','text':s,'voice':v})
json.dump(meta,open('wav/sentences.json','w'),ensure_ascii=False,indent=1)
print(len(meta))

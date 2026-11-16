import sys; sys.argv=['x']
src=open('run.py').read().split('audio={f')[0]; exec(src)
clips={'çamaşır makinesi 4 sn':load('10-tr-camasir-makinesi-ilk4sn-sadece-gurultu.wav')[:4*16000],'müzik 4 sn':load('15-tr-konusma-muzik-ustunde.wav')[:4*16000],
 'hışırtılı müzik':load('18-muzik-hisirtili.wav')[:30*16000],'piyano':load('19-muzik-temiz-piyano.wav')[:30*16000],'tam sessizlik 3 sn':load('25-tr-basta-3sn-tam-sessizlik.wav')[:3*16000]}
for mn in ['whisper-base','whisper-small','seda-v0.1','seda-v0.2']:
    run=MODELS[mn]()
    for k,x in clips.items(): h=run(x); print(mn,'|',k,'|',len(norm(h).split()),'|',h[:120])

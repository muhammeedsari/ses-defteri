# Masaüstü ses testleri (5.x) — Java DSP JVM'de

Derlenmiş uygulama sınıfları (`android/out/obj`, build_apk.py üretir) + `android-34.jar` + org.json shim (`tests/desktop/jsonshim`) ile çalışır.
Sınıf yolu sırası önemli: `shim:android/out/obj:out:android-34.jar`. Yerel kütüphaneler (`libsdtp.so` masaüstü derlemesi) `-Djava.library.path`.

- `TAll.java`: `Workspace.op` ile bütün işlemleri bir dosyada çalıştırır; süre, kanal, tepe, RMS, kırpılan örnek sayısı yazar. Kullanım: `java … app.sesdefteri.TAll dosya.wav denoise,eq,speed,…`
  (Masaüstü shim'inde `JSONArray(Collection)` yok → `studio` orada hata verir, Android'de sorun değil.)
- `TCh2.java`: `web/fx-presets.js`'teki 10 hazır efekt zincirini çalıştırır (`chains.json` node ile üretilir).
- `SongMapTest.java`: akorları ve bölümleri bilinen sentetik şarkı → `dsp/SongMap` (beklenen: akorların ve bölümlerin hepsi doğru).
- `TP.java` + `stretch_compare.py`: eski perde motoru (TimePitch) ile Signalsmith (`drv` sürücüsü) gidiş-dönüş spektral hata karşılaştırması.

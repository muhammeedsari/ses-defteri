# Ses Defteri — Yol haritası (4 Kasım 2026, 3.4.5'ten sonra)

Kaynak: CapCut, BandLab ve Moises APK incelemeleri, AudioLab özellik listesi (ekran görüntüleri) + 3.4.4 tam testi + masaüstü model ölçümleri (ayrıntılar `docs/DURUM.md`).
İlke: internetsiz, veri toplanmaz, sade. Her iyileştirme ayrı düğmeyle uygulanır, kullanıcı değişimi görür. Kod en hafif hâliyle yazılır.

## Sürümler (sıra kullanıcıyla kararlaştırıldı)

| Sürüm | İçerik | Not |
|---|---|---|
| 3.4.5 | ✅ Zaman çizelgesi temizlik yarışı, sade hata metni, "seviyeleri dengele" ses yüksekliğini koruyor, sesten metne varsayılan Otomatik, lisanslar, tekil/çoğul | yayımlandı |
| 3.5 | Pan (klip başına sağ-sol); yerel çökme notu (model çalışmadan önce dosyaya not, açılışta varsa hafif seçenek öner); YZ işlemlerini büyük parçalarla ve paralel işleme (DPDFNet/Spleeter) | ölçüm: akışlı DPDFNet 2,5×, offline 3,6× gerçek zaman (2 çekirdek); 1 vs 2 iş parçacığı fark yok → parçaları paralel işle |
| 3.6 | Ses güzelleştirme kaydırıcısı (şiddet + ortam sesi %); dolgu kelime silme ("şey, yani, hani, işte" — Whisper metni, kesimi sessizliğe yasla); öksürük/gülme tespiti (sherpa-onnx audio tagging) | |
| 3.7 | Efektleri dosyaya yazmadan ayar olarak tutma (klip başına düzenlenebilir efekt listesi; hafifler canlı, ağırlar önbellekli); **Mastering stilleri** (Doğal/Sıcak/Bant/Yüksek + şiddet + canlı önce/sonra; multiband kompresör + EQ + limiter + LUFS) | BandLab mastering'i telefonda yapıyor |
| 3.8 | Oboe ile düşük gecikmeli kayıt, dinleme ve zaman çizelgesi çalma; **kayıt sırasında canlı efekt** (kulaklıkta reverb/EQ) | Oboe Apache-2.0 |
| 3.9 | Müzik modu gürültü azaltma (klasik spektral; DPDFNet müziği bozar); isteğe bağlı "Kaliteli ses ayırma" (UVR-MDX, 5–10 sn parçalarla, indirilir); gerçek yankı silme modeli bulunursa buraya | ölçüm: DPDFNet/GTCRN/bizim dereverb yankıyı gerçekten silmiyor (sadece kuyruk). UVR 30 sn için 3,5 GB RAM, 1,4× → parçalı şart |
| 3.10 | Signalsmith Stretch (MIT) — önce bizim TimePitch ile karşılaştırmalı test, sadece müzikte fark varsa geç; autotune'a dokunma | Rubber Band GPL → kullanma |
| 3.11 | Beat This! (MIT) isteğe bağlı indirme: ritim + ölçü başı | tam model ~190 MB |
| 3.12 | **Tek özellik:** NNAPI/GPU hızlandırma; telefonda ölç, kazanç yoksa kapat | masaüstünde XNNPACK fark yaratmadı, int8 DPDFNet daha yavaştı |
| 3.13 | **Tek özellik:** yavaş kalan Java DSP'yi C++'a taşıma (önce telefonda profil çıkar, sadece yavaş olanı taşı; `libsdsep.so` altyapısı) | |
| 3.14 | Kayıtta yankı engelleme ve otomatik kazanç (önce Android AcousticEchoCanceler/AutomaticGainControl, yetersizse WebRTC) | |
| 3.15 | Projenin tüm sesini eşitleme (tek tuş, hedef LUFS) + izlere göre otomatik miks dengesi (önce modelsiz, LUFS ile) | BandLab AutoMixer'ı küçük modelle yapıyor |
| 3.16 | Hazır ses efektleri veri dosyasından (JSON zincir); autotune hazır ayarları (Harmoni, Düet = perdesi kaydırılmış kopya); içerik üreticisi efektleri: vokal çiftleme, stereo genişletme, ters çalma, lo-fi, **8D ses** (zamanla dönen pan + hafif reverb; 3.5'teki pan üzerine) | BandLab efektlerini JSON metadata ile tanımlıyor |
| 3.17 | Telefon gücüne göre ayar (RAM/çekirdek → zayıf telefonda hafif mod, paralel parça sayısı) | |
| 3.18 | Akort aleti (tuner) + ses aralığı tespiti (autotune'daki perde takibiyle) | |
| 3.19 | Müzik çalarda **pratik modu**: hız + perde + vuruşa oturan A-B döngüsü + işaretler; ayrılmış parçaları birlikte çalma (parça başına ses / sadece bunu çal); **ton ve BPM gösterimi** (autotune'daki ton bulucu + ritim tespiti, şarkı bilgisi olarak) | Moises'in ana ekranı |
| 3.20 | Şarkı sözlerini çıkarma: ses ayırma (vokal) → Whisper; **karaoke akışı** (vokali sil → kulaklıkta çal + üstüne kaydet → birleştir; sözler varsa ekranda) | Moises sunucuda yapıyor, biz internetsiz |
| 3.21 | Şarkı bölümlerini bulma (nakarat/köprü; kendine benzerlik matrisi) | video editöründe müziği süreye uydurmanın da temeli |
| 3.22 | Akor gösterimi (deneysel; kromagram) | doğruluk sınırlı |
| 3.23 | **Her işlem için toplu işleme** (birden çok kayda aynı işlemi uygula: dönüştür, ses eşitle, efekt…) + kayıt menüsünden **hızlı dönüştürme / küçültme** ("MP3'e çevir", "WhatsApp için küçült"; mevcut dışa aktarma kodu) | AudioLab'da toplu dönüştürme/yükseltme var |

## Ayrı iş: Google Play yayını (karar verilince araya girer)
- targetSdk 34 → 36 (Play şartı; edge-to-edge, ön plan servis türü `mediaProcessing`)
- AAB + işlemci bölmesi; Spleeter'ı isteğe bağlı indirme
- Küçültülmüş ONNX Runtime (sadece bizim modellerin işlemleri; BandLab'ınki 3,3 MB, bizimki 22 MB) — sherpa-onnx ile yeniden derleme gerekir
- Kapalı test (12 kişi × 14 gün), mağaza metinleri 9 dil, veri güvenliği formu
- Lisans: Türkçe Piper sesi (dfki) CC BY-NC-SA 4.0 → premium/ücretli sürümde değiştirilmeli; espeak-ng GPL-3.0

## Video editörüne kalanlar
Videoda konuşma/müzik/efekt ayırma, şarkı üzerine videolu kayıt, müziği süreye uydurma, ritme göre kes (ölçü başı), Media3/MediaCodec + OpenGL ES önizleme.

## Bilinçli olarak alınmayanlar
- Müzik prodüksiyon araçları (MIDI, synth, sampler, beat yapıcı, looper, amfi) — uygulamayı DAW'a çevirir
- Sosyal/bulut özellikler, sunucuda YZ, reklam/izleme SDK'ları — internetsiz ve gizlilik vaadine aykırı
- Ses dönüştürme / YZ kapak / enstrüman üretme — ağır veya sunucu; kötüye kullanım ve lisans riski
- Superpowered (ticari), Sony 360RA / MPEG-H (lisanslı), Rubber Band (GPL), madmom modelleri (ticari olmayan), MoisesDB (ticari olmayan)

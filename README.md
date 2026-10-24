# Ses Defteri

Android için internetsiz ses kaydı, müzik çalar ve ses stüdyosu.

**İndir:** [Sürümler (Releases)](../../releases/latest) sayfasındaki `SesDefteri.apk` dosyası.
Telefonda "Bilinmeyen kaynaklardan yükleme" izni istenirse tarayıcıya izin ver.

## Özellikler
- Ses ve müzik kaydı, işaretler ve notlar, kategoriler
- Telefondaki tüm sesler: albümler, sanatçılar, klasörler
- Kaydırmalı dalga formu, kesme, kırpma, boşlukları silme
- Otomatik iyileştirme: gürültü, ses yüksekliği (LUFS) ve tepe sınırı, sessizlik kırpma, tık/patlama onarımı, de-esser, kanal düzeltme, Auto-EQ (sesini öğrenir). Orijinal hep saklanır.
- İnternetsiz metin dökümü (Whisper), SRT altyazı, otomatik başlık ve etiket
- Hız ve ton (formant korumalı), beat/tempo, arka plan müziği ile otomatik ducking
- YouTube / Podcast / WhatsApp / WAV dışa aktarma
- 9 dil, açık/koyu tema, renk seçimi

## Kaynak
- `android/` — Java kaynakları, derleme betiği (Gradle'sız) ve `fetch_vendor.sh`
- `CLAUDE.md`, `docs/DURUM.md`, `docs/MIMARI.md` — geliştirme rehberi ve durum
- `android/src/app/sesdefteri/dsp/` — saf Java ses işleme (Android'siz test edilebilir)
- `web/` — arayüz (WebView içinde çalışır)
- `tests/` — ölçüme dayalı testler

Metin dökümü modeli ve yerel kütüphaneler [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) sürümlerinden alınır (depoya eklenmez).

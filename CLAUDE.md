# Ses Defteri — Claude için proje rehberi

Bu dosyayı her oturumun başında oku. **Güncel yol haritası: `docs/YOL-HARITASI-5.15-6.x.md`** (5.15 Play hazırlığı → 6.x). Ayrıntılar: `docs/OZELLIKLER.md` (sürüm sürüm tüm özellikler), `docs/DEVIR-4.12-5.14.md` (son dönem, en güncel), `docs/DEVIR-4.3-4.12.md`, `docs/DURUM.md` (eski sürüm geçmişi), `docs/MIMARI.md` (kod haritası; 4.x öncesi), `docs/LISANSLAR.md`, `docs/TEST-LISTESI.md`.

## Proje
Ses Defteri, **internetsiz çalışan bir Android uygulaması**: ses kaydı, müzik çalar ve çok katmanlı ses stüdyosu (düzenleme, gürültü temizliği, yapay zekâ ile ses ayırma/temizleme, sesten metne). Sahibi Türk; uygulama 9 dilde (tr, en, de, fr, es, pt, ru, ar, zh), varsayılan dil İngilizce (ilk açılışta dil seçilir).

- **Arayüz:** tek sayfalık HTML/CSS/vanilla JS, Android WebView içinde (`web/`). Çerçeve yok.
- **Yerel katman:** Java 8 (lambda/method reference YOK — dx/dalvik-exchange min-sdk 24 ile derlenir), `android/src/app/sesdefteri/`.
- **Ses işleme:** saf Java DSP (`dsp/`), masaüstü JVM'de de test edilebilir.
- **Yapay zekâ:** sherpa-onnx v1.13.8 (ONNX Runtime; 5.13 itibarıyla metin okuma KAPALI derlenmiş kendi kopyamız, espeak-ng GPL yok — `.github/workflows/sherpa-notts.yml`, ön sürüm `sherpa-onnx-v1.13.8-notts`) — Whisper (sesten metne, isteğe bağlı indirilir), Spleeter (sesi ayır), DPDFNet-2 48 kHz (gelişmiş gürültü temizliği), LavaSR (HD ses, `Hd.java`, ONNX Runtime C API doğrudan, model GitHub'dan isteğe bağlı ~56 MB), UVR-MDX-NET Inst_HQ_5 (5.6, deneysel kaliteli ayırma, ~59 MB isteğe bağlı, ≥6 GB RAM ve hafif mod kapalıyken). Kendi JNI köprüleri: `libsdsep.so` (ayırma + gürültü temizleme), `libsdmp3.so` (LAME MP3), `libsdtp.so` (5.10, Signalsmith Stretch perde değişimi, yalnız arm64; yoksa `dsp/TimePitch`).
- **Derleme:** Gradle YOK. `android/build_apk.py`: aapt → javac (-source 8, android-34.jar) → dalvik-exchange → aapt add (dex + .so) → zipalign → apksigner.
- **Sürüm:** şu an **5.15** (versionCode 91). minSdk 24, targetSdk 34. Paket adı `app.sesdefteri`.

## Kullanıcıyla çalışma kuralları (önemli)
- Kullanıcıyla **Türkçe** konuş. Teknik olmayan, kısa ve net anlat.
- Kullanıcının tercihi: **"kodu en optimize ve en hafif şekilde yaz; test etmene gerek yok, ben test ederim ya da 'test et' derim."** Cihazda test yok; yeni DSP algoritmalarında masaüstünde hızlı ölçüm yapmak serbest ve faydalı (bkz. `tests/desktop`).
- Her sürüm şöyle teslim edilir: kod değişikliği → sürüm numarası artır (AndroidManifest `versionCode` +1 ve `versionName`, `web/body.html` içindeki `const VERSION = '…'`) → imzalı APK derle → git commit + push → APK'yı kullanıcıya gönder → Türkçe kısa özet (ne değişti, neyi test etmeli). GitHub Release'i kullanıcı kendisi yayımlar.
- Commit mesajları İngilizce, kısa; sürüm numarasıyla başlar ("2.6: …").
- Ara sürüm (5.x → 5.y) değişince açılan "Yenilikler" kartı `body.html` içindeki `NEWS` listesinden gelir (`news_<anahtar>`): her ara sürümde güncelle.

## Güvenlik kuralları (kesin)
- **İmza anahtarı (`ses-defteri-imza.p12`, alias `sesdefteri`) ve parolası ASLA repoya girmez.** `build_apk.py` parolayı `SD_KS_PASS` ortam değişkeninden, anahtarı `SD_KS` (varsayılan: `android/ses-defteri-imza.p12`, gitignore'da) yolundan okur. Anahtar olmadan derleme durur; yeni anahtar üretmek (`SD_NEW_KEY=1`) mevcut kullanıcıların güncelleme kurmasını imkânsız kılar — yapma. Anahtar dosyası ve parolası kullanıcıda; yoksa kullanıcıdan iste.
- Kullanıcının e-postasını kodda/repoda kullanma. Geri bildirim bağlantısı: `https://github.com/muhammeedsari/ses-defteri/issues`.
- Gizlilik vaadi: veri toplanmaz, sunucu yok; internet yalnızca kullanıcı isteyince (model indirme, geri bildirim sayfası). Bunu bozan özellik ekleme (analitik, reklam vb.).

## Derleme
```bash
bash android/fetch_vendor.sh            # bir kez: ../vendor içine sherpa-onnx kütüphaneleri, modeller, fontlar (~200 MB)
# android-34.jar'ı vendor/android-34.jar olarak koy (Android SDK platforms/android-34/android.jar)
# araçlar (Ubuntu): apt install aapt zipalign apksigner dalvik-exchange openjdk-21-jdk python3-pil
SD_KS=/yol/ses-defteri-imza.p12 SD_KS_PASS=… python3 android/build_apk.py   # → android/SesDefteri.apk
```
- `build_apk.py`, `web/head.html + web/extra.css + "</style>" + web/body.html` birleştirerek `web/page.html` üretir, i18n*.js dosyalarını sayfaya gömer, fontları yerelleştirir.
- **Yeni i18n dosyası eklersen** iki yere yaz: `web/body.html` içindeki `<script src="i18nN.js">` satırları ve `build_apk.py` içindeki `for js in [...]` listesi.
- JS kontrolü (derlemeden önce): `page.html`'deki `<script>` bloklarını çıkarıp `node --check`; ayrıca `$('#id')` ile kullanılan ama HTML'de olmayan id var mı bak (bilinen zararsız: `wsTime`).

## Kod düzeni (özet)
- `web/body.html` (~4300 satır): tüm arayüz + JS. Modüller yorum başlıklarıyla ayrılmış (`/* ---------- … ---------- */`). Yeni özellikleri ilgili modülün yanına ya da sürüm bloğu olarak ekle.
- `web/i18n*.js`: her dosya 9 dili `window.SD_I18N`'e birleştirir. Yeni anahtarları yeni bir `i18nN.js` dosyasına koy (şu an son: `i18n64.js`; hazır efekt verisi `web/fx-presets.js`), 9 dilin hepsini yaz. `t('anahtar', {n})` ile kullanılır.
- `android/src/app/sesdefteri/MainActivity.java`: WebView + `SesDefteriAndroid` JS köprüsü (`AND` in JS) + iş kuyruğu (`job(kind, argsJson)` → `runJob(kind, args, onProg)` JS'de).
- `Workspace.java`: tüm düzenleme işlemleri (`op(...)` içinde `case "denoise"` vb.), teşhis (`diagnose`), Temizle (`clean`), kaydet/dışa aktar.
- `Timeline.java`: çok katmanlı proje motoru (canlı karışım `AudioTrack`, render). Klipler 48 kHz 16-bit WAV, `files/proj`.
- Ayrıntılı harita `docs/MIMARI.md`'de.

## Bilinen tuzaklar
- Java'da `new Resampler(ch, (double) from, (double) to)` — int verirsen yanlış overload seçilir (geçmişte ciddi hata yaptı).
- Hızlı biten işlerde ön plan servisi: `WorkService` sadece 1 sn'den uzun işlerde başlar; başlamadan durdurulursa Android uygulamayı kapatır (2.3.2'de düzeltildi, bozma).
- `wsClean`/`tlClean` işleri `studioPool`'da (tek iş parçacığı) çalışır ki çalışan bir işin çıktısını silmesin. Ama sıraya giren `tlClean` çalışan işin *ardından* koşar: keep listesi eskidir. Bu yüzden `tlClean` `since` alır (JS: `Date.now() - 60000`), o andan sonra yazılan dosyalar silinmez (3.4.5 düzeltmesi; bozma).
- `JSONArray.optDouble(int)` masaüstü org.json shim'inde yok → `getDouble(i)` kullan.
- `Separator.model()` modelleri APK'dan dosyaya kopyalar (C API dosya yolu ister); bu yüzden Spleeter/DPDFNet telefonda iki kez yer tutar.
- Ağ: bu geliştirme ortamında Hugging Face ve Maven erişimi engelliydi; GitHub release indirmeleri çalışıyordu.

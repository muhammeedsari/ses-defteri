# Ses Defteri — devir notu (4.12 → 5.14)

Bu not, bir önceki Claude oturumunda yapılan her şeyi, devam edecek Claude için özetler. Bir önceki dönem `DEVIR-4.3-4.12.md`'de.
Önce `CLAUDE.md`'yi oku: çalışma kuralları, derleme ve güvenlik oradadır. Lisans durumu `LISANSLAR.md`'de, test listesi `TEST-LISTESI.md`'de.

- Repo: `github.com/muhammeedsari/ses-defteri` (herkese açık)
- Çalışma dalı: `claude/project-thread-jz2p6y` (yerel dal adı `sd43`). Push: `git push origin HEAD:claude/project-thread-jz2p6y`
- Son sürüm: **5.14** (versionCode 87), son i18n dosyası: `web/i18n61.js`
- Sürüm planı (Claude Doc): "Ses Defteri — Sürüm planı (4.12'den sonra)". Plandaki 4.13–5.7'nin hepsi yapıldı.

## Kullanıcıyla çalışma kuralları (mutlaka uy)
- Türkçe konuş; kısa, teknik olmayan bir dil kullan.
- Kodu en optimize ve en hafif şekilde yaz. Test etme; kullanıcı kendisi test eder ya da "test et" der. Yeni DSP/YZ algoritmasında masaüstünde hızlı ölçüm serbest.
- Kullanıcı hata / arayüz sorunu bildirince **önce sadece incele ve listele**; kodu "düzelt" deyince değiştir.
- Var olan özelliği "yeni" diye önerme; önce `body.html`'de ara.
- Kullanıcı genelde "Başla" / "Devam et" / "Sıradakine başla" der: plandaki sıradaki sürüme geç.

## Sürüm teslim adımları (her sürümde)
1. Kod değişikliği.
2. `android/AndroidManifest.xml` versionCode +1 ve versionName; `web/body.html` `const VERSION`.
3. Yeni metin → yeni `web/i18nN.js` (9 dil: tr, en, de, fr, es, pt, ru, ar, zh). İki yere kaydet: `body.html` `<script src>` (fx-presets.js'den önce) ve `android/build_apk.py` `for js in [...]` listesi.
4. Ara sürüm değiştiyse `NEWS` listesini (body.html, `news_<anahtar>`) güncelle.
5. Derle: `cd android && SD_KS=<anahtar yolu> SD_KS_PASS=<parola> SD_VENDOR=<vendor yolu> python3 build_apk.py`, sonra `rm -rf __pycache__`. (Anahtar ve parola kullanıcıda; repoya girmez; yeni anahtar üretilmez.)
6. APK içindeki `assets/www/index.html`'den scriptleri çıkar, `node --check`.
7. `CLAUDE.md`'de sürüm ve son i18n dosyası.
8. `git grep` ile parolanın repoda olmadığını doğrula.
9. Commit (İngilizce, sürümle başlar) + push. APK'yı `SesDefteri-X.apk` adıyla gönder, Türkçe kısa özet: ne değişti, neyi test etmeli, sırada ne var.

## Sürüm sürüm yapılanlar

### 4.13 — Ses efektleri ve kaydırma
- `dsp/Fx.java`: **8D ses, Genişlet, Çiftle (double), Lo-fi** (stereo düğümler). Workspace op'ları `eightd/widen/double/lofi` ve `reverse` (ters çal).
- Zaman çizelgesinde klip başına **sağ-sol (pan)**.

### 4.14 — Proje seviyesi araçlar
- **Projeyi eşitle** (`tlLevelAll`, Java `Workspace.clipLoud` / iş `tlLoud`): klipler −16 LUFS'e, müzik konuşmanın 6 dB altına.
- Kayıt menüsü: **WhatsApp için gönder** (m4a 48 kbps mono, sonra paylaş) ve MP3'e çevir.
- **Toplu araç uygula** (çoklu seçim > Daha > Araç uygula, `batchOp`).

### 4.15 — Zayıf telefonlar
- `Device.java`: **Hafif mod** (Ayarlar), iş parçacığı sayısı, YZ çökmesi notu (`ai_running`) ve "hafif moda geç" teklifi.
- DPDFNet çevrimdışı modda 15 sn parçalar + paralel işçiler (`Denoiser.Par`, yerel `createOff/runOff`): masaüstünde 1,45× hızlı, 2 çekirdekte ~2×.
- CAPS: `ramMb`, `cores`, `weak`.

### 4.16 — Temizlik
- **Stüdyo sesi** penceresi: güç + oda sesi (YZ gürültüde arka planın ne kadar kalacağı).
- **Dolgu kelimeleri** (`Workspace.fillerWords`, iş `fwords`): Whisper sözcüklerinden dile göre liste ("yani", "şey"…).
- **Öksürük/gülme/hapşırık bulma** (`Sounds.java`, CED tiny 6 MB, iş `sounds`, indirme `tagGet`): kıs ya da kes.

### 4.17 — Hazır efektler ve harmoni
- `web/fx-presets.js` (`window.SD_FX`): ses karakterleri + **10 hazır efekt zinciri** (warm, trailer, stadium, cave, nextroom, walkie, oldradio, ghost, dream, choir). Workspace op `chain`.
- **Harmoni / Düet** (`tlHarmony`): perdesi kaydırılmış kopya boş katmana, ±40 pan.

### 5.0 — Efekt listesi (rack) ve mastering
- Klip başına **canlı efekt listesi** `c.rk` (sıra, aç/kapa, ayar): reverb, delay, comp, widen, eightd, double, lofi. Canlı çalmada ve dışa aktarmada (`Timeline` RackOut).
- **Mastering** (`dsp/Master.java`): 3 bant kompresör, ton, bant/yüksek yumuşak kırpma, seviye takipçisi (−18/−17/−13), limiter. Stiller: Doğal, Sıcak, Bant, Yüksek; A/B.
- Not: `c.fx` düzenleme GEÇMİŞİDİR, efekt listesi `c.rk`'dır.

### 5.1 — Hesaplanan (ağır) efektler de listede
- `HEAVY` = studio, chain, aidenoise, denoise, musicdenoise, clarify, dereverb, deess, autotune, hd. Listeye eklenir, sonradan ayarı değişebilir/kapanabilir.
- Klip alanları: `c.src` (orijinal), `c.hc` (önbellek, anahtar `yol|JSON(args)`), `c.hcOut`. `tlHeavyRun`, `hvBake`.

### 5.2 — Kesme ve geçmiş efekt listesiyle uyumlu
- Kesme, duraklama, kırpma, hız, ekleme yapılınca hesaplanan efektler orijinal sese de uygulanır (`HV_FOLLOW`, `hvFollow`); liste canlı kalır.
- Geçmişte "Buraya dön" efekt listesini de geri getirir.

### 5.3 — Pratik
- Çalarda **Pratik** penceresi (`openPractice`): hız, perde (`Player.setPitch`), A-B döngü (vuruşa yapıştırma), ton/tempo bul, parçalara ayır.
- **Akort aleti** ve **ses aralığı** (`Tuner.java`, YIN, hata <1,2 sent; `window.__sdTune`).

### 5.4 — Düşük gecikme
- Ayarlar > **Düşük gecikme**: kayıt VOICE_PERFORMANCE + 5 ms bloklar, zaman çizelgesi 40 ms tampon.
- Kayıt sırasında kulaklıkta **yankı/eko izleme** (`AND.recMonFx`).

### 5.5 — Şarkı sözleri ve karaoke
- **Sözleri çıkar** (`lyricsGet`: vokal ayrılır → Whisper): çalarda ve düzenleyicide akan sözler.
- **Karaoke** (`karaoke`): yeni proje, şarkı ayrılır, vokal kapalı ya da kısık (rehber), sözler alta.

### 5.6 — Müzik için
- **Müzikte gürültü azalt** (op `musicdenoise`: en çok 10 dB, takipsiz, yumuşak). Müzik klibinde "Gürültü azalt" önce bunu önerir; Temizle müzikte bunu önerir.
- **Kaliteli 2 parça (deneysel)**: UVR-MDX-NET Inst_HQ_5 (59 MB, indirme `uvrGet`), yalnız RAM ≥ 6 GB ve hafif mod kapalıyken. `sdsep.c`: eşlik (accompaniment) modeli boş verilirse UVR; UVR'de çıktı sırası ters (önce enstrüman), 10 sn parçalar. Masaüstü: ~2,2 GB bellek, ~1,5× gerçek zaman.

### 5.7 — Akorlar ve bölümler
- `dsp/SongMap.java` (model yok): chroma + bas chroma, vuruş başına 24 akor + Viterbi; ölçü özelliklerinden benzerlik matrisi → yenilik → bölümler, gruplama; en çok tekrar eden yüksek grup Nakarat. İş `songMap`, sonuç kayıtta `r.smap`.
- Çalarda anlık akor + sıradaki + bölüm adı (`#plChd`); Pratik'te bölüm çipleri (dokununca A-B döngü). Sentetik şarkıda akorlar ve bölümler %100 doğru.

### 5.8 — Stüdyo sesi ve hazır efektler geri alınabilir
- İkisi de efekt listesine girer (`hvArgs`): `studio` (Java'da Temizle zinciri tek adım), `chain` (id ile; tarif değiştirilebilir).

### 5.9 — Yapay zekâ hızlandırıcısı + Türkçe ses lisansı
- Ayarlar > **Yapay zekâ hızlandırıcısı (deneysel)** + "Bu telefonda ölç" (`Separator.accelTest`: 8 sn sesi işlemci ve NNAPI ile ayırır, süre + fark). %15'ten az hızlıysa ya da sonuç farklıysa kendini kapatır. Etkilediği: ayırma, DPDFNet, HD ses (`sdsep.c` `prov`).
- Türkçe okuma sesi DFKI (ticari değil) → Fettah (CC0). (5.12'de okuma sesleri tamamen kaldırıldı.)

### 5.10 — Yeni perde motoru
- **Signalsmith Stretch** (MIT) yerel `libsdtp.so` (yalnız arm64, zig ile musl hedefi + sahte libc/libm; `android/native/sdtp/`). `dsp/Stretch.make`: perde değişiyorsa Signalsmith, yalnız tempo ise eski `TimePitch`; 32 bit telefonda hep eski motor.
- Ölçüm (gidiş-dönüş spektral hata, düşük iyi): müzik +7 yarım ton 9,8 → 3,7; müzik +4: 5,9 → 3,9; konuşma benzer; yalnız hızda eski motor daha iyi.
- Masaüstünde yankı azaltma modeli (FoxJoy Reverb HQ) denendi: 5,2 GB bellek, sonuç sessiz → kullanılmadı.

### 5.11 — Lisans temizliği
- Okuma sesleri değişti: İngilizce Lessac (araştırma lisansı) → LJSpeech (kamu malı), Rusça irina → dmitri (CC0), Çince huayan → chaowen (CC0). Ayrıntı `LISANSLAR.md`.

### 5.12 — Yapay zekâ okuma sesleri kaldırıldı
- espeak-ng GPL-3.0 olduğu için **Piper sesleri tamamen çıkarıldı**. "Metinden ses" yalnız telefonun kendi sesiyle (Android TextToSpeech) çalışır. Eski indirilmiş sesler ilk açılışta silinir (`Tts.cleanup`).

### 5.13 — Kütüphane metin okuma olmadan derlendi
- sherpa-onnx v1.13.8 GitHub Actions'ta `SHERPA_ONNX_ENABLE_TTS=OFF` ile derlendi (`.github/workflows/sherpa-notts.yml`, sonuç bu deponun ön sürümü `sherpa-onnx-v1.13.8-notts`, `jniLibs.tar.bz2`). `fetch_vendor.sh` artık bunu indirir.
- APK'da espeak-ng kodu kalmadı (doğrulandı); kullanılmayan TTS dışındaki tüm semboller aynı; libonnxruntime birebir aynı. APK ~2 MB küçüldü.

### 5.14 — İnceleme düzeltmeleri + iki istek
- Tam inceleme yapıldı (lint, 150 araç denemesi, 40 ses işlemi masaüstünde, Java↔.so sembol eşleşmesi, çeviriler, dış adresler).
- Düzeltmeler: `openModal` artık her açılışta yeni pencere kartı üretir (eskiden `addEventListener` dinleyicileri birikiyordu: Pratik'te "Akorları gizle" ikinci açılışta çalışmıyordu); "Yenilikler" kartı 5.x içeriğiyle; "Yapıştır" yalnız kopya varken; müzikte uğultu önerisi eşiği 10; EQ / uğultu / hız sonrası limiter; ölü kod ve TTS Java sınıfları silindi.
- İstekler: klip kartının ilk satırında **Geçiş yerine Çoğalt**; **işaret kaldırma** (işaretin üstündeyken "İşareti kaldır", bayrağa iki dokunuş, "Tüm işaretleri sil").

## Önemli kod noktaları (yeni)
- JS: `tlHeavyRun / hvArgs / hvSteps / hvFollow / hvBake` (5.1–5.8), `tlRack`, `tlMaster`, `openPractice`, `openTuner`, `lyricsGet`, `karaoke`, `smSections` + `#plChd` aralığı, `tlSep` ('hq'), `tlAddMarker / tlDelMarker / tlDelMarkers / tlMarkerAt`, ayarlarda `#setAccel / #accTest`, `openModal` (temiz kart).
- Java: `Workspace.op` (`studio`, `chain`, `musicdenoise`, `eightd/widen/double/lofi/reverse`), `Separator` (UVR, `accel`, `accelTest`), `Hd`, `Sounds`, `Tuner`, `dsp/Master`, `dsp/SongMap`, `dsp/Stretch`, `Device`, `Tts` (yalnız telefon sesi).
- Yerel: `libsdsep.so` (sherpa C API + ORT C API; `prov` cpu/nnapi), `libsdmp3.so`, `libsdtp.so` (Signalsmith). Derleme notları `android/native/README.md`.
- Test düzeneği (masaüstü): Playwright + sahte köprü (mock) ile sayfa; Java ses işlemleri `android/out/obj` sınıflarıyla JVM'de.

## Açık konular
- 5.13 / 5.14 telefonda test edilmedi (bkz. `TEST-LISTESI.md`). Özellikle: yeni sherpa kütüphanesi (yazıya dök, ayırma, gürültü, konuşmacı, öksürük), Signalsmith (arm64), hızlandırıcı ölçümü, UVR, HD ses.
- **Google Play hazırlığı** (sıradaki iş): AAB paketi, arka plan işi türü `dataSync` → medya işleme, izin açıklamaları, mağaza metinleri (9 dil), gizlilik sayfası, veri güvenliği formu; kullanıcının işi: Play Console hesabı, 12 kişi × 14 gün kapalı test, imzayı mevcut anahtarla yüklemek.
- Bilinen küçük sınırlar: 32 bit telefonlarda MP3 ve Signalsmith yok (yedek var); akorlar yalnız majör/minör; UVR çok bellek ister.

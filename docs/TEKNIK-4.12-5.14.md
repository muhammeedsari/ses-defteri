# Ses Defteri 4.12 → 5.14: teknik döküm (test için)

Bu belge 4.12 ile 5.14 arasında eklenen her şeyi **test edecek kişi / Claude** için anlatır: kullanılan teknolojiler, eklenen özellikler, kodun nasıl yazıldığı, hangi dosya ve fonksiyonda olduğu, nasıl çalıştığı ve neyin test edileceği.
Birlikte okunacak dosyalar: `CLAUDE.md` (kurallar, derleme), `DEVIR-4.12-5.14.md` (sürüm özeti), `TEST-LISTESI.md` (telefon kontrol listesi), `LISANSLAR.md`.
Repo: `github.com/muhammeedsari/ses-defteri`, dal `claude/project-thread-jz2p6y`. Sürüm 5.14 = versionCode 87.

---

## 1. Teknoloji yığını (4.12–5.14'te eklenen / değişen)

| Alan | Teknoloji | Nerede |
|---|---|---|
| Arayüz | Tek sayfa HTML/CSS/vanilla JS, Android WebView; tek büyük IIFE (`web/body.html`), çeviriler `web/i18n46.js … i18n61.js` (9 dil), hazır efekt verisi `web/fx-presets.js` | web/ |
| Yerel | Java 8 (lambda yok), Gradle yok, `android/build_apk.py` (aapt → javac → dalvik-exchange → zipalign → apksigner) | android/src/app/sesdefteri |
| Ses işleme | Saf Java DSP (`dsp/`): yeni `Fx`, `Master`, `SongMap`, `Stretch` | dsp/ |
| YZ çalışma ortamı | sherpa-onnx v1.13.8 + ONNX Runtime 1.28.2. **5.13'ten beri kendi derlememiz, metin okuma (TTS) kapalı** → espeak-ng (GPL) yok. GitHub Actions: `.github/workflows/sherpa-notts.yml`, çıktı: depo ön sürümü `sherpa-onnx-v1.13.8-notts` (`jniLibs.tar.bz2`), `fetch_vendor.sh` bunu indirir | vendor/jniLibs |
| Kendi JNI köprüleri | `libsdsep.so` (C, zig ile çapraz derleme; sherpa C API + ONNX Runtime C API): Spleeter / UVR ayırma, DPDFNet (akışlı + çevrimdışı paralel), LavaSR (HD), NNAPI anahtarı. `libsdmp3.so` (LAME). **Yeni** `libsdtp.so` (C++, Signalsmith Stretch, yalnız arm64, musl hedefi + sahte libc/libm ile; Android'in bionic'ine bağlanır) | android/native |
| Modeller (isteğe bağlı indirme, GitHub) | LavaSR (HD ses, ~56 MB, Apache-2.0), CED tiny (ses olayı, 6 MB, Apache-2.0), UVR-MDX-NET Inst_HQ_5 (kaliteli ayırma, 59 MB, UVR/MIT), Whisper base/small (önceden vardı) | files/hd, files/tag, files/uvr |
| APK içindeki modeller | Spleeter 2 parça fp16, DPDFNet-2 48 kHz, Silero VAD, pyannote + CAM++ (konuşmacı) | assets |
| Android API'leri | `PlaybackParams.setPitch` (Pratik perde), `MediaRecorder.AudioSource.VOICE_PERFORMANCE` (API 29+, düşük gecikme), düşük gecikmeli `AudioTrack` (PERFORMANCE_MODE_LOW_LATENCY), `AudioRecord` (akort aleti), `TextToSpeech` (yalnız telefon sesi), `ActivityManager.MemoryInfo` (RAM kapısı) | |
| Algoritmalar | YIN perde (akort), chroma + Viterbi (akor), öz-benzerlik + dama tahtası çekirdeği (bölümler), 3 bant kompresör + seviye takipçisi + limiter (mastering), Signalsmith spektral perde kaydırma, spektral çıkarma (müzik gürültüsü), Haas/ortalama-yan (genişlet/çiftle), HRTF benzeri dönen pan (8D) | |
| Araçlar | zig (C/C++ çapraz derleme), GitHub Actions (NDK ile sherpa derlemesi), Playwright (arayüz testi), JVM (DSP testi) | |

## 2. Kodun yazılış düzeni (her özellikte aynı)

**Akış:** Arayüz düğmesi → JS fonksiyonu → `runJob(kind, args)` (ya da düzenleyicide `tlRunJob(label, kind, args)` → meşgul çubuğu + Vazgeç) → `AND.job(kind, json)` → `MainActivity` iş kuyruğu → Java (`Workspace.op`, `Separator`, `Studio`…) → sonuç JSON → JS. Uzun işler `WorkService` ile ön planda; indirmeler `Bg.java` (arka planda sürer, uygulama kapanıp açılsa da: `bgStart(kind, key, label, args)`).

**Düzenleme işlemleri:** `Workspace.op(c, {path, op, …})` tek giriş; çıktı yeni bir WAV, `{path, ch, report, same?}`. Takma adlar: `aigate/ailevel/snddip → gainmap`, `aipauses → cut`.

**Yetenekler:** Java açılışta `CAPS` gönderir (`sep, asr, hd, tag, uvr, uvrOk, ramMb, cores, weak, sdk…`); JS düğmeleri buna göre gösterir.

**Proje verisi (`TL.p`):** `tracks[]`, `clips[]`, `markers[]`, `master`, `lyrics`. Klip alanları (yeni olanlar kalın):
- `c.fx` = **düzenleme geçmişi** (`{op, r, b?}`; son 5 adım geri dönülebilir) — efekt listesi DEĞİL.
- **`c.rk`** = efekt listesi `[{k, on, v:{…}}]` (5.0). Canlı efektler (`RACK`: reverb, delay, comp, widen, eightd, double, lofi) çalarken uygulanır; hesaplananlar (`HEAVY`) dosya üretir.
- **`c.src`** (efektsiz orijinal), **`c.hc`** (hesaplanmış sonuç önbelleği, anahtar `yol|JSON(args)`), **`c.hcOut`** (5.1).
- **`c.pan`** (−100…100, 4.13).
- `TL.p.master = {on, style, amt}` (5.0), `TL.p.lyrics` (5.5), `TL.p.markers = [{t}]`.
- Kayıt (record) alanları: **`r.mkey`** (ton/tempo), **`r.lyrics`** (sözler), **`r.smap = {ch:[[t, akor]], sec:[[a, b, tür, grup]]}`** (5.7); `META_KEYS` ile kalıcı. `r.chOn` / `r.lyrOn` oturumluk.

**Ayarlar (localStorage `ls`):** `lite` (hafif mod; ayarlanmamışsa `CAPS.weak`), `lowLat`, `accel`, `monFx {rev, echo}`. `applyLite()` hepsini Java'ya iletir (`AND.setLite / setLowLat / setAccel`).

**Pencereler:** `openModal(html)` her açılışta **yeni bir `#modalCard`** üretir (5.14; eskiden aynı öğe kullanılıyordu ve `addEventListener` dinleyicileri birikiyordu). `confirmBox`, `chooseBox`, `tlSliders` yardımcıları.

**Çeviri:** Her sürümün yeni metinleri yeni bir `i18nN.js`'te, 9 dilde; `t('anahtar', {n})`. "Yenilikler" kartı `NEWS` listesi + `news_<anahtar>`.

**Kodlama kuralları:** en hafif ve en kısa kod; Java 8 (anonim sınıflar); yorumlar İngilizce ve "neden"i anlatır; her sürüm ayrı commit ("5.x: …").

---

## 3. Sürüm sürüm: özellik → kod → test

Her başlıkta: **Ne** (kullanıcı tarafı), **Nerede** (arayüz yolu), **Kod**, **Nasıl çalışır**, **Test et**.

### 4.12 — HD ses
- **Ne:** Boğuk / telefon kalitesindeki sesin tizlerini geri getirir. YZ grubu > HD ses.
- **Kod:** `Hd.java` (`Hd.Stage` düğümü, `bandwidth`, `download`), `sdsep.c` (`Hd_open/run/close`, ORT C API `GetApi(16)`), iş `hdGet` (indirme), op `hd`.
- **Nasıl:** Sesin bant genişliği ölçülür (ana banttan 25 dB düşüş = geçiş; 50 dB'de 15,5 kHz'e kadar doluysa "zaten HD"). Model 16 kHz'lik sürümü duyar, 80 mel → 2048/512 STFT üretir; geçişin altı **orijinal kalır**, üstü modelden (500 Hz yumuşak geçiş). 256 kare bloklar + 32 kare bağlam.
- **Ölçüm:** tiz bölgede orijinale uzaklık 25 → 14 dB; 62 sn stereo ≈ 5,6 sn (masaüstü).
- **Test et:** İlk kullanımda ~56 MB iner (uygulama kapatılıp açılınca devam). Telefon kalitesi seste tiz gelir, alt bant değişmez. İyi seste "zaten HD". Geri al. Efekt listesinden eklenince "zaten HD" mesajı çıkmaz (bilinen küçük eksik).

### 4.13 — Stereo efektler ve pan
- **Ne:** 8D ses, Genişlet, Çiftle (double), Lo-fi, Ters çal; klip başına sağ-sol.
- **Kod:** `dsp/Fx.java` (`EightD`, `Widen`, `Double`, `LoFi` düğümleri), `Workspace` op'ları `eightd/widen/double/lofi/reverse`, `Timeline` pan (eşit güç), JS `tlPan`.
- **Test et:** Mono klibe uygulanınca stereo olur; 8D kulaklıkta döner; ters çal uzunluğu korur; pan kaydırıcısı canlı ve dışa aktarmada.

### 4.14 — Proje araçları
- **Ne:** Projeyi eşitle; kayıt menüsünde WhatsApp için gönder ve MP3'e çevir; çoklu seçimde Araç uygula.
- **Kod:** JS `tlLevelAll` + iş `tlLoud` (`Workspace.clipLoud`: klip başına LUFS) → klipler −16 LUFS, müzik konuşmanın 6 dB altı. `copyItem('wa')` m4a 48 kbps mono + paylaş. `batchOp` (seçili kayıtlara tek araç).
- **Test et:** Farklı seviyedeki klipler eşitlenir; WhatsApp paylaşımı açılır, dosya küçük; birkaç kayda aynı araç uygulanır.

### 4.15 — Zayıf telefonlar
- **Ne:** Ayarlar > Hafif mod; YZ çökmesinden sonra "hafif moda geç" teklifi; daha hızlı gelişmiş gürültü temizliği.
- **Kod:** `Device.java` (`lite`, `threads()`, `workers()`, çökme notu `ai_running`, `model(kind, a)`), `Denoiser.Par` (DPDFNet çevrimdışı 15 sn parçalar, 1 sn ön / 0,5 sn arka bağlam, paralel işçiler; yerel `createOff/runOff/destroyOff`), CAPS `ramMb/cores/weak`, JS `applyLite`.
- **Ölçüm:** çevrimdışı 1,45× daha hızlı; 2 çekirdekte ~2×; parça ekleri −31…−42 dB.
- **Test et:** Hafif mod açık/kapalı süre farkı; uzun kayıtta YZ gürültü; ek yerlerinde tık yok.

### 4.16 — Temizlik
- **Ne:** Stüdyo sesi penceresi (güç + oda sesi); Dolgu kelimeleri ("yani", "şey"…); Öksürük/gülme/hapşırık bul (kıs ya da kes).
- **Kod:** JS `studioAsk`, `tlFwords` + iş `fwords` (`Workspace.fillerWords`, dile göre `FW` listesi, Türkçe İ/ı normalizasyonu), `Sounds.java` (CED tiny, 1 sn pencere / 0,5 sn adım, olayı yüksek çekirdeğine daraltır) + iş `sounds`, indirme `tagGet`; `snddip → gainmap`; `tlPickCut`.
- **Test et:** Dolgu kelimeleri bulunur ve listeden seçilerek silinir; gülme / öksürük bulunur, çevresindeki sözcükler kesilmez.

### 4.17 — Hazır efektler ve harmoni
- **Ne:** 10 hazır efekt (sıcak, fragman, stadyum, mağara, yan oda, telsiz, eski radyo, hayalet, rüya, koro); Harmoni/Düet.
- **Kod:** `web/fx-presets.js` (`SD_FX.voices`, `SD_FX.chains[{id, steps}]`), op `chain` (adımları sırayla), JS `tlChain`, `tlHarmony` (perde kopyası boş katmana, düette pan ±40).
- **Test et:** Her hazır efekt çalışır (masaüstünde 10'u da geçti); harmoni yeni katmanda.

### 5.0 — Efekt listesi (rack) + Mastering
- **Ne:** Klip başına sıralanabilir, açılıp kapanabilir canlı efektler; projeye mastering (Doğal, Sıcak, Bant, Yüksek) ve A/B.
- **Kod:** JS `RACK` tanımları, `tlRack`, `tlRackAdd`, `tlMaster` (`TL.abOff`); `Timeline` (RackOut zinciri, kuyruk süreleri, master öğesi karışımın sonunda); `dsp/Master.java` (200 Hz / 3 kHz çıkarmalı bölme, bant başına kompresör, ton rafları, bant doygunluğu / yumuşak kırpma, seviye takipçisi hedef −18 / −17 / −13, −1 dBFS limiter).
- **Test et:** Efektler çalarken duyulur, sıra değişince ses değişir, kapatınca gider, dışa aktarmada var; mastering stilleri farklı seviyede; A/B anahtarı.

### 5.1 — Hesaplanan efektler listede
- **Ne:** Stüdyo sesi, hazır efekt, YZ gürültü, gürültü, müzikte gürültü, netleştir, yankı, sert s, autotune, HD da efekt listesine girer; ayarı sonradan değişir.
- **Kod:** `HEAVY`, `HV` (parametreler), `RKDEF`, `tlHeavyAdd`, `tlHeavyRun` (sıradaki her ağır efekti `wsOp` ile hesaplar, `c.hc` önbelleğinden yeniden kullanır), `hvBake` (gerektiğinde kalıcılaştırır), `tlClean` src/hc dosyalarını korur.
- **Test et:** Ayarı değiştirince yeniden hesaplanır; aynı ayara dönünce anında gelir (önbellek); kapatınca orijinal ses.

### 5.2 — Düzenlemeler efekt listesiyle uyumlu
- **Ne:** Efektli klipte kes / duraklama kısalt / kırp / hız / ekle yapılınca efekt listesi kaybolmaz.
- **Kod:** `HV_FOLLOW = ['cut', 'pauses', 'aipauses', 'trim', 'speed', 'insert']`, `hvSteps`, `hvFollow` (aynı işlem `c.src`'ye de uygulanır); Java trim ve duraklama işlemleri `keep` aralıklarını raporlar; geçmişte "Buraya dön" efekt listesini de geri getirir (`tlState` içinde `rk/src/hc/hcOut`).
- **Test et:** Efekt ekle → kes → efekti kapat: kesik orijinal ses gelir (kesik korunur).

### 5.3 — Pratik, akort aleti, ses aralığı
- **Ne:** Çalarda Pratik: hız, perde (−6…+6), A-B döngü (vuruşa yapıştır), ton/BPM bul, parçalara ayır; Akort aleti; Ses aralığı.
- **Kod:** JS `openPractice`, `PR` durumu, `openTuner(range)`; `Player.setPitch` (`AND.plPitch`); `Tuner.java` (AudioRecord 24 kHz, YIN, `window.__sdTune(hz, db)`, `AND.tunerStart/Stop`); `musicKey` + `beats` işleri.
- **Ölçüm:** akort hatası < 1,2 sent.
- **Test et:** Perde değişince hız değişmez; A-B döngü vuruşa oturur; akort gitarda doğru nota; ses aralığı en alt ve en üst notayı gösterir.

### 5.4 — Düşük gecikme + eko izleme
- **Ne:** Ayarlar > Düşük gecikme; kayıtta kulaklığa yankı / eko.
- **Kod:** `Recorder` (`lowLat`: VOICE_PERFORMANCE, 5 ms blok; `monitorFx(rev, echo)`), `Timeline.lowLat` (40 ms tampon, BL 256, düşük gecikmeli AudioTrack), `AND.setLowLat`, `AND.recMonFx`, JS `monFxBox`.
- **Test et:** Üstüne kayıtta gecikme azalır; kulaklıkta eko duyulur ama kayda girmez; hoparlörde geri besleme olmaz.

### 5.5 — Şarkı sözleri ve karaoke
- **Ne:** Sözleri çıkar (çalarda ve düzenleyicide akar); tek dokunuşla karaoke projesi.
- **Kod:** JS `lyricsGet` (`wsInit` → `wsOp sepdenoise` (vokal) → `transcribe` → `r.lyrics`), `lyrShow` (`#plLyr`, `#tlLyr`), `karaoke(r)` (yeni proje, `tlSep('two')`, vokal −60 dB kapalı ya da −14 dB rehber, `TL.p.lyrics`).
- **Test et:** Sözler doğru zamanda; karaoke projesi açılır, vokal kısık / kapalı; sözler alta.

### 5.6 — Müzikte gürültü + kaliteli ayırma
- **Ne:** Müzikte gürültü azalt (yumuşak); Sesi ayır > Kaliteli 2 parça (deneysel).
- **Kod:** op `musicdenoise` (en çok 10 dB, aşırı çıkarma 0,7, takip yok); müzik klibinde gürültü penceresi bunu önce sunar (`tlIsMusic`); Temizle müzik amacında önerir (`clIssues`, `CL_ORDER`). UVR: `sdsep.c` (eşlik modeli boşsa UVR yapılandırması), `Separator.split` (`hq`, 10 sn parça, çıktı sırası ters: önce enstrüman), `uvrFile / uvrFits (RAM ≥ ~5,5 GB ve hafif mod kapalı) / uvrDownload`, iş `uvrGet`, CAPS `uvr/uvrOk`.
- **Ölçüm (masaüstü, UVR):** ~2,2 GB bellek, ~1,5× gerçek zaman, sentetik karışımda vokal SDR ≈ 20 dB.
- **Test et:** Müzikte enstrümanlar bozulmaz; UVR'de vokal / müzik yer değiştirmemiş, uygulama kapanmıyor, süre.

### 5.7 — Akorlar ve bölümler
- **Ne:** Pratik > Akorlar ve bölümler > Bul: çalarken akor + sıradaki + bölüm adı; bölüm çiplerine dokununca döngü.
- **Kod:** `dsp/SongMap.java`: 8192/2048 spektrum → chroma (60 Hz–2 kHz) + bas chroma (40–250 Hz) + 6 bant seviye; vuruş başına 24 üçlü akor şablonu (+bas kökü), Viterbi (değişim cezası 0,04); 4 vuruşluk ölçülerde öz-benzerlik → 4+4 dama tahtası yenilik eğrisi → tepe = bölüm sınırı → başlangıç hizalı benzerlikle gruplama → en çok tekrar eden yüksek grup Nakarat, diğer tekrarlar Kıta, ilk/son tekil Giriş/Çıkış, ortadaki tekil Köprü. `Studio.songMap` (vuruşlar `Beats.track`), iş `songMap`. JS `smSections`, `#plChd` aralığı (150 ms), `[data-psm]`, `[data-psec]`.
- **Ölçüm:** sentetik şarkıda akorların ve bölümlerin hepsi doğru, 33 ms.
- **Test et:** Akor zamanlaması; 7'li akorlar en yakın majör/minöre iner (bilinen sınır); bölüm döngüsü.

### 5.8 — Stüdyo sesi ve hazır efektler geri alınabilir
- **Kod:** `hvArgs(f)` (studio → `target` + `ai`; chain → `SD_FX` tarifinden `steps`), Java op `studio` (Temizle zinciri: hum, denoise, dereverb, mouth, plosive, deess, clarify, eq, comp, loud — tek adım; `amb` %).
- **Test et:** İkisi de efekt listesinde; hazır efekt listeden başkasıyla değiştirilebilir; kapatınca orijinal.

### 5.9 — Yapay zekâ hızlandırıcısı
- **Ne:** Ayarlar > Yapay zekâ hızlandırıcısı (deneysel) + "Bu telefonda ölç".
- **Kod:** `sdsep.c` `prov` ("cpu" / "nnapi"; Spleeter, UVR, DPDFNet sherpa sağlayıcısı; LavaSR için `OrtSessionOptionsAppendExecutionProvider_Nnapi`), `Separator.accel/setAccel/accelTest` (8 sn sentetik sesi iki yolla ayırır: ms + fark dB), `Device.accel`, iş `accelTest`, `AND.setAccel`. JS: sonuç bildirim olarak; %15'ten az hızlı, çalışmadı ya da fark > −20 dB ise kendini kapatır.
- **Test et:** Ölçüm sonucu (telefona göre değişir); açıkken ayırma / gürültü doğru.

### 5.10 — Yeni perde motoru (Signalsmith Stretch)
- **Ne:** Perde değişen her yerde (Hız ve ton, Ses değiştir, ince/kalın ses, harmoni, autotune) daha temiz ses.
- **Kod:** `android/native/sdtp/` (`signalsmith-stretch.h`, `linear/stft.h, fft.h` — MIT; `sdtp.cpp` JNI: `create/destroy/seekLength/seek/process`; C++ çalışma zamanı parçaları elle: operator new/delete, random_device, `__libcpp_verbose_abort`). Derleme: `zig c++ -target aarch64-linux-musl -fno-exceptions -fno-rtti`, sahte `libc.so/libm.so` ile bağlanır (gerçek bionic çalışma zamanında). `dsp/Stretch.java`: `make()` → perde ≠ 0 ve kütüphane yüklüyse Stretch, değilse eski `TimePitch`; `outputSeek` ile hizalama, çıktı uzunluğu tam `girdi/hız`.
- **Ölçüm (gidiş-dönüş spektral hata dB, düşük iyi):** müzik +4 → eski 5,9 / yeni 3,9; +7 → 9,8 / 3,7; konuşma +4 → 2,9 / 3,2; +7 → 3,9 / 3,3; yalnız hız konuşma → 2,7 / 6,6 (bu yüzden yalnız tempoda eski motor). Masaüstünde akış sarmalayıcısı `exact()` ile uzunluk / hizalama birebir.
- **Test et:** 64 bit telefonda yüklenme (yüklenmezse otomatik eski motor; ama yerel kodda çökme yakalanamaz → kapanırsa bildir); ±5 yarım ton müzikte temizlik.

### 5.11 – 5.13 — Lisans
- 5.11: okuma sesleri lisans güvenli seslere geçti. 5.12: Piper YZ sesleri tamamen kaldırıldı; `Tts.java` yalnız telefonun TextToSpeech'i; eski indirilen sesler `Tts.cleanup` ile silinir; işler `ttsGet/ttsDelete/ttsStatus` kaldırıldı.
- 5.13: sherpa-onnx TTS kapalı derlendi (Actions). Doğrulama: APK'da espeak-ng iç dizgileri yok; JNI'de yalnız TTS'e ait 9 sembol eksik; C API sembolleri aynı; libonnxruntime birebir aynı.
- **Test et:** Metinden ses telefon sesiyle; kütüphaneyi kullanan her YZ aracı (yazıya dök, metinden düzenle, ayır, 4 parça, YZ gürültü, konuşmacılar, akıllı araçlar, ııı/eee, dolgu, öksürük, altyazılı video) çalışıyor; Lisanslar ekranında espeak-ng / Piper yok.

### 5.14 — İnceleme düzeltmeleri + istekler
- `openModal` temiz kart (birikmiş dinleyiciler); `NEWS` = n5rack, n5master, n5practice, n5chords, n5hq, n5pitch, n5fix; Yapıştır yalnız kopya varken; müzikte uğultu eşiği 10; EQ / uğultu / hız sonrasında `Limiter(-1 dB)`; tekrar tanımlı eski fonksiyonlar (`renderEdit`, `renderTlBar`, `tlQBar`, `tlFxList`) ve TTS Java sınıfları silindi.
- İstekler: klip kartı ilk satırı Böl, Sil, Kopyala, Ses, Hız, **Çoğalt**; işaretler: `tlMarkerAt` (oynatma çizgisi işaretteyse araç "İşareti kaldır"), bayrağa ikinci dokunuş siler, "Tüm işaretleri sil" (`tlDelMarkers`, 2+ işaret).

---

## 4. Başvuru tabloları

**Yeni işler (`job` kind):** `tlLoud`, `fwords`, `sounds`, `tagGet`, `hdGet`, `uvrGet`, `songMap`, `accelTest` (+ mevcut `wsOp`, `tlSep` `hq`, `transcribe`, `musicKey`, `beats`).
**Yeni Workspace op'ları:** `hd`, `reverse`, `eightd`, `widen`, `double`, `lofi`, `chain`, `musicdenoise`, `studio`, `snddip` (→ gainmap).
**Yeni köprü (`AND.`) yöntemleri:** `setLite`, `aiCrash`, `plPitch`, `tunerStart`, `tunerStop`, `recMonFx`, `setLowLat`, `setAccel`.
**Yeni CAPS:** `hd`, `tag`, `uvr`, `uvrOk`, `ramMb`, `cores`, `weak`.
**Yeni Java sınıfları:** `Hd`, `Device`, `Sounds`, `Tuner`, `dsp/Fx`, `dsp/Master`, `dsp/SongMap`, `dsp/Stretch`; sherpa AudioTagging sınıfları.
**Yeni çeviri dosyaları:** i18n45 … i18n61.

## 5. Bilinen sınırlar / riskli noktalar
- Telefonda hiç denenmedi: yeni sherpa kütüphanesi (5.13), Signalsmith yerel kodu (5.10), NNAPI (5.9), UVR (5.6), HD ses, düşük gecikme, akort.
- 32 bit telefonlar: MP3 (LAME) ve Signalsmith yok; yedekler devrede.
- UVR çok bellek ister (≥6 GB RAM'e kapalı).
- Akorlar yalnız majör/minör; bölüm adları tahmin.
- HD ses efekt listesinden eklenince "zaten HD" mesajı yok. Tek dosya düzenleyicisinde ve toplu uygulamada efekt listesi yok.
- Masaüstü test ortamının sahte köprüsü bazı alanları döndürmez (spektrum, projeyi eşitle) — bunlar gerçek hata değil.

## 6. Yapılan masaüstü testleri (tekrar edilebilir, `tests/ui-5x`, `tests/desktop-5x`)
- ESLint (tanımsız isim yok), eksik id yok, 9 dilde eksik çeviri yok, sayfada dış adres yok (yalnız geri bildirim bağlantısı).
- Java `native` yöntemleri ↔ .so sembolleri: eksik yalnız kullanılmayan TTS ve (32 bitte) arm64'e özel kütüphaneler.
- 149 araç denemesi (3 durum) + menü / ayar / Pratik taraması: gerçek JS hatası yok.
- `Workspace.op` 40 işlem: mono konuşma, stereo müzik, çok sessiz kayıt — hepsi hatasız, süre doğru; 10 hazır efekt zinciri çalıştı.
- 5.14 testleri (`t17.py`) 10/10.

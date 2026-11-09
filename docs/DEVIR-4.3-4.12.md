# Ses Defteri — devir notu (4.3 → 4.12)

Bu not, Ses Defteri projesinde bir önceki Claude oturumunda yapılan tüm değişiklikleri devam edecek Claude için özetler.
Önce repodaki `CLAUDE.md`'yi oku: çalışma kuralları, derleme ve güvenlik oradadır. Ayrıntılı geçmiş `docs/DURUM.md`'de, kod haritası `docs/MIMARI.md`'de.

- Repo: `github.com/muhammeedsari/ses-defteri`
- Çalışma dalı: `claude/project-thread-jz2p6y` (yerel dal adı `sd43`). Push: `git push origin HEAD:claude/project-thread-jz2p6y`
- Son sürüm: **4.12** (versionCode 67), son i18n dosyası: `web/i18n45.js`

## Kullanıcıyla çalışma kuralları (mutlaka uy)
- Türkçe konuş; kısa, teknik olmayan bir dil kullan.
- Kodu en optimize ve en hafif şekilde yaz. Test etme; kullanıcı kendisi test eder ya da "test et" der. Yeni bir DSP/YZ algoritmasında masaüstünde hızlı ölçüm yapmak serbesttir.
- Kullanıcı hata veya arayüz sorunu bildirdiğinde **önce sadece incele ve listele**. Kodu kullanıcı "düzelt" deyince değiştir.
- Zaten var olan bir özelliği "yeni" diye önerme. Önceki oturumda üç kez oldu: 4 parçaya ayırma, doğal TTS, metinden düzenleme. Önermeden önce `body.html`'de ara.
- Kullanıcı tasarım isterse önce resmini çiz (HTML taslak → PNG). Kullanıcı seçtikten sonra uygula.

## Sürüm teslim adımları (her değişiklikte)
1. Kod değişikliği.
2. `android/AndroidManifest.xml`: versionCode +1 ve versionName. `web/body.html`: `const VERSION = '…'`.
3. Yeni metin gerekiyorsa yeni bir `web/i18nN.js` oluştur (9 dilin hepsi). Dosyayı iki yere kaydet: `body.html` içindeki `<script src>` satırı ve `android/build_apk.py` içindeki `for js in [...]` listesi.
4. Derle: `SD_KS=…/ses-defteri-imza.p12 SD_KS_PASS=… SD_VENDOR=…/vendor python3 android/build_apk.py`. Ardından `android/__pycache__`'yi sil.
5. JS kontrolü: `web/page.html` içindeki `<script>` bloklarını `node --check` ile denetle. Eksik id kontrolünde `wsTime` zararsızdır.
6. `git grep` ile parolanın repoda olmadığını doğrula. **Anahtar ve parola asla repoya girmez; yeni anahtar üretilmez.**
7. `CLAUDE.md`'de sürüm numarasını ve son i18n dosyasını güncelle.
8. Commit at: mesaj İngilizce olsun ve sürüm numarasıyla başlasın ("4.12: …"). Ardından push.
9. APK'yı `SesDefteri-X.apk` adıyla kullanıcıya gönder ve Türkçe kısa bir özet yaz: ne değişti, neyi test etmeli.

## Sürüm sürüm yapılanlar

### 4.3 — Yeni düzenleme ekranı
- Sabit üst satır, renkli ve adlandırılmış katmanlar.
- Klip/proje kartı: araç döşemeleri ve puanlı "Akıllı kontrol".
- "Tüm araçlar" çekmecesi.
- Yapıştırılan klip orijinal zamanına gelir.

### 4.4 — Arayüz düzenlemesi
**Genel ekran**
- İnce üst çubuk: sekmeler başlık satırına taşındı.
- Kayıtlar ve Müzik listeleri yenilendi:
  - Hızlı döşemeler yalnızca kayıt yokken görünür.
  - Kayıt satırlarında yuvarlak simge var.
  - Aynı şarkılar tekilleştirilir ve "×n" rozeti gösterilir.
  - Harf yerine nota görseli kullanılır.
  - "Kaydet" etiketli FAB eklendi.

**Zaman çizelgesi**
- Klipler artık üst üste binmiyor.
- En fazla **4 katman** var ve katmanlar kutuyu doldurur.
- Katman başlığı "A seçeneği": 32 px sütun, renkli şerit ve simge. Simgeye dokunulunca katman menüsü açılır.
- Cetvel hatası düzeltildi.
- Klip adları klip üzerinde görünür.

**Araç kartı ve gruplar**
- "Kes" ve "Katman" araçları kaldırıldı.
- Kartın ilk satırı 6 sütun: Böl, Sil, Kopyala, Ses, Hız, Geçiş.
- İkinci satır 6 kategori: ✦ YZ, Temizle, Onar, Ses-Ton, Kırp, Efekt. Klibin türüne göre ilgili kategori vurgulanır.
- Çekmece başlığı sabit kalır; simgeler birbirinden ayrışır.

**Kayıt ekranı**
- Seviye sözcükleri ve çubuk: sessiz / kısık / iyi / yüksek / patlıyor.

### 4.4.1
- Dil ekranı önce İngilizce açılır, sonra seçilen dile geçer.
- Varsayılan renk **mor**; uygulama simgesi de mor.
- Kayıt ekranındaki Ses/Müzik anahtarı ile amaç kartları (podcast, YouTube…) birbirine bağlı.
- Hareketler:
  - Sürükleme zamanı kaydırır.
  - Basılı tutup sürükleme (380 ms) klibi taşır.
  - Başka bir klibin üzerine bırakılınca iki klip yer değiştirir.
  - Katman menüsünde "Yukarı/Aşağı taşı" var.
- Boşalan katmanlar kapanır.
- Ekolayzer takılması düzeltildi: efektler yerinde güncellenir, ayrıca headroom eklendi.

### 4.5 / 4.5.1 / 4.5.2
- **Stüdyo sesi**: tek dokunuşla temizlik zinciri.
- Daha akıllı müzik algılama; daha geniş "Katman ekle" satırı.
- **Yazıya dök** ve **Metinden düzenle** (Whisper) YZ grubuna eklendi.
- **4 parçaya ayır** artık ayrı bir YZ aracı; ayırmadan önce boş katman kontrolü yapılır.

### 4.6
- **Ton ve tempo bulucu**: `dsp/KeyFind.java`, chroma + Krumhansl yöntemi. Sonuç solfej adlarıyla gösterilir.
- Doğal TTS varsayılan oldu; TTS YZ grubunda.

### 4.7 / 4.7.1 / 4.7.2
- **Gelişmiş ııı/eee silme** (YZ grubu): adaylar Whisper ile tek tek doğrulanır.
- Klasik ııı/eee silme Kırp grubunda kalır.
- "Sesi netleştir" YZ grubuna taşındı.

### 4.8 / 4.8.1
- **Altyazılı video (audiogram)**: `Audiogram.java`. Canvas kareleri EGL/GLES ile H.264 kodlayıcıya gider; altyazılar Whisper'dan gelir; AAC ses karıştırılıp birleştirilir.
- Ayarlara "Teknik bilgileri göster" seçeneği eklendi (dB, LUFS, dosya biçimi). Kapalıyken bu bilgiler gizlenir (`html.tech` sınıfı).

### 4.9 / 4.10 / 4.10.1 — Akıllı temizlik araçları
- **Akıllı sessizlik**: Silero VAD ile ses kapısı (gainmap).
- **Akıllı duraklama kısaltma**.
- **Konuşmacıya göre ses eşitleme**: konuşmacı ayırma + gainmap.
- **Akıllı müzik kısma (ducking)**: konuşma algılayıcı kısmayı yönetir. Klipler değiştikçe sessizce yeniden hesaplanır.
- **Akıllı nefes kısma**.
- Yenilikler kartı ve düzenleme ipucu güncel hareketlere uyduruldu.

### 4.11 — Akıllı araçlar klasiklerden iyi hâle getirildi
Masaüstünde bir TTS röportajıyla karşılaştırma yapıldı ve şu düzeltmeler geldi:
- Akıllı kapı ve duraklamada **hibrit karar**: VAD ile seviye ölçümü aynı fikirde olmalı.
- Nefesler Whisper ile doğrulanır; cümle sonları artık kesilmiyor.
- Whisper'ın "[Müzik]", "*…", "♪" gibi etiketleri konuşma sayılmaz.

### 4.11.1 — Değişiklik işaretleri
- Düzeltilen bölgeler zaman çizelgesinde görünür hâle geldi: renkli bant, 5 px çubuk ve kesim noktalarında üçgenler.
- Stüdyo sesi ve akıllı düzeltmelerden sonra da işaretlenir.
- İşaretleri tanıtan ipucu bir kez gösterilir.

### 4.11.2
- "Ses ayırma ile gürültü sil" adı **"Arka plan müziğini sil"** oldu. YZ grubundan **Temizle** grubuna taşındı ve "Deneysel" rozeti aldı.
- **Düzenleme geçmişi**: Orijinal ve her adım listelenir.
  - Her adımda ▶ Dinle ve "Buraya dön" düğmeleri var.
  - Yalnızca son 5 adım geri dönülebilir; eski dosyalar silinir.

### 4.12 — HD ses (YZ grubu)
- Model: LavaSR (Apache-2.0), Topping1/LavaSR-ONNX deposunun Alpha-v0.1 sürümü.
  - Dosyalar: `enhancer_backbone.onnx` + `.data` ve `enhancer_spec_head.onnx` + `.data`, toplam ~56 MB.
  - İlk kullanımda GitHub'dan arka planda indirilir (`hdGet` işi), `files/hd` klasörüne kaydedilir. Sonrası internetsiz çalışır.
- Algoritma, `Hd.java` içindeki `Hd.Stage` düğümü:
  - Ses 48 kHz'e getirilir. Modelin duyduğu sürüm 16 kHz'e indirilip yeniden 48 kHz'e çıkarılır.
  - Mel özelliği: 80 bant, Slaney, 44.1 kHz ölçeği, 0–8 kHz.
  - Model, STFT spektrumunu (2048/512) üretir.
  - Sesin kendi üst sınırının altı **orijinal kalır**; üstüne modelin bandı eklenir (500 Hz yumuşak geçiş).
  - İşlem 256 karelik bloklar ve 32 karelik bağlamla yapılır; bu sayede uzun kayıtlar az bellekle işlenir.
- Bant genişliği tespiti (`Hd.bandwidth`):
  - Geçiş noktası, ana banttan (300–3000 Hz) 25 dB düşülen yerdir.
  - 50 dB eşiğinde ses 15.5 kHz'e kadar doluysa "zaten HD" denir ve işlem yapılmaz.
- Yerel köprü: `libsdsep.so` artık ONNX Runtime C API'yi doğrudan çağırıyor (`Hd.open/run/close`, `GetApi(16)`). APK'daki `libonnxruntime.so` (1.28.2) kullanılıyor.
  - Derleme notları `android/native/README.md`'de.
  - Başlık dosyaları ORT v1.28.2 `include/onnxruntime/core/session/` klasöründen alınır; repoya konmadı.
- Masaüstü ölçümü:
  - Telefon kalitesindeki seste tiz bölgenin orijinale uzaklığı 25 dB'den 14 dB'ye indi; alt bant hiç değişmedi.
  - 62 saniyelik stereo kayıt yaklaşık 5,6 saniyede işlendi.
- **Cihazda henüz test edilmedi.**

## Önemli kod noktaları (body.html, JS)
- Bu fonksiyonlar tek bir IIFE içinde. Sonra tanımlanan fonksiyon öncekini geçersiz kılar.
- `tlGroups(c)`: grupların içeriği.
  - grp_ai: studio, hd, clarify, aigate, aipauses, ailevel, aibreaths, aiduck, stt, tedit, aidenoise, sep, sep4, spk, flai, tts, keyfind, capvid.
  - grp_clean: denoise, sepdenoise (deneysel), gate, dereverb, hum.
- `tlDo(k)`: döşeme tıklamaları.
- `tlEffect(c, op, extra)`: genel efekt yolu. Sırasıyla şunları yapar:
  - Kırpılmış klibi önce render eder (wsOp).
  - Geçmiş adımını kaydeder (tlState/tlStep).
  - Değişen bölgeyi işaretler (chg, cuts, tlDiff).
  - Raporu yazar (wsReport).
- Klip alanları:
  - `chg`: değişen aralıklar.
  - `cuts`: kesim noktaları.
  - `fx`: `{op, r, b?}` listesi; `b`, işlemden önceki durumdur (son 5 adımda tutulur).
- `CAPS`: Java'dan gelen yetenekler (sep, asr, spk, hd…).
- `bgStart(kind, key, label, args)`: arka plan işleri (model indirme vb.).

## Önemli kod noktaları (Java)
- `Workspace.op(...)`: tüm düzenleme işlemleri. Takma adlar:
  - aipauses → cut (`talk` verilirse aiPauses).
  - aigate/ailevel → gainmap.
  - Yeni: `hd`.
- `Speakers.vad`: Silero VAD, APK içindeki `asr/silero_vad.onnx` dosyasından.
- `Transcriber.recognize(float[])`: Whisper.
- `Timeline.Engine`: `talk` aralıkları kısmayı (ducking) yönetir.
- `Player`: ekolayzer (`toneOn`, `retune`, `headroom`).
- İş kayıtları: MainActivity (`job`) ve Bg.java (arka plan: dlModel, spkGet, hdGet…).

## Açık konular / fikirler
- HD ses cihazda denenmeli:
  - Hız.
  - İndirme ekranı: uygulama kapatılıp açıldığında indirme devam ediyor mu?
  - "Zaten HD" mesajı.
  - Geri al.
- İleri araştırma fikri: gelişmiş yankı azaltma modeli. Henüz araştırılmadı.
- Test düzeneği notu: Playwright testlerinde sayfa `build_apk.web_page()` ile kurulur. Bu fonksiyon sayfaya `[hidden]` sarmalayıcısını da ekler.

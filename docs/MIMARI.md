# Ses Defteri — Mimari ve kod haritası

## Dizinler
```
web/            arayüz: head.html (sayfa başı + ana CSS'in ilk kısmı), extra.css, body.html (HTML + tüm JS), i18n.js … i18n15.js
                page.html = head.html + extra.css + "</style>\n\n" + body.html (build_apk.py üretir)
android/        AndroidManifest.xml, build_apk.py, fetch_vendor.sh, res/, src/
android/src/app/sesdefteri/        uygulama (Java 8)
android/src/app/sesdefteri/dsp/    saf Java ses işleme
android/src/app/sesdefteri/asr/    Whisper + VAD sarmalayıcı (Transcriber, Keywords)
android/src/com/k2fsa/sherpa/onnx/ sherpa-onnx JNI'si için Java karşılıkları (Kotlin API'nin imzaları)
android/native/ sdsep.c, sdmp3.c, prebuilt/<abi>/*.so, stub/stdio.h
tests/          java/ (eski ölçüm testleri), desktop/harness (T19, TChain, TEq …), desktop/jsonshim (org.json yerine)
docs/           DURUM.md, MIMARI.md
vendor/         (git dışı) jniLibs/<abi>, sepm/, dn/, asr/silero_vad.onnx, fonts/, fix-webm-duration.js, android-34.jar
```

## Java sınıfları (satır sayısı yaklaşık)
| Sınıf | Görev |
|---|---|
| MainActivity (900) | WebView, `SesDefteriAndroid` köprüsü, izinler, paylaşım, iş kuyruğu (`pool`, tek iş parçacıklı `studioPool`, `bgPool`) |
| Workspace (1270) | Düzenleme işlemleri, teşhis, Temizle, kaydet/dışa aktar (WAV/M4A/MP3 + ID3), zil sesi, bölümler, dolgu sesleri |
| Timeline (320) | Proje motoru: `Engine` (karışım, ducking, canlı EQ/bas/tiz), `Play` (AudioTrack), `render` |
| Player (720) | MediaPlayer kuyruğu, karıştırma/tekrar, yumuşak geçiş, boşluksuz, A-B, atlamalar, uyku, EQ, LoudnessEnhancer, `enqueue` |
| PlayerService / RecordService / WorkService | ön plan servisleri |
| Recorder (210) | MediaRecorder (AAC), kaynak seçimi (MIC / VOICE_RECOGNITION=AGC kapalı / UNPROCESSED), aramada duraklatma |
| MediaLib (440) | MediaStore: listeleme, oluşturma, silme, etiket, zil sesi |
| AudioTools (530) | çözme, dalga formu (peaks), render |
| Studio (220) | biçim tespiti, ritim, sesten metne (`transcribe`) |
| AsrModels (135) | Whisper base/small indirme/silme/durum |
| Separator (145) | Spleeter (JNI `libsdsep`), model dosyası kopyalama (`model()`) |
| Denoiser (90) | DPDFNet akışlı temizleme (JNI `libsdsep`) |
| Speech (175) | 20 ms çerçeve taraması: duraklama, nefes, dolgu sesi |
| Mp3 | LAME JNI |
| RoomTest, Str (yerel metinler), ShareProvider |

`dsp/`: Node (zincir: `a.to(b)` b'yi döndürür), Pipeline, Fft, Spectral (gürültü/EQ/yankı, `tracking`), Analyzer (gürültü profili, LTAS, kanal, uğultu, kırpılma), Enhancer, Stages (Filters/eq5, ClickRepair, ChannelMap, Compressor, Delay, Reverb…), Biquad, Limiter, Loudness, TruePeak, Resampler, TimePitch, Beats, Mix, Wav.

## JS ↔ Java köprüsü (`AND.*` = `window.SesDefteriAndroid`)
Senkron/yardımcı: `isDark caps themePref setTheme getLang setLang setBars copyText requestMedia requestMic openSettings listMedia pickAudio pickVideo openSoundSettings vibrate takeImported share shareText deleteMedia openUrl`
Kayıt: `recStart(mode,name,opts{q,src,call,agc}) recPause recResume recInfo recLevel recStop recCancel`
Çalar: `plQueue(json,index,startMs,autoplay) plEnqueue(json,next) plPlay plPause plSeek plRate plNext plPrev plShuffle plRepeat plStop plState plGain plEq plXfade plLoop plSkips plSleep`
Zaman çizelgesi: `tlPlay(clipsJson,sec) tlUpdate tlStop tlPos`
İşler: `job(kind,argsJson)` → id; ilerleme/sonuç `window.__sd...` geri çağrılarıyla; `cancelJob(id)`. JS tarafında `runJob(kind, args, onProg)` Promise döner (`.jobId`).
Dosya aktarımı: `beginFile appendFile` (+ `commit` işi).

**İş türleri (`kind`):** `peaks commit diagnose clean wsInit wsOp wsSave wsExport wsClean tlAdd tlRender tlSep tlClean transcribe beats fillers chapters keepRender loudness ringtone roomTest asrStatus asrDownload asrDelete`.
Ağır işler `studioPool`'da ve WorkService ile; `peaks commit wsClean tlClean roomTest loudness asrStatus asrDelete` hafif; `args.bg` → `bgPool`.

**`wsOp` işlemleri (`op`):** `cut denoise(+profile, learn, track) aidenoise sepdenoise gate hum trim clicks mouth plosive deess channels stereo eq eqpreset level loud speed mix insert pauses(max,keep) breaths declip dereverb comp reverb delay`. Ortak: `strength` (0.6 Hafif / 1 Dengeli / 1.4 Güçlü). Sorun yoksa `{same:true, report:{none:true}}` döner (JS "sorun bulunamadı" der).
Dönüş: `{path, dur(ms), ch, rate, lufs, lra, peak, report}`.

## Zaman çizelgesi veri yapısı (localStorage `proj`)
```js
{ name, tracks: [{vol, mute, duck, duckDb}], markers: [{t, label}],
  clips: [{ id, tr, path, ch, dur, in, out, at, gain, fi, fo, name,
            eq: [5 dB] | null, bass, treble,        // canlı, tahribatsız (Timeline.Engine)
            fx: [{op, r}],                           // "Bu parçada yapılanlar"
            beats? }] }
```
`tlMix()` yerel motora `[{path, at, in, len, g, fi, fo, duck, eq, bass, treble}]` gönderir. Geri al/yinele: `TL.undo/redo` (JSON anlık görüntüleri). Pano: localStorage `tlClip` (dosyası `tlClean`'de korunur).

## Depolama
- **IndexedDB** stores: `recordings, blobs, categories, chunks, kv, peaks, texts, beats`.
- **localStorage** (`ls.get/set`): `accent agc artist askedMedia asrModel autoClean autoEnh autoText callPause chans clStrength cover eqLast filter fmt gapless lang learnVoice libNoteX micNoteX micSrc minSong mode mview nameTpl normalize pauseMax playEq playlists pps precompute proj purpose rate recQuality roomLearn roomProf showBeats silCut silLen skipSil sort startTab tab target textLang theme tlClip tlSnap trashDays xfade`.
- Yerel tercihler: SharedPreferences `sesdefteri` (`lang`, tema).
- Dosyalar: `files/proj` (klipler), `files/sep` (Spleeter/DPDFNet modelleri), `files/asr-base`, `files/asr-small`, kayıtlar MediaStore `Music/Ses Defteri`.

## body.html modül haritası (aranacak başlıklar)
state (`const S = {`), i18n `t()`, native glue (`nativeCall`, `runJob`), NativeAudio (çalar sarmalayıcı), settings (`openSettings`), recordings list, music library (`renderMusic`, `songRow`, `playList`), player (`openPlayer`, A-B, uyku), speech-to-text (`transcribeItem`, `asrModelUi`), timeline (`TL`, `tlLoad`, `tlMix`, `tlEffect`, `tlFx`, `TL_FX`, `tlEq`, `tlTone`, `tlFxList`, `renderEdit`, `drawTl`), Clean (`CL`, `clIssues`, `CL_ORDER`), batch (`openBatch`), 1.8 helpers (`fmtArgs`, `fileName`, çöp), recorder (`R`, `finishNative`, `silenceCut`), 2.3 block (pano, çalma listeleri, gizlilik), boot (`needLang`, `showLangPick`, `boot`).

## Masaüstü test
```bash
cd tests/desktop
mkdir -p /tmp/sd/c4 /tmp/sd/jcls && javac -d /tmp/sd/jcls jsonshim/org/json/*.java      # çalışma zamanında android.jar'ın boş org.json'u yerine
J=$VENDOR/android-34.jar
javac -nowarn -source 8 -target 8 -encoding UTF-8 -cp $J -d /tmp/sd/c4 \
  ../../android/src/app/sesdefteri/*.java ../../android/src/app/sesdefteri/dsp/*.java ../../android/src/app/sesdefteri/asr/*.java \
  ../../android/src/com/k2fsa/sherpa/onnx/*.java harness/*.java          # harness/R.java üretilen R'nin yerini tutar
java -cp /tmp/sd/jcls:/tmp/sd/c4:$J app.sesdefteri.TChain giris.wav denoise,denoise@track=false,gate x
# YZ işlemleri (aidenoise): sherpa-onnx linux-x64 C API (pip install sherpa-onnx → sherpa_onnx/lib) + x64 derlenmiş libsdsep.so;
#   LD_LIBRARY_PATH=<sherpa lib> java -Djava.library.path=<libsdsep x64 klasörü> -Dsd.dn=<dpdfnet onnx> …
```
`TChain` işlemleri sırayla uygular (`op@anahtar=değer`), sonuçları `R op yol rapor` olarak yazar; Python (numpy/soundfile) ile SDR ölçülür.

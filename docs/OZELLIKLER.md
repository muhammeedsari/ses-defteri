# Ses Defteri — sürüm sürüm tüm özellikler (1.3 → 5.14.1)

Bu dosya uygulamanın her sürümde kazandığı özellikleri tek yerde toplar. Teknik ayrıntılar için: `DURUM.md` (1.3–4.0), `DEVIR-4.3-4.12.md`, `DEVIR-4.12-5.14.md`, kod haritası `MIMARI.md`.
Sonradan değişen ya da kaldırılan özellikler yanında **(şimdi: …)** notuyla belirtildi.

## Uygulamanın bugünkü hâli (özet)

- **Kayıtlar:** kayıt (amaç, kalite, canlı gürültü bastırma, düşük gecikme, kendini duyma), liste, kategoriler, arama, sıralama, çöp kutusu, menü ve çoklu seçim.
- **Müzik çalar:** çalma listeleri, sıra, EQ, eşit ses yüksekliği, sessizliği atla, A-B döngüsü, uyku zamanlayıcı, akorlar ve bölümler, sözler, Pratik, akort aleti.
- **Düzenle (stüdyo):** en çok 4 katmanlı zaman çizelgesi, 6 araç grubu (YZ, Temizle, Onar, Ses-Ton, Kırp, Efekt), canlı efekt listesi, mastering, düzenleme geçmişi, dışa aktarma (MP3, M4A, WAV, FLAC, OGG/Opus, zil sesi, videoya koy, altyazılı video).
- **Yapay zekâ (internetsiz):** yazıya dökme, metinden düzenleme, ses ayırma (2 ve 4 parça, kaliteli 2 parça), YZ gürültü temizliği, konuşmacı ayırma, HD ses, akıllı temizlik araçları, öksürük/gülme bulma.
- **9 dil:** Türkçe, İngilizce, Almanca, Fransızca, İspanyolca, Portekizce, Rusça, Arapça, Çince.

---

## 1.x — Temel uygulama

| Sürüm | Eklenenler |
|---|---|
| 1.3–1.4 | Kayıtlar listesi, kategoriler, işaretler ve notlar, dalga görünümü, oynatıcı. **Sesi düzenle** sekmesi: adım adım işlemler (otomatik işlem yok). |
| 1.5 | **Çok katmanlı zaman çizelgesi:** taşı, kısalt, böl, çoğalt, ses düzeyi, soldurma, parça başına efekt, geri al/yinele, canlı karışım, dışa aktarma. (şimdi: en çok 4 katman) |
| 1.6 | **Sesi ayır** (Spleeter: konuşmayı koru / kaldır / ikisini ayır), konuşma gelince müziği kısma (ducking), zaman çizelgesine kayıt, ritim işaretleri. |
| 1.7 | Kayıt amacı (YouTube, Podcast, Toplantı, Sesli not, Müzik), oda testi, kayıtta canlı uyarılar (patlama vb.), aramada otomatik duraklatma. **Temizle:** teşhis → öneriler → tek seferde uygula → önce/sonra. |
| 1.7.1 | YZ ile gürültü silme, parça birleştirme, katmanı sessize alma rozeti, "sorun bulunamadı" bilgisi. |
| 1.8 | **MP3 dışa aktarma** (LAME), dosya adı şablonu, çöp kutusu, Ayarlar (kayıt kalitesi, mikrofon, temizlik şiddeti, biçim, depolama, lisanslar). |
| 1.9 | Duraklama kısaltma, nefes kısma, patlamış ses onarımı, 5 bantlı EQ ve hazır ayarlar, toplu temizleme. |

## 2.x — Stüdyo araçları

| Sürüm | Eklenenler |
|---|---|
| 2.0 | Mıknatıs/yapışma (kılavuz çizgi, titreşim), işaretçiler, punch-in (araya kayıt), bölümler, **zil sesi yapma**, **videodan ses alma**. |
| 2.1 | Çalar: eşit ses yüksekliği, sessizliği atla, A-B döngüsü, EQ, yumuşak geçiş, boşluksuz çalma, uyku zamanlayıcı. Yankı azaltma, kompresör, reverb, delay, MP3 etiketleri ve kapak resmi, oda gürültüsü hafızası. |
| 2.2 | Sesten metne iyileştirmeleri (dil = uygulama dili, arka planda metne dökme, gelişmiş model), **metinden düzenleme**, tekrar çekim ve "ııı" tespiti, konuya göre bölümler. |
| 2.3 | Projeler arası kopyala/kes/yapıştır, sessizlik ekle, **ağız sesi temizleme**, mono→stereo ve dışa aktarmada kanal seçimi, **çalma listeleri** ve "sıraya ekle", kısa sesleri yoksay, otomatik kazanç seçimi, kayıttan sonra uzun sessizlikleri kısalt, açılış sekmesi, gizlilik ve geri bildirim. |
| 2.3.1 | İlk açılışta **dil seçme ekranı** (varsayılan İngilizce). |
| 2.3.2 | Hızlı işlemlerde çökme düzeldi; klip EQ'su canlı ve geri alınabilir. |
| 2.4 | **Gelişmiş YZ gürültü temizliği** (DPDFNet; Hafif/Dengeli/Güçlü), "Gürültüyü buradan öğren", değişen gürültüyü takip eden temizlik, kelime arası sessizlik (ses kapısı), bas ve tiz güçlendirme, parça başına efekt geçmişi. |
| 2.5 | Sesten metin modeli **isteğe bağlı indirilir** (APK 168 → 74 MB). Ses ayırma ile gürültü silme ayrı efekt oldu. |
| 2.6 | **Ses seviyesi çizgisi** (klip içinde noktalarla ses aç/kıs), otomatik çapraz geçiş, **Videoya koy** (projenin sesini bir videoya yerleştirme). |
| 2.7 | **Sesi netleştir** (tek dokunuş), **ses efektleri**: radyo, telefon, megafon, robot, ince ses, kalın ses. |
| 2.8 | Düzenle'de **ses ölçer** (spektrum + LUFS + tepe), uygulama simgesinden **hızlı kayıt kısayolu**. |
| 2.9 | **Metronom** (vuruş çizgileri, yapışma), kayıttan önce bir ölçü sayma, **kayıt gecikmesi ölçümü** (üst üste kayıtlar hizalı). |

## 3.x — Kayıt motoru, YZ ve kullanım kolaylığı

| Sürüm | Eklenenler |
|---|---|
| 3.0 | **Spektrum görünümü** ve parmakla kutu çizip **spektrumdan silme** (zil, öksürük gibi tek sesleri çıkarma). |
| 3.1 | **Yeni kayıt motoru:** WAV, FLAC ve M4A kayıt, kayıtta canlı ölçer, kulaklıktan **kendini duyma**. **FLAC ve Opus (OGG) dışa aktarma.** Galeri'den bir videoyu "Paylaş" ile uygulamaya alma. |
| 3.2 | **Konuşmacı ayırma:** her konuşmacı ayrı katmana, seviyeleri eşitlenir (model indirilir). |
| 3.3 | **Autotune** (perde düzeltme, ton bulma ile) ve **hazır ses kütüphanesi** (giriş müziği, geçiş sesi, alkış vb., uygulama içinde üretilir). |
| 3.4 | **Metinden ses:** telefonun sesi ya da indirilebilen doğal sesler. (şimdi: yalnız telefonun sesi; doğal sesler 5.12'de lisans nedeniyle kaldırıldı) |
| 3.4.1–3.4.5 | Test ve düzeltme sürümleri: kırpınca komşu parçaya binmeme, listelerde kaydırma, WebView çökmesinden kurtulma ve çökme notu, sade hata mesajları, sesten metin dili Otomatik, tam lisans listesi. |
| 3.4.6 | Çalarda donma/takılma düzeltmeleri, ses odağı (başka uygulama çalınca durma), uyarlanır sessizliği atla, uyku zamanlayıcısında geri sayım, kopya kayıt sorunu. |
| 3.4.7 | Belirgin EQ hazırları, çalar ekranında EQ ve geçiş düğmeleri, yeni dışa aktarma ekranı, işlemde **Vazgeç**, uzun iş bitince **bildirim**, hazır kategoriler uygulama dilinde, Temizle'de müzik uyarısı. |
| 3.5 | Kayıt/şarkı **menüsü** (yeniden adlandır, kategoriler, çalma listesi, paylaş, kopya, Kayıtlar↔Müzik taşı, sanatçı, kapak resmi, sil), **çoklu seçim**, iki sıra düzenleme araçları, efektten sonra **Önce/Sonra** ve değişen yerlerin gösterimi, **tema rengi**. |
| 3.5.1 | Uzun üst üste binmede parça boş katmana geçer. |
| 3.6 | Uzun basınca yeni seçim modu (Sil, Paylaş, Çalma listesi, Taşı, Daha), Bilgi ekranı, **sıralama** (tarih, ad, süre, boyut, sanatçı, albüm), sade dışa aktarma (yalnız seçili bölüm, zil sesi), Android 16 uyumu. |
| 3.7 | **Kayıt kalitesi hazırları** (Otomatik/Yüksek/Orta/Düşük/Özel), canlı gürültü bastırma ve yankı engelleme, **Bluetooth mikrofon**, kayıt klasörü seçimi, amaç kartları. |
| 3.8 | Yeni **ekolayzır** (10 bant, kendi hazırların), **Ses değiştir**, **4 parçaya ayırma** ve parça karıştırıcı. (şimdi: EQ telefonun standart ekolayzırı, 4.0.1) |
| 3.9 | Parçaya dokununca **hızlı çubuk** (ekle, sil, kopyala, yapıştır, böl), zaman çizelgesinde kayıt düğmesi (çalarken o noktadan kayıt), kayıt öncesi **geri sayım**, kayıtta diğer katmanları çal. |

## 4.0–4.2.1 — Tanıtım, arka plan işleri, ses kalitesi

| Sürüm | Eklenenler |
|---|---|
| 4.0 | İlk açılışta 5 sayfalık **tanıtım**, güncellemeden sonra **Yenilikler** kartı, her ekranda bir kez **ipuçları**, ana ekranda **hızlı erişim kartları**. |
| 4.0.1 | EQ telefonun standart ekolayzırına döndü (ses tuşları yine çalışıyor), daha güçlü hazırlar. Silmede **"Medya yönetimi" izni** ve toplu silmede tek onay. **Bildirim izni** ve bildirimde yüzde. Yumuşak geçiş ve geri sayım **Ayarlar'a** taşındı. Dışa aktar ekranı dar ekrana sığıyor, basılı tutunca **titreşim**, geri hareketi, metne dök ekranında model açıklaması, Temizle'de Podcast uyarısı. |
| 4.1 | **Arka plan işleri:** model indirme ve metne dökme ekrandan bağımsız sürer; uygulamadan çıkınca ya da kapatınca devam eder; **durdur/devam**, kaldığı yerden sürdürme, yüzde gösterimi. Bu sırada uygulama kullanılabilir. |
| 4.2 | **Ses kalitesi:** çok daha güçlü **bas ve tiz** (−12…+18, harmonik bas, parlaklık), **Audacity kalitesinde "Gürültüyü buradan öğren"** (süre, azaltma ve hassasiyet ayarlı), yeniden yazılan **ağız sesi temizleme** (yalnız tiz bölgede), daha akıllı **sessizliği atla**, **eşit ses yüksekliği** EQ ekranında, kayıtta **canlı gürültü bastırma** gerçekten çalışıyor. |
| 4.2.1 | İnceleme düzeltmeleri: arka plan işlerinde durdur-devam takılması, toplu silmede kısmi sonuç ve Android 10 desteği, stereo seste gürültü öğrenme kanal başına, "Sesi düzenle" üst çubuğu dar ekranda taşmıyor. |

## 4.3–4.17 — Yeni düzenleme ekranı ve akıllı araçlar

| Sürüm | Eklenenler |
|---|---|
| 4.3 | **Yeni düzenleme ekranı:** sabit üst satır, renkli ve adlandırılmış katmanlar, klip/proje kartı, puanlı **Akıllı kontrol**, "Tüm araçlar" çekmecesi, yapıştırılan klip orijinal zamanına gelir. |
| 4.4 | İnce üst çubuk, yenilenen Kayıtlar/Müzik listeleri (aynı şarkılar tek satırda "×n"), "Kaydet" düğmesi, en çok 4 katman, 6 araç + 6 grup (YZ, Temizle, Onar, Ses-Ton, Kırp, Efekt), kayıtta seviye sözcükleri. |
| 4.4.1 | Varsayılan renk **mor**, sürükleyerek zaman kaydırma, basılı tutup klip taşıma ve yer değiştirme, katmanı yukarı/aşağı taşıma, boşalan katman kapanır. |
| 4.5 | **Stüdyo sesi** (tek dokunuşla temizlik zinciri), daha iyi müzik algılama, Yazıya dök ve Metinden düzenle YZ grubunda, 4 parçaya ayırma ayrı araç. |
| 4.6 | **Ton ve tempo bulucu** (Do, Re… adlarıyla). |
| 4.7 | **Gelişmiş ııı/eee silme** (her aday Whisper ile doğrulanır); klasik olan Kırp grubunda. Sesi netleştir YZ grubunda. |
| 4.8 | **Altyazılı video** (sesin dalgası + altyazılar, galeriye kaydedilir). "Teknik bilgileri göster" ayarı. |
| 4.9–4.10 | **Akıllı sessizlik**, **akıllı duraklama kısaltma**, **konuşmacıya göre ses eşitleme**, **akıllı müzik kısma**, **akıllı nefes kısma**. |
| 4.11 | Akıllı araçlar daha isabetli (konuşma algılayıcı + seviye birlikte karar verir), cümle sonları kesilmiyor. |
| 4.11.1 | **Değişiklik işaretleri:** düzeltilen yerler zaman çizelgesinde renkli bantla görünür. |
| 4.11.2 | "Arka plan müziğini sil" Temizle grubunda (deneysel). **Düzenleme geçmişi:** her adımı dinle, son 5 adıma geri dön. |
| 4.12 | **HD ses:** telefon kalitesindeki sese tizleri geri getirir (model ~56 MB, isteğe bağlı). |
| 4.13 | **8D ses, Genişlet, Çiftle, Lo-fi, Ters çal**, klip başına **sağ-sol (pan)**. |
| 4.14 | **Projeyi eşitle** (tüm klipler aynı seviyede, müzik konuşmanın altında), kayıt menüsünden **WhatsApp için gönder** ve **MP3'e çevir**, çoklu seçimde **araç uygula**. |
| 4.15 | **Hafif mod** (zayıf telefonlar), YZ çökmesinden sonra "hafif moda geç" teklifi, YZ gürültü temizliği daha hızlı. |
| 4.16 | Stüdyo sesinde güç ve oda sesi ayarı, **dolgu kelimeleri silme** ("yani", "şey"…), **öksürük/gülme/hapşırık bulma** (kıs ya da kes). |
| 4.17 | **10 hazır efekt** (sıcak, fragman, stadyum, mağara, yan oda, telsiz, eski radyo, hayalet, rüya, koro), **Harmoni/Düet**. |

## 5.x — Efekt listesi, müzik ve lisans temizliği

| Sürüm | Eklenenler |
|---|---|
| 5.0 | Klip başına **canlı efekt listesi** (reverb, delay, kompresör, genişlet, 8D, çiftle, lo-fi; sıra, aç/kapa, ayar). **Mastering** (Doğal, Sıcak, Bant, Yüksek; miktar, A/B). |
| 5.1 | Hesaplanan efektler de (gürültü azalt, YZ gürültü, netleştir, yankı azalt, sert s, autotune, HD ses…) listeye girer; ayarı sonradan değişir ya da kapatılır. |
| 5.2 | Efekt listesi varken kes, kırp, hız: efektler kaybolmaz. Geçmişte "Buraya dön" efekt listesini de geri getirir. |
| 5.3 | Çalarda **Pratik** (hız, perde, A-B döngü, vuruşa yapıştır, ton/tempo bul), **akort aleti**, **ses aralığı ölçümü**. |
| 5.4 | **Düşük gecikme** ayarı, kayıtta kulaklıkta **yankı/eko izleme**. |
| 5.5 | **Şarkı sözlerini çıkar** (çalarken akan sözler), **Karaoke** (vokal kapalı ya da kısık, sözler altta). |
| 5.6 | **Müzikte gürültü azalt**, **Kaliteli 2 parça** ayırma (deneysel, ≥6 GB RAM, model ~59 MB). |
| 5.7 | **Akorlar ve bölümler:** çalarken anlık ve sıradaki akor, Giriş/Kıta/Nakarat çipleri (dokununca döngü). |
| 5.8 | Stüdyo sesi ve hazır efektler de efekt listesine girer, geri alınabilir. |
| 5.9 | **Yapay zekâ hızlandırıcısı** (deneysel) ve "Bu telefonda ölç". |
| 5.10 | **Yeni perde motoru** (Signalsmith): ton değişiminde çok daha temiz ses (64 bit telefonlar). |
| 5.11–5.13 | Lisans temizliği: doğal okuma sesleri kaldırıldı, metinden ses yalnız telefonun sesiyle; YZ kütüphanesi GPL parça olmadan yeniden derlendi, APK ~2 MB küçüldü. |
| 5.14 | İnceleme düzeltmeleri (Pratik'te "Akorları gizle" ikinci açılışta çalışıyor, Yapıştır yalnız kopya varken). Klip kartında Geçiş yerine **Çoğalt**; **işaret kaldırma** (İşareti kaldır, bayrağa iki dokunuş, Tüm işaretleri sil). |
| 5.14.1 | Mastering'de (Doğal, Sıcak) ani yüksek seslerde çatırtı giderildi; ton değiştirme iptal edilince bellek boşaltılıyor. |
| 5.15–5.17 | Not alma (fotoğraf, hızlı işaretler), canlı yazı (seda / Whisper), seda ile hızlı Türkçe yazıya dökme. |
| 5.18 | **Arama ve arşiv:** arama sonucuna dokununca o andan çalar; **Süz** (tarih, süre, işaret türü, konuşmacı, metni olanlar); kayıt metninde **konuşmacılar** ve ad verme (adlar telefonda, sonraki kayıtlarda önerilir); **bulutsuz yedek** (Ayarlar › Depolama: tek .zip, geri yükleme; aynı dosya varsa notları tamamlanır). |
| 5.19–5.21 | **Podcast stüdyosu:** podcast şablonu (giriş, ara jingle, kapanış; projeye tek dokunuş), **Bölümü hazırla** (Stüdyo sesi → dolgu kelimeleri → sessizlikler → şablon → seviye → müzik kısma → yayın seviyesi -16 LUFS; her adım ayrı geri alınır), **Bölümler ve notlar** (işaret adları MP3'e ID3 CHAP/CTOC bölümü olarak yazılır, bölüm notu kopyala/paylaş), dışa aktarmada yayın seviyesi anahtarı, altyazılı videoda 30/45/60 sn tanıtım parçası, **Çift kaydı hizala** (iki telefonun kaydı, `Align.java`, 1 ms). Masaüstü testi: `tests/desktop/feature-sweep.js` (5.15–5.21, 51 senaryo). |

## Bilinen sınırlar (5.14.1)

- 32 bit telefonlarda MP3 dışa aktarma ve yeni perde motoru yok (eski motor kullanılır; M4A ile dışa aktarılabilir).
- Akorlar yalnız majör/minör.
- Kaliteli 2 parça ayırma çok bellek ister.
- Başka uygulamadan paylaşınca kopya kayıt ve dalga titremesi sorunları telefonda henüz doğrulanmadı.

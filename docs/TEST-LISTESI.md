# Ses Defteri — telefon test listesi (5.14)

Kutuları test ettikçe işaretle: `[x]` çalıştı, `[!]` sorun var (yanına not yaz). Sorunları ekran görüntüsüyle birlikte bildir.
**Öncelik:** 🔴 hiç telefonda denenmedi / riskli, 🟡 yeni ama basit, ⚪ eski özellik (kısa kontrol yeter).

Hazırlık: Telefonda 5.14'ü kur (eski sürümün üstüne). Bir konuşma kaydı (1–2 dk, biraz gürültülü), bir şarkı (3–4 dk) ve kulaklık hazır olsun. İnternet açık olsun (bazı modeller ilk seferde iner).

---

## A. Açılış ve genel 🟡
- [ ] Uygulama açılıyor, çökmüyor.
- [ ] "5.14 sürümünde yenilikler" kartı çıkıyor ve **5.x özelliklerini** anlatıyor (efekt listesi, mastering, Pratik, akorlar…).
- [ ] Ayarlar → Lisanslar: espeak-ng ve Piper **yok**; Signalsmith, UVR, LavaSR, CED var.
- [ ] Uygulama öncekinden ~2 MB küçük (bilgi amaçlı).

## B. Yeni yapay zekâ kütüphanesi (5.13) 🔴 — en önemli bölüm
Kütüphane metin okuma olmadan yeniden derlendi. Bu araçların hepsi onu kullanıyor; her biri bir kez denenmeli:
- [ ] **Yazıya dök** (konuşma kaydında): metin doğru çıkıyor.
- [ ] **Metinden düzenle**: bir cümle silince ses de kesiliyor.
- [ ] **Sesi ayır** (2 parça): vokal ve müzik ayrı katmanlara geliyor.
- [ ] **4 parçaya ayır**: vokal, davul, bas, diğer.
- [ ] **Gelişmiş YZ gürültü temizliği**: gürültü azalıyor.
- [ ] **Arka plan müziğini sil** (Temizle grubu, deneysel).
- [ ] **Konuşmacılar**: konuşmacılar ayrı katmanlara ayrılıyor.
- [ ] **Akıllı sessizlik / Akıllı duraklama / Konuşmacıya göre eşitle / Akıllı nefes / Akıllı müzik kısma** (YZ grubu): her biri bir kez.
- [ ] **Gelişmiş ııı/eee silme** ve **Dolgu kelimeleri**.
- [ ] **Öksürük/gülme bulma**: model iniyor, sonuç veriyor (bulamazsa "bulunamadı" demesi de doğru).
- [ ] **Altyazılı video**: video oluşup galeriye kaydediliyor.
- [ ] Hiçbirinde uygulama kapanmıyor. Kapanırsa hangisi olduğunu yaz.

## C. Yeni perde motoru (5.10) 🔴 (64 bit telefon)
- [ ] Şarkıda **Hız ve ton** → ton +5 ve −5: ses temiz, uygulama kapanmıyor.
- [ ] Sadece hız değiştirme (ton 0): eskisi gibi çalışıyor.
- [ ] **Ses değiştir**: karakterler ve ton.
- [ ] **Ses efektleri → İnce ses / Kalın ses**.
- [ ] **Harmoni / Düet**: yeni katmana perdeli kopya geliyor.
- [ ] Autotune (perde düzeltme) çalışıyor.

## D. Yapay zekâ hızlandırıcısı (5.9) 🔴
- [ ] Ayarlar → "Bu telefonda ölç": sonuç mesajı çıkıyor (işlemci X sn · hızlandırıcı Y sn). **Mesajı bana yaz.**
- [ ] Anahtar açıksa ses ayırma ve gürültü temizliği yine doğru çalışıyor.

## E. Müzik araçları (5.6) 🔴
- [ ] Şarkı klibinde Temizle → Gürültü azalt: ilk seçenek **"Müzikte gürültü azalt"**; enstrümanlar bozulmuyor.
- [ ] Temizle (müzik amacı): hışırtılı şarkıda "müzikte gürültü azalt" öneriliyor; temiz şarkıda uğultu **önerilmiyor**.
- [ ] (RAM ≥ 6 GB ise) Sesi ayır → **Kaliteli 2 parça (deneysel)**: model iniyor (~60 MB), vokal ve müzik doğru katmanlarda (yer değiştirmemiş), uygulama kapanmıyor. Süreyi yaz.

## F. Akorlar ve bölümler (5.7) 🔴
- [ ] Çalar → Pratik → "Akorlar ve bölümler" → Bul: sonuç geliyor.
- [ ] Çalarken akor ve sıradaki akor görünüyor; zamanlaması doğru mu? Bilinen bir şarkıda akorlar doğru mu?
- [ ] Bölüm çipleri (Giriş, Kıta, Nakarat…) mantıklı yerlerde mi; dokununca o bölüm döngüye giriyor mu?
- [ ] Pratik'i kapatıp yeniden aç: "Akorları gizle/göster" **tek dokunuşta** çalışıyor (5.14 düzeltmesi).

## G. 5.14 değişiklikleri 🟡
- [ ] Parça seçince kartın ilk satırı: Böl, Sil, Kopyala, Ses, Hız, **Çoğalt** (Geçiş yok). Çoğalt kopyayı yanına koyuyor.
- [ ] Geçiş hâlâ Ses-Ton grubunda duruyor.
- [ ] İşaret ekle → oynatma çizgisi işaretteyken araç listesinde "İşareti kaldır" → siliniyor.
- [ ] Bayrağa dokun → "bir kez daha dokun" yazısı → ikinci dokunuşta siliniyor.
- [ ] 2+ işaret varken "Tüm işaretleri sil" çalışıyor. Geri al işaretleri geri getiriyor.
- [ ] Kopyalanmış parça yokken araç listesinde "Yapıştır" görünmüyor; kopyalayınca görünüyor.
- [ ] Pratik'i açıp kapattıktan sonra Ayarlar'da anahtarlar sorunsuz çalışıyor.

## H. Efekt listesi ve hesaplanan efektler (5.0–5.8) 🔴
- [ ] Bir klibe canlı efektler ekle (reverb, delay, kompresör, genişlet, 8D, çiftle, lo-fi): çalarken duyuluyor, sıra değiştirilebiliyor, kapatılabiliyor.
- [ ] Hesaplanan efektler: **Stüdyo sesi**, **Hazır efekt**, gürültü azalt, YZ gürültü, netleştir, yankı azalt, sert s, autotune, HD ses listeye giriyor; ayarı değişince yeniden hesaplanıyor; kapatınca orijinal ses geri geliyor.
- [ ] Hazır efektin listesinden başka bir efekt seçilebiliyor (5.8).
- [ ] Efekt listesi varken kes / duraklama kısalt / kırp / hız: efektler kaybolmuyor (5.2).
- [ ] Düzenleme geçmişinde "Buraya dön" efekt listesini de geri getiriyor.
- [ ] Dışa aktarılan dosyada efektler var.
- [ ] **Mastering**: 4 stil, miktar, A/B karşılaştırma; dışa aktarmada uygulanıyor.

## I. Pratik, akort, karaoke, sözler (5.3–5.5) 🔴
- [ ] Pratik: hız, perde (−6…+6), A-B döngü, vuruşa yapıştır, Ton bul (ton + BPM).
- [ ] Akort aleti: gitar/ses ile nota ve sent doğru.
- [ ] Ses aralığı ölçümü.
- [ ] Sözleri çıkar: çalarken sözler akıyor; gizle/göster.
- [ ] Karaoke: yeni proje, vokal kapalı / kısık seçimi, sözler alta; kulaklıkla kayıt.

## J. Kayıt ve düşük gecikme (5.4) 🔴
- [ ] Ayarlar → Düşük gecikme açıkken düzenleyicide üstüne kayıt: gecikme az mı?
- [ ] Kayıtta kulaklıkta yankı/eko izleme.
- [ ] Normal kayıt, duraklat/devam, bitir (eski özellik, kısa kontrol).

## K. Diğer yeni araçlar (4.13–4.17) 🟡
- [ ] 8D, Genişlet, Çiftle, Lo-fi, Ters çal; parça sağ-sol (pan).
- [ ] Projeyi eşitle (Ses-Ton / proje araçları).
- [ ] Kayıt menüsü → WhatsApp için gönder; MP3'e çevir.
- [ ] Çoklu seçim → Daha → Araç uygula.
- [ ] Hafif mod; YZ çökmesi sonrası "hafif moda geç" teklifi.
- [ ] HD ses: model iniyor (~56 MB), telefon kaydında tizler geliyor; zaten iyi seste "zaten HD" mesajı.
- [ ] Metinden ses: **yalnız telefonun kendi sesi** ile okuyor (Türkçe, İngilizce, Arapça…).

## L. Eski özellikler (kısa genel kontrol) ⚪
- [ ] Kayıtlar listesi, arama, sıralama, kategoriler, çöp kutusu.
- [ ] Müzik listesi, çalma listeleri, sıraya ekle, karıştır/tekrar, uyku zamanlayıcı, EQ, boşluksuz/yumuşak geçiş, A-B.
- [ ] Temizle (teşhis → öneriler → uygula → önce/sonra).
- [ ] Düzenleyici: taşı, böl, sil, kopyala/yapıştır, ses düzeyi, soldurma, ses çizgisi, EQ, bas/tiz, geri al/yinele, katman menüsü, metronom, spektrum, punch-in.
- [ ] Dışa aktar: MP3/M4A/WAV/FLAC/OGG, zil sesi, videoya koy.
- [ ] Dil değiştirme, tema/renk, Ayarlar'daki diğer anahtarlar.
- [ ] Bildirimler (uzun iş bitince), arka planda indirme devam ediyor mu.

## M. Google Play öncesi son kontrol
- [ ] Yukarıdaki 🔴 maddelerin hepsi geçti.
- [ ] Hiçbir ekranda İngilizce/anahtar adı gibi garip metin kalmadı (örn. `news_…`, `{n}`).
- [ ] İnternet kapalıyken uygulama açılıyor ve indirilmiş modellerle çalışıyor.

# Ses Defteri — Konuşma Yol Haritası (5.15 → 6.x)


> **Güncel sıra (2026-11-12, Doktor'un kararı; aşağıdaki aşama numaralarından önce gelir, sürüm numaraları buna göre kaydı):**
> 1. 5.14.2 + 5.14.3 telefon testi (test listesi 0-X / 0-Y), gerekirse 5.14.4 düzeltme
> 2. 5.15 Kayıt sırasında not alma (Aşama 1)
> 3. Türkçe doğruluk karşılaştırması (Samsung, Otter, Whisper base/small, seda-v0.1; aynı kayıtlar)
> 4. 5.16–5.17 Canlı yazıya dökme (Aşama 2; yol A = VAD + Whisper, yedek seda-v0.1 Apache 2.0)
> 5. 5.18 Arama ve arşiv (Aşama 4)
> 6. 5.19–5.21 Podcast stüdyosu (Aşama 5); ses seviyesi hedefleri zaten Temizle'de, MP3 bölümleri (ID3 CHAP/CTOC) araya girebilir
> 7. 5.22 Play'e hazırlık (Aşama 0): podcast stüdyosundan SONRA
> 8. 6.x Kalite ve hız (Aşama 6)
> 9. **En son:** Özet ve başlıklar (Aşama 3), önce modelsiz özet, dil modeli ölçümden sonra
>
> Araştırma raporu: https://claude.ai/artifact/17fzuwrQ9RuPGuUoKyCbjq

## Hedef
Her Android telefonda çalışan, internetsiz, reklamsız, aboneliksiz konuşma stüdyosu:
kaydet → temizle → yazıya dök → düzenle → paylaş.
Rakipler: Otter, Notta, Plaud (yazıya dökme ve not), Samsung/Pixel kaydedicileri, Ferrite ve Adobe Podcast (podcast).

## Değişmez kurallar (her aşamada geçerli)
- İnternet yok: tüm işlem telefonda. İnternet yalnız kullanıcı isteyince model indirmek için.
- Veri toplama, analitik, reklam, hesap yok.
- Otomatik işlem yok: her iyileştirme kullanıcının bastığı ayrı bir düğme. "Tek dokunuş" zincirleri de kullanıcı başlatır ve her adım geri alınabilir.
- Kod hafif ve optimize. Test yalnız kullanıcı "test et" deyince. Hata bildirimlerinde önce incele ve listele, "düzelt" denince değiştir.
- Yeni model eklemeden önce: lisansı ticari kullanıma uygun mu, boyutu ve hızı orta seviye bir telefonda kabul edilebilir mi? Önce masaüstünde ölç, sonra karar ver.
- Her sürüm tek bir ana işe odaklansın.

## Nerede kazanabiliriz, nerede kazanamayız
- Kazanabiliriz: gizlilik, aboneliksizlik, her marka telefonda çalışmak, Türkçe ve 9 dil, kayıttan yayına kadar tek uygulama.
- Kazanamayız: sunucu gücü isteyen en yüksek kalite ve hız, canlı toplantı botu (Zoom/Meet), bulutta birlikte çalışma. Bunlarla yarışmıyoruz.

---

## Aşama 0 — Play'e hazırlık (5.15)
Amaç: uygulama yayımlanabilir olsun. Bu aşamada yeni özellik yok.
1. Derleme betiğine AAB çıktısı ekle (Play APK kabul etmiyor). APK çıktısı da kalsın.
2. Her yapay zekâ modelinin lisansını listele (model, lisans, ticari kullanım: evet/hayır). Uygun olmayanı çıkar ya da değiştir.
3. Stüdyoya "Müzik" grubu ekle: Sesi ayır, 4 parçaya ayır, Ton ve tempo, Ritim, Metronom, Autotune, Harmoni/Düet buraya taşınsın. Yapay zekâ grubu konuşma araçlarına odaklansın. Çalardaki Pratik yerinde kalsın.
4. İlk açılış: amaç kartları (Podcast, Toplantı, Ders, Sesli not, Müzik) giriş kapısı olsun. Konuşma kartları tek net yolu göstersin: Kaydet → Stüdyo sesi → Yazıya dök.
5. Zayıf telefon kontrolü: RAM ve işlemciye göre hafif modu ilk açılışta öner; uzun işlerden önce tahmini süreyi göster.
6. Gizlilik politikası sayfası, Play'deki veri güvenliği beyanı ("veri toplanmıyor") ve izin açıklamaları (mikrofon, ön plan servisleri, MANAGE_MEDIA).
Bitti sayılır: AAB derleniyor ve imzalanıyor; lisans tablosu hazır; 12 kişilik kapalı test başladı.

## Aşama 1 — Kayıt sırasında not alma (5.16)
Amaç: Otter ve Plaud'un "not" deneyimini internetsiz vermek. En ucuz ve en etkili adım.
1. Kayıt sırasında "işaret" düğmesi: o ana bir yer imi koyar (isteğe bağlı kısa yazı ya da etiket: Önemli, Soru, Yapılacak).
2. Kayıt sırasında yazılı not: notlar zaman damgasıyla kaydedilir, metinde ve dalga formunda görünür.
3. Kayıt ekranına fotoğraf ekleme (tahta, slayt): zaman damgasıyla nota bağlanır. Yalnız kullanıcı çekerse.
4. Her kaydın bir "not sayfası" olsun: başlık, etiketler, işaretler, notlar, yazıya dökülmüş metin tek ekranda. İşarete dokunmak sesi o andan çalar.
Bitti sayılır: işaret ve not, kaydı bölmeden ve gecikme yaratmadan çalışıyor; uzun kayıtlarda da (2 saat) sorunsuz.

## Aşama 2 — Canlı yazıya dökme (5.17–5.18)
Amaç: kayıt sürerken yazı aksın. Pixel'in canlı yazıya dökme listesinde Türkçe yok; burada fark yaratabiliriz.
Yöntem (önce ölç, sonra seç):
- A) Ses etkinliği algılama (silero VAD zaten uygulamada var) ile konuşmayı cümle cümle kes, her parçayı mevcut Whisper modeline ver. Gerçek akış değil ama birkaç saniye gecikmeyle yazı akar; yeni model gerekmez.
- B) sherpa-onnx'in akış (streaming) modelleri. Türkçe ve diğer 8 dil için uygun model var mı, lisansı ve doğruluğu ne? Araştır ve ölç; bilmeden varsayma.
Kurallar:
1. Canlı yazı "taslak"tır. Kayıt bitince kullanıcı isterse "Hassas yazıya dök" düğmesiyle tam model yeniden çalışır.
2. Kaydı asla aksatmasın: yazıya dökme kayıttan ayrı iş parçacığında; telefon yetişemezse yazı geride kalır, kayıt asla kesilmez.
3. Pil ve ısınma: canlı yazıya dökme ayrı bir aç/kapa düğmesi; varsayılanı kapalı ya da telefona göre önerilen.
Bitti sayılır: orta seviye bir telefonda Türkçe konuşmada kabul edilebilir gecikme (ölçülüp yazılacak), 1 saatlik kayıtta kayıp yok.

## Aşama 3 — Özet ve başlıklar (5.19–5.20)
Amaç: yazıya dökülmüş metinden kısa özet, bölüm başlıkları ve yapılacaklar listesi. İki adımda:
- 3a) Modelsiz özet (hızlı, her telefonda, her dilde): metindeki en önemli cümleleri seçen klasik yöntem (cümle puanlama). Ek olarak işaretlenen anlar ve "Yapılacak" etiketli notlar özete girer. Yeni model yok, çok hafif.
- 3b) Küçük dil modeliyle özet (isteğe bağlı indirme): telefonda çalışan küçük bir dil modeli ile gerçek özet, başlıklar, yapılacaklar, soru-cevap ("bu toplantıda ne karar verildi?").
  Önce araştır ve ölç: hangi model (lisansı ticari kullanıma uygun olmalı), boyutu, 4 GB ve 8 GB RAM'li telefonda 1 saatlik metni ne kadar sürede özetliyor, Türkçe kalitesi nasıl. Uygun değilse 3b'yi ertele; 3a yeterli.
Kurallar: özet her zaman kaynağına bağlı olsun (özetteki cümleye dokununca o anın sesi çalar). Özet otomatik değil, düğmeyle.
Bitti sayılır: 3a tüm dillerde çalışıyor; 3b için ölçüm raporu ve karar yazılı.

## Aşama 4 — Arama ve arşiv (5.21)
Amaç: kayıtlar bir "ses defteri" olsun.
1. Tüm kayıtların metninde arama (telefonda yerel dizin). Sonuca dokununca o anın sesi çalar.
2. Klasörler ve etiketler; tarih, süre, etiket, konuşmacıya göre filtre.
3. Konuşmacılara ad verme (konuşmacı ayırma zaten var): "Konuşmacı 1" → "Ayşe"; ad bir sonraki kayıtlarda öneri olarak gelsin (yalnız telefonda saklanır).
4. Bulutsuz yedek: tüm kayıt, not ve projeleri tek dosyaya aktar ve geri yükle (telefon değiştirenler için).
Bitti sayılır: 500 kayıtta arama 1 saniyenin altında (ölçülecek); yedek dosyası başka telefonda eksiksiz geri yükleniyor.

## Aşama 5 — Podcast stüdyosu (5.22–5.24)
Amaç: Android'de Ferrite ve Adobe Podcast'in boşluğunu doldurmak. Mevcut güçlü yanlarımız: çok katmanlı zaman çizelgesi, metinden düzenleme, dolgu kelime silme, Stüdyo sesi, konuşmacı ayırma, mastering.
1. Podcast şablonu: giriş müziği, konuşma, ara jingle, kapanış katmanlarıyla hazır proje. Kullanıcı kendi giriş/kapanışını bir kez kaydeder, her bölümde kullanır.
2. "Bölümü hazırla" düğmesi (kullanıcı başlatır, her adım ayrı geri alınabilir ve atlanabilir): Stüdyo sesi → dolgu kelimeleri öner (kullanıcı onaylar) → uzun sessizlikleri kısalt → konuşmacıların sesini eşitle → müziği konuşma altında kıs → yayın ses seviyesi.
3. Yayın ses seviyesi hazır ayarları (podcast için yaygın hedef -16 LUFS, tepe -1 dB; değerleri yayın platformlarının güncel önerilerinden doğrula).
4. Bölüm işaretleri: MP3 dışa aktarırken bölüm (chapter) bilgisi ve kapak resmi gömülsün. Bölüm başlıkları Aşama 3'teki özetten önerilsin.
5. Bölüm notları: özet + bölüm zamanları + konuşmacılar, kopyalanabilir metin olarak (show notes).
6. Altyazılı dikey video (9:16) ve ses dalgalı kısa klip: bölümden 30–60 saniyelik tanıtım parçası seçip videoya çevir (altyazılı video zaten var; dikey şablon eklenmeli).
7. Uzaktan röportaj: internet istediği için sunucu kurmuyoruz. Bunun yerine "çift kayıt" rehberi: iki kişi kendi telefonunda kaydeder, dosyalar stüdyoda alkış ya da ses eşleşmesiyle otomatik hizalanır (hizalama kullanıcı düğmesiyle).
Bitti sayılır: 45 dakikalık iki kişilik bir bölüm, ham kayıttan yayına hazır MP3 + bölüm notları + tanıtım videosuna kadar uygulama dışına çıkmadan hazırlanabiliyor.

## Aşama 6 — Kalite ve hız (sürekli, 6.x)
Amaç: sunucuya en yakın kalite.
1. Yazıya dökmede kelime doğruluğunu ölçmek için sabit bir test seti (Türkçe ve İngilizce, gürültülü/temiz, tek/çok konuşmacı). Her model değişikliği bu setle karşılaştırılsın.
2. Telefonun yapay zekâ hızlandırıcısı ve C++ ile ağır işlerin hızlandırılması: önce ölç, kazanç yoksa yapma.
3. Özel kelimeler: kullanıcının eklediği adlar ve terimler (kişi, şirket, ilaç, ders adı) yazıya dökmede öncelikli olsun (modelin desteklediği ölçüde; önce araştır).
4. Bir kayda göre yanlış yazılan kelimeyi düzeltince aynı kelimenin diğer geçişlerini de düzeltme önerisi.

## Yapmayacaklarımız
- Bulut hesap, sunucuda işlem, toplantıya katılan bot.
- Reklam, analitik, abonelik.
- Beat yapımı, sanal enstrüman, sosyal ağ.

## Sıra ve öncelik özeti
5.15 Play'e hazırlık → 5.16 Not alma → 5.17–5.18 Canlı yazıya dökme → 5.19–5.20 Özet → 5.21 Arama ve arşiv → 5.22–5.24 Podcast stüdyosu → 6.x Kalite ve hız.
Not: Play'e çıkış Aşama 0'dan hemen sonra yapılır; sonraki aşamalar güncelleme olarak gelir.

## Premium önerisi (tek seferlik)
Ücretsiz: kayıt, temizlik, yazıya dökme, canlı yazı, işaret/not, modelsiz özet, çalar.
Premium: dil modeliyle özet ve soru-cevap, podcast şablonu ve "Bölümü hazırla", bölüm işaretleri, dikey tanıtım videosu, kaliteli parça ayırma, HD ses, toplu işlem.

## Önce araştırılıp ölçülmesi gerekenler (varsayma)
- Türkçe dahil 9 dil için akış (streaming) yazıya dökme modeli var mı; lisansı ve doğruluğu.
- Telefonda çalışan küçük dil modelinin Türkçe özet kalitesi, hızı, RAM ihtiyacı ve lisansı.
- Podcast platformlarının güncel ses seviyesi önerileri.
- MP3 bölüm (chapter) bilgisini hangi uygulamaların okuduğu.

## Türkçe doğruluk karşılaştırması (2026-11-12, bitti)
8 test sesi (test-sesleri-3), kelime hata oranı: Whisper base %40,8, Whisper small %17,8, seda-v0.1 %21,9, seda-v0.2-tiny %19,2, seda-v0.2 %10,9 (akış, greedy, LM yok). seda ~16× hızlı, müzik/sessizlikte uydurmuyor; Whisper müzikte "(Müzik çalıyor)" ekliyor. Rapor: https://claude.ai/artifact/McaTZBUfnw2wtvddTgc11C, betik `tests/asr/`. Lisans: v0.1 Apache 2.0, v0.2 CC BY-SA 4.0.

# Ses Defteri 5.21.2 — kaynak düzeltme paketi

16 Kasım 2026. Taban: `claude/project-thread-ea0qoe`, `d59ff36` (5.21.1).
Çalışma dalı: `codex/fix-5.21.1`. Main ve ayrışmış `jz2p6y` dalı kullanılmadı.
Sürüm adayı: 5.21.2, versionCode 97. Bu bir imzalı APK teslimi değildir.

## Yapılan değişiklikler

- **B01, B02 — kayıt koruma:** Kurtarma başarısızlığında ham ses, hedef ve journal korunur. Başarılı yayınlamadan sonra silinir. Yeni kayıt bekleyen kurtarmanın üzerine yazamaz. Journal geçici dosya + fsync + rename ile yazılır. Busy kontrolü seçenekleri değiştirmeden yapılır. Mikrofon, kayıt thread'i beklenmeden önce durdurulur; thread bitmediyse sink sonlandırılmaz. Çok kısa/boş kayıtları silme politikası ayrı kalır.
- **B03 — proje yedeği:** Aynı bağımlılık gezgini güncel ses, kaynak, A/B, hesaplanan efekt önbelleği, iç içe düzenleme geçmişi ve podcast şablonlarını toplar. Mutlak yollar yerine arşiv kimlikleri kullanılır; geri yüklemede önbellek anahtarları dahil çevrilir. Yedek alınırken açık proje değiştirilmez.
- **B04 — kayıt kimliği:** Ad+boyut üzerinden sessiz eşleştirme kaldırıldı. Geri yüklenen kayıtlar ayrı dosyalar olarak korunur. Aynı içerik de tekrar geri yüklendiğinde yeni kopya oluşabilir; bu paket mevcut kitaplıkla içerik hash'i üzerinden kopya birleştirme yapmaz.
- **B05, B06 — geri yükleme:** Önce sınırlı yerel ZIP kopyası ve staging dosyaları oluşturulur. ZIP merkez dizini, yinelenen girdiler, uygulama/sürüm/temel şema, beklenen dosyalar ve v2 SHA-256/boyut manifesti doğrulanır. Sonra benzersiz proje/fotoğraf adları ve yeni medya hedefleri oluşturulur. Hata/iptalde yalnız bu oturumun oluşturduğu dosyalar geri alınır; önceden mevcut dosyalar ezilmez veya silinmez. Fotoğraf adları da metadata'da çevrilir.
- **B07 — canlı yazı:** Atlanan aralıklar, kısmi sonuç ve işlenen metin sonu kalıcı saklanır; boş fakat eksik sonuç da kaybolmaz. Kullanıcı mevcut yeniden yazıya dökme düğmesiyle tam metni yeniden oluşturabilir. Eski native inference bitmeden ikinci canlı model yüklenmez. Artık sonucu kullanılmayacak kuyruk bırakılır; native inference zorla serbest bırakılmaz.
- **B08 — efektler:** Hesap klibin aday kopyasında yapılır. Ayar, ses ve önbellek yalnız başarıda birlikte uygulanır. Hata/iptalde son başarılı durum döner. Kapanmış pencerenin geç gelen sonucu uygulanmaz.
- **B09 — Temizle:** Oturum ve iş sahipliği; başarı, hata, ilerleme, finally, profil hazırlığı ve kaydetme dönüşlerinde kontrol edilir. A kaydının eski işi B kaydının penceresini veya iptal bilgisini değiştiremez.
- **B10 — bellek:** Dolgu analizi 12 saniyelik, 2 saniye örtüşen pencerelerle çalışır; adaylar sıralanıp birleştirilir. Model kontrolü yalnız aday parçaları okur. Olay taraması resample edilmiş PCM'i geçici diske yazar ve 1 saniyelik model pencerelerini diskten okur. Tam ses uzunluğunda float PCM kopyaları kaldırıldı. Olay skorları ve 10 ms enerji özeti süreyle büyümeye devam eder; modelin native belleği bu düzeltmeden bağımsızdır.
- **B11 — konuşmacılar:** 30 dakika sınırı kayıt yolunda da açık hata olarak uygulanır; dosyanın kalanı sessizce atlanmaz.
- **B12 — çalar:** Geçişte ikinci çalara hız/perde uygulanır. Geçiş eşiği ve zarf süresi medya zamanı yerine çalma hızına göre hesaplanır.
- **B13 — iptal:** Yedek kopyaları, staging, medya kopyalama, WAV dışa aktarma ve olay taraması dönüşümü bloklar arasında iptali kontrol eder. Yayınlamadan önce kontrol yapılır. Native inference'ın güvenli durma noktası beklenir.
- **B14 — codec sonlandırma:** AAC/Opus ve FLAC EOS bekleme zaman aşımı başarı değildir. FLAC input EOS kuyruğa girmemişse hata verir. Encoder kaynakları finally içinde kapanır.
- **B15 — ekran/Activity yaşam döngüsü:** Normal işlerin politikası güvenli kesintidir; eski sayfanın işlem zinciri otomatik sürdürülmez. Havuzlar süreç kapsamındadır; eski ve yeni Activity ayrı studio havuzları açmaz. Activity kapanınca ona ait normal işler iptal edilir, başlamadan önce ve sonuç uygulamadan önce kontrol edilir; yeniden açılan sayfaya kesinti bildirilir. Bg'nin kalıcı görevleri bu iptalin dışındadır. Tamamlanmış restore sonucu, JS metadata'yı kalıcı yazıp token ile onaylayana kadar diskte tutulur ve yeniden açılışta idempotent uygulanır. Bu arada proje/fotoğraf temizliği engellenir. Kaydı değiştiren sessizlik kaldırmada özgün kayıt, yeni metadata kalıcı yazılmadan silinmez.
- **B16 — paylaşım:** Aynı adlı dosyalar benzersiz paylaşım kopyaları ve URI'ler alır. Görünen özgün ad provider metadata'sında korunur.
- **B17 — işaretler:** Toplu trim/pauses işlemi `report.keep` zaman haritasını kullanır. Korunan sesin işaret/fotoğraf/notları yeni zamana taşınır; çıkarılan sesin içindeki işaretler yeni kayda aktarılmaz.
- **B18 — doğrulama:** Eski test betiklerindeki sabit ev dizinleri ve hata bastırma kaldırıldı. Arayüz taramasında bilinmeyen bridge metodu/işi artık sessiz başarı değildir; hata/gecikme enjekte edilebilir ve başarısız tarama nonzero çıkar. JSON test shim'inin metin/array serileştirmesi düzeltildi. Yeni bağımsız regression paketi eklendi.

## Uyumluluk ve sınırlar

- Yedek şeması v2 oldu; v1 yedekleri de okunur. Eski yedekte hiç bulunmayan özgün/geçmiş sesler üretilemez. Böyle bir projede mevcut işlenmiş ses ve canlı efektler korunur, eksik geçmiş/hazır hesaplanmış efekt ayarları kaldırılır ve kullanıcı uyarılır. Mevcut kitaplık ve projeler sıfırlanmaz.
- Metadata en çok 16 MiB, tek açılmış dosya 16 GiB, toplam 64 GiB, girdi sayısı 100.000 ile sınırlıdır. Boş disk alanı ayrıca denetlenir. Yerel ZIP kopyası, staging ve commit kopyaları için ek boş alan gerekir.
- Süreç dosya yazarken tamamen öldürülürse bu oturumun bazı yeni staging/medya dosyaları kalabilir; tekrar deneme bunları tamamlanmış dosya kabul etmez ve eski verinin üzerine yazmaz. Activity/WebView yeniden kurulması için yukarıdaki replay/kesinti politikası uygulanır. Tüm normal işlerin süreç ölümünden sonra devam etmesi sağlanmış değildir.
- Kayıt kurtarma tekrar başarısız olursa yeni kayıt engellenir; kullanıcı depolama/izin sorununu giderip uygulamayı yeniden açabilir. Kurtarılabilir kaydı otomatik silerek engeli kaldırma yoktur.

## Çalıştırılan kontroller

`tests/desktop/run-regression-5211.ps1`:

- Web kaynaklarında 72 JavaScript sözdizimi kontrolü.
- Gerçek JS fonksiyonlarıyla bağımlılık toplama, yol/cache dönüşümü, eski yedek göçü, metadata hatasında receipt'in korunması, tekrar uygulama, Temizle'nin geç sonuçları, efekt rollback/başarı ve işaret zamanları.
- Yeni mesajların dokuz dilde varlığı.
- Uygulamanın bütün Java kaynaklarının gerçek Android 36 `android.jar` sınıflarına karşı Java 8 hedefiyle derlenmesi. Yalnız `R` kaynak kimlikleri derleme kontrolü için taklit edildi. Üç mevcut deprecated-finalize uyarısı var; derleme hatası yok.
- Gerçek `Archive` koduyla geçersiz uygulama/JSON, eksik dosya/metadata, yol kaçışı, checksum uyuşmazlığı, ZIP footer kesilmesi, limit, iptal, kopya/yayınlama hatası ve rollback; eski yarım dosyayı koruma, v2 roundtrip, yeni context'te receipt'i okuma ve yanlış token'ı reddetme. Android dosya/medya sınırları yerel dosya taklitleridir.
- Gerçek `Recorder.recover/start` ile busy ayar koruması, WAV kurtarma başarısızlığı ve yeniden deneme, M4A remux hatasında kaynak koruma, bekleyen kaydın üzerine yazmama ve FLAC yayınlama hatası.
- Gerçek encoder koduyla programlanabilir sahte codec üzerinde input/output EOS eksikliği, gecikmiş EOS, normal EOS ve kaynak kapatma.
- Gerçek dolgu/olay işleme akışlarıyla bir saatlik sessiz WAV, `-Xmx40m`; dolgu penceresi sınırındaki 2 saniyelik sentetik tonun tek ve doğru zamanlı bulunması; dönüşümde iptal. WAV decoder ve olay sınıflandırıcısı taklit edildi; model doğruluğu ölçülmedi.

Tekrar çalıştırma (PowerShell, JDK ve Android SDK gerekir):

```powershell
./tests/desktop/run-regression-5211.ps1 -AndroidJar /path/to/android.jar -Javac /path/to/javac -Java /path/to/java
```

**Çalıştırılmayanlar:** İmzalı APK/AAB derlemesi, Android cihaz/emülatör, gerçek mikrofon/MediaCodec/MediaStore, gerçek JNI modelleri, Bluetooth, ses kalitesi ve tam Playwright taraması. Java derleme ve sahte sınır testleri bunların geçtiği anlamına gelmez.

## Öncelikli cihaz kabul testleri

1. Yedekli test kayıtlarında WAV/M4A/FLAC durdurma ve süreç kesilmesi sonrası kurtarma; dolu depolama hatasından sonra tekrar deneme.
2. Efekt/geçmiş/A-B/fotoğraf/şablon içeren projeyi yeni yedekle temiz kurulumda geri yükleme; eski v1 yedeğinin uyarısı ve dinlenebilirliği.
3. Restore sırasında iptal/Activity yeniden kurma ve metadata kaydı sırasında ekran kapanması; tekrar açılışta notların gelmesi.
4. Efekt değiştirirken hata/iptal/pencere kapatma; A Temizle → kapat → B Temizle.
5. Uzun kayıt/canlı yazı son cümlesi ve eksik aralık uyarısı; yeniden yazıya dökme.
6. Gerçek modellerle bir saatlik analiz: RAM, disk kullanımı, iptal gecikmesi ve parça sınırları.
7. 0,5x/2x hız ve farklı perdelerde çalar geçişi; aynı adlı iki dosyayı paylaşma; toplu kırpma sonrası işaretler.

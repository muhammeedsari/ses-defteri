# Arayüz testleri (5.x) — Playwright + sahte köprü

Telefon olmadan arayüzü masaüstü Chromium'da açar. `mock.js`, Android köprüsünün (`SesDefteriAndroid` / `AND`) sahtesidir:
işleri (`job`) kaydeder (`window.__calls = [[kind, args], ['AND.x', args]…]`) ve basit cevaplar döner. Sahte cevaplar
gerçek Java'nın döndürdüğü alanları her zaman içermez; o yüzden "spec" (spektrum) ve "levelall" hataları test ortamına aittir.

- `mk.py`: `android/build_apk.py`'deki `web_page()` ile sayfayı kurar, `mock.js` + `window.__T` kancalarını ekler → `web/page_t.html` (repoya girmez, .gitignore'da). Betiklerdeki mutlak yollar bu geliştirme ortamına göredir; kendi ortamında düzelt.
- `smoke.py`: araç listesindeki her aracı 3 durumda (konuşma klibi, müzik klibi, seçim yok) açar, pencereleri otomatik ilerletir, JS hatalarını toplar.
- `smoke2.py`: sekmeler, kayıt menüsü seçenekleri, çalar, Pratik, Temizle, Ayarlar anahtarları.
- `t17.py`: 5.14 değişiklikleri (Çoğalt, işaret ekle/kaldır/tümünü sil, Yapıştır, yenilikler metni).
- `probe4.py`: pencere dinleyicisi birikmesi hatası (5.14'te düzeltildi): Pratik'i iki kez açıp "Akorları gizle".
- `t12`–`t16`: 5.0–5.5 testleri (efekt listesi/mastering, hesaplanan efektler, takip/geçmiş, Pratik/akort, karaoke). t13'teki iki madde 5.2'deki tasarım değişikliği yüzünden eskidi.
Çalıştırma: `python3 mk.py && python3 smoke.py`

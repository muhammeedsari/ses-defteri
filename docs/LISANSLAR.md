# Lisans kontrolü (5.11, Kasım 2026; 5.12 güncellemesi)

**5.12:** Piper okuma sesleri (yapay zekâ sesi) uygulamadan çıkarıldı; metin okuma yalnız telefonun kendi sesiyle (Android TextToSpeech) yapılıyor, Arapça dahil. Telefonda kalan indirilmiş sesler ilk açılışta silinir.

**5.13:** sherpa-onnx kütüphaneleri `SHERPA_ONNX_ENABLE_TTS=OFF` ile yeniden derlendi (`.github/workflows/sherpa-notts.yml`, sonuç bu deponun `sherpa-onnx-v1.13.8-notts` ön sürümü). APK'da artık espeak-ng (GPL) yok; lisans listesinden çıkarıldı.

Google Play öncesi uygulamanın içerdiği ve indirdiği her parçanın lisansı. "Ticari" = ücretli sürüm / reklam / şirket adına dağıtım.

## Okuma sesleri (Piper, isteğe bağlı indirilir)
| Dil | Ses | Veri lisansı | Durum |
|---|---|---|---|
| tr | Fettah | CC0 | tamam (5.9'da DFKI'dan geçildi; DFKI: CC BY-NC-SA 4.0) |
| en | LJSpeech | kamu malı | tamam (5.11'de Lessac'tan geçildi; Lessac/Blizzard 2013: yalnız araştırma, ticari yasak) |
| de | Thorsten | CC0 | tamam |
| fr | SIWIS | CC BY 4.0 | tamam, atıf gerekir (lisans listesinde) |
| es | davefx | CC0 | tamam |
| pt | faber | CC0 | tamam |
| ru | dmitri | CC0 | tamam (5.11'de irina'dan geçildi; irina: lisans bilinmiyor) |
| zh | chaowen | CC0 | tamam (5.11'de huayan'dan geçildi; huayan: lisans bilinmiyor) |
| ar | kareem | belirsiz: veri deposunda lisans yok | **açık konu** |

Arapça için bakılan başka sesler: SA_miro (CC BY-NC-ND 4.0), SA_dii (CC BY-NC-SA 4.0) — ikisi de ticari değil.

## Modeller ve kütüphaneler
- espeak-ng (GPL-3.0): 5.13'ten beri APK'da yok (okuma sesleri 5.12'de çıkarıldı, kütüphane 5.13'te onsuz derlendi).
- LAME (MP3): LGPL, ayrı libsdmp3.so olarak — tamam.
- UVR MDX-Net Inst HQ 5: UVR kodu MIT; modeller UVR ekibince eğitilmiş, README "herkes kullanabilir, UVR'a atıf verin" diyor — atıf lisans listesinde.
- Signalsmith Stretch, ONNX Runtime, Whisper, Spleeter, Silero VAD, pyannote: MIT. sherpa-onnx, DPDFNet, CAM++, LavaSR, CED: Apache-2.0. Fontlar: OFL-1.1.

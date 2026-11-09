# Lisans kontrolü (5.11, Kasım 2026)

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
- espeak-ng (okuma seslerinin harf→ses çevirisi, sherpa-onnx içinde): **GPL-3.0**. APK'da olduğu için uygulamanın kaynağı GPL-3.0 ile açık olmalı (repo herkese açıksa ve GPL-3.0 LICENSE eklenirse sorun yok) ya da okuma sesi özelliği çıkarılmalı. **Karar kullanıcıda.**
- LAME (MP3): LGPL, ayrı libsdmp3.so olarak — tamam.
- UVR MDX-Net Inst HQ 5: UVR kodu MIT; modeller UVR ekibince eğitilmiş, README "herkes kullanabilir, UVR'a atıf verin" diyor — atıf lisans listesinde.
- Signalsmith Stretch, ONNX Runtime, Whisper, Spleeter, Silero VAD, pyannote: MIT. sherpa-onnx, DPDFNet, CAM++, LavaSR, CED: Apache-2.0. Fontlar: OFL-1.1.

/* Ses Defteri 3.1 — new recorder: format, live meter, monitoring (merged into SD_I18N). */
(function () {
const A = {
tr: { setRecFmt: "Kayıt biçimi", rf_m4a: "M4A (küçük dosya)", rf_wav: "WAV (kayıpsız, büyük dosya)", rf_flac: "FLAC (kayıpsız, WAV'ın yarısı)",
  recMon: "Kulaklıktan dinle", monOn: "Kendi sesini kulaklıktan duyuyorsun. Hoparlörden yankı yapmaması için kulaklık tak.", monFail: "Dinleme açılamadı." },
en: { setRecFmt: "Recording format", rf_m4a: "M4A (small file)", rf_wav: "WAV (lossless, large file)", rf_flac: "FLAC (lossless, half of WAV)",
  recMon: "Listen on headphones", monOn: "You now hear yourself on headphones. Plug in headphones so the speaker doesn't feed back.", monFail: "Couldn't turn on listening." },
de: { setRecFmt: "Aufnahmeformat", rf_m4a: "M4A (kleine Datei)", rf_wav: "WAV (verlustfrei, große Datei)", rf_flac: "FLAC (verlustfrei, halb so groß wie WAV)",
  recMon: "Über Kopfhörer mithören", monOn: "Du hörst dich jetzt über Kopfhörer. Schließe Kopfhörer an, damit der Lautsprecher nicht rückkoppelt.", monFail: "Mithören konnte nicht eingeschaltet werden." },
fr: { setRecFmt: "Format d’enregistrement", rf_m4a: "M4A (petit fichier)", rf_wav: "WAV (sans perte, gros fichier)", rf_flac: "FLAC (sans perte, moitié du WAV)",
  recMon: "Écouter au casque", monOn: "Tu t’entends maintenant au casque. Branche un casque pour éviter le larsen du haut-parleur.", monFail: "Impossible d’activer l’écoute." },
es: { setRecFmt: "Formato de grabación", rf_m4a: "M4A (archivo pequeño)", rf_wav: "WAV (sin pérdida, archivo grande)", rf_flac: "FLAC (sin pérdida, la mitad que WAV)",
  recMon: "Escuchar con auriculares", monOn: "Ahora te oyes por los auriculares. Conecta auriculares para que el altavoz no acople.", monFail: "No se pudo activar la escucha." },
pt: { setRecFmt: "Formato de gravação", rf_m4a: "M4A (arquivo pequeno)", rf_wav: "WAV (sem perdas, arquivo grande)", rf_flac: "FLAC (sem perdas, metade do WAV)",
  recMon: "Ouvir nos fones", monOn: "Agora você se ouve nos fones. Conecte fones para o alto-falante não microfonar.", monFail: "Não foi possível ativar a escuta." },
ru: { setRecFmt: "Формат записи", rf_m4a: "M4A (маленький файл)", rf_wav: "WAV (без потерь, большой файл)", rf_flac: "FLAC (без потерь, вдвое меньше WAV)",
  recMon: "Слушать в наушниках", monOn: "Теперь ты слышишь себя в наушниках. Подключи наушники, чтобы динамик не заводился.", monFail: "Не удалось включить прослушивание." },
ar: { setRecFmt: "صيغة التسجيل", rf_m4a: "M4A (ملف صغير)", rf_wav: "WAV (بلا فقد، ملف كبير)", rf_flac: "FLAC (بلا فقد، نصف حجم WAV)",
  recMon: "الاستماع عبر السماعات", monOn: "أنت تسمع صوتك الآن عبر السماعات. صِل سماعات حتى لا يحدث صفير من مكبر الصوت.", monFail: "تعذّر تشغيل الاستماع." },
zh: { setRecFmt: "录音格式", rf_m4a: "M4A（文件小）", rf_wav: "WAV（无损，文件大）", rf_flac: "FLAC（无损，约为 WAV 的一半）",
  recMon: "用耳机监听", monOn: "现在可以在耳机里听到自己的声音。请插上耳机，以免扬声器产生啸叫。", monFail: "无法开启监听。" }
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

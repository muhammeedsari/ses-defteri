/* 4.5: studio voice (merged into SD_I18N). */
(function () {
const A = {
tr: { sepRoom: "Bunun için o zamanda {n} boş katman gerekiyor (en fazla 4 katman). Bir katmanı boşalt.", sh_stt: "Yazıya dök", sh_studio: "Stüdyo sesi", op_studio: "Stüdyo sesi", studioDone: "Stüdyo sesi uygulandı. Önce/Sonra ile karşılaştırabilirsin." },
en: { sepRoom: "This needs {n} free tracks at that time (4 tracks at most). Free up a track.", sh_stt: "Transcribe", sh_studio: "Studio voice", op_studio: "Studio voice", studioDone: "Studio voice applied. Compare with Before/After." },
de: { sepRoom: "Dafür braucht es {n} freie Spuren an dieser Stelle (höchstens 4). Gib eine Spur frei.", sh_stt: "Transkribieren", sh_studio: "Studio-Stimme", op_studio: "Studio-Stimme", studioDone: "Studio-Stimme angewendet. Mit Vorher/Nachher vergleichen." },
fr: { sepRoom: "Il faut {n} pistes libres à cet endroit (4 pistes au plus). Libère une piste.", sh_stt: "Transcrire", sh_studio: "Voix studio", op_studio: "Voix studio", studioDone: "Voix studio appliquée. Compare avec Avant/Après." },
es: { sepRoom: "Se necesitan {n} pistas libres en ese tramo (máximo 4). Libera una pista.", sh_stt: "Transcribir", sh_studio: "Voz de estudio", op_studio: "Voz de estudio", studioDone: "Voz de estudio aplicada. Compara con Antes/Después." },
pt: { sepRoom: "São precisas {n} faixas livres nesse trecho (no máximo 4). Libere uma faixa.", sh_stt: "Transcrever", sh_studio: "Voz de estúdio", op_studio: "Voz de estúdio", studioDone: "Voz de estúdio aplicada. Compare com Antes/Depois." },
ru: { sepRoom: "Нужно {n} свободных дорожек в этом месте (максимум 4). Освободите дорожку.", sh_stt: "В текст", sh_studio: "Студийный голос", op_studio: "Студийный голос", studioDone: "Студийный голос применён. Сравните «До/После»." },
ar: { sepRoom: "يلزم {n} مسارات فارغة في هذا الموضع (4 مسارات كحد أقصى). أفرغ مسارًا.", sh_stt: "تحويل إلى نص", sh_studio: "صوت استوديو", op_studio: "صوت استوديو", studioDone: "تم تطبيق صوت الاستوديو. قارن بين قبل/بعد." },
zh: { sepRoom: "该位置需要 {n} 条空轨道（最多 4 条）。请腾出一条轨道。", sh_stt: "转文字", sh_studio: "录音棚人声", op_studio: "录音棚人声", studioDone: "已应用录音棚人声，可用前/后对比。" },
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

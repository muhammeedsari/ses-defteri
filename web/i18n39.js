/* 4.5: studio voice (merged into SD_I18N). */
(function () {
const A = {
tr: { sh_studio: "Stüdyo sesi", op_studio: "Stüdyo sesi", studioDone: "Stüdyo sesi uygulandı. Önce/Sonra ile karşılaştırabilirsin." },
en: { sh_studio: "Studio voice", op_studio: "Studio voice", studioDone: "Studio voice applied. Compare with Before/After." },
de: { sh_studio: "Studio-Stimme", op_studio: "Studio-Stimme", studioDone: "Studio-Stimme angewendet. Mit Vorher/Nachher vergleichen." },
fr: { sh_studio: "Voix studio", op_studio: "Voix studio", studioDone: "Voix studio appliquée. Compare avec Avant/Après." },
es: { sh_studio: "Voz de estudio", op_studio: "Voz de estudio", studioDone: "Voz de estudio aplicada. Compara con Antes/Después." },
pt: { sh_studio: "Voz de estúdio", op_studio: "Voz de estúdio", studioDone: "Voz de estúdio aplicada. Compare com Antes/Depois." },
ru: { sh_studio: "Студийный голос", op_studio: "Студийный голос", studioDone: "Студийный голос применён. Сравните «До/После»." },
ar: { sh_studio: "صوت استوديو", op_studio: "صوت استوديو", studioDone: "تم تطبيق صوت الاستوديو. قارن بين قبل/بعد." },
zh: { sh_studio: "录音棚人声", op_studio: "录音棚人声", studioDone: "已应用录音棚人声，可用前/后对比。" },
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

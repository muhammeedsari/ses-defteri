/* 4.12: change marks tutorial (merged into SD_I18N). */
(function () {
const A = {
tr: { co_marks: "Kırmızı bant: son düzenlemenin değiştirdiği yerler. Kırmızı üçgen: sesin kesilip birleştirildiği nokta.", co_hist: "✦ rozetine dokun: bu parçaya yapılan işlemlerin geçmişi ve Önce/Sonra karşılaştırması." },
en: { co_marks: "Red band: the places your last edit changed. Red triangle: where the sound was cut and joined.", co_hist: "Tap the ✦ badge: the history of what was done to this clip, and Before/After." },
de: { co_marks: "Rotes Band: Stellen, die die letzte Bearbeitung geändert hat. Rotes Dreieck: hier wurde geschnitten.", co_hist: "Tippe auf das ✦-Abzeichen: Verlauf der Bearbeitungen und Vorher/Nachher." },
fr: { co_marks: "Bande rouge : les endroits modifiés par la dernière retouche. Triangle rouge : là où le son a été coupé.", co_hist: "Touche le badge ✦ : l’historique des retouches et Avant/Après." },
es: { co_marks: "Banda roja: los lugares que cambió la última edición. Triángulo rojo: donde se cortó el sonido.", co_hist: "Toca la insignia ✦: el historial de cambios y Antes/Después." },
pt: { co_marks: "Faixa vermelha: os trechos alterados pela última edição. Triângulo vermelho: onde o som foi cortado.", co_hist: "Toque no selo ✦: o histórico de edições e Antes/Depois." },
ru: { co_marks: "Красная полоса: места, изменённые последней правкой. Красный треугольник: место склейки.", co_hist: "Нажмите значок ✦: история правок и «До/После»." },
ar: { co_marks: "الشريط الأحمر: الأماكن التي غيّرها آخر تعديل. المثلث الأحمر: موضع القص والوصل.", co_hist: "المس شارة ✦: سجل التعديلات وقبل/بعد." },
zh: { co_marks: "红色条带：上一次编辑改动的位置。红色三角：剪切拼接处。", co_hist: "点按 ✦ 标记：查看此片段的处理历史和前后对比。" },
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

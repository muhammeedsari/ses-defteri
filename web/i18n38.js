/* 4.4.1: language screen in the picked language, track order (merged into SD_I18N). */
(function () {
const A = {
tr: { langChoose: "Dilini seç", langGo: "Devam", tkUp: "Yukarı taşı", tkDown: "Aşağı taşı" },
en: { langChoose: "Choose your language", langGo: "Continue", tkUp: "Move up", tkDown: "Move down" },
de: { langChoose: "Sprache wählen", langGo: "Weiter", tkUp: "Nach oben", tkDown: "Nach unten" },
fr: { langChoose: "Choisis ta langue", langGo: "Continuer", tkUp: "Monter", tkDown: "Descendre" },
es: { langChoose: "Elige tu idioma", langGo: "Continuar", tkUp: "Subir", tkDown: "Bajar" },
pt: { langChoose: "Escolha seu idioma", langGo: "Continuar", tkUp: "Mover para cima", tkDown: "Mover para baixo" },
ru: { langChoose: "Выберите язык", langGo: "Продолжить", tkUp: "Выше", tkDown: "Ниже" },
ar: { langChoose: "اختر لغتك", langGo: "متابعة", tkUp: "نقل لأعلى", tkDown: "نقل لأسفل" },
zh: { langChoose: "选择语言", langGo: "继续", tkUp: "上移", tkDown: "下移" },
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

/* 5.2: effect list in the edit history (merged into SD_I18N). */
(function () {
const A = {
tr: { op_rack: "Efekt listesi", hvStep: "efekt listesine eklendi", rkNone: "efekt yok" },
en: { op_rack: "Effect list", hvStep: "added to the effect list", rkNone: "no effects" },
de: { op_rack: "Effektliste", hvStep: "zur Effektliste hinzugefügt", rkNone: "keine Effekte" },
fr: { op_rack: "Liste d'effets", hvStep: "ajouté à la liste d'effets", rkNone: "aucun effet" },
es: { op_rack: "Lista de efectos", hvStep: "añadido a la lista de efectos", rkNone: "sin efectos" },
pt: { op_rack: "Lista de efeitos", hvStep: "adicionado à lista de efeitos", rkNone: "sem efeitos" },
ru: { op_rack: "Список эффектов", hvStep: "добавлено в список эффектов", rkNone: "нет эффектов" },
ar: { op_rack: "قائمة المؤثرات", hvStep: "أُضيف إلى قائمة المؤثرات", rkNone: "لا مؤثرات" },
zh: { op_rack: "效果列表", hvStep: "已加入效果列表", rkNone: "无效果" },
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

/* Ses Defteri 3.4.6+ — phone test fixes: sleep timer, problem notes, player menus (merged into SD_I18N). */
(function () {
const A = {
tr: { sleep5: "5 dakika sonra", sleepIn: "Uyku · {t}", probNote: "Sorun notunu gönder", probNoteNone: "Kaydedilmiş bir sorun notu yok" },
en: { sleep5: "In 5 minutes", sleepIn: "Sleep · {t}", probNote: "Send a problem note", probNoteNone: "No problem notes saved" },
de: { sleep5: "In 5 Minuten", sleepIn: "Schlaf · {t}", probNote: "Problemnotiz senden", probNoteNone: "Keine Problemnotizen gespeichert" },
fr: { sleep5: "Dans 5 minutes", sleepIn: "Minuterie · {t}", probNote: "Envoyer une note de problème", probNoteNone: "Aucune note de problème" },
es: { sleep5: "En 5 minutos", sleepIn: "Temporizador · {t}", probNote: "Enviar nota del problema", probNoteNone: "No hay notas de problemas" },
pt: { sleep5: "Em 5 minutos", sleepIn: "Temporizador · {t}", probNote: "Enviar nota do problema", probNoteNone: "Nenhuma nota de problema" },
ru: { sleep5: "Через 5 минут", sleepIn: "Сон · {t}", probNote: "Отправить заметку о проблеме", probNoteNone: "Заметок о проблемах нет" },
ar: { sleep5: "بعد 5 دقائق", sleepIn: "النوم · {t}", probNote: "إرسال ملاحظة المشكلة", probNoteNone: "لا توجد ملاحظات مشاكل" },
zh: { sleep5: "5 分钟后", sleepIn: "睡眠 · {t}", probNote: "发送问题记录", probNoteNone: "没有保存的问题记录" }
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

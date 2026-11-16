/* Ses Defteri 3.4.3 — note after an unexpected close (merged into SD_I18N). */
(function () {
const A = {
tr: { crashT: "Uygulama beklenmedik şekilde kapandı", crashD: "Son seferde uygulama kapandı. Hata notunu gönderirsen sorunu bulup düzeltebiliriz. Not sadece telefonun ve uygulamanın sürümünü ve hatanın yerini içerir, kayıtlarından hiçbir şey içermez.", crashSend: "Notu paylaş" },
en: { crashT: "The app closed unexpectedly", crashD: "The app closed last time. If you send the error note, we can find and fix the problem. It only contains the phone and app version and where the error happened, nothing from your recordings.", crashSend: "Share note" },
de: { crashT: "Die App wurde unerwartet geschlossen", crashD: "Die App wurde beim letzten Mal geschlossen. Wenn du die Fehlernotiz sendest, können wir das Problem finden und beheben. Sie enthält nur Telefon- und App-Version und die Fehlerstelle, nichts aus deinen Aufnahmen.", crashSend: "Notiz teilen" },
fr: { crashT: "L'application s'est fermée de façon inattendue", crashD: "L'application s'est fermée la dernière fois. Si tu envoies la note d'erreur, nous pourrons trouver et corriger le problème. Elle contient seulement la version du téléphone et de l'application et l'endroit de l'erreur, rien de tes enregistrements.", crashSend: "Partager la note" },
es: { crashT: "La app se cerró inesperadamente", crashD: "La app se cerró la última vez. Si envías la nota de error, podremos encontrar y corregir el problema. Solo contiene la versión del teléfono y de la app y dónde ocurrió el error, nada de tus grabaciones.", crashSend: "Compartir nota" },
pt: { crashT: "O app fechou inesperadamente", crashD: "O app fechou da última vez. Se você enviar a nota de erro, podemos encontrar e corrigir o problema. Ela contém apenas a versão do telefone e do app e onde o erro aconteceu, nada das suas gravações.", crashSend: "Compartilhar nota" },
ru: { crashT: "Приложение неожиданно закрылось", crashD: "В прошлый раз приложение закрылось. Если отправите заметку об ошибке, мы сможем найти и исправить проблему. В ней только версии телефона и приложения и место ошибки, ничего из ваших записей.", crashSend: "Поделиться заметкой" },
ar: { crashT: "أُغلق التطبيق بشكل غير متوقع", crashD: "أُغلق التطبيق في المرة السابقة. إذا أرسلت ملاحظة الخطأ يمكننا إيجاد المشكلة وإصلاحها. تحتوي فقط على إصدار الهاتف والتطبيق ومكان الخطأ، ولا شيء من تسجيلاتك.", crashSend: "مشاركة الملاحظة" },
zh: { crashT: "应用意外关闭", crashD: "应用上次意外关闭。如果你发送错误说明，我们就能找到并修复问题。其中只包含手机和应用的版本以及出错位置，不含你的任何录音内容。", crashSend: "分享说明" }
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

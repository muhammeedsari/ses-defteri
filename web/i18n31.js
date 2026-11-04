/* 3.9: recording options, countdown (merged into SD_I18N). */
(function () {
const A = {
tr: { tlRecSet: "Kayıt seçenekleri", cdT: "Kayıttan önce geri sayım", cdOff: "Kapalı", cdN: "{n} sn", cdTap: "İptal için dokunun", cueT: "Kayıtta diğer kanalları çal", cueD: "Kulaklıkla dinleyin ki hoparlör sesi kayda girmesin. Çalarken kayıt düğmesine basarsanız kayıt o noktadan başlar." },
en: { tlRecSet: "Recording options", cdT: "Countdown before recording", cdOff: "Off", cdN: "{n} s", cdTap: "Tap to cancel", cueT: "Play the other tracks while recording", cueD: "Use headphones so the speaker is not recorded. Press record while playing to record from that point." },
de: { tlRecSet: "Aufnahmeoptionen", cdT: "Countdown vor der Aufnahme", cdOff: "Aus", cdN: "{n} s", cdTap: "Zum Abbrechen tippen", cueT: "Andere Spuren während der Aufnahme abspielen", cueD: "Kopfhörer verwenden, damit der Lautsprecher nicht mit aufgenommen wird. Während der Wiedergabe Aufnahme drücken, um ab dieser Stelle aufzunehmen." },
fr: { tlRecSet: "Options d'enregistrement", cdT: "Compte à rebours avant l'enregistrement", cdOff: "Désactivé", cdN: "{n} s", cdTap: "Touchez pour annuler", cueT: "Lire les autres pistes pendant l'enregistrement", cueD: "Utilisez un casque pour que le haut-parleur ne soit pas enregistré. Appuyez sur enregistrer pendant la lecture pour enregistrer à partir de ce point." },
es: { tlRecSet: "Opciones de grabación", cdT: "Cuenta atrás antes de grabar", cdOff: "Desactivado", cdN: "{n} s", cdTap: "Toca para cancelar", cueT: "Reproducir las otras pistas al grabar", cueD: "Usa auriculares para que no se grabe el altavoz. Pulsa grabar durante la reproducción para grabar desde ese punto." },
pt: { tlRecSet: "Opções de gravação", cdT: "Contagem regressiva antes de gravar", cdOff: "Desligado", cdN: "{n} s", cdTap: "Toque para cancelar", cueT: "Tocar as outras faixas durante a gravação", cueD: "Use fones para o alto-falante não ser gravado. Toque em gravar durante a reprodução para gravar a partir desse ponto." },
ru: { tlRecSet: "Параметры записи", cdT: "Обратный отсчёт перед записью", cdOff: "Выкл.", cdN: "{n} с", cdTap: "Нажмите для отмены", cueT: "Играть другие дорожки во время записи", cueD: "Используйте наушники, чтобы динамик не попал в запись. Нажмите запись во время воспроизведения, чтобы записывать с этого места." },
ar: { tlRecSet: "خيارات التسجيل", cdT: "عد تنازلي قبل التسجيل", cdOff: "إيقاف", cdN: "{n} ث", cdTap: "اضغط للإلغاء", cueT: "تشغيل المسارات الأخرى أثناء التسجيل", cueD: "استخدم سماعات الرأس حتى لا يُسجَّل صوت مكبر الصوت. اضغط تسجيل أثناء التشغيل للتسجيل من تلك النقطة." },
zh: { tlRecSet: "录音选项", cdT: "录音前倒计时", cdOff: "关闭", cdN: "{n} 秒", cdTap: "轻触取消", cueT: "录音时播放其他音轨", cueD: "请使用耳机，以免录入扬声器的声音。播放时按录音，即从该位置开始录音。" },
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

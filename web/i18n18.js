/* Ses Defteri 2.8 — live spectrum and loudness meter, home screen recording shortcut (merged into SD_I18N). */
(function () {
const A = {
tr: { tlMeter: "Ses ölçer", luM: "Anlık", luS: "Kısa (3 sn)", luI: "Ortalama", luPk: "Tepe", luTarget: "Hedef {t}: {v} LUFS",
  setPin: "Ana ekran", setPinB: "Kayıt kısayolu ekle", setPinD: "Ana ekrandaki kısayola dokununca uygulama doğrudan kayda başlar. Uygulama simgesine basılı tutunca da \"Yeni kayıt\" çıkar.", setPinNo: "Bu telefon kısayol eklemeye izin vermiyor. Uygulama simgesine basılı tutup \"Yeni kayıt\"ı ana ekrana sürükleyebilirsin.", recShort: "Yeni kayıt" },
en: { tlMeter: "Sound meter", luM: "Momentary", luS: "Short (3 s)", luI: "Average", luPk: "Peak", luTarget: "Target {t}: {v} LUFS",
  setPin: "Home screen", setPinB: "Add recording shortcut", setPinD: "Tapping the shortcut on your home screen starts recording straight away. Holding the app icon also shows \"New recording\".", setPinNo: "This phone doesn't allow adding shortcuts. Hold the app icon and drag \"New recording\" to your home screen.", recShort: "New recording" },
de: { tlMeter: "Pegelmesser", luM: "Momentan", luS: "Kurz (3 s)", luI: "Durchschnitt", luPk: "Spitze", luTarget: "Ziel {t}: {v} LUFS",
  setPin: "Startbildschirm", setPinB: "Aufnahme-Verknüpfung hinzufügen", setPinD: "Ein Tipp auf die Verknüpfung startet sofort die Aufnahme. Gedrückt halten des App-Symbols zeigt auch \"Neue Aufnahme\".", setPinNo: "Dieses Telefon erlaubt keine Verknüpfungen. Halte das App-Symbol gedrückt und ziehe \"Neue Aufnahme\" auf den Startbildschirm.", recShort: "Neue Aufnahme" },
fr: { tlMeter: "Vumètre", luM: "Instantané", luS: "Court (3 s)", luI: "Moyenne", luPk: "Crête", luTarget: "Cible {t} : {v} LUFS",
  setPin: "Écran d’accueil", setPinB: "Ajouter un raccourci d’enregistrement", setPinD: "Toucher le raccourci sur l’écran d’accueil lance aussitôt l’enregistrement. Un appui long sur l’icône de l’app affiche aussi « Nouvel enregistrement ».", setPinNo: "Ce téléphone n’autorise pas l’ajout de raccourcis. Maintiens l’icône de l’app et fais glisser « Nouvel enregistrement » sur l’écran d’accueil.", recShort: "Nouvel enregistrement" },
es: { tlMeter: "Medidor de sonido", luM: "Instantáneo", luS: "Corto (3 s)", luI: "Promedio", luPk: "Pico", luTarget: "Objetivo {t}: {v} LUFS",
  setPin: "Pantalla de inicio", setPinB: "Añadir acceso directo de grabación", setPinD: "Al tocar el acceso directo de la pantalla de inicio empieza a grabar enseguida. Mantener pulsado el icono de la app también muestra \"Nueva grabación\".", setPinNo: "Este teléfono no permite añadir accesos directos. Mantén pulsado el icono de la app y arrastra \"Nueva grabación\" a la pantalla de inicio.", recShort: "Nueva grabación" },
pt: { tlMeter: "Medidor de som", luM: "Instantâneo", luS: "Curto (3 s)", luI: "Média", luPk: "Pico", luTarget: "Meta {t}: {v} LUFS",
  setPin: "Tela inicial", setPinB: "Adicionar atalho de gravação", setPinD: "Tocar no atalho da tela inicial começa a gravar na hora. Segurar o ícone do app também mostra \"Nova gravação\".", setPinNo: "Este telefone não permite adicionar atalhos. Segure o ícone do app e arraste \"Nova gravação\" para a tela inicial.", recShort: "Nova gravação" },
ru: { tlMeter: "Измеритель звука", luM: "Мгновенная", luS: "Короткая (3 с)", luI: "Средняя", luPk: "Пик", luTarget: "Цель {t}: {v} LUFS",
  setPin: "Главный экран", setPinB: "Добавить ярлык записи", setPinD: "Нажатие на ярлык на главном экране сразу начинает запись. Если удерживать значок приложения, тоже появится «Новая запись».", setPinNo: "Этот телефон не позволяет добавлять ярлыки. Удерживай значок приложения и перетащи «Новая запись» на главный экран.", recShort: "Новая запись" },
ar: { tlMeter: "مقياس الصوت", luM: "لحظي", luS: "قصير (3 ث)", luI: "المتوسط", luPk: "الذروة", luTarget: "الهدف {t}: {v} LUFS",
  setPin: "الشاشة الرئيسية", setPinB: "إضافة اختصار للتسجيل", setPinD: "لمس الاختصار على الشاشة الرئيسية يبدأ التسجيل فورًا. الضغط المطوّل على أيقونة التطبيق يُظهر أيضًا \"تسجيل جديد\".", setPinNo: "هذا الهاتف لا يسمح بإضافة اختصارات. اضغط مطولًا على أيقونة التطبيق واسحب \"تسجيل جديد\" إلى الشاشة الرئيسية.", recShort: "تسجيل جديد" },
zh: { tlMeter: "声音表", luM: "瞬时", luS: "短期（3 秒）", luI: "平均", luPk: "峰值", luTarget: "目标 {t}：{v} LUFS",
  setPin: "主屏幕", setPinB: "添加录音快捷方式", setPinD: "点按主屏幕上的快捷方式会立即开始录音。长按应用图标也会显示“新录音”。", setPinNo: "此手机不允许添加快捷方式。长按应用图标，把“新录音”拖到主屏幕。", recShort: "新录音" }
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

/* 5.14.2: a recording cut short is kept, missing clip files, a project that could not be saved, undo is not kept after closing */
(function () {
  const L = {
    tr: { recCut: 'Kayıt yarıda kesildi (depolama dolu ya da mikrofon kesildi). O ana kadar kaydedilen kısım saklandı.', tlMissing: '{n} parçanın dosyası bulunamadı; bu parçalar sessiz kalır.', tlSaveFail: 'Proje kaydedilemedi: telefonda yer azalmış olabilir.',
      tlNewB: 'Zaman çizelgesi boşaltılır. Uygulama açık kaldıkça geri al ile dönebilirsin; uygulama kapanınca geri alınamaz.' },
    en: { recCut: 'The recording was cut short (storage full or the microphone stopped). What was recorded until then is kept.', tlMissing: 'The files of {n} clips could not be found; those clips stay silent.', tlSaveFail: 'The project could not be saved: the phone may be low on space.',
      tlNewB: 'The timeline is cleared. While the app stays open you can undo; once it is closed this cannot be undone.' },
    de: { recCut: 'Die Aufnahme wurde unterbrochen (Speicher voll oder Mikrofon ausgefallen). Was bis dahin aufgenommen wurde, ist gespeichert.', tlMissing: 'Die Dateien von {n} Clips wurden nicht gefunden; diese Clips bleiben stumm.', tlSaveFail: 'Das Projekt konnte nicht gespeichert werden: Der Speicher ist vielleicht knapp.',
      tlNewB: 'Die Zeitleiste wird geleert. Solange die App offen bleibt, kannst du es rückgängig machen; nach dem Schließen nicht mehr.' },
    fr: { recCut: 'L’enregistrement a été interrompu (stockage plein ou micro coupé). Ce qui avait été enregistré est conservé.', tlMissing: 'Les fichiers de {n} clips sont introuvables ; ces clips restent muets.', tlSaveFail: 'Le projet n’a pas pu être enregistré : le téléphone manque peut-être d’espace.',
      tlNewB: 'La timeline est vidée. Tant que l’app reste ouverte, tu peux annuler ; une fois fermée, ce n’est plus possible.' },
    es: { recCut: 'La grabación se cortó (almacenamiento lleno o micrófono detenido). Lo grabado hasta entonces se ha guardado.', tlMissing: 'No se encontraron los archivos de {n} clips; esos clips quedan en silencio.', tlSaveFail: 'No se pudo guardar el proyecto: puede que el teléfono tenga poco espacio.',
      tlNewB: 'La línea de tiempo se vacía. Mientras la app siga abierta puedes deshacer; al cerrarla ya no se puede.' },
    pt: { recCut: 'A gravação foi interrompida (armazenamento cheio ou microfone parou). O que foi gravado até então foi mantido.', tlMissing: 'Os arquivos de {n} clipes não foram encontrados; esses clipes ficam em silêncio.', tlSaveFail: 'Não foi possível salvar o projeto: o celular pode estar com pouco espaço.',
      tlNewB: 'A linha do tempo é esvaziada. Enquanto o app estiver aberto você pode desfazer; depois de fechado, não dá mais.' },
    ru: { recCut: 'Запись прервалась (память заполнена или микрофон отключился). Записанное до этого момента сохранено.', tlMissing: 'Не найдены файлы фрагментов: {n}. Эти фрагменты будут без звука.', tlSaveFail: 'Не удалось сохранить проект: возможно, на телефоне мало места.',
      tlNewB: 'Таймлайн будет очищен. Пока приложение открыто, можно нажать «Отменить»; после закрытия отменить нельзя.' },
    ar: { recCut: 'انقطع التسجيل (امتلأت الذاكرة أو توقف الميكروفون). حُفظ ما سُجّل حتى تلك اللحظة.', tlMissing: 'تعذّر العثور على ملفات {n} من المقاطع؛ ستبقى هذه المقاطع صامتة.', tlSaveFail: 'تعذّر حفظ المشروع: ربما المساحة في الهاتف قليلة.',
      tlNewB: 'سيُفرَّغ الخط الزمني. ما دام التطبيق مفتوحاً يمكنك التراجع؛ بعد إغلاقه لا يمكن التراجع.' },
    zh: { recCut: '录音中途中断（存储已满或麦克风停止）。中断前录下的内容已保存。', tlMissing: '有 {n} 个片段的文件找不到，这些片段将没有声音。', tlSaveFail: '项目无法保存：手机空间可能不足。',
      tlNewB: '时间线将被清空。应用保持打开时可以撤销；关闭应用后无法撤销。' }
  };
  const D = window.SD_I18N = window.SD_I18N || {};
  for (const k in L) D[k] = Object.assign(D[k] || {}, L[k]);
})();

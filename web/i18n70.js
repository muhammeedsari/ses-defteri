/* Recovery and incomplete-operation messages. */
(function () {
  const L = {
    tr: { recoveryPending: 'Kayıt korunuyor, ancak henüz tamamlanamadı. Depolama alanını kontrol edip uygulamayı yeniden açarak kurtarmayı tekrar dene.', archiveLimit: 'Yedek boyut sınırını aşıyor veya doğrulama için yeterli boş alan yok.', livePartial: 'Bu metinde eksik bölümler var. Ses kaydı korunuyor. Tam metin için yeniden yazıya dök.' },
    en: { recoveryPending: 'The recording is preserved but could not be finalized. Check free storage and reopen the app to retry recovery.', archiveLimit: 'The backup exceeds the size limit or there is not enough free space to verify it.', livePartial: 'This text has missing sections. The audio recording is preserved. Transcribe again for the full text.' },
    de: { recoveryPending: 'Die Aufnahme bleibt erhalten, konnte aber nicht abgeschlossen werden. Speicherplatz prüfen und die App zur Wiederherstellung erneut öffnen.', archiveLimit: 'Die Sicherung ist zu groß oder es fehlt freier Speicher zur Prüfung.', livePartial: 'In diesem Text fehlen Abschnitte. Die Tonaufnahme bleibt erhalten. Für den vollständigen Text erneut transkribieren.' },
    fr: { recoveryPending: 'L’enregistrement est conservé mais n’a pas pu être finalisé. Vérifiez l’espace libre et rouvrez l’application pour réessayer.', archiveLimit: 'La sauvegarde dépasse la limite ou l’espace libre est insuffisant pour la vérifier.', livePartial: 'Des passages manquent dans ce texte. L’audio est conservé. Relancez la transcription pour obtenir le texte complet.' },
    es: { recoveryPending: 'La grabación se conserva, pero no se pudo finalizar. Comprueba el espacio libre y vuelve a abrir la aplicación para recuperarla.', archiveLimit: 'La copia supera el límite o no hay espacio libre suficiente para verificarla.', livePartial: 'Faltan partes en este texto. El audio se conserva. Transcribe de nuevo para obtener el texto completo.' },
    pt: { recoveryPending: 'A gravação foi preservada, mas não pôde ser finalizada. Verifique o espaço livre e reabra o aplicativo para tentar recuperá-la.', archiveLimit: 'O backup excede o limite ou não há espaço livre suficiente para verificá-lo.', livePartial: 'Faltam trechos neste texto. O áudio foi preservado. Transcreva novamente para obter o texto completo.' },
    ru: { recoveryPending: 'Запись сохранена, но завершить её не удалось. Проверьте свободное место и откройте приложение снова для восстановления.', archiveLimit: 'Резервная копия превышает лимит или недостаточно места для её проверки.', livePartial: 'В тексте пропущены фрагменты. Аудиозапись сохранена. Повторите расшифровку для получения полного текста.' },
    ar: { recoveryPending: 'تم الاحتفاظ بالتسجيل، لكن تعذر إنهاؤه. تحقق من المساحة المتاحة وأعد فتح التطبيق لمحاولة الاسترداد.', archiveLimit: 'تتجاوز النسخة الاحتياطية الحد المسموح أو لا توجد مساحة كافية للتحقق منها.', livePartial: 'توجد أجزاء مفقودة في النص. التسجيل الصوتي محفوظ. أعد التفريغ للحصول على النص الكامل.' },
    zh: { recoveryPending: '录音已保留，但暂时无法完成。请检查可用存储空间并重新打开应用以重试恢复。', archiveLimit: '备份超出大小限制，或没有足够的可用空间进行验证。', livePartial: '此文本有缺失部分。录音已保留。请重新转写以获取完整文本。' }
  };
  const D = window.SD_I18N = window.SD_I18N || {};
  for (const k in L) D[k] = Object.assign(D[k] || {}, L[k]);
  const interrupted = {
    tr: 'Ekran yeniden açıldığı için devam eden işlemler kesildi. Asıl kayıtlar korundu. Kitaplıkta oluşmuş yeni kopyaları kontrol edip gerekirse işlemi yeniden başlat.',
    en: 'The screen restarted, so unfinished operations were interrupted. Original recordings were preserved. Check the library for any new copies before retrying.',
    de: 'Der Bildschirm wurde neu gestartet; laufende Vorgänge wurden unterbrochen. Originalaufnahmen bleiben erhalten. Vor einem neuen Versuch die Bibliothek auf neue Kopien prüfen.',
    fr: 'L’écran a redémarré et les opérations en cours ont été interrompues. Les originaux sont conservés. Vérifiez les nouvelles copies dans la bibliothèque avant de réessayer.',
    es: 'La pantalla se reinició y se interrumpieron las operaciones pendientes. Los originales se conservaron. Revisa las nuevas copias en la biblioteca antes de reintentarlo.',
    pt: 'A tela reiniciou e as operações pendentes foram interrompidas. Os originais foram preservados. Verifique novas cópias na biblioteca antes de tentar novamente.',
    ru: 'Экран перезапустился, незавершённые операции прерваны. Исходные записи сохранены. Перед повтором проверьте новые копии в библиотеке.',
    ar: 'أُعيد تشغيل الشاشة وتوقفت العمليات غير المكتملة. التسجيلات الأصلية محفوظة. تحقق من النسخ الجديدة في المكتبة قبل إعادة المحاولة.',
    zh: '界面已重新启动，未完成的操作已中断。原始录音已保留。请先检查媒体库中的新副本，再重试。'
  };
  for (const k in interrupted) D[k].jobsInterrupted = interrupted[k];
  const legacy = {
    tr: 'Eski yedekte bazı özgün sesler ve geçmiş dosyaları yok. Projenin duyulan sesi korundu; eksik geçmiş ve hesaplanan efekt ayarları kaldırıldı.',
    en: 'Some original and history audio files are absent from this old backup. The audible project is preserved; unavailable history and computed-effect settings were removed.',
    de: 'In dieser alten Sicherung fehlen Original- und Verlaufsdateien. Der hörbare Projektstand bleibt erhalten; nicht verfügbarer Verlauf und berechnete Effekteinstellungen wurden entfernt.',
    fr: 'Cette ancienne sauvegarde ne contient pas certains fichiers originaux et historiques. Le son du projet est conservé ; l’historique indisponible et les réglages des effets calculés ont été retirés.',
    es: 'Faltan archivos originales y del historial en esta copia antigua. Se conserva el audio del proyecto; se quitaron el historial no disponible y los ajustes de efectos calculados.',
    pt: 'Este backup antigo não contém alguns áudios originais e do histórico. O som do projeto foi preservado; o histórico indisponível e os ajustes de efeitos calculados foram removidos.',
    ru: 'В старой копии нет части исходных файлов и истории. Звучание проекта сохранено; недоступная история и настройки рассчитанных эффектов удалены.',
    ar: 'تفتقد هذه النسخة القديمة بعض ملفات الصوت الأصلية وسجل التعديلات. تم الاحتفاظ بصوت المشروع وإزالة السجل غير المتاح وإعدادات التأثيرات المحسوبة.',
    zh: '此旧备份缺少部分原始音频和历史文件。已保留项目当前声音，并移除不可用的历史记录和已计算效果的设置。'
  };
  for (const k in legacy) D[k].rsLegacy = legacy[k];
})();

/* 5.17: live text in every language (Whisper), fast Turkish transcription with seda, marks in the live text */
(function () {
  const L = {
    tr: { liveSwD: 'Konuştukça yazı akar; kayıt bitince kaydın metni olur. Türkçe\'de seda modeli, diğer dillerde Whisper (birkaç saniye gecikmeli). Pil biraz daha fazla harcanır.',
      asr_seda: 'seda (Türkçe, hızlı)', asrD_seda: 'Yalnız Türkçe · Whisper small\'dan daha doğru ve çok daha hızlı · canlı yazı modelini kullanır',
      news_n517fast: 'Türkçe kayıtları seda ile yazıya dök: Ayarlar\'da "seda (Türkçe, hızlı)" seç; daha doğru ve çok daha hızlı.', news_n517lang: 'Canlı yazı artık 9 dilde: Türkçe dışında Whisper cümle cümle yazar.', news_n517marks: 'Kayıt sırasında koyduğun işaretler canlı yazının içinde görünür.', news_n517music: 'Whisper müzikte "(Müzik çalıyor)" gibi uydurma yazılar eklemiyor.' },
    en: { liveSwD: 'Text flows as you speak and becomes the recording\'s text when you stop. Turkish uses the seda model, other languages Whisper (a few seconds behind). Uses a little more battery.',
      asr_seda: 'seda (Turkish, fast)', asrD_seda: 'Turkish only · more accurate than Whisper small and much faster · uses the live text model',
      news_n517fast: 'Transcribe Turkish recordings with seda: pick "seda (Turkish, fast)" in Settings; more accurate and much faster.', news_n517lang: 'Live text now works in all 9 languages: outside Turkish, Whisper writes sentence by sentence.', news_n517marks: 'Marks you add while recording show up in the live text.', news_n517music: 'Whisper no longer adds made-up labels like "(Music playing)" over music.' },
    de: { liveSwD: 'Der Text erscheint beim Sprechen und wird am Ende zum Text der Aufnahme. Türkisch mit seda, andere Sprachen mit Whisper (einige Sekunden später). Braucht etwas mehr Akku.',
      asr_seda: 'seda (Türkisch, schnell)', asrD_seda: 'Nur Türkisch · genauer als Whisper small und viel schneller · nutzt das Live-Text-Modell',
      news_n517fast: 'Türkische Aufnahmen mit seda transkribieren: in den Einstellungen „seda (Türkisch, schnell)“ wählen.', news_n517lang: 'Live-Text jetzt in allen 9 Sprachen: außer Türkisch schreibt Whisper Satz für Satz.', news_n517marks: 'Marken beim Aufnehmen erscheinen im Live-Text.', news_n517music: 'Whisper fügt bei Musik keine erfundenen Hinweise wie „(Musik)“ mehr ein.' },
    fr: { liveSwD: 'Le texte s\'affiche pendant que vous parlez et devient le texte de l\'enregistrement. Turc avec seda, autres langues avec Whisper (quelques secondes de retard). Consomme un peu plus de batterie.',
      asr_seda: 'seda (turc, rapide)', asrD_seda: 'Turc uniquement · plus précis que Whisper small et bien plus rapide · utilise le modèle de texte en direct',
      news_n517fast: 'Transcrivez le turc avec seda : choisissez « seda (turc, rapide) » dans Réglages.', news_n517lang: 'Texte en direct dans les 9 langues : hors turc, Whisper écrit phrase par phrase.', news_n517marks: 'Les repères ajoutés pendant l\'enregistrement apparaissent dans le texte en direct.', news_n517music: 'Whisper n\'ajoute plus de mentions inventées comme « (Musique) » sur la musique.' },
    es: { liveSwD: 'El texto aparece mientras hablas y se convierte en el texto de la grabación. Turco con seda, otros idiomas con Whisper (unos segundos de retraso). Usa algo más de batería.',
      asr_seda: 'seda (turco, rápido)', asrD_seda: 'Solo turco · más preciso que Whisper small y mucho más rápido · usa el modelo de texto en vivo',
      news_n517fast: 'Transcribe grabaciones en turco con seda: elige «seda (turco, rápido)» en Ajustes.', news_n517lang: 'Texto en vivo en los 9 idiomas: fuera del turco, Whisper escribe frase a frase.', news_n517marks: 'Las marcas que añades al grabar aparecen en el texto en vivo.', news_n517music: 'Whisper ya no añade etiquetas inventadas como «(Música)» sobre la música.' },
    pt: { liveSwD: 'O texto aparece enquanto você fala e vira o texto da gravação. Turco com seda, outros idiomas com Whisper (alguns segundos de atraso). Gasta um pouco mais de bateria.',
      asr_seda: 'seda (turco, rápido)', asrD_seda: 'Só turco · mais preciso que o Whisper small e bem mais rápido · usa o modelo de texto ao vivo',
      news_n517fast: 'Transcreva gravações em turco com seda: escolha "seda (turco, rápido)" nas Configurações.', news_n517lang: 'Texto ao vivo nos 9 idiomas: fora do turco, o Whisper escreve frase a frase.', news_n517marks: 'As marcas adicionadas ao gravar aparecem no texto ao vivo.', news_n517music: 'O Whisper não adiciona mais rótulos inventados como "(Música)" sobre música.' },
    ru: { liveSwD: 'Текст появляется по ходу речи и после остановки становится текстом записи. Турецкий — модель seda, другие языки — Whisper (с задержкой в несколько секунд). Чуть больше расход батареи.',
      asr_seda: 'seda (турецкий, быстро)', asrD_seda: 'Только турецкий · точнее Whisper small и намного быстрее · использует модель живого текста',
      news_n517fast: 'Распознавайте турецкие записи с seda: выберите «seda (турецкий, быстро)» в настройках.', news_n517lang: 'Живой текст на всех 9 языках: кроме турецкого, Whisper пишет по предложениям.', news_n517marks: 'Метки, поставленные во время записи, видны в живом тексте.', news_n517music: 'Whisper больше не добавляет выдуманные пометки вроде «(Музыка)» на музыке.' },
    ar: { liveSwD: 'يظهر النص أثناء الكلام ويصبح نص التسجيل عند الإيقاف. التركية بنموذج seda، واللغات الأخرى بـ Whisper (بتأخير ثوانٍ). يستهلك بطارية أكثر قليلًا.',
      asr_seda: 'seda (تركي، سريع)', asrD_seda: 'التركية فقط · أدق من Whisper small وأسرع بكثير · يستخدم نموذج النص المباشر',
      news_n517fast: 'فرّغ التسجيلات التركية بـ seda: اختر «seda (تركي، سريع)» في الإعدادات.', news_n517lang: 'النص المباشر الآن بكل اللغات التسع: خارج التركية يكتب Whisper جملة بجملة.', news_n517marks: 'تظهر العلامات التي تضيفها أثناء التسجيل داخل النص المباشر.', news_n517music: 'لم يعد Whisper يضيف عبارات مختلقة مثل «(موسيقى)» على الموسيقى.' },
    zh: { liveSwD: '说话时文字实时出现，停止后成为录音的文字。土耳其语用 seda 模型，其他语言用 Whisper（延迟几秒）。耗电略多。',
      asr_seda: 'seda（土耳其语，快速）', asrD_seda: '仅限土耳其语 · 比 Whisper small 更准确且快得多 · 使用实时文字模型',
      news_n517fast: '用 seda 转写土耳其语录音：在设置中选择“seda（土耳其语，快速）”。', news_n517lang: '实时文字现支持全部 9 种语言：土耳其语以外由 Whisper 逐句书写。', news_n517marks: '录音时添加的标记会显示在实时文字中。', news_n517music: 'Whisper 不再在音乐上添加“（音乐）”之类的编造标签。' }
  };
  const D = window.SD_I18N = window.SD_I18N || {};
  for (const k in L) D[k] = Object.assign(D[k] || {}, L[k]);
})();

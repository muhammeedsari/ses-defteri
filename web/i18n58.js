/* 5.7: chords and song sections */
(function () {
  const L = {
    tr: { smT: 'Akorlar ve bölümler', smShow: 'Akorları göster', smHide: 'Akorları gizle', smOnT: 'Çalarken akorlar ekranda görünür; bir bölüme dokunursan o kısım tekrar eder.', sec_intro: 'Giriş', sec_verse: 'Kıta', sec_chorus: 'Nakarat', sec_bridge: 'Köprü', sec_outro: 'Çıkış' },
    en: { smT: 'Chords and sections', smShow: 'Show chords', smHide: 'Hide chords', smOnT: 'Chords show on screen while playing; tap a section to loop it.', sec_intro: 'Intro', sec_verse: 'Verse', sec_chorus: 'Chorus', sec_bridge: 'Bridge', sec_outro: 'Outro' },
    de: { smT: 'Akkorde und Teile', smShow: 'Akkorde zeigen', smHide: 'Akkorde ausblenden', smOnT: 'Beim Abspielen erscheinen die Akkorde; tippe auf einen Teil, um ihn zu wiederholen.', sec_intro: 'Intro', sec_verse: 'Strophe', sec_chorus: 'Refrain', sec_bridge: 'Bridge', sec_outro: 'Outro' },
    fr: { smT: 'Accords et sections', smShow: 'Afficher les accords', smHide: 'Masquer les accords', smOnT: 'Les accords s’affichent pendant la lecture ; touchez une section pour la répéter.', sec_intro: 'Intro', sec_verse: 'Couplet', sec_chorus: 'Refrain', sec_bridge: 'Pont', sec_outro: 'Outro' },
    es: { smT: 'Acordes y secciones', smShow: 'Mostrar acordes', smHide: 'Ocultar acordes', smOnT: 'Los acordes aparecen al reproducir; toca una sección para repetirla.', sec_intro: 'Intro', sec_verse: 'Estrofa', sec_chorus: 'Estribillo', sec_bridge: 'Puente', sec_outro: 'Final' },
    pt: { smT: 'Acordes e seções', smShow: 'Mostrar acordes', smHide: 'Ocultar acordes', smOnT: 'Os acordes aparecem ao tocar; toque numa seção para repeti-la.', sec_intro: 'Intro', sec_verse: 'Verso', sec_chorus: 'Refrão', sec_bridge: 'Ponte', sec_outro: 'Final' },
    ru: { smT: 'Аккорды и части', smShow: 'Показать аккорды', smHide: 'Скрыть аккорды', smOnT: 'Во время воспроизведения аккорды видны на экране; нажмите на часть, чтобы зациклить её.', sec_intro: 'Вступление', sec_verse: 'Куплет', sec_chorus: 'Припев', sec_bridge: 'Бридж', sec_outro: 'Концовка' },
    ar: { smT: 'الأوتار والمقاطع', smShow: 'إظهار الأوتار', smHide: 'إخفاء الأوتار', smOnT: 'تظهر الأوتار على الشاشة أثناء التشغيل؛ اضغط على مقطع لتكراره.', sec_intro: 'مقدمة', sec_verse: 'مقطع', sec_chorus: 'لازمة', sec_bridge: 'جسر', sec_outro: 'خاتمة' },
    zh: { smT: '和弦与段落', smShow: '显示和弦', smHide: '隐藏和弦', smOnT: '播放时屏幕上显示和弦；点一个段落即可循环播放。', sec_intro: '前奏', sec_verse: '主歌', sec_chorus: '副歌', sec_bridge: '桥段', sec_outro: '尾奏' }
  };
  const D = window.SD_I18N = window.SD_I18N || {};
  for (const k in L) D[k] = Object.assign(D[k] || {}, L[k]);
})();

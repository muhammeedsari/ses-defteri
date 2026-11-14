/* 5.8: studio voice and ready-made effects in the effect list */
(function () {
  const L = {
    tr: { rk_amb: 'Oda sesi', rk_id: 'Hazır efekt' },
    en: { rk_amb: 'Room sound', rk_id: 'Preset' },
    de: { rk_amb: 'Raumklang', rk_id: 'Vorlage' },
    fr: { rk_amb: 'Son de la pièce', rk_id: 'Préréglage' },
    es: { rk_amb: 'Sonido de la sala', rk_id: 'Preajuste' },
    pt: { rk_amb: 'Som da sala', rk_id: 'Predefinição' },
    ru: { rk_amb: 'Звук комнаты', rk_id: 'Пресет' },
    ar: { rk_amb: 'صوت الغرفة', rk_id: 'إعداد جاهز' },
    zh: { rk_amb: '房间声', rk_id: '预设' }
  };
  const D = window.SD_I18N = window.SD_I18N || {};
  for (const k in L) D[k] = Object.assign(D[k] || {}, L[k]);
})();

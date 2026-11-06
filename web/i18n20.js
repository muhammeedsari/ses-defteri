/* Ses Defteri 3.0 — spectral view and painted repair (merged into SD_I18N). */
(function () {
const A = {
tr: { tlSpec: "Spektrum", specHint: "Ses burada renkli bir harita: sola sağa zaman, aşağı yukarı ses perdesi (alçak sesler altta). Öksürük, zil, kapı gibi istenmeyen sesin üzerine parmağınla kutu çiz. Kutuya dokununca silinir.",
  specPrev: "Önceki bölüm", specNext: "Sonraki bölüm", specClear: "Kutuları temizle", specGo: "Sil", specGoN: "Sil ({n})", specLoad: "Hazırlanıyor…",
  spm_blend: "Arka plana karıştır", spm_mute: "Tamamen sustur", op_spaint: "Spektrumdan silme", rpw_spaint: "{n} alan temizlendi" },
en: { tlSpec: "Spectrum", specHint: "Here the sound is a colour map: time left to right, pitch bottom to top (low sounds at the bottom). Draw a box with your finger over an unwanted sound like a cough, a ring or a door. Tap a box to remove it.",
  specPrev: "Previous part", specNext: "Next part", specClear: "Clear boxes", specGo: "Remove", specGoN: "Remove ({n})", specLoad: "Preparing…",
  spm_blend: "Blend into background", spm_mute: "Silence completely", op_spaint: "Spectral removal", rpw_spaint: "{n} areas cleaned" },
de: { tlSpec: "Spektrum", specHint: "Hier ist der Ton eine Farbkarte: Zeit von links nach rechts, Tonhöhe von unten nach oben (tiefe Töne unten). Zeichne mit dem Finger einen Kasten über ein störendes Geräusch wie Husten, Klingeln oder eine Tür. Tippe auf einen Kasten, um ihn zu entfernen.",
  specPrev: "Vorheriger Teil", specNext: "Nächster Teil", specClear: "Kästen löschen", specGo: "Entfernen", specGoN: "Entfernen ({n})", specLoad: "Wird vorbereitet…",
  spm_blend: "In den Hintergrund mischen", spm_mute: "Ganz stumm schalten", op_spaint: "Spektrale Entfernung", rpw_spaint: "{n} Bereiche bereinigt" },
fr: { tlSpec: "Spectre", specHint: "Ici le son est une carte de couleurs : le temps de gauche à droite, la hauteur de bas en haut (sons graves en bas). Dessine du doigt un cadre sur un bruit gênant comme une toux, une sonnerie ou une porte. Touche un cadre pour le retirer.",
  specPrev: "Partie précédente", specNext: "Partie suivante", specClear: "Effacer les cadres", specGo: "Supprimer", specGoN: "Supprimer ({n})", specLoad: "Préparation…",
  spm_blend: "Fondre dans le fond", spm_mute: "Rendre totalement muet", op_spaint: "Suppression spectrale", rpw_spaint: "{n} zones nettoyées" },
es: { tlSpec: "Espectro", specHint: "Aquí el sonido es un mapa de colores: el tiempo de izquierda a derecha, el tono de abajo arriba (los graves abajo). Dibuja con el dedo un recuadro sobre un sonido no deseado como una tos, un timbre o una puerta. Toca un recuadro para quitarlo.",
  specPrev: "Parte anterior", specNext: "Parte siguiente", specClear: "Borrar recuadros", specGo: "Quitar", specGoN: "Quitar ({n})", specLoad: "Preparando…",
  spm_blend: "Mezclar con el fondo", spm_mute: "Silenciar por completo", op_spaint: "Eliminación espectral", rpw_spaint: "{n} zonas limpiadas" },
pt: { tlSpec: "Espectro", specHint: "Aqui o som é um mapa de cores: o tempo da esquerda para a direita, o tom de baixo para cima (graves embaixo). Desenhe com o dedo um quadro sobre um som indesejado, como tosse, campainha ou porta. Toque num quadro para removê-lo.",
  specPrev: "Parte anterior", specNext: "Próxima parte", specClear: "Limpar quadros", specGo: "Remover", specGoN: "Remover ({n})", specLoad: "Preparando…",
  spm_blend: "Misturar ao fundo", spm_mute: "Silenciar totalmente", op_spaint: "Remoção espectral", rpw_spaint: "{n} áreas limpas" },
ru: { tlSpec: "Спектр", specHint: "Здесь звук — цветная карта: время слева направо, высота снизу вверх (низкие звуки внизу). Нарисуй пальцем рамку поверх лишнего звука — кашля, звонка, двери. Нажми на рамку, чтобы убрать её.",
  specPrev: "Предыдущая часть", specNext: "Следующая часть", specClear: "Убрать рамки", specGo: "Удалить", specGoN: "Удалить ({n})", specLoad: "Готовлю…",
  spm_blend: "Смешать с фоном", spm_mute: "Полностью заглушить", op_spaint: "Спектральное удаление", rpw_spaint: "Очищено областей: {n}" },
ar: { tlSpec: "الطيف", specHint: "هنا يظهر الصوت كخريطة ألوان: الزمن من اليسار إلى اليمين، وحدة الصوت من الأسفل إلى الأعلى (الأصوات المنخفضة في الأسفل). ارسم بإصبعك مربعًا فوق الصوت غير المرغوب مثل السعال أو الرنين أو الباب. المس المربع لإزالته.",
  specPrev: "الجزء السابق", specNext: "الجزء التالي", specClear: "مسح المربعات", specGo: "إزالة", specGoN: "إزالة ({n})", specLoad: "جارٍ التحضير…",
  spm_blend: "دمج مع الخلفية", spm_mute: "كتم تام", op_spaint: "إزالة طيفية", rpw_spaint: "تم تنظيف {n} مناطق" },
zh: { tlSpec: "频谱", specHint: "这里把声音显示成彩色图：从左到右是时间，从下到上是音高（低音在下）。用手指在咳嗽、铃声、关门声等不需要的声音上画一个框。点按框可将其删除。",
  specPrev: "上一段", specNext: "下一段", specClear: "清除所有框", specGo: "去除", specGoN: "去除（{n}）", specLoad: "正在准备…",
  spm_blend: "融入背景", spm_mute: "完全静音", op_spaint: "频谱去除", rpw_spaint: "已清理 {n} 个区域" }
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

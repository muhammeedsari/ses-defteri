/* 5.1: computed effects in the effect list (merged into SD_I18N). */
(function () {
const A = {
tr: { rkHeavy: "Hesaplanan efektler", rkLive: "Canlı efektler", hvAdded: "{x} efekt listesine eklendi; ayarını sonradan değiştirebilirsin", hvBaked: "Hesaplanan efektler sese işlendi (kesme gibi işlemler sesin duyulan hâliyle çalışır)", hvWorking: "Hesaplanıyor… %{p}", rk_strength: "Şiddet", rk_style: "Tarz" },
en: { rkHeavy: "Computed effects", rkLive: "Live effects", hvAdded: "{x} added to the effect list; you can change it later", hvBaked: "The computed effects are now part of the sound (cuts and the like work on the sound as heard)", hvWorking: "Working it out… {p}%", rk_strength: "Strength", rk_style: "Style" },
de: { rkHeavy: "Berechnete Effekte", rkLive: "Live-Effekte", hvAdded: "{x} zur Effektliste hinzugefügt; später änderbar", hvBaked: "Die berechneten Effekte sind jetzt Teil des Tons (Schnitte u. Ä. arbeiten mit dem hörbaren Ton)", hvWorking: "Wird berechnet … {p} %", rk_strength: "Stärke", rk_style: "Stil" },
fr: { rkHeavy: "Effets calculés", rkLive: "Effets en direct", hvAdded: "{x} ajouté à la liste d'effets ; modifiable plus tard", hvBaked: "Les effets calculés font maintenant partie du son (les coupes et autres s'appliquent au son tel qu'on l'entend)", hvWorking: "Calcul en cours… {p} %", rk_strength: "Intensité", rk_style: "Style" },
es: { rkHeavy: "Efectos calculados", rkLive: "Efectos en directo", hvAdded: "{x} añadido a la lista de efectos; puedes cambiarlo después", hvBaked: "Los efectos calculados ya forman parte del sonido (los cortes y similares trabajan con el sonido tal como se oye)", hvWorking: "Calculando… {p} %", rk_strength: "Intensidad", rk_style: "Estilo" },
pt: { rkHeavy: "Efeitos calculados", rkLive: "Efeitos ao vivo", hvAdded: "{x} adicionado à lista de efeitos; dá para mudar depois", hvBaked: "Os efeitos calculados agora fazem parte do som (cortes e afins usam o som como se ouve)", hvWorking: "Calculando… {p}%", rk_strength: "Intensidade", rk_style: "Estilo" },
ru: { rkHeavy: "Вычисляемые эффекты", rkLive: "Живые эффекты", hvAdded: "{x}: добавлено в список эффектов; можно изменить позже", hvBaked: "Вычисленные эффекты теперь часть звука (обрезка и подобное работают со звуком как он слышен)", hvWorking: "Вычисляется… {p}%", rk_strength: "Сила", rk_style: "Стиль" },
ar: { rkHeavy: "مؤثرات محسوبة", rkLive: "مؤثرات مباشرة", hvAdded: "أُضيف {x} إلى قائمة المؤثرات؛ يمكنك تغييره لاحقًا", hvBaked: "أصبحت المؤثرات المحسوبة جزءًا من الصوت (القص وما شابهه يعمل على الصوت كما يُسمع)", hvWorking: "جارٍ الحساب… {p}%", rk_strength: "الشدة", rk_style: "النمط" },
zh: { rkHeavy: "计算型效果", rkLive: "实时效果", hvAdded: "已将{x}加入效果列表，之后可随时修改", hvBaked: "计算型效果已写入声音（剪切等操作作用于听到的声音）", hvWorking: "正在计算… {p}%", rk_strength: "强度", rk_style: "风格" },
};
for (const l in A) window.SD_I18N[l] = Object.assign(window.SD_I18N[l] || {}, A[l]);
})();

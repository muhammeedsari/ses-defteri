/* 5.21.1: AI noise removal on a song: warning, the music tool offered first */
(function () {
  const L = {
    tr: { aiMusicWarn: 'Bu parça müzik gibi görünüyor. YZ gürültü temizliği konuşma içindir ve müziği de siler; müzik için ilk seçeneği kullan.' },
    en: { aiMusicWarn: 'This looks like music. AI noise removal is made for speech and removes music too; for music use the first choice.' },
    de: { aiMusicWarn: 'Das sieht nach Musik aus. Die KI-Rauschentfernung ist für Sprache gemacht und entfernt auch Musik; für Musik die erste Wahl nehmen.' },
    fr: { aiMusicWarn: 'Cela ressemble à de la musique. La suppression du bruit par IA est faite pour la voix et enlève aussi la musique ; pour la musique, prenez le premier choix.' },
    es: { aiMusicWarn: 'Esto parece música. La eliminación de ruido con IA está hecha para la voz y también borra la música; para música usa la primera opción.' },
    pt: { aiMusicWarn: 'Isto parece música. A remoção de ruído por IA é feita para voz e também apaga a música; para música use a primeira opção.' },
    ru: { aiMusicWarn: 'Похоже на музыку. ИИ-шумоподавление сделано для речи и убирает и музыку; для музыки выберите первый вариант.' },
    ar: { aiMusicWarn: 'يبدو أن هذا موسيقى. إزالة الضوضاء بالذكاء الاصطناعي مخصصة للكلام وتزيل الموسيقى أيضًا؛ للموسيقى استخدم الخيار الأول.' },
    zh: { aiMusicWarn: '这看起来是音乐。AI 降噪是为语音设计的，也会去掉音乐；音乐请用第一个选项。' }
  };
  const D = window.SD_I18N = window.SD_I18N || {};
  for (const k in L) D[k] = Object.assign(D[k] || {}, L[k]);
})();

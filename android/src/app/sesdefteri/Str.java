package app.sesdefteri;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** The few texts the phone shows outside the page (notifications, choosers), in the app's language. */
final class Str {
    private static final String[] KEYS = {"recTitle", "recText", "chRec", "chPlay", "share", "pick", "prev", "play", "pause", "next", "chWork", "workTitle", "workText"};
    private static final Map<String, String[]> T = new HashMap<>();

    static {
        T.put("tr", new String[]{"Ses Defteri kaydediyor", "Kayda dönmek için dokun", "Kayıt", "Çalan ses", "Paylaş", "Ses dosyası seç", "Önceki", "Oynat", "Duraklat", "Sonraki", "İşlemler", "Ses Defteri çalışıyor", "İyileştirme, metin dökümü veya miks sürüyor"});
        T.put("en", new String[]{"Ses Defteri is recording", "Tap to return to the recording", "Recording", "Now playing", "Share", "Choose audio files", "Previous", "Play", "Pause", "Next", "Processing", "Ses Defteri is working", "Enhancing, transcribing or mixing"});
        T.put("de", new String[]{"Ses Defteri nimmt auf", "Tippen, um zur Aufnahme zurückzukehren", "Aufnahme", "Wiedergabe", "Teilen", "Audiodateien auswählen", "Zurück", "Abspielen", "Pause", "Weiter", "Verarbeitung", "Ses Defteri arbeitet", "Verbessern, Transkribieren oder Mischen läuft"});
        T.put("fr", new String[]{"Ses Defteri enregistre", "Touchez pour revenir à l'enregistrement", "Enregistrement", "Lecture en cours", "Partager", "Choisir des fichiers audio", "Précédent", "Lire", "Pause", "Suivant", "Traitement", "Ses Defteri travaille", "Amélioration, transcription ou mixage en cours"});
        T.put("es", new String[]{"Ses Defteri está grabando", "Toca para volver a la grabación", "Grabación", "Reproduciendo", "Compartir", "Elegir archivos de audio", "Anterior", "Reproducir", "Pausa", "Siguiente", "Procesamiento", "Ses Defteri está trabajando", "Mejorando, transcribiendo o mezclando"});
        T.put("pt", new String[]{"Ses Defteri está gravando", "Toque para voltar à gravação", "Gravação", "Tocando agora", "Compartilhar", "Escolher arquivos de áudio", "Anterior", "Tocar", "Pausar", "Próxima", "Processamento", "Ses Defteri está trabalhando", "Melhorando, transcrevendo ou mixando"});
        T.put("ru", new String[]{"Ses Defteri записывает", "Нажмите, чтобы вернуться к записи", "Запись", "Сейчас играет", "Поделиться", "Выбрать аудиофайлы", "Назад", "Играть", "Пауза", "Далее", "Обработка", "Ses Defteri работает", "Улучшение, расшифровка или сведение"});
        T.put("ar", new String[]{"Ses Defteri يسجّل الآن", "اضغط للعودة إلى التسجيل", "التسجيل", "قيد التشغيل", "مشاركة", "اختر ملفات صوتية", "السابق", "تشغيل", "إيقاف مؤقت", "التالي", "المعالجة", "Ses Defteri يعمل", "جارٍ التحسين أو التفريغ النصي أو المزج"});
        T.put("zh", new String[]{"Ses Defteri 正在录音", "点按返回录音", "录音", "正在播放", "分享", "选择音频文件", "上一首", "播放", "暂停", "下一首", "处理", "Ses Defteri 正在处理", "正在优化、转写或混音"});
    }

    private Str() { }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("sesdefteri", Context.MODE_PRIVATE);
    }

    static String lang(Context c) {
        String l = prefs(c).getString("lang", "");
        if (T.containsKey(l)) return l;
        String sys = Locale.getDefault().getLanguage();
        return T.containsKey(sys) ? sys : "tr";
    }

    static String get(Context c, String key) {
        String[] row = T.get(lang(c));
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return row[i];
        return key;
    }
}

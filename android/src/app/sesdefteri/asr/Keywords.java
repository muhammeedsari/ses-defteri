package app.sesdefteri.asr;

import java.util.*;

/** Automatic title and tags from a transcript (no network, no model: word statistics with stop-word lists). */
public final class Keywords {
    private Keywords() { }

    static final Set<String> STOP = new HashSet<>(Arrays.asList((
        // Turkish
        "ve veya ile bir bu şu o da de ki mi mı mu mü için gibi çok daha en ama fakat ya ne ben sen biz siz onlar olan olarak var yok " +
        "şey şeyi şeyler şimdi sonra önce kadar diye her hem ise eğer yani işte tamam evet hayır bunu şunu onu bunun şunun onun bana sana " +
        "bize size benim senin bizim sizin oldu olur olacak olsun oluyor ediyor etmek etti yapmak yaptı yapıyoruz yapacağım gerekiyor " +
        "lazım biraz bence sanki zaten artık hiç hep bazı bütün tüm böyle şöyle öyle burada orada nasıl neden niye hangi kim kimi kendi " +
        "değil aslında belki gibi göre karşı rağmen üzere dolayı beri itibaren doğru bile sadece yine tekrar çünkü ancak yoksa ayrıca " +
        "bunlar şunlar onlara bunlara onları bunları buna şuna ona olduğu olduğunu olması olmak olmadan bizi sizi beni seni mısın misin " +
        "mıyım miyim musun müsün geliyor gidiyor diyor dedi demek alıyor aldı veriyor verdi biri birisi birşey herşey hepsi " +
        // English
        "the a an and or but of to in on at for with from by as is are was were be been being it its this that these those i you he she " +
        "we they me him her us them my your our their not no yes so if then than there here what which who whom when where why how all " +
        "any some can could will would should do does did have has had just also very really like get got going go know think okay well").split(" ")));

    static List<String> words(String text) {
        List<String> w = new ArrayList<>();
        for (String t : text.toLowerCase(new Locale("tr")).split("[^\\p{L}\\p{N}']+")) {
            t = t.replaceAll("^'+|'+$", "");
            int ap = t.indexOf('\'');
            if (ap > 0) t = t.substring(0, ap);          // Kars'tan → kars
            if (t.length() >= 3 && !STOP.contains(t) && !t.matches("\\d+")) w.add(t);
        }
        return w;
    }

    /** up to n tags: frequent, longer, non-stop words; Turkish suffixes folded onto a shared stem when possible */
    public static List<String> tags(String text, int n) {
        List<String> w = words(text);
        Map<String, Integer> cnt = new HashMap<>();
        final Map<String, String> show = new HashMap<>();
        for (String x : w) {
            String stem = x.length() > 6 ? x.substring(0, 6) : x;
            Integer c0 = cnt.get(stem);
            cnt.put(stem, c0 == null ? 1 : c0 + 1);
            String cur = show.get(stem);
            if (cur == null || x.length() < cur.length()) show.put(stem, x);
        }
        List<Map.Entry<String, Integer>> e = new ArrayList<>(cnt.entrySet());
        Collections.sort(e, new Comparator<Map.Entry<String, Integer>>() {
            @Override public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                double sa = a.getValue() * Math.log(2 + show.get(a.getKey()).length()), sb = b.getValue() * Math.log(2 + show.get(b.getKey()).length());
                return Double.compare(sb, sa);
            }
        });
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> x : e) { if (out.size() >= n) break; out.add(show.get(x.getKey())); }
        return out;
    }

    /** a short title: the first utterance with some substance, cut at a word boundary */
    public static String title(List<Transcriber.Segment> segs) {
        for (Transcriber.Segment s : segs) {
            String t = s.text.replaceAll("[\\s]+", " ").trim();
            if (words(t).size() >= 2) {
                t = t.replaceAll("[.!?…]+$", "");
                if (t.length() > 60) {
                    int cut = t.lastIndexOf(' ', 57);
                    t = t.substring(0, cut > 20 ? cut : 57) + "…";
                }
                return t;
            }
        }
        return "";
    }
}

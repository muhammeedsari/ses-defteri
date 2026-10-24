package org.json;
import java.util.*;
public class JSONObject {
  final LinkedHashMap<String,Object> m = new LinkedHashMap<>();
  public JSONObject() {}
  public JSONObject(String s) { }
  public JSONObject put(String k, Object v) { m.put(k, v); return this; }
  public JSONObject put(String k, double v) { m.put(k, v); return this; }
  public JSONObject put(String k, long v) { m.put(k, v); return this; }
  public JSONObject put(String k, int v) { m.put(k, v); return this; }
  public JSONObject put(String k, boolean v) { m.put(k, v); return this; }
  public Object get(String k) { return m.get(k); }
  public boolean has(String k) { return m.containsKey(k); }
  public boolean isNull(String k) { return m.get(k) == null; }
  public Object remove(String k) { return m.remove(k); }
  public String getString(String k) { return String.valueOf(m.get(k)); }
  public String optString(String k, String d) { Object v = m.get(k); return v == null ? d : String.valueOf(v); }
  public String optString(String k) { return optString(k, ""); }
  public double optDouble(String k, double d) { Object v = m.get(k); return v instanceof Number ? ((Number) v).doubleValue() : d; }
  public double getDouble(String k) { return ((Number) m.get(k)).doubleValue(); }
  public int optInt(String k, int d) { Object v = m.get(k); return v instanceof Number ? ((Number) v).intValue() : d; }
  public boolean optBoolean(String k) { return optBoolean(k, false); }
  public boolean optBoolean(String k, boolean d) { Object v = m.get(k); return v instanceof Boolean ? (Boolean) v : d; }
  public JSONObject getJSONObject(String k) { return (JSONObject) m.get(k); }
  public JSONObject optJSONObject(String k) { Object v = m.get(k); return v instanceof JSONObject ? (JSONObject) v : null; }
  public JSONArray getJSONArray(String k) { return (JSONArray) m.get(k); }
  public JSONArray optJSONArray(String k) { Object v = m.get(k); return v instanceof JSONArray ? (JSONArray) v : null; }
  public long optLong(String k, long d) { Object v = m.get(k); return v instanceof Number ? ((Number) v).longValue() : d; }
  public long optLong(String k) { return optLong(k, 0); }
  public long getLong(String k) { return ((Number) m.get(k)).longValue(); }
  public int getInt(String k) { return ((Number) m.get(k)).intValue(); }
  public boolean getBoolean(String k) { return (Boolean) m.get(k); }
  public Iterator<String> keys() { return m.keySet().iterator(); }
  public int length() { return m.size(); }
  public static String quote(String s) { return "\"" + s + "\""; }
  public String toString() { StringBuilder b = new StringBuilder("{"); for (Map.Entry<String,Object> e : m.entrySet()) { if (b.length() > 1) b.append(','); b.append('"').append(e.getKey()).append("\":").append(e.getValue() instanceof String ? "\"" + e.getValue() + "\"" : String.valueOf(e.getValue())); } return b.append('}').toString(); }
}

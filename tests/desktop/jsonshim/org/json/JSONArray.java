package org.json;
import java.util.*;
public class JSONArray {
  final ArrayList<Object> l = new ArrayList<>();
  public JSONArray() {}
  public JSONArray(String s) { l.addAll(((JSONArray) new JSONObject.P(s).val()).l); }
  public JSONArray put(Object v) { l.add(v); return this; }
  public JSONArray put(double v) { l.add(v); return this; }
  public JSONArray put(long v) { l.add(v); return this; }
  public JSONArray put(int v) { l.add(v); return this; }
  public int length() { return l.size(); }
  public Object get(int i) { return l.get(i); }
  public double getDouble(int i) { return ((Number) l.get(i)).doubleValue(); }
  public double optDouble(int i, double d) { Object v = i < l.size() ? l.get(i) : null; return v instanceof Number ? ((Number) v).doubleValue() : d; }
  public String getString(int i) { return String.valueOf(l.get(i)); }
  public String optString(int i) { return i < l.size() ? String.valueOf(l.get(i)) : ""; }
  public JSONArray getJSONArray(int i) { return (JSONArray) l.get(i); }
  public JSONObject getJSONObject(int i) { return (JSONObject) l.get(i); }
  public long getLong(int i) { return ((Number) l.get(i)).longValue(); }
  public int getInt(int i) { return ((Number) l.get(i)).intValue(); }
  public JSONObject optJSONObject(int i) { Object v = i < l.size() ? l.get(i) : null; return v instanceof JSONObject ? (JSONObject) v : null; }
  public JSONArray optJSONArray(int i) { Object v = i < l.size() ? l.get(i) : null; return v instanceof JSONArray ? (JSONArray) v : null; }
  public String toString() { return l.toString(); }
}

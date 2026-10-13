package lab.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Current values of a set of parameters (edited in the UI, read by the factories).
 */
public final class ParamValues {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public static ParamValues defaults(List<ParamSpec> specs) {
        ParamValues v = new ParamValues();
        for (ParamSpec s : specs)
            v.values.put(s.name(), s.defaultValue());
        return v;
    }

    public ParamValues copy() {
        ParamValues v = new ParamValues();
        v.values.putAll(values);
        return v;
    }

    public Object get(String name) {
        return values.get(name);
    }

    public void set(String name, Object value) {
        values.put(name, value);
    }

    public int getInt(String name) {
        return (Integer) values.get(name);
    }

    public double getDouble(String name) {
        return ((Number) values.get(name)).doubleValue();
    }

    public String getChoice(String name) {
        return (String) values.get(name);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        values.forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ", ").append(k).append('=').append(v));
        return sb.toString();
    }
}

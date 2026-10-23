package dev.middleshare;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class CreditsCatalog {
    static List<JSONObject> load(Context context) throws IOException {
        try {
            JSONArray entries = new JSONObject(read(context, "third_party.json")).getJSONArray("components");
            ArrayList<JSONObject> components = new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) components.add(entries.getJSONObject(i));
            return components;
        } catch (JSONException e) { throw new IOException("Unable to read the included software credits.", e); }
    }
    static String read(Context context, String asset) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(asset), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
            return text.toString();
        }
    }
}

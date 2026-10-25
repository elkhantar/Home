package com.majnioui.tv.channels;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The channels. The base list is an M3U playlist at a link the owner can change in the app (by default
 * the playlist in res/values/config.xml); it is fetched each time the app starts, so editing that
 * playlist changes the channels. The last copy that could be fetched is saved, and res/raw/channels.m3u
 * is the copy for the very first start without a network.
 *
 * On top of the playlist sit the owner's own edits, made in the app: channels removed, changed or
 * added. They are kept apart from the playlist (edits.json) so a new copy of it doesn't undo them.
 */
final class ChannelList {
    private static final String TAG = "Channels";
    private static final String SAVED = "playlist.m3u";
    private static final String EDITS = "edits.json";
    private static final Pattern ATTR = Pattern.compile("([a-z-]+)=\"([^\"]*)\"");
    /** "(1080p)", "[Geo-blocked]", "[Not 24/7]": playlist notes, not part of the name. */
    private static final Pattern NOTES = Pattern.compile("\\s*(\\(\\d{3,4}[pi]\\)|\\[[^\\]]*\\])");

    static final class Channel {
        /** Stable name of the channel for the edits and the last channel watched. */
        String key;
        final String id;
        String name, group, logo, url, userAgent, referrer;
        /** Added in the app rather than in the playlist. */
        boolean own;

        Channel(String id, String name, String group, String logo, String url, String userAgent, String referrer) {
            this.id = id;
            this.name = name;
            this.group = group;
            this.logo = logo;
            this.url = url;
            this.userAgent = userAgent;
            this.referrer = referrer;
        }
    }

    private ChannelList() {
    }

    static String playlist(Context ctx) {
        return ctx.getSharedPreferences("channels", Context.MODE_PRIVATE)
                .getString("playlist", ctx.getString(R.string.playlist));
    }

    /** The saved playlist if there is one, else the bundled one, with the edits. Fast; main thread is fine. */
    static List<Channel> load(Context ctx) {
        List<Channel> base = null;
        File saved = new File(ctx.getFilesDir(), SAVED);
        try {
            if (saved.isFile()) base = parse(new String(readAll(new FileInputStream(saved)), StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "saved list: " + e);
        }
        if (base == null || base.isEmpty()) {
            try (InputStream in = ctx.getResources().openRawResource(R.raw.channels)) {
                base = parse(new String(readAll(in), StandardCharsets.UTF_8));
            } catch (Exception e) {
                Log.w(TAG, "bundled list: " + e);
                base = new ArrayList<>();
            }
        }
        return applyEdits(ctx, base);
    }

    /**
     * Fetches the playlist at src and saves it, and src becomes the playlist link; returns the new list,
     * or null when it can't be fetched or has no channels (the link and the saved copy stay as they were
     * then). Blocking: run off the main thread.
     */
    static List<Channel> refresh(Context ctx, String src) {
        try {
            byte[] data = fetch(src);
            if (parse(new String(data, StandardCharsets.UTF_8)).isEmpty()) {
                Log.w(TAG, "playlist " + src + ": no channels");
                return null;
            }
            try (FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), SAVED))) {
                out.write(data);
            }
            ctx.getSharedPreferences("channels", Context.MODE_PRIVATE).edit().putString("playlist", src).apply();
            return load(ctx);
        } catch (Exception e) {
            Log.w(TAG, "playlist " + src + ": " + e);
            return null;
        }
    }

    // ---- the owner's edits ----------------------------------------------------------------------

    /** Removes a channel from the list; a playlist channel stays removed when the playlist comes back. */
    static void remove(Context ctx, Channel c) {
        JSONObject e = edits(ctx);
        try {
            if (c.own) dropOwn(e, c.key);
            else {
                e.getJSONObject("changed").remove(c.key);
                e.getJSONArray("removed").put(c.key);
            }
        } catch (Exception x) {
            Log.w(TAG, "remove: " + x);
        }
        saveEdits(ctx, e);
    }

    /** Saves a changed or a new channel (c.key null). */
    static void save(Context ctx, Channel c) {
        JSONObject e = edits(ctx);
        try {
            JSONObject o = new JSONObject().put("name", c.name).put("group", c.group).put("url", c.url)
                    .put("logo", c.logo);
            if (c.key == null) {
                c.key = "own-" + System.currentTimeMillis();
                c.own = true;
            }
            if (c.own) {
                dropOwn(e, c.key);
                e.getJSONArray("added").put(o.put("key", c.key));
            } else {
                e.getJSONObject("changed").put(c.key, o);
            }
        } catch (Exception x) {
            Log.w(TAG, "save: " + x);
        }
        saveEdits(ctx, e);
    }

    /** Forgets every edit: the list is the playlist again. */
    static void resetEdits(Context ctx) {
        new File(ctx.getFilesDir(), EDITS).delete();
    }

    static boolean hasEdits(Context ctx) {
        return new File(ctx.getFilesDir(), EDITS).isFile();
    }

    private static List<Channel> applyEdits(Context ctx, List<Channel> base) {
        JSONObject e = edits(ctx);
        JSONArray removedArr = e.optJSONArray("removed");
        JSONObject changed = e.optJSONObject("changed");
        JSONArray added = e.optJSONArray("added");
        Set<String> removed = new HashSet<>();
        for (int i = 0; removedArr != null && i < removedArr.length(); i++) removed.add(removedArr.optString(i));
        List<Channel> out = new ArrayList<>();
        for (Channel c : base) {
            if (removed.contains(c.key)) continue;
            JSONObject o = changed == null ? null : changed.optJSONObject(c.key);
            if (o != null) {
                c.name = o.optString("name", c.name);
                c.group = o.optString("group", c.group);
                c.logo = o.optString("logo", c.logo);
                String url = o.optString("url", c.url);
                if (!url.equals(c.url)) {
                    c.url = url;
                    c.userAgent = null;
                    c.referrer = null;
                }
            }
            out.add(c);
        }
        for (int i = 0; added != null && i < added.length(); i++) {
            JSONObject o = added.optJSONObject(i);
            if (o == null) continue;
            Channel c = new Channel("", o.optString("name"), o.optString("group"), o.optString("logo"),
                    o.optString("url"), null, null);
            c.key = o.optString("key");
            c.own = true;
            out.add(c);
        }
        return byGroup(out);
    }

    private static JSONObject edits(Context ctx) {
        JSONObject e = null;
        File f = new File(ctx.getFilesDir(), EDITS);
        try {
            if (f.isFile()) e = new JSONObject(new String(readAll(new FileInputStream(f)), StandardCharsets.UTF_8));
        } catch (Exception x) {
            Log.w(TAG, "edits: " + x);
        }
        if (e == null) e = new JSONObject();
        try {
            if (e.optJSONArray("removed") == null) e.put("removed", new JSONArray());
            if (e.optJSONObject("changed") == null) e.put("changed", new JSONObject());
            if (e.optJSONArray("added") == null) e.put("added", new JSONArray());
        } catch (Exception ignored) {
        }
        return e;
    }

    private static void saveEdits(Context ctx, JSONObject e) {
        try (FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), EDITS))) {
            out.write(e.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception x) {
            Log.w(TAG, "save edits: " + x);
        }
    }

    private static void dropOwn(JSONObject e, String key) throws Exception {
        JSONArray added = e.getJSONArray("added");
        for (int i = added.length() - 1; i >= 0; i--) {
            JSONObject o = added.optJSONObject(i);
            if (o != null && key.equals(o.optString("key"))) added.remove(i);
        }
    }

    /** Channels of a group together, groups in the order they first appear. */
    private static List<Channel> byGroup(List<Channel> list) {
        Map<String, List<Channel>> groups = new LinkedHashMap<>();
        for (Channel c : list) {
            List<Channel> g = groups.get(c.group);
            if (g == null) groups.put(c.group, g = new ArrayList<>());
            g.add(c);
        }
        List<Channel> out = new ArrayList<>(list.size());
        for (List<Channel> g : groups.values()) out.addAll(g);
        return out;
    }

    // ---- M3U ------------------------------------------------------------------------------------

    /** M3U with #EXTINF attributes (tvg-id, tvg-logo, group-title) and optional #EXTVLCOPT headers. */
    static List<Channel> parse(String text) {
        List<Channel> out = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        String[] lines = text.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (!l.startsWith("#EXTINF")) continue;
            int comma = l.lastIndexOf("\",");
            comma = comma >= 0 ? comma + 1 : l.indexOf(',');
            if (comma < 0) continue;
            String name = NOTES.matcher(l.substring(comma + 1)).replaceAll("").trim();
            Map<String, String> attrs = new LinkedHashMap<>();
            Matcher m = ATTR.matcher(l.substring(0, comma));
            while (m.find()) attrs.put(m.group(1), m.group(2));
            String ua = null, ref = null, url = "";
            for (int j = i + 1; j < lines.length && j < i + 8; j++) {
                String x = lines[j].trim();
                if (x.startsWith("#EXTVLCOPT:http-user-agent=")) ua = x.substring(27);
                else if (x.startsWith("#EXTVLCOPT:http-referrer=")) ref = x.substring(25);
                else if (x.startsWith("#EXTINF")) break;
                else if (!x.isEmpty() && !x.startsWith("#")) {
                    url = x;
                    break;
                }
            }
            if (url.isEmpty()) continue;
            // "Business;Documentary": the first category is the group.
            String group = value(attrs, "group-title");
            int semi = group.indexOf(';');
            if (semi >= 0) group = group.substring(0, semi).trim();
            if (group.isEmpty()) group = "Other";
            Channel c = new Channel(value(attrs, "tvg-id"), name, group, value(attrs, "tvg-logo"), url, ua, ref);
            String key = c.id.isEmpty() ? name : c.id;
            for (int n = 2; !keys.add(key); n++) key = (c.id.isEmpty() ? name : c.id) + "#" + n;
            c.key = key;
            out.add(c);
        }
        return out;
    }

    private static String value(Map<String, String> attrs, String key) {
        String v = attrs.get(key);
        return v == null ? "" : v.trim();
    }

    static byte[] fetch(String url) throws Exception {
        return fetch(url, Integer.MAX_VALUE);
    }

    /** The first max bytes (or all) of url. */
    static byte[] fetch(String url, int max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; out.size() < max && (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    static byte[] readAll(InputStream in) throws Exception {
        try (InputStream src = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = src.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}

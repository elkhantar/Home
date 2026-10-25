package com.majnioui.tv.channels;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.text.InputType;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * M3U Lite: live channels from the owner's M3U playlists (see ChannelList), like a TV's own channel
 * app: it opens on the last channel, full screen. OK opens the channel list on the left: Up/Down to
 * move (past the last channel back to the first and the other way round), Left/Right to jump between
 * groups, OK to watch, hold OK to make a channel a favourite (favourites come first), Menu to edit or
 * remove it, Back to close. Without the list, Up/Down and CH+/CH- switch channels and Back leaves.
 *
 * Left twice, in the list or while watching, opens the other panel: the playlists (add, change, reload,
 * remove) and the owner's own channels. The lists are ListViews, so only the rows on screen exist,
 * however many channels there are.
 *
 * One player plays every channel: switching only changes what it plays, and playback starts after a
 * second of video rather than ExoPlayer's default of two and a half.
 */
public class MainActivity extends Activity {
    private static final String TAG = "Channels";
    private static final long BAR_MS = 4000;
    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    /** Group titles in the bundled list, with their Arabic names. */
    private static final Map<String, String> ARABIC = new HashMap<>();

    static {
        ARABIC.put("Morocco", "المغرب");
        ARABIC.put("News", "الأخبار");
        ARABIC.put("Entertainment", "الترفيه");
        ARABIC.put("Documentary", "الوثائقيات");
        ARABIC.put("Religious", "القنوات الدينية");
        ARABIC.put("General", "عامة");
        ARABIC.put("Sports", "الرياضة");
        ARABIC.put("Music", "الموسيقى");
        ARABIC.put("Family", "العائلة");
        ARABIC.put("Business", "الاقتصاد");
        ARABIC.put("Favorites", "المفضلة");
        ARABIC.put("Playlists", "قوائم التشغيل");
        ARABIC.put("My list", "قائمتي");
    }

    /** The rows of the playlists panel, with its "Playlists" and "My list" groups. */
    private enum Action {
        ADD_PLAYLIST("＋  Add a playlist"),
        RELOAD("Reload the playlists"),
        ADD("＋  Add a channel"),
        RESET("Undo my changes");

        final String label;

        Action(String label) {
            this.label = label;
        }
    }

    private static final String FAVORITES = "Favorites", PLAYLISTS = "Playlists", MY_LIST = "My list";
    /** Two Left presses this close together open the playlists panel. */
    private static final long DOUBLE_MS = 450;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> logos = new LruCache<>(60);
    private Typeface font;
    private int accent;
    private SharedPreferences prefs;

    /** The channels as ChannelList gives them; channels is the same with the favourites first. */
    private List<ChannelList.Channel> loaded = new ArrayList<>();
    private List<ChannelList.Channel> channels = new ArrayList<>();
    /** Keys of the favourite channels, in the order they were chosen. */
    private final List<String> favorites = new ArrayList<>();
    private int favoriteCount;
    /** Rows of the channel list: a String is a group header, an Integer the index of a channel. */
    private final List<Object> rows = new ArrayList<>();
    /** Rows of the playlists panel: a String is a group header, then Playlists and Actions. */
    private final List<Object> manageRows = new ArrayList<>();
    private int current = -1;
    /** When Left was last pressed, and the list row it was pressed on, for the double press. */
    private long lastLeft;
    private int leftFrom = -1;

    private FrameLayout root;
    private SurfaceView surface;
    private TextView message;
    private LinearLayout bar;
    private ImageView barLogo;
    private TextView barName, barGroup;
    private LinearLayout menu, manage;
    private ListView list, manageList;
    private TextView menuStatus;
    private final RowAdapter adapter = new RowAdapter(rows);
    private final RowAdapter manageAdapter = new RowAdapter(manageRows);
    private ExoPlayer player;
    private final DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(10000);
    /** Counts the play() calls, so a late answer for a channel no longer wanted is dropped. */
    private int playSeq;
    /** Links without .m3u8 or .mpd whose kind is known: DASH or HLS. */
    private final Map<String, String> mimes = new HashMap<>();
    private final Runnable hideBar = () -> bar.animate().alpha(0f).setDuration(400).start();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        font = getResources().getFont(R.font.outfit);
        accent = getColor(R.color.accent);
        prefs = getSharedPreferences("channels", MODE_PRIVATE);
        try {
            JSONArray a = new JSONArray(prefs.getString("favorites", "[]"));
            for (int i = 0; i < a.length(); i++) favorites.add(a.getString(i));
        } catch (Exception e) {
            Log.w(TAG, "favorites: " + e);
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        message = text(22, 400, 0xCCFFFFFF);
        message.setGravity(Gravity.CENTER);
        root.addView(message, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        buildBar();
        buildMenu();
        buildManage();
        setContentView(root);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setChannels(ChannelList.load(this));
        // Playlists are small: fetch them on every start so edits to them show up right away.
        reload(false);
    }

    /** Fetches every playlist again in the background. */
    private void reload(boolean tell) {
        if (tell) menuStatus.setText("Loading the playlists…");
        io.execute(() -> {
            List<ChannelList.Channel> fresh = ChannelList.refresh(this);
            main.post(() -> {
                if (isDestroyed()) return;
                if (fresh != null) setChannels(fresh);
                else menuStatus.setText(channels.size() + " channels");
                if (tell) toast(fresh != null ? fresh.size() + " channels" : "Couldn't load the playlists");
                playIfIdle();
            });
        });
    }

    /** After channels arrive in an empty list: start the first one. */
    private void playIfIdle() {
        if (current < 0 && !channels.isEmpty() && player != null) play(0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http))
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(15000, 30000, 1000, 2000).build())
                .build();
        player.setVideoSurfaceView(surface);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) message.setVisibility(View.GONE);
                else if (state == Player.STATE_BUFFERING) {
                    message.setText("Loading…");
                    message.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onVideoSizeChanged(VideoSize size) {
                fitSurface(size);
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                String name = current >= 0 && current < channels.size() ? channels.get(current).name : "This channel";
                Log.w(TAG, name + ": " + e.getErrorCodeName());
                message.setText(name + " isn't available right now.\nUp / Down for another channel, OK for the list.");
                message.setVisibility(View.VISIBLE);
            }
        });
        if (channels.isEmpty()) {
            message.setText("No channels yet.\nAdd an M3U playlist on the left.");
            message.setVisibility(View.VISIBLE);
            openManage();
            return;
        }
        int last = indexOf(prefs.getString("last", ""));
        play(last >= 0 ? last : 0);
        if (last < 0) openMenu();   // first time: show what there is
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    /**
     * New list (bundled, saved, refreshed or edited): rebuild the rows, keep the channel that is playing,
     * or play its neighbour if it is gone.
     */
    private void setChannels(List<ChannelList.Channel> fresh) {
        String playingKey = current >= 0 && current < channels.size() ? channels.get(current).key : null;
        String playingUrl = playingKey != null ? channels.get(current).url : null;
        int was = current;
        loaded = fresh;
        // Favourites first, in the order they were chosen; the others as the playlists have them.
        Map<String, ChannelList.Channel> byKey = new HashMap<>();
        for (ChannelList.Channel c : fresh) byKey.put(c.key, c);
        channels = new ArrayList<>(fresh.size());
        for (String k : favorites) if (byKey.containsKey(k)) channels.add(byKey.remove(k));
        favoriteCount = channels.size();
        for (ChannelList.Channel c : fresh) if (byKey.containsKey(c.key)) channels.add(c);
        rows.clear();
        String group = null;
        for (int i = 0; i < channels.size(); i++) {
            String g = i < favoriteCount ? FAVORITES : channels.get(i).group;
            if (!g.equals(group)) {
                group = g;
                rows.add(group);
            }
            rows.add(i);
        }
        manageRows.clear();
        manageRows.add(PLAYLISTS);
        manageRows.addAll(ChannelList.playlists(this));
        manageRows.add(Action.ADD_PLAYLIST);
        manageRows.add(Action.RELOAD);
        manageRows.add(MY_LIST);
        manageRows.add(Action.ADD);
        if (ChannelList.hasEdits(this)) manageRows.add(Action.RESET);
        current = playingKey != null ? indexOf(playingKey) : -1;
        menuStatus.setText(channels.size() + " channels");
        adapter.notifyDataSetChanged();
        manageAdapter.notifyDataSetChanged();
        if (player == null || playingKey == null) return;
        if (current < 0) {
            if (channels.isEmpty()) {
                player.stop();
                message.setText("No channels.");
                message.setVisibility(View.VISIBLE);
            } else {
                play(Math.min(was, channels.size() - 1));
            }
        } else if (!channels.get(current).url.equals(playingUrl)) {
            play(current);   // its link changed
        }
    }

    private int indexOf(String key) {
        for (int i = 0; i < channels.size(); i++) if (channels.get(i).key.equals(key)) return i;
        return -1;
    }

    // ---- channel list (side menu) ---------------------------------------------------------------

    private void buildMenu() {
        menu = panel();

        TextView title = text(14, 500, 0x99FFFFFF);
        title.setLetterSpacing(0.22f);
        title.setText("M3U LITE");
        menu.addView(title);
        menuStatus = text(13, 400, 0x66FFFFFF);
        menuStatus.setPadding(0, dp(4), 0, dp(14));
        menu.addView(menuStatus);

        list = listView(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Object row = rows.get(position);
            if (row instanceof Integer) {
                play((Integer) row);
                closeMenu();
            }
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Object row = rows.get(position);
            if (!(row instanceof Integer)) return false;
            toggleFavorite((Integer) row);
            return true;
        });
        list.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (doubleLeft(event)) {
                    if (leftFrom >= 0) list.setSelection(leftFrom);   // undo the first press's jump
                    openManage();
                } else {
                    leftFrom = list.getSelectedItemPosition();
                    jumpGroup(-1);
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                jumpGroup(1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                int pos = list.getSelectedItemPosition();
                if (pos >= 0 && rows.get(pos) instanceof Integer) channelOptions((Integer) rows.get(pos));
                return true;
            }
            return wrap(list, rows, keyCode);
        });
        menu.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        menu.addView(hint("◀ ▶ groups  ·  OK watch  ·  hold OK favourite\nMenu edit  ·  ◀◀ playlists  ·  Back close"));

        root.addView(menu, new FrameLayout.LayoutParams(dp(400), -1, Gravity.START));
    }

    /** The playlists panel: in place of the channel list, opened with Left twice. */
    private void buildManage() {
        manage = panel();
        TextView title = text(14, 500, 0x99FFFFFF);
        title.setLetterSpacing(0.22f);
        title.setText("M3U LITE");
        manage.addView(title);
        TextView sub = text(13, 400, 0x66FFFFFF);
        sub.setText("Playlists and your own channels");
        sub.setPadding(0, dp(4), 0, dp(14));
        manage.addView(sub);

        manageList = listView(manageAdapter);
        manageList.setOnItemClickListener((parent, view, position, id) -> {
            Object row = manageRows.get(position);
            if (row instanceof Action) run((Action) row);
            else if (row instanceof ChannelList.Playlist) playlistOptions((ChannelList.Playlist) row);
        });
        manageList.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                closeManage();
                if (!channels.isEmpty()) openMenu();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return true;
            return wrap(manageList, manageRows, keyCode);
        });
        manage.addView(manageList, new LinearLayout.LayoutParams(-1, 0, 1f));
        manage.addView(hint("OK choose  ·  ▶ channels  ·  Back close"));

        root.addView(manage, new FrameLayout.LayoutParams(dp(400), -1, Gravity.START));
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(28), dp(36), dp(20), dp(24));
        p.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xF20B0D10, 0xE60B0D10, 0xB30B0D10}));
        p.setVisibility(View.GONE);
        return p;
    }

    private ListView listView(RowAdapter a) {
        ListView l = new ListView(this);
        l.setAdapter(a);
        l.setDivider(null);
        l.setVerticalScrollBarEnabled(false);
        l.setItemsCanFocus(false);
        l.setDrawSelectorOnTop(false);
        GradientDrawable sel = new GradientDrawable();
        sel.setCornerRadius(dp(12));
        sel.setColor(0xF5FFFFFF);
        l.setSelector(sel);
        return l;
    }

    private TextView hint(String s) {
        TextView hint = text(12, 400, 0x59FFFFFF);
        hint.setText(s);
        hint.setLineSpacing(dp(4), 1f);
        hint.setPadding(0, dp(12), 0, 0);
        return hint;
    }

    /** Up on the first row selects the last one, Down on the last the first. */
    private boolean wrap(ListView l, List<Object> r, int keyCode) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        boolean up = keyCode == KeyEvent.KEYCODE_DPAD_UP;
        int pos = l.getSelectedItemPosition();
        for (int i = up ? pos - 1 : pos + 1; i >= 0 && i < r.size(); i += up ? -1 : 1) {
            if (!(r.get(i) instanceof String)) return false;   // a row to move to: the list does it
        }
        for (int i = up ? r.size() - 1 : 0; i >= 0 && i < r.size(); i += up ? -1 : 1) {
            if (!(r.get(i) instanceof String)) {
                // Going down to the top: show the group header above the first row too.
                l.setSelectionFromTop(i, up ? l.getHeight() - dp(140) : dp(40));
                return true;
            }
        }
        return false;
    }

    /** True on the second of two quick Left presses (not a held key). */
    private boolean doubleLeft(KeyEvent event) {
        if (event.getRepeatCount() > 0) {
            lastLeft = 0;
            return false;
        }
        long now = event.getEventTime();
        boolean twice = now - lastLeft < DOUBLE_MS;
        lastLeft = twice ? 0 : now;
        return twice;
    }

    private void toggleFavorite(int index) {
        ChannelList.Channel c = channels.get(index);
        boolean add = !favorites.remove(c.key);
        if (add) favorites.add(c.key);
        prefs.edit().putString("favorites", new JSONArray(favorites).toString()).apply();
        setChannels(loaded);
        list.setSelectionFromTop(Math.max(0, rows.indexOf(indexOf(c.key))), dp(140));
        toast(add ? "★  " + c.name + " is a favourite" : c.name + " is no longer a favourite");
    }

    private boolean menuOpen() {
        return menu.getVisibility() == View.VISIBLE;
    }

    private boolean manageOpen() {
        return manage.getVisibility() == View.VISIBLE;
    }

    private void openManage() {
        menu.animate().cancel();
        menu.setVisibility(View.GONE);
        manage.setVisibility(View.VISIBLE);
        manage.setAlpha(0f);
        manage.setTranslationX(-dp(40));
        manage.animate().alpha(1f).translationX(0f).setDuration(180).start();
        main.removeCallbacks(hideBar);
        bar.setAlpha(0f);
        manageAdapter.notifyDataSetChanged();
        manageList.requestFocus();
        manageList.setSelection(Math.max(0, manageRows.indexOf(Action.ADD_PLAYLIST)));
    }

    private void closeManage() {
        manage.animate().cancel();
        manage.setVisibility(View.GONE);
    }

    private void openMenu() {
        if (channels.isEmpty()) {
            openManage();
            return;
        }
        menu.setVisibility(View.VISIBLE);
        menu.setAlpha(0f);
        menu.setTranslationX(-dp(40));
        menu.animate().alpha(1f).translationX(0f).setDuration(180).start();
        main.removeCallbacks(hideBar);
        bar.setAlpha(0f);
        adapter.notifyDataSetChanged();
        list.requestFocus();
        list.setSelectionFromTop(Math.max(0, rows.indexOf(current >= 0 ? current : 0)), dp(140));
    }

    private void closeMenu() {
        menu.animate().alpha(0f).translationX(-dp(40)).setDuration(150)
                .withEndAction(() -> menu.setVisibility(View.GONE)).start();
    }

    /** Selects the first channel of the next (+1) or previous (-1) group. */
    private void jumpGroup(int dir) {
        int pos = list.getSelectedItemPosition();
        if (pos < 0) pos = 0;
        int header = -1;
        if (dir > 0) {
            for (int i = pos + 1; i < rows.size(); i++) if (rows.get(i) instanceof String) { header = i; break; }
        } else {
            int own = pos;
            while (own > 0 && !(rows.get(own) instanceof String)) own--;
            for (int i = own - 1; i >= 0; i--) if (rows.get(i) instanceof String) { header = i; break; }
        }
        if (header >= 0 && header + 1 < rows.size()) list.setSelectionFromTop(header + 1, dp(60));
    }

    private final class RowAdapter extends BaseAdapter {
        private final List<Object> rows;

        RowAdapter(List<Object> rows) {
            this.rows = rows;
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            return row instanceof String ? 0 : row instanceof Integer ? 1 : 2;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return !(rows.get(position) instanceof String);
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Object row = rows.get(position);
            if (row instanceof String) {
                TextView h = convert instanceof TextView ? (TextView) convert : text(13, 500, 0x99FFFFFF);
                String ar = ARABIC.get(row);
                h.setText(((String) row).toUpperCase(Locale.ROOT) + (ar == null ? "" : "  ·  " + ar));
                h.setLetterSpacing(0.12f);
                h.setPadding(dp(12), dp(position == 0 ? 4 : 18), 0, dp(8));
                return h;
            }
            if (!(row instanceof Integer)) {
                TextView a;
                if (convert instanceof TextView) {
                    a = (TextView) convert;
                } else {
                    a = text(16, 500, 0);
                    a.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_selected}, {}},
                            new int[] {0xFF111111, 0xD9FFFFFF}));
                    a.setGravity(Gravity.CENTER_VERTICAL);
                    a.setMinHeight(dp(48));
                    a.setPadding(dp(16), 0, dp(14), 0);
                }
                if (row instanceof Action) {
                    a.setText(((Action) row).label);
                } else {
                    ChannelList.Playlist p = (ChannelList.Playlist) row;
                    int n = 0;
                    for (ChannelList.Channel c : channels) if (p.url.equals(c.source)) n++;
                    a.setText(p.name + "  ·  " + n + " channels");
                }
                return a;
            }
            RowHolder holder;
            if (convert != null && convert.getTag() instanceof RowHolder) {
                holder = (RowHolder) convert.getTag();
            } else {
                holder = new RowHolder();
                convert = holder.build();
            }
            holder.bind((Integer) row);
            return convert;
        }
    }

    /** number · logo · name, and a dot on the channel that is playing. */
    private final class RowHolder {
        TextView number, name;
        ImageView logo;
        View playing;
        String logoUrl;

        View build() {
            LinearLayout r = new LinearLayout(MainActivity.this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(12), dp(8), dp(14), dp(8));
            r.setMinimumHeight(dp(56));
            ColorStateList fg = new ColorStateList(new int[][] {{android.R.attr.state_selected}, {}},
                    new int[] {0xFF111111, 0xFFFFFFFF});
            ColorStateList dim = new ColorStateList(new int[][] {{android.R.attr.state_selected}, {}},
                    new int[] {0x99111111, 0x80FFFFFF});
            number = text(14, 500, 0);
            number.setTextColor(dim);
            number.setGravity(Gravity.END);
            r.addView(number, new LinearLayout.LayoutParams(dp(26), -2));
            FrameLayout logoBox = new FrameLayout(MainActivity.this);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(8));
            bg.setColor(0x1FFFFFFF);
            logoBox.setBackground(bg);
            logo = new ImageView(MainActivity.this);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logoBox.addView(logo, new FrameLayout.LayoutParams(-1, -1));
            logoBox.setPadding(dp(5), dp(4), dp(5), dp(4));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(68), dp(40));
            llp.leftMargin = dp(12);
            llp.rightMargin = dp(14);
            r.addView(logoBox, llp);
            name = text(17, 500, 0);
            name.setTextColor(fg);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            playing = new View(MainActivity.this);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(accent);
            playing.setBackground(dot);
            r.addView(playing, new LinearLayout.LayoutParams(dp(9), dp(9)));
            r.setTag(this);
            return r;
        }

        void bind(int index) {
            ChannelList.Channel c = channels.get(index);
            number.setText(String.valueOf(index + 1));
            name.setText(c.name);
            playing.setVisibility(index == current ? View.VISIBLE : View.INVISIBLE);
            if (!c.logo.equals(logoUrl)) {
                logoUrl = c.logo;
                logo.setImageDrawable(null);
                final String want = c.logo;
                loadLogo(want, bmp -> {
                    if (want.equals(logoUrl)) logo.setImageBitmap(bmp);
                });
            }
        }
    }

    // ---- player ---------------------------------------------------------------------------------

    private void buildBar() {
        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(16), dp(28), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(22));
        bg.setColor(0xCC0B0D10);
        bar.setBackground(bg);
        barLogo = new ImageView(this);
        barLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bar.addView(barLogo, new LinearLayout.LayoutParams(dp(96), dp(54)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(18), 0, 0, 0);
        barName = text(24, 500, 0xFFFFFFFF);
        barGroup = text(15, 400, 0xA6FFFFFF);
        texts.addView(barName);
        texts.addView(barGroup);
        bar.addView(texts);
        bar.setAlpha(0f);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        blp.setMargins(dp(48), 0, 0, dp(40));
        root.addView(bar, blp);
    }

    private void play(int index) {
        if (channels.isEmpty()) return;
        index = (index % channels.size() + channels.size()) % channels.size();
        current = index;
        final ChannelList.Channel c = channels.get(index);
        prefs.edit().putString("last", c.key).apply();
        adapter.notifyDataSetChanged();
        message.setText("Loading…");
        message.setVisibility(View.VISIBLE);
        if (!menuOpen()) showBar(c, index);

        if (player == null) return;
        // The data sources made for the new item take the channel's own headers.
        Map<String, String> headers = new HashMap<>();
        if (c.referrer != null) headers.put("Referer", c.referrer);
        http.setUserAgent(c.userAgent != null ? c.userAgent : DEFAULT_UA).setDefaultRequestProperties(headers);
        final ExoPlayer p = player;
        final int seq = ++playSeq;
        // Links without .m3u8 or .mpd: look at the first bytes to tell DASH from HLS, once per link.
        String lower = c.url.toLowerCase(Locale.ROOT);
        String known = mimes.get(c.url);
        if (lower.contains(".m3u8") || lower.contains(".mpd") || known != null) {
            start(p, c.url, known);
        } else {
            p.stop();
            io.execute(() -> {
                String mime = MimeTypes.APPLICATION_M3U8;
                try {
                    // DASH manifests are XML; HLS playlists start with #EXTM3U.
                    String head = new String(ChannelList.fetch(c.url, 512), StandardCharsets.UTF_8).trim();
                    if (head.startsWith("<")) mime = MimeTypes.APPLICATION_MPD;
                } catch (Exception ignored) {
                }
                final String m = mime;
                main.post(() -> {
                    mimes.put(c.url, m);
                    if (player == p && playSeq == seq) start(p, c.url, m);
                });
            });
        }
    }

    private static void start(ExoPlayer p, String url, String mime) {
        MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(url))
                .setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(C.TIME_UNSET).build());
        if (mime != null) item.setMimeType(mime);
        p.setMediaItem(item.build());
        p.prepare();
        p.setPlayWhenReady(true);
    }

    /** Letterbox the video in the screen at its own aspect ratio. */
    private void fitSurface(VideoSize size) {
        if (size.width == 0 || size.height == 0) return;
        float aspect = size.width * size.pixelWidthHeightRatio / size.height;
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return;
        int vw = w, vh = Math.round(w / aspect);
        if (vh > h) {
            vh = h;
            vw = Math.round(h * aspect);
        }
        surface.setLayoutParams(new FrameLayout.LayoutParams(vw, vh, Gravity.CENTER));
    }

    private void showBar(ChannelList.Channel c, int index) {
        barName.setText(c.name);
        String ar = ARABIC.get(c.group);
        barGroup.setText((index + 1) + "  ·  " + c.group + (ar == null ? "" : "  ·  " + ar));
        barLogo.setImageDrawable(null);
        loadLogo(c.logo, barLogo::setImageBitmap);
        bar.animate().cancel();
        bar.setAlpha(1f);
        main.removeCallbacks(hideBar);
        main.postDelayed(hideBar, BAR_MS);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (manageOpen()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                manage.animate().alpha(0f).translationX(-dp(40)).setDuration(150)
                        .withEndAction(() -> manage.setVisibility(View.GONE)).start();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        if (menuOpen()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeMenu();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MENU:
                openMenu();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                if (current < 0) return true;
                play(current - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                if (current < 0) return true;
                play(current + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (doubleLeft(event)) {
                    openManage();
                    return true;
                }
                if (current >= 0) showBar(channels.get(current), current);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_INFO:
                if (current >= 0) showBar(channels.get(current), current);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    // ---- my list: editing the channels ---------------------------------------------------------

    private void run(Action a) {
        switch (a) {
            case ADD:
                editChannel(null);
                break;
            case ADD_PLAYLIST:
                editPlaylist(null);
                break;
            case RELOAD:
                reload(true);
                break;
            case RESET:
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Undo my changes?")
                        .setMessage("The channels you added, changed or removed go back to the playlists as they are.")
                        .setPositiveButton("Undo", (d, w) -> {
                            ChannelList.resetEdits(this);
                            setChannels(ChannelList.load(this));
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                break;
        }
    }

    private void playlistOptions(ChannelList.Playlist p) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(p.name)
                .setItems(new String[] {"Edit", "Reload", "Remove"}, (d, which) -> {
                    if (which == 0) {
                        editPlaylist(p);
                    } else if (which == 1) {
                        reload(true);
                    } else {
                        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle("Remove " + p.name + "?")
                                .setMessage("Its channels go too. You can add it again with its link.")
                                .setPositiveButton("Remove", (d2, w) -> {
                                    ChannelList.removePlaylist(this, p.url);
                                    setChannels(ChannelList.load(this));
                                    toast(p.name + " removed");
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }

    /** Changes the name or link of p, or adds a playlist when p is null; it must load to be saved. */
    private void editPlaylist(ChannelList.Playlist p) {
        EditText name = field("Name", p == null ? "" : p.name, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText url = field("Link of the .m3u playlist", p == null ? "" : p.url, InputType.TYPE_TEXT_VARIATION_URI);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(p == null ? "Add a playlist" : "Edit " + p.name)
                .setMessage("An M3U playlist. Its channels are loaded again each time the app starts; "
                        + "your own changes to them stay.")
                .setView(form(name, url))
                .setPositiveButton(p == null ? "Add" : "Save", null)
                .setNegativeButton("Cancel", null)
                .show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String u = url.getText().toString().trim();
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                url.setError("A link starting with http:// or https://");
                return;
            }
            String n = name.getText().toString().trim();
            if (n.isEmpty()) n = Uri.parse(u).getLastPathSegment() != null ? Uri.parse(u).getLastPathSegment() : "Playlist";
            final String fn = n;
            d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            d.setMessage("Loading…");
            io.execute(() -> {
                int count = ChannelList.addPlaylist(this, fn, u, p == null ? null : p.url);
                main.post(() -> {
                    if (isDestroyed()) return;
                    if (count < 0) {
                        d.setMessage("No channels could be loaded from that link. Check it and try again.");
                        d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        return;
                    }
                    d.dismiss();
                    setChannels(ChannelList.load(this));
                    toast(fn + ": " + count + " channels");
                    playIfIdle();
                });
            });
        });
    }

    private void channelOptions(int index) {
        ChannelList.Channel c = channels.get(index);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(c.name)
                .setItems(new String[] {"Edit", "Remove"}, (d, which) -> {
                    if (which == 0) {
                        editChannel(c);
                    } else {
                        ChannelList.remove(this, c);
                        setChannels(ChannelList.load(this));
                        toast(c.name + " removed");
                    }
                })
                .show();
    }

    /** Edits c, or adds a channel when c is null. */
    private void editChannel(ChannelList.Channel c) {
        EditText name = field("Name", c == null ? "" : c.name, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText url = field("Stream link (.m3u8, .mpd)", c == null ? "" : c.url, InputType.TYPE_TEXT_VARIATION_URI);
        EditText group = field("Group", c == null ? "My channels" : c.group, InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText logo = field("Logo link (optional)", c == null ? "" : c.logo, InputType.TYPE_TEXT_VARIATION_URI);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(c == null ? "Add a channel" : "Edit " + c.name)
                .setView(form(name, url, group, logo))
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .show();
        // Set here rather than in the builder so a missing name or link keeps the dialog open.
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim(), u = url.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("A name");
                return;
            }
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                url.setError("A link starting with http:// or https://");
                return;
            }
            String g = group.getText().toString().trim();
            ChannelList.Channel e = c != null ? c : new ChannelList.Channel("", n, g, "", u, null, null);
            e.name = n;
            e.url = u;
            e.group = g.isEmpty() ? "My channels" : g;
            e.logo = logo.getText().toString().trim();
            ChannelList.save(this, e);
            d.dismiss();
            setChannels(ChannelList.load(this));
            if (c == null) {
                play(indexOf(e.key));
                closeManage();
            }
        });
    }

    private EditText field(String hint, String value, int variation) {
        EditText f = new EditText(this);
        f.setHint(hint);
        f.setText(value);
        f.setSingleLine(true);
        f.setInputType(InputType.TYPE_CLASS_TEXT | variation);
        return f;
    }

    private View form(View... fields) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(24), dp(8), dp(24), 0);
        for (View f : fields) l.addView(f, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // ---- logos ----------------------------------------------------------------------------------

    private interface BitmapCallback {
        void onBitmap(Bitmap bmp);
    }

    /** From memory, then the cache directory, then the network. */
    private void loadLogo(String url, BitmapCallback cb) {
        if (url == null || url.isEmpty()) return;
        Bitmap hit = logos.get(url);
        if (hit != null) {
            cb.onBitmap(hit);
            return;
        }
        io.execute(() -> {
            File dir = new File(getCacheDir(), "logos");
            if (!dir.isDirectory()) dir.mkdirs();
            File f = new File(dir, Integer.toHexString(url.hashCode()) + ".img");
            try {
                if (!f.isFile()) {
                    byte[] data = ChannelList.fetch(url);
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        out.write(data);
                    }
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                o.inJustDecodeBounds = false;
                o.inSampleSize = 1;
                while (o.outWidth / (o.inSampleSize * 2) >= 240) o.inSampleSize *= 2;
                Bitmap bmp = BitmapFactory.decodeFile(f.getPath(), o);
                if (bmp == null) {
                    f.delete();
                    return;
                }
                main.post(() -> {
                    logos.put(url, bmp);
                    cb.onBitmap(bmp);
                });
            } catch (Exception e) {
                Log.w(TAG, "logo " + url + ": " + e);
            }
        });
    }

    // ---- helpers --------------------------------------------------------------------------------

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (color != 0) t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, weight, false) : font);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

package tv.fefetv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** FefeTV home screen: sidebar, greeting, clock and weather, rows of apps. */
public final class Home extends Activity implements Screen.Host {
    private static final String RECENT = "fefe.recent", SOUND = "fefe.sound", FAV = "fefe.fav", FAV_SET = "fefe.favset";
    private static final String NAME = "fefe.name", CITY = "fefe.city", LAT = "fefe.lat", LON = "fefe.lon";
    private static final int MAX_RECENT = 8, GRID = 6;
    private static final long WEATHER_MS = 30 * 60 * 1000L;
    private static final List<String> DEFAULT_FAV = Arrays.asList(
            "com.amazon.avl.ftv", "com.netflix.ninja", "com.amazon.firetv.youtube", "org.smarttube.beta",
            "org.smarttube.stable", "com.disney.disneyplus", "com.plexapp.android", "com.stremio.one",
            "org.xbmc.kodi", "st.veezie", "de.herber_edevelopment.m3uiptv", "it.rainet.androidtv");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Config cfg;
    private TvInputManager tv;
    private Screen screen;
    private Sounds sounds;
    private List<Item> all = new ArrayList<>();
    private boolean resumed;
    private long backdropSig = Long.MIN_VALUE;
    private int backdropW, backdropH;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            loadApps();
        }
    };

    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    private final BroadcastReceiver netWatch = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            network();
        }
    };

    /** Wi-Fi strength changes without a broadcast this app receives, so poll it while visible. */
    private final Runnable netLoop = new Runnable() {
        @Override
        public void run() {
            network();
            if (resumed) ui.postDelayed(this, 10000);
        }
    };

    private final Runnable weatherLoop = new Runnable() {
        @Override
        public void run() {
            refreshWeather();
            ui.postDelayed(this, WEATHER_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);
        tv = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        screen = new Screen(this, this);
        setContentView(screen);
        screen.requestFocus();
        sounds = new Sounds();
        screen.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> backdrop(r - l, b - t));

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        Keeper.start(this);
        loadApps();
        ui.post(weatherLoop);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Guard.arrived(this);
        cfg.refresh();
        screen.setAccent(accent());
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(time, f);
        registerReceiver(netWatch, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
        tick();
        ui.removeCallbacks(netLoop);
        ui.post(netLoop);
        showPage(screen.page());
        backdrop(backdropW, backdropH);
        screen.requestFocus();
    }

    @Override
    public void onEnterAnimationComplete() {
        Guard.shown();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        unregisterReceiver(time);
        unregisterReceiver(netWatch);
        ui.removeCallbacks(netLoop);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        ui.removeCallbacks(weatherLoop);
        io.shutdownNow();
        sounds.release();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (resumed) {
            showPage(Screen.HOME);
            screen.focusContent();
            screen.back();
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!screen.back() && screen.page() != Screen.HOME) {
                showPage(Screen.HOME);
                screen.focusContent();
            }
            return true;
        }
        return super.onKeyDown(code, e);
    }

    // ---- background ----

    private void backdrop(int w, int h) {
        if (w <= 0 || h <= 0) return;
        File dir = cfg.dir;
        long sig = w * 31L + h;
        for (String n : new String[] {"wallpaper.jpg", "wallpaper.png", "wallpaper.webp"}) {
            File f = new File(dir, n);
            if (f.isFile()) sig = sig * 31 + f.lastModified() + f.length() + n.hashCode();
        }
        if (sig == backdropSig) return;
        backdropSig = sig;
        backdropW = w;
        backdropH = h;
        io.execute(() -> {
            Bitmap b = Backdrop.make(w, h, dir);
            ui.post(() -> screen.setBackdrop(b));
        });
    }

    // ---- data ----

    private void loadApps() {
        io.execute(() -> {
            List<Item> apps = Catalog.apps(this);
            for (Item it : apps) {
                Catalog.art(getPackageManager(), it);
                it.artRequested = true;
            }
            ui.post(() -> {
                all = apps;
                showPage(screen.page());
            });
        });
    }

    private List<Item> visible() {
        return Catalog.visible(cfg, Catalog.APPS, all);
    }

    private Item byId(String id) {
        for (Item it : all) if (it.id.equals(id)) return it;
        return null;
    }

    private List<String> favIds() {
        if ("1".equals(cfg.get(FAV_SET, ""))) return cfg.list(FAV);
        List<String> out = new ArrayList<>();
        for (String id : DEFAULT_FAV) {
            Item it = byId(id);
            if (it != null && Catalog.shown(cfg, Catalog.APPS, it) && out.size() < 8) out.add(id);
        }
        return out;
    }

    private void setFav(List<String> ids) {
        cfg.set(FAV_SET, "1");
        cfg.setList(FAV, ids);
    }

    private List<Item> favorites() {
        List<Item> out = new ArrayList<>();
        for (String id : favIds()) {
            Item it = byId(id);
            if (it != null && Catalog.shown(cfg, Catalog.APPS, it)) out.add(it);
        }
        return out;
    }

    private List<Item> recentItems() {
        List<Item> out = new ArrayList<>();
        for (String id : cfg.list(RECENT)) {
            Item it = byId(id);
            if (it != null) out.add(it);
        }
        return out;
    }

    private List<Item> hiddenItems() {
        List<Item> out = new ArrayList<>();
        for (Item it : all) if (it.leanback && !Catalog.shown(cfg, Catalog.APPS, it)) out.add(it);
        return out;
    }

    private List<Item> group(String g) {
        List<Item> out = new ArrayList<>();
        for (Item it : visible()) if (g.equals(Groups.of(cfg, it))) out.add(it);
        return out;
    }

    private void remember(String id) {
        List<String> r = cfg.list(RECENT);
        r.remove(id);
        r.add(0, id);
        while (r.size() > MAX_RECENT) r.remove(r.size() - 1);
        cfg.setList(RECENT, r);
    }

    private String label(Item it) {
        return Catalog.label(cfg, it).toString();
    }

    private Card app(Item it) {
        return new Card(label(it), Groups.label(Groups.of(cfg, it)), it, Glyphs.NONE, () -> openApp(it));
    }

    private List<Screen.Row> grid(String title, List<Item> items) {
        List<Screen.Row> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i += GRID) {
            List<Card> cs = new ArrayList<>();
            for (Item it : items.subList(i, Math.min(items.size(), i + GRID))) cs.add(app(it));
            rows.add(new Screen.Row(i == 0 ? title + "  ·  " + items.size() : null, cs, false));
        }
        return rows;
    }

    private void showPage(int page) {
        List<Screen.Row> rows = new ArrayList<>();
        switch (page) {
            case Screen.APPS:
                rows = grid("Tutte le app", visible());
                break;
            case Screen.VIDEO:
                rows = grid("Film e Serie", group(Groups.VIDEO));
                break;
            case Screen.GAMES:
                rows = grid("Giochi e utility", group(Groups.GAMES));
                break;
            case Screen.FILES: {
                List<Item> files = new ArrayList<>();
                for (Item it : visible()) if (Groups.onFilesPage(cfg, it)) files.add(it);
                rows = grid("File", files);
                break;
            }
            case Screen.SETTINGS:
                rows = settingsRows();
                break;
            default: {
                page = Screen.HOME;
                List<Card> fav = new ArrayList<>();
                for (Item it : favorites()) fav.add(app(it));
                fav.add(new Card("Tutte le app", null, null, Glyphs.PLUS, () -> {
                    showPage(Screen.APPS);
                    screen.focusContent();
                }));
                rows.add(new Screen.Row("Le tue app", fav, false));

                List<Card> recent = new ArrayList<>();
                for (Item it : recentItems()) recent.add(new Card(label(it), "Riprendi", it, Glyphs.NONE, () -> openApp(it)));
                if (!recent.isEmpty()) rows.add(new Screen.Row("Riprendi", recent, true));

                List<Card> games = new ArrayList<>();
                for (Item it : group(Groups.GAMES)) games.add(app(it));
                if (!games.isEmpty()) rows.add(new Screen.Row("Giochi e utility", games, false));

                List<String> favs = favIds();
                List<Card> rest = new ArrayList<>();
                for (Item it : visible()) {
                    if (!favs.contains(it.id) && !Groups.GAMES.equals(Groups.of(cfg, it))) rest.add(app(it));
                }
                if (!rest.isEmpty()) rows.add(new Screen.Row("Altre app", rest, false));
            }
        }
        screen.setPage(page, rows);
    }

    private List<Screen.Row> settingsRows() {
        List<Card> a = new ArrayList<>();
        a.add(new Card("Fire TV", "Rete, display, telecomando", null, Glyphs.GEAR, this::openSettings));
        a.add(new Card("Launcher", "Attivo: " + activeLauncher(), null, Glyphs.SWAP, this::launcherDialog));
        a.add(new Card("Suono", soundOn() ? "Attivo" : "Spento", null, soundOn() ? Glyphs.SOUND : Glyphs.MUTE,
                this::toggleSound));
        a.add(new Card("Nome e città", orDash(cfg.get(NAME, "")) + " · " + orDash(cfg.get(CITY, "")), null,
                Glyphs.PERSON, this::profileDialog));
        a.add(new Card("Colore", colorName(), null, Glyphs.PALETTE, this::colorDialog));
        a.add(new Card("Sfondo", "Usa una tua foto", null, Glyphs.IMAGE, this::wallpaperInfo));
        a.add(new Card("App nascoste", hiddenItems().size() + "", null, Glyphs.EYE, this::hiddenDialog));
        List<Card> b = new ArrayList<>();
        b.add(new Card("Info FefeTV", "Gestisci l'app", null, Glyphs.INFO, () -> launch(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)),
                null)));
        b.add(new Card("Home Amazon", "Per un momento", null, Glyphs.AMAZON, () -> Guard.amazon(this)));
        List<Screen.Row> rows = new ArrayList<>();
        rows.add(new Screen.Row("Impostazioni", a, false));
        rows.add(new Screen.Row(null, b, false));
        return rows;
    }

    // ---- screen callbacks ----

    @Override
    public void open(Card c) {
        if (c.action != null) c.action.run();
    }

    @Override
    public void moved() {
        if (soundOn()) sounds.play(false);
    }

    @Override
    public void sidebarMoved(int index) {
        if (index != Screen.SEARCH) showPage(index);
    }

    @Override
    public void sidebarChosen(int index) {
        if (index == Screen.SEARCH) {
            searchDialog();
            return;
        }
        showPage(index);
        screen.focusContent();
    }

    @Override
    public void menu(Card c, int page, int row) {
        if (c.item == null) {
            open(c);
            return;
        }
        Item it = c.item;
        List<String> favs = favIds();
        boolean fav = favs.contains(it.id);
        boolean inFavRow = page == Screen.HOME && row == 0 && fav;
        List<String> actions = new ArrayList<>();
        actions.add("Apri");
        actions.add(fav ? "Togli da Le tue app" : "Aggiungi a Le tue app");
        if (inFavRow) {
            actions.add("Sposta a sinistra");
            actions.add("Sposta a destra");
        }
        if (page == Screen.APPS) {
            actions.add("Sposta prima");
            actions.add("Sposta dopo");
        }
        actions.add("Gruppo: " + Groups.label(Groups.of(cfg, it)));
        actions.add("Nascondi");
        actions.add("Info app");
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(c.title)
                .setItems(actions.toArray(new String[0]), (d, w) -> {
                    String a = actions.get(w);
                    if (a.equals("Apri")) {
                        openApp(it);
                    } else if (a.startsWith("Togli")) {
                        favs.remove(it.id);
                        setFav(favs);
                        showPage(page);
                    } else if (a.startsWith("Aggiungi")) {
                        favs.add(it.id);
                        setFav(favs);
                        showPage(page);
                    } else if (a.equals("Sposta a sinistra") || a.equals("Sposta a destra")) {
                        int i = favs.indexOf(it.id), j = i + (a.endsWith("sinistra") ? -1 : 1);
                        if (j >= 0 && j < favs.size()) {
                            favs.remove(i);
                            favs.add(j, it.id);
                            setFav(favs);
                            showPage(page);
                            screen.focusApp(0, it.id);
                        }
                    } else if (a.equals("Sposta prima") || a.equals("Sposta dopo")) {
                        moveInOrder(it, a.endsWith("prima") ? -1 : 1);
                    } else if (a.startsWith("Gruppo")) {
                        groupDialog(it, page);
                    } else if (a.equals("Nascondi")) {
                        Catalog.setShown(cfg, Catalog.APPS, it, false);
                        showPage(page);
                        Toast.makeText(this, "Nascosta. La ritrovi in Impostazioni › App nascoste.", Toast.LENGTH_SHORT).show();
                    } else {
                        launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", it.id, null)), null);
                    }
                })
                .show();
    }

    private static String orDash(String s) {
        return s.isEmpty() ? "—" : s;
    }

    private void moveInOrder(Item it, int by) {
        List<Item> v = visible();
        int i = v.indexOf(it), j = i + by;
        if (i < 0 || j < 0 || j >= v.size()) return;
        v.remove(i);
        v.add(j, it);
        List<String> ids = new ArrayList<>();
        for (Item x : v) ids.add(x.id);
        Catalog.saveOrder(cfg, Catalog.APPS, ids);
        showPage(Screen.APPS);
        screen.focusApp(j / GRID, it.id);
    }

    private void groupDialog(Item it, int page) {
        String[] keys = {Groups.VIDEO, Groups.GAMES, Groups.FILES, Groups.NONE};
        String[] names = new String[keys.length];
        for (int i = 0; i < keys.length; i++) names[i] = Groups.label(keys[i]);
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Gruppo di " + label(it))
                .setItems(names, (d, w) -> {
                    Groups.set(cfg, it, keys[w]);
                    showPage(page);
                })
                .show();
    }

    // ---- actions ----

    private void openApp(Item it) {
        remember(it.id);
        launch(new Intent(it.intent), it.id);
    }

    /**
     * Fire OS answers the generic settings action with a stub that reopens Amazon's home, which
     * Guard then replaces with ours. The TV settings entry point opens the real settings screen.
     */
    private void openSettings() {
        Intent tvSettings = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS");
        boolean found = tvSettings.resolveActivity(getPackageManager()) != null;
        launch(found ? tvSettings : new Intent(Settings.ACTION_SETTINGS), null);
    }

    private void launch(Intent i, String pkg) {
        if (Guard.AMAZON.equals(pkg)) Guard.pause();
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Impossibile aprirla", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean soundOn() {
        return !"off".equals(cfg.get(SOUND, "on"));
    }

    private void toggleSound() {
        cfg.set(SOUND, soundOn() ? "off" : "on");
        showPage(Screen.SETTINGS);
        if (soundOn()) sounds.play(false);
    }

    private void searchDialog() {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("Nome dell'app");
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Cerca un'app")
                .setView(field)
                .setPositiveButton("Cerca", (d, w) -> {
                    String q = field.getText().toString().trim().toLowerCase(Locale.ROOT);
                    List<Item> found = new ArrayList<>();
                    for (Item it : all) if (label(it).toLowerCase(Locale.ROOT).contains(q)) found.add(it);
                    if (found.isEmpty()) Toast.makeText(this, "Nessuna app trovata", Toast.LENGTH_SHORT).show();
                    else pickApp("Risultati", found);
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void pickApp(String title, List<Item> items) {
        String[] names = new String[items.size()];
        for (int i = 0; i < names.length; i++) names[i] = label(items.get(i));
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(title)
                .setItems(names, (d, w) -> openApp(items.get(w)))
                .show();
    }

    private void hiddenDialog() {
        List<Item> hidden = hiddenItems();
        if (hidden.isEmpty()) {
            Toast.makeText(this, "Non ci sono app nascoste", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[hidden.size()];
        for (int i = 0; i < names.length; i++) names[i] = label(hidden.get(i));
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Mostra di nuovo")
                .setItems(names, (d, w) -> {
                    Catalog.setShown(cfg, Catalog.APPS, hidden.get(w), true);
                    showPage(Screen.SETTINGS);
                })
                .show();
    }

    private void wallpaperInfo() {
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Sfondo")
                .setMessage("Per usare una tua foto, copiala sulla TV con questo nome:\n\n"
                        + new File(cfg.dir, "wallpaper.jpg").getPath()
                        + "\n\nDal PC: adb push foto.jpg " + new File(cfg.dir, "wallpaper.jpg").getPath()
                        + "\n\nSenza foto FefeTV disegna il paesaggio notturno.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void profileDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Il tuo nome");
        name.setText(cfg.get(NAME, ""));
        EditText city = new EditText(this);
        city.setSingleLine(true);
        city.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        city.setHint("Città per il meteo");
        city.setText(cfg.get(CITY, ""));
        box.addView(name);
        box.addView(city);
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Nome e città")
                .setView(box)
                .setPositiveButton("Salva", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (!n.isEmpty()) cfg.set(NAME, n);
                    String c = city.getText().toString().trim();
                    tick();
                    showPage(Screen.SETTINGS);
                    if (!c.isEmpty() && !c.equalsIgnoreCase(cfg.get(CITY, ""))) findCity(c);
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void findCity(String name) {
        io.execute(() -> {
            Object[] found;
            try {
                found = Weather.geocode(name);
            } catch (java.io.IOException e) {
                found = null;
            }
            final Object[] f = found;
            ui.post(() -> {
                if (f == null) {
                    Toast.makeText(this, "Città non trovata: " + name, Toast.LENGTH_LONG).show();
                    return;
                }
                cfg.set(LAT, String.valueOf(f[0]));
                cfg.set(LON, String.valueOf(f[1]));
                cfg.set(CITY, (String) f[2]);
                showPage(Screen.SETTINGS);
                refreshWeather();
            });
        });
    }

    // ---- theme colour ----

    private static final String ACCENT = "fefe.accent";
    private static final String[] COLOR_NAMES = {"Verde acqua", "Viola", "Verde", "Blu", "Azzurro", "Arancione",
            "Rosa", "Rosso", "Giallo", "Bianco"};
    private static final int[] COLORS = {0xFF35E0B4, 0xFFB57BFF, 0xFF5BD46A, 0xFF4D8DFF, 0xFF4CC9F0, 0xFFFF9F43,
            0xFFFF6FB5, 0xFFFF5A5F, 0xFFFFD43B, 0xFFE8EEF4};

    private int accent() {
        String v = cfg.get(ACCENT, "");
        for (int i = 0; i < COLORS.length; i++) if (COLOR_NAMES[i].equals(v)) return COLORS[i];
        if (v.startsWith("#")) {
            try {
                return android.graphics.Color.parseColor(v) | 0xFF000000;
            } catch (IllegalArgumentException ignored) {
                // falls back to the default
            }
        }
        return COLORS[0];
    }

    private String colorName() {
        String v = cfg.get(ACCENT, "");
        return v.isEmpty() ? COLOR_NAMES[0] : v;
    }

    private void colorDialog() {
        CharSequence[] names = new CharSequence[COLOR_NAMES.length];
        for (int i = 0; i < names.length; i++) {
            android.text.SpannableString s = new android.text.SpannableString("\u25CF  " + COLOR_NAMES[i]);
            s.setSpan(new android.text.style.ForegroundColorSpan(COLORS[i]), 0, 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            names[i] = s;
        }
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Colore del tema")
                .setItems(names, (d, w) -> {
                    cfg.set(ACCENT, COLOR_NAMES[w]);
                    screen.setAccent(COLORS[w]);
                    showPage(Screen.SETTINGS);
                })
                .show();
    }

    // ---- network ----

    @SuppressWarnings("deprecation")
    private void network() {
        android.net.ConnectivityManager cm =
                (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        if (ni == null || !ni.isConnected()) {
            screen.setNetwork(0, 0);
        } else if (ni.getType() == android.net.ConnectivityManager.TYPE_ETHERNET) {
            screen.setNetwork(2, 3);
        } else {
            int level = 3;
            android.net.wifi.WifiManager wm =
                    (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                level = android.net.wifi.WifiManager.calculateSignalLevel(wm.getConnectionInfo().getRssi(), 4);
            }
            screen.setNetwork(1, Math.max(1, level));
        }
    }

    // ---- header ----

    private void tick() {
        String date = new SimpleDateFormat("EEE d MMM yyyy", Locale.ITALIAN).format(new Date());
        StringBuilder sb = new StringBuilder();
        for (String w : date.split(" ")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        screen.setHeader(cfg.get(NAME, ""), DateFormat.getTimeFormat(this).format(new Date()),
                sb.toString().replace(".", ""));
    }

    private void refreshWeather() {
        double lat, lon;
        try {
            lat = Double.parseDouble(cfg.get(LAT, ""));
            lon = Double.parseDouble(cfg.get(LON, ""));
        } catch (NumberFormatException e) {
            return; // no city set yet: Impostazioni › Nome e città
        }
        final double la = lat, lo = lon;
        io.execute(() -> {
            try {
                Weather w = Weather.fetch(la, lo);
                ui.post(() -> screen.setWeather(Math.round(w.temp) + "°C", cfg.get(CITY, ""), w.code, w.day));
            } catch (java.io.IOException | RuntimeException ignored) {
                // no network or blocked: the header simply shows no weather
            }
        });
    }

    // ---- launcher switch ----

    private static final String FEFE = "tv.fefetv.home", DUMB = "tv.dumbtv.home";
    private static final String A11Y = "enabled_accessibility_services";

    private boolean installed(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private List<String> services() {
        String cur = Settings.Secure.getString(getContentResolver(), A11Y);
        List<String> out = new ArrayList<>();
        if (cur == null) return out;
        for (String s : cur.split(":")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    private static boolean isGuard(String service, String pkg) {
        return service.equalsIgnoreCase(pkg + "/" + pkg + ".Guard") || service.equalsIgnoreCase(pkg + "/.Guard");
    }

    private String activeLauncher() {
        for (String s : services()) {
            if (isGuard(s, FEFE)) return "FefeTV";
            if (isGuard(s, DUMB)) return "dumbtv home";
        }
        return "Home Amazon";
    }

    private void launcherDialog() {
        List<String> names = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        names.add("FefeTV");
        pkgs.add(FEFE);
        if (installed(DUMB)) {
            names.add("dumbtv home");
            pkgs.add(DUMB);
        }
        names.add("Home Amazon (originale)");
        pkgs.add(null);
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Cambia launcher · attivo: " + activeLauncher())
                .setItems(names.toArray(new String[0]), (d, w) -> switchTo(pkgs.get(w)))
                .show();
    }

    /** Leaves exactly one launcher guard enabled (or none, for Amazon's home), keeping other services. */
    private void switchTo(String pkg) {
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this, Ui.DIALOG)
                    .setTitle("Serve un permesso")
                    .setMessage("Per cambiare launcher da qui, FefeTV deve avere il permesso di modificare "
                            + "le impostazioni di sistema. Rilancia dal PC:\n\nfefetv.sh launcher")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        List<String> keep = new ArrayList<>();
        for (String s : services()) if (!isGuard(s, FEFE) && !isGuard(s, DUMB)) keep.add(s);
        if (pkg != null) keep.add(pkg + "/" + pkg + ".Guard");
        try {
            Settings.Secure.putString(getContentResolver(), A11Y, TextUtils.join(":", keep));
            Settings.Secure.putString(getContentResolver(), "accessibility_enabled", keep.isEmpty() ? "0" : "1");
        } catch (SecurityException e) {
            Toast.makeText(this, "Fire OS ha rifiutato il cambio", Toast.LENGTH_LONG).show();
            return;
        }
        if (pkg == null) {
            Guard.amazon(this);
        } else if (DUMB.equals(pkg)) {
            launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(DUMB), null);
        } else {
            Toast.makeText(this, "FefeTV è il launcher attivo", Toast.LENGTH_SHORT).show();
            showPage(Screen.SETTINGS);
        }
    }
}

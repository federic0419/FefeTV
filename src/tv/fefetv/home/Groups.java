package tv.fefetv.home;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Which apps go under "Film e Serie", "Giochi e utility" and "File". The user can override it. */
final class Groups {
    static final String VIDEO = "video", GAMES = "games", FILES = "file", NONE = "none";

    private static final Set<String> VIDEO_PKGS = new HashSet<>(Arrays.asList(
            "com.amazon.avl.ftv", "com.netflix.ninja", "com.amazon.firetv.youtube", "org.smarttube.beta",
            "org.smarttube.stable", "com.disney.disneyplus", "com.plexapp.android", "org.xbmc.kodi",
            "com.stremio.one", "st.veezie", "de.herber_edevelopment.m3uiptv", "it.rainet.androidtv",
            "tv.twitch.android.viewer", "com.dazn", "com.mediaset.mediasetplay", "com.nowtv.it",
            "com.apple.atve.amazon.appletv", "com.hbo.hbonow", "com.wbd.stream", "com.crunchyroll.crunchyroid"));
    private static final Set<String> GAME_PKGS = new HashSet<>(Arrays.asList(
            "com.valvesoftware.steamlink", "com.limelight", "com.microsoft.xcloud", "com.gamepass",
            "org.videolan.vlc", "tv.plex.labs.plexamp", "com.spotify.tv.android", "com.esaba.downloader",
            "com.retroarch", "com.retroarch.aarch64", "com.nvidia.geforcenow"));
    private static final Set<String> FILE_PKGS = new HashSet<>(Arrays.asList(
            "com.estrongs.android.pop", "com.lonelycatgames.Xplore", "com.cxinventor.file.explorer",
            "com.alphainventor.filemanager", "com.ghisler.android.TotalCommander", "com.esaba.downloader",
            "com.amazon.tv.localgallery"));

    private Groups() {}

    static String of(Config cfg, Item it) {
        String set = cfg.get("fefe.group." + it.id, "");
        if (!set.isEmpty()) return set;
        if (VIDEO_PKGS.contains(it.id)) return VIDEO;
        if (FILE_PKGS.contains(it.id) && !"com.esaba.downloader".equals(it.id)) return FILES;
        if (GAME_PKGS.contains(it.id)) return GAMES;
        if (Build.VERSION.SDK_INT >= 26 && it.info != null && it.info.applicationInfo != null) {
            switch (it.info.applicationInfo.category) {
                case ApplicationInfo.CATEGORY_GAME:
                    return GAMES;
                case ApplicationInfo.CATEGORY_VIDEO:
                case ApplicationInfo.CATEGORY_AUDIO:
                    return VIDEO;
                default:
                    break;
            }
        }
        return NONE;
    }

    /** Files page also lists Downloader, which is a downloader more than a file manager. */
    static boolean onFilesPage(Config cfg, Item it) {
        return FILES.equals(of(cfg, it)) || "com.esaba.downloader".equals(it.id);
    }

    static void set(Config cfg, Item it, String group) {
        cfg.set("fefe.group." + it.id, group);
    }

    static String label(String group) {
        switch (group) {
            case VIDEO: return "Film e Serie";
            case GAMES: return "Giochi e utility";
            case FILES: return "File";
            default: return "Nessun gruppo";
        }
    }
}

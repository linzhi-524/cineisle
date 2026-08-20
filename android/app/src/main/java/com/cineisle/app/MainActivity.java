package com.cineisle.app;

import android.view.WindowManager;

import android.view.Window;

import android.graphics.drawable.ColorDrawable;

import android.app.Dialog;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.text.*;
import android.text.method.ScrollingMovementMethod;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private android.widget.FrameLayout fullscreenDanmakuRoot = null;
    private LinearLayout root, pageHome, pageRoom, pageHall, pageCard, pageFavorites;
    private String theme = "night";
    private String serverUrl = "";
    private String token = "";
    private String roomId = "";
    private String name = "观影人A";
    private String assistantName = "观影助手";
    private String avatar = "🐰";
    private String currentPage = "home";
    private VideoView video;
    private Button navHome, navRoom, navHall, navCard, navFavorites;
    private TextView roomTitle, roomCodeView, syncState, chatLog, noteLog, cardPreview, memberState, homeStatus, homeSub, heroBadge, fullChatLog, movieLibraryList, favoritesList, inviteSummary, importState;
    private EditText serverInput, tokenInput, nameInput, assistantNameInput, roomInput, chatInput, noteInput, quoteInput, cardNoteInput, linQuoteInput, linNoteInput, inviteMovieInput, invitePartnerInput, inviteMoodInput, inviteNoteInput;
    private Handler handler = new Handler();
    private boolean polling = false;
    private boolean applyingRemote = false;
    private boolean danmakuOn = true;
    private FrameLayout videoFrame, normalVideoFrame;
    private Dialog fullscreenDialog;
    private String fileName = "";
    private String movieTitle = "";
    private String currentMovieUri = "";
    private String invitePartner = "观影人 A × 观影人 B";
    private String inviteMood = "夜航";
    private String inviteNote = "今晚一起登岛看一场电影。";
    private String cardTemplate = "ticket";
    private JSONObject remoteCard = null;
    private TextView contextState, subtitleOverlay;
    private boolean contextAutoSync = true;
    private boolean autoScreenshot = false;
    private int lastSentSecond = -1;
    private int lastContextSecond = -1;
    private String lastContextSubtitle = "";
    private String lastSentContextSubtitle = "";
    private int lastStablePositionMs = 0;
    private long lastStablePositionAt = 0L;
    private int fullscreenExitRestoreMs = -1;
    private boolean fullscreenExitWasPlaying = false;
    private long fullscreenExitAt = 0L;
    private String lastPlaybackIssue = "";
    private String lastNetworkIssue = "";
    private final ArrayList<SubtitleCue> subtitleCues = new ArrayList<>();
    private final ArrayList<MovieItem> movieLibrary = new ArrayList<>();

    private static class SubtitleCue {
        double start;
        double end;
        String text;
        SubtitleCue(double start, double end, String text) { this.start = start; this.end = end; this.text = text; }
    }

    private static class MovieItem {
        String uri;
        String title;
        String fileName;
        long addedAt;
        int lastPositionMs;
        MovieItem(String uri, String title, String fileName, long addedAt, int lastPositionMs) {
            this.uri = uri == null ? "" : uri;
            this.title = title == null ? "本地影片" : title;
            this.fileName = fileName == null ? "" : fileName;
            this.addedAt = addedAt;
            this.lastPositionMs = Math.max(0, lastPositionMs);
        }
    }

    private static class PendingChat {
        String id;
        String who;
        String text;
        boolean failed;
        PendingChat(String id, String who, String text) {
            this.id = id == null ? "" : id;
            this.who = who == null ? "观影人" : who;
            this.text = text == null ? "" : text;
            this.failed = false;
        }
    }

    private final ArrayList<PendingChat> pendingChats = new ArrayList<>();
    private long lastLocalPlaybackActionAt = 0L;
    private long lastPlaybackSyncAt = 0L;
    private String lastAppliedPlaybackCommandId = "";
    private int lastObservedPositionMs = 0;
    private boolean lastObservedPlaying = false;

    private final HashSet<String> seenDanmakuKeys = new HashSet<>();
    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (polling && roomId.length() > 0) {
                fetchRoom();
                handler.postDelayed(this, 3000);
            }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        loadPrefs();
        buildUI();
        showPage("home");
    }

    private void loadPrefs() {
        android.content.SharedPreferences sp = getSharedPreferences("cineisle", 0);
        serverUrl = sp.getString("serverUrl", "");
        token = sp.getString("token", "");
        name = sp.getString("name", "观影人A");
        assistantName = sp.getString("assistantName", "观影助手");
        avatar = sp.getString("avatar", "🐰");
        theme = sp.getString("theme", "night");
        invitePartner = sp.getString("invitePartner", "观影人 A × 观影人 B");
        inviteMood = sp.getString("inviteMood", "夜航");
        inviteNote = sp.getString("inviteNote", "今晚一起登岛看一场电影。");
        cardTemplate = sp.getString("cardTemplate", "ticket");
        contextAutoSync = sp.getBoolean("contextAutoSync", true);
        autoScreenshot = sp.getBoolean("autoScreenshot", false);
        lastStablePositionMs = sp.getInt("lastStablePositionMs", 0);
        lastStablePositionAt = sp.getLong("lastStablePositionAt", 0L);
        currentMovieUri = sp.getString("currentMovieUri", "");
        loadMovieLibrary();
    }

    private void savePrefs() {
        getSharedPreferences("cineisle", 0).edit()
            .putString("serverUrl", serverUrl)
            .putString("token", token)
            .putString("name", name)
            .putString("assistantName", aiName())
            .putString("avatar", avatar)
            .putString("theme", theme)
            .putString("invitePartner", invitePartner)
            .putString("inviteMood", inviteMood)
            .putString("inviteNote", inviteNote)
            .putString("cardTemplate", cardTemplate)
            .putBoolean("contextAutoSync", contextAutoSync)
            .putBoolean("autoScreenshot", autoScreenshot)
            .putString("roomId", roomId)
            .putString("currentMovieUri", currentMovieUri)
            .putInt("lastStablePositionMs", lastStablePositionMs)
            .putLong("lastStablePositionAt", lastStablePositionAt)
            .putBoolean("contextPaused", video == null || !video.isPlaying())
            .apply();
    }

    private int bg() {
        if (theme.equals("cream")) return color("#FCFBF7");
        if (theme.equals("galaxy")) return color("#130F28");
        if (theme.equals("matcha")) return color("#EFF6EC");
        if (theme.equals("film")) return color("#12100D");
        if (theme.equals("dusk")) return color("#231729");
        return color("#0D1325");
    }
    private int card() {
        if (theme.equals("cream")) return color("#FFFFFFFF");
        if (theme.equals("matcha")) return color("#FFFFFFFF");
        if (theme.equals("galaxy")) return color("#201A3A");
        if (theme.equals("film")) return color("#1D1912");
        if (theme.equals("dusk")) return color("#2D2034");
        return color("#161F38");
    }
    private int cardSoft() {
        if (theme.equals("cream")) return color("#F4F2ED");
        if (theme.equals("matcha")) return color("#E7F0E1");
        if (theme.equals("galaxy")) return color("#2A214B");
        if (theme.equals("film")) return color("#2A251B");
        if (theme.equals("dusk")) return color("#3A2940");
        return color("#202A47");
    }
    private int ink() {
        if (theme.equals("cream") || theme.equals("matcha")) return color("#26314D");
        return color("#F6F8FF");
    }
    private int muted() {
        if (theme.equals("cream")) return color("#7C869F");
        if (theme.equals("matcha")) return color("#738174");
        if (theme.equals("film")) return color("#C8BFA9");
        if (theme.equals("dusk")) return color("#D0B9D6");
        return color("#AFB8D8");
    }
    private int accent() {
        if (theme.equals("cream")) return color("#8F88F3");
        if (theme.equals("galaxy")) return color("#A980FF");
        if (theme.equals("matcha")) return color("#87B68D");
        if (theme.equals("film")) return color("#C7A86B");
        if (theme.equals("dusk")) return color("#D394D8");
        return color("#88A6FF");
    }
    private int accent2() {
        if (theme.equals("cream")) return color("#F2B8C6");
        if (theme.equals("galaxy")) return color("#6AD0FF");
        if (theme.equals("matcha")) return color("#C7E2B8");
        if (theme.equals("film")) return color("#8B6D3F");
        if (theme.equals("dusk")) return color("#6EA8FF");
        return color("#C497FF");
    }
    private int color(String s) { return Color.parseColor(s); }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private TextView tv(String text, int sp, int style) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(ink());
        v.setTypeface(Typeface.DEFAULT, style);
        v.setLineSpacing(dp(2), 1.04f);
        return v;
    }

    private TextView small(String text) {
        TextView v = tv(text, 12, Typeface.NORMAL);
        v.setTextColor(muted());
        return v;
    }

    private GradientDrawable round(int c, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(dp(r));
        g.setStroke(dp(1), theme.equals("cream") || theme.equals("matcha") ? color("#1C576587") : color("#29FFFFFF"));
        return g;
    }

    private GradientDrawable grad(int[] colors, float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        g.setCornerRadius(dp(r));
        return g;
    }

    private Button btn(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : ink());
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(primary ? grad(new int[]{accent(), accent2()}, 18) : round(cardSoft(), 18));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setSingleLine(true);
        e.setTextColor(ink());
        e.setHintTextColor(muted());
        e.setTextSize(14);
        e.setBackground(round(cardSoft(), 18));
        e.setPadding(dp(14), 0, dp(14), 0);
        return e;
    }

    private ScrollView scroll(View child) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.addView(child);
        return s;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private void add(ViewGroup parent, View child, int w, int h, int mt) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
        lp.setMargins(0, dp(mt), 0, 0);
        parent.addView(child, lp);
    }

    private TextView chip(String text) {
        TextView v = small(text);
        v.setTextColor(ink());
        v.setBackground(round(cardSoft(), 18));
        v.setPadding(dp(12), dp(8), dp(12), dp(8));
        return v;
    }

    private LinearLayout panel() {
        LinearLayout p = vbox();
        p.setPadding(dp(16), dp(16), dp(16), dp(16));
        p.setBackground(round(card(), 28));
        p.setElevation(dp(3));
        return p;
    }

    private int topInset() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private void buildUI() {
        root = vbox();
        root.setBackgroundColor(bg());
        setContentView(root);

        FrameLayout frame = new FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));

        pageHome = buildHome();
        pageRoom = buildRoom();
        pageHall = buildHall();
        pageCard = buildCard();
        pageFavorites = buildFavorites();
        frame.addView(pageHome);
        frame.addView(pageRoom);
        frame.addView(pageHall);
        frame.addView(pageCard);
        frame.addView(pageFavorites);

        LinearLayout navWrap = vbox();
        navWrap.setPadding(dp(14), dp(6), dp(14), dp(14));
        LinearLayout nav = hbox();
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        nav.setBackground(round(card(), 30));
        nav.setElevation(dp(5));
        navWrap.addView(nav, new LinearLayout.LayoutParams(-1, dp(70)));
        root.addView(navWrap);

        navHome = btn("首页", true);
        navRoom = btn("放映厅", false);
        navHall = btn("影厅", false);
        navCard = btn("票根", false);
        navFavorites = btn("档案馆", false);
        nav.addView(navHome, new LinearLayout.LayoutParams(0, dp(50), 1));
        nav.addView(navRoom, new LinearLayout.LayoutParams(0, dp(50), 1));
        nav.addView(navHall, new LinearLayout.LayoutParams(0, dp(50), 1));
        nav.addView(navCard, new LinearLayout.LayoutParams(0, dp(50), 1));
        nav.addView(navFavorites, new LinearLayout.LayoutParams(0, dp(50), 1));
        navHome.setOnClickListener(v -> showPage("home"));
        navRoom.setOnClickListener(v -> showPage("room"));
        navHall.setOnClickListener(v -> showPage("hall"));
        navCard.setOnClickListener(v -> showPage("card"));
        navFavorites.setOnClickListener(v -> showPage("favorites"));
        updateNav("home");
    }

    private LinearLayout buildHome() {
        LinearLayout wrap = vbox();
        wrap.setPadding(dp(16), topInset() + dp(8), dp(16), dp(12));
        LinearLayout c = vbox();
        wrap.addView(scroll(c), new LinearLayout.LayoutParams(-1, -1));

        LinearLayout topBar = hbox();
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand = vbox();

        FrameLayout logo = new FrameLayout(this);
        TextView logoShadow = new TextView(this);
        logoShadow.setText("CineIsle");
        logoShadow.setTextSize(30);
        logoShadow.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        logoShadow.setTextColor(color("#55FFFFFF"));
        logoShadow.setLetterSpacing(0.025f);
        logoShadow.setTranslationX(dp(2));
        logoShadow.setTranslationY(dp(2));
        logo.addView(logoShadow, new FrameLayout.LayoutParams(-1, dp(44)));

        TextView logoText = new TextView(this);
        logoText.setText("CineIsle");
        logoText.setTextSize(30);
        logoText.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        logoText.setTextColor(ink());
        logoText.setLetterSpacing(0.025f);
        logoText.setShadowLayer(dp(2), 0, dp(1), theme.equals("cream") || theme.equals("matcha") ? color("#77FFFFFF") : color("#553B82F6"));
        logo.addView(logoText, new FrameLayout.LayoutParams(-1, dp(44)));

        brand.addView(logo, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView sub = small("CineIsle · your island for watching together");
        sub.setLetterSpacing(0.04f);
        brand.addView(sub);
        topBar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button gear = btn("设置", false);
        topBar.addView(gear, new LinearLayout.LayoutParams(dp(88), dp(44)));
        c.addView(topBar);

        LinearLayout heroBox = panel();
        heroBox.setPadding(dp(0), dp(0), dp(0), dp(0));
        LinearLayout hero = vbox();
        hero.setBackground(grad(heroColors(), 30));
        hero.setPadding(dp(20), dp(22), dp(20), dp(22));
        heroBadge = chip("CineIsle");
        heroBadge.setBackground(round(color("#22FFFFFF"), 18));
        heroBadge.setTextColor(Color.WHITE);
        hero.addView(heroBadge, new LinearLayout.LayoutParams(-2, -2));
        TextView heroTitle = new TextView(this);
        heroTitle.setText("今晚登岛，\n把电影变成共同记忆。");
        heroTitle.setTextColor(Color.WHITE);
        heroTitle.setTextSize(24);
        heroTitle.setTypeface(Typeface.DEFAULT_BOLD);
        heroTitle.setLineSpacing(dp(4), 1.05f);
        add(hero, heroTitle, -1, -2, 16);
        homeSub = new TextView(this);
        homeSub.setTextColor(color("#EBF0FF"));
        homeSub.setTextSize(13);
        homeSub.setText("本地导入视频，同步播放进度、聊天、弹幕、时间轴笔记、字幕感知和低频画面截图。\n适合自部署的双人共同观影。");
        add(hero, homeSub, -1, -2, 10);
        LinearLayout heroBottom = hbox();
        heroBottom.setGravity(Gravity.CENTER_VERTICAL);
        TextView mood = new TextView(this);
        mood.setText(avatar + "  " + name + " 正在筹备今晚的观影岛");
        mood.setTextColor(Color.WHITE);
        mood.setTextSize(13);
        heroBottom.addView(mood, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chip2 = new TextView(this);
        chip2.setText("6 themes");
        chip2.setTextColor(Color.WHITE);
        chip2.setTextSize(12);
        chip2.setPadding(dp(10), dp(7), dp(10), dp(7));
        chip2.setBackground(round(color("#22FFFFFF"), 18));
        heroBottom.addView(chip2);
        add(hero, heroBottom, -1, -2, 16);
        heroBox.addView(hero, new LinearLayout.LayoutParams(-1, -2));
        add(c, heroBox, -1, -2, 18);

        LinearLayout quick = panel();
        quick.addView(tv("观影前 · 登岛邀请", 18, Typeface.BOLD));
        quick.addView(small("先写好今晚看什么、和谁看、是什么氛围，再一键生成观影岛。"));
        add(c, quick, -1, -2, 14);

        LinearLayout row1 = hbox();
        row1.addView(actionCard("创建观影岛", "带着邀请卡生成房间", "创建", true, v -> { collectInvitation(); collectSettings(); createRoom(); }), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams r1b = new LinearLayout.LayoutParams(0, -2, 1); r1b.setMargins(dp(10),0,0,0);
        row1.addView(actionCard("登岛加入", "输入房间号马上会合", "加入", false, v -> { collectSettings(); joinRoom(roomInput.getText().toString()); }), r1b);
        add(c, row1, -1, -2, 10);

        LinearLayout row2 = hbox();
        row2.addView(actionCard("导入影片", "本地文件不会上传", "导入", false, v -> pickVideo()), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams r2b = new LinearLayout.LayoutParams(0, -2, 1); r2b.setMargins(dp(10),0,0,0);
        row2.addView(actionCard("回到放映厅", "继续这场电影", "继续", false, v -> showPage("room")), r2b);
        add(c, row2, -1, -2, 10);

        LinearLayout invitePanel = panel();
        invitePanel.addView(tv("今晚的观影邀请卡", 17, Typeface.BOLD));
        invitePanel.addView(small("这里写的内容会进入房间、邀请卡和票根，适合两个人开场前先定好今晚氛围。"));
        inviteMovieInput = input("电影名 / 房间标题", movieTitle);
        invitePartnerInput = input("观影人，如 观影人 A × 观影人 B", invitePartner);
        inviteMoodInput = input("今晚氛围，如 夜航 / 雨天 / 奶油 / 深蓝", inviteMood);
        inviteNoteInput = input("开场备注", inviteNote);
        inviteNoteInput.setSingleLine(false);
        add(invitePanel, inviteMovieInput, -1, 48, 12);
        add(invitePanel, invitePartnerInput, -1, 48, 10);
        add(invitePanel, inviteMoodInput, -1, 48, 10);
        add(invitePanel, inviteNoteInput, -1, 82, 10);
        Button saveInvite = btn("保存邀请卡", true);
        saveInvite.setOnClickListener(v -> { collectInvitation(); toast("观影邀请卡已保存"); renderCard(); });
        add(invitePanel, saveInvite, -1, 46, 12);
        add(c, invitePanel, -1, -2, 14);

        LinearLayout roomPanel = panel();
        roomPanel.addView(tv("快速加入房间", 17, Typeface.BOLD));
        roomPanel.addView(small("你可以在这里直接输入房间号，像敲开一扇小小放映室的门。"));
        roomInput = input("输入房间号，如 QXQ8KU", roomId);
        add(roomPanel, roomInput, -1, 48, 12);
        homeStatus = small(roomId.length() > 0 ? "当前房间：" + roomId : "当前还没有加入房间");
        add(roomPanel, homeStatus, -1, -2, 10);
        add(c, roomPanel, -1, -2, 14);

        LinearLayout currentPanel = panel();
        currentPanel.addView(tv("今晚的放映状态", 17, Typeface.BOLD));
        TextView line1 = chip(movieTitle.length() > 0 ? "片名 · " + movieTitle : "片名 · 等待导入影片");
        TextView line2 = chip(serverUrl.length() > 0 ? "后端已配置" : "还没有配置后端");
        add(currentPanel, line1, -1, -2, 10);
        add(currentPanel, line2, -1, -2, 8);
        add(c, currentPanel, -1, -2, 14);

        gear.setOnClickListener(v -> openSettingsSheet());
        return wrap;
    }

    private LinearLayout actionCard(String title, String desc, String buttonText, boolean primary, View.OnClickListener listener) {
        LinearLayout p = panel();
        p.setMinimumHeight(dp(142));
        TextView t = tv(title, 17, Typeface.BOLD);
        TextView d = small(desc);
        d.setTextSize(13);
        p.addView(t);
        add(p, d, -1, -2, 6);
        Button b = btn(buttonText, primary);
        b.setOnClickListener(listener);
        add(p, b, -1, 42, 18);
        return p;
    }

    private void openSettingsSheet() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout outer = vbox();
        outer.setPadding(dp(16), dp(16), dp(16), dp(16));
        outer.setBackground(round(card(), 30));

        LinearLayout head = hbox();
        TextView t = tv("设置抽屉", 22, Typeface.BOLD);
        TextView close = chip("关闭");
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(close);
        outer.addView(head);
        outer.addView(small("把后端、昵称、主题和头像都收在这里，主页只留给一起看电影的氛围。"));

        serverInput = input("后端地址，如 https://xxx.onrender.com", serverUrl);
        tokenInput = input("MCP Token，可不填", token);
        nameInput = input("你的昵称", name);
        assistantNameInput = input("AI 名字，如 小G / Claude / 观影搭子", aiName());
        add(outer, serverInput, -1, 48, 14);
        add(outer, tokenInput, -1, 48, 10);
        add(outer, nameInput, -1, 48, 10);
        add(outer, assistantNameInput, -1, 48, 10);

        LinearLayout avatarRow = hbox();
        avatarRow.setGravity(Gravity.CENTER);
        String[] avs = {"🐰","🎬","🌙","🍿","☁️"};
        for (String a: avs) {
            Button ab = btn(a, a.equals(avatar));
            ab.setTextSize(20);
            ab.setOnClickListener(v -> {
                avatar = ((Button)v).getText().toString();
                updateHeroTexts();
                openSettingsSheetRefresh(dialog);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1);
            lp.setMargins(dp(4), dp(12), dp(4), 0);
            avatarRow.addView(ab, lp);
        }
        add(outer, tv("头像", 16, Typeface.BOLD), -1, -2, 16);
        outer.addView(avatarRow);

        add(outer, tv("主题皮肤", 16, Typeface.BOLD), -1, -2, 16);
        LinearLayout skinGrid = vbox();
        outer.addView(skinGrid, new LinearLayout.LayoutParams(-1, -2));
        String[][] themes = {{"cream","奶油白"},{"night","夜航蓝"},{"galaxy","星河紫"},{"matcha","雾岛绿"},{"film","胶片黑"},{"dusk","暮光紫"}};
        LinearLayout skinRow = null;
        for (int i = 0; i < themes.length; i++) {
            if (i % 2 == 0) {
                skinRow = hbox();
                skinGrid.addView(skinRow, new LinearLayout.LayoutParams(-1, -2));
            }
            final String key = themes[i][0];
            Button tb = btn(themes[i][1], key.equals(theme));
            tb.setOnClickListener(v -> {
                theme = key;
                savePrefs();
                dialog.dismiss();
                rebuild();
                if (roomId.length() > 0) fetchRoom();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1);
            lp.setMargins(i % 2 == 0 ? 0 : dp(8), dp(8), 0, 0);
            skinRow.addView(tb, lp);
        }

        Button save = btn("保存并应用", true);
        save.setOnClickListener(v -> {
            serverUrl = normalizeServer(serverInput.getText().toString());
            token = tokenInput.getText().toString().trim();
            name = nameInput.getText().toString().trim();
            assistantName = assistantNameInput == null ? assistantName : assistantNameInput.getText().toString().trim();
            if (name.length() == 0) name = "观影人";
            if (assistantName.length() == 0) assistantName = "观影助手";
            savePrefs();
            updateServicePrefs();
            dialog.dismiss();
            rebuild();
        });
        add(outer, save, -1, 48, 18);

        ScrollView sc = scroll(outer);
        dialog.setContentView(sc);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void openSettingsSheetRefresh(Dialog old) {
        if (old != null && old.isShowing()) old.dismiss();
        openSettingsSheet();
    }

    private int[] heroColors() {
        if (theme.equals("cream")) return new int[]{color("#E7DBFF"), color("#C9B9FF")};
        if (theme.equals("galaxy")) return new int[]{color("#4D3B87"), color("#1A133E")};
        if (theme.equals("matcha")) return new int[]{color("#A7D0A2"), color("#7CAC86")};
        if (theme.equals("film")) return new int[]{color("#3B2F22"), color("#0F0D0A")};
        if (theme.equals("dusk")) return new int[]{color("#6E3C7F"), color("#1A1530")};
        return new int[]{color("#3F4F97"), color("#111A34")};
    }

    private LinearLayout buildRoom() {
        LinearLayout wrap = vbox();
        wrap.setPadding(dp(16), topInset() + dp(6), dp(16), dp(12));
        LinearLayout c = vbox();
        wrap.addView(scroll(c), new LinearLayout.LayoutParams(-1, -1));

        LinearLayout header = panel();
        header.setBackground(grad(heroColors(), 28));
        header.setPadding(dp(18), dp(18), dp(18), dp(18));
        roomTitle = new TextView(this);
        roomTitle.setText("映屿 CineIsle");
        roomTitle.setTextColor(Color.WHITE);
        roomTitle.setTextSize(24);
        roomTitle.setTypeface(Typeface.DEFAULT_BOLD);
        roomCodeView = new TextView(this);
        roomCodeView.setText("还没有进入房间 · 可以先导入本地影片");
        roomCodeView.setTextColor(color("#E9EDFF"));
        roomCodeView.setTextSize(13);
        header.addView(roomTitle);
        add(header, roomCodeView, -1, -2, 8);
        LinearLayout headerBottom = hbox();
        syncState = chip("未连接房间");
        syncState.setTextColor(Color.WHITE);
        syncState.setBackground(round(color("#22FFFFFF"), 18));
        headerBottom.addView(syncState);
        memberState = new TextView(this);
        memberState.setTextColor(color("#F1F5FF"));
        memberState.setText("0 人在线");
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2); mlp.setMargins(dp(10), 0, 0, 0);
        headerBottom.addView(memberState, mlp);
        add(header, headerBottom, -1, -2, 14);
        add(c, header, -1, -2, 4);

        LinearLayout inviteMini = panel();
        inviteMini.addView(tv("观影邀请卡", 17, Typeface.BOLD));
        inviteSummary = small(invitationText());
        inviteSummary.setTextColor(ink());
        inviteSummary.setBackground(round(cardSoft(), 20));
        inviteSummary.setPadding(dp(14), dp(12), dp(14), dp(12));
        add(inviteMini, inviteSummary, -1, -2, 10);
        importState = chip("本地影片未导入 · 等待准备");
        add(inviteMini, importState, -1, -2, 10);
        add(c, inviteMini, -1, -2, 14);

        videoFrame = new FrameLayout(this);
        normalVideoFrame = videoFrame;
        videoFrame.setBackground(round(color("#090D18"), 28));
        video = new VideoView(this);
        try { video.setZOrderOnTop(false); video.setZOrderMediaOverlay(false); } catch(Exception ignored) {}
        videoFrame.addView(video, new FrameLayout.LayoutParams(-1, -1));
        subtitleOverlay = small("");
        subtitleOverlay.setTextColor(Color.WHITE);
        subtitleOverlay.setGravity(Gravity.CENTER);
        subtitleOverlay.setTextSize(15);
        subtitleOverlay.setPadding(dp(12), dp(8), dp(12), dp(8));
        subtitleOverlay.setBackground(round(color("#99000000"), 18));
        subtitleOverlay.setVisibility(View.GONE);
        LinearLayout overlay = vbox();
        overlay.setGravity(Gravity.CENTER);
        overlay.setTag("video_overlay");
        TextView empty = tv("在这里放映今晚的电影", 20, Typeface.BOLD);
        empty.setTextColor(Color.WHITE);
        empty.setGravity(Gravity.CENTER);
        empty.setTag("empty");
        TextView hint = new TextView(this);
        hint.setText("先导入本地影片，再和对方同步播放。\n弹幕、金句、笔记都会长成一条时间轴。♡");
        hint.setGravity(Gravity.CENTER);
        hint.setTextSize(13);
        hint.setTextColor(color("#C9D1F3"));
        overlay.addView(empty);
        add(overlay, hint, -1, -2, 8);
        videoFrame.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setOnClickListener(v -> togglePlay());
        videoFrame.setOnClickListener(v -> togglePlay());
        FrameLayout.LayoutParams subLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        subLp.setMargins(dp(16), 0, dp(16), dp(18));
        videoFrame.addView(subtitleOverlay, subLp);
        add(c, videoFrame, -1, 250, 16);

        MediaController mc = new MediaController(this);
        video.setMediaController(mc);

        LinearLayout actions = hbox();
        Button pick = btn("导入影片", true);
        Button playPause = btn("播放/暂停", true);
        actions.addView(pick, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams ppLp = new LinearLayout.LayoutParams(0, dp(46), 1); ppLp.setMargins(dp(8),0,0,0);
        actions.addView(playPause, ppLp);
        add(c, actions, -1, 46, 8);

        LinearLayout actions2 = hbox();
        Button sync = btn("同步进度", false);
        Button danmaku = btn("弹幕 ON", false);
        Button fullscreen = btn("横屏", false);
        actions2.addView(sync, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams dx = new LinearLayout.LayoutParams(0, dp(46), 1); dx.setMargins(dp(8),0,0,0);
        actions2.addView(danmaku, dx);
        LinearLayout.LayoutParams fx = new LinearLayout.LayoutParams(0, dp(46), 1); fx.setMargins(dp(8),0,0,0);
        actions2.addView(fullscreen, fx);
        add(c, actions2, -1, 46, 12);

        LinearLayout senseP = panel();
        senseP.addView(tv(aiName() + "感知", 18, Typeface.BOLD));
        senseP.addView(small("同步片名、进度、当前字幕和最近字幕；低频画面截图可交给无障碍服务自动上传，只在你打开开关后启用。"));
        contextState = small(contextStatusText());
        contextState.setTextColor(ink());
        contextState.setBackground(round(cardSoft(), 20));
        contextState.setPadding(dp(14), dp(12), dp(14), dp(12));
        add(senseP, contextState, -1, -2, 10);
        LinearLayout senseRow1 = hbox();
        Button pickSubtitle = btn("导入字幕", false);
        Button contextToggle = btn(contextAutoSync ? "字幕感知 ON" : "字幕感知 OFF", contextAutoSync);
        senseRow1.addView(pickSubtitle, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(0, dp(44), 1); stLp.setMargins(dp(8),0,0,0);
        senseRow1.addView(contextToggle, stLp);
        add(senseP, senseRow1, -1, 44, 10);
        LinearLayout senseRow2 = hbox();
        Button screenshotToggle = btn(autoScreenshot ? "自动截图 ON" : "自动截图 OFF", autoScreenshot);
        Button a11y = btn("开启无障碍", false);
        senseRow2.addView(screenshotToggle, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams ayLp = new LinearLayout.LayoutParams(0, dp(44), 1); ayLp.setMargins(dp(8),0,0,0);
        senseRow2.addView(a11y, ayLp);
        add(senseP, senseRow2, -1, 44, 8);
        LinearLayout senseRow3 = hbox();
        Button snapNow = btn("立即截图一次", false);
        senseRow3.addView(snapNow, new LinearLayout.LayoutParams(-1, dp(44)));
        add(senseP, senseRow3, -1, 44, 8);
        add(c, senseP, -1, -2, 14);

        LinearLayout chatP = panel();
        chatP.addView(tv("岛上留言与弹幕雨", 18, Typeface.BOLD));
        chatP.addView(small("聊天像留言，弹幕像漂过银幕的小纸条，都会进入本场观影时间轴。"));
        chatLog = tv("还没有聊天。第一句可以留给今晚的电影。", 13, Typeface.NORMAL);
        chatLog.setTextColor(ink());
        chatLog.setMovementMethod(new ScrollingMovementMethod());
        chatLog.setMinHeight(dp(150));
        chatLog.setBackground(round(cardSoft(), 20));
        chatLog.setPadding(dp(14), dp(14), dp(14), dp(14));
        add(chatP, chatLog, -1, 170, 10);
        chatInput = input("今晚想和对方说什么？", "");
        add(chatP, chatInput, -1, 48, 10);
        LinearLayout cr = hbox();
        Button sendChat = btn("聊天", true);
        Button sendDm = btn("发弹幕", false);
        cr.addView(sendChat, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams dmLp = new LinearLayout.LayoutParams(0, dp(44), 1); dmLp.setMargins(dp(8),0,0,0);
        cr.addView(sendDm, dmLp);
        add(chatP, cr, -1, 44, 8);
        add(c, chatP, -1, -2, 14);

        LinearLayout noteP = panel();
        noteP.addView(tv("时间轴笔记", 18, Typeface.BOLD));
        noteP.addView(small("记录某一幕、摘一句台词，最后自动落进票根和档案馆。"));
        noteInput = input("这一幕想记下什么？也可以直接写一句台词", "");
        noteInput.setSingleLine(false);
        add(noteP, noteInput, -1, 88, 10);
        Button addNote = btn("添加笔记", true);
        Button addQuote = btn("摘一句", false);
        LinearLayout noteButtons = hbox();
        noteButtons.addView(addNote, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(0, dp(44), 1); qlp.setMargins(dp(8),0,0,0);
        noteButtons.addView(addQuote, qlp);
        add(noteP, noteButtons, -1, 44, 10);
        noteLog = tv("", 13, Typeface.NORMAL);
        noteLog.setTextColor(ink());
        noteLog.setMovementMethod(new ScrollingMovementMethod());
        noteLog.setBackground(round(cardSoft(), 20));
        noteLog.setPadding(dp(14), dp(14), dp(14), dp(14));
        add(noteP, noteLog, -1, 180, 10);
        add(c, noteP, -1, -2, 14);

        pick.setOnClickListener(v -> pickVideo());
        playPause.setOnClickListener(v -> togglePlay());
        sync.setOnClickListener(v -> sendPlayback(true));
        danmaku.setOnClickListener(v -> { danmakuOn = !danmakuOn; danmaku.setText(danmakuOn ? "弹幕 ON" : "弹幕 OFF"); });
        fullscreen.setOnClickListener(v -> openCinemaFullscreen());
        pickSubtitle.setOnClickListener(v -> pickSubtitleFile());
        contextToggle.setOnClickListener(v -> {
            contextAutoSync = !contextAutoSync;
            savePrefs();
            updateServicePrefs();
            rebuild();
            toast(contextAutoSync ? "字幕感知已开启" : "字幕感知已关闭");
            if (contextAutoSync) sendCinemaContext(true);
        });
        screenshotToggle.setOnClickListener(v -> {
            autoScreenshot = !autoScreenshot;
            savePrefs();
            updateServicePrefs();
            rebuild();
            toast(autoScreenshot ? "自动低频截图已开启，记得打开无障碍服务" : "自动低频截图已关闭");
        });
        a11y.setOnClickListener(v -> {
            updateServicePrefs();
            try { startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch(Exception e) { toast("无法打开无障碍设置"); }
        });
        snapNow.setOnClickListener(v -> requestLocalScreenshotNow());
        sendChat.setOnClickListener(v -> sendMessage(false));
        sendDm.setOnClickListener(v -> sendMessage(true));
        addNote.setOnClickListener(v -> sendNote());
        addQuote.setOnClickListener(v -> sendQuoteLine());

        video.setOnPreparedListener(mp -> {
            View e = videoFrame.findViewWithTag("video_overlay");
            if (e != null) e.setVisibility(View.GONE);
            updateViewingContext(false);
            if (lastStablePositionMs > 1500) {
                final int restoreMs = lastStablePositionMs;
                handler.postDelayed(() -> {
                    try {
                        if (video != null && video.getDuration() > 0 && video.getCurrentPosition() < 1000 && restoreMs > 1500) {
                            video.seekTo(restoreMs);
                        }
                    } catch(Exception ignored) {}
                }, 350);
            }
            sendMovieInfo();
            sendCinemaContext(true);
        });
        video.setOnCompletionListener(mp -> sendPlayback(true));
        video.setOnErrorListener((mp, what, extra) -> {
            lastPlaybackIssue = "VideoView error what=" + what + " extra=" + extra;
            toast("播放异常：" + lastPlaybackIssue);
            sendPlayback(true);
            return false;
        });
        video.setOnInfoListener((mp, what, extra) -> {
            if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) lastPlaybackIssue = "buffering_start";
            else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) lastPlaybackIssue = "buffering_end";
            else if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) lastPlaybackIssue = "rendering_start";
            if (lastPlaybackIssue.length() > 0) sendPlayback(false);
            return false;
        });
        video.setOnClickListener(v -> togglePlay());
        video.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP || ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                lastLocalPlaybackActionAt = System.currentTimeMillis();
                handler.postDelayed(() -> sendPlayback(true), 450);
            }
            return false;
        });

        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!applyingRemote && roomId.length() > 0 && video.getDuration() > 0) {
                    int pos = outboundPositionMs();
                    boolean playing = video.isPlaying();
                    boolean stateChanged = playing != lastObservedPlaying;
                    boolean movedWhilePlaying = playing && Math.abs(pos - lastObservedPositionMs) > 450;
                    if (stateChanged || movedWhilePlaying) lastLocalPlaybackActionAt = System.currentTimeMillis();
                    lastObservedPlaying = playing;
                    lastObservedPositionMs = pos;

                    rememberPlaybackPosition();
                    int sec = pos / 1000;
                    updateViewingContext(false);
                    if (stateChanged || sec != lastSentSecond) {
                        lastSentSecond = sec;
                        sendPlayback(stateChanged);
                    }
                    sendCinemaContext(false);
                }
                handler.postDelayed(this, 1000);
            }
        }, 700);

        return wrap;
    }


    private LinearLayout buildHall() {
        LinearLayout wrap = vbox();
        wrap.setPadding(dp(16), topInset() + dp(8), dp(16), dp(12));
        LinearLayout c = vbox();
        wrap.addView(scroll(c), new LinearLayout.LayoutParams(-1, -1));
        TextView title = tv("影厅", 30, Typeface.BOLD);
        c.addView(title);
        c.addView(small("这里保存本机导入过的影片。影片文件不会上传，只保存本机 URI，之后想重看可以直接从这里打开。"));

        LinearLayout top = hbox();
        Button importMovie = btn("导入新影片", true);
        Button refresh = btn("刷新影厅", false);
        top.addView(importMovie, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, dp(44), 1); rlp.setMargins(dp(8),0,0,0);
        top.addView(refresh, rlp);
        add(c, top, -1, 44, 14);
        importMovie.setOnClickListener(v -> pickVideo());
        refresh.setOnClickListener(v -> { loadMovieLibrary(); rebuild(); showPage("hall"); });

        LinearLayout p = panel();
        p.addView(tv("本机片库", 18, Typeface.BOLD));
        movieLibraryList = tv("影厅还空着。导入一部影片后会自动出现在这里。", 14, Typeface.NORMAL);
        movieLibraryList.setTextColor(ink());
        movieLibraryList.setPadding(dp(14), dp(14), dp(14), dp(14));
        movieLibraryList.setBackground(round(cardSoft(), 20));
        add(p, movieLibraryList, -1, -2, 10);

        int count = Math.min(movieLibrary.size(), 8);
        for (int i=0; i<count; i++) {
            final MovieItem item = movieLibrary.get(i);
            LinearLayout row = hbox();
            Button open = btn("打开", i == 0);
            TextView info = small((i+1) + ". " + item.title + (item.lastPositionMs > 1000 ? " · 上次 " + formatTime(item.lastPositionMs/1000) : ""));
            info.setTextColor(ink());
            info.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(info, new LinearLayout.LayoutParams(0, dp(46), 1));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(dp(86), dp(42)); olp.setMargins(dp(8),0,0,0);
            row.addView(open, olp);
            add(p, row, -1, 46, 8);
            open.setOnClickListener(v -> openMovieFromLibrary(item));
        }

        LinearLayout bottom = hbox();
        Button openLast = btn("打开最近影片", false);
        Button clear = btn("清空影厅记录", false);
        bottom.addView(openLast, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(44), 1); clp.setMargins(dp(8),0,0,0);
        bottom.addView(clear, clp);
        add(p, bottom, -1, 44, 12);
        openLast.setOnClickListener(v -> {
            if (movieLibrary.size() == 0) toast("影厅还没有影片");
            else openMovieFromLibrary(movieLibrary.get(0));
        });
        clear.setOnClickListener(v -> {
            movieLibrary.clear();
            saveMovieLibrary();
            refreshMovieLibrary();
            toast("已清空影厅记录，本地影片文件不会被删除");
        });
        add(c, p, -1, -2, 16);
        refreshMovieLibrary();
        return wrap;
    }

    private LinearLayout buildCard() {
        LinearLayout wrap = vbox();
        wrap.setPadding(dp(16), topInset() + dp(8), dp(16), dp(12));
        LinearLayout c = vbox();
        wrap.addView(scroll(c), new LinearLayout.LayoutParams(-1, -1));

        LinearLayout head = hbox();
        TextView title = tv("票根工坊", 30, Typeface.NORMAL);
        title.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = btn("刷新", false);
        head.addView(refresh, new LinearLayout.LayoutParams(dp(86), dp(42)));
        c.addView(head);
        c.addView(small("三套模板：电影票根、片尾回执、观影明信片。选好模板再保存到档案馆。"));

        LinearLayout p = panel();
        p.setPadding(dp(14), dp(14), dp(14), dp(14));

        cardPreview = tv("等待生成。", 14, Typeface.NORMAL);
        cardPreview.setTextColor(ink());
        cardPreview.setPadding(dp(18), dp(18), dp(18), dp(18));
        cardPreview.setBackground(ticketBg());
        add(p, cardPreview, -1, -2, 0);

        TextView formTitle = tv("票根内容", 18, Typeface.BOLD);
        add(p, formTitle, -1, -2, 16);
        add(p, small("生成时会把观影邀请卡、台词、感想和时间轴笔记一起放进模板。"), -1, -2, 4);

        LinearLayout templateRow = hbox();
        Button tplTicket = btn("电影票根", cardTemplate.equals("ticket"));
        Button tplReceipt = btn("片尾回执", cardTemplate.equals("receipt"));
        Button tplPostcard = btn("观影明信片", cardTemplate.equals("postcard"));
        templateRow.addView(tplTicket, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams trp1 = new LinearLayout.LayoutParams(0, dp(44), 1); trp1.setMargins(dp(8),0,0,0);
        templateRow.addView(tplReceipt, trp1);
        LinearLayout.LayoutParams trp2 = new LinearLayout.LayoutParams(0, dp(44), 1); trp2.setMargins(dp(8),0,0,0);
        templateRow.addView(tplPostcard, trp2);
        add(p, templateRow, -1, 44, 12);
        tplTicket.setOnClickListener(v -> setCardTemplate("ticket"));
        tplReceipt.setOnClickListener(v -> setCardTemplate("receipt"));
        tplPostcard.setOnClickListener(v -> setCardTemplate("postcard"));

        quoteInput = input("观影人A最喜欢的台词", "");
        linQuoteInput = input(aiName() + "摘录的台词", "");
        cardNoteInput = input("我的观后感", "");
        linNoteInput = input(aiName() + "的观影札记", "");
        cardNoteInput.setSingleLine(false);
        linNoteInput.setSingleLine(false);
        add(p, quoteInput, -1, 48, 12);
        add(p, linQuoteInput, -1, 48, 10);
        add(p, cardNoteInput, -1, 88, 10);
        add(p, linNoteInput, -1, 88, 10);

        LinearLayout row1 = hbox();
        Button make = btn("生成小卡片", true);
        Button saveRoom = btn("保存到房间", false);
        row1.addView(make, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(46), 1); slp.setMargins(dp(8),0,0,0);
        row1.addView(saveRoom, slp);
        add(p, row1, -1, 46, 12);

        LinearLayout row2 = hbox();
        Button collect = btn("存入档案馆", false);
        Button copy = btn("复制文案", false);
        row2.addView(collect, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(46), 1); clp.setMargins(dp(8),0,0,0);
        row2.addView(copy, clp);
        add(p, row2, -1, 46, 8);
        add(c, p, -1, -2, 16);

        refresh.setOnClickListener(v -> { if (roomId.length() > 0) { fetchRoom(); toast("正在刷新小卡片"); } else toast("先进入房间"); });
        make.setOnClickListener(v -> showTicketDialog());
        saveRoom.setOnClickListener(v -> saveCardToRoom());
        collect.setOnClickListener(v -> addFavorite(currentTicketText()));
        copy.setOnClickListener(v -> copyText(cardPreview.getText().toString()));

        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { renderCard(); }
            public void afterTextChanged(Editable e) {}
        };
        quoteInput.addTextChangedListener(watcher);
        linQuoteInput.addTextChangedListener(watcher);
        cardNoteInput.addTextChangedListener(watcher);
        linNoteInput.addTextChangedListener(watcher);
        return wrap;
    }

    private LinearLayout buildFavorites() {
        LinearLayout wrap = vbox();
        wrap.setPadding(dp(16), topInset() + dp(8), dp(16), dp(12));
        LinearLayout c = vbox();
        wrap.addView(scroll(c), new LinearLayout.LayoutParams(-1, -1));
        TextView title = tv("档案馆", 30, Typeface.BOLD);
        c.addView(title);
        c.addView(small("这里会收着每一次观影留下的票根、回执和明信片。"));
        LinearLayout p = panel();
        favoritesList = tv("档案馆还空着。看完一场电影，就把票根存进来。", 14, Typeface.NORMAL);
        favoritesList.setTextColor(ink());
        favoritesList.setPadding(dp(16), dp(16), dp(16), dp(16));
        favoritesList.setBackground(round(cardSoft(), 22));
        add(p, favoritesList, -1, -2, 0);
        Button clear = btn("清空档案馆", false);
        add(p, clear, -1, 44, 12);
        clear.setOnClickListener(v -> {
            getSharedPreferences("cineisle", 0).edit().remove("favorites").apply();
            refreshFavorites();
            toast("已清空档案馆");
        });
        add(c, p, -1, -2, 16);
        return wrap;
    }


    private void loadMovieLibrary() {
        movieLibrary.clear();
        String raw = getSharedPreferences("cineisle", 0).getString("movieLibrary", "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i=0; i<arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String uri = o.optString("uri", "");
                if (uri.length() == 0) continue;
                movieLibrary.add(new MovieItem(
                        uri,
                        o.optString("title", "本地影片"),
                        o.optString("fileName", ""),
                        o.optLong("addedAt", System.currentTimeMillis()),
                        o.optInt("lastPositionMs", 0)
                ));
            }
        } catch(Exception ignored) {}
    }

    private void saveMovieLibrary() {
        try {
            JSONArray arr = new JSONArray();
            for (MovieItem item: movieLibrary) {
                JSONObject o = new JSONObject();
                o.put("uri", item.uri);
                o.put("title", item.title);
                o.put("fileName", item.fileName);
                o.put("addedAt", item.addedAt);
                o.put("lastPositionMs", item.lastPositionMs);
                arr.put(o);
            }
            getSharedPreferences("cineisle", 0).edit().putString("movieLibrary", arr.toString()).apply();
        } catch(Exception ignored) {}
    }

    private void rememberMovieInLibrary(Uri uri, String title, String displayName) {
        if (uri == null) return;
        String uriText = uri.toString();
        MovieItem found = null;
        for (MovieItem item: movieLibrary) {
            if (item.uri.equals(uriText)) { found = item; break; }
        }
        if (found != null) {
            found.title = title;
            found.fileName = displayName;
            found.addedAt = System.currentTimeMillis();
            movieLibrary.remove(found);
            movieLibrary.add(0, found);
        } else {
            movieLibrary.add(0, new MovieItem(uriText, title, displayName, System.currentTimeMillis(), 0));
        }
        while (movieLibrary.size() > 30) movieLibrary.remove(movieLibrary.size() - 1);
        currentMovieUri = uriText;
        saveMovieLibrary();
        savePrefs();
        refreshMovieLibrary();
    }

    private void updateCurrentMovieProgress() {
        if (currentMovieUri == null || currentMovieUri.length() == 0 || lastStablePositionMs <= 1000) return;
        for (MovieItem item: movieLibrary) {
            if (item.uri.equals(currentMovieUri)) {
                item.lastPositionMs = lastStablePositionMs;
                saveMovieLibrary();
                break;
            }
        }
    }

    private void refreshMovieLibrary() {
        if (movieLibraryList == null) return;
        if (movieLibrary.size() == 0) {
            movieLibraryList.setText("影厅还空着。导入一部影片后会自动出现在这里。");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<movieLibrary.size(); i++) {
            MovieItem item = movieLibrary.get(i);
            if (i > 0) sb.append("\n");
            sb.append(i+1).append(". ").append(item.title);
            if (item.lastPositionMs > 1000) sb.append(" · 上次 ").append(formatTime(item.lastPositionMs/1000));
        }
        movieLibraryList.setText(sb.toString());
    }

    private void openMovieFromLibrary(MovieItem item) {
        if (item == null || item.uri.length() == 0 || video == null) return;
        try {
            Uri uri = Uri.parse(item.uri);
            currentMovieUri = item.uri;
            fileName = item.fileName.length() > 0 ? item.fileName : getName(uri);
            movieTitle = item.title.length() > 0 ? item.title : fileName.replaceFirst("\\.[^.]+$", "");
            lastStablePositionMs = Math.max(0, item.lastPositionMs);
            lastStablePositionAt = System.currentTimeMillis();
            video.setVideoURI(uri);
            View e = videoFrame == null ? null : videoFrame.findViewWithTag("video_overlay");
            if (e != null) e.setVisibility(View.GONE);
            if (roomTitle != null) roomTitle.setText((roomId.length() > 0 ? "正在放映 · " : "正在预览 · ") + movieTitle);
            showPage("room");
            savePrefs();
            updateHeroTexts();
            toast("已从影厅打开：" + movieTitle);
            handler.postDelayed(() -> {
                try { if (video != null && lastStablePositionMs > 1500) video.seekTo(lastStablePositionMs); } catch(Exception ignored) {}
            }, 500);
            sendMovieInfo();
            sendCinemaContext(true);
        } catch(Exception e) {
            toast("打开失败：可能是系统收回了文件权限，请重新导入影片");
        }
    }

    private GradientDrawable ticketBg() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, cardCardColors());
        g.setCornerRadius(dp(28));
        g.setStroke(dp(1), theme.equals("cream") || theme.equals("matcha") ? color("#D9E4D6") : color("#33FFFFFF"));
        return g;
    }

    private String safeText(EditText e) {
        return e == null ? "" : e.getText().toString().trim();
    }

    private String aiName() {
        String v = assistantName == null ? "" : assistantName.trim();
        return v.length() > 0 ? v : "观影助手";
    }

    private String aiPeekText() {
        return "给" + aiName() + "看一眼";
    }

    private String currentTicketText() {
        collectInvitationSilently();
        String title = movieTitle.length() > 0 ? movieTitle : "今晚的影片";
        String room = roomId.length() > 0 ? roomId : "------";
        String partner = invitePartner.length() > 0 ? invitePartner : "观影人 A × 观影人 B";
        String mood = inviteMood.length() > 0 ? inviteMood : themeLabel();
        String invite = inviteNote.length() > 0 ? inviteNote : "今晚一起登岛看一场电影。";
        String zq = safeText(quoteInput);
        String lq = safeText(linQuoteInput);
        String zn = safeText(cardNoteInput);
        String ln = safeText(linNoteInput);
        if (zq.length() == 0) zq = remoteCard != null ? remoteCard.optString("quote", "这一幕被我们一起看见了。") : "这一幕被我们一起看见了。";
        if (lq.length() == 0) lq = remoteCard != null ? remoteCard.optString("linQuote", "爱是真的，留下却不一定是爱的唯一形式。") : "爱是真的，留下却不一定是爱的唯一形式。";
        if (zn.length() == 0) zn = remoteCard != null ? remoteCard.optString("zhiNote", "一起看电影这件事，本身就像把普通晚上藏进了一张小票根。") : "一起看电影这件事，本身就像把普通晚上藏进了一张小票根。";
        if (ln.length() == 0) ln = remoteCard != null ? remoteCard.optString("note", "这部电影把陪伴、理解、占有、边界和告别都放进一段温柔又遗憾的关系里。") : "这部电影把陪伴、理解、占有、边界和告别都放进一段温柔又遗憾的关系里。";
        String notes = (noteLog == null || noteLog.getText().length()==0) ? "还没有添加时间轴笔记。" : noteLog.getText().toString();
        if (cardTemplate.equals("receipt")) {
            return "CineIsle · 片尾回执\n" +
                    "━━━━━━━━━━━━━━━━\n" +
                    "影片｜" + title + "\n" +
                    "观影人｜" + partner + "\n" +
                    "氛围｜" + mood + "\n" +
                    "房间｜" + room + "\n\n" +
                    "开场备注\n" + invite + "\n\n" +
                    "片尾留下的一句话\n「" + zq + "」\n\n" +
                    "双人回执\n" + zn + "\n\n" + ln + "\n\n" +
                    "— after the credits · CineIsle";
        }
        if (cardTemplate.equals("postcard")) {
            return "CineIsle · 观影明信片\n" +
                    "━━━━━━━━━━━━━━━━\n" +
                    "寄给｜" + partner + "\n" +
                    "来自｜" + title + "\n" +
                    "邮戳｜" + mood + " · " + room + "\n\n" +
                    "今夜摘句\n「" + zq + "」\n「" + lq + "」\n\n" +
                    "明信片正文\n" + zn + "\n" + ln + "\n\n" +
                    "— from our private island";
        }
        return "CineIsle · Movie Ticket\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "片名｜" + title + "\n" +
                "房间｜" + room + "\n" +
                "观影人｜" + partner + "\n" +
                "氛围｜" + mood + "\n" +
                "主题｜" + themeLabel() + "\n\n" +
                "邀请卡\n" + invite + "\n\n" +
                "我的高光台词\n「" + zq + "」\n\n" +
                aiName() + "摘录的台词\n「" + lq + "」\n\n" +
                "我的观后感\n" + zn + "\n\n" +
                aiName() + "的观影札记\n" + ln + "\n\n" +
                "时间轴笔记\n" + notes + "\n\n" +
                "— watch together · CineIsle";
}

    private String themeLabel() {
        if (theme.equals("cream")) return "奶油白";
        if (theme.equals("galaxy")) return "星河紫";
        if (theme.equals("matcha")) return "雾岛绿";
        if (theme.equals("film")) return "胶片黑";
        if (theme.equals("dusk")) return "暮光紫";
        return "夜航蓝";
    }

    private LinearLayout ticketView(String text) {
        LinearLayout box = vbox();
        box.setPadding(dp(22), dp(22), dp(22), dp(22));
        box.setBackground(ticketBg());
        TextView tag = small(cardTemplate.equals("receipt") ? "CineIsle · 片尾回执" : cardTemplate.equals("postcard") ? "CineIsle · 观影明信片" : "CineIsle · 纪念票根");
        tag.setTextColor(muted());
        box.addView(tag);
        TextView big = tv(cardTemplate.equals("receipt") ? "After Credits" : cardTemplate.equals("postcard") ? "Postcard" : "Movie Ticket", 25, Typeface.BOLD);
        big.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC));
        add(box, big, -1, -2, 6);
        TextView content = tv(text, 14, Typeface.NORMAL);
        content.setTextColor(ink());
        content.setLineSpacing(dp(3), 1.08f);
        add(box, content, -1, -2, 12);
        TextView foot = small((invitePartner.length() > 0 ? invitePartner : "观影人 A × 观影人 B") + " · watch together");
        foot.setGravity(Gravity.CENTER);
        add(box, foot, -1, -2, 12);
        return box;
    }

    private void showTicketDialog() {
        renderCard();
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout outer = vbox();
        outer.setPadding(dp(16), dp(16), dp(16), dp(16));
        outer.setBackground(round(card(), 30));
        LinearLayout head = hbox();
        head.addView(tv("生成观影卡片", 22, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = chip("关闭");
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close);
        outer.addView(head);
        outer.addView(small("按当前模板生成，可保存成图片，也可以存入档案馆。"));
        LinearLayout ticket = ticketView(currentTicketText());
        add(outer, ticket, -1, -2, 14);
        LinearLayout row = hbox();
        Button save = btn("保存图片", true);
        Button fav = btn("存档", false);
        row.addView(save, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, dp(46), 1); flp.setMargins(dp(8),0,0,0);
        row.addView(fav, flp);
        add(outer, row, -1, 46, 12);
        save.setOnClickListener(v -> saveTicketImage(ticket));
        fav.setOnClickListener(v -> addFavorite(currentTicketText()));
        dialog.setContentView(scroll(outer));
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void saveTicketImage(View view) {
        try {
            Bitmap bm = bitmapFromView(view);
            String file = "cineisle_ticket_" + System.currentTimeMillis() + ".png";
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file);
                values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CineIsle");
                Uri uri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("无法创建图片文件");
                try(OutputStream os = getContentResolver().openOutputStream(uri)) { bm.compress(Bitmap.CompressFormat.PNG, 100, os); }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CineIsle");
                dir.mkdirs();
                File out = new File(dir, file);
                try(FileOutputStream fos = new FileOutputStream(out)) { bm.compress(Bitmap.CompressFormat.PNG, 100, fos); }
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(out)));
            }
            toast("小卡片已保存到相册");
        } catch(Exception e) { toast("保存失败：" + e.getMessage()); }
    }

    private Bitmap bitmapFromView(View v) {
        int width = getResources().getDisplayMetrics().widthPixels - dp(48);
        int wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        v.measure(wSpec, hSpec);
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
        Bitmap bm = Bitmap.createBitmap(v.getMeasuredWidth(), Math.max(1, v.getMeasuredHeight()), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        v.draw(c);
        return bm;
    }

    private void addFavorite(String text) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("cineisle", 0);
            JSONArray arr = new JSONArray(sp.getString("favorites", "[]"));
            JSONObject item = new JSONObject();
            item.put("id", System.currentTimeMillis()+"");
            item.put("title", movieTitle.length() > 0 ? movieTitle : "今晚的影片");
            item.put("room", roomId);
            item.put("text", text);
            item.put("at", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(new java.util.Date()));
            arr.put(item);
            sp.edit().putString("favorites", arr.toString()).apply();
            refreshFavorites();
            toast("已存入档案馆");
        } catch(Exception e) { toast("收藏失败"); }
    }

    private void refreshFavorites() {
        if (favoritesList == null) return;
        try {
            JSONArray arr = new JSONArray(getSharedPreferences("cineisle", 0).getString("favorites", "[]"));
            if (arr.length() == 0) { favoritesList.setText("档案馆还空着。看完一场电影，就把票根存进来。"); return; }
            StringBuilder sb = new StringBuilder();
            for (int i = arr.length()-1; i >= 0; i--) {
                JSONObject it = arr.getJSONObject(i);
                sb.append("🎞 ").append(it.optString("title", "观影档案")).append("\n");
                sb.append(it.optString("at", "")).append(" · 房间 ").append(it.optString("room", "------")).append("\n");
                sb.append(it.optString("text", "")).append("\n\n");
                if (i > 0) sb.append("————————————\n\n");
            }
            favoritesList.setText(sb.toString());
        } catch(Exception e) { favoritesList.setText("收藏读取失败。"); }
    }

    private void saveCardToRoom() {
        if (roomId.length() == 0) { toast("先进入房间"); return; }
        if (serverUrl.length() == 0) { toast("先配置后端地址"); return; }
        final String zq = safeText(quoteInput);
        final String lq = safeText(linQuoteInput);
        final String zn = safeText(cardNoteInput);
        final String ln = safeText(linNoteInput);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("title", movieTitle.length() > 0 ? movieTitle : "CineIsle放映记录");
                body.put("rating", 4.5);
                body.put("template", cardTemplate);
                body.put("partner", invitePartner);
                body.put("assistantName", aiName());
                body.put("mood", inviteMood);
                body.put("inviteNote", inviteNote);
                body.put("quote", zq.length() > 0 ? zq : (lq.length() > 0 ? lq : "这一幕被我们一起看见了。"));
                body.put("note", ln.length() > 0 ? ln : zn);
                body.put("zhiQuote", zq);
                body.put("linQuote", lq);
                body.put("zhiNote", zn);
                body.put("linNote", ln);
                postJson("/api/rooms/" + roomId + "/card", body, true);
                runOnUiThread(() -> toast("已保存到房间小卡片"));
                fetchRoom();
            } catch(Exception e) { runOnUiThread(() -> toast("保存失败：" + e.getMessage())); }
        }).start();
    }

    private int[] cardCardColors() {
        if (theme.equals("cream")) return new int[]{color("#FFF6FA"), color("#FBFAFF")};
        if (theme.equals("galaxy")) return new int[]{color("#2A214B"), color("#18122E")};
        if (theme.equals("matcha")) return new int[]{color("#F8FFF5"), color("#EEF7EB")};
        if (theme.equals("film")) return new int[]{color("#2A2419"), color("#17130E")};
        if (theme.equals("dusk")) return new int[]{color("#35233F"), color("#1B1530")};
        return new int[]{color("#1A223B"), color("#11172A")};
    }

    private void rebuild() {
        root.removeAllViews();
        buildUI();
        updateHeroTexts();
        showPage(roomId.length() > 0 ? "room" : "home");
        renderCard();
    }

    private void updateHeroTexts() {
        if (homeStatus != null) homeStatus.setText(roomId.length() > 0 ? "当前房间：" + roomId : "当前还没有加入房间");
        if (heroBadge != null) heroBadge.setText("CineIsle · " + themeLabel());
        if (inviteSummary != null) inviteSummary.setText(invitationText());
        if (importState != null) importState.setText(fileName.length() > 0 ? "本机已导入 · " + movieTitle : "本地影片未导入 · 等待准备");
    }

    private void showPage(String page) {
        currentPage = page;
        pageHome.setVisibility(page.equals("home") ? View.VISIBLE : View.GONE);
        pageRoom.setVisibility(page.equals("room") ? View.VISIBLE : View.GONE);
        pageHall.setVisibility(page.equals("hall") ? View.VISIBLE : View.GONE);
        pageCard.setVisibility(page.equals("card") ? View.VISIBLE : View.GONE);
        pageFavorites.setVisibility(page.equals("favorites") ? View.VISIBLE : View.GONE);
        updateNav(page);
        if (page.equals("room") && roomId.length() > 0) startPolling();
        if (page.equals("card") && roomId.length() > 0) fetchRoom();
        if (page.equals("hall")) refreshMovieLibrary();
        if (page.equals("favorites")) refreshFavorites();
    }

    private void updateNav(String page) {
        if (navHome == null || navRoom == null || navHall == null || navCard == null || navFavorites == null) return;
        styleNav(navHome, page.equals("home"));
        styleNav(navRoom, page.equals("room"));
        styleNav(navHall, page.equals("hall"));
        styleNav(navCard, page.equals("card"));
        styleNav(navFavorites, page.equals("favorites"));
    }

    private void styleNav(Button b, boolean selected) {
        b.setTextColor(selected ? Color.WHITE : ink());
        b.setBackground(selected ? grad(new int[]{accent(), accent2()}, 20) : round(cardSoft(), 20));
        b.setElevation(selected ? dp(3) : 0);
    }

    private void collectSettings() {
        serverUrl = normalizeServer(serverUrl);
        if (name.length() == 0) name = "观影人";
        if (assistantName == null || assistantName.trim().length() == 0) assistantName = "观影助手";
        savePrefs();
    }

    private void collectInvitation() {
        collectInvitationSilently();
        savePrefs();
        updateHeroTexts();
    }

    private void collectInvitationSilently() {
        if (inviteMovieInput != null && safeText(inviteMovieInput).length() > 0) movieTitle = safeText(inviteMovieInput);
        if (invitePartnerInput != null && safeText(invitePartnerInput).length() > 0) invitePartner = safeText(invitePartnerInput);
        if (inviteMoodInput != null && safeText(inviteMoodInput).length() > 0) inviteMood = safeText(inviteMoodInput);
        if (inviteNoteInput != null && safeText(inviteNoteInput).length() > 0) inviteNote = safeText(inviteNoteInput);
    }

    private String invitationText() {
        String title = movieTitle.length() > 0 ? movieTitle : "等待写入片名";
        String partner = invitePartner.length() > 0 ? invitePartner : "观影人 A × 观影人 B";
        String mood = inviteMood.length() > 0 ? inviteMood : themeLabel();
        String note = inviteNote.length() > 0 ? inviteNote : "今晚一起登岛看一场电影。";
        return "影片｜" + title + "\n"
                + "观影人｜" + partner + "\n"
                + "氛围｜" + mood + "\n"
                + "开场备注｜" + note;
    }

    private void setCardTemplate(String tpl) {
        cardTemplate = tpl;
        savePrefs();
        toast(tpl.equals("receipt") ? "已切换片尾回执" : tpl.equals("postcard") ? "已切换观影明信片" : "已切换电影票根");
        rebuild();
        showPage("card");
    }

    private String normalizeServer(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.endsWith("/")) s = s.substring(0, s.length()-1);
        return s;
    }

    private void createRoom() {
        if (serverUrl.length() == 0) { toast("先在设置里填写后端地址"); openSettingsSheet(); return; }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("assistantName", aiName());
                body.put("theme", theme);
                if (movieTitle.length() > 0) body.put("title", movieTitle);
                body.put("partner", invitePartner);
                body.put("mood", inviteMood);
                body.put("inviteNote", inviteNote);
                JSONObject res = postJson("/api/rooms", body, false);
                JSONObject room = res.getJSONObject("room");
                runOnUiThread(() -> joinRoom(room.optString("id")));
            } catch (Exception e) { runOnUiThread(() -> toast("创建失败：" + e.getMessage())); }
        }).start();
    }

    private void joinRoom(String id) {
        roomId = id.trim().toUpperCase();
        if (roomId.length() == 0) { toast("先输入房间号"); return; }
        roomCodeView.setText("房间号 · " + roomId);
        roomTitle.setText(movieTitle.length() > 0 ? "正在放映 · " + movieTitle : "映屿 CineIsle");
        updateHeroTexts();
        showPage("room");
        startPolling();
        updateServicePrefs();
        fetchRoom();
        sendCinemaContext(true);
        toast("已进入房间 " + roomId);
    }

    private void startPolling() {
        if (!polling) {
            polling = true;
            handler.post(poller);
        }
    }


    private String contextStatusText() {
        String sub = subtitleCues.size() > 0 ? ("已载入 " + subtitleCues.size() + " 条字幕") : "未导入字幕";
        String screen = autoScreenshot ? "自动低频截图：ON（需无障碍服务已开启）" : "自动低频截图：OFF";
        String cur = lastContextSubtitle.length() > 0 ? "\n当前字幕：" + lastContextSubtitle : "";
        String shotStatus = getSharedPreferences("cineisle", 0).getString("lastScreenshotStatus", "");
        String shot = shotStatus.length() > 0 ? "\n截图状态：" + shotStatus : "";
        return sub + " · " + (contextAutoSync ? "字幕感知：ON" : "字幕感知：OFF") + "\n" + screen + cur + shot;
    }

    private void updateServicePrefs() {
        android.content.SharedPreferences.Editor e = getSharedPreferences("cineisle", 0).edit();
        e.putString("serverUrl", serverUrl);
        e.putString("token", token);
        e.putString("roomId", roomId);
        e.putString("name", name);
        e.putString("assistantName", aiName());
        e.putBoolean("autoScreenshot", autoScreenshot);
        e.putBoolean("contextAutoSync", contextAutoSync);
        e.putBoolean("contextPaused", video == null || !video.isPlaying());
        e.putLong("contextUpdatedAt", System.currentTimeMillis());
        e.apply();
    }

    private void requestLocalScreenshotNow() {
        if (serverUrl.length() == 0) { toast("先在设置里填写后端地址"); return; }
        if (roomId.length() == 0) { toast("先创建或进入房间，截图才知道上传到哪里"); return; }
        autoScreenshot = true;
        savePrefs();
        long requestId = System.currentTimeMillis();
        android.content.SharedPreferences.Editor e = getSharedPreferences("cineisle", 0).edit();
        e.putBoolean("autoScreenshot", true);
        e.putLong("screenshotRequestId", requestId);
        e.putString("lastScreenshotStatus", "已请求" + aiName() + "看一眼，等待无障碍服务上传");
        e.apply();
        updateServicePrefs();
        if (contextState != null) contextState.setText(contextStatusText());
        toast("已请求" + aiName() + "看一眼，请停留在想给它看的画面");
    }


    private void togglePlay() {
        if (video == null) return;
        try {
            if (fileName.length() == 0 && currentMovieUri.length() == 0) {
                toast("先导入影片");
                return;
            }
            lastLocalPlaybackActionAt = System.currentTimeMillis();
            applyingRemote = false;
            if (video.isPlaying()) {
                video.pause();
                toast("已暂停");
            } else {
                video.start();
                toast("开始播放");
            }
            updateViewingContext(false);
            handler.postDelayed(() -> sendPlayback(true), 250);
        } catch(Exception e) {
            toast("播放控制失败：" + e.getClass().getSimpleName());
        }
    }

    private void rememberPlaybackPosition() {
        try {
            if (video == null || video.getDuration() <= 0) return;
            int pos = video.getCurrentPosition();
            if (pos > 1000) {
                lastStablePositionMs = pos;
                lastStablePositionAt = System.currentTimeMillis();
                getSharedPreferences("cineisle", 0).edit()
                        .putInt("lastStablePositionMs", lastStablePositionMs)
                        .putLong("lastStablePositionAt", lastStablePositionAt)
                        .apply();
                updateCurrentMovieProgress();
            }
        } catch(Exception ignored) {}
    }

    private int outboundPositionMs() {
        try {
            if (video == null) return Math.max(0, lastStablePositionMs);
            int pos = video.getCurrentPosition();
            long now = System.currentTimeMillis();
            // 横屏退出/竖屏恢复的几秒内，VideoView 偶尔会短暂回报 0；不要让这个 0 覆盖后端。
            if (pos < 1000 && lastStablePositionMs > 5000 && now - lastStablePositionAt < 15000) {
                return lastStablePositionMs;
            }
            if (pos > 1000) {
                lastStablePositionMs = pos;
                lastStablePositionAt = now;
            }
            return Math.max(0, pos);
        } catch(Exception e) {
            return Math.max(0, lastStablePositionMs);
        }
    }

    private void forceRestoreAfterFullscreen(final int posMs, final boolean shouldPlay) {
        if (video == null || posMs <= 1000) return;
        fullscreenExitRestoreMs = posMs;
        fullscreenExitWasPlaying = shouldPlay;
        fullscreenExitAt = System.currentTimeMillis();
        lastStablePositionMs = posMs;
        lastStablePositionAt = fullscreenExitAt;
        savePrefs();
        for (int delay: new int[]{120, 450, 1000, 1800}) {
            handler.postDelayed(() -> {
                try {
                    if (video == null || fullscreenExitRestoreMs <= 1000) return;
                    if (video.getCurrentPosition() < 1000 || Math.abs(video.getCurrentPosition() - fullscreenExitRestoreMs) > 1500) {
                        video.seekTo(fullscreenExitRestoreMs);
                    }
                    if (fullscreenExitWasPlaying && !video.isPlaying()) video.start();
                    updateViewingContext(true);
                } catch(Exception ignored) {}
            }, delay);
        }
    }

    private void pickSubtitleFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "*/*",
            "text/*",
            "application/x-subrip",
            "application/octet-stream",
            "application/x-ass",
            "application/x-ssa"
        });
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, 1002);
    }

    private void loadSubtitleFile(Uri uri) {
        new Thread(() -> {
            try {
                String fileDisplayName = getName(uri);
                String text = readTextUri(uri);
                ArrayList<SubtitleCue> cues = parseSubtitleText(text);
                String detected = detectSubtitleFormat(fileDisplayName, text);
                final String finalName = fileDisplayName;
                final String finalDetected = detected;
                runOnUiThread(() -> {
                    subtitleCues.clear();
                    subtitleCues.addAll(cues);
                    updateViewingContext(true);
                    if (cues.size() > 0) {
                        toast("字幕已导入：" + cues.size() + " 条（" + finalDetected + "）");
                    } else {
                        toast("字幕导入 0 条：" + subtitleImportHint(finalName, finalDetected));
                    }
                    sendCinemaContext(true);
                });
            } catch(Exception e) {
                runOnUiThread(() -> toast("字幕导入失败：" + e.getMessage()));
            }
        }).start();
    }

    private String readTextUri(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("无法读取文件");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        byte[] data = bos.toByteArray();
        if (data.length == 0) return "";

        ArrayList<String[]> candidates = new ArrayList<>();
        addSubtitleDecodeCandidate(candidates, data, "UTF-8", startsWith(data, 0xef, 0xbb, 0xbf) ? 3 : 0);
        addSubtitleDecodeCandidate(candidates, data, "GB18030", 0);
        addSubtitleDecodeCandidate(candidates, data, "UTF-16LE", startsWith(data, 0xff, 0xfe) ? 2 : 0);
        addSubtitleDecodeCandidate(candidates, data, "UTF-16BE", startsWith(data, 0xfe, 0xff) ? 2 : 0);
        if (candidates.size() == 0) throw new IOException("编码读取失败");
        Collections.sort(candidates, (a,b) -> Integer.compare(Integer.parseInt(b[0]), Integer.parseInt(a[0])));
        return candidates.get(0)[2];
    }

    private boolean startsWith(byte[] data, int... values) {
        if (data == null || data.length < values.length) return false;
        for (int i=0; i<values.length; i++) if ((data[i] & 0xff) != values[i]) return false;
        return true;
    }

    private void addSubtitleDecodeCandidate(ArrayList<String[]> out, byte[] data, String charset, int offset) {
        try {
            if (offset < 0 || offset >= data.length) offset = 0;
            String text = new String(data, offset, data.length - offset, charset);
            text = normalizeSubtitleText(text);
            int score = parseSubtitleText(text).size() * 1000 + countTimelineLines(text) * 20 + countCjk(text) - countReplacement(text) * 30;
            out.add(new String[]{String.valueOf(score), charset, text});
        } catch(Exception ignored) {}
    }

    private String normalizeSubtitleText(String text) {
        if (text == null) return "";
        return text.replace("\ufeff", "")
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace('\u00a0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private int countTimelineLines(String text) {
        int c = 0;
        for (String line: normalizeSubtitleText(text).split("\n")) if (line.contains("-->")) c++;
        return c;
    }
    private int countReplacement(String text) {
        int c = 0;
        for (int i=0; i<text.length(); i++) if (text.charAt(i) == '\ufffd') c++;
        return c;
    }
    private int countCjk(String text) {
        int c = 0;
        for (int i=0; i<text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '\u4e00' && ch <= '\u9fff') c++;
        }
        return c;
    }

    private String detectSubtitleFormat(String name, String text) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".ass") || lower.endsWith(".ssa") || (text != null && text.contains("[Events]") && text.contains("Dialogue:"))) return "ASS/SSA";
        if (lower.endsWith(".vtt") || (text != null && text.trim().startsWith("WEBVTT"))) return "VTT";
        if (lower.endsWith(".srt") || (text != null && text.contains("-->"))) return "SRT";
        return "未知格式";
    }

    private String subtitleImportHint(String name, String detected) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".rar") || lower.endsWith(".zip") || lower.endsWith(".7z")) return "这是压缩包，请先解压出 .srt/.vtt/.ass";
        if ("ASS/SSA".equals(detected)) return "已识别 ASS，但没有 Dialogue 台词，可能文件损坏或不是字幕";
        if ("未知格式".equals(detected)) return "已读到文本但未匹配时间轴，可能不是标准 SRT/VTT/ASS";
        return "匹配到时间轴但正文为空，可能字幕结构不规范、编码异常或隐藏字符干扰";
    }

    private ArrayList<SubtitleCue> parseSubtitleText(String raw) {
        ArrayList<SubtitleCue> cues = new ArrayList<>();
        if (raw == null) return cues;
        raw = normalizeSubtitleText(raw);
        String trimmed = raw.trim();
        if (trimmed.contains("[Events]") && trimmed.contains("Dialogue:")) {
            cues.addAll(parseAssSubtitleText(raw));
        }
        if (cues.size() == 0) cues.addAll(parseSrtVttSubtitleText(raw));
        Collections.sort(cues, (a,b) -> Double.compare(a.start, b.start));
        return cues;
    }

    private ArrayList<SubtitleCue> parseSrtVttSubtitleText(String raw) {
        ArrayList<SubtitleCue> cues = new ArrayList<>();
        if (raw == null) return cues;
        String text = normalizeSubtitleText(raw).replaceFirst("(?i)^WEBVTT[^\n]*(\n|$)", "");
        String[] lines = text.split("\n");
        for (int i=0; i<lines.length; i++) {
            String timeLine = lines[i] == null ? "" : lines[i].trim();
            if (!timeLine.contains("-->")) continue;
            String[] parts = timeLine.split("-->", 2);
            if (parts.length < 2) continue;
            double start = parseSubtitleTime(parts[0]);
            String right = parts[1].trim().split("\\s+")[0];
            double end = parseSubtitleTime(right);
            if (!(end > start)) continue;
            StringBuilder sb = new StringBuilder();
            for (int j=i+1; j<lines.length; j++) {
                String line = lines[j] == null ? "" : lines[j].trim();
                String next = (j + 1 < lines.length && lines[j+1] != null) ? lines[j+1] : "";
                if (line.contains("-->")) break;
                if (line.length() == 0) {
                    if (sb.length() > 0) break;
                    continue;
                }
                if (line.matches("^\\d+$") && next.contains("-->")) break;
                if (line.matches("(?i)^(NOTE|STYLE|REGION)(\\s|$).*")) continue;
                line = cleanSubtitleLine(line);
                if (line.length() == 0) continue;
                if (sb.length() > 0) sb.append(" / ");
                sb.append(line);
            }
            if (sb.length() > 0) cues.add(new SubtitleCue(start, end, sb.toString()));
        }
        return cues;
    }

    private ArrayList<SubtitleCue> parseAssSubtitleText(String raw) {
        ArrayList<SubtitleCue> cues = new ArrayList<>();
        if (raw == null) return cues;
        String text = raw.replace("\r", "");
        String[] lines = text.split("\n");
        int startIdx = 1, endIdx = 2, textIdx = -1, fieldCount = 10;
        boolean inEvents = false;
        for (String originalLine: lines) {
            String line = originalLine.trim();
            if (line.length() == 0) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                inEvents = line.equalsIgnoreCase("[Events]");
                continue;
            }
            if (!inEvents) continue;
            if (line.toLowerCase(Locale.US).startsWith("format:")) {
                String[] fields = line.substring(line.indexOf(':') + 1).split(",");
                fieldCount = fields.length;
                for (int i=0; i<fields.length; i++) {
                    String f = fields[i].trim().toLowerCase(Locale.US);
                    if (f.equals("start")) startIdx = i;
                    else if (f.equals("end")) endIdx = i;
                    else if (f.equals("text")) textIdx = i;
                }
                continue;
            }
            if (!line.toLowerCase(Locale.US).startsWith("dialogue:")) continue;
            String payload = line.substring(line.indexOf(':') + 1).trim();
            int maxParts = Math.max(fieldCount, 10);
            String[] parts = payload.split(",", maxParts);
            if (parts.length <= Math.max(startIdx, endIdx)) continue;
            if (textIdx < 0) textIdx = Math.min(9, parts.length - 1);
            if (parts.length <= textIdx) continue;
            double start = parseSubtitleTime(parts[startIdx]);
            double end = parseSubtitleTime(parts[endIdx]);
            String lineText = parts[textIdx];
            if (parts.length > fieldCount && textIdx == fieldCount - 1) {
                StringBuilder rest = new StringBuilder(parts[textIdx]);
                for (int i=fieldCount; i<parts.length; i++) rest.append(",").append(parts[i]);
                lineText = rest.toString();
            }
            lineText = cleanSubtitleLine(lineText);
            if (end > start && lineText.length() > 0) cues.add(new SubtitleCue(start, end, lineText));
        }
        return cues;
    }

    private String cleanSubtitleLine(String line) {
        if (line == null) return "";
        String out = line.replace("\\N", " / ").replace("\\n", " / ").replace("\\h", " ");
        out = out.replaceAll("\\{[^}]*\\}", "");
        out = out.replaceAll("<[^>]+>", "");
        out = out.replaceAll("\\s+", " ").trim();
        // 过滤 ASS 矢量绘图残留，例如 m 0 0 b 0 0...，避免混进最近字幕
        if (out.matches("(?i)^[mnlbspc\\s0-9.\\-]+$") && out.matches(".*[0-9].*")) return "";
        return out;
    }

    private double parseSubtitleTime(String t) {
        t = t.trim().replace(',', '.');
        t = t.replaceAll("[^0-9:.]", "");
        String[] parts = t.split(":");
        try {
            if (parts.length == 3) return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Double.parseDouble(parts[2]);
            if (parts.length == 2) return Integer.parseInt(parts[0]) * 60 + Double.parseDouble(parts[1]);
            return Double.parseDouble(parts[0]);
        } catch(Exception e) { return 0; }
    }

    private String subtitleAt(double sec) {
        if (subtitleCues.size() == 0) return "";
        double s = sec + 0.15;
        for (SubtitleCue cue: subtitleCues) {
            if (s >= cue.start && s <= cue.end + 0.6) return cue.text;
            if (cue.start > s + 1.0) break;
        }
        return "";
    }

    private String subtitleForContext(double sec) {
        String cur = subtitleAt(sec);
        if (cur.length() > 0) return cur;
        SubtitleCue latest = null;
        for (SubtitleCue cue: subtitleCues) {
            if (cue.start <= sec + 0.5 && cue.text.length() > 0) latest = cue;
            if (cue.start > sec + 0.5) break;
        }
        if (latest != null && sec - latest.start <= 10.0) return "刚刚：" + latest.text;
        return "";
    }

    private JSONArray recentSubtitleArray(double sec) throws JSONException {
        JSONArray arr = new JSONArray();
        ArrayList<String> tmp = new ArrayList<>();
        for (SubtitleCue cue: subtitleCues) {
            if (cue.start <= sec + 0.5 && cue.text.length() > 0) tmp.add(formatTime((int)cue.start) + " " + cue.text);
            if (cue.start > sec + 0.5) break;
        }
        int from = Math.max(0, tmp.size() - 6);
        for (int i=from; i<tmp.size(); i++) arr.put(tmp.get(i));
        return arr;
    }

    private void updateViewingContext(boolean force) {
        double sec = outboundPositionMs() / 1000.0;
        String cur = subtitleAt(sec);
        String contextCur = subtitleForContext(sec);
        lastContextSubtitle = contextCur;
        if (subtitleOverlay != null) {
            subtitleOverlay.setText(cur);
            subtitleOverlay.setVisibility(cur.length() > 0 ? View.VISIBLE : View.GONE);
        }
        if (contextState != null) contextState.setText(contextStatusText());
        updateServicePrefs();
    }

    private void sendCinemaContext(boolean force) {
        if (!contextAutoSync || roomId.length() == 0 || serverUrl.length() == 0) return;
        int sec = outboundPositionMs() / 1000;
        String cur = subtitleForContext(sec);
        if (!force && sec == lastContextSecond && cur.equals(lastSentContextSubtitle)) return;
        if (!force && sec % 5 != 0 && cur.equals(lastSentContextSubtitle)) return;
        lastContextSecond = sec;
        lastSentContextSubtitle = cur;
        lastContextSubtitle = cur;
        if (contextState != null) contextState.setText(contextStatusText());
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("actor", name);
                body.put("assistantName", aiName());
                body.put("currentTime", outboundPositionMs()/1000.0);
                body.put("duration", video == null ? 0 : Math.max(0, video.getDuration()/1000.0));
                body.put("paused", video == null || !video.isPlaying());
                if (movieTitle.length() > 0) body.put("title", movieTitle);
                if (fileName.length() > 0) body.put("fileName", fileName);
                body.put("currentSubtitle", cur);
                body.put("recentSubtitles", recentSubtitleArray(sec));
                body.put("observedAt", new Date().toString());
                body.put("playbackDebug", playbackDebugPayload());
                postJson("/api/rooms/" + roomId + "/context", body, true);
            } catch(Exception ignored) {}
        }).start();
    }

    private void pickVideo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, 1001);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1002 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch(Exception ignored) {}
            loadSubtitleFile(uri);
            return;
        }
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch(Exception ignored) {}
            fileName = getName(uri);
            movieTitle = fileName.replaceFirst("\\.[^.]+$", "");
            currentMovieUri = uri.toString();
            rememberMovieInLibrary(uri, movieTitle, fileName);
            video.setVideoURI(uri);
            View e = videoFrame.findViewWithTag("video_overlay");
            if (e != null) e.setVisibility(View.GONE);
            video.requestFocus();
            roomTitle.setText((roomId.length() > 0 ? "正在放映 · " : "正在预览 · ") + movieTitle);
            showPage("room");
            toast("影片已导入，本地文件不会上传");
            sendMovieInfo();
            updateHeroTexts();
            renderCard();
        }
    }

    private String getName(Uri uri) {
        String result = "本地影片";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = cursor.getString(idx);
            }
        } catch(Exception ignored) {}
        return result;
    }

    private void sendMovieInfo() {
        updateServicePrefs();
        if (roomId.length() == 0 || serverUrl.length() == 0 || fileName.length() == 0) return;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("actor", name);
                body.put("assistantName", aiName());
                body.put("title", movieTitle);
                body.put("fileName", fileName);
                body.put("duration", video.getDuration() / 1000.0);
                body.put("partner", invitePartner);
                body.put("mood", inviteMood);
                body.put("inviteNote", inviteNote);
                body.put("playbackDebug", playbackDebugPayload());
                postJson("/api/rooms/" + roomId + "/playback", body, true);
            } catch(Exception ignored) {}
        }).start();
    }


    private JSONObject playbackDebugPayload() throws JSONException {
        JSONObject dbg = new JSONObject();
        JSONArray events = new JSONArray();
        JSONObject e = new JSONObject();
        e.put("at", new Date().toString());
        e.put("event", lastPlaybackIssue.length() > 0 ? lastPlaybackIssue : "android-videoview-status");
        e.put("position", video == null ? 0 : video.getCurrentPosition()/1000.0);
        e.put("readyState", 0);
        e.put("networkState", 0);
        e.put("message", "Android VideoView；本地文件播放不走 HTTP Range，远程片源卡顿请检查代理 206/Accept-Ranges");
        events.put(e);
        dbg.put("events", events);
        JSONObject range = new JSONObject();
        range.put("checked", true);
        range.put("ok", true);
        range.put("note", "Android 本地文件/Content URI 播放，不需要 HTTP Range；若使用远程代理片源，服务端仍需支持 206 Partial Content。");
        dbg.put("range", range);
        dbg.put("lastError", lastPlaybackIssue);
        return dbg;
    }

    private void sendPlayback(boolean force) {
        if (roomId.length() == 0 || serverUrl.length() == 0 || applyingRemote) return;
        long nowMs = System.currentTimeMillis();
        if (!force && nowMs - lastPlaybackSyncAt < 2200) return;
        lastPlaybackSyncAt = nowMs;
        rememberPlaybackPosition();
        int pos = outboundPositionMs();
        boolean paused = video == null || !video.isPlaying();
        updateServicePrefs();
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("actor", name);
                body.put("assistantName", aiName());
                body.put("currentTime", pos / 1000.0);
                body.put("duration", Math.max(0, video.getDuration() / 1000.0));
                body.put("paused", paused);
                if (movieTitle.length() > 0) body.put("title", movieTitle);
                if (fileName.length() > 0) body.put("fileName", fileName);
                body.put("partner", invitePartner);
                body.put("mood", inviteMood);
                body.put("inviteNote", inviteNote);
                body.put("playbackDebug", playbackDebugPayload());
                postJson("/api/rooms/" + roomId + "/playback", body, true);
            } catch(Exception ignored) {}
        }).start();
    }

    private void sendMessage(boolean dm) {
        String text = chatInput.getText().toString().trim();
        if (text.length() == 0) return;
        if (sendMessageText(text, dm)) chatInput.setText("");
    }

    private boolean sendMessageText(String text, boolean dm) {
        if (text == null) return false;
        text = text.trim();
        if (text.length() == 0) return false;
        if (roomId.length() == 0) { toast("先进入房间"); return false; }
        final String out = dm ? "弹幕：" + text : text;
        final String pendingId = "local-" + System.currentTimeMillis() + "-" + Math.abs(out.hashCode());
        pendingChats.add(new PendingChat(pendingId, name, out));
        appendChat(name, out + "（发送中…）");
        if (dm && danmakuOn) showDanmaku(text);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("assistantName", aiName());
                body.put("text", out);
                postJson("/api/rooms/" + roomId + "/message", body, true);
                runOnUiThread(() -> {
                    removePendingChat(pendingId);
                    fetchRoom();
                });
            } catch(Exception e) {
                runOnUiThread(() -> {
                    markPendingFailed(pendingId);
                    renderPendingChats();
                    toast("发送失败：" + e.getMessage());
                });
            }
        }).start();
        return true;
    }

    private void sendNote() {
        String text = noteInput.getText().toString().trim();
        if (text.length() == 0) return;
        if (roomId.length() == 0) { toast("先进入房间"); return; }
        noteInput.setText("");
        appendNote(name, text, video.getCurrentPosition()/1000);
        postTimelineNote(text, "note");
    }

    private void sendQuoteLine() {
        String text = noteInput.getText().toString().trim();
        if (text.length() == 0) { toast("先写一句台词或金句"); return; }
        if (roomId.length() == 0) { toast("先进入房间"); return; }
        noteInput.setText("");
        String out = "金句｜" + text;
        appendNote(name, out, video.getCurrentPosition()/1000);
        if (quoteInput != null && safeText(quoteInput).length() == 0) quoteInput.setText(text);
        postTimelineNote(out, "quote");
    }

    private void postTimelineNote(String text, String type) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("assistantName", aiName());
                body.put("text", text);
                body.put("type", type);
                body.put("time", video.getCurrentPosition()/1000.0);
                postJson("/api/rooms/" + roomId + "/note", body, true);
            } catch(Exception e) { runOnUiThread(() -> toast("时间轴发送失败")); }
        }).start();
    }

    private void fetchRoom() {
        if (serverUrl.length() == 0 || roomId.length() == 0) return;
        new Thread(() -> {
            try {
                JSONObject res = getJson("/api/rooms/" + roomId);
                JSONObject room = res.getJSONObject("room");
                runOnUiThread(() -> applyRoom(room));
            } catch(Exception ignored) {}
        }).start();
    }


    private boolean applyRemotePlaybackCommand(JSONObject ctx) {
        if (ctx == null) return false;
        JSONObject cmd = ctx.optJSONObject("playbackCommand");
        if (cmd == null) return false;
        String id = cmd.optString("id", "");
        if (id.length() == 0 || id.equals(lastAppliedPlaybackCommandId)) return false;

        String action = cmd.optString("action", "").toLowerCase(java.util.Locale.US);
        boolean hasPaused = cmd.has("paused");
        boolean paused = cmd.optBoolean("paused", action.equals("pause"));
        double sec = cmd.has("currentTime") ? cmd.optDouble("currentTime", -1) : -1;
        int targetMs = sec >= 0 ? (int)Math.max(0, sec * 1000) : -1;
        String actor = cmd.optString("actor", aiName());

        if (video == null || video.getDuration() <= 0) {
            syncState.setText("远程播放命令已收到，但本机还没导入/准备好影片 · " + actor);
            lastPlaybackIssue = "remote command waiting: video not ready, action=" + action;
            return false;
        }

        try {
            applyingRemote = true;
            lastAppliedPlaybackCommandId = id;
            lastLocalPlaybackActionAt = System.currentTimeMillis();
            if (targetMs >= 0) safeSeekTo(video, targetMs);
            if (action.equals("play") || (hasPaused && !paused)) {
                if (!video.isPlaying()) video.start();
                syncState.setText("已执行远程播放 · " + actor);
            } else if (action.equals("pause") || (hasPaused && paused)) {
                if (video.isPlaying()) video.pause();
                syncState.setText("已执行远程暂停 · " + actor);
            } else if (action.equals("seek")) {
                syncState.setText("已执行远程跳转 · " + actor);
            }
            handler.postDelayed(() -> applyingRemote = false, 900);
            handler.postDelayed(() -> sendPlayback(true), 350);
            return true;
        } catch (Exception e) {
            applyingRemote = false;
            lastPlaybackIssue = "remote command failed: " + e.getClass().getSimpleName() + " " + e.getMessage();
            syncState.setText("远程播放命令失败：" + e.getClass().getSimpleName());
            sendPlayback(false);
            return false;
        }
    }

    private void applyRoom(JSONObject room) {
        try {
            roomCodeView.setText("房间号 " + room.optString("id", roomId));
            if (room.optString("title").length() > 0 && !room.optString("title").equals("未命名影片")) {
                movieTitle = room.optString("title", movieTitle);
                roomTitle.setText("正在放映 · " + room.optString("title"));
            }
            if (room.optString("partner").length() > 0) invitePartner = room.optString("partner", invitePartner);
            if (room.optString("assistantName").length() > 0) {
                assistantName = room.optString("assistantName", aiName());
                savePrefs();
                updateServicePrefs();
            }
            if (room.optString("mood").length() > 0) inviteMood = room.optString("mood", inviteMood);
            if (room.optString("inviteNote").length() > 0) inviteNote = room.optString("inviteNote", inviteNote);
            if (inviteSummary != null) inviteSummary.setText(invitationText());
            if (importState != null) importState.setText((fileName.length() > 0 ? "本机已导入" : "本机未导入") + " · 片长 " + formatTime((int)room.optDouble("duration",0)));
            JSONArray members = room.optJSONArray("members");
            memberState.setText((members == null ? 0 : members.length()) + " 人在线");
            double t = room.optDouble("currentTime", 0);
            boolean paused = room.optBoolean("paused", true);
            syncState.setText((paused ? "已同步暂停" : "同步播放中") + " · " + formatTime((int)t));
            JSONObject ctx = room.optJSONObject("context");
            if (ctx != null && contextState != null && !contextAutoSync) {
                String remoteSubtitle = ctx.optString("currentSubtitle", "");
                if (remoteSubtitle.length() > 0) contextState.setText("远端字幕：" + remoteSubtitle);
            }
            boolean handledRemoteCommand = applyRemotePlaybackCommand(ctx);
            if (!handledRemoteCommand && video.getDuration() > 0) {
                String remoteActor = room.optString("lastActor", "");
                long nowMs = System.currentTimeMillis();
                boolean recentLocalAction = nowMs - lastLocalPlaybackActionAt < 4200;

                // 避免“刚点播放，房间里旧的 paused=true 又被轮询拉回来”，这是播几秒自动暂停的主要原因。
                if (remoteActor.equals(name) || recentLocalAction || (remoteActor.length() == 0 && video.isPlaying() && paused)) {
                    if (video.isPlaying() && paused) sendPlayback(true);
                } else {
                    int remoteMs = (int)(t * 1000);
                    if (remoteMs < 1000 && lastStablePositionMs > 5000 && System.currentTimeMillis() - lastStablePositionAt < 15000) {
                        remoteMs = lastStablePositionMs;
                    }
                    if (Math.abs(video.getCurrentPosition() - remoteMs) > 1800) {
                        applyingRemote = true;
                        safeSeekTo(video, remoteMs);
                        handler.postDelayed(() -> applyingRemote = false, 800);
                    }
                    if (!paused && !video.isPlaying()) video.start();
                    if (paused && video.isPlaying()) video.pause();
                }
            }
            chatLog.setText("");
            if (fullChatLog != null) fullChatLog.setText("");
            JSONArray msgs = room.optJSONArray("messages");
            if (msgs != null) {
                for (int i = Math.max(0, msgs.length()-30); i < msgs.length(); i++) {
                    JSONObject m = msgs.getJSONObject(i);
                    String msgName = m.optString("name","观影人");
                    String msgText = m.optString("text","");
                    appendChat(msgName, msgText);

                    if (msgText.startsWith("弹幕：") && danmakuOn) {
                        String key = m.optString("id", "") + "|" + m.optString("at", "") + "|" + msgText;
                        if (!seenDanmakuKeys.contains(key)) {
                            seenDanmakuKeys.add(key);
                            showDanmaku(msgText.replaceFirst("^弹幕：", ""));
                        }
                    }
                }
            }
            renderPendingChats();
            if (chatLog.getText().length() == 0) chatLog.setText("还没有聊天。第一句可以留给今晚的电影。");
            noteLog.setText("");
            JSONArray notes = room.optJSONArray("notes");
            if (notes != null) {
                for (int i = Math.max(0, notes.length()-20); i < notes.length(); i++) {
                    JSONObject n = notes.getJSONObject(i);
                    appendNote(n.optString("name","观影人"), n.optString("text",""), (int)n.optDouble("time",0));
                }
            }
            JSONObject c = room.optJSONObject("card");
            if (c != null) {
                remoteCard = c;
                if (quoteInput != null && safeText(quoteInput).length() == 0) quoteInput.setText(c.optString("zhiQuote", c.optString("quote", "")));
                if (linQuoteInput != null && safeText(linQuoteInput).length() == 0) linQuoteInput.setText(c.optString("linQuote", ""));
                if (cardNoteInput != null && safeText(cardNoteInput).length() == 0) cardNoteInput.setText(c.optString("zhiNote", ""));
                if (linNoteInput != null && safeText(linNoteInput).length() == 0) linNoteInput.setText(c.optString("linNote", c.optString("note", "")));
                if (c.optString("template").length() > 0) cardTemplate = c.optString("template", cardTemplate);
            }
            renderCard();
        } catch(Exception ignored) {}
    }

    private URL apiUrl(String path, boolean auth) throws Exception {
        String base = normalizeServer(serverUrl);
        if (base.length() == 0) throw new IOException("后端地址为空");
        String p = path.startsWith("/") ? path : ("/" + path);
        if (auth && token.length() > 0 && p.indexOf("token=") < 0) {
            p += (p.indexOf('?') >= 0 ? "&" : "?") + "token=" + URLEncoder.encode(token, "UTF-8");
        }
        return new URL(base + p);
    }

    private JSONObject getJson(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) apiUrl(path, false).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        String s = read(c);
        return new JSONObject(s);
    }

    private JSONObject postJson(String path, JSONObject body, boolean auth) throws Exception {
        if (body == null) body = new JSONObject();
        HttpURLConnection c = (HttpURLConnection) apiUrl(path, auth).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (auth && token.length() > 0) {
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("X-CineIsle-Token", token);
            body.put("token", token);
        }
        try(OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes("UTF-8"));
        }
        String s = read(c);
        return new JSONObject(s);
    }

    private String read(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream raw = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (raw == null) throw new IOException("HTTP " + code + " 无响应内容");
        BufferedReader br = new BufferedReader(new InputStreamReader(raw, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while((line = br.readLine()) != null) sb.append(line);
        String text = sb.toString();
        if (code >= 400) {
            lastNetworkIssue = "HTTP " + code + " " + text;
            throw new IOException(lastNetworkIssue.length() > 180 ? lastNetworkIssue.substring(0,180) : lastNetworkIssue);
        }
        return text;
    }

    private void removePendingChat(String pendingId) {
        for (int i = pendingChats.size() - 1; i >= 0; i--) {
            if (pendingChats.get(i).id.equals(pendingId)) pendingChats.remove(i);
        }
    }

    private void markPendingFailed(String pendingId) {
        for (PendingChat p: pendingChats) {
            if (p.id.equals(pendingId)) p.failed = true;
        }
    }

    private void renderPendingChats() {
        for (PendingChat p: pendingChats) {
            appendChat(p.who, p.text + (p.failed ? "（本机待同步）" : "（发送中…）"));
        }
    }

    private void appendChat(String who, String text) {
        if (text != null && text.startsWith("弹幕：")) {
            String danmakuKey = (who == null ? "" : who) + "|" + text;
            if (seenDanmakuKeys.add(danmakuKey)) {
                showDanmaku(text);
            }
        }

        String line = who + "： " + text;
        String old = chatLog.getText().toString();
        if (old.startsWith("还没有聊天")) old = "";
        chatLog.setText(old + (old.length()>0 ? "\n" : "") + line);
        if (fullChatLog != null) {
            String f = fullChatLog.getText().toString();
            if (f.startsWith("还没有聊天")) f = "";
            fullChatLog.setText(f + (f.length()>0 ? "\n" : "") + line);
        }
    }

    private void appendNote(String who, String text, int sec) {
        String old = noteLog.getText().toString();
        noteLog.setText(old + (old.length()>0 ? "\n\n" : "") + "◦ [" + formatTime(sec) + "] " + who + "\n" + text);
        renderCard();
    }






    private long lastSafeSeekAt = 0L;

    private void safeSeekTo(android.widget.VideoView target, int targetMs) {
        if (target == null) return;

        try {
            int currentMs = target.getCurrentPosition();
            int diffMs = Math.abs(currentMs - targetMs);
            boolean playing = target.isPlaying();
            long now = System.currentTimeMillis();

            // 播放中最怕频繁回拉：30 秒以内的进度差都让电影自然播放，不 seek
            if (playing && diffMs < 30000) {
                return;
            }

            // 暂停状态下，小误差也不用动
            if (!playing && diffMs < 1500) {
                return;
            }

            // 避免短时间连续 seek 导致画面抽搐
            if (now - lastSafeSeekAt < 5000 && diffMs < 60000) {
                return;
            }

            lastSafeSeekAt = now;
            target.seekTo(targetMs);
        } catch (Exception ignored) {}
    }

    private void showDanmaku(String text) {
        if (!danmakuOn) return;
        if (text == null) return;

        text = text.replaceFirst("^弹幕：", "").trim();
        if (text.length() == 0) return;

        final String finalText = text;

        runOnUiThread(() -> {
            try {
                // 横屏 Dialog 模式：直接加到横屏根布局上，避免被 Dialog 盖住
                if (fullscreenDanmakuRoot != null) {
                    final android.widget.FrameLayout layer = fullscreenDanmakuRoot;

                    final android.widget.TextView tv = new android.widget.TextView(this);
                    tv.setText(finalText);
                    tv.setTextColor(android.graphics.Color.WHITE);
                    tv.setTextSize(18);
                    tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    tv.setSingleLine(true);
                    tv.setPadding(dp(16), dp(8), dp(16), dp(8));
                    tv.setBackgroundColor(0xAA000000);

                    if (android.os.Build.VERSION.SDK_INT >= 21) {
                        tv.setElevation(dp(120));
                    }

                    int w = layer.getWidth();
                    int h = layer.getHeight();
                    if (w <= 0) w = getResources().getDisplayMetrics().widthPixels;
                    if (h <= 0) h = getResources().getDisplayMetrics().heightPixels;

                    int y = dp(40) + new java.util.Random().nextInt(Math.max(1, h / 2));

                    android.widget.FrameLayout.LayoutParams lp =
                            new android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                            );
                    lp.leftMargin = 0;
                    lp.topMargin = y;

                    tv.setTranslationX(w + dp(40));
                    layer.addView(tv, lp);
                    tv.bringToFront();

                    tv.post(() -> {
                        tv.animate()
                                .translationX(-tv.getWidth() - dp(100))
                                .setDuration(6500)
                                .withEndAction(() -> {
                                    try {
                                        layer.removeView(tv);
                                    } catch (Exception ignored) {}
                                })
                                .start();
                    });

                    return;
                }

                // 竖屏普通模式：使用全屏透明 PopupWindow，已经验证可见
                final android.view.View rootView = getWindow().getDecorView();

                final int screenW = getResources().getDisplayMetrics().widthPixels;
                final int screenH = getResources().getDisplayMetrics().heightPixels;

                final android.widget.FrameLayout layer = new android.widget.FrameLayout(this);
                layer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                layer.setClipChildren(false);
                layer.setClipToPadding(false);

                final android.widget.TextView tv = new android.widget.TextView(this);
                tv.setText(finalText);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setTextSize(18);
                tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                tv.setSingleLine(true);
                tv.setPadding(dp(16), dp(8), dp(16), dp(8));
                tv.setBackgroundColor(0xAA000000);

                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    tv.setElevation(dp(100));
                    layer.setElevation(dp(100));
                }

                int y = dp(140);
                if (videoFrame != null && videoFrame.getHeight() > 0) {
                    int[] loc = new int[2];
                    videoFrame.getLocationInWindow(loc);
                    int topMin = loc[1] + dp(20);
                    int topMax = loc[1] + Math.max(dp(50), videoFrame.getHeight() / 2);
                    y = topMin + new java.util.Random().nextInt(Math.max(1, topMax - topMin));
                } else {
                    y = dp(120) + new java.util.Random().nextInt(Math.max(1, screenH / 3));
                }

                android.widget.FrameLayout.LayoutParams tvLp =
                        new android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                        );
                tvLp.leftMargin = 0;
                tvLp.topMargin = y;

                tv.setTranslationX(screenW + dp(40));
                layer.addView(tv, tvLp);

                final android.widget.PopupWindow popup = new android.widget.PopupWindow(
                        layer,
                        android.view.WindowManager.LayoutParams.MATCH_PARENT,
                        android.view.WindowManager.LayoutParams.MATCH_PARENT,
                        false
                );

                popup.setTouchable(false);
                popup.setFocusable(false);
                popup.setOutsideTouchable(false);
                popup.setClippingEnabled(false);
                popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    popup.setElevation(dp(100));
                }

                popup.showAtLocation(rootView, android.view.Gravity.NO_GRAVITY, 0, 0);

                tv.post(() -> {
                    tv.animate()
                            .translationX(-tv.getWidth() - dp(80))
                            .setDuration(6500)
                            .withEndAction(() -> {
                                try {
                                    popup.dismiss();
                                } catch (Exception ignored) {}
                            })
                            .start();
                });
            } catch (Exception e) {
                toast("弹幕显示失败：" + e.getMessage());
            }
        });
    }






    private void openCinemaFullscreen() {
        if (video == null || videoFrame == null) {
            toast("先进入房间页");
            return;
        }

        final FrameLayout originFrame = videoFrame;
        final ViewGroup oldParent = (ViewGroup) video.getParent();

        if (oldParent == null) {
            toast("先导入影片");
            return;
        }

        rememberPlaybackPosition();
        final int enterPosMs = outboundPositionMs();
        final boolean enterPlaying = video.isPlaying();
        try {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } catch (Exception ignored) {}

        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCanceledOnTouchOutside(false);

        FrameLayout root = new FrameLayout(this);
        fullscreenDanmakuRoot = root;
        root.setBackgroundColor(Color.BLACK);
        root.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(Color.BLACK);
        host.setClipChildren(false);
        host.setClipToPadding(false);

        oldParent.removeView(video);
        host.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        videoFrame = host;
        try {
            video.seekTo(enterPosMs);
            if (enterPlaying) video.start();
        } catch (Exception ignored) {}

        root.addView(host, new FrameLayout.LayoutParams(-1, -1));

        final LinearLayout panel = vbox();
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackground(round(color("#DD111827"), 22));

        TextView title = tv("映屿弹幕间", 15, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        panel.addView(title);

        TextView drawerHint = small("聊天和弹幕分开；这里也能同步进度、" + aiPeekText() + "画面。");
        drawerHint.setTextColor(color("#E5E7EB"));
        panel.addView(drawerHint);

        EditText talkInput = input("聊天：聊天：说一句…", "");
        talkInput.setTextColor(Color.WHITE);
        talkInput.setHintTextColor(color("#CBD5E1"));
        add(panel, talkInput, -1, 42, 8);

        Button sendChat = btn("发送聊天", true);
        add(panel, sendChat, -1, 40, 6);

        EditText dmInput = input("弹幕：让它飘过银幕…", "");
        dmInput.setTextColor(Color.WHITE);
        dmInput.setHintTextColor(color("#CBD5E1"));
        add(panel, dmInput, -1, 42, 10);

        Button sendDm = btn("发送弹幕", false);
        add(panel, sendDm, -1, 40, 6);

        LinearLayout row = hbox();
        Button syncNow = btn("同步", false);
        Button peek = btn(aiPeekText(), false);
        Button close = btn("退出", false);
        row.addView(syncNow, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams lpPeek = new LinearLayout.LayoutParams(0, dp(40), 1);
        lpPeek.setMargins(dp(6), 0, 0, 0);
        row.addView(peek, lpPeek);
        LinearLayout.LayoutParams lpClose = new LinearLayout.LayoutParams(0, dp(40), 1);
        lpClose.setMargins(dp(6), 0, 0, 0);
        row.addView(close, lpClose);
        add(panel, row, -1, 40, 10);

        TextView drawerStatus = small("弹幕间已准备好。");
        drawerStatus.setTextColor(color("#E5E7EB"));
        drawerStatus.setBackground(round(color("#33111111"), 16));
        drawerStatus.setPadding(dp(10), dp(8), dp(10), dp(8));
        add(panel, drawerStatus, -1, -2, 8);

        sendChat.setOnClickListener(v -> {
            if (chatInput != null) {
                chatInput.setText(talkInput.getText().toString());
                sendMessage(false);
                talkInput.setText("");
                drawerStatus.setText("聊天已发送。");
            }
        });

        sendDm.setOnClickListener(v -> {
            if (chatInput != null) {
                chatInput.setText(dmInput.getText().toString());
                sendMessage(true);
                dmInput.setText("");
                drawerStatus.setText("弹幕已发送。已和聊天分开记录。");
            }
        });

        syncNow.setOnClickListener(v -> {
            rememberPlaybackPosition();
            sendPlayback(true);
            sendCinemaContext(true);
            drawerStatus.setText("进度已同步：" + formatTime(outboundPositionMs()/1000));
            toast("已同步当前进度");
        });

        peek.setOnClickListener(v -> {
            requestLocalScreenshotNow();
            drawerStatus.setText("已请求" + aiName() + "看一眼，横屏保持几秒让无障碍上传。");
        });

        close.setOnClickListener(v -> d.dismiss());

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(350), -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        pp.setMargins(0, 0, dp(12), 0);
        panel.setVisibility(View.GONE);
        root.addView(panel, pp);

        final android.widget.TextView fullscreenChatToggle = new android.widget.TextView(this);
        fullscreenChatToggle.setText(">");
        fullscreenChatToggle.setTextColor(android.graphics.Color.WHITE);
        fullscreenChatToggle.setTextSize(26);
        fullscreenChatToggle.setGravity(android.view.Gravity.CENTER);
        fullscreenChatToggle.setPadding(dp(8), dp(8), dp(8), dp(8));

        android.graphics.drawable.GradientDrawable fullscreenChatToggleBg = new android.graphics.drawable.GradientDrawable();
        fullscreenChatToggleBg.setColor(0xAA111827);
        fullscreenChatToggleBg.setCornerRadius(dp(18));
        fullscreenChatToggle.setBackground(fullscreenChatToggleBg);

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            fullscreenChatToggle.setElevation(dp(130));
        }

        android.widget.FrameLayout.LayoutParams fullscreenChatToggleLp =
                new android.widget.FrameLayout.LayoutParams(dp(44), dp(74), android.view.Gravity.RIGHT | android.view.Gravity.CENTER_VERTICAL);
        fullscreenChatToggleLp.setMargins(0, 0, 0, 0);
        root.addView(fullscreenChatToggle, fullscreenChatToggleLp);
        fullscreenChatToggle.bringToFront();

        fullscreenChatToggle.setOnClickListener(v -> {
            if (panel.getVisibility() == android.view.View.VISIBLE) {
                panel.setVisibility(android.view.View.GONE);
                fullscreenChatToggle.setText(">");
            } else {
                panel.setVisibility(android.view.View.VISIBLE);
                fullscreenChatToggle.setText("<");
                panel.bringToFront();
                fullscreenChatToggle.bringToFront();
            }
        });


        d.setContentView(root);

        d.setOnDismissListener(x -> {
            fullscreenDanmakuRoot = null;
            final int exitPosMs = video.getCurrentPosition();
            final boolean exitPlaying = video.isPlaying();
            try {
                ViewGroup p = (ViewGroup) video.getParent();
                if (p != null) p.removeView(video);
                originFrame.addView(video, 0, new FrameLayout.LayoutParams(-1, -1));
                videoFrame = originFrame;
                forceRestoreAfterFullscreen(exitPosMs, exitPlaying);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                forceRestoreAfterFullscreen(exitPosMs, exitPlaying);
                if (exitPosMs > 1000) sendPlayback(true);
                sendCinemaContext(true);
            } catch (Exception ignored) {}
        });

        d.show();

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            w.setLayout(-1, -1);
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void renderCard() {
        if (cardPreview != null) {
            cardPreview.setText(currentTicketText());
            cardPreview.setBackground(ticketBg());
        }
    }

    private String formatTime(int sec) {
        sec = Math.max(0, sec);
        return (sec/60) + ":" + String.format("%02d", sec%60);
    }

    private void copyText(String s) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("cineisle", s));
        toast("已复制");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}

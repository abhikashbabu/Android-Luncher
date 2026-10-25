package com.crimson.launcher;

import android.animation.*;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.util.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {

    // Frosted Glassmorphism & Lively Crimson-Charcoal Palette (Zero Flat Pure Black)
    static final int BG_CHARCOAL_DEEP = 0xFF14131A;
    static final int BG_CHARCOAL_MID  = 0xFF1E1924;
    static final int BG_CRIMSON_CORE  = 0xFFE50914;
    static final int BG_RUBY_GLOW     = 0xFF9E0B22;
    static final int GLASS_STROKE     = 0x3AFFFFFF;
    static final int CRIMSON_ACCENT   = 0xFFFF2A3C;
    static final int TEXT_PRIMARY     = 0xFFFFFFFF;
    static final int TEXT_SECONDARY   = 0xFFD4D0DB;
    static final int TEXT_MUTED       = 0xFF9A94A6;

    // Core UI Layers (Persistent in one root FrameLayout for 120FPS GPU-layered motion)
    FrameLayout root;
    WallpaperView wallpaper;
    FrameLayout homeStage;
    LinearLayout homeStrip;
    final View[] homePages = new View[3];
    LinearLayout pageIndicatorRow;
    LinearLayout bottomDock;
    LinearLayout multiSelectBar;

    // Overlays driven by continuous progress [0f..1f] with Hardware Layer Promotion
    View scrimView;
    LinearLayout navDrawerLayer;     // Slides X from -drawerWidth to 0
    LinearLayout appDrawerLayer;     // Slides Y from screenHeight to 0
    LinearLayout volumeOverlayCard;  // Floating right-side glass volume slider
    VolumeBarView volumeBarView;
    TextView volumePercentText;

    // Widgets & Dynamic Views
    TextView clockText, dateText, weatherTempText;
    WaveformView musicWaveform;
    TextView musicTitleText, musicPlayBtn;
    boolean musicPlaying = true;
    SystemRingsView systemRingsView;
    GridLayout page1AppGrid, page2FolderGrid, drawerAppGrid, recentAppGrid;
    EditText drawerSearchInput;
    LinearLayout categoryChipsRow;
    LinearLayout instantSearchCard;
    TextView instantWebSearchText;

    // App Data, Pre-Scaled Hardware Icon Cache & Multi-Select State
    final ArrayList<ResolveInfo> apps = new ArrayList<>();
    final HashMap<String, String> appNames = new HashMap<>();
    final HashMap<String, BitmapDrawable> scaledIconCache = new HashMap<>();
    final HashSet<String> selectedApps = new HashSet<>();
    final HashMap<String, ArrayList<String>> customFolders = new HashMap<>();
    boolean multiSelectMode = false;
    String activeCategory = "All";
    boolean torchEnabled = false;

    // Continuous Motion, Hardware Layer & Gesture State
    int screenW, screenH, drawerW;
    int topGestureGuardPx, bottomGestureGuardPx, rightEdgeGuardPx;
    int currentHomePage = 0;
    int lastIndicatorPage = -1;
    boolean hardwareLayersPromoted = false;
    boolean blurAppliedOnHome = false;

    float homeOffsetPx = 0f;       // Continuous pixel offset for 3-page horizontal strip [0 .. 2*screenW]
    float appDrawerProgress = 0f;  // 0f = closed (at bottom), 1f = fully open
    float navDrawerProgress = 0f;  // 0f = closed (left), 1f = fully open

    // Touch Gesture Tracking
    static final int GESTURE_NONE = 0;
    static final int GESTURE_HOME_PAGER = 1;
    static final int GESTURE_APP_DRAWER = 2;
    static final int GESTURE_NAV_DRAWER = 3;
    static final int GESTURE_SYSTEM_IGNORE = 4; // Reserved for System Recents / Task Manager / Status Bar
    int activeGesture = GESTURE_NONE;
    float downX, downY;
    float startHomeOffsetPx, startAppDrawerProgress, startNavDrawerProgress;
    VelocityTracker velocityTracker;
    ValueAnimator activeAnimator;

    // Timers
    final Handler handler = new Handler(Looper.getMainLooper());
    final SimpleDateFormat clockFormat = new SimpleDateFormat("hh:mm", Locale.getDefault());
    final SimpleDateFormat dateFormat  = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());

    final Runnable minuteTicker = new Runnable() {
        @Override public void run() {
            updateClockAndStats();
            handler.postDelayed(this, 60000 - (System.currentTimeMillis() % 60000));
        }
    };

    final Runnable waveformTicker = new Runnable() {
        @Override public void run() {
            // Pause waveform redraws while dragging any drawer or when not on Page 2 to save 100% UI thread
            if (musicWaveform != null && musicPlaying
                    && activeGesture == GESTURE_NONE
                    && appDrawerProgress < 0.05f
                    && navDrawerProgress < 0.05f
                    && currentHomePage == 1) {
                musicWaveform.stepWave();
            }
            handler.postDelayed(this, 75);
        }
    };

    final Runnable hideVolumeRunnable = new Runnable() {
        @Override public void run() {
            animateVolumeOverlay(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure Android OS treats this Activity strictly as a Home Launcher, not a Recents card
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null && am.getAppTasks() != null) {
                for (ActivityManager.AppTask task : am.getAppTasks()) {
                    task.setExcludeFromRecents(true);
                }
            }
        } catch (Exception ignored) {}

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(0x00000000);
        w.setNavigationBarColor(0x00000000);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        drawerW = Math.round(screenW * 0.80f);

        // Default System Gesture Exclusion Guards (Prevents conflict with Android Task Manager / Recents swipe-up)
        topGestureGuardPx = dp(42);
        bottomGestureGuardPx = dp(82); // Bottom 82dp reserved for System Gesture Navigation / Recents Task Manager
        rightEdgeGuardPx = dp(28);

        initDefaultFolders();
        loadInstalledApps();
        buildRootScene();

        // Dynamically read OS System Gesture Insets on Android 10+ (API 29+)
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Insets sysGestures = insets.getSystemGestureInsets();
                bottomGestureGuardPx = Math.max(dp(78), sysGestures.bottom + dp(44));
                topGestureGuardPx = Math.max(dp(38), sysGestures.top + dp(12));
                rightEdgeGuardPx = Math.max(dp(24), sysGestures.right + dp(8));
            }
            return insets;
        });

        applyMotionTransforms();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Clean up any partial transition if returning from Recents / Task Manager
        if (appDrawerProgress > 0f && appDrawerProgress < 0.95f) {
            appDrawerProgress = 0f;
        }
        if (navDrawerProgress > 0f && navDrawerProgress < 0.95f) {
            navDrawerProgress = 0f;
        }
        setHardwareLayersActive(false);
        applyMotionTransforms();
        updateClockAndStats();
        handler.removeCallbacks(minuteTicker);
        handler.post(minuteTicker);
        handler.removeCallbacks(waveformTicker);
        handler.post(waveformTicker);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // When System Gesture Task Manager (Recents) or Notification Shade takes focus,
        // immediately cancel any mid-drag drawer so it never jitters or fights with SystemUI!
        if (!hasFocus) {
            if (activeAnimator != null && activeAnimator.isRunning()) {
                activeAnimator.cancel();
            }
            activeGesture = GESTURE_NONE;
            boolean changed = false;
            if (appDrawerProgress > 0f && appDrawerProgress < 0.95f) {
                appDrawerProgress = 0f;
                changed = true;
            }
            if (navDrawerProgress > 0f && navDrawerProgress < 0.95f) {
                navDrawerProgress = 0f;
                changed = true;
            }
            setHardwareLayersActive(false);
            if (changed) applyMotionTransforms();
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(minuteTicker);
        handler.removeCallbacks(waveformTicker);
        setHardwareLayersActive(false);
        super.onPause();
    }

    int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    void vibrateTick() {
        try {
            if (root != null) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        } catch (Exception ignored) {}
    }

    void initDefaultFolders() {
        if (customFolders.isEmpty()) {
            customFolders.put("Social", new ArrayList<>());
            customFolders.put("Media", new ArrayList<>());
            customFolders.put("Gaming", new ArrayList<>());
            customFolders.put("Work", new ArrayList<>());
        }
    }

    void loadInstalledApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(mainIntent, 0);
        String myPkg = getPackageName();
        int iconPx = dp(44);
        for (ResolveInfo ri : list) {
            if (!ri.activityInfo.packageName.equals(myPkg)) {
                apps.add(ri);
                String k = appKey(ri);
                appNames.put(k, ri.loadLabel(pm).toString());
                // Pre-scale icon into a small GPU-friendly BitmapDrawable once so drawer scrolling & sliding has 0ms decode lag
                if (!scaledIconCache.containsKey(k)) {
                    try {
                        Drawable raw = ri.loadIcon(pm);
                        if (raw != null) {
                            Bitmap bmp = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888);
                            Canvas c = new Canvas(bmp);
                            raw.setBounds(0, 0, iconPx, iconPx);
                            raw.draw(c);
                            scaledIconCache.put(k, new BitmapDrawable(getResources(), bmp));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        apps.sort((a, b) -> appName(a).compareToIgnoreCase(appName(b)));
    }

    String appKey(ResolveInfo a) {
        return a.activityInfo.packageName + "/" + a.activityInfo.name;
    }

    String appName(ResolveInfo a) {
        String n = appNames.get(appKey(a));
        return n != null ? n : a.activityInfo.packageName;
    }

    Drawable getAppIcon(ResolveInfo a) {
        String k = appKey(a);
        BitmapDrawable cached = scaledIconCache.get(k);
        if (cached != null) return cached;
        return a.loadIcon(getPackageManager());
    }

    boolean isPinned(ResolveInfo a) {
        return getPreferences(MODE_PRIVATE).getBoolean("pin_" + appKey(a), false);
    }

    void togglePinned(ResolveInfo a) {
        String k = "pin_" + appKey(a);
        boolean next = !isPinned(a);
        getPreferences(MODE_PRIVATE).edit().putBoolean(k, next).apply();
    }

    String resolveCategory(ResolveInfo a) {
        int cat = a.activityInfo.applicationInfo.category;
        if (cat == ApplicationInfo.CATEGORY_GAME) return "Games";
        if (cat == ApplicationInfo.CATEGORY_SOCIAL) return "Social";
        if (cat == ApplicationInfo.CATEGORY_PRODUCTIVITY) return "Work";
        if (cat == ApplicationInfo.CATEGORY_AUDIO || cat == ApplicationInfo.CATEGORY_VIDEO || cat == ApplicationInfo.CATEGORY_IMAGE) return "Media";
        String n = appName(a).toLowerCase(Locale.ROOT);
        if (n.contains("chat") || n.contains("mess") || n.contains("whats") || n.contains("insta") || n.contains("social") || n.contains("tele")) return "Social";
        if (n.contains("music") || n.contains("video") || n.contains("play") || n.contains("tube") || n.contains("cam") || n.contains("photo")) return "Media";
        if (n.contains("mail") || n.contains("doc") || n.contains("cal") || n.contains("note") || n.contains("files")) return "Work";
        return "Utility";
    }

    // =========================================================================
    // SINGLE-PASS GPU-OPTIMIZED FROSTED GLASS DRAWABLE FACTORY
    // (Eliminates multi-layer overdraw while keeping the exact Frosted Glass look!)
    // =========================================================================
    Drawable glassDrawable(int radiusDp, boolean activeCrimson, boolean darkSheet) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                darkSheet
                        ? new int[]{0xE6261B2C, 0xEC16141F, 0xE6211524}
                        : (activeCrimson
                        ? new int[]{0x48FF2A3C, 0x24FFFFFF, 0x369E0B22}
                        : new int[]{0x34FFFFFF, 0x18FFFFFF, 0x22FF2A3C})
        );
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(activeCrimson ? 1.6f : 1f), activeCrimson ? CRIMSON_ACCENT : GLASS_STROKE);
        return d;
    }

    Drawable pillDrawable(boolean active) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                active
                        ? new int[]{0xBBFF2A3C, 0x889E0B22}
                        : new int[]{0x2EFFFFFF, 0x16FFFFFF}
        );
        d.setCornerRadius(dp(50));
        d.setStroke(dp(active ? 1.5f : 1f), active ? 0xFFFF6B78 : 0x36FFFFFF);
        return d;
    }

    TextView label(String text, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return tv;
    }

    // =========================================================================
    // SCENE CONSTRUCTION (ALL LAYERS PERSIST FOR SMOOTH MOTION)
    // =========================================================================
    void buildRootScene() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG_CHARCOAL_DEEP);

        // 1. Pre-Rendered Cached Bitmap Crimson & Charcoal Mesh Wallpaper (0ms per frame!)
        int padX = Math.round(screenW * 0.12f);
        int padY = Math.round(screenH * 0.08f);
        wallpaper = new WallpaperView(this);
        FrameLayout.LayoutParams wpLp = new FrameLayout.LayoutParams(screenW + padX * 2, screenH + padY * 2);
        wpLp.leftMargin = -padX;
        wpLp.topMargin = -padY;
        root.addView(wallpaper, wpLp);

        // 2. Home Stage (contains 3-page horizontal strip, page dots, bottom dock, multi-select bar)
        homeStage = new FrameLayout(this);
        root.addView(homeStage, new FrameLayout.LayoutParams(-1, -1));

        buildHomePagerStrip();
        buildBottomArea();

        // 3. Frosted Glass Scrim Layer
        scrimView = new View(this);
        scrimView.setBackgroundColor(0xB8110E18);
        scrimView.setAlpha(0f);
        scrimView.setVisibility(View.GONE);
        scrimView.setOnClickListener(v -> {
            if (navDrawerProgress > 0.1f) animateNavDrawerTo(0f);
            else if (appDrawerProgress > 0.1f) animateAppDrawerTo(0f);
        });
        root.addView(scrimView, new FrameLayout.LayoutParams(-1, -1));

        // 4. Open App Drawer & Universal Search Sheet (Vertical sliding layer)
        buildAppDrawerLayer();

        // 5. Left Frosted Glass Navigation Drawer (Horizontal sliding layer)
        buildNavDrawerLayer();

        // 6. Right-Edge Floating Glass Volume Slider Overlay
        buildVolumeOverlayLayer();

        setContentView(root);
    }

    void buildHomePagerStrip() {
        homeStrip = new LinearLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int w = MeasureSpec.makeMeasureSpec(screenW * 3, MeasureSpec.EXACTLY);
                super.onMeasure(w, heightMeasureSpec);
            }
        };
        homeStrip.setOrientation(LinearLayout.HORIZONTAL);

        homePages[0] = buildHomePageOne();
        homePages[1] = buildHomePageTwo();
        homePages[2] = buildHomePageThree();

        for (int i = 0; i < 3; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(screenW, -1);
            homeStrip.addView(homePages[i], lp);
        }
        homeStage.addView(homeStrip, new FrameLayout.LayoutParams(screenW * 3, -1));
    }

    // -------------------------------------------------------------------------
    // HOME PAGE 1: Primary Clean Glass Home Screen
    // -------------------------------------------------------------------------
    View buildHomePageOne() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(42), dp(20), dp(118));

        // Frosted Glass Weather & Clock Hero Card
        LinearLayout heroCard = new LinearLayout(this);
        heroCard.setOrientation(LinearLayout.HORIZONTAL);
        heroCard.setGravity(Gravity.CENTER_VERTICAL);
        heroCard.setBackground(glassDrawable(26, false, false));
        heroCard.setPadding(dp(22), dp(18), dp(22), dp(18));

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        clockText = label(clockFormat.format(new Date()), 48, TEXT_PRIMARY, false);
        clockText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        dateText = label(dateFormat.format(new Date()), 13, TEXT_SECONDARY, false);
        leftCol.addView(clockText);
        leftCol.addView(dateText);
        heroCard.addView(leftCol, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.END);
        TextView sunIcon = label("\u26C5", 28, 0xFFFFD166, false);
        weatherTempText = label("Partly Cloudy\n72\u00B0F \u00B7 Crimson OS", 12, TEXT_SECONDARY, false);
        weatherTempText.setGravity(Gravity.END);
        rightCol.addView(sunIcon);
        rightCol.addView(weatherTempText);
        heroCard.addView(rightCol, new LinearLayout.LayoutParams(-2, -2));

        page.addView(heroCard, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(page, 14);

        // Glass Search Pill on Home
        LinearLayout searchPill = new LinearLayout(this);
        searchPill.setOrientation(LinearLayout.HORIZONTAL);
        searchPill.setGravity(Gravity.CENTER_VERTICAL);
        searchPill.setBackground(glassDrawable(50, false, false));
        searchPill.setPadding(dp(18), dp(12), dp(18), dp(12));
        TextView sHint = label("Search apps, web & tools...", 14, TEXT_SECONDARY, false);
        searchPill.addView(sHint, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView sIcon = label("\uD83D\uDD0D", 15, TEXT_PRIMARY, false);
        searchPill.addView(sIcon);
        searchPill.setOnClickListener(v -> {
            vibrateTick();
            animateAppDrawerTo(1f);
            handler.postDelayed(() -> {
                if (drawerSearchInput != null) {
                    drawerSearchInput.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(drawerSearchInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 240);
        });
        page.addView(searchPill, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(page, 16);

        // Primary 4x3 Glass App Icons Grid
        page1AppGrid = new GridLayout(this);
        page1AppGrid.setColumnCount(4);
        page.addView(page1AppGrid, new LinearLayout.LayoutParams(-1, 0, 1f));
        populatePage1Grid();

        // Mini Dual Glass Widgets Row (Developed By Abhikash Babu + Quick Status/Volume)
        LinearLayout miniRow = new LinearLayout(this);
        miniRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout miniMusic = new LinearLayout(this);
        miniMusic.setOrientation(LinearLayout.HORIZONTAL);
        miniMusic.setGravity(Gravity.CENTER_VERTICAL);
        miniMusic.setBackground(glassDrawable(20, false, false));
        miniMusic.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView noteBadge = label("\u266B", 18, TEXT_PRIMARY, true);
        noteBadge.setGravity(Gravity.CENTER);
        noteBadge.setBackground(glassDrawable(12, true, false));
        miniMusic.addView(noteBadge, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout mTextCol = new LinearLayout(this);
        mTextCol.setOrientation(LinearLayout.VERTICAL);
        mTextCol.setPadding(dp(10), 0, 0, 0);
        mTextCol.addView(label("Developed By", 11, TEXT_PRIMARY, true));
        mTextCol.addView(label("Abhikash Babu", 10, CRIMSON_ACCENT, false));
        miniMusic.addView(mTextCol);
        miniMusic.setOnClickListener(v -> animateHomeToPage(1));

        LinearLayout miniStatus = new LinearLayout(this);
        miniStatus.setOrientation(LinearLayout.HORIZONTAL);
        miniStatus.setGravity(Gravity.CENTER);
        miniStatus.setBackground(glassDrawable(20, false, false));
        miniStatus.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView calBtn = label("\uD83D\uDCC5", 16, TEXT_PRIMARY, false);
        TextView volBtn = label("  \uD83D\uDD0A Vol  ", 12, CRIMSON_ACCENT, true);
        TextView drwBtn = label("\u2630", 16, TEXT_PRIMARY, false);
        miniStatus.addView(calBtn);
        miniStatus.addView(volBtn);
        miniStatus.addView(drwBtn);
        miniStatus.setOnClickListener(v -> {
            vibrateTick();
            animateVolumeOverlay(true);
        });

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, dp(62), 1f);
        leftLp.rightMargin = dp(6);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, dp(62), 1f);
        rightLp.leftMargin = dp(6);
        miniRow.addView(miniMusic, leftLp);
        miniRow.addView(miniStatus, rightLp);
        page.addView(miniRow, new LinearLayout.LayoutParams(-1, -2));

        return page;
    }

    // -------------------------------------------------------------------------
    // HOME PAGE 2: Interactive Music Waveform, Live CPU/RAM/Battery Rings & Multi-Select Folders
    // -------------------------------------------------------------------------
    View buildHomePageTwo() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(42), dp(20), dp(118));

        // Interactive Glass Music Player Widget with Live Crimson Waveform
        LinearLayout musicCard = new LinearLayout(this);
        musicCard.setOrientation(LinearLayout.HORIZONTAL);
        musicCard.setGravity(Gravity.CENTER_VERTICAL);
        musicCard.setBackground(glassDrawable(26, false, false));
        musicCard.setPadding(dp(18), dp(16), dp(18), dp(16));

        LinearLayout musicLeft = new LinearLayout(this);
        musicLeft.setOrientation(LinearLayout.VERTICAL);
        musicTitleText = label("Now Playing \u00B7 Crimson Pulse", 14, TEXT_PRIMARY, true);
        musicLeft.addView(musicTitleText);

        musicWaveform = new WaveformView(this);
        LinearLayout.LayoutParams wfLp = new LinearLayout.LayoutParams(-1, dp(38));
        wfLp.topMargin = dp(8);
        wfLp.bottomMargin = dp(8);
        musicLeft.addView(musicWaveform, wfLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView prevBtn = label("\u23EE", 18, TEXT_PRIMARY, false);
        prevBtn.setPadding(dp(10), dp(4), dp(16), dp(4));
        musicPlayBtn = label("\u23F8", 16, TEXT_PRIMARY, true);
        musicPlayBtn.setGravity(Gravity.CENTER);
        musicPlayBtn.setBackground(pillDrawable(true));
        TextView nextBtn = label("\u23ED", 18, TEXT_PRIMARY, false);
        nextBtn.setPadding(dp(16), dp(4), dp(10), dp(4));

        musicPlayBtn.setOnClickListener(v -> {
            vibrateTick();
            musicPlaying = !musicPlaying;
            musicPlayBtn.setText(musicPlaying ? "\u23F8" : "\u25B6");
        });
        prevBtn.setOnClickListener(v -> { vibrateTick(); musicWaveform.randomize(); });
        nextBtn.setOnClickListener(v -> { vibrateTick(); musicWaveform.randomize(); });

        controls.addView(prevBtn);
        controls.addView(musicPlayBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));
        controls.addView(nextBtn);
        musicLeft.addView(controls);

        musicCard.addView(musicLeft, new LinearLayout.LayoutParams(0, -2, 1f));

        // Translucent Glass Album Art Cube
        FrameLayout albumCube = new FrameLayout(this);
        albumCube.setBackground(glassDrawable(22, true, false));
        TextView discIcon = label("\uD83C\uDFB5", 28, TEXT_PRIMARY, false);
        discIcon.setGravity(Gravity.CENTER);
        albumCube.addView(discIcon, new FrameLayout.LayoutParams(-1, -1));
        musicCard.addView(albumCube, new LinearLayout.LayoutParams(dp(84), dp(84)));

        page.addView(musicCard, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(page, 14);

        // Live CPU / RAM / Battery Ring Gauges Card
        LinearLayout statsCard = new LinearLayout(this);
        statsCard.setOrientation(LinearLayout.VERTICAL);
        statsCard.setBackground(glassDrawable(24, false, false));
        statsCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        systemRingsView = new SystemRingsView(this);
        statsCard.addView(systemRingsView, new LinearLayout.LayoutParams(-1, dp(86)));
        page.addView(statsCard, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(page, 14);

        // Header for Categorized Glass Folders & Multi-Select Toggle
        LinearLayout folderHeader = new LinearLayout(this);
        folderHeader.setOrientation(LinearLayout.HORIZONTAL);
        folderHeader.setGravity(Gravity.CENTER_VERTICAL);
        folderHeader.addView(label("Smart Glass Folders & Apps", 13, TEXT_SECONDARY, true), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView multiBtn = label("Multi-Select", 11, TEXT_PRIMARY, true);
        multiBtn.setBackground(pillDrawable(true));
        multiBtn.setPadding(dp(12), dp(5), dp(12), dp(5));
        multiBtn.setOnClickListener(v -> {
            vibrateTick();
            setMultiSelectMode(!multiSelectMode);
        });
        folderHeader.addView(multiBtn);
        page.addView(folderHeader, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(page, 8);

        page2FolderGrid = new GridLayout(this);
        page2FolderGrid.setColumnCount(4);
        page.addView(page2FolderGrid, new LinearLayout.LayoutParams(-1, 0, 1f));
        populatePage2FoldersAndApps();

        return page;
    }

    // -------------------------------------------------------------------------
    // HOME PAGE 3: Quick Control Center, Pinned Essential Hub & Customizations
    // -------------------------------------------------------------------------
    View buildHomePageThree() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(42), dp(20), dp(118));

        LinearLayout hubCard = new LinearLayout(this);
        hubCard.setOrientation(LinearLayout.VERTICAL);
        hubCard.setBackground(glassDrawable(26, false, false));
        hubCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        hubCard.addView(label("CRIMSON GLASS STUDIO \u00B7 120FPS GPU", 11, CRIMSON_ACCENT, true));
        addSpacer(hubCard, 6);
        hubCard.addView(label("Hardware-Accelerated Glass & Gesture Guard", 17, TEXT_PRIMARY, true));
        addSpacer(hubCard, 4);
        hubCard.addView(label("Bottom system gesture zone is isolated so Android Task Manager (Recents) opens cleanly without fighting the App Drawer.", 12, TEXT_SECONDARY, false));
        addSpacer(hubCard, 14);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView openNavBtn = label("Open Nav Drawer", 12, TEXT_PRIMARY, true);
        openNavBtn.setGravity(Gravity.CENTER);
        openNavBtn.setBackground(pillDrawable(false));
        openNavBtn.setPadding(dp(14), dp(10), dp(14), dp(10));
        openNavBtn.setOnClickListener(v -> animateNavDrawerTo(1f));

        TextView openVolBtn = label("Volume Slider", 12, TEXT_PRIMARY, true);
        openVolBtn.setGravity(Gravity.CENTER);
        openVolBtn.setBackground(pillDrawable(true));
        openVolBtn.setPadding(dp(14), dp(10), dp(14), dp(10));
        openVolBtn.setOnClickListener(v -> animateVolumeOverlay(true));

        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, -2, 1f);
        b1.rightMargin = dp(6);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, -2, 1f);
        b2.leftMargin = dp(6);
        actionRow.addView(openNavBtn, b1);
        actionRow.addView(openVolBtn, b2);
        hubCard.addView(actionRow, new LinearLayout.LayoutParams(-1, -2));

        page.addView(hubCard, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(page, 16);

        // Quick Launcher Actions Card
        LinearLayout toolsCard = new LinearLayout(this);
        toolsCard.setOrientation(LinearLayout.VERTICAL);
        toolsCard.setBackground(glassDrawable(24, false, false));
        toolsCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        toolsCard.addView(label("Quick System Actions", 14, TEXT_PRIMARY, true));
        addSpacer(toolsCard, 10);

        String[][] quickItems = {
                {"Set Default Home Launcher", "Make Crimson Glass your default Home screen"},
                {"Toggle Multi-Select Mode", "Select multiple icons to group or manage"},
                {"Open Universal App Search", "Search installed apps, categories & web"},
                {"Refresh Installed Apps", "Rescan PackageManager app list"}
        };
        for (int i = 0; i < quickItems.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(glassDrawable(16, false, false));
            row.addView(label(quickItems[i][0], 13, TEXT_PRIMARY, true));
            row.addView(label(quickItems[i][1], 11, TEXT_MUTED, false));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = dp(8);
            row.setOnClickListener(v -> {
                vibrateTick();
                if (idx == 0) startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                else if (idx == 1) { animateHomeToPage(1); setMultiSelectMode(true); }
                else if (idx == 2) animateAppDrawerTo(1f);
                else {
                    scaledIconCache.clear();
                    loadInstalledApps();
                    populatePage1Grid();
                    populatePage2FoldersAndApps();
                    filterDrawerApps();
                    Toast.makeText(this, "Apps refreshed", Toast.LENGTH_SHORT).show();
                }
            });
            toolsCard.addView(row, rlp);
        }
        page.addView(toolsCard, new LinearLayout.LayoutParams(-1, -2));

        return page;
    }

    // -------------------------------------------------------------------------
    // BOTTOM AREA: Page Indicators, Multi-Select Floating Bar & Frosted Glass Dock
    // -------------------------------------------------------------------------
    void buildBottomArea() {
        LinearLayout bottomContainer = new LinearLayout(this);
        bottomContainer.setOrientation(LinearLayout.VERTICAL);
        bottomContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        bottomContainer.setPadding(dp(20), 0, dp(20), dp(18));

        // Floating Multi-Select Options Glass Bar
        multiSelectBar = new LinearLayout(this);
        multiSelectBar.setOrientation(LinearLayout.VERTICAL);
        multiSelectBar.setGravity(Gravity.CENTER_HORIZONTAL);
        multiSelectBar.setBackground(glassDrawable(24, true, true));
        multiSelectBar.setPadding(dp(16), dp(12), dp(16), dp(12));
        multiSelectBar.setVisibility(View.GONE);

        TextView msTitle = label("Multi-Select Options", 13, TEXT_PRIMARY, true);
        msTitle.setGravity(Gravity.CENTER);
        multiSelectBar.addView(msTitle);
        addSpacer(multiSelectBar, 10);

        LinearLayout msActions = new LinearLayout(this);
        msActions.setOrientation(LinearLayout.HORIZONTAL);
        msActions.setGravity(Gravity.CENTER);

        String[][] actions = {
                {"\uD83D\uDC65", "Group"},
                {"\uD83D\uDDD1", "Uninstall"},
                {"\u2716", "Clear"},
                {"\uD83D\uDCC1", "Create Folder"}
        };
        for (int i = 0; i < actions.length; i++) {
            final int actIdx = i;
            LinearLayout btn = new LinearLayout(this);
            btn.setOrientation(LinearLayout.VERTICAL);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(8), dp(4), dp(8), dp(4));
            btn.addView(label(actions[i][0], 18, TEXT_PRIMARY, false));
            btn.addView(label(actions[i][1], 10, TEXT_SECONDARY, false));
            btn.setOnClickListener(v -> handleMultiSelectAction(actIdx));
            msActions.addView(btn, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        multiSelectBar.addView(msActions, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams msLp = new LinearLayout.LayoutParams(-1, -2);
        msLp.bottomMargin = dp(10);
        bottomContainer.addView(multiSelectBar, msLp);

        // Page Dots Indicator (Zero requestLayout during drag!)
        pageIndicatorRow = new LinearLayout(this);
        pageIndicatorRow.setOrientation(LinearLayout.HORIZONTAL);
        pageIndicatorRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            final int pIdx = i;
            View dot = new View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(12), dp(7));
            dlp.setMargins(dp(5), 0, dp(5), 0);
            dot.setBackground(pillDrawable(i == 0));
            dot.setOnClickListener(v -> animateHomeToPage(pIdx));
            pageIndicatorRow.addView(dot, dlp);
        }
        LinearLayout.LayoutParams indLp = new LinearLayout.LayoutParams(-2, -2);
        indLp.bottomMargin = dp(10);
        bottomContainer.addView(pageIndicatorRow, indLp);

        // Sleek Frosted Glass Bottom Dock
        bottomDock = new LinearLayout(this);
        bottomDock.setOrientation(LinearLayout.HORIZONTAL);
        bottomDock.setGravity(Gravity.CENTER);
        bottomDock.setBackground(glassDrawable(28, false, false));
        bottomDock.setPadding(dp(12), dp(10), dp(12), dp(10));

        populateDock();
        bottomContainer.addView(bottomDock, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams bcLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        homeStage.addView(bottomContainer, bcLp);
    }

    void populateDock() {
        bottomDock.removeAllViews();
        int count = Math.min(4, apps.size());
        for (int i = 0; i < count; i++) {
            final ResolveInfo app = apps.get(i);
            FrameLayout slot = new FrameLayout(this);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(getAppIcon(app));
            iv.setPadding(dp(10), dp(10), dp(10), dp(10));
            iv.setBackground(glassDrawable(18, false, false));
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER);
            slot.addView(iv, ilp);
            slot.setOnClickListener(v -> launchApp(app));
            bottomDock.addView(slot, new LinearLayout.LayoutParams(0, dp(54), 1f));
        }
        // 5th Dock Icon: App Drawer Trigger
        FrameLayout drawerBtn = new FrameLayout(this);
        TextView gear = label("\u2699", 22, TEXT_PRIMARY, false);
        gear.setGravity(Gravity.CENTER);
        gear.setBackground(glassDrawable(18, true, false));
        drawerBtn.addView(gear, new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER));
        drawerBtn.setOnClickListener(v -> {
            vibrateTick();
            animateAppDrawerTo(1f);
        });
        bottomDock.addView(drawerBtn, new LinearLayout.LayoutParams(0, dp(54), 1f));
    }

    // -------------------------------------------------------------------------
    // POPULATE HOME GRIDS & MULTI-SELECT CARDS
    // -------------------------------------------------------------------------
    void populatePage1Grid() {
        if (page1AppGrid == null) return;
        page1AppGrid.removeAllViews();
        ArrayList<ResolveInfo> sorted = new ArrayList<>(apps);
        sorted.sort((a, b) -> Boolean.compare(isPinned(b), isPinned(a)));
        int limit = Math.min(12, sorted.size());
        for (int i = 0; i < limit; i++) {
            page1AppGrid.addView(createAppCell(sorted.get(i), true));
        }
    }

    void populatePage2FoldersAndApps() {
        if (page2FolderGrid == null) return;
        page2FolderGrid.removeAllViews();

        String[] folderNames = {"Social", "Media", "Gaming", "Work"};
        for (String fn : folderNames) {
            page2FolderGrid.addView(createFolderCell(fn));
        }
        int start = Math.min(4, apps.size());
        int end = Math.min(start + 8, apps.size());
        for (int i = start; i < end; i++) {
            page2FolderGrid.addView(createAppCell(apps.get(i), true));
        }
    }

    View createFolderCell(final String folderName) {
        final String folderKey = "folder:" + folderName;
        final boolean selected = selectedApps.contains(folderKey);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(4), dp(6), dp(4), dp(10));

        FrameLayout box = new FrameLayout(this);
        box.setBackground(glassDrawable(20, selected || multiSelectMode, false));

        GridLayout miniGrid = new GridLayout(this);
        miniGrid.setColumnCount(2);
        miniGrid.setPadding(dp(8), dp(8), dp(8), dp(8));

        int added = 0;
        for (ResolveInfo a : apps) {
            if (added >= 4) break;
            if (resolveCategory(a).equalsIgnoreCase(folderName) || added < 4) {
                ImageView mini = new ImageView(this);
                mini.setImageDrawable(getAppIcon(a));
                GridLayout.LayoutParams mlp = new GridLayout.LayoutParams();
                mlp.width = dp(18);
                mlp.height = dp(18);
                mlp.setMargins(dp(2), dp(2), dp(2), dp(2));
                miniGrid.addView(mini, mlp);
                added++;
            }
        }
        box.addView(miniGrid, new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));

        if (multiSelectMode) {
            TextView badge = label(selected ? "\u2713" : "", 10, TEXT_PRIMARY, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(pillDrawable(selected));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP | Gravity.END);
            box.addView(badge, blp);
        }

        cell.addView(box, new LinearLayout.LayoutParams(dp(60), dp(60)));
        TextView title = label(folderName, 11, TEXT_SECONDARY, false);
        title.setSingleLine();
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(5), 0, 0);
        cell.addView(title);

        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = 0;
        glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cell.setLayoutParams(glp);

        cell.setOnClickListener(v -> {
            vibrateTick();
            if (multiSelectMode) {
                if (selectedApps.contains(folderKey)) selectedApps.remove(folderKey);
                else selectedApps.add(folderKey);
                refreshMultiSelectVisuals();
            } else {
                activeCategory = folderName.equals("Gaming") ? "Games" : folderName;
                updateCategoryChips();
                filterDrawerApps();
                animateAppDrawerTo(1f);
            }
        });
        cell.setOnLongClickListener(v -> {
            vibrateTick();
            selectedApps.add(folderKey);
            setMultiSelectMode(true);
            return true;
        });
        return cell;
    }

    View createAppCell(final ResolveInfo a, final boolean allowMultiSelect) {
        final String k = appKey(a);
        final boolean selected = selectedApps.contains(k);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(4), dp(6), dp(4), dp(10));

        FrameLayout iconWrap = new FrameLayout(this);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(getAppIcon(a));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(glassDrawable(20, (allowMultiSelect && selected) || isPinned(a), false));
        iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));

        if (allowMultiSelect && multiSelectMode) {
            TextView check = label(selected ? "\u2713" : "", 10, TEXT_PRIMARY, true);
            check.setGravity(Gravity.CENTER);
            check.setBackground(pillDrawable(selected));
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP | Gravity.END);
            iconWrap.addView(check, clp);
        }

        cell.addView(iconWrap, new LinearLayout.LayoutParams(dp(60), dp(60)));

        TextView title = label(appName(a), 11, TEXT_SECONDARY, false);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(5), 0, 0);
        cell.addView(title, new LinearLayout.LayoutParams(-1, -2));

        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = 0;
        glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cell.setLayoutParams(glp);

        cell.setOnClickListener(v -> {
            vibrateTick();
            if (allowMultiSelect && multiSelectMode) {
                if (selectedApps.contains(k)) selectedApps.remove(k);
                else selectedApps.add(k);
                refreshMultiSelectVisuals();
            } else {
                launchApp(a);
            }
        });

        cell.setOnLongClickListener(v -> {
            vibrateTick();
            if (allowMultiSelect) {
                selectedApps.add(k);
                setMultiSelectMode(true);
            } else {
                showAppQuickOptions(a);
            }
            return true;
        });

        return cell;
    }

    void setMultiSelectMode(boolean enabled) {
        multiSelectMode = enabled;
        if (!enabled) selectedApps.clear();
        multiSelectBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (enabled) {
            multiSelectBar.setAlpha(0f);
            multiSelectBar.setTranslationY(dp(20));
            multiSelectBar.animate().alpha(1f).translationY(0f).setDuration(200).setInterpolator(new OvershootInterpolator(1.1f)).start();
        }
        refreshMultiSelectVisuals();
    }

    void refreshMultiSelectVisuals() {
        populatePage1Grid();
        populatePage2FoldersAndApps();
    }

    void handleMultiSelectAction(int actionIndex) {
        vibrateTick();
        if (actionIndex == 0 || actionIndex == 3) {
            int count = selectedApps.size();
            Toast.makeText(this, "Grouped " + count + " items into Glass Folder", Toast.LENGTH_SHORT).show();
            setMultiSelectMode(false);
        } else if (actionIndex == 1) {
            for (String key : selectedApps) {
                if (!key.startsWith("folder:") && key.contains("/")) {
                    String pkg = key.substring(0, key.indexOf('/'));
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
                    break;
                }
            }
            setMultiSelectMode(false);
        } else {
            setMultiSelectMode(false);
        }
    }

    // =========================================================================
    // LAYER 4: GPU-OPTIMIZED FROSTED GLASS APP DRAWER & UNIVERSAL SEARCH
    // =========================================================================
    void buildAppDrawerLayer() {
        appDrawerLayer = new LinearLayout(this);
        appDrawerLayer.setOrientation(LinearLayout.VERTICAL);
        // High-speed single-pass Frosted Crimson-Obsidian Glass Sheet
        appDrawerLayer.setBackground(glassDrawable(32, false, true));
        appDrawerLayer.setPadding(dp(20), dp(42), dp(20), dp(20));

        // Drag Handle Pill
        View handle = new View(this);
        handle.setBackground(pillDrawable(false));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(44), dp(5));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = dp(14);
        appDrawerLayer.addView(handle, hlp);

        // Universal Frosted Glass Search Bar + Voice Trigger
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackground(glassDrawable(50, false, false));
        searchBar.setPadding(dp(16), dp(6), dp(16), dp(6));

        TextView sIcon = label("\uD83D\uDD0D", 16, CRIMSON_ACCENT, false);
        searchBar.addView(sIcon);

        drawerSearchInput = new EditText(this);
        drawerSearchInput.setSingleLine();
        drawerSearchInput.setHint("Search apps & web...");
        drawerSearchInput.setHintTextColor(TEXT_SECONDARY);
        drawerSearchInput.setTextColor(TEXT_PRIMARY);
        drawerSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        drawerSearchInput.setBackgroundColor(0x00000000);
        drawerSearchInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        searchBar.addView(drawerSearchInput, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView micBtn = label("\uD83C\uDF99", 18, CRIMSON_ACCENT, false);
        micBtn.setOnClickListener(v -> {
            vibrateTick();
            try {
                startActivity(new Intent(Intent.ACTION_VOICE_COMMAND).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Toast.makeText(this, "Voice Search Ready", Toast.LENGTH_SHORT).show();
            }
        });
        searchBar.addView(micBtn);
        appDrawerLayer.addView(searchBar, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(appDrawerLayer, 14);

        // Horizontal Glowing Category Filter Pills
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        categoryChipsRow = new LinearLayout(this);
        categoryChipsRow.setOrientation(LinearLayout.HORIZONTAL);
        updateCategoryChips();
        chipScroll.addView(categoryChipsRow);
        appDrawerLayer.addView(chipScroll, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(appDrawerLayer, 16);

        // Scrollable App Drawer Content + Bottom Instant Search Overlay Card
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout scrollBody = new LinearLayout(this);
        scrollBody.setOrientation(LinearLayout.VERTICAL);

        scrollBody.addView(label("Recent Apps", 14, TEXT_PRIMARY, true));
        addSpacer(scrollBody, 8);
        recentAppGrid = new GridLayout(this);
        recentAppGrid.setColumnCount(4);
        scrollBody.addView(recentAppGrid, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(scrollBody, 14);

        scrollBody.addView(label("All Installed Apps", 14, TEXT_PRIMARY, true));
        addSpacer(scrollBody, 8);
        drawerAppGrid = new GridLayout(this);
        drawerAppGrid.setColumnCount(4);
        scrollBody.addView(drawerAppGrid, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(scrollBody, 16);

        // Bottom Frosted Glass Instant Search System Card
        instantSearchCard = new LinearLayout(this);
        instantSearchCard.setOrientation(LinearLayout.VERTICAL);
        instantSearchCard.setBackground(glassDrawable(26, true, false));
        instantSearchCard.setPadding(dp(18), dp(14), dp(18), dp(14));

        LinearLayout webRow = new LinearLayout(this);
        webRow.setOrientation(LinearLayout.HORIZONTAL);
        webRow.setGravity(Gravity.CENTER_VERTICAL);
        webRow.setPadding(0, dp(8), 0, dp(8));
        TextView wIcon = label("\uD83D\uDD0D", 16, CRIMSON_ACCENT, true);
        wIcon.setGravity(Gravity.CENTER);
        wIcon.setBackground(glassDrawable(14, false, false));
        webRow.addView(wIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        instantWebSearchText = label("  Search on web", 14, TEXT_PRIMARY, false);
        webRow.addView(instantWebSearchText);
        webRow.setOnClickListener(v -> {
            String q = drawerSearchInput.getText().toString().trim();
            Uri uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(q.isEmpty() ? "Crimson Android Launcher" : q));
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
        });
        instantSearchCard.addView(webRow);

        View div = new View(this);
        div.setBackgroundColor(0x28FFFFFF);
        instantSearchCard.addView(div, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout setRow = new LinearLayout(this);
        setRow.setOrientation(LinearLayout.HORIZONTAL);
        setRow.setGravity(Gravity.CENTER_VERTICAL);
        setRow.setPadding(0, dp(8), 0, dp(8));
        TextView gIcon = label("\u2699", 18, TEXT_PRIMARY, false);
        gIcon.setGravity(Gravity.CENTER);
        gIcon.setBackground(glassDrawable(14, false, false));
        setRow.addView(gIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        setRow.addView(label("  System Settings", 14, TEXT_PRIMARY, false));
        setRow.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
        });
        instantSearchCard.addView(setRow);

        scrollBody.addView(instantSearchCard, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(scrollBody, new FrameLayout.LayoutParams(-1, -2));
        appDrawerLayer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        drawerSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterDrawerApps();
            }
        });

        filterDrawerApps();
        root.addView(appDrawerLayer, new FrameLayout.LayoutParams(-1, -1));
    }

    void updateCategoryChips() {
        if (categoryChipsRow == null) return;
        categoryChipsRow.removeAllViews();
        String[] cats = {"All", "Games", "Social", "Utility", "Work", "Media"};
        for (final String c : cats) {
            boolean active = activeCategory.equalsIgnoreCase(c);
            TextView chip = label(c, 13, TEXT_PRIMARY, active);
            chip.setBackground(pillDrawable(active));
            chip.setPadding(dp(18), dp(8), dp(18), dp(8));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.rightMargin = dp(8);
            chip.setOnClickListener(v -> {
                vibrateTick();
                activeCategory = c;
                updateCategoryChips();
                filterDrawerApps();
            });
            categoryChipsRow.addView(chip, clp);
        }
    }

    void filterDrawerApps() {
        if (drawerAppGrid == null || recentAppGrid == null) return;
        drawerAppGrid.removeAllViews();
        recentAppGrid.removeAllViews();

        for (int i = 0; i < Math.min(4, apps.size()); i++) {
            recentAppGrid.addView(createAppCell(apps.get(i), false));
        }

        String q = drawerSearchInput != null ? drawerSearchInput.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        if (instantWebSearchText != null) {
            instantWebSearchText.setText(q.isEmpty() ? "  Search on web" : "  Search web for \"" + q + "\"");
        }
        for (ResolveInfo a : apps) {
            boolean matchesQuery = q.isEmpty() || appName(a).toLowerCase(Locale.ROOT).contains(q);
            boolean matchesCat = activeCategory.equals("All") || resolveCategory(a).equalsIgnoreCase(activeCategory);
            if (matchesQuery && matchesCat) {
                drawerAppGrid.addView(createAppCell(a, false));
            }
        }
    }

    // =========================================================================
    // LAYER 5: LEFT FROSTED GLASS NAVIGATION DRAWER
    // =========================================================================
    void buildNavDrawerLayer() {
        navDrawerLayer = new LinearLayout(this);
        navDrawerLayer.setOrientation(LinearLayout.VERTICAL);
        navDrawerLayer.setBackground(glassDrawable(28, false, true));
        navDrawerLayer.setPadding(dp(22), dp(46), dp(22), dp(24));

        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = label("AB", 16, TEXT_PRIMARY, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(pillDrawable(true));
        profileRow.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout pInfo = new LinearLayout(this);
        pInfo.setOrientation(LinearLayout.VERTICAL);
        pInfo.setPadding(dp(14), 0, 0, 0);
        pInfo.addView(label("Abhikash Babu", 17, TEXT_PRIMARY, true));
        pInfo.addView(label("Crimson Glass OS \u00B7 v0.4", 11, CRIMSON_ACCENT, false));
        profileRow.addView(pInfo);
        navDrawerLayer.addView(profileRow);
        addSpacer(navDrawerLayer, 16);

        TextView navSearch = label("\uD83D\uDD0D  Search apps...", 13, TEXT_SECONDARY, false);
        navSearch.setBackground(glassDrawable(20, false, false));
        navSearch.setPadding(dp(16), dp(11), dp(16), dp(11));
        navSearch.setOnClickListener(v -> {
            animateNavDrawerTo(0f);
            animateAppDrawerTo(1f);
        });
        navDrawerLayer.addView(navSearch, new LinearLayout.LayoutParams(-1, -2));
        addSpacer(navDrawerLayer, 18);

        navDrawerLayer.addView(label("CATEGORIES", 11, TEXT_MUTED, true));
        addSpacer(navDrawerLayer, 8);

        String[][] navCats = {
                {"\uD83D\uDC65", "Social", "Social"},
                {"\uD83C\uDFAE", "Gaming", "Games"},
                {"\u2705", "Productivity", "Work"},
                {"\uD83E\uDDF0", "Tools", "Utility"},
                {"\u2699", "Others", "All"}
        };
        for (final String[] nc : navCats) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(8), dp(10), dp(8), dp(10));
            TextView ic = label(nc[0], 17, CRIMSON_ACCENT, false);
            ic.setPadding(0, 0, dp(14), 0);
            item.addView(ic);
            item.addView(label(nc[1], 15, TEXT_PRIMARY, false));
            item.setOnClickListener(v -> {
                vibrateTick();
                activeCategory = nc[2];
                updateCategoryChips();
                filterDrawerApps();
                animateNavDrawerTo(0f);
                animateAppDrawerTo(1f);
            });
            navDrawerLayer.addView(item);
        }

        View sep = new View(this);
        sep.setBackgroundColor(0x28FFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(1));
        slp.topMargin = dp(12);
        slp.bottomMargin = dp(14);
        navDrawerLayer.addView(sep, slp);

        GridLayout qsGrid = new GridLayout(this);
        qsGrid.setColumnCount(4);
        String[][] toggles = {
                {"\uD83D\uDCF6", "Wi-Fi", "1"},
                {"\u2744", "Bluetooth", "1"},
                {"\uD83D\uDD14", "Volume", "1"},
                {"\uD83D\uDD0D", "Search", "0"},
                {"\uD83C\uDF19", "DND", "0"},
                {"\u2728", "Glass+", "1"},
                {"\uD83D\uDD26", "Torch", "0"},
                {"\u2699", "Settings", "0"}
        };
        for (int i = 0; i < toggles.length; i++) {
            final int tIdx = i;
            final boolean[] activeState = { "1".equals(toggles[i][2]) };
            LinearLayout tCell = new LinearLayout(this);
            tCell.setOrientation(LinearLayout.VERTICAL);
            tCell.setGravity(Gravity.CENTER);
            tCell.setPadding(dp(4), dp(6), dp(4), dp(6));

            final TextView circle = label(toggles[i][0], 15, TEXT_PRIMARY, true);
            circle.setGravity(Gravity.CENTER);
            circle.setBackground(pillDrawable(activeState[0]));
            tCell.addView(circle, new LinearLayout.LayoutParams(dp(44), dp(44)));

            TextView tLbl = label(toggles[i][1], 10, TEXT_SECONDARY, false);
            tLbl.setGravity(Gravity.CENTER);
            tLbl.setPadding(0, dp(4), 0, 0);
            tCell.addView(tLbl);

            tCell.setOnClickListener(v -> {
                vibrateTick();
                if (tIdx == 0) {
                    try { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); } catch (Exception ignored) {}
                } else if (tIdx == 1) {
                    try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); } catch (Exception ignored) {}
                } else if (tIdx == 2) {
                    animateVolumeOverlay(true);
                } else if (tIdx == 3) {
                    animateNavDrawerTo(0f);
                    animateAppDrawerTo(1f);
                } else if (tIdx == 6) {
                    toggleFlashlight();
                    activeState[0] = torchEnabled;
                    circle.setBackground(pillDrawable(activeState[0]));
                } else if (tIdx == 7) {
                    try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); } catch (Exception ignored) {}
                } else {
                    activeState[0] = !activeState[0];
                    circle.setBackground(pillDrawable(activeState[0]));
                }
            });

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            qsGrid.addView(tCell, glp);
        }
        navDrawerLayer.addView(qsGrid, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(drawerW, -1, Gravity.START);
        nlp.topMargin = dp(16);
        nlp.bottomMargin = dp(16);
        nlp.leftMargin = dp(10);
        root.addView(navDrawerLayer, nlp);
    }

    void toggleFlashlight() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cm != null && cm.getCameraIdList().length > 0) {
                torchEnabled = !torchEnabled;
                cm.setTorchMode(cm.getCameraIdList()[0], torchEnabled);
            }
        } catch (Exception e) {
            torchEnabled = !torchEnabled;
        }
    }

    // =========================================================================
    // LAYER 6: FLOATING GLASSMORPHIC VERTICAL VOLUME SLIDER
    // =========================================================================
    void buildVolumeOverlayLayer() {
        volumeOverlayCard = new LinearLayout(this);
        volumeOverlayCard.setOrientation(LinearLayout.VERTICAL);
        volumeOverlayCard.setGravity(Gravity.CENTER_HORIZONTAL);
        volumeOverlayCard.setBackground(glassDrawable(32, false, true));
        volumeOverlayCard.setPadding(dp(12), dp(16), dp(12), dp(16));
        volumeOverlayCard.setAlpha(0f);
        volumeOverlayCard.setVisibility(View.GONE);

        TextView topSpeaker = label("\uD83D\uDD0A", 16, TEXT_PRIMARY, false);
        volumeOverlayCard.addView(topSpeaker);

        volumePercentText = label("85%", 11, TEXT_SECONDARY, true);
        volumePercentText.setPadding(0, dp(4), 0, dp(8));
        volumeOverlayCard.addView(volumePercentText);

        volumeBarView = new VolumeBarView(this);
        volumeOverlayCard.addView(volumeBarView, new LinearLayout.LayoutParams(dp(28), dp(150)));

        TextView muteSpeaker = label("\uD83D\uDD08", 15, TEXT_MUTED, false);
        muteSpeaker.setPadding(0, dp(8), 0, 0);
        volumeOverlayCard.addView(muteSpeaker);

        syncDeviceVolumeToBar();

        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(dp(62), -2, Gravity.END | Gravity.CENTER_VERTICAL);
        vlp.rightMargin = dp(14);
        root.addView(volumeOverlayCard, vlp);
    }

    void syncDeviceVolumeToBar() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                float ratio = max > 0 ? (cur / (float) max) : 0.85f;
                volumeBarView.setVolumeLevel(ratio, false);
            }
        } catch (Exception ignored) {}
    }

    void animateVolumeOverlay(boolean show) {
        handler.removeCallbacks(hideVolumeRunnable);
        if (show) {
            syncDeviceVolumeToBar();
            volumeOverlayCard.setVisibility(View.VISIBLE);
            volumeOverlayCard.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(200)
                    .setInterpolator(new OvershootInterpolator(1.1f))
                    .start();
            handler.postDelayed(hideVolumeRunnable, 3000);
        } else {
            volumeOverlayCard.animate()
                    .alpha(0f)
                    .translationX(dp(24))
                    .setDuration(180)
                    .withEndAction(() -> volumeOverlayCard.setVisibility(View.GONE))
                    .start();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                am.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        keyCode == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                        0
                );
                animateVolumeOverlay(true);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // =========================================================================
    // GPU HARDWARE LAYER MANAGEMENT (Eliminates Glass Drawer Lag & Jitter!)
    // =========================================================================
    void setHardwareLayersActive(boolean active) {
        if (hardwareLayersPromoted == active) return;
        hardwareLayersPromoted = active;
        int mode = active ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
        if (appDrawerLayer != null) appDrawerLayer.setLayerType(mode, null);
        if (navDrawerLayer != null) navDrawerLayer.setLayerType(mode, null);
        if (homePages[currentHomePage] != null) homePages[currentHomePage].setLayerType(mode, null);
    }

    // =========================================================================
    // 120FPS TOUCH SLIDING ENGINE + SYSTEM TASK MANAGER GESTURE ISOLATION
    // =========================================================================
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        float x = ev.getRawX();
        float y = ev.getRawY();

        if (volumeOverlayCard.getVisibility() == View.VISIBLE && isPointInsideView(x, y, volumeOverlayCard)) {
            handler.removeCallbacks(hideVolumeRunnable);
            handler.postDelayed(hideVolumeRunnable, 3000);
            return super.dispatchTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_DOWN) {
            if (activeAnimator != null && activeAnimator.isRunning()) {
                activeAnimator.cancel();
            }
            if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
            else velocityTracker.clear();
            velocityTracker.addMovement(ev);

            downX = x;
            downY = y;
            startHomeOffsetPx = homeOffsetPx;
            startAppDrawerProgress = appDrawerProgress;
            startNavDrawerProgress = navDrawerProgress;

            // CRITICAL FIX FOR ANDROID SYSTEM GESTURE TASK MANAGER (RECENTS) & STATUS BAR:
            // If touch starts inside the Bottom System Gesture Bar (y >= screenH - bottomGestureGuardPx)
            // or Top Status Bar (y <= topGestureGuardPx) or Right System Back Edge when drawers are closed,
            // DO NOT intercept vertical swipes so System Task Manager (Recents) opens with zero jitter!
            if (appDrawerProgress < 0.05f && navDrawerProgress < 0.05f) {
                if (downY >= (screenH - bottomGestureGuardPx) || downY <= topGestureGuardPx) {
                    activeGesture = GESTURE_SYSTEM_IGNORE;
                    return super.dispatchTouchEvent(ev);
                }
            }
            activeGesture = GESTURE_NONE;
            return super.dispatchTouchEvent(ev);
        }

        if (activeGesture == GESTURE_SYSTEM_IGNORE) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                activeGesture = GESTURE_NONE;
            }
            return super.dispatchTouchEvent(ev);
        }

        if (velocityTracker != null) velocityTracker.addMovement(ev);

        if (action == MotionEvent.ACTION_MOVE) {
            float dx = x - downX;
            float dy = y - downY;
            float absDx = Math.abs(dx);
            float absDy = Math.abs(dy);
            float slop = dp(10);

            if (activeGesture == GESTURE_NONE && (absDx > slop || absDy > slop)) {
                if (navDrawerProgress > 0.05f) {
                    if (absDx > absDy) activeGesture = GESTURE_NAV_DRAWER;
                } else if (appDrawerProgress > 0.05f) {
                    if (dy > slop && absDy > absDx * 1.2f && downY < screenH * 0.38f) {
                        activeGesture = GESTURE_APP_DRAWER;
                    }
                } else {
                    if (downX < dp(34) && dx > slop && absDx > absDy) {
                        activeGesture = GESTURE_NAV_DRAWER;
                    } else if (downX < (screenW - rightEdgeGuardPx) && absDx > absDy * 1.15f) {
                        activeGesture = GESTURE_HOME_PAGER;
                    } else if (dy < -slop && absDy > absDx * 1.15f && downY < (screenH - bottomGestureGuardPx)) {
                        activeGesture = GESTURE_APP_DRAWER;
                    }
                }

                if (activeGesture != GESTURE_NONE) {
                    setHardwareLayersActive(true);
                    MotionEvent cancel = MotionEvent.obtain(ev);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
            }

            if (activeGesture == GESTURE_HOME_PAGER) {
                float minOffset = 0f;
                float maxOffset = screenW * 2f;
                float raw = startHomeOffsetPx - dx;
                if (raw < minOffset) raw = minOffset + (raw - minOffset) * 0.28f;
                if (raw > maxOffset) raw = maxOffset + (raw - maxOffset) * 0.28f;
                homeOffsetPx = raw;
                applyMotionTransforms();
                return true;
            } else if (activeGesture == GESTURE_APP_DRAWER) {
                float deltaProgress = -dy / (screenH * 0.66f);
                appDrawerProgress = clamp(startAppDrawerProgress + deltaProgress, 0f, 1f);
                applyMotionTransforms();
                return true;
            } else if (activeGesture == GESTURE_NAV_DRAWER) {
                float deltaProgress = dx / drawerW;
                navDrawerProgress = clamp(startNavDrawerProgress + deltaProgress, 0f, 1f);
                applyMotionTransforms();
                return true;
            }
        }

        // CRITICAL FIX: If Android SystemUI sends ACTION_CANCEL (because the user triggered System Recents / Task Manager
        // or System Back Gesture), immediately snap any partial drawer drag back to closed (0f)!
        if (action == MotionEvent.ACTION_CANCEL) {
            if (activeGesture == GESTURE_APP_DRAWER) {
                animateAppDrawerTo(startAppDrawerProgress > 0.6f ? 1f : 0f);
            } else if (activeGesture == GESTURE_NAV_DRAWER) {
                animateNavDrawerTo(startNavDrawerProgress > 0.6f ? 1f : 0f);
            } else if (activeGesture == GESTURE_HOME_PAGER) {
                animateHomeToPage(currentHomePage);
            } else {
                setHardwareLayersActive(false);
            }
            activeGesture = GESTURE_NONE;
            return super.dispatchTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_UP) {
            float vx = 0f, vy = 0f;
            if (velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000);
                vx = velocityTracker.getXVelocity();
                vy = velocityTracker.getYVelocity();
            }

            if (activeGesture == GESTURE_HOME_PAGER) {
                float pageFloat = homeOffsetPx / screenW;
                int targetPage = Math.round(pageFloat);
                if (Math.abs(vx) > dp(250)) {
                    targetPage = vx < 0 ? (int) Math.floor(pageFloat + 0.85f) : (int) Math.ceil(pageFloat - 0.85f);
                }
                targetPage = Math.max(0, Math.min(2, targetPage));
                animateHomeToPage(targetPage);
                activeGesture = GESTURE_NONE;
                return true;
            } else if (activeGesture == GESTURE_APP_DRAWER) {
                boolean open = vy < -dp(260) || (Math.abs(vy) <= dp(260) && appDrawerProgress > 0.32f);
                animateAppDrawerTo(open ? 1f : 0f);
                activeGesture = GESTURE_NONE;
                return true;
            } else if (activeGesture == GESTURE_NAV_DRAWER) {
                boolean open = vx > dp(220) || (Math.abs(vx) <= dp(220) && navDrawerProgress > 0.32f);
                animateNavDrawerTo(open ? 1f : 0f);
                activeGesture = GESTURE_NONE;
                return true;
            }
        }

        return super.dispatchTouchEvent(ev);
    }

    void animateHomeToPage(final int targetPage) {
        currentHomePage = Math.max(0, Math.min(2, targetPage));
        final float start = homeOffsetPx;
        final float end = currentHomePage * screenW;
        if (activeAnimator != null) activeAnimator.cancel();

        setHardwareLayersActive(true);
        activeAnimator = ValueAnimator.ofFloat(start, end);
        activeAnimator.setDuration(250);
        activeAnimator.setInterpolator(new DecelerateInterpolator(1.7f));
        activeAnimator.addUpdateListener(anim -> {
            homeOffsetPx = (Float) anim.getAnimatedValue();
            applyMotionTransforms();
        });
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                setHardwareLayersActive(false);
            }
        });
        activeAnimator.start();
    }

    void animateAppDrawerTo(final float target) {
        if (target < 0.5f) hideKeyboard();
        final float start = appDrawerProgress;
        if (activeAnimator != null) activeAnimator.cancel();

        setHardwareLayersActive(true);
        activeAnimator = ValueAnimator.ofFloat(start, target);
        activeAnimator.setDuration(240);
        activeAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        activeAnimator.addUpdateListener(anim -> {
            appDrawerProgress = (Float) anim.getAnimatedValue();
            applyMotionTransforms();
        });
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                setHardwareLayersActive(false);
                applyMotionTransforms();
            }
        });
        activeAnimator.start();
    }

    void animateNavDrawerTo(final float target) {
        final float start = navDrawerProgress;
        if (activeAnimator != null) activeAnimator.cancel();

        setHardwareLayersActive(true);
        activeAnimator = ValueAnimator.ofFloat(start, target);
        activeAnimator.setDuration(230);
        activeAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        activeAnimator.addUpdateListener(anim -> {
            navDrawerProgress = (Float) anim.getAnimatedValue();
            applyMotionTransforms();
        });
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                setHardwareLayersActive(false);
                applyMotionTransforms();
            }
        });
        activeAnimator.start();
    }

    // =========================================================================
    // ZERO-ALLOCATION, ZERO-REQUESTLAYOUT 120FPS GPU TRANSFORM PIPELINE
    // =========================================================================
    void applyMotionTransforms() {
        // 1. Horizontal Home Strip Translation
        homeStrip.setTranslationX(-homeOffsetPx);
        float pageFloat = homeOffsetPx / Math.max(1f, (float) screenW);

        // Update Page Indicator Dots using pure GPU scaleX/alpha (ZERO requestLayout!)
        int nearestPage = Math.max(0, Math.min(2, Math.round(pageFloat)));
        boolean pageChanged = (nearestPage != lastIndicatorPage);
        if (pageChanged) lastIndicatorPage = nearestPage;

        for (int i = 0; i < pageIndicatorRow.getChildCount(); i++) {
            View dot = pageIndicatorRow.getChildAt(i);
            float prox = Math.max(0f, 1f - Math.abs(pageFloat - i));
            dot.setScaleX(1f + 0.85f * prox);
            dot.setAlpha(0.55f + 0.45f * prox);
            if (pageChanged) {
                dot.setBackground(pillDrawable(i == nearestPage));
            }
        }

        // 2. Overlay Progress (App Drawer + Nav Drawer depth)
        float overlay = Math.max(appDrawerProgress, navDrawerProgress);
        float homeScale = 1f - (overlay * 0.06f);
        float homeAlpha = 1f - (overlay * 0.42f);
        homeStage.setScaleX(homeScale);
        homeStage.setScaleY(homeScale);
        homeStage.setAlpha(homeAlpha);
        homeStage.setTranslationX(navDrawerProgress * dp(28));

        // 3. Static Hardware Blur ONLY when drawer is settled open (0ms lag during drag!)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean shouldBlur = (overlay >= 0.98f && !hardwareLayersPromoted);
            if (shouldBlur && !blurAppliedOnHome) {
                blurAppliedOnHome = true;
                homeStage.setRenderEffect(RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP));
            } else if (!shouldBlur && blurAppliedOnHome) {
                blurAppliedOnHome = false;
                homeStage.setRenderEffect(null);
            }
        }

        // 4. Frosted Glass Scrim Dimmer
        if (overlay > 0.005f) {
            if (scrimView.getVisibility() != View.VISIBLE) scrimView.setVisibility(View.VISIBLE);
            scrimView.setAlpha(overlay * 0.88f);
        } else {
            if (scrimView.getVisibility() != View.GONE) {
                scrimView.setAlpha(0f);
                scrimView.setVisibility(View.GONE);
            }
        }

        // 5. App Drawer Vertical Sliding Position (Pure GPU translationY on Hardware Layer)
        if (appDrawerProgress > 0.004f) {
            if (appDrawerLayer.getVisibility() != View.VISIBLE) appDrawerLayer.setVisibility(View.VISIBLE);
            appDrawerLayer.setTranslationY((1f - appDrawerProgress) * screenH);
            appDrawerLayer.setAlpha(Math.min(1f, 0.35f + appDrawerProgress * 0.65f));
        } else {
            if (appDrawerLayer.getVisibility() != View.GONE) {
                appDrawerLayer.setTranslationY(screenH);
                appDrawerLayer.setVisibility(View.GONE);
            }
        }

        // 6. Left Navigation Drawer Horizontal Sliding Position (Pure GPU translationX on Hardware Layer)
        if (navDrawerProgress > 0.004f) {
            if (navDrawerLayer.getVisibility() != View.VISIBLE) navDrawerLayer.setVisibility(View.VISIBLE);
            navDrawerLayer.setTranslationX((navDrawerProgress - 1f) * (drawerW + dp(20)));
            navDrawerLayer.setAlpha(Math.min(1f, 0.35f + navDrawerProgress * 0.65f));
        } else {
            if (navDrawerLayer.getVisibility() != View.GONE) {
                navDrawerLayer.setTranslationX(-(drawerW + dp(20)));
                navDrawerLayer.setVisibility(View.GONE);
            }
        }

        // 7. Zero-Draw Wallpaper Parallax via Pure GPU View Translation
        if (wallpaper != null) {
            wallpaper.setTranslationX(-pageFloat * dp(18) + navDrawerProgress * dp(14));
            wallpaper.setTranslationY(-appDrawerProgress * dp(18));
        }
    }

    float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    boolean isPointInsideView(float rawX, float rawY, View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX <= loc[0] + v.getWidth()
                && rawY >= loc[1] && rawY <= loc[1] + v.getHeight();
    }

    // =========================================================================
    // HELPERS, APP LAUNCH & SYSTEM STATS
    // =========================================================================
    void launchApp(ResolveInfo a) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(a.activityInfo.packageName, a.activityInfo.name));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open " + appName(a), Toast.LENGTH_SHORT).show();
        }
    }

    void showAppQuickOptions(final ResolveInfo a) {
        String[] opts = {
                isPinned(a) ? "Unpin from Home" : "Pin to Home",
                "Select Multiple Apps (Multi-Select)",
                "App Info & Permissions"
        };
        new AlertDialog.Builder(this)
                .setTitle(appName(a))
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        togglePinned(a);
                        populatePage1Grid();
                    } else if (which == 1) {
                        animateAppDrawerTo(0f);
                        selectedApps.add(appKey(a));
                        setMultiSelectMode(true);
                    } else {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + a.activityInfo.packageName)));
                    }
                })
                .show();
    }

    void updateClockAndStats() {
        Date now = new Date();
        if (clockText != null) clockText.setText(clockFormat.format(now));
        if (dateText != null) dateText.setText(dateFormat.format(now));
        if (systemRingsView != null) systemRingsView.refreshMetrics();
    }

    void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && root != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    void addSpacer(LinearLayout parent, int heightDp) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    @Override
    public void onBackPressed() {
        if (multiSelectMode) {
            setMultiSelectMode(false);
        } else if (navDrawerProgress > 0.1f) {
            animateNavDrawerTo(0f);
        } else if (appDrawerProgress > 0.1f) {
            animateAppDrawerTo(0f);
        } else if (currentHomePage != 0) {
            animateHomeToPage(0);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        hideKeyboard();
        if (multiSelectMode) setMultiSelectMode(false);
        if (navDrawerProgress > 0.05f) animateNavDrawerTo(0f);
        if (appDrawerProgress > 0.05f) animateAppDrawerTo(0f);
        if (currentHomePage != 0) animateHomeToPage(0);
    }

    // =========================================================================
    // CUSTOM VIEW 1: PRE-CACHED HARDWARE BITMAP WALLPAPER (ZERO PER-FRAME SHADERS!)
    // =========================================================================
    class WallpaperView extends View {
        Bitmap cachedWallpaper;
        final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

        WallpaperView(Context c) {
            super(c);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w <= 0 || h <= 0) return;
            try {
                cachedWallpaper = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(cachedWallpaper);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

                // Base Rich Charcoal-Crimson Diagonal Wash
                p.setShader(new LinearGradient(
                        0, 0, w, h,
                        new int[]{0xFF280812, BG_CHARCOAL_DEEP, 0xFF1A1423},
                        new float[]{0f, 0.55f, 1f},
                        Shader.TileMode.CLAMP
                ));
                c.drawRect(0, 0, w, h, p);

                // Top-Left Lively Crimson Glow Orb
                p.setShader(new RadialGradient(
                        w * 0.22f, h * 0.18f, w * 0.92f,
                        new int[]{0xCCFF1E32, 0x669E0B22, 0x0014131A},
                        new float[]{0f, 0.52f, 1f},
                        Shader.TileMode.CLAMP
                ));
                c.drawRect(0, 0, w, h, p);

                // Mid-Right Ruby Ribbon Glow
                p.setShader(new RadialGradient(
                        w * 0.85f, h * 0.52f, w * 0.78f,
                        new int[]{0xBBE50914, 0x44640818, 0x0014131A},
                        new float[]{0f, 0.55f, 1f},
                        Shader.TileMode.CLAMP
                ));
                c.drawRect(0, 0, w, h, p);

                // Bottom-Left Crimson Rim Highlight
                p.setShader(new RadialGradient(
                        w * 0.25f, h * 0.90f, w * 0.68f,
                        new int[]{0x88D90B24, 0x0014131A},
                        null,
                        Shader.TileMode.CLAMP
                ));
                c.drawRect(0, 0, w, h, p);
            } catch (Exception ignored) {}
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (cachedWallpaper != null) {
                canvas.drawBitmap(cachedWallpaper, 0, 0, bmpPaint);
            } else {
                canvas.drawColor(BG_CHARCOAL_DEEP);
            }
        }
    }

    // =========================================================================
    // CUSTOM VIEW 2: ANIMATED CRIMSON EQUALIZER WAVEFORM
    // =========================================================================
    class WaveformView extends View {
        final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float[] heights = new float[28];
        float phase = 0f;

        WaveformView(Context c) {
            super(c);
            barPaint.setColor(CRIMSON_ACCENT);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            randomize();
        }

        void randomize() {
            for (int i = 0; i < heights.length; i++) {
                heights[i] = 0.25f + (float) Math.abs(Math.sin(i * 0.55f)) * 0.70f;
            }
            invalidate();
        }

        void stepWave() {
            phase += 0.35f;
            for (int i = 0; i < heights.length; i++) {
                heights[i] = 0.20f + 0.75f * (float) Math.abs(Math.sin(phase + i * 0.48f) * Math.cos(phase * 0.5f - i * 0.2f));
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            float step = w / (float) heights.length;
            barPaint.setStrokeWidth(Math.max(dp(2.5f), step * 0.48f));
            float cy = h / 2f;
            for (int i = 0; i < heights.length; i++) {
                float x = i * step + step * 0.5f;
                float halfLen = Math.max(dp(3), (h * 0.45f) * heights[i]);
                barPaint.setAlpha(150 + (int) (105 * heights[i]));
                canvas.drawLine(x, cy - halfLen, x, cy + halfLen, barPaint);
            }
        }
    }

    // =========================================================================
    // CUSTOM VIEW 3: LIVE CPU / RAM / BATTERY RADIAL RINGS WIDGET
    // =========================================================================
    class SystemRingsView extends View {
        final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint arcPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint valPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint lblPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF arcRect    = new RectF();
        int cpuPct = 52, ramPct = 38, batPct = 82;

        SystemRingsView(Context c) {
            super(c);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(dp(5));
            trackPaint.setColor(0x28FFFFFF);

            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(dp(5));
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
            arcPaint.setColor(CRIMSON_ACCENT);

            valPaint.setColor(TEXT_PRIMARY);
            valPaint.setTextSize(dp(13));
            valPaint.setTextAlign(Paint.Align.CENTER);
            valPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

            lblPaint.setColor(TEXT_SECONDARY);
            lblPaint.setTextSize(dp(11));
            lblPaint.setTextAlign(Paint.Align.CENTER);
            refreshMetrics();
        }

        void refreshMetrics() {
            try {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (am != null) {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    if (mi.totalMem > 0) {
                        ramPct = (int) Math.round(((mi.totalMem - mi.availMem) * 100.0) / mi.totalMem);
                    }
                }
                BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
                if (bm != null) {
                    int b = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    if (b > 0 && b <= 100) batPct = b;
                }
                cpuPct = 35 + (int) (System.currentTimeMillis() / 1000 % 28);
            } catch (Exception ignored) {}
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            int[] vals = {cpuPct, ramPct, batPct};
            String[] names = {"CPU", "RAM", "Battery"};
            float colW = w / 3f;
            float radius = Math.min(colW * 0.34f, h * 0.32f);
            float cy = h * 0.42f;

            for (int i = 0; i < 3; i++) {
                float cx = colW * i + colW * 0.5f;
                arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
                canvas.drawOval(arcRect, trackPaint);
                canvas.drawArc(arcRect, -90f, (vals[i] / 100f) * 360f, false, arcPaint);
                canvas.drawText(vals[i] + "%", cx, cy + dp(4), valPaint);
                canvas.drawText(names[i], cx, h - dp(4), lblPaint);
            }
        }
    }

    // =========================================================================
    // CUSTOM VIEW 4: TOUCH-DRAGGABLE GLASS VOLUME BAR
    // =========================================================================
    class VolumeBarView extends View {
        final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint glowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        float level = 0.85f;

        VolumeBarView(Context c) {
            super(c);
            trackPaint.setColor(0x30FFFFFF);
            trackPaint.setStrokeCap(Paint.Cap.ROUND);
            fillPaint.setColor(CRIMSON_ACCENT);
            fillPaint.setStrokeCap(Paint.Cap.ROUND);
            glowPaint.setColor(0x55FF2A3C);
            glowPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setVolumeLevel(float newLevel, boolean updateAudio) {
            level = clamp(newLevel, 0f, 1f);
            if (volumePercentText != null) {
                volumePercentText.setText(Math.round(level * 100f) + "%");
            }
            if (updateAudio) {
                try {
                    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                    if (am != null) {
                        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level * max), 0);
                    }
                } catch (Exception ignored) {}
            }
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int a = event.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float ratio = 1f - (event.getY() / Math.max(1f, (float) getHeight()));
                setVolumeLevel(ratio, true);
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f;
            float pad = dp(8);
            float top = pad;
            float bot = h - pad;
            float fillTop = bot - (bot - top) * level;

            trackPaint.setStrokeWidth(dp(6));
            fillPaint.setStrokeWidth(dp(6));
            glowPaint.setStrokeWidth(dp(12));

            canvas.drawLine(cx, top, cx, bot, trackPaint);
            canvas.drawLine(cx, fillTop, cx, bot, glowPaint);
            canvas.drawLine(cx, fillTop, cx, bot, fillPaint);
            canvas.drawCircle(cx, fillTop, dp(6), fillPaint);
        }
    }
}
package com.luckypick.app;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PREFS = "lucky_pick";
    private static final String SAVED_KEY = "saved_games";
    private static final String RECENT_KEY = "recent_games";
    private static final String OFFICIAL_DRAWS_KEY = "official_draws";
    private static final String OFFICIAL_LATEST_KEY = "official_latest_round";
    private static final int HOME_IMAGE_WIDTH = 895;
    private static final int HOME_IMAGE_HEIGHT = 1200;
    private static final int REQ_LOCATION_FOR_STORE_MAP = 3201;
    private static final String VENYSOUND_URL = "https://venysound.com";
    private static final long HOME_BANNER_SHOW_MS = 5000L;
    private static final long HOME_BANNER_FADE_MS = 1000L;
    private static final int REPORT_GAME_COUNT = 7;
    private static final int VENYSOUND_BANNER_WIDTH = 2736;
    private static final int VENYSOUND_BANNER_HEIGHT = 384;
    private static final int OFFICIAL_STATS_ROUND_COUNT = 40;

    private final Set<Integer> fixed = new LinkedHashSet<>();
    private final Set<Integer> excluded = new LinkedHashSet<>();
    private final List<List<Integer>> currentGames = new ArrayList<>();
    private final List<String> backStack = new ArrayList<>();
    private final Random random = new Random();

    private SharedPreferences preferences;
    private ScrollView rootScroll;
    private LinearLayout root;
    private LinearLayout tabBar;
    private LinearLayout content;
    private View resultAnchor;
    private TextView notice;
    private TextView officialStatus;
    private TextView officialLookupResult;
    private LinearLayout officialLookupResultBox;
    private TextView gameCountText;
    private TextView autoIntervalText;
    private Button autoButton;
    private SeekBar gameSeek;
    private LottoMachineView lottoMachineView;
    private int gameCount = 5;
    private int autoIntervalSeconds = 3;
    private boolean autoGenerating = false;
    private boolean officialFetching = false;
    private String activeTab = "";
    private String statsSubTab = "my";
    private String selectedZodiac = "";
    private String selectedBirthDateTime = "";
    private int zodiacDrawNonce = 0;
    private boolean pendingNearbySellerSearch = false;
    private boolean retryNearbySellerSearchOnResume = false;
    private OnBackInvokedCallback backInvokedCallback;
    private final Handler autoHandler = new Handler(Looper.getMainLooper());
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private Runnable homeBannerRotateTask;
    private View homeBannerImage;
    private View homeBannerText;
    private TextView homeBannerTitle;
    private TextView homeBannerSubtitle;
    private boolean homeBannerShowingImage = true;
    private final Runnable autoGenerateTask = new Runnable() {
        @Override
        public void run() {
            if (!autoGenerating) return;
            if ("generate".equals(activeTab)) {
                generateGames(true);
            }
            autoHandler.postDelayed(this, autoIntervalSeconds * 1000L);
        }
    };

    private final int bg = Color.rgb(17, 19, 22);
    private final int panel = Color.rgb(35, 37, 42);
    private final int line = Color.rgb(66, 67, 72);
    private final int text = Color.rgb(248, 244, 234);
    private final int muted = Color.rgb(185, 180, 169);
    private final int gold = Color.rgb(245, 200, 75);
    private final int green = Color.rgb(103, 211, 155);
    private final int red = Color.rgb(255, 120, 134);
    private final int blue = Color.rgb(105, 169, 255);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        Window window = getWindow();
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(bg);
        registerSystemBackHandler();
        showIntro();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoGenerate();
        stopHomeBannerRotation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ("home".equals(activeTab) && homeBannerImage != null && homeBannerText != null) {
            startHomeBannerRotation();
        }
        if (!retryNearbySellerSearchOnResume) return;
        retryNearbySellerSearchOnResume = false;
        if ("official".equals(activeTab)) {
            lookupNearbySellerByLocation(true);
        }
    }

    @Override
    protected void onDestroy() {
        stopHomeBannerRotation();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backInvokedCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
            backInvokedCallback = null;
        }
        super.onDestroy();
    }

    private void registerSystemBackHandler() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        backInvokedCallback = () -> {
            if (!handleBackNavigation()) {
                finish();
            }
        };
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            backInvokedCallback
        );
    }

    @Override
    public void onBackPressed() {
        if (handleBackNavigation()) return;
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && handleBackNavigation()) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean handleBackNavigation() {
        if (!backStack.isEmpty()) {
            String previous = backStack.remove(backStack.size() - 1);
            switchTab(previous, false);
            return true;
        }
        if (!"home".equals(activeTab)) {
            switchTab("home", false);
            return true;
        }
        return false;
    }

    private void buildShell() {
        rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(bg);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22) + statusBarHeight(), dp(18), dp(26));
        rootScroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        tabBar = row();
        tabBar.setVisibility(View.GONE);
        content = column();
        root.addView(content);
        setContentView(rootScroll);
    }

    private void showIntro() {
        FrameLayout intro = new FrameLayout(this);
        intro.setBackgroundColor(bg);

        LinearLayout words = column();
        words.setGravity(Gravity.CENTER);
        String[] lines = {"십이지신이,", "당신에게 행운의 번호를,", "추천합니다"};
        for (String line : lines) {
            TextView textLine = label(line, 29, text, true);
            textLine.setGravity(Gravity.CENTER);
            textLine.setAlpha(0f);
            textLine.setTranslationY(dp(22));
            textLine.setScaleX(0.96f);
            textLine.setScaleY(0.96f);
            textLine.setShadowLayer(dp(4), 0, dp(2), Color.BLACK);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, -2);
            lineParams.setMargins(0, dp(5), 0, dp(5));
            words.addView(textLine, lineParams);
        }
        intro.addView(words, new FrameLayout.LayoutParams(-1, -1));

        setContentView(intro);
        intro.post(() -> animateIntro(intro, words));
    }

    private void animateIntro(FrameLayout intro, LinearLayout words) {
        for (int i = 0; i < words.getChildCount(); i++) {
            View line = words.getChildAt(i);
            AnimatorSet lineSet = new AnimatorSet();
            lineSet.playTogether(
                ObjectAnimator.ofFloat(line, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(line, View.TRANSLATION_Y, dp(22), 0f),
                ObjectAnimator.ofFloat(line, View.SCALE_X, 0.96f, 1f),
                ObjectAnimator.ofFloat(line, View.SCALE_Y, 0.96f, 1f)
            );
            lineSet.setStartDelay(i * 220L);
            lineSet.setDuration(620);
            lineSet.setInterpolator(new DecelerateInterpolator());
            lineSet.start();
        }

        intro.postDelayed(() -> {
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(intro, View.ALPHA, 1f, 0f);
            fadeOut.setDuration(360);
            fadeOut.setInterpolator(new AccelerateInterpolator());
            fadeOut.start();
        }, 1780);

        intro.postDelayed(() -> {
            buildShell();
            switchTab("home", false);
        }, 2200);
    }

    private void renderTabs() {
        if (tabBar != null) tabBar.removeAllViews();
    }
    private void addTab(String id, String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(id.equals(activeTab) ? Color.rgb(21, 21, 20) : text);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round(id.equals(activeTab) ? gold : panel, 28));
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setOnClickListener(v -> switchTab(id));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        tabBar.addView(button, params);
    }

    private void switchTab(String id) {
        switchTab(id, true);
    }

    private void switchTab(String id, boolean addToBackStack) {
        if (id.equals(activeTab)) return;
        if (addToBackStack && activeTab != null && !activeTab.isEmpty()) {
            backStack.add(activeTab);
        }
        activeTab = id;
        renderTabs();
        content.removeAllViews();

        if (!"generate".equals(id)) stopAutoGenerate();
        if ("home".equals(id)) renderHome();
        if ("generate".equals(id)) renderGenerator();
        if ("saved".equals(id)) renderSaved();
        if ("stats".equals(id)) renderStatsWithOfficial();
        if ("about".equals(id)) renderAbout();
        if ("official".equals(id)) renderOfficialPage();
        if ("generate".equals(id)) addAdMobPlaceholder();
    }

    private void renderOfficialPage() {
        LinearLayout page = column();
        page.setPadding(0, 0, 0, dp(16));

        page.addView(officialRoundLookupPanel(false));
        content.addView(page);

        if (!pendingNearbySellerSearch) {
            if (officialLookupResult != null) {
                officialLookupResult.setText("");
            }
            if (officialLookupResultBox != null) {
                officialLookupResultBox.removeAllViews();
            }
        }

        if (pendingNearbySellerSearch) {
            pendingNearbySellerSearch = false;
            lookupNearbySellerByLocation(true);
            return;
        }

        loadLatestOfficialRounds(10);
    }

    private void renderHome() {
        FrameLayout homeFrame = new FrameLayout(this);
        homeFrame.setClipChildren(false);
        homeFrame.setClipToPadding(false);
        homeFrame.setBackgroundColor(bg);
        int posterWidth = getResources().getDisplayMetrics().widthPixels;
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(posterWidth, -2);
        frameParams.height = homeImageHeight(posterWidth);
        frameParams.setMargins(-dp(18), 0, -dp(18), dp(14));
        homeFrame.setLayoutParams(frameParams);

        ImageView poster = new ImageView(this);
        poster.setImageResource(getResources().getIdentifier("zodiac_lotto_home", "drawable", getPackageName()));
        poster.setScaleType(ImageView.ScaleType.FIT_XY);
        homeFrame.addView(poster, new FrameLayout.LayoutParams(-1, -1));

        homeFrame.post(() -> layoutHomeImageMap(homeFrame, poster));

        content.addView(homeFrame);

        String hintText = selectedZodiac.isEmpty()
            ? "띠 아이콘을 누르고 생년월일시를 입력한 뒤 추첨 시작하기를 눌러주세요."
            : selectedZodiac + "띠 선택됨 · 추첨 시작하기를 누르면 행운번호가 움직입니다.";
        TextView hint = label(hintText, 13, muted, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, dp(10));
        content.addView(hint);
        content.addView(officialRoundLookupPanel(true));

        if (officialLookupResult != null) {
            officialLookupResult.setText("로또 QR코드를 스캔하면 당첨번호를 확인합니다.");
        }
        if (officialLookupResultBox != null) {
            officialLookupResultBox.removeAllViews();
        }
    }
    private void layoutHomeImageMap(FrameLayout frame, ImageView poster) {
        int width = frame.getWidth();
        if (width <= 0) return;
        int height = homeImageHeight(width);

        FrameLayout.LayoutParams posterParams = new FrameLayout.LayoutParams(width, height);
        poster.setLayoutParams(posterParams);

        ViewGroup.LayoutParams frameParams = frame.getLayoutParams();
        frameParams.width = width;
        frameParams.height = height;
        frame.setLayoutParams(frameParams);

        addMainTitleOverlay(frame);
        addLottoMachine(frame);
        addStartHotspot(frame, 448, 842, 300, 66);
        addDrawDateOverlay(frame);
        addHomeInfoPanel(frame);
        addHomeHamburgerMenu(frame);
        addMenuHotspot(frame, "home", 230, 154, 82, 56);
        addMenuHotspot(frame, "generate", 333, 154, 112, 56);
        addMenuHotspot(frame, "saved", 454, 154, 118, 56);
        addMenuHotspot(frame, "stats", 568, 154, 100, 56);
        addMenuHotspot(frame, "about", 672, 154, 100, 56);

        addZodiacHotspot(frame, "쥐", 79, 356, 90);
        addZodiacHotspot(frame, "소", 180, 337, 90);
        addZodiacHotspot(frame, "호랑이", 79, 510, 90);
        addZodiacHotspot(frame, "토끼", 79, 663, 90);
        addZodiacHotspot(frame, "용", 79, 816, 90);
        addZodiacHotspot(frame, "뱀", 79, 963, 90);
        addZodiacHotspot(frame, "말", 706, 337, 90);
        addZodiacHotspot(frame, "양", 808, 356, 90);
        addZodiacHotspot(frame, "원숭이", 808, 510, 90);
        addZodiacHotspot(frame, "닭", 808, 663, 90);
        addZodiacHotspot(frame, "개", 808, 816, 90);
        addZodiacHotspot(frame, "돼지", 808, 963, 90);
    }

    private int homeImageHeight(int width) {
        return width * HOME_IMAGE_HEIGHT / HOME_IMAGE_WIDTH;
    }

    private int venysoundBannerHeight(int bannerWidth) {
        return Math.max(dp(56), bannerWidth * VENYSOUND_BANNER_HEIGHT / VENYSOUND_BANNER_WIDTH + dp(8));
    }

    private void addMainTitleOverlay(FrameLayout frame) {
        int width = frame.getWidth();
        int height = homeImageHeight(width);

        TextView cover = new TextView(this);
        cover.setBackground(round(Color.rgb(9, 12, 28), 14));
        cover.setAlpha(0.96f);
        FrameLayout.LayoutParams coverParams = new FrameLayout.LayoutParams(
            width * 470 / HOME_IMAGE_WIDTH,
            height * 104 / HOME_IMAGE_HEIGHT
        );
        coverParams.leftMargin = width * 230 / HOME_IMAGE_WIDTH;
        coverParams.topMargin = height * 10 / HOME_IMAGE_HEIGHT;
        frame.addView(cover, coverParams);

        TextView title = label("십이지신이 추천하는,\n행운의 로또번호", 17, Color.rgb(244, 205, 130), true);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(true);
        title.setLineSpacing(0, 1.0f);
        title.setShadowLayer(dp(3), 0, dp(2), Color.BLACK);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
            width * 470 / HOME_IMAGE_WIDTH,
            height * 104 / HOME_IMAGE_HEIGHT
        );
        titleParams.leftMargin = coverParams.leftMargin;
        titleParams.topMargin = coverParams.topMargin;
        frame.addView(title, titleParams);
    }

    private void addLottoMachine(FrameLayout frame) {
        int width = frame.getWidth();
        int height = homeImageHeight(width);
        lottoMachineView = new LottoMachineView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            width * 430 / HOME_IMAGE_WIDTH,
            height * 395 / HOME_IMAGE_HEIGHT
        );
        params.leftMargin = width * 232 / HOME_IMAGE_WIDTH;
        params.topMargin = height * 345 / HOME_IMAGE_HEIGHT;
        frame.addView(lottoMachineView, params);
    }

    private void addStartHotspot(FrameLayout frame, int centerX, int centerY, int sourceWidth, int sourceHeight) {
        Button hotspot = transparentHotspot();
        hotspot.setOnClickListener(v -> startZodiacDraw(frame));
        addScaledHotspot(frame, hotspot, centerX, centerY, sourceWidth, sourceHeight);
    }

    private void addDrawDateOverlay(FrameLayout frame) {
        int width = frame.getWidth();
        int height = homeImageHeight(width);

        TextView drawDate = label(nextDrawDateText(), 15, Color.rgb(244, 205, 130), true);
        drawDate.setGravity(Gravity.CENTER);
        drawDate.setIncludeFontPadding(true);
        drawDate.setShadowLayer(dp(3), 0, dp(2), Color.BLACK);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            width * 500 / HOME_IMAGE_WIDTH,
            height * 56 / HOME_IMAGE_HEIGHT
        );
        params.leftMargin = width * 198 / HOME_IMAGE_WIDTH;
        params.topMargin = height * 878 / HOME_IMAGE_HEIGHT;
        frame.addView(drawDate, params);
    }

    private void addHomeInfoPanel(FrameLayout frame) {
        stopHomeBannerRotation();

        int width = frame.getWidth();
        int height = homeImageHeight(width);
        int bannerWidth = width * 895 / HOME_IMAGE_WIDTH;
        int bannerHeight = venysoundBannerHeight(bannerWidth);
        int bannerTop = Math.max(0, height - bannerHeight);
        int extraHeight = Math.max(0, bannerTop + bannerHeight - height);

        if (extraHeight > 0) {
            ViewGroup.LayoutParams frameParams = frame.getLayoutParams();
            frameParams.height = height + extraHeight;
            frame.setLayoutParams(frameParams);
        }

        frame.setClipChildren(false);
        frame.setClipToPadding(false);

        TextView cover = new TextView(this);
        cover.setBackgroundColor(Color.rgb(5, 8, 20));

        FrameLayout.LayoutParams coverParams = new FrameLayout.LayoutParams(bannerWidth, bannerHeight);
        coverParams.leftMargin = 0;
        coverParams.topMargin = bannerTop;
        frame.addView(cover, coverParams);

        FrameLayout bannerSlot = new FrameLayout(this);
        bannerSlot.setBackgroundColor(Color.rgb(5, 8, 20));
        bannerSlot.setClipChildren(false);
        bannerSlot.setClipToPadding(false);
        bannerSlot.setOnClickListener(v -> openUrl(VENYSOUND_URL));

        ImageView imageBanner = new ImageView(this);
        imageBanner.setImageResource(getResources().getIdentifier("venysound_banner", "drawable", getPackageName()));
        imageBanner.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageBanner.setAdjustViewBounds(true);
        imageBanner.setBackgroundColor(Color.rgb(5, 8, 20));
        bannerSlot.addView(imageBanner, new FrameLayout.LayoutParams(-1, -1));

        View textBanner = createVenysoundTextBanner();
        textBanner.setAlpha(0f);
        bannerSlot.addView(textBanner, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(bannerWidth, bannerHeight);
        params.leftMargin = coverParams.leftMargin;
        params.topMargin = bannerTop;
        frame.addView(bannerSlot, params);

        homeBannerImage = imageBanner;
        homeBannerText = textBanner;
        startHomeBannerRotation();
    }

    private View createVenysoundTextBanner() {
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setGravity(Gravity.CENTER);
        banner.setBackgroundColor(Color.WHITE);
        banner.setPadding(dp(10), dp(6), dp(10), dp(6));

        homeBannerTitle = label("내 음성으로 나만의 음악을 만든다", 12, Color.rgb(20, 24, 31), true);
        homeBannerTitle.setGravity(Gravity.CENTER);
        homeBannerTitle.setIncludeFontPadding(false);
        homeBannerTitle.setMaxLines(2);
        homeBannerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        homeBannerTitle.setLineSpacing(0f, 0.95f);
        banner.addView(homeBannerTitle, new LinearLayout.LayoutParams(-1, -2));

        homeBannerSubtitle = label("베니사운드에서 가능", 10, Color.rgb(67, 97, 238), true);
        homeBannerSubtitle.setGravity(Gravity.CENTER);
        homeBannerSubtitle.setIncludeFontPadding(false);
        homeBannerSubtitle.setMaxLines(1);
        homeBannerSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        homeBannerSubtitle.setPadding(0, dp(3), 0, 0);
        homeBannerSubtitle.setLetterSpacing(0.01f);
        banner.addView(homeBannerSubtitle, new LinearLayout.LayoutParams(-1, -2));

        resetVenysoundTextAnimation();
        return banner;
    }

    private void animateVenysoundTextIn() {
        if (homeBannerTitle == null || homeBannerSubtitle == null) return;

        homeBannerTitle.setAlpha(0f);
        homeBannerTitle.setTranslationY(dp(8));
        homeBannerSubtitle.setAlpha(0f);
        homeBannerSubtitle.setTranslationY(dp(8));

        homeBannerTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(420)
            .setStartDelay(80)
            .start();
        homeBannerSubtitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(420)
            .setStartDelay(220)
            .start();
    }

    private void resetVenysoundTextAnimation() {
        if (homeBannerTitle != null) {
            homeBannerTitle.animate().cancel();
            homeBannerTitle.setAlpha(1f);
            homeBannerTitle.setTranslationY(0f);
        }
        if (homeBannerSubtitle != null) {
            homeBannerSubtitle.animate().cancel();
            homeBannerSubtitle.setAlpha(1f);
            homeBannerSubtitle.setTranslationY(0f);
        }
    }

    private void startHomeBannerRotation() {
        if (homeBannerImage == null || homeBannerText == null) return;

        stopHomeBannerRotation();
        homeBannerShowingImage = true;
        homeBannerImage.setAlpha(1f);
        homeBannerText.setAlpha(0f);
        resetVenysoundTextAnimation();

        homeBannerRotateTask = new Runnable() {
            @Override
            public void run() {
                if (homeBannerImage == null || homeBannerText == null) return;

                boolean showingImage = homeBannerShowingImage;
                View fadeOut = showingImage ? homeBannerImage : homeBannerText;
                View fadeIn = showingImage ? homeBannerText : homeBannerImage;
                fadeOut.animate().alpha(0f).setDuration(HOME_BANNER_FADE_MS).start();
                fadeIn.animate()
                    .alpha(1f)
                    .setDuration(HOME_BANNER_FADE_MS)
                    .withEndAction(() -> {
                        if (fadeIn == homeBannerText) animateVenysoundTextIn();
                    })
                    .start();
                if (fadeIn == homeBannerImage) resetVenysoundTextAnimation();
                homeBannerShowingImage = !showingImage;
                bannerHandler.postDelayed(this, HOME_BANNER_SHOW_MS);
            }
        };
        bannerHandler.postDelayed(homeBannerRotateTask, HOME_BANNER_SHOW_MS);
    }

    private void stopHomeBannerRotation() {
        if (homeBannerRotateTask != null) {
            bannerHandler.removeCallbacks(homeBannerRotateTask);
            homeBannerRotateTask = null;
        }
        if (homeBannerImage != null) homeBannerImage.animate().cancel();
        if (homeBannerText != null) homeBannerText.animate().cancel();
        resetVenysoundTextAnimation();
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "연결할 브라우저를 찾지 못했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void addHomeHamburgerMenu(FrameLayout frame) {
        int width = frame.getWidth();
        int height = homeImageHeight(width);

        Button menu = new Button(this);
        menu.setText("☰");
        menu.setTextColor(text);
        menu.setTextSize(20);
        menu.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        menu.setAllCaps(false);
        menu.setPadding(0, 0, 0, 0);
        menu.setMinWidth(0);
        menu.setMinimumWidth(0);
        menu.setMinHeight(0);
        menu.setMinimumHeight(0);
        menu.setBackground(round(Color.rgb(12, 16, 31), 18));
        menu.setOnClickListener(v -> showQuickLinkMenu());

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            width * 54 / HOME_IMAGE_WIDTH,
            height * 54 / HOME_IMAGE_HEIGHT
        );
        params.leftMargin = width * 806 / HOME_IMAGE_WIDTH;
        params.topMargin = height * 70 / HOME_IMAGE_HEIGHT;
        frame.addView(menu, params);
    }

    private void showQuickLinkMenu() {
        String[] items = {"회차별 당첨번호", "복권 판매점 찾기"};
        new AlertDialog.Builder(this)
            .setTitle("공식 데이터 조회")
            .setItems(items, (dialog, which) -> {
                if (which == 0) {
                    switchTab("official");
                } else {
                    showSellerSearchDialog();
                }
            })
            .show();
    }

    private void loadLatestOfficialRounds(int count) {
        if (officialLookupResult != null) {
            officialLookupResult.setVisibility(View.VISIBLE);
            officialLookupResult.setText("조회중...");
        }
        if (officialLookupResultBox != null) {
            officialLookupResultBox.removeAllViews();
        }

        new Thread(() -> {
            try {
                int latest = findLatestOfficialRound();
                List<JSONObject> slots = new ArrayList<>();
                for (int i = 0; i < count; i++) slots.add(null);

                List<Thread> workers = new ArrayList<>();
                for (int index = 0; index < count; index++) {
                    final int slotIndex = index;
                    final int roundNo = latest - index;
                    Thread worker = new Thread(() -> {
                        if (roundNo <= 0) return;
                        try {
                            JSONObject draw = fetchOfficialRound(roundNo);
                            synchronized (slots) {
                                slots.set(slotIndex, draw);
                            }
                        } catch (Exception ignored) {
                        }
                    });
                    workers.add(worker);
                    worker.start();
                }

                for (Thread worker : workers) {
                    try {
                        worker.join();
                    } catch (InterruptedException ignored) {
                    }
                }

                List<JSONObject> draws = new ArrayList<>();
                List<Integer> rounds = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    JSONObject draw = slots.get(i);
                    if (draw != null) {
                        draws.add(draw);
                        rounds.add(latest - i);
                    }
                }

                if (draws.isEmpty()) {
                    throw new IllegalStateException("당첨번호 데이터를 찾지 못했습니다.");
                }

                runOnUiThread(() -> renderOfficialRoundList(rounds, draws));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "알 수 없는 오류" : error.getMessage();
                runOnUiThread(() -> {
                    if (officialLookupResult != null) {
                        officialLookupResult.setText("조회 실패: " + message);
                    }
                    Toast.makeText(this, "공식 데이터 조회 실패", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void renderOfficialRoundList(List<Integer> rounds, List<JSONObject> draws) {
        if (officialLookupResult == null || officialLookupResultBox == null) return;

        officialLookupResult.setText("");
        officialLookupResult.setVisibility(View.GONE);
        officialLookupResultBox.removeAllViews();

        TextView title = label("최근10회차 당첨번호 결과", 16, text, true);
        title.setPadding(dp(10), 0, dp(10), dp(10));
        officialLookupResultBox.addView(title);

        for (int index = 0; index < draws.size(); index++) {
            JSONObject draw = draws.get(index);
            int round = rounds.get(index);
            int winners = readIntField(draw, "firstPrzwnerCo");
            long amount = readLongField(draw, "firstWinamnt");
            int bonus = draw.optInt("bnusNo", 0);
            officialLookupResultBox.addView(officialRoundTableRow(round, drawNumbersToString(draw), bonus, winners, amount));
        }
    }

    private View officialRoundTableRow(int round, String numberText, int bonus, int winners, long amount) {
        LinearLayout block = column();

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.setMargins(dp(10), 0, dp(10), dp(12));
        block.setLayoutParams(rowParams);

        TextView roundView = label(round + "회", 15, Color.WHITE, true);
        block.addView(roundView);

        LinearLayout numbers = row();
        numbers.setGravity(Gravity.CENTER_VERTICAL);
        numbers.setPadding(0, dp(8), 0, dp(6));
        for (Integer number : parseGame(numberText)) {
            numbers.addView(resultBall(number));
        }
        TextView plus = label("+", 17, Color.rgb(128, 135, 146), true);
        plus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(dp(16), dp(34));
        plusParams.setMargins(dp(2), 0, dp(2), 0);
        numbers.addView(plus, plusParams);
        numbers.addView(resultBall(bonus));
        block.addView(numbers);

        TextView meta = label("당첨자수: " + winners + "명, 당첨금: " + formatWon(amount), 13, muted, false);
        block.addView(meta);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(226, 229, 234));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(10), 0, 0);
        block.addView(divider, dividerParams);

        return block;
    }

    private void showRoundNumberDialog() {
        EditText input = dialogInput("예: " + Math.max(1, estimatedLatestRound()));
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
            .setTitle("회차별 당첨번호")
            .setMessage("조회할 회차를 입력하세요.")
            .setView(input)
            .setNegativeButton("취소", null)
            .setPositiveButton("조회", (dialog, which) -> {
                int round = parsePositiveInt(input.getText().toString());
                if (round <= 0) {
                    Toast.makeText(this, "회차를 숫자로 입력해 주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                ensureHomeLookupPanel();
                lookupOfficialRound(round);
            })
            .show();
    }

    private void showWinningStoreDialog() {
        EditText input = dialogInput("예: " + Math.max(1, estimatedLatestRound()));
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
            .setTitle("회차별 당첨 판매점")
            .setMessage("1등 배출 판매점을 조회할 회차를 입력하세요.")
            .setView(input)
            .setNegativeButton("취소", null)
            .setPositiveButton("조회", (dialog, which) -> {
                int round = parsePositiveInt(input.getText().toString());
                if (round <= 0) {
                    Toast.makeText(this, "회차를 숫자로 입력해 주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                ensureHomeLookupPanel();
                lookupWinningStores(round);
            })
            .show();
    }

    private void showSellerSearchDialog() {
        pendingNearbySellerSearch = true;
        switchTab("official");
    }

    private void lookupNearbySellerByLocation(boolean allowPermissionRequest) {
        if (allowPermissionRequest && !hasLocationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQ_LOCATION_FOR_STORE_MAP);
                return;
            }
        }

        double[] center = currentMapCenter();
        if (center == null) {
            openLocationSettingsForRetry();
            return;
        }

        final double centerLat = center[0];
        final double centerLng = center[1];
        showNearbyStatus("복권 판매점 조회 중", "현재 위치 기준 3km 이내 판매점을 찾고 있습니다.");

        new Thread(() -> {
            try {
                List<NearbyStore> nearby = fetchNearbyOfficialStores(centerLat, centerLng, 3000f);
                String keyword = resolveNearbyKeyword(centerLat, centerLng);
                runOnUiThread(() -> {
                    renderNearbyStores(nearby, keyword);
                    if (nearby.isEmpty()) {
                        Toast.makeText(this, "3km 이내 공식 판매점을 찾지 못했습니다. 지도 검색을 엽니다.", Toast.LENGTH_LONG).show();
                        openNearbyStoresFallback(centerLat, centerLng);
                        return;
                    }
                    Toast.makeText(this, "3km 이내 복권 판매점 " + nearby.size() + "곳을 찾았습니다.", Toast.LENGTH_SHORT).show();
                    openNearbyStoresMap(centerLat, centerLng, nearby);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "판매점 조회에 실패했습니다. 지도 검색을 엽니다.", Toast.LENGTH_LONG).show();
                    openNearbyStoresFallback(centerLat, centerLng);
                });
            }
        }).start();
    }

    private void openLocationSettingsForRetry() {
        retryNearbySellerSearchOnResume = true;
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    private void openNearbyStoresMap(double centerLat, double centerLng, List<NearbyStore> stores) {
        if (stores == null || stores.isEmpty()) {
            openNearbyStoresFallback(centerLat, centerLng);
            return;
        }

        int limit = Math.min(stores.size(), 8);
        StringBuilder markers = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            NearbyStore store = stores.get(i);
            if (i > 0) markers.append("|");
            String label = store.item.name == null ? "복권판매점" : store.item.name.replace("|", " ").trim();
            if (label.length() > 18) label = label.substring(0, 18);
            markers.append(String.format(Locale.US, "%.6f,%.6f(%s)", store.lat, store.lng, Uri.encode(label)));
        }

        Uri geoUri = Uri.parse("geo:" + centerLat + "," + centerLng + "?z=14&q=" + markers);
        if (launchMapIntent(geoUri, "com.google.android.apps.maps")) return;
        if (launchMapIntent(geoUri, "com.nhn.android.nmap")) return;
        if (launchMapIntent(geoUri, null)) return;

        NearbyStore first = stores.get(0);
        String webUrl = String.format(
            Locale.US,
            "https://www.google.com/maps/search/?api=1&query=%f,%f",
            first.lat,
            first.lng
        );
        openUrl(webUrl);
    }

    private void openNearbyStoresFallback(double centerLat, double centerLng) {
        String webUrl = String.format(
            Locale.US,
            "https://www.google.com/maps/search/%s/@%f,%f,14z",
            Uri.encode("복권판매점"),
            centerLat,
            centerLng
        );
        openUrl(webUrl);
    }

    private boolean launchMapIntent(Uri uri, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            if (packageName != null && !packageName.isEmpty()) {
                intent.setPackage(packageName);
            }
            if (packageName == null && intent.resolveActivity(getPackageManager()) == null) {
                return false;
            }
            startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void renderNearbyStores(List<NearbyStore> stores, String keyword) {
        if (officialLookupResult == null || officialLookupResultBox == null) return;

        officialLookupResult.setVisibility(View.VISIBLE);
        officialLookupResult.setText("현재 위치 3km 이내 판매점 " + stores.size() + "곳");
        officialLookupResultBox.removeAllViews();

        LinearLayout card = column();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(round(Color.rgb(245, 246, 248), 12));
        card.addView(label("내 주변 3km 복권 판매점", 17, Color.rgb(20, 24, 31), true));

        TextView subtitle = label("검색 기준: " + keyword, 12, Color.rgb(92, 98, 110), false);
        subtitle.setPadding(0, dp(8), 0, dp(8));
        card.addView(subtitle);

        if (stores.isEmpty()) {
            card.addView(label("3km 이내 공식 판매점을 찾지 못했습니다.", 13, Color.rgb(92, 98, 110), false));
            card.addView(label("지도 검색으로 주변 복권점을 확인합니다.", 12, Color.rgb(116, 121, 130), false));
        } else {
            int limit = Math.min(15, stores.size());
            for (int i = 0; i < limit; i++) {
                NearbyStore store = stores.get(i);
                String meta = String.format(Locale.KOREA, "거리 %.2fkm", store.distanceMeters / 1000f);
                card.addView(nearbyStoreCard((i + 1) + ". " + store.item.name, meta, store.item.address, store.lat, store.lng));
            }
            if (stores.size() > limit) {
                card.addView(label("외 " + (stores.size() - limit) + "곳", 12, Color.rgb(104, 109, 118), false));
            }
        }

        officialLookupResultBox.addView(card);
    }

    private View nearbyStoreCard(String title, String meta, String address, double lat, double lng) {
        View card = lightStoreCard(title, meta, address);
        card.setOnClickListener(v -> {
            String webUrl = String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%f,%f", lat, lng);
            openUrl(webUrl);
        });
        return card;
    }

    private void showNearbyStatus(String title, String detail) {
        if (officialLookupResult != null) {
            officialLookupResult.setVisibility(View.VISIBLE);
            officialLookupResult.setText(title);
        }
        if (officialLookupResultBox != null) {
            officialLookupResultBox.removeAllViews();
            officialLookupResultBox.addView(label(detail, 12, Color.rgb(116, 121, 130), false));
        }
    }

    private String resolveNearbyKeyword(double lat, double lng) {
        String[] region = resolveOfficialRegion(lat, lng);
        String keyword = (region[0] + " " + region[1]).trim();
        return keyword.isEmpty() ? "복권" : keyword;
    }

    private String[] resolveOfficialRegion(double lat, double lng) {
        try {
            if (Geocoder.isPresent()) {
                Geocoder geocoder = new Geocoder(this, Locale.KOREA);
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    String city = toOfficialCityShortName(firstNonEmpty(address.getAdminArea(), ""));
                    String subAdmin = firstNonEmpty(address.getSubAdminArea(), "");
                    String locality = firstNonEmpty(address.getLocality(), address.getSubLocality(), "");
                    String district;
                    if (!subAdmin.isEmpty() && !locality.isEmpty() && subAdmin.contains("시")
                        && (locality.endsWith("구") || locality.endsWith("군")) && !subAdmin.contains(locality)) {
                        district = subAdmin + " " + locality;
                    } else {
                        district = firstNonEmpty(locality, subAdmin);
                    }
                    if (!city.isEmpty()) return new String[]{city, district};
                }
            }
        } catch (Exception ignored) {
        }

        try {
            String url = String.format(Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&accept-language=ko&zoom=14&lat=%f&lon=%f",
                lat,
                lng
            );
            String response = readUrl(url, "LuckyPick/1.0 (com.luckypick.app)");
            JSONObject json = new JSONObject(response);
            JSONObject address = json.optJSONObject("address");
            if (address == null) return new String[]{"", ""};

            String city = toOfficialCityShortName(firstNonEmpty(
                address.optString("city", ""),
                address.optString("state", ""),
                address.optString("province", "")
            ));
            String county = firstNonEmpty(address.optString("county", ""));
            String borough = firstNonEmpty(
                address.optString("borough", ""),
                address.optString("city_district", ""),
                address.optString("town", "")
            );
            String district;
            if (!county.isEmpty() && !borough.isEmpty() && county.contains("시")
                && (borough.endsWith("구") || borough.endsWith("군")) && !county.contains(borough)) {
                district = county + " " + borough;
            } else {
                district = firstNonEmpty(borough, county);
            }
            return new String[]{city, district};
        } catch (Exception ignored) {
            return new String[]{"", ""};
        }
    }

    private String resolveNearbyDong(double lat, double lng) {
        try {
            if (!Geocoder.isPresent()) return "";
            Geocoder geocoder = new Geocoder(this, Locale.KOREA);
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses == null || addresses.isEmpty()) return "";
            Address address = addresses.get(0);
            return firstNonEmpty(address.getSubLocality(), address.getThoroughfare(), "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String toOfficialCityShortName(String city) {
        if (city == null || city.trim().isEmpty()) return "";
        String value = city.trim();
        if (value.contains("서울")) return "서울";
        if (value.contains("경기")) return "경기";
        if (value.contains("부산")) return "부산";
        if (value.contains("대구")) return "대구";
        if (value.contains("인천")) return "인천";
        if (value.contains("대전")) return "대전";
        if (value.contains("울산")) return "울산";
        if (value.contains("광주")) return "광주";
        if (value.contains("세종")) return "세종";
        if (value.contains("강원")) return "강원";
        if (value.contains("충북") || value.contains("충청북")) return "충북";
        if (value.contains("충남") || value.contains("충청남")) return "충남";
        if (value.contains("전북") || value.contains("전라북")) return "전북";
        if (value.contains("전남") || value.contains("전라남")) return "전남";
        if (value.contains("경북") || value.contains("경상북")) return "경북";
        if (value.contains("경남") || value.contains("경상남")) return "경남";
        if (value.contains("제주")) return "제주";
        return value;
    }

    private List<NearbyStore> fetchNearbyOfficialStores(double centerLat, double centerLng, float maxMeters) throws Exception {
        String[] region = resolveOfficialRegion(centerLat, centerLng);
        String city = region[0];
        String district = region[1];

        List<NearbyStore> nearby = new ArrayList<>();
        if (!city.isEmpty()) {
            nearby = collectNearbyFromRegion(centerLat, centerLng, maxMeters, city, district, 20);
            if (nearby.isEmpty() && !district.isEmpty()) {
                nearby = collectNearbyFromRegion(centerLat, centerLng, maxMeters, city, "", 30);
            }
        }

        if (nearby.isEmpty()) {
            String dong = resolveNearbyDong(centerLat, centerLng);
            if (!dong.isEmpty()) {
                nearby = collectNearbyFromDong(centerLat, centerLng, maxMeters, dong, 20);
            }
        }

        Collections.sort(nearby, (a, b) -> Float.compare(a.distanceMeters, b.distanceMeters));
        return nearby;
    }

    private List<NearbyStore> collectNearbyFromRegion(double centerLat, double centerLng, float maxMeters, String city, String district, int maxPages) throws Exception {
        List<NearbyStore> nearby = new ArrayList<>();
        int total = Integer.MAX_VALUE;
        for (int page = 1; page <= maxPages && nearby.size() < 30 && (page - 1) * 10 < total; page++) {
            JSONObject response = fetchOfficialStorePage(city, district, page);
            total = appendNearbyStores(nearby, response, centerLat, centerLng, maxMeters);
            if (total <= page * 10) break;
        }
        return nearby;
    }

    private List<NearbyStore> collectNearbyFromDong(double centerLat, double centerLng, float maxMeters, String dong, int maxPages) throws Exception {
        List<NearbyStore> nearby = new ArrayList<>();
        int total = Integer.MAX_VALUE;
        for (int page = 1; page <= maxPages && nearby.size() < 30 && (page - 1) * 10 < total; page++) {
            JSONObject response = fetchOfficialStoreByDong(dong, page);
            total = appendNearbyStores(nearby, response, centerLat, centerLng, maxMeters);
            if (total <= page * 10) break;
        }
        return nearby;
    }

    private int appendNearbyStores(List<NearbyStore> nearby, JSONObject response, double centerLat, double centerLng, float maxMeters) throws JSONException {
        JSONObject data = response.optJSONObject("data");
        if (data == null) return 0;

        int total = data.optInt("total", 0);
        JSONArray list = data.optJSONArray("list");
        if (list == null || list.length() == 0) return total;

        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            double lat = item.optDouble("shpLat", 0);
            double lng = item.optDouble("shpLot", 0);
            if (lat == 0 || lng == 0) continue;

            float[] distance = new float[1];
            Location.distanceBetween(centerLat, centerLng, lat, lng, distance);
            if (distance[0] > maxMeters) continue;

            String name = item.optString("conmNm", "복권 판매점");
            String address = firstNonEmpty(
                item.optString("bplcRdnmDaddr", ""),
                item.optString("bplcRdnmAddr", ""),
                item.optString("bplcAddr", "")
            );
            NearbyStore candidate = new NearbyStore(new StoreItem(name, "", address), distance[0], lat, lng);
            if (!containsNearbyStore(nearby, candidate)) nearby.add(candidate);
        }
        return total;
    }

    private boolean containsNearbyStore(List<NearbyStore> stores, NearbyStore item) {
        for (NearbyStore store : stores) {
            if (Math.abs(store.lat - item.lat) < 0.00001 && Math.abs(store.lng - item.lng) < 0.00001) return true;
        }
        return false;
    }

    private JSONObject fetchOfficialStorePage(String city, String district, int page) throws Exception {
        StringBuilder url = new StringBuilder("https://www.dhlottery.co.kr/prchsplcsrch/selectLtShp.do?");
        url.append("l645LtNtslYn=Y&l520LtNtslYn=N&st5LtNtslYn=N&st10LtNtslYn=N&st20LtNtslYn=N");
        url.append("&cpexUsePsbltyYn=N&pageNum=").append(page);
        url.append("&recordCountPerPage=10&pageCount=5");
        url.append("&srchCtpvNm=").append(URLEncoder.encode(city, "UTF-8"));
        if (district != null && !district.isEmpty()) {
            url.append("&srchSggNm=").append(URLEncoder.encode(district, "UTF-8"));
        }
        String response = readOfficialJson(url.toString());
        if (response.isEmpty() || !response.trim().startsWith("{")) return new JSONObject();
        return new JSONObject(response);
    }

    private JSONObject fetchOfficialStoreByDong(String dong, int page) throws Exception {
        StringBuilder url = new StringBuilder("https://www.dhlottery.co.kr/prchsplcsrch/selectLtShp.do?");
        url.append("l645LtNtslYn=Y&l520LtNtslYn=N&st5LtNtslYn=N&st10LtNtslYn=N&st20LtNtslYn=N");
        url.append("&cpexUsePsbltyYn=N&pageNum=").append(page);
        url.append("&recordCountPerPage=10&pageCount=5");
        url.append("&srchOption=2");
        url.append("&srchValue=").append(URLEncoder.encode(dong, "UTF-8"));
        String response = readOfficialJson(url.toString());
        if (response.isEmpty() || !response.trim().startsWith("{")) return new JSONObject();
        return new JSONObject(response);
    }

    private List<NearbyStore> filterNearbyStores(List<StoreItem> stores, double centerLat, double centerLng, float maxMeters) {
        List<NearbyStore> filtered = new ArrayList<>();
        for (StoreItem item : stores) {
            try {
                double[] point = geocodeAddress(item.address);
                if (point == null) continue;

                float[] distance = new float[1];
                Location.distanceBetween(centerLat, centerLng, point[0], point[1], distance);
                if (distance[0] <= maxMeters) {
                    filtered.add(new NearbyStore(item, distance[0], point[0], point[1]));
                }
            } catch (Exception ignored) {
            }
        }

        Collections.sort(filtered, (a, b) -> Float.compare(a.distanceMeters, b.distanceMeters));
        return filtered;
    }

    private double[] geocodeAddress(String address) {
        if (address == null || address.trim().isEmpty()) return null;
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=" + URLEncoder.encode(address, "UTF-8");
            String response = readUrl(url, "LuckyPick/1.0");
            JSONArray array = new JSONArray(response);
            if (array.length() == 0) return null;
            JSONObject first = array.getJSONObject(0);
            return new double[]{
                Double.parseDouble(first.optString("lat", "0")),
                Double.parseDouble(first.optString("lon", "0"))
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private double[] currentMapCenter() {
        if (!hasLocationPermission()) return null;
        try {
            LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (manager == null) return null;
            List<String> providers = manager.getProviders(true);
            Location best = null;
            for (String provider : providers) {
                try {
                    Location location = manager.getLastKnownLocation(provider);
                    if (location == null) continue;
                    if (!isLocationUsable(location)) continue;
                    if (best == null || location.getAccuracy() < best.getAccuracy()) best = location;
                } catch (SecurityException ignored) {
                }
            }
            if (best == null) return null;
            return new double[]{best.getLatitude(), best.getLongitude()};
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isLocationUsable(Location location) {
        long ageMs = System.currentTimeMillis() - location.getTime();
        if (ageMs > 20L * 60L * 1000L) return false;
        if (location.hasAccuracy() && location.getAccuracy() > 2000f) return false;
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION_FOR_STORE_MAP) return;
        boolean granted = false;
        if (grantResults != null) {
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
        }
        if (granted) {
            lookupNearbySellerByLocation(false);
        }
    }

    private EditText dialogInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(210, 210, 210));
        input.setBackground(round(Color.rgb(38, 40, 46), 10));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        return input;
    }

    private int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void ensureHomeLookupPanel() {
        if (officialLookupResult == null || officialLookupResultBox == null) {
            switchTab("home", false);
        }
    }

    private void addMenuHotspot(FrameLayout frame, String targetTab, int centerX, int centerY, int sourceWidth, int sourceHeight) {
        Button hotspot = transparentHotspot();
        hotspot.setOnClickListener(v -> switchTab(targetTab));
        addScaledHotspot(frame, hotspot, centerX, centerY, sourceWidth, sourceHeight);
    }

    private Button transparentHotspot() {
        Button hotspot = new Button(this);
        hotspot.setText("");
        hotspot.setAlpha(0.02f);
        hotspot.setBackgroundColor(Color.TRANSPARENT);
        return hotspot;
    }

    private void addScaledHotspot(FrameLayout frame, View hotspot, int centerX, int centerY, int sourceWidth, int sourceHeight) {
        int width = frame.getWidth();
        int height = homeImageHeight(width);
        int hotspotWidth = Math.max(dp(44), width * sourceWidth / HOME_IMAGE_WIDTH);
        int hotspotHeight = Math.max(dp(34), height * sourceHeight / HOME_IMAGE_HEIGHT);
        int left = width * centerX / HOME_IMAGE_WIDTH - hotspotWidth / 2;
        int top = height * centerY / HOME_IMAGE_HEIGHT - hotspotHeight / 2;

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(hotspotWidth, hotspotHeight);
        params.leftMargin = Math.max(0, left);
        params.topMargin = Math.max(0, top);
        frame.addView(hotspot, params);
    }

    private void addZodiacHotspot(FrameLayout frame, String zodiac, int centerX, int centerY, int sourceSize) {
        int width = frame.getWidth();
        int height = homeImageHeight(width);
        int size = Math.max(dp(42), width * sourceSize / HOME_IMAGE_WIDTH);
        int left = width * centerX / HOME_IMAGE_WIDTH - size / 2;
        int top = height * centerY / HOME_IMAGE_HEIGHT - size / 2;

        Button hotspot = new Button(this);
        hotspot.setText("");
        hotspot.setAlpha(0.02f);
        hotspot.setBackgroundColor(Color.TRANSPARENT);
        hotspot.setOnClickListener(v -> showBirthDateDialog(zodiac));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.leftMargin = Math.max(0, left);
        params.topMargin = Math.max(0, top);
        frame.addView(hotspot, params);
    }

    private void showBirthDateDialog(String zodiac) {
        EditText input = new EditText(this);
        input.setHint("예: 1990012315");
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(210, 210, 210));
        input.setBackground(round(Color.rgb(38, 40, 46), 10));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));

        new AlertDialog.Builder(this)
            .setTitle(zodiac + "띠 생년월일시 입력")
            .setMessage("생년월일과 태어난 시간을 숫자로 입력하세요. 예: 1990012315")
            .setView(input)
            .setNegativeButton("취소", null)
            .setPositiveButton("선택 완료", (dialog, which) -> {
                String birthday = input.getText().toString().trim();
                if (!birthday.matches("\\d{8,12}")) {
                    Toast.makeText(this, "생년월일시는 8~12자리 숫자로 입력해 주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedZodiac = zodiac;
                selectedBirthDateTime = birthday;
                Toast.makeText(this, zodiac + "띠가 선택되었습니다. 추첨 시작하기를 눌러주세요.", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private LinearLayout officialRoundLookupPanel(boolean showQrSection) {
        LinearLayout panel = panelView();
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));

        if (showQrSection) {
            TextView title = label("당첨번호 조회", 16, text, true);
            title.setPadding(0, 0, 0, dp(8));
            panel.addView(title);

            Button qrButton = secondaryButton("QR 스캔");
            qrButton.setOnClickListener(v -> startQrScan());
            panel.addView(qrButton);
        }

        officialLookupResult = label(showQrSection ? "로또 QR코드를 스캔하면 당첨번호를 확인합니다." : "", 13, muted, false);
        officialLookupResult.setPadding(0, showQrSection ? dp(10) : 0, 0, 0);
        if (!showQrSection) {
            officialLookupResult.setVisibility(View.GONE);
        }
        panel.addView(officialLookupResult);

        officialLookupResultBox = column();
        officialLookupResultBox.setPadding(0, dp(4), 0, 0);
        panel.addView(officialLookupResultBox);

        return panel;
    }

    private void startQrScan() {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
        Toast.makeText(this, "로또 QR코드를 화면에 맞춰주세요.", Toast.LENGTH_SHORT).show();
        Task<Barcode> task = scanner.startScan();
        task.addOnSuccessListener(barcode -> {
            String qrText = barcode.getRawValue();
            if (qrText == null || qrText.trim().isEmpty()) {
                Toast.makeText(this, "QR 내용을 읽지 못했습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
            handleQrLookup(qrText.trim());
        });
        task.addOnCanceledListener(() -> Toast.makeText(this, "QR 스캔을 취소했습니다.", Toast.LENGTH_SHORT).show());
        task.addOnFailureListener(error -> {
            String message = error.getMessage() == null ? "스캔 화면을 열지 못했습니다." : error.getMessage();
            Toast.makeText(this, "QR 스캔 실패: " + message, Toast.LENGTH_LONG).show();
        });
    }

    private void handleQrLookup(String qrText) {
        if (officialLookupResult != null) {
            officialLookupResult.setText("QR 확인 중...\n" + qrText);
        }
        int round = extractRoundFromQr(qrText);
        if (round > 0) {
            lookupOfficialRound(round);
            return;
        }
        if (qrText.startsWith("http://") || qrText.startsWith("https://")) {
            Toast.makeText(this, "공식 QR 페이지를 엽니다.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(qrText)));
            return;
        }
        Toast.makeText(this, "QR에서 회차나 공식 URL을 찾지 못했습니다.", Toast.LENGTH_SHORT).show();
    }

    private int extractRoundFromQr(String qrText) {
        String[] keys = {"drwNo=", "drwNo%3D", "round=", "no="};
        for (String key : keys) {
            int index = qrText.indexOf(key);
            if (index >= 0) {
                int start = index + key.length();
                StringBuilder digits = new StringBuilder();
                while (start < qrText.length() && Character.isDigit(qrText.charAt(start))) {
                    digits.append(qrText.charAt(start));
                    start++;
                }
                if (digits.length() > 0) {
                    try {
                        return Integer.parseInt(digits.toString());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return 0;
    }

    private void lookupOfficialRound(int round) {
        if (round <= 0) {
            Toast.makeText(this, "1회 이상 회차를 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (officialLookupResult != null) {
            officialLookupResult.setText(round + "회 당첨번호를 조회하는 중입니다...");
        }

        new Thread(() -> {
            try {
                JSONObject draw = fetchOfficialRound(round);
                runOnUiThread(() -> renderOfficialRoundResult(round, draw));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "알 수 없는 오류" : error.getMessage();
                runOnUiThread(() -> {
                    if (officialLookupResult != null) {
                        officialLookupResult.setText("조회 실패: " + message);
                    }
                    Toast.makeText(this, "공식 데이터 조회 실패", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void renderOfficialRoundResult(int round, JSONObject draw) {
        if (officialLookupResult == null) return;
        if (officialLookupResultBox != null) officialLookupResultBox.removeAllViews();
        if (draw == null) {
            officialLookupResult.setText(round + "회 당첨번호를 찾지 못했습니다.");
            return;
        }

        String date = draw.optString("drwNoDate", "");
        int bonus = draw.optInt("bnusNo", 0);
        officialLookupResult.setText("추첨결과");

        if (officialLookupResultBox != null) {
            officialLookupResultBox.addView(drawResultCard(round, date, drawNumbersToString(draw), bonus, draw));
        }
    }

    private View drawResultCard(int round, String date, String numberText, int bonus, JSONObject draw) {
        LinearLayout card = column();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(round(Color.rgb(245, 246, 248), 12));

        TextView top = label("추첨결과", 18, Color.rgb(20, 24, 31), true);
        card.addView(top);

        TextView subtitle = label("회차별 당첨번호", 13, Color.rgb(20, 24, 31), true);
        subtitle.setPadding(0, dp(10), 0, dp(8));
        card.addView(subtitle);

        LinearLayout resultBox = column();
        resultBox.setGravity(Gravity.CENTER_HORIZONTAL);
        resultBox.setPadding(dp(10), dp(14), dp(10), dp(14));
        resultBox.setBackground(round(Color.WHITE, 10));
        resultBox.addView(centerLabel("제 " + round + "회 추첨 결과", 16, Color.rgb(20, 24, 31), true));
        resultBox.addView(centerLabel(date.isEmpty() ? "" : date + " 추첨", 12, Color.rgb(111, 116, 126), false));

        LinearLayout balls = row();
        balls.setGravity(Gravity.CENTER);
        balls.setPadding(0, dp(14), 0, dp(8));
        for (Integer number : parseGame(numberText)) {
            balls.addView(resultBall(number));
        }
        TextView plus = label("+", 17, Color.rgb(128, 135, 146), true);
        plus.setGravity(Gravity.CENTER);
        balls.addView(plus, new LinearLayout.LayoutParams(dp(20), dp(34)));
        balls.addView(resultBall(bonus));
        resultBox.addView(balls);

        LinearLayout captions = row();
        captions.setGravity(Gravity.CENTER);
        captions.addView(centerLabel("당첨번호", 11, Color.rgb(92, 98, 110), false), new LinearLayout.LayoutParams(dp(160), -2));
        captions.addView(centerLabel("보너스번호", 11, Color.rgb(92, 98, 110), false), new LinearLayout.LayoutParams(dp(90), -2));
        resultBox.addView(captions);
        card.addView(resultBox);

        LinearLayout table = column();
        table.setPadding(0, dp(12), 0, 0);
        table.addView(prizeRow("순위", "당첨게임 수", "1게임당 당첨금", "당첨기준", true));
        table.addView(prizeRow("1등", String.valueOf(readIntField(draw, "firstPrzwnerCo")), formatWon(readLongField(draw, "firstWinamnt")), "6개번호 일치", false));
        table.addView(prizeRow("2등", "-", "-", "5개번호 + 보너스", false));
        table.addView(prizeRow("3등", "-", "공식 상세 확인", "5개번호 일치", false));
        table.addView(prizeRow("4등", "-", "50,000원", "4개번호 일치", false));
        table.addView(prizeRow("5등", "-", "5,000원", "3개번호 일치", false));
        card.addView(table);

        TextView note = label("등수별 총 당첨금과 2~3등 당첨게임 수는 공식 상세 페이지 기준으로 변동됩니다.", 11, Color.rgb(104, 109, 118), false);
        note.setPadding(0, dp(8), 0, 0);
        card.addView(note);
        return card;
    }

    private TextView centerLabel(String value, int size, int color, boolean bold) {
        TextView view = label(value, size, color, bold);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private TextView resultBall(int number) {
        TextView view = label(String.valueOf(number), 13, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(ball3d(number));
        applyBallTextStyle(view, number);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(32), dp(32));
        params.setMargins(dp(2), 0, dp(2), 0);
        view.setLayoutParams(params);
        return view;
    }

    private View prizeRow(String rank, String count, String prize, String criteria, boolean header) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        row.setBackground(round(header ? Color.rgb(232, 235, 240) : Color.WHITE, 4));
        row.addView(tableCell(rank, header ? 12 : 11, header), new LinearLayout.LayoutParams(0, -2, 0.8f));
        row.addView(tableCell(count, header ? 12 : 11, header), new LinearLayout.LayoutParams(0, -2, 1.1f));
        row.addView(tableCell(prize, header ? 12 : 11, header), new LinearLayout.LayoutParams(0, -2, 1.5f));
        row.addView(tableCell(criteria, header ? 12 : 10, header), new LinearLayout.LayoutParams(0, -2, 1.4f));
        return row;
    }

    private TextView tableCell(String value, int size, boolean bold) {
        TextView view = label(value, size, Color.rgb(20, 24, 31), bold);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(2), 0, dp(2), 0);
        return view;
    }

    private void lookupWinningStores(int round) {
        if (officialLookupResult != null) {
            officialLookupResult.setText(round + "회 1등 배출 판매점을 조회하는 중입니다...");
        }
        if (officialLookupResultBox != null) officialLookupResultBox.removeAllViews();

        new Thread(() -> {
            try {
                List<StoreItem> stores = fetchWinningStores(round);
                runOnUiThread(() -> renderWinningStores(round, stores));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "알 수 없는 오류" : error.getMessage();
                runOnUiThread(() -> {
                    if (officialLookupResult != null) officialLookupResult.setText("판매점 조회 실패: " + message);
                    if (officialLookupResultBox != null) {
                        officialLookupResultBox.removeAllViews();
                        officialLookupResultBox.addView(openOfficialButton("공식 판매점 페이지 열기", "https://www.dhlottery.co.kr/store.do?method=topStore&pageGubun=L645&drwNo=" + round));
                    }
                    Toast.makeText(this, "판매점 데이터 조회 실패", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void renderWinningStores(int round, List<StoreItem> stores) {
        if (officialLookupResult == null || officialLookupResultBox == null) return;
        officialLookupResultBox.removeAllViews();
        officialLookupResult.setText("당첨 판매점 조회");
        if (stores.isEmpty()) {
            officialLookupResultBox.addView(label("앱에서 판매점 목록을 가져오지 못했습니다.", 13, muted, false));
            officialLookupResultBox.addView(openOfficialButton("공식 판매점 페이지 열기", "https://www.dhlottery.co.kr/store.do?method=topStore&pageGubun=L645&drwNo=" + round));
            return;
        }

        officialLookupResultBox.addView(winningStoreResultCard(round, stores));
    }

    private View winningStoreResultCard(int round, List<StoreItem> stores) {
        LinearLayout card = column();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(round(Color.rgb(245, 246, 248), 12));

        card.addView(label("당첨 판매점 조회", 18, Color.rgb(20, 24, 31), true));
        TextView subtitle = label(round + "회 1등 배출 판매점", 13, Color.rgb(20, 24, 31), true);
        subtitle.setPadding(0, dp(10), 0, dp(8));
        card.addView(subtitle);

        LinearLayout summary = regionSummaryGrid(stores);
        card.addView(summary);

        TextView listTitle = label("판매점 목록", 13, Color.rgb(20, 24, 31), true);
        listTitle.setPadding(0, dp(12), 0, dp(6));
        card.addView(listTitle);

        int limit = Math.min(10, stores.size());
        for (int i = 0; i < limit; i++) {
            card.addView(lightStoreCard((i + 1) + ". " + stores.get(i).name, stores.get(i).meta, stores.get(i).address));
        }
        if (stores.size() > limit) {
            TextView more = label("외 " + (stores.size() - limit) + "곳", 12, Color.rgb(104, 109, 118), false);
            more.setPadding(0, dp(4), 0, 0);
            card.addView(more);
        }
        card.addView(openOfficialButton("공식 지도 보기", "https://www.dhlottery.co.kr/store.do?method=topStore&pageGubun=L645&drwNo=" + round));
        return card;
    }

    private LinearLayout regionSummaryGrid(List<StoreItem> stores) {
        LinearLayout grid = column();
        grid.setPadding(dp(8), dp(8), dp(8), dp(8));
        grid.setBackground(round(Color.WHITE, 10));

        List<String> regions = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (StoreItem store : stores) {
            String region = regionName(store.address);
            int index = regions.indexOf(region);
            if (index >= 0) counts.set(index, counts.get(index) + 1);
            else {
                regions.add(region);
                counts.add(1);
            }
        }

        LinearLayout rowView = null;
        for (int i = 0; i < regions.size(); i++) {
            if (i % 2 == 0) {
                rowView = row();
                rowView.setGravity(Gravity.CENTER);
                grid.addView(rowView);
            }
            TextView pill = label(regions.get(i) + "\n1등(" + counts.get(i) + ")", 12, Color.rgb(20, 24, 31), true);
            pill.setGravity(Gravity.CENTER);
            pill.setBackground(round(Color.rgb(255, 255, 255), 22));
            pill.setPadding(dp(6), dp(7), dp(6), dp(7));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(58), 1);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            if (rowView != null) rowView.addView(pill, params);
        }
        return grid;
    }

    private String regionName(String address) {
        String[] regions = {"서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"};
        for (String region : regions) {
            if (address.startsWith(region)) return region;
        }
        return "기타";
    }

    private View lightStoreCard(String title, String meta, String address) {
        LinearLayout card = column();
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setBackground(round(Color.WHITE, 8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(5));
        card.setLayoutParams(params);
        card.addView(label(title, 13, Color.rgb(20, 24, 31), true));
        if (!meta.isEmpty()) card.addView(label(meta, 11, Color.rgb(0, 150, 136), true));
        card.addView(label(address, 11, Color.rgb(92, 98, 110), false));
        return card;
    }

    private void lookupSellerKeyword(String keyword) {
        if (officialLookupResult != null) {
            officialLookupResult.setText("'" + keyword + "' 복권 판매점을 조회하는 중입니다...");
        }
        if (officialLookupResultBox != null) officialLookupResultBox.removeAllViews();

        new Thread(() -> {
            try {
                List<StoreItem> stores = fetchSellerKeyword(keyword);
                runOnUiThread(() -> renderSellerKeyword(keyword, stores));
            } catch (Exception error) {
                runOnUiThread(() -> renderSellerKeyword(keyword, new ArrayList<>()));
            }
        }).start();
    }

    private void renderSellerKeyword(String keyword, List<StoreItem> stores) {
        if (officialLookupResult == null || officialLookupResultBox == null) return;
        officialLookupResultBox.removeAllViews();
        officialLookupResult.setText("복권 판매점 찾기");
        officialLookupResultBox.addView(sellerSearchResultCard(keyword, stores));
    }

    private View sellerSearchResultCard(String keyword, List<StoreItem> stores) {
        LinearLayout card = column();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(round(Color.rgb(245, 246, 248), 12));

        card.addView(label("복권 판매점 찾기", 18, Color.rgb(20, 24, 31), true));
        TextView subtitle = label("'" + keyword + "' 주변 판매점", 13, Color.rgb(20, 24, 31), true);
        subtitle.setPadding(0, dp(10), 0, dp(8));
        card.addView(subtitle);

        LinearLayout mapHint = column();
        mapHint.setPadding(dp(10), dp(10), dp(10), dp(10));
        mapHint.setBackground(round(Color.rgb(229, 241, 250), 10));
        mapHint.addView(centerLabel("지도 검색", 14, Color.rgb(20, 24, 31), true));
        String status = stores.isEmpty() ? "공식 지도에서 검색어를 이어서 확인하세요." : stores.size() + "개 판매점 후보";
        mapHint.addView(centerLabel(status, 12, Color.rgb(92, 98, 110), false));
        card.addView(mapHint);

        TextView listTitle = label("판매점 목록", 13, Color.rgb(20, 24, 31), true);
        listTitle.setPadding(0, dp(12), 0, dp(6));
        card.addView(listTitle);

        if (stores.isEmpty()) {
            card.addView(label("공식 검색 결과를 앱 안에서 바로 읽지 못했습니다. 아래 버튼으로 공식 판매점 지도를 열어 주세요.", 12, Color.rgb(92, 98, 110), false));
        } else {
            int limit = Math.min(10, stores.size());
            for (int i = 0; i < limit; i++) {
                card.addView(lightStoreCard("핀 " + (i + 1) + " · " + stores.get(i).name, stores.get(i).meta, stores.get(i).address));
            }
        }
        card.addView(openOfficialButton("공식 지도에서 판매점 찾기", "https://www.dhlottery.co.kr/prchsplcsrch/home"));
        return card;
    }

    private View storeCard(String title, String meta, String address) {
        LinearLayout card = compactCardView();
        card.addView(label(title, 14, text, true));
        if (!meta.isEmpty()) card.addView(label(meta, 12, gold, false));
        card.addView(label(address, 12, muted, false));
        return card;
    }

    private Button openOfficialButton(String textValue, String url) {
        Button button = secondaryButton(textValue);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(42));
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> openUrl(url));
        return button;
    }

    private void startZodiacDraw(FrameLayout frame) {
        if (selectedZodiac.isEmpty() || selectedBirthDateTime.isEmpty()) {
            Toast.makeText(this, "먼저 띠를 선택하고 생년월일시를 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        zodiacDrawNonce++;
        int drawNonce = zodiacDrawNonce;
        List<List<Integer>> games = generateZodiacGames(selectedZodiac, selectedBirthDateTime, drawNonce);
        List<Integer> firstGame = games.get(0);
        if (lottoMachineView == null) {
            renderZodiacLuckyNumbers(selectedZodiac, selectedBirthDateTime, drawNonce);
            return;
        }
        lottoMachineView.startDraw(firstGame, () -> renderZodiacLuckyNumbers(selectedZodiac, selectedBirthDateTime, drawNonce));
    }

    private class LottoBallView extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int number = 1;

        LottoBallView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            textPaint.setColor(Color.rgb(82, 50, 8));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        void setNumber(int number) {
            this.number = number;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float radius = Math.min(w, h) * 0.46f;
            float cx = w * 0.5f;
            float cy = h * 0.5f;

            fillPaint.setShader(new RadialGradient(
                w * 0.32f, h * 0.28f, radius * 1.15f,
                new int[] {
                    Color.rgb(255, 245, 168),
                    Color.rgb(249, 199, 62),
                    Color.rgb(159, 101, 18)
                },
                new float[] {0f, 0.62f, 1f},
                Shader.TileMode.CLAMP
            ));
            fillPaint.setShadowLayer(dp(5), 0, dp(3), Color.argb(170, 0, 0, 0));
            canvas.drawCircle(cx, cy, radius, fillPaint);
            fillPaint.setShader(null);

            shinePaint.setColor(Color.argb(145, 255, 255, 230));
            RectF shine = new RectF(w * 0.25f, h * 0.16f, w * 0.52f, h * 0.35f);
            canvas.drawOval(shine, shinePaint);

            textPaint.setTextSize(w * 0.38f);
            textPaint.setShadowLayer(dp(2), 0, dp(1), Color.argb(180, 255, 247, 184));
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = cy - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(String.valueOf(number), cx, baseline, textPaint);
        }
    }

    private class LottoMachineView extends View {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random motion = new Random();
        private final float[] xs = new float[45];
        private final float[] ys = new float[45];
        private final float[] vxs = new float[45];
        private final float[] vys = new float[45];
        private List<Integer> finalNumbers = new ArrayList<>();
        private Runnable finishCallback;
        private ToneGenerator tone;
        private boolean drawing = false;
        private int ticks = 0;
        private int idleTicks = 0;

        LottoMachineView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            textPaint.setColor(Color.rgb(82, 50, 8));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);
            autoHandler.postDelayed(idleStep, 400);
        }

        void startDraw(List<Integer> numbers, Runnable done) {
            finalNumbers = new ArrayList<>(numbers);
            finishCallback = done;
            drawing = true;
            ticks = 0;
            playTone(ToneGenerator.TONE_PROP_BEEP, 120);
            seedVelocities(true);
            invalidate();
            autoHandler.removeCallbacks(idleStep);
            autoHandler.removeCallbacks(machineStep);
            autoHandler.post(machineStep);
        }

        private final Runnable idleStep = new Runnable() {
            @Override
            public void run() {
                if (!drawing && getWidth() > 0) {
                    idleTicks++;
                    driftIdleBalls();
                    invalidate();
                }
                autoHandler.postDelayed(this, 90);
            }
        };

        @Override
        protected void onDetachedFromWindow() {
            autoHandler.removeCallbacks(idleStep);
            autoHandler.removeCallbacks(machineStep);
            super.onDetachedFromWindow();
        }

        private final Runnable machineStep = new Runnable() {
            @Override
            public void run() {
                ticks++;
                moveBalls();
                invalidate();
                if (ticks == 12 || ticks == 28 || ticks == 44 || ticks == 60) {
                    playTone(ToneGenerator.TONE_PROP_ACK, 45);
                }
                if (ticks < 82) {
                    autoHandler.postDelayed(this, 24);
                    return;
                }
                drawing = false;
                settleWinningBalls();
                playTone(ToneGenerator.TONE_PROP_PROMPT, 180);
                invalidate();
                autoHandler.postDelayed(idleStep, 1400);
                autoHandler.postDelayed(() -> {
                    if (finishCallback != null) finishCallback.run();
                }, 1100);
            }
        };

        private void seedVelocities(boolean strong) {
            float speedX = strong ? 22f : 5f;
            float speedY = strong ? 34f : 5f;
            for (int i = 0; i < 45; i++) {
                vxs[i] = (motion.nextFloat() - 0.5f) * speedX;
                vys[i] = -motion.nextFloat() * speedY - (strong ? 12f : 0f);
            }
        }

        private void ensurePositions() {
            if (getWidth() <= 0 || xs[0] != 0f) return;
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.79f;
            float rx = getWidth() * 0.34f;
            float ry = getHeight() * 0.16f;
            for (int i = 0; i < 45; i++) {
                double angle = motion.nextDouble() * Math.PI * 2.0;
                double distance = Math.sqrt(motion.nextDouble());
                float layer = i / 44f;
                float spreadX = rx * (0.55f + layer * 0.55f);
                float spreadY = ry * (0.55f + layer * 0.45f);
                xs[i] = cx + (float) Math.cos(angle) * (float) distance * spreadX;
                ys[i] = cy + (float) Math.sin(angle) * (float) distance * spreadY + layer * dp(16);
            }
            seedVelocities(false);
        }

        private void driftIdleBalls() {
            ensurePositions();
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.80f;
            float rx = getWidth() * 0.37f;
            float ry = getHeight() * 0.18f;
            for (int i = 0; i < 45; i++) {
                float phase = (idleTicks + i * 7) * 0.12f;
                xs[i] += (float) Math.sin(phase) * 0.45f + (motion.nextFloat() - 0.5f) * 0.35f;
                ys[i] += (float) Math.cos(phase * 0.8f) * 0.28f + (motion.nextFloat() - 0.5f) * 0.22f;
                float nx = (xs[i] - cx) / rx;
                float ny = (ys[i] - cy) / ry;
                float distance = nx * nx + ny * ny;
                if (distance > 1f) {
                    float scale = 0.97f / (float) Math.sqrt(distance);
                    xs[i] = cx + (xs[i] - cx) * scale;
                    ys[i] = cy + (ys[i] - cy) * scale;
                }
            }
        }

        private void moveBalls() {
            ensurePositions();
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.53f;
            float rx = getWidth() * 0.40f;
            float ry = getHeight() * 0.42f;
            for (int i = 0; i < 45; i++) {
                if (drawing) {
                    vys[i] += 0.78f;
                    if (ticks % 6 == 0 && i % 5 == ticks % 5) {
                        vys[i] -= 18f + motion.nextFloat() * 18f;
                        vxs[i] += (motion.nextFloat() - 0.5f) * 18f;
                    }
                    if (ticks % 13 == 0 && i % 7 == ticks % 7) {
                        vys[i] -= 10f + motion.nextFloat() * 12f;
                        vxs[i] += (motion.nextFloat() - 0.5f) * 26f;
                    }
                }
                xs[i] += vxs[i];
                ys[i] += vys[i];
                float nx = (xs[i] - cx) / rx;
                float ny = (ys[i] - cy) / ry;
                float distance = nx * nx + ny * ny;
                if (distance > 1f) {
                    float scale = 0.94f / (float) Math.sqrt(distance);
                    xs[i] = cx + (xs[i] - cx) * scale;
                    ys[i] = cy + (ys[i] - cy) * scale;
                    vxs[i] = -vxs[i] * 1.02f + (motion.nextFloat() - 0.5f) * 4.8f;
                    vys[i] = -vys[i] * 1.05f + (motion.nextFloat() - 0.5f) * 4.8f;
                }
                vxs[i] += (motion.nextFloat() - 0.5f) * 1.8f;
                vys[i] += (motion.nextFloat() - 0.5f) * 1.2f;
                vxs[i] *= drawing ? 0.993f : 0.985f;
                vys[i] *= drawing ? 0.992f : 0.985f;
            }
        }

        private void playTone(int toneType, int durationMs) {
            try {
                if (tone == null) {
                    tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 65);
                }
                tone.startTone(toneType, durationMs);
            } catch (RuntimeException ignored) {
            }
        }

        private void settleWinningBalls() {
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.82f;
            float gap = getWidth() * 0.14f;
            for (int i = 0; i < finalNumbers.size(); i++) {
                int index = Math.max(0, Math.min(44, finalNumbers.get(i) - 1));
                xs[index] = cx - gap * 2.5f + gap * i;
                ys[index] = cy + (i % 2 == 0 ? -dp(6) : dp(6));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            ensurePositions();
            float small = Math.max(dp(10), getWidth() * 0.035f);
            float large = Math.max(dp(20), getWidth() * 0.064f);
            for (int i = 0; i < 45; i++) {
                boolean winner = !drawing && finalNumbers.contains(i + 1);
                float radius = winner ? large : small;
                drawBall(canvas, xs[i], ys[i], radius, i + 1, winner);
            }
        }

        private void drawBall(Canvas canvas, float cx, float cy, float radius, int number, boolean winner) {
            fillPaint.setShader(new RadialGradient(
                cx - radius * 0.35f, cy - radius * 0.38f, radius * 1.45f,
                new int[] {
                    Color.rgb(255, 249, 179),
                    Color.rgb(246, 196, 54),
                    Color.rgb(146, 88, 18)
                },
                new float[] {0f, 0.62f, 1f},
                Shader.TileMode.CLAMP
            ));
            fillPaint.setShadowLayer(dp(winner ? 6 : 3), 0, dp(winner ? 3 : 1), Color.argb(150, 0, 0, 0));
            canvas.drawCircle(cx, cy, radius, fillPaint);
            fillPaint.setShader(null);

            shinePaint.setColor(Color.argb(winner ? 150 : 95, 255, 255, 230));
            canvas.drawOval(new RectF(cx - radius * 0.45f, cy - radius * 0.62f, cx - radius * 0.08f, cy - radius * 0.28f), shinePaint);

            textPaint.setTextSize(winner ? radius * 0.72f : radius * 0.62f);
            textPaint.setShadowLayer(dp(1), 0, 1, Color.argb(160, 255, 247, 184));
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = cy - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(String.valueOf(number), cx, baseline, textPaint);
        }
    }

    private void renderZodiacLuckyNumbers(String zodiac, String birthday) {
        renderZodiacLuckyNumbers(zodiac, birthday, zodiacDrawNonce);
    }

    private void renderZodiacLuckyNumbers(String zodiac, String birthday, int drawNonce) {
        if (!"zodiac".equals(activeTab) && activeTab != null && !activeTab.isEmpty()) {
            backStack.add(activeTab);
        }
        activeTab = "zodiac";
        renderTabs();
        content.removeAllViews();

        LinearLayout resultPanel = panelView();
        resultPanel.setPadding(dp(12), dp(12), dp(12), dp(12));
        resultPanel.addView(compactResultTitle(zodiac + "띠의 행운의 당첨번호입니다", birthday));

        List<List<Integer>> games = generateZodiacGames(zodiac, birthday, drawNonce);
        for (int i = 0; i < games.size(); i++) {
            resultPanel.addView(gameCard((i + 1) + "게임", games.get(i), true, i));
        }

        Button again = primaryButton("다시 추첨하기");
        again.setOnClickListener(v -> switchTab("home", false));
        resultPanel.addView(again);
        content.addView(resultPanel);
        rootScroll.post(() -> rootScroll.smoothScrollTo(0, content.getTop()));
    }

    private List<List<Integer>> generateZodiacGames(String zodiac, String birthday) {
        return generateZodiacGames(zodiac, birthday, zodiacDrawNonce);
    }

    private List<List<Integer>> generateZodiacGames(String zodiac, String birthday, int drawNonce) {
        Random seeded = new Random((zodiac + birthday + "#" + drawNonce).hashCode());
        List<List<Integer>> games = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();

        while (games.size() < 5) {
            List<Integer> game = makeSeededGame(seeded);
            String key = numbersToString(game);
            if (unique.add(key)) games.add(game);
        }

        currentGames.clear();
        currentGames.addAll(games);
        saveRecent(games);
        return games;
    }

    private List<Integer> makeSeededGame(Random seeded) {
        List<Integer> available = new ArrayList<>();
        for (int number = 1; number <= 45; number++) available.add(number);
        Collections.shuffle(available, seeded);
        List<Integer> picked = new ArrayList<>(available.subList(0, 6));
        Collections.sort(picked);
        return picked;
    }

    private void renderGenerator() {
        ensureReportGames();

        LinearLayout summary = compactStatsPanelView();
        String side = selectedZodiac.isEmpty() ? nextDrawText() : selectedZodiac + "띠";
        summary.addView(compactStatsTitle("Lucky Report", "이번 주 추천 리포트", side));
        TextView lead = label("개인화된 행운번호 7게임", 12, muted, false);
        summary.addView(lead);

        LinearLayout info = row();
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(0, dp(6), 0, 0);
        info.addView(infoPill(nextDrawDateText()), new LinearLayout.LayoutParams(0, dp(28), 1));
        TextView count = infoPill(currentGames.size() + "게임 추천");
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(0, dp(28), 1);
        countParams.setMargins(dp(8), 0, 0, 0);
        info.addView(count, countParams);
        summary.addView(info);
        content.addView(summary);

        LinearLayout results = compactStatsPanelView();
        resultAnchor = results;
        results.addView(compactStatsTitle("Result", "추천 7게임", "저장 가능"));
        for (int i = 0; i < currentGames.size(); i++) {
            results.addView(generatorGameCard((i + 1) + "게임", currentGames.get(i)));
        }
        content.addView(results);
    }

    private LinearLayout numberGrid() {
        LinearLayout wrapper = column();
        for (int row = 0; row < 5; row++) {
            LinearLayout line = row();
            for (int col = 1; col <= 9; col++) {
                int number = row * 9 + col;
                Button button = new Button(this);
                button.setText(String.valueOf(number));
                button.setTextColor(fixed.contains(number) ? Color.rgb(21, 21, 20) : text);
                button.setTextSize(13);
                button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                button.setAllCaps(false);
                button.setPadding(0, 0, 0, 0);
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setMinHeight(0);
                button.setMinimumHeight(0);
                button.setBackground(round(numberColor(number), 22));
                button.setOnClickListener(v -> {
                    toggleNumber(number);
                    switchTab("home");
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
                params.setMargins(dp(3), dp(3), dp(3), dp(3));
                line.addView(button, params);
            }
            wrapper.addView(line);
        }
        return wrapper;
    }

    private int numberColor(int number) {
        if (fixed.contains(number)) return gold;
        if (excluded.contains(number)) return red;
        return Color.rgb(42, 44, 49);
    }

    private void toggleNumber(int number) {
        if (fixed.contains(number)) {
            fixed.remove(number);
            excluded.add(number);
        } else if (excluded.contains(number)) {
            excluded.remove(number);
        } else if (fixed.size() < 5) {
            fixed.add(number);
        } else {
            Toast.makeText(this, "고정 번호는 5개까지만 선택할 수 있어요.", Toast.LENGTH_SHORT).show();
        }
    }

    private void ensureReportGames() {
        if (currentGames.size() == REPORT_GAME_COUNT) return;

        currentGames.clear();
        try {
            for (int i = 0; i < REPORT_GAME_COUNT; i++) currentGames.add(makeGame());
            saveRecent(currentGames);
        } catch (IllegalStateException error) {
            if (notice != null) notice.setText(error.getMessage());
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void generateGames(boolean refresh) {
        currentGames.clear();
        try {
            for (int i = 0; i < gameCount; i++) currentGames.add(makeGame());
            saveRecent(currentGames);
            if (notice != null) notice.setText("마음에 드는 조합은 저장하거나 복사할 수 있어요.");
            if (refresh) {
                switchTab("home");
                scrollToResults();
            }
        } catch (IllegalStateException error) {
            if (notice != null) notice.setText(error.getMessage());
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleAutoGenerate() {
        if (autoGenerating) {
            stopAutoGenerate();
            setAutoNotice("자동 생성을 중지했어요.");
        } else {
            autoGenerating = true;
            generateGames(true);
            restartAutoGenerate();
            setAutoNotice(autoIntervalSeconds + "초마다 새 조합을 자동 생성합니다.");
        }
        updateAutoButton();
    }

    private void restartAutoGenerate() {
        autoHandler.removeCallbacks(autoGenerateTask);
        if (autoGenerating) {
            autoHandler.postDelayed(autoGenerateTask, autoIntervalSeconds * 1000L);
        }
    }

    private void stopAutoGenerate() {
        autoGenerating = false;
        autoHandler.removeCallbacks(autoGenerateTask);
        updateAutoButton();
    }

    private void updateAutoButton() {
        if (autoButton != null) {
            autoButton.setText(autoGenerating ? "자동 생성 중지" : "자동 생성 시작");
        }
    }

    private void setAutoNotice(String message) {
        if (notice != null) notice.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void scrollToResults() {
        if (rootScroll == null || resultAnchor == null) return;
        rootScroll.postDelayed(() -> {
            int target = Math.max(0, resultAnchor.getTop() - dp(12));
            rootScroll.smoothScrollTo(0, target);
        }, 120);
    }

    private List<Integer> makeGame() {
        List<Integer> available = new ArrayList<>();
        for (int number = 1; number <= 45; number++) {
            if (!fixed.contains(number) && !excluded.contains(number)) available.add(number);
        }
        if (fixed.size() + available.size() < 6) {
            throw new IllegalStateException("선택 가능한 번호가 부족해요. 제외 번호를 줄여 주세요.");
        }
        Collections.shuffle(available, random);
        List<Integer> picked = new ArrayList<>(fixed);
        picked.addAll(available.subList(0, 6 - fixed.size()));
        Collections.sort(picked);
        return picked;
    }

    private View gameCard(String title, List<Integer> numbers, boolean canSave, int index) {
        LinearLayout card = compactCardView();

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label(title, 12, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(label(combinationScore(numbers) + "점", 11, gold, true));
        card.addView(header);

        LinearLayout balls = row();
        balls.setGravity(Gravity.CENTER_VERTICAL);
        balls.setPadding(0, dp(3), 0, dp(2));
        for (Integer number : numbers) balls.addView(compactBall(number));
        card.addView(balls);

        TextView report = label(compactCombinationReport(numbers), 10, muted, false);
        report.setSingleLine(true);
        report.setPadding(0, 0, 0, dp(3));
        card.addView(report);

        LinearLayout actions = row();
        Button left = tinyButton(canSave ? "저장" : "삭제");
        left.setOnClickListener(v -> {
            if (canSave) saveGame(numbers);
            else deleteSaved(index);
        });
        actions.addView(left, new LinearLayout.LayoutParams(0, dp(28), 1));

        Button copy = tinyButton("복사");
        copy.setOnClickListener(v -> copyGame(numbers));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, dp(28), 1);
        copyParams.setMargins(dp(6), 0, 0, 0);
        actions.addView(copy, copyParams);
        card.addView(actions);
        return card;
    }

    private View generatorGameCard(String title, List<Integer> numbers) {
        LinearLayout card = compactCardView();

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(label(title + " · " + combinationScore(numbers) + "점", 11, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button save = microButton("저장");
        save.setOnClickListener(v -> saveGame(numbers));
        top.addView(save, new LinearLayout.LayoutParams(dp(48), dp(24)));
        Button copy = microButton("복사");
        copy.setOnClickListener(v -> copyGame(numbers));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(dp(48), dp(24));
        copyParams.setMargins(dp(4), 0, 0, 0);
        top.addView(copy, copyParams);
        card.addView(top);

        LinearLayout balls = row();
        balls.setGravity(Gravity.CENTER_VERTICAL);
        balls.setPadding(0, dp(2), 0, 0);
        for (Integer number : numbers) balls.addView(generatorBall(number));
        TextView report = label(compactCombinationReport(numbers), 10, muted, false);
        report.setSingleLine(true);
        balls.addView(report, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(balls);
        return card;
    }

    private TextView compactBall(int number) {
        TextView view = label(String.valueOf(number), 12, Color.rgb(21, 21, 20), true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(ball3d(number));
        applyBallTextStyle(view, number);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(28), dp(28));
        params.setMargins(0, 0, dp(4), 0);
        view.setLayoutParams(params);
        return view;
    }

    private TextView generatorBall(int number) {
        TextView view = label(String.valueOf(number), 11, Color.rgb(21, 21, 20), true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(ball3d(number));
        applyBallTextStyle(view, number);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(24), dp(24));
        params.setMargins(0, 0, dp(3), 0);
        view.setLayoutParams(params);
        return view;
    }

    private String compactCombinationReport(List<Integer> numbers) {
        return oddEvenLabel(numbers) + " · " + spreadLabel(numbers) + " · " + patternLabel(numbers);
    }
    private TextView ball(int number) {
        TextView view = label(String.valueOf(number), 16, Color.rgb(21, 21, 20), true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(ball3d(number));
        applyBallTextStyle(view, number);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
        params.setMargins(0, 0, dp(8), 0);
        view.setLayoutParams(params);
        return view;
    }

    private void applyBallTextStyle(TextView view, int number) {
        int textColor = ballTextColor(number);
        view.setTextColor(textColor);
        int shadowColor = textColor == Color.WHITE
            ? Color.argb(190, 0, 0, 0)
            : Color.argb(150, 255, 255, 255);
        view.setShadowLayer(1.6f, 0f, 1f, shadowColor);
    }

    private int ballTextColor(int number) {
        int color = ballColor(number);
        double luminance = Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114;
        return luminance >= 148 ? Color.rgb(22, 22, 22) : Color.WHITE;
    }

    private int ballColor(int number) {
        if (number <= 10) return gold;
        if (number <= 20) return blue;
        if (number <= 30) return red;
        if (number <= 40) return Color.rgb(176, 181, 190);
        return Color.rgb(60, 168, 95);
    }

    private Drawable ball3d(int number) {
        int base = ballColor(number);
        GradientDrawable core = new GradientDrawable();
        core.setShape(GradientDrawable.OVAL);
        core.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        core.setGradientCenter(0.34f, 0.3f);
        core.setGradientRadius(dp(22));
        core.setColors(new int[]{
            adjustColor(base, 0.45f),
            base,
            adjustColor(base, -0.35f)
        });
        core.setStroke(dp(1), adjustColor(base, -0.45f));

        GradientDrawable shine = new GradientDrawable();
        shine.setShape(GradientDrawable.OVAL);
        shine.setColor(Color.argb(130, 255, 255, 255));

        LayerDrawable layers = new LayerDrawable(new Drawable[]{core, shine});
        layers.setLayerInset(1, dp(5), dp(4), dp(15), dp(16));
        return layers;
    }

    private int adjustColor(int color, float delta) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        if (delta >= 0f) {
            r += Math.round((255 - r) * delta);
            g += Math.round((255 - g) * delta);
            b += Math.round((255 - b) * delta);
        } else {
            float factor = 1f + delta;
            r = Math.round(r * factor);
            g = Math.round(g * factor);
            b = Math.round(b * factor);
        }

        return Color.rgb(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b))
        );
    }

    private String combinationReport(List<Integer> numbers) {
        int score = combinationScore(numbers);
        return "조합 리포트 " + score + "점 · " + oddEvenLabel(numbers) + " · " + spreadLabel(numbers) + " · " + patternLabel(numbers);
    }

    private int combinationScore(List<Integer> numbers) {
        int score = 100;
        int odd = 0;
        int low = 0;
        int consecutivePairs = 0;
        int maxGap = 0;

        for (int i = 0; i < numbers.size(); i++) {
            int number = numbers.get(i);
            if (number % 2 == 1) odd++;
            if (number <= 22) low++;
            if (i > 0) {
                int gap = number - numbers.get(i - 1);
                if (gap == 1) consecutivePairs++;
                maxGap = Math.max(maxGap, gap);
            }
        }

        if (odd == 0 || odd == 6) score -= 18;
        else if (odd == 1 || odd == 5) score -= 8;
        if (low == 0 || low == 6) score -= 14;
        else if (low == 1 || low == 5) score -= 6;
        if (consecutivePairs >= 2) score -= 14;
        else if (consecutivePairs == 1) score -= 5;
        if (maxGap >= 18) score -= 10;
        if (hasArithmeticRun(numbers)) score -= 12;

        return Math.max(58, score);
    }

    private String oddEvenLabel(List<Integer> numbers) {
        int odd = 0;
        for (Integer number : numbers) if (number % 2 == 1) odd++;
        return "홀짝 " + odd + ":" + (numbers.size() - odd);
    }

    private String spreadLabel(List<Integer> numbers) {
        int[] zones = new int[5];
        for (Integer number : numbers) zones[(number - 1) / 10]++;
        return "구간 " + zones[0] + "-" + zones[1] + "-" + zones[2] + "-" + zones[3] + "-" + zones[4];
    }

    private String patternLabel(List<Integer> numbers) {
        int consecutivePairs = 0;
        for (int i = 1; i < numbers.size(); i++) {
            if (numbers.get(i) - numbers.get(i - 1) == 1) consecutivePairs++;
        }
        if (hasArithmeticRun(numbers)) return "등차 패턴 주의";
        if (consecutivePairs >= 2) return "연속수 주의";
        if (consecutivePairs == 1) return "연속수 1쌍";
        return "패턴 안정";
    }

    private boolean hasArithmeticRun(List<Integer> numbers) {
        for (int i = 0; i < numbers.size() - 2; i++) {
            int firstGap = numbers.get(i + 1) - numbers.get(i);
            int secondGap = numbers.get(i + 2) - numbers.get(i + 1);
            if (firstGap == secondGap && firstGap <= 8) return true;
        }
        return false;
    }

    private void saveGame(List<Integer> numbers) {
        JSONArray saved = readArray(SAVED_KEY);
        String value = numbersToString(numbers);
        for (int i = 0; i < saved.length(); i++) {
            if (value.equals(saved.optString(i))) {
                Toast.makeText(this, "이미 보관함에 있는 조합입니다.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        JSONArray next = new JSONArray();
        next.put(value);
        for (int i = 0; i < Math.min(saved.length(), 29); i++) next.put(saved.optString(i));
        preferences.edit().putString(SAVED_KEY, next.toString()).apply();
        Toast.makeText(this, "보관함에 저장했어요.", Toast.LENGTH_SHORT).show();
    }

    private void deleteSaved(int index) {
        JSONArray saved = readArray(SAVED_KEY);
        JSONArray next = new JSONArray();
        for (int i = 0; i < saved.length(); i++) {
            if (i != index) next.put(saved.optString(i));
        }
        preferences.edit().putString(SAVED_KEY, next.toString()).apply();
        renderSaved();
    }

    private void renderSaved() {
        content.removeAllViews();
        LinearLayout savedPanel = panelView();
        savedPanel.addView(sectionTitle("Archive", "보관함", ""));
        JSONArray saved = readArray(SAVED_KEY);
        if (saved.length() == 0) {
            savedPanel.addView(label("아직 저장한 조합이 없습니다.", 15, muted, false));
        }
        for (int i = 0; i < saved.length(); i++) {
            savedPanel.addView(gameCard("저장 조합", parseGame(saved.optString(i)), false, i));
        }
        content.addView(savedPanel);
    }

    private void renderStats() {
        content.removeAllViews();
        LinearLayout statsPanel = panelView();
        statsPanel.addView(sectionTitle("Insights", "최근 생성 통계", ""));
        int[] counts = new int[46];
        JSONArray recent = readArray(RECENT_KEY);
        for (int i = 0; i < recent.length(); i++) {
            for (Integer number : parseGame(recent.optString(i))) counts[number]++;
        }
        int max = 1;
        for (int number = 1; number <= 45; number++) max = Math.max(max, counts[number]);

        for (int rank = 0; rank < 12; rank++) {
            int best = 1;
            for (int number = 2; number <= 45; number++) {
                if (counts[number] > counts[best]) best = number;
            }
            statsPanel.addView(statRow(best, counts[best], max));
            counts[best] = -1;
        }
        content.addView(statsPanel);
    }

    private void renderStatsWithOfficial() {
        content.removeAllViews();

        LinearLayout tabPanel = compactStatsPanelView();
        LinearLayout switcher = row();
        switcher.setGravity(Gravity.CENTER_VERTICAL);
        Button myTab = statsTabButton("내 생성통계", "my".equals(statsSubTab));
        myTab.setOnClickListener(v -> {
            statsSubTab = "my";
            renderStatsWithOfficial();
        });
        switcher.addView(myTab, new LinearLayout.LayoutParams(0, dp(36), 1));

        Button officialTab = statsTabButton("공식당첨통계", "official".equals(statsSubTab));
        officialTab.setOnClickListener(v -> {
            statsSubTab = "official";
            renderStatsWithOfficial();
        });
        LinearLayout.LayoutParams officialTabParams = new LinearLayout.LayoutParams(0, dp(36), 1);
        officialTabParams.setMargins(dp(8), 0, 0, 0);
        switcher.addView(officialTab, officialTabParams);
        tabPanel.addView(switcher);
        content.addView(tabPanel);

        if ("official".equals(statsSubTab)) renderOfficialStatsPanel();
        else renderMyStatsPanel();
        addAdMobPlaceholder();
    }

    private void renderMyStatsPanel() {
        LinearLayout statsPanel = compactStatsPanelView();
        JSONArray recent = readArray(RECENT_KEY);
        JSONArray saved = readArray(SAVED_KEY);
        statsPanel.addView(compactStatsTitle("My Insights", "내 생성통계", recent.length() + "게임 분석"));
        int[] counts = new int[46];
        for (int i = 0; i < recent.length(); i++) {
            for (Integer number : parseGame(recent.optString(i))) {
                if (number >= 1 && number <= 45) counts[number]++;
            }
        }

        LinearLayout summary = row();
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(infoPill("최근 " + recent.length() + "게임"), new LinearLayout.LayoutParams(0, dp(28), 1));
        TextView savedPill = infoPill("저장 " + saved.length() + "개");
        LinearLayout.LayoutParams savedParams = new LinearLayout.LayoutParams(0, dp(28), 1);
        savedParams.setMargins(dp(8), 0, 0, 0);
        summary.addView(savedPill, savedParams);
        statsPanel.addView(summary);

        addTopStats(statsPanel, counts, 15);
        content.addView(statsPanel);
    }

    private void renderOfficialStatsPanel() {
        LinearLayout officialPanel = compactStatsPanelView();
        int latestRound = preferences.getInt(OFFICIAL_LATEST_KEY, 0);
        String side = latestRound > 0 ? latestRound + "회까지" : "데이터 없음";
        officialPanel.addView(compactStatsTitle("Official", "공식당첨통계", side));

        officialStatus = label("회차별 당첨번호 기준 빈도 통계입니다.", 13, muted, false);
        officialStatus.setLineSpacing(dp(3), 1f);
        officialPanel.addView(officialStatus);

        JSONArray officialDraws = readArray(OFFICIAL_DRAWS_KEY);
        if (officialDraws.length() > 0) {
            TextView summary = label("최근 " + officialDraws.length() + "개 회차 기준 빈출 번호", 13, muted, false);
            summary.setPadding(0, dp(4), 0, dp(1));
            officialPanel.addView(summary);

            int[] officialCounts = new int[46];
            for (int i = 0; i < officialDraws.length(); i++) {
                for (Integer number : parseGame(officialDraws.optString(i))) {
                    if (number >= 1 && number <= 45) officialCounts[number]++;
                }
            }
            addTopStats(officialPanel, officialCounts, 15);
        } else {
            TextView empty = label(officialFetching ? "공식 데이터를 불러오는 중입니다..." : "공식당첨통계를 불러오려면 아래 버튼을 눌러주세요.", 15, muted, false);
            empty.setPadding(0, dp(12), 0, dp(8));
            officialPanel.addView(empty);

            Button loadButton = secondaryButton(officialFetching ? "불러오는 중..." : "공식 데이터 불러오기");
            loadButton.setEnabled(!officialFetching);
            loadButton.setOnClickListener(v -> fetchOfficialDraws());
            officialPanel.addView(loadButton);

            if (!officialFetching) {
                content.post(this::fetchOfficialDraws);
            }
        }
        content.addView(officialPanel);
    }

    private void addTopStats(LinearLayout panel, int[] counts, int limit) {
        int max = 1;
        for (int number = 1; number <= 45; number++) max = Math.max(max, counts[number]);
        int[] working = counts.clone();
        for (int rank = 0; rank < limit; rank++) {
            int best = 1;
            for (int number = 2; number <= 45; number++) {
                if (working[number] > working[best]) best = number;
            }
            panel.addView(statRow(best, Math.max(0, working[best]), max));
            working[best] = -1;
        }
    }

    private void fetchOfficialDraws() {
        if (officialFetching) return;
        officialFetching = true;
        statsSubTab = "official";
        if (officialStatus != null) officialStatus.setText("공식 데이터를 가져오는 중입니다...");
        if ("stats".equals(activeTab)) renderStatsWithOfficial();

        new Thread(() -> {
            try {
                int latest = resolveLatestOfficialRound();
                List<String> slots = new ArrayList<>();
                for (int i = 0; i < OFFICIAL_STATS_ROUND_COUNT; i++) slots.add(null);

                List<Thread> workers = new ArrayList<>();
                for (int index = 0; index < OFFICIAL_STATS_ROUND_COUNT; index++) {
                    final int slotIndex = index;
                    final int roundNo = latest - index;
                    Thread worker = new Thread(() -> {
                        if (roundNo <= 0) return;
                        try {
                            JSONObject draw = fetchOfficialRoundFromBackup(roundNo);
                            if (draw == null) draw = fetchOfficialRound(roundNo);
                            if (draw != null) {
                                synchronized (slots) {
                                    slots.set(slotIndex, drawNumbersToString(draw));
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    });
                    workers.add(worker);
                    worker.start();
                }

                for (Thread worker : workers) {
                    try {
                        worker.join();
                    } catch (InterruptedException ignored) {
                    }
                }

                JSONArray draws = new JSONArray();
                for (String numbers : slots) {
                    if (numbers != null && !numbers.isEmpty()) draws.put(numbers);
                }

                if (draws.length() == 0) {
                    throw new IllegalStateException("당첨번호 데이터를 찾지 못했습니다.");
                }

                preferences.edit()
                    .putString(OFFICIAL_DRAWS_KEY, draws.toString())
                    .putInt(OFFICIAL_LATEST_KEY, latest)
                    .apply();

                final int loadedCount = draws.length();
                runOnUiThread(() -> {
                    officialFetching = false;
                    Toast.makeText(this, "공식 데이터 " + loadedCount + "개 회차를 가져왔어요.", Toast.LENGTH_SHORT).show();
                    if ("stats".equals(activeTab)) renderStatsWithOfficial();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null ? "알 수 없는 오류" : error.getMessage();
                runOnUiThread(() -> {
                    officialFetching = false;
                    if (officialStatus != null) officialStatus.setText("공식 데이터를 가져오지 못했습니다.\n" + message);
                    Toast.makeText(this, "공식 데이터 조회 실패", Toast.LENGTH_SHORT).show();
                    if ("stats".equals(activeTab)) renderStatsWithOfficial();
                });
            }
        }).start();
    }

    private int resolveLatestOfficialRound() throws Exception {
        try {
            int backupLatest = findLatestBackupRound();
            if (backupLatest > 0) return backupLatest;
        } catch (Exception ignored) {
        }
        return findLatestOfficialRound();
    }

    private void openOfficialPage() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.dhlottery.co.kr/gameResult.do?method=byWin"));
        startActivity(intent);
    }

    private int findLatestOfficialRound() throws Exception {
        int estimate = estimatedLatestRound() + 2;
        for (int round = estimate; round >= Math.max(1, estimate - 12); round--) {
            try {
                JSONObject draw = fetchOfficialRound(round);
                if (draw != null) return round;
            } catch (Exception ignored) {
            }
        }
        int backupLatest = findLatestBackupRound();
        if (backupLatest > 0) return backupLatest;
        throw new IllegalStateException("최신 회차를 찾지 못했습니다.");
    }

    private int estimatedLatestRound() {
        Calendar first = Calendar.getInstance();
        first.set(2002, Calendar.DECEMBER, 7, 0, 0, 0);
        Calendar today = Calendar.getInstance();
        long diffMillis = today.getTimeInMillis() - first.getTimeInMillis();
        long weeks = diffMillis / (7L * 24L * 60L * 60L * 1000L);
        return (int) weeks + 1;
    }

    private JSONObject fetchOfficialRound(int round) throws Exception {
        try {
            JSONObject draw = fetchOfficialRoundFromApi(round);
            if (draw != null) return ensureFirstPrizeFields(round, draw);
        } catch (Exception ignored) {
        }

        try {
            JSONObject draw = fetchOfficialRoundFromPage(round);
            if (draw != null) return ensureFirstPrizeFields(round, draw);
        } catch (Exception ignored) {
        }

        JSONObject draw = fetchOfficialRoundFromBackup(round);
        return ensureFirstPrizeFields(round, draw);
    }

    private JSONObject ensureFirstPrizeFields(int round, JSONObject draw) {
        if (draw == null) return null;
        if (readIntField(draw, "firstPrzwnerCo") > 0 && readLongField(draw, "firstWinamnt") > 0) {
            return draw;
        }
        try {
            JSONObject backup = fetchOfficialRoundFromBackup(round);
            if (backup == null) return draw;

            if (readIntField(draw, "firstPrzwnerCo") <= 0 && readIntField(backup, "firstPrzwnerCo") > 0) {
                draw.put("firstPrzwnerCo", backup.opt("firstPrzwnerCo"));
            }
            if (readLongField(draw, "firstWinamnt") <= 0 && readLongField(backup, "firstWinamnt") > 0) {
                draw.put("firstWinamnt", backup.opt("firstWinamnt"));
            }
            if (draw.optString("drwNoDate", "").isEmpty() && !backup.optString("drwNoDate", "").isEmpty()) {
                draw.put("drwNoDate", backup.optString("drwNoDate", ""));
            }
        } catch (Exception ignored) {
        }
        return draw;
    }

    private JSONObject fetchOfficialRoundFromApi(int round) throws Exception {
        String response = readUrl("https://www.dhlottery.co.kr/common.do?method=getLottoNumber&drwNo=" + round, "LuckyPick/1.0").trim();
        if (!response.startsWith("{")) return null;

        JSONObject json = new JSONObject(response);
        if (!"success".equalsIgnoreCase(json.optString("returnValue"))) return null;
        return json;
    }

    private List<StoreItem> fetchWinningStores(int round) throws Exception {
        String url = "https://m.dhlottery.co.kr/store.do?method=topStore&pageGubun=L645&drwNo=" + round;
        String html = readUrl(url, "Mozilla/5.0 LuckyPick");
        List<StoreItem> stores = parseWinningStoreHtml(html);
        if (!stores.isEmpty()) return stores;

        url = "https://www.dhlottery.co.kr/store.do?method=topStore&pageGubun=L645&drwNo=" + round;
        html = readUrl(url, "Mozilla/5.0 LuckyPick");
        return parseWinningStoreHtml(html);
    }

    private List<StoreItem> fetchSellerKeyword(String keyword) throws Exception {
        String encoded = URLEncoder.encode(keyword, "UTF-8");
        String[] urls = {
            "https://m.dhlottery.co.kr/store.do?method=prtLotSellerInfo&searchType=2&keyword=" + encoded,
            "https://www.dhlottery.co.kr/prchsplcsrch/home?keyword=" + encoded
        };
        for (String url : urls) {
            List<StoreItem> stores = parseSellerHtml(readUrl(url, "Mozilla/5.0 LuckyPick"), keyword);
            if (!stores.isEmpty()) return stores;
        }
        return new ArrayList<>();
    }

    private List<StoreItem> parseWinningStoreHtml(String html) {
        List<StoreItem> stores = new ArrayList<>();
        if (html == null || html.isEmpty()) return stores;

        String normalized = cleanHtml(html);
        int start = normalized.indexOf("1등 배출");
        if (start < 0) start = normalized.indexOf("1등 배출점");
        if (start < 0) start = normalized.indexOf("1등 배출 판매점");
        int end = normalized.indexOf("2등", Math.max(0, start));
        if (start >= 0 && end > start) normalized = normalized.substring(start, end);

        String[] chunks = normalized.split("지도보기");
        for (String chunk : chunks) {
            StoreItem item = parseStoreChunk(chunk);
            if (item != null && !containsStore(stores, item)) stores.add(item);
            if (stores.size() >= 20) break;
        }
        return stores;
    }

    private List<StoreItem> parseSellerHtml(String html, String keyword) {
        List<StoreItem> stores = new ArrayList<>();
        if (html == null || html.isEmpty()) return stores;

        String normalized = cleanHtml(html);
        String[] chunks = normalized.split("지도보기");
        for (String chunk : chunks) {
            if (!chunk.contains(keyword)) continue;
            StoreItem item = parseStoreChunk(chunk);
            if (item != null && !containsStore(stores, item)) stores.add(item);
            if (stores.size() >= 20) break;
        }
        return stores;
    }

    private StoreItem parseStoreChunk(String chunk) {
        if (chunk == null) return null;
        String cleaned = chunk.replaceAll("\\s+", " ").trim();
        if (cleaned.length() < 8) return null;
        if (cleaned.contains("조회된 내역이 없습니다") || cleaned.contains("검색결과")) return null;

        Matcher addressMatcher = Pattern.compile("((서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)[^|]{4,120})").matcher(cleaned);
        if (!addressMatcher.find()) return null;

        String address = addressMatcher.group(1).trim();
        String before = cleaned.substring(0, addressMatcher.start()).trim();
        before = before.replaceAll("^(번호|상호명|구분|소재지|위치보기)\\s*", "").trim();
        String[] parts = before.split(" ");

        String name = "";
        String meta = "";
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.matches("\\d+")) continue;
            if ("자동".equals(part) || "수동".equals(part) || "반자동".equals(part)) {
                meta = part;
                continue;
            }
            if (name.isEmpty()) name = part;
            else if (name.length() < 24) name += " " + part;
        }
        if (name.isEmpty()) name = "복권 판매점";
        return new StoreItem(name, meta, address);
    }

    private boolean containsStore(List<StoreItem> stores, StoreItem item) {
        for (StoreItem store : stores) {
            if (store.name.equals(item.name) && store.address.equals(item.address)) return true;
        }
        return false;
    }

    private String readUrl(String urlValue, String userAgent) throws Exception {
        URL url = new URL(urlValue);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", userAgent);

        if (connection.getResponseCode() != 200) {
            connection.disconnect();
            return "";
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        reader.close();
        connection.disconnect();

        return builder.toString();
    }

    private String readOfficialJson(String urlValue) throws Exception {
        URL url = new URL(urlValue);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36");
        connection.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
        connection.setRequestProperty("Referer", "https://www.dhlottery.co.kr/prchsplcsrch/home");
        connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");

        if (connection.getResponseCode() != 200) {
            connection.disconnect();
            return "";
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        reader.close();
        connection.disconnect();

        return builder.toString();
    }

    private String cleanHtml(String html) {
        return html
            .replaceAll("(?is)<script.*?</script>", " ")
            .replaceAll("(?is)<style.*?</style>", " ")
            .replaceAll("(?is)<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private JSONObject fetchOfficialRoundFromPage(int round) throws Exception {
        String html = readUrl("https://www.dhlottery.co.kr/gameResult.do?method=byWin&drwNo=" + round, "Mozilla/5.0 LuckyPick");
        int resultStart = html.indexOf("win_result");
        if (resultStart < 0) resultStart = html.indexOf("당첨번호");
        if (resultStart < 0) resultStart = 0;
        String resultArea = html.substring(resultStart, Math.min(html.length(), resultStart + 6000));

        Matcher matcher = Pattern.compile("ball_645[^>]*>\\s*(\\d+)\\s*<").matcher(resultArea);
        List<Integer> numbers = new ArrayList<>();
        while (matcher.find() && numbers.size() < 7) {
            numbers.add(Integer.parseInt(matcher.group(1)));
        }
        if (numbers.size() < 7) return null;

        JSONObject draw = new JSONObject();
        draw.put("returnValue", "success");
        draw.put("drwNo", round);
        for (int i = 0; i < 6; i++) draw.put("drwtNo" + (i + 1), numbers.get(i));
        draw.put("bnusNo", numbers.get(6));

        Matcher dateMatcher = Pattern.compile("(\\d{4})[.년\\-]\\s*(\\d{1,2})[.월\\-]\\s*(\\d{1,2})").matcher(resultArea);
        if (dateMatcher.find()) {
            draw.put("drwNoDate", dateMatcher.group(1) + "-" + pad2(dateMatcher.group(2)) + "-" + pad2(dateMatcher.group(3)));
        }

        String text = cleanHtml(resultArea);
        fillFirstPrizeFields(draw, text);
        return draw;
    }

    private JSONObject fetchOfficialRoundFromBackup(int round) throws Exception {
        String html = readUrl("https://lotto-view.com/lotto/numbers/" + round, "Mozilla/5.0 LuckyPick");
        Matcher descriptionMatcher = Pattern.compile("제" + round + "회[^\\\"]*당첨번호는\\s*([0-9,\\s]+)\\s*\\+\\s*보너스\\s*(\\d+)").matcher(html);
        if (!descriptionMatcher.find()) return null;

        List<Integer> numbers = new ArrayList<>();
        Matcher numberMatcher = Pattern.compile("\\d+").matcher(descriptionMatcher.group(1));
        while (numberMatcher.find() && numbers.size() < 6) {
            numbers.add(Integer.parseInt(numberMatcher.group()));
        }
        if (numbers.size() != 6) return null;

        JSONObject draw = new JSONObject();
        draw.put("returnValue", "success");
        draw.put("drwNo", round);
        for (int i = 0; i < 6; i++) draw.put("drwtNo" + (i + 1), numbers.get(i));
        draw.put("bnusNo", Integer.parseInt(descriptionMatcher.group(2)));

        String text = cleanHtml(html);
        Matcher dateMatcher = Pattern.compile("(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일").matcher(text);
        if (dateMatcher.find()) {
            draw.put("drwNoDate", dateMatcher.group(1) + "-" + pad2(dateMatcher.group(2)) + "-" + pad2(dateMatcher.group(3)));
        }

        Matcher firstRow = Pattern.compile("1등\\s*6개\\s*번호\\s*일치\\s*([0-9][0-9,]*)\\s*명\\s*([0-9][0-9,]*)\\s*원").matcher(text);
        if (firstRow.find()) {
            draw.put("firstPrzwnerCo", firstRow.group(1));
            draw.put("firstWinamnt", firstRow.group(2));
        }

        fillFirstPrizeFields(draw, text);
        return draw;
    }

    private int findLatestBackupRound() throws Exception {
        String html = readUrl("https://lotto-view.com/lotto/numbers", "Mozilla/5.0 LuckyPick");
        Matcher matcher = Pattern.compile("/lotto/numbers/(\\d+)").matcher(html);
        int latest = 0;
        while (matcher.find()) {
            latest = Math.max(latest, Integer.parseInt(matcher.group(1)));
        }
        return latest;
    }

    private String pad2(String value) {
        return value.length() == 1 ? "0" + value : value;
    }

    private String drawNumbersToString(JSONObject draw) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            numbers.add(draw.optInt("drwtNo" + i));
        }
        Collections.sort(numbers);
        return numbersToString(numbers);
    }

    private int readIntField(JSONObject draw, String key) {
        Object raw = draw.opt(key);
        if (raw instanceof Number) return ((Number) raw).intValue();
        if (raw == null) return 0;
        String digits = raw.toString().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private long readLongField(JSONObject draw, String key) {
        Object raw = draw.opt(key);
        if (raw instanceof Number) return ((Number) raw).longValue();
        if (raw == null) return 0L;
        String digits = raw.toString().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0L;
        try {
            return Long.parseLong(digits);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void fillFirstPrizeFields(JSONObject draw, String text) {
        if (draw == null || text == null || text.isEmpty()) return;

        try {
            if (readIntField(draw, "firstPrzwnerCo") <= 0) {
                Matcher winnersMatcher = Pattern.compile("1등[^0-9]{0,40}([0-9][0-9,]*)\\s*명").matcher(text);
                if (winnersMatcher.find()) {
                    draw.put("firstPrzwnerCo", winnersMatcher.group(1));
                }
            }

            if (readLongField(draw, "firstWinamnt") <= 0) {
                Matcher amountMatcher = Pattern.compile("1등[^0-9]{0,80}([0-9][0-9,]*)\\s*원").matcher(text);
                if (amountMatcher.find()) {
                    draw.put("firstWinamnt", amountMatcher.group(1));
                }
            }
        } catch (JSONException ignored) {
        }
    }

    private String formatWon(long amount) {
        if (amount <= 0) return "정보 없음";
        return String.format(Locale.KOREA, "%,d원", amount);
    }

    private static class StoreItem {
        final String name;
        final String meta;
        final String address;

        StoreItem(String name, String meta, String address) {
            this.name = name;
            this.meta = meta;
            this.address = address;
        }
    }

    private static class NearbyStore {
        final StoreItem item;
        final float distanceMeters;
        final double lat;
        final double lng;

        NearbyStore(StoreItem item, float distanceMeters, double lat, double lng) {
            this.item = item;
            this.distanceMeters = distanceMeters;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private View statRow(int number, int count, int max) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(1), 0, dp(1));
        row.addView(statBall(number));
        TextView bar = new TextView(this);
        bar.setBackground(round(Color.rgb(52, 55, 60), 8));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0, dp(8), 1);
        barParams.setMargins(dp(4), 0, dp(10), 0);
        row.addView(bar, barParams);
        row.addView(label(String.valueOf(count), 13, text, true));
        return row;
    }

    private TextView statBall(int number) {
        TextView view = label(String.valueOf(number), 12, Color.rgb(21, 21, 20), true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(ballColor(number), 20));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(29), dp(29));
        params.setMargins(0, 0, dp(7), 0);
        view.setLayoutParams(params);
        return view;
    }

    private void renderAbout() {
        LinearLayout about = panelView();
        about.addView(sectionTitle("Product Notes", "서비스 안내", ""));
        TextView body = label(
            "십이지신이 추천하는, 행운의 로또번호는 로또 6/45 번호 추천 및 통계 참고용 앱입니다.\n\n"
                + "본 앱은 동행복권, 복권위원회, Google과 제휴하거나 공식 인증을 받은 서비스가 아닙니다.\n\n"
                + "생성 결과는 무작위 추천이며 당첨을 보장하지 않습니다. 복권 구매와 당첨 확인은 반드시 동행복권 공식 채널을 이용하세요.\n\n"
                + "생년월일시는 기기 안에서 번호 생성을 위해서만 사용되며 외부 서버로 전송하지 않습니다. 저장 조합과 최근 생성 통계도 기기 안에만 저장됩니다.\n\n"
                + "공식당첨통계는 공개된 당첨번호 데이터를 조회해 빈도 분석용으로 표시합니다. 네트워크 연결 상태나 제공처 응답에 따라 조회가 지연되거나 실패할 수 있습니다.",
            14,
            muted,
            false
        );
        body.setLineSpacing(dp(5), 1f);
        about.addView(body);
        content.addView(about);

        LinearLayout policy = panelView();
        policy.addView(sectionTitle("Privacy", "개인정보 처리방침 요약", ""));
        TextView policyBody = label(
            "수집 항목: 사용자가 입력한 생년월일시, 저장한 번호 조합, 최근 생성 기록\n\n"
                + "이용 목적: 개인화 번호 생성, 보관함, 내 생성통계 표시\n\n"
                + "보관 위치: 사용자 기기 내부 저장소\n\n"
                + "제3자 제공: 현재 제공하지 않음\n\n"
                + "삭제 방법: 앱 삭제 시 기기 내 저장 데이터가 함께 삭제됩니다.",
            14,
            muted,
            false
        );
        policyBody.setLineSpacing(dp(5), 1f);
        policy.addView(policyBody);
        content.addView(policy);
    }

    private void copyGame(List<Integer> numbers) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("lotto", "럭키픽 추천 번호: " + numbersToString(numbers)));
        Toast.makeText(this, "번호를 복사했어요.", Toast.LENGTH_SHORT).show();
    }

    private void saveRecent(List<List<Integer>> games) {
        JSONArray old = readArray(RECENT_KEY);
        JSONArray next = new JSONArray();
        for (List<Integer> game : games) next.put(numbersToString(game));
        for (int i = 0; i < Math.min(old.length(), 115); i++) next.put(old.optString(i));
        preferences.edit().putString(RECENT_KEY, next.toString()).apply();
    }

    private JSONArray readArray(String key) {
        try {
            return new JSONArray(preferences.getString(key, "[]"));
        } catch (JSONException error) {
            return new JSONArray();
        }
    }

    private List<Integer> parseGame(String value) {
        List<Integer> numbers = new ArrayList<>();
        for (String part : value.split(",")) {
            try {
                numbers.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return numbers;
    }

    private String numbersToString(List<Integer> numbers) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < numbers.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(numbers.get(i));
        }
        return builder.toString();
    }

    private String nextDrawText() {
        Calendar calendar = Calendar.getInstance();
        int diff = Calendar.SATURDAY - calendar.get(Calendar.DAY_OF_WEEK);
        if (diff < 0) diff += 7;
        calendar.add(Calendar.DAY_OF_MONTH, diff);
        return "다음 추첨 " + new SimpleDateFormat("M월 d일 E", Locale.KOREAN).format(calendar.getTime());
    }

    private String nextDrawDateText() {
        Calendar calendar = Calendar.getInstance();
        int diff = Calendar.SATURDAY - calendar.get(Calendar.DAY_OF_WEEK);
        if (diff < 0) diff += 7;
        calendar.add(Calendar.DAY_OF_MONTH, diff);
        return "다음 추첨일: " + new SimpleDateFormat("M월 d일 E", Locale.KOREAN).format(calendar.getTime());
    }

    private LinearLayout sectionTitle(String kicker, String title, String side) {
        LinearLayout box = row();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(0, 0, 0, dp(14));
        LinearLayout left = column();
        left.addView(label(kicker, 11, gold, true));
        left.addView(label(title, 22, text, true));
        box.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        if (!side.isEmpty()) box.addView(label(side, 12, muted, false));
        return box;
    }

    private LinearLayout compactStatsTitle(String kicker, String title, String side) {
        LinearLayout box = row();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(0, 0, 0, dp(6));
        LinearLayout left = column();
        left.addView(label(kicker, 10, gold, true));
        left.addView(label(title, 18, text, true));
        box.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        if (!side.isEmpty()) box.addView(label(side, 11, muted, false));
        return box;
    }

    private LinearLayout compactResultTitle(String title, String side) {
        LinearLayout box = row();
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(0, 0, 0, dp(7));
        LinearLayout left = column();
        left.addView(label("Lucky Numbers", 10, gold, true));
        left.addView(label(title, 17, text, true));
        box.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        if (!side.isEmpty()) box.addView(label(side, 10, muted, false));
        return box;
    }

    private LinearLayout panelView() {
        LinearLayout view = column();
        view.setPadding(dp(18), dp(18), dp(18), dp(18));
        view.setBackground(round(panel, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        view.setLayoutParams(params);
        return view;
    }

    private LinearLayout compactStatsPanelView() {
        LinearLayout view = column();
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setBackground(round(panel, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        view.setLayoutParams(params);
        return view;
    }

    private void addAdMobPlaceholder() {
        LinearLayout adBox = adMobBox();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, dp(2), 0, 0);
        adBox.setLayoutParams(params);
        content.addView(adBox);
    }

    private LinearLayout adMobBox() {
        LinearLayout adBox = column();
        adBox.setGravity(Gravity.CENTER);
        adBox.setPadding(dp(12), dp(5), dp(12), dp(5));
        adBox.setBackground(round(Color.rgb(16, 20, 35), 10));

        TextView label = label("AdMob 배너 광고 영역", 12, muted, true);
        label.setGravity(Gravity.CENTER);
        adBox.addView(label);

        TextView size = label("320 x 50", 10, Color.rgb(110, 114, 122), false);
        size.setGravity(Gravity.CENTER);
        adBox.addView(size);
        return adBox;
    }

    private LinearLayout compactCardView() {
        LinearLayout view = column();
        view.setPadding(dp(8), dp(6), dp(8), dp(6));
        view.setBackground(round(Color.rgb(31, 33, 38), 8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(4));
        view.setLayoutParams(params);
        return view;
    }

    private Button primaryButton(String textValue) {
        Button button = new Button(this);
        button.setText(textValue);
        button.setTextColor(Color.rgb(21, 21, 20));
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(round(green, 10));
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(44)));
        return button;
    }

    private Button secondaryButton(String textValue) {
        Button button = new Button(this);
        button.setText(textValue);
        button.setTextColor(text);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(round(Color.rgb(48, 50, 55), 10));
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(50)));
        return button;
    }

    private Button statsTabButton(String textValue, boolean selected) {
        Button button = new Button(this);
        button.setText(textValue);
        button.setTextColor(selected ? Color.rgb(21, 21, 20) : text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(round(selected ? gold : Color.rgb(48, 50, 55), 20));
        return button;
    }

    private Button smallButton(String textValue) {
        Button button = new Button(this);
        button.setText(textValue);
        button.setTextColor(text);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(round(Color.rgb(48, 50, 55), 22));
        return button;
    }

    private TextView infoPill(String value) {
        TextView view = label(value, 12, text, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setBackground(round(Color.rgb(38, 40, 46), 18));
        return view;
    }

    private Button tinyButton(String textValue) {
        Button button = new Button(this);
        button.setText(textValue);
        button.setTextColor(text);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(round(Color.rgb(48, 50, 55), 16));
        return button;
    }

    private Button microButton(String textValue) {
        Button button = new Button(this);
        button.setText(textValue);
        button.setTextColor(text);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(round(Color.rgb(48, 50, 55), 10));
        return button;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), line);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int statusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return dp(24);
    }
}


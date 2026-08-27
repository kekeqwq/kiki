/*
 * Kiki — a tiny e-ink Android launcher
 * Copyright (C) 2026 kekeqwq
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.kekeqwq.kiki;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HomeActivity extends Activity {
    static final int HOME = 0, APPS = 1, SET = 2;
    static final String VER = "1.4";
    static final String REL = "2026.8.27";
    static final String[] WK = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
    static final int PAPER_L = 0xFFF6F1E8, INK_L = 0xFF1C1B19;
    static final int PAPER_D = 0xFF1C1B19, INK_D = 0xFFF6F1E8;

    final Handler clock = new Handler(Looper.getMainLooper());
    final Calendar cal = Calendar.getInstance();
    final ArrayList<App> catalog = new ArrayList<App>();
    final StringBuilder buf = new StringBuilder(16);
    final View.OnLongClickListener toSet =
            new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    show(SET);
                    return true;
                }
            };

    int paper, ink, screen, hair, bar, pad, rowH;
    float density;
    Typeface textFace;
    Bitmap wallpaper;
    ImageView wpView;
    View home, apps, settings, stack, corner;
    StackNum hour, minute;
    TextView date, cornerTime, cornerDate, appCount, markNone, markPick, wpPathLine, changeLabel;
    ListView list;
    AppsAdapter adapter;
    ColorStateList pressText;
    View changeRow;
    final LinkedHashMap<String, String> conf = new LinkedHashMap<String, String>();
    boolean wpOn;
    String wpPath, wpUri;
    int wpTries;
    boolean askedRead;
    final Runnable wpRetry =
            new Runnable() {
                public void run() {
                    tryWp();
                }
            };
    final Runnable tick =
            new Runnable() {
                public void run() {
                    paintClock();
                    eink();
                    schedule();
                }
            };
    final BroadcastReceiver pkgRx =
            new BroadcastReceiver() {
                public void onReceive(Context c, Intent i) {
                    catalog.clear();
                    if (screen == APPS) loadApps();
                }
            };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.setWindowAnimations(0);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;
        int width = dm.widthPixels;
        hair = dp(40);
        bar = dp(3);
        pad = dp(20);
        rowH = dp(50);
        loadFaces();
        palette();
        setContentView(build(width));
        restoreWp();
        applyHome();
        show(HOME);
        hideUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideUi();
        syncConf();
        paintClock();
        schedule();
        clearPress();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        try {
            getClass()
                    .getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class, int.class)
                    .invoke(this, pkgRx, f, Integer.valueOf(2));
        } catch (Exception e) {
            registerReceiver(pkgRx, f);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        clock.removeCallbacks(tick);
        clearPress();
        try {
            unregisterReceiver(pkgRx);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) {
            hideUi();
            clearPress();
            tryWp();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        palette();
        applyColors();
        applyHome();
        if (screen == APPS) {
            catalog.clear();
            loadApps();
        }
        paintClock();
        eink();
    }

    @Override
    public void onBackPressed() {
        if (screen != HOME) show(HOME);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_HOME && screen != HOME) {
            show(HOME);
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_COMPLETE && wallpaper != null && screen != HOME) {
            recycleWp();
            wpView.setImageBitmap(null);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req != 1 || res != RESULT_OK || data == null) return;
        Uri u = data.getData();
        if (u == null) return;
        try {
            getContentResolver()
                    .takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        String path = pathOf(u);
        putConf("wallpaper", "on");
        putConf("wallpaper-file", path != null ? path : u.toString());
        putConf("wallpaper-uri", u.toString());
        saveConf();
        applyConf();
        applyHome();
        show(HOME);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grant) {
        if (req == 2) openPicker();
    }

    void loadFaces() {
        textFace = fromFile("/system/fonts/NotoSansCJK-Regular.ttc", 2);
        if (textFace == null) textFace = Typeface.create("sans-serif", Typeface.NORMAL);
        if (textFace == null) textFace = Typeface.SANS_SERIF;
    }

    static Typeface fromFile(String path, int ttc) {
        File f = new File(path);
        if (!f.exists()) return null;
        try {
            if (ttc >= 0 && Build.VERSION.SDK_INT >= 26) {
                Typeface.Builder b = new Typeface.Builder(path);
                b.setTtcIndex(ttc);
                Typeface t = b.build();
                if (t != null) return t;
            }
            return Typeface.createFromFile(f);
        } catch (Throwable ignored) {
            return null;
        }
    }

    void palette() {
        boolean night =
                (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        paper = night ? PAPER_D : PAPER_L;
        ink = night ? INK_D : INK_L;
        pressText =
                new ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_pressed}, new int[] {}},
                        new int[] {paper, ink});
        getWindow().setStatusBarColor(paper);
        getWindow().setNavigationBarColor(paper);
    }

    StateListDrawable pressBg() {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[] {android.R.attr.state_pressed}, new ColorDrawable(ink));
        d.addState(new int[] {}, new ColorDrawable(Color.TRANSPARENT));
        return d;
    }

    void hideUi() {
        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    View build(int width) {
        int stackPx = width * 28 / 100;
        int cornerPx = width * 11 / 100;
        int metaPx = Math.max(dp(13), width * 4 / 100);
        int listPx = Math.max(dp(16), width * 5 / 100);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(paper);
        root.setLayoutParams(new FrameLayout.LayoutParams(vp(), vp()));

        wpView = new ImageView(this);
        wpView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wpView.setVisibility(View.GONE);
        root.addView(wpView, new FrameLayout.LayoutParams(vp(), vp()));

        home = homePane(stackPx, cornerPx, metaPx);
        root.addView(home, new FrameLayout.LayoutParams(vp(), vp()));

        apps = appsPane(listPx);
        apps.setVisibility(View.GONE);
        root.addView(apps, new FrameLayout.LayoutParams(vp(), vp()));

        settings = settingsPane(listPx);
        settings.setVisibility(View.GONE);
        root.addView(settings, new FrameLayout.LayoutParams(vp(), vp()));
        return root;
    }

    View homePane(int stackPx, int cornerPx, int metaPx) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setOnLongClickListener(toSet);
        box.setClipChildren(false);

        FrameLayout area = new FrameLayout(this);
        area.setOnLongClickListener(toSet);
        area.setClipChildren(false);
        box.addView(area, new LinearLayout.LayoutParams(vp(), 0, 1));

        stack = new LinearLayout(this);
        ((LinearLayout) stack).setOrientation(LinearLayout.VERTICAL);
        ((LinearLayout) stack).setGravity(Gravity.CENTER_HORIZONTAL);
        ((ViewGroup) stack).setClipChildren(false);
        ((ViewGroup) stack).setClipToPadding(false);
        hour = new StackNum(stackPx);
        minute = new StackNum(stackPx);
        date = metaText(metaPx, 0f);
        date.setAllCaps(false);
        date.setGravity(Gravity.CENTER_HORIZONTAL);
        date.setPadding(0, dp(24), 0, 0);
        ((LinearLayout) stack).addView(hour);
        ((LinearLayout) stack).addView(minute);
        ((LinearLayout) stack)
                .addView(
                        date,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        area.addView(
                stack,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER));

        corner = new LinearLayout(this);
        ((LinearLayout) corner).setOrientation(LinearLayout.VERTICAL);
        ((LinearLayout) corner).setGravity(Gravity.END);
        corner.setPadding(0, dp(28), pad, 0);
        corner.setVisibility(View.GONE);
        cornerTime = clockText(cornerPx);
        cornerTime.setGravity(Gravity.END);
        cornerDate = metaText(metaPx, 0f);
        cornerDate.setAllCaps(false);
        cornerDate.setGravity(Gravity.END);
        cornerDate.setPadding(0, dp(8), 0, 0);
        ((LinearLayout) corner).addView(cornerTime);
        ((LinearLayout) corner).addView(cornerDate);
        area.addView(
                corner,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.END));

        box.addView(hairline(APPS, dp(8), dp(36)));
        return box;
    }

    View appsPane(int listPx) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(paper);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, dp(32), pad, dp(18));

        TextView appsLbl = body(listPx);
        appsLbl.setText("APPS");
        appsLbl.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams side = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        head.addView(appsLbl, side);

        View mid = new View(this);
        mid.setBackgroundColor(ink);
        mid.setTag("h");
        FrameLayout hit = new FrameLayout(this);
        hit.setPadding(dp(12), dp(14), dp(12), dp(14));
        hit.setClickable(true);
        hit.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        show(HOME);
                    }
                });
        hit.addView(mid, new FrameLayout.LayoutParams(hair, bar, Gravity.CENTER));
        head.addView(hit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        appCount = body(listPx);
        appCount.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        head.addView(appCount, side);
        box.addView(head, new LinearLayout.LayoutParams(vp(), ViewGroup.LayoutParams.WRAP_CONTENT));

        adapter = new AppsAdapter(listPx);
        list = new ListView(this);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setChoiceMode(ListView.CHOICE_MODE_NONE);
        list.setItemsCanFocus(false);
        list.setVerticalScrollBarEnabled(false);
        list.setHorizontalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setScrollingCacheEnabled(false);
        list.setAnimationCacheEnabled(false);
        list.setFadingEdgeLength(0);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setSoundEffectsEnabled(false);
        list.setHapticFeedbackEnabled(false);
        list.setOnItemClickListener(
                new android.widget.AdapterView.OnItemClickListener() {
                    public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                        launch(catalog.get(pos));
                    }
                });
        list.setOnItemLongClickListener(
                new android.widget.AdapterView.OnItemLongClickListener() {
                    public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                        show(SET);
                        return true;
                    }
                });
        box.addView(list, new LinearLayout.LayoutParams(vp(), 0, 1));
        return box;
    }

    View settingsPane(int listPx) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(paper);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, dp(32), pad, 0);
        TextView title = body(listPx);
        title.setText("设置");
        title.setTypeface(textFace, Typeface.BOLD);
        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        head.addView(title, grow);
        TextView close = body(listPx);
        close.setText("关闭");
        close.setGravity(Gravity.END);
        close.setPadding(dp(12), dp(8), 0, dp(8));
        close.setClickable(true);
        close.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        show(HOME);
                    }
                });
        head.addView(close);
        box.addView(head);

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);
        sc.setHorizontalScrollBarEnabled(false);
        sc.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView sub = body(listPx);
        sub.setText("壁纸");
        sub.setTypeface(textFace, Typeface.BOLD);
        sub.setPadding(pad, dp(40), pad, dp(6));
        col.addView(sub);

        col.addView(settingRow("无壁纸", listPx, 0));
        col.addView(settingRow("有壁纸", listPx, 1));
        col.addView(settingRow("更改壁纸", listPx, 2));
        wpPathLine = body(listPx);
        wpPathLine.setPadding(pad, 0, pad, dp(10));
        wpPathLine.setSingleLine(true);
        wpPathLine.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        col.addView(wpPathLine);

        TextView about = body(listPx);
        about.setText("关于");
        about.setTypeface(textFace, Typeface.BOLD);
        about.setPadding(pad, dp(40), pad, dp(6));
        col.addView(about);
        col.addView(infoLine("Kiki " + VER, listPx));
        col.addView(infoLine("极简墨水屏启动器", listPx));
        col.addView(infoLine("主屏报时，短线打开应用", listPx));
        col.addView(infoLine("Copyright 2026 kekeqwq", listPx));
        col.addView(infoLine("GPL-3.0-or-later", listPx));
        col.addView(infoLine(REL, listPx));
        sc.addView(col, new FrameLayout.LayoutParams(vp(), ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(sc, new LinearLayout.LayoutParams(vp(), 0, 1));
        return box;
    }

    View infoLine(String s, int px) {
        TextView t = body(px);
        t.setText(s);
        t.setPadding(pad, dp(6), pad, dp(6));
        return t;
    }

    View settingRow(final String label, int listPx, final int kind) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(pad, dp(14), pad, dp(14));
        row.setMinimumHeight(rowH);
        row.setClickable(true);
        row.setBackground(pressBg());
        TextView t = body(listPx);
        t.setText(label);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        row.addView(t, lp);
        TextView mark = body(listPx);
        mark.setGravity(Gravity.END);
        if (kind == 0) markNone = mark;
        else if (kind == 1) markPick = mark;
        else {
            changeRow = row;
            changeLabel = t;
        }
        row.addView(mark);
        row.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        if (kind == 0) {
                            putConf("wallpaper", "off");
                            saveConf();
                            applyConf();
                            eink();
                        } else if (kind == 1) {
                            if (wpPath == null || wpPath.length() == 0) {
                                pickWallpaper();
                            } else {
                                putConf("wallpaper", "on");
                                saveConf();
                                applyConf();
                                eink();
                            }
                        } else if (wpOn) {
                            pickWallpaper();
                        }
                    }
                });
        return row;
    }

    TextView clockText(int px) {
        TextView t = new TextView(this);
        t.setTypeface(textFace);
        t.setTextColor(ink);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
        t.setIncludeFontPadding(false);
        t.setLetterSpacing(0f);
        t.setFontFeatureSettings("tnum");
        t.setGravity(Gravity.CENTER);
        t.setElegantTextHeight(false);
        return t;
    }

    TextView metaText(int px, float track) {
        TextView t = new TextView(this);
        t.setTypeface(textFace);
        t.setTextColor(ink);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
        t.setIncludeFontPadding(false);
        t.setLetterSpacing(track);
        return t;
    }

    TextView body(int px) {
        TextView t = new TextView(this);
        t.setTypeface(textFace);
        t.setTextColor(ink);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px);
        t.setIncludeFontPadding(false);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setSoundEffectsEnabled(false);
        return t;
    }

    View hairline(final int dest, int top, int bottom) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(0, top, 0, bottom);
        wrap.setClickable(true);
        wrap.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        show(dest);
                    }
                });
        wrap.setOnLongClickListener(toSet);
        View v = new View(this);
        v.setBackgroundColor(ink);
        v.setTag("h");
        wrap.addView(v, new LinearLayout.LayoutParams(hair, bar));
        return wrap;
    }

    void show(int s) {
        if (s != APPS) clearPress();
        screen = s;
        home.setVisibility(s == HOME ? View.VISIBLE : View.GONE);
        apps.setVisibility(s == APPS ? View.VISIBLE : View.GONE);
        settings.setVisibility(s == SET ? View.VISIBLE : View.GONE);
        if (s == APPS) {
            if (catalog.isEmpty()) loadApps();
            clearPress();
        }
        if (s == SET) paintMarks();
        eink();
    }

    void applyHome() {
        if (wallpaper == null && wpOn) {
            try {
                loadWallpaper();
            } catch (Exception ignored) {
            }
        }
        boolean has = wallpaper != null;
        wpView.setVisibility(has ? View.VISIBLE : View.GONE);
        stack.setVisibility(has ? View.GONE : View.VISIBLE);
        corner.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) wpView.setImageBitmap(null);
    }

    void applyColors() {
        View root = findViewById(android.R.id.content);
        if (root != null) root.setBackgroundColor(paper);
        home.setBackgroundColor(hasWp() ? Color.TRANSPARENT : paper);
        apps.setBackgroundColor(paper);
        settings.setBackgroundColor(paper);
        hour.recolor();
        minute.recolor();
        date.setTextColor(ink);
        cornerTime.setTextColor(ink);
        cornerDate.setTextColor(ink);
        appCount.setTextColor(ink);
        tintTree(home);
        tintTree(apps);
        tintTree(settings);
        if (adapter != null) adapter.notifyDataSetChanged();
        paintMarks();
    }

    boolean hasWp() {
        return wallpaper != null;
    }

    void tintTree(View v) {
        if (v instanceof TextView) ((TextView) v).setTextColor(ink);
        if ("h".equals(v.getTag())) v.setBackgroundColor(ink);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) tintTree(g.getChildAt(i));
        }
    }

    void paintClock() {
        cal.setTimeInMillis(System.currentTimeMillis());
        int h = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);
        buf.setLength(0);
        if (h < 10) buf.append('0');
        buf.append(h);
        String hh = buf.toString();
        buf.setLength(0);
        if (m < 10) buf.append('0');
        buf.append(m);
        String mm = buf.toString();
        hour.setDigits(hh);
        minute.setDigits(mm);
        buf.setLength(0);
        buf.append(hh).append(':').append(mm);
        cornerTime.setText(buf);
        buf.setLength(0);
        buf.append(cal.get(Calendar.MONTH) + 1)
                .append('.')
                .append(cal.get(Calendar.DAY_OF_MONTH))
                .append(' ')
                .append(WK[cal.get(Calendar.DAY_OF_WEEK) - 1]);
        CharSequence d = buf.toString();
        date.setText(d);
        cornerDate.setText(d);
    }

    void schedule() {
        clock.removeCallbacks(tick);
        long now = System.currentTimeMillis();
        clock.postDelayed(tick, 60000L - (now % 60000L) + 8L);
    }

    void loadApps() {
        catalog.clear();
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
        ComponentName self = getComponentName();
        int n = found.size();
        for (int i = 0; i < n; i++) {
            ResolveInfo ri = found.get(i);
            ComponentName cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
            if (self.equals(cn)) continue;
            CharSequence label = ri.loadLabel(pm);
            catalog.add(new App(label == null ? ri.activityInfo.packageName : label.toString(), cn));
        }
        final Collator col = Collator.getInstance();
        Collections.sort(
                catalog,
                new Comparator<App>() {
                    public int compare(App a, App b) {
                        return col.compare(a.name, b.name);
                    }
                });
        appCount.setText(Integer.toString(catalog.size()));
        adapter.notifyDataSetChanged();
    }

    void launch(App app) {
        clearPress();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(app.cn);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            show(HOME);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    void clearPress() {
        if (list == null) return;
        list.setPressed(false);
        list.clearChoices();
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        int n = list.getChildCount();
        for (int i = 0; i < n; i++) {
            View v = list.getChildAt(i);
            v.setPressed(false);
            v.setSelected(false);
            v.setActivated(false);
            v.refreshDrawableState();
            if (v instanceof TextView) {
                ((TextView) v).setTextColor(ink);
                v.setBackground(pressBg());
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    void pickWallpaper() {
        if (Build.VERSION.SDK_INT >= 23 && !hasRead() && !askedRead) {
            askedRead = true;
            String p =
                    Build.VERSION.SDK_INT >= 33
                            ? "android.permission.READ_MEDIA_IMAGES"
                            : android.Manifest.permission.READ_EXTERNAL_STORAGE;
            requestPermissions(new String[] {p}, 2);
            return;
        }
        openPicker();
    }

    void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, 1);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    boolean hasRead() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission("android.permission.READ_MEDIA_IMAGES")
                    == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    File confFile() {
        return new File(getFilesDir(), "kiki.conf");
    }

    void putConf(String k, String v) {
        if (v == null || v.length() == 0) conf.remove(k);
        else conf.put(k, v);
    }

    void loadConf() {
        conf.clear();
        File f = confFile();
        if (!f.exists()) return;
        try {
            BufferedReader r =
                    new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#') continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                if (k.length() > 0) conf.put(k, v);
            }
            r.close();
        } catch (Exception ignored) {
        }
        readWpKeys();
    }

    void readWpKeys() {
        String w = conf.get("wallpaper");
        wpOn = w != null && ("on".equalsIgnoreCase(w) || "true".equalsIgnoreCase(w) || "1".equals(w));
        String p = conf.get("wallpaper-file");
        wpPath = (p == null || p.length() == 0) ? null : p;
        String u = conf.get("wallpaper-uri");
        wpUri = (u == null || u.length() == 0) ? null : u;
        if (wpPath != null) wpPath = normalize(wpPath);
    }

    void saveConf() {
        try {
            StringBuilder s = new StringBuilder();
            writeKey(s, "wallpaper");
            writeKey(s, "wallpaper-file");
            writeKey(s, "wallpaper-uri");
            for (Map.Entry<String, String> e : conf.entrySet()) {
                String k = e.getKey();
                if ("wallpaper".equals(k) || "wallpaper-file".equals(k) || "wallpaper-uri".equals(k))
                    continue;
                s.append(k).append(" = ").append(e.getValue()).append('\n');
            }
            FileOutputStream out = new FileOutputStream(confFile());
            out.write(s.toString().getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
        }
    }

    void writeKey(StringBuilder s, String k) {
        String v = conf.get(k);
        if (v != null && v.length() > 0) s.append(k).append(" = ").append(v).append('\n');
    }

    void restoreWp() {
        File old = new File(getFilesDir(), "wp");
        if (old.exists()) old.delete();
        File tmp = new File(getFilesDir(), "wp.tmp");
        if (tmp.exists()) tmp.delete();
        loadConf();
        applyConf();
    }

    void syncConf() {
        String wasOn = wpOn ? "on" : "off";
        String wasPath = wpPath;
        loadConf();
        boolean same = (wpOn ? "on" : "off").equals(wasOn) && eq(wpPath, wasPath);
        if (same) {
            if (wpOn && wallpaper == null) applyConf();
            return;
        }
        applyConf();
    }

    void applyConf() {
        readWpKeys();
        recycleWp();
        wpTries = 0;
        clock.removeCallbacks(wpRetry);
        if (wpOn) tryWp();
        applyHome();
        paintMarks();
    }

    void tryWp() {
        if (!wpOn || wallpaper != null) return;
        try {
            loadWallpaper();
            applyHome();
            paintMarks();
            wpTries = 0;
        } catch (Exception e) {
            if (wpTries < 8) {
                wpTries++;
                clock.postDelayed(wpRetry, 2000);
            }
        }
    }

    static boolean eq(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    String pathOf(Uri u) {
        if (u == null) return null;
        if ("file".equals(u.getScheme())) {
            String p = u.getPath();
            if (usable(p)) return p;
        }
        String fd = pathFromFd(u);
        if (usable(fd)) return fd;
        Cursor c = null;
        try {
            c = getContentResolver().query(u, new String[] {"_data"}, null, null, null);
            if (c != null && c.moveToFirst()) {
                String p = c.getString(0);
                if (usable(p)) return p;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    String pathFromFd(Uri u) {
        ParcelFileDescriptor pfd = null;
        try {
            pfd = getContentResolver().openFileDescriptor(u, "r");
            if (pfd == null) return null;
            String p = Os.readlink("/proc/self/fd/" + pfd.getFd());
            return normalize(p);
        } catch (Exception e) {
            return null;
        } finally {
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    static boolean usable(String p) {
        if (p == null || p.length() < 2 || p.charAt(0) != '/') return false;
        if (p.startsWith("/proc") || p.startsWith("/dev") || p.startsWith("/data/data/")) return false;
        File f = new File(p);
        return f.isFile() && f.canRead();
    }

    static String normalize(String p) {
        if (p == null || p.length() == 0) return p;
        int i = p.indexOf("/emulated/");
        if (i >= 0 && (p.startsWith("/mnt/") || p.startsWith("/data/media/"))) {
            p = "/storage" + p.substring(i);
        }
        return p;
    }

    void loadWallpaper() throws Exception {
        if (!wpOn) throw new IllegalStateException();
        recycleWp();
        Point p = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(p);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = openWp();
        try {
            BitmapFactory.decodeStream(in, null, o);
        } finally {
            in.close();
        }
        if (o.outWidth <= 0) throw new IllegalStateException();
        int sample = 1;
        while (o.outWidth / sample > p.x || o.outHeight / sample > p.y) sample <<= 1;
        if (sample < 1) sample = 1;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        o.inDither = true;
        o.inScaled = false;
        in = openWp();
        try {
            wallpaper = BitmapFactory.decodeStream(in, null, o);
        } finally {
            in.close();
        }
        if (wallpaper == null) throw new IllegalStateException();
        wpView.setImageBitmap(wallpaper);
    }

    InputStream openWp() throws Exception {
        Exception last = null;
        if (wpPath != null && wpPath.charAt(0) == '/') {
            try {
                return new FileInputStream(wpPath);
            } catch (Exception e) {
                last = e;
            }
            if (wpPath.startsWith("/storage/emulated/0")) {
                try {
                    return new FileInputStream("/sdcard" + wpPath.substring(19));
                } catch (Exception e) {
                    last = e;
                }
            } else if (wpPath.startsWith("/sdcard")) {
                try {
                    return new FileInputStream("/storage/emulated/0" + wpPath.substring(7));
                } catch (Exception e) {
                    last = e;
                }
            }
        }
        String[] us = {wpUri, wpPath != null && wpPath.startsWith("content:") ? wpPath : null};
        for (int i = 0; i < us.length; i++) {
            if (us[i] == null) continue;
            try {
                InputStream in = getContentResolver().openInputStream(Uri.parse(us[i]));
                if (in != null) return in;
            } catch (Exception e) {
                last = e;
            }
        }
        try {
            List<UriPermission> ps = getContentResolver().getPersistedUriPermissions();
            for (int i = 0; i < ps.size(); i++) {
                UriPermission up = ps.get(i);
                if (!up.isReadPermission()) continue;
                InputStream in = getContentResolver().openInputStream(up.getUri());
                if (in != null) return in;
            }
        } catch (Exception e) {
            last = e;
        }
        if (last != null) throw last;
        throw new IllegalStateException();
    }

    void dropWp() {
        putConf("wallpaper", "off");
        saveConf();
        applyConf();
    }

    void recycleWp() {
        if (wallpaper != null) {
            wallpaper.recycle();
            wallpaper = null;
        }
    }

    void paintMarks() {
        if (markNone != null) markNone.setText(wpOn ? "" : "·");
        if (markPick != null) markPick.setText(wpOn ? "·" : "");
        boolean can = wpOn;
        if (changeRow != null) {
            changeRow.setEnabled(can);
            changeRow.setClickable(can);
            changeRow.setBackground(can ? pressBg() : null);
        }
        if (changeLabel != null) changeLabel.setTextColor(can ? ink : mute());
        if (wpPathLine != null) {
            if (wpPath != null && wpPath.length() > 0) {
                wpPathLine.setText(wpPath);
                wpPathLine.setTextColor(can ? ink : mute());
                wpPathLine.setVisibility(View.VISIBLE);
            } else {
                wpPathLine.setText("");
                wpPathLine.setVisibility(View.GONE);
            }
        }
    }

    int mute() {
        int ir = Color.red(ink), ig = Color.green(ink), ib = Color.blue(ink);
        int pr = Color.red(paper), pg = Color.green(paper), pb = Color.blue(paper);
        return Color.rgb((ir + pr * 2) / 3, (ig + pg * 2) / 3, (ib + pb * 2) / 3);
    }

    void eink() {
        View v = getWindow().getDecorView();
        try {
            Method m = View.class.getMethod("setUpdateMode", int.class);
            m.invoke(v, Integer.valueOf(0));
        } catch (Throwable ignored) {
        }
        try {
            Method m = View.class.getMethod("invalidate", int.class);
            m.invoke(v, Integer.valueOf(0));
        } catch (Throwable ignored) {
        }
        String[] names = {
            "android.onyx.hardware.EpdController", "com.onyx.android.sdk.api.device.epd.EpdController"
        };
        for (int i = 0; i < names.length; i++) {
            try {
                Class<?> c = Class.forName(names[i]);
                Method[] ms = c.getMethods();
                for (int j = 0; j < ms.length; j++) {
                    if (ms[j].getName().startsWith("refresh") && ms[j].getParameterTypes().length == 1) {
                        ms[j].invoke(null, v);
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        v.postInvalidate();
    }

    int dp(int v) {
        return (int) (v * density + 0.5f);
    }

    static int vp() {
        return ViewGroup.LayoutParams.MATCH_PARENT;
    }

    static final class App {
        final String name;
        final ComponentName cn;

        App(String n, ComponentName c) {
            name = n;
            cn = c;
        }
    }

    final class StackNum extends View {
        final Paint p = new Paint();
        final Rect box = new Rect();
        final float cell, inkTop, inkH;
        String t = "00";

        StackNum(int px) {
            super(HomeActivity.this);
            p.setAntiAlias(false);
            p.setSubpixelText(false);
            p.setLinearText(true);
            p.setTypeface(textFace);
            p.setTextSize(px);
            p.setColor(ink);
            p.setTextAlign(Paint.Align.LEFT);
            p.setFontFeatureSettings("tnum");
            float w = 0f;
            int top = Integer.MAX_VALUE, bot = Integer.MIN_VALUE;
            for (int i = 0; i < 10; i++) {
                p.getTextBounds(Integer.toString(i), 0, 1, box);
                if (box.width() > w) w = box.width();
                if (box.top < top) top = box.top;
                if (box.bottom > bot) bot = box.bottom;
            }
            cell = w + px * 0.06f;
            inkTop = top;
            inkH = bot - top;
            setWillNotDraw(false);
            setLayerType(LAYER_TYPE_NONE, null);
        }

        void setDigits(String s) {
            if (s.equals(t)) return;
            t = s;
            invalidate();
        }

        void recolor() {
            p.setColor(ink);
            invalidate();
        }

        @Override
        protected void onMeasure(int wspec, int hspec) {
            int gap = Math.max(2, (int) (inkH * 0.04f));
            setMeasuredDimension((int) Math.ceil(cell * 2f), (int) Math.ceil(inkH) + gap);
        }

        @Override
        protected void onDraw(Canvas c) {
            float baseline = (getHeight() - inkH) / 2f - inkTop;
            if (t.length() < 2) {
                drawInk(c, t, (getWidth() - cell) / 2f, baseline);
                return;
            }
            float start = (getWidth() - cell * 2f) / 2f;
            drawInk(c, t.substring(0, 1), start, baseline);
            drawInk(c, t.substring(1, 2), start + cell, baseline);
        }

        void drawInk(Canvas c, String s, float left, float baseline) {
            p.getTextBounds(s, 0, 1, box);
            c.drawText(s, left + (cell - box.width()) / 2f - box.left, baseline, p);
        }
    }

    final class AppsAdapter extends BaseAdapter {
        final int textPx;

        AppsAdapter(int textPx) {
            this.textPx = textPx;
        }

        public int getCount() {
            return catalog.size();
        }

        public Object getItem(int i) {
            return catalog.get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int i, View convert, ViewGroup parent) {
            TextView t;
            if (convert instanceof TextView) {
                t = (TextView) convert;
            } else {
                t = body(textPx);
                t.setGravity(Gravity.CENTER);
                t.setLayoutParams(new AbsListView.LayoutParams(vp(), rowH));
            }
            t.setPressed(false);
            t.setSelected(false);
            t.setActivated(false);
            t.setText(catalog.get(i).name);
            t.setTextColor(pressText);
            t.setBackground(pressBg());
            return t;
        }
    }
}

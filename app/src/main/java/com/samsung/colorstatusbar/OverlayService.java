package com.samsung.colorstatusbar;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SignalStrength;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/** Accessibility overlay. Draws a non-touchable user-controlled strip at the top of the screen. */
public class OverlayService extends AccessibilityService {
    private static final long STATUS_REFRESH_MS = 4000L;
    private static final long RAINBOW_REFRESH_MS = 160L;

    private WindowManager wm;
    private LinearLayout overlay;
    private GradientDrawable overlayBackground;
    private SharedPreferences prefs;
    private SignalBars wifiBars, cellBars;
    private BatteryIcon batteryIcon;
    private TextView hourMinute, secondsView, dateView, weekdayView, networkType;
    private SymbolIcon airplaneIcon, alarmIcon, bluetoothIcon, soundIcon, nfcIcon, vpnIcon, dndIcon;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean hasServiceWindow;
    private boolean connected = false;
    private DisplayManager displayManager;
    private int lastDisplayWidth = -1;
    private int lastDisplayHeight = -1;
    private LinearLayout dateAndTime;
    private int dateFitMode = 0;
    private int lastBackgroundColor = Integer.MIN_VALUE;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener =
        (sp, key) -> onSettingsChanged(key);
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int displayId) { checkDisplayGeometry(); }
        @Override public void onDisplayRemoved(int displayId) { checkDisplayGeometry(); }
        @Override public void onDisplayChanged(int displayId) { checkDisplayGeometry(); }
    };
    private final Runnable rebuildTask = new Runnable() {
        @Override public void run() {
            if (!connected || prefs == null) return;
            if (prefs.getBoolean("enabled", false)) showOverlay();
            else removeOverlay();
        }
    };
    private int overrideNetworkType;
    private TelephonyManager telephonyManager;
    private TelephonyCallback mobileCallback;

    private final Runnable statusTask = new Runnable() {
        @Override public void run() {
            if (overlay == null) return;
            updateStatus();
            handler.postDelayed(this, STATUS_REFRESH_MS);
        }
    };
    private final Runnable rainbowTask = new Runnable() {
        @Override public void run() {
            if (overlay == null) return;
            applyColors();
            if (isRainbowActive()) handler.postDelayed(this, RAINBOW_REFRESH_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(StatusBarSettingsActivity.class.getName(), MODE_PRIVATE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        prefs.registerOnSharedPreferenceChangeListener(settingsListener);
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) displayManager.registerDisplayListener(displayListener, handler);
        scheduleRebuild();
    }

    // This service does NOT inspect or collect text from other apps.
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        checkDisplayGeometry();
    }

    @Override public void onInterrupt() { }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        scheduleRebuild();
    }

    private void scheduleRebuild() {
        if (!connected) return;
        handler.removeCallbacks(rebuildTask);
        handler.postDelayed(rebuildTask, 350);
    }

    private int[] getScreenSize() {
        if (Build.VERSION.SDK_INT >= 30 && wm != null) {
            try {
                Rect bounds = wm.getCurrentWindowMetrics().getBounds();
                return new int[]{bounds.width(), bounds.height()};
            } catch (RuntimeException ignored) { }
        }
        return new int[]{getResources().getDisplayMetrics().widthPixels,
            getResources().getDisplayMetrics().heightPixels};
    }

    private void checkDisplayGeometry() {
        if (!connected || overlay == null) return;
        int[] dims = getScreenSize();
        // Samsung's display-change callbacks can fire without the physical size
        // changing (e.g. refresh rate changes). Rebuilding then made the bar flash.
        if (dims[0] != lastDisplayWidth || dims[1] != lastDisplayHeight)
            scheduleRebuild();
    }

    private void onSettingsChanged(String key) {
        if (!connected || prefs == null) return;
        if (key != null && (key.endsWith("_color")
            || key.startsWith("rainbow_") || key.equals("background_opacity"))) {
            if (overlay != null) {
                applyColors();
                handler.removeCallbacks(rainbowTask);
                if (isRainbowActive()) handler.post(rainbowTask);
            }
            return;
        }
        if ("battery_follow_rainbow".equals(key) && batteryIcon != null) {
            applyColors();
            return;
        }
        if ("battery_style".equals(key) && batteryIcon != null) {
            batteryIcon.setStyle(opt("battery_style", 0, 0, 19));
            scheduleRebuild(); // Refresh spacing when switching portrait/landscape.
            return;
        }
        if ("battery_text_size".equals(key) && batteryIcon != null) {
            batteryIcon.setFontSp(opt("battery_text_size", 18, 7, 22));
            return;
        }
        if ("corner_radius".equals(key) && overlayBackground != null) {
            overlayBackground.setCornerRadius(dp(opt("corner_radius", 8, 0, 25)));
            return;
        }
        scheduleRebuild();
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
    private int opt(String key, int def, int min, int max) {
        return Math.max(min, Math.min(max, prefs.getInt(key, def)));
    }
    private boolean show(String key, boolean def) { return prefs.getBoolean(key, def); }
    private int savedColor(int index) {
        return StatusBarSettingsActivity.getIndividualColor(prefs, index);
    }
    private TextClock clock(String format, int size, int color) {
        TextClock t = new TextClock(this);
        t.setFormat24Hour(format);
        t.setFormat12Hour(format);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.TOP);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setSingleLine(true);
        return t;
    }
    private SymbolIcon addSymbol(LinearLayout parent, int shape, int size) {
        SymbolIcon icon = new SymbolIcon(this, shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
        int gap = dp(opt("icon_spacing", 3, 0, 15));
        lp.leftMargin = gap / 2;
        lp.rightMargin = gap - gap / 2;
        parent.addView(icon, lp);
        return icon;
    }
    // TEST12: picker numbers 06,07,08,13,14,15,16,18,19,20.
    private static boolean isUprightBatteryStyle(int style) {
        switch (style) {
            case 5: case 6: case 7: case 12: case 13:
            case 14: case 15: case 17: case 18: case 19: return true;
            default: return false;
        }
    }

    private void showOverlay() {
        removeOverlay();
        int left = opt("left", 12, 0, 80);
        int right = opt("right", 12, 0, 80);
        int height = opt("height", 44, 24, 90);
        int topEdgeGap = opt("top_edge_gap", 4, 0, 16);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.HORIZONTAL);
        overlay.setGravity(Gravity.CENTER_VERTICAL);
        overlay.setPadding(dp(5), 0, dp(5), 0);
        overlayBackground = new GradientDrawable();
        overlayBackground.setCornerRadius(dp(opt("corner_radius", 8, 0, 25)));
        overlay.setBackground(overlayBackground);

        // First row is ONLY one line: HH:mm:ss followed by yyyy/M/d weekday.
        dateAndTime = new LinearLayout(this);
        dateAndTime.setOrientation(LinearLayout.HORIZONTAL);
        // Align TextClock baselines, not their bounding-box centers.
        dateAndTime.setBaselineAligned(true);
        dateAndTime.setGravity(Gravity.TOP | Gravity.LEFT);
        dateAndTime.setClipChildren(true);
        dateFitMode = 0;
        int timeSp = opt("time_text_size", 16, 10, 30);
        hourMinute = clock("HH:mm", timeSp, savedColor(1));
        dateAndTime.addView(hourMinute);
        if (show("show_seconds", true)) {
            secondsView = clock(":ss", opt("seconds_text_size", 13, 8, 26), savedColor(2));
            dateAndTime.addView(secondsView);
        }
        boolean hasDate = show("show_date", true);
        boolean hasWeekday = show("show_weekday", true);
        if (hasDate) {
            String datePattern = show("short_date", false) ? "M/d" : "yyyy年M月d日";
            dateView = clock(datePattern, opt("date_text_size", 10, 8, 22), savedColor(3));
            dateView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(-2, -2);
            dateLp.leftMargin = dp(opt("date_spacing", 8, 0, 24));
            dateAndTime.addView(dateView, dateLp);
        }
        if (hasWeekday) {
            weekdayView = clock(show("short_date", false) ? "E" : "EEEE",
                opt("date_text_size", 10, 8, 22), savedColor(9));
            LinearLayout.LayoutParams weekLp = new LinearLayout.LayoutParams(-2, -2);
            weekLp.leftMargin = dp(hasDate ? 3 : opt("date_spacing", 8, 0, 24));
            dateAndTime.addView(weekdayView, weekLp);
        }
        overlay.addView(dateAndTime, new LinearLayout.LayoutParams(0, -2, 1));
        dateAndTime.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (r - l > 0 && r - l != oldR - oldL) fitDateToSpace();
        });

        LinearLayout indicators = new LinearLayout(this);
        indicators.setOrientation(LinearLayout.HORIZONTAL);
        indicators.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        int iconSize = opt("other_icon_size", 19, 12, 34);
        airplaneIcon = addSymbol(indicators, SymbolIcon.AIRPLANE, iconSize);
        alarmIcon = addSymbol(indicators, SymbolIcon.ALARM, iconSize);
        bluetoothIcon = addSymbol(indicators, SymbolIcon.BLUETOOTH, iconSize);
        soundIcon = addSymbol(indicators, SymbolIcon.VIBRATE, iconSize);
        nfcIcon = addSymbol(indicators, SymbolIcon.NFC, iconSize);
        vpnIcon = addSymbol(indicators, SymbolIcon.VPN, iconSize);
        dndIcon = addSymbol(indicators, SymbolIcon.DND, iconSize);

        int gap = dp(opt("icon_spacing", 3, 0, 15));
        wifiBars = new SignalBars(this, true);
        int wifiSize = opt("wifi_size", 20, 12, 36);
        LinearLayout.LayoutParams wifiLp = new LinearLayout.LayoutParams(dp(wifiSize), dp(wifiSize));
        wifiLp.setMargins(gap / 2, 0, gap / 2, 0);
        indicators.addView(wifiBars, wifiLp);

        cellBars = new SignalBars(this, false);
        int cellSize = opt("cell_size", 20, 12, 36);
        LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(dp(cellSize), dp(cellSize));
        cellLp.setMargins(gap / 2, 0, 0, 0);
        indicators.addView(cellBars, cellLp);
        networkType = new TextView(this);
        networkType.setTextSize(opt("network_text_size", 12, 8, 22));
        networkType.setIncludeFontPadding(false);
        networkType.setSingleLine(true);
        networkType.setGravity(Gravity.CENTER_VERTICAL);
        indicators.addView(networkType);

        batteryIcon = new BatteryIcon(this);
        batteryIcon.setFontSp(opt("battery_text_size", 18, 7, 22));
        batteryIcon.setStyle(opt("battery_style", 0, 0, 19));
        // Use physical display width in dp to separate cover and unfolded layouts.
        // A fold/unfold geometry change already triggers a rebuild of this overlay.
        int screenWidthDp = Math.round(getScreenSize()[0] / getResources().getDisplayMetrics().density);
        String screenKey = screenWidthDp >= 600 ? "inner" : "outer";
        int width = opt("battery_size_" + screenKey, 40, 28, 90);
        int batteryHeight = opt("battery_height_" + screenKey, Math.max(19, width / 2), 16, 60);
        LinearLayout.LayoutParams batteryLp = new LinearLayout.LayoutParams(dp(width), dp(batteryHeight));
        batteryLp.leftMargin = dp(isUprightBatteryStyle(opt("battery_style", 0, 0, 19))
            ? 2 : opt("battery_spacing", 10, 0, 30));
        batteryLp.leftMargin = Math.max(0, batteryLp.leftMargin + dp(opt("battery_x_" + screenKey, 100, 0, 200) - 100));
        indicators.addView(batteryIcon, batteryLp);
        batteryIcon.setAlpha(opt("battery_opacity", 100, 20, 100) / 100f);
        overlay.addView(indicators, new LinearLayout.LayoutParams(-2, -1));

        int[] screen = getScreenSize();
        int screenWidth = screen[0];
        lastDisplayWidth = screenWidth;
        lastDisplayHeight = screen[1];
        int widthPx = Math.max(dp(110), screenWidth - dp(left + right));
        widthPx = Math.min(screenWidth, widthPx);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            widthPx, dp(height), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = dp(left);
        // Accessibility overlay coordinates start at the top edge; try drawing
        // over the visible stock status bar while still letting touch pass through.
        // The first few pixels are left uncovered for third-party edge lighting.
        // Android still decides which overlay is actually on top.
        params.y = dp(topEdgeGap);
        try {
            wm.addView(overlay, params);
            hasServiceWindow = true;
            dateAndTime.post(this::fitDateToSpace);
            registerMobileTypeCallback();
            applyColors();
            updateStatus();
            handler.postDelayed(statusTask, STATUS_REFRESH_MS);
            if (isRainbowActive()) handler.post(rainbowTask);
        } catch (Exception error) {
            removeOverlay();
            // Vendor restrictions may reject this window type; keep the service
            // alive so the user can change settings or disable accessibility.
        }
    }

    private void fitDateToSpace() {
        if (dateAndTime == null || dateView == null || !show("auto_compact_date", true)) return;
        int available = dateAndTime.getWidth();
        if (available <= 0 || show("short_date", false)) return;

        // Keep time, date and weekday adjacent and on one baseline. When icons
        // take away space (including on a folded display), shorten only the date.
        java.util.Date now = new java.util.Date();
        java.util.Locale locale = java.util.Locale.TAIWAN;
        String[] patterns = {"yyyy年M月d日", "M月d日", "M/d"};
        float fixed = hourMinute.getPaint().measureText(hourMinute.getText().toString());
        if (secondsView != null) fixed += secondsView.getPaint().measureText(
            secondsView.getText().toString());
        fixed += dp(opt("date_spacing", 8, 0, 24));
        if (weekdayView != null) fixed += weekdayView.getPaint().measureText(
            weekdayView.getText().toString()) + dp(3);
        int mode = patterns.length - 1;
        for (int i = 0; i < patterns.length; i++) {
            String formatted = new java.text.SimpleDateFormat(patterns[i], locale).format(now);
            if (fixed + dateView.getPaint().measureText(formatted) < available - dp(2)) {
                mode = i;
                break;
            }
        }
        if (mode != dateFitMode) {
            dateFitMode = mode;
            dateView.setText(new java.text.SimpleDateFormat(patterns[mode], locale).format(new java.util.Date()));
        }
    }

    private void updateStatus() {
        if (batteryIcon != null) updateBattery();
        if (wifiBars != null) updateWifi();
        if (cellBars != null) updateCellular();
        if (bluetoothIcon != null) updateBluetooth();
        if (alarmIcon != null) updateAlarm();
        if (soundIcon != null) updateSoundAndAirplane();
        if (dndIcon != null) updateDnd();
        if (nfcIcon != null) updateNfcAndVpn();
    }

    private void updateBattery() {
        if (!show("show_battery", true)) { batteryIcon.setVisibility(View.GONE); return; }
        batteryIcon.setVisibility(View.VISIBLE);
        int level = -1;
        boolean charging = false;
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i != null) {
                int raw = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (raw >= 0 && scale > 0) level = Math.round(raw * 100f / scale);
                int s = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = s == BatteryManager.BATTERY_STATUS_CHARGING
                    || s == BatteryManager.BATTERY_STATUS_FULL;
            }
            if (level < 0) {
                BatteryManager manager = (BatteryManager) getSystemService(BATTERY_SERVICE);
                if (manager != null) {
                    level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    charging = manager.isCharging();
                }
            }
        } catch (Exception ignored) {}
        batteryIcon.setBattery(level, charging);
    }

    private void updateWifi() {
        if (!show("show_wifi", true)) { wifiBars.setVisibility(View.GONE); return; }
        boolean connected = false;
        int bars = 0;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                for (Network network : cm.getAllNetworks()) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        connected = true;
                        if (Build.VERSION.SDK_INT >= 29 && caps.getTransportInfo() instanceof WifiInfo) {
                            int rssi = ((WifiInfo)caps.getTransportInfo()).getRssi();
                            if (rssi > -127 && rssi <= 0)
                                bars = Math.max(bars, Math.max(1, Math.min(4, WifiManager.calculateSignalLevel(rssi, 5))));
                        }
                    }
                }
            }
            WifiManager wmWifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wmWifi != null && wmWifi.isWifiEnabled()) {
                WifiInfo wifi = wmWifi.getConnectionInfo();
                if (wifi != null) {
                    int rssi = wifi.getRssi();
                    if (rssi > -127 && rssi <= 0) {
                        connected = true;
                        bars = Math.max(bars, Math.max(1, Math.min(4, WifiManager.calculateSignalLevel(rssi, 5))));
                    }
                }
            }
        } catch (Exception ignored) { }
        wifiBars.setVisibility(connected ? View.VISIBLE : View.GONE);
        wifiBars.setLevel(bars);
    }

    private void updateCellular() {
        if (!show("show_cell", true)) {
            cellBars.setVisibility(View.GONE); networkType.setVisibility(View.GONE); return;
        }
        boolean airplane = Settings.Global.getInt(getContentResolver(),
            Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        int bars = 0;
        String type = "";
        boolean hasSim = false;
        try {
            TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            if (tm != null) {
                hasSim = tm.getSimState() == TelephonyManager.SIM_STATE_READY;
                if (hasSim && !airplane) {
                    SignalStrength signal = tm.getSignalStrength();
                    if (signal != null) bars = Math.max(0, Math.min(4, signal.getLevel()));
                    try {
                        type = cellularName(tm.getDataNetworkType());
                    } catch (SecurityException ignored) { type = ""; }
                    if (overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
                        || overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED)
                        type = "5G";
                }
            }
        } catch (Exception ignored) { }
        boolean visible = hasSim && !airplane;
        cellBars.setVisibility(visible ? View.VISIBLE : View.GONE);
        cellBars.setLevel(bars);
        if (!TextUtils.equals(networkType.getText(), type)) networkType.setText(type);
        networkType.setVisibility(visible && !type.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String cellularName(int t) {
        switch (t) {
            case TelephonyManager.NETWORK_TYPE_NR: return "5G";
            case TelephonyManager.NETWORK_TYPE_LTE: return "4G";
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B: return "3G";
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_GSM:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT: return "2G";
            default: return "";
        }
    }

    /** 5G NSA is often reported as LTE by getDataNetworkType; use the public override callback. */
    private void registerMobileTypeCallback() {
        if (Build.VERSION.SDK_INT < 31 || mobileCallback != null) return;
        try {
            telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            if (telephonyManager == null) return;
            mobileCallback = new DisplayCallback();
            telephonyManager.registerTelephonyCallback(getMainExecutor(), mobileCallback);
        } catch (Exception e) { mobileCallback = null; }
    }
    private final class DisplayCallback extends TelephonyCallback
        implements TelephonyCallback.DisplayInfoListener {
        @Override public void onDisplayInfoChanged(TelephonyDisplayInfo info) {
            overrideNetworkType = info.getOverrideNetworkType();
            if (overlay != null && cellBars != null) updateCellular();
        }
    }

    private void updateBluetooth() {
        if (!show("show_bluetooth", true)) { bluetoothIcon.setVisibility(View.GONE); return; }
        boolean on = false;
        try {
            BluetoothManager bm = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
            on = adapter != null && adapter.isEnabled();
        } catch (SecurityException e) {
            on = Settings.Global.getInt(getContentResolver(), "bluetooth_on", 0) == 1;
        } catch (Exception ignored) {}
        bluetoothIcon.setVisibility(on ? View.VISIBLE : View.GONE);
    }
    private void updateAlarm() {
        if (!show("show_alarm", true)) { alarmIcon.setVisibility(View.GONE); return; }
        boolean on = false;
        try {
            AlarmManager manager = (AlarmManager)getSystemService(ALARM_SERVICE);
            on = manager != null && manager.getNextAlarmClock() != null;
        } catch (Exception ignored) {}
        alarmIcon.setVisibility(on ? View.VISIBLE : View.GONE);
    }
    private void updateSoundAndAirplane() {
        boolean airplane = Settings.Global.getInt(getContentResolver(),
            Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        airplaneIcon.setVisibility(airplane && show("show_airplane", true) ? View.VISIBLE : View.GONE);
        if (!show("show_sound", true)) { soundIcon.setVisibility(View.GONE); return; }
        try {
            AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
            int mode = audio == null ? AudioManager.RINGER_MODE_NORMAL : audio.getRingerMode();
            if (mode == AudioManager.RINGER_MODE_VIBRATE) {
                soundIcon.setShape(SymbolIcon.VIBRATE);
                soundIcon.setColor(resolve(15, 3));
                soundIcon.setVisibility(View.VISIBLE);
            } else if (mode == AudioManager.RINGER_MODE_SILENT) {
                soundIcon.setShape(SymbolIcon.MUTE);
                soundIcon.setColor(resolve(16, 4));
                soundIcon.setVisibility(View.VISIBLE);
            } else soundIcon.setVisibility(View.GONE);
        } catch (Exception ignored) { soundIcon.setVisibility(View.GONE); }
    }
    private void updateDnd() {
        if (!show("show_dnd", true)) { dndIcon.setVisibility(View.GONE); return; }
        boolean on = false;
        try {
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            int f = manager == null ? NotificationManager.INTERRUPTION_FILTER_ALL
                : manager.getCurrentInterruptionFilter();
            on = f != NotificationManager.INTERRUPTION_FILTER_ALL
                && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
        } catch (Exception ignored) {}
        dndIcon.setVisibility(on ? View.VISIBLE : View.GONE);
    }
    private void updateNfcAndVpn() {
        boolean nfcOn = false;
        if (show("show_nfc", false)) {
            try {
                NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
                nfcOn = nfc != null && nfc.isEnabled();
            } catch (Exception ignored) {}
        }
        nfcIcon.setVisibility(nfcOn ? View.VISIBLE : View.GONE);
        boolean vpn = false;
        if (show("show_vpn", true)) {
            try {
                ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
                if (cm != null) {
                    for (Network network : cm.getAllNetworks()) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                            vpn = true; break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        vpnIcon.setVisibility(vpn ? View.VISIBLE : View.GONE);
    }

    private boolean isRainbowActive() {
        if (show("rainbow_enabled", false) || show("rainbow_bg", false)) return true;
        for (String key : StatusBarSettingsActivity.COLOR_KEYS)
            if (show("rainbow_item_" + key, false)) return true;
        return false;
    }
    private int rainbowColor(int slot) {
        long cycle = opt("rainbow_period", 7, 2, 30) * 1000L;
        double position = ((System.currentTimeMillis() % cycle) / (double)cycle * 7.0 + slot * 0.72) % 7.0;
        int i = (int)Math.floor(position);
        double amount = position - i;
        int a = prefs.getInt("rainbow_" + i, StatusBarSettingsActivity.SEVEN_COLORS[i]);
        int j = (i + 1) % 7;
        int b = prefs.getInt("rainbow_" + j, StatusBarSettingsActivity.SEVEN_COLORS[j]);
        return Color.rgb((int)(Color.red(a) + (Color.red(b) - Color.red(a)) * amount),
            (int)(Color.green(a) + (Color.green(b) - Color.green(a)) * amount),
            (int)(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * amount));
    }
    private int resolve(int index, int slot) {
        return (show("rainbow_enabled", false)
            || show("rainbow_item_" + StatusBarSettingsActivity.COLOR_KEYS[index], false))
            ? rainbowColor(slot) : savedColor(index);
    }
    private void applyColors() {
        if (overlay == null || overlayBackground == null) return;
        int opacity = opt("background_opacity", 76, 0, 100);
        int bg = (show("rainbow_bg", false)
            || show("rainbow_item_" + StatusBarSettingsActivity.COLOR_KEYS[0], false))
            ? rainbowColor(4) : savedColor(0);
        int alpha = Math.round(opacity * 255f / 100);
        int background = (alpha << 24) | (bg & 0xFFFFFF);
        if (background != lastBackgroundColor) {
            lastBackgroundColor = background;
            overlayBackground.setColor(background);
        }
        if (hourMinute != null) hourMinute.setTextColor(resolve(1, 0));
        if (secondsView != null) secondsView.setTextColor(resolve(2, 1));
        if (dateView != null) dateView.setTextColor(resolve(3, 2));
        if (weekdayView != null) weekdayView.setTextColor(resolve(9, 3));
        if (wifiBars != null) wifiBars.setColor(resolve(4, 4));
        if (cellBars != null) cellBars.setColor(resolve(5, 5));
        if (networkType != null) networkType.setTextColor(resolve(6, 6));
        if (batteryIcon != null) {
            // The battery follows rainbow only when explicitly opted in.
            boolean follow = show("battery_follow_rainbow", false);
            batteryIcon.setFollowRainbow(follow);
            batteryIcon.setColor(follow ? resolve(7, 7) : savedColor(7));
            batteryIcon.setNumberColor(follow ? resolve(10, 8) : savedColor(10));
            batteryIcon.setFillColor(follow ? resolve(11, 9) : savedColor(11));
        }
        if (airplaneIcon != null) airplaneIcon.setColor(resolve(12, 0));
        if (alarmIcon != null) alarmIcon.setColor(resolve(13, 1));
        if (bluetoothIcon != null) bluetoothIcon.setColor(resolve(14, 2));
        if (soundIcon != null) soundIcon.setColor(soundIcon.isShape(SymbolIcon.MUTE)
            ? resolve(16, 4) : resolve(15, 3));
        if (nfcIcon != null) nfcIcon.setColor(resolve(17, 5));
        if (vpnIcon != null) vpnIcon.setColor(resolve(18, 6));
        if (dndIcon != null) dndIcon.setColor(resolve(19, 7));
    }

    private void removeOverlay() {
        handler.removeCallbacks(statusTask);
        handler.removeCallbacks(rainbowTask);
        if (hasServiceWindow && overlay != null) {
            try { wm.removeView(overlay); } catch (Exception ignored) {}
        }
        hasServiceWindow = false;
        overlay = null;
        dateAndTime = null;
        lastBackgroundColor = Integer.MIN_VALUE;
        hourMinute = secondsView = dateView = weekdayView = networkType = null;
        airplaneIcon = alarmIcon = bluetoothIcon = soundIcon = nfcIcon = vpnIcon = dndIcon = null;
        wifiBars = cellBars = null;
        batteryIcon = null;
        if (Build.VERSION.SDK_INT >= 31 && telephonyManager != null && mobileCallback != null) {
            try { telephonyManager.unregisterTelephonyCallback(mobileCallback); } catch (Exception ignored) {}
        }
        mobileCallback = null;
        telephonyManager = null;
        overrideNetworkType = 0;
    }
    @Override public boolean onUnbind(Intent intent) {
        cleanup();
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() { cleanup(); super.onDestroy(); }
    private void cleanup() {
        if (connected) {
            connected = false;
            if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(settingsListener);
            if (displayManager != null) displayManager.unregisterDisplayListener(displayListener);
        }
        handler.removeCallbacks(rebuildTask);
        removeOverlay();
    }

    /** Draws cellular bars or Wi-Fi arcs; does not claim a signal strength if Android withholds RSSI. */
    private static final class SignalBars extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean wifi;
        private int color = 0xFFFFFFFF;
        private int level;
        SignalBars(Context context, boolean wifi) { super(context); this.wifi = wifi; }
        void setLevel(int value) {
            int next = Math.max(0, Math.min(4, value));
            if (level != next) { level = next; invalidate(); }
        }
        void setColor(int c) { if (color != c) { color = c; invalidate(); } }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            canvas.save();
            canvas.scale(w / 24f, h / 24f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (!wifi) {
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 4; i++) {
                    paint.setColor(color);
                    paint.setAlpha(i < level ? 255 : 58);
                    float x = 2f + i * 5.2f;
                    float y = 18f - i * 4f;
                    canvas.drawRoundRect(x, y, x + 3.4f, 22f, 1f, 1f, paint);
                }
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.7f);
                for (int ring = 3; ring >= 1; ring--) {
                    paint.setColor(color);
                    paint.setAlpha(level >= ring + 1 ? 255 : 58);
                    float r = ring * 4.8f;
                    canvas.drawArc(new RectF(12f - r, 20f - r, 12f + r, 20f + r),
                        220f, 100f, false, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(color);
                paint.setAlpha(level > 0 ? 255 : 58);
                canvas.drawCircle(12f, 20f, 2f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
            canvas.restore();
        }
    }

    /** Filled Samsung-like pill, battery percentage rendered INSIDE the outline. */
    private static final class BatteryIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int level = -1;
        private int color = 0xFFFF8A80;
        private int numberColor = Color.WHITE;
        private int fillColor = 0xFFFF8A80;
        private boolean charging;
        private int fontSp = 18;
        private int style = 0;
        void setStyle(int s) { if (style != s) { style = s; invalidate(); } }
        BatteryIcon(Context context) { super(context); }
        void setBattery(int p, boolean c) {
            if (level != p || charging != c) { level = p; charging = c; invalidate(); }
        }
        void setColor(int c) { if (color != c) { color = c; invalidate(); } }
        void setNumberColor(int c) { if (numberColor != c) { numberColor = c; invalidate(); } }
        void setFillColor(int c) { if (fillColor != c) { fillColor = c; invalidate(); } }
        void setFontSp(int sp) { if (fontSp != sp) { fontSp = sp; invalidate(); } }
        // TEST6: distinct vector compositions for the twenty reference styles.
        // The artwork is drawn at normalized 200x100 coordinates, then scaled.
        private void shape(Canvas c, float l,float t,float r,float b,float rad,int color) {
            paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(color);
            c.drawRoundRect(l,t,r,b,rad,rad,paint);
        }
        private void gradient(Canvas c,float l,float t,float r,float b,float rad,int... colors) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
            paint.setShader(new android.graphics.LinearGradient(l,t,r,b,colors,null,android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(l,t,r,b,rad,rad,paint); paint.setShader(null);
        }
        private void ring(Canvas c,float x,float y,float radius,float thickness,int color) {
            paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(thickness);paint.setColor(color);
            c.drawCircle(x,y,radius,paint);paint.setStyle(Paint.Style.FILL);
        }
        private void bolt(Canvas c,float x,float y,float size,int color) {
            Path b=new Path(); b.moveTo(x+size*.50f,y);b.lineTo(x+size*.14f,y+size*.53f);
            b.lineTo(x+size*.43f,y+size*.53f);b.lineTo(x+size*.31f,y+size);
            b.lineTo(x+size*.88f,y+size*.39f);b.lineTo(x+size*.56f,y+size*.39f);b.close();
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawPath(b,paint);
        }
        // TEST11: common full-width horizontal battery, preserves metallic ends and charge level.
        private int levelColor(float pct) {
            if (pct <= .20f) return 0xFFFF363B;
            float t = Math.min(1f,(pct-.20f)/.80f);
            // Red at 20%, blue at 100%, smoothly interpolated.
            return Color.rgb(Math.round(255*(1-t)+30*t),
                Math.round(54*(1-t)+165*t),Math.round(59*(1-t)+255*t));
        }
        private void drawHorizontalCell(Canvas c,float pct,int primary,int secondary,
                boolean rainbowLiquid,int colorCount,boolean lightning) {
            // Main silhouette spans nearly all the 200-unit view width.
            gradient(c,11,17,180,83,20,0xFF515D6B,0xFFF5FAFF,0xFF515D6B);
            gradient(c,21,22,172,78,14,0xFF172A42,secondary,0xFF10253B);
            c.save();c.clipRect(24,25,24+145*pct,75);
            if (rainbowLiquid) {
                // 26 closely spaced color bands based on the rainbow glass reference.
                for(int i=0;i<colorCount;i++) {
                    float left=24f+i*145f/colorCount;
                    float right=24f+(i+1)*145f/colorCount+0.5f;
                    float hue=(185f+i*300f/(colorCount-1))%360f;
                    paint.setShader(null);paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.HSVToColor(new float[]{hue,.78f,.98f}));
                    c.drawRect(left,25,right,75,paint);
                }
                paint.setColor(0x88FFFFFF);
                for(int i=0;i<13;i++)c.drawCircle(32+(i*37)%132,33+(i*19)%37,1.6f+i%3,paint);
            } else {
                gradient(c,24,25,169,75,11,primary,secondary,primary);
            }
            c.restore();
            // Glossy reflection and chrome end caps.
            gradient(c,24,25,169,38,6,0xB8FFFFFF,0x22FFFFFF,0x00FFFFFF);
            gradient(c,11,17,26,83,8,0xFF48505D,0xFFEDF6FC,0xFF39434F);
            gradient(c,169,17,183,83,8,0xFF434B57,0xFFFAFFFF,0xFF3C4650);
            gradient(c,183,39,193,61,4,0xFF4D5964,0xFFE8F0F7,0xFF48505D);
            if (lightning) bolt(c,77,29,42,0xEEFFFFFF);
        }
        private void drawStyledBattery(Canvas canvas,float w,float h) {
            canvas.save();canvas.scale(w/200f,h/100f);
            // TEST13: shift upright artwork right inside its existing view to reduce empty right side.
            if (isUprightBatteryStyle(style)) canvas.translate(8f, 0f);
            Canvas c=canvas;
            float pct=Math.max(0,Math.min(100,level))/100f;
            int cyan=0xFF4FE9FF,blue=0xFF168EFF, silver=0xFFDDEBF5;
            int green=0xFF80F645,pink=0xFFFF4BCF, gold=0xFFFFC954;
            int tint=followRainbow?color:cyan;
            // Each branch has a different silhouette, layout, and material treatment.
            switch(style) {
                case 0: // aurora wave capsule
                    gradient(c,9,15,179,84,30,0xFF104777,0xFF522FDD,0xFF1FADFC);
                    gradient(c,15,20,15+158*pct,79,25,0xFF31CBFF,0xFF7162FF,0xFF5F25C7);
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);paint.setColor(tint);
                    c.drawRoundRect(8,13,181,86,28,28,paint);paint.setStyle(Paint.Style.FILL);
                    Path wave=new Path();wave.moveTo(34,56);wave.cubicTo(62,12,88,28,110,53);wave.cubicTo(132,79,153,66,171,45);
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(0xFFE1F6FF);c.drawPath(wave,paint);paint.setStyle(Paint.Style.FILL);
                    shape(c,184,39,194,60,4,tint);break;
                case 1: // V4.30 original: drawn by legacy onDraw branch
                    break;
                case 2: // five separate RGB cells
                    shape(c,9,16,181,84,12,0xFF182738);
                    int[] cells={0xFFFF428C,0xFFB24BFF,0xFF4685FF,0xFF31D9E4,0xFF27B38B};
                    for(int i=0;i<5;i++)shape(c,18+i*31,25,43+i*31,75,5,pct*5>i?cells[i]:0xFF28313A);
                    ring(c,185,50,1,1,cyan);shape(c,181,39,191,61,3,cyan);break;
                case 3: // TEST11: 26-tone horizontal rainbow liquid glass cylinder
                    drawHorizontalCell(c,pct,0xFF1EACFF,0xFFEE5CBA,true,26,false);
                    break;
                case 4: // gold beveled premium battery (provisional)
                    gradient(c,19,20,176,80,18,0xFF8A4C0E,0xFFFFE8A1,0xFFC38320,0xFF78440D);
                    gradient(c,29,28,29+137*pct,72,10,0xFFFFD64F,0xFFFFA925,0xFFB36C14);
                    shape(c,179,39,193,61,4,gold);break;
                case 5: // tilted transparent low charge glass
                    c.rotate(-15,100,50);gradient(c,62,10,139,90,18,0x55FFFFFF,0xAACAD9E8,0x55FFFFFF);
                    gradient(c,70,68,131,82,8,0xFFFF934F,0xFFFF4629);
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(silver);c.drawRoundRect(62,10,139,90,18,18,paint);paint.setStyle(Paint.Style.FILL);break;
                case 6: // blue rectangular battery with orange bolt
                    gradient(c,45,9,150,91,5,0xFF83F8FF,0xFF119DD7,0xFF38354C);
                    shape(c,84,2,111,10,2,silver);bolt(c,75,18,57,0xFFFF8B13);break;
                case 7: // tall glass plasma vessel
                    gradient(c,71,6,129,94,12,0xFF173452,0xFF19AFFF,0xFF10234F);
                    ring(c,100,8,23,4,0xFFB6DAF1);ring(c,100,92,23,4,0xFF70CFFF);
                    bolt(c,83,27,36,0xFF4FFFFF);break;
                case 8: // tilted horizontal rainbow glass cylinder
                    c.rotate(-16,100,50);gradient(c,25,24,169,76,17,0xFFFF4970,0xFFFFE546,0xFF45F26C,0xFF5762FF);
                    ring(c,27,50,24,6,silver);ring(c,171,50,24,6,0xFFB8C2D5);break;
                case 9: // segmented blue mechanical energy cylinder
                    gradient(c,23,23,171,78,13,0xFF0C4175,0xFF26CAFF,0xFF184C9A);
                    for(int i=0;i<4;i++)shape(c,48+i*27,25,50+i*27,76,1,0x885DCAFF);
                    shape(c,17,17,35,84,7,0xFF8E99AD);shape(c,165,17,183,84,7,0xFF7B8799);
                    bolt(c,83,28,34,0xFFFFCF22);break;
                case 10: // green glass cylinder
                    gradient(c,23,24,174,76,17,0xFFE7FFFF,0xFF73F445,0xFF32B940,0xFFD9FFFF);
                    shape(c,33,36,33+129*pct,64,8,0x9954E62D);
                    paint.setColor(0xCCFFFFFF);paint.setStrokeWidth(3);c.drawLine(40,32,155,32,paint);break;
                case 11: // TEST11: glossy pink lightning tube, level blue -> red
                    drawHorizontalCell(c,pct,levelColor(pct),0xFFFF48A9,false,0,true);
                    break;
                case 12: // vertical titanium can
                    gradient(c,66,12,134,88,15,0xFF3A445C,0xFF38C6F7,0xFF122C74,0xFF9AAABF);
                    gradient(c,65,10,135,26,12,0xFF64768B,0xFFF3F6FF,0xFF59697C);
                    gradient(c,65,74,135,90,12,0xFF5A6676,0xFFBFCBD8,0xFF29394A);break;
                case 13: // transparent upright rainbow liquid bubbles
                    gradient(c,68,11,132,89,13,0xFFCCD5DC,0xFF44F6EA,0xFFFFE96E,0xFFFF5CBD);
                    gradient(c,67,8,133,22,9,0xFF6D7583,0xFFE5F1F9,0xFF748294);
                    for(int i=0;i<8;i++)ring(c,83+(i*13)%36,32+(i*19)%48,2+(i%3),1,0xAAFFFFFF);break;
                case 14: // slender purple neon transparent tube
                    c.rotate(15,100,50);gradient(c,84,5,117,95,9,0xFF2A177C,0xFF62E7FF,0xFFFF63D9);
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);paint.setColor(0xFFDA9DFF);
                    c.drawRoundRect(84,5,117,95,9,9,paint);paint.setStyle(Paint.Style.FILL);break;
                case 15: // blue bubble science can + dial
                    ring(c,100,50,47,2,0xFF8CA4BB);gradient(c,76,13,124,87,10,0xFF0C4B86,0xFF24C6FF,0xFF07518F);
                    gradient(c,75,10,125,23,8,0xFF68798B,0xFFDFEAF0,0xFF788C9D);
                    for(int i=0;i<8;i++)ring(c,87+(i*11)%27,29+(i*13)%48,2,1,0xAAFFFFFF);break;
                case 16: // rainbow dual neon rings
                    int[] rainbow={0xFFFF4977,0xFFFFCF35,0xFF42F48E,0xFF2ED8FF,0xFF9861FF};
                    for(int i=0;i<5;i++) {paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(7);
                        paint.setColor(rainbow[i]);c.drawArc(58,8,142,92,-90+i*72,70,false,paint);}
                    ring(c,100,50,34,2,0xFF7ACBFF);paint.setStyle(Paint.Style.FILL);break;
                case 17: // diagonal square metallic battery
                    c.rotate(-30,100,50);gradient(c,55,13,145,87,8,0xFF6C7C8C,0xFF24C6FF,0xFF066FAF);
                    shape(c,90,7,111,14,2,silver);shape(c,59,19,59+81*pct,78,5,0x7752D8FF);break;
                case 18: // TEST8: upright glass can, silver caps, red/orange/green energy
                    // Artwork follows the supplied cylindrical battery reference, rendered as vector.
                    gradient(c,72,12,128,88,10,0xFF386C4E,0xB0C6FFDC,0xFF2B865A,0xFFB5FFE0);
                    shape(c,78,20,122,81,7,0xC8172522);
                    if (pct > 0f) {
                        float top = 80f - 57f * pct;
                        c.save();c.clipRect(79,top,121,80);
                        gradient(c,79,20,121,80,5,0xFF70F33D,0xFFFFB728,0xFFEA4A32);
                        c.restore();
                    }
                    gradient(c,68,9,132,23,8,0xFF68767A,0xFFF7FFFD,0xFF7B8A8A,0xFFBCC6C5);
                    gradient(c,86,3,114,13,4,0xFF536367,0xFFFAFFFF,0xFF5B6869);
                    gradient(c,69,79,131,91,7,0xFF677477,0xFFE7F0EE,0xFF667476);
                    paint.setColor(0x77FFFFFF);paint.setStrokeWidth(2);c.drawLine(79,26,79,76,paint);
                    for(int i=0;i<3;i++) {
                        float yy=36+i*16;
                        paint.setColor(0x9954FF2D);c.drawCircle(100,yy,3,paint);
                    }
                    break;
                case 19: // TEST8: upright chrome can with four level-dependent colors
                    gradient(c,72,13,128,87,8,0xFF3A4047,0xFF7E909A,0xFF202B34,0xFF86949B);
                    shape(c,77,21,123,81,3,0xFF13191D);
                    int liquid = level <= 20 ? 0xFFFF4238 : level <= 50 ? 0xFFFF9836
                        : level <= 85 ? 0xFFB2F538 : 0xFF30BFFF;
                    if(pct>0) {
                        float top=80-58*pct;
                        shape(c,78,top,122,80,2,liquid);
                        paint.setColor(0x66FFFFFF);paint.setStrokeWidth(2);c.drawLine(80,top+1,119,top+1,paint);
                    }
                    gradient(c,69,11,131,23,5,0xFF5B6269,0xFFF5FAFC,0xFF68717A);
                    shape(c,88,5,112,13,3,0xFFD7E0E3);
                    gradient(c,69,79,131,89,5,0xFF59616A,0xFFEDF4F7,0xFF606A73);
                    break;
            }
            // TEST7: fontSp is real SP converted to drawing coordinates. Keep it
            // independent of width and height sliders, subject to fitting inside.
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);paint.setColor(numberColor);
            String value=level<0?"--":level+"%";
            float pxPerUnit = h / 100f;
            // TEST14: increase apparent percentage size while preserving the existing slider.
            float desired = fontSp * 1.30f * getResources().getDisplayMetrics().scaledDensity / Math.max(.01f, pxPerUnit);
            float labelX=105f, labelY=59f, maxWidth=140f, maxHeight=48f;
            if(style==16) {labelX=100;labelY=58;maxWidth=95;maxHeight=60;}
            else if(isUprightBatteryStyle(style)) {
                labelX=157f; labelY=65f; maxWidth=82f; maxHeight=68f;
            } else if(style==3||style==11) {
                labelX=105f; labelY=61f; maxWidth=95f; maxHeight=58f;
            }
            paint.setTextSize(Math.min(desired,maxHeight));
            float measured=paint.measureText(value);
            if(measured>maxWidth) paint.setTextSize(paint.getTextSize()*maxWidth/measured);
            paint.setShadowLayer(2,0,1,Color.BLACK);
            c.drawText(value,labelX,labelY,paint);
            paint.clearShadowLayer();
            if (charging && style != 1) {
                float phase = (System.currentTimeMillis() % 1050L) / 1050f;
                paint.setShader(null); paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                if (style == 16) {
                    float start = -90f + phase * 360f;
                    paint.setColor(0xB000E8FF);paint.setStrokeWidth(12f);
                    c.drawArc(58,8,142,92,start,85f,false,paint);
                    paint.setColor(Color.WHITE);paint.setStrokeWidth(5f);
                    c.drawArc(58,8,142,92,start+10f,55f,false,paint);
                } else if (isUprightBatteryStyle(style)) {
                    // Light runs vertically inside the upright glass, not in a side ring.
                    float yy = 26f + 49f * phase;
                    paint.setStrokeWidth(8f);paint.setColor(0xB000E8FF);
                    c.drawLine(91f,yy,109f,yy,paint);
                    paint.setStrokeWidth(3f);paint.setColor(Color.WHITE);
                    c.drawLine(93f,yy,107f,yy,paint);
                } else {
                    float xx=29f+132f*phase;
                    paint.setStrokeWidth(13f);paint.setColor(0xB000E8FF);
                    c.drawLine(xx,31f,xx,70f,paint);
                    paint.setStrokeWidth(5f);paint.setColor(Color.WHITE);
                    c.drawLine(xx,34f,xx,67f,paint);
                }
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
            }
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);canvas.restore();
            if (charging && style != 1 && getVisibility() == View.VISIBLE) postInvalidateDelayed(60);
        }
        private boolean followRainbow = false;
        void setFollowRainbow(boolean value) { if(followRainbow!=value){followRainbow=value;invalidate();} }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (style >= 0 && style <= 19 && style != 1) { drawStyledBattery(canvas, w, h); return; }
            float x = w * 0.04f, y = h * 0.16f, right = w * 0.90f, bottom = h * 0.84f;
            float radius = Math.min(h * 0.22f, w * 0.09f);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.5f, h * 0.075f));
            canvas.drawRoundRect(x, y, right, bottom, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(right + w * 0.012f, h * 0.37f,
                w * 0.98f, h * 0.63f, h * 0.035f, h * 0.035f, paint);
            // Dark inner panel keeps the white percentage legible even with transparent bar backgrounds.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xD5212530);
            canvas.drawRoundRect(x + w * 0.025f, y + h * 0.065f,
                right - w * 0.02f, bottom - h * 0.065f,
                h * 0.08f, h * 0.08f, paint);
            paint.setColor(fillColor);
            paint.setAlpha(92);
            if (level >= 0) {
                float innerX = x + w * 0.032f;
                float innerRight = right - w * 0.03f;
                float filledRight = innerX + (innerRight - innerX)
                    * Math.max(0, Math.min(100, level)) / 100f;
                if (filledRight > innerX) canvas.drawRoundRect(innerX, y + h * 0.1f,
                    filledRight, bottom - h * 0.1f, h * 0.07f, h * 0.07f, paint);
            }
            paint.setAlpha(255);
            String number = level >= 0 ? level + "%" : "--";
            paint.setColor(numberColor);
            if (charging) {
                Path lightning = new Path();
                lightning.moveTo(x + w * 0.17f, h * 0.29f);
                lightning.lineTo(x + w * 0.10f, h * 0.53f);
                lightning.lineTo(x + w * 0.18f, h * 0.53f);
                lightning.lineTo(x + w * 0.14f, h * 0.72f);
                lightning.lineTo(x + w * 0.28f, h * 0.44f);
                lightning.lineTo(x + w * 0.20f, h * 0.44f);
                lightning.close();
                canvas.drawPath(lightning, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
            float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
            // Respect the slider's sp value, constrained only by available battery height.
            float textSize = Math.min(fontSp * 1.20f * scaledDensity, h * 0.82f);
            paint.setTextSize(textSize);
            float available = (right - x) * (charging ? 0.66f : 0.92f);
            float minTextSize = 6f * scaledDensity;
            while (paint.measureText(number) > available && textSize > minTextSize) {
                textSize -= 0.5f * scaledDensity;
                paint.setTextSize(textSize);
            }
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float textY = (y + bottom) / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(number, (x + right) / 2f + (charging ? w * 0.075f : 0f), textY, paint);
        }
    }

    /** Vector symbols without emoji/font dependencies; vibrate and mute are actual drawings. */
    private static final class SymbolIcon extends View {
        static final int AIRPLANE=1, ALARM=2, BLUETOOTH=3, VIBRATE=4, MUTE=5, NFC=6, VPN=7, DND=8;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int shape;
        private int color = 0xFFCE93D8;
        SymbolIcon(Context context, int s) { super(context); shape=s; }
        void setShape(int s) { if (shape != s) { shape=s; invalidate(); } }
        boolean isShape(int s) { return shape == s; }
        void setColor(int c) { if (color != c) { color=c; invalidate(); } }
        private void segment(Canvas c, float x1, float y1, float x2, float y2) {
            c.drawLine(x1, y1, x2, y2, paint);
        }
        private void path(Canvas c, float... points) {
            Path p = new Path();
            p.moveTo(points[0], points[1]);
            for (int i=2; i<points.length; i+=2) p.lineTo(points[i], points[i+1]);
            c.drawPath(p, paint);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (getWidth() <= 0 || getHeight() <= 0) return;
            c.save(); c.scale(getWidth()/24f, getHeight()/24f);
            paint.setColor(color); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            switch (shape) {
                case AIRPLANE:
                    path(c, 3,11, 10,13, 20,3, 22,5, 13,14, 15,21, 12,19, 10,15, 5,13, 3,11);
                    break;
                case ALARM:
                    c.drawCircle(12,13,7,paint);
                    segment(c,12,13,12,8); segment(c,12,13,15,15);
                    path(c,4,6,7,3,10,4); path(c,14,4,17,3,20,6);
                    segment(c,7,21,5,23); segment(c,17,21,19,23);
                    break;
                case BLUETOOTH:
                    path(c,12,2,18,7,7,17); path(c,12,22,18,17,7,7);
                    segment(c,12,2,12,22);
                    break;
                case VIBRATE:
                    c.drawRoundRect(7,4,17,20,2,2,paint);
                    segment(c,10,17,14,17);
                    path(c,4,7,2,10,4,13); path(c,20,7,22,10,20,13);
                    segment(c,4,17,2,19); segment(c,20,17,22,19);
                    break;
                case MUTE:
                    path(c,3,10,7,10,12,5,12,19,7,14,3,14,3,10);
                    segment(c,16,8,22,16); segment(c,22,8,16,16);
                    break;
                case NFC:
                    c.drawRoundRect(4,4,20,20,2,2,paint);
                    path(c,7,17,7,7,17,17,17,7);
                    break;
                case VPN:
                    c.drawCircle(8,12,4,paint); segment(c,12,12,22,12);
                    segment(c,17,12,17,16); segment(c,20,12,20,15);
                    break;
                case DND:
                    c.drawCircle(12,12,9,paint); segment(c,7,12,17,12);
                    break;
            }
            c.restore();
        }
    }
}

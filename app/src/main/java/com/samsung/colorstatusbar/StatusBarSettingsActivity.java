package com.samsung.colorstatusbar;

import android.Manifest;
import android.app.Activity;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.view.accessibility.AccessibilityManager;
import java.util.List;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

/** V2 full settings: one-line date, live icons, editable palette and rainbow animation. */
public class StatusBarSettingsActivity extends Activity {
    private LinearLayout root;
    private TextView state;
    private SharedPreferences settings;
    private Spinner colorTarget;
    private EditText hexField;
    private View colorPreview;
    private CheckBox colorItemRainbow;
    private boolean updatingColorSelection;

    // The original V3 color keys (0-8) remain unchanged, so existing settings
    // are preserved when upgrading. Each additional symbol has its own key.
    static final String[] COLOR_KEYS = {
        "background_color", "time_color", "seconds_color", "date_color",
        "wifi_color", "cell_color", "network_color", "battery_color", "other_color",
        "weekday_color", "battery_number_color", "battery_fill_color",
        "airplane_color", "alarm_color", "bluetooth_color", "vibrate_color",
        "silent_color", "nfc_color", "vpn_color", "dnd_color"
    };
    static final String[] COLOR_NAMES = {
        "狀態列背景", "時間（時、分）", "秒數", "日期（年月日）",
        "Wi-Fi 訊號格數", "手機訊號格數", "4G／5G 文字", "電池外框", "其他圖標舊版預設色",
        "星期幾", "電池內百分比數字", "電池電量填充",
        "飛航模式", "鬧鐘", "藍牙", "震動模式",
        "靜音模式", "NFC", "VPN", "勿擾模式"
    };
    static final int[] DEFAULT_COLORS = {
        0xFF242733, 0xFFFFD54F, 0xFFFFD54F, 0xFFA5D6A7,
        0xFF4FC3F7, 0xFF81C784, 0xFF81C784, 0xFFFF8A80, 0xFFCE93D8,
        0xFFA5D6A7, 0xFFFFFFFF, 0xFFFF8A80,
        0xFFCE93D8, 0xFFCE93D8, 0xFFCE93D8, 0xFFCE93D8,
        0xFFCE93D8, 0xFFCE93D8, 0xFFCE93D8, 0xFFCE93D8
    };
    // Fall back to the user's V2/V3 colors until individual values are edited.
    static int getIndividualColor(SharedPreferences settings, int index) {
        String key = COLOR_KEYS[index];
        if (settings.contains(key)) return settings.getInt(key, DEFAULT_COLORS[index]);
        if (index == 9) return settings.getInt("date_color", DEFAULT_COLORS[3]);
        if (index == 11) return settings.getInt("battery_color", DEFAULT_COLORS[7]);
        if (index >= 12) return settings.getInt("other_color", DEFAULT_COLORS[8]);
        return DEFAULT_COLORS[index];
    }
    static final int[] SEVEN_COLORS = { 0xFFFF5252, 0xFFFF9800, 0xFFFFEB3B,
        0xFF4CAF50, 0xFF29B6F6, 0xFF536DFE, 0xFFCE5CFF };
    static final int[] PALETTE = { 0xFFFFFFFF, 0xFF222222, 0xFFFF5252, 0xFFFF9800,
        0xFFFFEB3B, 0xFF4CAF50, 0xFF00BCD4, 0xFF448AFF, 0xFF9575CD, 0xFFE040FB,
        0xFFFF80AB, 0xFFB0BEC5, 0xFFFFD54F, 0xFF81C784 };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = getSharedPreferences(StatusBarSettingsActivity.class.getName(), MODE_PRIVATE);
        SharedPreferences legacy = getPreferences(MODE_PRIVATE);
        if (!settings.contains("left") && legacy.contains("left")) {
            settings.edit().putInt("left", legacy.getInt("left", 12))
                .putInt("right", legacy.getInt("right", 12))
                .putInt("height", legacy.getInt("height", 34)).apply();
        }
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(30));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        heading("三星彩色狀態列 V3｜畫面穩定修正版", 22);
        note("無障礙浮動列可顯示在頂部，但是否能完整覆蓋原生列、與第三方邊緣燈的前後層次，仍取決於三星系統。這項服務不讀取其他 App 的畫面文字。");
        note("第一次使用：按『啟動／更新』後，到系統『已安裝的應用程式／無障礙』啟用『三星彩色狀態列 V3』。啟用後會自動出現；再回此頁調整樣式。");

        heading("位置、大小與間距", 18);
        addSlider("left", "左側邊距", 12, 0, 80, " dp");
        addSlider("right", "右側邊距", 12, 0, 80, " dp");
        addSlider("height", "狀態列高度", 44, 24, 90, " dp");
        addSlider("top_edge_gap", "頂部七彩燈保留空間", 4, 0, 16, " dp");
        addSlider("icon_spacing", "各圖標間距", 3, 0, 15, " dp");
        addSlider("battery_spacing", "訊號和電池的額外間距", 10, 0, 30, " dp");

        heading("日期、星期與時間（同一排）", 18);
        addCheckBox("show_date", "顯示年月日（時間右邊）", true);
        addCheckBox("show_weekday", "顯示星期幾（時間右邊）", true);
        addCheckBox("show_seconds", "顯示秒數", true);
        addCheckBox("short_date", "固定使用精簡日期", false);
        addCheckBox("auto_compact_date", "空間不足時自動縮短年月日", true);
        addSlider("date_text_size", "日期／星期字體", 10, 8, 22, " sp");
        addSlider("time_text_size", "時分字體", 16, 10, 30, " sp");
        addSlider("seconds_text_size", "秒數字體", 13, 8, 26, " sp");
        addSlider("date_spacing", "時間與日期間距", 8, 0, 24, " dp");

        heading("訊號、電池和其他圖標", 18);
        addCheckBox("show_wifi", "顯示 Wi-Fi 格數（僅連線時）", true);
        addCheckBox("show_cell", "顯示手機訊號與 4G／5G", true);
        addCheckBox("show_battery", "顯示三星風格電池（數字在電池內）", true);
        addCheckBox("show_bluetooth", "顯示藍牙（開啟時）", true);
        addCheckBox("show_alarm", "顯示鬧鐘（有設定鬧鐘時）", true);
        addCheckBox("show_sound", "顯示震動／靜音圖示", true);
        addCheckBox("show_airplane", "顯示飛航模式", true);
        addCheckBox("show_nfc", "顯示 NFC（預設隱藏 N）", false);
        addCheckBox("show_vpn", "顯示 VPN", true);
        addCheckBox("show_dnd", "顯示勿擾模式", true);
        addSlider("wifi_size", "Wi-Fi 圖標大小", 20, 12, 36, " dp");
        addSlider("cell_size", "手機訊號圖標大小", 20, 12, 36, " dp");
        addSlider("network_text_size", "4G／5G 文字大小", 12, 8, 22, " sp");
        heading("V4.31 TEST7｜20 款電池樣式", 18);
        note("此階段為程式繪製的辨識版，尚非 20 款參考照片的精緻原圖；電量百分比會即時更新。");
        final String[] batteryStyles = {
            "01 極光波浪", "02 V4.30 原版電池", "03 RGB 五色", "04 數字電量光環",
            "05 金色立體", "06 透明低電量", "07 藍色閃電", "08 藍色電漿閃電",
            "09 七彩透明圓柱", "10 藍色機械能量", "11 經典綠色玻璃", "12 藍色漸層金屬",
            "13 鈦銀藍光圓柱", "14 七彩氣泡液態", "15 紫藍霓虹透明", "16 藍色氣泡科技",
            "17 RGB 七彩霓虹雙環", "18 冰藍金屬方形 100%", "19 冰藍金屬圓柱 100%", "20 銀黑金屬綠光數位 100%"
        };
        Spinner batteryStyleSpinner = new Spinner(this);
        ArrayAdapter<String> batteryStyleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, batteryStyles);
        batteryStyleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        batteryStyleSpinner.setAdapter(batteryStyleAdapter);
        batteryStyleSpinner.setSelection(Math.max(0, Math.min(19, settings.getInt("battery_style", 0))));
        batteryStyleSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (settings.getInt("battery_style", 0) != position)
                    settings.edit().putInt("battery_style", position).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        root.addView(batteryStyleSpinner);
        heading("V4.31｜內外螢幕電池大小與位置", 18);
        addSlider("battery_size_outer", "外螢幕電池寬度（拉長／縮短）", 40, 28, 90, " dp");
        addSlider("battery_height_outer", "外螢幕電池高度（拉高／壓扁）", 20, 16, 60, " dp");
        addSlider("battery_x_outer", "外螢幕左右（100=原位）", 100, 0, 200, " dp");
        addSlider("battery_y_outer", "外螢幕上下（60=原位）", 60, 0, 120, " dp");
        addSlider("battery_size_inner", "內螢幕電池寬度（拉長／縮短）", 40, 28, 90, " dp");
        addSlider("battery_height_inner", "內螢幕電池高度（拉高／壓扁）", 20, 16, 60, " dp");
        addSlider("battery_x_inner", "內螢幕左右（100=原位）", 100, 0, 200, " dp");
        addSlider("battery_y_inner", "內螢幕上下（60=原位）", 60, 0, 120, " dp");
        addSlider("battery_text_size", "電池內百分比字體", 11, 7, 22, " sp");
        heading("V4.31 TEST1｜電池獨立設定", 18);
        addCheckBox("battery_follow_rainbow", "電池跟隨狀態列七彩（關閉時保持自訂原色）", false);
        addSlider("battery_opacity", "電池透明度", 100, 20, 100, "%");
        note("寬度和高度可獨立調整電池形狀；左右／上下位置則只移動圖標。數字字體獨立設定，超出圖案時會限制大小以避免裁切。");
        addSlider("other_icon_size", "藍牙／鬧鐘／震動等圖標大小", 19, 12, 34, " dp");

        heading("背景與透明度", 18);
        addSlider("background_opacity", "背景不透明度（0=完全透明）", 76, 0, 100, "%");
        addSlider("corner_radius", "狀態列圓角", 8, 0, 25, " dp");

        heading("自訂色盤", 18);
        note("先選時間、日期、星期、訊號、電池或各個圖標，再點色塊／輸入 #RRGGBB 儲存。每項都有自己的顏色；更改後會立即更新。若開啟下方的『全部圖標七彩』，會暫時蓋過單獨選色，但不會刪除你的顏色。");
        colorTarget = new Spinner(this);
        ArrayAdapter<String> items = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, COLOR_NAMES);
        colorTarget.setAdapter(items);
        root.addView(colorTarget);
        colorTarget.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (hexField != null) {
                    updatingColorSelection = true;
                    hexField.setText(String.format(java.util.Locale.US, "#%06X",
                        getIndividualColor(settings, position) & 0xFFFFFF));
                    if (colorItemRainbow != null)
                        colorItemRainbow.setChecked(settings.getBoolean("rainbow_item_" + COLOR_KEYS[position], false));
                    updatingColorSelection = false;
                    refreshPreview();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        LinearLayout palette = new LinearLayout(this);
        palette.setOrientation(LinearLayout.VERTICAL);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 7; col++) {
                final int chosenColor = PALETTE[row * 7 + col];
                Button swatch = new Button(this);
                GradientDrawable shape = new GradientDrawable();
                shape.setColor(chosenColor);
                shape.setCornerRadius(dp(9));
                shape.setStroke(dp(1), 0xFF9E9E9E);
                swatch.setBackground(shape);
                LinearLayout.LayoutParams sw = new LinearLayout.LayoutParams(0, dp(38), 1);
                sw.setMargins(dp(2), dp(3), dp(2), dp(3));
                line.addView(swatch, sw);
                swatch.setOnClickListener(v -> applyChosenColor(chosenColor));
            }
            palette.addView(line);
        }
        root.addView(palette);
        LinearLayout custom = new LinearLayout(this);
        custom.setOrientation(LinearLayout.HORIZONTAL);
        custom.setGravity(Gravity.CENTER_VERTICAL);
        hexField = new EditText(this);
        hexField.setSingleLine(true);
        hexField.setText("#242733");
        hexField.setTextSize(16);
        hexField.setInputType(InputType.TYPE_CLASS_TEXT);
        custom.addView(hexField, new LinearLayout.LayoutParams(0, dp(55), 1));
        Button applyHex = new Button(this);
        applyHex.setText("套用顏色");
        custom.addView(applyHex);
        applyHex.setOnClickListener(v -> {
            try { applyChosenColor(Color.parseColor(hexField.getText().toString().trim())); }
            catch (IllegalArgumentException error) { Toast.makeText(this, "請輸入有效色碼，例如 #FF8800", Toast.LENGTH_SHORT).show(); }
        });
        root.addView(custom);
        colorPreview = new View(this);
        root.addView(colorPreview, new LinearLayout.LayoutParams(-1, dp(16)));
        refreshPreview();
        colorItemRainbow = new CheckBox(this);
        colorItemRainbow.setText("只有目前選取的項目使用七彩變色");
        colorItemRainbow.setTextColor(Color.BLACK);
        colorItemRainbow.setTextSize(15);
        root.addView(colorItemRainbow);
        colorItemRainbow.setOnCheckedChangeListener((button, checked) -> {
            if (updatingColorSelection) return;
            int at = colorTarget.getSelectedItemPosition();
            if (at < 0 || at >= COLOR_KEYS.length) return;
            settings.edit().putBoolean("rainbow_item_" + COLOR_KEYS[at], checked).apply();
        });
        int currentTarget = colorTarget.getSelectedItemPosition();
        if (currentTarget < 0 || currentTarget >= COLOR_KEYS.length) currentTarget = 0;
        colorItemRainbow.setChecked(settings.getBoolean("rainbow_item_" + COLOR_KEYS[currentTarget], false));

        heading("七彩自動變色（七色可自訂）", 18);
        addCheckBox("rainbow_enabled", "全部時間、日期、星期與圖標七彩循環變色（覆蓋個別靜態色）", false);
        addCheckBox("rainbow_bg", "背景也七彩循環變色", false);
        addSlider("rainbow_period", "七彩循環週期", 7, 2, 30, " 秒");
        note("以下七個色碼是七彩循環的顏色；修改後按右邊的儲存即可套用。");
        for (int i = 0; i < 7; i++) addRainbowEditor(i);

        Button start = new Button(this);
        start.setText("啟動／更新彩色狀態列");
        root.addView(start);
        start.setOnClickListener(v -> {
            settings.edit().putBoolean("enabled", true).apply();
            requestOptionalPermissions();
            if (!isAccessibilityEnabled()) {
                state.setText("請在無障礙設定裡啟用『三星彩色狀態列 V3』。啟用後會自動顯示。若系統提示受限制，請先查看 App 資訊的安全設定。");
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            updateOverlay();
            state.setText("已要求更新無障礙彩色狀態列。");
        });
        Button stop = new Button(this);
        stop.setText("停止彩色狀態列");
        root.addView(stop);
        stop.setOnClickListener(v -> {
            settings.edit().putBoolean("enabled", false).apply();
            updateOverlay();
            state.setText("已停止彩色列畫面。如不再使用，可至系統無障礙設定關閉此服務。");
        });
        state = label("狀態：等待啟動。若訊號或藍牙資料未顯示，請確認權限與系統限制。", 14);
        root.addView(state);
        setContentView(scroll);
    }

    private void addRainbowEditor(int index) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label("第 " + (index + 1) + " 色", 14);
        line.addView(name);
        EditText edit = new EditText(this);
        int current = settings.getInt("rainbow_" + index, SEVEN_COLORS[index]);
        edit.setSingleLine(true);
        edit.setText(String.format(java.util.Locale.US, "#%06X", current & 0xFFFFFF));
        edit.setTextSize(14);
        line.addView(edit, new LinearLayout.LayoutParams(0, dp(49), 1));
        Button save = new Button(this);
        save.setText("儲存");
        line.addView(save);
        save.setOnClickListener(v -> {
            try {
                int color = Color.parseColor(edit.getText().toString().trim());
                settings.edit().putInt("rainbow_" + index, 0xFF000000 | (color & 0xFFFFFF)).apply();
                Toast.makeText(this, "第 " + (index + 1) + " 色已儲存", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) {
                Toast.makeText(this, "色碼格式錯誤，範例 #FF0000", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(line);
    }

    private void applyChosenColor(int color) {
        int at = colorTarget.getSelectedItemPosition();
        if (at < 0 || at >= COLOR_KEYS.length) return;
        color = 0xFF000000 | (color & 0xFFFFFF);
        settings.edit().putInt(COLOR_KEYS[at], color).apply();
        hexField.setText(String.format(java.util.Locale.US, "#%06X", color & 0xFFFFFF));
        refreshPreview();
    }

    private void refreshPreview() {
        if (colorPreview == null || colorTarget == null) return;
        int at = colorTarget.getSelectedItemPosition();
        if (at < 0 || at >= COLOR_KEYS.length) at = 0;
        colorPreview.setBackgroundColor(getIndividualColor(settings, at));
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String value, int sp) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextColor(Color.BLACK); t.setTextSize(sp);
        t.setPadding(0, dp(8), 0, dp(5));
        return t;
    }
    private void heading(String value, int sp) { root.addView(label(value, sp)); }
    private void note(String value) { root.addView(label(value, 13)); }

    private void addCheckBox(String key, String text, boolean def) {
        CheckBox box = new CheckBox(this);
        box.setText(text); box.setTextColor(Color.BLACK); box.setTextSize(15);
        box.setChecked(settings.getBoolean(key, def));
        root.addView(box);
        box.setOnCheckedChangeListener((button, checked) -> {
            settings.edit().putBoolean(key, checked).apply();
        });
    }

    private void addSlider(String key, String title, int def, int min, int max, String unit) {
        int initial = Math.max(min, Math.min(max, settings.getInt(key, def)));
        TextView caption = label(title + "：" + initial + unit, 15);
        root.addView(caption);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min); bar.setProgress(initial - min);
        root.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + min;
                caption.setText(title + "：" + value + unit);
                // Only persist at the end of dragging. Writing every step caused
                // the accessibility overlay to be repeatedly removed and recreated.
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                settings.edit().putInt(key, seekBar.getProgress() + min).apply();
            }
        });
    }

    private void updateOverlayIfEnabled() {
        if (settings.getBoolean("enabled", false) && isAccessibilityEnabled()) updateOverlay();
    }
    private void updateOverlay() {
        // A connected AccessibilityService receives SharedPreferences callbacks.
        // This token also forces a rebuild when the user taps "update" without edits.
        settings.edit().putLong("refresh_token", System.nanoTime()).apply();
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        ComponentName component = new ComponentName(this, OverlayService.class);
        String wanted = component.flattenToString();
        String shortName = component.flattenToShortString();
        List<AccessibilityServiceInfo> enabled = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabled) {
            if (wanted.equals(info.getId()) || shortName.equals(info.getId())
                || component.equals(ComponentName.unflattenFromString(info.getId()))) return true;
        }
        return false;
    }

    private void requestOptionalPermissions() {
        ArrayList<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), 102);
    }
}

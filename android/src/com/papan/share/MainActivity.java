package com.papan.share;

import android.app.Dialog;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.appcompat.view.ContextThemeWrapper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigationrail.NavigationRailView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.RadioGroup;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.Locale;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends AppCompatActivity {
    private int TEXT, MUTED, ACCENT;
    private LinearLayout root, board;
    private TabLayout tabs;
    private MaterialToolbar toolbar;
    private NavigationBarView navigation;
    private FloatingActionButton addLink;
    private TextInputLayout searchLayout;
    private RecyclerView pinGrid;
    private ScrollView boardScroll;
    private String gridKey = "";
    private int gridAnchor = -1, gridAnchorOffset;
    private boolean loadingMore;
    private TextView status, heading;
    private EditText search, pairLink, desktopAddress;
    private Dialog saveDialog;
    private AlertDialog connectionsDialog, detailDialog, appearanceDialog;
    private VideoView video;
    private boolean remote, foreground, settingsOpen, activityOpen, updatingTabs;
    private String selectedLocal = "", selectedRemote = "", pendingLink, sharedId;
    private String pairingDraft = "", addressDraft = "";
    private JSONArray remoteCollections = new JSONArray(), remotePins = new JSONArray();
    private int nextOffset = -1, boardGeneration;
    private long pollUntil;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::sync;
    private final Runnable browsePoll = () -> { if (foreground && remote && detailDialog == null) loadDesktop(false, false); };
    private final Runnable detailPoll = this::checkDetailAccess;
    private final Runnable searchPoll = () -> { if (foreground && remote) loadDesktop(false, true); };
    private final Runnable resizeBoard = this::renderPins;
    private final ExecutorService requests = Executors.newSingleThreadExecutor(), images = Executors.newFixedThreadPool(3);
    private DesktopLibrary desktop;
    private SharedPreferences appearance;
    private interface Work { void run() throws Exception; }

    @Override public void onCreate(Bundle saved) {
        appearance = getSharedPreferences("board-appearance", MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(appearance.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        super.onCreate(saved); desktop = new DesktopLibrary(this);
        TEXT = getColor(R.color.papan_on_surface); MUTED = getColor(R.color.papan_on_surface_variant); ACCENT = getColor(R.color.papan_primary);
        selectedLocal = appearance.getString("selectedLocal", ""); selectedRemote = appearance.getString("selectedRemote", "");
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if (saved != null) { remote = saved.getBoolean("remote"); selectedLocal = saved.getString("local", ""); selectedRemote = saved.getString("desktop", ""); pendingLink = saved.getString("pendingLink"); sharedId = saved.getString("sharedId"); settingsOpen = saved.getBoolean("settingsOpen"); pairingDraft = saved.getString("pairingDraft", ""); addressDraft = saved.getString("addressDraft", ""); }
        activityOpen = saved != null && saved.getBoolean("activityOpen");
        buildLayout();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { if (searchLayout.isShown()) toggleSearch(); else if (remote || activityOpen) switchLocation(false); else { setEnabled(false); try { getOnBackPressedDispatcher().onBackPressed(); } finally { setEnabled(true); } } }
        });
        if (activityOpen) renderActivity(); else if (!remote) refreshLocal();
        if (saved == null) receive(getIntent()); else if (pendingLink != null) handler.post(() -> chooseDestination(pendingLink)); else if (settingsOpen) handler.post(this::connections);
    }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); receive(intent); }
    @Override public void onStart() { super.onStart(); foreground = true; pollUntil = SystemClock.elapsedRealtime() + 30000; sync(); capture(); if (remote) loadDesktop(false, true); }
    @Override public void onStop() {
        appearance.edit().putString("selectedLocal", selectedLocal).putString("selectedRemote", selectedRemote).apply();
        foreground = false; handler.removeCallbacks(poll); handler.removeCallbacks(browsePoll); handler.removeCallbacks(detailPoll); handler.removeCallbacks(searchPoll); boardGeneration++;
        if (video != null) { video.stopPlayback(); video = null; }
        if (detailDialog != null) detailDialog.dismiss();
        if (appearanceDialog != null) appearanceDialog.dismiss();
        remotePins = new JSONArray(); for (int i = 0; i < remoteCollections.length(); i++) { JSONObject value = remoteCollections.optJSONObject(i); if (value != null && value.optBoolean("protected")) try { value.put("locked", true); } catch (Exception ignored) {} }
        if (remote) { clearBoard(); board.addView(text("Desktop contents hidden while Papan is in the background.", 16)); }
        requests.execute(desktop::clear); super.onStop();
    }
    @Override public void onDestroy() { pinGrid.removeCallbacks(resizeBoard); requests.shutdown(); images.shutdownNow(); super.onDestroy(); }
    @Override public void onSaveInstanceState(Bundle state) { state.putBoolean("remote", remote); state.putBoolean("activityOpen", activityOpen); state.putString("local", selectedLocal); state.putString("desktop", selectedRemote); state.putString("pendingLink", pendingLink); state.putString("sharedId", sharedId); state.putBoolean("settingsOpen", settingsOpen); state.putString("pairingDraft", pairLink == null ? pairingDraft : pairLink.getText().toString()); state.putString("addressDraft", desktopAddress == null ? addressDraft : desktopAddress.getText().toString()); super.onSaveInstanceState(state); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private void buildLayout() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        root = column(); root.setBackgroundColor(getColor(R.color.papan_background)); setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        toolbar = new MaterialToolbar(this); toolbar.setTitle("papan."); toolbar.setTitleTextAppearance(this, R.style.PapanBrand); toolbar.setBackgroundColor(getColor(R.color.papan_background)); root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        toolbar.getMenu().add(0, 107, 0, "Search").setIcon(R.drawable.search).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.getMenu().add(0, 108, 1, "Board appearance").setIcon(R.drawable.tune).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        for (int action : new int[] {107, 108}) toolbar.getMenu().findItem(action).setIconTintList(ColorStateList.valueOf(MUTED));
        toolbar.getMenu().add(0, 101, 2, "Paste link");
        toolbar.getMenu().add(0, 102, 1, "Connections");
        toolbar.getMenu().add(0, 103, 2, "New collection");
        toolbar.getMenu().add(0, 104, 3, "Rename collection");
        toolbar.getMenu().add(0, 105, 4, "Delete collection");
        toolbar.getMenu().add(0, 106, 5, "Refresh desktop");
        toolbar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 101: chooseDestination(clipboardText()); break;
                case 102: connections(); break;
                case 103: newCollection(null); break;
                case 104: renameCollection(); break;
                case 105: deleteCollection(); break;
                case 106: loadDesktop(false, true); break;
                case 107: toggleSearch(); break;
                case 108: boardAppearance(); break;
                default: return false;
            }
            return true;
        });
        boolean wide = getResources().getConfiguration().screenWidthDp >= 600;
        LinearLayout middle = row(); middle.setGravity(Gravity.TOP); root.addView(middle, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = wide ? new NavigationRailView(this) : new BottomNavigationView(this);
        navigation.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        navigation.getMenu().add(0, 1, 0, "On phone").setIcon(R.drawable.phone);
        navigation.getMenu().add(0, 2, 1, "Desktop").setIcon(R.drawable.desktop);
        navigation.getMenu().add(0, 3, 2, "Activity").setIcon(R.drawable.history);
        ColorStateList navigationColors = new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}}, new int[] {ACCENT, MUTED});
        navigation.setItemIconTintList(navigationColors); navigation.setItemTextColor(navigationColors); navigation.setBackgroundColor(getColor(R.color.papan_surface)); navigation.setElevation(0);
        if (wide) middle.addView(navigation, new LinearLayout.LayoutParams(dp(getResources().getConfiguration().fontScale > 1.5f ? 96 : 80), -1));
        LinearLayout content = column(); middle.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        tabs = new TabLayout(this); tabs.setBackgroundColor(getColor(R.color.papan_background)); tabs.setTabMode(TabLayout.MODE_SCROLLABLE); tabs.setTabGravity(TabLayout.GRAVITY_START); tabs.setInlineLabel(true); content.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { if (updatingTabs || tab.getTag() == null) return; String id = tab.getTag().toString(); search.setText(""); if (remote) { selectedRemote = id; loadDesktop(true, true); } else { selectedLocal = id; refreshLocal(); } }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        heading = text("", 14); heading.setTextColor(MUTED); heading.setPadding(dp(16), dp(8), dp(16), dp(8)); heading.setVisibility(View.GONE); content.addView(heading);
        search = field("Search this collection", false); search.setContentDescription("Search this collection"); search.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(500) }); searchLayout = fieldLayout(search);
        searchLayout.setVisibility(View.GONE);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2); searchParams.setMargins(dp(16), 0, dp(16), dp(8)); content.addView(searchLayout, searchParams);
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence value,int start,int count,int after) {} public void onTextChanged(CharSequence value,int start,int before,int count) { if (activityOpen) return; if (remote) { boardGeneration++; handler.removeCallbacks(searchPoll); handler.postDelayed(searchPoll, 250); } else renderPins(); } public void afterTextChanged(Editable value) {} });
        FrameLayout canvas = new FrameLayout(this); content.addView(canvas, new LinearLayout.LayoutParams(-1, 0, 1));
        boardScroll = new ScrollView(this); boardScroll.setFillViewport(true); board = column(); board.setPadding(dp(16), dp(4), dp(16), dp(104)); boardScroll.addView(board); canvas.addView(boardScroll, new FrameLayout.LayoutParams(-1, -1));
        pinGrid = new RecyclerView(this); pinGrid.setPadding(dp(10), dp(4), dp(10), dp(88)); pinGrid.setClipToPadding(false); pinGrid.setHasFixedSize(true); pinGrid.setItemAnimator(null); pinGrid.setVisibility(View.GONE); canvas.addView(pinGrid, new FrameLayout.LayoutParams(-1, -1));
        pinGrid.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> { if (right - left != oldRight - oldLeft && pinGrid.getAdapter() != null) pinGrid.post(this::renderPins); });
        addLink = new FloatingActionButton(this); addLink.setImageResource(R.drawable.add); addLink.setBackgroundTintList(ColorStateList.valueOf(ACCENT)); addLink.setImageTintList(ColorStateList.valueOf(getColor(R.color.papan_on_primary))); addLink.setContentDescription("Add link"); addLink.setOnClickListener(v -> enterLink());
        FrameLayout.LayoutParams fab = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END); fab.setMargins(dp(16), dp(16), dp(16), dp(16)); canvas.addView(addLink, fab);
        status = text("", 14); status.setVisibility(View.GONE); root.addView(status);
        if (!wide) root.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        navigation.setSelectedItemId(activityOpen ? 3 : remote ? 2 : 1);
        navigation.setOnItemSelectedListener(item -> { showDestination(item.getItemId()); return true; });
        updateChrome();
    }
    private TextView text(String value, int size) { TextView view = new TextView(this); view.setText(value); view.setTextColor(TEXT); view.setTextSize(size); view.setPadding(0, dp(4), 0, dp(4)); return view; }
    private Button button(String label, Runnable action, boolean primary) {
        MaterialButton view = new MaterialButton(primary ? this : new ContextThemeWrapper(this, R.style.PapanTextButtonOverlay));
        view.setText(label); view.setAllCaps(false); view.setMinHeight(dp(48)); view.setMinimumHeight(dp(48)); view.setOnClickListener(v -> action.run()); return view;
    }
    private EditText field(String hint, boolean multiline) {
        TextInputLayout layout = new TextInputLayout(this); layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE); layout.setHint(hint);
        TextInputEditText value = new TextInputEditText(layout.getContext()); value.setTextSize(16); value.setMinHeight(dp(56)); value.setSingleLine(!multiline);
        value.setInputType(android.text.InputType.TYPE_CLASS_TEXT | (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
        value.setTag(layout); layout.addView(value, new LinearLayout.LayoutParams(-1, -2));
        value.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence v,int s,int c,int a) {} public void onTextChanged(CharSequence v,int s,int b,int c) { layout.setError(null); } public void afterTextChanged(Editable v) {} }); return value;
    }
    private TextInputLayout fieldLayout(EditText value) { return (TextInputLayout)value.getTag(); }
    private void addField(LinearLayout body, EditText value) { LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2); size.setMargins(0, dp(8), 0, dp(8)); body.addView(fieldLayout(value), size); }
    private void error(EditText value, String message) { fieldLayout(value).setError(message); }
    private LinearLayout dialogBody() { LinearLayout value = column(); value.setPadding(dp(24), dp(8), dp(24), dp(24)); return value; }
    private ScrollView scrolling(LinearLayout body) { ScrollView scroll = new ScrollView(this); scroll.addView(body); return scroll; }
    private AlertDialog dialog(String title, LinearLayout body) { ScrollView scroll = new ScrollView(this); scroll.addView(body); AlertDialog value = new MaterialAlertDialogBuilder(this).setTitle(title).setView(scroll).setNegativeButton("Close", null).create(); value.show(); value.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); return value; }
    private Dialog destinationSheet(LinearLayout body) {
        BottomSheetDialog value = new BottomSheetDialog(this);
        LinearLayout content = column(); LinearLayout title = row(); title.setPadding(dp(24), dp(8), dp(16), dp(4));
        TextView label = text("Save link", 24); title.addView(label, new LinearLayout.LayoutParams(0, -2, 1)); title.addView(button("Cancel", value::dismiss, false)); content.addView(title);
        ScrollView scroll = new ScrollView(this); scroll.addView(body); content.addView(scroll); value.setContentView(content);
        value.setOnShowListener(d -> { value.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED); value.getBehavior().setMaxHeight((int)(getResources().getDisplayMetrics().heightPixels * .9)); }); value.show(); return value;
    }
    private void message(String value) {
        String message = value == null ? "Could not complete the operation." : value;
        if (status == null || message.contentEquals(status.getText())) return;
        status.setText(message);
        if (!message.isEmpty() && foreground) {
            Snackbar bar = Snackbar.make(root, message, Snackbar.LENGTH_LONG);
            if (addLink.isShown()) bar.setAnchorView(addLink);
            else if (navigation instanceof BottomNavigationView) bar.setAnchorView(navigation);
            bar.show();
        }
    }
    private void async(Work work, Runnable complete) { requests.execute(() -> { try { work.run(); runOnUiThread(() -> { if (!isDestroyed()) complete.run(); }); } catch (Exception error) { runOnUiThread(() -> { if (!isDestroyed()) message(error.getMessage()); }); } }); }
    private String clipboardText() { ClipData data = ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).getPrimaryClip(); if (data == null || data.getItemCount() == 0) return null; ClipData.Item value = data.getItemAt(0); return value.getText() != null ? value.getText().toString() : value.getUri() != null ? value.getUri().toString() : null; }
    private void receive(Intent intent) { if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) chooseDestination(intent.getStringExtra(Intent.EXTRA_TEXT)); else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) pair(intent.getData().toString()); setIntent(new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)); }
    private void switchLocation(boolean desktopLocation) { int id = desktopLocation ? 2 : 1; if (navigation.getSelectedItemId() == id) showDestination(id); else navigation.setSelectedItemId(id); }
    private void showDestination(int id) {
        remote = id == 2; activityOpen = id == 3; boardGeneration++; gridKey = ""; handler.removeCallbacks(browsePoll); handler.removeCallbacks(searchPoll); search.setText(""); updateChrome();
        if (activityOpen) { getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE); heading.setText("Desktop delivery activity"); renderActivity(); }
        else if (remote) loadDesktop(false, true); else refreshLocal();
    }
    private void updateChrome() {
        tabs.setVisibility(activityOpen ? View.GONE : View.VISIBLE); if (activityOpen) searchLayout.setVisibility(View.GONE); heading.setVisibility(activityOpen ? View.VISIBLE : View.GONE); addLink.setVisibility(activityOpen ? View.GONE : View.VISIBLE);
        toolbar.getMenu().findItem(107).setVisible(!activityOpen);
        toolbar.getMenu().findItem(108).setVisible(!activityOpen);
        toolbar.getMenu().findItem(103).setVisible(!remote && !activityOpen);
        toolbar.getMenu().findItem(104).setVisible(!remote && !activityOpen && !selectedLocal.isEmpty());
        toolbar.getMenu().findItem(105).setVisible(!remote && !activityOpen && !selectedLocal.isEmpty());
        toolbar.getMenu().findItem(106).setVisible(remote);
    }
    private void toggleSearch() {
        boolean show = !searchLayout.isShown(); searchLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) search.requestFocus(); else { search.setText(""); search.clearFocus(); ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0); }
    }
    private String boardPreferenceKey() { return (remote ? "desktop:" + selectedRemote : "phone:" + selectedLocal); }
    private void boardAppearance() {
        final String key = boardPreferenceKey(); LinearLayout body = dialogBody();
        TextView sizeLabel = text("Preview size", 18); body.addView(sizeLabel);
        body.addView(text("Keep rows and columns aligned as previews get smaller or larger.", 14));
        Slider size = new Slider(this); size.setId(View.generateViewId()); size.setContentDescription("Preview size"); sizeLabel.setLabelFor(size.getId()); size.setValueFrom(100); size.setValueTo(300); size.setStepSize(20); size.setValue(appearance.getInt("size:" + key, 160));
        size.setLabelFormatter(value -> value <= 120 ? "Small" : value >= 240 ? "Large" : "Medium"); body.addView(size);
        LinearLayout ends = row(); TextView small = text("Smaller", 14), large = text("Larger", 14); small.setTextColor(MUTED); large.setTextColor(MUTED); large.setGravity(Gravity.END); ends.addView(small, new LinearLayout.LayoutParams(0, -2, 1)); ends.addView(large, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(ends);
        size.addOnChangeListener((slider, value, fromUser) -> { if (fromUser) { appearance.edit().putInt("size:" + key, Math.round(value)).apply(); pinGrid.removeCallbacks(resizeBoard); pinGrid.postOnAnimation(resizeBoard); } });
        MaterialSwitch fill = new MaterialSwitch(this); fill.setText("Fill image frames"); fill.setMinHeight(dp(48)); fill.setChecked(appearance.getBoolean("fill:" + key, true)); body.addView(fill); fill.setOnCheckedChangeListener((button, checked) -> { appearance.edit().putBoolean("fill:" + key, checked).apply(); renderPins(); });
        body.addView(text("Appearance", 18)); RadioGroup themes = new RadioGroup(this);
        int[] modes = {AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES}; String[] labels = {"Use phone setting", "Light", "Dark"};
        for (int i = 0; i < modes.length; i++) { MaterialRadioButton option = new MaterialRadioButton(this); option.setId(i + 201); option.setText(labels[i]); option.setMinHeight(dp(48)); themes.addView(option); if (appearance.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) == modes[i]) option.setChecked(true); }
        body.addView(themes); AlertDialog value = dialog("Board appearance", body); appearanceDialog = value;
        value.setOnDismissListener(d -> { if (appearanceDialog == value) appearanceDialog = null; });
        themes.setOnCheckedChangeListener((group, checked) -> { int mode = modes[checked - 201]; appearance.edit().putInt("theme", mode).apply(); value.dismiss(); AppCompatDelegate.setDefaultNightMode(mode); });
    }
    private void refreshLocal() {
        if (remote || activityOpen) return;
        try { JSONArray collections = MobileLibrary.get(this).snapshot().getJSONArray("collections"); if (find(collections, selectedLocal) == null) selectedLocal = collections.length() == 0 ? "" : collections.getJSONObject(0).getString("id"); renderTabs(collections, selectedLocal); updateChrome(); renderPins(); }
        catch (Exception error) { message(error.getMessage()); }
    }
    private JSONObject find(JSONArray values, String id) { for (int i = 0; i < values.length(); i++) { JSONObject value = values.optJSONObject(i); if (value != null && id.equals(value.optString("id"))) return value; } return null; }
    private void renderTabs(JSONArray collections, String selected) {
        updatingTabs = true; tabs.removeAllTabs();
        for (int i = 0; i < collections.length(); i++) {
            JSONObject value = collections.optJSONObject(i); if (value == null) continue; String id = value.optString("id");
            TabLayout.Tab tab = tabs.newTab().setText(value.optString("name")).setTag(id).setContentDescription(value.optString("name") + (value.optBoolean("protected") ? " · protected" : ""));
            if (value.optBoolean("protected")) tab.setIcon(android.R.drawable.ic_lock_lock);
            tabs.addTab(tab, id.equals(selected));
        }
        updatingTabs = false;
    }
    private void loadDesktop(boolean ask, boolean loading) {
        final int generation = ++boardGeneration, loadedCount = loading ? 40 : Math.max(40, remotePins.length()); final String query = search.getText().toString(); handler.removeCallbacks(browsePoll); handler.removeCallbacks(searchPoll);
        if (loading) { remotePins = new JSONArray(); loadingMore = false; gridKey = ""; clearBoard(); board.addView(text("Connecting to desktop…", 16)); }
        requests.execute(() -> {
            try {
                JSONArray collections = desktop.collections(); String chosen = selectedRemote; if (find(collections, chosen) == null) chosen = collections.length() == 0 ? "" : collections.getJSONObject(0).getString("id");
                JSONObject current = find(collections, chosen), page = current == null || current.optBoolean("locked") ? null : desktop.loadedPins(chosen, loadedCount, query); final String id = chosen;
                runOnUiThread(() -> { if (isDestroyed() || !foreground || !remote || generation != boardGeneration) return; JSONArray pins = page == null ? new JSONArray() : page.optJSONArray("pins"); if (pins == null) pins = new JSONArray(); boolean changed = !id.equals(selectedRemote) || !remoteCollections.toString().equals(collections.toString()) || !remotePins.toString().equals(pins.toString()); selectedRemote = id; remoteCollections = collections; remotePins = pins; nextOffset = page == null || page.isNull("nextOffset") ? -1 : page.optInt("nextOffset", -1); if (loading || changed) { renderTabs(collections, id); renderPins(); } if (ask && current != null && current.optBoolean("locked")) unlock(current, () -> loadDesktop(false, true)); handler.postDelayed(browsePoll, 5000); });
            } catch (Exception error) { runOnUiThread(() -> { if (isDestroyed() || !remote || generation != boardGeneration) return; remotePins = new JSONArray(); remoteCollections = new JSONArray(); tabs.removeAllTabs(); clearBoard(); heading.setText("Desktop unavailable"); board.addView(text(error.getMessage() == null ? "Desktop is unavailable." : error.getMessage(), 16)); board.addView(button("Try desktop again", () -> loadDesktop(false, true), false)); board.addView(button("Use phone collections", () -> switchLocation(false), false)); }); }
        });
    }
    private void morePins() {
        if (nextOffset < 0 || loadingMore) return;
        final int offset = nextOffset, generation = boardGeneration; final String id = selectedRemote, query = search.getText().toString();
        loadingMore = true; handler.removeCallbacks(browsePoll); renderPins();
        requests.execute(() -> {
            JSONObject result = null; String failure = null;
            try { result = desktop.pins(id, offset, query); } catch (Exception error) { failure = error.getMessage(); }
            final JSONObject page = result; final String problem = failure;
            runOnUiThread(() -> {
                loadingMore = false;
                if (isDestroyed() || !remote || generation != boardGeneration || !id.equals(selectedRemote)) return;
                if (page != null) {
                    JSONArray pins = page.optJSONArray("pins");
                    for (int i = 0; pins != null && i < pins.length(); i++) { JSONObject pin = pins.optJSONObject(i); if (pin != null && find(remotePins, pin.optString("id")) == null) remotePins.put(pin); }
                    nextOffset = page.isNull("nextOffset") ? -1 : page.optInt("nextOffset", -1);
                } else message(problem);
                renderPins(); if (foreground) handler.postDelayed(browsePoll, 5000);
            });
        });
    }
    private void clearBoard() { pinGrid.removeCallbacks(resizeBoard); pinGrid.setVisibility(View.GONE); pinGrid.setAdapter(null); boardScroll.setVisibility(View.VISIBLE); board.removeAllViews(); }
    private void renderPins() {
        if (board == null || activityOpen) return;
        try {
            JSONArray collections = remote ? remoteCollections : MobileLibrary.get(this).snapshot().getJSONArray("collections"); JSONObject collection = find(collections, remote ? selectedRemote : selectedLocal);
            if (remote && collection != null && collection.optBoolean("protected")) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            heading.setText(collection == null ? remote ? "Open collections on your desktop" : "Your phone library" : collection.optString("name") + (remote ? " · on desktop" : " · on phone"));
            if (collection == null) { clearBoard(); board.addView(text(remote ? "No collections are open on the desktop." : "Keep your references with you.", 22)); board.addView(text(remote ? "Open a collection in desktop Papan, then refresh." : "Create a collection, then paste or share a link. Your phone library works without the desktop.", 16)); board.addView(button(remote ? "Refresh desktop" : "Create collection", () -> { if (remote) loadDesktop(false, true); else newCollection(null); }, true)); return; }
            if (remote && collection.optBoolean("locked")) { clearBoard(); board.addView(text("This collection is protected.", 20)); board.addView(text("Enter its password to view or add references from this phone.", 16)); board.addView(button("Unlock collection", () -> unlock(collection, () -> loadDesktop(false, true)), true)); return; }
            JSONArray values = remote ? remotePins : MobileLibrary.get(this).snapshot().getJSONArray("pins"); String query = search.getText().toString().trim().toLowerCase(Locale.ROOT); ArrayList<JSONObject> pins = new ArrayList<>();
            float density = getResources().getDisplayMetrics().density;
            int width = pinGrid.getWidth() > 0 ? Math.round(pinGrid.getWidth() / density) : getResources().getConfiguration().screenWidthDp - (navigation instanceof NavigationRailView ? 80 : 0);
            int available = Math.max(1, width - 20), target = appearance.getInt("size:" + boardPreferenceKey(), 160), minimum = Math.round(112 * getResources().getConfiguration().fontScale);
            int columns = Math.max(1, Math.min(6, Math.min(Math.max(1, available / minimum), Math.round((available + 8f) / (target + 8f)))));
            if (getResources().getConfiguration().screenWidthDp < 340) columns = 1;
            for (int position = 0; position < values.length(); position++) { int i = remote ? position : values.length() - 1 - position; JSONObject pin = values.getJSONObject(i); if (!remote && !selectedLocal.equals(pin.optString("collectionId"))) continue; if (!remote && !query.isEmpty() && !(pin.optString("title") + " " + pin.optString("sourceUrl") + " " + pin.optString("notes")).toLowerCase(Locale.ROOT).contains(query)) continue;
                pins.add(pin);
            }
            if (pins.isEmpty()) { clearBoard(); board.addView(text(query.isEmpty() ? "This collection is empty." : "No matching pins.", 20)); board.addView(text(query.isEmpty() ? "Paste a link or share one to Papan to add your first reference." : "Try another title, source or note.", 16)); return; }
            String key = (remote ? "desktop:" + selectedRemote : "phone:" + selectedLocal) + ":" + query;
            GridLayoutManager previous = (GridLayoutManager)pinGrid.getLayoutManager(); int first = key.equals(gridKey) && previous != null ? previous.findFirstVisibleItemPosition() : -1;
            View anchor = first >= 0 ? previous.findViewByPosition(first) : null; int offset = anchor == null ? 0 : previous.getDecoratedTop(anchor) - pinGrid.getPaddingTop();
            // A second size event can arrive before the replacement layout has attached cards.
            if (anchor == null && key.equals(gridKey)) { first = gridAnchor; offset = gridAnchorOffset; }
            gridAnchor = first; gridAnchorOffset = offset;
            PinAdapter adapter = new PinAdapter(pins, remote, Math.max(48, (available - columns * 8) / columns)); GridLayoutManager layout = new GridLayoutManager(this, columns);
            final int spanCount = columns;
            layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() { @Override public int getSpanSize(int position) { return adapter.getItemViewType(position) == 1 ? spanCount : 1; } });
            pinGrid.setLayoutManager(layout); pinGrid.setAdapter(adapter); if (first >= 0) layout.scrollToPositionWithOffset(Math.min(first, adapter.getItemCount() - 1), offset); gridKey = key;
            boardScroll.setVisibility(View.GONE); pinGrid.setVisibility(View.VISIBLE);
        } catch (Exception error) { message(error.getMessage()); }
    }
    private final class PinAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final ArrayList<JSONObject> pins; private final boolean fromDesktop, more; private final int frameHeight;
        PinAdapter(ArrayList<JSONObject> values, boolean remote, int height) { pins = values; fromDesktop = remote; frameHeight = height; more = remote && nextOffset >= 0; }
        @Override public int getItemCount() { return pins.size() + (more ? 1 : 0); }
        @Override public int getItemViewType(int position) { return position == pins.size() ? 1 : 0; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            if (type == 1) { Button more = button(loadingMore ? "Loading pins…" : "Load more pins", MainActivity.this::morePins, false); more.setEnabled(!loadingMore); return new RecyclerView.ViewHolder(more) {}; }
            MaterialCardView card = new MaterialCardView(MainActivity.this); card.setRadius(dp(10)); card.setCardElevation(0); card.setCardBackgroundColor(getColor(R.color.papan_surface)); card.setPreventCornerOverlap(false); card.setStrokeWidth(dp(2)); card.setStrokeColor(new ColorStateList(new int[][] { {android.R.attr.state_focused}, {} }, new int[] { ACCENT, Color.TRANSPARENT })); card.setFocusable(true); card.setClickable(true);
            RecyclerView.LayoutParams size = new RecyclerView.LayoutParams(-1, -2); size.setMargins(dp(4), dp(4), dp(4), dp(4)); card.setLayoutParams(size);
            LinearLayout body = column(); card.addView(body); return new CardHolder(card, body);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (!(holder instanceof CardHolder)) { Button more = (Button)holder.itemView; more.setText(loadingMore ? "Loading pins…" : "Load more pins"); more.setEnabled(!loadingMore); return; }
            CardHolder card = (CardHolder)holder; JSONObject pin = pins.get(position); card.body.removeAllViews(); card.itemView.setContentDescription("Open pin: " + pin.optString("title")); card.itemView.setOnClickListener(v -> openPin(pin, fromDesktop));
            FrameLayout preview = new FrameLayout(MainActivity.this); preview.setBackgroundColor(getColor(R.color.papan_background)); card.body.addView(preview, new LinearLayout.LayoutParams(-1, dp(frameHeight)));
            String notice = !fromDesktop && "pending".equals(pin.optString("capture")) ? "Preparing preview…" : !fromDesktop && "failed".equals(pin.optString("capture")) ? "Preview unavailable" : null;
            try {
                JSONObject item = cover(pin, fromDesktop);
                if (item != null && "image".equals(item.optString("kind"))) preview.addView(imageFrame(pin, item, fromDesktop, appearance.getBoolean("fill:" + boardPreferenceKey(), true)), new FrameLayout.LayoutParams(-1, -1));
                else if (notice == null) { String excerpt = item == null ? pin.optString("notes") : item.optString("text"); TextView kind = text(excerpt.isEmpty() ? item != null && "video".equals(item.optString("kind")) ? "Video" : "Saved link" : excerpt, 16); kind.setTextColor(MUTED); kind.setPadding(dp(12), dp(10), dp(12), 0); kind.setEllipsize(android.text.TextUtils.TruncateAt.END); kind.setMaxLines(1); preview.addView(kind, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP)); }
            } catch (Exception error) { TextView failed = text("Preview unavailable", 14); failed.setPadding(dp(12), dp(10), dp(12), 0); preview.addView(failed); }
            LinearLayout overlay = column(), caption = column();
            overlay.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[] {Color.TRANSPARENT, 0xB3000000, 0xD9000000}));
            View spacing = new View(MainActivity.this); overlay.addView(spacing, new LinearLayout.LayoutParams(-1, dp(Math.round(50 * getResources().getConfiguration().fontScale))));
            caption.setPadding(dp(15), 0, dp(15), dp(16)); overlay.addView(caption);
            TextView title = text(pin.optString("title", pin.optString("sourceUrl")), 12); title.setTextColor(0xFFECECEA); title.setPadding(0, 0, 0, 0); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END); LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(-1, -2); titleSize.bottomMargin = dp(5); caption.addView(title, titleSize);
            String host; try { host = new java.net.URI(pin.optString("sourceUrl")).getHost(); } catch (Exception ignored) { host = null; }
            TextView source = text(host == null ? "Saved reference" : host.replaceFirst("^www\\.", ""), 10); source.setTextColor(0xFFAAAAAA); source.setPadding(0, 0, 0, 0); source.setMaxLines(1); source.setEllipsize(android.text.TextUtils.TruncateAt.END); caption.addView(source);
            preview.addView(overlay, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            View placeholder = preview.getChildAt(0); if (placeholder instanceof TextView) { placeholder.setBackgroundColor(getColor(R.color.papan_background)); preview.bringChildToFront(placeholder); }
            if (notice != null) { TextView state = text(notice, 12); state.setTextColor(MUTED); state.setBackgroundColor(getColor(R.color.papan_background)); state.setPadding(dp(10), dp(6), dp(10), dp(6)); state.setMaxLines(1); state.setEllipsize(android.text.TextUtils.TruncateAt.END); preview.addView(state, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP)); }
        }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder) { if (holder instanceof CardHolder) ((CardHolder)holder).body.removeAllViews(); }
    }
    private static final class CardHolder extends RecyclerView.ViewHolder {
        final LinearLayout body;
        CardHolder(MaterialCardView card, LinearLayout content) { super(card); body = content; }
    }
    private void addImage(LinearLayout body, JSONObject pin, JSONObject item, boolean fromDesktop, int height, boolean crop) {
        body.addView(imageFrame(pin, item, fromDesktop, crop), new LinearLayout.LayoutParams(-1, dp(height)));
    }
    private FrameLayout imageFrame(JSONObject pin, JSONObject item, boolean fromDesktop, boolean crop) {
        FrameLayout frame = new FrameLayout(this); frame.setBackgroundColor(getColor(R.color.papan_background)); ImageView image = new ImageView(this); image.setScaleType(crop ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER); image.setContentDescription(pin.optString("title")); frame.addView(image, new FrameLayout.LayoutParams(-1, -1)); TextView loading = text("Loading preview…", 13); loading.setTextColor(MUTED); loading.setGravity(Gravity.CENTER); frame.addView(loading, new FrameLayout.LayoutParams(-1, -1)); image.setTag(loading); loadImage(image, pin, item, fromDesktop); return frame;
    }
    private JSONObject cover(JSONObject pin, boolean fromDesktop) throws Exception {
        if (!fromDesktop) return pin.has("mediaFile") ? new JSONObject().put("kind", pin.optString("kind", "image")) : null;
        JSONArray items = pin.optJSONArray("items"); if (items == null) return null; JSONObject fallback = null;
        for (int i = 0; i < items.length(); i++) { JSONObject item = items.getJSONObject(i); if (item.optString("id").equals(pin.optString("coverId"))) return item; if (fallback == null && !"text".equals(item.optString("kind"))) fallback = item; } return fallback;
    }
    private void loadImage(ImageView view, JSONObject pin, JSONObject item, boolean fromDesktop) {
        images.execute(() -> {
            try {
                if (isDestroyed()) return;
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true; byte[] bytes = null; File file = null;
                if (fromDesktop) { bytes = desktop.media(item.getString("previewUrl")); BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options); }
                else { file = MobileLibrary.get(this).mediaFile(pin.optString("mediaFile")); if (file == null) return; BitmapFactory.decodeFile(file.toString(), options); }
                options.inSampleSize = 1; while (options.outWidth / options.inSampleSize > 1000 || options.outHeight / options.inSampleSize > 1000) options.inSampleSize *= 2; options.inJustDecodeBounds = false;
                Bitmap bitmap = fromDesktop ? BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options) : BitmapFactory.decodeFile(file.toString(), options);
                runOnUiThread(() -> { if (!isDestroyed() && view.isAttachedToWindow()) { view.setImageBitmap(bitmap); TextView loading = (TextView)view.getTag(); if (bitmap != null) loading.setVisibility(View.GONE); else loading.setText("Preview unavailable"); } else if (bitmap != null) bitmap.recycle(); });
            } catch (Exception ignored) { runOnUiThread(() -> { if (!isDestroyed() && view.isAttachedToWindow()) { view.setContentDescription("Preview unavailable. " + pin.optString("title")); ((TextView)view.getTag()).setText("Preview unavailable"); } }); }
        });
    }
    private void newCollection(Runnable after) { EditText name = field("Collection name", false); LinearLayout body = dialogBody(); addField(body, name); AlertDialog value = new MaterialAlertDialogBuilder(this).setTitle("New phone collection").setView(scrolling(body)).setNegativeButton("Cancel", null).setPositiveButton("Create", null).create(); value.show(); value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { try { selectedLocal = MobileLibrary.get(this).create(name.getText().toString()); value.dismiss(); if (after != null) after.run(); else switchLocation(false); } catch (Exception error) { error(name, error.getMessage()); } }); }
    private void deleteCollection() {
        if (selectedLocal.isEmpty()) return;
        new MaterialAlertDialogBuilder(this).setTitle("Delete this phone collection?").setMessage("Its pins and saved phone media will be removed. Desktop collections are separate.").setNegativeButton("Keep", null).setPositiveButton("Delete", (d,w) -> { try { MobileLibrary.get(this).removeCollection(selectedLocal); refreshLocal(); } catch (Exception error) { message(error.getMessage()); } }).show();
    }
    private void renameCollection() {
        try {
            JSONObject collection = find(MobileLibrary.get(this).snapshot().getJSONArray("collections"), selectedLocal);
            EditText name = field("Collection name", false); name.setText(collection.optString("name"));
            LinearLayout body = dialogBody(); addField(body, name);
            AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Rename collection").setView(scrolling(body)).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try { MobileLibrary.get(this).rename(selectedLocal, name.getText().toString()); dialog.dismiss(); refreshLocal(); }
                catch (Exception error) { error(name, error.getMessage()); }
            });
        } catch (Exception error) { message(error.getMessage()); }
    }
    private void enterLink() { EditText link = field("https://…", false); link.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); LinearLayout body = dialogBody(); addField(body, link); AlertDialog value = new MaterialAlertDialogBuilder(this).setTitle("Add a link").setView(scrolling(body)).setNegativeButton("Cancel", null).setPositiveButton("Choose collection", null).create(); value.show(); value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { try { String url = LocalCapture.webLink(link.getText().toString()); value.dismiss(); chooseDestination(url); } catch (Exception error) { error(link, error.getMessage()); } }); }
    private void chooseDestination(String input) {
        try {
            String url = LocalCapture.webLink(input); if (saveDialog != null) saveDialog.dismiss(); pendingLink = url;
            LinearLayout body = dialogBody(); TextView source = text(url, 14); source.setTextColor(MUTED); body.addView(source); body.addView(text("On phone", 18)); JSONArray local = MobileLibrary.get(this).snapshot().getJSONArray("collections");
            for (int i = 0; i < local.length(); i++) { JSONObject collection = local.getJSONObject(i); body.addView(button("On phone · " + collection.getString("name"), () -> saveLocal(url, collection.optString("id")), false)); }
            body.addView(button("+ New phone collection", () -> newCollection(() -> saveLocal(url, selectedLocal)), false));
            LinearLayout remoteChoices = column(); body.addView(remoteChoices); remoteChoices.addView(text("Desktop", 18)); TextView loading = text("Checking paired desktop…", 14); remoteChoices.addView(loading);
            saveDialog = destinationSheet(body); final Dialog current = saveDialog; current.setOnDismissListener(d -> { if (saveDialog == current) { pendingLink = null; saveDialog = null; } });
            requests.execute(() -> {
                JSONArray collections = null; String error = null; boolean legacy = false;
                try { if (desktop.supported()) collections = desktop.collections(); else { legacy = true; error = "Enable browsing in desktop Receive from phone, or update desktop Papan. Default delivery is still available."; } }
                catch (Exception failure) { error = failure.getMessage(); }
                final JSONArray choices = collections; final String problem = error; final boolean fallback = legacy;
                runOnUiThread(() -> {
                    if (isDestroyed() || saveDialog != current || !current.isShowing()) return; remoteChoices.removeAllViews(); remoteChoices.addView(text("Desktop", 18));
                    if (choices != null) { if (choices.length() == 0) remoteChoices.addView(text("Open a collection on the desktop to save there.", 14)); for (int i = 0; i < choices.length(); i++) { JSONObject collection = choices.optJSONObject(i); if (collection == null) continue; remoteChoices.addView(button("Desktop · " + collection.optString("name") + (collection.optBoolean("protected") ? " · protected" : ""), () -> { if (collection.optBoolean("locked")) unlock(collection, () -> saveDesktop(url, collection.optString("id"), collection.optString("name"))); else saveDesktop(url, collection.optString("id"), collection.optString("name")); }, false)); } }
                    else { remoteChoices.addView(text(problem == null ? "Desktop unavailable." : problem, 14)); try { if (PhoneStore.get(this).snapshot().has("config")) remoteChoices.addView(button(fallback ? "Desktop default collection" : "Queue for desktop default", () -> saveDesktop(url, "", "Desktop default"), false)); else { remoteChoices.addView(button("Queue for desktop after pairing", () -> saveDesktop(url, "", "Desktop default"), false)); remoteChoices.addView(button("Pair desktop", () -> { current.dismiss(); scanPair(); }, false)); } } catch (Exception failure) { message(failure.getMessage()); } }
                });
            });
        } catch (Exception error) { message(error.getMessage()); }
    }
    private void saveLocal(String url, String id) { try { MobileLibrary.get(this).add(id, url); selectedLocal = id; if (saveDialog != null) saveDialog.dismiss(); switchLocation(false); message("Saved on phone. Preparing preview…"); capture(); } catch (Exception error) { message(error.getMessage()); } }
    private void saveDesktop(String url, String id, String name) { try { sharedId = PhoneStore.get(this).enqueue(url, id, name); if (saveDialog != null) saveDialog.dismiss(); pollUntil = SystemClock.elapsedRealtime() + 30000; message("Queued for " + name + "."); Delivery.schedule(this); sync(); } catch (Exception error) { message(error.getMessage()); } }
    private void unlock(JSONObject collection, Runnable after) {
        EditText password = field("Collection password", false); password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); fieldLayout(password).setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE); LinearLayout body = dialogBody(); body.addView(text("Password for " + collection.optString("name") + ". Use a trusted local network; the desktop receiver uses HTTP.", 14)); addField(body, password);
        AlertDialog value = new MaterialAlertDialogBuilder(this).setTitle("Unlock desktop collection").setView(scrolling(body)).setNegativeButton("Cancel", null).setPositiveButton("Unlock", null).create(); value.show(); value.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { String secret = password.getText().toString(); if (secret.isEmpty()) { error(password, "Enter the password."); return; } value.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); password.setText(""); requests.execute(() -> { try { desktop.unlock(collection.optString("id"), secret); runOnUiThread(() -> { if (isDestroyed() || !value.isShowing()) return; value.dismiss(); after.run(); }); } catch (Exception error) { runOnUiThread(() -> { if (!isDestroyed() && value.isShowing()) { value.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); error(password, error.getMessage()); } }); } }); });
    }
    private void capture() { LocalCapture.submit(getApplicationContext(), error -> runOnUiThread(() -> { if (isDestroyed()) return; if (!remote) refreshLocal(); if (error != null) message(error.getMessage()); else if (status.getText().toString().startsWith("Saved on phone. Preparing")) message("Saved on phone."); })); }
    private void openPin(JSONObject pin, boolean fromDesktop) {
        try {
            handler.removeCallbacks(browsePoll); LinearLayout body = dialogBody(); body.addView(text(pin.optString("title"), 20));
            if (fromDesktop) { JSONArray items = pin.optJSONArray("items"); if (items != null) for (int i = 0; i < items.length(); i++) addMedia(body, pin, items.getJSONObject(i), true); }
            else { JSONObject item = cover(pin, false); if (item != null) addMedia(body, pin, item, false); if (!pin.optString("captureError").isEmpty()) { body.addView(text(pin.optString("captureError"), 14)); body.addView(button("Retry preview", () -> { try { MobileLibrary.get(this).retry(pin.getString("id")); if (detailDialog != null) detailDialog.dismiss(); capture(); } catch (Exception error) { message(error.getMessage()); } }, false)); } }
            if (!pin.optString("notes").isEmpty()) body.addView(text(pin.optString("notes"), 16)); if (!pin.optString("text").isEmpty()) body.addView(text(pin.optString("text"), 16));
            if (fromDesktop && pin.optBoolean("textTruncated")) body.addView(text("Text preview shortened for your phone. Open the source for the full page.", 13));
            TextView source = text(pin.optString("sourceUrl"), 14); source.setTextIsSelectable(true); source.setTextColor(MUTED); body.addView(source);
            body.addView(button("Open source", () -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LocalCapture.webLink(pin.optString("sourceUrl"))))); } catch (Exception error) { message("No app could open this link."); } }, true));
            if (!fromDesktop) { body.addView(button("Edit title and notes", () -> editPin(pin), false)); body.addView(button("Save to another collection", () -> { if (detailDialog != null) detailDialog.dismiss(); chooseDestination(pin.optString("sourceUrl")); }, false)); body.addView(button("Delete from phone", () -> new MaterialAlertDialogBuilder(this).setTitle("Delete this phone pin?").setMessage("Its saved phone media will also be removed.").setNegativeButton("Keep", null).setPositiveButton("Delete", (d,w) -> { try { MobileLibrary.get(this).removePin(pin.getString("id")); if (detailDialog != null) detailDialog.dismiss(); refreshLocal(); } catch (Exception error) { message(error.getMessage()); } }).show(), false)); }
            detailDialog = dialog("Reference", body); detailDialog.setOnDismissListener(d -> { handler.removeCallbacks(detailPoll); if (video != null) { video.stopPlayback(); video = null; } detailDialog = null; if (remote && foreground) loadDesktop(false, false); });
            if (fromDesktop && find(remoteCollections, selectedRemote) != null && find(remoteCollections, selectedRemote).optBoolean("protected")) detailDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (fromDesktop) handler.postDelayed(detailPoll, 3000);
        } catch (Exception error) { message(error.getMessage()); }
    }
    private void checkDetailAccess() {
        if (!foreground || !remote || detailDialog == null) return;
        final AlertDialog showing = detailDialog; final String id = selectedRemote;
        requests.execute(() -> { boolean permitted = false; try { JSONObject collection = find(desktop.collections(), id); permitted = collection != null && !collection.optBoolean("locked"); } catch (Exception ignored) {} final boolean allowed = permitted;
            runOnUiThread(() -> { if (isDestroyed() || showing != detailDialog) return; if (!allowed) { remotePins = new JSONArray(); showing.dismiss(); message("Desktop access ended. Unlock or reconnect to continue."); } else handler.postDelayed(detailPoll, 3000); }); });
    }
    private void addMedia(LinearLayout body, JSONObject pin, JSONObject item, boolean fromDesktop) throws Exception {
        if ("text".equals(item.optString("kind"))) { body.addView(text(item.optString("text"), 16)); return; }
        if ("image".equals(item.optString("kind"))) addImage(body, pin, item, fromDesktop, 300, false);
        else if ("video".equals(item.optString("kind"))) body.addView(button("Play video", () -> { try { if (video != null) { video.stopPlayback(); ((ViewGroup)video.getParent()).removeView(video); } video = new VideoView(this); body.addView(video, new LinearLayout.LayoutParams(-1, dp(280))); MediaController controls = new MediaController(this); controls.setAnchorView(video); video.setMediaController(controls); video.setOnErrorListener((player, what, extra) -> { message("This video cannot be played on this phone."); return true; }); if (fromDesktop) { String url = item.optString("originalUrl", item.optString("previewUrl")); video.setVideoURI(Uri.parse(url), desktop.videoHeaders(url)); } else video.setVideoURI(Uri.fromFile(MobileLibrary.get(this).mediaFile(pin.optString("mediaFile")))); video.start(); } catch (Exception error) { message(error.getMessage()); } }, false));
    }
    private void editPin(JSONObject pin) { LinearLayout body = dialogBody(); EditText title = field("Title", false), notes = field("Notes", true); title.setText(pin.optString("title")); notes.setText(pin.optString("notes")); addField(body, title); addField(body, notes); AlertDialog value = dialog("Edit phone pin", body); Button save = button("Save changes", () -> { try { MobileLibrary.get(this).edit(pin.getString("id"), title.getText().toString(), notes.getText().toString()); value.dismiss(); if (detailDialog != null) detailDialog.dismiss(); refreshLocal(); } catch (Exception error) { error(title, error.getMessage()); } }, true); body.addView(save); }
    private void connections() {
        settingsOpen = true; LinearLayout body = dialogBody(); body.addView(text("On the desktop, open Receive from phone, enable the receiver and phone browsing, then choose Pair a phone.", 15)); body.addView(button("Pair desktop", this::scanPair, true)); pairLink = field("Complete pairing link", false); pairLink.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); addField(body, pairLink);
        pairLink.setText(pairingDraft); body.addView(button("Paste pairing link instead", () -> { String copied = clipboardText(); if (copied != null) { pairLink.setText(copied); pair(copied); } else message("Copy the complete desktop pairing link first."); }, false)); body.addView(button("Pair from link", () -> pair(pairLink.getText().toString()), false));
        try { JSONObject state = PhoneStore.get(this).snapshot(); if (state.has("config")) { EditText address = field("http://192.168.1.10:47778", false); desktopAddress = address; address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); address.setText(addressDraft.isEmpty() ? state.getJSONObject("config").optString("endpoint") : addressDraft); addField(body, address); body.addView(button("Update desktop address", () -> { try { PhoneStore.get(this).endpoint(Delivery.endpoint(address.getText().toString())); addressDraft = ""; remotePins = new JSONArray(); remoteCollections = new JSONArray(); message("Desktop address updated; queued destinations were kept."); sync(); if (remote) loadDesktop(false, true); } catch (Exception error) { error(address, error.getMessage()); } }, false)); } } catch (Exception error) { message(error.getMessage()); }
        body.addView(text("Desktop links, passwords and media use HTTP on your trusted local network. Phone collections and previews stay in app-private storage. Phone and desktop collections are separate. Pending deliveries survive restarts; Android controls background retry timing.", 13)); connectionsDialog = dialog("Connections", body); connectionsDialog.setOnDismissListener(d -> { settingsOpen = false; connectionsDialog = null; });
    }
    private void scanPair() { try { JSONObject state = PhoneStore.get(this).snapshot(); if (state.has("config") && state.getJSONArray("entries").length() != 0) throw new Exception("Deliver or dismiss pending links before replacing your pairing. Update the desktop address to reconnect instead."); startActivityForResult(new Intent(this, ScanActivity.class), 1); } catch (Exception error) { message(error.getMessage()); } }
    @Override public void onActivityResult(int request, int result, Intent data) { super.onActivityResult(request, result, data); if (request == 1 && result == RESULT_OK && data != null) pair(data.getStringExtra("pairingLink")); else if (request == 1) message("Pairing cancelled. Your existing pairing was kept."); }
    private void pair(String value) {
        message("Pairing…"); async(() -> { JSONObject before = PhoneStore.get(this).snapshot(); if (before.has("config") && before.getJSONArray("entries").length() != 0) throw new Exception("Deliver or dismiss pending links before replacing your pairing. Update the desktop address to reconnect instead."); String name = Build.MANUFACTURER + " " + Build.MODEL; PhoneStore.get(this).pair(Delivery.pair(value, name.substring(0, Math.min(80, name.length())))); }, () -> { pairingDraft = ""; if (pairLink != null) pairLink.setText(""); if (connectionsDialog != null) connectionsDialog.dismiss(); message("Paired with Papan."); sync(); switchLocation(true); });
    }
    private void sync() {
        if (!syncing.compareAndSet(false, true)) return;
        new Thread(() -> { try { if (Delivery.sync(getApplicationContext(), new AtomicBoolean(false))) Delivery.schedule(this); } catch (Exception error) { runOnUiThread(() -> { if (!isDestroyed()) message("Your queue was preserved: " + error.getMessage()); }); }
            runOnUiThread(() -> { syncing.set(false); if (isDestroyed()) return; try { JSONArray entries = PhoneStore.get(this).snapshot().getJSONArray("entries"); if (sharedId != null) { JSONObject entry = find(entries, sharedId); message(entry == null ? "Saved in Papan." : entry.optString("status")); } if (activityOpen) renderActivity(); if (foreground && entries.length() > 0 && SystemClock.elapsedRealtime() < pollUntil) { handler.removeCallbacks(poll); handler.postDelayed(poll, 1500); } } catch (Exception error) { message(error.getMessage()); } });
        }, "papan-send").start();
    }
    private void renderActivity() {
        if (!activityOpen) return;
        try {
            clearBoard(); LinearLayout body = board; body.addView(button("Send pending / check saves", () -> { pollUntil = SystemClock.elapsedRealtime() + 30000; sync(); }, true)); JSONArray entries = PhoneStore.get(this).snapshot().getJSONArray("entries");
            if (entries.length() == 0) body.addView(text("No pending desktop links.", 16));
            for (int i = 0; i < entries.length(); i++) { JSONObject entry = entries.getJSONObject(i); String id = entry.getString("id"); body.addView(text(entry.optString("collectionName", "Desktop default") + "\n" + entry.optString("url") + "\n" + entry.optString("status"), 14));
                if (entry.has("collectionId") && !entry.optBoolean("received") && entry.optString("status").toLowerCase(Locale.ROOT).contains("password")) body.addView(button("Unlock & retry", () -> { try { unlock(new JSONObject().put("id", entry.getString("collectionId")).put("name", entry.optString("collectionName")), () -> { pollUntil = SystemClock.elapsedRealtime() + 30000; sync(); }); } catch (Exception error) { message(error.getMessage()); } }, false));
                body.addView(button("Dismiss from phone", () -> new MaterialAlertDialogBuilder(this).setTitle("Dismiss this link?").setMessage("This removes the phone queue entry. An already accepted desktop save remains.").setNegativeButton("Keep", null).setPositiveButton("Dismiss", (d,w) -> { try { PhoneStore.get(this).remove(id, false); if (id.equals(sharedId)) sharedId = null; renderActivity(); } catch (Exception error) { message(error.getMessage()); } }).show(), false)); }
        } catch (Exception error) { message(error.getMessage()); }
    }
}

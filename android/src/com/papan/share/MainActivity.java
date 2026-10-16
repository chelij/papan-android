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
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.widget.NestedScrollView;
import androidx.appcompat.view.ContextThemeWrapper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.appbar.AppBarLayout;
import androidx.appcompat.widget.ListPopupWindow;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
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
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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
    private static final String[] LOCATION_LABELS = {"Phone", "Desktop", "Activity"};
    private static final int[] LOCATION_ICONS = {R.drawable.phone, R.drawable.desktop, R.drawable.history};
    private int TEXT, MUTED, ACCENT;
    private LinearLayout root, board, actions;
    private MaterialButton collectionButton;
    private JSONArray collectionChoices = new JSONArray();
    private ListPopupWindow collectionMenu, locationMenu;
    private MaterialToolbar toolbar;
    private AppBarLayout appBar;
    private ExtendedFloatingActionButton locationButton;
    private FloatingActionButton addLink;
    private TextInputLayout searchLayout;
    private RecyclerView pinGrid;
    private NestedScrollView boardScroll;
    private String gridKey = "";
    private int gridAnchor = -1, gridAnchorOffset;
    private boolean loadingMore;
    private TextView status, heading, connectionFeedback;
    private Snackbar snackbar;
    private boolean messagePending;
    private EditText search, pairLink, desktopAddress;
    private Dialog saveDialog;
    private AlertDialog connectionsDialog, appearanceDialog, nearbyDialog;
    private PinViewer detailDialog;
    private Dialog detailInfo;
    private PreviewImages previewImages;
    private String viewerPinId, viewerItemId;
    private int viewerPosition;
    private boolean viewerPlaying, movingViewer;
    private int viewerAdvance;
    private String viewerAdvanceFrom;
    private boolean remote, foreground, settingsOpen, activityOpen;
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
    private final ExecutorService requests = Executors.newSingleThreadExecutor();
    private DesktopLibrary desktop;
    private SharedPreferences appearance;
    private interface Work { void run() throws Exception; }

    @Override public void onCreate(Bundle saved) {
        appearance = getSharedPreferences("board-appearance", MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(appearance.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        super.onCreate(saved); desktop = new DesktopLibrary(this);
        Object retained = getLastCustomNonConfigurationInstance(); previewImages = retained instanceof PreviewImages ? (PreviewImages)retained : new PreviewImages();
        TEXT = getColor(R.color.papan_on_surface); MUTED = getColor(R.color.papan_on_surface_variant); ACCENT = getColor(R.color.papan_primary);
        selectedLocal = appearance.getString("selectedLocal", ""); selectedRemote = appearance.getString("selectedRemote", "");
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if (saved != null) { remote = saved.getBoolean("remote"); selectedLocal = saved.getString("local", ""); selectedRemote = saved.getString("desktop", ""); pendingLink = saved.getString("pendingLink"); sharedId = saved.getString("sharedId"); settingsOpen = saved.getBoolean("settingsOpen"); pairingDraft = saved.getString("pairingDraft", ""); addressDraft = saved.getString("addressDraft", ""); }
        activityOpen = saved != null && saved.getBoolean("activityOpen");
        if (saved != null) { viewerPinId = saved.getString("viewerPin"); viewerItemId = saved.getString("viewerItem"); viewerPosition = saved.getInt("viewerPosition"); viewerPlaying = saved.getBoolean("viewerPlaying"); }
        buildLayout();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { if (searchLayout.isShown()) toggleSearch(); else if (remote || activityOpen) switchLocation(false); else finishAndRemoveTask(); }
        });
        if (activityOpen) renderActivity(); else if (!remote) refreshLocal();
        if (saved == null) receive(getIntent()); else if (pendingLink != null) handler.post(() -> chooseDestination(pendingLink)); else if (settingsOpen) handler.post(this::connections);
    }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); receive(intent); }
    @Override public void onStart() { super.onStart(); foreground = true; pollUntil = SystemClock.elapsedRealtime() + 30000; sync(); capture(); if (remote) loadDesktop(false, true); else if (!activityOpen) refreshLocal(); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused && snackbar != null) snackbar.dismiss();
        if (focused && foreground && messagePending) showBoardMessage();
    }
    @Override public void onStop() {
        appearance.edit().putString("selectedLocal", selectedLocal).putString("selectedRemote", selectedRemote).apply();
        foreground = false; handler.removeCallbacks(poll); handler.removeCallbacks(browsePoll); handler.removeCallbacks(detailPoll); handler.removeCallbacks(searchPoll); boardGeneration++;
        rememberViewer(); if (detailDialog != null) detailDialog.dismiss(); if (detailInfo != null) detailInfo.dismiss();
        if (appearanceDialog != null) appearanceDialog.dismiss();
        if (nearbyDialog != null) nearbyDialog.dismiss();
        if (collectionMenu != null) collectionMenu.dismiss(); if (locationMenu != null) locationMenu.dismiss();
        remotePins = new JSONArray(); for (int i = 0; i < remoteCollections.length(); i++) { JSONObject value = remoteCollections.optJSONObject(i); if (value != null && value.optBoolean("protected")) try { value.put("locked", true); } catch (Exception ignored) {} }
        if (remote) { clearBoard(); board.addView(text("Desktop contents hidden while Papan is in the background.", 16)); }
        super.onStop();
    }
    @Override public Object onRetainCustomNonConfigurationInstance() { return previewImages; }
    @Override public void onDestroy() { pinGrid.removeCallbacks(resizeBoard); if (!isChangingConfigurations()) { desktop.closeSession(); requests.execute(desktop::clear); previewImages.close(); } requests.shutdown(); super.onDestroy(); }
    @Override public void onSaveInstanceState(Bundle state) { rememberViewer(); state.putString("viewerPin", viewerPinId); state.putString("viewerItem", viewerItemId); state.putInt("viewerPosition", viewerPosition); state.putBoolean("viewerPlaying", viewerPlaying); state.putBoolean("remote", remote); state.putBoolean("activityOpen", activityOpen); state.putString("local", selectedLocal); state.putString("desktop", selectedRemote); state.putString("pendingLink", pendingLink); state.putString("sharedId", sharedId); state.putBoolean("settingsOpen", settingsOpen); state.putString("pairingDraft", pairLink == null ? pairingDraft : pairLink.getText().toString()); state.putString("addressDraft", desktopAddress == null ? addressDraft : desktopAddress.getText().toString()); super.onSaveInstanceState(state); }
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
        CoordinatorLayout screen = new CoordinatorLayout(this); root.addView(screen, new LinearLayout.LayoutParams(-1, 0, 1));
        appBar = new AppBarLayout(this); appBar.setBackgroundColor(getColor(R.color.papan_background)); appBar.setElevation(0); appBar.setStateListAnimator(null);
        appBar.setTouchscreenBlocksFocus(false); appBar.setKeyboardNavigationCluster(false);
        AccessibilityManager accessibility = (AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        AppBarLayout.Behavior headerScroll = new AppBarLayout.Behavior() {
            @Override public boolean onStartNestedScroll(CoordinatorLayout parent, AppBarLayout child, View direct, View target, int axes, int type) {
                return !accessibility.isTouchExplorationEnabled() && !toolbar.hasFocus() && !searchLayout.hasFocus() && !actions.hasFocus() && (child.getTop() < 0 || target.canScrollVertically(-1) || target.canScrollVertically(1)) && super.onStartNestedScroll(parent, child, direct, target, axes, type);
            }
        };
        CoordinatorLayout.LayoutParams headerParams = new CoordinatorLayout.LayoutParams(-1, -2); headerParams.setBehavior(headerScroll); screen.addView(appBar, headerParams);
        toolbar = new MaterialToolbar(this); toolbar.setTouchscreenBlocksFocus(false); toolbar.setContentInsetsRelative(dp(12), dp(8)); toolbar.setBackgroundColor(getColor(R.color.papan_background));
        AppBarLayout.LayoutParams toolbarParams = new AppBarLayout.LayoutParams(-1, -2); toolbarParams.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS | AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP); appBar.addView(toolbar, toolbarParams);
        LinearLayout identity = row(); TextView brand = new TextView(this); brand.setText("papan."); brand.setTextAppearance(this, R.style.PapanBrand); brand.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 23); brand.setTextColor(TEXT); brand.setContentDescription("Papan"); identity.addView(brand);
        collectionButton = new MaterialButton(new ContextThemeWrapper(this, R.style.PapanTextButtonOverlay)); collectionButton.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge); collectionButton.setAllCaps(false); collectionButton.setSingleLine(true); collectionButton.setEllipsize(android.text.TextUtils.TruncateAt.END); collectionButton.setMinWidth(dp(48)); collectionButton.setMinimumWidth(dp(48)); collectionButton.setMinHeight(dp(48)); collectionButton.setMinimumHeight(dp(48)); collectionButton.setInsetTop(0); collectionButton.setInsetBottom(0); collectionButton.setPaddingRelative(dp(8), 0, dp(4), 0); collectionButton.setIconResource(com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down); collectionButton.setIconGravity(MaterialButton.ICON_GRAVITY_END); collectionButton.setIconPadding(dp(4)); collectionButton.setIconSize(dp(24)); collectionButton.setTextColor(TEXT); collectionButton.setIconTint(ColorStateList.valueOf(MUTED)); collectionButton.setOnClickListener(v -> chooseCollection()); identity.addView(collectionButton, new LinearLayout.LayoutParams(0, -2, 1));
        toolbar.addView(identity, new androidx.appcompat.widget.Toolbar.LayoutParams(-1, -2, Gravity.START | Gravity.CENTER_VERTICAL));
        toolbar.getMenu().add(0, 107, 0, "Search").setIcon(R.drawable.search).setShowAsAction(getResources().getConfiguration().fontScale > 1.5f ? android.view.MenuItem.SHOW_AS_ACTION_NEVER : android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.getMenu().add(0, 108, 1, "Board appearance").setIcon(R.drawable.tune).setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
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
                case 106: retainRemoteImages(new JSONArray()); loadDesktop(false, true); break;
                case 107: toggleSearch(); break;
                case 108: boardAppearance(); break;
                default: return false;
            }
            return true;
        });
        LinearLayout content = column(); CoordinatorLayout.LayoutParams contentParams = new CoordinatorLayout.LayoutParams(-1, -1); contentParams.setBehavior(new AppBarLayout.ScrollingViewBehavior()); screen.addView(content, contentParams);
        heading = text("", 14); heading.setTextColor(MUTED); heading.setPadding(dp(16), dp(8), dp(16), dp(8)); heading.setVisibility(View.GONE); content.addView(heading);
        search = field("Search this collection", false); search.setContentDescription("Search this collection"); search.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(500) }); searchLayout = fieldLayout(search);
        searchLayout.setVisibility(View.GONE);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2); searchParams.setMargins(dp(16), 0, dp(16), dp(8)); content.addView(searchLayout, searchParams);
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence value,int start,int count,int after) {} public void onTextChanged(CharSequence value,int start,int before,int count) { if (activityOpen) return; if (remote) { boardGeneration++; handler.removeCallbacks(searchPoll); handler.postDelayed(searchPoll, 250); } else renderPins(); } public void afterTextChanged(Editable value) {} });
        FrameLayout canvas = new FrameLayout(this); content.addView(canvas, new LinearLayout.LayoutParams(-1, 0, 1));
        boardScroll = new NestedScrollView(this); boardScroll.setFillViewport(true); board = column(); board.setPadding(dp(16), dp(4), dp(16), dp(80)); boardScroll.addView(board); canvas.addView(boardScroll, new FrameLayout.LayoutParams(-1, -1));
        pinGrid = new RecyclerView(this); pinGrid.setPadding(0, dp(4), 0, dp(80)); pinGrid.setClipToPadding(false); pinGrid.setHasFixedSize(true); pinGrid.setItemAnimator(null); pinGrid.setVisibility(View.GONE); canvas.addView(pinGrid, new FrameLayout.LayoutParams(-1, -1));
        pinGrid.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> { if (right - left != oldRight - oldLeft && pinGrid.getAdapter() != null) pinGrid.post(this::renderPins); });
        actions = row(); actions.setPadding(dp(16), dp(8), dp(16), dp(16));
        CoordinatorLayout.LayoutParams actionParams = new CoordinatorLayout.LayoutParams(-1, -2); actionParams.gravity = Gravity.BOTTOM; screen.addView(actions, actionParams);
        appBar.addOnOffsetChangedListener((AppBarLayout.OnOffsetChangedListener)(bar, offset) -> syncBars(offset));
        locationButton = new ExtendedFloatingActionButton(this); locationButton.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge); locationButton.setSingleLine(true); locationButton.setEllipsize(android.text.TextUtils.TruncateAt.END); locationButton.setMinHeight(dp(48)); locationButton.setMinimumHeight(dp(48)); locationButton.setInsetTop(dp(4)); locationButton.setInsetBottom(dp(4)); locationButton.setPaddingRelative(dp(12), dp(8), dp(12), dp(8)); locationButton.setIconSize(dp(20)); locationButton.setIconPadding(dp(8)); locationButton.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.papan_primary_container))); locationButton.setTextColor(getColor(R.color.papan_on_primary_container)); locationButton.setIconTint(ColorStateList.valueOf(getColor(R.color.papan_on_primary_container))); locationButton.setOnClickListener(v -> chooseLocation()); actions.addView(locationButton, new LinearLayout.LayoutParams(-2, -2));
        View flexible = new View(this); actions.addView(flexible, new LinearLayout.LayoutParams(dp(12), 0, 1));
        addLink = new FloatingActionButton(this); addLink.setSize(FloatingActionButton.SIZE_MINI); addLink.setEnsureMinTouchTargetSize(true); addLink.setMaxImageSize(dp(20)); addLink.setImageResource(R.drawable.add); addLink.setBackgroundTintList(ColorStateList.valueOf(ACCENT)); addLink.setImageTintList(ColorStateList.valueOf(getColor(R.color.papan_on_primary))); addLink.setContentDescription("Add link"); addLink.setOnClickListener(v -> enterLink()); actions.addView(addLink, new LinearLayout.LayoutParams(-2, -2));
        View.OnFocusChangeListener revealActions = (view, focused) -> { if (focused) revealChrome(); };
        locationButton.setOnFocusChangeListener(revealActions); addLink.setOnFocusChangeListener(revealActions);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((before, focused) -> { if (toolbar.hasFocus() || searchLayout.hasFocus()) revealChrome(); });
        AccessibilityManager.TouchExplorationStateChangeListener exploration = enabled -> { if (enabled) revealChrome(); }; accessibility.addTouchExplorationStateChangeListener(exploration);
        appBar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) { accessibility.removeTouchExplorationStateChangeListener(exploration); }
        });
        actions.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            syncBars(appBar.getTop());
            int width = 0; for (String label : LOCATION_LABELS) width = Math.max(width, (int)Math.ceil(locationButton.getPaint().measureText(label)));
            width += locationButton.getPaddingStart() + locationButton.getPaddingEnd() + locationButton.getIconSize() + locationButton.getIconPadding();
            width = Math.min(width, Math.max(dp(48), right - left - actions.getPaddingLeft() - actions.getPaddingRight() - addLink.getMeasuredWidth() - dp(12)));
            if (locationButton.getMaxWidth() != width || locationButton.getMinWidth() != width) locationButton.setWidth(width);
            int clearance = bottom - top + dp(8);
            if (pinGrid.getPaddingBottom() != clearance) pinGrid.setPadding(pinGrid.getPaddingLeft(), pinGrid.getPaddingTop(), pinGrid.getPaddingRight(), clearance);
            if (board.getPaddingBottom() != clearance) board.setPadding(board.getPaddingLeft(), board.getPaddingTop(), board.getPaddingRight(), clearance);
        });
        status = text("", 14); status.setVisibility(View.GONE); root.addView(status);
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
        if (status == null) return;
        boolean changed = !message.contentEquals(status.getText());
        status.setText(message);
        if (message.isEmpty() || !foreground) { messagePending = false; return; }
        if (connectionsDialog != null && connectionsDialog.isShowing()) {
            messagePending = false;
            if (snackbar != null) snackbar.dismiss();
            TextView feedback = connectionFeedback;
            feedback.setText(message); feedback.setVisibility(View.VISIBLE);
            feedback.post(() -> feedback.requestRectangleOnScreen(new android.graphics.Rect(0, 0, feedback.getWidth(), feedback.getHeight())));
            return;
        }
        messagePending = changed || messagePending;
        if (root.hasWindowFocus() && messagePending) showBoardMessage();
    }
    private void showBoardMessage() {
        messagePending = false;
        snackbar = Snackbar.make(root, status.getText(), Snackbar.LENGTH_LONG);
        if (addLink.isShown()) snackbar.setAnchorView(addLink);
        else snackbar.setAnchorView(locationButton);
        snackbar.show();
    }
    private void async(Work work, Runnable complete) { requests.execute(() -> { try { work.run(); runOnUiThread(() -> { if (!isDestroyed()) complete.run(); }); } catch (Exception error) { runOnUiThread(() -> { if (!isDestroyed()) message(error.getMessage()); }); } }); }
    private String clipboardText() { ClipData data = ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).getPrimaryClip(); if (data == null || data.getItemCount() == 0) return null; ClipData.Item value = data.getItemAt(0); return value.getText() != null ? value.getText().toString() : value.getUri() != null ? value.getUri().toString() : null; }
    private void receive(Intent intent) { if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) chooseDestination(intent.getStringExtra(Intent.EXTRA_TEXT)); else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) pair(intent.getData().toString()); setIntent(new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)); }
    private void switchLocation(boolean desktopLocation) { showDestination(desktopLocation ? 2 : 1); }
    private int locationIndex() { return activityOpen ? 2 : remote ? 1 : 0; }
    private void chooseLocation() {
        String[] labels = new String[2]; int[] icons = new int[2], destinations = new int[2];
        for (int i = 0, entry = 0; i < LOCATION_LABELS.length; i++) if (i != locationIndex()) { labels[entry] = LOCATION_LABELS[i]; icons[entry] = LOCATION_ICONS[i]; destinations[entry++] = i + 1; }
        locationMenu = picker(locationButton, labels, icons, -1, position -> showDestination(destinations[position]));
    }
    private void chooseCollection() {
        if (activityOpen || collectionChoices.length() == 0) return;
        JSONArray choices = collectionChoices; String[] labels = new String[choices.length()]; int selected = 0;
        for (int i = 0; i < choices.length(); i++) { JSONObject value = choices.optJSONObject(i); labels[i] = value == null ? "" : collectionLabel(value); if (value != null && value.optString("id").equals(remote ? selectedRemote : selectedLocal)) selected = i; }
        collectionMenu = picker(collectionButton, labels, null, selected, position -> { JSONObject value = choices.optJSONObject(position); if (value == null) return; search.setText(""); if (remote) { selectedRemote = value.optString("id"); loadDesktop(true, true); } else { selectedLocal = value.optString("id"); refreshLocal(); } });
    }
    private ListPopupWindow picker(View anchor, String[] labels, int[] icons, int selected, java.util.function.IntConsumer choose) {
        ListPopupWindow popup = new ListPopupWindow(this); popup.setAnchorView(anchor); popup.setModal(true); popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        if (anchor == locationButton) { popup.setVerticalOffset(-dp(8)); popup.setAnimationStyle(0); popup.setEpicenterBounds(new android.graphics.Rect(0, -dp(8), anchor.getWidth(), -dp(8))); }
        android.text.TextPaint measure = new android.text.TextPaint(((TextView)anchor).getPaint()); int width = anchor.getWidth();
        for (String label : anchor == locationButton ? LOCATION_LABELS : labels) width = Math.max(width, (int)Math.ceil(measure.measureText(label)) + dp(icons == null ? 32 : 68));
        popup.setWidth(Math.min(Math.min(dp(320), getResources().getDisplayMetrics().widthPixels - dp(32)), width));
        popup.setAdapter(new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int position, View reusable, ViewGroup parent) {
                TextView row = (TextView)super.getView(position, reusable, parent); row.setTextAppearance(MainActivity.this, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge); row.setMinHeight(dp(48)); row.setPaddingRelative(dp(16), dp(12), dp(16), dp(12)); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setTextColor(position == selected ? getColor(R.color.papan_on_primary_container) : TEXT);
                int resting = position == selected ? getColor(R.color.papan_primary_container) : Color.TRANSPARENT, focused = position == selected ? resting : getColor(R.color.papan_secondary_container);
                android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
                for (int state : new int[] {android.R.attr.state_selected, android.R.attr.state_pressed, android.R.attr.state_hovered}) background.addState(new int[] {state}, new android.graphics.drawable.ColorDrawable(focused));
                background.addState(new int[0], new android.graphics.drawable.ColorDrawable(resting)); row.setBackground(background);
                android.graphics.drawable.Drawable icon = icons == null ? null : androidx.appcompat.content.res.AppCompatResources.getDrawable(MainActivity.this, icons[position]);
                if (icon != null) { icon = icon.mutate(); icon.setTint(position == selected ? getColor(R.color.papan_on_primary_container) : MUTED); icon.setBounds(0, 0, dp(24), dp(24)); }
                row.setCompoundDrawablesRelative(icon, null, null, null); row.setCompoundDrawablePadding(dp(12)); return row;
            }
        });
        popup.setOnItemClickListener((parent, view, position, id) -> { popup.dismiss(); choose.accept(position); }); popup.show();
        popup.getListView().setChoiceMode(selected < 0 ? android.widget.ListView.CHOICE_MODE_NONE : android.widget.ListView.CHOICE_MODE_SINGLE); if (selected >= 0) popup.getListView().setItemChecked(selected, true); popup.setSelection(Math.max(0, selected)); return popup;
    }
    private String collectionLabel(JSONObject value) { return value.optString("name") + (value.optBoolean("protected") ? " · protected" : ""); }
    private void syncBars(int offset) { actions.setTranslationY(appBar.getTotalScrollRange() > 0 ? -(float)offset * actions.getHeight() / appBar.getTotalScrollRange() : 0); }
    private void revealChrome() { appBar.setExpanded(true, false); }
    private void showDestination(int id) {
        revealChrome();
        remote = id == 2; activityOpen = id == 3; boardGeneration++; gridKey = ""; handler.removeCallbacks(browsePoll); handler.removeCallbacks(searchPoll); search.setText(""); updateChrome();
        if (activityOpen) { getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE); heading.setText("Desktop delivery activity"); renderActivity(); }
        else if (remote) loadDesktop(false, true); else refreshLocal();
    }
    private void updateChrome() {
        collectionButton.setVisibility(activityOpen ? View.GONE : View.VISIBLE); if (activityOpen) searchLayout.setVisibility(View.GONE); heading.setVisibility(activityOpen ? View.VISIBLE : View.GONE);
        locationButton.setText(LOCATION_LABELS[locationIndex()]); locationButton.setIconResource(LOCATION_ICONS[locationIndex()]); locationButton.setContentDescription("Choose location: " + locationButton.getText());
        toolbar.getMenu().findItem(107).setVisible(!activityOpen);
        toolbar.getMenu().findItem(108).setVisible(!activityOpen);
        toolbar.getMenu().findItem(103).setVisible(!remote && !activityOpen);
        toolbar.getMenu().findItem(104).setVisible(!remote && !activityOpen && !selectedLocal.isEmpty());
        toolbar.getMenu().findItem(105).setVisible(!remote && !activityOpen && !selectedLocal.isEmpty());
        toolbar.getMenu().findItem(106).setVisible(remote);
    }
    private void toggleSearch() {
        revealChrome();
        boolean show = !searchLayout.isShown(); searchLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) search.requestFocus(); else { search.setText(""); search.clearFocus(); ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0); }
    }
    private String boardPreferenceKey() { return (remote ? "desktop:" + selectedRemote : "phone:" + selectedLocal); }
    private boolean fillFrames() { return appearance.getBoolean("fill:" + boardPreferenceKey(), true); }
    private void retainRemoteImages(JSONArray collections) { java.util.Set<String> authorized = new java.util.HashSet<>(); for (int i = 0; i < collections.length(); i++) { JSONObject collection = collections.optJSONObject(i); if (collection != null && !collection.optBoolean("locked")) authorized.add(collection.optString("id")); } previewImages.retainRemote(authorized); }
    private boolean measuredItem(JSONObject item) { return item.optDouble("previewWidth", item.optDouble("width", 0)) > 0 && item.optDouble("previewHeight", item.optDouble("height", 0)) > 0; }
    private double frameRatio(JSONObject collection) {
        JSONObject preview = remote && collection != null ? collection.optJSONObject("preview") : null; if (preview == null || "cover".equals(preview.optString("fit"))) return 1;
        double count = Math.max(0, preview.optInt("measuredCount")), sum = preview.optDouble("aspectRatio", 1) * count; java.util.Set<String> known = new java.util.HashSet<>();
        for (int p = 0; p < remotePins.length(); p++) { JSONArray items = remotePins.optJSONObject(p).optJSONArray("items"); for (int i = 0; items != null && i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item != null && measuredItem(item)) known.add(item.optString("id")); } }
        for (java.util.Map.Entry<String, Double> entry : previewImages.ratios(collection.optString("id")).entrySet()) if (!known.contains(entry.getKey())) { sum += entry.getValue(); count++; }
        double ratio = count > 0 ? sum / count : preview.optDouble("aspectRatio", 1); return Double.isFinite(ratio) && ratio >= .05 && ratio <= 20 ? ratio : 1;
    }
    private void boardAppearance() {
        final String key = boardPreferenceKey(); LinearLayout body = dialogBody();
        TextView sizeLabel = text("Preview size", 18); body.addView(sizeLabel);
        body.addView(text("Keep rows and columns aligned as previews get smaller or larger.", 14));
        Slider size = new Slider(this); size.setId(View.generateViewId()); size.setContentDescription("Preview size"); sizeLabel.setLabelFor(size.getId()); size.setValueFrom(100); size.setValueTo(300); size.setStepSize(20); size.setValue(appearance.getInt("size:" + key, 120));
        size.setLabelFormatter(value -> value <= 120 ? "Small" : value >= 240 ? "Large" : "Medium"); body.addView(size);
        LinearLayout ends = row(); TextView small = text("Smaller", 14), large = text("Larger", 14); small.setTextColor(MUTED); large.setTextColor(MUTED); large.setGravity(Gravity.END); ends.addView(small, new LinearLayout.LayoutParams(0, -2, 1)); ends.addView(large, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(ends);
        size.addOnChangeListener((slider, value, fromUser) -> { if (fromUser) { appearance.edit().putInt("size:" + key, Math.round(value)).apply(); pinGrid.removeCallbacks(resizeBoard); pinGrid.postOnAnimation(resizeBoard); } });
        if (remote) body.addView(text("Frame proportions follow this collection on your desktop.", 14));
        MaterialSwitch fill = new MaterialSwitch(this); fill.setText("Fill image frames"); fill.setMinHeight(dp(48)); fill.setChecked(fillFrames()); body.addView(fill); fill.setOnCheckedChangeListener((button, checked) -> { appearance.edit().putBoolean("fill:" + key, checked).apply(); renderPins(); });
        body.addView(text("Appearance", 18)); RadioGroup themes = new RadioGroup(this);
        int[] modes = {AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES}; String[] labels = {"Use phone setting", "Light", "Dark"};
        for (int i = 0; i < modes.length; i++) { MaterialRadioButton option = new MaterialRadioButton(this); option.setId(i + 201); option.setText(labels[i]); option.setMinHeight(dp(48)); themes.addView(option); if (appearance.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) == modes[i]) option.setChecked(true); }
        body.addView(themes); AlertDialog value = dialog("Board appearance", body); appearanceDialog = value;
        value.setOnDismissListener(d -> { if (appearanceDialog == value) appearanceDialog = null; });
        themes.setOnCheckedChangeListener((group, checked) -> { int mode = modes[checked - 201]; appearance.edit().putInt("theme", mode).apply(); value.dismiss(); AppCompatDelegate.setDefaultNightMode(mode); });
    }
    private void refreshLocal() {
        if (remote || activityOpen) return;
        try { JSONArray collections = MobileLibrary.get(this).snapshot().getJSONArray("collections"); if (find(collections, selectedLocal) == null) selectedLocal = collections.length() == 0 ? "" : collections.getJSONObject(0).getString("id"); renderCollections(collections, selectedLocal); updateChrome(); renderPins(); restoreViewer(); }
        catch (Exception error) { message(error.getMessage()); }
    }
    private JSONObject find(JSONArray values, String id) { for (int i = 0; i < values.length(); i++) { JSONObject value = values.optJSONObject(i); if (value != null && id.equals(value.optString("id"))) return value; } return null; }
    private void renderCollections(JSONArray collections, String selected) {
        collectionChoices = collections; JSONObject current = find(collections, selected); collectionButton.setEnabled(current != null);
        collectionButton.setText(current == null ? "Collections" : current.optString("name")); collectionButton.setContentDescription(current == null ? "Choose collection" : "Choose collection: " + collectionLabel(current));
    }
    private void loadDesktop(boolean ask, boolean loading) {
        final int generation = ++boardGeneration, loadedCount = loading ? 40 : Math.max(40, remotePins.length()); final String query = search.getText().toString(); handler.removeCallbacks(browsePoll); handler.removeCallbacks(searchPoll);
        if (loading) { remotePins = new JSONArray(); loadingMore = false; gridKey = ""; clearBoard(); board.addView(text("Connecting to desktop…", 16)); }
        requests.execute(() -> {
            try {
                JSONArray collections = desktop.collections(); String chosen = selectedRemote; if (find(collections, chosen) == null) chosen = collections.length() == 0 ? "" : collections.getJSONObject(0).getString("id");
                JSONObject current = find(collections, chosen), page = current == null || current.optBoolean("locked") ? null : desktop.loadedPins(chosen, loadedCount, query); final String id = chosen;
                runOnUiThread(() -> { if (isDestroyed() || !foreground || !remote || generation != boardGeneration) return; retainRemoteImages(collections); JSONArray pins = page == null ? new JSONArray() : page.optJSONArray("pins"); if (pins == null) pins = new JSONArray(); boolean changed = !id.equals(selectedRemote) || !remoteCollections.toString().equals(collections.toString()) || !remotePins.toString().equals(pins.toString()); selectedRemote = id; remoteCollections = collections; remotePins = pins; nextOffset = page == null || page.isNull("nextOffset") ? -1 : page.optInt("nextOffset", -1); if (loading || changed) { renderCollections(collections, id); renderPins(); } restoreViewer(); if (ask && current != null && current.optBoolean("locked")) unlock(current, () -> loadDesktop(false, true)); handler.postDelayed(browsePoll, 5000); });
            } catch (Exception error) { runOnUiThread(() -> { if (isDestroyed() || !remote || generation != boardGeneration) return; retainRemoteImages(new JSONArray()); remotePins = new JSONArray(); remoteCollections = new JSONArray(); renderCollections(remoteCollections, ""); clearBoard(); heading.setText("Desktop unavailable"); board.addView(text(error.getMessage() == null ? "Desktop is unavailable." : error.getMessage(), 16)); board.addView(button("Try desktop again", () -> loadDesktop(false, true), false)); board.addView(button("Use phone collections", () -> switchLocation(false), false)); }); }
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
                renderPins(); if (viewerAdvance != 0) { int advance = viewerAdvance; viewerAdvance = 0; if (detailDialog != null && detailDialog.pin.optString("id").equals(viewerAdvanceFrom)) { if (page != null) navigatePin(advance); else detailDialog.notice(problem == null ? "Could not load more pins. Try again." : problem); } } if (foreground) handler.postDelayed(browsePoll, 5000);
            });
        });
    }
    private void clearBoard() { revealChrome(); pinGrid.removeCallbacks(resizeBoard); pinGrid.setVisibility(View.GONE); pinGrid.setAdapter(null); boardScroll.setVisibility(View.VISIBLE); board.removeAllViews(); }
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
            int width = pinGrid.getWidth() > 0 ? Math.round(pinGrid.getWidth() / density) : getResources().getConfiguration().screenWidthDp;
            int available = Math.max(1, width), target = appearance.getInt("size:" + boardPreferenceKey(), 120), minimum = Math.round(112 * getResources().getConfiguration().fontScale);
            int columns = Math.max(1, Math.min(6, Math.min(Math.max(1, available / minimum), Math.round((float)available / target))));
            if (getResources().getConfiguration().screenWidthDp < 340) columns = 1;
            for (int position = 0; position < values.length(); position++) { int i = remote ? position : values.length() - 1 - position; JSONObject pin = values.getJSONObject(i); if (!remote && !selectedLocal.equals(pin.optString("collectionId"))) continue; if (!remote && !query.isEmpty() && !(pin.optString("title") + " " + pin.optString("sourceUrl") + " " + pin.optString("notes")).toLowerCase(Locale.ROOT).contains(query)) continue;
                pins.add(pin);
            }
            if (pins.isEmpty()) { clearBoard(); board.addView(text(query.isEmpty() ? "This collection is empty." : "No matching pins.", 20)); board.addView(text(query.isEmpty() ? "Paste a link or share one to Papan to add your first reference." : "Try another title, source or note.", 16)); return; }
            String key = (remote ? "desktop:" + selectedRemote : "phone:" + selectedLocal) + ":" + query;
            if (!key.equals(gridKey)) revealChrome();
            GridLayoutManager previous = (GridLayoutManager)pinGrid.getLayoutManager(); int first = key.equals(gridKey) && previous != null ? previous.findFirstVisibleItemPosition() : -1;
            View anchor = first >= 0 ? previous.findViewByPosition(first) : null; int offset = anchor == null ? 0 : previous.getDecoratedTop(anchor) - pinGrid.getPaddingTop();
            // A second size event can arrive before the replacement layout has attached cards.
            if (anchor == null && key.equals(gridKey)) { first = gridAnchor; offset = gridAnchorOffset; }
            gridAnchor = first; gridAnchorOffset = offset;
            double ratio = frameRatio(collection);
            int frameWidth = Math.max(48, available / columns), frameHeight = Math.max(Math.round(56 * getResources().getConfiguration().fontScale), (int)Math.round(frameWidth / ratio));
            PinAdapter adapter = new PinAdapter(pins, remote, frameHeight); GridLayoutManager layout = new GridLayoutManager(this, columns);
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
            MaterialCardView card = new MaterialCardView(MainActivity.this); card.setRadius(0); card.setCardElevation(0); card.setCardBackgroundColor(getColor(R.color.papan_surface)); card.setPreventCornerOverlap(false); card.setStrokeWidth(dp(2)); card.setStrokeColor(new ColorStateList(new int[][] { {android.R.attr.state_focused}, {} }, new int[] { ACCENT, Color.TRANSPARENT })); card.setFocusable(true); card.setClickable(true);
            card.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            LinearLayout body = column(); card.addView(body); return new CardHolder(card, body);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (!(holder instanceof CardHolder)) { Button more = (Button)holder.itemView; more.setText(loadingMore ? "Loading pins…" : "Load more pins"); more.setEnabled(!loadingMore); return; }
            CardHolder card = (CardHolder)holder; JSONObject pin = pins.get(position); card.body.removeAllViews(); card.itemView.setContentDescription("Open pin: " + pin.optString("title")); card.itemView.setOnClickListener(v -> openPin(pin, fromDesktop));
            FrameLayout preview = new FrameLayout(MainActivity.this); preview.setBackgroundColor(getColor(R.color.papan_background)); card.body.addView(preview, new LinearLayout.LayoutParams(-1, dp(frameHeight)));
            String notice = !fromDesktop && "pending".equals(pin.optString("capture")) ? "Preparing preview…" : !fromDesktop && "failed".equals(pin.optString("capture")) ? "Preview unavailable" : null;
            try {
                JSONObject item = cover(pin, fromDesktop);
                if (item != null && ("image".equals(item.optString("kind")) || !fromDesktop && "video".equals(item.optString("kind")) || fromDesktop && item.has("thumbnailUrl"))) preview.addView(imageFrame(pin, item, fromDesktop, fillFrames()), new FrameLayout.LayoutParams(-1, -1));
                else if (notice == null) { String excerpt = item == null ? pin.optString("notes") : item.optString("text"); TextView kind = text(excerpt.isEmpty() ? item != null && "video".equals(item.optString("kind")) ? "Video" : "Saved link" : excerpt, 16); kind.setTextColor(MUTED); kind.setPadding(dp(12), dp(10), dp(12), 0); kind.setEllipsize(android.text.TextUtils.TruncateAt.END); kind.setMaxLines(1); preview.addView(kind, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP)); }
            } catch (Exception error) { TextView failed = text("Preview unavailable", 14); failed.setPadding(dp(12), dp(10), dp(12), 0); preview.addView(failed); }
            LinearLayout overlay = new LinearLayout(MainActivity.this) { @Override protected void onMeasure(int width, int height) { super.onMeasure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)); } }, caption = column(); overlay.setOrientation(LinearLayout.VERTICAL);
            overlay.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[] {Color.TRANSPARENT, 0x8C000000, 0xA6000000}));
            View spacing = new View(MainActivity.this); overlay.addView(spacing, new LinearLayout.LayoutParams(-1, dp(Math.round(50 * getResources().getConfiguration().fontScale))));
            caption.setPadding(dp(15), 0, dp(15), dp(16)); overlay.addView(caption);
            TextView title = text(pin.optString("title", pin.optString("sourceUrl")), 12); title.setTextColor(Color.WHITE); title.setPadding(0, 0, 0, 0); title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END); LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(-1, -2); titleSize.bottomMargin = dp(5); caption.addView(title, titleSize);
            String host; try { host = new java.net.URI(pin.optString("sourceUrl")).getHost(); } catch (Exception ignored) { host = null; }
            TextView source = text(host == null ? "Saved reference" : host.replaceFirst("^www\\.", ""), 10); source.setTextColor(0xFFEEEEEE); source.setPadding(0, 0, 0, 0); source.setMaxLines(1); source.setEllipsize(android.text.TextUtils.TruncateAt.END); caption.addView(source);
            preview.addView(overlay, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            try { JSONObject item = cover(pin, fromDesktop); if (item != null && "video".equals(item.optString("kind"))) { ImageView kind = new ImageView(MainActivity.this); kind.setImageResource(R.drawable.play); kind.setImageTintList(ColorStateList.valueOf(Color.WHITE)); kind.setContentDescription("Video"); kind.setPadding(dp(4), dp(4), dp(4), dp(4)); android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable(); background.setColor(0x99000000); background.setCornerRadius(dp(4)); kind.setBackground(background); FrameLayout.LayoutParams label = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP | Gravity.END); label.setMargins(dp(6), dp(6), dp(6), dp(6)); preview.addView(kind, label); } } catch (Exception ignored) {}
            View placeholder = preview.getChildAt(0); if (placeholder instanceof TextView) { placeholder.setBackgroundColor(getColor(R.color.papan_background)); preview.bringChildToFront(placeholder); }
            if (notice != null) { TextView state = text(notice, 12); state.setTextColor(MUTED); state.setBackgroundColor(getColor(R.color.papan_background)); state.setPadding(dp(10), dp(6), dp(10), dp(6)); state.setMaxLines(1); state.setEllipsize(android.text.TextUtils.TruncateAt.END); preview.addView(state, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP)); }
        }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder) { if (holder instanceof CardHolder) ((CardHolder)holder).body.removeAllViews(); }
    }
    private static final class CardHolder extends RecyclerView.ViewHolder {
        final LinearLayout body;
        CardHolder(MaterialCardView card, LinearLayout content) { super(card); body = content; }
    }
    private FrameLayout imageFrame(JSONObject pin, JSONObject item, boolean fromDesktop, boolean crop) {
        return imageFrame(pin, item, fromDesktop, crop, true);
    }
    private FrameLayout imageFrame(JSONObject pin, JSONObject item, boolean fromDesktop, boolean crop, boolean thumbnail) {
        FrameLayout frame = new FrameLayout(this); frame.setBackgroundColor(getColor(R.color.papan_background)); ImageView image = thumbnail ? new ImageView(this) : new ZoomImageView(this); if (thumbnail) image.setScaleType(crop ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER); image.setContentDescription(pin.optString("title")); frame.addView(image, new FrameLayout.LayoutParams(-1, -1)); TextView loading = text("Loading preview…", 13); loading.setTextColor(MUTED); loading.setGravity(Gravity.CENTER); frame.addView(loading, new FrameLayout.LayoutParams(-1, -1)); image.setTag(loading); loadImage(image, pin, item, fromDesktop, thumbnail); return frame;
    }
    private JSONObject cover(JSONObject pin, boolean fromDesktop) throws Exception {
        if (!fromDesktop) return pin.has("mediaFile") ? new JSONObject().put("kind", pin.optString("kind", "image")) : null;
        JSONArray items = pin.optJSONArray("items"); if (items == null) return null; JSONObject fallback = null;
        for (int i = 0; i < items.length(); i++) { JSONObject item = items.getJSONObject(i); if (item.optString("id").equals(pin.optString("coverId"))) return item; if (fallback == null && !"text".equals(item.optString("kind"))) fallback = item; } return fallback;
    }
    private void loadImage(ImageView view, JSONObject pin, JSONObject item, boolean fromDesktop, boolean thumbnail) {
        try {
            final String url = fromDesktop ? thumbnail ? item.optString("thumbnailUrl", item.optString("previewUrl")) : item.optString("previewUrl") : "";
            final File saved = fromDesktop ? null : MobileLibrary.get(this).mediaFile(pin.optString("mediaFile"));
            String key = fromDesktop ? "remote:" + pin.optString("collectionId") + ":" + desktop.sessionId() + ":" + url : "local:" + saved + ":" + (saved == null ? "" : saved.lastModified() + ":" + saved.length()); key += thumbnail ? ":thumbnail" : ":viewer";
            if (!thumbnail) { String previewKey = fromDesktop ? "remote:" + pin.optString("collectionId") + ":" + desktop.sessionId() + ":" + item.optString("thumbnailUrl", item.optString("previewUrl")) + ":thumbnail" : key.substring(0, key.length() - ":viewer".length()) + ":thumbnail"; Bitmap ready = previewImages.peek(previewKey); if (ready != null) { view.setImageBitmap(ready); ((TextView)view.getTag()).setVisibility(View.GONE); } }
            previewImages.load(key, () -> {
                if (!fromDesktop && "video".equals(item.optString("kind"))) {
                    if (saved == null) return null; android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                    try { retriever.setDataSource(saved.toString()); int width = Integer.parseInt(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)), height = Integer.parseInt(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)); double scale = Math.min(1, 512.0 / Math.max(width, height));
                        if (Build.VERSION.SDK_INT >= 27) return retriever.getScaledFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, Math.max(1, (int)Math.round(width * scale)), Math.max(1, (int)Math.round(height * scale)));
                        if ((long)width * height > 4096L * 4096) return null; Bitmap frame = retriever.getFrameAtTime(0); if (frame == null || scale >= 1) return frame; Bitmap reduced = Bitmap.createScaledBitmap(frame, Math.max(1, (int)Math.round(frame.getWidth() * scale)), Math.max(1, (int)Math.round(frame.getHeight() * scale)), true); if (reduced != frame) frame.recycle(); return reduced;
                    } finally { retriever.release(); }
                }
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true; byte[] bytes = null; File file = null;
                if (fromDesktop) { bytes = desktop.media(url); BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options); }
                else { file = saved; if (file == null) return null; BitmapFactory.decodeFile(file.toString(), options); }
                int maximum = thumbnail ? 512 : 1536; options.inSampleSize = 1; while (options.outWidth / options.inSampleSize > maximum || options.outHeight / options.inSampleSize > maximum) options.inSampleSize *= 2; options.inJustDecodeBounds = false;
                return fromDesktop ? BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options) : BitmapFactory.decodeFile(file.toString(), options);
            }, bitmap -> { if (!isDestroyed()) { view.setImageBitmap(bitmap); TextView loading = (TextView)view.getTag(); if (bitmap != null) { loading.setVisibility(View.GONE); if (fromDesktop && thumbnail && !measuredItem(item)) { JSONObject collection = find(remoteCollections, pin.optString("collectionId")); double before = frameRatio(collection); boolean changed = previewImages.measure(pin.optString("collectionId"), item.optString("id"), (double)bitmap.getWidth() / bitmap.getHeight()); if (changed && remote && selectedRemote.equals(pin.optString("collectionId")) && Math.abs(before - frameRatio(collection)) > .001) { pinGrid.removeCallbacks(resizeBoard); pinGrid.postOnAnimation(resizeBoard); } } } else { loading.setText("Preview unavailable"); view.setContentDescription("Preview unavailable. " + pin.optString("title")); } } });
        } catch (Exception ignored) { ((TextView)view.getTag()).setText("Preview unavailable"); }
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
            LinearLayout body = dialogBody(); TextView source = text(url, 14); source.setTextColor(MUTED); body.addView(source); body.addView(text("Phone", 18)); JSONArray local = MobileLibrary.get(this).snapshot().getJSONArray("collections");
            for (int i = 0; i < local.length(); i++) { JSONObject collection = local.getJSONObject(i); body.addView(button("Phone · " + collection.getString("name"), () -> saveLocal(url, collection.optString("id")), false)); }
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
            handler.removeCallbacks(browsePoll); JSONArray items = fromDesktop ? pin.optJSONArray("items") : new JSONArray(); File local = null;
            if (!fromDesktop) { JSONObject item = cover(pin, false); if (item != null) { items.put(item); local = MobileLibrary.get(this).mediaFile(pin.optString("mediaFile")); } }
            if (items == null || items.length() == 0) items = new JSONArray().put(new JSONObject().put("kind", "text").put("text", pin.optString("text", pin.optString("notes"))));
            boolean restore = pin.optString("id").equals(viewerPinId);
            PinViewer value = new PinViewer(this, pin, items, (item, thumbnail) -> imageFrame(pin, item, fromDesktop, false, thumbnail), fromDesktop ? desktop : null, local, () -> pinDetails(pin, fromDesktop), this::navigatePin, restore ? viewerItemId : null, restore ? viewerPosition : 0, !restore || viewerPlaying);
            if (fromDesktop && find(remoteCollections, selectedRemote) != null && find(remoteCollections, selectedRemote).optBoolean("protected")) value.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            detailDialog = value; viewerPinId = null; value.setOnDismissListener(d -> { if (detailDialog != value) return; handler.removeCallbacks(detailPoll); if (detailInfo != null) detailInfo.dismiss(); detailDialog = null; if (foreground) viewerPinId = null; if (remote && foreground && !movingViewer) loadDesktop(false, false); }); value.show();
            if (fromDesktop) handler.postDelayed(detailPoll, 3000);
        } catch (Exception error) { message(error.getMessage()); }
    }
    private void rememberViewer() { if (detailDialog != null) { viewerPinId = detailDialog.pin.optString("id"); viewerItemId = detailDialog.itemId(); viewerPosition = detailDialog.position(); viewerPlaying = detailDialog.playing(); } }
    private void restoreViewer() {
        if (!foreground || activityOpen || detailDialog != null || viewerPinId == null) return;
        try { JSONObject pin = find(remote ? remotePins : MobileLibrary.get(this).snapshot().getJSONArray("pins"), viewerPinId); if (pin != null) openPin(pin, remote); }
        catch (Exception error) { message(error.getMessage()); }
    }
    private boolean navigatePin(int delta) {
        if (detailDialog == null || !(pinGrid.getAdapter() instanceof PinAdapter)) return false;
        PinAdapter adapter = (PinAdapter)pinGrid.getAdapter(); int current = -1;
        for (int i = 0; i < adapter.pins.size(); i++) if (adapter.pins.get(i).optString("id").equals(detailDialog.pin.optString("id"))) current = i;
        if (current < 0 || current + delta < 0) return false;
        if (current + delta >= adapter.pins.size()) { if (remote && nextOffset >= 0 && delta > 0) { viewerAdvance = delta; viewerAdvanceFrom = detailDialog.pin.optString("id"); detailDialog.notice("Loading more pins…"); morePins(); return true; } return false; }
        JSONObject next = adapter.pins.get(current + delta); movingViewer = true; detailDialog.dismiss(); openPin(next, remote); movingViewer = false; ((GridLayoutManager)pinGrid.getLayoutManager()).scrollToPositionWithOffset(current + delta, 0); return true;
    }
    private void pinDetails(JSONObject pin, boolean fromDesktop) {
        try {
            if (detailInfo != null) detailInfo.dismiss(); LinearLayout body = dialogBody(); body.addView(text(pin.optString("title"), 20));
            if (!fromDesktop && !pin.optString("captureError").isEmpty()) { body.addView(text(pin.optString("captureError"), 14)); body.addView(button("Retry preview", () -> { try { MobileLibrary.get(this).retry(pin.getString("id")); if (detailDialog != null) detailDialog.dismiss(); capture(); } catch (Exception error) { message(error.getMessage()); } }, false)); }
            if (!pin.optString("notes").isEmpty()) body.addView(text(pin.optString("notes"), 16)); if (!pin.optString("text").isEmpty()) body.addView(text(pin.optString("text"), 16));
            if (fromDesktop && pin.optBoolean("textTruncated")) body.addView(text("Text preview shortened for your phone. Open the source for the full page.", 13));
            TextView source = text(pin.optString("sourceUrl"), 14); source.setTextIsSelectable(true); source.setTextColor(MUTED); body.addView(source);
            body.addView(button("Open source", () -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LocalCapture.webLink(pin.optString("sourceUrl"))))); } catch (Exception error) { message("No app could open this link."); } }, true));
            if (!fromDesktop) { body.addView(button("Edit title and notes", () -> editPin(pin), false)); body.addView(button("Save to another collection", () -> { if (detailDialog != null) detailDialog.dismiss(); chooseDestination(pin.optString("sourceUrl")); }, false)); body.addView(button("Delete from phone", () -> new MaterialAlertDialogBuilder(this).setTitle("Delete this phone pin?").setMessage("Its saved phone media will also be removed.").setNegativeButton("Keep", null).setPositiveButton("Delete", (d,w) -> { try { MobileLibrary.get(this).removePin(pin.getString("id")); if (detailDialog != null) detailDialog.dismiss(); refreshLocal(); } catch (Exception error) { message(error.getMessage()); } }).show(), false)); }
            body.addView(button("Close details", () -> { if (detailInfo != null) detailInfo.dismiss(); }, false)); detailInfo = destinationSheet(body);
            if (fromDesktop && find(remoteCollections, selectedRemote) != null && find(remoteCollections, selectedRemote).optBoolean("protected")) detailInfo.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } catch (Exception error) { message(error.getMessage()); }
    }
    private void checkDetailAccess() {
        if (!foreground || !remote || detailDialog == null) return;
        final PinViewer showing = detailDialog; final String id = selectedRemote;
        requests.execute(() -> { boolean permitted = false; try { JSONObject collection = find(desktop.collections(), id); permitted = collection != null && !collection.optBoolean("locked"); } catch (Exception ignored) {} final boolean allowed = permitted;
            runOnUiThread(() -> { if (isDestroyed() || showing != detailDialog) return; if (!allowed) { retainRemoteImages(new JSONArray()); remotePins = new JSONArray(); showing.dismiss(); message("Desktop access ended. Unlock or reconnect to continue."); } else handler.postDelayed(detailPoll, 3000); }); });
    }
    private void editPin(JSONObject pin) { LinearLayout body = dialogBody(); EditText title = field("Title", false), notes = field("Notes", true); title.setText(pin.optString("title")); notes.setText(pin.optString("notes")); addField(body, title); addField(body, notes); AlertDialog value = dialog("Edit phone pin", body); Button save = button("Save changes", () -> { try { MobileLibrary.get(this).edit(pin.getString("id"), title.getText().toString(), notes.getText().toString()); value.dismiss(); if (detailDialog != null) detailDialog.dismiss(); refreshLocal(); } catch (Exception error) { error(title, error.getMessage()); } }, true); body.addView(save); }
    private void connections() {
        settingsOpen = true; LinearLayout body = dialogBody(); body.addView(text("Enable Receive from phone on your computer. Pair desktop finds it nearby; approve the matching code there. Paired desktops reconnect automatically.", 15));
        connectionFeedback = text("", 14); connectionFeedback.setVisibility(View.GONE); connectionFeedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); body.addView(connectionFeedback);
        body.addView(button("Pair desktop", this::nearbyDesktops, true)); pairLink = field("Complete pairing link", false); pairLink.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); addField(body, pairLink);
        pairLink.setText(pairingDraft); body.addView(button("Paste pairing link instead", () -> { String copied = clipboardText(); if (copied != null) { pairLink.setText(copied); pair(copied); } else message("Copy the complete desktop pairing link first."); }, false)); body.addView(button("Pair from link", () -> pair(pairLink.getText().toString()), false));
        try { JSONObject state = PhoneStore.get(this).snapshot(); if (state.has("config")) { EditText address = field("http://192.168.1.10:47778", false); desktopAddress = address; address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); address.setText(addressDraft.isEmpty() ? state.getJSONObject("config").optString("endpoint") : addressDraft); addField(body, address); body.addView(button("Update desktop address", () -> { try { PhoneStore.get(this).endpoint(Delivery.endpoint(address.getText().toString())); addressDraft = ""; remotePins = new JSONArray(); remoteCollections = new JSONArray(); message("Desktop address updated; queued destinations were kept."); sync(); if (remote) loadDesktop(false, true); } catch (Exception error) { error(address, error.getMessage()); } }, false)); } } catch (Exception error) { message(error.getMessage()); }
        body.addView(text("Desktop links, passwords and media use HTTP on your trusted local network. Phone collections and previews stay in app-private storage. Phone and desktop collections are separate. Pending deliveries survive restarts; Android controls background retry timing.", 13)); connectionsDialog = dialog("Connections", body); connectionsDialog.setOnDismissListener(d -> { settingsOpen = false; connectionsDialog = null; });
    }
    private void scanPair() { try { JSONObject state = PhoneStore.get(this).snapshot(); if (state.has("config") && state.getJSONArray("entries").length() != 0) throw new Exception(PhoneStore.PAIRING_PENDING); startActivityForResult(new Intent(this, ScanActivity.class), 1); } catch (Exception error) { message(error.getMessage()); } }
    private void nearbyDesktops() { nearbyDialog = DesktopPairing.show(this, this::scanPair, this::paired); }
    private void paired() { pairingDraft = ""; if (pairLink != null) pairLink.setText(""); if (nearbyDialog != null) nearbyDialog.dismiss(); if (connectionsDialog != null) connectionsDialog.dismiss(); message("Paired with Papan."); sync(); switchLocation(true); }
    @Override public void onActivityResult(int request, int result, Intent data) { super.onActivityResult(request, result, data); if (request == 1 && result == RESULT_OK && data != null) pair(data.getStringExtra("pairingLink")); else if (request == 1) message("Pairing cancelled. Your existing pairing was kept."); }
    private void pair(String value) {
        message("Pairing…"); async(() -> { JSONObject before = PhoneStore.get(this).snapshot(); if (before.has("config") && before.getJSONArray("entries").length() != 0) throw new Exception(PhoneStore.PAIRING_PENDING); String name = Build.MANUFACTURER + " " + Build.MODEL; PhoneStore.get(this).pair(Delivery.pair(value, name.substring(0, Math.min(80, name.length())))); }, this::paired);
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

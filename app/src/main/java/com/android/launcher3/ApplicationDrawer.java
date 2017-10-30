/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.nfc.Tag;
import android.os.Handler;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.settings.SettingsProvider;
import com.dev.sadooo.decenthome.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The workspace is a wide area with a wallpaper and a finite number of pages.
 * Each page contains a number of icons, folders or widgets the user can
 * interact with. A workspace is meant to be used with a fixed width only.
 */
public class ApplicationDrawer extends SmoothPagedView
        implements DropTarget, DragSource, DragScroller,
        DragController.DragListener, LauncherTransitionable, ViewGroup.OnHierarchyChangeListener,
        Insettable, View.OnLongClickListener, View.OnClickListener {

    private Launcher mLauncher;
    private IconCache mIconCache;

    private DragController mDragController;
    private SpringLoadedDragController mSpringLoadedDragController;

    private DragEnforcer mDragEnforcer;
    private boolean mCreateUserFolderOnDrop = false;
    private boolean mAddToExistingFolderOnDrop = false;

    private static final int FOLDER_CREATION_TIMEOUT = 0;
    public static final int REORDER_TIMEOUT = 350;
    private CellLayout mDropToLayout = null;
    private CellLayout mDragTargetLayout = null;
    private CellLayout mDragOverlappingLayout = null;
    private final Alarm mFolderCreationAlarm = new Alarm();
    private final Alarm mReorderAlarm = new Alarm();
    private FolderIcon.FolderRingAnimator mDragFolderRingAnimator = null;
    private FolderIcon mDragOverFolderIcon = null;
    private float mMaxDistanceForFolderCreation;

    private int[] mTargetCell = new int[2];
    private int mDragOverX = -1;
    private int mDragOverY = -1;

    // These are temporary variables to prevent having to allocate a new object just to
    // return an (x, y) value from helper functions. Do NOT use them to maintain other state.
    private int[] mTempCell = new int[2];
    private int[] mTempPt = new int[2];
    private int[] mTempEstimate = new int[2];
    private float[] mDragViewVisualCenter = new float[2];
    private float[] mTempCellLayoutCenterCoordinates = new float[2];
    private Matrix mTempInverseMatrix = new Matrix();

    // These variables are used for storing the initial and final values during workspace animations
    private int mSavedScrollX;
    private float mSavedRotationY;
    private float mSavedTranslationX;

    // Related to dragging, folder creation and reordering
    private static final int DRAG_MODE_NONE = 0;
    private static final int DRAG_MODE_CREATE_FOLDER = 1;
    private static final int DRAG_MODE_ADD_TO_FOLDER = 2;
    private static final int DRAG_MODE_REORDER = 3;
    private int mDragMode = DRAG_MODE_NONE;
    private int mLastReorderX = -1;
    private int mLastReorderY = -1;

    private static final int ADJACENT_SCREEN_DROP_DURATION = 300;

    protected static final int SNAP_OFF_EMPTY_SCREEN_DURATION = 400;
    protected static final int FADE_EMPTY_SCREEN_DURATION = 150;

    private static final float ALPHA_CUTOFF_THRESHOLD = 0.01f;

    private static final int BACKGROUND_FADE_OUT_DURATION = 350;

    private ValueAnimator mBackgroundFadeInAnimation;
    private ValueAnimator mBackgroundFadeOutAnimation;

    private CellLayout.CellInfo mDragInfo;

    /** Is the user is dragging an item near the edge of a page? */
    private boolean mInScrollArea = false;

    Launcher.CustomContentCallbacks mCustomContentCallbacks;
    boolean mCustomContentShowing;
    private float mLastCustomContentScrollProgress = -1f;
    private String mCustomContentDescription = "";

    float mOverScrollEffect = 0f;

    private HolographicOutlineHelper mOutlineHelper;
    private Bitmap mDragOutline = null;
    private static final Rect sTempRect = new Rect();
    private final int[] mTempXY = new int[2];
    private int[] mTempVisiblePagesRange = new int[2];
    private boolean mOverscrollEffectSet;
    public static final int DRAG_BITMAP_PADDING = 2;
    private boolean mWorkspaceFadeInAdjacentScreens;

    private final Canvas mCanvas = new Canvas();

    private ShortcutAndWidgetContainer mDragSourceInternal;
    private static boolean sAccessibilityEnabled;

    private Point mDisplaySize = new Point();

    private int mContentWidth, mContentHeight;

    enum State {NORMAL, NORMAL_HIDDEN, SPRING_LOADED, OVERVIEW, OVERVIEW_HIDDEN}
    private State mState = State.NORMAL;

    private boolean mIsSwitchingState = false;
    boolean mAnimatingViewIntoPlace = false;
    boolean mChildrenLayersEnabled = true;

    boolean mIsDragOccuring = false;
    private Runnable mRemoveEmptyScreenRunnable;
    private boolean mDeferRemoveExtraEmptyScreen = false;

    private float mCurrentScale;
    private float mNewScale;
    private float[] mOldBackgroundAlphas;
    private float[] mOldAlphas;
    private float[] mNewBackgroundAlphas;
    private float[] mNewAlphas;
    private int mLastChildCount = -1;
    private float mTransitionProgress;
    private Animator mStateAnimator = null;

    private boolean mShowOutlines;
    private boolean mHideIconLabels;

    private float mSpringLoadedShrinkFactor;
    private float mOverviewModeShrinkFactor;

    static final boolean MAP_NO_RECURSE = false;
    static final boolean MAP_RECURSE = true;

    private Runnable mDeferredAction;
    private boolean mDeferDropAfterUninstall;
    private boolean mUninstallSuccessful;

    public boolean isInternalDrop = false;

    public View longClickedItem = null;

    private HashMap<Long, CellLayout> mDrawerScreens = new HashMap<Long, CellLayout>();
    private ArrayList<Long> mScreenOrder = new ArrayList<Long>();

    public static HashMap<Long, FolderInfo> sFolders = new HashMap<Long, FolderInfo>();

    final static long EXTRA_EMPTY_SCREEN_ID = -101;
    private final static long CUSTOM_CONTENT_SCREEN_ID = -301;

    private String TAG = "ApplicationDrawer";

    public ApplicationDrawer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ApplicationDrawer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContentIsRefreshable = false;

        mLauncher = (Launcher) context;

        mFadeInAdjacentScreens = false;

        mDragEnforcer = new DragEnforcer(context);

        mOutlineHelper = HolographicOutlineHelper.obtain(context);

        mSpringLoadedShrinkFactor =
                getResources().getInteger(R.integer.config_workspaceSpringLoadShrinkPercentage) / 100.0f;

        TransitionEffect.setFromString(this, SettingsProvider.getString(mLauncher,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_SCROLLING_TRANSITION_EFFECT,
                R.string.preferences_interface_homescreen_scrolling_transition_effect));

        initDrawer();

        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    protected void initDrawer() {
        //Not fully available
        Launcher.setScreen(mCurrentPage);
        LauncherAppState app = LauncherAppState.getInstance();
        mIconCache = app.getIconCache();

        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        SharedPreferences sp = mLauncher.getSharedPrefs();
        if(sp.getInt(Launcher.DRAWER_COUNTX_SAVED, 0) == 0){
            SharedPreferences.Editor editor = sp.edit();
            editor.putInt(Launcher.DRAWER_COUNTX_SAVED, grid.allAppsNumCols);
            editor.commit();
        }
        mCellCountX = sp.getInt(Launcher.DRAWER_COUNTX_SAVED, 0);

        if(sp.getInt(Launcher.DRAWER_COUNTY_SAVED, 0) == 0){
            SharedPreferences.Editor editor = sp.edit();
            editor.putInt(Launcher.DRAWER_COUNTY_SAVED, grid.allAppsNumRows);
            editor.commit();
        }
        mCellCountY = sp.getInt(Launcher.DRAWER_COUNTY_SAVED, 0);

        mOverviewModeShrinkFactor = grid.getOverviewModeScale();

        mContentWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        mContentHeight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();

        mMaxDistanceForFolderCreation = (0.55f * grid.iconSizePx);

        Display display = mLauncher.getWindowManager().getDefaultDisplay();
        display.getSize(mDisplaySize);

    }

    private final Runnable mBindPages = new Runnable() {
        @Override
        public void run() {
            mLauncher.getModel().bindRemainingSynchronousPages();
        }
    };

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        post(mBindPages);
    }

    protected void onResume() {
        if (getPageIndicator() != null) {
            // In case accessibility state has changed, we need to perform this on every
            // attach to window
            OnClickListener listener = getPageIndicatorClickListener();
            if (listener != null) {
                getPageIndicator().setOnClickListener(listener);
            }
        }
        AccessibilityManager am = (AccessibilityManager)
                getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        sAccessibilityEnabled = am.isEnabled();

        /*mScrollWallpaper = SettingsProvider.getBoolean(mLauncher,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_SCROLLING_WALLPAPER_SCROLL,
                R.bool.preferences_interface_homescreen_scrolling_wallpaper_scroll_default);*/

        /*if (!mScrollWallpaper) {
            if (mWindowToken != null) mWallpaperManager.setWallpaperOffsets(mWindowToken, 0f, 0.5f);
        } else {
            mWallpaperOffset.syncWithScroll();
        }*/

        // Update wallpaper dimensions if they were changed since last onResume
        // (we also always set the wallpaper dimensions in the constructor)
        /*if (LauncherAppState.getInstance().hasWallpaperChangedSinceLastCheck()) {
            setWallpaperDimension();
        }
        mWallpaperIsLiveWallpaper = mWallpaperManager.getWallpaperInfo() != null;
        // Force the wallpaper offset steps to be set again, because another app might have changed
        // them
        mLastSetWallpaperOffsetSteps = 0f;*/
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        post(new Runnable() {
            // This code triggers requestLayout so must be posted outside of the
            // layout pass.
            public void run() {
                if (Utilities.isViewAttachedToWindow(ApplicationDrawer.this)) {
                    setDataIsReady();
                }
            }
        });
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    public boolean onLongClick(View v) {
        longClickedItem = v;
        CellLayout.CellInfo longClickCellInfo = null;
        View itemUnderLongClick = null;
        if (v instanceof BubbleTextView) {
            ItemInfo info = (ItemInfo) v.getTag();
            longClickCellInfo = new CellLayout.CellInfo(v, info);
            itemUnderLongClick = longClickCellInfo.cell;
        } else if(v instanceof FolderIcon){
            ItemInfo info = (ItemInfo) v.getTag();
            longClickCellInfo = new CellLayout.CellInfo(v, info);
            itemUnderLongClick = longClickCellInfo.cell;
        }
        if(itemUnderLongClick != null){
            startDrag(longClickCellInfo);
        }
        return true;
    }

    @Override
    public void onClick(View v){
        if (v instanceof FolderIcon) {
            FolderIcon folderIcon = (FolderIcon) v;
            final FolderInfo info = folderIcon.getFolderInfo();
            Folder openFolder = getFolderForTag(info);

            int[] touchXY = new int[2];
            v.getLocationOnScreen(touchXY);


            // If the folder info reports that the associated folder is open, then verify that
            // it is actually opened. There have been a few instances where this gets out of sync.
            if (info.opened && openFolder == null) {
                Log.d(TAG, "Folder info marked as open, but associated folder is not open. Screen: "
                        + info.screenId + " (" + info.cellX + ", " + info.cellY + ")");
                info.opened = false;
            }

            if (!info.opened && !folderIcon.getFolder().isDestroyed()) {
                // Close any open folder
                mLauncher.closeFolder();
                // Open the requested folder
                mLauncher.openFolder(folderIcon, touchXY);
            } else {
                // Find the open folder...
                int folderScreen;
                if (openFolder != null) {
                    folderScreen = getPageForView(openFolder);
                    // .. and close it
                    mLauncher.closeFolder(openFolder);
                    if (folderScreen != getCurrentPage()) {
                        // Close any folder open on the current screen
                        mLauncher.closeFolder();
                        // Pull the folder onto this screen
                        mLauncher.openFolder(folderIcon, touchXY);
                    }
                }
            }
        }
    }

    /***********************************************************************************************
     * Methods related to the onClick are below
     */
    public Folder getFolderForTag(final Object tag) {
        return (Folder) getFirstMatch(new ItemOperator() {

            @Override
            public boolean evaluate(ItemInfo info, View v, View parent) {
                return (v instanceof Folder) && (((Folder) v).getInfo() == tag)
                        && ((Folder) v).getInfo().opened;
            }
        });
    }

    private View getFirstMatch(final ItemOperator operator) {
        final View[] value = new View[1];
        mapOverItems(MAP_NO_RECURSE, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v, View parent) {
                if (operator.evaluate(info, v, parent)) {
                    value[0] = v;
                    return true;
                }
                return false;
            }
        });
        return value[0];
    }

    void mapOverItems(boolean recurse, ItemOperator op) {
        ArrayList<ShortcutAndWidgetContainer> containers = getAllShortcutAndWidgetContainers();
        final int containerCount = containers.size();
        for (int containerIdx = 0; containerIdx < containerCount; containerIdx++) {
            ShortcutAndWidgetContainer container = containers.get(containerIdx);
            // map over all the shortcuts on the workspace
            final int itemCount = container.getChildCount();
            for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
                View item = container.getChildAt(itemIdx);
                ItemInfo info = (ItemInfo) item.getTag();
                if (recurse && info instanceof FolderInfo && item instanceof FolderIcon) {
                    FolderIcon folder = (FolderIcon) item;
                    ArrayList<View> folderChildren = folder.getFolder().getItemsInReadingOrder();
                    // map over all the children in the folder
                    final int childCount = folderChildren.size();
                    for (int childIdx = 0; childIdx < childCount; childIdx++) {
                        View child = folderChildren.get(childIdx);
                        info = (ItemInfo) child.getTag();
                        if (op.evaluate(info, child, folder)) {
                            return;
                        }
                    }
                } else {
                    if (op.evaluate(info, item, null)) {
                        return;
                    }
                }
            }
        }
    }

    interface ItemOperator {
        /**
         * Process the next itemInfo, possibly with side-effect on {@link ItemOperator#value}.
         *
         * @param info info for the shortcut
         * @param view view for the shortcut
         * @param parent containing folder, or null
         * @return true if done, false to continue the map
         */
        public boolean evaluate(ItemInfo info, View view, View parent);
    }

    ArrayList<ShortcutAndWidgetContainer> getAllShortcutAndWidgetContainers() {
        ArrayList<ShortcutAndWidgetContainer> childrenLayouts =
                new ArrayList<ShortcutAndWidgetContainer>();
        int screenCount = getChildCount();
        for (int screen = 0; screen < screenCount; screen++) {
            childrenLayouts.add(((CellLayout) getChildAt(screen)).getShortcutsAndWidgets());
        }
        return childrenLayouts;
    }

    /**********************************************************************************************/

    public void setFinalScrollForPageChange(int pageIndex) {
        CellLayout cl = (CellLayout) getChildAt(pageIndex);
        if (cl != null) {
            mSavedScrollX = getScrollX();
            mSavedTranslationX = cl.getTranslationX();
            mSavedRotationY = cl.getRotationY();
            final int newX = getScrollForPage(pageIndex);
            setScrollX(newX);
            cl.setTranslationX(0f);
            cl.setRotationY(0f);
        }
    }

    public void resetFinalScrollForPageChange(int pageIndex) {
        if (pageIndex >= 0) {
            CellLayout cl = (CellLayout) getChildAt(pageIndex);
            setScrollX(mSavedScrollX);
            cl.setTranslationX(mSavedTranslationX);
            cl.setRotationY(mSavedRotationY);
        }
    }

    void updateShortcuts(ArrayList<ShortcutInfo> shortcuts) {
        final HashSet<ShortcutInfo> updates = new HashSet<ShortcutInfo>(shortcuts);
        mapOverItems(MAP_RECURSE, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v, View parent) {
                if (info instanceof ShortcutInfo && v instanceof BubbleTextView &&
                        updates.contains(info)) {
                    ShortcutInfo si = (ShortcutInfo) info;
                    BubbleTextView shortcut = (BubbleTextView) v;
                    boolean oldPromiseState = shortcut.getCompoundDrawables()[1]
                            instanceof PreloadIconDrawable;
                    shortcut.applyFromShortcutInfo(si, mIconCache, true,
                            si.isPromise() != oldPromiseState);

                    if (parent != null) {
                        parent.invalidate();
                    }
                }
                // process all the shortcuts
                return false;
            }
        });
    }

    public void removeAbandonedPromise(String packageName, UserHandleCompat user) {
        ArrayList<String> packages = new ArrayList<String>(1);
        packages.add(packageName);
        LauncherModel.deletePackageFromDrawerDatabase(mLauncher, packageName, user);
        removeItemsByPackageName(packages, user);
    }

    void removeItemsByPackageName(final ArrayList<String> packages, final UserHandleCompat user) {
        final HashSet<String> packageNames = new HashSet<String>();
        packageNames.addAll(packages);

        // Filter out all the ItemInfos that this is going to affect
        final HashSet<ItemInfo> infos = new HashSet<ItemInfo>();
        final HashSet<ComponentName> cns = new HashSet<ComponentName>();
        ArrayList<CellLayout> cellLayouts = getCellLayouts();
        for (CellLayout layoutParent : cellLayouts) {
            ViewGroup layout = layoutParent.getShortcutsAndWidgets();
            int childCount = layout.getChildCount();
            for (int i = 0; i < childCount; ++i) {
                View view = layout.getChildAt(i);
                infos.add((ItemInfo) view.getTag());
            }
        }
        LauncherModel.ItemInfoFilter filter = new LauncherModel.ItemInfoFilter() {
            @Override
            public boolean filterItem(ItemInfo parent, ItemInfo info,
                                      ComponentName cn) {
                if (packageNames.contains(cn.getPackageName())
                        && info.user.equals(user)) {
                    cns.add(cn);
                    return true;
                }
                return false;
            }
        };
        LauncherModel.filterItemInfos(infos, filter);

        for (String pn : packageNames) {
            LauncherModel.deletePackageFromDrawerDatabase(mLauncher, pn, user);
        }

        // Remove the affected components
        removeItemsByComponentName(cns, user);
    }

    void removeItemsByComponentName(final HashSet<ComponentName> componentNames,
                                    final UserHandleCompat user) {
        ArrayList<CellLayout> cellLayouts = getCellLayouts();
        for (final CellLayout layoutParent: cellLayouts) {
            final ViewGroup layout = layoutParent.getShortcutsAndWidgets();

            final HashMap<ItemInfo, View> children = new HashMap<ItemInfo, View>();
            for (int j = 0; j < layout.getChildCount(); j++) {
                final View view = layout.getChildAt(j);
                children.put((ItemInfo) view.getTag(), view);
            }

            final ArrayList<View> childrenToRemove = new ArrayList<View>();
            final HashMap<FolderInfo, ArrayList<ShortcutInfo>> folderAppsToRemove =
                    new HashMap<FolderInfo, ArrayList<ShortcutInfo>>();
            LauncherModel.ItemInfoFilter filter = new LauncherModel.ItemInfoFilter() {
                @Override
                public boolean filterItem(ItemInfo parent, ItemInfo info,
                                          ComponentName cn) {
                    if (parent instanceof FolderInfo) {
                        if (componentNames.contains(cn) && info.user.equals(user)) {
                            FolderInfo folder = (FolderInfo) parent;
                            ArrayList<ShortcutInfo> appsToRemove;
                            if (folderAppsToRemove.containsKey(folder)) {
                                appsToRemove = folderAppsToRemove.get(folder);
                            } else {
                                appsToRemove = new ArrayList<ShortcutInfo>();
                                folderAppsToRemove.put(folder, appsToRemove);
                            }
                            appsToRemove.add((ShortcutInfo) info);
                            return true;
                        }
                    } else {
                        if (componentNames.contains(cn) && info.user.equals(user)) {
                            childrenToRemove.add(children.get(info));
                            return true;
                        }
                    }
                    return false;
                }
            };
            LauncherModel.filterItemInfos(children.keySet(), filter);

            // Remove all the apps from their folders
            for (FolderInfo folder : folderAppsToRemove.keySet()) {
                ArrayList<ShortcutInfo> appsToRemove = folderAppsToRemove.get(folder);
                for (ShortcutInfo info : appsToRemove) {
                    folder.remove(info);
                }
            }

            // Remove all the other children
            for (View child : childrenToRemove) {
                // Note: We can not remove the view directly from CellLayoutChildren as this
                // does not re-mark the spaces as unoccupied.
                layoutParent.removeViewInLayout(child);
                if (child instanceof DropTarget) {
                    mDragController.removeDropTarget((DropTarget) child);
                }
            }

            if (childrenToRemove.size() > 0) {
                layout.requestLayout();
                layout.invalidate();

                ArrayList<ItemInfo> infos = new ArrayList<>();
                for (View v : childrenToRemove) {
                    infos.add((ItemInfo)v.getTag());
                }
                LauncherModel.deleteDrawerItemsFromDatabase(mLauncher, infos);
            }
        }

        // Strip all the empty screens
        stripEmptyScreens();
    }

    ArrayList<CellLayout> getCellLayouts() {
        ArrayList<CellLayout> layouts = new ArrayList<CellLayout>();
        int screenCount = getChildCount();
        for (int screen = 0; screen < screenCount; screen++) {
            layouts.add(((CellLayout) getChildAt(screen)));
        }
        return layouts;
    }

    @Override
    public void syncPages() {
    }

    @Override
    public void syncPageItems(int page, boolean immediate) {
    }

    public void getLocationInDragLayer(int[] loc) {
        mLauncher.getDragLayer().getLocationInDragLayer(this, loc);
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        // We want the workspace to have the whole area of the display (it will find the correct
        // cell layout to drop to in the existing drag/drop logic.
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(this, outRect);
    }

    @Override
    public View getContent() {
        return this;
    }

    static class AlphaUpdateListener implements ValueAnimator.AnimatorUpdateListener, Animator.AnimatorListener {
        View view;
        float endAlpha;
        public AlphaUpdateListener(View v, float target) {
            view = v;
            endAlpha = target;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator arg0) {
            updateVisibility(view);
        }

        public static void updateVisibility(View view) {
            // We want to avoid the extra layout pass by setting the views to GONE unless
            // accessibility is on, in which case not setting them to GONE causes a glitch.
            int invisibleState = sAccessibilityEnabled ? GONE : INVISIBLE;
            if (view.getAlpha() < ALPHA_CUTOFF_THRESHOLD && view.getVisibility() != invisibleState) {
                view.setVisibility(invisibleState);
            } else if (view.getAlpha() > ALPHA_CUTOFF_THRESHOLD
                    && view.getVisibility() != VISIBLE) {
                view.setVisibility(VISIBLE);
            }
        }

        @Override
        public void onAnimationCancel(Animator arg0) {
        }

        @Override
        public void onAnimationEnd(Animator arg0) {
            // in low power mode the animation doesn't play, so we need to set the end alpha
            // before calling updateVisibility
            view.setAlpha(endAlpha);
            updateVisibility(view);
        }

        @Override
        public void onAnimationRepeat(Animator arg0) {
        }

        @Override
        public void onAnimationStart(Animator arg0) {
            // We want the views to be visible for animation, so fade-in/out is visible
            view.setVisibility(VISIBLE);
        }
    }

    @Override
    public void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace) {
        onTransitionPrepare();
    }

    @Override
    public void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace) {
    }

    @Override
    public void onLauncherTransitionStep(Launcher l, float t) {
        mTransitionProgress = t;
    }

    @Override
    public void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace) {
        onTransitionEnd();
    }

    @Override
    public void onFlingToDelete(DragObject d, int x, int y, PointF vec) {
        // Do nothing
    }

    @Override
    public void onFlingToDeleteCompleted() {
        // Do nothing
    }

    protected void onEndReordering() {
        super.onEndReordering();
        mScreenOrder.clear();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            CellLayout cl = ((CellLayout) getChildAt(i));
            mScreenOrder.add(getIdForScreen(cl));
        }

        mLauncher.getModel().updateDrawerScreenOrder(mLauncher, mScreenOrder);
    }

    void startDrag(CellLayout.CellInfo cellInfo) {
        View child = cellInfo.cell;

        // Make sure the drag was started by a long press as opposed to a long click.
        if (!child.isInTouchMode()) {
            return;
        }

        mDragInfo = cellInfo;
        child.setVisibility(INVISIBLE);
        CellLayout layout = (CellLayout) child.getParent().getParent();
        layout.prepareChildForDrag(child);

        beginDragShared(child, this);
    }

    public void beginDragShared(View child, DragSource source) {
        child.clearFocus();
        child.setPressed(false);

        // The outline is used to visualize where the item will land if dropped
        mDragOutline = createDragOutline(child, DRAG_BITMAP_PADDING);

        mLauncher.onDragStarted(child);
        // The drag bitmap follows the touch point around on the screen
        AtomicInteger padding = new AtomicInteger(DRAG_BITMAP_PADDING);
        final Bitmap b = createDragBitmap(child, padding);

        final int bmpWidth = b.getWidth();
        final int bmpHeight = b.getHeight();

        float scale = mLauncher.getDragLayer().getLocationInDragLayer(child, mTempXY);
        int dragLayerX = Math.round(mTempXY[0] - (bmpWidth - scale * child.getWidth()) / 2);
        int dragLayerY = Math.round(mTempXY[1] - (bmpHeight - scale * bmpHeight) / 2
                - padding.get() / 2);

        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        Point dragVisualizeOffset = null;
        Rect dragRect = null;
        if (child instanceof BubbleTextView) {
            int iconSize = grid.iconSizePx;
            int top = child.getPaddingTop();
            int left = (bmpWidth - iconSize) / 2;
            int right = left + iconSize;
            int bottom = top + iconSize;
            dragLayerY += top;
            // Note: The drag region is used to calculate drag layer offsets, but the
            // dragVisualizeOffset in addition to the dragRect (the size) to position the outline.
            dragVisualizeOffset = new Point(-padding.get() / 2, padding.get() / 2);
            dragRect = new Rect(left, top, right, bottom);
        } else if (child instanceof FolderIcon) {
            int previewSize = grid.folderIconSizePx;
            dragRect = new Rect(0, child.getPaddingTop(), child.getWidth(), previewSize);
        }

        // Clear the pressed state if necessary
        if (child instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView) child;
            icon.clearPressedBackground();
        }

        if (child.getTag() == null || !(child.getTag() instanceof ItemInfo)) {
            String msg = "Drag started with a view that has no tag set. This "
                    + "will cause a crash (issue 11627249) down the line. "
                    + "View: " + child + "  tag: " + child.getTag();
            throw new IllegalStateException(msg);
        }

        DragView dv = mDragController.startDrag(b, dragLayerX, dragLayerY, source, child.getTag(),
                DragController.DRAG_ACTION_MOVE, dragVisualizeOffset, dragRect, scale);

        dv.setIntrinsicIconScaleFactor(source.getIntrinsicIconScaleFactor());

        if (child.getParent() instanceof ShortcutAndWidgetContainer) {
            mDragSourceInternal = (ShortcutAndWidgetContainer) child.getParent();
            mDragSourceInternal.requestLayout();
        }

        b.recycle();
    }

    public View getViewForTag(final Object tag) {
        return getFirstMatch(new ItemOperator() {

            @Override
            public boolean evaluate(ItemInfo info, View v, View parent) {
                return info == tag;
            }
        });
    }

    private Bitmap createDragOutline(View v, int padding) {
        final int outlineColor = getResources().getColor(R.color.outline_color);
        final Bitmap b = Bitmap.createBitmap(
                v.getWidth() + padding, v.getHeight() + padding, Bitmap.Config.ARGB_8888);

        mCanvas.setBitmap(b);
        drawDragView(v, mCanvas, padding);
        mOutlineHelper.applyExpensiveOutlineWithBlur(b, mCanvas, outlineColor, outlineColor);
        mCanvas.setBitmap(null);
        return b;
    }

    private static void drawDragView(View v, Canvas destCanvas, int padding) {
        final Rect clipRect = sTempRect;
        v.getDrawingRect(clipRect);

        boolean textVisible = false;

        destCanvas.save();
        if (v instanceof TextView) {
            Drawable d = ((TextView) v).getCompoundDrawables()[1];
            Rect bounds = getDrawableBounds(d);
            clipRect.set(0, 0, bounds.width() + padding, bounds.height() + padding);
            destCanvas.translate(padding / 2 - bounds.left, padding / 2 - bounds.top);
            d.draw(destCanvas);
        } else {
            if (v instanceof FolderIcon) {
                // For FolderIcons the text can bleed into the icon area, and so we need to
                // hide the text completely (which can't be achieved by clipping).
                if (((FolderIcon) v).getTextVisible()) {
                    ((FolderIcon) v).setTextVisible(false);
                    textVisible = true;
                }
            }
            destCanvas.translate(-v.getScrollX() + padding / 2, -v.getScrollY() + padding / 2);
            destCanvas.clipRect(clipRect, Region.Op.REPLACE);
            v.draw(destCanvas);

            // Restore text visibility of FolderIcon if necessary
            if (textVisible) {
                ((FolderIcon) v).setTextVisible(true);
            }
        }
        destCanvas.restore();
    }

    public Bitmap createDragBitmap(View v, AtomicInteger expectedPadding) {
        Bitmap b;

        int padding = expectedPadding.get();
        if (v instanceof TextView) {
            Drawable d = ((TextView) v).getCompoundDrawables()[1];
            Rect bounds = getDrawableBounds(d);
            b = Bitmap.createBitmap(bounds.width() + padding,
                    bounds.height() + padding, Bitmap.Config.ARGB_8888);
            expectedPadding.set(padding - bounds.left - bounds.top);
        } else {
            b = Bitmap.createBitmap(
                    v.getWidth() + padding, v.getHeight() + padding, Bitmap.Config.ARGB_8888);
        }

        mCanvas.setBitmap(b);
        drawDragView(v, mCanvas, padding);
        mCanvas.setBitmap(null);

        return b;
    }

    private static Rect getDrawableBounds(Drawable d) {
        Rect bounds = new Rect();
        d.copyBounds(bounds);
        if (bounds.width() == 0 || bounds.height() == 0) {
            bounds.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        } else {
            bounds.offsetTo(0, 0);
        }
        if (d instanceof PreloadIconDrawable) {
            int inset = -((PreloadIconDrawable) d).getOutset();
            bounds.inset(inset, inset);
        }
        return bounds;
    }

    public void onDragStart(final DragSource source, Object info, int dragAction) {
        mIsDragOccuring = true;
        updateChildrenLayersEnabled(false);
        mLauncher.lockScreenOrientation();
        mLauncher.onInteractionBegin();

        mLauncher.getSearchBar().setVisibility(VISIBLE);

        setChildrenBackgroundAlphaMultipliers(1f);
        // Prevent any Un/InstallShortcutReceivers from updating the db while we are dragging
        // Commented because there is no install and uninstall shortcut receiver implemented for ApplicationDrawer
        //InstallShortcutReceiver.enableInstallQueue();
        //UninstallShortcutReceiver.enableUninstallQueue();
        post(new Runnable() {
            @Override
            public void run() {
                if (mIsDragOccuring) {
                    mDeferRemoveExtraEmptyScreen = false;
                    addExtraEmptyScreenOnDrag();
                }
            }
        });
    }

    public void addExtraEmptyScreenOnDrag() {
        // Log to disk
        Launcher.addDumpLog(TAG, "11683562 - addExtraEmptyScreenOnDrag()", true);

        boolean lastChildOnScreen = false;
        boolean childOnFinalScreen = false;

        // Cancel any pending removal of empty screen
        mRemoveEmptyScreenRunnable = null;

        if (mDragSourceInternal != null) {
            if (mDragSourceInternal.getChildCount() == 1) {
                lastChildOnScreen = true;
            }
            CellLayout cl = (CellLayout) mDragSourceInternal.getParent();
            if (indexOfChild(cl) == getChildCount() - 1) {
                childOnFinalScreen = true;
            }
        }

        // If this is the last item on the final screen
        if (lastChildOnScreen && childOnFinalScreen) {
            return;
        }
        if (!mDrawerScreens.containsKey(EXTRA_EMPTY_SCREEN_ID)) {
            insertNewScreen(EXTRA_EMPTY_SCREEN_ID);
        }

        if(getPageIndicator() != null){
            try {
                getPageIndicator().updateMarker(getChildCount() - 1, getPageIndicatorMarker(getChildCount() - 1));
            }catch (Exception ex){}
        }
        /*if(getPageIndicator().mMarkers.size() > getChildCount()){
            for(int i = getChildCount(); i < getPageIndicator().mMarkers.size(); i++){
                getPageIndicator().removeMarker(i, false);
            }
        }*/
    }

    public boolean addExtraEmptyScreen() {
        // Log to disk
        Launcher.addDumpLog(TAG, "11683562 - addExtraEmptyScreen()", true);

        if (!mDrawerScreens.containsKey(EXTRA_EMPTY_SCREEN_ID)) {
            insertNewScreen(EXTRA_EMPTY_SCREEN_ID);
            return true;
        }
        return false;
    }

    private void setChildrenBackgroundAlphaMultipliers(float a) {
        for (int i = 0; i < getChildCount(); i++) {
            CellLayout child = (CellLayout) getChildAt(i);
            child.setBackgroundAlphaMultiplier(a);
        }
    }

    public void onDragEnter(DragObject d) {
        mDragEnforcer.onDragEnter();
        mCreateUserFolderOnDrop = false;
        mAddToExistingFolderOnDrop = false;
        isInternalDrop = true;

        mDropToLayout = null;
        CellLayout layout = getCurrentDropLayout();
        setCurrentDropLayout(layout);
        setCurrentDragOverlappingLayout(layout);

        if (!inModalState()) {
            mLauncher.getDragLayer().showPageHints();
        }
    }

    public boolean inModalState() {
        return mState != State.NORMAL;
    }

    void setCurrentDropLayout(CellLayout layout) {
        if (mDragTargetLayout != null) {
            mDragTargetLayout.revertTempState();
            mDragTargetLayout.onDragExit();
        }
        mDragTargetLayout = layout;
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragEnter();
        }
        cleanupReorder(true);
        cleanupFolderCreation();
        setCurrentDropOverCell(-1, -1);
    }

    private void cleanupReorder(boolean cancelAlarm) {
        // Any pending reorders are canceled
        if (cancelAlarm) {
            mReorderAlarm.cancelAlarm();
        }
        mLastReorderX = -1;
        mLastReorderY = -1;
    }

    private void cleanupFolderCreation() {
        if (mDragFolderRingAnimator != null) {
            mDragFolderRingAnimator.animateToNaturalState();
            mDragFolderRingAnimator = null;
        }
        mFolderCreationAlarm.setOnAlarmListener(null);
        mFolderCreationAlarm.cancelAlarm();
    }

    void setCurrentDropOverCell(int x, int y) {
        if (x != mDragOverX || y != mDragOverY) {
            mDragOverX = x;
            mDragOverY = y;
            setDragMode(DRAG_MODE_NONE);
        }
    }

    void setDragMode(int dragMode) {
        if (dragMode != mDragMode) {
            if (dragMode == DRAG_MODE_NONE) {
                cleanupAddToFolder();
                // We don't want to cancel the re-order alarm every time the target cell changes
                // as this feels to slow / unresponsive.
                cleanupReorder(false);
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_ADD_TO_FOLDER) {
                cleanupReorder(true);
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_CREATE_FOLDER) {
                cleanupAddToFolder();
                cleanupReorder(true);
            } else if (dragMode == DRAG_MODE_REORDER) {
                cleanupAddToFolder();
                cleanupFolderCreation();
            }
            mDragMode = dragMode;
        }
    }

    private void cleanupAddToFolder() {
        if (mDragOverFolderIcon != null) {
            mDragOverFolderIcon.onDragExit(null);
            mDragOverFolderIcon = null;
        }
    }

    void setCurrentDragOverlappingLayout(CellLayout layout) {
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(false);
        }
        mDragOverlappingLayout = layout;
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(true);
        }
        invalidate();
    }

    public CellLayout getCurrentDropLayout() {
        return (CellLayout) getChildAt(getNextPage());
    }

    public void onDragOver(DragObject d) {
        // Skip drag over events while we are dragging over side pages
        if (mInScrollArea || !transitionStateShouldAllowDrop()) return;

        CellLayout layout = null;
        ItemInfo item = (ItemInfo) d.dragInfo;
        if (item == null) {
            if (LauncherAppState.isDogfoodBuild()) {
                throw new NullPointerException("DragObject has null info");
            }
            return;
        }

        // Ensure that we have proper spans for the item that we are dropping
        if (item.spanX < 0 || item.spanY < 0) throw new RuntimeException("Improper spans found");
        mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset,
                d.dragView, mDragViewVisualCenter);


        final View child = (mDragInfo == null) ? null : mDragInfo.cell;
        // Identify whether we have dragged over a side page
        if (inModalState()) {
            if (layout == null) {
                layout = findMatchingPageForDragOver(d.dragView, d.x, d.y, false);
            }
            if (layout != mDragTargetLayout) {
                setCurrentDropLayout(layout);
                setCurrentDragOverlappingLayout(layout);

                boolean isInSpringLoadedMode = (mState == State.SPRING_LOADED);
                if (isInSpringLoadedMode) {
                    mSpringLoadedDragController.setAlarm(mDragTargetLayout);
                }
            }
        } else {
            if (layout == null) {
                layout = getCurrentDropLayout();
            }
            if (layout != mDragTargetLayout) {
                setCurrentDropLayout(layout);
                setCurrentDragOverlappingLayout(layout);
            }
        }

        // Handle the drag over
        if (mDragTargetLayout != null) {
            // We want the point to be mapped to the dragTarget.
            mapPointFromSelfToChild(mDragTargetLayout, mDragViewVisualCenter, null);
            ItemInfo info = (ItemInfo) d.dragInfo;

            int minSpanX = item.spanX;
            int minSpanY = item.spanY;
            if (item.minSpanX > 0 && item.minSpanY > 0) {
                minSpanX = item.minSpanX;
                minSpanY = item.minSpanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY,
                    mDragTargetLayout, mTargetCell);
            int reorderX = mTargetCell[0];
            int reorderY = mTargetCell[1];

            setCurrentDropOverCell(mTargetCell[0], mTargetCell[1]);

            float targetCellDistance = mDragTargetLayout.getDistanceFromCell(
                    mDragViewVisualCenter[0], mDragViewVisualCenter[1], mTargetCell);

            final View dragOverView = mDragTargetLayout.getChildAt(mTargetCell[0],
                    mTargetCell[1]);

            manageFolderFeedback(info, mDragTargetLayout, mTargetCell,
                    targetCellDistance, dragOverView);

            boolean nearestDropOccupied = mDragTargetLayout.isNearestDropLocationOccupied((int)
                            mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1], item.spanX,
                    item.spanY, child, mTargetCell);

            if (!nearestDropOccupied) {
                mDragTargetLayout.visualizeDropLocation(child, mDragOutline,
                        (int) mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1],
                        mTargetCell[0], mTargetCell[1], item.spanX, item.spanY, false,
                        d.dragView.getDragVisualizeOffset(), d.dragView.getDragRegion());
            } else if ((mDragMode == DRAG_MODE_NONE || mDragMode == DRAG_MODE_REORDER)
                    && !mReorderAlarm.alarmPending() && (mLastReorderX != reorderX ||
                    mLastReorderY != reorderY)) {

                int[] resultSpan = new int[2];
                mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], minSpanX, minSpanY, item.spanX, item.spanY,
                        child, mTargetCell, resultSpan, CellLayout.MODE_SHOW_REORDER_HINT);
                // Otherwise, if we aren't adding to or creating a folder and there's no pending
                // reorder, then we schedule a reorder
                ReorderAlarmListener listener = new ReorderAlarmListener(mDragViewVisualCenter,
                        minSpanX, minSpanY, item.spanX, item.spanY, d.dragView, child);
                mReorderAlarm.setOnAlarmListener(listener);
                mReorderAlarm.setAlarm(REORDER_TIMEOUT);
            }

            if (mDragMode == DRAG_MODE_CREATE_FOLDER || mDragMode == DRAG_MODE_ADD_TO_FOLDER ||
                    !nearestDropOccupied) {
                if (mDragTargetLayout != null) {
                    mDragTargetLayout.revertTempState();
                }
            }
        }
    }

    /***********************************************************************************************
     * Methods related to the onDragOver are below
     */
    public boolean transitionStateShouldAllowDrop() {
        return ((mState == State.NORMAL || mState == State.SPRING_LOADED));
    }

    // This is used to compute the visual center of the dragView. This point is then
    // used to visualize drop locations and determine where to drop an item. The idea is that
    // the visual center represents the user's interpretation of where the item is, and hence
    // is the appropriate point to use when determining drop location.
    private float[] getDragViewVisualCenter(int x, int y, int xOffset, int yOffset,
                                            DragView dragView, float[] recycle) {
        float res[];
        if (recycle == null) {
            res = new float[2];
        } else {
            res = recycle;
        }

        // First off, the drag view has been shifted in a way that is not represented in the
        // x and y values or the x/yOffsets. Here we account for that shift.
        x += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetX);
        y += getResources().getDimensionPixelSize(R.dimen.dragViewOffsetY);

        // These represent the visual top and left of drag view if a dragRect was provided.
        // If a dragRect was not provided, then they correspond to the actual view left and
        // top, as the dragRect is in that case taken to be the entire dragView.
        // R.dimen.dragViewOffsetY.
        int left = x - xOffset;
        int top = y - yOffset;

        // In order to find the visual center, we shift by half the dragRect
        res[0] = left + dragView.getDragRegion().width() / 2;
        res[1] = top + dragView.getDragRegion().height() / 2;

        return res;
    }

    /*
     *
     * This method returns the CellLayout that is currently being dragged to. In order to drag
     * to a CellLayout, either the touch point must be directly over the CellLayout, or as a second
     * strategy, we see if the dragView is overlapping any CellLayout and choose the closest one
     *
     * Return null if no CellLayout is currently being dragged over
     *
     */
    private CellLayout findMatchingPageForDragOver(
            DragView dragView, float originX, float originY, boolean exact) {
        // We loop through all the screens (ie CellLayouts) and see which ones overlap
        // with the item being dragged and then choose the one that's closest to the touch point
        final int screenCount = getChildCount();
        CellLayout bestMatchingScreen = null;
        float smallestDistSoFar = Float.MAX_VALUE;

        for (int i = 0; i < screenCount; i++) {
            // The custom content screen is not a valid drag over option
            CellLayout cl = (CellLayout) getChildAt(i);

            final float[] touchXy = {originX, originY};
            // Transform the touch coordinates to the CellLayout's local coordinates
            // If the touch point is within the bounds of the cell layout, we can return immediately
            cl.getMatrix().invert(mTempInverseMatrix);
            mapPointFromSelfToChild(cl, touchXy, mTempInverseMatrix);

            if (touchXy[0] >= 0 && touchXy[0] <= cl.getWidth() &&
                    touchXy[1] >= 0 && touchXy[1] <= cl.getHeight()) {
                return cl;
            }

            if (!exact) {
                // Get the center of the cell layout in screen coordinates
                final float[] cellLayoutCenter = mTempCellLayoutCenterCoordinates;
                cellLayoutCenter[0] = cl.getWidth()/2;
                cellLayoutCenter[1] = cl.getHeight()/2;
                mapPointFromChildToSelf(cl, cellLayoutCenter);

                touchXy[0] = originX;
                touchXy[1] = originY;

                // Calculate the distance between the center of the CellLayout
                // and the touch point
                float dist = squaredDistance(touchXy, cellLayoutCenter);

                if (dist < smallestDistSoFar) {
                    smallestDistSoFar = dist;
                    bestMatchingScreen = cl;
                }
            }
        }
        return bestMatchingScreen;
    }

    /*
    *
    * Convert the 2D coordinate xy from the parent View's coordinate space to this CellLayout's
    * coordinate space. The argument xy is modified with the return result.
    *
    * if cachedInverseMatrix is not null, this method will just use that matrix instead of
    * computing it itself; we use this to avoid redundant matrix inversions in
    * findMatchingPageForDragOver
    *
    */
    void mapPointFromSelfToChild(View v, float[] xy, Matrix cachedInverseMatrix) {
        xy[0] = xy[0] - v.getLeft();
        xy[1] = xy[1] - v.getTop();
    }

    /*
    *
    * Convert the 2D coordinate xy from this CellLayout's coordinate space to
    * the parent View's coordinate space. The argument xy is modified with the return result.
    *
    */
    void mapPointFromChildToSelf(View v, float[] xy) {
        xy[0] += v.getLeft();
        xy[1] += v.getTop();
    }

    static private float squaredDistance(float[] point1, float[] point2) {
        float distanceX = point1[0] - point2[0];
        float distanceY = point2[1] - point2[1];
        return distanceX * distanceX + distanceY * distanceY;
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     *
     * pixelX and pixelY should be in the coordinate system of layout
     */
    private int[] findNearestArea(int pixelX, int pixelY,
                                  int spanX, int spanY, CellLayout layout, int[] recycle) {
        return layout.findNearestArea(
                pixelX, pixelY, spanX, spanY, recycle);
    }

    class ReorderAlarmListener implements OnAlarmListener {
        float[] dragViewCenter;
        int minSpanX, minSpanY, spanX, spanY;
        DragView dragView;
        View child;

        public ReorderAlarmListener(float[] dragViewCenter, int minSpanX, int minSpanY, int spanX,
                                    int spanY, DragView dragView, View child) {
            this.dragViewCenter = dragViewCenter;
            this.minSpanX = minSpanX;
            this.minSpanY = minSpanY;
            this.spanX = spanX;
            this.spanY = spanY;
            this.child = child;
            this.dragView = dragView;
        }

        public void onAlarm(Alarm alarm) {
            int[] resultSpan = new int[2];
            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, mDragTargetLayout,
                    mTargetCell);
            mLastReorderX = mTargetCell[0];
            mLastReorderY = mTargetCell[1];

            mTargetCell = mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                    child, mTargetCell, resultSpan, CellLayout.MODE_DRAG_OVER);

            if (mTargetCell[0] < 0 || mTargetCell[1] < 0) {
                mDragTargetLayout.revertTempState();
            } else {
                setDragMode(DRAG_MODE_REORDER);
            }

            boolean resize = resultSpan[0] != spanX || resultSpan[1] != spanY;
            mDragTargetLayout.visualizeDropLocation(child, mDragOutline,
                    (int) mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1],
                    mTargetCell[0], mTargetCell[1], resultSpan[0], resultSpan[1], resize,
                    dragView.getDragVisualizeOffset(), dragView.getDragRegion());
        }
    }

    private void manageFolderFeedback(ItemInfo info, CellLayout targetLayout,
                                      int[] targetCell, float distance, View dragOverView) {
        boolean userFolderPending = willCreateUserFolder(info, targetLayout, targetCell, distance,
                false);

        if (mDragMode == DRAG_MODE_NONE && userFolderPending &&
                !mFolderCreationAlarm.alarmPending()) {
            mFolderCreationAlarm.setOnAlarmListener(new
                    FolderCreationAlarmListener(targetLayout, targetCell[0], targetCell[1]));
            mFolderCreationAlarm.setAlarm(FOLDER_CREATION_TIMEOUT);
            return;
        }

        boolean willAddToFolder =
                willAddToExistingUserFolder(info, targetLayout, targetCell, distance);

        if (willAddToFolder && mDragMode == DRAG_MODE_NONE) {
            mDragOverFolderIcon = ((FolderIcon) dragOverView);
            mDragOverFolderIcon.onDragEnter(info);
            if (targetLayout != null) {
                targetLayout.clearDragOutlines();
            }
            setDragMode(DRAG_MODE_ADD_TO_FOLDER);
            return;
        }

        if (mDragMode == DRAG_MODE_ADD_TO_FOLDER && !willAddToFolder) {
            setDragMode(DRAG_MODE_NONE);
        }
        if (mDragMode == DRAG_MODE_CREATE_FOLDER && !userFolderPending) {
            setDragMode(DRAG_MODE_NONE);
        }

        return;
    }

    /**********************************************************************************************/

    /***********************************************************************************************
     * Methods related to the manageFolderFeedback are below
     */
    class FolderCreationAlarmListener implements OnAlarmListener {
        CellLayout layout;
        int cellX;
        int cellY;

        public FolderCreationAlarmListener(CellLayout layout, int cellX, int cellY) {
            this.layout = layout;
            this.cellX = cellX;
            this.cellY = cellY;
        }

        public void onAlarm(Alarm alarm) {
            if (mDragFolderRingAnimator != null) {
                // This shouldn't happen ever, but just in case, make sure we clean up the mess.
                mDragFolderRingAnimator.animateToNaturalState();
            }
            mDragFolderRingAnimator = new FolderIcon.FolderRingAnimator(mLauncher, null);
            mDragFolderRingAnimator.setCell(cellX, cellY);
            mDragFolderRingAnimator.setCellLayout(layout);
            mDragFolderRingAnimator.animateToAcceptState();
            layout.showFolderAccept(mDragFolderRingAnimator);
            layout.clearDragOutlines();
            setDragMode(DRAG_MODE_CREATE_FOLDER);
        }
    }

    boolean willCreateUserFolder(ItemInfo info, CellLayout target, int[] targetCell, float
            distance, boolean considerTimeout) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);

        if (dropOverView != null) {
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.tmpCellY)) {
                return false;
            }
        }

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            hasntMoved = dropOverView == mDragInfo.cell;
        }

        if (dropOverView == null || hasntMoved || (considerTimeout && !mCreateUserFolderOnDrop)) {
            return false;
        }

        boolean aboveShortcut = (dropOverView.getTag() instanceof ShortcutInfo);
        boolean willBecomeShortcut =
                (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                        info.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT);

        return (aboveShortcut && willBecomeShortcut);
    }

    boolean willAddToExistingUserFolder(Object dragInfo, CellLayout target, int[] targetCell,
                                        float distance) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);

        if (dropOverView != null) {
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) dropOverView.getLayoutParams();
            if (lp.useTmpCoords && (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.tmpCellY)) {
                return false;
            }
        }

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(dragInfo)) {
                return true;
            }
        }
        return false;
    }

    /**********************************************************************************************/

    public void onDragExit(DragObject d) {
        mDragEnforcer.onDragExit();

        // Here we store the final page that will be dropped to, if the workspace in fact
        // receives the drop
        if (mInScrollArea) {
            if (isPageMoving()) {
                // If the user drops while the page is scrolling, we should use that page as the
                // destination instead of the page that is being hovered over.
                mDropToLayout = (CellLayout) getPageAt(getNextPage());
            } else {
                mDropToLayout = mDragOverlappingLayout;
            }
        } else {
            mDropToLayout = mDragTargetLayout;
        }

        if (mDragMode == DRAG_MODE_CREATE_FOLDER) {
            mCreateUserFolderOnDrop = true;
        } else if (mDragMode == DRAG_MODE_ADD_TO_FOLDER) {
            mAddToExistingFolderOnDrop = true;
        }

        // Reset the scroll area and previous drag target
        onResetScrollArea();
        setCurrentDropLayout(null);
        setCurrentDragOverlappingLayout(null);

        mSpringLoadedDragController.cancel();

        //Commented because there is no showOutlines
        /*if (!mIsPageMoving) {
            hideOutlines();
        }*/
        mLauncher.getDragLayer().hidePageHints();
    }
    /***********************************************************************************************
     * Methods related to the onDragExit are below
     */
    private void onResetScrollArea() {
        setCurrentDragOverlappingLayout(null);
        mInScrollArea = false;
    }
    /**********************************************************************************************/

    public void onDragEnd() {
        if (!mDeferRemoveExtraEmptyScreen) {
            removeExtraEmptyScreen(true, mDragSourceInternal != null);
        }

        mIsDragOccuring = false;
        updateChildrenLayersEnabled(false);
        mLauncher.unlockScreenOrientation(false);

        mLauncher.getSearchBar().setVisibility(INVISIBLE);

        // Re-enable any Un/InstallShortcutReceiver and now process any queued items
        //InstallShortcutReceiver.disableAndFlushInstallQueue(getContext());
        //UninstallShortcutReceiver.disableAndFlushUninstallQueue(getContext());

        if(mDragSourceInternal != null){
            mDragSourceInternal.requestLayout();
        }

        mDragSourceInternal = null;
        mLauncher.onInteractionEnd();
    }

    public void clearDragAnimations(){
        mDragOutline = null;
        cleanupReorder(true);
        cleanupFolderCreation();
        cleanupAddToFolder();
        clearDropTargets();
        getCurrentDropLayout().clearDragOutlines();
        getCurrentDropLayout().completeAndClearReorderPreviewAnimations();
        mLauncher.getDragLayer().hidePageHints();
    }

    public void removeExtraEmptyScreen(final boolean animate, boolean stripEmptyScreens) {
        removeExtraEmptyScreenDelayed(animate, null, 0, stripEmptyScreens);
    }

    public void removeExtraEmptyScreenDelayed(final boolean animate, final Runnable onComplete,
                                              final int delay, final boolean stripEmptyScreens) {
        // Log to disk
        Launcher.addDumpLog(TAG, "11683562 - removeExtraEmptyScreen()", true);
        if (mLauncher.isWorkspaceLoading()) {
            // Don't strip empty screens if the workspace is still loading
            Launcher.addDumpLog(TAG, "    - workspace loading, skip", true);
            return;
        }

        if (delay > 0) {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    removeExtraEmptyScreenDelayed(animate, onComplete, 0, stripEmptyScreens);
                }
            }, delay);
            return;
        }

        convertFinalScreenToEmptyScreenIfNecessary();
        if (hasExtraEmptyScreen()) {
            int emptyIndex = mScreenOrder.indexOf(EXTRA_EMPTY_SCREEN_ID);
            if (getNextPage() == emptyIndex) {
                snapToPage(getNextPage() - 1, SNAP_OFF_EMPTY_SCREEN_DURATION);
                fadeAndRemoveEmptyScreen(SNAP_OFF_EMPTY_SCREEN_DURATION, FADE_EMPTY_SCREEN_DURATION,
                        onComplete, stripEmptyScreens);
            } else {
                fadeAndRemoveEmptyScreen(0, FADE_EMPTY_SCREEN_DURATION,
                        onComplete, stripEmptyScreens);
            }
            return;
        } else if (stripEmptyScreens) {
            // If we're not going to strip the empty screens after removing
            // the extra empty screen, do it right away.
            stripEmptyScreens();
        }

        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void fadeAndRemoveEmptyScreen(int delay, int duration, final Runnable onComplete,
                                          final boolean stripEmptyScreens) {
        // Log to disk
        // XXX: Do we need to update LM workspace screens below?
        Launcher.addDumpLog(TAG, "11683562 - fadeAndRemoveEmptyScreen()", true);
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0f);
        PropertyValuesHolder bgAlpha = PropertyValuesHolder.ofFloat("backgroundAlpha", 0f);

        final CellLayout cl = mDrawerScreens.get(EXTRA_EMPTY_SCREEN_ID);

        mRemoveEmptyScreenRunnable = new Runnable() {
            @Override
            public void run() {
                if (hasExtraEmptyScreen()) {
                    mDrawerScreens.remove(EXTRA_EMPTY_SCREEN_ID);
                    mScreenOrder.remove(EXTRA_EMPTY_SCREEN_ID);
                    removeView(cl);
                    if (stripEmptyScreens) {
                        stripEmptyScreens();
                    }
                }
            }
        };

        ObjectAnimator oa = ObjectAnimator.ofPropertyValuesHolder(cl, alpha, bgAlpha);
        oa.setDuration(duration);
        oa.setStartDelay(delay);
        oa.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mRemoveEmptyScreenRunnable != null) {
                    mRemoveEmptyScreenRunnable.run();
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        oa.start();
    }

    public void stripEmptyScreens() {
        // Log to disk
        Launcher.addDumpLog(TAG, "11683562 - stripEmptyScreens()", true);

        int currentPage = getNextPage();
        ArrayList<Long> removeScreens = new ArrayList<Long>();
        for (Long id: mDrawerScreens.keySet()) {
            CellLayout cl = mDrawerScreens.get(id);
            if (id >= 0 && cl.getShortcutsAndWidgets().getChildCount() == 0) {
                removeScreens.add(id);
            }
        }

        // We enforce at least one page to add new items to. In the case that we remove the last
        // such screen, we convert the last screen to the empty screen
        int minScreens = 1 + numCustomPages();

        int pageShift = 0;
        for (Long id: removeScreens) {
            Launcher.addDumpLog(TAG, "11683562 -   removing id: " + id, true);
            CellLayout cl = mDrawerScreens.get(id);
            mDrawerScreens.remove(id);
            mScreenOrder.remove(id);

            if (getChildCount() > minScreens) {
                if (indexOfChild(cl) < currentPage) {
                    pageShift++;
                }
                removeView(cl);
            } else {
                // if this is the last non-custom content screen, convert it to the empty screen
                mRemoveEmptyScreenRunnable = null;
                mDrawerScreens.put(EXTRA_EMPTY_SCREEN_ID, cl);
                mScreenOrder.add(EXTRA_EMPTY_SCREEN_ID);
            }
        }

        if (!removeScreens.isEmpty()) {
            // Update the model if we have changed any screens
            mLauncher.getModel().updateDrawerScreenOrder(mLauncher, mScreenOrder);
        }

        if (pageShift >= 0) {
            setCurrentPage(currentPage - pageShift);
        }
    }

    private void convertFinalScreenToEmptyScreenIfNecessary() {
        // Log to disk
        Launcher.addDumpLog(TAG, "11683562 - convertFinalScreenToEmptyScreenIfNecessary()", true);

        if (hasExtraEmptyScreen() || mScreenOrder.size() == 0) return;
        long finalScreenId = mScreenOrder.get(mScreenOrder.size() - 1);

        if (finalScreenId == CUSTOM_CONTENT_SCREEN_ID) return;
        CellLayout finalScreen = mDrawerScreens.get(finalScreenId);

        // If the final screen is empty, convert it to the extra empty screen
        if (finalScreen.getShortcutsAndWidgets().getChildCount() == 0 &&
                !finalScreen.isDropPending()) {
            mDrawerScreens.remove(finalScreenId);
            mScreenOrder.remove(finalScreenId);

            // if this is the last non-custom content screen, convert it to the empty screen
            mDrawerScreens.put(EXTRA_EMPTY_SCREEN_ID, finalScreen);
            mScreenOrder.add(EXTRA_EMPTY_SCREEN_ID);

            // Update the model if we have changed any screens
            mLauncher.getModel().updateDrawerScreenOrder(mLauncher, mScreenOrder);
            Launcher.addDumpLog(TAG, "11683562 -   extra empty screen: " + finalScreenId, true);
        }
    }

    public boolean hasExtraEmptyScreen() {
        int nScreens = getChildCount();
        nScreens = nScreens - numCustomPages();
        return mDrawerScreens.containsKey(EXTRA_EMPTY_SCREEN_ID) && nScreens > 1;
    }

    public int numCustomPages() {
        return hasCustomContent() ? 1 : 0;
    }

    public boolean hasCustomContent() {
        return (mScreenOrder.size() > 0 && mScreenOrder.get(0) == CUSTOM_CONTENT_SCREEN_ID);
    }

    public boolean isDropEnabled() {
        return true;
    }

    public boolean acceptDrop(DragObject d) {
        CellLayout dropTargetLayout = mDropToLayout;

        if (d.dragSource != this) {
            // Don't accept the drop if we're not over a screen at time of drop
            if (dropTargetLayout == null) {
                return false;
            }
            if (!transitionStateShouldAllowDrop()) return false;

            mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset,
                    d.dragView, mDragViewVisualCenter);

            // We want the point to be mapped to the dragTarget.
            mapPointFromSelfToChild(dropTargetLayout, mDragViewVisualCenter, null);


            int spanX = 1;
            int spanY = 1;
            if (mDragInfo != null) {
                final CellLayout.CellInfo dragCellInfo = mDragInfo;
                spanX = dragCellInfo.spanX;
                spanY = dragCellInfo.spanY;
            } else {
                final ItemInfo dragInfo = (ItemInfo) d.dragInfo;
                spanX = dragInfo.spanX;
                spanY = dragInfo.spanY;
            }

            int minSpanX = spanX;
            int minSpanY = spanY;
            if (d.dragInfo instanceof PendingAddWidgetInfo) {
                minSpanX = ((PendingAddWidgetInfo) d.dragInfo).minSpanX;
                minSpanY = ((PendingAddWidgetInfo) d.dragInfo).minSpanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, dropTargetLayout,
                    mTargetCell);
            float distance = dropTargetLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                    mDragViewVisualCenter[1], mTargetCell);
            if (mCreateUserFolderOnDrop && willCreateUserFolder((ItemInfo) d.dragInfo,
                    dropTargetLayout, mTargetCell, distance, true)) {
                return true;
            }

            if (mAddToExistingFolderOnDrop && willAddToExistingUserFolder((ItemInfo) d.dragInfo,
                    dropTargetLayout, mTargetCell, distance)) {
                return true;
            }

            int[] resultSpan = new int[2];
            mTargetCell = dropTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                    null, mTargetCell, resultSpan, CellLayout.MODE_ACCEPT_DROP);
            boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

            // Don't accept the drop if there's no room for the item
            if (!foundCell) {
                // Don't show the message if we are dropping on the AllApps button and the hotseat
                // is full

                if(d.dragSource instanceof ApplicationDrawer){
                    d.dragView.setAlpha(0);
                    d.deferDragViewCleanupPostAnimation = true;
                    d.cancelled = true;
                    mLauncher.getWorkspace().clearDragAnimations();
                    removeExtraEmptyScreen(false, true);
                    mLauncher.getSearchBar().setVisibility(View.GONE);
                    mLauncher.getDrawer().removeExtraEmptyScreen(false, false);
                }
                mLauncher.showOutOfSpaceMessage(false);
                return false;
            }
        }

        long screenId = getIdForScreen(dropTargetLayout);
        if (screenId == EXTRA_EMPTY_SCREEN_ID) {
            commitExtraEmptyScreen();
        }

        return true;
    }

    void updateItemLocationsInDatabase(CellLayout cl) {
        if(cl.getParent() instanceof ApplicationDrawer) {

            int count = cl.getShortcutsAndWidgets().getChildCount();

            long screenId = getIdForScreen(cl);
            int container = LauncherSettings.DrawerItems.CONTAINER_DRAWER;


            for (int i = 0; i < count; i++) {
                View v = cl.getShortcutsAndWidgets().getChildAt(i);
                ItemInfo info = (ItemInfo) v.getTag();
                // Null check required as the AllApps button doesn't have an item info
                if (info != null && info.requiresDbUpdate) {
                    info.requiresDbUpdate = false;
                    LauncherModel.modifyDrawerItemInDatabase(mLauncher, info, container, screenId, info.cellX,
                            info.cellY, info.spanX, info.spanY);
                }
            }
        }
    }

    /***********************************************************************************************
     * Methods related to the acceptDrop are below
     */
    public long commitExtraEmptyScreen() {
        // Log to disk
        Launcher.addDumpLog(TAG, "11683562 - commitExtraEmptyScreen()", true);

        int index = getPageIndexForScreenId(EXTRA_EMPTY_SCREEN_ID);
        CellLayout cl = mDrawerScreens.get(EXTRA_EMPTY_SCREEN_ID);
        mDrawerScreens.remove(EXTRA_EMPTY_SCREEN_ID);
        mScreenOrder.remove(EXTRA_EMPTY_SCREEN_ID);

        long newId = LauncherAppState.getLauncherProvider().generateNewDrawerScreenId();
        mDrawerScreens.put(newId, cl);
        mScreenOrder.add(newId);

        // Update the page indicator marker
        if (getPageIndicator() != null) {
            getPageIndicator().updateMarker(index, getPageIndicatorMarker(index));
        }

        mLauncher.getModel().updateDrawerScreenOrder(mLauncher, mScreenOrder);
        return newId;
    }

    @Override
    protected PageIndicator.PageMarkerResources getPageIndicatorMarker(int pageIndex) {
        long screenId = getScreenIdForPageIndex(pageIndex);
        if (screenId == EXTRA_EMPTY_SCREEN_ID) {
            return new PageIndicator.PageMarkerResources(R.drawable.ic_pageindicator_current,
                        R.drawable.ic_pageindicator_add);
        }

        return super.getPageIndicatorMarker(pageIndex);
    }
    /**********************************************************************************************/
    public void onDrop(final DragObject d) {
        mDragViewVisualCenter = getDragViewVisualCenter(d.x, d.y, d.xOffset, d.yOffset, d.dragView,
                mDragViewVisualCenter);

        CellLayout dropTargetLayout = mDropToLayout;

        // We want the point to be mapped to the dragTarget.
        if (dropTargetLayout != null) {
                mapPointFromSelfToChild(dropTargetLayout, mDragViewVisualCenter, null);
        }

        int snapScreen = -1;
        boolean resizeOnDrop = false;
        if (d.dragSource != this) {
            final int[] touchXY = new int[] { (int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1] };
            onDropExternal(touchXY, d.dragInfo, dropTargetLayout, false, d);
        } else if (mDragInfo != null) {
            final View cell = mDragInfo.cell;

            Runnable resizeRunnable = null;
            if (dropTargetLayout != null && !d.cancelled) {
                // Move internally
                boolean hasMovedLayouts = (getParentCellLayoutForView(cell) != dropTargetLayout);
                boolean hasMovedIntoHotseat = mLauncher.isHotseatLayout(dropTargetLayout);
                long screenId = (mTargetCell[0] < 0) ?
                        mDragInfo.screenId : getIdForScreen(dropTargetLayout);
                int spanX = mDragInfo != null ? mDragInfo.spanX : 1;
                int spanY = mDragInfo != null ? mDragInfo.spanY : 1;
                // First we find the cell nearest to point at which the item is
                // dropped, without any consideration to whether there is an item there.

                mTargetCell = findNearestArea((int) mDragViewVisualCenter[0], (int)
                        mDragViewVisualCenter[1], spanX, spanY, dropTargetLayout, mTargetCell);
                float distance = dropTargetLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                        mDragViewVisualCenter[1], mTargetCell);

                // If the item being dropped is a shortcut and the nearest drop
                // cell also contains a shortcut, then create a folder with the two shortcuts.
                if (!mInScrollArea && createUserFolderIfNecessary(cell,
                        dropTargetLayout, mTargetCell, distance, false, d.dragView, null)) {
                    return;
                }

                if (addToExistingFolderIfNecessary(cell, dropTargetLayout, mTargetCell,
                        distance, d, false)) {
                    return;
                }

                // Aside from the special case where we're dropping a shortcut onto a shortcut,
                // we need to find the nearest cell location that is vacant
                ItemInfo item = (ItemInfo) d.dragInfo;
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }

                int[] resultSpan = new int[2];
                mTargetCell = dropTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY, cell,
                        mTargetCell, resultSpan, CellLayout.MODE_ON_DROP);

                boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;


                if (getScreenIdForPageIndex(mCurrentPage) != screenId && !hasMovedIntoHotseat) {
                    snapScreen = getPageIndexForScreenId(screenId);
                    snapToPage(snapScreen);
                }

                if (foundCell) {
                    final ItemInfo info = (ItemInfo) cell.getTag();
                    if (hasMovedLayouts) {
                        // Reparent the view
                        CellLayout parentCell = getParentCellLayoutForView(cell);
                        if (parentCell != null) {
                            parentCell.removeView(cell);
                        } else if (LauncherAppState.isDogfoodBuild()) {
                            throw new NullPointerException("mDragInfo.cell has null parent");
                        }
                        addInScreen(cell, screenId, mTargetCell[0], mTargetCell[1]);
                    }

                    // update the item's position after drop
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    lp.cellX = lp.tmpCellX = mTargetCell[0];
                    lp.cellY = lp.tmpCellY = mTargetCell[1];
                    lp.cellHSpan = item.spanX;
                    lp.cellVSpan = item.spanY;
                    lp.isLockedToGrid = true;

                    LauncherModel.modifyDrawerItemInDatabase(mLauncher, info, LauncherSettings.DrawerItems.CONTAINER_DRAWER, screenId, lp.cellX,
                            lp.cellY, item.spanX, item.spanY);
                } else {
                    // If we can't find a drop location, we return the item to its original position
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    mTargetCell[0] = lp.cellX;
                    mTargetCell[1] = lp.cellY;
                    CellLayout layout = (CellLayout) cell.getParent().getParent();
                    layout.markCellsAsOccupiedForView(cell);
                }
            }

            final CellLayout parent = (CellLayout) cell.getParent().getParent();
            final Runnable finalResizeRunnable = resizeRunnable;
            // Prepare it to be animated into its new position
            // This must be called after the view has been re-parented
            final Runnable onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    mAnimatingViewIntoPlace = false;
                    updateChildrenLayersEnabled(false);
                    if (finalResizeRunnable != null) {
                        finalResizeRunnable.run();
                    }
                }
            };
            mAnimatingViewIntoPlace = true;
            if (d.dragView.hasDrawn()) {
                final ItemInfo info = (ItemInfo) cell.getTag();
                int duration = snapScreen < 0 ? -1 : ADJACENT_SCREEN_DROP_DURATION;
                mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, cell, duration,
                        onCompleteRunnable, this);

            } else {
                d.deferDragViewCleanupPostAnimation = false;
                cell.setVisibility(VISIBLE);
            }
            parent.onDropChild(cell);
        }
    }
    /***********************************************************************************************
     * nDropExternal is below
     */
    /**
     * Drop an item that didn't originate on one of the workspace screens.
     * It may have come from Launcher (e.g. from all apps or customize), or it may have
     * come from another app altogether.
     *
     * NOTE: This can also be called when we are outside of a drag event, when we want
     * to add an item to one of the workspace screens.
     */
    private void onDropExternal(final int[] touchXY, final Object dragInfo,
                                final CellLayout cellLayout, boolean insertAtFirst, DragObject d) {
        final Runnable exitSpringLoadedRunnable = new Runnable() {
            @Override
            public void run() {
                mLauncher.exitSpringLoadedDragModeDelayed(true,
                        Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
            }
        };


        ItemInfo info = (ItemInfo) dragInfo;
        int spanX = info.spanX;
        int spanY = info.spanY;
        if (mDragInfo != null) {
            spanX = mDragInfo.spanX;
            spanY = mDragInfo.spanY;
        }

        final long screenId = getIdForScreen(cellLayout);
        if (screenId != getScreenIdForPageIndex(mCurrentPage)
                && mState != State.SPRING_LOADED) {
            snapToScreenId(screenId);
        }

        if (info instanceof PendingAddItemInfo) {
            final PendingAddItemInfo pendingInfo = (PendingAddItemInfo) dragInfo;

            boolean findNearestVacantCell = true;
            if (pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                mTargetCell = findNearestArea((int) touchXY[0], (int) touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
                float distance = cellLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                        mDragViewVisualCenter[1], mTargetCell);
                if (willCreateUserFolder((ItemInfo) d.dragInfo, cellLayout, mTargetCell,
                        distance, true) || willAddToExistingUserFolder((ItemInfo) d.dragInfo,
                        cellLayout, mTargetCell, distance)) {
                    findNearestVacantCell = false;
                }
            }

            final ItemInfo item = (ItemInfo) d.dragInfo;
            boolean updateWidgetSize = false;
            if (findNearestVacantCell) {
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }
                int[] resultSpan = new int[2];
                mTargetCell = cellLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], minSpanX, minSpanY, info.spanX, info.spanY,
                        null, mTargetCell, resultSpan, CellLayout.MODE_ON_DROP_EXTERNAL);

                if (resultSpan[0] != item.spanX || resultSpan[1] != item.spanY) {
                    updateWidgetSize = true;
                }
                item.spanX = resultSpan[0];
                item.spanY = resultSpan[1];
            }

            /*Runnable onAnimationCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    // Normally removeExtraEmptyScreen is called in Workspace#onDragEnd, but when
                    // adding an item that may not be dropped right away (due to a config activity)
                    // we defer the removal until the activity returns.
                    deferRemoveExtraEmptyScreen();

                    // When dragging and dropping from customization tray, we deal with creating
                    // widgets/shortcuts/folders in a slightly different way
                    switch (pendingInfo.itemType) {
                        case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                            mLauncher.processShortcutFromDrop(pendingInfo.componentName,
                                    container, screenId, mTargetCell, null);
                            break;
                        default:
                            throw new IllegalStateException("Unknown item type: " +
                                    pendingInfo.itemType);
                    }
                }
            };*/
            View finalView = pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
                    ? ((PendingAddWidgetInfo) pendingInfo).boundWidget : null;

            if (finalView instanceof AppWidgetHostView && updateWidgetSize) {
                AppWidgetHostView awhv = (AppWidgetHostView) finalView;
                AppWidgetResizeFrame.updateWidgetSizeRanges(awhv, mLauncher, item.spanX,
                        item.spanY);
            }

            /*int animationStyle = ANIMATE_INTO_POSITION_AND_DISAPPEAR;
            if (pendingInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET &&
                    ((PendingAddWidgetInfo) pendingInfo).info.configure != null) {
                animationStyle = ANIMATE_INTO_POSITION_AND_REMAIN;
            }*/
            /*animateWidgetDrop(info, cellLayout, d.dragView, onAnimationCompleteRunnable,
                    animationStyle, finalView, true);*/
        } else {
            // This is for other drag/drop cases, like dragging from All Apps
            View view = null;

            switch (info.itemType) {
                case LauncherSettings.DrawerItems.ITEM_TYPE_APPLICATION:
                case LauncherSettings.DrawerItems.ITEM_TYPE_SHORTCUT:
                    if (info.container == NO_ID && info instanceof AppInfo) {
                        // Came from all apps -- make a copy
                        info = new ShortcutInfo((AppInfo) info);
                    }
                    view = mLauncher.createShortcut(R.layout.apps_customize_application, cellLayout,
                            (ShortcutInfo) info);
                    break;
                case LauncherSettings.DrawerItems.ITEM_TYPE_FOLDER:
                    view = FolderIcon.fromXml(R.layout.folder_icon, mLauncher, cellLayout,
                            (FolderInfo) info, mIconCache);
                    break;

                case LauncherSettings.DrawerItems.ITEM_TYPE_APPWIDGET:
                    break;
                default:
                    throw new IllegalStateException("Unknown item type: " + info.itemType);
            }

            // First we find the cell nearest to point at which the item is
            // dropped, without any consideration to whether there is an item there.
            if (touchXY != null) {
                mTargetCell = findNearestArea((int) touchXY[0], (int) touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
                float distance = cellLayout.getDistanceFromCell(mDragViewVisualCenter[0],
                        mDragViewVisualCenter[1], mTargetCell);
                d.postAnimationRunnable = exitSpringLoadedRunnable;
                if (createUserFolderIfNecessary(view, cellLayout, mTargetCell, distance,
                        true, d.dragView, d.postAnimationRunnable)) {
                    return;
                }
                if (addToExistingFolderIfNecessary(view, cellLayout, mTargetCell, distance, d,
                        true)) {
                    return;
                }
            }

            if (touchXY != null) {
                // when dragging and dropping, just find the closest free spot
                mTargetCell = cellLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], 1, 1, 1, 1,
                        null, mTargetCell, null, CellLayout.MODE_ON_DROP_EXTERNAL);
            } else {
                cellLayout.findCellForSpan(mTargetCell, 1, 1);
            }

            LauncherModel.addOrMoveDrawerItemInDatabase(mLauncher, info, screenId,
                    mTargetCell[0], mTargetCell[1]);

            addInScreen(view, screenId, mTargetCell[0], mTargetCell[1]);
            cellLayout.onDropChild(view);
            cellLayout.getShortcutsAndWidgets().measureChild(view);

            if (d.dragView != null) {
                // We wrap the animation call in the temporary set and reset of the current
                // cellLayout to its final transform -- this means we animate the drag view to
                // the correct final location.
                //setFinalTransitionTransform(cellLayout);
                mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, view,
                        exitSpringLoadedRunnable, this);
                //resetTransitionTransform(cellLayout);
            }
        }
    }

    protected void snapToScreenId(long screenId) {
        snapToPage(getPageIndexForScreenId(screenId));
    }

    /**********************************************************************************************/

    /***********************************************************************************************
     * Methods related to the onDrop are below
     */

    CellLayout getParentCellLayoutForView(View v) {
        ArrayList<CellLayout> layouts = getDrawerCellLayouts();
        for (CellLayout layout : layouts) {
            if (layout.getShortcutsAndWidgets().indexOfChild(v) > -1) {
                return layout;
            }
        }
        return null;
    }

    ArrayList<CellLayout> getDrawerCellLayouts() {
        ArrayList<CellLayout> layouts = new ArrayList<CellLayout>();
        int screenCount = getChildCount();
        for (int screen = 0; screen < screenCount; screen++) {
            layouts.add(((CellLayout) getChildAt(screen)));
        }
        return layouts;
    }

    public long getIdForScreen(CellLayout layout) {
        Iterator<Long> iter = mDrawerScreens.keySet().iterator();
        while (iter.hasNext()) {
            long id = iter.next();
            if (mDrawerScreens.get(id) == layout) {
                return id;
            }
        }
        return -1;
    }

    boolean createUserFolderIfNecessary(View newView, CellLayout target,
                                        int[] targetCell, float distance, boolean external, DragView dragView,
                                        Runnable postAnimationRunnable) {
        if (distance > mMaxDistanceForFolderCreation) return false;
        View v = target.getChildAt(targetCell[0], targetCell[1]);

        boolean hasntMoved = false;
        if (mDragInfo != null) {
            CellLayout cellParent = getParentCellLayoutForView(mDragInfo.cell);
            hasntMoved = (mDragInfo.cellX == targetCell[0] &&
                    mDragInfo.cellY == targetCell[1]) && (cellParent == target);
        }

        if (v == null || hasntMoved || !mCreateUserFolderOnDrop) return false;
        mCreateUserFolderOnDrop = false;
        final long screenId = (targetCell == null) ? mDragInfo.screenId : getIdForScreen(target);

        boolean aboveShortcut = (v.getTag() instanceof ShortcutInfo);
        boolean willBecomeShortcut = (newView.getTag() instanceof ShortcutInfo);

        if (aboveShortcut && willBecomeShortcut) {
            ShortcutInfo sourceInfo = (ShortcutInfo) newView.getTag();
            ShortcutInfo destInfo = (ShortcutInfo) v.getTag();
            // if the drag started here, we need to remove it from the workspace
            if (!external) {
                getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
            }

            Rect folderLocation = new Rect();
            float scale = mLauncher.getDragLayer().getDescendantRectRelativeToSelf(v, folderLocation);
            target.removeView(v);

            FolderIcon fi = addFolder(target, screenId, targetCell[0], targetCell[1]);
            destInfo.cellX = -1;
            destInfo.cellY = -1;
            sourceInfo.cellX = -1;
            sourceInfo.cellY = -1;

            // If the dragView is null, we can't animate
            boolean animate = dragView != null;
            if (animate) {
                fi.performCreateAnimation(destInfo, v, sourceInfo, dragView, folderLocation, scale,
                        postAnimationRunnable);
            } else {
                fi.addItem(destInfo);
                fi.addItem(sourceInfo);
            }
            return true;
        }
        return false;
    }

    FolderIcon addFolder(CellLayout layout, final long screenId, int cellX,
                         int cellY) {
        final FolderInfo folderInfo = new FolderInfo();
        folderInfo.title = mLauncher.getText(R.string.folder_name);
        folderInfo.screenId = screenId;

        LauncherModel.addDrawerItemToDatabase(mLauncher, folderInfo, LauncherSettings.DrawerItems.CONTAINER_DRAWER, screenId, cellX, cellY,
                false);
        sFolders.put(folderInfo.id, folderInfo);

        // Create the view
        FolderIcon newFolder =
                FolderIcon.fromXml(R.layout.folder_icon, mLauncher, layout, folderInfo, mIconCache);
        newFolder.setOnClickListener(this);
        newFolder.setOnLongClickListener(this);
        addInScreen(newFolder, screenId, cellX, cellY);
        // Force measure the new folder icon
        CellLayout parent = getParentCellLayoutForView(newFolder);
        parent.getShortcutsAndWidgets().measureChild(newFolder);
        return newFolder;
    }

    boolean addToExistingFolderIfNecessary(View newView, CellLayout target, int[] targetCell,
                                           float distance, DragObject d, boolean external) {
        if (distance > mMaxDistanceForFolderCreation) return false;

        View dropOverView = target.getChildAt(targetCell[0], targetCell[1]);
        if (!mAddToExistingFolderOnDrop) return false;
        mAddToExistingFolderOnDrop = false;

        if (dropOverView instanceof FolderIcon) {
            FolderIcon fi = (FolderIcon) dropOverView;
            if (fi.acceptDrop(d.dragInfo)) {
                fi.onDrop(d);

                // if the drag started here, we need to remove it from the workspace
                if (!external) {
                    getParentCellLayoutForView(mDragInfo.cell).removeView(mDragInfo.cell);
                }
                return true;
            }
        }
        return false;
    }

    public int getPageIndexForScreenId(long screenId) {
        return indexOfChild(mDrawerScreens.get(screenId));
    }

    /**********************************************************************************************/

    public void onDropCompleted(final View target, final DragObject d,
                                final boolean isFlingToDelete, final boolean success) {
        if(isInternalDrop) {
            if (mDeferDropAfterUninstall) {
                mDeferredAction = new Runnable() {
                    public void run() {
                        onDropCompleted(target, d, isFlingToDelete, success);
                        mDeferredAction = null;
                    }
                };
                return;
            }

            boolean beingCalledAfterUninstall = mDeferredAction != null;

            if (success && !(beingCalledAfterUninstall && !mUninstallSuccessful)) {
                if (target != this && mDragInfo != null) {
                    CellLayout parentCell = getParentCellLayoutForView(mDragInfo.cell);
                    if (parentCell != null) {
                        parentCell.removeView(mDragInfo.cell);
                    } else if (LauncherAppState.isDogfoodBuild()) {
                        throw new NullPointerException("mDragInfo.cell has null parent");
                    }
                    if (mDragInfo.cell instanceof DropTarget) {
                        mDragController.removeDropTarget((DropTarget) mDragInfo.cell);
                    }
                }
            } else if (mDragInfo != null) {
                CellLayout cellLayout = getScreenWithId(mDragInfo.screenId);
                if (cellLayout == null && LauncherAppState.isDogfoodBuild()) {
                    throw new RuntimeException("Invalid state: cellLayout == null in "
                            + "Workspace#onDropCompleted. Please file a bug. ");
                }
                if (cellLayout != null) {
                    cellLayout.onDropChild(mDragInfo.cell);
                }
            }
            if ((d.cancelled || (beingCalledAfterUninstall && !mUninstallSuccessful))
                    && mDragInfo.cell != null) {
                mDragInfo.cell.setVisibility(VISIBLE);
            }
        }else{
            View child = mDragInfo.cell;
            child.setVisibility(VISIBLE);
        }
        mDragOutline = null;
        mDragInfo = null;
    }

    public boolean onEnterScrollArea(int x, int y, int direction) {

        boolean result = false;
        if (!inModalState() && !mIsSwitchingState && getOpenFolder() == null) {
            mInScrollArea = true;

            final int page = getNextPage() +
                    (direction == DragController.SCROLL_LEFT ? -1 : 1);
            // We always want to exit the current layout to ensure parity of enter / exit
            setCurrentDropLayout(null);

            if (0 <= page && page < getChildCount()) {
                // Ensure that we are not dragging over to the custom content screen
                if (getScreenIdForPageIndex(page) == CUSTOM_CONTENT_SCREEN_ID) {
                    return false;
                }

                CellLayout layout = (CellLayout) getChildAt(page);
                setCurrentDragOverlappingLayout(layout);

                // Workspace is responsible for drawing the edge glow on adjacent pages,
                // so we need to redraw the workspace when this may have changed.
                invalidate();
                result = true;
            }
        }
        return result;
    }

    /***********************************************************************************************
     * Methods related to the onEnterScrollArea are below
     */
    Folder getOpenFolder() {
        DragLayer dragLayer = mLauncher.getDragLayer();
        int count = dragLayer.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = dragLayer.getChildAt(i);
            if (child instanceof Folder) {
                Folder folder = (Folder) child;
                if (folder.getInfo().opened)
                    return folder;
            }
        }
        return null;
    }

    public long getScreenIdForPageIndex(int index) {
        if (0 <= index && index < mScreenOrder.size()) {
            return mScreenOrder.get(index);
        }
        return -1;
    }
    /**********************************************************************************************/


    @Override
    public boolean onExitScrollArea() {
        boolean result = false;
        if (mInScrollArea) {
            invalidate();
            CellLayout layout = getCurrentDropLayout();
            setCurrentDropLayout(layout);
            setCurrentDragOverlappingLayout(layout);

            result = true;
            mInScrollArea = false;
        }
        return result;
    }

    @Override
    public boolean supportsDeleteDropTarget() {
        return false;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return false;
    }

    @Override
    public boolean supportsAppInfoDropTarget() {
        if(longClickedItem instanceof FolderIcon){
            return false;
        }
        return true;
    }

    @Override
    public boolean supportsMoveHomeDragTarget() { return true; }

    @Override
    public float getIntrinsicIconScaleFactor() {
        return 1f;
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
    }

    private void onTransitionPrepare() {
        mIsSwitchingState = true;

        // Invalidate here to ensure that the pages are rendered during the state change transition.
        invalidate();

        updateChildrenLayersEnabled(false);
    }

    private void onTransitionEnd() {
        mIsSwitchingState = false;
        updateChildrenLayersEnabled(false);
    }


    void setup(DragController dragController) {
        mSpringLoadedDragController = new SpringLoadedDragController(mLauncher);
        mDragController = dragController;

        // hardware layers on children are enabled on startup, but should be disabled until
        // needed
        updateChildrenLayersEnabled(false);
    }

    private void updateChildrenLayersEnabled(boolean force) {
        boolean small = mState == State.OVERVIEW || mIsSwitchingState;
        boolean enableChildrenLayers = force || small || mAnimatingViewIntoPlace || isPageMoving();

        if (enableChildrenLayers != mChildrenLayersEnabled) {
            mChildrenLayersEnabled = enableChildrenLayers;
            if (mChildrenLayersEnabled) {
                enableHwLayersOnVisiblePages();
            } else {
                for (int i = 0; i < getPageCount(); i++) {
                    final CellLayout cl = (CellLayout) getChildAt(i);
                    cl.enableHardwareLayer(false);
                }
            }
        }
    }

    private void enableHwLayersOnVisiblePages() {
        if (mChildrenLayersEnabled) {
            final int screenCount = getChildCount();
            getVisiblePages(mTempVisiblePagesRange);
            int leftScreen = mTempVisiblePagesRange[0];
            int rightScreen = mTempVisiblePagesRange[1];
            if (leftScreen == rightScreen) {
                // make sure we're caching at least two pages always
                if (rightScreen < screenCount - 1) {
                    rightScreen++;
                } else if (leftScreen > 0) {
                    leftScreen--;
                }
            }

            final CellLayout customScreen = mDrawerScreens.get(CUSTOM_CONTENT_SCREEN_ID);
            for (int i = 0; i < screenCount; i++) {
                final CellLayout layout = (CellLayout) getPageAt(i);

                // enable layers between left and right screen inclusive, except for the
                // customScreen, which may animate its content during transitions.
                boolean enableLayer = layout != customScreen &&
                        leftScreen <= i && i <= rightScreen && shouldDrawChild(layout);
                layout.enableHardwareLayer(enableLayer);
            }
        }
    }

    public CellLayout getScreenWithId(long screenId) {
        CellLayout layout = mDrawerScreens.get(screenId);
        return layout;
    }

    void clearDropTargets() {
        mapOverItems(MAP_NO_RECURSE, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v, View parent) {
                if (v instanceof DropTarget) {
                    mDragController.removeDropTarget((DropTarget) v);
                }
                // not done, process all the shortcuts
                return false;
            }
        });
    }

    public void removeAllScreens() {
        // Since we increment the current page when we call addCustomContentPage via bindScreens
        // (and other places), we need to adjust the current page back when we clear the pages
        if (hasCustomContent()) {
            removeCustomContentPage();
        }

        // Remove the pages and clear the screen models
        removeAllViews();
        mScreenOrder.clear();
        mDrawerScreens.clear();
    }

    public void removeCustomContentPage() {
        CellLayout customScreen = getScreenWithId(CUSTOM_CONTENT_SCREEN_ID);
        if (customScreen == null) {
            throw new RuntimeException("Expected custom content screen to exist");
        }

        mDrawerScreens.remove(CUSTOM_CONTENT_SCREEN_ID);
        mScreenOrder.remove(CUSTOM_CONTENT_SCREEN_ID);
        removeView(customScreen);

        // Update the custom content hint
        if (mRestorePage != INVALID_RESTORE_PAGE) {
            mRestorePage = mRestorePage - 1;
        } else {
            setCurrentPage(getCurrentPage() - 1);
        }
    }

    void addInScreenFromBind(View child, long screenId, int x, int y) {
        addInScreen(child, screenId, x, y);
        if(getPageIndicator().mMarkers.size() > getChildCount()){
            for(int i = getChildCount(); i < getPageIndicator().mMarkers.size(); i++){
                getPageIndicator().removeMarker(i, false);
            }
        }
    }

    void addInScreen(View child, long screenId, int x, int y) {
        int spanX = 1;
        int spanY = 1;
        if (getScreenWithId(screenId) == null) {
            Log.e(TAG, "Skipping child, screenId " + screenId + " not found");
            // DEBUGGING - Print out the stack trace to see where we are adding from
            insertNewScreen(screenId);
            //new Throwable().printStackTrace();
            //return;
        }

        if (screenId == EXTRA_EMPTY_SCREEN_ID) {
            // This should never happen
            throw new RuntimeException("Screen id should not be EXTRA_EMPTY_SCREEN_ID");
        }

        final CellLayout layout;

        // Show folder title if not in the hotseat
        if (child instanceof FolderIcon) {
            ((FolderIcon) child).setTextVisible(true);
        }
        layout = getScreenWithId(screenId);
        child.setOnKeyListener(new IconKeyEventListener());


        ViewGroup.LayoutParams genericLp = child.getLayoutParams();
        CellLayout.LayoutParams lp;
        if (genericLp == null || !(genericLp instanceof CellLayout.LayoutParams)) {
            lp = new CellLayout.LayoutParams(x, y, spanX, spanY);
        } else {
            lp = (CellLayout.LayoutParams) genericLp;
            lp.cellX = x;
            lp.cellY = y;
            lp.cellHSpan = spanX;
            lp.cellVSpan = spanY;
        }

        if (spanX < 0 && spanY < 0) {
            lp.isLockedToGrid = false;
        }

        // Get the canonical child id to uniquely represent this view in this screen
        ItemInfo info = (ItemInfo) child.getTag();
        int childId = mLauncher.getViewIdForItem(info);

        boolean markCellsAsOccupied = !(child instanceof Folder);
        if (!layout.addViewToCellLayout(child,  0, childId, lp, markCellsAsOccupied)) {
            // TODO: This branch occurs when the workspace is adding views
            // outside of the defined grid
            // maybe we should be deleting these items from the LauncherModel?
            Launcher.addDumpLog(TAG, "Failed to add to item at (" + lp.cellX + "," + lp.cellY + ") to CellLayout", true);
        }

        if (!(child instanceof Folder)) {
            child.setHapticFeedbackEnabled(false);
            child.setOnLongClickListener(this);
        }
        if (child instanceof DropTarget) {
            mDragController.addDropTarget((DropTarget) child);
        }
    }

    public long insertNewScreen() {
        return insertNewScreen((long)getChildCount(), getChildCount());
    }

    public long insertNewScreen(long screenId) {
        return insertNewScreen(screenId, getChildCount());
    }

    public long insertNewScreen(long screenId, int insertIndex) {
        if (mDrawerScreens.containsKey(screenId)) {
            throw new RuntimeException("Screen id " + screenId + " already exists!");
        }

        //CellLayout newScreen = new CellLayout(mLauncher);
        CellLayout newScreen = (CellLayout)
                mLauncher.getLayoutInflater().inflate(R.layout.workspace_screen, null);
        setupPage(newScreen);

        newScreen.setOnLongClickListener(this);
        newScreen.setOnClickListener(mLauncher);
        newScreen.setSoundEffectsEnabled(false);

        addView(newScreen, insertIndex, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        mDrawerScreens.put(screenId, newScreen);
        mScreenOrder.add(insertIndex, screenId);

        mLauncher.getModel().updateDrawerScreenOrder(mLauncher, mScreenOrder);

        /*if(getPageIndicator() != null){
            getPageIndicator().updateMarker(getChildCount() - 1, getPageIndicatorMarker(getChildCount() - 1));
        }*/

        return screenId;
    }

    public long insertNewScreenBeforeEmptyScreen(long screenId) {
        // Find the index to insert this view into.  If the empty screen exists, then
        // insert it before that.
        int insertIndex = mScreenOrder.indexOf(EXTRA_EMPTY_SCREEN_ID);
        if (insertIndex < 0) {
            insertIndex = mScreenOrder.size();
        }
        return insertNewScreen(screenId, insertIndex);
    }

    public void createCustomContentContainer() {
        CellLayout customScreen = (CellLayout)
                mLauncher.getLayoutInflater().inflate(R.layout.workspace_screen, null);
        customScreen.disableBackground();
        customScreen.disableDragTarget();

        setupPage(customScreen);

        customScreen.setOnLongClickListener(this);
        customScreen.setOnClickListener(mLauncher);
        customScreen.setSoundEffectsEnabled(false);

        mDrawerScreens.put(CUSTOM_CONTENT_SCREEN_ID, customScreen);
        mScreenOrder.add(0, CUSTOM_CONTENT_SCREEN_ID);

        addFullScreenPage(customScreen);

        // Update the custom content hint
        if (mRestorePage != INVALID_RESTORE_PAGE) {
            mRestorePage = mRestorePage + 1;
        } else {
            setCurrentPage(getCurrentPage() + 1);
        }
    }

    private void setupPage(CellLayout layout) {
        layout.setGridSize(mCellCountX, mCellCountY);

        // Note: We force a measure here to get around the fact that when we do layout calculations
        // immediately after syncing, we don't have a proper width.  That said, we already know the
        // expected page width, so we can actually optimize by hiding all the TextView-based
        // children that are expensive to measure, and let that happen naturally later.
        setVisibilityOnChildren(layout, View.GONE);
        int widthSpec = MeasureSpec.makeMeasureSpec(mContentWidth, MeasureSpec.AT_MOST);
        int heightSpec = MeasureSpec.makeMeasureSpec(mContentHeight, MeasureSpec.AT_MOST);
        layout.measure(widthSpec, heightSpec);

        setVisibilityOnChildren(layout, View.VISIBLE);
    }

    private void setVisibilityOnChildren(ViewGroup layout, int visibility) {
        int childCount = layout.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            layout.getChildAt(i).setVisibility(visibility);
        }
    }

    void saveDrawerToDb() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            CellLayout cl = (CellLayout) getChildAt(i);
            saveScreenToDb(cl);
        }
    }

    void saveScreenToDb(CellLayout cl) {
        int count = cl.getShortcutsAndWidgets().getChildCount();

        long screenId = getIdForScreen(cl);
        int container = LauncherSettings.DrawerItems.CONTAINER_DRAWER;

        for (int i = 0; i < count; i++) {
            View v = cl.getShortcutsAndWidgets().getChildAt(i);
            ItemInfo info = (ItemInfo) v.getTag();
            // Null check required as the AllApps button doesn't have an item info
            if (info != null) {
                int cellX = info.cellX;
                int cellY = info.cellY;
                LauncherModel.addDrawerItemToDatabase(mLauncher, info, container, screenId, cellX,
                        cellY, false);
            }
            if (v instanceof FolderIcon) {
                FolderIcon fi = (FolderIcon) v;
                fi.getFolder().addItemLocationsInDatabase();
            }
        }
    }

    public void fillDrawer(ArrayList<AppInfo> appsList) {
        int cellX = 0;
        int cellY = 0;
        long screenId = LauncherAppState.getLauncherProvider().generateNewDrawerScreenId();
        insertNewScreen(screenId);

        for (AppInfo app : appsList) {
            View view = mLauncher.createShortcut(R.layout.apps_customize_application, getScreenWithId(screenId),
                    app.makeShortcut());
            view.setOnLongClickListener(this);
            addInScreen(view, screenId, cellX, cellY);
            mLauncher.getModel().addOrMoveDrawerItemInDatabase(mLauncher, (ItemInfo) view.getTag(), screenId, cellX, cellY);
            if(Launcher.compareTWoCharSeq(app.contentDescription, Launcher.SIM_TOOLKIT_CONTENT) == 0){
                Launcher.SIM_TOOLKIT_LOADED = true;
            }
            cellX++;

            if (cellX == mCellCountX) {
                cellX = 0;
                cellY++;
            }

            if (cellY == mCellCountY) {
                cellX = 0;
                cellY = 0;
                screenId = LauncherAppState.getLauncherProvider().generateNewDrawerScreenId();
                if(appsList.size() != getChildCount() * mCellCountX * mCellCountY) {
                    insertNewScreen(screenId);
                }
            }
        }
    }

    private void setState(State state) {
        mState = state;
        updateInteractionForState();
        updateAccessibilityFlags();
    }

    State getState() {
        return mState;
    }

    private void updateAccessibilityFlags() {
        int accessible = mState == State.NORMAL ?
                ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES :
                ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
        setImportantForAccessibility(accessible);
    }

    public void updateInteractionForState() {
        if (mState != State.NORMAL) {
            mLauncher.onInteractionBegin();
        } else {
            mLauncher.onInteractionEnd();
        }
    }

    private void initAnimationArrays() {
        final int childCount = getChildCount();
        if (mLastChildCount == childCount) return;

        mOldBackgroundAlphas = new float[childCount];
        mOldAlphas = new float[childCount];
        mNewBackgroundAlphas = new float[childCount];
        mNewAlphas = new float[childCount];
    }

    Animator getChangeStateAnimation(final State state, boolean animated,
                                     ArrayList<View> layerViews) {
        return getChangeStateAnimation(state, animated, 0, -1, layerViews);
    }

    Animator getChangeStateAnimation(final State state, boolean animated, int delay, int snapPage) {
        return getChangeStateAnimation(state, animated, delay, snapPage, null);
    }


    static class ZoomInInterpolator implements TimeInterpolator {
        private final InverseZInterpolator inverseZInterpolator = new InverseZInterpolator(0.35f);
        private final DecelerateInterpolator decelerate = new DecelerateInterpolator(3.0f);

        public float getInterpolation(float input) {
            return decelerate.getInterpolation(inverseZInterpolator.getInterpolation(input));
        }
    }

    private final ZoomInInterpolator mZoomInInterpolator = new ZoomInInterpolator();

    private static final int HIDE_WORKSPACE_DURATION = 100;

    Animator getChangeStateAnimation(final State state, boolean animated, int delay, int snapPage,
                                     ArrayList<View> layerViews) {
        if (mState == state) {
            return null;
        }

        // Initialize animation arrays for the first time if necessary
        initAnimationArrays();

        AnimatorSet anim = animated ? LauncherAnimUtils.createAnimatorSet() : null;

        // We only want a single instance of a workspace animation to be running at once, so
        // we cancel any incomplete transition.
        if (mStateAnimator != null) {
            mStateAnimator.cancel();
        }
        mStateAnimator = anim;

        final State oldState = mState;
        final boolean oldStateIsNormal = (oldState == State.NORMAL);
        final boolean oldStateIsSpringLoaded = (oldState == State.SPRING_LOADED);
        final boolean oldStateIsNormalHidden = (oldState == State.NORMAL_HIDDEN);
        final boolean oldStateIsOverviewHidden = (oldState == State.OVERVIEW_HIDDEN);
        final boolean oldStateIsOverview = (oldState == State.OVERVIEW);
        setState(state);
        final boolean stateIsNormal = (state == State.NORMAL);
        final boolean stateIsSpringLoaded = (state == State.SPRING_LOADED);
        final boolean stateIsNormalHidden = (state == State.NORMAL_HIDDEN);
        final boolean stateIsOverviewHidden = (state == State.OVERVIEW_HIDDEN);
        final boolean stateIsOverview = (state == State.OVERVIEW);
        float finalBackgroundAlpha = (stateIsSpringLoaded || stateIsOverview) ? 1.0f : 0f;
        float finalHotseatAndPageIndicatorAlpha = (stateIsNormal || stateIsSpringLoaded) ? 1f : 0f;
        /*final float finalOverviewPanelAlpha = stateIsOverview ? 1f : 0f;
        float finalSearchBarAlpha = !stateIsNormal ? 0f : 1f;*/
        final float finalWorkspaceTranslationY = stateIsOverview || stateIsOverviewHidden ?
                getOverviewModeTranslationY() : 0;

        boolean workspaceToAllApps = (oldStateIsNormal && stateIsNormalHidden);
        boolean overviewToAllApps = (oldStateIsOverview && stateIsOverviewHidden);
        boolean allAppsToWorkspace = (stateIsNormalHidden && stateIsNormal);
        final boolean workspaceToOverview = (oldStateIsNormal && stateIsOverview);
        final boolean overviewToWorkspace = (oldStateIsOverview && stateIsNormal);

        if (overviewToWorkspace || overviewToAllApps) {
            // Check to see if new Settings need to be taken
            reloadSettings();
        }

        mNewScale = 1.0f;

        if (oldStateIsOverview) {
            disableFreeScroll();
        } else if (stateIsOverview) {
            enableFreeScroll();
        }

        if (state != State.NORMAL) {
            if (stateIsSpringLoaded) {
                mNewScale = mSpringLoadedShrinkFactor;
            } else if (stateIsOverview || stateIsOverviewHidden) {
                mNewScale = mOverviewModeShrinkFactor;
            }
        }

        final int duration;
        if (workspaceToAllApps || overviewToAllApps) {
            duration = HIDE_WORKSPACE_DURATION; //getResources().getInteger(R.integer.config_workspaceUnshrinkTime);
        } else if (workspaceToOverview || overviewToWorkspace) {
            duration = getResources().getInteger(R.integer.config_overviewTransitionTime);
        } else {
            duration = getResources().getInteger(R.integer.config_appsCustomizeWorkspaceShrinkTime);
        }

        if (snapPage == -1) {
            snapPage = getPageNearestToCenterOfScreen();
        }
        if (hasCustomContent()) {
            snapPage = Math.max(1, snapPage);
        }
        snapToPage(snapPage, duration, mZoomInInterpolator);

        for (int i = 0; i < getChildCount(); i++) {
            final CellLayout cl = (CellLayout) getChildAt(i);
            boolean isCurrentPage = (i == snapPage);
            float initialAlpha = cl.getShortcutsAndWidgets().getAlpha();
            float finalAlpha;
            if (stateIsNormalHidden || stateIsOverviewHidden) {
                finalAlpha = 0f;
            } else if (stateIsNormal && mWorkspaceFadeInAdjacentScreens) {
                finalAlpha = (i == snapPage || i < numCustomPages()) ? 1f : 0f;
            } else {
                finalAlpha = 1f;
            }

            if (stateIsOverview) {
                cl.setVisibility(VISIBLE);
                cl.setTranslationX(0f);
                cl.setTranslationY(0f);
                cl.setPivotX(cl.getMeasuredWidth() * 0.5f);
                cl.setPivotY(cl.getMeasuredHeight() * 0.5f);
                cl.setRotation(0f);
                cl.setRotationY(0f);
                cl.setRotationX(0f);
                cl.setScaleX(1f);
                cl.setScaleY(1f);
                cl.setShortcutAndWidgetAlpha(1f);
            }

            // If we are animating to/from the small state, then hide the side pages and fade the
            // current page in
            if (!mIsSwitchingState) {
                if (workspaceToAllApps || allAppsToWorkspace) {
                    if (allAppsToWorkspace && isCurrentPage) {
                        initialAlpha = 0f;
                    } else if (!isCurrentPage) {
                        initialAlpha = finalAlpha = 0f;
                    }
                    cl.setShortcutAndWidgetAlpha(initialAlpha);
                }
            }

            mOldAlphas[i] = initialAlpha;
            mNewAlphas[i] = finalAlpha;
            if (animated) {
                mOldBackgroundAlphas[i] = cl.getBackgroundAlpha();
                mNewBackgroundAlphas[i] = finalBackgroundAlpha;
            } else {
                cl.setBackgroundAlpha(finalBackgroundAlpha);
                cl.setShortcutAndWidgetAlpha(finalAlpha);
            }
        }

        //final View searchBar = mLauncher.getQsbBar();
        //final View overviewPanel = mLauncher.getOverviewPanel();
        //final View hotseat = mLauncher.getHotseat();
        final View pageIndicator = getPageIndicator();
        if (animated) {
            LauncherViewPropertyAnimator scale = new LauncherViewPropertyAnimator(this);
            scale.scaleX(mNewScale)
                    .scaleY(mNewScale)
                    .translationY(finalWorkspaceTranslationY)
                    .setDuration(duration)
                    .setInterpolator(mZoomInInterpolator);
            scale.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // in low power mode the animation doesn't play, so set the end value here
                    setScaleX(mNewScale);
                    setScaleY(mNewScale);
                    setTranslationY(finalWorkspaceTranslationY);
                }
            });
            anim.play(scale);
            for (int index = 0; index < getChildCount(); index++) {
                final int i = index;
                final CellLayout cl = (CellLayout) getChildAt(i);
                float currentAlpha = cl.getShortcutsAndWidgets().getAlpha();
                if (mOldAlphas[i] == 0 && mNewAlphas[i] == 0) {
                    cl.setBackgroundAlpha(mNewBackgroundAlphas[i]);
                    cl.setShortcutAndWidgetAlpha(mNewAlphas[i]);
                } else {
                    if (layerViews != null) {
                        layerViews.add(cl);
                    }
                    if (mOldAlphas[i] != mNewAlphas[i] || currentAlpha != mNewAlphas[i]) {
                        final View shortcutAndWidgets = cl.getShortcutsAndWidgets();
                        LauncherViewPropertyAnimator alphaAnim =
                                new LauncherViewPropertyAnimator(shortcutAndWidgets);
                        alphaAnim.alpha(mNewAlphas[i])
                                .setDuration(duration)
                                .setInterpolator(mZoomInInterpolator);
                        alphaAnim.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                // in low power mode the animation doesn't play,
                                // so set the end value here
                                shortcutAndWidgets.setAlpha(mNewAlphas[i]);
                            }
                        });
                        anim.play(alphaAnim);
                    }
                    if (mOldBackgroundAlphas[i] != 0 ||
                            mNewBackgroundAlphas[i] != 0) {
                        ValueAnimator bgAnim =
                                LauncherAnimUtils.ofFloat(cl, 0f, 1f);
                        bgAnim.setInterpolator(mZoomInInterpolator);
                        bgAnim.setDuration(duration);
                        bgAnim.addUpdateListener(new LauncherAnimatorUpdateListener() {
                            public void onAnimationUpdate(float a, float b) {
                                cl.setBackgroundAlpha(
                                        a * mOldBackgroundAlphas[i] +
                                                b * mNewBackgroundAlphas[i]);
                            }
                        });
                        anim.play(bgAnim);
                    }
                }
            }
            Animator pageIndicatorAlpha = null;
            if (pageIndicator != null) {
                pageIndicatorAlpha = new LauncherViewPropertyAnimator(pageIndicator)
                        .alpha(finalHotseatAndPageIndicatorAlpha).withLayer();
                pageIndicatorAlpha.addListener(
                        new AlphaUpdateListener(pageIndicator, finalHotseatAndPageIndicatorAlpha));
            } else {
                // create a dummy animation so we don't need to do null checks later
                pageIndicatorAlpha = ValueAnimator.ofFloat(0, 0);
            }

            /*Animator hotseatAlpha = new LauncherViewPropertyAnimator(hotseat)
                    .alpha(finalHotseatAndPageIndicatorAlpha).withLayer();
            hotseatAlpha.addListener(
                    new AlphaUpdateListener(hotseat, finalHotseatAndPageIndicatorAlpha));

            Animator overviewPanelAlpha = new LauncherViewPropertyAnimator(overviewPanel)
                    .alpha(finalOverviewPanelAlpha).withLayer();
            overviewPanelAlpha.addListener(
                    new AlphaUpdateListener(overviewPanel, finalOverviewPanelAlpha));*/

            // For animation optimations, we may need to provide the Launcher transition
            // with a set of views on which to force build layers in certain scenarios.
            /*hotseat.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            overviewPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            if (layerViews != null) {
                layerViews.add(hotseat);
                layerViews.add(overviewPanel);
            }*/

            if (workspaceToOverview) {
                pageIndicatorAlpha.setInterpolator(new DecelerateInterpolator(2));
                //hotseatAlpha.setInterpolator(new DecelerateInterpolator(2));
                //overviewPanelAlpha.setInterpolator(null);
            } else if (overviewToWorkspace) {
                pageIndicatorAlpha.setInterpolator(null);
                //hotseatAlpha.setInterpolator(null);
                //overviewPanelAlpha.setInterpolator(new DecelerateInterpolator(2));
            }

            //overviewPanelAlpha.setDuration(duration);
            pageIndicatorAlpha.setDuration(duration);
            //hotseatAlpha.setDuration(duration);

            /*if (searchBar != null) {
                Animator searchBarAlpha = new LauncherViewPropertyAnimator(searchBar)
                        .alpha(finalSearchBarAlpha).withLayer();
                searchBarAlpha.addListener(new AlphaUpdateListener(searchBar, finalSearchBarAlpha));
                searchBar.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                if (layerViews != null) {
                    layerViews.add(searchBar);
                }
                searchBarAlpha.setDuration(duration);
                if (mShowSearchBar && !mLauncher.getDragController().isDragging()) {
                    anim.play(searchBarAlpha);
                }
            }*/

            float mOverviewPanelSlideScale = 1.0f;

            if (overviewToWorkspace || stateIsNormal) {
                /*((SlidingUpPanelLayout) overviewPanel).collapsePane();
                overviewPanel.setScaleY(1.0f);*/
                mOverviewPanelSlideScale = 3.0f;
            } else if (workspaceToOverview || stateIsOverview) {
                //overviewPanel.setScaleY(3.0f);
                mOverviewPanelSlideScale = 1.0f;
            }

            /*final ViewPropertyAnimator overviewPanelScale = overviewPanel.animate();
            overviewPanelScale.scaleY(mOverviewPanelSlideScale)
                    .alpha(finalOverviewPanelAlpha)
                    .setInterpolator(new AccelerateDecelerateInterpolator());
            overviewPanelScale.setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (workspaceToOverview || stateIsOverview) {
                        overviewPanel.setAlpha(finalOverviewPanelAlpha);
                        AlphaUpdateListener.updateVisibility(overviewPanel);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (overviewToWorkspace || stateIsNormal) {
                        overviewPanel.setAlpha(finalOverviewPanelAlpha);
                        AlphaUpdateListener.updateVisibility(overviewPanel);
                    }
                    overviewPanelScale.setListener(null);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    overviewPanel.setAlpha(finalOverviewPanelAlpha);
                    AlphaUpdateListener.updateVisibility(overviewPanel);
                    overviewPanelScale.setListener(null);
                }
                @Override
                public void onAnimationRepeat(Animator animation) {}
            });*/

            // Animation animation = AnimationUtils.loadAnimation(mLauncher, R.anim.drop_down);
            // overviewPanel.startAnimation(animation);
            //anim.play(hotseatAlpha);
            anim.play(pageIndicatorAlpha);
            anim.setStartDelay(delay);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mStateAnimator = null;
                }
            });
        } else {
            /*overviewPanel.setAlpha(finalOverviewPanelAlpha);
            AlphaUpdateListener.updateVisibility(overviewPanel);
            hotseat.setAlpha(finalHotseatAndPageIndicatorAlpha);
            AlphaUpdateListener.updateVisibility(hotseat);*/
            if (pageIndicator != null) {
                pageIndicator.setAlpha(finalHotseatAndPageIndicatorAlpha);
                AlphaUpdateListener.updateVisibility(pageIndicator);
            }

            /*if (searchBar != null && mShowSearchBar && !mLauncher.getDragController().isDragging()) {
                searchBar.setAlpha(finalSearchBarAlpha);
                AlphaUpdateListener.updateVisibility(searchBar);
            }*/
            updateCustomContentVisibility();
            setScaleX(mNewScale);
            setScaleY(mNewScale);
            setTranslationY(finalWorkspaceTranslationY);
        }

        if (stateIsNormal || stateIsNormalHidden) {
            animateBackgroundGradient(0f, animated);
        } else {
            animateBackgroundGradient(getResources().getInteger(
                    R.integer.config_workspaceScrimAlpha) / 100f, animated);
        }
        return anim;
    }

    private void animateBackgroundGradient(float finalAlpha, boolean animated) {
        final DragLayer dragLayer = mLauncher.getDragLayer();

        if (mBackgroundFadeInAnimation != null) {
            mBackgroundFadeInAnimation.cancel();
            mBackgroundFadeInAnimation = null;
        }
        if (mBackgroundFadeOutAnimation != null) {
            mBackgroundFadeOutAnimation.cancel();
            mBackgroundFadeOutAnimation = null;
        }
        float startAlpha = dragLayer.getBackgroundAlpha();
        if (finalAlpha != startAlpha) {
            if (animated) {
                mBackgroundFadeOutAnimation =
                        LauncherAnimUtils.ofFloat(this, startAlpha, finalAlpha);
                mBackgroundFadeOutAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator animation) {
                        dragLayer.setBackgroundAlpha(
                                ((Float)animation.getAnimatedValue()).floatValue());
                    }
                });
                mBackgroundFadeOutAnimation.setInterpolator(new DecelerateInterpolator(1.5f));
                mBackgroundFadeOutAnimation.setDuration(BACKGROUND_FADE_OUT_DURATION);
                mBackgroundFadeOutAnimation.start();
            } else {
                dragLayer.setBackgroundAlpha(finalAlpha);
            }
        }
    }

    void updateCustomContentVisibility() {
        int visibility = mState == ApplicationDrawer.State.NORMAL ? VISIBLE : INVISIBLE;
        if (hasCustomContent()) {
            mDrawerScreens.get(CUSTOM_CONTENT_SCREEN_ID).setVisibility(visibility);
        }
    }

    int getOverviewModeTranslationY() {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        Rect overviewBar = grid.getOverviewModeButtonBarRect();

        int availableHeight = getViewportHeight();
        int scaledHeight = (int) (mOverviewModeShrinkFactor * getNormalChildHeight());
        int offsetFromTopEdge = (availableHeight - scaledHeight) / 2;
        int offsetToCenterInOverview = (availableHeight - mInsets.top - overviewBar.height()
                - scaledHeight) / 2;

        return -offsetFromTopEdge + mInsets.top + offsetToCenterInOverview;
    }

    @Override
    protected void screenScrolled(int screenCenter) {
        final boolean isRtl = isLayoutRtl();

        boolean isOnLastPageBeforeCustomContent = false;
        if (hasCustomContent()) {
            int customContentWidth = mDrawerScreens.get(CUSTOM_CONTENT_SCREEN_ID).getMeasuredWidth();
            isOnLastPageBeforeCustomContent = (mOverScrollX < customContentWidth && (!hasCustomContent() || isLayoutRtl())) ||
                    (mOverScrollX > mMaxScrollX - customContentWidth && (!hasCustomContent() || !isLayoutRtl()));
        }
        mUseTransitionEffect = !isOnLastPageBeforeCustomContent && mState == State.NORMAL && !mIsSwitchingState;

        super.screenScrolled(screenCenter);

        updatePageAlphaValues(screenCenter);
        updateStateForCustomContent(screenCenter);
        enableHwLayersOnVisiblePages();

        boolean shouldOverScroll = mOverScrollX < 0 || mOverScrollX > mMaxScrollX;

        if (shouldOverScroll) {
            int index = 0;
            final int lowerIndex = 0;
            final int upperIndex = getChildCount() - 1;

            final boolean isLeftPage = mOverScrollX < 0;
            index = (!isRtl && isLeftPage) || (isRtl && !isLeftPage) ? lowerIndex : upperIndex;

            CellLayout cl = (CellLayout) getChildAt(index);
            float effect = Math.abs(mOverScrollEffect);
            cl.setOverScrollAmount(Math.abs(effect), isLeftPage);

            mOverscrollEffectSet = true;
        } else {
            if (mOverscrollEffectSet && getChildCount() > 0) {
                mOverscrollEffectSet = false;
                ((CellLayout) getChildAt(0)).setOverScrollAmount(0, false);
                ((CellLayout) getChildAt(getChildCount() - 1)).setOverScrollAmount(0, false);
            }
        }
    }

    private void updatePageAlphaValues(int screenCenter) {
        boolean isInOverscroll = mOverScrollX < 0 || mOverScrollX > mMaxScrollX;
        if (mWorkspaceFadeInAdjacentScreens &&
                !inModalState() &&
                !mIsSwitchingState &&
                !isInOverscroll) {
            for (int i = numCustomPages(); i < getChildCount(); i++) {
                CellLayout child = (CellLayout) getChildAt(i);
                if (child != null) {
                    float scrollProgress = getScrollProgress(screenCenter, child, i);
                    float alpha = 1 - Math.abs(scrollProgress);
                    child.getShortcutsAndWidgets().setAlpha(alpha);
                    //child.setBackgroundAlphaMultiplier(1 - alpha);
                }
            }
        }
    }

    private void updateStateForCustomContent(int screenCenter) {
        float translationX = 0;
        float progress = 0;
        if (hasCustomContent()) {
            int index = mScreenOrder.indexOf(CUSTOM_CONTENT_SCREEN_ID);

            int scrollDelta = getScrollX() - getScrollForPage(index) -
                    getLayoutTransitionOffsetForPage(index);
            float scrollRange = getScrollForPage(index + 1) - getScrollForPage(index);
            translationX = scrollRange - scrollDelta;
            progress = (scrollRange - scrollDelta) / scrollRange;

            if (isLayoutRtl()) {
                translationX = Math.min(0, translationX);
            } else {
                translationX = Math.max(0, translationX);
            }
            progress = Math.max(0, progress);
        }

        if (Float.compare(progress, mLastCustomContentScrollProgress) == 0) return;

        CellLayout cc = mDrawerScreens.get(CUSTOM_CONTENT_SCREEN_ID);
        if (progress > 0 && cc.getVisibility() != VISIBLE && !inModalState()) {
            cc.setVisibility(VISIBLE);
        }

        mLastCustomContentScrollProgress = progress;

        mLauncher.getDragLayer().setBackgroundAlpha(progress * 0.8f);

        if (mLauncher.getHotseat() != null) {
            mLauncher.getHotseat().setTranslationX(translationX);
        }

        if (getPageIndicator() != null) {
            getPageIndicator().setTranslationX(translationX);
        }

        if (mCustomContentCallbacks != null) {
            mCustomContentCallbacks.onScrollProgressChanged(progress);
        }
    }

    private void reloadSettings() {
        /*mShowSearchBar = SettingsProvider.getBoolean(mLauncher, SettingsProvider.SETTINGS_UI_HOMESCREEN_SEARCH,
                R.bool.preferences_interface_homescreen_search_default);*/
        mShowOutlines = SettingsProvider.getBoolean(mLauncher,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_SCROLLING_PAGE_OUTLINES,
                R.bool.preferences_interface_homescreen_scrolling_page_outlines_default);
        mHideIconLabels = SettingsProvider.getBoolean(mLauncher,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_HIDE_ICON_LABELS,
                R.bool.preferences_interface_homescreen_hide_icon_labels_default);
        mWorkspaceFadeInAdjacentScreens = SettingsProvider.getBoolean(mLauncher,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_SCROLLING_FADE_ADJACENT,
                R.bool.preferences_interface_homescreen_scrolling_fade_adjacent_default);
        TransitionEffect.setFromString(this, SettingsProvider.getString(mLauncher,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_SCROLLING_TRANSITION_EFFECT,
                R.string.preferences_interface_homescreen_scrolling_transition_effect));

        /*mScrollWallpaper = SettingsProvider.getBoolean(mLauncher,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_SCROLLING_WALLPAPER_SCROLL,
                R.bool.preferences_interface_homescreen_scrolling_wallpaper_scroll_default);*/

        /*if (!mScrollWallpaper) {
            if (mWindowToken != null) mWallpaperManager.setWallpaperOffsets(mWindowToken, 0f, 0.5f);
        } else {
            mWallpaperOffset.syncWithScroll();
        }*/
    }

}
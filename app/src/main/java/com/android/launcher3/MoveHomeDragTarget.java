/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.TransitionDrawable;
import android.nfc.Tag;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.dev.sadooo.decenthome.R;

public class MoveHomeDragTarget extends ButtonDropTarget {

    private ColorStateList mOriginalTextColor;
    private TransitionDrawable mDrawable;

    private String TAG = "MoveHomeDragTarget";

    public MoveHomeDragTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MoveHomeDragTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mOriginalTextColor = getTextColors();

        // Get the hover color
        Resources r = getResources();
        mHoverColor = r.getColor(R.color.move_target_hover_tint);
        mDrawable = (TransitionDrawable) getCurrentDrawable();

        if (mDrawable == null) {
            // TODO: investigate why this is ever happening. Presently only on one known device.
            mDrawable = (TransitionDrawable) r.getDrawable(R.drawable.home_target_selector);
            setCompoundDrawablesRelativeWithIntrinsicBounds(mDrawable, null, null, null);
        }

        if (null != mDrawable) {
            mDrawable.setCrossFadeEnabled(true);
        }

        // Remove the text in the Phone UI in landscape
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!LauncherAppState.getInstance().isScreenLarge()) {
                setText("");
            }
        }
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        d.deferDragViewCleanupPostAnimation = false;
        mLauncher.getWorkspace().clearDragAnimations();
        return true;
    }


    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        boolean isVisible = true;

        // Hide this button unless we are dragging something from AllApps
        if (!source.supportsMoveHomeDragTarget()) {
            isVisible = false;
        }

        mActive = isVisible;
        mDrawable.resetTransition();
        setTextColor(mOriginalTextColor);

        ((ViewGroup) getParent()).setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDragEnd() {
        super.onDragEnd();
        mActive = false;
        ((ViewGroup) getParent()).setVisibility(mActive ? View.VISIBLE : View.GONE);
    }

    public void onDragEnter(DragObject d) {
        super.onDragEnter(d);
        mDrawable.startTransition(mTransitionDuration);
        setTextColor(mHoverColor);

        if(d.dragSource instanceof ApplicationDrawer) {
            mLauncher.getDrawer().isInternalDrop = false;
            d.dragView.setAlpha(0);
            View item = mLauncher.getDrawer().longClickedItem;
            if (d.dragInfo instanceof FolderInfo) {
                mLauncher.showWorkspace(true);
                mLauncher.getWorkspace().beginDragShared(item, d.dragSource);
                item.setEnabled(true);
                item.setOnClickListener(mLauncher);
            } else if (d.dragInfo instanceof ShortcutInfo) {
                mLauncher.showWorkspace(true);
                mLauncher.getWorkspace().beginDragShared(item, d.dragSource);
            }
        }
        ((ViewGroup) getParent()).setVisibility(View.GONE);
    }

    public void onDragExit(DragObject d) {
        super.onDragExit(d);

        if (!d.dragComplete) {
            mDrawable.resetTransition();
            setTextColor(mOriginalTextColor);
        }
    }
}
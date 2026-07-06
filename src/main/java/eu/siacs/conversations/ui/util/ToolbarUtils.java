/*
 * Copyright (C) 2020 The Android Open Source Project
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

package eu.siacs.conversations.ui.util;

import static java.util.Collections.max;
import static java.util.Collections.min;

import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ToolbarUtils {

    private static final Comparator<View> VIEW_TOP_COMPARATOR =
            new Comparator<View>() {
                @Override
                public int compare(View view1, View view2) {
                    return view1.getTop() - view2.getTop();
                }
            };

    private ToolbarUtils() {
        // Private constructor to prevent unwanted construction.
    }

    public static void resetActionBarOnClickListeners(@NonNull MaterialToolbar view) {
        final TextView title = getTitleTextView(view);
        final TextView subtitle = getSubtitleTextView(view);
        if (title != null) {
            title.setOnClickListener(null);
        }
        if (subtitle != null) {
            subtitle.setOnClickListener(null);
        }
    }

    public static void setActionBarOnClickListener(
            @NonNull MaterialToolbar view, @NonNull final View.OnClickListener onClickListener) {
        final TextView title = getTitleTextView(view);
        final TextView subtitle = getSubtitleTextView(view);
        if (title != null) {
            title.setOnClickListener(onClickListener);
        }
        if (subtitle != null) {
            subtitle.setOnClickListener(onClickListener);
        }
    }

    @Nullable
    public static TextView getTitleTextView(@NonNull Toolbar toolbar) {
        List<TextView> textViews = getTextViewsWithText(toolbar, toolbar.getTitle());
        return textViews.isEmpty() ? null : min(textViews, VIEW_TOP_COMPARATOR);
    }

    @Nullable
    public static TextView getSubtitleTextView(@NonNull Toolbar toolbar) {
        List<TextView> textViews = getTextViewsWithText(toolbar, toolbar.getSubtitle());
        return textViews.isEmpty() ? null : max(textViews, VIEW_TOP_COMPARATOR);
    }

    private static List<TextView> getTextViewsWithText(
            @NonNull Toolbar toolbar, CharSequence text) {
        List<TextView> textViews = new ArrayList<>();
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView textView) {
                if (TextUtils.equals(textView.getText(), text)) {
                    textViews.add(textView);
                }
            }
        }
        return textViews;
    }

    public static void adjustToolbarHeight(
            final MaterialToolbar toolbar, final boolean adjustToSearchBar) {
        final var context = toolbar.getContext();
        final ViewGroup.LayoutParams params = toolbar.getLayoutParams();

        if (adjustToSearchBar) {
            params.height =
                    (int)
                            TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    88,
                                    context.getResources().getDisplayMetrics());
        } else {
            final TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                params.height =
                        TypedValue.complexToDimensionPixelSize(
                                tv.data, context.getResources().getDisplayMetrics());
            }
        }

        toolbar.setLayoutParams(params);
    }
}

/*
 * Copyright (C) 2014 nohana, Inc.
 * Copyright 2017 Zhihu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an &quot;AS IS&quot; BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zhihu.matisse.internal.entity;

import android.content.pm.ActivityInfo;
import android.support.annotation.StyleRes;

import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.R;
import com.zhihu.matisse.engine.ImageEngine;
import com.zhihu.matisse.engine.impl.GlideEngine;
import com.zhihu.matisse.filter.Filter;
import com.zhihu.matisse.listener.OnCheckedListener;
import com.zhihu.matisse.listener.OnSelectedListener;

import java.util.List;
import java.util.Set;

public final class SelectionSpec {

    public Set<MimeType> mimeTypeSet;
    public boolean mediaTypeExclusive;
    public boolean showSingleMediaType;
    @StyleRes
    public int themeId;
    public int orientation;
    public boolean countable;
    public int maxSelectable;
    public int maxImageSelectable;
    public int maxVideoSelectable;
    public List<Filter> filters;
    public boolean capture;
    public CaptureStrategy captureStrategy;
    public int spanCount;
    public int gridExpectedSize;
    public float thumbnailScale;
    public ImageEngine imageEngine;
    public boolean hasInited;
    public OnSelectedListener onSelectedListener;
    public boolean originalable;
    public boolean autoHideToobar;
    public int originalMaxSize;
    public OnCheckedListener onCheckedListener;

    //2019/1/21 新增图片压缩相关参数
    public boolean isCompress;//是否开启图片压缩
    public int ignoreCompressSize;//忽略压缩图片大小阈值，单位KB
    public boolean isShowCompressProgress;//是否显示图片压缩等待框

    //2019/1/22 图片裁剪相关参数
    public boolean isCrop;// 裁剪
    public int cropMaxWidth;// 裁剪保存宽度
    public int cropMaxHeight;// 裁剪保存高度
    public int cropAspectX;//裁剪横向比例
    public int cropAspectY;//裁剪竖向比例

    //其它
    public boolean isOnlyCapture;//是否仅拍照获取


    private SelectionSpec() {
    }

    public static SelectionSpec getInstance() {
        return InstanceHolder.INSTANCE;
    }

    public static SelectionSpec getCleanInstance() {
        SelectionSpec selectionSpec = getInstance();
        selectionSpec.reset();
        return selectionSpec;
    }

    private void reset() {
        mimeTypeSet = null;
        mediaTypeExclusive = true;
        showSingleMediaType = false;
        themeId = R.style.Matisse_Zhihu;
        orientation = 0;
        countable = false;
        maxSelectable = 1;
        maxImageSelectable = 0;
        maxVideoSelectable = 0;
        filters = null;
        capture = false;
        captureStrategy = null;
        spanCount = 3;
        gridExpectedSize = 0;
        thumbnailScale = 0.5f;
        imageEngine = new GlideEngine();
        hasInited = true;
        originalable = false;
        autoHideToobar = false;
        originalMaxSize = Integer.MAX_VALUE;

        //图片压缩相关参数
        isCompress = true;
        ignoreCompressSize = 100;
        isShowCompressProgress = false;

        //图片裁剪相关参数
        isCrop = true;
        cropMaxWidth = 300;
        cropMaxHeight = 300;

        isOnlyCapture = false;
    }

    public boolean singleSelectionModeEnabled() {
        return !countable && (maxSelectable == 1 || (maxImageSelectable == 1 && maxVideoSelectable == 1));
    }

    public boolean needOrientationRestriction() {
        return orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    }

    public boolean onlyShowImages() {
        return showSingleMediaType && MimeType.ofImage().containsAll(mimeTypeSet);
    }

    public boolean onlyShowVideos() {
        return showSingleMediaType && MimeType.ofVideo().containsAll(mimeTypeSet);
    }

    private static final class InstanceHolder {
        private static final SelectionSpec INSTANCE = new SelectionSpec();
    }
}

package com.zhihu.matisse.crop;

/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.opengl.GLES10;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.zhihu.matisse.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

/*
 * Modified from original in AOSP.
 */
public class CropImageActivity extends MonitoredActivity {

    public static final String AUTHORITY = "authority";
    public static final String SOURCE_PATH = "source_path";//裁剪图片源key
    public static final String OUT_PUT_PATH = "out_put_path";//裁剪后输出key

    private static final int SIZE_DEFAULT = 2048;
    private static final int SIZE_LIMIT = 4096;

    private final Handler handler = new Handler();

    //参数传递定义
    private String authority;
    private int aspectY;
    private int aspectX;
    // Output image
    private int maxX;
    private int maxY;
    private boolean saveAsPng;
    private ArrayList<String> sourcePathList;


    private int exifRotation;
    private boolean isSaving;
    private Uri currentSaveUri;
    private Uri currentCropSourceUri;

    private int sampleSize;
    private RotateBitmap rotateBitmap;
    private CropImageView imageView;
    private HighlightView cropView;
    private ArrayList<String> outPutPathList;

    private TextView tv_crop_progress;
    private int totalCount;//需要裁剪的图片总数量


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setupWindowFlags();
        setupViews();

        //获取传递参数
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            authority = extras.getString(AUTHORITY);
            aspectX = extras.getInt(Crop.Extra.ASPECT_X);
            aspectY = extras.getInt(Crop.Extra.ASPECT_Y);
            maxX = extras.getInt(Crop.Extra.MAX_X);
            maxY = extras.getInt(Crop.Extra.MAX_Y);
            saveAsPng = extras.getBoolean(Crop.Extra.AS_PNG, false);
            sourcePathList = extras.getStringArrayList(SOURCE_PATH);
        }

        if (sourcePathList == null || sourcePathList.isEmpty()) {
            Toast.makeText(this, "请先选择裁剪的图片", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        outPutPathList = new ArrayList<>();

        //记录总张数
        totalCount = sourcePathList.size();
        this.refreshCropProgress();
        this.startCrop(sourcePathList.get(0));
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void setupWindowFlags() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }


    private void setupViews() {
        setContentView(R.layout.activity_crop);

        tv_crop_progress = findViewById(R.id.tv_crop_progress);
        imageView = (CropImageView) findViewById(R.id.crop_image);
        imageView.context = this;

        imageView.setRecycler(new ImageViewTouchBase.Recycler() {
            @Override
            public void recycle(Bitmap b) {
                b.recycle();
                System.gc();
            }
        });

        findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        findViewById(R.id.btn_done).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSaveClicked();
            }
        });
    }

    private void loadInput(String imagePath) {
        currentCropSourceUri = Uri.fromFile(new File(imagePath));
        exifRotation = CropUtil.getExifRotation(CropUtil.getFromMediaUri(this, getContentResolver(), currentCropSourceUri));

        InputStream is = null;
        try {
            sampleSize = calculateBitmapSampleSize(currentCropSourceUri);
            is = getContentResolver().openInputStream(currentCropSourceUri);
            BitmapFactory.Options option = new BitmapFactory.Options();
            option.inSampleSize = sampleSize;
            rotateBitmap = new RotateBitmap(BitmapFactory.decodeStream(is, null, option), exifRotation);
        } catch (IOException e) {
            Log.e("Error reading image: " + e.getMessage(), e);
            setResultException(e);
        } catch (OutOfMemoryError e) {
            Log.e("OOM reading image: " + e.getMessage(), e);
            setResultException(e);
        } finally {
            CropUtil.closeSilently(is);
        }
    }

    private int calculateBitmapSampleSize(Uri bitmapUri) throws IOException {
        InputStream is = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            is = getContentResolver().openInputStream(bitmapUri);
            BitmapFactory.decodeStream(is, null, options); // Just get image size
        } finally {
            CropUtil.closeSilently(is);
        }

        int maxSize = getMaxImageSize();
        int sampleSize = 1;
        while (options.outHeight / sampleSize > maxSize || options.outWidth / sampleSize > maxSize) {
            sampleSize = sampleSize << 1;
        }
        return sampleSize;
    }

    private int getMaxImageSize() {
        int textureLimit = getMaxTextureSize();
        if (textureLimit == 0) {
            return SIZE_DEFAULT;
        } else {
            return Math.min(textureLimit, SIZE_LIMIT);
        }
    }

    private int getMaxTextureSize() {
        // The OpenGL texture size is the maximum size that can be drawn in an ImageView
        int[] maxSize = new int[1];
        GLES10.glGetIntegerv(GLES10.GL_MAX_TEXTURE_SIZE, maxSize, 0);
        return maxSize[0];
    }

    private void startCrop(String imagePath) {
        isSaving = false;
        currentSaveUri = Crop.getFileUri(false,this,authority);
        this.loadInput(imagePath);
        if (rotateBitmap == null) {
            finish();
            return;
        }

        if (isFinishing()) {
            return;
        }
        imageView.setImageRotateBitmapResetBase(rotateBitmap, true);
        CropUtil.startBackgroundJob(this, null, getResources().getString(R.string.crop__wait),
                new Runnable() {
                    public void run() {
                        final CountDownLatch latch = new CountDownLatch(1);
                        handler.post(new Runnable() {
                            public void run() {
                                if (imageView.getScale() == 1F) {
                                    imageView.center();
                                }
                                latch.countDown();
                            }
                        });
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        new Cropper().crop();
                    }
                }, handler
        );
    }

    private class Cropper {

        private void makeDefault() {
            if (rotateBitmap == null) {
                return;
            }

            HighlightView hv = new HighlightView(imageView);
            final int width = rotateBitmap.getWidth();
            final int height = rotateBitmap.getHeight();

            Rect imageRect = new Rect(0, 0, width, height);

            // Make the default size about 4/5 of the width or height
            int cropWidth = Math.min(width, height) * 4 / 5;
            @SuppressWarnings("SuspiciousNameCombination")
            int cropHeight = cropWidth;

            if (aspectX != 0 && aspectY != 0) {
                if (aspectX > aspectY) {
                    cropHeight = cropWidth * aspectY / aspectX;
                } else {
                    cropWidth = cropHeight * aspectX / aspectY;
                }
            }

            int x = (width - cropWidth) / 2;
            int y = (height - cropHeight) / 2;

            RectF cropRect = new RectF(x, y, x + cropWidth, y + cropHeight);
            hv.setup(imageView.getUnrotatedMatrix(), imageRect, cropRect, aspectX != 0 && aspectY != 0);
            imageView.add(hv);
        }

        public void crop() {
            handler.post(new Runnable() {
                public void run() {
                    makeDefault();
                    imageView.invalidate();
                    if (imageView.highlightViews.size() == 1) {
                        cropView = imageView.highlightViews.get(0);
                        cropView.setFocus(true);
                    }
                }
            });
        }
    }

    private void onSaveClicked() {
        if (cropView == null || isSaving) {
            return;
        }
        isSaving = true;

        Bitmap croppedImage;
        Rect r = cropView.getScaledCropRect(sampleSize);
        int width = r.width();
        int height = r.height();

        int outWidth = width;
        int outHeight = height;
        if (maxX > 0 && maxY > 0 && (width > maxX || height > maxY)) {
            float ratio = (float) width / (float) height;
            if ((float) maxX / (float) maxY > ratio) {
                outHeight = maxY;
                outWidth = (int) ((float) maxY * ratio + .5f);
            } else {
                outWidth = maxX;
                outHeight = (int) ((float) maxX / ratio + .5f);
            }
        }

        try {
            croppedImage = decodeRegionCrop(r, outWidth, outHeight);
        } catch (IllegalArgumentException e) {
            setResultException(e);
            finish();
            return;
        }

        if (croppedImage != null) {
            imageView.setImageRotateBitmapResetBase(new RotateBitmap(croppedImage, exifRotation), true);
            imageView.center();
            imageView.highlightViews.clear();
        }
        saveImage(croppedImage);
    }

    private void saveImage(Bitmap croppedImage) {
        if (croppedImage != null) {
            final Bitmap b = croppedImage;
            CropUtil.startBackgroundJob(this, null, getResources().getString(R.string.crop__saving),
                    new Runnable() {
                        public void run() {
                            saveOutput(b);
                        }
                    }, handler
            );
        } else {
            finish();
        }
    }

    private Bitmap decodeRegionCrop(Rect rect, int outWidth, int outHeight) {
        // Release memory now
        clearImageView();

        InputStream is = null;
        Bitmap croppedImage = null;
        try {
            is = getContentResolver().openInputStream(currentCropSourceUri);
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);
            final int width = decoder.getWidth();
            final int height = decoder.getHeight();

            if (exifRotation != 0) {
                // Adjust crop area to account for image rotation
                Matrix matrix = new Matrix();
                matrix.setRotate(-exifRotation);

                RectF adjusted = new RectF();
                matrix.mapRect(adjusted, new RectF(rect));

                // Adjust to account for origin at 0,0
                adjusted.offset(adjusted.left < 0 ? width : 0, adjusted.top < 0 ? height : 0);
                rect = new Rect((int) adjusted.left, (int) adjusted.top, (int) adjusted.right, (int) adjusted.bottom);
            }

            try {
                croppedImage = decoder.decodeRegion(rect, new BitmapFactory.Options());
                if (croppedImage != null && (rect.width() > outWidth || rect.height() > outHeight)) {
                    Matrix matrix = new Matrix();
                    matrix.postScale((float) outWidth / rect.width(), (float) outHeight / rect.height());
                    croppedImage = Bitmap.createBitmap(croppedImage, 0, 0, croppedImage.getWidth(), croppedImage.getHeight(), matrix, true);
                }
            } catch (IllegalArgumentException e) {
                // Rethrow with some extra information
                throw new IllegalArgumentException("Rectangle " + rect + " is outside of the image ("
                        + width + "," + height + "," + exifRotation + ")", e);
            }

        } catch (IOException e) {
            Log.e("Error cropping image: " + e.getMessage(), e);
            setResultException(e);
        } catch (OutOfMemoryError e) {
            Log.e("OOM cropping image: " + e.getMessage(), e);
            setResultException(e);
        } finally {
            CropUtil.closeSilently(is);
        }
        return croppedImage;
    }

    private void clearImageView() {
        imageView.clear();
        if (rotateBitmap != null) {
            rotateBitmap.recycle();
        }
        System.gc();
    }

    private void saveOutput(Bitmap croppedImage) {
        OutputStream outputStream = null;
        try {
            outputStream = getContentResolver().openOutputStream(currentSaveUri);
            if (outputStream != null) {
                croppedImage.compress(saveAsPng ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                        90,     // note: quality is ignored when using PNG
                        outputStream);
            }
        } catch (IOException e) {
            setResultException(e);
            Log.e("Cannot open file: " + currentSaveUri, e);
        } finally {
            CropUtil.closeSilently(outputStream);
        }

        CropUtil.copyExifRotation(
                CropUtil.getFromMediaUri(this, getContentResolver(), currentCropSourceUri),
                CropUtil.getFromMediaUri(this, getContentResolver(), currentSaveUri)
        );

        //保存裁剪完成的图片路径
        outPutPathList.add(currentSaveUri.getPath());

        final Bitmap b = croppedImage;
        handler.post(new Runnable() {
            public void run() {
                imageView.clear();
                b.recycle();
            }
        });

        //裁剪成功一个移除对应的path
        if (sourcePathList != null && !sourcePathList.isEmpty()) {
            sourcePathList.remove(0);
            if (!sourcePathList.isEmpty()) {
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshCropProgress();
                        startCrop(sourcePathList.get(0));
                    }
                });
                return;
            }
        }

        //2019/1/24 验证是否继续裁剪
        if (sourcePathList.isEmpty()) {
            this.setResultOk(outPutPathList);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rotateBitmap != null) {
            rotateBitmap.recycle();
        }
    }

    /**
     * 刷新当前裁剪进度
     */
    private void refreshCropProgress() {
        if (totalCount <= 1) {
            tv_crop_progress.setVisibility(View.INVISIBLE);
            return;
        }
        tv_crop_progress.setVisibility(View.VISIBLE);

        int index = totalCount - sourcePathList.size();
        tv_crop_progress.setText(String.format(
                "共%s张/第%s张"
                , String.valueOf(totalCount)
                , String.valueOf(index <= 0 ? 1 : index + 1)
        ));

    }

    @Override
    public boolean onSearchRequested() {
        return false;
    }

    public boolean isSaving() {
        return isSaving;
    }

    private void setResultOk(ArrayList<String> outPutPathList) {
        setResult(RESULT_OK, new Intent().putExtra(OUT_PUT_PATH, outPutPathList));
    }

    private void setResultException(Throwable throwable) {
        setResult(Crop.RESULT_ERROR, new Intent().putExtra(Crop.Extra.ERROR, throwable));
    }

}

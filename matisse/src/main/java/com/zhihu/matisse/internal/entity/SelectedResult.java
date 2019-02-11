package com.zhihu.matisse.internal.entity;

import com.zhihu.matisse.MimeType;

import java.io.Serializable;

/**
 * author Richard
 * date 2019/1/23 15:39
 * version V1.0
 * description: 选择结果信息
 */
public class SelectedResult implements Serializable {

    private String path;

    private String mineType;

    private long size;//单位：byte

    private long videoDuration;//仅限视频有效


    public SelectedResult(String path, String mineType, long size, long videoDuration) {
        this.path = path;
        this.mineType = mineType;
        this.size = size;
        this.videoDuration = videoDuration;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMineType() {
        return mineType;
    }

    public void setMineType(String mineType) {
        this.mineType = mineType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getVideoDuration() {
        return videoDuration;
    }

    public void setVideoDuration(long videoDuration) {
        this.videoDuration = videoDuration;
    }

    //-----------------------------自定义方法-------------------------------
    public boolean isImage() {
        return MimeType.isImage(mineType);
    }

    public boolean isVideo() {
        return MimeType.isVideo(mineType);
    }

    public boolean isGif() {
        return MimeType.isGif(mineType);
    }
}

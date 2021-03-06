package com.white.luckcloud.framelibrary.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;

import com.white.luckcloud.baselibrary.utils.FileUtils;
import com.white.luckcloud.baselibrary.utils.LogUtils;
import com.white.luckcloud.baselibrary.utils.StringUtils;
import com.white.luckcloud.baselibrary.utils.ThreadPoolUtils;
import com.white.luckcloud.baselibrary.utils.ToastUtils;
import com.libjpeg.NativeUtil;
import com.white.luckcloud.framelibrary.entity.UploadImageEntity;
import com.white.luckcloud.framelibrary.entity.User;
import com.white.luckcloud.framelibrary.entity.event.UploadEvent;
import com.white.luckcloud.framelibrary.http.HttpRequestUtils;
import com.white.luckcloud.framelibrary.http.callback.DefaultHttpCallback;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 上传的工具类
 * author: fa
 * date: 2018/3/22 09:29.
 */

public class UploadUtils {
    private static final String TAG = UploadUtils.class.getSimpleName();

    private Context mContext;
    // 文件上传的时候是否需要压缩（LibJpeg）
    private boolean mNeedCompress = true;
    // 压缩的文件大小标准
    private long mMaxFileSize = MAX_IMAGE_LENGTH;
    // 上传的文件的大小（用来显示进度），每个文件进度按照100计算
    private int mTotalUploadSize = 0;
    //    // 提交进度的对话框
//    private UploadProgressDialog mUploadProgressDialog;
    // 需要上传的文件路径集合
    private List<UploadImageEntity> mUploadList = new ArrayList<>();
    // 上传之后的图片集合
    private List<UploadImageEntity> mResultUrlList = new ArrayList<>();
    // 当前提交的文件的索引(提交的第几个文件)，用来计算进度
    private int mCurrentUploadIndex = 0;
    // Handler
    private static Handler mHandler = new Handler();
    // 压缩文件的时候生成的最终文件路径
    private UploadImageEntity mCurrentEntity;// 当前正在处理的实体

    // 压缩允许的图片的最大大小（500K）
    public static final long MAX_IMAGE_LENGTH = 500 * 1024;

    public UploadUtils(Context context) {
        this.mContext = context;
    }

    /**
     * 上传之前是否压缩图片
     */
    public void setCompressImage(boolean compressImage) {
        this.mNeedCompress = compressImage;
    }

    /**
     * 设置上传允许的文件大小
     */
    public void setMaxFileSize(long maxFileSize) {
        this.mMaxFileSize = maxFileSize;
    }

    /**
     * 上传多个图片
     */
    public void uploadImages(List<UploadImageEntity> list) {
        // 初始化
        mCurrentUploadIndex = 0;
        mResultUrlList.clear();
//        // 显示正在上传的对话框
//        if (mUploadProgressDialog == null) {
//            mUploadProgressDialog = new UploadProgressDialog(mContext);
//        }
//        mUploadProgressDialog.show();
        // 需要上传的集合赋值
        mUploadList.clear();
        mUploadList.addAll(list);
        // 设置提交的时候需要显示的总进度（图片数量*100）
        mTotalUploadSize = mUploadList.size() * 100;
        // 上传
        if (mUploadList.size() > 0) {
            uploadImage();
        } else {
//            // 提交图片进度对话框消失
//            mUploadProgressDialog.dismiss();
            // 全部上传完成之后回调回去
            if (mCallback != null) {
                mCallback.onUploadComplete(mResultUrlList);
            }
        }
    }

    /**
     * 递归上传
     */
    private void uploadImage() {
        // 获取当前要处理的图片实体
        mCurrentEntity = mUploadList.get(0);
        // 判断是否需要压缩
        if (mNeedCompress) {
            compressImage();
        } else {// 不用压缩就直接上传
            uploadFile(false);
        }
    }


    /**
     * 压缩图片
     */
    private void compressImage() {
        final String uploadPath = mCurrentEntity.getImagePath();
        long fileSize = FileUtils.getFileSize(mCurrentEntity.getImagePath());
        LogUtils.e(TAG, "原图（" + uploadPath + "）大小是：" + fileSize);
        // 如果文件大小小于限制大小，则直接上传
        if (fileSize <= mMaxFileSize) {
            LogUtils.e(TAG, "不需压缩直接上传");
            uploadFile(false);
            return;
        }
        // 否则压缩再上传
        final String tempPath = AppStorageUtils.getTempImageDir(mContext) + File.separator + System.currentTimeMillis() + ".jpg";
        LogUtils.e(TAG, "压缩创建临时文件：" + tempPath);
        // 子线程压缩
        ThreadPoolUtils.execute(new Runnable() {
            @Override
            public void run() {
                // 压缩
                NativeUtil.compressBitmap(uploadPath, tempPath, mMaxFileSize);
                LogUtils.e(TAG, "压缩后图片大小是：" + FileUtils.getFileLength(tempPath));
                // 压缩完成判断文件是否存在（文件本身大小就满足的时候不会走压缩，所以要将远路径赋值给最终路径）
                if (new File(tempPath).exists()) {
                    mCurrentEntity.setCompressPath(tempPath);
                } else {
                    mCurrentEntity.setCompressPath(mCurrentEntity.getImagePath());
                }
                // 压缩完成了，发消息到主线程执行上传
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // 上传
                        uploadFile(true);
                    }
                });
            }
        });
    }

    private void uploadFile(final boolean compress) {
        String imagePath = mCurrentEntity.getImagePath();
        if (compress) {
            imagePath = mCurrentEntity.getCompressPath();
        }
        if (StringUtils.isEmpty(imagePath)) {
            LogUtils.e(TAG, "文件路径为空");
            return;
        }
        File file = new File(imagePath);
        if (!file.exists()) {
            LogUtils.e(TAG, "文件不存在");
            return;
        }
        HttpRequestUtils.uploadImage(mContext, FileUtils.FileToStringBase64(file.getAbsolutePath()), User.getInstance().getToken(mContext),
                new DefaultHttpCallback<Object>((Activity) mContext, true) {
                    @Override
                    protected void onProgress(Context context, int progress) {
                        UploadEvent event = new UploadEvent();
                        event.setProgress(progress);
                        if (compress) {
                            event.setFileName(mCurrentEntity.getCompressPath());
                        } else {
                            event.setFileName(mCurrentEntity.getImagePath());
                        }
                        updateUploadProgress(event);
                    }

                    @Override
                    protected void onResponseSuccessMap(Map<String, Object> result) {
                        if (result != null) {
                            UploadEvent event = new UploadEvent();
                            event.setUrl(String.valueOf(result.get("url")));
                            onUploadSuccess(compress, event);
                        }
                    }

                    @Override
                    protected void onResponseFail(Context context, String errorCode, String errorMsg) {
                        if ("1".equals(errorCode)) {
                            onUploadFail(null, "图片上传失败，请重新选择图片上传");
                        } else {
                            onUploadFail(null, "");
                        }
                    }

                    @Override
                    protected void onResponseError(Context context, String errorCode, String errorMsg) {
                        onUploadFail(null, "");
                    }
                });
    }


    /**
     * 上传成功的处理
     */
    private void onUploadSuccess(boolean compress, UploadEvent event) {
        // 图片数量大于0的时候就继续
        if (mUploadList.size() > 0) {
            // 添加上传成功之后的URL到集合中
            mCurrentEntity.setImageUrl(event.getUrl());
            mResultUrlList.add(mCurrentEntity);
            // 从数据源集合中移除上传成功的数据
            mUploadList.remove(0);
        }
        // 判断剩余量，执行上传或者提交
        if (mUploadList.size() == 0) {
//            // 提交图片进度对话框消失
//            mUploadProgressDialog.dismiss();
            // 全部上传完成之后回调回去
            if (mCallback != null) {
                mCallback.onUploadComplete(mResultUrlList);
            }
        } else {
            String imagePath = mCurrentEntity.getImagePath();
            if (compress) {
                imagePath = mCurrentEntity.getCompressPath();
            }
            // 如果是需要压缩，在提交下一张之前，删除上一张压缩之后生成的临时文件
            if (mNeedCompress && FileUtils.exists(imagePath)) {
                // 上传成功之后删除压缩后的临时文件
                boolean result = FileUtils.delFile(imagePath);
                LogUtils.e(TAG, "删除文件：" + imagePath + "的结果" + result);
            }
            // 继续上传
            uploadImage();
        }
        // 上传的文件计数
        mCurrentUploadIndex++;
    }

    /**
     * 上传失败的处理
     */
    private void onUploadFail(UploadEvent event, String message) {
        message = StringUtils.isEmpty(message) ? "文件上传出错" : message;
        ToastUtils.showToast(mContext, message);
//        // 进度对话框消失
//        if (mUploadProgressDialog != null) {
//            mUploadProgressDialog.dismiss();
//        }
    }

    /**
     * 上传中更新上传进度
     */
    private void updateUploadProgress(UploadEvent event) {
        // 获取单个文件的上传进度
        int singleFileProgress = event.getProgress();
        LogUtils.d(TAG, "文件" + event.getFileName() + "上传进度：" + singleFileProgress);
        // 计算当前提交的文件大小
        // 由于每个文件提交完成大小都按照100计算的，所以根据文件索引计算当前提交的大小
        int allFileProgress = singleFileProgress + mCurrentUploadIndex * 100;
        // 当前的进度
        int progress = (int) ((float) allFileProgress / (float) mTotalUploadSize * 100);
//        // 展示
//        mUploadProgressDialog.updateProgress(progress);
    }

    /**
     * 释放
     */
    public void release() {
//        if (mUploadProgressDialog != null) {
//            mUploadProgressDialog.dismiss();
//        }
    }

    // 上传的回调
    private UploadCallback mCallback;

    /**
     * 设置上传的回调
     */
    public void setCallback(UploadCallback callback) {
        this.mCallback = callback;
    }

    /**
     * 图片上传的回调监听
     */
    public interface UploadCallback {
        void onUploadComplete(List<UploadImageEntity> resultList);
    }

}

package com.luck.luckcloud.baselibrary.view.webView;

import android.content.Context;

import com.luck.luckcloud.baselibrary.utils.AppUtils;


/**
 * 自定义WebView配置信息
 * author: fa
 * date: 2017/11/15 10:25.
 */

public class WebViewConfig {

    // WebView存储临时文件目录，缓存等。可删除
    public static final String WEB_CACHE_DIRNAME = "/webCache";
    // WebView下载的目录
    private static final String WEB_VIEW_DOWNLOAD_PATH = "/webViewDownLoad";

    /**
     * 获取自定义的WebView下载文件的存储目录
     */
    public static String getWebViewDownLoadPath(Context context) {
        return AppUtils.SDCachePath(context) + WEB_VIEW_DOWNLOAD_PATH;
    }

}

package com.white.luckcloud.framelibrary.http.callback;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;

import com.alibaba.android.arouter.launcher.ARouter;
import com.white.luckcloud.baselibrary.http.HttpUtils;
import com.white.luckcloud.baselibrary.http.callback.EngineCallback;
import com.white.luckcloud.baselibrary.http.config.BaseHttpConfig;
import com.white.luckcloud.baselibrary.http.dialog.LoadingDialog;
import com.white.luckcloud.baselibrary.http.progress.entity.Progress;
import com.white.luckcloud.baselibrary.utils.AppUtils;
import com.white.luckcloud.baselibrary.utils.LogUtils;
import com.white.luckcloud.baselibrary.utils.StringUtils;
import com.white.luckcloud.baselibrary.utils.ToastUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.white.luckcloud.framelibrary.R;
import com.white.luckcloud.framelibrary.commom.HttpConfig;
import com.white.luckcloud.framelibrary.entity.HttpResponseEntity;
import com.white.luckcloud.framelibrary.entity.UpdateEntity;
import com.white.luckcloud.framelibrary.entity.User;
import com.white.luckcloud.framelibrary.http.HttpRequestUtils;
import com.white.luckcloud.framelibrary.http.gson.GSonUtils;
import com.white.luckcloud.framelibrary.update.UpdateUtils;
import com.white.luckcloud.framelibrary.utils.ErrorCodeTips;
import com.white.luckcloud.framelibrary.utils.LoginUtils;
import com.white.luckcloud.framelibrary.utils.LogoutUtils;
import com.white.luckcloud.framelibrary.utils.PreferencesUtils;

import java.util.Map;


/**
 * 工程使用的HttpCallBack，在这里封装一些Post请求公用的参数，并将返回的数据信息封装为具体业务类型
 * Created by fa on 2017/5/31.
 */

public class DefaultHttpCallback<T> implements EngineCallback {
    private static final String TAG = DefaultHttpCallback.class.getSimpleName();

    // 是否显示正在加载的对话框
    private boolean mShowLoadDialog = false;
    // 正在加载的对话框是否可以取消
    private boolean mCancelable = false;
    // 显示的文字
    private String mDialogText;

    private Activity mActivity;
    private Fragment mFragment;

    static Handler mHandler = new Handler(Looper.getMainLooper());

    public DefaultHttpCallback() {
    }

    public DefaultHttpCallback(Activity activity, boolean showLoadDialog) {
        this.mActivity = activity;
        this.mShowLoadDialog = showLoadDialog;
    }

    public DefaultHttpCallback(Fragment fragment, boolean showLoadDialog) {
        this.mFragment = fragment;
        this.mShowLoadDialog = showLoadDialog;
    }

    protected void postMainThread(Runnable runnable) {
        mHandler.post(runnable);
    }

    public DefaultHttpCallback dialogCancelable(boolean cancelable) {
        this.mCancelable = cancelable;
        return this;
    }

    public DefaultHttpCallback dialogText(String text) {
        this.mDialogText = text;
        return this;
    }

    //修改请求头
    public static final String KEY_TOKEN = "Authorization";
    private static final String KEY_VERSION = "app-version";// 版本号
    private static final String KEY_IMEI = "equipment-code";// 设备码
    private static final String KEY_TYPE = "os-type";// 终端类型

    @Override
    public void onPreExecute(Context context, Map<String, Object> params, Map<String, String> header) {
        //TODO 在这里封装一些请求的公共参数
        // Header
        put2MapNotNull(header, KEY_TOKEN, User.getInstance().getToken(context));
        // 版本号
        put2MapNotNull(header, KEY_VERSION, AppUtils.getAppVersionName(context));
        // 设备码
        put2MapNotNull(header, KEY_IMEI, AppUtils.getDeviceUniqueId(context));
        // 设备类型
        put2MapNotNull(header, KEY_TYPE, HttpConfig.DEVICES_TYPE_ANDROID);
        onPreExecute(context);
    }

    @Override
    public void onResponse(Context context, String token) {

    }

    private void put2MapNotNull(Map<String, String> header, String key, String value) {
        if (!StringUtils.isEmpty(value)) {
            header.put(key, value);
        }
    }

    /**
     * 这里用来解析具体的实体信息，然后用回调方法返回
     * <p>
     * 格式：{"data":null,"code": "200","info":"操作成功"}
     */
    @Override
    public void onSuccess(final Context context, String result) {
        postMainThread(new Runnable() {
            @Override
            public void run() {
                dialogDismiss();
            }
        });
        final Gson gson = GSonUtils.buildGSon();
        try {
            final HttpResponseEntity responseEntity = gson.fromJson(result, HttpResponseEntity.class);
            if (responseEntity != null) {
                // 直接把返回信息转换成的实体回传回去
                postMainThread(new Runnable() {
                    @Override
                    public void run() {
                        onResponseFailData(responseEntity);
                    }
                });
                // 描述信息
                final Object message = responseEntity.getMessage();
                // 状态码
                final String code = responseEntity.getCode();
                if (HttpConfig.CODE_REQUEST_SUCCESS.equals(code)) {// 接口返回成功
                    // 避免数据返回类型不对应转换失败
                    try {
                        convertSuccessData(gson, responseEntity.getResult());
                    } catch (Exception e) {
                        e.printStackTrace();
                        LogUtils.e(TAG, "Json数据转换异常：\nMessage：" + e.getMessage() + "Cause：\n" + e.getCause());
                        postMainThread(new Runnable() {
                            @Override
                            public void run() {
                                // 回调回去
                                onCommonFail(context, BaseHttpConfig.ERROR_CODE_BAD_REQUEST, "");
                            }
                        });
                    }
                } else if (HttpConfig.SERVICE_CODE_NEW_VERSION.equals(code)) { // 版本更新
                    final UpdateEntity updateEntity = gson.fromJson(gson.toJson(responseEntity.getResult()), UpdateEntity.class);
                    postMainThread(new Runnable() {
                        @Override
                        public void run() {
                            onResponseUpdate(context, updateEntity, code, String.valueOf(message));
                        }
                    });
                } else {// 接口返回失败
                    postMainThread(new Runnable() {
                        @Override
                        public void run() {
                            String errorMsg = message == null ? "" : message.toString();
                            String errorCode = String.valueOf(code);
                            // 回调回去
                            onCommonFail(context, errorCode, errorMsg);
                        }
                    });
                }
            } else {
                //TODO 此处以后需要修改
                postMainThread(new Runnable() {
                    @Override
                    public void run() {
                        onResponseError(context, BaseHttpConfig.ERROR_CODE_BAD_REQUEST, "请求超时");
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            postMainThread(new Runnable() {
                @Override
                public void run() {
                    onResponseError(context, BaseHttpConfig.ERROR_CODE_BAD_REQUEST, "请求超时");
                }
            });
        }
    }

    @Override
    public void onSuccess(Context context, byte[] result) {
    }

    /**
     * 处理返回成功的数据
     */
    @SuppressWarnings("unchecked")
    protected void convertSuccessData(Gson gson, final Object data) {
        // 如果返回的是空字符串，统一当null处理
        if (data instanceof String) {
            postMainThread(new Runnable() {
                @Override
                public void run() {
                    onResponseSuccess(StringUtils.isEmpty(String.valueOf(data)) ? null : (T) data);
                }
            });
            return;
        }
        Class<?> clazz = HttpUtils.analysisClassInfo(this);
        // 区分Object类型，如果泛型是Object的，就直接回调回去，不能再转了，否则Gson会把其中的number类型又格式化成浮点类型
        if (clazz == Object.class) {
            postMainThread(new Runnable() {
                @Override
                public void run() {
                    onResponseSuccess((T) data);
                }
            });
            // 不在主线程的方法回调
            onResponseSuccessInThread((T) data);
            // 转换成Map回调
            if (data == null) {
                postMainThread(new Runnable() {
                    @Override
                    public void run() {
                        onResponseSuccessMap(null);
                    }
                });
            } else {
                final Map<String, Object> map = gson.fromJson(gson.toJson(data), new TypeToken<Map<String, Object>>() {
                }.getType());
                postMainThread(new Runnable() {
                    @Override
                    public void run() {
                        onResponseSuccessMap(map);
                    }
                });
            }
        } else {
            final T t = (T) gson.fromJson(gson.toJson(data), clazz);
            postMainThread(new Runnable() {
                @Override
                public void run() {
                    onResponseSuccess(t);
                }
            });
            // 不在主线程的方法回调
            onResponseSuccessInThread(t);
        }
    }

    @Override
    public void onError(final Context context, final String errorCode, final String errorMsg) {
        postMainThread(new Runnable() {
            @Override
            public void run() {
                dialogDismiss();
                // 回调回去
                onResponseError(context, errorCode, errorMsg);
            }
        });
    }


    @Override
    public void onCancel(Context context) {
        LogUtils.e(TAG, "请求被取消");
    }

    /**
     * 加载对话框消失
     */
    private void dialogDismiss() {
        if (mActivity != null) {
            LoadingDialog.getInstance().dismiss(mActivity);
        } else if (mFragment != null) {
            LoadingDialog.getInstance().dismiss(mFragment);
        }
    }


    @Override
    public void onProgressInThread(final Context context, Progress mProgress) {
        boolean isComplete = mProgress.isComplete();
        final long contentLength = mProgress.getContentLength();
        final int progress = mProgress.getProgress();
        if (isComplete) {
            // 下载完成了，将文件大小存储到本地
            PreferencesUtils.saveUpdateAPKSizeToLocal(context, contentLength);
        }
        postMainThread(new Runnable() {
            @Override
            public void run() {
                onProgress(context, progress);
            }
        });
    }

    @Override
    public void onFileSuccess(Context context, String result) {
        postMainThread(new Runnable() {
            @Override
            public void run() {
                onFileSuccess();
            }
        });
    }

    protected void onFileSuccess() {

    }


    /**
     * 这个方法主要是用来暴露给使用者，因为这个方法调用之后网络请求就真正开始了
     * 不用写成抽象方法，因为不一定所有使用者都需要覆盖
     */
    protected void onPreExecute(final Context context) {
        // 控制加载对话框的显示
        if (mShowLoadDialog) {
            LoadingDialog.getInstance().setCancelable(mCancelable);
            postMainThread(new Runnable() {
                @Override
                public void run() {
                    if (mActivity != null) {
                        LoadingDialog.getInstance().show(mActivity, mDialogText);
                    } else if (mFragment != null) {
                        LoadingDialog.getInstance().show(mFragment, mDialogText);
                    }
                }
            });
        }
    }


    /**
     * 涉及具体业务逻辑的返回
     *
     * @param result 返回的实体信息
     */
    protected void onResponseSuccess(T result) {
    }

    /**
     * 涉及具体业务逻辑的返回
     *
     * @param result 返回的实体信息
     */
    protected void onResponseSuccessMap(Map<String, Object> result) {
    }

    /**
     * 返回失败，但是包含业务数据
     */
    protected void onResponseFailData(HttpResponseEntity responseEntity) {
    }


    /**
     * 涉及具体业务逻辑的返回(在子线程执行)
     *
     * @param result 返回的实体信息
     */
    protected void onResponseSuccessInThread(T result) {
    }


    protected void onCommonFail(Context context, String errorCode, String errorMsg) {
        //token过期不要提示
        if (HttpConfig.isNeedLogin(errorCode)) {
            if (StringUtils.isEmpty(errorMsg)) {
                errorMsg = "登录信息超时,请重新登录";
            }
            if (context instanceof Activity) {
                ToastUtils.showToast(context, errorMsg);
            }
            onTokenOutDate(context);
        } else if (HttpConfig.SERVICE_CODE_OTHER_DEVICE.equals(errorCode)) {
            ToastUtils.showToast(context, "您的账号在其他设备上登录，请重新登录");
            onLoginOtherDevices(context);
        } else if (HttpConfig.SERVICE_CODE_SEAL_UP.equals(errorCode)) {// 账号封停
            ToastUtils.showToast(context, "您的账号已经封停");
            // 清除登录数据，通知更新
            new LogoutUtils().logout(context, false, null);
        }
        onResponseFail(context, errorCode, errorMsg);
    }

    /**
     * 接口返回失败
     */
    protected void onResponseFail(Context context, String errorCode, String errorMsg) {
        // 弹Toast显示错误提示
        String showMsg = StringUtils.isEmpty(errorMsg) ? ErrorCodeTips.getErrorString(context, errorCode) : errorMsg;
        if (StringUtils.isEmpty(showMsg)) {
            showMsg = context.getResources().getString(R.string.msg_timeout);
        }
        ToastUtils.showToast(context, showMsg);
    }


    /**
     * Token过期的处理
     */
    private void onTokenOutDate(final Context context) {
        if (context instanceof Application) {
            LogUtils.e(TAG, "Application触发的token过期，只清token，不处理");
            // 清空用户登录信息，避免后面接口提示信息
            User.getInstance().clearUserLoginInfo(context);
            return;
        }
        // 清除登录数据，通知更新
        new LogoutUtils().logout(context, false, new LogoutUtils.LogoutCallback() {
            @Override
            public void onLogout(Context context) {
                // 其他设备登录
                new LoginUtils().onLoginOtherDevices(context);
            }
        });

    }

    /**
     * 其他设备登录
     */
    private void onLoginOtherDevices(Context context) {
        // 清除登录数据，通知更新
        new LogoutUtils().logout(context, false, new LogoutUtils.LogoutCallback() {
            @Override
            public void onLogout(Context context) {
                // 其他设备登录
                new LoginUtils().onLoginOtherDevices(context);
            }
        });
    }


    /**
     * 接口返回错误
     */
    protected void onResponseError(Context context, String errorCode, String errorMsg) {
        String tips = ErrorCodeTips.getErrorString(context, errorCode);
        // 修改的时候需要注意，接口请求前的统一网络连接状态校验也放到了这里，修改需要谨慎
        if (!StringUtils.isEmpty(tips)) {
            ToastUtils.showToast(context, tips);
        } else {
            ToastUtils.showToast(context, errorMsg);
        }
    }

    /**
     * 当返回版本更新
     */
    protected void onResponseUpdate(Context context, UpdateEntity entity, String code, String message) {
        // 显示版本更新的对话框
        if (!User.getInstance().isShowUpdateDialog()) {
            new UpdateUtils(context).updateNewVersion(entity);
        }
        // 回调Fail方法，使得请求为一个完整请求
        onResponseFail(context, code, message);
    }


    /**
     * 进度变化
     */
    protected void onProgress(Context context, int progress) {

    }

    /**
     * 使用阿里ARouter进行页面跳转
     */
    protected void switchActivity(String router, Bundle bundle) {
        ARouter.getInstance().build(router).with(bundle).navigation();
    }

    /**
     * 使用阿里ARouter进行页面跳转
     */
    protected void switchActivity(String router) {
        ARouter.getInstance().build(router).navigation();
    }
}

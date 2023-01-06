package com.naruto.mvvm.base;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;

import com.naruto.mvvm.R;
import com.naruto.mvvm.utils.DialogFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kotlin.Unit;

/**
 * @Purpose
 * @Author Naruto Yang
 * @CreateDate 2020/11/13 0013
 * @Note
 */
public abstract class BaseActivity extends AppCompatActivity implements BaseView {
    private ActivityResultLauncher<String[]> permissionLauncher;//权限申请启动器
    private ActivityResultCallback<Map<String, Boolean>> permissionCallback;//权限申请回调
    private ActivityResultLauncher<Intent> activityLauncher;//Activity启动器
    private ActivityResultCallback<ActivityResult> activityCallback;//Activity启动回调


    public AlertDialog loadingDialog;//加载弹窗
    protected View rootView;//根布局，即getLayoutRes()返回的布局

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutRes());
        //注册权限申请启动器
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions()
                , result -> {
                    permissionCallback.onActivityResult(result);
                    permissionCallback = null;
                });
        //注册Activity启动器
        activityLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult()
                , result -> {
                    activityCallback.onActivityResult(result);
                    activityCallback = null;
                });

        rootView = ((ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content)).getChildAt(0);

        initView();
        init();
    }

    @Override
    public View getRootView() {
        return rootView;
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public LifecycleOwner getLifecycleOwner() {
        return this;
    }

    @Override
    public Context getContext() {
        return this;
    }

    /**
     * 初始化view相关
     */
    protected void initView(){}

    /**
     * 展示等待对话框
     */
    public void showLoadingDialog() {
        showLoadingDialog(getString(R.string.hint_loading));
    }

    /**
     * 展示等待对话框
     */
    public void showLoadingDialog(String msg) {
        if (loadingDialog == null) {
            loadingDialog = DialogFactory.Companion.makeSimpleDialog(this, R.layout.dialog_loading);
            loadingDialog.getWindow().setDimAmount(0f);//移除遮罩层
            loadingDialog.setCancelable(false);
        }
        if (!TextUtils.isEmpty(msg)) loadingDialog.setMessage(msg);
        if (!loadingDialog.isShowing()) {
            loadingDialog.show();
        }
    }

    /**
     * 隐藏等待对话框
     */
    public void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    public void updateLoadingDialogMsg(String msg) {
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.setMessage(msg);
    }

    public void startActivity(Class<? extends Activity> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    /**
     * 检查并申请权限
     *
     * @param callBack
     */
    public void doWithPermission(RequestPermissionsCallBack callBack) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {//6.0以下系统无需动态申请权限
            if (callBack != null) callBack.onGranted();
            return;
        }
        //检查权限
        List<String> requestPermissionsList = checkPermissions(callBack.permissions);//记录需要申请的权限
        if (requestPermissionsList.isEmpty()) {//均已授权
            callBack.onGranted();
        } else if (callBack.autoRequest) {//申请
            String[] requestPermissionsArray = requestPermissionsList.toArray(new String[requestPermissionsList.size()]);
            requestPermissions(requestPermissionsArray, result -> {
                        List<String> refuseList = new ArrayList<>();//被拒绝的权限
                        List<String> shouldShowReasonList = new ArrayList<>();//需要提示申请理由的权限，即没有被设置“不再询问”的权限
                        String permission;
                        for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                            if (entry.getValue()) continue;
                            refuseList.add(permission = entry.getKey());
                            if (shouldShowRequestPermissionRationale(permission))
                                shouldShowReasonList.add(permission);
                        }
                        if (refuseList.isEmpty()) {//全部已授权
                            callBack.onGranted();
                        } else {//被拒绝
                            if (TextUtils.isEmpty(callBack.requestPermissionReason)) {
                                callBack.onDenied(this);//直接执行拒绝后的操作
                            } else {//弹窗
                                if (shouldShowReasonList.isEmpty()) //被设置“不再询问”
                                    showGoToSettingPermissionDialog(callBack);//弹窗引导前往设置页面
                                else
                                    showPermissionRequestReasonDialog(callBack);//弹窗显示申请原因并重新请求权限
                            }
                        }
                    }
            );
        }
    }


    /**
     * startActivityForResult
     *
     * @param intent
     * @param callback
     */
    public void startActivityForResult(Intent intent, ActivityResultCallback<ActivityResult> callback) {
        if (activityCallback != null) {
            Log.e("naruto", "--->startActivityForResult: ", new Exception("ActivityCallback!=null"));
            return;
        }
        activityCallback = callback;
        activityLauncher.launch(intent);
    }

    /**
     * 申请权限
     *
     * @param permissions
     * @param callback
     */
    public void requestPermissions(String[] permissions, ActivityResultCallback<Map<String, Boolean>> callback) {
        if (permissionCallback != null) {
            Log.e("naruto", "--->requestPermissions: ", new Exception("permissionCallback!=null"));
            return;
        }
        permissionCallback = callback;
        permissionLauncher.launch(permissions);
    }


    /**
     * 显示引导设置权限弹窗
     *
     * @param callback
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public AlertDialog showGoToSettingPermissionDialog(RequestPermissionsCallBack callback) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        AlertDialog permissionDialog = DialogFactory.Companion.makeGoSettingDialog(this
                , callback.requestPermissionReason + "，是否前往设置？", intent
                , () -> {
                    callback.onDenied(this);
                    return Unit.INSTANCE;
                }
                , result -> {
                    if (checkPermissions(callback.permissions).isEmpty()) {//已获取权限
                        callback.onGranted();
                    } else {//被拒绝
                        callback.onDenied(BaseActivity.this);
                    }
                });
        permissionDialog.show();
        return permissionDialog;
    }

    /**
     * 显示申请权限理由
     *
     * @param callback
     * @return
     */
    public AlertDialog showPermissionRequestReasonDialog(RequestPermissionsCallBack callback) {
        DialogFactory.DialogData dialogData = new DialogFactory.DialogData();
        dialogData.setTitle("提示");
        dialogData.setContent(callback.requestPermissionReason);
        dialogData.setCancelText("取消");
        dialogData.setConfirmText("授予");
        dialogData.setCancelListener((v) -> callback.onDenied(this));
        dialogData.setConfirmListener((v) -> doWithPermission(callback));
        AlertDialog dialog = DialogFactory.Companion.makeSimpleDialog(this, dialogData);
        dialog.show();
        return dialog;
    }


    /**
     * 检查权限
     *
     * @param permissions
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    protected List<String> checkPermissions(String... permissions) {
        List<String> list = new ArrayList<>();
        for (String p : permissions) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {//未授权，记录下来
                list.add(p);
            }
        }
        return list;
    }

    /**
     * 启动其他页面
     *
     * @param activityClass
     */
    protected void launchActivity(Class<? extends Activity> activityClass) {
        Intent intent = new Intent(this, activityClass);
        startActivity(intent);
    }

    /**
     * @Purpose 申请权限后处理接口
     * @Author Naruto Yang
     * @CreateDate 2019/12/19
     * @Note
     */
    public static abstract class RequestPermissionsCallBack {
        public String[] permissions;
        public String requestPermissionReason;
        public boolean autoRequest = true;//是否自动申请权限

        public RequestPermissionsCallBack(String requestPermissionReason, String... permissions) {
            this.permissions = permissions;
            this.requestPermissionReason = requestPermissionReason;
        }

        public RequestPermissionsCallBack(String[] permissions) {
            this(null, permissions);
        }

        public void setAutoRequest(boolean autoRequest) {
            this.autoRequest = autoRequest;
        }

        /**
         * 已授权
         */
        public abstract void onGranted();

        /**
         * 被拒绝
         *
         * @param context
         */
        public void onDenied(Context context) {
            Toast.makeText(context, "授权失败", Toast.LENGTH_SHORT).show();
        }

    }

}

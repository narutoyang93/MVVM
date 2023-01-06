package com.naruto.mvvm;

import com.naruto.mvvm.utils.FileUtil;

/**
 * @Description 全局配置文件
 * @Author Naruto Yang
 * @CreateDate 2021/4/30 0030
 * @Note
 */
public class Config {
    public static final String BASE_URL = "https://www.baidu.com/";

    public static final int CONNECT_TIMEOUT = 10;//网络连接超时（单位：s）
    public static final int FILE_TIMEOUT = 60;//文件相关超时

    //http响应码
    public static final int RESPONSE_CODE_SUCCESS = 200000;//请求成功
    public static final int RESPONSE_CODE_TOKEN_INVALID = 40003;//token失效
    public static final int RESPONSE_CODE_LOGIN_PASSWORD_INCORRECT = 400002;//登录密码错误

    public static final String DIR_APP_FOLDER = BuildConfig.APP_NAME + "/";
    public static final String DIR_ANNEX = "annex/";//附件下载目录
    private static final String DIR_APK_DOWNLOAD = "download/apk/";
    private static final String DIR_CRASH_LOG = "crash/";

    public static final String TIME_FORMAT_FILE_NAME = "yyyyMMdd_HHmmssSSS";//用于文件名的时间格式
    public static final int DEFAULT_PAGE_SIZE = 10;//默认分页

    /**
     * 获取apk下载路径
     *
     * @return
     */
    public static String getDirApkDownload() {
        return FileUtil.getPathFromExternalPrivateSpace(DIR_APK_DOWNLOAD);
    }

}

package com.naruto.mvvm.http;

import com.naruto.mvvm.Config;

import java.util.concurrent.TimeUnit;

import kotlin.jvm.functions.Function2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2021/4/30 0030
 * @Note
 */
public class APIFactory {
    private static final int TYPE_NORMAL = 1;//普通
    private static final int TYPE_FILE = 2;//文件
    private static Retrofit retrofit = getRetrofit(TYPE_NORMAL);
    private static Retrofit fileRetrofit = getRetrofit(TYPE_FILE);
    public static Function2<Request, Request.Builder, Request.Builder> builderOperation;//用于设置通用header之类的

    private static Retrofit getRetrofit(int type) {
        //开启Log
        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient.Builder builder = new OkHttpClient.Builder().addInterceptor(httpLoggingInterceptor);
        if (builderOperation != null) builder.addInterceptor(chain -> {
            Request request = chain.request();
            return chain.proceed(builderOperation.invoke(request, request.newBuilder()).build());
        });

        if (type == TYPE_FILE) {
            builder.writeTimeout(Config.FILE_TIMEOUT, TimeUnit.SECONDS);
            builder.readTimeout(Config.FILE_TIMEOUT, TimeUnit.SECONDS);
//            builder.callTimeout(Config.FILE_TIMEOUT, TimeUnit.SECONDS);
        }
        builder.connectTimeout(Config.CONNECT_TIMEOUT, TimeUnit.SECONDS);


        //创建 OkHttpClient
        OkHttpClient client = builder.build();

        //创建 Retrofit 对象
        return new Retrofit.Builder()
                .baseUrl(Config.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())// 添加Gson转换器
                .client(client)
                .build();
    }

    public static <T> T getAPI(Class<T> tClass) {
        return retrofit.create(tClass);
    }

    public static <T> T getFileApi(Class<T> tClass) {
        return fileRetrofit.create(tClass);
    }

}

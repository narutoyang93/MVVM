package com.naruto.mvvm.base;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.LifecycleOwner;

/**
 * @Description 提取BaseActivity和BaseFragment的公共方法
 * @Author Naruto Yang
 * @CreateDate 2021/8/23 0023
 * @Note
 */
public interface BaseView {
    Context getContext();

    View getRootView();

    Activity getActivity();

    LifecycleOwner getLifecycleOwner();

    /**
     * 获取布局资源
     *
     * @return
     */
    int getLayoutRes();

    /**
     * 初始化
     */
    void init();

    default <T extends ViewDataBinding> T getDataBinding() {
        T binding = DataBindingUtil.getBinding(getRootView());
        if (binding == null)
            binding = DataBindingUtil.bind(getRootView());
        return binding;
    }

}

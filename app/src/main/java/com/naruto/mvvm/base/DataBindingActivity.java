package com.naruto.mvvm.base;

import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;

/**
 * @Purpose 使用DataBinding的activity
 * @Author Naruto Yang
 * @CreateDate 2019/8/26 0026
 * @Note
 */
public abstract class DataBindingActivity<T extends ViewDataBinding> extends BaseActivity {
    protected T dataBinding;
    private boolean b = true;//避免循环调用setContentView

    @Override
    public void setContentView(int layoutResID) {
        if (b) {
            b = false;//DataBindingUtil.setContentView会调用setContentView，利用b避免循环调用
            dataBinding = DataBindingUtil.setContentView(this, layoutResID);
        } else {
            super.setContentView(layoutResID);
        }
    }

    @Override
    public T getDataBinding() {
        return  dataBinding;
    }
}

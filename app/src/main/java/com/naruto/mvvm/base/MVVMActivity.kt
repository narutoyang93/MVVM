package com.naruto.mvvm.base

import android.util.Log
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.lang.reflect.ParameterizedType

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/3/11 0011
 * @Note
 */
abstract class MVVMActivity<T : ViewDataBinding, VM : ViewModel> : DataBindingActivity<T>() {
    protected val viewModel: VM by lazy {
        (getViewModelFactory()?.let { ViewModelProvider(this, it) }
            ?: run { ViewModelProvider(this) })
            .get(getViewModelClass())
    }

    protected fun getViewModelClass(): Class<VM> {
        var clazz: Class<*> = javaClass
        while (clazz.superclass != MVVMActivity::class.java) {
            clazz = clazz.superclass
        }
        val type = clazz.genericSuperclass
        if (type != null && type is ParameterizedType) {
            val actualTypeArguments = type.actualTypeArguments
            val tClass = actualTypeArguments[1]
            return tClass as Class<VM>
        }
        TODO("异常")
    }

    protected fun getViewModelFactory(): ViewModelProvider.Factory? {
        return null
    }

    override fun initView() {
        super.initView()
        if (isNeedBindingViewModelToLayout()) bindingViewModel()
        dataBinding.lifecycleOwner = this
    }

    protected fun isNeedBindingViewModelToLayout() = true

    protected fun bindingViewModel() {
        kotlin.runCatching {
            dataBinding.javaClass.getMethod("setViewModel", getViewModelClass())
                .invoke(dataBinding, viewModel)
        }.onFailure {
            if (it is NoSuchMethodException)
                Log.e("MVVMActivity", "--->请在layout文件中设置参数：viewModel", it)
        }
    }

}
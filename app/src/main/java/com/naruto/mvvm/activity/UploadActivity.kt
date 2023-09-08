package com.naruto.mvvm.activity

import androidx.lifecycle.viewModelScope
import com.naruto.mvvm.MyApplication
import com.naruto.mvvm.R
import com.naruto.mvvm.base.MVVMActivity
import com.naruto.mvvm.databinding.ActivityUploadBinding
import com.naruto.mvvm.filetransfer.Result
import com.naruto.mvvm.filetransfer.Status
import com.naruto.mvvm.filetransfer.UploadListener
import com.naruto.mvvm.filetransfer.UploadVM
import com.naruto.mvvm.setMyOnClickListener
import com.naruto.mvvm.utils.DialogFactory
import com.naruto.mvvm.utils.LogUtils
import kotlinx.coroutines.launch

/**
 * @Description 上传文件
 * @Author Naruto Yang
 * @CreateDate 2023/9/6 0006
 * @Note
 */
class UploadActivity : MVVMActivity<ActivityUploadBinding, UploadActivity.VM>() {

    override fun init() {
        dataBinding.btn.setMyOnClickListener { viewModel.uploadFile() }
        dataBinding.btnCancel.setMyOnClickListener { viewModel.cancel() }
        viewModel.result.observe(this) {
            LogUtils.i("--->")
            when (it) {
                is Result.Failure -> DialogFactory
                    .makeSimpleDialog(this, R.layout.dialog_hint, "上传失败", it.throwable.message)
                    .show()

                is Result.Success<*> -> {
                    MyApplication.toast("上传完毕")
                    LogUtils.i("--->${it.data}")
                }

                else -> {}
            }
        }
    }

    override fun getLayoutRes(): Int = R.layout.activity_upload

    class VM : UploadVM<String>() {
        override fun onStatusChanged(status: Status) {
            LogUtils.i("--->")
        }

        fun uploadFile() {
            upload(
                viewModelScope, "https://api.github.com/markdown/raw", { MyApplication.context.getAssets().open("abc.mp3") },
                object : UploadListener<String>(this) {
                    override fun onProgress(transferredBytes: Long, totalBytes: Long) {
                        (transferredBytes.toFloat() / totalBytes * 100).let {
                            viewModelScope.launch {
                                _transferProgress.value = it.toInt()
                            }
                        }
                    }
                })
        }

    }
}
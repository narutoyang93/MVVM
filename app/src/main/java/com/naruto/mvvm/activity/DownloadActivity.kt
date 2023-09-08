package com.naruto.mvvm.activity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.naruto.mvvm.MyApplication
import com.naruto.mvvm.R
import com.naruto.mvvm.base.MVVMActivity
import com.naruto.mvvm.databinding.ActivityDownloadBinding
import com.naruto.mvvm.filetransfer.Result
import com.naruto.mvvm.filetransfer.Status
import com.naruto.mvvm.filetransfer.DownloadListener
import com.naruto.mvvm.filetransfer.DownloadVM
import com.naruto.mvvm.setMyOnClickListener
import com.naruto.mvvm.utils.DialogFactory
import kotlinx.coroutines.launch

/**
 * @Description 下载示例（断点续传）
 * @Author Naruto Yang
 * @CreateDate 2022/5/18 0018
 * @Note
 */
class DownloadActivity : MVVMActivity<ActivityDownloadBinding, DownloadActivity.VM>() {

    override fun init() {
        dataBinding.btn.setMyOnClickListener { viewModel.downloadFile() }
        dataBinding.btnCancel.setMyOnClickListener { viewModel.cancel() }
        viewModel.result.observe(this) {
            when (it) {
                is Result.Failure -> DialogFactory
                    .makeSimpleDialog(this, R.layout.dialog_hint, "下载失败", it.throwable.message)
                    .show()

                is Result.Success<*> -> MyApplication.toast("下载完毕")
                else -> {}
            }
        }
    }

    override fun getLayoutRes() = R.layout.activity_download


    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/3/23 0023
     * @Note
     */
    class VM : DownloadVM() {
        private val _label = MutableLiveData("下载")
        val label: LiveData<String>
            get() = _label

        override fun onStatusChanged(status: Status) {
            when (status) {
                Status.Ready -> _label.value = "下载"
                Status.Going -> {}
                else -> Status.Unknown.also { _label.value = "UNKNOW" }
            }
        }

        fun downloadFile() {
            val kuGouApp =
                "https://packagebssdlbig.kugou.com/Android/KugouPlayer/11709/KugouPlayer_201_V11.7.0.apk"
            download(viewModelScope, "", kuGouApp, object : DownloadListener(this) {
                override fun onProgress(transferredBytes: Long, totalBytes: Long) {
                    (transferredBytes.toFloat() / totalBytes * 100).let {
//                            LogUtils.i("--->downloadedBytes=$downloadedBytes;totalBytes=$totalBytes;progress=$it")
                        viewModelScope.launch {
                            _transferProgress.value = it.toInt()
                            _label.value = "已下载 " + if (totalBytes <= 0L) "0.0%"
                            else String.format("%.1f%%", it)
                        }
                    }
                }
            })
        }

    }
}
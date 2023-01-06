package com.naruto.mvvm.activity

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naruto.mvvm.MyApplication
import com.naruto.mvvm.R
import com.naruto.mvvm.base.MVVMActivity
import com.naruto.mvvm.databinding.ActivityDownloadBinding
import com.naruto.mvvm.http.DownloadListener
import com.naruto.mvvm.http.DownloadUtil
import com.naruto.mvvm.setMyOnClickListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    }

    override fun getLayoutRes() = R.layout.activity_download


    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/3/23 0023
     * @Note
     */
    class VM : ViewModel() {
        private lateinit var httpJob: Job
        private val _label = MutableLiveData("下载")
        val label: LiveData<String>
            get() = _label

        private val _status = MutableLiveData(Status.Ready)
        val status: LiveData<Status>
            get() = _status

        private val _downloadProgress = MutableLiveData(0)
        val downloadProgress: LiveData<Int>
            get() = _downloadProgress

        private fun changeStatus(status: Status) {
            when (status.also { _status.value = status }) {
                Status.Ready -> _label.value = "下载"
                Status.Downloading -> {}
                else -> Status.Unknown.also { _label.value = "UNKNOW" }
            }
        }

        fun cancel() {
            viewModelScope.launch {
                httpJob.cancelAndJoin()
                changeStatus(Status.Ready)
            }
        }

        fun downloadFile() {
            DownloadUtil.download(viewModelScope, "",
                "http://210.21.9.132:61112/collection_V2.5.6.apk", object : DownloadListener() {
                    override fun onStart(httpJob: Job) {
                        super.onStart(httpJob)
                        this@VM.httpJob = httpJob
                        viewModelScope.launch { changeStatus(Status.Downloading) }
                    }

                    override fun onProgress(downloadedBytes: Long, totalBytes: Long) {
                        (downloadedBytes.toFloat() / totalBytes * 100).let {
//                            LogUtils.i("--->downloadedBytes=$downloadedBytes;totalBytes=$totalBytes;progress=$it")
                            viewModelScope.launch {
                                _downloadProgress.value = it.toInt()
                                _label.value = "已下载 " + if (totalBytes <= 0L) "0.0%"
                                else String.format("%.1f%%", it)
                            }
                        }
                    }

                    override fun onComplete(uri: Uri) {
                        super.onComplete(uri)
                        viewModelScope.launch {
                            MyApplication.toast("下载完毕")
                            changeStatus(Status.Ready)
                        }
                    }
                })
        }

        enum class Status {
            Ready, Downloading, Unknown
        }
    }
}
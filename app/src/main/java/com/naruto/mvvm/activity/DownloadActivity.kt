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
import com.naruto.mvvm.utils.DialogFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

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
                is VM.Result.Failure -> DialogFactory
                    .makeSimpleDialog(this, R.layout.dialog_hint, "下载失败", it.throwable.message)
                    .show()

                is VM.Result.Success -> MyApplication.toast("下载完毕")
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
    class VM : ViewModel() {
        private val sdf = SimpleDateFormat("yyyy_dd_MM_HH_mm_ss", Locale.getDefault())
        private lateinit var httpJob: Job
        private val _label = MutableLiveData("下载")
        val label: LiveData<String>
            get() = _label

        private val _status = MutableLiveData(Status.Ready)
        val status: LiveData<Status>
            get() = _status

        private var _result = MutableLiveData<Result>(null)
        val result: LiveData<Result>
            get() = _result

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
            val kuGouApp =
                "https://packagebssdlbig.kugou.com/Android/KugouPlayer/11709/KugouPlayer_201_V11.7.0.apk"
            DownloadUtil.download(viewModelScope, "", kuGouApp, object : DownloadListener() {
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
                    onFinish(Result.Success(uri, fileName))
                }

                override fun onError(throwable: Throwable) {
                    super.onError(throwable)
                    onFinish(Result.Failure(throwable))
                }

                fun onFinish(result: Result) {
                    viewModelScope.launch {
                        changeStatus(Status.Ready)
                        _result.value = result
                    }
                }
            })
        }

        enum class Status {
            Ready, Downloading, Unknown
        }

        sealed class Result {
            class Success(val uri: Uri, val fileName: String) : Result()
            class Failure(val throwable: Throwable) : Result()
        }
    }
}
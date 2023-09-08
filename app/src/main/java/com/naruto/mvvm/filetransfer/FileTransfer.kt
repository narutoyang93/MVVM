package com.naruto.mvvm.filetransfer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naruto.mvvm.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2023/9/7 0007
 * @param T http response
 * @param R Result data
 * @Note
 */
abstract class TransferVM<T, R, L : TransferListener<R>> : ViewModel() {
    protected val sdf = SimpleDateFormat("yyyy_dd_MM_HH_mm_ss", Locale.getDefault())
    internal lateinit var httpJob: Job

    protected val _status = MutableLiveData(Status.Ready)
    val status: LiveData<Status>
        get() = _status

    internal var _result = MutableLiveData<Result>(null)
    val result: LiveData<Result>
        get() = _result

    protected val _transferProgress = MutableLiveData(0)
    val transferProgress: LiveData<Int>
        get() = _transferProgress

    internal fun changeStatus(status: Status) {
        _status.value = status
        onStatusChanged(status)
    }

    protected fun transfer(
        coroutineScope: CoroutineScope, transferListener: L, doTransferHttp: suspend () -> T?
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            kotlin.runCatching { doTransferHttp()!! }
                .onSuccess { onHttpSuccess(it, transferListener) }
                .onFailure { transferListener.onError(it) }
        }.also { transferListener.onStart(it) }
    }

    fun cancel() {
        viewModelScope.launch {
            httpJob.cancelAndJoin()
            changeStatus(Status.Ready)
        }
    }

    protected abstract fun onStatusChanged(status: Status)
    protected abstract fun onHttpSuccess(response: T, transferListener: L)
}

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2023/9/7 0007
 * @Note
 */
abstract class TransferListener<T>(protected val viewModel: TransferVM<*, *, *>) {
    internal var httpJob: Job? = null
    var transferredBytes = 0L
     var totalBytes = 0L
    var hasCancelled = false
    internal open fun onStart(httpJob: Job) {
        LogUtils.i("--->onStart")
        this.httpJob = httpJob
        viewModel.httpJob = httpJob
        runOnViewModelScope { viewModel.changeStatus(Status.Going) }
    }

    internal fun onReadBytes(len: Long) {
        transferredBytes += len
        onProgress(transferredBytes, totalBytes)
    }

    internal open fun onComplete(data: T) {
        onFinish(Result.Success(data))
    }

    internal open fun onError(throwable: Throwable) {
        LogUtils.e("--->", throwable)
        onFinish(Result.Failure(throwable))
    }

    protected open fun onFinish(result: Result) {
        runOnViewModelScope {
            viewModel.changeStatus(Status.Ready)
            viewModel._result.value = result
        }
    }

    open fun onCancel() {
        if (hasCancelled) return//防止执行多次
        hasCancelled = true
        LogUtils.i("--->transferredBytes=$transferredBytes")
    }

    private fun runOnViewModelScope(block: suspend CoroutineScope.() -> Unit) {
        viewModel.viewModelScope.launch(block = block)
    }

    abstract fun onProgress(transferredBytes: Long, totalBytes: Long)
}


enum class Status {
    Ready, Going, Unknown
}

sealed class Result {
    class Success<T>(val data: T) : Result()
    class Failure(val throwable: Throwable) : Result()
}
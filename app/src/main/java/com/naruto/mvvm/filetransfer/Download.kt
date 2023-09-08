package com.naruto.mvvm.filetransfer

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.datastore.preferences.core.longPreferencesKey
import com.naruto.mvvm.http.APIFactory
import com.naruto.mvvm.utils.CommonDataStore
import com.naruto.mvvm.utils.FileUtil
import com.naruto.mvvm.utils.LogUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.ResponseBody
import okhttp3.internal.http2.StreamResetException
import okio.Source
import okio.buffer
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.lang.ref.WeakReference
import java.net.SocketException

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2021/11/18 0018
 * @Note
 */
abstract class DownloadVM : TransferVM<ResponseBody, Uri, DownloadListener>() {
    private lateinit var fileRelativePath: String

    /**
     * 根据存储位置和下载链接生成对应的DataStoreKey，用于存储下载进度
     */
    private fun getDataStoreKey(fileRelativePath: String, url: String): String {
        return "$fileRelativePath($url)"
    }

    /**
     * 下载
     * @param coroutineScope CoroutineScope
     * @param fileRelativePath String 相对路径，如：”download/picture/“
     * @param url String
     * @param downloadListener DownloadListener
     * @param rename Function1<String, String>
     */
    fun download(
        coroutineScope: CoroutineScope, fileRelativePath: String,
        url: String, downloadListener: DownloadListener,
        rename: ((String) -> String) = { it }//重命名的方法，参数是从url截取的默认文件名
    ) {
        downloadListener.run {
            fileName = rename(url.substringAfterLast("/"))
            dataStoreKey = getDataStoreKey(fileRelativePath, url)
            cacheParamForRetry(coroutineScope, fileRelativePath, url)
        }
        val block = {
            checkLocalFile(downloadListener.fileName, fileRelativePath)
            { fileUri, fileSize, hasCompleted ->
                if (hasCompleted) downloadListener.onComplete(fileUri!!)
                else {
                    this.fileRelativePath = fileRelativePath
                    downloadListener.init(fileUri, fileSize)
                    doDownload(coroutineScope, url, fileSize, downloadListener)
                }
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
            FileUtil.doWithStoragePermission { block() }
        else block()
    }

    /**
     * 执行下载
     * @param coroutineScope CoroutineScope
     * @param url String
     * @param startPos Long //下载开始位置，即之前的下载进度，用于实现断点续传
     * @param downloadListener DownloadListener
     */
    internal fun doDownload(
        coroutineScope: CoroutineScope, url: String, startPos: Long,
        downloadListener: DownloadListener
    ) {
        transfer(coroutineScope, downloadListener)
        { downloadApi.downloadFile("bytes=$startPos-", url)!! }
    }


    /**
     * 检查本地是否有已下载的文件或临时文件，以此决定继续下载or直接返回
     */
    private fun checkLocalFile(
        fileName: String, fileRelativePath: String, callback: (Uri?, Long, Boolean) -> Unit
    ) {
        val filter = FileUtil.MyFileFilter(
            null, { dir, name -> name.startsWith(fileName) },
            MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ? ", arrayOf("$fileName%")
        )
        FileUtil.getFilesInExternalPublicSpace(
            FileUtil.MediaType.DOWNLOAD, fileRelativePath, filter, { it }, { list ->
                if (list.isEmpty()) callback(null, 0, false)
                else {
                    list.forEach { data ->
                        data.fileName?.run {
                            if (endsWith(fileName.substringAfterLast("."))) {//已经下载过了
                                callback(data.fileUri, data.size, true)
                                return@getFilesInExternalPublicSpace
                            }
                            if (contains(".download")) {//曾经下载中断
                                callback(data.fileUri, data.size, false)
                                return@getFilesInExternalPublicSpace
                            }
                        }
                    }
                    callback(null, 0, false)
                }
            }
        )
    }

    /**
     * 将内容写进文件
     */
    private fun writeDataToFile(
        responseBody: ResponseBody, fileRelativePath: String, downloadListener: DownloadListener
    ) {
        if (downloadListener.httpJob!!.isCancelled) {
            downloadListener.onCancel()
            return
        }
        if (downloadListener.fileUri == null) {
            val tempFileName = downloadListener.fileName + ".download"
            FileUtil.createDownloadFileInExternalPublicSpace(fileRelativePath, tempFileName) {
                downloadListener.fileUri = it
                writeDataToFile(responseBody, downloadListener)
            }
        } else writeDataToFile(responseBody, downloadListener)
    }

    /**
     * 将内容写进文件
     */
    private fun writeDataToFile(responseBody: ResponseBody, downloadListener: DownloadListener) {
        if (downloadListener.httpJob!!.isCancelled) {//判断线程状态
            downloadListener.onCancel()
            return
        }
        //开始写入数据
        val uri = downloadListener.fileUri
        FileUtil.writeDataToFile({ outputStream ->
            if (!downloadListener.onReady(responseBody.contentLength()))
                throw ResourceChangedException()
            val buffer = ByteArray(1024 * 4)
            var len: Int
//        val stream = object : ForwardingSource(responseBody.source()) {}.buffer().inputStream()
            //这里必须调用的是Source.buffer()而不是BufferedSource.buffer()
            val stream = (responseBody.source() as Source).buffer().inputStream()
            while (true) {
                downloadListener.httpJob!!.ensureActive()
                len = stream.read(buffer)
                if (len == -1) break//下载完毕
                outputStream.write(buffer, 0, len)
                downloadListener.onReadBytes(len.toLong())//更新进度
            }
        }, { FileUtil.getOutputStream(uri, true) }, {
            if (it == null) {
                FileUtil.rename(uri, downloadListener.fileName)
                downloadListener.onComplete(uri!!)
            } else when (it) {
                is SocketException, is CancellationException -> downloadListener.onCancel()
                is ResourceChangedException -> downloadListener.onResourceChanged()
                is StreamResetException -> downloadListener.onError(it)
                else -> downloadListener.onError(Throwable("写入文件失败:${it.message}"))
            }
        })
    }

    override fun onHttpSuccess(response: ResponseBody, transferListener: DownloadListener) {
        writeDataToFile(response, fileRelativePath, transferListener)
    }

}


/**
 * @Description 下载监听
 * @Author Naruto Yang
 * @CreateDate 2021/11/19 0019
 * @Note
 */
abstract class DownloadListener(viewModel: TransferVM<*, *, *>) : TransferListener<Uri>(viewModel) {
    private lateinit var paramCache: ParamCache
    lateinit var dataStoreKey: String
    var fileUri: Uri? = null
    lateinit var fileName: String

    internal fun cacheParamForRetry(
        coroutineScope: CoroutineScope, fileRelativePath: String, url: String
    ) {
        paramCache = ParamCache(WeakReference(coroutineScope), fileRelativePath, url)
    }

    internal fun init(fileUri: Uri?, downloadedBytes: Long) {
        this.fileUri = fileUri
        transferredBytes = downloadedBytes
        LogUtils.i("--->downloadedBytes=$downloadedBytes")
    }

    /**
     * 计算文件总大小，并与之前记录的进行比较，若不一致，则说明服务端的资源文件已被替换，不可继续下载
     */
    internal fun onReady(remainBytes: Long): Boolean {
        totalBytes = transferredBytes + remainBytes
        val record = runBlocking { CommonDataStore.getLongValue(dataStoreKey, -1L).first() }
        if (record == -1L) {
            CoroutineScope(Dispatchers.IO).launch {
                CommonDataStore.setLongValue(dataStoreKey, totalBytes)
            }
            return true
        }
        return record == totalBytes
    }

    override fun onComplete(data: Uri) {
        super.onComplete(data)
        CoroutineScope(Dispatchers.IO).launch {
            CommonDataStore.remove(longPreferencesKey(dataStoreKey))
        }
        LogUtils.i("--->下载完成")
    }

    open fun onResourceChanged() {
        LogUtils.w("--->此处代码（onResourceChanged）暂未测试，不保证没有bug")
        FileUtil.delete(fileUri)
        CoroutineScope(Dispatchers.IO).launch {
            CommonDataStore.remove(longPreferencesKey(dataStoreKey))
        }

        (viewModel as? DownloadVM)?.also { downloadVM ->
            paramCache.takeIf { it.coroutineScopeWF.get() != null }?.let { param ->
                downloadVM.doDownload(param.coroutineScopeWF.get()!!, param.url, 0, this)
            }
        } ?: kotlin.run { onError(ResourceChangedException()) }
    }

    /*    open fun onPause() {}
        open fun onResume() {}*/

    data class ParamCache(
        val coroutineScopeWF: WeakReference<CoroutineScope>,
        val fileRelativePath: String, val url: String
    )
}

private val downloadApi by lazy { APIFactory.getFileApi(DownloadApi::class.java) }

interface DownloadApi {
    /**
     * 下载文件，支持断点续传
     */
    @Streaming
    @GET
    suspend fun downloadFile(@Header("Range") range: String, @Url url: String): ResponseBody?
}

/**
 * @Description url指向的服务端文件已改变
 * @Author Naruto Yang
 * @CreateDate 2022/1/27 0027
 * @Note
 */
private class ResourceChangedException : Exception("Resource has change")
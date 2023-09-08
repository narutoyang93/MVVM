package com.naruto.mvvm.filetransfer

import androidx.datastore.preferences.protobuf.Api
import com.naruto.mvvm.http.APIFactory
import com.naruto.mvvm.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2023/9/7 0007
 * @Note
 */
abstract class UploadVM<T> : TransferVM<T, T, UploadListener<T>>() {
  private  val MEDIA_TYPE= "text/x-markdown; charset=utf-8".toMediaType()
    override fun onHttpSuccess(response: T, transferListener: UploadListener<T>) {
        transferListener.onComplete(response)
    }

     fun upload(
        coroutineScope: CoroutineScope, url: String,
        inputStreamCreator: () -> InputStream, uploadListener: UploadListener<T>
    ) {
        var buffer = ByteArray(0)
        kotlin.runCatching { inputStreamCreator() }
            .onSuccess {
                it.use { inputStream ->
                    buffer = ByteArray(inputStream.available())
                    val fileSize: Int = inputStream.read(buffer)
                    LogUtils.i("--->uploadFile: fileSize=$fileSize")
                }
            }
            .onFailure { it.printStackTrace();uploadListener.onError(it) ;return}

        transfer(coroutineScope,uploadListener){
            uploadApi.uploadFile(url,UploadProgressRequestBody(buffer,MEDIA_TYPE,uploadListener))
        }
    }
}


abstract class UploadListener<T>(viewModel: TransferVM<*, *, *>) : TransferListener<T>(viewModel) {

}

/**
 * @Description 上传字节数组进度监听
 * @Author Naruto Yang
 * @CreateDate 2023/8/17 0017
 * @Note
 */
class UploadProgressRequestBody(
    private val data: ByteArray, private val mediaType: MediaType,
    private val progressListener: UploadListener<*>
) : RequestBody() {

    init {
        progressListener.totalBytes = contentLength()
    }

    override fun contentType(): MediaType {
        return mediaType
    }

    @Throws(IOException::class)
    override fun contentLength(): Long {
        return data.size.toLong()
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val source = ByteArrayInputStream(data).source()
        val buf = Buffer()
        var readCount: Long
        while (source.read(buf, 2048).also { readCount = it } != -1L) {
            sink.write(buf, readCount)
            progressListener.onReadBytes(readCount)
        }
    }
}

private val uploadApi by lazy { APIFactory.getFileApi(UploadApi::class.java) }

private interface UploadApi {
    /**
     * 下载文件，支持断点续传
     */
    @Streaming
    @POST
    suspend fun <T> uploadFile(@Url url: String, @Body requestBody: RequestBody): T?
}
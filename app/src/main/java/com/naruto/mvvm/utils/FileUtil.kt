package com.naruto.mvvm.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.TextUtils
import android.view.View
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider.getUriForFile
import androidx.documentfile.provider.DocumentFile
import com.naruto.mvvm.BuildConfig
import com.naruto.mvvm.MyApplication
import com.naruto.mvvm.base.BaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.*
import java.util.*


/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/1/17 0017
 * @Note
 */
object FileUtil {
    private val APP_FOLDER = BuildConfig.APP_NAME + "/"

    private fun getContext(): Context = MyApplication.context

    /**
     * @param relativePath    相对根目录（/storage/emulated/0/）的路径，不以“/”开头，但以“/”结尾
     */
    fun createSAFIntent(
        operation: Operation, relativePath: String,
        fileName: String? = null, fileNameSuffix: String? = null
    ): Intent {
        var rp: String = relativePath
        while (rp.endsWith("/")) {
            rp = rp.substring(0, rp.length - 1)
        }
        val relativePath0 = rp.replace("/", "%2F")
        val uri =
            Uri.parse("content://com.android.externalstorage.documents/document/primary:$relativePath0")

        val action = when (operation) {
            Operation.CREATE -> {
                fileName ?: throw Exception("FileName is required.")
                Intent.ACTION_CREATE_DOCUMENT
            }
            Operation.OPEN -> Intent.ACTION_OPEN_DOCUMENT
            else -> Intent.ACTION_OPEN_DOCUMENT_TREE
        }
        return Intent(action).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            (fileNameSuffix ?: (fileName?.substringAfterLast(".") ?: "")).run {
                if (isNotEmpty()) type = getMimeTypeFromExtension(this)
            }
            putExtra(Intent.EXTRA_TITLE, fileName ?: "")
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
        }
    }

    /**
     * @param relativePath    相对应用私有目录（/.../APP_NAME_EN/）的路径，不以“/”开头，但以“/”结尾
     * 注意：暂未找到让SAF实现mkdirs的方法，故若相对路径不存在，会自动导向Download/文件夹
     */
    fun createSAFIntentForCreateFile(
        mediaType: MediaType, relativePath: String, fileName: String
    ): Intent {
        val rp = when (mediaType) {
            MediaType.AUDIO -> Environment.DIRECTORY_MUSIC
            MediaType.IMAGE -> Environment.DIRECTORY_PICTURES
            MediaType.VIDEO -> Environment.DIRECTORY_MOVIES
            MediaType.FILE -> Environment.DIRECTORY_DOCUMENTS
            MediaType.DOWNLOAD -> Environment.DIRECTORY_DOWNLOADS;
        } + "/$APP_FOLDER$relativePath"
        return createSAFIntent(Operation.CREATE, rp, fileName)
    }


    /**
     * 打开文件
     */
    fun openFile(
        activity: Activity, fileUri: Uri,
        fileName: String? = null, fileNameSuffix: String? = null
    ) {
        //获取文件file的MIME类型
        val type: String? = (fileNameSuffix ?: (fileName?.substringAfterLast(".") ?: "")).run {
            if (isNotEmpty()) getMimeTypeFromExtension(this)
            else throw Exception("FileName/fileNameSuffix is required.")
        }
        val intent = Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.action = Intent.ACTION_VIEW
        intent.setDataAndType(fileUri, type)
        activity.startActivity(intent)
    }

    /**
     * 获取文件写入流
     *
     * @param uri      uri
     * @param isAppend 是否追加模式
     * @return 输入流
     */
    fun getOutputStream(uri: Uri?, isAppend: Boolean): OutputStream? {
        return uri?.let { getContentResolver().openOutputStream(it, if (isAppend) "wa" else "rw") }
    }

    /**
     * 写文件
     *
     * @param bytes
     * @param iOutputStream
     * @param callback      回调
     */
    private fun writeDataToExternalPublicSpaceFile(
        bytes: ByteArray, outputStreamProvider: (() -> OutputStream?),
        callback: ((Boolean) -> Unit)?
    ) {
        outputStreamProvider().use {
            val result = if (it == null) false else {
                it.write(bytes)
                it.flush()
                true
            }
            if (callback != null) callback(result)
        }
    }


    /**
     * 写文件
     */
    fun writeDataToExternalPublicSpaceFile(
        bytes: ByteArray, mediaType: MediaType, relativePath: String, fileName: String,
        isAppend: Boolean, callback: ((Boolean) -> Unit)?
    ) {
        val uri = getFileInExternalPublicSpace(mediaType, relativePath, fileName)
        if (uri == null) {
            createFileInExternalPublicSpace(mediaType, relativePath, fileName) { uri0 ->
                uri0?.let {
                    writeDataToExternalPublicSpaceFile(
                        bytes, { getOutputStream(it, isAppend) }, callback
                    )
                }
            }
        } else writeDataToExternalPublicSpaceFile(
            bytes, { getOutputStream(uri, isAppend) }, callback
        )
    }


    /**
     * 截取视图保存文件
     */
    fun saveViewToFile(view: View, fileUri: Uri) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        BufferedOutputStream(view.context.contentResolver.openOutputStream(fileUri)).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.flush()
            it.close()
        }
    }


    /**
     * 根据文件扩展名获取对应的 MimeType
     *
     * @param extension
     * @return
     */
    fun getMimeTypeFromExtension(extension: String?): String? {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).also {
            LogUtils.i("--->extension=$extension;MimeType=$it")
        }
    }

    private fun getContentResolver(): ContentResolver {
        return getContext().contentResolver
    }

    /**
     * @param mediaType
     * @return
     */
    private fun getMediaStoreData(mediaType: MediaType): MediaData {
        var directory: String? = null
        var contentUri: Uri? = null
        when (mediaType) {
            MediaType.AUDIO -> {
                directory = Environment.DIRECTORY_MUSIC
                contentUri =
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            MediaType.IMAGE -> {
                directory = Environment.DIRECTORY_PICTURES
                contentUri =
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            MediaType.VIDEO -> {
                directory = Environment.DIRECTORY_MOVIES
                contentUri =
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            MediaType.FILE -> {
                directory = Environment.DIRECTORY_DOCUMENTS
                contentUri = MediaStore.Files.getContentUri("external")
            }
            MediaType.DOWNLOAD -> {
                directory = Environment.DIRECTORY_DOWNLOADS
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }
            }
        }
        return MediaData(directory = directory, contentUri = contentUri)
    }

    /**
     * 根据文件获取Uri
     *
     * @param file
     * @return
     */
    fun getUriForFile(file: File): Uri {
        val context: Context = MyApplication.context
        return getUriForFile(context, context.packageName + ".app.fileProvider", file)
    }

    /**
     * 根据URI创建文件
     *
     * @param contentUri
     * @param relativePath
     * @param fileName
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun createFile(contentUri: Uri, relativePath: String, fileName: String): Uri? {
        val contentValues = ContentValues()
        contentValues.put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        contentValues.put(
            MediaStore.Downloads.MIME_TYPE,
            getMimeTypeFromExtension(fileName.substringAfterLast("."))
        )
        contentValues.put(MediaStore.Downloads.DATE_TAKEN, System.currentTimeMillis())
        contentValues.put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
        return getContentResolver().insert(contentUri, contentValues)
    }

    /**
     * 创建文件
     *
     * @param folderPath 文件夹绝对路径
     * @param fileName
     * @return
     */
    private fun createFile(folderPath: String, fileName: String): Uri? {
        val storageDir = File(folderPath)
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            LogUtils.e("--->mkdirs失败")
            return null
        }
        val file = File(folderPath + fileName)
        try {
            if (!file.createNewFile()) return null
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return getUriForFile(file)
    }

    /**
     * 删除Uri对应的资源
     */
    fun delete(uri: Uri?): Boolean {
        if (uri == null) return true
/*        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val file: File = FileUtil.getFileByUri(uri)
            if (file.exists()) file.delete() else true
        } else*/
        return getContentResolver().delete(uri, null, null) > 0
    }

    /**
     * 重命名
     *
     * @param FileUri
     * @param newName
     * @return
     */
    fun rename(FileUri: Uri?, newName: String): Boolean {
        if (FileUri == null) {
            LogUtils.e("FileUri==null")
            return false
        }
        if (TextUtils.isEmpty(newName)) {
            LogUtils.e("newName is empty")
            return false
        }
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val oldFile: File = getFileByUri(FileUri)
            if (!oldFile.exists()) {
                LogUtils.e("File not found")
                return false
            }
            val newFile = File(oldFile.parent + File.separator + newName)
            oldFile.renameTo(newFile)
        } else {
            val updateValues = ContentValues()
            updateValues.put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
            updateFileInExternalPublicSpace(FileUri, updateValues)
        }
    }

    /**
     * 根据Uri获取File
     *
     * @param uri
     * @return
     */
    private fun getFileByUri(uri: Uri): File {
        return File(uri.path)
    }

    /**
     * 外部私有空间，卸载即删除，读写无需申请权限
     *
     * @param relativePath 相对路径，如：”download/picture/“
     * @return
     */
    @JvmStatic
    fun getPathFromExternalPrivateSpace(relativePath: String): String {
        return getContext().getExternalFilesDir(null)!!.absolutePath +
                File.separator + relativePath
    }

    /**
     * 外部公共空间，读写需申请权限
     *
     * @param mediaType
     * @param relativePath
     * @return
     */
    fun getPathFromExternalPublicSpace(mediaType: MediaType, relativePath: String): String {
        val mediaData: MediaData = getMediaStoreData(mediaType)
        return getPathFromExternalPublicSpace(mediaData.directory!!, relativePath)
    }

    /**
     * 外部公共空间，读写需申请权限
     *
     * @param systemDirectory
     * @param relativePath
     * @return
     */
    private fun getPathFromExternalPublicSpace(systemDirectory: String, relativePath: String)
            : String {
        return getPathFromExternalPublicSpace(systemDirectory) + "/" + APP_FOLDER + relativePath
    }

    private fun getPathFromExternalPublicSpace(systemDirectory: String): String {
        return Environment.getExternalStoragePublicDirectory(systemDirectory).absolutePath
    }

    /**
     * 获取sd根目录下的相对路径
     *
     * @param mediaType
     * @param relativePath
     * @return
     */
    fun getRelativePathInRoot(mediaType: MediaType, relativePath: String): String {
        return getRelativePathInRoot(getMediaStoreData(mediaType).directory!!, relativePath)
    }

    /**
     * 获取sd根目录下的相对路径
     *
     * @param systemDirectory
     * @param relativePath
     * @return
     */
    private fun getRelativePathInRoot(systemDirectory: String, relativePath: String): String {
        return "$systemDirectory/$APP_FOLDER$relativePath"
    }

    /**
     * 在外部私有空间创建文件
     *
     * @param relativePath
     * @param fileName
     * @return
     */
    fun createFileInExternalPrivateSpace(relativePath: String, fileName: String): Uri? {
        val folderPath: String =
            getPathFromExternalPrivateSpace(relativePath)
        return createFile(folderPath, fileName)
    }


    /**
     * 在外部公共存储空间创建文件
     *
     * @param mediaType
     * @param relativePath
     * @param fileName     文件名，需带后缀名
     * @return
     */
    private fun createFileInExternalPublicSpace(
        mediaType: MediaType, relativePath: String, fileName: String, callback: (Uri?) -> Unit
    ) {
        if (TextUtils.isEmpty(fileName)) {
            callback(null)
            return
        }
        val mediaData: MediaData = getMediaStoreData(mediaType)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            doWithStoragePermission {
                val folderPath: String =
                    getPathFromExternalPublicSpace(mediaData.directory!!, relativePath)
                callback(createFile(folderPath, fileName))
            }
        } else {
            try {
                val path = getRelativePathInRoot(mediaData.directory!!, relativePath)
                val uri: Uri? = createFile(mediaData.contentUri!!, path, fileName)
                callback(uri)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 在外部公共存储空间创建音频文件
     *
     * @param relativePath
     * @param fileName
     * @param callback
     */
    fun createAudioFileInExternalPublicSpace(
        relativePath: String, fileName: String, callback: (Uri?) -> Unit
    ) {
        createFileInExternalPublicSpace(MediaType.AUDIO, relativePath, fileName, callback)
    }

    /**
     * 在外部公共存储空间创建视频文件
     *
     * @param relativePath
     * @param fileName
     * @param callback
     */
    fun createVideoFileInExternalPublicSpace(
        relativePath: String, fileName: String, callback: (Uri?) -> Unit
    ) {
        createFileInExternalPublicSpace(MediaType.VIDEO, relativePath, fileName, callback)
    }

    /**
     * 在外部公共存储空间创建图像文件
     *
     * @param relativePath
     * @param fileName
     * @param callback
     */
    fun createImageFileInExternalPublicSpace(
        relativePath: String, fileName: String, callback: (Uri?) -> Unit
    ) {
        createFileInExternalPublicSpace(MediaType.IMAGE, relativePath, fileName, callback)
    }

    /**
     * 在外部公共存储空间创建文本文件
     *
     * @param relativePath
     * @param fileName
     * @param callback
     */
    fun createDocumentFileInExternalPublicSpace(
        relativePath: String, fileName: String, callback: (Uri?) -> Unit
    ) {
        createFileInExternalPublicSpace(MediaType.FILE, relativePath, fileName, callback)
    }

    /**
     * 在外部公共存储空间创建下载文件
     *
     * @param relativePath
     * @param fileName
     * @param callback
     */
    fun createDownloadFileInExternalPublicSpace(
        relativePath: String, fileName: String, callback: (Uri?) -> Unit
    ) {
        createFileInExternalPublicSpace(MediaType.DOWNLOAD, relativePath, fileName, callback)
    }

    /**
     * 获取外部公共存储空间文件
     *
     * @param mediaData
     * @param selection
     * @param selectionArgs
     * @param sortOrder
     * @param fileInfoCreator
     * @param <T>
     * @return
    </T> */
    fun <T> getFileInExternalPublicSpace(
        mediaData: MediaData, selection: String?, selectionArgs: Array<String>?, sortOrder: String?,
        fileInfoCreator: (MediaData) -> T
    ): List<T> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE
        )
        val list: MutableList<T> = ArrayList()
        getContentResolver().query(
            mediaData.contentUri!!, projection, selection, selectionArgs, sortOrder
        ).use { cursor ->
            if (cursor == null) return list
            val idColumn: Int = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn: Int = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val relativePathColumn: Int =
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val durationColumn: Int = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val createTimeColumn: Int =
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeColumn: Int = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val id: Long = cursor.getLong(idColumn)
                val name: String = cursor.getString(nameColumn)
                val relativePath: String = cursor.getString(relativePathColumn)
                val duration: Int = cursor.getInt(durationColumn)
                val size: Long = cursor.getInt(sizeColumn).toLong()
                val createTime: Long = cursor.getLong(createTimeColumn)
                val fileUri = ContentUris.withAppendedId(mediaData.contentUri, id)
                val data = MediaData(
                    id, fileUri, name, null, relativePath, duration, size, createTime
                )
                fileInfoCreator(data)?.run { list.add(this) }
            }
        }
        return list
    }

    /**
     * 获取外部公共空间的文件
     *
     * @param mediaType
     * @param relativePath
     * @param fileName
     * @return
     */
    fun getFileInExternalPublicSpace(
        mediaType: MediaType, relativePath: String, fileName: String
    ): Uri? {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val folderPath: String = getPathFromExternalPublicSpace(mediaType, relativePath)
            getUriForFile(File(folderPath + fileName))
        } else {
            val mediaData: MediaData = getMediaStoreData(mediaType)
            val selection =
                MediaStore.MediaColumns.DISPLAY_NAME + "=? and " + MediaStore.MediaColumns.RELATIVE_PATH + "=?"
            val args = arrayOf(fileName, getRelativePathInRoot(mediaData.directory!!, relativePath))
            val list: List<Uri> =
                getFileInExternalPublicSpace(mediaData, selection, args, null) { it.fileUri!! }
            if (list.isEmpty()) null else list[0]
        }
    }


    /**
     * 获取外部公共存储空间文件
     *
     * @param mediaType
     * @param selection
     * @param selectionArgs
     * @param sortOrder
     * @param fileInfoCreator
     * @param <T>
     * @return
    </T> */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    fun <T> getFilesInExternalPublicSpace(
        mediaType: MediaType,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?,
        fileInfoCreator: (MediaData) -> T
    ): List<T> {
        val mediaData = getMediaStoreData(mediaType)
        return getFilesInExternalPublicSpace(
            mediaData,
            selection,
            selectionArgs,
            sortOrder,
            fileInfoCreator
        )
    }

    /**
     * 获取外部公共存储空间文件
     *
     * @param mediaData
     * @param selection
     * @param selectionArgs
     * @param sortOrder
     * @param fileInfoCreator
     * @param <T>
     * @return
    </T> */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    fun <T> getFilesInExternalPublicSpace(
        mediaData: MediaData,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?,
        fileInfoCreator: (MediaData) -> T
    ): List<T> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE
        )
        val list: MutableList<T> = ArrayList()
        getContentResolver().query(
            mediaData.contentUri!!,
            projection,
            selection,
            selectionArgs,
            sortOrder
        ).use { cursor ->
            if (cursor == null) return list
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val relativePathColumn =
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val durationColumn =
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val createTimeColumn =
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val relativePath = cursor.getString(relativePathColumn)
                val duration = cursor.getInt(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val createTime =
                    cursor.getLong(createTimeColumn) * 1000 //MediaStore里存储的createTime单位是秒，这里*1000转毫秒
                val fileUri = ContentUris.withAppendedId(mediaData.contentUri, id)
                val data =
                    MediaData(id, fileUri, name, null, relativePath, duration, size, createTime)
                val item: T = fileInfoCreator.invoke(data)
                if (item != null) list.add(item)
            }
        }
        return list
    }


    /**
     * 获取外部公共空间的文件
     *
     * @param mediaType
     * @param relativePath
     * @param myFileFilter
     * @param fileInfoCreator
     * @param callback
     * @param <T>
    </T> */
    fun <T> getFilesInExternalPublicSpace(
        mediaType: MediaType,
        relativePath: String,
        myFileFilter: MyFileFilter?,
        fileInfoCreator: (MediaData) -> T,
        callback: (List<T>) -> Unit
    ) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val folderPath: String = getPathFromExternalPublicSpace(mediaType, relativePath)
            getFileInExternalSpace(folderPath, myFileFilter, fileInfoCreator, callback)
        } else {
            val data = getMediaStoreData(mediaType)
            var selection = MediaStore.MediaColumns.RELATIVE_PATH + "=?"
            var args = arrayOf<String?>(getRelativePathInRoot(data.directory!!, relativePath))
            if (myFileFilter != null && myFileFilter.selection.isNotEmpty()) { //有过滤条件
                if (!myFileFilter.selection.trim().lowercase().startsWith("and"))
                    selection += " and "
                selection += myFileFilter.selection
                if (myFileFilter.selectionArgs != null && myFileFilter.selectionArgs.isNotEmpty()) { //合并参数
                    val a = args
                    val b = myFileFilter.selectionArgs
                    val c = arrayOfNulls<String>(a.size + b.size)
                    System.arraycopy(a, 0, c, 0, a.size)
                    System.arraycopy(b, 0, c, a.size, b.size)
                    args = c
                }
            }
            val list: List<T> =
                getFilesInExternalPublicSpace(data, selection, args, null, fileInfoCreator)
            callback.invoke(list)
        }
    }

    /**
     * 获取外部公共空间的文件
     *
     * @param mediaType
     * @param relativePath
     * @param fileName
     * @param callback
     */
    fun getFileInExternalPublicSpace(
        mediaType: MediaType,
        relativePath: String,
        fileName: String,
        callback: (MediaData?) -> Unit
    ) {
        val filenameFilter = FilenameFilter { dir: File?, name: String -> name == fileName }
        val selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ? "
        getFilesInExternalPublicSpace(mediaType, relativePath,
            MyFileFilter(null, filenameFilter, selection, arrayOf(fileName)),
            { mediaData -> mediaData },
            { list -> callback.invoke(if (list.isEmpty()) null else list[0]) })
    }


    /**
     * 获取外部公共空间文件
     *
     * @param mediaType
     * @param relativePath
     * @param fileName
     * @return
     */
    fun getFilePathInExternalPublicSpace(
        mediaType: MediaType,
        relativePath: String,
        fileName: String
    ): String? {
        if (TextUtils.isEmpty(relativePath)) {
            LogUtils.e("relativePath is empty")
            return null
        } else if (TextUtils.isEmpty(fileName)) {
            LogUtils.e("fileName is empty")
            return null
        }
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val folderPath: String = getPathFromExternalPublicSpace(mediaType, relativePath)
            if (TextUtils.isEmpty(folderPath)) null else File(folderPath + fileName).absolutePath
        } else {
            val uri = getFileInExternalPublicSpace(mediaType, relativePath, fileName)
            uri?.toString()
        }
    }


    /**
     * 获取外部非公共空间的文件
     *
     * @param relativePath    相对根目录（/storage/emulated/0/）的路径，不以“/”开头，但以“/”结尾
     * @param fileInfoCreator
     * @param callback
     * @param <T>
    </T> */
    @SuppressLint("CheckResult")
    fun <T> getFileInExternalNonPublicSpace(
        relativePath: String, fileInfoCreator: (MediaData) -> T, callback: (List<T>) -> Unit
    ) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val folderPath =
                Environment.getExternalStorageDirectory().toString() + "/" + relativePath
            getFileInExternalSpace(folderPath, null, fileInfoCreator, callback)
            return
        }
        val spKey = "treeUri_$relativePath"
        val operation: (DocumentFile) -> Unit = { df ->
            val files: Array<DocumentFile> = df.listFiles()
            var mediaData: MediaData
            val list: MutableList<T> = ArrayList()
            for (f in files) {
                mediaData = MediaData(
                    f.uri, fileName = f.name, size = f.length(), createTime = f.lastModified()
                )
                list.add(fileInfoCreator.invoke(mediaData))
            }
            callback.invoke(list)
        }
        val treeUriString = runBlocking { CommonDataStore.getStringValue(spKey, "").first() }
        if (!TextUtils.isEmpty(treeUriString)) {
            kotlin.runCatching {
                val treeUri = Uri.parse(treeUriString)
                //检查权限
                getContentResolver()
                    .takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                DocumentFile.fromTreeUri(getContext(), treeUri)
            }.getOrNull()?.let {
                operation.invoke(it)
                return
            }
        }
        //前往授权
        val message = "由于当前系统限制，访问外部非共享文件需获取局部访问权限，即将打开设置页面，请在打开的页面点击底部按钮"
        val confirmListener: View.OnClickListener =
            object : View.OnClickListener {
                override fun onClick(v: View) {
                    var rp = relativePath
                    while (rp.endsWith("/")) {
                        rp = rp.substring(0, rp.length - 1)
                    }
                    val relativePath0 = rp.replace("/", "%2F")
                    val uri =
                        Uri.parse("content://com.android.externalstorage.documents/document/primary:$relativePath0")
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                    MyApplication.doByActivity { activity ->
                        activity.startActivityForResult(intent) { result ->
                            if (result.resultCode == Activity.RESULT_OK)
                                result.data?.data?.let { treeUri ->
                                    DocumentFile.fromTreeUri(getContext(), treeUri)?.let { df ->
                                        val uriStr = treeUri.toString()
                                        if (uriStr.endsWith(relativePath0)) {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                CommonDataStore.setStringValue(spKey, uriStr)
                                            }
                                            //永久保存获取的目录权限
                                            getContentResolver().takePersistableUriPermission(
                                                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            )
                                            operation.invoke(df)
                                        } else activity.runOnUiThread {
                                            DialogFactory.makeSimpleDialog(
                                                activity, title = "操作失败",
                                                content = "授权文件夹与目标文件夹不一致，请重新设置（设置局部权限时请勿选择其他文件夹）",
                                                confirmListener = this
                                            ).show()
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        //弹窗
        MyApplication.doByActivity { activity ->
            activity.runOnUiThread {
                DialogFactory.makeSimpleDialog(
                    activity, title = "提示", content = message,
                    confirmListener = confirmListener
                ).show()
            }
        }
    }


    /**
     * 获取外部空间的文件
     *
     * @param folderPath
     * @param myFileFilter
     * @param fileInfoCreator
     * @param callback
     * @param <T>
    </T> */
    private fun <T> getFileInExternalSpace(
        folderPath: String,
        myFileFilter: MyFileFilter?,
        fileInfoCreator: (MediaData) -> T,
        callback: (List<T>) -> Unit
    ) {
        assert(Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) { "Android 10 不支持" }
        MyApplication.doWithPermission(object :
            BaseActivity.RequestPermissionsCallBack(
                null,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) {
            override fun onGranted() {
                val list: MutableList<T> = ArrayList()
                val folder = File(folderPath)
                val files = if (myFileFilter == null) folder.listFiles() else { //有过滤条件
                    if (myFileFilter.filenameFilter != null) folder.listFiles(myFileFilter.filenameFilter)
                    else folder.listFiles(myFileFilter.fileFilter)
                }
                if (files != null && files.isNotEmpty()) for (f in files) {
                    MediaData(getUriForFile(f), fileName = f.name, absolutePath = f.absolutePath)
                        .run {
                            createTime = f.lastModified()
                            size = f.length()
                            list.add(fileInfoCreator.invoke(this))
                        }
                }
                callback.invoke(list)
            }
        })
    }

    /**
     * 更新外部存储空间的文件
     *
     * @param mediaType
     * @param selection
     * @param selectionArgs
     * @param updateValues
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    fun updateFileInExternalPublicSpace(
        mediaType: MediaType,
        selection: String?,
        selectionArgs: Array<String?>?,
        updateValues: ContentValues?
    ): Boolean {
        val mediaData = getMediaStoreData(mediaType)
        val uriList = getFilesInExternalPublicSpace(mediaData, selection, selectionArgs, null)
        { it.fileUri!! }
        return kotlin.runCatching {
            for (uri in uriList) {
                updateFileInExternalPublicSpace(uri, updateValues)
            }
        }.isSuccess
    }

    fun updateFileInExternalPublicSpace(fileUri: Uri?, updateValues: ContentValues?): Boolean {
        return getContentResolver().update(fileUri!!, updateValues, null, null) > 0
    }

    /**
     * 重命名
     *
     * @param mediaType
     * @param relativePath
     * @param oldFileName
     * @param newFileName
     * @return
     */
    fun renameFileInExternalPublicSpace(
        mediaType: MediaType,
        relativePath: String,
        oldFileName: String,
        newFileName: String
    ): Boolean {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val folderPath: String = getPathFromExternalPublicSpace(mediaType, relativePath)
            val old = File(folderPath + oldFileName)
            old.renameTo(File(folderPath + newFileName))
        } else {
            val selection =
                MediaStore.MediaColumns.DISPLAY_NAME + "=? and " + MediaStore.MediaColumns.RELATIVE_PATH + "=?"
            val args = arrayOf<String?>(
                oldFileName, getRelativePathInRoot(
                    mediaType,
                    relativePath
                )
            )
            val updateValues = ContentValues()
            updateValues.put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
            updateFileInExternalPublicSpace(mediaType, selection, args, updateValues)
        }
    }

    /**
     * 执行需要存储权限的操作
     * 外部需判断 if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
     *
     * @param operation
     */
    fun doWithStoragePermission(autoRequest: Boolean = true, operation: () -> Unit) {
        MyApplication.doWithPermission(object : BaseActivity.RequestPermissionsCallBack(
            null, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) {
            override fun onGranted() = operation()
        }.apply { setAutoRequest(autoRequest) })
    }

    /**
     * 写文件
     *
     * @param bytes
     * @param iOutputStream
     * @param callback      回调
     */
    private fun writeDataToFile(
        bytes: ByteArray,
        iOutputStream: () -> OutputStream,
        callback: (Boolean) -> Unit
    ) {
        writeDataToFile({ outputStream ->
            outputStream.write(bytes)
        }, iOutputStream, { exception -> callback(exception == null) })
    }

    /**
     * 写文件
     *
     * @param writeDataFunc
     * @param iOutputStream
     * @param callback
     */
    fun writeDataToFile(
        writeDataFunc: (OutputStream) -> Unit,
        iOutputStream: () -> OutputStream?,
        callback: ((java.lang.Exception?) -> Unit)?
    ) {
        var exception: java.lang.Exception? = null
        try {
            iOutputStream()!!.use { outputStream ->
                writeDataFunc(outputStream)
                outputStream.flush()
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            exception = e
        }
        callback?.invoke(exception)
    }

    /**
     * 写文件
     *
     * @param bytes
     * @param uri      uri地址
     * @param callback 回调
     */
    fun writeDataToFile(bytes: ByteArray, uri: Uri?, callback: ((Boolean) -> Unit)) {
        if (uri == null) {
            callback(false)
            return
        }
        writeDataToFile(
            bytes, { BufferedOutputStream(getContentResolver().openOutputStream(uri)) }, callback
        )
    }

    fun writeDataToFile(
        bytes: ByteArray, fileName: String, savePath: String, callback: ((Boolean) -> Unit)
    ) {
        //创建文件目录
        val dir = File(savePath)
        if (!dir.exists() && !dir.isDirectory) {
            dir.mkdirs()
        }
        writeDataToFile(bytes, {
            val fos = FileOutputStream(File(savePath + File.separator + fileName))
            BufferedOutputStream(fos)
        }, callback)
    }


    /**
     * @Description 文件过滤
     * @Author Naruto Yang
     * @CreateDate 2021/7/18 0018
     * @Note
     */
    data class MyFileFilter(
        val fileFilter: FileFilter? = null,
        val filenameFilter: FilenameFilter? = null,
        val selection: String,
        val selectionArgs: Array<String>?
    )


    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/1/24 0024
     * @Note
     */
    data class MediaData(
        val contentUri: Uri? = null,
        val directory: String? = null,
        val id: Long = 0,
        val fileUri: Uri? = null,
        val fileName: String? = null,
        val absolutePath: String? = null,
        val relativePath: String? = null,
        val duration: Int = 0,
        var size: Long = 0,
        var createTime: Long = 0
    ) {
        constructor (
            id: Long,
            fileUri: Uri?,
            fileName: String?,
            absolutePath: String?,
            relativePath: String?,
            duration: Int,
            size: Long,
            createTime: Long
        ) : this(
            null, null, id, fileUri, fileName, absolutePath, relativePath,
            duration, size, createTime
        )
    }

    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/1/17 0017
     * @Note
     */
    enum class Operation {
        CREATE, OPEN, SELECT_TREE
    }

    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2021/7/7 0007
     * @Note
     */
    enum class MediaType {
        AUDIO, VIDEO, IMAGE, FILE, DOWNLOAD
    }
}
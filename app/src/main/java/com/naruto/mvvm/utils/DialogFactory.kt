package com.naruto.mvvm.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.naruto.mvvm.R
import com.naruto.mvvm.base.BaseActivity

/**
 * @Description 构建弹窗
 * @Author Naruto Yang
 * @CreateDate 2022/3/9 0009
 * @Note
 */
class DialogFactory {
    companion object {
        /**
         * 弹窗提示信息
         *
         * @param messageResId
         * @param message
         * @param activity
         * @return
         */
        fun showHintDialog(
            messageResId: Int,
            message: String?,
            confirmText: String?,
            contentGravityCenter: Boolean,
            activity: Activity,
            onConfirmListener: View.OnClickListener?
        ): AlertDialog {
            val dialog = makeSimpleDialog(
                activity, R.layout.dialog_hint, contentResId = messageResId, content = message,
                confirmText = confirmText, confirmListener = onConfirmListener,
                contentGravityCenter = contentGravityCenter
            )
            dialog.show()
            return dialog
        }

        /**
         * 前往设置页面弹窗
         *
         * @param activity
         * @param message
         * @param intent
         * @param onCancel
         * @param activityResultCallback
         * @return
         */
        fun makeGoSettingDialog(
            activity: BaseActivity, message: String?, intent: Intent?, onCancel: () -> Unit,
            activityResultCallback: ActivityResultCallback<ActivityResult?>?
        ): AlertDialog {
            return makeSimpleDialog(activity, title = "提示", content = message, confirmText = "去设置",
                cancelListener = { onCancel() },
                confirmListener = {
                    activity.startActivityForResult(
                        intent,
                        activityResultCallback
                    )
                })
        }


        /**
         * 构建弹窗
         * @param context Context
         * @param data DialogData
         * @param viewProcessor Function1<View, Unit>?
         * @return AlertDialog
         */
        @JvmOverloads
        fun makeSimpleDialog(
            context: Context, data: DialogData, viewProcessor: ((View) -> Unit)? = null
        ): AlertDialog {
            data.run {
                return makeSimpleDialog(
                    context,
                    layoutResId,
                    title,
                    content,
                    cancelText,
                    confirmText,
                    contentResId,
                    contentGravityCenter,
                    cancelListener,
                    confirmListener,
                    isNeedCancelBtn,
                    viewProcessor
                )
            }
        }

        /**
         * 构建弹窗
         * @param context Context
         * @param layoutResId Int 布局id
         * @param title String? 标题
         * @param content String? 内容
         * @param cancelText String? 取消按钮文本
         * @param confirmText String? 确定按钮文本
         * @param contentResId Int 内容文本资源id
         * @param contentGravityCenter Boolean
         * @param cancelListener OnClickListener?
         * @param confirmListener OnClickListener?
         * @param isNeedCancelBtn Boolean
         * @return Pair<AlertDialog, View>
         */
        @JvmOverloads
        fun makeSimpleDialog(
            context: Context,
            @LayoutRes layoutResId: Int = R.layout.dialog_simple_2_button,
            title: String? = null,
            content: String? = null,
            cancelText: String? = null,
            confirmText: String? = null,
            @StringRes contentResId: Int = -1,
            contentGravityCenter: Boolean = true,
            cancelListener: View.OnClickListener? = null,
            confirmListener: View.OnClickListener? = null,
            isNeedCancelBtn: Boolean = true,
            viewProcessor: ((View) -> Unit)? = null
        ): AlertDialog {
            val builder = AlertDialog.Builder(context, R.style.DefaultDialogStyle)
            val view = LayoutInflater.from(context).inflate(layoutResId, null)
            val dialog = builder.setView(view).create()
            //设置文本
            setText(view, R.id.tv_title, title)
            if (isNeedCancelBtn) setText(view, R.id.btn_cancel, cancelText)
            else {
                val cancelBtn = view.findViewById<View>(R.id.btn_cancel)
                if (cancelBtn != null) cancelBtn.visibility = View.GONE
            }
            setText(view, R.id.btn_confirm, confirmText)
            if (contentResId != -1) {
                (view.findViewById<View>(R.id.tv_content) as TextView).setText(contentResId)
            } else setText(view, R.id.tv_content, content)
            if (!contentGravityCenter)
                doWithTextView(view, R.id.tv_content) { tv: TextView -> tv.gravity = Gravity.LEFT }

            //设置点击事件
            setOnClickListener(dialog, view, R.id.btn_cancel, cancelListener)
            setOnClickListener(dialog, view, R.id.btn_confirm, confirmListener)
            viewProcessor?.invoke(view)
            return dialog
        }

        /**
         * 设置文本
         *
         * @param dialogView
         * @param textViewId
         * @param text
         */
        private fun setText(dialogView: View, textViewId: Int, text: String?) {
            doWithTextView(dialogView, textViewId) { textView ->
                if (!TextUtils.isEmpty(text)) {
                    textView.text = text
                    textView.visibility = View.VISIBLE
                }
            }
        }

        private fun doWithTextView(
            dialogView: View,
            textViewId: Int,
            operation: (TextView) -> Unit
        ) {
            val textView = dialogView.findViewById<TextView>(textViewId)
            if (textView != null) operation(textView)
        }

        /**
         * 设置点击监听
         *
         * @param dialog
         * @param dialogView
         * @param viewId
         * @param onClickListener
         */
        private fun setOnClickListener(
            dialog: AlertDialog,
            dialogView: View,
            viewId: Int,
            onClickListener: View.OnClickListener?
        ) {
            dialogView.findViewById<View>(viewId)?.let {
                it.setOnClickListener { v ->
                    dialog.dismiss()
                    onClickListener?.onClick(v)
                }
            }
        }
    }

    /**
     * @Purpose 弹窗所需配置的数据
     * @Author Naruto Yang
     * @CreateDate 2020/4/01 0001
     * @Note
     */
    data class DialogData(
        @LayoutRes var layoutResId: Int = R.layout.dialog_simple_2_button, //布局id
        var title: String? = null,//标题
        var content: String? = null,//内容
        var cancelText: String? = null,//取消按钮文本
        var confirmText: String? = null,//确定按钮文本
        @StringRes var contentResId: Int = -1,
        var contentGravityCenter: Boolean = true,//内容文本资源id
        var cancelListener: View.OnClickListener? = null,
        var confirmListener: View.OnClickListener? = null,
        var isNeedCancelBtn: Boolean = true
    )
}
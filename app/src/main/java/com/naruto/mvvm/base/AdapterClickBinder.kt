package com.naruto.mvvm.base

import android.util.SparseArray
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.naruto.mvvm.setMyOnClickListener

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/5/24 0024
 * @Note
 */
class AdapterClickBinder {

    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/5/24 0024
     * @Note
     */
    interface FullClickBinder<T : Any, VH : RecyclerView.ViewHolder> : CommonClickBinder {
        var onItemClickListener: OnItemClickListener<T>? //绑定item点击事件，用于整个item点击
        var itemClickable: Boolean //item是否可以点击

        /**
         * 点击拦截，用于扩展某些时候需要根据情况判断点击item时是否拦截点击事件
         * @param holder VH?
         * @return Boolean
         */
        fun checkClickIntercept(holder: VH?): Boolean = false

        fun binding(
            holder: VH, getItemFunc: (Int) -> T?, holderClickBinder: (BatchClickBinder) -> Unit
        ) {
            //item上的view点击
            binding(holderClickBinder)
            //整个item点击
            onItemClickListener?.run {
                val listener = View.OnClickListener {
                    if (itemClickable && !checkClickIntercept(holder))
                        holder.bindingAdapterPosition.let { onClick(getItemFunc(it), it) }
                }
                if (isWithoutClickInterval) holder.itemView.setOnClickListener(listener)
                else holder.itemView.setMyOnClickListener(listener)
            }
        }

    }

    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/5/24 0024
     * @Note
     */
    interface CommonClickBinder {
        var batchClickBinder: BatchClickBinder? //绑定item点击事件，用于item上多个view点击
        var clickBinderMap: SparseArray<ClickBinder>? //绑定item点击事件

        /**
         * binding点击事件
         * @param holder VH
         * @param holderClickBinder Function2<VH, BatchClickBinder, Unit>
         */
        fun binding(holderClickBinder: (BatchClickBinder) -> Unit) {
            clickBinderMap?.run {
                val ids = IntArray(size())
                for (i in ids.indices) {
                    ids[i] = keyAt(i)
                }

                batchClickBinder = object : BatchClickBinder {
                    override fun getClickableViewIds(): IntArray = ids

                    override fun onClick(v: View?, position: Int) {
                        v?.id?.let { get(it).onClick(v, position) }
                    }
                }
            }
            batchClickBinder?.let { holderClickBinder(it) }
        }

        /**
         * 设置item上多个view点击事件
         * @param clickBinders Array<out ClickBinder>
         */
        fun addClickBinder(vararg clickBinders: ClickBinder) {
            if (clickBinders.isEmpty()) return
            clickBinderMap ?: SparseArray<ClickBinder>().also { clickBinderMap = it }.let {
                for (c in clickBinders) {
                    it.put(c.viewId, c)
                }
            }
        }
    }

    /**
     * @Description 点击事件接口，用于整个item点击
     * @Author Naruto Yang
     * @CreateDate 2022/5/24 0024
     * @Note
     */
    interface OnItemClickListener<T> {
        val isWithoutClickInterval: Boolean //是否不限制两次点击事件时间间隔（默认500ms内只允许一次点击事件）
            get() = false

        fun onClick(data: T?, position: Int)
    }

    /**
     * @Purpose 用于批量绑定item内部view点击事件
     * @Author Naruto Yang
     * @CreateDate 2019/12/4
     * @Note
     */
    interface BatchClickBinder {
        //需要设置点击监听的view的Id
        fun getClickableViewIds(): IntArray

        fun onClick(v: View?, position: Int)
    }

    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2021/5/31 0031
     * @Note
     */
    abstract class ClickBinder(var viewId: Int) {
        abstract fun onClick(v: View?, position: Int)
    }
}
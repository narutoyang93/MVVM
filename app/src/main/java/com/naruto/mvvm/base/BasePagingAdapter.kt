package com.naruto.mvvm.base

import android.text.TextUtils
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.naruto.mvvm.R
import com.naruto.mvvm.databinding.ErrorPageBinding
import com.naruto.mvvm.databinding.FooterLoadMoreBinding
import com.naruto.mvvm.doClick
import com.naruto.mvvm.utils.LogUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.properties.Delegates

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/5/19 0019
 * @Note
 */
abstract class BasePagingAdapter<T : Any>(
    @LayoutRes private val itemLayoutRes: Int,
    diffCallback: DiffUtil.ItemCallback<T>,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    workerDispatcher: CoroutineDispatcher = Dispatchers.Default
) : PagingDataAdapter<T, BasePagingAdapter.VH>(diffCallback, mainDispatcher, workerDispatcher),
    AdapterClickBinder.FullClickBinder<T, BasePagingAdapter.VH> {

    override var batchClickBinder: AdapterClickBinder.BatchClickBinder? = null
    override var clickBinderMap: SparseArray<AdapterClickBinder.ClickBinder>? = null
    override var onItemClickListener: AdapterClickBinder.OnItemClickListener<T>? = null
    override var itemClickable: Boolean = true

    override fun onBindViewHolder(holder: VH, position: Int) {
        setView(holder, getItem(position), position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view: View = LayoutInflater.from(parent.context).inflate(
            itemLayoutRes, parent, false
        )
        return createVH(view).also { initViewHolder(it, viewType) }
    }

    protected fun createVH(itemView: View): VH = VH(itemView)

    protected fun initViewHolder(holder: VH, viewType: Int) {
        binding(holder, { getItem(it) }) { holder.batchClickBinder = batchClickBinder }
    }

    /**
     * 局部刷新
     * @param position Int
     * @param block Function1<T?, Unit>
     * @param payload Any?
     */
    inline fun notifyItemChanged(position: Int, block: (T?) -> Unit, payload: Any? = null) {
        if (position < 0 || position >= snapshot().size) return
        block(snapshot()[position])
        notifyItemChanged(position, payload)
    }

    fun withDefaultLoadStateFooter(): ConcatAdapter {
        val footerAdapter = FooterAdapter()
        footerAdapter.addClickBinder(object : AdapterClickBinder.ClickBinder(-1) {
            override fun onClick(v: View?, position: Int) = retry()
        })
//         return withLoadStateFooter(footerAdapter)

        val refreshAdapter = RefreshAdapter()
        refreshAdapter.addClickBinder(object : AdapterClickBinder.ClickBinder(R.id.btn_retry) {
            override fun onClick(v: View?, position: Int) = retry()
        })

        addLoadStateListener { loadStates ->
            LogUtils.w("--->loadStates.append=${loadStates.append}")
            var skipFooter = false
            when (loadStates.refresh) {
                is LoadState.Loading -> {
                    LogUtils.i("--->${loadStates.refresh};itemCount=${itemCount}")
                    refreshAdapter.itemCountBeforeRefresh = itemCount
                }
                is LoadState.NotLoading -> {
                    LogUtils.i("--->${loadStates.refresh};itemCount=${itemCount}")
                    refreshAdapter.itemCountAfterRefresh = itemCount
                    if (itemCount == 0) {
                        skipFooter = true
                        LogUtils.i("--->skipFooter")
                    }
                }
            }

            refreshAdapter.loadState = loadStates.refresh
            footerAdapter.skipDisplay = skipFooter
            footerAdapter.loadState = loadStates.append
        }
        return ConcatAdapter(refreshAdapter, this, footerAdapter)
    }

    /**
     * 设置view
     *
     * @param holder
     * @param data
     * @param position
     */
    protected abstract fun setView(holder: VH, data: T?, position: Int)


    /**
     * @Purpose ViewHolder
     * @Author Naruto Yang
     * @CreateDate 2019/7/22 0022
     * @Note
     */
    class VH(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val viewMap = SparseArray<View?>()
        var batchClickBinder: AdapterClickBinder.BatchClickBinder? by Delegates.observable(null) { _, _, newValue ->
            newValue?.run {
                getClickableViewIds().forEach { getView<View>(it)?.setOnClickListener(this@VH) }
            }
        }

        init {
            viewMap.put(-1, itemView)
        }

        /**
         * 获取item对应的ViewDataBinding对象
         *
         * @param <T>
         * @return
        </T> */
        fun <T : ViewDataBinding?> getBinding(): T? {
            return DataBindingUtil.getBinding<T>(itemView) ?: DataBindingUtil.bind(itemView)
            //            return DataBindingUtil.getBinding(this.itemView);
        }

        /**
         * 根据Id获取view
         *
         * @param id
         * @param <E>
         * @return
        </E> */
        fun <E : View> getView(id: Int): E? {
            return viewMap[id] as E? ?: (itemView.findViewById<E>(id)?.also { viewMap.put(id, it) })
        }

        fun setText(id: Int, text: String?) {
            getView<TextView>(id)?.text = text
        }

        fun showTextIfHaveData(id: Int, text: String?) {
            getView<TextView>(id)?.run {
                this.text = text
                visibility = if (TextUtils.isEmpty(text)) View.GONE else View.VISIBLE
            }
        }

        override fun onClick(v: View) {
            v.doClick { view -> batchClickBinder?.onClick(view, bindingAdapterPosition) }
        }
    }


    /**
     * @Description 底部”加载更多“，与BasePagingAdapter配合使用
     * @Author Naruto Yang
     * @CreateDate 2022/5/24 0024
     * @Note
     */
    class FooterAdapter : LoadStateAdapter<VH>(), AdapterClickBinder.CommonClickBinder {
        var skipDisplay = false
        override var batchClickBinder: AdapterClickBinder.BatchClickBinder? = null
        override var clickBinderMap: SparseArray<AdapterClickBinder.ClickBinder>? = null

        override fun onBindViewHolder(holder: VH, loadState: LoadState) {
            holder.getBinding<FooterLoadMoreBinding>()?.state = loadState
        }

        override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): VH {
            FooterLoadMoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                .run { return VH(root).also { initViewHolder(it) } }
        }

        private fun initViewHolder(holder: VH) {
            binding { holder.batchClickBinder = it }
        }

        override fun displayLoadStateAsItem(loadState: LoadState): Boolean {
            return (!skipDisplay && (super.displayLoadStateAsItem(loadState) ||
                    (loadState is LoadState.NotLoading && loadState.endOfPaginationReached)))
                //.also { LogUtils.i("--->result=$it;skipDisplay=$skipDisplay;loadState=$loadState") }
        }
    }

    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/5/24 0024
     * @Notexxxxxx4
     */
    open class RefreshAdapter : LoadStateAdapter<VH>(), AdapterClickBinder.CommonClickBinder {
        override var batchClickBinder: AdapterClickBinder.BatchClickBinder? = null
        override var clickBinderMap: SparseArray<AdapterClickBinder.ClickBinder>? = null
        var itemCountBeforeRefresh: Int = -1
        var itemCountAfterRefresh: Int = -1

        override fun onBindViewHolder(holder: VH, loadState: LoadState) {
            holder.getBinding<ErrorPageBinding>()?.state = loadState
        }

        override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): VH {
            ErrorPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                .run { return VH(root).also { initViewHolder(it) } }
        }

        private fun initViewHolder(holder: VH) {
            binding { holder.batchClickBinder = it }
        }

        override fun displayLoadStateAsItem(loadState: LoadState): Boolean {
            return when (loadState) {
                is LoadState.Loading -> true
                is LoadState.Error -> itemCountBeforeRefresh == 0
                is LoadState.NotLoading -> loadState.endOfPaginationReached || itemCountAfterRefresh == 0
            }
        }
    }

}
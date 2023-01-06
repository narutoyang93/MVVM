package com.naruto.mvvm.activity

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.naruto.mvvm.MyApplication
import com.naruto.mvvm.R
import com.naruto.mvvm.base.BasePagingAdapter
import com.naruto.mvvm.base.MVVMActivity
import com.naruto.mvvm.databinding.ActivityPagingListBinding
import com.naruto.mvvm.setMyOnClickListener
import com.naruto.mvvm.utils.LogUtils
import com.naruto.mvvm.utils.RandomUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/5/19 0019
 * @Note
 */
class PagingListActivity : MVVMActivity<ActivityPagingListBinding, PagingListActivity.VM>() {

    override fun init() {
        val diffCallback = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem === newItem

            override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        }
        //列表适配器
        val adapter =
            object : BasePagingAdapter<String>(android.R.layout.simple_list_item_1, diffCallback) {
                override fun setView(holder: VH, data: String?, position: Int) {
                    holder.setText(-1, data)
                }
            }.also { dataBinding.rv.adapter = it.withDefaultLoadStateFooter() }
        adapter.addLoadStateListener {
            when (it.refresh) {
                is LoadState.Loading -> {
                    dataBinding.srl.run { if (!isRefreshing) isRefreshing = true }
                }
                is LoadState.NotLoading -> {
                    dataBinding.run { if (srl.isRefreshing) rv.post { srl.isRefreshing = false } }
                }
                is LoadState.Error -> {
                    MyApplication.toast((it.refresh as LoadState.Error).error.message ?: "加载异常")
                    dataBinding.srl.isRefreshing = false
                }
            }
        }
        //列表相关设置
        dataBinding.rv.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        dataBinding.rv.hasFixedSize()
        //SwipeRefreshLayout
        dataBinding.srl.setOnRefreshListener { adapter.refresh() }//下拉监听
        dataBinding.srl.setColorSchemeResources(R.color.theme)
        //数据加载
        lifecycleScope.launch { viewModel.dataFlow.collectLatest { adapter.submitData(it) } }
        //点击事件
        dataBinding.btnRefreshToEmpty.setMyOnClickListener {
            viewModel.setEmptyFlag()
            adapter.refresh()
        }
    }

    override fun getLayoutRes() = R.layout.activity_paging_list

    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/5/19 0019
     * @Note
     */
    class VM : ViewModel() {
        val errorFlag = MutableLiveData(false)
        val emptyFlag = MutableLiveData(false)

        val dataFlow = Pager(PagingConfig(PAGE_SIZE, 3, true, PAGE_SIZE)) {
            object : PagingSource<Int, String>() {
                override fun getRefreshKey(state: PagingState<Int, String>): Int? {
                    return if (emptyFlag.value == true) null
                    else state.anchorPosition?.let {
                        state.closestPageToPosition(it)
                            ?.run { prevKey?.plus(1) ?: nextKey?.minus(1) }
                    }
                }

                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> {
                    val nextPageNumber = params.key ?: 1
                    LogUtils.i("--->nextPageNumber=$nextPageNumber")
                    val flag = when {
                        emptyFlag.value!! -> Repository.Flag.EMPTY
                        errorFlag.value!! -> Repository.Flag.ERROR
                        else -> Repository.Flag.NORMAL
                    }
                    try {
                        val response = Repository.getData(flag, nextPageNumber, params.loadSize)
                        if (response.code != 200) throw Exception(response.msg)
                        response.data!!.run {
                            val prev = if (pageNo <= 1) null else pageNo - 1
                            val next = if (pageNo + 1 > totalPage) null else pageNo + 1
                            return LoadResult.Page(data, prev, next)
                        }
                    } catch (e: Exception) {
                        return LoadResult.Error(e)
                    } finally {
                        if (emptyFlag.value!!) emptyFlag.value = false
                    }
                }
            }
        }.flow.cachedIn(viewModelScope)

        fun setEmptyFlag() {
            emptyFlag.value = true
            errorFlag.value = false
        }
    }

    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/5/20 0020
     * @Note
     */
    private object Repository {

        suspend fun getData(flag: Flag, pageNo: Int, pageSize: Int)
                : BaseResult<PagingData<List<String>>> {
            RandomUtil.run {
                delay(randomInt(100, 1000).toLong())
                return when (flag) {
                    Flag.ERROR -> BaseResult(502, "服务器异常", null)
                    Flag.EMPTY -> BaseResult(200, null, PagingData(1, listOf(), 1))
                    else -> {
                        val totalPage = 3
                        val size = if (pageNo == totalPage) randomInt(0, pageSize) else pageSize
                        val list = List(size) {
                            "${((pageNo - 1) * pageSize + it + 1)}. " + randomStr(randomInt(1, 50))
                        }
                        BaseResult(200, null, PagingData(pageNo, list, totalPage))
                    }
                }
            }
        }

        enum class Flag {
            NORMAL, ERROR, EMPTY
        }
    }

    data class PagingData<T>(val pageNo: Int, val data: T, val totalPage: Int)

    /**
     * @Description 网络返回数据基类
     * @Author Naruto Yang
     * @CreateDate 2022/5/20 0020
     * @Note
     */
    data class BaseResult<T>(val code: Int, val msg: String?, val data: T?)
}

private const val PAGE_SIZE = 20

package com.naruto.mvvm.activity

import android.app.Activity
import android.widget.ArrayAdapter
import com.naruto.mvvm.R
import com.naruto.mvvm.base.DataBindingActivity
import com.naruto.mvvm.databinding.ActivityMainBinding

class MainActivity : DataBindingActivity<ActivityMainBinding>() {

    override fun init() {
        val list = listOf(
            Item("断点续传", DownloadActivity::class.java),
            Item("上传文件", UploadActivity::class.java),
            Item("分页列表", PagingListActivity::class.java)
        )
        dataBinding.lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
        dataBinding.lv.setOnItemClickListener { _, _, position, _ -> startActivity(list[position].clazz) }
    }

    override fun getLayoutRes() = R.layout.activity_main

    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/5/19 0019
     * @Note
     */
    private class Item(val text: String, val clazz: Class<out Activity>) {
        override fun toString() = text
    }
}
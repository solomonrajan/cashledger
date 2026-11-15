/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.ui.fragment.stub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.ArrayList

/**
 * Created by andrea on 24/01/18.
 */
open class StubListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val recyclerView = RecyclerView(requireActivity())
        val adapter = ArrayAdapter()
        for (i in 0 until 100) {
            adapter.add("Item $i")
        }
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()
        return recyclerView
    }

    open class ArrayAdapter : RecyclerView.Adapter<ArrayAdapter.StubViewHolder>() {

        private val mData: MutableList<String> = ArrayList()

        fun add(item: String) {
            mData.add(item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StubViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return StubViewHolder(view)
        }

        override fun onBindViewHolder(holder: StubViewHolder, position: Int) {
            holder.textView.text = mData[position]
        }

        override fun getItemCount(): Int = mData.size

        class StubViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(android.R.id.text1)

            init {
                itemView.setOnClickListener { }
            }
        }
    }
}

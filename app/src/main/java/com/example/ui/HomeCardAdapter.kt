package com.example.ui

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.model.HomeCardItem

class HomeCardAdapter(
    private val onClick: (HomeCardItem) -> Unit,
    private val onLongClick: (HomeCardItem) -> Unit,
    private val onFocused: (HomeCardItem, Int) -> Unit
) : RecyclerView.Adapter<HomeCardAdapter.CardViewHolder>() {

    private val items = mutableListOf<HomeCardItem>()
    var isLargeText: Boolean = false
        private set

    fun submitItems(newItems: List<HomeCardItem>, largeText: Boolean) {
        items.clear()
        items.addAll(newItems)
        isLargeText = largeText
        notifyDataSetChanged()
    }

    fun setLargeText(largeText: Boolean) {
        if (isLargeText != largeText) {
            isLargeText = largeText
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_card, parent, false)
        return CardViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: View = itemView.findViewById(R.id.cardRoot)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivCardIcon)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvCardBadge)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvCardTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvCardSubtitle)

        fun bind(item: HomeCardItem, position: Int) {
            val density = itemView.resources.displayMetrics.density
            val widthDp = if (isLargeText) 226 else 186
            val heightDp = if (isLargeText) 142 else 116
            val lp = cardRoot.layoutParams as ViewGroup.MarginLayoutParams
            lp.width = (widthDp * density).toInt()
            lp.height = (heightDp * density).toInt()
            cardRoot.layoutParams = lp

            tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isLargeText) 21f else 17f)
            tvSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isLargeText) 15f else 12f)

            val iconSize = ((if (isLargeText) 42 else 36) * density).toInt()
            ivIcon.layoutParams = ivIcon.layoutParams.apply {
                width = iconSize
                height = iconSize
            }

            tvTitle.text = item.title
            tvSubtitle.text = item.subtitle

            if (item.badge.isNullOrBlank()) {
                tvBadge.visibility = View.GONE
            } else {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = item.badge
            }

            if (item.appIcon != null) {
                ivIcon.setImageDrawable(item.appIcon)
            } else if (item.iconRes != 0) {
                ivIcon.setImageResource(item.iconRes)
            } else {
                ivIcon.setImageResource(R.drawable.ic_apps_grid)
            }

            cardRoot.setOnClickListener { onClick(item) }
            cardRoot.setOnLongClickListener {
                onLongClick(item)
                true
            }
            cardRoot.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    onFocused(item, bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: position)
                }
            }
        }
    }
}

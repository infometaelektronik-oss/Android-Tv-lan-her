package com.example.ui

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.LauncherPrefs
import com.example.model.InstalledAppInfo
import com.example.model.TvSourceOption
import com.example.tv.TvInputHelper

object AppDrawerDialogs {

    fun showAllAppsDialog(
        context: Context,
        prefs: LauncherPrefs,
        onAppsChanged: () -> Unit
    ) {
        val dialog = Dialog(context, R.style.SadeTvDialog)
        dialog.setContentView(R.layout.dialog_list)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
        val tvSub = dialog.findViewById<TextView>(R.id.tvDialogSubtitle)
        val rv = dialog.findViewById<RecyclerView>(R.id.rvDialogList)
        val btnClose = dialog.findViewById<Button>(R.id.btnDialogClose)

        tvTitle.text = context.getString(R.string.card_all_apps)
        tvSub.visibility = View.VISIBLE
        tvSub.text = "Açmak için OK • Ana ekrana sabitlemek veya gizlemek için uzun basın"
        btnClose.setOnClickListener { dialog.dismiss() }

        val apps = TvInputHelper.queryInstalledApps(
            context = context,
            includeHidden = false,
            hiddenSet = prefs.hiddenPackages
        )

        val pm = context.packageManager
        rv.layoutManager = GridLayoutManager(context, 4)
        rv.adapter = object : RecyclerView.Adapter<AppGridHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppGridHolder {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app_grid, parent, false)
                return AppGridHolder(v)
            }

            override fun getItemCount(): Int = apps.size

            override fun onBindViewHolder(holder: AppGridHolder, position: Int) {
                val app = apps[position]
                holder.tvName.text = app.label
                val icon = runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
                holder.ivIcon.setImageDrawable(icon)

                holder.itemView.setOnClickListener {
                    dialog.dismiss()
                    if (TvInputHelper.launchApp(context, app.packageName, app.activityName)) {
                        prefs.saveLastWatched(app.label, "APP", "${app.packageName}/${app.activityName}")
                    }
                }
                holder.itemView.setOnLongClickListener {
                    showAppActionDialog(context, prefs, app) {
                        dialog.dismiss()
                        onAppsChanged()
                        showAllAppsDialog(context, prefs, onAppsChanged)
                    }
                    true
                }
            }
        }
        dialog.show()
        rv.post { rv.getChildAt(0)?.requestFocus() }
    }

    private fun showAppActionDialog(
        context: Context,
        prefs: LauncherPrefs,
        app: InstalledAppInfo,
        onUpdated: () -> Unit
    ) {
        val isPinned = prefs.pinnedPackages.contains(app.packageName)
        val rows = listOf(
            DialogRowItem(
                title = if (isPinned) "Ana Ekrandan Kaldır" else "Ana Ekrana Sabitle",
                desc = "Ana ekran satırında hızlı erişim",
                onClick = {
                    val list = prefs.pinnedPackages.toMutableList()
                    if (isPinned) list.remove(app.packageName) else list.add(app.packageName)
                    prefs.pinnedPackages = list
                    Toast.makeText(
                        context,
                        if (isPinned) "${app.label} ana ekrandan kaldırıldı" else "${app.label} ana ekrana eklendi",
                        Toast.LENGTH_SHORT
                    ).show()
                    onUpdated()
                }
            ),
            DialogRowItem(
                title = "Bu Uygulamayı Gizle",
                desc = "Tüm Uygulamalar listesinden gizler (Ayarlardan geri açılabilir)",
                onClick = {
                    val hidden = prefs.hiddenPackages.toMutableSet()
                    hidden.add(app.packageName)
                    prefs.hiddenPackages = hidden
                    val pinned = prefs.pinnedPackages.toMutableList()
                    pinned.remove(app.packageName)
                    prefs.pinnedPackages = pinned
                    Toast.makeText(context, "${app.label} gizlendi", Toast.LENGTH_SHORT).show()
                    onUpdated()
                }
            )
        )
        showSimpleRowDialog(context, app.label, null, rows)
    }

    fun showHiddenAppsManager(
        context: Context,
        prefs: LauncherPrefs,
        onChanged: () -> Unit
    ) {
        val allApps = TvInputHelper.queryInstalledApps(
            context = context,
            includeHidden = true,
            hiddenSet = emptySet()
        )
        val hidden = prefs.hiddenPackages.toMutableSet()
        val rows = allApps.map { app ->
            val isHidden = hidden.contains(app.packageName)
            DialogRowItem(
                title = app.label,
                desc = app.packageName,
                value = if (isHidden) "Gizli" else "Görünür",
                onClick = {
                    if (hidden.contains(app.packageName)) {
                        hidden.remove(app.packageName)
                    } else {
                        hidden.add(app.packageName)
                    }
                    prefs.hiddenPackages = hidden
                    onChanged()
                }
            )
        }
        showSimpleRowDialog(
            context = context,
            title = context.getString(R.string.settings_hidden_apps),
            subtitle = "Gizlemek veya tekrar göstermek için uygulamaya basın",
            rows = rows,
            refreshOnClick = { showHiddenAppsManager(context, prefs, onChanged) }
        )
    }

    fun showTvSourcePicker(
        context: Context,
        prefs: LauncherPrefs,
        onSelected: (TvSourceOption) -> Unit
    ) {
        val sources = TvInputHelper.discoverAllTvSources(context)
        val current = prefs.preferredTvTarget
        val rows = sources.map { src ->
            DialogRowItem(
                title = src.label,
                desc = src.subtitle,
                value = if (src.key == current) "Seçili" else null,
                onClick = {
                    prefs.preferredTvTarget = src.key
                    onSelected(src)
                }
            )
        }
        showSimpleRowDialog(
            context = context,
            title = context.getString(R.string.settings_tv_input),
            subtitle = "Dahili uydu / TV izle uygulamasını veya TIF girişini seçin",
            rows = rows
        )
    }

    fun showSimpleRowDialog(
        context: Context,
        title: String,
        subtitle: String?,
        rows: List<DialogRowItem>,
        refreshOnClick: (() -> Unit)? = null
    ) {
        val dialog = Dialog(context, R.style.SadeTvDialog)
        dialog.setContentView(R.layout.dialog_list)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
        val tvSub = dialog.findViewById<TextView>(R.id.tvDialogSubtitle)
        val rv = dialog.findViewById<RecyclerView>(R.id.rvDialogList)
        val btnClose = dialog.findViewById<Button>(R.id.btnDialogClose)

        tvTitle.text = title
        if (!subtitle.isNullOrBlank()) {
            tvSub.visibility = View.VISIBLE
            tvSub.text = subtitle
        }
        btnClose.setOnClickListener { dialog.dismiss() }

        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = DialogRowAdapter(rows) {
            dialog.dismiss()
            refreshOnClick?.invoke()
        }
        dialog.show()
        rv.post { rv.getChildAt(0)?.requestFocus() }
    }

    private class AppGridHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
    }
}

data class DialogRowItem(
    val title: String,
    val desc: String? = null,
    val value: String? = null,
    val iconRes: Int = 0,
    val onClick: () -> Unit
)

class DialogRowAdapter(
    private val rows: List<DialogRowItem>,
    private val afterClick: (() -> Unit)? = null
) : RecyclerView.Adapter<DialogRowAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dialog_row, parent, false)
        return Holder(v)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.tvTitle.text = row.title
        if (row.desc.isNullOrBlank()) {
            holder.tvDesc.visibility = View.GONE
        } else {
            holder.tvDesc.visibility = View.VISIBLE
            holder.tvDesc.text = row.desc
        }
        if (row.value.isNullOrBlank()) {
            holder.tvValue.visibility = View.GONE
        } else {
            holder.tvValue.visibility = View.VISIBLE
            holder.tvValue.text = row.value
        }
        if (row.iconRes != 0) {
            holder.ivIcon.visibility = View.VISIBLE
            holder.ivIcon.setImageResource(row.iconRes)
        } else {
            holder.ivIcon.visibility = View.GONE
        }
        holder.itemView.setOnClickListener {
            row.onClick()
            afterClick?.invoke()
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivRowIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvRowTitle)
        val tvDesc: TextView = view.findViewById(R.id.tvRowDesc)
        val tvValue: TextView = view.findViewById(R.id.tvRowValue)
    }
}

package io.github.fcmfix.hyperos.ui

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.fcmfix.hyperos.R

/**
 * Ana Aktivite - FCMFix HyperOS 4
 *
 * Modülün durumunu ve hook bilgilerini gösteren Material 3 tarzı arayüz.
 * Tüm metinler Türkçedir.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupStatusCard()
        setupHookCards()
        setupScopeInfo()
        setupAboutSection()
    }

    /**
     * Modül durumu kartını ayarlar
     */
    private fun setupStatusCard() {
        val statusCard = findViewById<CardView>(R.id.card_status)
        val statusIcon = findViewById<ImageView>(R.id.iv_status_icon)
        val statusTitle = findViewById<TextView>(R.id.tv_status_title)
        val statusHint = findViewById<TextView>(R.id.tv_status_hint)

        // Xposed modülünün aktif olup olmadığını kontrol et
        val isModuleActive = isModuleActive()

        if (isModuleActive) {
            statusCard.setCardBackgroundColor(getColor(R.color.status_active_bg))
            statusTitle.text = getString(R.string.module_status_active)
            statusTitle.setTextColor(getColor(R.color.status_active_text))
            statusHint.visibility = View.GONE
        } else {
            statusCard.setCardBackgroundColor(getColor(R.color.status_inactive_bg))
            statusTitle.text = getString(R.string.module_status_inactive)
            statusTitle.setTextColor(getColor(R.color.status_inactive_text))
            statusHint.text = getString(R.string.module_status_hint)
            statusHint.visibility = View.VISIBLE
        }
    }

    /**
     * Hook durum kartlarını ayarlar
     */
    private fun setupHookCards() {
        val hookItems = listOf(
            HookCardItem(
                getString(R.string.card_framework_hook),
                getString(R.string.card_framework_hook_desc),
                R.drawable.ic_framework
            ),
            HookCardItem(
                getString(R.string.card_powerkeeper_hook),
                getString(R.string.card_powerkeeper_hook_desc),
                R.drawable.ic_power
            ),
            HookCardItem(
                getString(R.string.card_greezer_hook),
                getString(R.string.card_greezer_hook_desc),
                R.drawable.ic_freeze
            ),
            HookCardItem(
                getString(R.string.card_idle_hook),
                getString(R.string.card_idle_hook_desc),
                R.drawable.ic_idle
            ),
        )

        val recyclerView = findViewById<RecyclerView>(R.id.rv_hooks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = HookCardAdapter(hookItems)
    }

    /**
     * Kapsam bilgilerini gösterir
     */
    private fun setupScopeInfo() {
        val scopeTitle = findViewById<TextView>(R.id.tv_scope_title)
        val scopeInfo = findViewById<TextView>(R.id.tv_scope_info)
        val scopeFramework = findViewById<TextView>(R.id.tv_scope_framework)
        val scopePowerkeeper = findViewById<TextView>(R.id.tv_scope_powerkeeper)
        val scopeHint = findViewById<TextView>(R.id.tv_scope_hint)

        scopeTitle.text = getString(R.string.scope_title)
        scopeInfo.text = getString(R.string.scope_info)
        scopeFramework.text = getString(R.string.scope_framework)
        scopePowerkeeper.text = getString(R.string.scope_powerkeeper)
        scopeHint.text = getString(R.string.scope_hint)
    }

    /**
     * Hakkında bölümünü ayarlar
     */
    @SuppressLint("SetTextI18n")
    private fun setupAboutSection() {
        val aboutVersion = findViewById<TextView>(R.id.tv_about_version)
        val aboutDesc = findViewById<TextView>(R.id.tv_about_desc)
        val aboutCredits = findViewById<TextView>(R.id.tv_about_credits)

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            aboutVersion.text = getString(R.string.about_version, pInfo.versionName)
        } catch (_: PackageManager.NameNotFoundException) {
            aboutVersion.text = getString(R.string.about_version, "1.0.0")
        }

        aboutDesc.text = getString(R.string.about_description)
        aboutCredits.text = getString(R.string.about_credits)
    }

    /**
     * Xposed modülünün aktif olup olmadığını kontrol eder.
     * Bu metot, LSPosed tarafından runtime'da hook'lanarak true döndürülür.
     * Hook'lanmamışsa false döner, yani modül aktif değildir.
     */
    private fun isModuleActive(): Boolean {
        return false
    }

    // --- Veri Sınıfları ---

    data class HookCardItem(
        val title: String,
        val description: String,
        val iconRes: Int
    )

    // --- RecyclerView Adapter ---

    inner class HookCardAdapter(
        private val items: List<HookCardItem>
    ) : RecyclerView.Adapter<HookCardAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: CardView = view.findViewById(R.id.card_hook_item)
            val title: TextView = view.findViewById(R.id.tv_hook_title)
            val description: TextView = view.findViewById(R.id.tv_hook_desc)
            val icon: ImageView = view.findViewById(R.id.iv_hook_icon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_hook_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.description.text = item.description
            try {
                holder.icon.setImageResource(item.iconRes)
            } catch (_: Exception) {
                // Drawable yoksa varsayılan ikon
            }
        }

        override fun getItemCount() = items.size
    }
}

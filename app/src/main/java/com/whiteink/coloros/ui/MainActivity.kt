package com.whiteink.coloros.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.whiteink.coloros.R
import com.whiteink.coloros.databinding.ActivityMainBinding
import com.whiteink.coloros.service.FluidCloudService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    // ★ 本地追踪服务状态，不再仅依赖 getRunningServices()
    private var serviceRunning = false

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startService()
        else Toast.makeText(this, "需要通知权限才能使用流体云", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("fluidcloud", MODE_PRIVATE)

        // 初始化 service 状态
        serviceRunning = checkServiceRunning()

        // 恢复已保存的文字
        val savedTitle = prefs.getString("title", "") ?: ""
        val savedSub = prefs.getString("subtitle", "") ?: ""
        binding.etTitle.setText(savedTitle)
        binding.etSubtitle.setText(savedSub)
        updatePreview(savedTitle, savedSub)

        // --- 模板点击 ---
        setupChip(binding.chipMusic) {
            setContent("正在播放", "音乐", "🎵 正在播放音乐")
        }
        setupChip(binding.chipTimer) {
            setContent("计时器", "00:00", "⏱ 计时器")
        }
        setupChip(binding.chipDelivery) {
            setContent("快递配送", "预计今日到达", "📦 快递配送")
        }
        setupChip(binding.chipNavigation) {
            setContent("导航中", "约5分钟", "🧭 导航中")
        }

        // --- 保存 ---
        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString()
            val subtitle = binding.etSubtitle.text.toString()
            saveContent(title, subtitle)
            updatePreview(title, subtitle)
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            if (serviceRunning) refreshService()
        }

        // --- 启动/停止 ---
        binding.btnToggleService.setOnClickListener {
            if (serviceRunning) stopService() else checkPermAndStart()
        }

        // --- 自启动 ---
        binding.btnAutoStart.setOnClickListener {
            val enabled = !prefs.getBoolean("auto_start_enabled", false)
            prefs.edit().putBoolean("auto_start_enabled", enabled).apply()
            binding.tvAutoStartState.text = if (enabled) "已开启" else "已关闭"
            val i = Intent(this, FluidCloudService::class.java)
                .setAction(FluidCloudService.ACTION_SET_AUTO_START)
                .putExtra(FluidCloudService.EXTRA_AUTO_START, enabled)
            ContextCompat.startForegroundService(this, i)
        }

        // --- 通知设置 ---
        binding.btnNotificationSettings.setOnClickListener {
            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(i)
        }

        // --- 电池优化 ---
        binding.btnBatteryOptimization.setOnClickListener {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "已在白名单中", Toast.LENGTH_SHORT).show()
            } else {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                i.data = Uri.parse("package:$packageName")
                startActivity(i)
            }
        }

        // --- 关于 ---
        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        updateUI()

        // ★ 可预测式返回手势 — 返回桌面时显示过渡动画
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    // ---------- 模板 / 内容 ----------

    /** 给 chip TextView 绑定点击（不用 XML clickable，确保可靠） */
    private fun setupChip(view: android.widget.TextView, action: () -> Unit) {
        view.setOnClickListener { action() }
    }

    /** 设置大标题+小标题并存入 pref */
    private fun setContent(title: String, subtitle: String, toastText: String) {
        binding.etTitle.setText(title)
        binding.etSubtitle.setText(subtitle)
        saveContent(title, subtitle)
        updatePreview(title, subtitle)
        if (serviceRunning) refreshService()
        Toast.makeText(this, "已应用: $toastText", Toast.LENGTH_SHORT).show()
    }

    private fun saveContent(title: String, subtitle: String) {
        prefs.edit()
            .putString("title", title)
            .putString("subtitle", subtitle)
            .apply()
    }

    /** 更新预览胶囊 */
    private fun updatePreview(title: String, subtitle: String) {
        val t = title.ifBlank { "（大标题）" }
        val s = subtitle.ifBlank { "（小标题）" }
        binding.previewTitle.text = t
        binding.previewSubtitle.text = s

        // 大标题首字作为图标
        val icon = when {
            title.isNotBlank() && title[0].isHighSurrogate() && title.length > 1 ->
                title.substring(0, 2)
            title.isNotBlank() && title[0].code > 127 -> title[0].toString()
            title.isNotBlank() -> title[0].uppercase()
            subtitle.isNotBlank() && subtitle[0].isHighSurrogate() && subtitle.length > 1 ->
                subtitle.substring(0, 2)
            subtitle.isNotBlank() && subtitle[0].code > 127 -> subtitle[0].toString()
            subtitle.isNotBlank() -> subtitle[0].uppercase()
            else -> "●"
        }
        binding.previewIcon.text = icon
        binding.previewSeparator.visibility =
            if (title.isNotBlank() && subtitle.isNotBlank()) android.view.View.VISIBLE
            else android.view.View.GONE
    }

    // ---------- 生命周期 ----------

    override fun onResume() {
        super.onResume()
        val realRunning = checkServiceRunning()

        // 自启动恢复
        if (!realRunning && prefs.getBoolean("auto_start_enabled", false)
            && !prefs.getBoolean("fluid_cloud_stopped", false)
        ) {
            serviceRunning = true
            startService()
        } else {
            serviceRunning = realRunning
        }

        val title = prefs.getString("title", "") ?: ""
        val sub = prefs.getString("subtitle", "") ?: ""
        updatePreview(title, sub)
        updateUI()
    }

    // ---------- 服务控制 ----------

    private fun checkPermAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startService()
        }
    }

    private fun startService() {
        val title = prefs.getString("title", "") ?: ""
        val subtitle = prefs.getString("subtitle", "") ?: ""
        val i = Intent(this, FluidCloudService::class.java)
            .setAction(FluidCloudService.ACTION_UPDATE)
            .putExtra(FluidCloudService.EXTRA_TITLE, title)
            .putExtra(FluidCloudService.EXTRA_SUBTITLE, subtitle)
        ContextCompat.startForegroundService(this, i)
        prefs.edit().putBoolean("fluid_cloud_stopped", false).apply()
        serviceRunning = true
        Toast.makeText(this, "流体云已启动", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun stopService() {
        val i = Intent(this, FluidCloudService::class.java)
            .setAction(FluidCloudService.ACTION_STOP)
        startService(i)
        prefs.edit().putBoolean("fluid_cloud_stopped", true).apply()
        serviceRunning = false
        Toast.makeText(this, "流体云已停止", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun refreshService() {
        val i = Intent(this, FluidCloudService::class.java)
            .setAction(FluidCloudService.ACTION_UPDATE)
        startService(i)
    }

    private fun checkServiceRunning(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (s in am.getRunningServices(Int.MAX_VALUE)) {
            if (FluidCloudService::class.java.name == s.service.className) return true
        }
        return false
    }

    // ---------- UI ----------

    private fun updateUI() {
        val fluidEnabled = prefs.getBoolean("fluid_cloud_enabled", false)
        val autoStart = prefs.getBoolean("auto_start_enabled", false)

        binding.tvStatus.text = if (serviceRunning) "● 运行中" else "○ 已停止"
        binding.indicatorStatus.setBackgroundColor(
            if (serviceRunning) getColor(R.color.green) else getColor(R.color.red)
        )

        if (serviceRunning && fluidEnabled) {
            binding.tvFluidState.text = "✅ 已适配流体云"
            binding.indicatorFluidState.setBackgroundColor(getColor(R.color.green))
        } else if (serviceRunning) {
            binding.tvFluidState.text = "⏳ 等待 ColorOS 识别…"
            binding.indicatorFluidState.setBackgroundColor(getColor(R.color.orange))
        } else {
            binding.tvFluidState.text = "等待启动…"
            binding.indicatorFluidState.setBackgroundColor(getColor(R.color.red))
        }

        binding.btnToggleService.text = if (serviceRunning)
            getString(R.string.stop_service) else getString(R.string.start_service)

        binding.tvAutoStartState.text = if (autoStart) "已开启" else "已关闭"

        binding.previewCapsule.alpha = if (serviceRunning && fluidEnabled) 1.0f else 0.5f
        binding.tvCapsuleHint.text = if (serviceRunning && fluidEnabled)
            "↑ 通知栏已显示此胶囊 ↑" else "↑ 通知栏胶囊效果预览 ↑"
    }
}

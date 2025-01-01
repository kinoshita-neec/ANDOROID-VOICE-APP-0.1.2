package com.example.voiceapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.voiceapp.databinding.ActivitySettingsBinding
import com.google.android.material.tabs.TabLayoutMediator

/**
 * アプリケーションの設定画面を管理するアクティビティ
 * - ViewPager2を使用したタブ式の設定画面
 * - ユーザー設定、エージェント設定、プロンプト、会話ログの4つのタブを提供
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupTabs()

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = SettingsPagerAdapter(this)
    }

    private fun setupTabs() {
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "ユーザー設定"
                1 -> "エージェント設定"
                2 -> "プロンプト"
                3 -> "会話ログ"
                else -> ""
            }
        }.attach()
    }
}

private class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4  // タブを4つに変更

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> UserSettingsFragment()
            1 -> AgentSettingsFragment()
            2 -> PromptPreviewFragment()
            3 -> ConversationLogFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}

package com.example.voiceapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.voiceapp.databinding.ActivitySettingsBinding
import com.google.android.material.tabs.TabLayoutMediator

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = SettingsPagerAdapter(this)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "ユーザー設定"
                1 -> "エージェント設定"
                2 -> "プロンプト"
                else -> ""
            }
        }.attach()

        binding.backButton.setOnClickListener {
            finish()
        }
    }
}

private class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3  // タブを3つに変更

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> UserSettingsFragment()
            1 -> AgentSettingsFragment()
            2 -> PromptPreviewFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}

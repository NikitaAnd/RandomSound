package com.nikita.randomsound

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private val sounds = mutableListOf<Uri>()
    private var player: MediaPlayer? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 64, 48, 48) }
        status = TextView(this).apply { text = "Звуки не выбраны"; textSize = 18f }
        val pick = Button(this).apply { text = "Выбрать звуки"; setOnClickListener { pickSounds() } }
        val play = Button(this).apply { text = "Случайный звук"; setOnClickListener { playRandom() } }
        val stop = Button(this).apply { text = "Остановить"; setOnClickListener { player?.stop(); player?.release(); player = null } }
        layout.addView(status); layout.addView(pick); layout.addView(play); layout.addView(stop)
        setContentView(layout)
    }

    private fun pickSounds() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "audio/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); addCategory(Intent.CATEGORY_OPENABLE) }, 10)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 10 || resultCode != RESULT_OK || data == null) return
        sounds.clear(); data.clipData?.let { clip -> for (i in 0 until clip.itemCount) sounds.add(clip.getItemAt(i).uri) } ?: data.data?.let { sounds.add(it) }
        status.text = "Выбрано звуков: ${sounds.size}"
    }

    private fun playRandom() {
        if (sounds.isEmpty()) { status.text = "Сначала выбери звуки"; return }
        player?.release()
        player = MediaPlayer.create(this, sounds[Random.nextInt(sounds.size)]).apply { setOnCompletionListener { release() }; start() }
    }

    override fun onDestroy() { player?.release(); super.onDestroy() }
}

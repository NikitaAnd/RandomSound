package com.nikita.randomsound

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var targetInput: EditText
    private lateinit var portInput: EditText
    private lateinit var scanBtn: Button
    private lateinit var testBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var deviceList: ListView

    private val devices = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private var scanRunning = false
    private var testRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        targetInput = findViewById(R.id.targetInput)
        portInput = findViewById(R.id.portInput)
        scanBtn = findViewById(R.id.scanBtn)
        testBtn = findViewById(R.id.testBtn)
        stopBtn = findViewById(R.id.stopBtn)
        deviceList = findViewById(R.id.deviceList)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devices)
        deviceList.adapter = adapter

        scanBtn.setOnClickListener { scanNetworkAsync() }
        testBtn.setOnClickListener { testConnection() }
        stopBtn.setOnClickListener {
            scanRunning = false
            testRunning = false
            statusText.text = "ОСТАНОВЛЕНО"
            setBusy(false)
        }

        deviceList.setOnItemClickListener { _, _, position, _ ->
            targetInput.setText(devices[position].substringBefore(" | "))
        }
    }

    private fun setBusy(busy: Boolean) {
        scanBtn.isEnabled = !busy
        testBtn.isEnabled = !busy
    }

    private fun scanNetworkAsync() {
        if (scanRunning) return
        scanRunning = true
        setBusy(true)
        statusText.text = "СКАНИРОВАНИЕ…"
        devices.clear()
        adapter.notifyDataSetChanged()

        Thread {
            scanNetwork()
            runOnUiThread {
                scanRunning = false
                setBusy(false)
                statusText.text = "ГОТОВО • найдено: ${devices.size}"
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun scanNetwork() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = intToIp(wifiManager.connectionInfo.ipAddress)

        if (ip == "0.0.0.0") {
            runOnUiThread { statusText.text = "НЕТ АКТИВНОГО WI-FI" }
            return
        }

        val subnet = ip.substringBeforeLast(".")

        try {
            File("/proc/net/arp").readLines().drop(1).forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4 && parts[3] != "00:00:00:00:00:00") {
                    synchronized(devices) {
                        if (devices.none { it.startsWith("${parts[0]} |") }) {
                            devices.add("${parts[0]} | ${parts[3]}")
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val executor = Executors.newFixedThreadPool(24)
        for (i in 1..254) {
            if (!scanRunning) break
            val candidate = "$subnet.$i"
            executor.submit {
                if (!scanRunning) return@submit
                try {
                    val process = Runtime.getRuntime().exec(
                        arrayOf("/system/bin/ping", "-c", "1", "-W", "1", candidate)
                    )
                    if (process.waitFor() == 0) {
                        synchronized(devices) {
                            if (devices.none { it.startsWith("$candidate |") }) {
                                devices.add("$candidate | pingable")
                            }
                        }
                        runOnUiThread { adapter.notifyDataSetChanged() }
                    }
                } catch (_: Exception) {
                }
            }
        }

        executor.shutdown()
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun testConnection() {
        if (testRunning) return

        val target = targetInput.text?.toString()?.trim().orEmpty()
        val port = portInput.text?.toString()?.toIntOrNull()

        if (target.isEmpty()) {
            Toast.makeText(this, "Укажи IP или домен", Toast.LENGTH_SHORT).show()
            return
        }
        if (port == null || port !in 1..65535) {
            Toast.makeText(this, "Порт должен быть от 1 до 65535", Toast.LENGTH_SHORT).show()
            return
        }

        testRunning = true
        setBusy(true)
        statusText.text = "ПРОВЕРКА $target:$port…"

        Thread {
            val result = try {
                val address = InetAddress.getByName(target)
                DatagramSocket().use { socket ->
                    val payload = "NetKiller diagnostic".toByteArray()
                    socket.send(DatagramPacket(payload, payload.size, address, port))
                }
                "UDP-пакет отправлен • $target:$port"
            } catch (e: Exception) {
                "ОШИБКА • ${e.javaClass.simpleName}"
            }

            runOnUiThread {
                testRunning = false
                setBusy(false)
                statusText.text = result
            }
        }.start()
    }

    private fun intToIp(ip: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }
}

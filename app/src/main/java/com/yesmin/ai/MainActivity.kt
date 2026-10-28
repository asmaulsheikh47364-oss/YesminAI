package com.yesmin.ai

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yesmin.ai.agent.TaskOrchestrator
import com.yesmin.ai.ui.LiveTaskMonitor

class MainActivity : AppCompatActivity() {
    private lateinit var taskOrchestrator: TaskOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        taskOrchestrator = TaskOrchestrator(this)


        statusText?.text = "Yesmin AI Initialized & Ready"
        LiveTaskMonitor.logStatus("MainActivity", "READY", "System initialized successfully.")

        actionButton?.setOnClickListener {
            LiveTaskMonitor.logStatus("MainActivity", "CLICK", "Executing WhatsApp test task.")
            // Example automated task trigger
            taskOrchestrator.executeTask("whatsapp", "1234567890", "Hello from Yesmin AI Autonomous Agent!")
        }
    }
}

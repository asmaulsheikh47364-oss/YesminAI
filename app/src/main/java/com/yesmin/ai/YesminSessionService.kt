package com.yesmin.ai

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class YesminSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = YesminSession(this)
}

class YesminSession(ctx: Context) : VoiceInteractionSession(ctx) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        try {
            val vm = (context.applicationContext as YesminApp).vm
            if (!vm.sessionActive) vm.startSession()
            else if (vm.sessionPaused) vm.resumeSession()
        } catch (e: Exception) {
        }
        hide()
    }
}

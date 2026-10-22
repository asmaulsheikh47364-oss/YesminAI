package com.yesmin.ai

import android.service.voice.VoiceInteractionService

class YesminVoiceService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Dbg.log(this, "Yesmin ab default assistant hai")
    }
}

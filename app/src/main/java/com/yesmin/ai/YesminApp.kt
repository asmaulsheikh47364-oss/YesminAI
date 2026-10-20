package com.yesmin.ai

import android.app.Application

/** The assistant core lives at application level so the voice session survives screen changes and runs with the foreground service. */
class YesminApp : Application() {
    val vm: AppViewModel by lazy { AppViewModel(this) }
}

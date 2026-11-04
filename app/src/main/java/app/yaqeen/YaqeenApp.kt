package app.yaqeen

import android.app.Application
import app.yaqeen.work.Workers

class YaqeenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
        Workers.schedulePeriodic(this)
    }
}

val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as YaqeenApp).container

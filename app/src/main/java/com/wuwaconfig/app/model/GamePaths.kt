package com.wuwaconfig.app.model

object GamePaths {
    const val TARGET_PACKAGE = "com.kurogame.wutheringwaves.global"
    const val CONFIG_PATH = "files/UE4Game/Client/Client/Saved/Config/Android"
    const val LOG_PATH = "files/UE4Game/Client/Client/Saved/Logs"
    const val LOG_FILE_NAME = "Client.log"
    val TARGET_DIR = "/storage/emulated/0/Android/data/$TARGET_PACKAGE/$CONFIG_PATH"
    val LOG_DIR = "/storage/emulated/0/Android/data/$TARGET_PACKAGE/$LOG_PATH"
}

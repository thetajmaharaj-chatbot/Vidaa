package com.example.vidaaremote.protocol

enum class VidaaKey(val wireValue: String) {
    POWER("KEY_POWER"),
    HOME("KEY_HOME"),
    BACK("KEY_RETURNS"),
    MENU("KEY_MENU"),
    UP("KEY_UP"),
    DOWN("KEY_DOWN"),
    LEFT("KEY_LEFT"),
    RIGHT("KEY_RIGHT"),
    OK("KEY_OK"),
    VOLUME_UP("KEY_VOLUMEUP"),
    VOLUME_DOWN("KEY_VOLUMEDOWN"),
    MUTE("KEY_MUTE"),
    CHANNEL_UP("KEY_CHANNELUP"),
    CHANNEL_DOWN("KEY_CHANNELDOWN"),
    PLAY("KEY_PLAY"),
    PAUSE("KEY_PAUSE"),
    STOP("KEY_STOP"),
    REWIND("KEY_BACK"),
    FAST_FORWARD("KEY_FORWARDS"),
}

package com.aesthetic.music.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder

object AppJson {
    val gson: Gson = GsonBuilder()
        .setLenient()
        .create()
}

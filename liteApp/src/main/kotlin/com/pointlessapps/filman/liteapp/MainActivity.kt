package com.pointlessapps.filman.liteapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pointlessapps.filman.lite.LiteAndroid
import com.pointlessapps.filman.lite.LiteApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LiteAndroid.init(this)
        setContent { LiteApp() }
    }
}

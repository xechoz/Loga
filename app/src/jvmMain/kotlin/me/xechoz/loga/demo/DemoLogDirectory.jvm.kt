package me.xechoz.loga.demo

actual fun demoLogDirectory(): String =
    "${System.getProperty("user.home")}/.loga-demo"

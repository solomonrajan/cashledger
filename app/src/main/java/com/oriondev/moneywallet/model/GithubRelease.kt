package com.oriondev.moneywallet.model

data class GithubRelease(
    val name: String,
    val tagName: String,
    val body: String,
    val isPrerelease: Boolean,
    val publishedAt: String,
    val downloadUrl: String?
)

package com.liujiaming.embypro

import android.content.Context
import androidx.annotation.StringRes
import androidx.media3.common.PlaybackException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun Context.userFriendlyErrorMessage(error: Throwable?, @StringRes fallbackResId: Int): String {
    return when (error) {
        is UnknownHostException -> getString(R.string.error_network_host_unreachable)
        is ConnectException -> getString(R.string.error_server_unreachable)
        is SocketTimeoutException -> getString(R.string.error_network_timeout)
        is SSLException -> getString(R.string.error_ssl_connection_failed)
        is IOException -> getString(R.string.error_network_failed)
        is SecurityException -> getString(R.string.error_permission_denied)
        else -> getString(fallbackResId)
    }
}

fun Context.userFriendlyPlaybackErrorMessage(error: PlaybackException?): String {
    return when (error?.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> getString(R.string.error_playback_network_failed)
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> getString(R.string.error_playback_server_rejected)
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> getString(R.string.error_playback_url_unavailable)
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> getString(R.string.error_playback_format_unsupported)
        else -> getString(R.string.player_error)
    }
}

package za.co.mjrsolutions.shared.auth

import android.os.Build
import com.android.volley.toolbox.BaseHttpStack
import com.android.volley.toolbox.HurlStack
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal object TlsCompat {

    fun createHttpStack(): BaseHttpStack {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            HurlStack(null, TlsSocketFactory())
        } else {
            HurlStack()
        }
    }

    private class TlsSocketFactory : SSLSocketFactory() {
        private val delegate: SSLSocketFactory

        init {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, null)
            delegate = sslContext.socketFactory
        }

        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
            enableTls(delegate.createSocket(s, host, port, autoClose))

        override fun createSocket(host: String, port: Int): Socket =
            enableTls(delegate.createSocket(host, port))

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            enableTls(delegate.createSocket(host, port, localHost, localPort))

        override fun createSocket(host: InetAddress, port: Int): Socket =
            enableTls(delegate.createSocket(host, port))

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            enableTls(delegate.createSocket(address, port, localAddress, localPort))

        private fun enableTls(socket: Socket): Socket {
            if (socket is SSLSocket) {
                socket.enabledProtocols = arrayOf("TLSv1.1", "TLSv1.2")
            }
            return socket
        }
    }
}

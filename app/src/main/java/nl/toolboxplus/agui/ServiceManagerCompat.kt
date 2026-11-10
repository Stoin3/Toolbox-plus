package nl.toolboxplus.agui

import android.os.IBinder
import android.os.Parcel
import rikka.shizuku.ShizukuBinderWrapper

internal object ServiceManagerCompat {

    /** Direct lookup. SELinux denies this to regular apps, so it normally returns null. */
    fun getService(name: String): IBinder? = runCatching {
        Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, name) as IBinder?
    }.getOrNull()

    /**
     * Looks up [name] by routing the servicemanager request through Shizuku, so the lookup is
     * made with the shell identity that SELinux allows. The binder in the reply is then usable
     * directly by this app, without Shizuku, for as long as the process lives.
     */
    fun getServiceViaShizuku(name: String): IBinder? {
        val serviceManager = Class.forName("com.android.internal.os.BinderInternal")
            .getMethod("getContextObject")
            .invoke(null) as IBinder
        // IServiceManager itself is blocked for apps, so build its getService() call by hand.
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_MANAGER_DESCRIPTOR)
            data.writeString(name)
            ShizukuBinderWrapper(serviceManager).transact(TX_GET_SERVICE, data, reply, 0)
            reply.readException()
            return reply.readStrongBinder()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private const val SERVICE_MANAGER_DESCRIPTOR = "android.os.IServiceManager"
    private const val TX_GET_SERVICE = 1
}

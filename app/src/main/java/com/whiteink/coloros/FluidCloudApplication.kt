package com.whiteink.coloros

import android.app.Application

class FluidCloudApplication : Application() {
    // 通知渠道在 FluidCloudService 中动态创建，
    // 不需在此初始化，参考项目也是这么做的
}

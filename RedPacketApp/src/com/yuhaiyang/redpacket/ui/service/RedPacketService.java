/**
 * Copyright (C) 2016 The yuhaiyang Android Source Project
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yuhaiyang.redpacket.ui.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import com.bright.common.widget.YToast;
import com.yuhaiyang.redpacket.BuildConfig;
import com.yuhaiyang.redpacket.Config;
import com.yuhaiyang.redpacket.IStatusBarNotification;
import com.yuhaiyang.redpacket.job.IAccessbilityJob;
import com.yuhaiyang.redpacket.job.WechatAccessbilityJob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * 抢红包服务
 */
public class RedPacketService extends AccessibilityService {

    private static final String TAG = "QiangHongBao";

    private static final Class[] ACCESSBILITY_JOBS = {
            WechatAccessbilityJob.class,
    };

    private static RedPacketService service;

    private List<IAccessbilityJob> mAccessbilityJobs;
    private HashMap<String, IAccessbilityJob> mPkgAccessbilityJobMap;

    @Override
    public void onCreate() {
        super.onCreate();

        mAccessbilityJobs = new ArrayList<>();
        mPkgAccessbilityJobMap = new HashMap<>();

        //初始化辅助插件工作
        for (Class clazz : ACCESSBILITY_JOBS) {
            try {
                Object object = clazz.newInstance();
                if (object instanceof IAccessbilityJob) {
                    IAccessbilityJob job = (IAccessbilityJob) object;
                    job.onCreateJob(this);
                    mAccessbilityJobs.add(job);
                    mPkgAccessbilityJobMap.put(job.getTargetPackageName(), job);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "qianghongbao service destory");
        if (mPkgAccessbilityJobMap != null) {
            mPkgAccessbilityJobMap.clear();
        }
        if (mAccessbilityJobs != null && !mAccessbilityJobs.isEmpty()) {
            for (IAccessbilityJob job : mAccessbilityJobs) {
                job.onStopJob();
            }
            mAccessbilityJobs.clear();
        }

        service = null;
        mAccessbilityJobs = null;
        mPkgAccessbilityJobMap = null;
        //发送广播，已经断开辅助服务
        Intent intent = new Intent(Config.ACTION_QIANGHONGBAO_SERVICE_DISCONNECT);
        sendBroadcast(intent);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "qianghongbao service interrupt");
        YToast.makeText(this, "中断抢红包服务", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        service = this;
        //发送广播，已经连接上了
        Intent intent = new Intent(Config.ACTION_QIANGHONGBAO_SERVICE_CONNECT);
        sendBroadcast(intent);
        YToast.makeText(this, "已连接抢红包服务", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "事件--->" + event);
        }
        String pkn = String.valueOf(event.getPackageName());
        if (mAccessbilityJobs != null && !mAccessbilityJobs.isEmpty()) {
            if (!getConfig().isAgreement()) {
                return;
            }
            for (IAccessbilityJob job : mAccessbilityJobs) {
                if (pkn.equals(job.getTargetPackageName()) && job.isEnable()) {
                    job.onReceiveJob(event);
                }
            }
        }
    }

    public Config getConfig() {
        return Config.getConfig(this);
    }

    /** 接收通知栏事件*/
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void handeNotificationPosted(IStatusBarNotification notificationService) {
        if (notificationService == null) {
            return;
        }
        if (service == null || service.mPkgAccessbilityJobMap == null) {
            return;
        }
        String pack = notificationService.getPackageName();
        IAccessbilityJob job = service.mPkgAccessbilityJobMap.get(pack);
        if (job == null) {
            return;
        }
        job.onNotificationPosted(notificationService);
    }

    /**
     * 判断当前服务是否正在运行
     * */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static boolean isRunning() {
        if (service == null) {
            return false;
        }
        AccessibilityManager accessibilityManager = (AccessibilityManager) service.getSystemService(Context.ACCESSIBILITY_SERVICE);
        AccessibilityServiceInfo info = service.getServiceInfo();
        if (info == null) {
            return false;
        }
        List<AccessibilityServiceInfo> list = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        Iterator<AccessibilityServiceInfo> iterator = list.iterator();

        boolean isConnect = false;
        while (iterator.hasNext()) {
            AccessibilityServiceInfo i = iterator.next();
            if (i.getId().equals(info.getId())) {
                isConnect = true;
                break;
            }
        }
        if (!isConnect) {
            return false;
        }
        return true;
    }

    /** 快速读取通知栏服务是否启动*/
    public static boolean isNotificationServiceRunning() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return false;
        }
        //部份手机没有NotificationService服务
        try {
            return RedPacketNotificationService.isRunning();
        } catch (Throwable t) {
        }
        return false;
    }


}
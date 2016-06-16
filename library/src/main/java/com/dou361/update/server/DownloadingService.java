
package com.dou361.update.server;

import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.dou361.update.R;
import com.dou361.update.UpdateHelper;
import com.dou361.update.bean.Update;
import com.dou361.update.http.DownloadWorker;
import com.dou361.update.http.UpdateExecutor;
import com.dou361.update.listener.DownloadListener;
import com.dou361.update.util.DialogSafeOperator;
import com.dou361.update.util.FileUtils;
import com.dou361.update.util.InstallUtil;
import com.dou361.update.util.UpdateConstants;
import com.dou361.update.view.DialogDownloadUI;
import com.dou361.update.view.DialogUI;

import java.io.File;
/**
 * ========================================
 * <p/>
 * 版 权：dou361.com 版权所有 （C） 2015
 * <p/>
 * 作 者：陈冠明
 * <p/>
 * 个人网站：http://www.dou361.com
 * <p/>
 * 版 本：1.0
 * <p/>
 * 创建日期：2016/6/16 23:25
 * <p/>
 * 描 述：原理
 * 纵线
 * 首先是点击更新时，弹出进度对话框（进度，取消和运行在后台），
 * 如果是在前台完成下载，弹出安装对话框，
 * 如果是在后台完成下载，通知栏提示下载完成，
 * 横线
 * 如果进入后台后，还在继续下载点击时重新回到原界面
 * 如果强制更新无进入后台功能
 * 如果是静默更新，安静的安装
 *
 * <p/>
 * <p/>
 * 修订历史：
 * <p/>
 * ========================================
 */
public class DownloadingService extends Service {

    private RemoteViews contentView;
    private NotificationManager notificationManager;
    private Notification notification;
    private Update update;
    private NotificationCompat.Builder ntfBuilder;
    private ProgressDialog dialogDowning;
    private boolean dialogBackgroud;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int action = intent.getIntExtra(UpdateConstants.SERVER_ACTION, 0);
            if (action == UpdateConstants.START_DOWN) {
                update = (Update) intent.getSerializableExtra("update");
                if (update != null && !TextUtils.isEmpty(update.getUpdateUrl())) {
                    downApk();
                }
            } else if (action == UpdateConstants.PAUSE_DOWN) {
                Toast.makeText(this, "暂停或开始", Toast.LENGTH_LONG).show();
            } else if (action == UpdateConstants.CANCEL_DOWN) {
                Toast.makeText(this, "取消", Toast.LENGTH_LONG).show();

            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void downApk() {


        DownloadWorker downloadWorker = new DownloadWorker();
        downloadWorker.setUrl(update.getUpdateUrl());
        final DownloadListener mDownload = UpdateHelper.getInstance().getDownloadListener();
        final DialogDownloadUI dialogDownloadUI = UpdateHelper.getInstance().getDialogDownloadUI();
        downloadWorker.setDownloadListener(new DownloadListener() {
            @Override
            public void onUpdateStart() {
                if (mDownload != null) {
                    mDownload.onUpdateStart();
                }
                if (UpdateHelper.getInstance().getStrategy().isShowDownloadDialog()) {
                    dialogDowning = dialogDownloadUI.create(update);
                    DialogSafeOperator.safeShowDialog(dialogDowning);
                }
            }

            @Override
            public void onUpdateComplete(File file) {
                if (mDownload != null) {
                    mDownload.onUpdateComplete(file);
                }
                DialogSafeOperator.safeDismissDialog(dialogDowning);
                if (UpdateHelper.getInstance().getStrategy().isShowInstallDialog()) {
                    if (dialogBackgroud) {
                        showInstallNotificationUI(file);
                    } else {

                        DialogUI creator = UpdateHelper.getInstance().getDialogUI();
                        Dialog dialog = creator.create(1, update, file.getAbsolutePath());
                        DialogSafeOperator.safeShowDialog(dialog);
                    }
                } else if (UpdateHelper.getInstance().getStrategy().isAutoInstall()) {
                    installApk(this, file);
                }
            }

            @Override
            public void onUpdateProgress(long current, long total) {
                    if (mDownload != null) {
                        mDownload.onUpdateProgress(current, total);
                    }
                if (dialogBackgroud) {
                    notifyNotification(current, total);
                } else {
                    int percent = (int) (current * 1.0f / total * 100);
                    if (dialogDowning != null) {
                        dialogDowning.setProgress(percent);
                    }
                }
            }

            @Override
            public void onUpdateError(int code, String errorMsg) {
                if (mDownload != null) {
                    mDownload.onUpdateError(code, errorMsg);
                }
                DialogSafeOperator.safeDismissDialog(dialogDowning);
            }
        });
        downloadWorker.setCacheFileName(FileUtils.createFile(update.getVersionName()));
        UpdateExecutor.getInstance().download(downloadWorker);
    }

    @SuppressWarnings("deprecation")
    public void createNotification() {
        notification = new Notification(
                getApplicationInfo().icon,
                "安装包正在下载...",
                System.currentTimeMillis());
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        /*** 自定义  Notification 的显示****/
        contentView = new RemoteViews(getPackageName(), R.layout.jjdxm_download_notification);
        contentView.setTextViewText(R.id.jjdxm_update_title, getApplicationInfo().name);
        contentView.setProgressBar(R.id.jjdxm_update_progress_bar, 100, 0, false);
        contentView.setTextViewText(R.id.jjdxm_update_progress_text, "0%");
        /**暂停和开始*/
        Intent pauseIntent = new Intent(this, DownloadingService.class);
        pauseIntent.putExtra(UpdateConstants.SERVER_ACTION, UpdateConstants.PAUSE_DOWN);
        pauseIntent.putExtra("update", update);
        PendingIntent pendingIntent1 = PendingIntent.getService(this, 0, pauseIntent, 0);
        contentView.setOnClickPendingIntent(R.id.jjdxm_update_rich_notification_continue, pendingIntent1);

        /**取消*/
        Intent cancelIntent = new Intent(this, DownloadingService.class);
        cancelIntent.putExtra(UpdateConstants.SERVER_ACTION, UpdateConstants.CANCEL_DOWN);
        cancelIntent.putExtra("update", update);
        PendingIntent pendingIntent2 = PendingIntent.getService(this, 0, cancelIntent, 0);
        contentView.setOnClickPendingIntent(R.id.jjdxm_update_rich_notification_cancel, pendingIntent2);

        notification.contentView = contentView;
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(UpdateConstants.NOTIFICATION_ACTION, notification);
    }

    private void notifyNotification(long percent, long length) {
        contentView.setTextViewText(R.id.jjdxm_update_progress_text, (percent * 100 / length) + "%");
        contentView.setProgressBar(R.id.jjdxm_update_progress_bar, (int) length, (int) percent, false);
        notification.contentView = contentView;
        notificationManager.notify(UpdateConstants.NOTIFICATION_ACTION, notification);
    }

    private void showInstallNotificationUI(File file) {
        if (ntfBuilder == null) {
            ntfBuilder = new NotificationCompat.Builder(this);
        }
        ntfBuilder.setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(getApplicationInfo().name)
                .setContentText("下载完成，点击安装").setTicker("任务下载完成");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(
                Uri.fromFile(file),
                "application/vnd.android.package-archive");
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, 0);
        ntfBuilder.setContentIntent(pendingIntent);
        if (notificationManager == null) {
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        notificationManager.notify(UpdateConstants.NOTIFICATION_ACTION,
                ntfBuilder.build());
    }

    /**
     * 安装apk
     *
     * @param context 上下文
     * @param file    APK文件
     */
    public static void installApk(Context context, File file) {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file),
                "application/vnd.android.package-archive");
        context.startActivity(intent);
    }
}
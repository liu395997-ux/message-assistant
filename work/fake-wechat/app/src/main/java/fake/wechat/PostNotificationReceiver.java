package fake.wechat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Person;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class PostNotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                "messages",
                "Messages",
                NotificationManager.IMPORTANCE_HIGH);
        manager.createNotificationChannel(channel);

        Person user = new Person.Builder().setName("我").build();
        Person teacher = new Person.Builder().setName("李老师").build();
        Person student = new Person.Builder().setName("王同学").build();
        Notification.MessagingStyle style =
                new Notification.MessagingStyle(user).setConversationTitle("项目群");
        style.addMessage("端到端测试：请提交课程设计文档", System.currentTimeMillis() - 2_000, teacher);
        style.addMessage("收到，今晚提交", System.currentTimeMillis() - 1_000, student);

        Notification notification = new Notification.Builder(context, "messages")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setContentTitle("项目群")
                .setStyle(style)
                .setShowWhen(true)
                .build();
        manager.notify(8801, notification);
    }
}

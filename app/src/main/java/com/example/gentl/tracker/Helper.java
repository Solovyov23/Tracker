package com.example.gentl.tracker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Helper
{
    public static boolean checkDistance(Context context, Location startLocation, Location currentLocation, int distance)
    {
        float distanceInMeters = startLocation.distanceTo(currentLocation);
        int NOTIFICATION_LOCATION_ID = createID();

        Log.d("LocationService", "checkDistance");
        if ((int) distanceInMeters >= distance)
        {
            // Start Notification
            NotificationManager nManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            Intent targetIntent = new Intent(context, MapsActivity.class);
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent contentIntent = PendingIntent.getActivity(context, NOTIFICATION_LOCATION_ID, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(context)
                            .setSmallIcon(R.mipmap.tracker_icon)
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(R.string.you_have_moved_by)
                                    + distance + context.getString(R.string.meters_after_the_next)
                                    + distance + context.getString(R.string.meters_we_will_notify_you_again)))
                            .setContentTitle(context.getString(R.string.you_have_reached_the_distance_limit))
                            .setContentIntent(contentIntent);

            builder.setAutoCancel(true);
            builder.setDefaults(Notification.DEFAULT_ALL);

            nManager.notify(NOTIFICATION_LOCATION_ID, builder.build());
            // End Notification

            return true;
        }
        return false;
    }

    public static int createID(){
        Date now = new Date();
        int id = Integer.parseInt(new SimpleDateFormat("ddHHmmss",  Locale.US).format(now));
        return id;
    }
}

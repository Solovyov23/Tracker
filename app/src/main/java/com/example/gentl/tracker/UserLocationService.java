package com.example.gentl.tracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import com.example.gentl.tracker.IUserLocationBinder;

public class UserLocationService extends Service
{
    protected static final String LOCATION_LOG_TAG = "LocationService";
    public static final int LOCATION_INTERVAL = 1000;
    public static final String DISTANCE_TAG = "mDistance";
    public static final String START_LOCATION_TAG = "mStartLocation";
    protected int mDistance = 0;
    protected Location mStartLocation;
    /**
     * Provides access to the Fused Location Provider API.
     */
    protected FusedLocationProviderClient mFusedLocationClient;

    /**
     * Contains parameters used by {@link com.google.android.gms.location.FusedLocationProviderApi}.
     */
    protected LocationRequest mLocationRequest;
    protected Location mLastLocation;

    private LocationCallback mLocationCallback = new LocationCallback()
    {
        @Override
        public void onLocationResult(LocationResult locationResult)
        {
            super.onLocationResult(locationResult);

            Location location = locationResult.getLastLocation();

            // Something is not right!
            if(location == null) return;;

            Log.e(LOCATION_LOG_TAG, "onLocationChanged: " + location);

            // Too early...
            if(mStartLocation == null)
            {
                mStartLocation = location;
                return;
            }

            mLastLocation = location;

            // If a person has left the distance, then show him the notification and start a new report
            if(Helper.checkDistance(getApplicationContext(), mStartLocation, mLastLocation, mDistance))
            {
                mStartLocation = mLastLocation;
                // Report activity that the start coordinates have been changed
                // and you need to redraw the distance on the map
                Intent i = new Intent("android.intent.action.MAIN")
                        .putExtra(START_LOCATION_TAG, mStartLocation);
                getApplicationContext().sendBroadcast(i);
            }
        }
    };

    // This is the object that receives interactions from clients.
    private final IBinder mBinder = new UserLocationBinder();
    public class UserLocationBinder extends IUserLocationBinder.Stub
    {
        @Override
        public int getDistance() throws RemoteException
        {
            return mDistance;
        }

        @Override
        public Location getStartLocation() throws RemoteException
        {
            return mStartLocation;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(LOCATION_LOG_TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if(intent != null)
        {
            mDistance = (int) intent.getExtras().get("mDistance");
            mStartLocation = (Location) intent.getExtras().get("mStartLocation");

            // Save mDistance to SP
            preferences.edit().putInt("mDistance", mDistance).apply();
        }
        else // data was saved in shared preferences
        {
            mDistance = preferences.getInt("mDistance", 0);
        }

        return START_REDELIVER_INTENT;
    }
    @Override
    public void onCreate()
    {
        super.onCreate();

        Log.e(LOCATION_LOG_TAG, "onCreate");

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        createLocationRequest();

        try
        {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper());
        }
        catch (SecurityException unlikely)
        {
            Log.e(LOCATION_LOG_TAG, "Lost location permission. Could not request updates. " + unlikely);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(NotificationManager notificationManager)
    {
        String channelId = "my_service_channelid";
        String channelName = "My Foreground Service";
        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        // omitted the LED color
        channel.setImportance(NotificationManager.IMPORTANCE_NONE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationManager.createNotificationChannel(channel);
        return channelId;
    }

    @Override
    public void onDestroy()
    {
        Log.e(LOCATION_LOG_TAG, "onDestroy");

        // Must get rid of the location callback
        removeLocationUpdates();

        super.onDestroy();
    }

    /**
     * Sets the location request parameters.
     */
    private void createLocationRequest()
    {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(LOCATION_INTERVAL);
        mLocationRequest.setFastestInterval(LOCATION_INTERVAL / 2);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Removes location updates
     */
    public void removeLocationUpdates()
    {
        Log.i(LOCATION_LOG_TAG, "Removing location updates");
        try
        {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);

            stopSelf();
        }
        catch (SecurityException unlikely)
        {
            Log.e(LOCATION_LOG_TAG, "Lost location permission. Could not remove updates. " + unlikely);
        }
    }
}
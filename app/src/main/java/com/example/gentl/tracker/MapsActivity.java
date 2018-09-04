package com.example.gentl.tracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;


/* Simple activity to manage location service */
public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, View.OnClickListener
{
    protected static final String LOG_TAG = "MapsActivity";

    private Circle mapCircle;
    private GoogleMap googleMap;
    private static final int MY_PERMISSION_ACCESS_COURSE_LOCATION = 11;
    private int distanceInMeters = 20;

    private Location myCurrentPosition;
    // Координаты, при которых был запущена слежка
    private Location myStartLocation;

    // Views
    private SeekBar distanceSeekBar;
    private TextView descriptionTextView;
    private Button bStart, bStop;

    private boolean distanceBegan = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        boolean bIsLocationServiceRunning = isMyServiceRunning(UserLocationService.class);

        // Two main buttons
        bStart = (Button) findViewById(R.id.bStart);
        bStart.setOnClickListener(this);
        bStart.setEnabled(!bIsLocationServiceRunning);

        bStop = (Button) findViewById(R.id.bStop);
        bStop.setOnClickListener(this);
        bStop.setEnabled(bIsLocationServiceRunning);

        // Dist
        descriptionTextView = (TextView) findViewById(R.id.descriptionTextView);
        descriptionTextView.setText(distanceInMeters + "");

        distanceSeekBar = (SeekBar)findViewById(R.id.distanceSeekBar);
        distanceSeekBar.setOnSeekBarChangeListener(seekBarChangeListener);

        updateUI();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        doUnbindService();
    }

    // Updates UI state depending on the location service
    public void updateUI()
    {
        // No runnning service
        if (mBoundService == null)
        {
            bStart.setEnabled(true);
            distanceSeekBar.setEnabled(true);
            bStop.setEnabled(false);
        }
        else // Retrieve data from service
        {
            try
            {
                distanceInMeters = mBoundService.getDistance();
                if(mBoundService.getStartLocation() != null) myStartLocation = mBoundService.getStartLocation();
            }
            catch (RemoteException e)
            {
                e.printStackTrace();
            }

            bStart.setEnabled(false);
            distanceSeekBar.setEnabled(false);
            distanceSeekBar.setProgress(distanceInMeters);
            descriptionTextView.setText(distanceInMeters + "");
            bStop.setEnabled(true);
        }

        addCircleOnTheMap();
    }

    // Implement the onClick method for start/stop location events
    public void onClick(View v)
    {
        // Perform action on click
        switch(v.getId())
        {
            case R.id.bStart:
            {
                if (myCurrentPosition == null) return;

                // Start the location service if it's not already running
                if (!isMyServiceRunning(UserLocationService.class))
                {
                    Intent intent = new Intent(getApplicationContext(), UserLocationService.class);
                    intent.putExtra(UserLocationService.DISTANCE_TAG, distanceInMeters);
                    intent.putExtra(UserLocationService.START_LOCATION_TAG, myStartLocation);
                    ContextCompat.startForegroundService(getApplicationContext(), intent);
                    doBindService();
                }

                myStartLocation = myCurrentPosition;

                updateUI();

                break;
            }
            case R.id.bStop:
            {
                if (mapCircle != null)
                {
                    mapCircle.remove();
                }

                stopService(new Intent(this, UserLocationService.class));

                updateUI();

                break;
            }
        } // switch
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(final GoogleMap googleMap)
    {
        this.googleMap = googleMap;
        LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean enabledGPS = service
                .isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean enabledWiFi = service
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        // Check if enabled and if not send user to the GSP settings
        // Better solution would be to display a dialog and suggesting to
        // go to the settings
        if (!enabledGPS)
        {
            Toast.makeText(this, "GPS signal not found", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        if(checkSelfPermission(true)) init();
    }

    @SuppressLint("MissingPermission")
    public void init()
    {
        // Enabling MyLocation Layer of Google Map
        googleMap.setMyLocationEnabled(true);

        // Getting LocationManager object from System Service LOCATION_SERVICE
        LocationManager locationManager = (LocationManager)getApplicationContext().getSystemService(LOCATION_SERVICE);

        // Getting Current Location
        Location location = getLastKnownLocation(locationManager);

        if (location != null)
        {
            // Getting latitude of the current location
            double latitude = location.getLatitude();

            // Getting longitude of the current location
            double longitude = location.getLongitude();

            // Creating a LatLng object for the current location
            LatLng myPosition = new LatLng(latitude, longitude);

            googleMap.moveCamera( CameraUpdateFactory.newLatLngZoom(myPosition, 18));

            myCurrentPosition = location;

            addCircleOnTheMap();
        }

        googleMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener()
        {
            @Override
            public void onMyLocationChange(Location location)
            {
                myCurrentPosition = location;
                if(!isMyServiceRunning(UserLocationService.class) && !distanceBegan)
                {
                    addCircleOnTheMap();
                }
                if(distanceBegan)
                {
                    myStartLocation = myCurrentPosition;
                    //addCircleOnTheMap();
                }
            }
        });
    }

    private Location getLastKnownLocation(LocationManager mLocationManager)
    {
        List<String> providers = mLocationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers)
        {
            @SuppressLint("MissingPermission") Location l = mLocationManager.getLastKnownLocation(provider);
            if (l == null)
            {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy())
            {
                // Found best last known location: %s", l);
                bestLocation = l;
            }
        }

        return bestLocation;
    }

    private boolean isMyServiceRunning(Class<?> serviceClass)
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
        {
            if (serviceClass.getName().equals(service.service.getClassName()))
            {
                return true;
            }
        }
        return false;
    }

    ////////////////////// Service communication /////////////////

    private IUserLocationBinder mBoundService;
    protected boolean mIsBound = false;

    private ServiceConnection mConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            /*if(service == null)
            {
                Log.i(LOG_TAG, "onServiceConnected: service is not created yet!");
                return;
            }*/
            // This is called when the connection with the service has
            // been established, giving us the service object we can use
            // to interact with the service.  Because we have bound to a
            // explicit service that we know is running in our own
            // process, we can cast its IBinder to a concrete class and
            // directly access it.

            IUserLocationBinder binder = IUserLocationBinder.Stub.asInterface(service);

            mBoundService = binder;
            distanceBegan = true;
            updateUI();
        }

        public void onServiceDisconnected(ComponentName className)
        {
            // This is called when the connection with the service has
            // been unexpectedly disconnected
            mBoundService = null;
            distanceBegan = false;
            updateUI();
        }
    };

    void doBindService()
    {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation
        // that we know will be running in our own process (and thus
        // won't be supporting component replacement by other
        // applications).
        if(isMyServiceRunning(UserLocationService.class))
        {
            bindService(new Intent(getApplicationContext(), UserLocationService.class),
                    mConnection,
                    Context.BIND_ABOVE_CLIENT);
            mIsBound = true;

            IntentFilter intentFilter = new IntentFilter(
                    "android.intent.action.MAIN");

            mReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    //extract our message from intent
                    Location newStartLocation = (Location) intent.getExtras().get(UserLocationService.START_LOCATION_TAG);
                    // Update the data and draw a circle (distance)
                    myStartLocation = newStartLocation;
                    myCurrentPosition = newStartLocation;
                    addCircleOnTheMap();
                }
            };

            //registering our receiver
            this.registerReceiver(mReceiver, intentFilter);
        }
    }

    void doUnbindService()
    {
        if (mIsBound)
        {
            // Detach our existing connection.
            unbindService(mConnection);
            mConnection.onServiceDisconnected(ComponentName.unflattenFromString(""));
            mIsBound = false;
            //unregister our receiver
            this.unregisterReceiver(mReceiver);
        }
    }

    //////////////////////END Service communication /////////////////


    ////////////////////////////Permissions//////////////////////////////

    // Called when user response was made to permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        // If the user allowed geolocation, then
        if(checkSelfPermission(false)) init();
        else
        {
            // Otherwise close the window
        }
    }

    private boolean checkSelfPermission(boolean requestPermissions)
    {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            if(requestPermissions) requestPermissions();
            return false;
        }
        return true;
    }

    private void requestPermissions()
    {
        ActivityCompat.requestPermissions( this, new String[] {  android.Manifest.permission.ACCESS_COARSE_LOCATION  },
                MY_PERMISSION_ACCESS_COURSE_LOCATION );
    }

    //////////Permissions end////////

    // BroadcastReceiver takes events from the service and draws a distance on the map,
    // when the user has moved
    private BroadcastReceiver mReceiver;

    @Override
    protected void onResume()
    {
        // TODO Auto-generated method stub
        super.onResume();

        doBindService();
        updateUI();
    }

    @Override
    protected void onPause()
    {
        // TODO Auto-generated method stub
        super.onPause();

        doUnbindService();
    }

    // Draws the distance on the map
    private void addCircleOnTheMap()
    {
        if(myCurrentPosition == null) return;

        if(mapCircle!=null)
        {
            mapCircle.remove();
        }
        LatLng targetLocation = new LatLng(myCurrentPosition.getLatitude(), myCurrentPosition.getLongitude());
        mapCircle = googleMap.addCircle(new CircleOptions()
                .center(targetLocation)
                .radius(distanceInMeters)
                .strokeWidth(0f)
                .fillColor(0x550000FF));
    }

    // To select a distance by the user
    private SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener()
    {
        @SuppressLint("SetTextI18n")
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
        {
            //if(progress == 0) return;
            distanceInMeters = progress;
            descriptionTextView.setText(distanceInMeters + "");
            addCircleOnTheMap();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar)
        {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {


        }
    };
}

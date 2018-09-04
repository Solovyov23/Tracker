package com.example.gentl.tracker;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

public class NetworkHelper
{
    public NetworkHelper() { }

    // Check your internet connection once
    public static boolean isOnline(Context context) {

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        // Should check null because in airplane mode it will be null

        boolean res = netInfo != null && netInfo.isConnected();
        if(!res)
        {
            Toast.makeText(context,context.getResources().getString(R.string.not_internet_connection) + "", Toast.LENGTH_SHORT)
                    .show();
        }
        return (res);
    }
}

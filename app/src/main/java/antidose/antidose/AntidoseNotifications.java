package antidose.antidose;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.Math.*;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.Executor;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;

public class AntidoseNotifications extends FirebaseMessagingService {
    LocationManager mLocationManager;

    Location end = new Location("");
    int max = 0;
    String notification, incidentId = "";


    // When the Android user receives a notification from the server through Firebase, handle it

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // Set up variables
        max = 0;
        incidentId = "";
        Float lat, lon;

        // Create jsonobject
        Map<String, String> params = remoteMessage.getData();
        JSONObject jsonObject = new JSONObject(params);

        // Determine if it is a dismissal message
        try {
            notification = jsonObject.getString("notification");
        } catch (JSONException e) {
            return;
        }

        // This is a help notification
        if (notification.equals("help")) {
            try {
                lat = Float.parseFloat(jsonObject.getString("lat"));
                lon = Float.parseFloat(jsonObject.getString("lon"));
                max = jsonObject.getInt("max");
                incidentId = jsonObject.getString("incident_id");
            } catch (JSONException e) {
                return;
            }

            end.setLatitude(lat);
            end.setLongitude(lon);

            LocationManager mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

/*            try {
                Location location = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                createNotification(location, max, incidentId);
            } catch (SecurityException e) {

            }*/


            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            Location locationGPS = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location locationNETWORK = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);


            if (locationGPS != null && locationGPS.getTime() > Calendar.getInstance().getTimeInMillis() - 2 * 60 * 1000) {
                // Last location in phone was 2 minutes ago
                // Do something with the recent location fix
                //  otherwise wait for the update below
                createNotification(locationGPS, max, incidentId);

            } else if (locationNETWORK != null && locationNETWORK.getTime() > Calendar.getInstance().getTimeInMillis() - 2 * 60 * 1000){
                // Last location in phone was 2 minutes ago
                // Do something with the recent location fix
                //  otherwise wait for the update below
                createNotification(locationNETWORK, max, incidentId);

            }

            else {
                try{
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListenerGps);
                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListenerNetwork);
                }
                catch(Exception e)
                {
                    createNotification(locationGPS, max, incidentId);
                }
            }

            // This is a notification dismissal
        } else if(notification.equals("dismiss")){
            cancelNotification();
        }
    }

    LocationListener locationListenerGps = new LocationListener() {
        public void onLocationChanged(Location location) {
            mLocationManager.removeUpdates(this);
            mLocationManager.removeUpdates(locationListenerGps);
            createNotification(location, max, incidentId);

        }

        public void onProviderDisabled(String provider) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    LocationListener locationListenerNetwork = new LocationListener() {
        public void onLocationChanged(Location location) {
            mLocationManager.removeUpdates(this);
            mLocationManager.removeUpdates(locationListenerNetwork);
            createNotification(location, max, incidentId);

        }

        public void onProviderDisabled(String provider) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    public String getNotificationDistance(Float distance) {
        String responseText = "";
        if (distance >= 1000.0) {
            // Do km (e.g 1.2km)
            responseText = String.format("%.1f km", distance / 1000);
        } else {
            // Do m (e.g 100m
            responseText = String.format("%dm", (int)Math.ceil(distance));
        }
        return responseText;
    }

    // Create the associated notification for the help request
    public void createNotification(Location location, int max, String incidentId){
        Float bearing = location.bearingTo(end);
        Float distance = location.distanceTo(end);

        // If the person in need is too far away, update actual location and do not create
        // a notification
        if (distance > max){
            updateLocation(location);
            return;
        }



        Intent resultIntent = new Intent(this, NotifyActivity.class);
        resultIntent.putExtra("BEARING", bearing);

        resultIntent.putExtra("INCIDENT_ID", incidentId);


        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setDeleteIntent(createOnDismissedIntent(this, incidentId))
                        .setAutoCancel(true)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                        .setContentTitle("Help! Overdose " +
                                getNotificationDistance(distance) + " away!")
                        .setContentText("Click to respond!")
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setPriority(Notification.PRIORITY_HIGH);

        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        //swipe to close

        mBuilder.setContentIntent(resultPendingIntent);



        // Gets an instance of the NotificationManager service
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Builds the notification and issues it.
        mNotifyMgr.notify(0, mBuilder.build());
    }

    // Pending intent, used for swipe
    private PendingIntent createOnDismissedIntent(Context context, String incidentId) {
        Intent intent = new Intent(context, NotificationDismissedReceiver.class);
        intent.putExtra("INCIDENT_ID", incidentId);
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(context.getApplicationContext(),
                        0, intent, 0);
        return pendingIntent;
    }


    // Cancel notification with specified incident id
    public void cancelNotification(){
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(0);
    }

    // Issues an POST call to the database to update the user's location
    public void updateLocation(Location location){
        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getResources().getText(R.string.server_url).toString())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        RestInterface.restInterface apiService =
                retrofit.create(RestInterface.restInterface.class);

        SharedPreferences settings = getSharedPreferences(MainActivity.TOKEN_PREFS_NAME, 0);
        String token = settings.getString("Token", null);

        if(token == null){
            Log.d("D","User has no token");
            stopSelf();
            return;
        }

        Call<ResponseBody> call =   apiService.sendLocationUpdate(new RestInterface()
                .new ResponderLatLong(token, location.getLatitude(), location.getLongitude()));

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful()){
                    try {
                        Timber.d("Polling request successful: " + response.body().string());

                    }catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.d("D", "Polling Request failed :(");
                Log.d("D", t.toString());
            }
        });

    }

    public void makeAPICallRespond(){

        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getResources().getText(R.string.server_url).toString())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        RestInterface.restInterface apiService =
                retrofit.create(RestInterface.restInterface.class);

        SharedPreferences settings = getSharedPreferences(MainActivity.TOKEN_PREFS_NAME, 0);
        String token = settings.getString("Token", null);

        if(token == null){
            Log.d("D","User has no token");
            stopSelf();
            return;
        }
        Call<RestInterface.IncidentLocation> call = apiService.respondIncident(new RestInterface().new Responder(token, incidentId, false, false));

        call.enqueue(new Callback<RestInterface.IncidentLocation>() {
            @Override
            public void onResponse(Call<RestInterface.IncidentLocation> call, retrofit2.Response<RestInterface.IncidentLocation> response) {
                if (response.isSuccessful()) {
                    Timber.d("Responding to incident successful: ");
                    return;
                }
            }

            @Override
            public void onFailure(Call<RestInterface.IncidentLocation> call, Throwable t) {
                Log.d("D", "Responding to incident failed :(");
                Log.d("D", t.toString());
            }
        });

    }
}

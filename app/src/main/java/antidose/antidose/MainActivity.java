package antidose.antidose;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.Calendar;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements LocationListener, CancelRequestFragment.CancelRequestListener {

    LocationManager mLocationManager;
    static String IMEI;

    public static final String TOKEN_PREFS_NAME = "User_Token";

    AnimationDrawable alertAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences settings = getSharedPreferences(TOKEN_PREFS_NAME, 0);
        String token = settings.getString("Token", null);

        if (token != null) {
            Button regButton = (Button) findViewById(R.id.button_login);
            regButton.setVisibility(View.INVISIBLE);
            Button settingButton = (Button) findViewById(R.id.button_settings);
            settingButton.setVisibility(View.VISIBLE);
            //service should only run when the user is registered
            Intent intent = new Intent(this, PollingService.class);
            startService(intent);
        }


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                ) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE}, 1);
        }

        //get users imei to make the request


        updateFonts();
        //header
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        //animation
        ImageButton alertImage = (ImageButton) findViewById(R.id.imageButton);
        alertImage.setImageResource(R.drawable.animatedalert);
        alertImage.setScaleType(ScaleType.FIT_CENTER);
        alertAnimation = (AnimationDrawable) alertImage.getDrawable();
        alertAnimation.start();

        String frag = getIntent().getStringExtra("CANCEL_FRAGMENT");
        if(frag !=null){
            showCancelRequestDialog();
        }

    }

    public void showCancelRequestDialog (){
        // Create an instance of the dialog fragment and show it
        DialogFragment dialog = new CancelRequestFragment();
        dialog.show(getSupportFragmentManager(), "CancelRequestFragment");
    }


    @Override
    public void onDialogPositiveClickCancelRequest(DialogFragment dialog) {

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.information, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public void sendAlert(View view) {
        TelephonyManager mngr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        IMEI = mngr.getDeviceId();

        //bring up loading thing, this could take a sec
        Button loadButton = (Button) findViewById(R.id.buttonLoading);
        loadButton.setVisibility(View.VISIBLE);



        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        Location location = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        if (location != null && location.getTime() > Calendar.getInstance().getTimeInMillis() - 2 * 60 * 1000) {
            // Last location in phone was 2 minutes ago
            // Do something with the recent location fix
            //  otherwise wait for the update below
            makeAPICall(IMEI, location);
        } else {
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);

        }
    }

    public void onLocationChanged(Location location) {
        if (location != null) {
            Log.v("Location Changed", location.getLatitude() + " and " + location.getLongitude());
            mLocationManager.removeUpdates(this);
            makeAPICall(IMEI, location);
        }
    }

    // Required functions for implementing location services
    public void onProviderDisabled(String arg0) {}
    public void onProviderEnabled(String arg0) {}
    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {}

    public void updateFonts(){

        Typeface custom_font = Typeface.createFromAsset(getAssets(), "font/gravitylight.otf");
        Typeface custom_font_regular = Typeface.createFromAsset(getAssets(), "font/gravityregular.otf");

        TextView tx = (TextView)findViewById(R.id.textView);
        tx.setTypeface(custom_font_regular);
        TextView tx1 = (TextView)findViewById(R.id.textView2);
        tx1.setTypeface(custom_font);
        TextView tx2 = (TextView)findViewById(R.id.textView3);
        tx2.setTypeface(custom_font);

        Button b1 = (Button)findViewById(R.id.button_login);
        b1.setTypeface(custom_font);
        Button b2 = (Button)findViewById(R.id.button_settings);
        b2.setTypeface(custom_font);

    }

    public void goNavigate(View view) {
        // Do something in response to button
        Intent intent = new Intent(this, NavigationActivity.class);
        startActivity(intent);
    }

    public void goNotify(View view) {
        // Do something in response to button
        Intent intent = new Intent(this, NotifyActivity.class);
        startActivity(intent);
    }

    public void goHelp(View view) {
        // Do something in response to button
        Intent intent = new Intent(this, HelpActivity.class);
        startActivity(intent);

    }

    public void goSettings(View view) {
        // Do something in response to button
        Intent intent = new Intent(this, SettingActivity.class);
        startActivity(intent);

    }
    public void callEMS(View view) {
        // Do something in response to button
        Intent intent = new Intent(this, ContactEMSActivity.class);
        startActivity(intent);
    }

    public void makeAPICall(String IMEI, Location location){
        Gson gson = new GsonBuilder()
                .setLenient()
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getResources().getText(R.string.server_url).toString())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        RestInterface.restInterface apiService =
                retrofit.create(RestInterface.restInterface.class);

        Call<RestInterface.startIncidentResponse> call = apiService.sendHelp(new RestInterface().new Alert(IMEI, location.getLatitude(), location.getLongitude()));

        call.enqueue(new Callback<RestInterface.startIncidentResponse>() {
            @Override
            public void onResponse(Call<RestInterface.startIncidentResponse> call, Response<RestInterface.startIncidentResponse> response) {
                if (response.isSuccessful()){
                        Timber.d("Alert request successful");

                        Intent intent = new Intent(MainActivity.this, HelpActivity.class);
                        int rad = response.body().getRadius();
                        intent.putExtra("INCIDENT_ID", response.body().getIncidentId().toString());
                        intent.putExtra("RADIUS", Integer.toString(response.body().getRadius()/1000));
                        intent.putExtra("NUM_RESPONDERS", Integer.toString(response.body().getNumNotified()));

                        Button loadButton = (Button) findViewById(R.id.buttonLoading);
                        loadButton.setVisibility(View.INVISIBLE);
                        startActivity(intent);
                }
            }

            @Override
            public void onFailure(Call<RestInterface.startIncidentResponse> call, Throwable t) {
                Log.d("D", "Alert Request failed :(");
                Log.d("D", t.toString());
            }
        });

    }

    public void register(View view) {
        Intent intent = new Intent(this, RegistrationActivity.class);
        startActivity(intent);
    }

}

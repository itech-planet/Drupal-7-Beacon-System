package net.iplanet.Drupal7BeaconSystem;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import net.iplanet.drupal.v7.Authentication;
import net.iplanet.drupal.v7.Crud;
import net.iplanet.drupal.v7.Tokens;
import net.iplanet.utils.Logger;

import org.apache.http.HttpResponse;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import static net.iplanet.Drupal7BeaconSystem.R.id.switchOnOff;

public class MainActivity extends AppCompatActivity implements LocationListener {

    //region Global Var & Obj
    Logger Log = new Logger();
    public String session_name;
    public String session_id;
    public String token;
    public String login;
    boolean switchPressed = false;
    Timer timer;
    TimerTask timerTask;
    final Handler handler = new Handler();
    double longitude;
    double latitude;
    ImageView v;
    private LocationManager mService;
    private GpsStatus mStatus;
    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            session_name  = extras.getString(Tokens.KEY_SESSION_NAME_RESPONSE);
            session_id  = extras.getString(Tokens.KEY_SESSION_ID_RESPONSE);
            token  = extras.getString(Tokens.KEY_TOKEN_RESPONSE);
            login  = extras.getString(Tokens.KEY_LOGIN);
        }
    }

    //region Location Listener Procedures
    public void onLocationChanged(Location location) {
        longitude = location.getLongitude();
        latitude = location.getLatitude();
        if(switchPressed) doCloudSync();
    }

    public void onProviderDisabled(String provider) {
        setOffSatelite();
    }

    public void onProviderEnabled(String provider) {
        setOnSatelite();
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        switch (status) {
            case LocationProvider.OUT_OF_SERVICE:
                setIdleSatelite();
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                setOffSatelite();
                break;
            case LocationProvider.AVAILABLE:
                setOnSatelite();
                break;
        }
    }

    //endregion

    //region Switch Button Event & Procedures
    public void switchOnOff_click(View view){
        try {
            switchPressed = !switchPressed;
            final ImageButton btnSwitchOnOff = (ImageButton) findViewById(R.id.switchOnOff);
            final TextView v = (TextView) findViewById(R.id.switchStatusMsg);
            if(switchPressed) {
                btnSwitchOnOff.setImageResource(R.drawable.beacongreen);
                startTimer();
                v.setText("TRANSMITING");
                v.setTextColor(Color.GREEN);
            }else {
                btnSwitchOnOff.setImageResource(R.drawable.beacongray);
                stopTimer();
                v.setText("STOPED");
                v.setTextColor(Color.LTGRAY);
                TextView mLat = (TextView) findViewById(R.id.txtLatitude);
                TextView mLon = (TextView) findViewById(R.id.txtLongitude);
                mLat.setText("Latitude: n/a");
                mLon.setText("Longitude: n/a");
                setIdleTransmission();
            }
        }catch (Exception ex) {
            Log.SyslogException(ex);
        }
    }

    public void startTimer() {
        timer = new Timer();
        initializeTimerTask();
        timer.schedule(timerTask, 30000,60000);
    }

    public void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        doCloudSync();
                    }
                });
            }
        };
    }
    //endregion

    //region TASKS Procedures
    private class cloudSyncActivityTask extends AsyncTask<String, Void, Integer> {

        protected Integer doInBackground(String... params) {
            try {
                String session_name_parm = params[0];
                String session_id_parm = params[1];
                String session_token_parm = params[2];
                String longitude_parm = params[3];
                String latitude_parm = params[4];
                String endpoint_parm = params[5];
                String query_parm = params[6];
                Crud CrudMngr = new Crud();
                CrudMngr.post_Drupal(query_parm, session_name_parm, session_id_parm, session_token_parm);
            }catch (Exception ex){
                Log.SyslogException(ex);
                setTransmissionError();
            }
            return 0;
        }

        protected void onPostExecute(Integer result) {
            setTransmited();
        }

    }
    //endregion

    //region Helpers

    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;

//        String phrase = "";
        StringBuilder phrase = new StringBuilder();
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
//                phrase += Character.toUpperCase(c);
                phrase.append(Character.toUpperCase(c));
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
//            phrase += c;
            phrase.append(c);
        }

        return phrase.toString();
    }

    private void setOnSatelite() {
        ImageView v = (ImageView)findViewById((R.id.GPS_STATUS));
        v.setImageResource(R.drawable.sateliteon);
    }

    private void setOffSatelite() {
        ImageView v = (ImageView)findViewById((R.id.GPS_STATUS));
        v.setImageResource(R.drawable.sateliteoff);
    }

    private void setIdleSatelite() {
        ImageView v = (ImageView)findViewById((R.id.GPS_STATUS));
        v.setImageResource(R.drawable.sateliteidle);
    }

    private void setIdleTransmission() {
        ImageView v = (ImageView)findViewById((R.id.TRANSMISION_STATUS));
        v.setImageResource(R.drawable.transmisionidle);
    }

    private void setTransmissionInvoked() {
        ImageView v = (ImageView)findViewById((R.id.TRANSMISION_STATUS));
        v.setImageResource(R.drawable.transmisioninvoked);
    }

    private void setTransmited() {
        ImageView v = (ImageView)findViewById((R.id.TRANSMISION_STATUS));
        v.setImageResource(R.drawable.transmited);
    }

    private void setTransmissionError() {
        ImageView v = (ImageView)findViewById((R.id.TRANSMISION_STATUS));
        v.setImageResource(R.drawable.transmisonerror);
    }

    //endregion

    //region LOGOUT Procedures
    public void logout_click(View view){
        try {
            new logOutTask().execute();
        }catch (Exception ex) {
            Log.SyslogException(ex);
        }
    }

    private class logOutTask extends AsyncTask<String, Void, Boolean> {
        protected Boolean doInBackground(String... params) {
            try {
                Authentication Auth = new  Authentication();
                HttpResponse response = Auth.doLogout(session_name, session_id, token);
            }catch (Exception ex) {
                Log.SyslogException(ex);
                return false;
            }
            return true;
        }

        protected void onPostExecute(Boolean result) {
            finish();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        }
    }

    //Fires after the OnStop() state
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            trimCache(this);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void trimCache(Context context) {
        try {
            File dir = context.getCacheDir();
            if (dir != null && dir.isDirectory()) {
                deleteDir(dir);
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }
    //endregion

    private void doCloudSync() {
        try {
            setTransmissionInvoked();
            TextView mLat = (TextView) findViewById(R.id.txtLatitude);
            TextView mLon = (TextView) findViewById(R.id.txtLongitude);
            mLat.setText("Latitude: " + latitude);
            mLon.setText("Longitude: " + longitude);
            String  data = "{\"title\":\"" + getDeviceName() + "\", \"type\": \"geo_localization\",\"field_longitude\": {\"und\":[{\"value\": \"" + longitude + "\"}]},\"field_latitude\": {\"und\": [{\"value\": \"" + latitude + "\"}]}}";
            new MainActivity.cloudSyncActivityTask().execute(session_name, session_id, token, Double.toString(longitude), Double.toString(latitude), Tokens.serv_end_Pnt_NODE, data);
        } catch (Exception ex) {
            Log.SyslogException(ex);
            setTransmissionError();
        }
    }
}

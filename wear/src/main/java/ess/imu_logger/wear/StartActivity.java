package ess.imu_logger.wear;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.List;

public class StartActivity extends Activity {

    private TextView mTextView;
    private SharedPreferences sharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });


        PreferenceManager.setDefaultValues(this, R.xml.preferences, false); // false ensures this is only executed once
        //sharedPrefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        // SharedPreferences.Editor editor = sharedPrefs.edit();
        // editor.putString("key", "value");
        // editor.commit();

        List<Sensor> sensors;
        SensorManager mgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensors = mgr.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : sensors) {
            Log.d("Sensors", "" + sensor.getName());
        }
    }

    public void onStartLiveScreen(View v) {
        Intent intent = new Intent(this, ImuLiveScreen.class);
        startActivity(intent);
    }

    public void onStartAnnotateSmoking(View v) {
        Intent intent = new Intent(this, AnnotateSmoking.class);
        startActivity(intent);
    }
}

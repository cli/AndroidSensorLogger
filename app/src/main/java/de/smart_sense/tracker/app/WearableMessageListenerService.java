package de.smart_sense.tracker.app;

/**
 * Created by martin on 09.09.2014.
 */

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.smart_sense.tracker.app.assetConsumer.AssetConsumer;
import de.smart_sense.tracker.libs.Util;
import de.smart_sense.tracker.libs.WearableMessageSenderService;
import de.smart_sense.tracker.libs.data_save.SensorDataSavingService;
import de.smart_sense.tracker.libs.data_zip_upload.ZipUploadService;

import static de.smart_sense.tracker.libs.WearableMessageSenderService.sendMessage;

/**
 * Listens for a message telling it to start the Wearable MainActivity.
 */
public class WearableMessageListenerService extends WearableListenerService implements
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "de.smart_sense.tracker.app.WearableMessageListenerService";

    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate(){

        super.onCreate();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();

    }


    @Override
    public void onMessageReceived(MessageEvent event) {
        Log.d(TAG, "onMessageReceived");
        if (event.getPath().equals(Util.GAC_PATH_TEST_ACTIVITY)) {
            Toast.makeText(this, "Hello from Wearable!", Toast.LENGTH_LONG).show();
        } else if (event.getPath().equals(Util.GAC_PATH_ANNOTATED)) {
            Log.d(TAG, "annotating Smoking");
            Intent sendIntent = new Intent(SensorDataSavingService.BROADCAST_ANNOTATION);
            sendIntent.putExtra(SensorDataSavingService.EXTRA_ANNOTATION_NAME, "smoking");
            sendIntent.putExtra(SensorDataSavingService.EXTRA_ANNOTATION_VIA, "watch_ui");
            sendBroadcast(sendIntent);
        }
    }


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {

        // TODO TODO TODO check that
        // it's for usage outside the callscope
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        dataEvents.close();

        Log.d(TAG, "onDataChanged");


        for (DataEvent event : events) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {

                Log.d(TAG, "DataItem changed: " + event.getDataItem().getUri());

                if (event.getDataItem().getUri().getPath().equals(Util.GAC_PATH_SENSOR_DATA)) {


                    Intent mServiceIntent = new Intent(this, AssetConsumer.class);
                    mServiceIntent.setAction(AssetConsumer.ACTION_START_SERVICE);
                    this.startService(mServiceIntent);


                    /*
                    Log.d(TAG, "trying to extract asset");

                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());

                    String fileName = dataMapItem.getDataMap().getString(Util.SENSOR_FILE_NAME);
                    Asset profileAsset = dataMapItem.getDataMap().getAsset(Util.SENSOR_FILE);

                    saveFileFromAsset(fileName, profileAsset);  // TODO error handling


                    ConnectionResult result =
                            mGoogleApiClient.blockingConnect(1000, TimeUnit.MILLISECONDS);
                    if (!result.isSuccess()) {
                        Log.d(TAG, "GoogleClientConnect was false");
                        return;
                    }
                    Uri.Builder uri = new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(Util.GAC_PATH_SENSOR_DATA);
                    Wearable.DataApi.deleteDataItems(mGoogleApiClient, uri.build()).await();


                    sendMessage(this, Util.GAC_PATH_CONFIRM_FILE_RECEIVED, fileName);


                    mGoogleApiClient.disconnect();

                    Log.d(TAG, "SensorDataFileName: " + fileName);
                    */
                }
            }
        }

        // mGoogleApiClient.disconnect();
    }





    private void saveFileFromAsset(String fileName, Asset asset) {

        Log.d(TAG, "trying to Save File form Asset");

        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        ConnectionResult result =
                mGoogleApiClient.blockingConnect(1000, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return;
        }
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return;
        }

        if (!Util.isExternalStorageWritable())
            return; // TODO cry for some help...

        Util.checkDirs();
        try {

            // CHECK IF FILE ALREADY EXISTED! IF SO BREAK.
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + Util.fileDir, "wear_" + fileName);
            if(file.exists())
                return;

            OutputStream out = new FileOutputStream(file, true);

            byte[] buffer = new byte[1024];
            int bytesRead;
            //read from is to buffer
            while ((bytesRead = assetInputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            assetInputStream.close();
            //flush OutputStream to write any buffered data to file
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // decode the stream into a bitmap
        // return BitmapFactory.decodeStream(assetInputStream);
    }



    @Override
    public void onDestroy(){
        if(mGoogleApiClient.isConnected())
            mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "Failed to connect to Google Api Client");
    }

}

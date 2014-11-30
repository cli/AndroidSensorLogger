package ess.imu_logger.libs.data_save;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import ess.imu_logger.libs.Util;
import ess.imu_logger.libs.logging.Logger;

/**
 * Created by martin on 28.08.2014.
 */
public class PlainFileWriter extends Thread {

    // private static final String TAG = PlainFileWriter.class.getSimpleName();
    private static final String TAG = "ess.imu_logger.libs.data_save.PlainFileWriter";


    public static final String MESSAGE_TYPE_ACTION = "ess.imu_logger.libs.data_save.MESSAGE_TYPE_ACTION";
    public static final int MESSAGE_ACTION_SAVE = 0;
    public static final int MESSAGE_ACTION_POLL = 1;

    public static final String MESSAGE_DATA = "ess.imu_logger.libs.data_save.MESSAGE_DATA";

    private Handler inHandler;


    private ArrayList<String> data;


    public static final String fileExtension = ".log";



    private static final Integer MAX_FILE_SIZE = 1024*1024*1; // 1 MB
    private static final Integer MAX_BUFFER_SIZE = 1024*1;



    //private static final Integer MAX_FILE_SIZE = 1024*1024*50; // 50 MB
    //private static final Integer MAX_BUFFER_SIZE = 1024*8;


    public PlainFileWriter() {
        this.data = new ArrayList<String>(MAX_BUFFER_SIZE);
    }


    public void run() {
        try {
            // preparing a looper on current thread
            // the current thread is being detected implicitly
            Looper.prepare();
            synchronized (this) {
                // now, the handler will automatically bind to the
                // Looper that is attached to the current thread
                // You don't need to specify the Looper explicitly
                inHandler = new Handler() {
                    public void handleMessage(Message msg) {
                        // Act on the message received from my UI thread doing my stuff


                        // TODO Buffer Data in Array so file is not opened every single Sensor Event

                        if (msg.getData().getInt(MESSAGE_TYPE_ACTION) == MESSAGE_ACTION_POLL) {
                            Log.d(TAG, "polling");
                            writeSensorDataToFile();
                            startPolling();

                        } else if (msg.getData().getInt(MESSAGE_TYPE_ACTION) == MESSAGE_ACTION_SAVE) {

                            String sensorValue = msg.getData().getString(MESSAGE_DATA);

                            if (data.size() > MAX_BUFFER_SIZE) {

                                Log.v(TAG, "saving some Data");

                                if (!Util.isExternalStorageWritable())
                                    return; // TODO cry for some help...

                                Util.checkDirs();
                                File file;
                                FileOutputStream out = null;

                                try {
                                    file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + Util.fileDir, getFilename());
                                    out = new FileOutputStream(file, true);
                                    for (int i = 0; i < data.size(); i++) {
                                        out.write(data.get(i).getBytes(), 0, data.get(i).length());
                                    }
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (out != null) {
                                            out.flush();
                                            out.close();
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    data = new ArrayList<String>(MAX_BUFFER_SIZE);
                                }

                                Log.d(TAG, "saved Sensor Values");
                            } else {
                                data.add(sensorValue);
                            }
                        }
                    }
                };
                notifyAll();
            }

            // After the following line the thread will start
            // running the message loop and will not normally
            // exit the loop unless a problem happens or you
            // quit() the looper (see below)
            Looper.loop();

            Log.i(TAG, "Thread exiting gracefully");
        } catch (Throwable t) {
            Log.e(TAG, "Thread halted due to an error", t);
        }
    }

    private boolean writeSensorDataToFile() {
        if (!Util.isExternalStorageWritable())
            return false;

        Util.checkDirs();
        File file;
        FileOutputStream out = null;

        try {
            file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + Util.fileDir, getFilename());
            out = new FileOutputStream(file, true);
            while(!Logger.sensorEvents.isEmpty()){ // TODO check if that condition is ok...  think so...
                String sensorEvent = Logger.sensorEvents.poll();
                out.write(sensorEvent.getBytes(), 0, sensorEvent.length());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            data = new ArrayList<String>(MAX_BUFFER_SIZE);
        }
        return false;
    }

    // This method is allowed to be called from any thread
    public synchronized void requestStop() {
        // using the handler, post a Runnable that will quit()
        // the Looper attached to our DownloadThread
        // obviously, all previously queued tasks will be executed
        // before the loop gets the quit Runnable
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                // This is guaranteed to run on the DownloadThread
                // so we can use myLooper() to get its looper
                Log.i(TAG, "Thread loop quitting by request");

                Looper.myLooper().quit();
            }
        });
    }

    private Handler getHandler() {
        while (inHandler == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                //Ignore and try again.
            }
        }
        return inHandler;
    }

    public synchronized void saveString(final String data) {
        Message msg = new Message();
        Bundle b = new Bundle();
        b.putInt(MESSAGE_TYPE_ACTION, MESSAGE_ACTION_SAVE);
        b.putString(MESSAGE_DATA, data);
        msg.setData(b);

        // could be a runnable when calling post instead of sendMessage
        getHandler().sendMessage(msg);
    }

    public synchronized void startPolling() {
        Message msg = new Message();
        Bundle b = new Bundle();
        b.putInt(MESSAGE_TYPE_ACTION, MESSAGE_ACTION_POLL);
        msg.setData(b);

        // could be a runnable when calling post instead of sendMessage
        getHandler().sendMessage(msg);
    }


    private String getFilename() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + File.separator + Util.fileDir);
        String[] listOfFiles = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(fileExtension) ? true : false;
            }
        });

        if (listOfFiles.length < 1) {
            return String.valueOf(System.currentTimeMillis()) + fileExtension;
        }

        Arrays.sort(listOfFiles);


        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + File.separator + Util.fileDir, listOfFiles[listOfFiles.length - 1]);
        if (file.length() > MAX_FILE_SIZE) {
            return String.valueOf(System.currentTimeMillis()) + fileExtension;
        }

        return listOfFiles[listOfFiles.length - 1];

    }


}
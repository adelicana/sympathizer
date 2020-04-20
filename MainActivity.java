package com.example.sympathizer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class MainActivity<ServiceConnection> extends AppCompatActivity implements SensorEventListener{
    MediaPlayer player;
    private MediaRecorder mRecorder = null;
    private static final String TAG = "MyActivity";

    int nl; //if 0, quiet; if 1, noisy
    boolean isDay; //if true, current time is before 6pm

    //for movement & light sensor stuff
    int movementType;
    private SensorManager mSensorManager;
    private Sensor mAccel, mLight;
    private float movementThreshold;
    private float prevX;
    private float prevY;
    private float prevZ;
    private float changeX = 0;
    private float changeY = 0;
    private float changeZ = 0;
    double activity, light, sumLight, numLightValues, averageLight, lightingType;

    //songs
    int song;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //accelerometer
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        movementThreshold = 5;
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mAccel == null)
            Toast.makeText(getApplicationContext(), "No accelerometer found!", Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(getApplicationContext(), "Accelerometer found!", Toast.LENGTH_SHORT).show();

        //light sensor
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (mLight == null)
            Toast.makeText(getApplicationContext(), "No light sensor found!", Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(getApplicationContext(), "Light sensor found!", Toast.LENGTH_SHORT).show();

        //read or reread the room
        Button refresh = (Button) findViewById(R.id.refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Now sympathizing; please wait...", Toast.LENGTH_LONG).show();
                stop(v);
                sympathizeStart(v);
            }
        });

        //play the song
        Button play = (Button) findViewById(R.id.play);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Playing song...", Toast.LENGTH_LONG).show();
                stop(v);
                play(v, song);
            }
        });

        //stop the song
        Button stop = (Button) findViewById(R.id.stop);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Stopping song...", Toast.LENGTH_SHORT).show();
                stop(v);
            }
        });

        //pause the song
        Button pause = (Button) findViewById(R.id.pause);
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(), "Pausing song...", Toast.LENGTH_SHORT).show();
                pause(v);
            }
        });

    }

    public void checkTime() {
        //return true if day, false if night
        Date date = new Date();   // given date
        Calendar calendar = GregorianCalendar.getInstance(); // creates a new calendar instance
        calendar.setTime(date);   // assigns calendar to given date
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        //distinguish between "day" and "evening" by 6 pm
        if (hour >= 18) {
            Log.d(TAG, "Nighttime");
            isDay = false;
        }
        else {
            Log.d(TAG, "Daytime");
            isDay = true;
        }
    }

    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener((SensorEventListener) this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //get accel data
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){

            // get the change of the x,y,z values of the accelerometer
            changeX = Math.abs(prevX - event.values[0]);
            changeX = Math.abs(prevY - event.values[1]);
            changeX = Math.abs(prevZ - event.values[2]);

            //account for some noise
            if (changeX < .6)
                changeX = 0;
            if (changeX < .6)
                changeX = 0;
            if (changeX < .6)
                changeX = 0;

            if ((changeX > 7) || (changeY > 7) || (changeZ > 8)) {
                //sharper movements = higher activity score
                activity += 2;
            }
            else if ((changeX > 4) || (changeY > 4) || (changeZ > 2.5)) {
                //gentler movements = lower activity score
                activity++;
            }
            else{}

            //update previous values
            prevX = event.values[0];
            prevY = event.values[1];
            prevZ = event.values[2];
            }
        //get light sensor data
        if(event.sensor.getType() == Sensor.TYPE_LIGHT){
            sumLight += event.values[0];
            numLightValues++;
            averageLight = sumLight/numLightValues;
            Log.d(TAG, "LIGHT: " + event.values[0] + " | Average light: " + averageLight);
        }

        }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //if accuracy changes
    }

    public void sympathizeFinish(){
        /*Data will be processed here.*/

        /*============PROCESSING MOVEMENT============
        Based on real-life tests that include moving with the phone in the pocket:
            -10 full seconds of sharp movement can add up to an activity score of around 39-50, even potentially peaking in the 70s
            -10 full seconds of gentle movement can add up to a score of around 14-21

        Scores can vary if movements are inconsistent, so the maximum of "gentle" has been raised and the minimum of "sharp" has been lowered to account for median values
        Thus, the following qualifications are used: 0-14 = no/little movement/"still"; 15-33 = moving gently/"calm"; 33+ = moving sharply/"energetic"*/

        if(activity <= 14) //still
            movementType = 1;
        else if (activity > 14 && activity <= 33) //calm
            movementType = 2;
        else //energetic
            movementType = 3;

        /*============PROCESSING LIGHT============
        Darkness/dimness yielded very low light values, found around 0-10. Placing the phone in dimness for the 10 full seconds yields an averageLight of around 10-14.
        Placing the phone in a fully lit room for the 10 full seconds yields an averageLight of around 50-100 (may vary depending on color of light). Depending on
        the phone's proximity to the light, it can peak up to 150. However, again, averageLight can vary if light is changing.
        Thus, the following qualifications are used: 0-40 = dark/dim, 40+ = bright
         */

        if(averageLight <= 40) //dark or dim
            lightingType = 0;
        else //bright
            lightingType = 1;

        //Printing final results
        Log.d(TAG, "Movement type: " + movementType + " | Noise level: " + nl + " | Lighting type: " + lightingType + " | Day?: " + isDay);
        Toast.makeText(getApplicationContext(), "Sympathized! You can now press play.", Toast.LENGTH_LONG).show();

        /*============CHOOSING SONG============
        Music will be chosen based on the variables: nl, isDay, movementType, and lightingType.
        Once again...
            nl (noise level)
                0 = "quiet"
                1 = "noisy"
            isDay
                true = it's before 6pm
                false = it's after 6pm
            movementType
                1 = still
                2 = calm
                3 = energetic
            lightingType
                0 "dark" or "dim"
                1 = bright
        */

        if(isDay){
            if(movementType == 1){
                if (lightingType == 0){
                    if (nl == 0){
                        //it is sometime before 6pm, the user is still, the environment is dim, and it is quiet
                        song = R.raw.day_intheshade;
                    }
                    else{
                        //it is sometime before 6pm, the user is still, the environment is dim, and it is noisy
                        song = R.raw.day_still_bright;
                    }
                }
                else{
                    if (nl == 0){
                        //it is sometime before 6pm, the user is still, the environment is bright, and it is quiet
                        song = R.raw.stretchinthesun;
                    }
                    else{
                        //it is sometime before 6pm, the user is still, the environment is bright, and it is noisy
                        song = R.raw.sunnyday_noisy;
                    }
                }
            }else if (movementType == 2){
                if (lightingType == 0){
                    //it is sometime before 6pm, the user is moving calmly, and the environment is dim
                    song = R.raw.cloudystroll;
                }
                else{
                    //it is sometime before 6pm, the user is moving calmly, and the environment is bright
                    song = R.raw.day_bright_noisy;
                }
            }else{
                if (lightingType == 0){
                    //it is sometime before 6pm, the user is moving energetically, and the environment is dim
                    song = R.raw.indoor_games;
                }
                else{
                    //it is sometime before 6pm, the user is moving energetically, and the environment is bright
                    song = R.raw.cheery_dayjog;
                }
            }
        }
        else{
            if(movementType == 1){
                if (lightingType == 0){
                    if (nl == 0){
                        //it is sometime after 6pm, the user is still, the environment is dim, and it is quiet
                        song = R.raw.stargaze;
                    }
                    else{
                        //it is sometime after 6pm, the user is still, the environment is dim, and it is noisy
                        song = R.raw.still_lit_noisy;
                    }
                }
                else{
                    if (nl == 0){
                        //it is sometime after 6pm, the user is still, the environment is bright, and it is quiet
                        song = R.raw.lightsinbed;
                    }
                    else{
                        //it is sometime after 6pm, the user is still, the environment is bright, and it is noisy
                        song = R.raw.leisurely_dim_noisy;
                    }
                }
            }else if (movementType == 2){
                if (lightingType == 0){
                    //it is sometime after 6pm, the user is moving calmly, and the environment is dim
                    song = R.raw.nightstroll_dim;
                }
                else{
                    //it is sometime after 6pm, the user is moving calmly, and the environment is bright
                    song = R.raw.nightstroll_lit;
                }
            }else{
                if (lightingType == 0){
                    //it is sometime after 6pm, the user is moving energetically, and the environment is dim
                    song = R.raw.indoor_cheery;
                }
                else{
                    //it is sometime after 6pm, the user is moving energetically, and the environment is bright
                    song = R.raw.cheery_nightjog;
                }
            }
        }

        //check if day or night
        //day
            //check if moving
            //moving
                //check temp
                    //if mild = dayMovingMild
                    //if cool = dayMovingCool
                    //if warm = dayMovingWarm
            //not moving
                //check noise level
                //if noisy
                    //check temp
                        // if mild = daySittingNoiseMild
                        // if cool = daySittingNoiseCool
                        // if warm = daySittingNoiseWarm
                //if quiet
                    //check temp
                        // if mild = daySittingQuietMild
                        // if cool = daySittingQuietCool
                        // if warm = daySittingQuietWarm
        //night
            //check if moving
            //moving
                //check temp
                    //if mild = nightMovingMild
                    //if cool = nightMovingCool
                    //if warm = nightMovingWarm
            //not moving
            //check noise level
            //if noisy
                //check temp
                    // if mild = nightSittingNoiseMild
                    // if cool = nightSittingNoiseCool
                    // if warm = nightSittingNoiseWarm
            //if quiet
                //check temp
                    // if mild = nightSittingQuietMild
                    // if cool = nightSittingQuietCool
                    // if warm = nightSittingQuietWarm
    }

    public void stopCheckingNoise(){
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
    }

    public void stopCheckingSensors(){
        mSensorManager.unregisterListener(this);
    }

    public void sympathizeStart(View v) {
        //THE FIRST STEP IS TO SAMPLE NOISE AND MOVEMENT.
        //start everything at zero

        //noise level; 0 = "quiet"; 1 = "noisy"
        nl = 0;

        //movement; 1 = still; 2 = calm; 3 = energetic
        movementType = 0; //how data will be analyzed, what will be used for qualifications
        activity = 0; //for live data collection

        //light; 0 = dim; 1 = bright
        light = 0; //how data will be analyzed, what will be used for qualifications

        //for live data collection
        averageLight = 0;
        sumLight = 0;
        numLightValues = 0;

        //set up noise detection
        if (mRecorder == null) {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setOutputFile("/dev/null");
            try {
                mRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mRecorder.start();
            mRecorder.getMaxAmplitude();
        }

        //set up accelerometer & light sensors
        mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_NORMAL);

        //run both for a period of 10 seconds
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run(){
                Toast.makeText(getApplicationContext(), "Please wait...", Toast.LENGTH_LONG).show();

                //noise check
                double rawnoise = 0;
                if (mRecorder != null) {
                    rawnoise = mRecorder.getMaxAmplitude();
                    if (rawnoise/1000 > 15.3){
                        //if noise value surpasses 15.3, then the environment is "noisy" (aka, nl = 1)
                        nl = 1;
                    }
                }
                else
                    rawnoise = 0;
                //Log.d(TAG, "Noise detected = " + rawnoise);
                stopCheckingNoise();

                stopCheckingSensors();

                /*
                Log.d(TAG, "Total 'activity': " + activity);
                Log.d(TAG, "Type of motion': " + movement);
                Log.d(TAG, "Total average light: " + averageLight);
                 */

                //move onto next step ONLY after noise & movement & light checks have finished
                sympathizeFinish();
            }
        }, 10000);   //5 seconds

        //get time
        checkTime();
    }

    public void play(View v, int song) {
        if (player == null) {
            //create a new media player with the song chosen
            player = MediaPlayer.create(this, song);
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopPlayer();
                }
            });
        }
        //start playing music
        player.start();
    }

    public void pause(View v) {
        if (player != null) {
            player.pause();
        }
    }

    public void stop(View v) {
        stopPlayer();
    }

    private void stopPlayer() {
        if (player != null) {
            player.release();
            player = null;
        }

    }
}
//Music service by moisoni97: https://github.com/moisoni97/services.backgroud_music
//Music tracks used from Animal Crossing: New Leaf, for educational purposes
package com.example.safetyscoreassistant;

import static android.os.SystemClock.elapsedRealtime;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gravitySensor;
    private long lastCycleTime;

    private int systemState = 0; //0: Stopped, 1: Running, 2: Paused, 3: Calibrating

    private float currX = 0.0f;
    private float currY = 0.0f;
    private float currZ = 0.0f;

    private float gX = 0.0f;
    private float gY = 0.0f;
    private float gZ = 0.0f;

    private float xOffset = 0.0f;
    private float yOffset = 0.0f;
    private float zOffset = 0.0f;

    private float gAngle = 0.0f;
    private float gMag = 0.0f;

    private float lateralAcceleration = 0.0f;
    private float longitudinalDeceleration = 0.0f;
    private float score = 100.0f;

    private float hardTurnPercent = 0.0f;
    private float hardTurnNumerator = 0.0f;
    private float hardTurnDenominator = 0.0f;

    private float hardBrakePercent = 0.0f;
    private float hardBrakeNumerator = 0.0f;
    private float hardBrakeDenominator = 0.0f;

    private float cumSumX = 0.0f;
    private float cumSumY = 0.0f;
    private float cumSumZ = 0.0f;
    private float counter = 0.0f;

    private LinearLayout latAccelBg, brakeAccelBg;
    private TextView latAccel, brakeAccel, safetyScore;
    private TextView turnPct, turnNum, turnDen;
    private TextView brakePct, brakeNum, brakeDen;
    private Button resetBtn, pauseBtn;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeViews();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, 2);
        }
        if (sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) {
            gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            sensorManager.registerListener(this, gravitySensor, 2);
        }

        lastCycleTime = elapsedRealtime();
    }

    public void initializeViews() {
        latAccelBg = (LinearLayout) findViewById(R.id.latAccelBg);
        brakeAccelBg = (LinearLayout) findViewById(R.id.brakeAccelBg);

        latAccel = (TextView) findViewById(R.id.latAccel);
        brakeAccel = (TextView) findViewById(R.id.brakeAccel);

        turnPct = (TextView) findViewById(R.id.turnPct);
        turnNum = (TextView) findViewById(R.id.turnNum);
        turnDen = (TextView) findViewById(R.id.turnDen);

        brakePct = (TextView) findViewById(R.id.brakePct);
        brakeNum = (TextView) findViewById(R.id.brakeNum);
        brakeDen = (TextView) findViewById(R.id.brakeDen);

        safetyScore = (TextView) findViewById(R.id.safetyScore);

        resetBtn = (Button) findViewById(R.id.buttonReset);
        pauseBtn = (Button) findViewById(R.id.buttonPause);
    }

    //onResume() register the accelerometer for listening the events
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, 2);
        sensorManager.registerListener(this, gravitySensor, 2);
    }

    //onPause() unregister the accelerometer for stop listening the events
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // get the change of the x,y,z values of the accelerometer
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            currX = event.values[0] / 9.81f;
            currY = event.values[1] / 9.81f;
            currZ = event.values[2] / 9.81f;
        } else if (event.sensor.getType() == Sensor.TYPE_GRAVITY && systemState == 3) {
            gX = event.values[0] / 9.81f;
            gY = event.values[1] / 9.81f;
            gZ = event.values[2] / 9.81f;
            calibrate();
        }

        if (systemState == 1) {
            // internal computations
            compute();

            // display the current x,y,z accelerometer values
            updateValues();
        }

        lastCycleTime = elapsedRealtime();
    }

    // Calculate everything
    public void compute() {
        //coordinate transformation
        lateralAcceleration = Math.abs(currX - xOffset / gMag);
        longitudinalDeceleration = (float) ((currY - yOffset / gMag) * Math.cos(gAngle) - (currZ - zOffset / gMag) * Math.sin(gAngle));

        longitudinalDeceleration = Math.min(longitudinalDeceleration, 0.0f);

        long timestep = elapsedRealtime() - lastCycleTime;
        if (lateralAcceleration > 0.2f && lateralAcceleration < 0.4f) {
            hardTurnDenominator += timestep;
            if (lateralAcceleration > 0.36f) {
                latAccelBg.setBackgroundColor(Color.parseColor("#bd9100"));
            } else {
                latAccelBg.setBackgroundColor(Color.parseColor("#00b003"));
            }
        } else if (lateralAcceleration >= 0.4f) {
            hardTurnNumerator += timestep;
            latAccelBg.setBackgroundColor(Color.parseColor("#ba0000"));
        } else {
            latAccelBg.setBackgroundColor(Color.parseColor("#000000"));
        }

        if (hardTurnDenominator < 0.0001f) {
            hardTurnPercent = 0.0f;
        } else {
            hardTurnPercent = hardTurnNumerator / hardTurnDenominator * 100.0f;
        }

        if (longitudinalDeceleration < -0.1f && longitudinalDeceleration > -0.3f) {
            hardBrakeDenominator += timestep;
            brakeAccelBg.setBackgroundColor(Color.parseColor("#00b003"));
            if (longitudinalDeceleration < -0.27f) {
                brakeAccelBg.setBackgroundColor(Color.parseColor("#bd9100"));
            } else {
                brakeAccelBg.setBackgroundColor(Color.parseColor("#00b003"));
            }
        } else if (longitudinalDeceleration <= -0.3f) {
            hardBrakeNumerator += timestep;
            brakeAccelBg.setBackgroundColor(Color.parseColor("#ba0000"));
        } else {
            brakeAccelBg.setBackgroundColor(Color.parseColor("#000000"));
        }

        if (hardBrakeDenominator < 0.0001f) {
            hardBrakePercent = 0.0f;
        } else {
            hardBrakePercent = hardBrakeNumerator / hardBrakeDenominator * 100.0f;
        }

        hardTurnPercent = Math.min(hardTurnPercent, 100.0f);
        hardBrakePercent = Math.min(hardBrakePercent, 100.0f);

        score = (float) (115.382324f - 22.526504 * 0.682854 * Math.pow(1.127294, hardBrakePercent) * Math.pow(1.019630, hardTurnPercent));
        score = Math.max(score, 0.0f);
    }

    // display the current x,y,z accelerometer values
    public void updateValues() {
        latAccel.setText(Integer.toString((int) (lateralAcceleration * 100.0f)));
        brakeAccel.setText(Integer.toString((int) (longitudinalDeceleration * 100.0f)));

        turnPct.setText(Float.toString(Math.round(hardTurnPercent * 100.0f) / 100.0f));
        turnNum.setText(Float.toString(Math.round(hardTurnNumerator * 100.0f) / 100.0f));
        turnDen.setText(Float.toString(Math.round(hardTurnDenominator * 100.0f) / 100.0f));

        brakePct.setText(Float.toString(Math.round(hardBrakePercent * 100.0f) / 100.0f));
        brakeNum.setText(Float.toString(Math.round(hardBrakeNumerator * 100.0f) / 100.0f));
        brakeDen.setText(Float.toString(Math.round(hardBrakeDenominator * 100.0f) / 100.0f));

        safetyScore.setText(Float.toString(Math.round(score * 1000.0f) / 1000.0f));
    }

    public void resetValues() {
        cumSumX = 0.0f;
        cumSumY = 0.0f;
        cumSumZ = 0.0f;
        counter = 0.0f;

        hardTurnPercent = 0.0f;
        hardTurnNumerator = 0.0f;
        hardTurnDenominator = 0.0f;

        hardBrakePercent = 0.0f;
        hardBrakeNumerator = 0.0f;
        hardBrakeDenominator = 0.0f;

        score = 100.0f;
    }

    // run calibration routine
    public void calibrate() {
        resetBtn.setText("Calibrating...");

        if (counter < 100.0f) {
            cumSumX += gX;
            cumSumY += gY;
            cumSumZ += gZ;
            counter += 1.0f;
        } else {
            systemState = 1;
            resetBtn.setText("Stop Run");
        }

        xOffset = cumSumX / counter;
        yOffset = cumSumY / counter;
        zOffset = cumSumZ / counter;

        gMag = (float) Math.sqrt(Math.pow(xOffset, 2) + Math.pow(yOffset, 2) + Math.pow(zOffset, 2));
        gAngle = (float) Math.atan2(yOffset, zOffset);
    }

    public void resetButton(View view) {
        if (systemState == 0) {
            resetValues();
            systemState = 3;
        } else if (systemState == 1) {
            resetBtn.setText("Start Run");
            systemState = 0;
        } else if (systemState == 2) {
            resetBtn.setText("Start Run");
            pauseBtn.setText("Pause Run");
            systemState = 0;
        }
    }

    public void pauseButton(View view) {
        if (systemState == 1) {
            pauseBtn.setText("Resume Run");
            systemState = 2;
        } else if (systemState == 2) {
            pauseBtn.setText("Pause Run");
            systemState = 1;
        }
    }
}
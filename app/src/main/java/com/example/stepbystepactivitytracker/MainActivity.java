package com.example.stepbystepactivitytracker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "SensorModule";

    // ---------- Shake detection tuning ----------
    /** g-force (1.0 = resting on a table) that counts as a shake. */
    private static final float SHAKE_THRESHOLD_G = 2.5f;
    /** Minimum time between two counted shakes, so one shake isn't counted many times. */
    private static final long SHAKE_COOLDOWN_MS = 500;

    // ---------- Sensors ----------
    private SensorManager sensorManager;
    private Sensor stepCounterSensor;
    private Sensor accelerometerSensor;

    // ---------- Sensor values (read by the session logic) ----------
    /**
     * Raw value from TYPE_STEP_COUNTER: total steps since the phone last rebooted.
     * -1 until the first sensor event arrives.
     */
    private long rawStepCount = -1;
    /** First raw value seen after the app opened, used to show steps since launch. */
    private long stepBaseline = -1;
    /** Number of shakes detected since the app opened. */
    private int shakeCount = 0;
    private long lastShakeTime = 0;
    /** Ask for the permission only once, so a denial doesn't re-prompt on every onResume. */
    private boolean permissionRequested = false;

    // ---------- UI ----------
    private TextView tvStepStatus;
    private TextView tvRawSteps;
    private TextView tvStepsSinceOpen;
    private TextView tvShakeStatus;
    private TextView tvShakeCount;
    private TextView tvAcceleration;

    private final ActivityResultLauncher<String> activityPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    registerStepCounter();
                } else {
                    tvStepStatus.setText(R.string.step_status_permission_denied);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvStepStatus = findViewById(R.id.tvStepStatus);
        tvRawSteps = findViewById(R.id.tvRawSteps);
        tvStepsSinceOpen = findViewById(R.id.tvStepsSinceOpen);
        tvShakeStatus = findViewById(R.id.tvShakeStatus);
        tvShakeCount = findViewById(R.id.tvShakeCount);
        tvAcceleration = findViewById(R.id.tvAcceleration);

        initSensors();
        updateStepUi();
        updateShakeUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startSensors();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop listening while the screen is not visible to save battery.
        sensorManager.unregisterListener(this);
    }

    // =====================================================================
    // Setup
    // =====================================================================

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (stepCounterSensor == null) {
            tvStepStatus.setText(R.string.step_status_unavailable);
        }
        if (accelerometerSensor == null) {
            tvShakeStatus.setText(R.string.shake_status_unavailable);
        }
    }

    private void startSensors() {
        if (stepCounterSensor != null) {
            if (hasActivityRecognitionPermission()) {
                registerStepCounter();
            } else if (!permissionRequested) {
                permissionRequested = true;
                tvStepStatus.setText(R.string.step_status_waiting_permission);
                activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION);
            } else {
                tvStepStatus.setText(R.string.step_status_permission_denied);
            }
        }

        if (accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor,
                    SensorManager.SENSOR_DELAY_GAME);
            tvShakeStatus.setText(R.string.shake_status_running);
        }
    }

    /** ACTIVITY_RECOGNITION is a runtime permission from Android 10 (API 29). */
    private boolean hasActivityRecognitionPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void registerStepCounter() {
        if (stepCounterSensor == null) return;
        sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI);
        tvStepStatus.setText(R.string.step_status_running);
    }

    // =====================================================================
    // SensorEventListener
    // =====================================================================

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_STEP_COUNTER) {
            handleStepCounter(event);
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            handleAccelerometer(event);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for step counting or shake detection.
    }

    // ---------- ข้อ 1: Step Counter ----------

    private void handleStepCounter(SensorEvent event) {
        // TYPE_STEP_COUNTER reports a float, but it is always a whole number of steps.
        long value = (long) event.values[0];

        // Sanity check: the value is cumulative since boot, so it should never be negative.
        if (value < 0) {
            Log.w(TAG, "Ignoring invalid step counter value: " + value);
            return;
        }

        // If the value went down, the phone rebooted / counter was reset: start a new baseline.
        if (stepBaseline < 0 || value < rawStepCount) {
            stepBaseline = value;
        }

        rawStepCount = value;
        Log.d(TAG, "Step counter raw=" + rawStepCount + " sinceOpen=" + getStepsSinceOpen());
        onStepCountChanged(rawStepCount);
        updateStepUi();
    }

    // ---------- ข้อ 2: Shake Detection ----------

    private void handleAccelerometer(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Total acceleration in units of g. About 1.0 when the phone is still.
        float gForce = (float) Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
        tvAcceleration.setText(getString(R.string.acceleration_format,
                String.format(Locale.US, "%.2f", gForce)));

        if (gForce < SHAKE_THRESHOLD_G) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastShakeTime < SHAKE_COOLDOWN_MS) return;
        lastShakeTime = now;

        shakeCount++;
        Log.d(TAG, "Shake detected gForce=" + gForce + " count=" + shakeCount);
        onShakeDetected(shakeCount);
        updateShakeUi();
    }

    // =====================================================================
    // Hooks for the session logic (คนที่ 2) — called whenever a sensor value changes
    // =====================================================================

    /** Called on every new TYPE_STEP_COUNTER reading with the raw cumulative value. */
    protected void onStepCountChanged(long rawStepCount) {
        // Session logic plugs in here (session delta = rawStepCount - sessionStartSteps).
    }

    /** Called once per detected shake with the shake total since the app opened. */
    protected void onShakeDetected(int shakeCount) {
        // Session logic plugs in here (session delta = shakeCount - sessionStartShakes).
    }

    /** Raw step counter value (steps since boot), or -1 if no reading yet. */
    public long getRawStepCount() {
        return rawStepCount;
    }

    /** Steps counted since the app was opened, or 0 if no reading yet. */
    public long getStepsSinceOpen() {
        return stepBaseline < 0 ? 0 : rawStepCount - stepBaseline;
    }

    /** Shakes detected since the app was opened. */
    public int getShakeCount() {
        return shakeCount;
    }

    // =====================================================================
    // UI
    // =====================================================================

    private void updateStepUi() {
        tvRawSteps.setText(getString(R.string.raw_steps_format,
                rawStepCount < 0 ? "-" : String.valueOf(rawStepCount)));
        tvStepsSinceOpen.setText(String.valueOf(getStepsSinceOpen()));
    }

    private void updateShakeUi() {
        tvShakeCount.setText(String.valueOf(shakeCount));
    }
}

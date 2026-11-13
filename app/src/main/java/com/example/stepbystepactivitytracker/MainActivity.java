package com.example.stepbystepactivitytracker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
    private TextView tvShakeStatus;
    private TextView tvAcceleration;
    private ActivityHistoryView historyView;

    // ---------- Session logic ----------
    private DBHelper dbHelper;
    private Button btnSession;
    private TextView tvSessionStatus;
    private TextView tvSessionSteps;
    private TextView tvSessionShakes;
    private TextView tvSessionDuration;
    private TextView tvAvgSteps;
    private TextView tvAvgShakes;
    private TextView tvTotalSteps;
    private TextView tvTotalShakes;
    private TextView tvRecord;

    /** ข้อ 3: ค่าเริ่มต้นของ session เอาไว้หักลบกับค่าจาก sensor (session delta) */
    private boolean sessionActive = false;
    private long sessionStartSteps = -1;   // -1 = ยังไม่มีค่าจาก sensor ให้ใช้เป็นฐาน
    private int sessionStartShakes = 0;
    private long sessionSteps = 0;
    private int sessionShakes = 0;

    /** ข้อ 4: เวลาเริ่มและระยะเวลาของ session */
    private long sessionStartElapsed = 0;
    private long sessionDurationMs = 0;

    /** อัปเดตเวลาบนหน้าจอทุก 1 วินาทีขณะ session ทำงาน */
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            if (!sessionActive) return;
            sessionDurationMs = SystemClock.elapsedRealtime() - sessionStartElapsed;
            updateSessionUi();
            ticker.postDelayed(this, 1000);
        }
    };

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
        tvShakeStatus = findViewById(R.id.tvShakeStatus);
        tvAcceleration = findViewById(R.id.tvAcceleration);

        // ประวัติกิจกรรม (RecyclerView + การ์ด + Dialog รายละเอียด)
        ViewGroup historyContainer = findViewById(R.id.historyContainer);
        historyView = new ActivityHistoryView(this, historyContainer);
        historyContainer.addView(historyView.getView());
        // ข้อ 8: ลบกิจกรรมแล้วให้คำนวณยอดรวมและสถิติสูงสุดใหม่
        historyView.setOnHistoryChangedListener(this::refreshTotals);

        // ระบบ Session (ข้อ 3-5)
        dbHelper = new DBHelper(this);
        btnSession = findViewById(R.id.btnSession);
        tvSessionStatus = findViewById(R.id.tvSessionStatus);
        tvSessionSteps = findViewById(R.id.tvSessionSteps);
        tvSessionShakes = findViewById(R.id.tvSessionShakes);
        tvSessionDuration = findViewById(R.id.tvSessionDuration);
        tvAvgSteps = findViewById(R.id.tvStepsPerMinute);
        tvAvgShakes = findViewById(R.id.tvShakesPerMinute);
        tvTotalSteps = findViewById(R.id.tvTotalSteps);
        tvTotalShakes = findViewById(R.id.tvTotalShakes);
        tvRecord = findViewById(R.id.tvRecord);
        btnSession.setOnClickListener(v -> {
            if (sessionActive) stopSession(); else startSession();
        });

        initSensors();
        updateStepUi();
        updateSessionUi();
        refreshTotals();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startSensors();
        historyView.refresh();
        refreshTotals();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ticker.removeCallbacks(tickTask);
        historyView.close();
        dbHelper.close();
    }

    /** Reload the history list, e.g. after the session logic saves a new session. */
    public void refreshHistory() {
        historyView.refresh();
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
    }

    // =====================================================================
    // Hooks for the session logic — called whenever a sensor value changes
    // =====================================================================

    /** Called on every new TYPE_STEP_COUNTER reading with the raw cumulative value. */
    protected void onStepCountChanged(long rawStepCount) {
        if (!sessionActive) return;

        // ถ้าเริ่ม session ก่อนที่ sensor จะส่งค่าแรกมา ให้ใช้ค่าแรกเป็นฐาน
        // หรือถ้าค่าลดลง (เครื่องรีบูต/ตัวนับรีเซ็ต) ให้เริ่มฐานใหม่
        if (sessionStartSteps < 0 || rawStepCount < sessionStartSteps) {
            sessionStartSteps = rawStepCount;
        }
        sessionSteps = rawStepCount - sessionStartSteps;   // ข้อ 3: session delta
        updateSessionUi();
    }

    /** Called once per detected shake with the shake total since the app opened. */
    protected void onShakeDetected(int shakeCount) {
        if (!sessionActive) return;

        sessionShakes = shakeCount - sessionStartShakes;   // ข้อ 3: session delta
        updateSessionUi();
    }

    // =====================================================================
    // ระบบ Session: ข้อ 3 Session Delta, ข้อ 4 Duration & Average,
    // ข้อ 5 Total & New Record
    // =====================================================================

    /** เริ่มกิจกรรมใหม่: จดค่าฐานจาก sensor ตอนนี้ ยอดรวมไม่ถูกแตะ */
    private void startSession() {
        sessionActive = true;
        sessionStartSteps = rawStepCount;      // -1 ถ้ายังไม่มีค่า (จะตั้งทีหลังใน onStepCountChanged)
        sessionStartShakes = shakeCount;
        sessionSteps = 0;
        sessionShakes = 0;
        sessionStartElapsed = SystemClock.elapsedRealtime();
        sessionDurationMs = 0;

        ticker.removeCallbacks(tickTask);
        ticker.postDelayed(tickTask, 1000);
        updateSessionUi();
    }

    /** จบกิจกรรม: สรุปผล เช็กสถิติสูงสุด บันทึกลงฐานข้อมูล แล้วอัปเดตยอดรวม */
    private void stopSession() {
        if (!sessionActive) return;

        sessionDurationMs = SystemClock.elapsedRealtime() - sessionStartElapsed;
        sessionActive = false;
        ticker.removeCallbacks(tickTask);

        long durationSeconds = sessionDurationMs / 1000;

        // ข้อ 5: เทียบสถิติสูงสุด "ก่อน" บันทึก session นี้ลงฐานข้อมูล
        DBHelper.SessionStats before = dbHelper.getStatistics();
        boolean newStepRecord = sessionSteps > 0 && sessionSteps > before.maxSteps;
        boolean newShakeRecord = sessionShakes > 0 && sessionShakes > before.maxShakes;

        long id = dbHelper.insertSession((int) sessionSteps, sessionShakes,
                durationSeconds, System.currentTimeMillis());
        if (id < 0) {
            Log.w(TAG, "Failed to save session");
            Toast.makeText(this, R.string.session_save_failed, Toast.LENGTH_LONG).show();
        } else if (newStepRecord || newShakeRecord) {
            Toast.makeText(this, R.string.new_record_toast, Toast.LENGTH_LONG).show();
        }

        updateSessionUi();
        refreshTotals();
        refreshHistory();
    }

    /** ข้อ 4: ค่าเฉลี่ยต่อนาที = จำนวน / ระยะเวลา (นาที) กันหารด้วยศูนย์ */
    private double perMinute(long count) {
        if (sessionDurationMs <= 0) return 0.0;
        return count * 60000.0 / sessionDurationMs;
    }

    private String formatDuration(long ms) {
        long totalSec = ms / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d",
                totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60);
    }

    /**
     * ข้อ 5: โหลดยอดรวมและสถิติสูงสุดจากฐานข้อมูลมาแสดงใหม่
     * เรียกเมื่อเปิดแอป จบ session หรือหลังลบประวัติ (ให้ฝั่งลบเรียกเมธอดนี้ด้วย)
     */
    public void refreshTotals() {
        DBHelper.SessionStats stats = dbHelper.getStatistics();
        tvTotalSteps.setText(getString(R.string.total_steps_format, stats.totalSteps));
        tvTotalShakes.setText(getString(R.string.total_shakes_format, stats.totalShakes));
        tvRecord.setText(getString(R.string.record_format, stats.maxSteps, stats.maxShakes));
    }

    private void updateSessionUi() {
        btnSession.setText(sessionActive ? R.string.session_stop : R.string.session_start);
        tvSessionStatus.setText(sessionActive
                ? R.string.session_status_running : R.string.session_status_idle);
        tvSessionSteps.setText(String.valueOf(sessionSteps));
        tvSessionShakes.setText(String.valueOf(sessionShakes));
        tvSessionDuration.setText(formatDuration(sessionDurationMs));
        tvAvgSteps.setText(String.format(Locale.US, "%.1f", perMinute(sessionSteps)));
        tvAvgShakes.setText(String.format(Locale.US, "%.1f", perMinute(sessionShakes)));
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
    }

}

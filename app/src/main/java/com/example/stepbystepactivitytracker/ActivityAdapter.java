
package com.example.stepbystepactivitytracker;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ActivityAdapter
        extends RecyclerView.Adapter<ActivityAdapter.ViewHolder> {

    private final Context context;
    private final DBHelper dbHelper;
    private final Runnable onDeleted;
    private final ArrayList<Session> sessions = new ArrayList<>();

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM HH:mm", Locale.ENGLISH);

    private final SimpleDateFormat fullDateFormat =
            new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.ENGLISH);

    public static class Session {
        public long id;
        public int steps;
        public int shakes;
        public long duration;
        public long timestamp;
        public boolean stepRecord;
        public boolean shakeRecord;
    }

    public ActivityAdapter(Context context, DBHelper dbHelper, Runnable onDeleted) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.onDeleted = onDeleted;
    }

    // ดึงข้อมูล Cursor จาก SQLite
    public void updateSessions(Cursor cursor) {
        sessions.clear();

        if (cursor != null) {
            int idCol = cursor.getColumnIndexOrThrow("id");
            int stepsCol = cursor.getColumnIndexOrThrow("steps");
            int shakesCol = cursor.getColumnIndexOrThrow("shakes");
            int durationCol = cursor.getColumnIndexOrThrow("duration_seconds");
            int timeCol = cursor.getColumnIndexOrThrow("timestamp_ms");

            while (cursor.moveToNext()) {
                Session s = new Session();
                s.id = cursor.getLong(idCol);
                s.steps = cursor.getInt(stepsCol);
                s.shakes = cursor.getInt(shakesCol);
                s.duration = cursor.getLong(durationCol);
                s.timestamp = cursor.getLong(timeCol);
                sessions.add(s);
            }
        }

        // คำนวณ New Record จากอดีตไปปัจจุบัน
        int maxSteps = 0;
        int maxShakes = 0;

        for (int i = sessions.size() - 1; i >= 0; i--) {
            Session s = sessions.get(i);

            s.stepRecord = s.steps > maxSteps;
            s.shakeRecord = s.shakes > maxShakes;

            maxSteps = Math.max(maxSteps, s.steps);
            maxShakes = Math.max(maxShakes, s.shakes);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_activity, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder, int position) {

        Session s = sessions.get(position);

        // วันที่
        holder.tvDate.setText(
                dateFormat.format(new Date(s.timestamp)));

        // จำนวนก้าว เขย่า และระยะเวลา
        holder.tvDetails.setText(
                s.steps + " steps • " +
                        s.shakes + " shakes • " +
                        formatDuration(s.duration));

        // ค่าเฉลี่ยก้าวต่อนาที
        holder.tvAverage.setText(
                String.format(Locale.ENGLISH,
                        "%.1f steps/min",
                        calculateAverage(s)));

        // แสดงป้ายสถิติใหม่บนการ์ด
        holder.tvStepRecord.setVisibility(
                s.stepRecord ? View.VISIBLE : View.GONE);

        holder.tvShakeRecord.setVisibility(
                s.shakeRecord ? View.VISIBLE : View.GONE);

        // กดดูรายละเอียดกิจกรรม
        holder.itemView.setOnClickListener(v -> showDetails(s));
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    private double calculateAverage(Session s) {
        if (s.duration <= 0) {
            return 0.0;
        }
        return s.steps * 60.0 / s.duration;
    }

    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long remaining = seconds % 60;

        return String.format(Locale.ENGLISH,
                "%02d:%02d", minutes, remaining);
    }

    // เปิด Dialog รายละเอียดกิจกรรม
    private void showDetails(Session s) {

        View view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_activity_detail, null);

        TextView tvDate =
                view.findViewById(R.id.tvDetailDate);

        TextView tvSteps =
                view.findViewById(R.id.tvDetailSteps);

        TextView tvShakes =
                view.findViewById(R.id.tvDetailShakes);

        TextView tvDuration =
                view.findViewById(R.id.tvDetailDuration);

        TextView tvAverage =
                view.findViewById(R.id.tvDetailAverage);

        TextView tvStepRecord =
                view.findViewById(R.id.tvDetailStepRecord);

        TextView tvShakeRecord =
                view.findViewById(R.id.tvDetailShakeRecord);

        Button btnClose =
                view.findViewById(R.id.btnCloseDetail);

        Button btnDelete =
                view.findViewById(R.id.btnDeleteDetail);

        // แสดงข้อมูลจริงจาก SQLite
        tvDate.setText(
                fullDateFormat.format(new Date(s.timestamp)));

        tvSteps.setText("ก้าวเดิน: " + s.steps + " ก้าว");

        tvShakes.setText("เขย่า: " + s.shakes + " ครั้ง");

        tvDuration.setText(
                "ระยะเวลา: " + formatDuration(s.duration));

        tvAverage.setText(
                String.format(Locale.ENGLISH,
                        "เฉลี่ย: %.1f ก้าว/นาที",
                        calculateAverage(s)));

        // แสดง New Record เฉพาะกิจกรรมที่ทำสถิติใหม่
        tvStepRecord.setVisibility(
                s.stepRecord ? View.VISIBLE : View.GONE);

        tvShakeRecord.setVisibility(
                s.shakeRecord ? View.VISIBLE : View.GONE);

        // สร้างหน้าต่าง Dialog
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // ข้อ 8: ลบกิจกรรม (ถามยืนยันก่อน) แล้วโหลดรายการและยอดรวมใหม่
        btnDelete.setOnClickListener(v -> new AlertDialog.Builder(context)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, which) -> {
                    if (dbHelper.deleteSession(s.id)) {
                        dialog.dismiss();
                        onDeleted.run();
                    } else {
                        Toast.makeText(context, R.string.delete_failed,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show());

        dialog.show();
    }

    public static class ViewHolder
            extends RecyclerView.ViewHolder {

        TextView tvDate;
        TextView tvDetails;
        TextView tvAverage;
        TextView tvStepRecord;
        TextView tvShakeRecord;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            tvDate = itemView.findViewById(R.id.tvDate);
            tvDetails = itemView.findViewById(R.id.tvDetails);
            tvAverage = itemView.findViewById(R.id.tvAverage);
            tvStepRecord = itemView.findViewById(R.id.tvStepRecord);
            tvShakeRecord = itemView.findViewById(R.id.tvShakeRecord);
        }
    }
}

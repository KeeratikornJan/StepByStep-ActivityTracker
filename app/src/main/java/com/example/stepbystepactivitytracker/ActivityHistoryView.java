
package com.example.stepbystepactivitytracker;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ActivityHistoryView {

    private final DBHelper dbHelper;
    private final ActivityAdapter adapter;
    private final View rootView;
    private final RecyclerView recyclerView;
    private final TextView tvEmptyHistory;
    private Runnable onHistoryChanged;

    public ActivityHistoryView(Context context, ViewGroup parent) {

        dbHelper = new DBHelper(context);
        adapter = new ActivityAdapter(context, dbHelper, () -> {
            refresh();
            if (onHistoryChanged != null) onHistoryChanged.run();
        });

        rootView = LayoutInflater.from(context)
                .inflate(R.layout.layout_activity_history, parent, false);

        recyclerView = rootView.findViewById(
                R.id.recyclerActivityHistory);

        tvEmptyHistory = rootView.findViewById(
                R.id.tvEmptyHistory);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(context));

        recyclerView.setAdapter(adapter);
    }

    /** Called after a session is deleted, so the screen can refresh totals/records. */
    public void setOnHistoryChangedListener(Runnable listener) {
        onHistoryChanged = listener;
    }

    public View getView() {
        return rootView;
    }

    public void refresh() {
        try (Cursor cursor = dbHelper.getAllSessions()) {
            adapter.updateSessions(cursor);
        }

        boolean isEmpty = adapter.isEmpty();

        recyclerView.setVisibility(
                isEmpty ? View.GONE : View.VISIBLE);

        tvEmptyHistory.setVisibility(
                isEmpty ? View.VISIBLE : View.GONE);
    }

    public void close() {
        dbHelper.close();
    }
}

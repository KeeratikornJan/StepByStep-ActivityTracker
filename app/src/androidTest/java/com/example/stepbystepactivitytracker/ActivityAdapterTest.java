package com.example.stepbystepactivitytracker;

import android.content.Context;
import android.database.MatrixCursor;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Item 9: RecyclerView list rows and the New Record badges, without touching the real DB. */
@RunWith(AndroidJUnit4.class)
public class ActivityAdapterTest {

    private static final String[] COLUMNS =
            {"id", "steps", "shakes", "duration_seconds", "timestamp_ms"};

    private Context context;
    private ActivityAdapter adapter;

    @Before
    public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context = new ContextThemeWrapper(target, R.style.Theme_StepByStepActivityTracker);
        // The adapter only touches the database when a delete is confirmed, so null is fine here.
        adapter = new ActivityAdapter(context, null, () -> { });
    }

    /** Rows are given newest-first, the same order DBHelper.getAllSessions() returns. */
    private MatrixCursor cursor(Object[]... rows) {
        MatrixCursor c = new MatrixCursor(COLUMNS);
        for (Object[] r : rows) c.addRow(r);
        return c;
    }

    private ActivityAdapter.ViewHolder bind(int position) {
        final ActivityAdapter.ViewHolder[] holder = new ActivityAdapter.ViewHolder[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            FrameLayout parent = new FrameLayout(context);
            holder[0] = adapter.onCreateViewHolder(parent, 0);
            adapter.onBindViewHolder(holder[0], position);
        });
        return holder[0];
    }

    private static boolean visible(TextView v) {
        return v.getVisibility() == View.VISIBLE;
    }

    @Test
    public void emptyCursorGivesEmptyAdapter() {
        adapter.updateSessions(cursor());

        assertTrue(adapter.isEmpty());
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void nullCursorGivesEmptyAdapter() {
        adapter.updateSessions(null);

        assertTrue(adapter.isEmpty());
    }

    @Test
    public void itemCountMatchesRows() {
        adapter.updateSessions(cursor(
                new Object[]{2L, 20, 8, 60L, 2000L},
                new Object[]{1L, 10, 5, 30L, 1000L}));

        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void rowShowsStepsShakesDurationAndAverage() {
        adapter.updateSessions(cursor(new Object[]{1L, 120, 4, 90L, 1000L}));

        ActivityAdapter.ViewHolder h = bind(0);

        assertEquals("120 steps • 4 shakes • 01:30", h.tvDetails.getText().toString());
        // 120 steps in 90 seconds = 80.0 steps/min
        assertEquals("80.0 steps/min", h.tvAverage.getText().toString());
    }

    @Test
    public void zeroDurationShowsZeroAverageInsteadOfCrashing() {
        adapter.updateSessions(cursor(new Object[]{1L, 50, 2, 0L, 1000L}));

        assertEquals("0.0 steps/min", bind(0).tvAverage.getText().toString());
    }

    @Test
    public void firstSessionEverIsARecord() {
        adapter.updateSessions(cursor(new Object[]{1L, 10, 5, 30L, 1000L}));

        ActivityAdapter.ViewHolder h = bind(0);

        assertTrue(visible(h.tvStepRecord));
        assertTrue(visible(h.tvShakeRecord));
    }

    @Test
    public void onlySessionsThatBeatThePastGetBadges() {
        // newest first: 3rd (5 steps) beats nothing, 2nd (20) beats 10, 1st oldest (10)
        adapter.updateSessions(cursor(
                new Object[]{3L, 5, 1, 30L, 3000L},
                new Object[]{2L, 20, 1, 30L, 2000L},
                new Object[]{1L, 10, 9, 30L, 1000L}));

        // position 0 = newest: 5 steps < 20 -> no step badge, 1 shake < 9 -> no shake badge
        ActivityAdapter.ViewHolder newest = bind(0);
        assertEquals(View.GONE, newest.tvStepRecord.getVisibility());
        assertEquals(View.GONE, newest.tvShakeRecord.getVisibility());

        // position 1: 20 steps beat 10 -> step badge; 1 shake < 9 -> none
        ActivityAdapter.ViewHolder middle = bind(1);
        assertTrue(visible(middle.tvStepRecord));
        assertEquals(View.GONE, middle.tvShakeRecord.getVisibility());

        // position 2 = oldest: always a record
        ActivityAdapter.ViewHolder oldest = bind(2);
        assertTrue(visible(oldest.tvStepRecord));
        assertTrue(visible(oldest.tvShakeRecord));
    }

    @Test
    public void tiedValueIsNotANewRecord() {
        adapter.updateSessions(cursor(
                new Object[]{2L, 10, 5, 30L, 2000L},
                new Object[]{1L, 10, 5, 30L, 1000L}));

        ActivityAdapter.ViewHolder newer = bind(0);

        assertEquals(View.GONE, newer.tvStepRecord.getVisibility());
        assertEquals(View.GONE, newer.tvShakeRecord.getVisibility());
    }

    @Test
    public void zeroValuesNeverGetABadge() {
        adapter.updateSessions(cursor(new Object[]{1L, 0, 0, 30L, 1000L}));

        ActivityAdapter.ViewHolder h = bind(0);

        assertEquals(View.GONE, h.tvStepRecord.getVisibility());
        assertEquals(View.GONE, h.tvShakeRecord.getVisibility());
    }

    @Test
    public void badgesAreRecalculatedWhenTheBestSessionIsRemoved() {
        adapter.updateSessions(cursor(
                new Object[]{2L, 500, 1, 30L, 2000L},
                new Object[]{1L, 10, 1, 30L, 1000L}));
        // After deleting the 500-step session only the 10-step one remains.
        adapter.updateSessions(cursor(new Object[]{1L, 10, 1, 30L, 1000L}));

        assertEquals(1, adapter.getItemCount());
        assertTrue(visible(bind(0).tvStepRecord));
    }
}

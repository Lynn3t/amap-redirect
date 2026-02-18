package com.example.amapredirect;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class LogActivity extends Activity {

    private TextView logView;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Redirect Log");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Button bar
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(dp(12), dp(8), dp(12), dp(4));

        Button refreshBtn = new Button(this);
        refreshBtn.setText("Refresh");
        refreshBtn.setOnClickListener(v -> loadLog());

        Button clearBtn = new Button(this);
        clearBtn.setText("Clear");
        clearBtn.setOnClickListener(v -> {
            RedirectLog.clear();
            loadLog();
        });

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnParams.setMargins(dp(4), 0, dp(4), 0);
        buttons.addView(refreshBtn, btnParams);
        buttons.addView(clearBtn, btnParams);
        root.addView(buttons);

        // Log content
        scrollView = new ScrollView(this);
        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(11f);
        logView.setPadding(dp(12), dp(8), dp(12), dp(8));
        logView.setTextColor(Color.parseColor("#E0E0E0"));
        scrollView.setBackgroundColor(Color.parseColor("#1E1E1E"));
        scrollView.addView(logView);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
        loadLog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLog();
    }

    private void loadLog() {
        logView.setText(RedirectLog.read());
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}

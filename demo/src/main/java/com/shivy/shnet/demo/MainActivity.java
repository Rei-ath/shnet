package com.shivy.shnet.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.shivy.shnet.ShnetLinks;
import com.shivy.shnet.ShnetQr;
import com.shivy.shnet.ShnetRuntime;
import com.shivy.shnet.ShnetService;

import java.util.List;

public class MainActivity extends Activity {
    private TextView statusText;
    private TextView linkText;
    private ImageView qrView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        linkText = findViewById(R.id.linkText);
        qrView = findViewById(R.id.qrView);

        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);

        startButton.setOnClickListener(v -> startNode());
        stopButton.setOnClickListener(v -> stopNode());

        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
    }

    private void startNode() {
        Intent intent = new Intent(this, DemoService.class);
        intent.setAction(ShnetService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        updateUi();
    }

    private void stopNode() {
        Intent intent = new Intent(this, DemoService.class);
        intent.setAction(ShnetService.ACTION_STOP);
        startService(intent);
        updateUi();
    }

    private void updateUi() {
        boolean running = ShnetRuntime.isRunning(this, DemoService.class);
        statusText.setText(running ? "Status: online" : "Status: offline");
        if (!running) {
            linkText.setText("Link: -");
            qrView.setImageDrawable(null);
            return;
        }

        List<ShnetLinks.ShnetLink> links = ShnetLinks.getActiveLinks(this, DemoService.PORT);
        if (links.isEmpty()) {
            linkText.setText("Link: -");
            qrView.setImageDrawable(null);
            return;
        }
        String url = links.get(0).url;
        linkText.setText("Link: " + url);
        try {
            qrView.setImageBitmap(ShnetQr.createQrBitmap(url, 420));
        } catch (Exception e) {
            qrView.setImageDrawable(null);
            Toast.makeText(this, "QR failed", Toast.LENGTH_SHORT).show();
        }
    }
}

package com.lineart.gen;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/** 裁剪界面：在图上自由选区，确认后裁剪并进入线稿对比 */
public class CropActivity extends Activity {

    private static final int MAX_DECODE = 2048;

    private CropView cropView;
    private ProgressBar progress;
    private Uri sourceUri;
    private Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        cropView = findViewById(R.id.crop_view);
        progress = findViewById(R.id.crop_progress);
        sourceUri = Uri.parse(getIntent().getStringExtra("uri"));

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btn_reset_crop).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cropView.resetCrop(); }
        });
        findViewById(R.id.btn_use_full).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startCompare(sourceUri.toString()); }
        });
        findViewById(R.id.btn_use_crop).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { useCropped(); }
        });

        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                try {
                    bitmap = BitmapUtils.decode(CropActivity.this, sourceUri, MAX_DECODE);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            progress.setVisibility(View.GONE);
                            cropView.setBitmap(bitmap);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            Toast.makeText(CropActivity.this, "图片读取失败", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                }
            }
        });
    }

    private void useCropped() {
        if (bitmap == null) return;
        progress.setVisibility(View.VISIBLE);
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                try {
                    Bitmap cropped = cropView.getCroppedBitmap();
                    File f = new File(getCacheDir(), "cropped.png");
                    FileOutputStream fos = new FileOutputStream(f);
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();
                    if (cropped != bitmap) cropped.recycle();
                    final Uri uri = Uri.fromFile(f);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            progress.setVisibility(View.GONE);
                            startCompare(uri.toString());
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            progress.setVisibility(View.GONE);
                            Toast.makeText(CropActivity.this, "裁剪失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void startCompare(String uri) {
        Intent it = new Intent(this, CompareActivity.class);
        it.putExtra("uri", uri);
        startActivity(it);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bitmap != null) bitmap.recycle();
    }
}

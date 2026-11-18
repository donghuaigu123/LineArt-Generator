package com.lineart.gen;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.lineart.gen.core.LineArtEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 编辑 / 对比界面：同尺寸对比 + 参数实时调节 + 保存 / 分享 */
public class CompareActivity extends Activity {

    private static final int PREVIEW_MAX = 1400;
    private static final int SAVE_MAX = 2600;
    private static final int REQ_PERM = 2001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Uri sourceUri;
    private Bitmap originPreview;
    private Bitmap resultPreview;
    private LineArtEngine.Params params = new LineArtEngine.Params();
    private final AtomicInteger taskSeq = new AtomicInteger(0);
    private final AtomicReference<LineArtEngine.Params> pending = new AtomicReference<>();
    private final Runnable recomputeTask = new Runnable() {
        @Override public void run() {
            LineArtEngine.Params p = pending.getAndSet(null);
            if (p != null) processAsync(p);
        }
    };

    private CompareView compareView;
    private TextView txtProcessing, txtSize;
    private TextView tvDetail, tvInk, tvThickness, tvClean;
    private SeekBar seekDetail, seekInk, seekThickness, seekClean;
    private CheckBox cbDark, cbInvert;
    private boolean applyingDefaults = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare);

        compareView = findViewById(R.id.compare_view);
        txtProcessing = findViewById(R.id.txt_processing);
        txtSize = findViewById(R.id.txt_size);
        tvDetail = findViewById(R.id.tv_detail);
        tvInk = findViewById(R.id.tv_ink);
        tvThickness = findViewById(R.id.tv_thickness);
        tvClean = findViewById(R.id.tv_clean);
        seekDetail = findViewById(R.id.seek_detail);
        seekInk = findViewById(R.id.seek_ink);
        seekThickness = findViewById(R.id.seek_thickness);
        seekClean = findViewById(R.id.seek_clean);
        cbDark = findViewById(R.id.cb_dark);
        cbInvert = findViewById(R.id.cb_invert);
        RadioGroup modeGroup = findViewById(R.id.mode_group);
        Button btnSave = findViewById(R.id.btn_save);
        Button btnShare = findViewById(R.id.btn_share);
        TextView btnReset = findViewById(R.id.btn_reset);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        sourceUri = Uri.parse(getIntent().getStringExtra("uri"));

        // ---------- 加载原图并首次生成 ----------
        txtProcessing.setVisibility(View.VISIBLE);
        executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    originPreview = BitmapUtils.decode(CompareActivity.this, sourceUri, PREVIEW_MAX);
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            Toast.makeText(CompareActivity.this, "图片读取失败", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                    return;
                }
                final int[] out = LineArtEngine.process(BitmapUtils.toPixels(originPreview),
                        originPreview.getWidth(), originPreview.getHeight(), copyParams());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        resultPreview = BitmapUtils.fromPixels(out, originPreview.getWidth(), originPreview.getHeight());
                        compareView.setImages(originPreview, resultPreview);
                        txtProcessing.setVisibility(View.GONE);
                        updateSizeLabel();
                    }
                });
            }
        });

        // ---------- 模式切换 ----------
        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (applyingDefaults) return;
                applyModeDefaults(modeFromId(checkedId));
                syncControls();
                scheduleRecompute();
            }
        });
        modeGroup.check(R.id.mode_standard);

        // ---------- 滑杆 ----------
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int v, boolean fromUser) {
                if (fromUser) {
                    readControls();
                    updateLabels();
                    scheduleRecompute();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        };
        seekDetail.setOnSeekBarChangeListener(listener);
        seekInk.setOnSeekBarChangeListener(listener);
        seekThickness.setOnSeekBarChangeListener(listener);
        seekClean.setOnSeekBarChangeListener(listener);

        CompoundButton.OnCheckedChangeListener cbListener = new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) {
                readControls();
                scheduleRecompute();
            }
        };
        cbDark.setOnCheckedChangeListener(cbListener);
        cbInvert.setOnCheckedChangeListener(cbListener);

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                applyModeDefaults(params.mode);
                syncControls();
                scheduleRecompute();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveFull(false); }
        });
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveFull(true); }
        });

        syncControls();
    }

    // ---------------- 参数管理 ----------------

    private int modeFromId(int id) {
        if (id == R.id.mode_fine) return LineArtEngine.MODE_FINE;
        if (id == R.id.mode_bold) return LineArtEngine.MODE_BOLD;
        if (id == R.id.mode_sketch) return LineArtEngine.MODE_SKETCH;
        if (id == R.id.mode_stencil) return LineArtEngine.MODE_STENCIL;
        return LineArtEngine.MODE_STANDARD;
    }

    /** 各模式的推荐默认参数 */
    private void applyModeDefaults(int mode) {
        params.mode = mode;
        switch (mode) {
            case LineArtEngine.MODE_FINE:
                params.detail = 72; params.ink = 55; params.thickness = 1; params.clean = 60; break;
            case LineArtEngine.MODE_BOLD:
                params.detail = 40; params.ink = 38; params.thickness = 1; params.clean = 60; break;
            case LineArtEngine.MODE_SKETCH:
                params.detail = 65; params.ink = 55; params.thickness = 0; params.clean = 45; break;
            case LineArtEngine.MODE_STENCIL:
                params.detail = 50; params.ink = 35; params.thickness = 0; params.clean = 50; break;
            default:
                params.detail = 55; params.ink = 50; params.thickness = 1; params.clean = 50; break;
        }
    }

    private void readControls() {
        params.detail = Math.max(1, seekDetail.getProgress());
        params.ink = Math.max(1, seekInk.getProgress());
        params.thickness = seekThickness.getProgress();
        params.clean = seekClean.getProgress();
        params.darkBgSuppression = cbDark.isChecked();
        params.invert = cbInvert.isChecked();
    }

    private void syncControls() {
        applyingDefaults = true;
        seekDetail.setProgress(params.detail);
        seekInk.setProgress(params.ink);
        seekThickness.setProgress(params.thickness);
        seekClean.setProgress(params.clean);
        cbDark.setChecked(params.darkBgSuppression);
        cbInvert.setChecked(params.invert);
        applyingDefaults = false;
        updateLabels();
    }

    private void updateLabels() {
        tvDetail.setText("细节保留 " + params.detail);
        tvInk.setText("线条浓度 " + params.ink);
        tvThickness.setText("线条粗细 " + params.thickness);
        tvClean.setText("背景净化 " + params.clean);
    }

    private LineArtEngine.Params copyParams() {
        LineArtEngine.Params p = new LineArtEngine.Params();
        p.mode = params.mode; p.detail = params.detail; p.ink = params.ink;
        p.thickness = params.thickness; p.clean = params.clean;
        p.invert = params.invert; p.darkBgSuppression = params.darkBgSuppression;
        return p;
    }

    private void updateSizeLabel() {
        if (originPreview != null)
            txtSize.setText(originPreview.getWidth() + "×" + originPreview.getHeight());
    }

    // ---------------- 实时重算（防抖） ----------------

    private void scheduleRecompute() {
        pending.set(copyParams());
        main.removeCallbacks(recomputeTask);
        main.postDelayed(recomputeTask, 220);
    }

    private void processAsync(final LineArtEngine.Params p) {
        if (originPreview == null) return;
        final int seq = taskSeq.incrementAndGet();
        txtProcessing.setVisibility(View.VISIBLE);
        executor.execute(new Runnable() {
            @Override public void run() {
                final int[] out = LineArtEngine.process(BitmapUtils.toPixels(originPreview),
                        originPreview.getWidth(), originPreview.getHeight(), p);
                if (seq != taskSeq.get()) return; // 已有更新任务
                final Bitmap bmp = BitmapUtils.fromPixels(out, originPreview.getWidth(), originPreview.getHeight());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (seq != taskSeq.get()) return;
                        if (resultPreview != null) resultPreview.recycle();
                        resultPreview = bmp;
                        compareView.setLineart(resultPreview);
                        txtProcessing.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    // ---------------- 全分辨率保存 / 分享 ----------------

    private void saveFull(final boolean share) {
        txtProcessing.setText(R.string.processing_full);
        txtProcessing.setVisibility(View.VISIBLE);
        final int seq = taskSeq.incrementAndGet();
        final LineArtEngine.Params p = copyParams();
        // 按设备可用堆内存自适应保存分辨率，防止 OOM
        int saveMax = SAVE_MAX;
        long heap = Runtime.getRuntime().maxMemory();
        if (heap < 300L * 1024 * 1024) saveMax = 1800;
        if (heap < 200L * 1024 * 1024) saveMax = 1400;
        final int finalSaveMax = saveMax;
        executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    Bitmap full = BitmapUtils.decode(CompareActivity.this, sourceUri, finalSaveMax);
                    int[] out = LineArtEngine.process(BitmapUtils.toPixels(full),
                            full.getWidth(), full.getHeight(), p);
                    Bitmap result = BitmapUtils.fromPixels(out, full.getWidth(), full.getHeight());
                    full.recycle();
                    String name = "lineart_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                            .format(new Date()) + ".png";
                    Uri saved;
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                        cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LineArt");
                        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                        if (uri == null) throw new IllegalStateException("无法写入相册");
                        OutputStream os = getContentResolver().openOutputStream(uri);
                        result.compress(Bitmap.CompressFormat.PNG, 100, os);
                        os.close();
                        saved = uri;
                    } else {
                        File dir = new File(Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES), "LineArt");
                        if (!dir.exists()) dir.mkdirs();
                        File f = new File(dir, name);
                        FileOutputStream fos = new FileOutputStream(f);
                        result.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                        // 广播扫描
                        android.content.Intent scan = new android.content.Intent(
                                android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                Uri.fromFile(f));
                        sendBroadcast(scan);
                        saved = Uri.fromFile(f);
                    }
                    if (share) {
                        // 统一走 FileProvider，避免 file:// URI 限制
                        File shared = new File(getCacheDir(), "shared");
                        if (!shared.exists()) shared.mkdirs();
                        File f = new File(shared, name);
                        FileOutputStream fos = new FileOutputStream(f);
                        result.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.close();
                        final Uri shareUri = Uri.parse("content://" + MiniFileProvider.AUTHORITY + "/" + name);
                        result.recycle();
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                txtProcessing.setVisibility(View.GONE);
                                txtProcessing.setText(R.string.generating);
                                Intent it = new Intent(Intent.ACTION_SEND);
                                it.setType("image/png");
                                it.putExtra(Intent.EXTRA_STREAM, shareUri);
                                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                startActivity(Intent.createChooser(it, getString(R.string.share)));
                            }
                        });
                    } else {
                        result.recycle();
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                txtProcessing.setVisibility(View.GONE);
                                txtProcessing.setText(R.string.generating);
                                Toast.makeText(CompareActivity.this,
                                        getString(R.string.saved_to) + " Pictures/LineArt/" + name,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                } catch (final OutOfMemoryError oom) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            txtProcessing.setVisibility(View.GONE);
                            txtProcessing.setText(R.string.generating);
                            Toast.makeText(CompareActivity.this,
                                    "内存不足，请重试（将自动降低分辨率）", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            txtProcessing.setVisibility(View.GONE);
                            txtProcessing.setText(R.string.generating);
                            Toast.makeText(CompareActivity.this,
                                    getString(R.string.save_failed) + "：" + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (originPreview != null) originPreview.recycle();
        if (resultPreview != null) resultPreview.recycle();
    }
}

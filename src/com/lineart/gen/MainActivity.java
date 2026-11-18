package com.lineart.gen;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** 主界面：导入图片 → 裁剪选区 → 生成线稿（傻瓜式三步操作） */
public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;

    private ImageView imgPreview;
    private TextView txtHint, txtGenerating;
    private ProgressBar progress;
    private Button btnGenerate;
    /** 复制到应用内部存储后的 file URI（跨 Activity 传递无权限问题） */
    private Uri pickedFileUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgPreview = findViewById(R.id.img_preview);
        txtHint = findViewById(R.id.txt_hint);
        txtGenerating = findViewById(R.id.txt_generating);
        progress = findViewById(R.id.progress);
        btnGenerate = findViewById(R.id.btn_generate);
        Button btnPick = findViewById(R.id.btn_pick);

        btnPick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("image/*");
                startActivityForResult(Intent.createChooser(it, getString(R.string.pick_image)), REQ_PICK);
            }
        });

        btnGenerate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (pickedFileUri == null) return;
                Intent it = new Intent(MainActivity.this, CropActivity.class);
                it.putExtra("uri", pickedFileUri.toString());
                startActivity(it);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            final Uri sourceUri = data.getData();
            txtHint.setVisibility(View.GONE);
            imgPreview.setImageURI(null);
            setPreparing(true);
            // 复制到内部存储，规避 content URI 跨 Activity 权限问题
            AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                @Override public void run() {
                    File f = new File(getCacheDir(), "origin.png");
                    boolean ok = false;
                    String err = null;
                    try {
                        InputStream in = getContentResolver().openInputStream(sourceUri);
                        if (in == null) throw new IllegalStateException("无法打开图片流");
                        FileOutputStream out = new FileOutputStream(f);
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        out.flush();
                        out.close();
                        in.close();
                        ok = true;
                    } catch (Exception e) {
                        err = e.getMessage();
                    }
                    final boolean success = ok;
                    final String error = err;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setPreparing(false);
                            if (success) {
                                pickedFileUri = Uri.fromFile(f);
                                imgPreview.setImageURI(pickedFileUri);
                                btnGenerate.setEnabled(true);
                                btnGenerate.setAlpha(1f);
                                btnGenerate.setText(R.string.crop_title);
                            } else {
                                Toast.makeText(MainActivity.this,
                                        "图片读取失败：" + (error == null ? "未知错误" : error),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            });
        }
    }

    private void setPreparing(boolean preparing) {
        progress.setVisibility(preparing ? View.VISIBLE : View.GONE);
        txtGenerating.setVisibility(preparing ? View.VISIBLE : View.GONE);
        btnGenerate.setEnabled(!preparing && pickedFileUri != null);
    }
}

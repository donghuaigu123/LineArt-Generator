package com.lineart.gen;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/** 主界面：导入图片 → 裁剪选区 → 一键生成线稿（傻瓜式三步操作） */
public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;

    private ImageView imgPreview;
    private TextView txtHint;
    private Button btnGenerate;
    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgPreview = findViewById(R.id.img_preview);
        txtHint = findViewById(R.id.txt_hint);
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
                if (imageUri == null) return;
                Intent it = new Intent(MainActivity.this, CropActivity.class);
                it.putExtra("uri", imageUri.toString());
                startActivity(it);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            // 持久化读取权限
            try {
                getContentResolver().takePersistableUriPermission(imageUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            txtHint.setVisibility(View.GONE);
            imgPreview.setImageURI(imageUri);
            btnGenerate.setEnabled(true);
            btnGenerate.setAlpha(1f);
            btnGenerate.setText(R.string.crop_title);
        }
    }
}

package com.lineart.gen;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** 极简 FileProvider（无需依赖 androidx），authority: com.lineart.gen.fileprovider */
public class MiniFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.lineart.gen.fileprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String rel = uri.getPath();
        if (rel == null || rel.startsWith("/")) rel = rel.substring(1);
        File root = new File(getContext().getCacheDir(), "shared");
        File f = new File(root, rel);
        // 防目录穿越
        try {
            if (!f.getCanonicalPath().startsWith(root.getCanonicalPath())) {
                throw new FileNotFoundException(uri.toString());
            }
        } catch (Exception e) {
            throw new FileNotFoundException(uri.toString());
        }
        int m = ParcelFileDescriptor.MODE_READ_ONLY;
        if (mode != null && mode.contains("w")) m = ParcelFileDescriptor.MODE_READ_WRITE;
        return ParcelFileDescriptor.open(f, m);
    }

    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}

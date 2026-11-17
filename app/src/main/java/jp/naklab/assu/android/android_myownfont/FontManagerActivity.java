package jp.naklab.assu.android.android_myownfont;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FontManagerActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private TextView textEmpty;
    private FontAdapter adapter;
    private final List<FontItem> fontList = new ArrayList<>();

    public static class FontItem {
        public String name;
        public File ttfFile;
        public File otfFile;
        public File svgFile;
        public long lastModified;
        public long totalSize;

        public FontItem(String name) {
            this.name = name;
        }

        public String getFormatsString() {
            List<String> formats = new ArrayList<>();
            if (ttfFile != null && ttfFile.exists()) formats.add("TTF");
            if (otfFile != null && otfFile.exists()) formats.add("OTF");
            if (svgFile != null && svgFile.exists()) formats.add("SVG");
            return String.join(" · ", formats);
        }

        public File getPrimaryFontFile() {
            if (ttfFile != null && ttfFile.exists()) return ttfFile;
            if (otfFile != null && otfFile.exists()) return otfFile;
            return svgFile;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_font_manager);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_manager);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_fonts);
        textEmpty = findViewById(R.id.text_empty_fonts);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FontAdapter(fontList);
        recyclerView.setAdapter(adapter);

        loadFonts();
    }

    private void loadFonts() {
        fontList.clear();
        File fontsDir = FontRepository.getFontsDir(this);
        if (fontsDir.exists() && fontsDir.isDirectory()) {
            File[] files = fontsDir.listFiles();
            if (files != null) {
                Map<String, FontItem> itemMap = new HashMap<>();
                for (File f : files) {
                    String fileName = f.getName();
                    int dotIdx = fileName.lastIndexOf('.');
                    if (dotIdx > 0) {
                        String baseName = fileName.substring(0, dotIdx);
                        String ext = fileName.substring(dotIdx + 1).toLowerCase();

                        FontItem item = itemMap.get(baseName);
                        if (item == null) {
                            item = new FontItem(baseName);
                            itemMap.put(baseName, item);
                        }

                        if ("ttf".equals(ext)) item.ttfFile = f;
                        else if ("otf".equals(ext)) item.otfFile = f;
                        else if ("svg".equals(ext)) item.svgFile = f;

                        item.totalSize += f.length();
                        if (f.lastModified() > item.lastModified) {
                            item.lastModified = f.lastModified();
                        }
                    }
                }
                fontList.addAll(itemMap.values());
            }
        }

        adapter.notifyDataSetChanged();
        textEmpty.setVisibility(fontList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void installOrViewFont(FontItem item) {
        File file = item.getPrimaryFontFile();
        if (file == null || !file.exists()) {
            Toast.makeText(this, "Font file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "font/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Install / View Font"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open font: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareFont(FontItem item) {
        File file = item.getPrimaryFontFile();
        if (file == null || !file.exists()) {
            Toast.makeText(this, "Font file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("font/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Font"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot share font: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void packageAsZip(FontItem item) {
        File fontsDir = FontRepository.getFontsDir(this);
        File zipFile = new File(fontsDir, item.name + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addFileToZip(zos, item.ttfFile);
            addFileToZip(zos, item.otfFile);
            addFileToZip(zos, item.svgFile);

            // Add README
            ZipEntry readme = new ZipEntry("README.txt");
            zos.putNextEntry(readme);
            String info = "Font Family: " + item.name + "\nCreated with Font Studio\nGenerated on: " + new Date();
            zos.write(info.getBytes());
            zos.closeEntry();

            Toast.makeText(this, "ZIP created: " + zipFile.getName(), Toast.LENGTH_LONG).show();

            // Offer to share the created zip
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", zipFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share ZIP Package"));

        } catch (IOException e) {
            Toast.makeText(this, "Failed to create ZIP: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void addFileToZip(ZipOutputStream zos, File file) throws IOException {
        if (file != null && file.exists()) {
            ZipEntry entry = new ZipEntry(file.getName());
            zos.putNextEntry(entry);
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
        }
    }

    private void deleteFont(FontItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Font")
                .setMessage("Are you sure you want to delete '" + item.name + "'? This will remove TTF, OTF, and SVG files.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (item.ttfFile != null && item.ttfFile.exists()) item.ttfFile.delete();
                    if (item.otfFile != null && item.otfFile.exists()) item.otfFile.delete();
                    if (item.svgFile != null && item.svgFile.exists()) item.svgFile.delete();
                    Toast.makeText(this, "Deleted " + item.name, Toast.LENGTH_SHORT).show();
                    loadFonts();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class FontAdapter extends RecyclerView.Adapter<FontViewHolder> {
        private final List<FontItem> list;

        public FontAdapter(List<FontItem> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public FontViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_font_card, parent, false);
            return new FontViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull FontViewHolder holder, int position) {
            FontItem item = list.get(position);
            holder.textName.setText(item.name);
            holder.textFormats.setText(item.getFormatsString());

            // Format size and date
            String sizeKb = (item.totalSize / 1024) + " KB";
            String dateStr = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date(item.lastModified));
            holder.textMeta.setText("Size: " + sizeKb + " · Modified: " + dateStr);

            // Apply typeface to preview if TTF or OTF exists
            File fontFile = item.getPrimaryFontFile();
            if (fontFile != null && (fontFile.getName().endsWith(".ttf") || fontFile.getName().endsWith(".otf"))) {
                try {
                    Typeface tf = Typeface.createFromFile(fontFile);
                    holder.textPreview.setTypeface(tf);
                } catch (Exception e) {
                    holder.textPreview.setTypeface(Typeface.DEFAULT);
                }
            } else {
                holder.textPreview.setTypeface(Typeface.DEFAULT);
            }

            holder.btnInstall.setOnClickListener(v -> installOrViewFont(item));
            holder.btnShare.setOnClickListener(v -> shareFont(item));
            holder.btnZip.setOnClickListener(v -> packageAsZip(item));
            holder.btnDelete.setOnClickListener(v -> deleteFont(item));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }

    private static class FontViewHolder extends RecyclerView.ViewHolder {
        TextView textName;
        TextView textFormats;
        TextView textPreview;
        TextView textMeta;
        MaterialButton btnInstall;
        MaterialButton btnShare;
        MaterialButton btnZip;
        MaterialButton btnDelete;

        public FontViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_font_item_name);
            textFormats = itemView.findViewById(R.id.text_font_formats);
            textPreview = itemView.findViewById(R.id.text_font_sample_preview);
            textMeta = itemView.findViewById(R.id.text_font_meta);
            btnInstall = itemView.findViewById(R.id.btn_install);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnZip = itemView.findViewById(R.id.btn_zip);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}

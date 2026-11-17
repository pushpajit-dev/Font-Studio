package jp.naklab.assu.android.android_myownfont;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.ClipData;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.util.Locale;

import jp.naklab.assu.android.android_myownfont.services.APIUtil;
import jp.naklab.assu.android.android_myownfont.services.GeminiFontService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSION = 1000;
    private static final int REQUEST_PICK_IMAGE = 2001;
    private static final int REQUEST_PICK_MODEL = 2002;
    private static final int REQUEST_PICK_ZIP = 2003;
    private static final int REQUEST_PICK_MULTIPLE_IMAGES = 2004;

    // Top UI Components
    private MaterialToolbar toolbar;
    private LinearProgressIndicator progressIndicator;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    // Core Business Objects
    private FontMaker fontMaker;
    private ImageRepository imageRepository;
    private FontRepository fontRepository;
    private OpenCVEdgeDetector openCVEdgeDetector;
    private FontSheetDetector fontSheetDetector;
    private GeminiFontService geminiFontService;

    // Language / Script
    private LanguageScript currentScript;

    // Character Data Model
    public static class CharacterItem {
        public final char character;
        public final String uId;
        public final int unicode;
        public boolean isGenerated;

        public CharacterItem(char character, String uId, int unicode) {
            this.character = character;
            this.uId = uId;
            this.unicode = unicode;
            this.isGenerated = false;
        }
    }

    private final List<CharacterItem> characterList = new ArrayList<>();
    private CharacterItem currentDrawCharacter;
    private CharacterItem currentImageTargetCharacter;
    private CharacterChipAdapter drawCharacterAdapter;
    private CharacterChipAdapter imageCharacterAdapter;
    private GlyphInspectorAdapter glyphInspectorAdapter;

    // Page 1: AI Studio Views
    private TextInputEditText inputFontName;
    private TextInputEditText inputPrompt;
    private MaterialButton buttonGenerateGemini;
    private TextView textGeminiStatus;
    private TextView textAiGlyphCount;
    private Slider sliderFontWeight;
    private Slider sliderFontSlant;
    private TextView textWeightVal;
    private TextView textSlantVal;
    private MaterialCardView cardAiProgress;
    private LinearProgressIndicator progressAiDeterminate;
    private TextView textAiProgressLabel;
    private TextView textAiProgressPercent;

    // Page 2: Image Studio Views
    private ImageView imageReferencePreview;
    private MaterialButton buttonPickImage;
    private MaterialButton buttonVectorizeImage;
    private MaterialButton buttonDetectFullSheet;
    private MaterialButton buttonApplyImageToLetter;
    private TextView textSelectedTargetLetter;
    private TextView textImageStatus;
    private Bitmap currentReferenceBitmap;
    private MaterialCardView cardImageProgress;
    private LinearProgressIndicator progressImageDeterminate;
    private TextView textImageProgressLabel;
    private TextView textImageProgressPercent;
    private ImageView imageCroppedGlyphPreview;
    private TextView textCropCharLabel;
    private TextView textCropBoxInfo;
    private MaterialButton buttonPrevBox;
    private MaterialButton buttonNextBox;
    private final List<org.opencv.core.Rect> currentSheetBoxes = new ArrayList<>();
    private int manualBoxIndexOffset = 0;

    // Page 3: Canvas Studio Views
    private TextView textDrawCurrentLetter;
    private FontCanvas fontCanvas;
    private MaterialButton buttonToggleGuidelines;
    private MaterialButtonToggleGroup toggleDrawTool;
    private MaterialButton colorBlack;
    private MaterialButton colorBlue;
    private MaterialButton colorRed;
    private Slider sliderBrushWidth;
    private TextView textBrushWidthVal;
    private MaterialButton buttonUndo;
    private MaterialButton buttonClear;
    private MaterialButton buttonSaveStroke;
    private TextView textDrawStatus;
    private boolean guidelinesVisible = true;

    // Page 4: Playground & Export Views
    private TextInputEditText inputPreviewTest;
    private Slider sliderPreviewSize;
    private TextView textPreviewSizeVal;
    private Slider sliderLetterSpacing;
    private TextView textLetterSpacingVal;
    private Slider sliderPreviewWeight;
    private TextView textPreviewWeightVal;
    private MaterialButtonToggleGroup toggleFormat;
    private MaterialButton buttonExportFont;
    private TextView textFontStatus;

    private Typeface currentTypeface;
    private File latestExportedFile;
    private String fontName = "MyAIFont";
    private int generatedGlyphsCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Restore persistent theme mode
        int savedNightMode = getSharedPreferences("font_studio_prefs", MODE_PRIVATE)
                .getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedNightMode);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fontMaker = new FontMaker();
        fontRepository = new FontRepository(this);
        imageRepository = new ImageRepository(this);
        openCVEdgeDetector = new OpenCVEdgeDetector();
        fontSheetDetector = new FontSheetDetector();
        geminiFontService = new GeminiFontService();

        // Initialize characters with default Latin script
        initCharacterList();

        // Check storage permissions on older devices if needed
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29) {
            checkPermission();
        }

        // Initialize OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.i("OpenCV", "OpenCV init failed");
        } else {
            Log.i("OpenCV", "OpenCV successfully initialized!");
        }

        initViews();
        setupViewPager();
    }

    private void initCharacterList() {
        if (currentScript == null) {
            List<LanguageScript> scripts = LanguageScript.getAvailableScripts();
            currentScript = scripts.get(0); // Default Latin
        }
        characterList.clear();
        characterList.addAll(currentScript.characters);

        if (!characterList.isEmpty()) {
            currentDrawCharacter = characterList.get(0);
            currentImageTargetCharacter = characterList.get(0);
            // If Latin, prefer 'A'
            for (CharacterItem item : characterList) {
                if (item.character == 'A') {
                    currentDrawCharacter = item;
                    currentImageTargetCharacter = item;
                    break;
                }
            }
        }
    }

    private void switchLanguageScript(LanguageScript script) {
        this.currentScript = script;
        initCharacterList();
        if (toolbar != null) {
            toolbar.setSubtitle(script.displayName);
        }
        if (drawCharacterAdapter != null) {
            drawCharacterAdapter.setSelectedCharacter(currentDrawCharacter != null ? currentDrawCharacter.character : ' ');
            drawCharacterAdapter.notifyDataSetChanged();
        }
        if (imageCharacterAdapter != null) {
            imageCharacterAdapter.setSelectedCharacter(currentImageTargetCharacter != null ? currentImageTargetCharacter.character : ' ');
            imageCharacterAdapter.notifyDataSetChanged();
        }
        if (glyphInspectorAdapter != null) {
            glyphInspectorAdapter.notifyDataSetChanged();
        }
        if (inputPreviewTest != null) {
            inputPreviewTest.setText(script.sampleText);
        }
        if (textDrawCurrentLetter != null && currentDrawCharacter != null) {
            textDrawCurrentLetter.setText("Drawing Character: '" + currentDrawCharacter.character + "' (" + currentDrawCharacter.uId + ")");
        }
        if (textSelectedTargetLetter != null && currentImageTargetCharacter != null) {
            textSelectedTargetLetter.setText("Select Target Glyph (Current: '" + currentImageTargetCharacter.character + "'):");
        }
        updateGlyphCountStatus();
        Snackbar.make(toolbar, "Switched to " + script.displayName, Snackbar.LENGTH_SHORT).show();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClick);
        if (currentScript != null) {
            toolbar.setSubtitle(currentScript.displayName);
        }

        progressIndicator = findViewById(R.id.progress_indicator);
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);
    }

    public static final int WORKFLOW_UNLOCKED = 0;
    public static final int WORKFLOW_AI_STUDIO = 1;
    public static final int WORKFLOW_IMAGE_SHEET = 2;
    public static final int WORKFLOW_CANVAS_DRAW = 3;

    private int currentWorkflowMode = WORKFLOW_UNLOCKED;

    private void setProjectWorkflow(int mode) {
        this.currentWorkflowMode = mode;
        updateTabLocks();
    }

    private void updateTabLocks() {
        if (tabLayout == null) return;
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null && tab.view != null) {
                boolean isLocked = isTabLocked(i);
                tab.view.setAlpha(isLocked ? 0.35f : 1.0f);
            }
        }
    }

    private boolean isTabLocked(int position) {
        if (currentWorkflowMode == WORKFLOW_UNLOCKED) return false;
        if (position == 3) return false; // Playground is always accessible!
        if (position == 2) return false; // Canvas Draw is always accessible to edit drawn characters!

        if (currentWorkflowMode == WORKFLOW_AI_STUDIO) {
            return (position == 1); // Image Sheet is locked
        } else if (currentWorkflowMode == WORKFLOW_IMAGE_SHEET) {
            return (position == 0); // AI Studio is locked
        } else if (currentWorkflowMode == WORKFLOW_CANVAS_DRAW) {
            return (position == 0 || position == 1); // AI Studio & Image Sheet locked
        }
        return false;
    }

    private int getFallbackAllowedTab() {
        if (currentWorkflowMode == WORKFLOW_AI_STUDIO) return 0;
        if (currentWorkflowMode == WORKFLOW_IMAGE_SHEET) return 1;
        if (currentWorkflowMode == WORKFLOW_CANVAS_DRAW) return 2;
        return 0;
    }

    private String getWorkflowLockDescription(int targetPosition) {
        String active = (currentWorkflowMode == WORKFLOW_AI_STUDIO) ? "Gemini AI Studio" :
                (currentWorkflowMode == WORKFLOW_IMAGE_SHEET) ? "Image Sheet Studio" : "Canvas Draw Studio";
        String target = (targetPosition == 0) ? "AI Studio" : "Image Sheet";
        return "This project is active in " + active + ". " + target + " is disabled for style consistency. Freehand editing in Canvas Draw and Playground are accessible. Tap 'Studio Workflow Lock' in menu to change.";
    }

    private void setupViewPager() {
        viewPager.setOffscreenPageLimit(3); // Keep all 4 pages alive in memory
        StudioPagerAdapter adapter = new StudioPagerAdapter();
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("AI Studio");
                    tab.setIcon(R.drawable.ic_auto_awesome);
                    break;
                case 1:
                    tab.setText("Image Sheet");
                    tab.setIcon(R.drawable.ic_image);
                    break;
                case 2:
                    tab.setText("Canvas Draw");
                    tab.setIcon(R.drawable.ic_draw);
                    break;
                case 3:
                    tab.setText("Playground");
                    tab.setIcon(R.drawable.ic_text_fields);
                    break;
            }
        }).attach();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (isTabLocked(position)) {
                    String reason = getWorkflowLockDescription(position);
                    Toast.makeText(MainActivity.this, reason, Toast.LENGTH_SHORT).show();
                    int fallback = getFallbackAllowedTab();
                    viewPager.setCurrentItem(fallback, true);
                }
            }
        });
    }

    private class StudioPagerAdapter extends RecyclerView.Adapter<StudioPageViewHolder> {
        @Override
        public int getItemCount() {
            return 4;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @NonNull
        @Override
        public StudioPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId;
            switch (viewType) {
                case 0: layoutId = R.layout.page_ai_studio; break;
                case 1: layoutId = R.layout.page_image_studio; break;
                case 2: layoutId = R.layout.page_draw_studio; break;
                default: layoutId = R.layout.page_export_studio; break;
            }
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new StudioPageViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull StudioPageViewHolder holder, int position) {
            switch (position) {
                case 0: setupPageAi(holder.itemView); break;
                case 1: setupPageImage(holder.itemView); break;
                case 2: setupPageDraw(holder.itemView); break;
                case 3: setupPageExport(holder.itemView); break;
            }
        }
    }

    private static class StudioPageViewHolder extends RecyclerView.ViewHolder {
        public StudioPageViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
        }
    }

    // ==========================================
    // PAGE 1: AI STUDIO
    // ==========================================
    private void setupPageAi(View v) {
        inputFontName = v.findViewById(R.id.input_font_name);
        inputPrompt = v.findViewById(R.id.input_prompt);
        buttonGenerateGemini = v.findViewById(R.id.button_generate_gemini);
        textGeminiStatus = v.findViewById(R.id.text_gemini_status);
        textAiGlyphCount = v.findViewById(R.id.text_ai_glyph_count);
        sliderFontWeight = v.findViewById(R.id.slider_font_weight);
        sliderFontSlant = v.findViewById(R.id.slider_font_slant);
        textWeightVal = v.findViewById(R.id.text_weight_val);
        textSlantVal = v.findViewById(R.id.text_slant_val);

        cardAiProgress = v.findViewById(R.id.card_ai_progress);
        progressAiDeterminate = v.findViewById(R.id.progress_ai_determinate);
        textAiProgressLabel = v.findViewById(R.id.text_ai_progress_label);
        textAiProgressPercent = v.findViewById(R.id.text_ai_progress_percent);

        // Sliders listeners
        sliderFontWeight.addOnChangeListener((slider, value, fromUser) -> {
            textWeightVal.setText(String.valueOf((int) value));
        });
        sliderFontSlant.addOnChangeListener((slider, value, fromUser) -> {
            textSlantVal.setText(((int) value) + "°");
        });

        // Preset Chips
        Chip chipGothic = v.findViewById(R.id.chip_gothic);
        Chip chipCyberpunk = v.findViewById(R.id.chip_cyberpunk);
        Chip chipBubble = v.findViewById(R.id.chip_bubble);
        Chip chipArtDeco = v.findViewById(R.id.chip_art_deco);
        Chip chipMinimalist = v.findViewById(R.id.chip_minimalist);

        chipGothic.setOnClickListener(v1 -> inputPrompt.setText("Gothic medieval calligraphy with elegant decorative flourishes"));
        chipCyberpunk.setOnClickListener(v1 -> inputPrompt.setText("Cyberpunk futuristic neon display font with sharp angles and techno geometry"));
        chipBubble.setOnClickListener(v1 -> inputPrompt.setText("Cute playful rounded bubble cartoon lettering"));
        chipArtDeco.setOnClickListener(v1 -> inputPrompt.setText("1920s Art Deco luxury geometric typography with high contrast"));
        chipMinimalist.setOnClickListener(v1 -> inputPrompt.setText("Modern minimalist clean sans-serif with perfect geometric circles and lines"));

        buttonGenerateGemini.setOnClickListener(v1 -> generateFontWithGemini(null));
        updateGlyphCountStatus();
    }

    // Reusable touch disallow listener to prevent ViewPager2 from intercepting horizontal character dragging
    private final RecyclerView.OnItemTouchListener touchDisallowListener = new RecyclerView.OnItemTouchListener() {
        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                rv.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                rv.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {}

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
    };

    // ==========================================
    // PAGE 2: IMAGE & SHEET STUDIO
    // ==========================================
    private void setupPageImage(View v) {
        imageReferencePreview = v.findViewById(R.id.image_reference_preview);
        buttonPickImage = v.findViewById(R.id.button_pick_image);
        buttonVectorizeImage = v.findViewById(R.id.button_vectorize_image);
        buttonDetectFullSheet = v.findViewById(R.id.button_detect_full_sheet);
        buttonApplyImageToLetter = v.findViewById(R.id.button_apply_image_to_letter);
        textSelectedTargetLetter = v.findViewById(R.id.text_selected_target_letter);
        textImageStatus = v.findViewById(R.id.text_image_status);

        cardImageProgress = v.findViewById(R.id.card_image_progress);
        progressImageDeterminate = v.findViewById(R.id.progress_image_determinate);
        textImageProgressLabel = v.findViewById(R.id.text_image_progress_label);
        textImageProgressPercent = v.findViewById(R.id.text_image_progress_percent);

        RecyclerView recyclerImageCharacters = v.findViewById(R.id.recycler_image_characters);
        recyclerImageCharacters.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerImageCharacters.addOnItemTouchListener(touchDisallowListener);

        imageCharacterAdapter = new CharacterChipAdapter(characterList, item -> {
            selectImageTargetCharacter(item);
        });
        if (currentImageTargetCharacter != null) {
            imageCharacterAdapter.setSelectedCharacter(currentImageTargetCharacter.character);
        }
        recyclerImageCharacters.setAdapter(imageCharacterAdapter);
        int initialImagePos = imageCharacterAdapter.getPositionForCharacter(currentImageTargetCharacter != null ? currentImageTargetCharacter.character : 'A');
        if (initialImagePos >= 0) {
            recyclerImageCharacters.scrollToPosition(initialImagePos);
        }

        // Category Filter Chips
        Chip chipImageUpper = v.findViewById(R.id.chip_image_cat_az_upper);
        Chip chipImageLower = v.findViewById(R.id.chip_image_cat_az_lower);
        Chip chipImageNumbers = v.findViewById(R.id.chip_image_cat_numbers);
        Chip chipImageSymbols = v.findViewById(R.id.chip_image_cat_symbols);

        if (chipImageUpper != null) chipImageUpper.setOnClickListener(v1 -> scrollToAndSelectImageCharacter('A', recyclerImageCharacters));
        if (chipImageLower != null) chipImageLower.setOnClickListener(v1 -> scrollToAndSelectImageCharacter('a', recyclerImageCharacters));
        if (chipImageNumbers != null) chipImageNumbers.setOnClickListener(v1 -> scrollToAndSelectImageCharacter('0', recyclerImageCharacters));
        if (chipImageSymbols != null) chipImageSymbols.setOnClickListener(v1 -> scrollToAndSelectImageCharacter('!', recyclerImageCharacters));

        buttonPickImage.setOnClickListener(v1 -> showImageImportOptions());
        buttonVectorizeImage.setOnClickListener(v1 -> {
            if (currentReferenceBitmap == null) {
                currentReferenceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.a_character_image);
            }
            showGeminiExpandDialog();
        });

        buttonDetectFullSheet.setOnClickListener(v1 -> detectFullFontSheet());
        buttonApplyImageToLetter.setOnClickListener(v1 -> applyImageToSelectedLetter());

        imageCroppedGlyphPreview = v.findViewById(R.id.image_cropped_glyph_preview);
        textCropCharLabel = v.findViewById(R.id.text_crop_char_label);
        textCropBoxInfo = v.findViewById(R.id.text_crop_box_info);
        buttonPrevBox = v.findViewById(R.id.button_prev_box);
        buttonNextBox = v.findViewById(R.id.button_next_box);

        if (buttonPrevBox != null) {
            buttonPrevBox.setOnClickListener(v1 -> {
                manualBoxIndexOffset--;
                updateSingleCropPreview();
            });
        }
        if (buttonNextBox != null) {
            buttonNextBox.setOnClickListener(v1 -> {
                manualBoxIndexOffset++;
                updateSingleCropPreview();
            });
        }

        View cardSingleCrop = v.findViewById(R.id.card_single_crop_preview);
        if (cardSingleCrop != null) {
            cardSingleCrop.setOnClickListener(v1 -> {
                if (currentImageTargetCharacter != null) {
                    showManualCropDialog(currentImageTargetCharacter);
                }
            });
        }

        scanSheetBoxesAsync(null);
    }

    private int getCharacterIndex(CharacterItem item) {
        if (item == null) return 0;
        for (int i = 0; i < characterList.size(); i++) {
            if (characterList.get(i).uId.equals(item.uId)) {
                return i;
            }
        }
        return 0;
    }

    private void scanSheetBoxesAsync(@Nullable Runnable onComplete) {
        if (currentReferenceBitmap == null) {
            try {
                currentReferenceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.a_character_image);
            } catch (Exception ignored) {}
        }
        if (currentReferenceBitmap == null) return;

        new Thread(() -> {
            try {
                List<org.opencv.core.Rect> boxes = fontSheetDetector.detectCharacterBoxes(currentReferenceBitmap);
                runOnUiThread(() -> {
                    currentSheetBoxes.clear();
                    if (boxes != null) {
                        currentSheetBoxes.addAll(boxes);
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    } else {
                        updateSingleCropPreview();
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "scanSheetBoxesAsync error: " + e.getMessage());
            }
        }).start();
    }

    private void updateSingleCropPreview() {
        if (currentImageTargetCharacter == null) return;

        if (textCropCharLabel != null) {
            textCropCharLabel.setText("Selected: '" + currentImageTargetCharacter.character + "' (" + currentImageTargetCharacter.uId + ")");
        }

        if (currentReferenceBitmap == null) {
            try {
                currentReferenceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.a_character_image);
            } catch (Exception ignored) {}
        }
        if (currentReferenceBitmap == null) return;

        if (currentSheetBoxes.isEmpty()) {
            if (imageCroppedGlyphPreview != null) {
                imageCroppedGlyphPreview.setImageBitmap(currentReferenceBitmap);
            }
            if (textCropBoxInfo != null) {
                textCropBoxInfo.setText("Single image / full preview");
            }
            return;
        }

        int targetIndex = getCharacterIndex(currentImageTargetCharacter);
        int mappedBoxIndex = targetIndex + manualBoxIndexOffset;

        if (mappedBoxIndex < 0) {
            mappedBoxIndex = 0;
            manualBoxIndexOffset = -targetIndex;
        } else if (mappedBoxIndex >= currentSheetBoxes.size()) {
            mappedBoxIndex = currentSheetBoxes.size() - 1;
            manualBoxIndexOffset = mappedBoxIndex - targetIndex;
        }

        org.opencv.core.Rect box = currentSheetBoxes.get(mappedBoxIndex);
        Bitmap cropped = fontSheetDetector.cropCharacterBitmap(currentReferenceBitmap, box);
        if (imageCroppedGlyphPreview != null && cropped != null) {
            imageCroppedGlyphPreview.setImageBitmap(cropped);
        }

        if (textCropBoxInfo != null) {
            textCropBoxInfo.setText("Sheet Box #" + (mappedBoxIndex + 1) + " of " + currentSheetBoxes.size() +
                    (manualBoxIndexOffset != 0 ? (" (offset " + (manualBoxIndexOffset > 0 ? "+" : "") + manualBoxIndexOffset + ")") : ""));
        }
    }

    private void selectImageTargetCharacter(CharacterItem item) {
        currentImageTargetCharacter = item;
        if (textSelectedTargetLetter != null) {
            textSelectedTargetLetter.setText("Select Target Glyph (Current: '" + item.character + "'):");
        }
        updateSingleCropPreview();
    }

    private void scrollToAndSelectImageCharacter(char c, RecyclerView rv) {
        if (imageCharacterAdapter == null) return;
        int pos = imageCharacterAdapter.getPositionForCharacter(c);
        if (pos >= 0) {
            rv.smoothScrollToPosition(pos);
            imageCharacterAdapter.setSelectedCharacter(c);
            selectImageTargetCharacter(characterList.get(pos));
        }
    }

    // ==========================================
    // PAGE 3: CANVAS STUDIO
    // ==========================================
    private void setupPageDraw(View v) {
        textDrawCurrentLetter = v.findViewById(R.id.text_draw_current_letter);
        fontCanvas = v.findViewById(R.id.main_font_canvas);
        buttonToggleGuidelines = v.findViewById(R.id.button_toggle_guidelines);
        toggleDrawTool = v.findViewById(R.id.toggle_draw_tool);
        colorBlack = v.findViewById(R.id.color_black);
        colorBlue = v.findViewById(R.id.color_blue);
        colorRed = v.findViewById(R.id.color_red);
        sliderBrushWidth = v.findViewById(R.id.slider_brush_width);
        textBrushWidthVal = v.findViewById(R.id.text_brush_width_val);
        buttonUndo = v.findViewById(R.id.button_undo);
        buttonClear = v.findViewById(R.id.button_clear);
        buttonSaveStroke = v.findViewById(R.id.button_save_stroke);
        textDrawStatus = v.findViewById(R.id.text_draw_status);

        // Lock ViewPager2 swiping while user is drawing on canvas
        fontCanvas.setOnCanvasTouchListener(new FontCanvas.OnCanvasTouchListener() {
            @Override
            public void onTouchStarted() {
                viewPager.setUserInputEnabled(false);
            }

            @Override
            public void onTouchEnded() {
                viewPager.setUserInputEnabled(true);
            }
        });

        RecyclerView recyclerDrawCharacters = v.findViewById(R.id.recycler_draw_characters);
        recyclerDrawCharacters.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerDrawCharacters.addOnItemTouchListener(touchDisallowListener);

        drawCharacterAdapter = new CharacterChipAdapter(characterList, item -> {
            selectDrawCharacter(item);
        });
        if (currentDrawCharacter != null) {
            drawCharacterAdapter.setSelectedCharacter(currentDrawCharacter.character);
        }
        recyclerDrawCharacters.setAdapter(drawCharacterAdapter);
        int initialDrawPos = drawCharacterAdapter.getPositionForCharacter(currentDrawCharacter != null ? currentDrawCharacter.character : 'A');
        if (initialDrawPos >= 0) {
            recyclerDrawCharacters.scrollToPosition(initialDrawPos);
        }

        // Category Filter Chips
        Chip chipDrawUpper = v.findViewById(R.id.chip_draw_cat_az_upper);
        Chip chipDrawLower = v.findViewById(R.id.chip_draw_cat_az_lower);
        Chip chipDrawNumbers = v.findViewById(R.id.chip_draw_cat_numbers);
        Chip chipDrawSymbols = v.findViewById(R.id.chip_draw_cat_symbols);

        if (chipDrawUpper != null) chipDrawUpper.setOnClickListener(v1 -> scrollToAndSelectDrawCharacter('A', recyclerDrawCharacters));
        if (chipDrawLower != null) chipDrawLower.setOnClickListener(v1 -> scrollToAndSelectDrawCharacter('a', recyclerDrawCharacters));
        if (chipDrawNumbers != null) chipDrawNumbers.setOnClickListener(v1 -> scrollToAndSelectDrawCharacter('0', recyclerDrawCharacters));
        if (chipDrawSymbols != null) chipDrawSymbols.setOnClickListener(v1 -> scrollToAndSelectDrawCharacter('!', recyclerDrawCharacters));

        sliderBrushWidth.addOnChangeListener((slider, value, fromUser) -> {
            fontCanvas.setStrokeWidth(value);
            textBrushWidthVal.setText(String.valueOf((int) value));
        });

        toggleDrawTool.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.tool_pen) {
                    fontCanvas.setToolMode(FontCanvas.TOOL_PEN);
                } else if (checkedId == R.id.tool_line) {
                    fontCanvas.setToolMode(FontCanvas.TOOL_LINE);
                } else if (checkedId == R.id.tool_bezier) {
                    fontCanvas.setToolMode(FontCanvas.TOOL_BEZIER);
                } else if (checkedId == R.id.tool_eraser) {
                    fontCanvas.setToolMode(FontCanvas.TOOL_ERASER);
                }
            }
        });

        colorBlack.setOnClickListener(v1 -> fontCanvas.setPenColor(0xFF000000));
        colorBlue.setOnClickListener(v1 -> fontCanvas.setPenColor(0xFF1976D2));
        colorRed.setOnClickListener(v1 -> fontCanvas.setPenColor(0xFFD32F2F));

        buttonToggleGuidelines.setOnClickListener(v1 -> {
            guidelinesVisible = !guidelinesVisible;
            fontCanvas.toggleGuidelines(guidelinesVisible);
            buttonToggleGuidelines.setText(guidelinesVisible ? "Rulers ON" : "Rulers OFF");
        });

        buttonUndo.setOnClickListener(v1 -> fontCanvas.undo());
        buttonClear.setOnClickListener(v1 -> {
            fontCanvas.clear();
            fontCanvas.setBackgroundResource(R.drawable.background_white);
            textDrawStatus.setVisibility(View.VISIBLE);
            textDrawStatus.setText("Canvas cleared.");
        });
        buttonSaveStroke.setOnClickListener(v1 -> saveDrawnGlyph());
    }

    private void selectDrawCharacter(CharacterItem item) {
        currentDrawCharacter = item;
        if (textDrawCurrentLetter != null) {
            textDrawCurrentLetter.setText("Drawing Character: '" + item.character + "' (" + item.uId + ")");
        }
        if (fontCanvas != null) {
            fontCanvas.clear();
            Bitmap saved = imageRepository.loadImageBitmap(getFontName(), item.uId);
            if (saved != null) {
                fontCanvas.setBackground(new BitmapDrawable(getResources(), saved));
            } else {
                fontCanvas.setBackgroundResource(R.drawable.background_white);
            }
        }
    }

    private void scrollToAndSelectDrawCharacter(char c, RecyclerView rv) {
        if (drawCharacterAdapter == null) return;
        int pos = drawCharacterAdapter.getPositionForCharacter(c);
        if (pos >= 0) {
            rv.smoothScrollToPosition(pos);
            drawCharacterAdapter.setSelectedCharacter(c);
            selectDrawCharacter(characterList.get(pos));
        }
    }

    // ==========================================
    // PAGE 4: PLAYGROUND & EXPORT
    // ==========================================
    private void setupPageExport(View v) {
        inputPreviewTest = v.findViewById(R.id.input_preview_test);
        if (inputPreviewTest != null) {
            inputPreviewTest.setMovementMethod(new android.text.method.ScrollingMovementMethod());
            inputPreviewTest.setVerticalScrollBarEnabled(true);
        }
        sliderPreviewSize = v.findViewById(R.id.slider_preview_size);
        textPreviewSizeVal = v.findViewById(R.id.text_preview_size_val);
        sliderLetterSpacing = v.findViewById(R.id.slider_letter_spacing);
        textLetterSpacingVal = v.findViewById(R.id.text_letter_spacing_val);
        sliderPreviewWeight = v.findViewById(R.id.slider_preview_weight);
        textPreviewWeightVal = v.findViewById(R.id.text_preview_weight_val);
        toggleFormat = v.findViewById(R.id.toggle_format);
        buttonExportFont = v.findViewById(R.id.button_export_font);
        textFontStatus = v.findViewById(R.id.text_font_status);

        // Sliders
        sliderPreviewSize.addOnChangeListener((slider, value, fromUser) -> {
            inputPreviewTest.setTextSize(value);
            textPreviewSizeVal.setText(((int) value) + "sp");
        });
        sliderLetterSpacing.addOnChangeListener((slider, value, fromUser) -> {
            inputPreviewTest.setLetterSpacing(value);
            textLetterSpacingVal.setText(String.format("%.2f", value));
        });

        // Thickness / Weight slider
        if (sliderPreviewWeight != null) {
            sliderPreviewWeight.addOnChangeListener((slider, value, fromUser) -> {
                int w = (int) value;
                String label;
                int style = Typeface.NORMAL;
                if (w <= 200) {
                    label = "Thin (" + w + ")";
                } else if (w <= 300) {
                    label = "Light (" + w + ")";
                } else if (w <= 500) {
                    label = "Regular (" + w + ")";
                    style = Typeface.NORMAL;
                } else if (w <= 600) {
                    label = "Medium (" + w + ")";
                    style = Typeface.BOLD;
                } else if (w <= 700) {
                    label = "Bold (" + w + ")";
                    style = Typeface.BOLD;
                } else {
                    label = "Heavy (" + w + ")";
                    style = Typeface.BOLD;
                }
                textPreviewWeightVal.setText(label);
                if (inputPreviewTest != null) {
                    if (currentTypeface != null) {
                        inputPreviewTest.setTypeface(currentTypeface, style);
                    } else {
                        inputPreviewTest.setTypeface(Typeface.DEFAULT, style);
                    }
                    if (w >= 700) {
                        inputPreviewTest.getPaint().setFakeBoldText(true);
                    } else {
                        inputPreviewTest.getPaint().setFakeBoldText(false);
                    }
                    inputPreviewTest.invalidate();
                }
            });
        }

        // Color buttons
        MaterialButton previewColorBlack = v.findViewById(R.id.preview_color_black);
        MaterialButton previewColorTurquoise = v.findViewById(R.id.preview_color_turquoise);
        MaterialButton previewColorBlue = v.findViewById(R.id.preview_color_blue);
        MaterialButton previewColorRed = v.findViewById(R.id.preview_color_red);
        MaterialButton previewColorAmber = v.findViewById(R.id.preview_color_amber);
        MaterialButton previewColorWhite = v.findViewById(R.id.preview_color_white);

        if (previewColorBlack != null) previewColorBlack.setOnClickListener(v1 -> inputPreviewTest.setTextColor(0xFF191C1D));
        if (previewColorTurquoise != null) previewColorTurquoise.setOnClickListener(v1 -> inputPreviewTest.setTextColor(0xFF00C4B4));
        if (previewColorBlue != null) previewColorBlue.setOnClickListener(v1 -> inputPreviewTest.setTextColor(0xFF1976D2));
        if (previewColorRed != null) previewColorRed.setOnClickListener(v1 -> inputPreviewTest.setTextColor(0xFFD32F2F));
        if (previewColorAmber != null) previewColorAmber.setOnClickListener(v1 -> inputPreviewTest.setTextColor(0xFFFF8F00));
        if (previewColorWhite != null) {
            previewColorWhite.setOnClickListener(v1 -> {
                inputPreviewTest.setTextColor(0xFFFFFFFF);
                inputPreviewTest.setBackgroundColor(0xFF191C1D);
            });
        }

        // Prevent ViewPager2 from hijacking horizontal or vertical touches in playground
        View.OnTouchListener disallowTouch = (view, motionEvent) -> {
            int action = motionEvent.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        };

        if (inputPreviewTest != null) {
            inputPreviewTest.setOnTouchListener(disallowTouch);
        }
        if (sliderPreviewSize != null) {
            sliderPreviewSize.setOnTouchListener(disallowTouch);
        }
        if (sliderLetterSpacing != null) {
            sliderLetterSpacing.setOnTouchListener(disallowTouch);
        }
        if (sliderPreviewWeight != null) {
            sliderPreviewWeight.setOnTouchListener(disallowTouch);
        }

        // Quick Pangram Chips
        Chip chipFox = v.findViewById(R.id.chip_pangram_fox);
        Chip chipBox = v.findViewById(R.id.chip_pangram_box);
        Chip chipNums = v.findViewById(R.id.chip_pangram_numbers);
        Chip chipSyms = v.findViewById(R.id.chip_pangram_symbols);

        if (chipFox != null) {
            if (chipFox.getParent() instanceof ViewGroup && ((ViewGroup) chipFox.getParent()).getParent() instanceof View) {
                ((View) ((ViewGroup) chipFox.getParent()).getParent()).setOnTouchListener(disallowTouch);
            }
            chipFox.setOnClickListener(v1 -> inputPreviewTest.setText(currentScript != null ? currentScript.sampleText : "The quick brown fox jumps over the lazy dog"));
        }
        if (chipBox != null) chipBox.setOnClickListener(v1 -> inputPreviewTest.setText("Pack my box with five dozen liquor jugs"));
        if (chipNums != null) chipNums.setOnClickListener(v1 -> inputPreviewTest.setText("0123456789 (Count 1 to 100)"));
        if (chipSyms != null) chipSyms.setOnClickListener(v1 -> inputPreviewTest.setText("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"));

        // Character Map Inspector Grid
        RecyclerView recyclerGlyphInspector = v.findViewById(R.id.recycler_glyph_inspector);
        if (recyclerGlyphInspector != null) {
            recyclerGlyphInspector.setLayoutManager(new GridLayoutManager(this, 7));
            glyphInspectorAdapter = new GlyphInspectorAdapter(characterList);
            recyclerGlyphInspector.setAdapter(glyphInspectorAdapter);
            recyclerGlyphInspector.addOnItemTouchListener(touchDisallowListener);
        }

        if (buttonExportFont != null) buttonExportFont.setOnClickListener(v1 -> exportFontLocally());
    }

    // ==========================================
    // BUSINESS LOGIC: GEMINI & DETECTION
    // ==========================================
    private String getFontName() {
        if (inputFontName != null && inputFontName.getText() != null) {
            String name = inputFontName.getText().toString().trim();
            if (!name.isEmpty()) return name;
        }
        return fontName;
    }

    private void showProgress(boolean show) {
        if (progressIndicator != null) {
            progressIndicator.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void generateFontWithGemini(@Nullable Bitmap refBitmap) {
        String apiKey = APIUtil.getGeminiApiKey(this);
        if (apiKey.isEmpty()) {
            showApiSettingsDialog();
            return;
        }

        String prompt = (inputPrompt != null && inputPrompt.getText() != null) ? inputPrompt.getText().toString().trim() : "";
        if (prompt.isEmpty() && refBitmap == null) {
            prompt = "Modern clean high-contrast sans-serif vector font";
        }

        showProgress(true);
        if (cardAiProgress != null) {
            cardAiProgress.setVisibility(View.VISIBLE);
            progressAiDeterminate.setProgress(10);
            textAiProgressPercent.setText("10%");
            textAiProgressLabel.setText("Generating Typography...");
        }
        if (textGeminiStatus != null) {
            textGeminiStatus.setVisibility(View.VISIBLE);
            textGeminiStatus.setText("Generating Typography...");
        }

        String selectedModel = APIUtil.getGeminiModel(this);
        geminiFontService.generateFontFromPrompt(apiKey, selectedModel, prompt, refBitmap, new GeminiFontService.OnFontGenerationListener() {
            @Override
            public void onProgress(int percent, String status) {
                showProgress(true);
                if (cardAiProgress != null) {
                    cardAiProgress.setVisibility(View.VISIBLE);
                    progressAiDeterminate.setProgress(percent);
                    textAiProgressPercent.setText(percent + "%");
                    textAiProgressLabel.setText(status);
                }
                if (textGeminiStatus != null) {
                    textGeminiStatus.setVisibility(View.VISIBLE);
                    textGeminiStatus.setText(status);
                }
            }

            @Override
            public void onSheetGenerated(Bitmap sheetBitmap, String fontFamily) {
                currentReferenceBitmap = sheetBitmap;
                if (imageReferencePreview != null) {
                    imageReferencePreview.setImageBitmap(sheetBitmap);
                }
                if (cardAiProgress != null) {
                    progressAiDeterminate.setProgress(90);
                    textAiProgressPercent.setText("90%");
                    textAiProgressLabel.setText("Generating Typography • Tracing...");
                }
                // Automatically run sheet detector on the generated clean white sheet
                detectFullFontSheet();
            }

            @Override
            public void onSuccess(String fontFamily, List<GeminiFontService.GlyphData> glyphs) {
                showProgress(false);
                setProjectWorkflow(WORKFLOW_AI_STUDIO);
                if (cardAiProgress != null) {
                    progressAiDeterminate.setProgress(100);
                    textAiProgressPercent.setText("100%");
                    textAiProgressLabel.setText("Typography Ready!");
                    cardAiProgress.postDelayed(() -> cardAiProgress.setVisibility(View.GONE), 1500);
                }
                generatedGlyphsCount += glyphs.size();
                fontMaker.applyGeminiGlyphs(glyphs);

                for (GeminiFontService.GlyphData g : glyphs) {
                    markCharacterGenerated(g.glyphName, g.unicode);
                }

                if (textGeminiStatus != null) {
                    textGeminiStatus.setVisibility(View.VISIBLE);
                    textGeminiStatus.setText("✓ Generated " + glyphs.size() + " glyphs successfully!");
                }
                updateGlyphCountStatus();
                notifyCharacterAdapters();
                autoCompilePreview();

                Snackbar.make(toolbar, "Generated " + glyphs.size() + " font glyphs with Gemini!", Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                showProgress(false);
                if (cardAiProgress != null) {
                    cardAiProgress.setVisibility(View.GONE);
                }
                if (textGeminiStatus != null) {
                    textGeminiStatus.setVisibility(View.VISIBLE);
                    textGeminiStatus.setText("⚠ " + error);
                }
                Snackbar.make(toolbar, "Gemini: " + error, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void detectFullFontSheet() {
        if (currentReferenceBitmap == null) {
            currentReferenceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.a_character_image);
        }

        showProgress(true);
        if (cardImageProgress != null) {
            cardImageProgress.setVisibility(View.VISIBLE);
            progressImageDeterminate.setProgress(5);
            textImageProgressPercent.setText("5%");
            textImageProgressLabel.setText("Scanning font sheet with OpenCV...");
        }
        if (textImageStatus != null) {
            textImageStatus.setVisibility(View.VISIBLE);
            textImageStatus.setText("Scanning font sheet with OpenCV...");
        }

        fontSheetDetector.detectAndProcessSheet(currentReferenceBitmap, characterList, new FontSheetDetector.OnSheetDetectionListener() {
            @Override
            public void onProgress(int current, int total, String status) {
                showProgress(true);
                if (cardImageProgress != null) {
                    cardImageProgress.setVisibility(View.VISIBLE);
                    int pct = Math.min(100, Math.max(0, current));
                    progressImageDeterminate.setProgress(pct);
                    textImageProgressPercent.setText(pct + "%");
                    textImageProgressLabel.setText(status);
                }
                if (textImageStatus != null) {
                    textImageStatus.setVisibility(View.VISIBLE);
                    textImageStatus.setText(status);
                }
            }

            @Override
            public void onComplete(List<GeminiFontService.GlyphData> detectedGlyphs) {
                showProgress(false);
                if (currentWorkflowMode == WORKFLOW_UNLOCKED) {
                    setProjectWorkflow(WORKFLOW_IMAGE_SHEET);
                }
                if (cardImageProgress != null) {
                    progressImageDeterminate.setProgress(100);
                    textImageProgressPercent.setText("100%");
                    textImageProgressLabel.setText("Complete!");
                    cardImageProgress.postDelayed(() -> cardImageProgress.setVisibility(View.GONE), 1500);
                }
                generatedGlyphsCount += detectedGlyphs.size();
                fontMaker.applyGeminiGlyphs(detectedGlyphs);

                for (GeminiFontService.GlyphData g : detectedGlyphs) {
                    markCharacterGenerated(g.glyphName, g.unicode);
                }

                if (textImageStatus != null) {
                    textImageStatus.setVisibility(View.VISIBLE);
                    textImageStatus.setText("✓ Fitted " + detectedGlyphs.size() + " characters to font template!");
                }
                updateGlyphCountStatus();
                notifyCharacterAdapters();
                autoCompilePreview();
                updateSingleCropPreview();

                Snackbar.make(toolbar, "Fitted " + detectedGlyphs.size() + " sheet glyphs to font!", Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                showProgress(false);
                if (cardImageProgress != null) {
                    cardImageProgress.setVisibility(View.GONE);
                }
                if (textImageStatus != null) {
                    textImageStatus.setVisibility(View.VISIBLE);
                    textImageStatus.setText("⚠ " + error);
                }
                Snackbar.make(toolbar, "Sheet Detection: " + error, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void applyImageToSelectedLetter() {
        if (currentReferenceBitmap == null) {
            currentReferenceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.a_character_image);
        }
        if (currentImageTargetCharacter == null) return;

        // Isolate single character: NEVER use full uncropped sheet!
        Bitmap croppedBmp = null;
        int targetCharIndex = getCharacterIndex(currentImageTargetCharacter);
        int mappedBoxIndex = targetCharIndex + manualBoxIndexOffset;

        if (!currentSheetBoxes.isEmpty() && mappedBoxIndex >= 0 && mappedBoxIndex < currentSheetBoxes.size()) {
            org.opencv.core.Rect box = currentSheetBoxes.get(mappedBoxIndex);
            croppedBmp = fontSheetDetector.cropCharacterBitmap(currentReferenceBitmap, box);
        } else if (currentSheetBoxes.isEmpty()) {
            List<org.opencv.core.Rect> boxes = fontSheetDetector.detectCharacterBoxes(currentReferenceBitmap);
            if (!boxes.isEmpty()) {
                currentSheetBoxes.clear();
                currentSheetBoxes.addAll(boxes);
                int validIdx = Math.max(0, Math.min(mappedBoxIndex, currentSheetBoxes.size() - 1));
                croppedBmp = fontSheetDetector.cropCharacterBitmap(currentReferenceBitmap, currentSheetBoxes.get(validIdx));
            } else {
                if (currentReferenceBitmap.getWidth() > 350 && currentReferenceBitmap.getHeight() > 350) {
                    showManualCropDialog(currentImageTargetCharacter);
                    return;
                }
                croppedBmp = currentReferenceBitmap;
            }
        }

        if (croppedBmp == null) {
            showManualCropDialog(currentImageTargetCharacter);
            return;
        }

        String targetUId = currentImageTargetCharacter.uId;
        String pathD = fontSheetDetector.vectorizeCroppedBitmap(croppedBmp);
        if (pathD.isEmpty()) {
            pathD = openCVEdgeDetector.extractPathDFromBitmap(croppedBmp);
        }

        if (!pathD.isEmpty()) {
            String unicode = "&#x" + Integer.toHexString(currentImageTargetCharacter.unicode) + ";";
            fontMaker.addCustomGlyph(targetUId, unicode, pathD, 800);
            generatedGlyphsCount++;
            currentImageTargetCharacter.isGenerated = true;
            notifyCharacterAdapters();
            updateGlyphCountStatus();

            char savedChar = currentImageTargetCharacter.character;

            // Auto-advance target character
            int currentIndex = -1;
            for (int i = 0; i < characterList.size(); i++) {
                if (characterList.get(i).uId.equals(currentImageTargetCharacter.uId)) {
                    currentIndex = i;
                    break;
                }
            }

            if (currentIndex >= 0 && currentIndex + 1 < characterList.size()) {
                CharacterItem nextItem = characterList.get(currentIndex + 1);
                selectImageTargetCharacter(nextItem);
                if (imageCharacterAdapter != null) {
                    imageCharacterAdapter.setSelectedCharacter(nextItem.character);
                }
                RecyclerView recyclerImg = findViewById(R.id.recycler_image_characters);
                if (recyclerImg != null) {
                    recyclerImg.smoothScrollToPosition(currentIndex + 1);
                }
                if (textImageStatus != null) {
                    textImageStatus.setVisibility(View.VISIBLE);
                    textImageStatus.setText("✓ Applied single letter '" + savedChar + "'! Next: '" + nextItem.character + "'");
                }
                Snackbar.make(toolbar, "Applied '" + savedChar + "'. Next: '" + nextItem.character + "'", Snackbar.LENGTH_SHORT).show();
            } else {
                if (textImageStatus != null) {
                    textImageStatus.setVisibility(View.VISIBLE);
                    textImageStatus.setText("✓ Applied vector outline to '" + savedChar + "'");
                }
                Snackbar.make(toolbar, "Applied image to '" + savedChar + "'", Snackbar.LENGTH_SHORT).show();
            }

            autoCompilePreview();
            updateSingleCropPreview();
        } else {
            if (textImageStatus != null) {
                textImageStatus.setVisibility(View.VISIBLE);
                textImageStatus.setText("⚠ No character outline detected. Try adjusting box with ◀ / ▶.");
            }
        }
    }

    private void saveDrawnGlyph() {
        if (fontCanvas == null || fontCanvas.isEmpty()) {
            Toast.makeText(this, "Please draw a character on the canvas first!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentDrawCharacter == null) return;

        Bitmap bmp = fontCanvas.getDrawingBitmap();
        if (bmp != null) {
            imageRepository.saveImageBitmap(bmp, getFontName(), currentDrawCharacter.uId);
            String pathD = openCVEdgeDetector.extractPathDFromBitmap(bmp);

            if (!pathD.isEmpty()) {
                String unicode = "&#x" + Integer.toHexString(currentDrawCharacter.unicode) + ";";
                fontMaker.addCustomGlyph(currentDrawCharacter.uId, unicode, pathD, 800);
                generatedGlyphsCount++;
                currentDrawCharacter.isGenerated = true;
                notifyCharacterAdapters();
                updateGlyphCountStatus();

                char savedChar = currentDrawCharacter.character;

                // Auto-advance to the next character in sequence
                int currentIndex = -1;
                for (int i = 0; i < characterList.size(); i++) {
                    if (characterList.get(i).uId.equals(currentDrawCharacter.uId)) {
                        currentIndex = i;
                        break;
                    }
                }

                if (currentIndex >= 0 && currentIndex + 1 < characterList.size()) {
                    CharacterItem nextItem = characterList.get(currentIndex + 1);
                    selectDrawCharacter(nextItem);
                    if (drawCharacterAdapter != null) {
                        drawCharacterAdapter.setSelectedCharacter(nextItem.character);
                    }
                    RecyclerView recyclerDraw = findViewById(R.id.recycler_draw_characters);
                    if (recyclerDraw != null) {
                        recyclerDraw.smoothScrollToPosition(currentIndex + 1);
                    }
                    if (textDrawStatus != null) {
                        textDrawStatus.setVisibility(View.VISIBLE);
                        textDrawStatus.setText("✓ Saved '" + savedChar + "'! Auto-advanced to '" + nextItem.character + "'");
                    }
                    Snackbar.make(toolbar, "Saved '" + savedChar + "'. Ready for '" + nextItem.character + "'", Snackbar.LENGTH_SHORT).show();
                } else {
                    if (textDrawStatus != null) {
                        textDrawStatus.setVisibility(View.VISIBLE);
                        textDrawStatus.setText("✓ Saved glyph for '" + savedChar + "' (" + currentDrawCharacter.uId + ")");
                    }
                    fontCanvas.clear();
                    fontCanvas.setBackgroundResource(R.drawable.background_white);
                    Snackbar.make(toolbar, "Saved glyph for '" + savedChar + "'", Snackbar.LENGTH_SHORT).show();
                }

                autoCompilePreview();
            } else {
                if (textDrawStatus != null) {
                    textDrawStatus.setVisibility(View.VISIBLE);
                    textDrawStatus.setText("⚠ No clear stroke detected. Draw thicker lines.");
                }
            }
        }
    }

    private void markCharacterGenerated(String name, String unicode) {
        for (CharacterItem item : characterList) {
            if (item.uId.equalsIgnoreCase(name) || String.valueOf(item.character).equalsIgnoreCase(name)) {
                item.isGenerated = true;
                return;
            }
        }
    }

    private void notifyCharacterAdapters() {
        if (drawCharacterAdapter != null) drawCharacterAdapter.notifyDataSetChanged();
        if (imageCharacterAdapter != null) imageCharacterAdapter.notifyDataSetChanged();
        if (glyphInspectorAdapter != null) glyphInspectorAdapter.notifyDataSetChanged();
    }

    private void updateGlyphCountStatus() {
        int count = 0;
        for (CharacterItem item : characterList) {
            if (item.isGenerated || fontMaker.hasGlyph(item.uId)) {
                item.isGenerated = true;
                count++;
            }
        }
        if (textAiGlyphCount != null) {
            textAiGlyphCount.setText("Font Glyphs Ready: " + count + " / " + characterList.size());
        }
        if (textFontStatus != null) {
            textFontStatus.setText("Font '" + getFontName() + "' has " + count + " glyphs ready for export.");
        }
    }

    // Auto-compiles in background to provide instant live typing in playground
    private void autoCompilePreview() {
        TrueTypeGenerator.exportFontLocally(this, getFontName(), fontMaker, new TrueTypeGenerator.OnExportListener() {
            @Override
            public void onSuccess(File ttfFile, File otfFile, File svgFile) {
                latestExportedFile = ttfFile;
                try {
                    currentTypeface = Typeface.createFromFile(ttfFile);
                    if (inputPreviewTest != null && currentTypeface != null) {
                        inputPreviewTest.setTypeface(currentTypeface);
                    }
                    if (glyphInspectorAdapter != null) {
                        glyphInspectorAdapter.setTypeface(currentTypeface);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Typeface preview error: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(String error) {}
        });
    }

    private void exportFontLocally() {
        String format = (toggleFormat != null && toggleFormat.getCheckedButtonId() == R.id.button_format_otf) ? "otf" : "ttf";
        String currentName = getFontName();

        showProgress(true);
        if (textFontStatus != null) {
            textFontStatus.setText("Exporting " + format.toUpperCase() + " locally on device...");
        }

        // Asynchronously compile full multi-style ZIP package with OFL License & README
        new Thread(() -> {
            try {
                TrueTypeGenerator.exportCompleteFontPackageZip(MainActivity.this, currentName, fontMaker);
            } catch (Exception e) {
                Log.w(TAG, "ZIP package background notice: " + e.getMessage());
            }
        }).start();

        TrueTypeGenerator.exportFontLocally(this, currentName, fontMaker, new TrueTypeGenerator.OnExportListener() {
            @Override
            public void onSuccess(File ttfFile, File otfFile, File svgFile) {
                showProgress(false);
                File primaryFile = "otf".equalsIgnoreCase(format) ? otfFile : ttfFile;
                latestExportedFile = primaryFile;

                try {
                    currentTypeface = Typeface.createFromFile(primaryFile);
                    if (inputPreviewTest != null && currentTypeface != null) {
                        inputPreviewTest.setTypeface(currentTypeface);
                    }
                    if (glyphInspectorAdapter != null) {
                        glyphInspectorAdapter.setTypeface(currentTypeface);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Typeface preview error: " + e.getMessage());
                }

                if (textFontStatus != null) {
                    textFontStatus.setText("✓ " + format.toUpperCase() + " exported: " + primaryFile.getName() + " (" + (primaryFile.length() / 1024) + " KB)");
                }

                Toast.makeText(MainActivity.this, "✓ " + format.toUpperCase() + " & styles exported! Showing in Font Manager...", Toast.LENGTH_SHORT).show();
                openFontManager();
            }

            @Override
            public void onFailure(String error) {
                showProgress(false);
                if (textFontStatus != null) {
                    textFontStatus.setText("⚠ Export failed: " + error);
                }
                Snackbar.make(toolbar, "Export failed: " + error, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void shareCurrentFont() {
        if (latestExportedFile == null || !latestExportedFile.exists()) {
            Toast.makeText(this, "Please export the font first before sharing!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", latestExportedFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("font/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share " + latestExportedFile.getName()));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot share font: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openFontManager() {
        Intent intent = new Intent(this, FontManagerActivity.class);
        startActivity(intent);
    }

    private void showImageImportOptions() {
        final String[] options = {
            "Single Font Sheet / Letter Image",
            "Multiple Letter Images (Batch Auto-Assign)",
            "Extract from ZIP Archive (.zip)"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle("Import Character Images")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pickSingleImage();
                    } else if (which == 1) {
                        pickMultipleImages();
                    } else {
                        pickZipArchive();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickSingleImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    private void pickMultipleImages() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_PICK_MULTIPLE_IMAGES);
    }

    private void pickZipArchive() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {"application/zip", "application/x-zip-compressed", "application/x-zip", "multipart/x-zip"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, REQUEST_PICK_ZIP);
    }

    private void showGeminiExpandDialog() {
        String apiKey = APIUtil.getGeminiApiKey(this);
        if (apiKey.isEmpty()) {
            showApiSettingsDialog();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Gemini AI Style Expand")
                .setMessage("Generate and expand this letter's style across all characters (A-Z, a-z, 0-9) using Gemini AI?")
                .setPositiveButton("Expand All", (dialog, which) -> {
                    generateFontWithGemini(currentReferenceBitmap);
                })
                .setNeutralButton("AI Studio", (dialog, which) -> {
                    if (viewPager != null) {
                        viewPager.setCurrentItem(0, true);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickCustomModelFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_PICK_MODEL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQUEST_PICK_IMAGE) {
            if (data.getClipData() != null) {
                handleMultipleImageImport(data.getClipData());
            } else if (data.getData() != null) {
                Uri imageUri = data.getData();
                String fileName = getFileNameFromUri(imageUri).toLowerCase(Locale.ROOT);
                if (fileName.endsWith(".zip")) {
                    handleZipArchiveImport(imageUri);
                } else {
                    loadSingleReferenceImage(imageUri);
                }
            }
        } else if (requestCode == REQUEST_PICK_MULTIPLE_IMAGES) {
            if (data.getClipData() != null) {
                handleMultipleImageImport(data.getClipData());
            } else if (data.getData() != null) {
                List<Uri> singleList = new ArrayList<>();
                singleList.add(data.getData());
                startBatchImportUris(singleList);
            }
        } else if (requestCode == REQUEST_PICK_ZIP) {
            if (data.getData() != null) {
                handleZipArchiveImport(data.getData());
            }
        } else if (requestCode == REQUEST_PICK_MODEL) {
            Uri modelUri = data.getData();
            if (modelUri != null) {
                String modelName = getFileNameFromUri(modelUri);
                OpenCVEdgeDetector.setActiveModel(OpenCVEdgeDetector.MODEL_NEURAL_TRACER);
                Toast.makeText(this, "Loaded model: " + modelName, Toast.LENGTH_LONG).show();
                Snackbar.make(toolbar, "Offline Model: " + modelName, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void loadSingleReferenceImage(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            currentReferenceBitmap = BitmapFactory.decodeStream(inputStream);
            if (imageReferencePreview != null) {
                imageReferencePreview.setImageBitmap(currentReferenceBitmap);
            }
            manualBoxIndexOffset = 0;
            currentSheetBoxes.clear();
            scanSheetBoxesAsync(() -> {
                updateSingleCropPreview();
                if (textImageStatus != null && !currentSheetBoxes.isEmpty()) {
                    textImageStatus.setVisibility(View.VISIBLE);
                    textImageStatus.setText("✓ Found " + currentSheetBoxes.size() + " characters! Tap 'Trace it' or set one-by-one.");
                }
            });
            if (textImageStatus != null) {
                textImageStatus.setVisibility(View.VISIBLE);
                textImageStatus.setText("Image loaded. Scanning character cells...");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleMultipleImageImport(ClipData clipData) {
        List<Uri> uris = new ArrayList<>();
        for (int i = 0; i < clipData.getItemCount(); i++) {
            ClipData.Item item = clipData.getItemAt(i);
            if (item != null && item.getUri() != null) {
                uris.add(item.getUri());
            }
        }
        startBatchImportUris(uris);
    }

    private void startBatchImportUris(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        showProgress(true);
        if (cardImageProgress != null) {
            cardImageProgress.setVisibility(View.VISIBLE);
            progressImageDeterminate.setProgress(0);
            textImageProgressPercent.setText("0%");
            textImageProgressLabel.setText("Importing " + uris.size() + " letter images...");
        }

        BatchImageFontImporter.importMultipleImageUris(
                this,
                uris,
                characterList,
                fontSheetDetector,
                openCVEdgeDetector,
                fontMaker,
                new BatchImageFontImporter.OnBatchImportListener() {
                    @Override
                    public void onProgress(int current, int total, char character, String status) {
                        int percent = (int) ((current / (float) total) * 100);
                        if (progressImageDeterminate != null) progressImageDeterminate.setProgress(percent);
                        if (textImageProgressPercent != null) textImageProgressPercent.setText(percent + "%");
                        if (textImageProgressLabel != null) textImageProgressLabel.setText(status);
                    }

                    @Override
                    public void onComplete(int successfulCount, int totalCount) {
                        showProgress(false);
                        if (cardImageProgress != null) cardImageProgress.setVisibility(View.GONE);
                        notifyCharacterAdapters();
                        updateGlyphCountStatus();
                        autoCompilePreview();

                        String msg = "✓ Successfully imported " + successfulCount + " of " + totalCount + " character glyphs!";
                        if (textImageStatus != null) {
                            textImageStatus.setVisibility(View.VISIBLE);
                            textImageStatus.setText(msg);
                        }
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        Snackbar.make(toolbar, msg, Snackbar.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String error) {
                        showProgress(false);
                        if (cardImageProgress != null) cardImageProgress.setVisibility(View.GONE);
                        if (textImageStatus != null) {
                            textImageStatus.setVisibility(View.VISIBLE);
                            textImageStatus.setText("⚠ " + error);
                        }
                        Snackbar.make(toolbar, "Batch Import Error: " + error, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void handleZipArchiveImport(Uri zipUri) {
        showProgress(true);
        if (cardImageProgress != null) {
            cardImageProgress.setVisibility(View.VISIBLE);
            progressImageDeterminate.setProgress(0);
            textImageProgressPercent.setText("0%");
            textImageProgressLabel.setText("Extracting ZIP archive...");
        }

        BatchImageFontImporter.importZipArchive(
                this,
                zipUri,
                characterList,
                fontSheetDetector,
                openCVEdgeDetector,
                fontMaker,
                new BatchImageFontImporter.OnBatchImportListener() {
                    @Override
                    public void onProgress(int current, int total, char character, String status) {
                        int percent = (total > 0) ? (int) ((current / (float) total) * 100) : 0;
                        if (progressImageDeterminate != null) progressImageDeterminate.setProgress(percent);
                        if (textImageProgressPercent != null) textImageProgressPercent.setText(percent + "%");
                        if (textImageProgressLabel != null) textImageProgressLabel.setText(status);
                    }

                    @Override
                    public void onComplete(int successfulCount, int totalCount) {
                        showProgress(false);
                        if (cardImageProgress != null) cardImageProgress.setVisibility(View.GONE);
                        notifyCharacterAdapters();
                        updateGlyphCountStatus();
                        autoCompilePreview();

                        String msg = "✓ Extracted & imported " + successfulCount + " of " + totalCount + " glyphs from ZIP!";
                        if (textImageStatus != null) {
                            textImageStatus.setVisibility(View.VISIBLE);
                            textImageStatus.setText(msg);
                        }
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        Snackbar.make(toolbar, msg, Snackbar.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String error) {
                        showProgress(false);
                        if (cardImageProgress != null) cardImageProgress.setVisibility(View.GONE);
                        if (textImageStatus != null) {
                            textImageStatus.setVisibility(View.VISIBLE);
                            textImageStatus.setText("⚠ " + error);
                        }
                        Snackbar.make(toolbar, "ZIP Import Error: " + error, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result != null ? result : "file";
    }

    private void toggleThemeMode() {
        int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        int targetMode = isNight ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;

        getSharedPreferences("font_studio_prefs", MODE_PRIVATE)
                .edit()
                .putInt("night_mode", targetMode)
                .apply();

        AppCompatDelegate.setDefaultNightMode(targetMode);
        Toast.makeText(this, isNight ? "Light mode enabled" : "Dark mode enabled", Toast.LENGTH_SHORT).show();
    }

    private void showModelLibraryDialog() {
        final String[] models = {
            "Adaptive Otsu",
            "Spline Tracer",
            "Neural Vectorizer"
        };
        int current = OpenCVEdgeDetector.getActiveModel();

        new MaterialAlertDialogBuilder(this)
                .setTitle("Offline Model Library")
                .setSingleChoiceItems(models, current, (dialog, which) -> {
                    OpenCVEdgeDetector.setActiveModel(which);
                    dialog.dismiss();
                    String name = which == 0 ? "Adaptive Otsu" : (which == 1 ? "Spline Tracer" : "Neural Vectorizer");
                    Snackbar.make(toolbar, "Active Tracer: " + name, Snackbar.LENGTH_SHORT).show();
                })
                .setNeutralButton("Choose", (dialog, which) -> {
                    pickCustomModelFile();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLanguageSelectionDialog() {
        List<LanguageScript> scripts = LanguageScript.getAvailableScripts();
        String[] names = new String[scripts.size()];
        int selectedIndex = 0;
        for (int i = 0; i < scripts.size(); i++) {
            names[i] = scripts.get(i).displayName;
            if (currentScript != null && currentScript.id.equals(scripts.get(i).id)) {
                selectedIndex = i;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Language & Script")
                .setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
                    switchLanguageScript(scripts.get(which));
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSaveSessionDialog() {
        TextInputEditText inputProject = new TextInputEditText(this);
        inputProject.setText(getFontName());
        inputProject.setHint("Project Name");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Save Project Session")
                .setMessage("Save current font session, custom glyphs, and configurations locally on device:")
                .setView(inputProject)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = inputProject.getText() != null ? inputProject.getText().toString().trim() : "";
                    if (name.isEmpty()) name = getFontName();
                    boolean ok = ProjectSessionManager.saveSession(this, name, getFontName(), currentScript != null ? currentScript.id : "latin", fontMaker.customGlyphsMap);
                    if (ok) {
                        Snackbar.make(toolbar, "✓ Project session '" + name + "' saved!", Snackbar.LENGTH_SHORT).show();
                    } else {
                        Snackbar.make(toolbar, "⚠ Failed to save session.", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLoadSessionDialog() {
        List<ProjectSessionManager.ProjectSession> sessions = ProjectSessionManager.listSessions(this);
        if (sessions.isEmpty()) {
            Toast.makeText(this, "No saved project sessions found.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] sessionNames = new String[sessions.size()];
        for (int i = 0; i < sessions.size(); i++) {
            ProjectSessionManager.ProjectSession s = sessions.get(i);
            sessionNames[i] = s.projectName + " (" + s.glyphCount + " glyphs)";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Load Project Session")
                .setItems(sessionNames, (dialog, which) -> {
                    ProjectSessionManager.ProjectSession selected = sessions.get(which);
                    boolean ok = ProjectSessionManager.loadSession(this, selected.projectName, fontMaker);
                    if (ok) {
                        if (inputFontName != null) {
                            inputFontName.setText(selected.fontName);
                        }
                        // Check if language script matches
                        for (LanguageScript ls : LanguageScript.getAvailableScripts()) {
                            if (ls.id.equalsIgnoreCase(selected.languageScriptId)) {
                                switchLanguageScript(ls);
                                break;
                            }
                        }
                        // Mark loaded glyphs as generated
                        for (CharacterItem item : characterList) {
                            if (fontMaker.hasGlyph(item.uId)) {
                                item.isGenerated = true;
                            }
                        }
                        updateGlyphCountStatus();
                        notifyCharacterAdapters();
                        autoCompilePreview();
                        Snackbar.make(toolbar, "✓ Loaded project '" + selected.projectName + "'", Snackbar.LENGTH_SHORT).show();
                    } else {
                        Snackbar.make(toolbar, "⚠ Failed to load session.", Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean onToolbarMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_theme_toggle) {
            toggleThemeMode();
            return true;
        } else if (id == R.id.action_language) {
            showLanguageSelectionDialog();
            return true;
        } else if (id == R.id.action_model_library) {
            showModelLibraryDialog();
            return true;
        } else if (id == R.id.action_save_session) {
            showSaveSessionDialog();
            return true;
        } else if (id == R.id.action_load_session) {
            showLoadSessionDialog();
            return true;
        } else if (id == R.id.action_font_manager) {
            openFontManager();
            return true;
        } else if (id == R.id.action_unlock_workflow) {
            showWorkflowDialog();
            return true;
        } else if (id == R.id.action_settings) {
            showApiSettingsDialog();
            return true;
        }
        return false;
    }

    private void showWorkflowDialog() {
        String currentModeName;
        switch (currentWorkflowMode) {
            case WORKFLOW_AI_STUDIO: currentModeName = "AI Studio (Gemini)"; break;
            case WORKFLOW_IMAGE_SHEET: currentModeName = "Image Sheet Tracing"; break;
            case WORKFLOW_CANVAS_DRAW: currentModeName = "Canvas Freehand Draw"; break;
            default: currentModeName = "Unlocked (Any Studio)"; break;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Studio Workflow Lock")
                .setMessage("Current Project Workflow: " + currentModeName + "\n\n" +
                        "For font metric and visual consistency, each project can be dedicated to one studio workflow (freehand editing in Canvas Draw and testing in Playground always remain enabled). Would you like to unlock all studios for this project?")
                .setPositiveButton("Unlock All Studios", (d, w) -> {
                    setProjectWorkflow(WORKFLOW_UNLOCKED);
                    Snackbar.make(toolbar, "All creation studios unlocked!", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("Keep Locked", null)
                .show();
    }

    private final List<String> cachedGeminiModels = new ArrayList<>();

    private void showApiSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_api_settings, null);
        TextInputEditText inputGemini = dialogView.findViewById(R.id.input_gemini_key);
        MaterialButton buttonValidate = dialogView.findViewById(R.id.button_validate_gemini_key);
        TextView textStatus = dialogView.findViewById(R.id.text_api_validation_status);
        MaterialButton buttonSelectModel = dialogView.findViewById(R.id.button_select_gemini_model);
        MaterialButton buttonActivate = dialogView.findViewById(R.id.button_activate_gemini);

        inputGemini.setText(APIUtil.getGeminiApiKey(this));
        final String[] currentModel = { APIUtil.getGeminiModel(this) };
        buttonSelectModel.setText("Model: " + currentModel[0]);

        buttonValidate.setOnClickListener(v -> {
            String key = inputGemini.getText() != null ? inputGemini.getText().toString().trim() : "";
            if (key.isEmpty()) {
                textStatus.setText("⚠ Please enter an API key.");
                textStatus.setTextColor(0xFFD32F2F);
                return;
            }
            textStatus.setText("Testing API key & querying models...");
            textStatus.setTextColor(0xFF1976D2);
            geminiFontService.validateApiKey(key, new GeminiFontService.ApiValidationListener() {
                @Override
                public void onSuccess(List<String> availableModels) {
                    cachedGeminiModels.clear();
                    cachedGeminiModels.addAll(availableModels);
                    textStatus.setText("✓ Key Valid: " + availableModels.size() + " models available");
                    textStatus.setTextColor(0xFF388E3C);
                }

                @Override
                public void onFailure(String error) {
                    textStatus.setText("✗ " + error);
                    textStatus.setTextColor(0xFFD32F2F);
                }
            });
        });

        buttonSelectModel.setOnClickListener(v -> {
            List<String> options = new ArrayList<>();
            options.add(APIUtil.MODEL_AUTO);
            options.add(APIUtil.MODEL_RANDOM);

            if (!cachedGeminiModels.isEmpty()) {
                for (String m : cachedGeminiModels) {
                    if (!options.contains(m)) options.add(m);
                }
            } else {
                options.add("gemini-3.8-flash");
                options.add("gemini-3.8-pro");
                options.add("gemini-3.8-flash-lite");
                options.add("gemini-3.5-flash");
                options.add("gemini-3.5-pro");
                options.add("gemini-3.0-flash");
                options.add("gemini-3.0-pro");
                options.add("gemini-2.5-flash");
                options.add("gemini-2.5-pro");
                options.add("gemini-2.0-flash");
                options.add("gemini-2.0-pro");
                options.add("gemini-2.0-flash-lite");
                options.add("gemini-1.5-flash");
                options.add("gemini-1.5-pro");
                options.add("gemini-1.5-flash-8b");
                options.add("gemini-1.0-pro");
            }

            final String[] items = options.toArray(new String[0]);
            int checkedItem = 0;
            for (int i = 0; i < items.length; i++) {
                if (items[i].equalsIgnoreCase(currentModel[0])) {
                    checkedItem = i;
                    break;
                }
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Select Gemini Model (1.0 - 3.8)")
                    .setSingleChoiceItems(items, checkedItem, (d, which) -> {
                        currentModel[0] = items[which];
                        buttonSelectModel.setText("Model: " + currentModel[0]);
                        APIUtil.setGeminiModel(this, currentModel[0]);
                        d.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        buttonActivate.setOnClickListener(v -> {
            String geminiKey = inputGemini.getText() != null ? inputGemini.getText().toString().trim() : "";
            APIUtil.setGeminiApiKey(this, geminiKey);
            APIUtil.setGeminiModel(this, currentModel[0]);
            Snackbar.make(toolbar, "Gemini configuration saved (" + currentModel[0] + ")!", Snackbar.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showManualCropDialog(CharacterItem item) {
        if (item == null) return;
        if (currentReferenceBitmap == null) {
            try {
                currentReferenceBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.a_character_image);
            } catch (Exception ignored) {}
        }
        if (currentReferenceBitmap == null) {
            Toast.makeText(this, "Please select an image or sheet first.", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_crop, null);
        TextView textTitle = dialogView.findViewById(R.id.text_manual_crop_title);
        ManualCropOverlayView cropOverlay = dialogView.findViewById(R.id.view_manual_crop_overlay);
        MaterialButton buttonCancel = dialogView.findViewById(R.id.button_manual_crop_cancel);
        MaterialButton buttonApply = dialogView.findViewById(R.id.button_manual_crop_apply);

        final Slider sliderX = dialogView.findViewById(R.id.slider_crop_x);
        final Slider sliderY = dialogView.findViewById(R.id.slider_crop_y);
        final Slider sliderSize = dialogView.findViewById(R.id.slider_crop_size);
        MaterialButton buttonZoomIn = dialogView.findViewById(R.id.button_crop_zoom_in);
        MaterialButton buttonZoomOut = dialogView.findViewById(R.id.button_crop_zoom_out);
        MaterialButton buttonZoomReset = dialogView.findViewById(R.id.button_crop_zoom_reset);

        textTitle.setText("Manual Crop Adjust: '" + item.character + "' (" + item.uId + ")");

        org.opencv.core.Rect initialBox = null;
        int targetCharIndex = getCharacterIndex(item);
        int mappedBoxIndex = targetCharIndex + manualBoxIndexOffset;
        if (!currentSheetBoxes.isEmpty() && mappedBoxIndex >= 0 && mappedBoxIndex < currentSheetBoxes.size()) {
            initialBox = currentSheetBoxes.get(mappedBoxIndex);
        }

        cropOverlay.setBitmap(currentReferenceBitmap, initialBox);

        // Bi-directional slider <-> overlay synchronization
        final boolean[] isUpdatingFromOverlay = { false };
        final boolean[] isUpdatingFromSliders = { false };

        cropOverlay.setOnCropBoxChangeListener((normX, normY, normSize) -> {
            if (isUpdatingFromSliders[0]) return;
            isUpdatingFromOverlay[0] = true;
            try {
                if (sliderX != null) sliderX.setValue(Math.max(sliderX.getValueFrom(), Math.min(sliderX.getValueTo(), normX)));
                if (sliderY != null) sliderY.setValue(Math.max(sliderY.getValueFrom(), Math.min(sliderY.getValueTo(), normY)));
                if (sliderSize != null) sliderSize.setValue(Math.max(sliderSize.getValueFrom(), Math.min(sliderSize.getValueTo(), normSize)));
            } finally {
                isUpdatingFromOverlay[0] = false;
            }
        });

        if (sliderX != null) {
            sliderX.addOnChangeListener((slider, value, fromUser) -> {
                if (!fromUser || isUpdatingFromOverlay[0]) return;
                isUpdatingFromSliders[0] = true;
                try {
                    cropOverlay.setCropPosition(value, sliderY != null ? sliderY.getValue() : 0.5f);
                } finally {
                    isUpdatingFromSliders[0] = false;
                }
            });
        }
        if (sliderY != null) {
            sliderY.addOnChangeListener((slider, value, fromUser) -> {
                if (!fromUser || isUpdatingFromOverlay[0]) return;
                isUpdatingFromSliders[0] = true;
                try {
                    cropOverlay.setCropPosition(sliderX != null ? sliderX.getValue() : 0.5f, value);
                } finally {
                    isUpdatingFromSliders[0] = false;
                }
            });
        }
        if (sliderSize != null) {
            sliderSize.addOnChangeListener((slider, value, fromUser) -> {
                if (!fromUser || isUpdatingFromOverlay[0]) return;
                isUpdatingFromSliders[0] = true;
                try {
                    cropOverlay.setCropSize(value);
                } finally {
                    isUpdatingFromSliders[0] = false;
                }
            });
        }

        if (buttonZoomIn != null) {
            buttonZoomIn.setOnClickListener(v -> cropOverlay.zoomIn());
        }
        if (buttonZoomOut != null) {
            buttonZoomOut.setOnClickListener(v -> cropOverlay.zoomOut());
        }
        if (buttonZoomReset != null) {
            buttonZoomReset.setOnClickListener(v -> cropOverlay.resetZoom());
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        buttonCancel.setOnClickListener(v -> dialog.dismiss());

        buttonApply.setOnClickListener(v -> {
            Bitmap cropped = cropOverlay.getCroppedBitmap();
            if (cropped == null) {
                Toast.makeText(this, "Could not crop selected area.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save cropped bitmap to image repository for Canvas Draw and socket preview
            try {
                imageRepository.saveImageBitmap(cropped, getFontName(), item.uId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save cropped bitmap for " + item.uId, e);
            }

            String pathD = fontSheetDetector.vectorizeCroppedBitmap(cropped);
            if (pathD.isEmpty()) {
                pathD = openCVEdgeDetector.extractPathDFromBitmap(cropped);
            }
            if (pathD.isEmpty()) {
                pathD = openCVEdgeDetector.buildFallbackPathDFromBitmap(cropped);
            }

            if (!pathD.isEmpty()) {
                String unicode = "&#x" + Integer.toHexString(item.unicode) + ";";
                fontMaker.addCustomGlyph(item.uId, unicode, pathD, 800);
                generatedGlyphsCount++;
                item.isGenerated = true;

                // Ensure item is in active list
                boolean inList = false;
                for (CharacterItem ci : characterList) {
                    if (ci.unicode == item.unicode) {
                        inList = true;
                        ci.isGenerated = true;
                        break;
                    }
                }
                if (!inList) {
                    characterList.add(item);
                }

                selectImageTargetCharacter(item);
                notifyCharacterAdapters();
                updateGlyphCountStatus();
                autoCompilePreview();
                updateSingleCropPreview();

                String msg = "✓ Applied & set glyph for '" + item.character + "'!";
                if (textImageStatus != null) {
                    textImageStatus.setVisibility(View.VISIBLE);
                    textImageStatus.setText(msg);
                }
                Snackbar.make(toolbar, msg, Snackbar.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Could not vectorize character outline. Please try adjusting the crop.", Toast.LENGTH_LONG).show();
            }
        });

        dialog.show();
    }

    public void checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
        }
    }

    // ==========================================
    // ADAPTERS: CHARACTER CHIP & GLYPH INSPECTOR
    // ==========================================
    public interface OnCharacterClickListener {
        void onCharacterClick(CharacterItem item);
    }

    public class CharacterChipAdapter extends RecyclerView.Adapter<CharacterChipAdapter.ChipViewHolder> {
        private final List<CharacterItem> list;
        private final OnCharacterClickListener listener;
        private int selectedIndex = 0;
        private long lastClickTime = 0;
        private int lastClickedPos = -1;

        public CharacterChipAdapter(List<CharacterItem> list, OnCharacterClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        public void setSelectedCharacter(char c) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).character == c) {
                    int oldIdx = selectedIndex;
                    selectedIndex = i;
                    notifyItemChanged(oldIdx);
                    notifyItemChanged(selectedIndex);
                    break;
                }
            }
        }

        public void setSelectedCharacter(CharacterItem item) {
            if (item != null) {
                setSelectedCharacter(item.character);
            }
        }

        public int getPositionForCharacter(char c) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).character == c) return i;
            }
            return -1;
        }

        public int getSelectedIndex() {
            return selectedIndex;
        }

        @NonNull
        @Override
        public ChipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_character_chip, parent, false);
            return new ChipViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ChipViewHolder holder, int position) {
            CharacterItem item = list.get(position);
            holder.textGlyph.setText(String.valueOf(item.character));
            holder.iconStatus.setVisibility(item.isGenerated ? View.VISIBLE : View.INVISIBLE);

            boolean isSelected = (position == selectedIndex);
            int primaryColor = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorPrimary);
            int primaryContainerColor = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorPrimaryContainer);
            int surfaceColor = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorSurface);
            int outlineColor = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOutline);
            int onPrimaryContainer = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOnPrimaryContainer);
            int onSurface = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOnSurface);

            if (isSelected) {
                holder.card.setStrokeWidth(4);
                holder.card.setStrokeColor(primaryColor);
                holder.card.setCardBackgroundColor(primaryContainerColor);
                holder.textGlyph.setTextColor(onPrimaryContainer);
            } else {
                holder.card.setStrokeWidth(1);
                holder.card.setStrokeColor(outlineColor);
                holder.card.setCardBackgroundColor(surfaceColor);
                holder.textGlyph.setTextColor(onSurface);
            }

            holder.itemView.setOnClickListener(v -> {
                long now = android.os.SystemClock.elapsedRealtime();
                int pos = holder.getAdapterPosition();
                boolean isDoubleTap = (now - lastClickTime < 350 && lastClickedPos == pos);
                lastClickTime = now;
                lastClickedPos = pos;

                int oldIdx = selectedIndex;
                selectedIndex = pos;
                notifyItemChanged(oldIdx);
                notifyItemChanged(selectedIndex);
                if (listener != null) listener.onCharacterClick(item);

                if (isDoubleTap) {
                    showManualCropDialog(item);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                showManualCropDialog(item);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ChipViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView textGlyph;
            ImageView iconStatus;

            public ChipViewHolder(@NonNull View itemView) {
                super(itemView);
                card = (MaterialCardView) itemView;
                textGlyph = itemView.findViewById(R.id.text_character_glyph);
                iconStatus = itemView.findViewById(R.id.icon_character_status);
            }
        }
    }

    public class GlyphInspectorAdapter extends RecyclerView.Adapter<GlyphInspectorAdapter.GlyphViewHolder> {
        private final List<CharacterItem> list;
        private Typeface customTypeface;

        public GlyphInspectorAdapter(List<CharacterItem> list) {
            this.list = list;
        }

        public void setTypeface(Typeface tf) {
            this.customTypeface = tf;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public GlyphViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_glyph_cell, parent, false);
            return new GlyphViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull GlyphViewHolder holder, int position) {
            CharacterItem item = list.get(position);
            holder.textPreview.setText(String.valueOf(item.character));
            holder.textCode.setText(String.valueOf(item.unicode));
            if (customTypeface != null && item.isGenerated) {
                holder.textPreview.setTypeface(customTypeface);
            } else {
                holder.textPreview.setTypeface(Typeface.DEFAULT);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class GlyphViewHolder extends RecyclerView.ViewHolder {
            TextView textPreview;
            TextView textCode;

            public GlyphViewHolder(@NonNull View itemView) {
                super(itemView);
                textPreview = itemView.findViewById(R.id.text_glyph_preview);
                textCode = itemView.findViewById(R.id.text_glyph_code);
            }
        }
    }
}

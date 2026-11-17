import os
from PIL import Image, ImageDraw, ImageFont

BASE_DIR = r"K:\3. Android & Flutter Apps\Font Studio"
OUT_DIR = os.path.join(BASE_DIR, "marketing")
os.makedirs(OUT_DIR, exist_ok=True)

LOGO_PATH = os.path.join(BASE_DIR, "app", "src", "main", "res", "drawable", "app_logo.png")

def get_font(name, size):
    font_path = os.path.join(r"C:\Windows\Fonts", name)
    if os.path.exists(font_path):
        try:
            return ImageFont.truetype(font_path, size)
        except Exception:
            pass
    return ImageFont.load_default()

font_h1 = get_font("segoeuib.ttf", 64)
font_h2 = get_font("segoeuib.ttf", 44)
font_h3 = get_font("segoeuib.ttf", 32)
font_body = get_font("segoeui.ttf", 26)
font_body_sm = get_font("segoeui.ttf", 20)
font_mono = get_font("consola.ttf", 24)

def draw_rounded_rect(draw, bbox, radius, fill, outline=None, width=1):
    x0, y0, x1, y1 = bbox
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill, outline=outline, width=width)

def generate_hero_banner():
    width, height = 1200, 630
    img = Image.new("RGBA", (width, height), (11, 15, 25, 255))
    draw = ImageDraw.Draw(img)

    # Ambient background glow
    for r in range(400, 0, -5):
        alpha = int(25 * (1 - r / 400.0))
        draw.ellipse([800 - r, 150 - r, 800 + r, 150 + r], fill=(59, 130, 246, alpha))
        draw.ellipse([200 - r, 450 - r, 200 + r, 450 + r], fill=(139, 92, 246, alpha))

    # Grid accents
    for x in range(0, width, 60):
        draw.line([(x, 0), (x, height)], fill=(255, 255, 255, 8), width=1)
    for y in range(0, height, 60):
        draw.line([(0, y), (width, y)], fill=(255, 255, 255, 8), width=1)

    # App Logo
    if os.path.exists(LOGO_PATH):
        logo = Image.open(LOGO_PATH).convert("RGBA").resize((160, 160), Image.Resampling.LANCZOS)
        # Background card for logo
        draw_rounded_rect(draw, (80, 80, 260, 260), 28, (26, 34, 52, 220), (59, 130, 246, 120), 2)
        img.paste(logo, (90, 90), logo)

    # Header Title & Tagline
    draw.text((290, 90), "Font Studio", font=font_h1, fill=(255, 255, 255, 255))
    draw.text((290, 168), "Professional Typography & Vector Font Creator for Android", font=font_h3, fill=(147, 197, 253, 255))
    draw.text((290, 215), "Turn handwriting sketches, sheet scans, and Gemini AI prompts into installable TTF & OTF fonts.", font=font_body, fill=(156, 163, 175, 255))

    # Feature Badges
    badges = [
        ("✨ Gemini 1.0 - 3.8 AI Studio", (99, 102, 241, 60), (165, 180, 252, 255)),
        ("⚡ OpenCV Auto-Contour Tracing", (16, 185, 129, 60), (110, 231, 183, 255)),
        ("📦 Multi-Image & ZIP Batch Extraction", (245, 158, 11, 60), (253, 230, 138, 255)),
        ("🎨 Precision Bezier Vector Canvas", (236, 72, 153, 60), (249, 168, 212, 255)),
        ("🔤 Interactive Font Playground & Manager", (14, 165, 233, 60), (186, 230, 253, 255)),
        ("💾 TrueType / OpenType & License Zip", (168, 85, 247, 60), (233, 213, 255, 255)),
    ]

    bx, by = 80, 310
    col_w = 520
    for idx, (text, bg_col, text_col) in enumerate(badges):
        x = bx + (idx % 2) * (col_w + 20)
        y = by + (idx // 2) * 80
        draw_rounded_rect(draw, (x, y, x + col_w, y + 62), 16, bg_col, text_col, 1)
        draw.text((x + 24, y + 16), text, font=font_body, fill=text_col)

    # Specimen strip at bottom
    draw_rounded_rect(draw, (80, 555, 1120, 600), 12, (20, 27, 45, 200), (75, 85, 99, 100), 1)
    draw.text((105, 563), "Aa Bb Cc Dd Ee Ff Gg Hh Ii Jj Kk Ll Mm Nn Oo Pp Qq Rr Ss Tt Uu Vv Ww Xx Yy Zz 0123456789", font=font_body_sm, fill=(209, 213, 219, 240))

    banner_path = os.path.join(OUT_DIR, "hero_banner.png")
    img.save(banner_path, "PNG")
    print("Generated:", banner_path)

def generate_mockup_screen(filename, title, subtitle, content_drawer):
    width, height = 1080, 1920
    img = Image.new("RGBA", (width, height), (15, 23, 42, 255))
    draw = ImageDraw.Draw(img)

    # Ambient glow
    for r in range(500, 0, -10):
        draw.ellipse([540 - r, 300 - r, 540 + r, 300 + r], fill=(30, 58, 138, int(20 * (1 - r/500.0))))

    # Top Bar / Marketing Header
    draw.text((80, 80), "FONT STUDIO", font=get_font("segoeuib.ttf", 26), fill=(96, 165, 250, 255))
    draw.text((80, 125), title, font=get_font("segoeuib.ttf", 52), fill=(255, 255, 255, 255))
    draw.text((80, 195), subtitle, font=get_font("segoeui.ttf", 28), fill=(148, 163, 184, 255))

    # Phone Frame Container
    px0, py0, px1, py1 = 90, 270, 990, 1830
    draw_rounded_rect(draw, (px0-6, py0-6, px1+6, py1+6), 46, (30, 41, 59, 255), (51, 65, 85, 255), 2)
    draw_rounded_rect(draw, (px0, py0, px1, py1), 40, (11, 15, 25, 255))

    # Phone Top Status Bar
    draw_rounded_rect(draw, (px0 + 320, py0 + 15, px0 + 580, py0 + 45), 15, (0, 0, 0, 255)) # dynamic island
    draw.text((px0 + 40, py0 + 18), "9:41", font=get_font("segoeuib.ttf", 22), fill=(255, 255, 255, 220))
    draw.text((px1 - 100, py0 + 18), "5G  100%", font=get_font("segoeuib.ttf", 20), fill=(255, 255, 255, 220))

    # App Bar inside phone
    draw_rounded_rect(draw, (px0, py0 + 60, px1, py0 + 150), 0, (26, 34, 52, 255))
    draw.text((px0 + 40, py0 + 88), "Font Studio", font=get_font("segoeuib.ttf", 36), fill=(255, 255, 255, 255))
    draw_rounded_rect(draw, (px1 - 180, py0 + 80, px1 - 30, py0 + 130), 20, (37, 99, 235, 255))
    draw.text((px1 - 150, py0 + 92), "Export", font=get_font("segoeuib.ttf", 24), fill=(255, 255, 255, 255))

    # Draw specific content
    content_drawer(draw, px0, py0 + 160, px1, py1)

    out_path = os.path.join(OUT_DIR, filename)
    img.save(out_path, "PNG")
    print("Generated:", out_path)

def draw_ai_studio_content(draw, x0, y0, x1, y1):
    # Tab Row
    draw_rounded_rect(draw, (x0 + 30, y0 + 15, x1 - 30, y0 + 75), 16, (20, 27, 45, 255))
    draw_rounded_rect(draw, (x0 + 35, y0 + 20, x0 + 245, y0 + 70), 12, (37, 99, 235, 255))
    draw.text((x0 + 70, y0 + 30), "AI Studio", font=font_body, fill=(255, 255, 255, 255))
    draw.text((x0 + 300, y0 + 30), "Image Studio", font=font_body, fill=(156, 163, 175, 255))
    draw.text((x0 + 560, y0 + 30), "Draw Canvas", font=font_body, fill=(156, 163, 175, 255))
    draw.text((x0 + 780, y0 + 30), "Playground", font=font_body, fill=(156, 163, 175, 255))

    # Model selector card
    draw_rounded_rect(draw, (x0 + 30, y0 + 105, x1 - 30, y0 + 225), 20, (26, 34, 52, 255), (59, 130, 246, 100), 1)
    draw.text((x0 + 55, y0 + 125), "Gemini AI Model Engine", font=font_body, fill=(147, 197, 253, 255))
    draw.text((x0 + 55, y0 + 165), "Gemini 2.0 Flash • Multi-Candidate Fallback (1.0 to 3.8)", font=get_font("segoeui.ttf", 22), fill=(209, 213, 219, 255))

    # Prompt card
    draw_rounded_rect(draw, (x0 + 30, y0 + 250, x1 - 30, y0 + 440), 20, (26, 34, 52, 255), (75, 85, 99, 120), 1)
    draw.text((x0 + 55, y0 + 270), "Font Style Prompt", font=font_body, fill=(147, 197, 253, 255))
    draw_rounded_rect(draw, (x0 + 55, y0 + 315, x1 - 55, y0 + 415), 14, (15, 23, 42, 255), (51, 65, 85, 200), 1)
    draw.text((x0 + 75, y0 + 340), "Modern clean high-contrast sans-serif vector font with geometric curves", font=get_font("segoeui.ttf", 23), fill=(241, 245, 249, 255))

    # Progress Card with real ETA Ticker
    draw_rounded_rect(draw, (x0 + 30, y0 + 465, x1 - 30, y0 + 600), 20, (26, 34, 52, 255), (37, 99, 235, 180), 2)
    draw.text((x0 + 55, y0 + 485), "Generating Typography • ETA: 6s", font=get_font("segoeuib.ttf", 26), fill=(147, 197, 253, 255))
    draw.text((x1 - 130, y0 + 485), "76%", font=get_font("segoeuib.ttf", 26), fill=(96, 165, 250, 255))
    # Determinate Progress Bar
    draw_rounded_rect(draw, (x0 + 55, y0 + 540, x1 - 55, y0 + 566), 13, (15, 23, 42, 255))
    bar_fill_w = int((x1 - x0 - 110) * 0.76)
    draw_rounded_rect(draw, (x0 + 55, y0 + 540, x0 + 55 + bar_fill_w, y0 + 566), 13, (37, 99, 235, 255))

    # Live Specimen Generated Sheet Preview
    draw_rounded_rect(draw, (x0 + 30, y0 + 625, x1 - 30, y1 - 40), 20, (255, 255, 255, 255), (203, 213, 225, 255), 2)
    draw.text((x0 + 55, y0 + 645), "AI Specimen Sheet Preview (Solid White 16:9 • High Precision)", font=get_font("segoeuib.ttf", 20), fill=(71, 85, 105, 255))

    # Render simulated clean glyph specimen in grid inside white sheet
    letters = [
        "A B C D E F G H I J K L M",
        "N O P Q R S T U V W X Y Z",
        "a b c d e f g h i j k l m",
        "n o p q r s t u v w x y z",
        "0 1 2 3 4 5 6 7 8 9 ! ? @ #"
    ]
    font_specimen = get_font("arialbd.ttf", 36)
    for i, line in enumerate(letters):
        draw.text((x0 + 65, y0 + 720 + i * 110), line, font=font_specimen, fill=(0, 0, 0, 255))

def draw_image_studio_content(draw, x0, y0, x1, y1):
    # Action Buttons: Multi-Image & ZIP Batch Extraction
    draw_rounded_rect(draw, (x0 + 30, y0 + 20, (x0 + x1)//2 - 15, y0 + 130), 18, (37, 99, 235, 255))
    draw.text((x0 + 60, y0 + 45), "📂 Import Images", font=font_h3, fill=(255, 255, 255, 255))
    draw.text((x0 + 60, y0 + 90), "Multi-image socket mapping", font=get_font("segoeui.ttf", 20), fill=(219, 234, 254, 255))

    draw_rounded_rect(draw, ((x0 + x1)//2 + 15, y0 + 20, x1 - 30, y0 + 130), 18, (147, 51, 234, 255))
    draw.text(((x0 + x1)//2 + 45, y0 + 45), "📦 Extract from ZIP", font=font_h3, fill=(255, 255, 255, 255))
    draw.text(((x0 + x1)//2 + 45, y0 + 90), "Auto-unzip & contour trace", font=get_font("segoeui.ttf", 20), fill=(243, 232, 255, 255))

    # Sheet Image Container
    draw_rounded_rect(draw, (x0 + 30, y0 + 155, x1 - 30, y0 + 720), 20, (255, 255, 255, 255), (148, 163, 184, 255), 2)
    # Simulated scanned handwriting / sheet
    draw.text((x0 + 55, y0 + 175), "Scanned Character Sheet • OpenCV Contour Extraction", font=get_font("segoeuib.ttf", 22), fill=(51, 65, 85, 255))

    # Grid of extracted sockets
    chars = [
        ['অ', 'আ', 'ই', 'ঈ', 'উ'],
        ['ঋ', 'এ', 'ঐ', 'ও', 'ঔ'],
        ['ক', 'খ', 'গ', 'ঘ', 'ঙ'],
        ['চ', 'ছ', 'জ', 'ঝ', 'ঞ']
    ]
    cell_w = (x1 - x0 - 100) // 5
    for r in range(4):
        for c in range(5):
            cx = x0 + 50 + c * cell_w
            cy = y0 + 230 + r * 110
            draw_rounded_rect(draw, (cx, cy, cx + cell_w - 10, cy + 95), 10, (248, 250, 252, 255), (16, 185, 129, 200), 2)
            draw.text((cx + 25, cy + 20), chars[r][c], font=get_font("segoeui.ttf", 36), fill=(15, 23, 42, 255))
            draw.text((cx + cell_w - 30, cy + 68), "✓", font=get_font("segoeuib.ttf", 18), fill=(16, 185, 129, 255))

    # Manual Selection Precision Overlay Box
    draw_rounded_rect(draw, (x0 + 30, y0 + 745, x1 - 30, y1 - 40), 20, (26, 34, 52, 255), (59, 130, 246, 120), 2)
    draw.text((x0 + 55, y0 + 770), "Manual Crop & Micro-Adjustment", font=font_h3, fill=(147, 197, 253, 255))
    draw.text((x0 + 55, y0 + 820), "Continuous horizontal, vertical & size sliders with pinch-to-zoom", font=font_body, fill=(209, 213, 219, 255))

    # Sliders preview
    slider_labels = ["Horizontal Shift (X)", "Vertical Shift (Y)", "Selection Box Size"]
    for i, label in enumerate(slider_labels):
        sy = y0 + 890 + i * 110
        draw.text((x0 + 55, sy), label, font=get_font("segoeui.ttf", 22), fill=(156, 163, 175, 255))
        draw_rounded_rect(draw, (x0 + 55, sy + 35, x1 - 55, sy + 47), 6, (51, 65, 85, 255))
        sw = int((x1 - x0 - 110) * (0.35 + i * 0.22))
        draw_rounded_rect(draw, (x0 + 55, sy + 35, x0 + 55 + sw, sy + 47), 6, (59, 130, 246, 255))
        draw.ellipse([x0 + 55 + sw - 12, sy + 29, x0 + 55 + sw + 12, sy + 53], fill=(255, 255, 255, 255))

    # Apply Button
    draw_rounded_rect(draw, (x0 + 55, y1 - 130, x1 - 55, y1 - 65), 16, (16, 185, 129, 255))
    draw.text(((x0 + x1)//2 - 40, y1 - 110), "Apply", font=font_h3, fill=(255, 255, 255, 255))

def draw_canvas_studio_content(draw, x0, y0, x1, y1):
    # Canvas Drawing Area
    draw_rounded_rect(draw, (x0 + 30, y0 + 20, x1 - 30, y0 + 820), 20, (255, 255, 255, 255), (148, 163, 184, 255), 2)
    # Typography guidelines
    draw.line([(x0 + 50, y0 + 220), (x1 - 50, y0 + 220)], fill=(239, 68, 68, 140), width=2) # Cap height
    draw.text((x0 + 60, y0 + 185), "Cap Height (800)", font=get_font("segoeui.ttf", 18), fill=(239, 68, 68, 200))

    draw.line([(x0 + 50, y0 + 440), (x1 - 50, y0 + 440)], fill=(59, 130, 246, 140), width=2) # X height
    draw.text((x0 + 60, y0 + 405), "X-Height (500)", font=get_font("segoeui.ttf", 18), fill=(59, 130, 246, 200))

    draw.line([(x0 + 50, y0 + 660), (x1 - 50, y0 + 660)], fill=(16, 185, 129, 180), width=3) # Baseline
    draw.text((x0 + 60, y0 + 625), "Baseline (0)", font=get_font("segoeui.ttf", 18), fill=(16, 185, 129, 220))

    # Beautiful Bezier vector letter "R" drawn on canvas
    draw.text((x0 + 280, y0 + 220), "R", font=get_font("timesbd.ttf", 360), fill=(15, 23, 42, 255))

    # Precision Toolbar
    draw_rounded_rect(draw, (x0 + 30, y0 + 855, x1 - 30, y0 + 965), 18, (26, 34, 52, 255))
    tools = ["✏️ Pen (Bezier)", "📏 Auto-Line", "↩️ Undo", "🧹 Clear", "💾 Save Glyph"]
    tw = (x1 - x0 - 90) // len(tools)
    for i, t in enumerate(tools):
        tx = x0 + 45 + i * tw
        draw_rounded_rect(draw, (tx, y0 + 875, tx + tw - 10, y0 + 945), 12, (37, 99, 235, 200) if i==0 else (51, 65, 85, 200))
        draw.text((tx + 12, y0 + 895), t, font=get_font("segoeui.ttf", 18), fill=(255, 255, 255, 255))

    # Character Socket Carousel
    draw_rounded_rect(draw, (x0 + 30, y0 + 995, x1 - 30, y1 - 40), 20, (26, 34, 52, 255))
    draw.text((x0 + 55, y0 + 1020), "Active Socket: Character 'R' (uni52)", font=font_h3, fill=(147, 197, 253, 255))

    chars = ["P", "Q", "R", "S", "T", "U", "V"]
    cw = (x1 - x0 - 100) // len(chars)
    for i, ch in enumerate(chars):
        cx = x0 + 50 + i * cw
        cy = y0 + 1085
        is_sel = (ch == "R")
        draw_rounded_rect(draw, (cx, cy, cx + cw - 10, cy + 90), 12, (37, 99, 235, 255) if is_sel else (15, 23, 42, 255), (96, 165, 250, 255) if is_sel else (75, 85, 99, 150), 2 if is_sel else 1)
        draw.text((cx + 25, cy + 20), ch, font=get_font("segoeuib.ttf", 36), fill=(255, 255, 255, 255))

def draw_font_manager_content(draw, x0, y0, x1, y1):
    # Interactive Typing Playground
    draw_rounded_rect(draw, (x0 + 30, y0 + 20, x1 - 30, y0 + 440), 20, (255, 255, 255, 255), (148, 163, 184, 255), 2)
    draw.text((x0 + 55, y0 + 40), "Interactive Font Playground", font=get_font("segoeuib.ttf", 24), fill=(30, 41, 59, 255))
    draw.text((x0 + 55, y0 + 95), "The quick brown fox jumps over the lazy dog.", font=get_font("timesbd.ttf", 36), fill=(15, 23, 42, 255))
    draw.text((x0 + 55, y0 + 160), "1234567890 !@#$%^&*()", font=get_font("timesbd.ttf", 32), fill=(15, 23, 42, 255))

    # Font size slider
    draw.text((x0 + 55, y0 + 260), "Font Size: 48sp", font=font_body, fill=(71, 85, 105, 255))
    draw_rounded_rect(draw, (x0 + 55, y0 + 305, x1 - 55, y0 + 317), 6, (203, 213, 225, 255))
    draw_rounded_rect(draw, (x0 + 55, y0 + 305, x0 + 450, y0 + 317), 6, (37, 99, 235, 255))
    draw.ellipse([x0 + 438, y0 + 299, x0 + 462, y0 + 323], fill=(37, 99, 235, 255))

    # Style Variants
    draw.text((x0 + 55, y0 + 355), "Styles: Regular • Bold • Italic • Bold Italic", font=font_body, fill=(71, 85, 105, 255))

    # Saved Fonts List
    draw_rounded_rect(draw, (x0 + 30, y0 + 470, x1 - 30, y1 - 160), 20, (26, 34, 52, 255))
    draw.text((x0 + 55, y0 + 495), "Your Compiled Fonts", font=font_h3, fill=(147, 197, 253, 255))

    fonts = [
        ("MyCustomScript.ttf", "92 Glyphs • Bengali & Latin • TrueType", "Installed"),
        ("StudioSans-Bold.otf", "118 Glyphs • OpenType CFF • DSIG Validated", "Ready"),
        ("ModernGeometric.ttf", "78 Glyphs • Gemini AI Vector Sheet", "Ready")
    ]
    for i, (fn, sub, st) in enumerate(fonts):
        fy = y0 + 560 + i * 140
        draw_rounded_rect(draw, (x0 + 55, fy, x1 - 55, fy + 115), 14, (15, 23, 42, 255), (51, 65, 85, 200), 1)
        draw.text((x0 + 80, fy + 22), fn, font=get_font("segoeuib.ttf", 26), fill=(255, 255, 255, 255))
        draw.text((x0 + 80, fy + 65), sub, font=get_font("segoeui.ttf", 20), fill=(156, 163, 175, 255))
        draw_rounded_rect(draw, (x1 - 220, fy + 35, x1 - 85, fy + 80), 10, (16, 185, 129, 50), (16, 185, 129, 200), 1)
        draw.text((x1 - 195, fy + 45), st, font=get_font("segoeuib.ttf", 18), fill=(110, 231, 183, 255))

    # Bottom Export & Package ZIP Button
    draw_rounded_rect(draw, (x0 + 30, y1 - 130, x1 - 30, y1 - 45), 18, (37, 99, 235, 255))
    draw.text(((x0 + x1)//2 - 190, y1 - 98), "📦 Package as ZIP (OTF/TTF + License)", font=font_h3, fill=(255, 255, 255, 255))

if __name__ == "__main__":
    generate_hero_banner()
    generate_mockup_screen("screenshot_1_ai_studio.png", "Gemini AI Font Studio", "Generate full vector typography specimen sheets", draw_ai_studio_content)
    generate_mockup_screen("screenshot_2_batch_importer.png", "Image & ZIP Batch Extraction", "Auto-contour tracing with precision manual crop", draw_image_studio_content)
    generate_mockup_screen("screenshot_3_drawing_canvas.png", "Precision Vector Drawing", "Bezier curves, stroke smoothing & baseline guides", draw_canvas_studio_content)
    generate_mockup_screen("screenshot_4_font_manager.png", "Font Manager & Playground", "Live testing, TTF/OTF packaging with license", draw_font_manager_content)
    print("All marketing assets generated successfully!")

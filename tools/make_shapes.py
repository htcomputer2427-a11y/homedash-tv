"""Writes the dashboard's shape drawables and font families. Run once: python tools/make_shapes.py"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'


def shape(fill=None, stroke=None, sw=1, radius=None, oval=False, grad=None):
    s = [f'<shape {NS}' + (' android:shape="oval"' if oval else '') + '>']
    if grad:
        s.append(f'    <gradient android:angle="270" android:startColor="{grad[0]}" android:endColor="{grad[1]}" />')
    elif fill:
        s.append(f'    <solid android:color="{fill}" />')
    if stroke:
        s.append(f'    <stroke android:width="{sw}dp" android:color="{stroke}" />')
    if radius is not None and not oval:
        s.append(f'    <corners android:radius="{radius}dp" />')
    s.append('</shape>')
    return "\n".join(s)


def inner(xml):
    # A <shape> nested in an <item>: drop the namespace, it is declared on the selector.
    return xml.replace(f" {NS}", "")


def selector(states):
    out = [f'<?xml version="1.0" encoding="utf-8"?>', f'<selector {NS}>']
    for state, xml in states:
        out.append(f'    <item{state}>')
        out.append("\n".join("        " + line for line in inner(xml).splitlines()))
        out.append('    </item>')
    out.append('</selector>')
    return "\n".join(out) + "\n"


FOCUS = ' android:state_focused="true"'
DRAWABLES = {
    # Glass cards: dark translucent, hairline border; a white border when focused.
    # About 60% opaque (68% focused): the wallpaper still shows, the text stays readable over bright parts of it.
    "card_bg": selector([(FOCUS, shape("#AD0E1622", "#D9FFFFFF", 1.5, 16)), ("", shape("#990E1622", "#40FFFFFF", 1, 16))]),
    "panel_bg": selector([("", shape("#40000000", "#26FFFFFF", 1, 10))]),
    "icon_btn_bg": selector([(FOCUS, shape("#F2FFFFFF", None, 1, 12)), ("", shape("#990E1622", "#40FFFFFF", 1, 12))]),
    "pill_bg": selector([(FOCUS, shape("#F2FFFFFF", None, 1, 16)), ("", shape("#26FFFFFF", "#33FFFFFF", 1, 16))]),
    "queue_row_bg": selector([(FOCUS, shape("#40FFFFFF", "#FFFFFFFF", 1.5, 10)),
                              (' android:state_selected="true"', shape("#40FFFFFF", "#FFFFFFFF", 1.5, 10)),
                              (' android:state_activated="true"', shape("#1F5BC0FF", "#FF5BC0FF", 1.5, 10)),
                              ("", shape("#00000000", None, 1, 10))]),
    "ctrl_bg": selector([(FOCUS, shape("#40FFFFFF", None, oval=True)), ("", shape("#00000000", oval=True))]),
    "play_bg": selector([(FOCUS, shape("#FF5BC0FF", "#FFFFFFFF", 2.5, oval=True)), ("", shape("#2A5BC0FF", "#FF5BC0FF", 2.5, oval=True))]),
    # The Top 100 picker panel: nearly opaque so a long list reads well over any wallpaper.
    "picker_bg": selector([("", shape("#EB0E1622", "#40FFFFFF", 1, 18))]),
    "tag_bg":selector([("", shape("#FFFFD54A", None, 1, 6))]),
    "art_bg":selector([(FOCUS, shape("#33FFFFFF", "#FFFFFFFF", 2, 12)), ("", shape("#33223344", None, 1, 12))]),
}

# App buttons: the coloured face sits GAP dp inside the view. Focused, a thin white ring with rounded corners
# concentric to the face's fills that gap, like the focus ring on Apple TV, instead of a thick border on the face.
GAP = 4
FACE_RADIUS = 15


def ring_button(face_focused, face, rim):
    # An <inset> drawable, not the item's left/top/right/bottom: on the MiTV-MOEU0 those item offsets were ignored
    # inside a selector and the face covered the ring's straight edges.
    def face_item(colors, stroke):
        return (f'        <item>\n            <inset android:inset="{GAP}dp">\n'
                + "\n".join("                " + l for l in inner(shape(None, stroke, 1, FACE_RADIUS, grad=colors)).splitlines())
                + '\n            </inset>\n        </item>')
    ring = ('        <item>\n'
            + "\n".join("            " + l for l in inner(shape("#00000000", "#E6FFFFFF", 2, FACE_RADIUS + GAP)).splitlines())
            + '\n        </item>')
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<selector {NS}>\n'
            f'    <item{FOCUS}>\n    <layer-list>\n{ring}\n{face_item(face_focused, "#40FFFFFF")}\n    </layer-list>\n    </item>\n'
            f'    <item>\n    <layer-list>\n{face_item(face, rim)}\n    </layer-list>\n    </item>\n</selector>\n')


BUTTONS = {
    "btn_youtube": ring_button(("#FFF5524D", "#FFC9141B"), ("#FFE8403B", "#FFB01218"), "#55FF9A9A"),
    "btn_netflix": ring_button(("#FF2A2A2A", "#FF050505"), ("#FF222222", "#FF000000"), "#40FFFFFF"),
    "btn_store": ring_button(("#FF2CCB9A", "#FF0C8A64"), ("#FF22B88A", "#FF0A7656"), "#5590FFD8"),
    "btn_apps": ring_button(("#FF4A5D86", "#FF202B45"), ("#FF3D4E72", "#FF1C263D"), "#55A0B8FF"),
}

PROGRESS = f'''<?xml version="1.0" encoding="utf-8"?>
<layer-list {NS}>
    <item android:id="@android:id/background">
        <shape><solid android:color="#40FFFFFF" /><corners android:radius="3dp" /></shape>
    </item>
    <item android:id="@android:id/progress">
        <clip><shape><solid android:color="#FF5BC0FF" /><corners android:radius="3dp" /></shape></clip>
    </item>
</layer-list>
'''

# Sunset fallback when no wallpaper.jpg has been pushed: a flat dusk gradient, no bitmap in the APK.
SUNSET = f'''<?xml version="1.0" encoding="utf-8"?>
<shape {NS}>
    <gradient android:angle="270" android:startColor="#FF1B2A4A" android:centerColor="#FF8A4A5A" android:endColor="#FFE08A4A" />
</shape>
'''

FONT = '''<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <font
        android:font="@font/outfit"
        android:fontStyle="normal"
        android:fontWeight="{w}"
        android:fontVariationSettings="'wght' {w}"
        tools:targetApi="28" />
</font-family>
'''


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


for name, xml in list(DRAWABLES.items()) + list(BUTTONS.items()):
    write(os.path.join(RES, "drawable", name + ".xml"), xml)
write(os.path.join(RES, "drawable", "progress_track.xml"), PROGRESS)
write(os.path.join(RES, "drawable", "bg_sunset.xml"), SUNSET)
for name, w in (("outfit_semibold", 600), ("outfit_bold", 700)):
    write(os.path.join(RES, "font", name + ".xml"), FONT.format(w=w))
print(len(DRAWABLES) + 2, "drawables, 2 fonts")

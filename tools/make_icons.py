"""Writes the dashboard's vector icons into app/src/main/res/drawable. Run once: python tools/make_icons.py"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
W = "#FFFFFFFF"
MOON = ("M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26"
        " -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z")
CLOUD = ("M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13"
         "c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z")

SIMPLE = {
    "ic_refresh": (14, W, "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"),
    "ic_shuffle": (22, "#FF5BC0FF", "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5L20,4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20L20,20v-5.5l-2.04,2.04 -3.13,-3.13z"),
    "ic_mic": (20, W, "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z"),
    "ic_prev": (26, W, "M6,6h2v12L6,18zM9.5,12l8.5,6L18,6z"),
    "ic_play": (26, W, "M8,5v14l11,-7z"),
    "ic_pause": (26, W, "M6,19h4L10,5L6,5v14zM14,5v14h4L18,5h-4z"),
    "ic_next": (26, W, "M6,18l8.5,-6L6,6v12zM16,6v12h2L18,6h-2z"),
    "ic_repeat": (22, W, "M7,7h10v3l4,-4 -4,-4v3L5,5v6h2L7,7zM17,17L7,17v-3l-4,4 4,4v-3h12v-6h-2v4z"),
    "ic_playlist": (16, W, "M15,6H3v2h12V6zm0,4H3v2h12v-2zM3,16h8v-2H3V16zM17,6v8.18C16.69,14.07 16.35,14 16,14c-1.66,0 -3,1.34 -3,3s1.34,3 3,3s3,-1.34 3,-3V8h3V6H17z"),
    "ic_more": (14, "#CCFFFFFF", "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"),
    "ic_store": (28, W, "M18,6h-2c0,-2.21 -1.79,-4 -4,-4S8,3.79 8,6H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM12,4c1.1,0 2,0.9 2,2h-4c0,-1.1 0.9,-2 2,-2zM12,14c-2.21,0 -4,-1.79 -4,-4h2c0,1.1 0.9,2 2,2s2,-0.9 2,-2h2c0,2.21 -1.79,4 -4,4z"),
    "ic_fuel": (22, W, "M19.77,7.23l0.01,-0.01 -3.72,-3.72L15,4.56l2.11,2.11c-0.94,0.36 -1.61,1.26 -1.61,2.33 0,1.38 1.12,2.5 2.5,2.5 0.36,0 0.69,-0.08 1,-0.21v7.21c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V14c0,-1.1 -0.9,-2 -2,-2h-1V5c0,-1.1 -0.9,-2 -2,-2H6c-1.1,0 -2,0.9 -2,2v16h10v-7.5h1.5v5c0,1.38 1.12,2.5 2.5,2.5s2.5,-1.12 2.5,-2.5V9c0,-0.69 -0.28,-1.32 -0.73,-1.77zM12,10H6V5h6v5zM18,10c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1z"),
    "ic_note": (40, "#99FFFFFF", "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"),
    "ic_w_moon": (56, "#FFFFE08A", "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z"),
    "ic_w_cloud": (56, "#FFF2F5FA", CLOUD),
    "ic_netflix": (32, "#FFE50914", "M6,2.5h3.6l4.8,11.6V2.5H18v19h-3.4L9.6,9.6v11.9H6z"),
}


def vector(size, body, vw=24, vh=24, h=None):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size}dp" android:height="{h or size}dp"\n'
            f'    android:viewportWidth="{vw}" android:viewportHeight="{vh}">\n{body}</vector>\n')


def fill(color, path):
    return f'    <path android:fillColor="{color}" android:pathData="{path}" />\n'


def stroke(color, width, path):
    return (f'    <path android:strokeColor="{color}" android:strokeWidth="{width}" android:strokeLineCap="round"\n'
            f'        android:pathData="{path}" />\n')


def cloud(color, sx, sy, tx, ty):
    return (f'    <group android:scaleX="{sx}" android:scaleY="{sy}" android:translateX="{tx}" android:translateY="{ty}">\n'
            f'    {fill(color, CLOUD)}    </group>\n')


COMPLEX = {
    "ic_youtube": vector(34, fill(W, "M5,5.5h14a3,3 0,0 1,3 3v7a3,3 0,0 1,-3 3H5a3,3 0,0 1,-3 -3v-7a3,3 0,0 1,3 -3z")
                         + fill("#FFE5252A", "M10,8.7L15.6,12 10,15.3z")),
    "ic_gold": vector(26, fill("#FFD68A14", "M1,21L2.6,16.2H11L11.8,21z") + fill("#FFD68A14", "M12.2,21L13,16.2H21.4L23,21z")
                      + fill("#FFF7C948", "M3.2,16.2L4,14.6H10.4L11,16.2z") + fill("#FFF7C948", "M13,16.2L13.6,14.6H20L20.8,16.2z")
                      + fill("#FFE9A825", "M6.2,14.4L7.8,9.6H16.2L17.8,14.4z") + fill("#FFFFE08A", "M8.4,9.6L9,8H15L15.6,9.6z")),
    "ic_flag_us": vector(26, '    <clip-path android:pathData="M12,2a10,10 0,1 1,0 20a10,10 0,1 1,0 -20z" />\n'
                         + fill(W, "M2,2h20v20H2z")
                         + fill("#FFD22F27", "M2,2h20v1.54H2zM2,5.08h20v1.54H2zM2,8.15h20v1.54H2zM2,11.23h20v1.54H2zM2,14.31h20v1.54H2zM2,17.38h20v1.54H2zM2,20.46h20V22H2z")
                         + fill("#FF2B3A8C", "M2,2h10v10.77H2z")
                         + fill(W, "M4.5,4.5h1v1h-1zM7.5,4.5h1v1h-1zM10,4.5h1v1h-1zM6,7h1v1H6zM9,7h1v1H9zM4.5,9.5h1v1h-1zM7.5,9.5h1v1h-1zM10,9.5h1v1h-1z")),
    "ic_w_sun": vector(56, fill("#FFFFC93C", "M12,7a5,5 0,1 1,0 10a5,5 0,1 1,0 -10z")
                       + stroke("#FFFFB020", 1.6, "M12,1.8V4M12,20V22.2M1.8,12H4M20,12H22.2M4.8,4.8L6.3,6.3M17.7,17.7L19.2,19.2M4.8,19.2L6.3,17.7M17.7,6.3L19.2,4.8")),
    "ic_w_partly": vector(64, stroke("#FFFFB020", 1.5, "M10,1V3M2.2,9H4.2M3.9,3L5.3,4.4M16.1,3L14.7,4.4M3.9,15L5.3,13.6")
                          + fill("#FFFFC93C", "M10,4.6a4.4,4.4 0,1 1,0 8.8a4.4,4.4 0,1 1,0 -8.8z")
                          + cloud("#FFF7F9FC", 0.88, 0.8, 6.5, 7.2), vw=28, vh=24, h=56),
    # Cloudy night: the crescent moon peeking out behind a cloud, laid out like ic_w_partly.
    "ic_w_partly_night": vector(64, '    <group android:translateX="1" android:translateY="0.5" android:scaleX="0.68" android:scaleY="0.68">\n'
                                + "    " + fill("#FFFFE08A", MOON) + "    </group>\n"
                                + cloud("#FFF7F9FC", 0.88, 0.8, 6.5, 7.2), vw=28, vh=24, h=56),
    "ic_w_rain": vector(56, cloud("#FFE6ECF3", 0.9, 0.78, 1.2, 0)
                        + stroke("#FF5BC0FF", 1.6, "M8,17.5L7,21M12,17.5L11,21M16,17.5L15,21")),
    "ic_w_storm": vector(56, cloud("#FFB8C2CE", 0.9, 0.78, 1.2, 0)
                         + fill("#FFFFD23F", "M12.5,15.5L9.5,19.5H12L11,23L15,18.5H12.5L13.5,15.5z")),
    "ic_app": vector(48, fill("#FF16233A", "M4,2h16a2,2 0,0 1,2 2v16a2,2 0,0 1,-2 2H4a2,2 0,0 1,-2 -2V4a2,2 0,0 1,2 -2z")
                     + fill("#FFFFC93C", "M7,6h4v5H7zM13,6h4v2h-4zM13,9h4v2h-4zM7,13h10v5H7z")),
}

for name, (size, color, path) in SIMPLE.items():
    COMPLEX[name] = vector(size, fill(color, path))
for name, xml in COMPLEX.items():
    with open(os.path.join(OUT, name + ".xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(xml)
print(len(COMPLEX), "icons")

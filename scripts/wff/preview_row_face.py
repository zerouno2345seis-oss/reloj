import sys
F="/Users/mohamad/reloj-face1/watchface-row/src/main/res"
A="/Users/mohamad/reloj-face1/app/src/main/res/drawable-nodpi" 
import glob,os
def find(n): return [p for p in glob.glob("/Users/mohamad/reloj-face1/app/src/main/res/drawable*/"+n)][0]
zones = len(sys.argv)>1 and sys.argv[1]=="zones"
aod = len(sys.argv)>1 and sys.argv[1]=="aod"
def box(x,y,w,h,inner,style=""):
    return f'<div style="position:absolute;left:{x}px;top:{y}px;width:{w}px;height:{h}px;{style}">{inner}</div>'
def txt(x,y,w,h,s,size,color="#fff",fam="pb"):
    return box(x,y,w,h,s,f"display:flex;align-items:center;justify-content:center;font-family:{fam};font-size:{size}px;color:{color};direction:rtl;white-space:nowrap")
SOFT="#D3DAD6"
h=[]
if not aod:
    h.append(box(0,0,450,450,"",f"background:url({F}/drawable-nodpi/bg_mosques.png);background-size:cover"))
    # seconds ring at 24 s
    h.append('<svg style="position:absolute;left:0;top:0" width="450" height="450"><circle cx="225" cy="225" r="217" fill="none" stroke="#123A30" stroke-width="3"/>'
             '<path d="M225,8 A217,217 0 0 1 %.1f,%.1f" fill="none" stroke="#43D49A" stroke-width="3" stroke-linecap="round"/>'%(225+217*0.5878,225-217*0.8090)
             +'<circle cx="%.1f" cy="%.1f" r="4" fill="#F2FFF9"/></svg>'%(225+217*0.5878,225-217*0.8090))
    h.append(box(0,380,450,70,"","background:rgba(4,16,16,.88)"))
    h.append(box(224,386,1.5,46,"","background:rgba(170,220,200,.22)"))
    h.append(txt(125,29,200,36,"22/10",30))
    h.append(box(70,68,310,70,"","background:rgba(10,32,28,.72);border:1px solid rgba(170,220,200,.35);border-radius:22px;box-sizing:border-box"))
    h.append(txt(82,72,286,24,"البقرة · 42",16,SOFT,"ps"))
    h.append(txt(82,95,286,38,"وَلَا تَلْبِسُوا الْحَقَّ بِالْبَاطِلِ",23))
    h.append(txt(0,142,450,114,"16:40",110))
    # side edges: icons only
    h.append(box(412,208,34,34,f'<img src="{find("img_quran.png")}" style="width:34px;height:34px">'))
    h.append(box(4,208,34,34,f'<img src="{find("img_bookmark.png")}" style="width:34px;height:34px">'))
    # prayer row
    h.append(box(64,269,323,101,"","background:rgba(4,16,13,.82);border:1px solid rgba(170,220,200,.28);border-radius:16px;box-sizing:border-box"))
    c=323/3
    for k in (1,2): h.append(box(64+k*c,283,1,73,"","background:rgba(170,220,200,.22)"))
    h.append(txt(64+2*c,283,c,24,"مضى",17,SOFT,"ps")); h.append(txt(64+2*c,311,c,42,"1س 30د",26))
    h.append(txt(64+c,279,c,38,"المغرب",26)); h.append(txt(64+c,321,c,32,"18:30",29))
    h.append(txt(64,283,c,24,"متبقي",17,SOFT,"ps")); h.append(txt(64,311,c,42,"1س 50د",26))
    # bottom: temperature (left), battery (right)
    h.append(txt(153,384,70,47,"18°",24)); h.append(box(131,394,20,28,"🌡","font-size:18px;filter:sepia(1) saturate(4) hue-rotate(5deg)"))
    h.append(txt(227,384,70,47,"76%",24)); h.append(box(298,396,22,22,"🔋","font-size:18px"))
else:
    h.append('<svg style="position:absolute;left:0;top:0" width="450" height="450"><circle cx="225" cy="225" r="217" fill="none" stroke="#2C3A35" stroke-width="1.5"/></svg>')
    h.append(txt(125,29,200,36,"22/10",30,"#8C9097"))
    h.append(txt(0,142,450,114,"16:40",110,"#C9CED6"))
    c=323/3
    h.append(txt(64+2*c,283,c,24,"مضى",17,"#6B7280","ps")); h.append(txt(64+2*c,311,c,42,"1س 30د",26,"#8C9097"))
    h.append(txt(64+c,279,c,38,"المغرب",26,"#8C9097")); h.append(txt(64+c,321,c,32,"18:30",29,"#8C9097"))
    h.append(txt(64,283,c,24,"متبقي",17,"#6B7280","ps")); h.append(txt(64,311,c,42,"1س 50د",26,"#8C9097"))
if zones:
    Z=[(406,93,44,264,"1"),(0,93,44,264,"2"),(227,384,100,47,"3"),(123,384,100,47,"4"),(125,29,200,36,"5"),(70,68,310,70,"6"),(64,269,323,101,"7")]
    for x,y,w,hh,n in Z:
        h.append(box(x,y,w,hh,f'<span style="background:#FF4D6D;color:#fff;font:bold 15px sans-serif;border-radius:10px;padding:1px 7px">{n}</span>',
                 "border:2px dashed #FF4D6D;box-sizing:border-box;display:flex;align-items:center;justify-content:center"))
html=f'''<html><head><style>@font-face{{font-family:pb;src:url({F}/font/plex_bold.ttf)}}@font-face{{font-family:ps;src:url({F}/font/plex_semibold.ttf)}}
body{{margin:0;background:#fff}}#w{{position:relative;width:450px;height:450px;border-radius:50%;overflow:hidden;background:#000}}</style></head>
<body><div id="w">{"".join(h)}</div></body></html>'''
open(sys.argv[2],"w").write(html)

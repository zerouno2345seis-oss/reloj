import sys
src, out = sys.argv[1], sys.argv[2]
t = open(src, encoding="utf-8").read()

def sub(old, new, count=1):
    global t
    n = t.count(old)
    assert n == count, (old[:80], n)
    t = t.replace(old, new)

# The publish step adds the page skeleton itself.
t = t[t.index("<title>"):].replace("<title>محرّر الواجهات</title>","<title>استوديو الواجهات</title>",1)
t = t[:t.index("</script>\n\n</body></html>") + len("</script>\n")] if "</script>\n\n</body></html>" in t else t.rsplit("</script>", 1)[0] + "</script>\n"

# Arabic fonts for the preview (the Codex copy lost this link).
sub('<meta name="claude:viewport-fit-sides" content="cover">\n',
    '<meta name="claude:viewport-fit-sides" content="cover">\n'
    '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Kufi+Arabic:wght@400;600;700&family=IBM+Plex+Sans+Arabic:wght@400;600&family=Amiri:wght@400;700&family=Tajawal:wght@400;700&family=Cairo:wght@400;700&family=Reem+Kufi:wght@400;700&family=Almarai:wght@400;700&family=El+Messiri:wght@400;700&family=Aref+Ruqaa:wght@400;700&family=Lateef&family=Scheherazade+New:wght@400;700&family=Changa:wght@400;700&display=swap">\n')

# Keep reading the designs saved by the current editor.
t = t.replace('"faceStudio_', '"faceEd_')

# In-page dialogs: prompt/confirm/alert do nothing inside an artifact.
DLG = r'''function dlg(msg,{input=null,ok="حسنًا",cancel=true}={}){return new Promise(res=>{const w=document.createElement("div");w.className="dlg";
  const b=document.createElement("div");b.className="dlg-box";b.setAttribute("role","dialog");b.setAttribute("aria-modal","true");
  const p=document.createElement("p");p.textContent=msg;b.append(p);let i=null;
  if(input!==null){i=document.createElement("input");i.type="text";i.maxLength=40;i.value=input;i.id="dlgInput";b.append(i)}
  const r=document.createElement("div");r.className="dlg-row";const y=document.createElement("button");y.type="button";y.className="on";y.textContent=ok;r.append(y);
  let n=null;if(cancel){n=document.createElement("button");n.type="button";n.textContent="إلغاء";r.append(n)}
  b.append(r);w.append(b);document.body.append(w);(i||y).focus();if(i)i.select();
  const done=v=>{w.remove();res(v)};y.onclick=()=>done(i?i.value.trim():true);if(n)n.onclick=()=>done(i?null:false);
  w.addEventListener("keydown",e=>{if(e.key==="Escape")done(i?null:false);if(e.key==="Enter"&&i)done(i.value.trim())})})}
'''
sub('const svg=document.getElementById("face");\n', DLG + 'const svg=document.getElementById("face");\n')
sub('if(!s.img){alert("ارفع صورة أولًا");return}', 'if(!s.img){dlg("ارفع صورة أولًا من «صورة خلفية من جهازك».",{cancel:false});return}')
sub('document.getElementById("newFace").onclick=()=>{const nm=(prompt("اسم الواجهة الجديدة","واجهتي")||"").trim();if(!nm)return;',
    'document.getElementById("newFace").onclick=async()=>{const nm=await dlg("اسم الواجهة الجديدة",{input:"واجهتي",ok:"أنشئ"});if(!nm)return;')
sub('document.getElementById("dupFace").onclick=()=>{const src=cur,nm=(prompt("اسم النسخة الجديدة",S().name+" (نسخة)")||"").trim();if(!nm)return;',
    'document.getElementById("dupFace").onclick=async()=>{const src=cur,nm=await dlg("اسم النسخة الجديدة",{input:S().name+" (نسخة)",ok:"انسخ"});if(!nm)return;')
sub('document.getElementById("delFace").onclick=()=>{if(BASE.has(cur)){alert("هذه واجهة أساسية؛ يمكنك فقط إعادة ضبطها.");return}if(!confirm("حذف الواجهة «"+S().name+"»؟"))return;',
    'document.getElementById("delFace").onclick=async()=>{if(BASE.has(cur)){dlg("هذه واجهة أساسية لا تُحذف. تستطيع إعادتها إلى شكلها الأول من «افتراضي».",{cancel:false});return}if(!await dlg("حذف الواجهة «"+S().name+"»؟ لا يمكن التراجع.",{ok:"احذف"}))return;')

# Gold handles at both ends of the chosen segment: drag one to lengthen it at its neighbour's expense.
sub('  curTag="time";\n  const rS=',
    '''  if(!aod&&s.segs[sel]){const g=s.segs[sel],rM=g.rg===2?R_OUT-th-3-th2/2:rMid;curTag="seg";
    [[0,g.a-g.s/2],[1,g.a+g.s/2]].forEach(([h,a])=>{const[x,y]=pt(rM,a);el("circle",{cx:x,cy:y,r:10,fill:"#e8bc3e",stroke:"#1a1408","stroke-width":2.5,"data-h":h,style:"cursor:grab"})})}
  curTag="time";
  const rS=''')
DRAG = r'''
// Dragging a gold handle moves that end of the chosen segment; a touching neighbour on the same ring gives or takes the room.
(()=>{let d=null;const n360=a=>((a%360)+360)%360;
  const ang=e=>{const p=new DOMPoint(e.clientX,e.clientY).matrixTransform(svg.getScreenCTM().inverse());return n360(Math.atan2(p.x-C,-(p.y-C))*180/Math.PI)};
  svg.addEventListener("pointerdown",e=>{const h=e.target.closest("[data-h]");if(!h)return;e.preventDefault();e.stopPropagation();
    const s=S(),g=s.segs[sel],side=+h.dataset.h,edge=side?g.a+g.s/2:g.a-g.s/2;let nb=null,best=10;
    s.segs.forEach((o,j)=>{if(j===sel||(o.rg||1)!==(g.rg||1))return;const oe=side?o.a-o.s/2:o.a+o.s/2,dd=Math.abs(n360(oe-edge+180)-180);if(dd<best){best=dd;nb=j}});
    d={side,nb,gap:nb===null?0:best,fixed:side?g.a-g.s/2:g.a+g.s/2,far:nb===null?null:(side?s.segs[nb].a+s.segs[nb].s/2:s.segs[nb].a-s.segs[nb].s/2)};svg.setPointerCapture(e.pointerId)});
  svg.addEventListener("pointermove",e=>{if(!d)return;const s=S(),g=s.segs[sel],a=ang(e);
    const max=d.nb===null?340:(d.side?n360(d.far-d.fixed):n360(d.fixed-d.far))-d.gap-8;
    let sp=d.side?n360(a-d.fixed):n360(d.fixed-a);if(sp>max)sp=sp>(max+360)/2?8:max;sp=Math.max(8,Math.round(sp*2)/2);
    g.s=sp;g.a=n360(d.side?d.fixed+sp/2:d.fixed-sp/2);
    if(d.nb!==null){const o=s.segs[d.nb];if(d.side){const st=d.fixed+sp+d.gap;o.s=n360(d.far-st)||8;o.a=n360(st+o.s/2)}else{const en=d.fixed-sp-d.gap;o.s=n360(en-d.far)||8;o.a=n360(d.far+o.s/2)}}
    draw()});
  const end=()=>{if(d){d=null;ui()}};svg.addEventListener("pointerup",end);svg.addEventListener("pointercancel",end)})();
'''
sub('draw();ui();\n</script>', 'draw();ui();\n' + DRAG + '</script>')

CSS = r'''
<style>
/* In-page dialog, phone layout (watch fixed in the top quarter, one tool window below), and drag handles. */
.dlg{position:fixed;inset:0;z-index:50;background:rgba(10,24,18,.45);display:flex;align-items:center;justify-content:center;padding:16px}
.dlg-box{background:#fff;color:#19332a;border-radius:14px;padding:18px;width:min(360px,100%);display:flex;flex-direction:column;gap:12px;box-shadow:0 18px 40px rgba(0,0,0,.25)}
.dlg-box p{margin:0;line-height:1.6}.dlg-box input{width:100%}.dlg-row{display:flex;gap:8px}
svg#face{touch-action:none}
@media(max-width:700px){
  html,body{height:100%!important;overflow:hidden!important}
  .studio-grid{display:grid!important;grid-template-columns:minmax(0,1fr)!important;grid-template-rows:34dvh minmax(0,1fr)!important;overflow:hidden!important;gap:8px;padding:8px}
  .studio-cell{min-height:0!important}.studio-cell.is-preview{height:auto!important}
  .studio-cell:nth-child(n+3){display:none}
  .cell-content{max-height:none!important}
  .is-preview .stage svg#face{max-height:calc(100cqh - 6px)!important}
}
</style>
'''
t = t.rstrip() + "\n" + CSS
open(out, "w", encoding="utf-8").write(t)
print(len(t.encode()))

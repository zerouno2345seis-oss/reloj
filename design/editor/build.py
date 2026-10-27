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

# A designed rings panel in place of the auto-generated buttons: pick a ring, see its parts as a strip,
# and set each part's content, colours and length (a neighbour gives or takes the room).
RINGS = r"""
let ringSel=1;
(()=>{const n360=a=>((a%360)+360)%360,GAPD=3;let host=null;
  const order=r=>S().segs.map((g,i)=>({g,i})).filter(x=>(x.g.rg||1)===r).sort((a,b)=>n360(a.g.a-a.g.s/2)-n360(b.g.a-b.g.s/2));
  const startOf=g=>n360(g.a-g.s/2),endOf=g=>n360(g.a+g.s/2);
  const mk=(t,c,txt)=>{const e=document.createElement(t);if(c)e.className=c;if(txt!=null)e.textContent=txt;return e};
  const slider=(id,lbl,min,max,step,val,fmt,on)=>{const w=mk("div","rs-field"),l=mk("label");l.htmlFor=id;l.textContent=lbl;const v=mk("span","rs-val",fmt(val));
    const i=mk("input");i.type="range";i.id=id;i.min=min;i.max=max;i.step=step;i.value=val;i.oninput=()=>{v.textContent=fmt(+i.value);on(+i.value)};
    const h=mk("div","rs-fh");h.append(l,v);w.append(h,i);return w};
  function setLen(r,i,v){const L=order(r),k=L.findIndex(x=>x.i===i);if(k<0)return;const g=L[k].g;
    let room=360-L.reduce((t,x)=>t+x.g.s,0)-GAPD*L.length;const nx=L.length>1?L[(k+1)%L.length].g:null;
    const free=nx?n360(startOf(nx)-endOf(g)):360-g.s;
    if(nx&&free<=12){v=Math.max(8,Math.min(g.s+nx.s-8,v));const d=v-g.s;g.s=v;g.a=n360(g.a+d/2);nx.s-=d;nx.a=n360(nx.a+d/2)}
    else{v=Math.max(8,Math.min(g.s+Math.max(0,free-GAPD),v));const d=v-g.s;g.s=v;g.a=n360(g.a+d/2)}}
  function even(r){const L=order(r),n=L.length;if(!n)return;let p=startOf(L[0].g);const sp=(360-GAPD*n)/n;L.forEach(({g})=>{g.s=Math.round(sp*10)/10;g.a=n360(p+sp/2);p+=sp+GAPD})}
  function seed(r){const cols=["#d9b36a","#3fb58a","#5aa9e6","#e46a6a"];const s=S();for(let k=0;k<4;k++)s.segs.push({c:"none",a:k*90,s:87,col:cols[k],rg:r})}
  function strip(){if(!host)return;const box=host.querySelector(".rs-strip");if(!box)return;box.replaceChildren();const L=order(ringSel);
    if(!L.length){box.append(mk("p","rs-empty","لا أجزاء في هذه الحلقة بعد."));return}
    L.forEach(({g,i})=>{const b=mk("button","rs-part"+(i===sel?" on":""));b.type="button";b.style.flexGrow=g.s;b.style.background=g.ce?`linear-gradient(to left,${g.col},${g.ce})`:g.col;
      b.title=CONTENT[g.c].name+" · "+Math.round(g.s)+"°";b.setAttribute("aria-label","جزء "+CONTENT[g.c].name+" طوله "+Math.round(g.s)+" درجة");
      const t=mk("span","rs-pl",CONTENT[g.c].name==="فارغ"?Math.round(g.s)+"°":CONTENT[g.c].name);b.append(t);b.onclick=()=>{sel=i;draw();ui()};box.append(b)});
    const lv=host.querySelector("#rsLenV");const g=S().segs[sel];if(lv&&g)lv.textContent=Math.round(g.s)+"°";const li=host.querySelector("#rsLen");if(li&&g&&document.activeElement!==li)li.value=g.s}
  window.ringStudioStrip=strip;
  function panel(){if(!host)return;const s=S();const g0=s.segs[sel];if(g0)ringSel=g0.rg||1;
    const has2=s.segs.some(g=>g.rg===2);if(ringSel===2&&!has2)ringSel=1;
    const root=mk("div","rs");
    // ring switch
    const sw=mk("div","rs-switch");sw.setAttribute("role","tablist");
    [[1,"الحلقة الخارجية"],[2,"الحلقة الداخلية"]].forEach(([r,t])=>{const b=mk("button",r===ringSel?"on":"",t);b.type="button";b.setAttribute("role","tab");b.setAttribute("aria-selected",r===ringSel);
      b.onclick=async()=>{if(r===2&&!S().segs.some(g=>g.rg===2)){if(!await dlg("أضيف حلقة داخلية بأربعة أجزاء متساوية؟",{ok:"أضف"}))return;seed(2)}ringSel=r;const L=order(r);if(L.length)sel=L[0].i;draw();ui()};sw.append(b)});
    root.append(sw);
    // ring settings
    const rset=mk("div","rs-card");rset.append(mk("h3","rs-h","إعدادات الحلقة"));
    const gr=mk("div","rs-grid");
    gr.append(slider("rsThick","السماكة",10,60,1,ringSel===2?(s.thick2||24):s.thick,v=>v+" بكسل",v=>{if(ringSel===2)s.thick2=v;else s.thick=v;draw()}));
    gr.append(slider("rsFont","حجم الكتابة",11,24,1,s.fsize,v=>v,v=>{s.fsize=v;draw()}));
    gr.append(slider("rsOp","الشفافية",.1,1,.05,STY(s).rOp,v=>Math.round(v*100)+"٪",v=>{STY(s).rOp=v;draw()}));
    let last=0;gr.append(slider("rsRot","تدوير الحلقة",-180,180,1,0,v=>(v>0?"+":"")+v+"°",v=>{const d=v-last;last=v;order(ringSel).forEach(({g})=>g.a=n360(g.a+d));draw()}));
    rset.append(gr);
    const acts=mk("div","rs-acts");
    const a1=mk("button","","وزّع بالتساوي");a1.type="button";a1.onclick=()=>{even(ringSel);draw();ui()};
    const a2=mk("button","","＋ جزء جديد");a2.type="button";a2.onclick=()=>{const L=order(ringSel);const k=L.findIndex(x=>x.i===sel);const base=k>=0?L[k].g:(L[0]&&L[0].g);
      if(!base){seed(ringSel);draw();ui();return}if(base.s<2*8+GAPD){dlg("هذا الجزء قصير لا يُقسم. أطِله أولًا.",{cancel:false});return}
      const h=(base.s-GAPD)/2,st=startOf(base);base.s=h;base.a=n360(st+h/2);S().segs.push({c:"none",a:n360(st+h+GAPD+h/2),s:h,col:base.col,rg:base.rg});sel=S().segs.length-1;draw();ui()};
    acts.append(a2,a1);
    if(ringSel===2){const a3=mk("button","rs-danger","إزالة الحلقة الداخلية");a3.type="button";a3.onclick=async()=>{if(!await dlg("أزيل الحلقة الداخلية بكل أجزائها؟",{ok:"أزل"}))return;S().segs=S().segs.filter(g=>g.rg!==2);sel=0;ringSel=1;draw();ui()};acts.append(a3)}
    rset.append(acts);root.append(rset);
    // strip of parts
    const sc=mk("div","rs-card");sc.append(mk("h3","rs-h","أجزاء الحلقة"),mk("p","rs-hint","اضغط جزءًا لتعديله، أو اسحب المقبضين الذهبيين على الساعة لتغيير طوله."));
    const st=mk("div","rs-strip");sc.append(st);root.append(sc);
    // the chosen part
    const g=s.segs[sel];
    if(g&&(g.rg||1)===ringSel){const pc=mk("div","rs-card");pc.append(mk("h3","rs-h","الجزء المختار"));
      const cf=mk("div","rs-field"),cl=mk("label","","المحتوى");cl.htmlFor="rsCont";const cs=mk("select");cs.id="rsCont";
      for(const k in CONTENT){const o=mk("option","",CONTENT[k].name);o.value=k;o.selected=k===g.c;cs.append(o)}cs.onchange=()=>{g.c=cs.value;draw();ui()};cf.append(cl,cs);pc.append(cf);
      const colors=mk("div","rs-colors");
      [["لون الجزء","col",g.col],["لون نهايته (تدرّج)","ce",g.ce||g.col],["لون الكتابة","tc",g.tc||"#ffffff"]].forEach(([t,k,v])=>{const w=mk("label","rs-color");const i=mk("input");i.type="color";i.value=v;i.oninput=()=>{g[k]=i.value;draw()};w.append(i,mk("span","",t));colors.append(w)});
      pc.append(colors);
      const lf=slider("rsLen","الطول",8,300,.5,g.s,v=>Math.round(v)+"°",v=>{setLen(ringSel,sel,v);draw()});lf.querySelector(".rs-val").id="rsLenV";pc.append(lf);
      const ck=mk("label","rs-check");const cb=mk("input");cb.type="checkbox";cb.checked=!!g.cz;cb.onchange=()=>{g.cz=cb.checked;draw()};ck.append(cb,mk("span","","يمكن تغيير محتواه من الساعة نفسها"));pc.append(ck);
      const pa=mk("div","rs-acts");const del=mk("button","rs-danger","احذف هذا الجزء");del.type="button";del.onclick=()=>{S().segs.splice(sel,1);const L=order(ringSel);sel=L.length?L[0].i:0;draw();ui()};pa.append(del);pc.append(pa);root.append(pc)}
    const n=slotCount(s),bd=mk("p","rs-slots"+(n>8?" over":""),"الخانات المستعملة: "+n+" من 8"+(n>8?". زدت عن الحد، فاجعل بعض الأجزاء «فارغ».":""));root.append(bd);
    host.replaceChildren(root);strip()}
  window.ringStudio=body=>{const keep=mk("div");keep.hidden=true;keep.append(...body.children);body.append(keep);host=mk("div");body.append(host);panel()};
  const _ui=ui;ui=function(){_ui();panel()};const _draw=draw;draw=function(){_draw();strip()};
})();
"""
sub(DRAG + '</script>', DRAG + RINGS + '</script>')
sub("    if(detail.id==='secAod'){buildAod(detail.querySelector('#aodBox'));return}",
    "    if(detail.id==='secRing'){window.ringStudio(body);return}\n    if(detail.id==='secAod'){buildAod(detail.querySelector('#aodBox'));return}")
# Button names should not carry the value they had when the page opened.
sub("const short = name => name.replace(/\\s*\\([^)]*\\)/g,'').replace(/\\s*الصورة\\s*/g,' ').trim();",
    "const short = name => name.replace(/\\s*\\([^)]*\\)/g,'').replace(/\\s*الصورة\\s*/g,' ').replace(/[\\s\\d.٪%°]+$/,'').trim();")
CSS += r"""
<style>
/* Rings panel */
.rs{display:flex;flex-direction:column;gap:12px;padding:12px}
.rs-switch{display:grid;grid-template-columns:1fr 1fr;gap:4px;background:#e6efe9;border-radius:11px;padding:4px}
.rs-switch button{border:none;background:transparent;border-radius:8px;padding:8px;font-weight:600;color:#3a5a4b}
.rs-switch button.on{background:#fff;color:#123b29;box-shadow:0 1px 4px rgba(25,49,38,.18)}
.rs-card{border:1px solid #d6e4db;border-radius:12px;padding:12px;display:flex;flex-direction:column;gap:10px;background:#fbfdfc}
.rs-h{margin:0;font-size:13px;color:#176747;letter-spacing:.2px}
.rs-hint{margin:0;font-size:12px;color:#4e6259}
.rs-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:10px 16px}
.rs-field{display:flex;flex-direction:column;gap:4px;min-width:0}.rs-field select{width:100%}
.rs-fh{display:flex;justify-content:space-between;align-items:baseline;font-size:13px}.rs-fh label{display:inline}
.rs-val{color:#4e6259;font-variant-numeric:tabular-nums;font-size:12px}
.rs-field input[type=range]{width:100%}
.rs-acts{display:flex;gap:8px;flex-wrap:wrap}
.rs-danger{color:#a33a2a!important;border-color:#e3b7ad!important}
.rs-strip{display:flex;gap:3px;direction:ltr;min-height:44px;border-radius:9px;overflow:hidden;background:#e6efe9;padding:3px}
.rs-part{flex-basis:0;min-width:18px;border:none!important;border-radius:6px!important;padding:2px 4px!important;display:flex;align-items:center;justify-content:center;cursor:pointer;box-shadow:inset 0 0 0 1px rgba(0,0,0,.08)}
.rs-part.on{outline:3px solid #123b29;outline-offset:-3px}
.rs-pl{font-size:11px;font-weight:700;color:#fff;text-shadow:0 1px 2px rgba(0,0,0,.55);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;direction:rtl}
.rs-empty{margin:auto;font-size:12px;color:#4e6259}
.rs-colors{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.rs-color{display:flex!important;flex-direction:column;align-items:stretch!important;gap:4px;font-size:12px;color:#4e6259}
.rs-color input{width:100%;height:34px;border:1px solid #c1d2c7;border-radius:8px;padding:2px;background:#fff}
.rs-check{justify-content:flex-start!important;gap:8px!important;font-size:13px}
.rs-slots{margin:0;font-size:12px;color:#176747}.rs-slots.over{color:#a33a2a;font-weight:700}
</style>
"""
t = t.rstrip() + "\n" + CSS
open(out, "w", encoding="utf-8").write(t)
print(len(t.encode()))

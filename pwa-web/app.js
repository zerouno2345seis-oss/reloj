
// ════════════════ QURAN WATCH 8 - 12-UNIT CONSTRAINED INTELLIGENT GRID STUDIO ════════════════
// Default State (Default is text only, font size 14, white text)
let tileConfig = {
    version: Date.now(),
    canvasInsets: { top: 0, right: 0, bottom: 0, left: 0 },
    rowWeights: {},
    appearance: { tileShape: 'square-connected', pattern: 'star-eight', iconPalette: 'jewel' },
    tiles: [
        // Layer 1 already shows the time on wrist-raise, so the tiles open with
        // the prayer countdown across the top row instead of a second clock.
        { id: 'prayer_countdown', colorHex: '#10B981', isLive: true, colSpan: 12, rowIndex: 0, fontSize: 20, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'animated', iconType: 'hourglass', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'folder_islamic', colorHex: '#0284C7', colSpan: 4, rowIndex: 1, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'folder', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'quran_resume', colorHex: '#0E7490', isLive: true, colSpan: 8, rowIndex: 1, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'quran', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'folder_tools', colorHex: '#EA580C', colSpan: 4, rowIndex: 2, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'folder', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'locations', colorHex: '#F59E0B', colSpan: 4, rowIndex: 2, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'pin', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'settings', colorHex: '#334155', colSpan: 4, rowIndex: 2, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'settings', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'cloud_sync_pull' }
    ]
};

// Global Watch Settings
let watchSettings = {
    fontFamily: 'Uthmanic',
    fontSize: 18,
    ayahColor: 'yellow',
    readerBgColor: 'black',
    readerTextColor: 'white',
    selectedLocationId: 'ba_caba',
    selectedLocationName: 'بوينس آيرس (العاصمة)',
    selectedLat: -34.6037,
    selectedLng: -58.3816,
    useGps: false,
    calculationMethod: 'ISNA',
    notificationsEnabled: true,
    reminderFajr: 15,
    reminderOthers: 10,
    reminderDhuhr: 10,
    reminderAsr: 10,
    reminderMaghrib: 10,
    reminderIsha: 10,
    notificationVibration: true,
    notificationFullScreen: false,
    customAyahColor: '#ffd60a',
    customReaderBgColor: '#111214',
    customReaderTextColor: '#ffffff',
    recentLocationIds: ['king_fahd_center', 'al_ahmad_mosque'],
    tilesDefaultMode: 'text'
};

// ── Saved locations (synced to the watch's SavedLocation table) ──────────────
let savedLocations = [];
function loadSavedLocations() {
    try { savedLocations = JSON.parse(localStorage.getItem('quran_saved_locations') || '[]'); }
    catch (_) { savedLocations = []; }
}
function persistSavedLocations() {
    try { localStorage.setItem('quran_saved_locations', JSON.stringify(savedLocations)); } catch (_) {}
}
function addSavedLocation(loc) {
    const id = loc.id || `loc_web_${Date.now()}`;
    const lat = Number(loc.latitude), lng = Number(loc.longitude);
    if (!Number.isFinite(lat) || !Number.isFinite(lng) || !loc.name) return;
    if (savedLocations.some(l => l.id === id || (l.name === loc.name && Math.abs(l.latitude - lat) < 1e-4 && Math.abs(l.longitude - lng) < 1e-4))) return;
    savedLocations.push({ id, name: String(loc.name).trim(), latitude: lat, longitude: lng });
    persistSavedLocations(); renderLocations(); scheduleAutoSync();
}
function removeSavedLocation(id) {
    savedLocations = savedLocations.filter(l => l.id !== id);
    persistSavedLocations(); renderLocations(); scheduleAutoSync();
}
function promptCustomLocation() {
    const name = window.prompt('اسم الموقع:');
    if (!name) return;
    const lat = window.prompt('خط العرض (latitude):');
    if (lat === null) return;
    const lng = window.prompt('خط الطول (longitude):');
    if (lng === null) return;
    addSavedLocation({ name, latitude: lat, longitude: lng });
}

// Selection, History & Drag State
let selectedIndices = new Set([0]);
let primarySelectedIdx = 0;
let undoStack = [];
let redoStack = [];
let clipboardTiles = [];

let dragType = null; // 'grid-tile', 'scale-text', 'scale-icon', 'text', 'icon'
let dragStartPointerX = 0;
let dragStartPointerY = 0;
let startTilesSnapshot = [];
// Row heights must also be snapshotted, otherwise a drag reads back its own
// half-applied result on every pointermove and the row grows without bound.
let startRowWeightsSnapshot = {};
let ghostTargetSlot = null; // { rowIndex, insertIndex, colSpan }
let lastHapticSlotKey = '';
let dragResizeCorner = null;
let interactionHistoryPushed = false;
let recentAutoLayoutPatterns = [];
let recentPaletteIndex = -1;
let initialLayoutSnapshot = null;

const AUTO_LAYOUT_PATTERNS = [
    { id: 'balanced-halves', weight: 10, rows: [[6, 6], [4, 8], [4, 4, 4]] },
    { id: 'hero-top', weight: 10, rows: [[12], [6, 6], [4, 4, 4]] },
    { id: 'hero-middle', weight: 9, rows: [[6, 6], [12], [4, 4, 4]] },
    { id: 'hero-bottom', weight: 9, rows: [[4, 4, 4], [6, 6], [12]] },
    { id: 'reading-focus', weight: 10, rows: [[12], [12], [4, 4, 4]] },
    { id: 'prayer-ribbon', weight: 10, rows: [[12], [3, 3, 3, 3], [6, 6]] },
    { id: 'asymmetric-left', weight: 8, rows: [[8, 4], [4, 4, 4], [6, 6]] },
    { id: 'asymmetric-right', weight: 8, rows: [[4, 8], [4, 4, 4], [6, 6]] },
    { id: 'tri-column-split', weight: 7, rows: [[4, 4, 4], [6, 6], [4, 4, 4]] },
    { id: 'mosaic-dense', weight: 7, rows: [[3, 6, 3], [6, 6], [4, 4, 4]] },
    { id: 'quad-grid', weight: 8, rows: [[6, 6], [6, 6], [6, 6]] },
    { id: 'compact-dock', weight: 8, rows: [[12], [6, 6], [3, 3, 3, 3]] },
    { id: 'golden-stack', weight: 7, rows: [[7, 5], [5, 7], [12]] },
    { id: 'hud-center', weight: 8, rows: [[4, 4, 4], [12], [4, 4, 4]] },
    { id: 'strip-hero-bottom', weight: 7, rows: [[6, 6], [4, 4, 4], [12]] },
    { id: 'twin-tall', weight: 6, rows: [[6, 6], [3, 3, 3, 3], [6, 6]] },
    { id: 'smart-five-flow', weight: 7, rows: [[4, 8], [8, 4], [4, 4, 4]] },
    { id: 'islamic-dashboard', weight: 8, rows: [[12], [6, 6], [6, 6], [12]] },
    { id: 'nine-mosaic', weight: 6, rows: [[4, 4, 4], [4, 4, 4], [4, 4, 4]] },
    { id: 'staggered-zigzag', weight: 6, rows: [[7, 5], [3, 6, 3], [5, 7]] },
    { id: 'four-deck', weight: 6, rows: [[6, 6], [4, 8], [8, 4], [6, 6]] },
    { id: 'dense-tools', weight: 6, rows: [[4, 4, 4], [3, 3, 3, 3], [6, 6]] },
    { id: 'minimal-duo', weight: 5, rows: [[12], [6, 6]] },
    { id: 'quiet-clock', weight: 5, rows: [[12], [4, 4, 4], [6, 6]] }
];

const COLOR_PALETTES = [
    { name: 'دمشقي ملكي', bg: '#0F172A', colors: ['#1E293B', '#0E7490', '#D97706', '#334155', '#475569', '#155E75', '#1E3A8A'] },
    { name: 'أندلسي زمردي', bg: '#064E3B', colors: ['#065F46', '#047857', '#059669', '#10B981', '#0F766E', '#115E59', '#044E45'] },
    { name: 'قرطبة ذهبي', bg: '#1C1917', colors: ['#292524', '#78350F', '#B45309', '#D97706', '#92400E', '#451A03', '#A16207'] },
    { name: 'ياقوت مغربي', bg: '#18181B', colors: ['#27272A', '#881337', '#BE123C', '#E11D48', '#9F1239', '#4C0519', '#701A75'] },
    { name: 'ليلي كحلي', bg: '#0B132B', colors: ['#1C2541', '#3A506B', '#5BC0BE', '#0E7490', '#2E4057', '#1D3557', '#0F2B48'] },
    { name: 'موف إسلامي', bg: '#1E1B4B', colors: ['#312E81', '#4338CA', '#6366F1', '#7C3AED', '#5B21B6', '#4C1D95', '#3730A3'] },
    { name: 'صحراوي دافئ', bg: '#292524', colors: ['#44403C', '#78350F', '#A16207', '#CA8A04', '#57534E', '#854D0E', '#6B390D'] },
    { name: 'مينيمال أردوازي', bg: '#0F172A', colors: ['#1E293B', '#334155', '#475569', '#64748B', '#1E293B', '#334155', '#475569'] },
    { name: 'فيروزي مشرق', bg: '#042F2E', colors: ['#115E59', '#0D9488', '#14B8A6', '#2DD4BF', '#0F766E', '#065F46', '#134E4A'] }
];

const RESIZE_EDGES = ['n', 'e', 's', 'w'];

const surahNamesAr = [
    "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس",
    "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه",
    "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم",
    "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
    "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق",
    "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة",
    "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج",
    "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
    "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد",
    "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات",
    "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر",
    "المسد", "الإخلاص", "الفلق", "الناس"
];

const iconLibrary = [
    { id: 'default', title: 'الأيقونة الافتراضية', icon: '⭐' },
    { id: 'quran', title: 'مصحف شريف', icon: '📖' },
    { id: 'kaaba', title: 'الكعبة المشرفة', icon: '🕋' },
    { id: 'mosque', title: 'مسجد ومئذنة', icon: '🕌' },
    { id: 'tasbih', title: 'سبحة وتسبيح', icon: '📿' },
    { id: 'crescent', title: 'هلال رمضان', icon: '🌙' },
    { id: 'star_islamic', title: 'نجمة إسلامية', icon: '✨' },
    { id: 'dua', title: 'دعاء وتضرع', icon: '🤲' },
    { id: 'clock', title: 'ساعة يد / وقت', icon: '⏰' },
    { id: 'stopwatch', title: 'ساعة إيقاف', icon: '⏱️' },
    { id: 'hourglass', title: 'ساعة رملية / متبقي', icon: '⏳' },
    { id: 'hourglass_done', title: 'ساعة رملية منتهية', icon: '⌛' },
    { id: 'calendar', title: 'تقويم وتاريخ', icon: '📅' },
    { id: 'battery', title: 'بطارية وطاقة', icon: '🔋' },
    { id: 'settings', title: 'إعدادات وترس', icon: '⚙️' },
    { id: 'pin', title: 'موقع ودبوس', icon: '📍' },
    { id: 'compass', title: 'بوصلة اتجاه', icon: '🧭' },
    { id: 'mic', title: 'ميكروفون وتسجيل', icon: '🎤' },
    { id: 'bookmark', title: 'علامة مرجعية', icon: '🔖' },
    { id: 'folder', title: 'مجلد ملفات', icon: '📁' },
    { id: 'bolt', title: 'صاعقة وبرق', icon: '⚡' },
    { id: 'bell', title: 'جرس وتنبيه', icon: '🔔' },
    { id: 'heart', title: 'قلب ونبض', icon: '❤️' },
    { id: 'sun', title: 'شمس ساطعة', icon: '☀️' },
    { id: 'cloud_sun', title: 'غائم جزئياً', icon: '⛅' },
    { id: 'rain', title: 'مطر', icon: '🌧️' },
    { id: 'cloud', title: 'غيوم', icon: '☁️' }
];

// A complete local catalogue. It deliberately stays data-only so it is safe to sync and easy to replace with a custom SVG pack later.
const expandedIconLibrary = [
    ['quran_open','مصحف مفتوح','📖'],['quran_closed','مصحف مغلق','📕'],['quran_bookmark','علامة المصحف','🔖'],['quran_read','قراءة القرآن','📜'],['quran_audio','تلاوة صوتية','🎧'],['quran_search','بحث قرآني','🔎'],['quran_pages','صفحات المصحف','📄'],['quran_verse','آية','۝'],['bismillah','البسملة','﷽'],['kaaba','الكعبة','🕋'],['qibla_arrow','اتجاه القبلة','➤'],['qibla_compass','بوصلة القبلة','🧭'],['mosque','مسجد','🕌'],['minaret','مئذنة','🕌'],['crescent_moon','هلال','☪'],['tasbih_beads','مسبحة','📿'],['dua_hands','دعاء','🤲'],['prayer_mat','سجادة صلاة','🟫'],['adhan','الأذان','📣'],['fajr','الفجر','🌅'],['dhuhr','الظهر','☀️'],['asr','العصر','🌤️'],['maghrib','المغرب','🌇'],['isha','العشاء','🌙'],['ramadan','رمضان','🌙'],['eid','العيد','🎉'],['zakat','الزكاة','🤝'],['sadaqah','صدقة','💝'],['fasting','صيام','🍽️'],['hajj','الحج','🕋'],['umrah','العمرة','🕋'],['calendar_hijri','تقويم هجري','🗓️'],
    ['time','الوقت','🕒'],['alarm','منبّه','⏰'],['timer','مؤقت','⏲️'],['stopwatch','ساعة إيقاف','⏱️'],['hourglass','ساعة رملية','⌛'],['date','التاريخ','📅'],['sunrise','شروق','🌄'],['sunset','غروب','🌆'],['night','ليل','🌃'],['star','نجمة','⭐'],['sparkles','بريق','✨'],['heart','قلب','♥'],['leaf','ورقة','🍃'],['flower','زهرة','🌸'],['water','ماء','💧'],['cloud','سحابة','☁️'],['rain','مطر','🌧️'],['wind','رياح','💨'],['temperature','حرارة','🌡️'],['weather','الطقس','⛅'],
    ['location','موقع','📍'],['map','خريطة','🗺️'],['navigation','ملاحة','🧭'],['home','الرئيسية','⌂'],['folder','مجلد','📁'],['archive','أرشيف','🗃️'],['bookmark','علامة','🔖'],['note','ملاحظة','📝'],['microphone','ميكروفون','🎙️'],['headphones','سماعات','🎧'],['camera','كاميرا','📷'],['gallery','صور','🖼️'],['phone','هاتف','☎'],['message','رسائل','✉'],['bell','جرس','🔔'],['settings','إعدادات','⚙'],['sync','مزامنة','⇅'],['wifi','واي فاي','⌁'],['bluetooth','بلوتوث','ᛒ'],['battery','بطارية','🔋'],['charging','شحن','⚡'],['lock','قفل','🔒'],['shield','حماية','🛡️'],['key','مفتاح','🔑'],['check','تم','✓'],['plus','إضافة','＋'],['edit','تعديل','✎'],['delete','حذف','⌫'],['share','مشاركة','↗'],['download','تنزيل','⇩'],['upload','رفع','⇧'],['refresh','تحديث','↻'],['play','تشغيل','▶'],['pause','إيقاف','Ⅱ'],['next','التالي','›'],['back','السابق','‹'],['info','معلومات','ⓘ'],['help','مساعدة','?'],['eye','عرض','◉'],['filter','تصفية','≡'],['grid','شبكة','▦'],['list','قائمة','☷'],['palette','ألوان','🎨'],['pattern','زخرفة','✺'],['eight_star','نجمة ثمانية','✦'],['arabesque','أرابيسك','❈'],['andalusian','أندلسي','✥'],['damascene','دمشقي','❖'],['egyptian','مصري','𓂀'],['ottoman','عثماني','✤'],['persian','فارسي','❋'],['asian_islamic','آسيوي إسلامي','✹'],['world_pattern','زخرفة عالمية','◎']
].map(([id, title, icon]) => ({ id, title, icon }));
iconLibrary.push(...expandedIconLibrary.filter(item => !iconLibrary.some(existing => existing.id === item.id)));

const tileActionsList = [
    { id: 'color_only', title: '🎨 بلاطة لون فقط (تزيينية)' },
    { id: 'clock_big', title: '⏰ الساعة الرقمية' },
    { id: 'prayer_countdown', title: '⏳ متبقي الصلاة القادمة' },
    { id: 'prayer_elapsed', title: '⌛ الوقت المنقضي على الصلاة' },
    { id: 'prayer', title: '🕌 مواقيت الصلاة اليومية' },
    { id: 'prayer_strip_5', title: '▤ صف مواقيت الصلاة الكامل' },
    { id: 'quran_resume', title: '📖 موضع القراءة الأخير' },
    { id: 'quran', title: '📖 المصحف الشريف (الفهرس)' },
    { id: 'tasbih', title: '📿 السبحة الإلكترونية' },
    { id: 'qibla', title: '🕋 بوصلة القبلة' },
    { id: 'folder_islamic', title: '📁 مجلد إسلاميات' },
    { id: 'folder_tools', title: '📁 مجلد الأدوات' },
    { id: 'folder_custom', title: '📁 مجلد مخصص' },
    { id: 'date_big', title: '📅 التاريخ الهجري والميلادي' },
    { id: 'bookmarks', title: '🔖 العلامات المرجعية' },
    { id: 'locations', title: '📍 الموا مواقع المحفوظة' },
    { id: 'settings', title: '⚙️ الإعدادات والمزامنة' },
    { id: 'battery', title: '🔋 نسبة شحن البطارية' },
    { id: 'weather', title: '⛅ حالة الطقس' },
    { id: 'auto_layout', title: '✦ ترتيب تلقائي جديد' },
    { id: 'palette_shuffle', title: '🎨 تبديل الألوان تلقائياً' },
    { id: 'presets', title: '📑 القوالب الجاهزة' }
];

const featureActionCatalog = {
    default: [{ id: '', title: 'بلا إجراء' }, { id: 'quick_edit', title: '✎ تحرير سريع' }],
    locations: [
        { id: 'locations_recent', title: '◷ المواقع الأخيرة' },
        { id: 'locations_active', title: '⌖ الانتقال إلى الموقع النشط' },
        { id: 'locations_navigate', title: '↗ فتح الملاحة' },
        { id: 'locations_add_current', title: '＋ حفظ موقعي الحالي' }
    ],
    quran_resume: [
        { id: 'reader_resume', title: '▶ متابعة القراءة' },
        { id: 'reader_next_ayah', title: '› الآية التالية' },
        { id: 'reader_bookmark', title: '🔖 إضافة علامة' },
        { id: 'reader_last_surah', title: '☰ فهرس السورة' }
    ],
    quran: [{ id: 'reader_index', title: '☰ فهرس المصحف' }, { id: 'reader_search', title: '⌕ بحث في القرآن' }, { id: 'reader_bookmarks', title: '🔖 العلامات المرجعية' }],
    tasbih: [{ id: 'tasbih_increment', title: '＋ تسبيحة واحدة' }, { id: 'tasbih_reset', title: '↺ تصفير العداد' }, { id: 'tasbih_select_dhikr', title: '☰ اختيار الذكر' }],
    prayer_countdown: [{ id: 'prayer_schedule', title: '◫ المواقيت اليومية' }, { id: 'prayer_next', title: '› الصلاة التالية' }, { id: 'prayer_reminders', title: '🔔 التنبيهات' }],
    qibla: [{ id: 'qibla_compass', title: '🕋 فتح البوصلة' }, { id: 'qibla_calibrate', title: '◌ معايرة البوصلة' }],
    folder_islamic: [{ id: 'folder_islamic_open', title: '📁 فتح الإسلاميات' }, { id: 'folder_islamic_customize', title: '✎ تخصيص المجلد' }],
    folder_tools: [{ id: 'folder_tools_open', title: '📁 فتح الأدوات' }, { id: 'folder_tools_customize', title: '✎ تخصيص المجلد' }],
    weather: [{ id: 'weather_details', title: '⛅ تفاصيل الطقس' }, { id: 'weather_refresh', title: '↻ تحديث الطقس' }],
    battery: [{ id: 'battery_status', title: '🔋 حالة البطارية' }, { id: 'battery_saver', title: '◐ وضع توفير الطاقة' }],
    settings: [{ id: 'settings_open', title: '⚙ فتح الإعدادات' }, { id: 'cloud_sync_pull', title: '⇅ جلب المزامنة' }, { id: 'settings_notifications', title: '🔔 إعداد التنبيهات' }]
    ,auto_layout: [{ id: 'auto_layout', title: '✦ ترتيب جديد' }, { id: 'auto_layout_restore', title: '↺ استعادة الترتيب الأول' }]
};

const argentinaLocations = [
    { id: "ba_caba", name: "بوينس آيرس (العاصمة CABA)", lat: -34.6037, lng: -58.3816, qibla: "72° NE" },
    { id: "king_fahd_center", name: "مركز الملك فهد الثقافي الإسلامي (Palermo)", lat: -34.5714, lng: -58.4253, qibla: "72° NE" },
    { id: "al_ahmad_mosque", name: "مسجد الأحمد (San Cristóbal)", lat: -34.6247, lng: -58.3975, qibla: "72° NE" },
    { id: "cordoba", name: "كوردوبا (Córdoba)", lat: -31.4201, lng: -64.1888, qibla: "73° NE" },
    { id: "rosario", name: "روساريو (Rosario)", lat: -32.9468, lng: -60.6393, qibla: "73° NE" },
    { id: "mendoza", name: "ميندوزا (Mendoza)", lat: -32.8895, lng: -68.8458, qibla: "74° NE" },
    { id: "mar_del_plata", name: "مار ديل بلاتا (Mar del Plata)", lat: -38.0055, lng: -57.5560, qibla: "70° NE" },
    { id: "salta", name: "سالتا (Salta)", lat: -24.7821, lng: -65.4232, qibla: "75° NE" },
    { id: "tucuman", name: "سان ميغيل دي توكومان (Tucumán)", lat: -26.8083, lng: -65.2176, qibla: "75° NE" },
    { id: "santa_fe", name: "سانتا في (Santa Fe)", lat: -31.6333, lng: -60.7000, qibla: "73° NE" }
];

let quranData = null;

// ── FLEXIBLE 12-UNIT GRID, EDGE MARGINS & FREEFORM LAYERS ──
const MAX_EDITOR_ROWS = 5;

function getAppearance() {
    const defaults = { tileShape: 'square-connected', pattern: 'star-eight', iconPalette: 'jewel' };
    tileConfig.appearance = { ...defaults, ...(tileConfig.appearance || {}) };
    return tileConfig.appearance;
}

function applyAppearanceControl(key, value) {
    pushHistory();
    tileConfig.appearance = { ...getAppearance(), [key]: value };
    validateAndPackGrid();
    renderCanvas();
    saveLocalDraft();
    updateSyncStatus('تم تطبيق مظهر الساعة', 'success');
}

function cycleTileShape() {
    const shapes = ['square-connected', 'oval', 'mixed', 'circle'];
    const currentIndex = shapes.indexOf(getAppearance().tileShape);
    const nextShape = shapes[(currentIndex + 1) % shapes.length];
    applyAppearanceControl('tileShape', nextShape);
    const control = document.getElementById('settingTileShape');
    if (control) control.value = nextShape;
}

function captureInitialLayout() {
    initialLayoutSnapshot = JSON.parse(JSON.stringify(tileConfig));
}

function restoreInitialLayout() {
    if (!initialLayoutSnapshot) return;
    pushHistory();
    tileConfig = JSON.parse(JSON.stringify(initialLayoutSnapshot));
    validateAndPackGrid();
    renderCanvas();
    updateEditor();
    scheduleAutoSync();
    updateSyncStatus('تمت استعادة الترتيب الأول', 'success');
}

function getCanvasInsets() {
    const defaults = { top: 0, right: 0, bottom: 0, left: 0 };
    const saved = tileConfig.canvasInsets || {};
    return Object.fromEntries(Object.entries(defaults).map(([edge, value]) => [
        edge,
        Math.max(0, Math.min(18, Number(saved[edge] ?? value) || 0))
    ]));
}

function clampManualSlot(slot, insets) {
    const minSize = 10;
    const usableWidth = 100 - insets.left - insets.right;
    const usableHeight = 100 - insets.top - insets.bottom;
    slot.width = Math.max(minSize, Math.min(usableWidth, Number(slot.width) || minSize));
    slot.height = Math.max(minSize, Math.min(usableHeight, Number(slot.height) || minSize));
    slot.x = Math.max(insets.left, Math.min(100 - insets.right - slot.width, Number(slot.x) || insets.left));
    slot.y = Math.max(insets.top, Math.min(100 - insets.bottom - slot.height, Number(slot.y) || insets.top));
}

function validateAndPackGrid() {
    if (!tileConfig.tiles.length) return;
    // The editor is now a collision-free grid. Legacy freeform drafts are safely returned to it.
    tileConfig.tiles.forEach(tile => { tile.manualLayout = false; });
    let rowsMap = {};
    tileConfig.tiles.forEach((t, i) => {
        let r = t.rowIndex !== undefined ? t.rowIndex : Math.floor(i / 2);
        if (!rowsMap[r]) rowsMap[r] = [];
        rowsMap[r].push(t);
    });

    let sortedRowKeys = Object.keys(rowsMap).map(Number).sort((a, b) => a - b);
    const insets = getCanvasInsets();
    const usableWidth = 100 - insets.left - insets.right;
    const usableHeight = 100 - insets.top - insets.bottom;
    const oldWeights = tileConfig.rowWeights || {};
    const weights = sortedRowKeys.map((rowKey, index) => Math.max(.22, Number(oldWeights[rowKey] ?? oldWeights[index] ?? 1) || 1));
    const totalWeight = weights.reduce((sum, weight) => sum + weight, 0);
    const normalizedWeights = {};
    let currentY = insets.top;

    sortedRowKeys.forEach((rKey, rIdx) => {
        let rowTiles = rowsMap[rKey];
        rowTiles.forEach(t => t.rowIndex = rIdx);
        normalizedWeights[rIdx] = weights[rIdx];
        const rowHeightPct = usableHeight * (weights[rIdx] / totalWeight);

        // Normalize every row precisely to 12 units. This prevents both overlap and blank cells.
        const minimum = rowTiles.length <= 6 ? 2 : 1;
        const desiredTotal = rowTiles.reduce((sum, tile) => sum + Math.max(minimum, Number(tile.colSpan) || 4), 0);
        let remainingUnits = 12;
        rowTiles.forEach((tile, index) => {
            const isLast = index === rowTiles.length - 1;
            const remainingTiles = rowTiles.length - index - 1;
            const requested = Math.max(minimum, Number(tile.colSpan) || 4);
            const proportional = Math.round((requested / desiredTotal) * 12);
            tile.colSpan = isLast
                ? remainingUnits
                : Math.max(minimum, Math.min(remainingUnits - (remainingTiles * minimum), proportional));
            remainingUnits -= tile.colSpan;
        });

        // Pack all tiles left-to-right with a small visual gutter; no tile can cover another.
        let currentX = 0;
        rowTiles.forEach(t => {
            t.x = insets.left + (currentX / 12) * usableWidth;
            t.y = currentY;
            t.width = ((t.colSpan || 4) / 12) * usableWidth;
            t.height = rowHeightPct;
            currentX += (t.colSpan || 4);
        });
        currentY += rowHeightPct;
    });
    tileConfig.canvasInsets = insets;
    tileConfig.rowWeights = normalizedWeights;
}

// ── UNDO / REDO HISTORY ──
function pushHistory() {
    undoStack.push(JSON.parse(JSON.stringify({
        tiles: tileConfig.tiles,
        rowWeights: tileConfig.rowWeights,
        canvasInsets: tileConfig.canvasInsets
    })));
    if (undoStack.length > 40) undoStack.shift();
    redoStack = [];
}

function undo() {
    if (undoStack.length === 0) return;
    redoStack.push(JSON.parse(JSON.stringify({ tiles: tileConfig.tiles, rowWeights: tileConfig.rowWeights, canvasInsets: tileConfig.canvasInsets })));
    const snapshot = undoStack.pop();
    tileConfig.tiles = snapshot.tiles || snapshot;
    if (snapshot.rowWeights) tileConfig.rowWeights = snapshot.rowWeights;
    if (snapshot.canvasInsets) tileConfig.canvasInsets = snapshot.canvasInsets;
    selectedIndices.clear();
    primarySelectedIdx = tileConfig.tiles.length > 0 ? 0 : -1;
    if (primarySelectedIdx >= 0) selectedIndices.add(primarySelectedIdx);
    validateAndPackGrid();
    renderCanvas();
    updateEditor();
    updateEditor();
    scheduleAutoSync();
}

function redo() {
    if (redoStack.length === 0) return;
    undoStack.push(JSON.parse(JSON.stringify({ tiles: tileConfig.tiles, rowWeights: tileConfig.rowWeights, canvasInsets: tileConfig.canvasInsets })));
    const snapshot = redoStack.pop();
    tileConfig.tiles = snapshot.tiles || snapshot;
    if (snapshot.rowWeights) tileConfig.rowWeights = snapshot.rowWeights;
    if (snapshot.canvasInsets) tileConfig.canvasInsets = snapshot.canvasInsets;
    selectedIndices.clear();
    primarySelectedIdx = tileConfig.tiles.length > 0 ? 0 : -1;
    if (primarySelectedIdx >= 0) selectedIndices.add(primarySelectedIdx);
    validateAndPackGrid();
    renderCanvas();
    updateEditor();
    scheduleAutoSync();
}

function copySelectedTiles() {
    if (selectedIndices.size === 0) return;
    clipboardTiles = Array.from(selectedIndices).map(idx => JSON.parse(JSON.stringify(tileConfig.tiles[idx])));
}

function pasteCopiedTiles() {
    if (clipboardTiles.length === 0) return;
    pushHistory();
    selectedIndices.clear();
    
    clipboardTiles.forEach(t => {
        let pasted = Object.assign({}, t, { rowIndex: tileConfig.tiles.length, colSpan: t.colSpan || 12 });
        tileConfig.tiles.push(pasted);
        let newIdx = tileConfig.tiles.length - 1;
        selectedIndices.add(newIdx);
        primarySelectedIdx = newIdx;
    });

    validateAndPackGrid();
    renderCanvas();
    updateEditor();
    scheduleAutoSync();
}

function selectAllTiles() {
    selectedIndices.clear();
    tileConfig.tiles.forEach((_, i) => selectedIndices.add(i));
    primarySelectedIdx = tileConfig.tiles.length > 0 ? 0 : -1;
    renderCanvas();
    updateEditor();
}

function deleteSelectedTiles() {
    if (selectedIndices.size === 0) return;
    pushHistory();
    tileConfig.tiles = tileConfig.tiles.filter((_, i) => !selectedIndices.has(i));
    selectedIndices.clear();
    primarySelectedIdx = tileConfig.tiles.length > 0 ? 0 : -1;
    if (primarySelectedIdx >= 0) selectedIndices.add(primarySelectedIdx);
    validateAndPackGrid();
    renderCanvas();
    updateEditor();
    scheduleAutoSync();
}

// ── KEYBOARD SHORTCUTS LISTENER ──
function setupKeyboardShortcuts() {
    window.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && closeDesignerSurfaces()) {
            e.preventDefault();
            return;
        }
        const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';
        if (activeTag === 'input' || activeTag === 'textarea' || activeTag === 'select') return;

        const isCtrlOrCmd = e.ctrlKey || e.metaKey;

        if (isCtrlOrCmd && e.key.toLowerCase() === 'z' && !e.shiftKey) {
            e.preventDefault();
            undo();
        } else if ((isCtrlOrCmd && e.key.toLowerCase() === 'y') || (isCtrlOrCmd && e.shiftKey && e.key.toLowerCase() === 'z')) {
            e.preventDefault();
            redo();
        } else if (isCtrlOrCmd && e.key.toLowerCase() === 'c') {
            e.preventDefault();
            copySelectedTiles();
        } else if (isCtrlOrCmd && e.key.toLowerCase() === 'v') {
            e.preventDefault();
            pasteCopiedTiles();
        } else if (isCtrlOrCmd && e.key.toLowerCase() === 'a') {
            e.preventDefault();
            selectAllTiles();
        } else if (e.key === 'Delete' || e.key === 'Backspace') {
            e.preventDefault();
            deleteSelectedTiles();
        }
    });
}

function initApp() {
    restoreLocalDraft();
    setupTabs();
    populateSelects();
    initColorSelects();
    validateAndPackGrid();
    captureInitialLayout();
    renderCanvas();
    updateEditor();
    setupCanvasEvents();
    setupQuranSearch();
    renderBookmarks();
    loadSavedLocations();
    renderLocations();
    document.getElementById('btnAddCustomLocation')?.addEventListener('click', promptCustomLocation);
    setupSettingsTab();
    setupPresetsManager();
    setupKeyboardShortcuts();
    setupFinderControls();
    initDesignerPanels();
    initInspectorToolbar();
    initStickyCanvas();

    // Toolbar Buttons
    document.getElementById('btnUndo')?.addEventListener('click', undo);
    document.getElementById('btnRedo')?.addEventListener('click', redo);
    document.getElementById('btnCopy')?.addEventListener('click', copySelectedTiles);
    document.getElementById('btnPaste')?.addEventListener('click', pasteCopiedTiles);
    document.getElementById('btnSelectAll')?.addEventListener('click', selectAllTiles);
    document.getElementById('btnDeleteKey')?.addEventListener('click', deleteSelectedTiles);
    
    // Sync Buttons
    document.getElementById('btnSaveTilesToWatch')?.addEventListener('click', () => syncAll(true));
    document.getElementById('btnCloudPush')?.addEventListener('click', () => syncAll(true));
    document.getElementById('btnCloudPull')?.addEventListener('click', () => pullFromCloud());
    document.getElementById('btnLocalSync')?.addEventListener('click', () => syncAll(true));
    
    // Toggle Sync Banner
    let syncBody = document.getElementById('syncBannerBody');
    let syncIcon = document.getElementById('syncToggleIcon');
    document.getElementById('btnToggleSync')?.addEventListener('click', () => {
        if(syncBody.style.display === 'none') {
            syncBody.style.display = 'grid';
            syncIcon.textContent = '🔽';
        } else {
            syncBody.style.display = 'none';
            syncIcon.textContent = '◀️';
        }
    });

    // Auto Layout Grid & Color Palette Shuffler
    document.getElementById('btnAutoLayout')?.addEventListener('click', () => {
        pushHistory();
        applySmartAutoLayout();
        renderCanvas();
        updateEditor();
        scheduleAutoSync();
    });
    document.getElementById('btnShuffleColors')?.addEventListener('click', () => {
        pushHistory();
        shuffleColorPalette();
        renderCanvas();
        updateEditor();
        scheduleAutoSync();
    });
    document.getElementById('btnCycleTileShape')?.addEventListener('click', cycleTileShape);

    // Delete Tile
    document.getElementById('btnDeleteTile')?.addEventListener('click', deleteSelectedTiles);

    // Editor field listeners
    ['tileAction', 'tileBgColor', 'tileFontColor', 'tileFontSize', 'tileFontFamily', 'tileDisplayStyle', 'tileIconStyle', 'tileIconColor', 'tileIconType', 'tileTapAction', 'tileLongPressAction', 'tileCustomLabel', 'tileIconCustom'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.addEventListener('input', () => onEditorChange(id));
            el.addEventListener('change', () => onEditorChange(id));
        }
    });
}


/**
 * Keeps the simulator on screen while the properties are being edited.
 *
 * On a narrow window the properties rail sits under the canvas, so scrolling
 * down to a field used to push the watch off the top -- you were editing a
 * design you could not see. The canvas is pinned there (CSS), and this marks
 * the moment it leaves the top of the page so it can shrink out of the way
 * instead of eating the viewport.
 */
function initStickyCanvas() {
    const grid = document.querySelector('#tab-tiles .designer-grid');
    if (!grid) return;
    const update = () => {
        if (!document.getElementById('tab-tiles')?.classList.contains('active')) return;
        grid.classList.toggle('canvas-compact', grid.getBoundingClientRect().top < 56);
    };
    window.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update, { passive: true });
    update();
}

function setupFinderControls() {
    document.querySelectorAll('[data-open-tab]').forEach(button => {
        button.addEventListener('click', () => activateTab(button.dataset.openTab));
    });
    document.querySelectorAll('[data-col-span]').forEach(button => {
        button.addEventListener('click', () => window.setTileColSpan(Number(button.dataset.colSpan)));
    });
    document.querySelectorAll('[data-text-align]').forEach(button => {
        button.addEventListener('click', () => {
            if (primarySelectedIdx < 0 || !tileConfig.tiles[primarySelectedIdx]) return;
            pushHistory();
            const align = button.dataset.textAlign;
            selectedIndices.forEach(idx => {
                const tile = tileConfig.tiles[idx];
                if (tile) tile.textAlign = align;
            });
            document.querySelectorAll('[data-text-align]').forEach(b => b.classList.toggle('active', b === button));
            renderCanvas();
            scheduleAutoSync();
        });
    });
    document.querySelectorAll('[data-align-x]').forEach(button => {
        button.addEventListener('click', () => setTileAlignment('x', button.dataset.alignX));
    });
    document.querySelectorAll('[data-align-y]').forEach(button => {
        button.addEventListener('click', () => setTileAlignment('y', button.dataset.alignY));
    });
    document.querySelectorAll('[data-font-size]').forEach(button => {
        button.addEventListener('click', () => {
            const field = document.getElementById('tileFontSize');
            if (!field) return;
            field.value = button.dataset.fontSize;
            onEditorChange('tileFontSize');
        });
    });
    const fontRange = document.getElementById('tileFontSizeRange');
    if (fontRange) {
        // Live drag: repaint only the watch, commit history + sync on release.
        fontRange.addEventListener('input', () => applyFontSize(parseInt(fontRange.value), false));
        fontRange.addEventListener('change', () => applyFontSize(parseInt(fontRange.value), true));
    }
    document.querySelectorAll('[data-swatch]').forEach(button => {
        button.style.background = button.dataset.swatch;
        button.addEventListener('click', () => {
            const field = document.getElementById('tileBgColor');
            if (!field) return;
            setColorSelect('tileBgColor', button.dataset.swatch, '#334155');
            onEditorChange('tileBgColor');
        });
    });
    document.querySelectorAll('[data-web-preset]').forEach(card => {
        card.querySelector('button')?.addEventListener('click', () => {
            const aliases = { classic: 'default', prayer: 'prayer', quran: 'quran', minimal: 'minimal', day: 'default' };
            window.loadBuiltInPreset(aliases[card.dataset.webPreset] || 'default');
            activateTab('tiles');
        });
    });
    document.querySelectorAll('[data-preset-filter]').forEach(button => {
        button.addEventListener('click', () => {
            document.querySelectorAll('[data-preset-filter]').forEach(item => item.classList.toggle('active', item === button));
            document.querySelectorAll('[data-category]').forEach(card => {
                card.hidden = button.dataset.presetFilter !== 'all' && card.dataset.category !== button.dataset.presetFilter;
            });
        });
    });
    document.getElementById('btnAddTileRight')?.addEventListener('click', () => addTile('right'));
    document.getElementById('btnAddTileLeft')?.addEventListener('click', () => addTile('left'));
    document.getElementById('btnAddRow')?.addEventListener('click', () => addTile('new-row'));
    document.getElementById('btnAddPrayerStrip')?.addEventListener('click', addPrayerStripRow);
    document.getElementById('btnCompactRow')?.addEventListener('click', () => setSelectedRowCompact(true));
    document.getElementById('btnRestoreRow')?.addEventListener('click', () => setSelectedRowCompact(false));
    document.querySelectorAll('[data-edge-inset]').forEach(button => {
        button.addEventListener('click', () => toggleCanvasInset(button.dataset.edgeInset));
    });
    document.getElementById('btnExpandTile')?.addEventListener('click', () => window.setTileColSpan(12));
    document.getElementById('btnApplyFromToolbar')?.addEventListener('click', () => syncAll(true));
    document.getElementById('btnSaveCurrentPresetPrompt')?.addEventListener('click', saveCurrentPresetFromPrompt);
    document.getElementById('safeAreaToggle')?.addEventListener('change', event => {
        document.getElementById('watchScreenSimulator')?.classList.toggle('hide-safe-area', !event.target.checked);
    });
}

// ── DESIGNER MENUS ──
// Properties stay in the fixed left panel. Only compact command menus open here.

function setDesignerMenu(menuId, triggerId, open) {
    const menu = document.getElementById(menuId);
    const trigger = document.getElementById(triggerId);
    if (!menu || !trigger) return false;
    const shouldOpen = open ?? menu.hidden;
    menu.hidden = !shouldOpen;
    trigger.setAttribute('aria-expanded', String(shouldOpen));
    if (shouldOpen) {
        document.querySelectorAll('.designer-popover').forEach(other => {
            if (other !== menu) other.hidden = true;
        });
        document.querySelectorAll('[aria-controls="designerAddMenu"], [aria-controls="designerMoreMenu"], [aria-controls="designerLayersMenu"]').forEach(button => {
            if (button !== trigger) button.setAttribute('aria-expanded', 'false');
        });
    }
    return shouldOpen;
}

function closeDesignerSurfaces() {
    let closed = false;
    ['designerAddMenu', 'designerMoreMenu', 'designerLayersMenu'].forEach(menuId => {
        const menu = document.getElementById(menuId);
        if (!menu || menu.hidden) return;
        menu.hidden = true;
        const trigger = document.querySelector(`[aria-controls="${menuId}"]`);
        trigger?.setAttribute('aria-expanded', 'false');
        closed = true;
    });
    return closed;
}

function initDesignerPanels() {
    const addButton = document.getElementById('btnAddTile');
    const moreButton = document.getElementById('btnOpenDesignerMore');

    addButton?.addEventListener('click', () => setDesignerMenu('designerAddMenu', 'btnAddTile'));
    moreButton?.addEventListener('click', () => setDesignerMenu('designerMoreMenu', 'btnOpenDesignerMore'));
    document.querySelectorAll('[data-close-designer-menu]').forEach(button => {
        button.addEventListener('click', () => {
            const menuId = button.dataset.closeDesignerMenu;
            const menu = document.getElementById(menuId);
            const trigger = document.querySelector(`[aria-controls="${menuId}"]`);
            if (menu) menu.hidden = true;
            trigger?.setAttribute('aria-expanded', 'false');
            trigger?.focus?.();
        });
    });
    ['btnAddTileRight', 'btnAddTileLeft', 'btnAddRow', 'btnAddPrayerStrip'].forEach(id => {
        document.getElementById(id)?.addEventListener('click', () => setDesignerMenu('designerAddMenu', 'btnAddTile', false));
    });
    document.addEventListener('pointerdown', event => {
        if (event.target.closest('.designer-popover, .designer-more-button, #btnAddTile')) return;
        ['designerAddMenu', 'designerMoreMenu'].forEach(menuId => {
            const menu = document.getElementById(menuId);
            const triggerId = menuId === 'designerAddMenu' ? 'btnAddTile' : 'btnOpenDesignerMore';
            if (!menu?.hidden) setDesignerMenu(menuId, triggerId, false);
        });
    });
}

// ── COLOR DROPDOWNS ──
// Colours are picked from a fixed palette so every value round-trips to the
// watch identically (a free-form hex from the native picker was the sync gap).
const DESIGNER_PALETTE = [
    ['#0E7490', 'فيروزي'], ['#0284C7', 'أزرق'], ['#1D4ED8', 'أزرق ملكي'],
    ['#2563EB', 'أزرق فاتح'], ['#7C3AED', 'بنفسجي'], ['#AF52DE', 'بنفسجي فاتح'],
    ['#DB2777', 'وردي'], ['#DC2626', 'أحمر'], ['#EA580C', 'برتقالي'],
    ['#D97706', 'ذهبي'], ['#F59E0B', 'كهرماني'], ['#CA8A04', 'خردلي'],
    ['#16A34A', 'أخضر'], ['#10B981', 'أخضر نعناعي'], ['#059669', 'زمردي'], ['#0D9488', 'أخضر مزرق'],
    ['#334155', 'أردوازي'], ['#1F2937', 'فحمي'], ['#1C1C1E', 'أسود'],
    ['#0B1120', 'أسود داكن'], ['#64748B', 'رمادي'], ['#94A3B8', 'رمادي فاتح'],
    ['#E2E8F0', 'رمادي باهت'], ['#F8FAFC', 'أبيض مطفأ'], ['#FFFFFF', 'أبيض'],
];
const PALETTE_HEXES = DESIGNER_PALETTE.map(([hex]) => hex);

// ── Shared calm tile system ──────────────────────────────────────────────────
// These three numbers are the mirror of TilePanel / TILE_TINT_ALPHA /
// TILE_BORDER_ALPHA in HomeScreen.kt, so a tile looks the same here and on the
// watch: a dark panel, with the user's colour surviving as a tint + a hairline.
const TILE_PANEL_RGB = [12, 19, 25];
const TILE_TINT_ALPHA = 0.14;
const TILE_BORDER_ALPHA = 0.38;

function hexToRgb(hex) {
    const clean = normalizeHex(hex);
    if (!/^#[0-9A-F]{6}$/i.test(clean)) return null;
    return [1, 3, 5].map(i => parseInt(clean.slice(i, i + 2), 16));
}

function tileSurface(hex, connected) {
    const rgb = hexToRgb(hex) || [51, 65, 85];
    const bg = rgb.map((v, i) => Math.round(v * TILE_TINT_ALPHA + TILE_PANEL_RGB[i] * (1 - TILE_TINT_ALPHA)));
    // Connected tiles share every edge, so two hairlines meet and would draw a
    // double-weight seam. Halve them there so the grid reads as one thin rule.
    const alpha = connected ? TILE_BORDER_ALPHA * 0.55 : TILE_BORDER_ALPHA;
    return { bg: `rgb(${bg.join(',')})`, border: `rgba(${rgb.join(',')},${alpha.toFixed(3)})` };
}

function normalizeHex(value) {
    let hex = String(value || '').trim();
    if (/^#?[0-9a-fA-F]{3}$/.test(hex)) {
        hex = hex.replace('#', '');
        hex = '#' + hex.split('').map(c => c + c).join('');
    }
    if (!hex.startsWith('#')) hex = '#' + hex;
    return /^#[0-9a-fA-F]{6}$/.test(hex) ? hex.toUpperCase() : hex;
}

function initColorSelects() {
    ['tileBgColor', 'tileFontColor', 'tileIconColor'].forEach(id => {
        const select = document.getElementById(id);
        if (!select || select.tagName !== 'SELECT' || select.options.length) return;
        DESIGNER_PALETTE.forEach(([hex, name]) => {
            const option = document.createElement('option');
            option.value = hex;
            option.textContent = `${name} — ${hex}`;
            select.appendChild(option);
        });
    });
}

function setColorSelect(id, value, fallback) {
    const select = document.getElementById(id);
    if (!select) return;
    const hex = normalizeHex(value || fallback);
    // Keep any earlier custom option from lingering.
    [...select.querySelectorAll('option[data-custom]')].forEach(o => o.remove());
    if (!PALETTE_HEXES.includes(hex)) {
        const option = document.createElement('option');
        option.value = hex;
        option.dataset.custom = '1';
        option.textContent = `مخصص — ${hex}`;
        select.insertBefore(option, select.firstChild);
    }
    select.value = hex;
}

// ── INSPECTOR: EXPAND / COLLAPSE ALL + SECTION VISIBILITY ──
const INSPECTOR_SECTIONS = ['secContent', 'secInteract', 'secLayout', 'secFont', 'secColors'];
const INSPECTOR_PREFS_KEY = 'quran_watch_inspector_prefs';

function loadInspectorPrefs() {
    try { return JSON.parse(localStorage.getItem(INSPECTOR_PREFS_KEY)) || {}; }
    catch (_) { return {}; }
}
function saveInspectorPrefs(prefs) {
    try { localStorage.setItem(INSPECTOR_PREFS_KEY, JSON.stringify(prefs)); } catch (_) {}
}

function syncToggleAllButton() {
    const button = document.getElementById('btnToggleAllDetails');
    if (!button) return;
    const anyOpen = INSPECTOR_SECTIONS
        .map(sid => document.getElementById(sid))
        .some(el => el && !el.hidden && el.open);
    button.dataset.allOpen = String(anyOpen);
    button.textContent = anyOpen ? 'طيّ الكل' : 'توسيع الكل';
}

function initInspectorToolbar() {
    const prefs = loadInspectorPrefs();
    INSPECTOR_SECTIONS.forEach(sid => {
        const details = document.getElementById(sid);
        if (!details) return;
        if (prefs[sid] && prefs[sid].shown === false) details.hidden = true;
        details.open = !(prefs[sid] && prefs[sid].open === false);
        const checkbox = document.querySelector(`[data-section-toggle="${sid}"]`);
        if (checkbox) checkbox.checked = !details.hidden;
        details.addEventListener('toggle', () => {
            const current = loadInspectorPrefs();
            current[sid] = { ...(current[sid] || {}), open: details.open };
            saveInspectorPrefs(current);
            syncToggleAllButton();
        });
    });

    document.querySelectorAll('[data-section-toggle]').forEach(checkbox => {
        checkbox.addEventListener('change', () => {
            const sid = checkbox.dataset.sectionToggle;
            const details = document.getElementById(sid);
            if (details) details.hidden = !checkbox.checked;
            const current = loadInspectorPrefs();
            current[sid] = { ...(current[sid] || {}), shown: checkbox.checked };
            saveInspectorPrefs(current);
            syncToggleAllButton();
        });
    });

    document.getElementById('btnToggleAllDetails')?.addEventListener('click', () => {
        const open = document.getElementById('btnToggleAllDetails').dataset.allOpen !== 'true';
        const current = loadInspectorPrefs();
        INSPECTOR_SECTIONS.forEach(sid => {
            const details = document.getElementById(sid);
            if (!details || details.hidden) return;
            details.open = open;
            current[sid] = { ...(current[sid] || {}), open };
        });
        saveInspectorPrefs(current);
        syncToggleAllButton();
    });

    syncToggleAllButton();
}

function setTileAlignment(axis, value) {
    if (selectedIndices.size === 0) return;
    const mapped = { start: 22, center: 50, end: 78 }[value] || 50;
    pushHistory();
    selectedIndices.forEach(index => {
        const tile = tileConfig.tiles[index];
        if (!tile) return;
        if (axis === 'x') {
            tile.textX = mapped;
            tile.iconX = mapped;
        } else {
            tile.textY = mapped;
            tile.iconY = mapped;
        }
    });
    renderCanvas();
    scheduleAutoSync();
}

function applyFontSize(size, commit) {
    if (primarySelectedIdx < 0 || !tileConfig.tiles[primarySelectedIdx]) return;
    const clamped = Math.max(8, Math.min(48, Math.round(size) || 14));
    if (commit) pushHistory();
    selectedIndices.forEach(idx => { if (tileConfig.tiles[idx]) tileConfig.tiles[idx].fontSize = clamped; });
    const num = document.getElementById('tileFontSize');
    const range = document.getElementById('tileFontSizeRange');
    if (num) num.value = clamped;
    if (range && range.value !== String(clamped)) range.value = clamped;
    renderCanvas(!commit);
    if (commit) scheduleAutoSync();
}

function onEditorChange(id) {
    if(primarySelectedIdx < 0 || !tileConfig.tiles[primarySelectedIdx]) return;
    pushHistory();

    selectedIndices.forEach(idx => {
        let slot = tileConfig.tiles[idx];
        if(id === 'tileAction') {
            slot.id = document.getElementById('tileAction').value;
            const defaults = {
                tasbih: ['tasbih_increment', 'tasbih_reset'],
                qibla: ['qibla_compass', 'qibla_calibrate'],
                quran_resume: ['reader_resume', 'reader_last_surah'],
                prayer_strip_5: ['prayer_schedule', 'quick_edit'],
                auto_layout: ['auto_layout', 'auto_layout_restore']
            };
            if (defaults[slot.id]) [slot.tapAction, slot.longPressAction] = defaults[slot.id];
            if (slot.id.startsWith('folder') && !slot.folderItems?.length) {
                slot.folderItems = slot.id === 'folder_tools'
                    ? ['bookmarks', 'locations', 'settings']
                    : ['quran', 'tasbih', 'qibla', 'prayer'];
            }
        }
        if(id === 'tileBgColor') slot.colorHex = normalizeHex(document.getElementById('tileBgColor').value);
        if(id === 'tileFontColor') slot.fontColorHex = normalizeHex(document.getElementById('tileFontColor').value);
        if(id === 'tileFontSize') slot.fontSize = parseInt(document.getElementById('tileFontSize').value) || 14;
        if(id === 'tileFontFamily') slot.fontFamily = document.getElementById('tileFontFamily').value;
        if(id === 'tileDisplayStyle') slot.displayStyle = document.getElementById('tileDisplayStyle').value;
        if(id === 'tileIconStyle') slot.iconStyle = document.getElementById('tileIconStyle').value;
        if(id === 'tileIconColor') slot.iconColorHex = normalizeHex(document.getElementById('tileIconColor').value);
        if(id === 'tileIconType') {
            slot.iconType = document.getElementById('tileIconType').value;
            const custom = document.getElementById('tileIconCustom');
            if (custom) custom.value = '';
        }
        if(id === 'tileCustomLabel') slot.customLabel = document.getElementById('tileCustomLabel').value;
        // A typed emoji wins over the picker: it is stored in the same iconType
        // field, and getIcon() shows an unknown value verbatim.
        if(id === 'tileIconCustom') {
            const typed = document.getElementById('tileIconCustom').value.trim();
            slot.iconType = typed || 'default';
        }
        if(id === 'tileTapAction') slot.tapAction = document.getElementById('tileTapAction').value;
        if(id === 'tileLongPressAction') slot.longPressAction = document.getElementById('tileLongPressAction').value;
    });
    
    renderCanvas();
    if (id === 'tileAction') updateEditor();
    scheduleAutoSync();
}

window.setTileColSpan = function(span) {
    if (primarySelectedIdx < 0 || !tileConfig.tiles[primarySelectedIdx]) return;
    pushHistory();
    let target = tileConfig.tiles[primarySelectedIdx];
    let r = target.rowIndex !== undefined ? target.rowIndex : 0;
    
    rebalanceRowWidths(r, target, span);

    validateAndPackGrid();
    renderCanvas();
    updateEditor();
    scheduleAutoSync();
};

function rebalanceRowWidths(rowIndex, target, requestedSpan) {
    const rowTiles = tileConfig.tiles.filter(tile => tile.rowIndex === rowIndex);
    const otherTiles = rowTiles.filter(tile => tile !== target);
    if (!otherTiles.length) {
        target.colSpan = 12;
        target.manualLayout = false;
        return;
    }

    const minForOthers = otherTiles.length * 2;
    const targetSpan = Math.max(2, Math.min(12 - minForOthers, Math.round(requestedSpan)));
    const remaining = 12 - targetSpan;
    const oldTotal = otherTiles.reduce((sum, tile) => sum + Math.max(1, Number(tile.colSpan) || 4), 0);
    let allocated = 0;
    target.colSpan = targetSpan;
    target.manualLayout = false;
    otherTiles.forEach((tile, index) => {
        const isLast = index === otherTiles.length - 1;
        const proportional = Math.max(2, Math.round((Math.max(1, Number(tile.colSpan) || 4) / oldTotal) * remaining));
        tile.colSpan = isLast ? Math.max(2, remaining - allocated) : proportional;
        allocated += tile.colSpan;
        tile.manualLayout = false;
    });
    // Rounding can leave one or two units. Put them into the final neighbour.
    const finalSum = rowTiles.reduce((sum, tile) => sum + tile.colSpan, 0);
    otherTiles[otherTiles.length - 1].colSpan += 12 - finalSum;
}

function pickWeightedLayoutPattern() {
    const available = AUTO_LAYOUT_PATTERNS.filter(pattern => !recentAutoLayoutPatterns.includes(pattern.id));
    const pool = available.length ? available : AUTO_LAYOUT_PATTERNS;
    const totalWeight = pool.reduce((sum, pattern) => sum + pattern.weight, 0);
    let cursor = Math.random() * totalWeight;
    const selected = pool.find(pattern => (cursor -= pattern.weight) <= 0) || pool[pool.length - 1];
    recentAutoLayoutPatterns = [...recentAutoLayoutPatterns, selected.id].slice(-4);
    return selected;
}

function applySmartAutoLayout() {
    const appearanceConfig = getAppearance();
    if (appearanceConfig.tileShape === 'circle') {
        applyCircularAutoLayout();
        updateSyncStatus('تم إنشاء ترتيب دائري شعاعي', 'success');
        return;
    }
    const pattern = pickWeightedLayoutPattern();
    const importance = { clock_big: 1, quran_resume: 2, prayer_countdown: 3, prayer: 4, qibla: 5, locations: 6, folder_islamic: 7, folder_tools: 8, settings: 9 };
    const appearance = {
        clock_big: ['#0A84FF', 26], quran_resume: ['#087E8B', 15], quran: ['#0B7285', 16],
        prayer_strip_5: ['#1F8A5B', 12], prayer: ['#1F8A5B', 15], prayer_countdown: ['#34A853', 16],
        qibla: ['#9C5B12', 15], tasbih: ['#7A5AF8', 16], locations: ['#E38B18', 14],
        folder_islamic: ['#1479C9', 14], folder_tools: ['#E87516', 14], settings: ['#455468', 14]
    };
    const intrinsicSpan = tile => {
        // A verse opening and five prayer times need readable breathing room.
        if (['quran_resume', 'prayer_strip_5', 'prayer'].includes(tile.id)) return 12;
        if (tile.id === 'clock_big') return 6;
        if (tile.id === 'prayer_countdown') return 6;
        const labelLength = getPreviewLabel(tile).length;
        return labelLength > 19 ? 9 : labelLength > 13 ? 6 : 4;
    };
    tileConfig.tiles.sort((a, b) => (importance[a.id] ?? 50) - (importance[b.id] ?? 50));
    const patternSlots = pattern.rows.flatMap((spans, rowIndex) => spans.map(colSpan => ({ rowIndex, colSpan })));
    tileConfig.tiles.forEach((tile, index) => {
        delete tile.radialLayout;
        const natural = intrinsicSpan(tile);
        const planned = patternSlots[index % patternSlots.length];
        const cycle = Math.floor(index / patternSlots.length);
        tile.rowIndex = Math.min(MAX_EDITOR_ROWS - 1, planned.rowIndex + cycle * pattern.rows.length);
        tile.colSpan = natural === 12 && index < pattern.rows.length ? 12 : planned.colSpan;
        tile.manualLayout = false;
        const [color, fontSize] = appearance[tile.id] || ['#4B5563', 14];
        tile.colorHex = color;
        tile.fontSize = natural === 12 ? Math.max(13, fontSize) : Math.max(10, Math.min(fontSize, 16));
        tile.fontColorHex = '#ffffff';
        tile.iconColorHex = '#ffffff';
    });
    tileConfig.rowWeights = {};
    validateAndPackGrid();
    updateSyncStatus(`تم إنشاء ترتيب تلقائي ذكي جديد: ${pattern.id}`, 'success');
}

function applyCircularAutoLayout() {
    const importance = { qibla: 0, clock_big: 1, quran_resume: 2, prayer_countdown: 3, tasbih: 4, quran: 5, locations: 6 };
    tileConfig.tiles.sort((a, b) => (importance[a.id] ?? 50) - (importance[b.id] ?? 50));
    const center = tileConfig.tiles.find(tile => tile.id === 'qibla') || tileConfig.tiles[0];
    const ring = tileConfig.tiles.filter(tile => tile !== center);
    const centerSide = ring.length > 7 ? 26 : 31;
    center.radialLayout = { x: 50 - centerSide / 2, y: 50 - centerSide / 2, width: centerSide, height: centerSide };
    center.colSpan = 4;
    center.rowIndex = 1;
    center.fontSize = 15;
    center.colorHex = '#0D5B62';

    const ringSide = ring.length > 8 ? 15 : ring.length > 5 ? 17 : 20;
    const radius = ring.length > 8 ? 34 : ring.length > 5 ? 31 : 29;
    ring.forEach((tile, index) => {
        const angle = (-Math.PI / 2) + ((Math.PI * 2 * index) / ring.length);
        const centerX = 50 + Math.cos(angle) * radius;
        const centerY = 50 + Math.sin(angle) * radius;
        tile.radialLayout = {
            x: Math.max(2, Math.min(98 - ringSide, centerX - ringSide / 2)),
            y: Math.max(2, Math.min(98 - ringSide, centerY - ringSide / 2)),
            width: ringSide,
            height: ringSide
        };
        tile.colSpan = 3;
        tile.rowIndex = index % 3;
        tile.fontSize = Math.min(13, tile.fontSize || 13);
        tile.manualLayout = false;
    });
    tileConfig.rowWeights = {};
    validateAndPackGrid();
}

function shuffleColorPalette() {
    let nextIdx;
    do {
        nextIdx = Math.floor(Math.random() * COLOR_PALETTES.length);
    } while (nextIdx === recentPaletteIndex && COLOR_PALETTES.length > 1);
    recentPaletteIndex = nextIdx;
    
    const pal = COLOR_PALETTES[nextIdx];
    const colors = pal.colors;
    
    tileConfig.tiles.forEach((tile, i) => {
        const color = colors[i % colors.length];
        tile.colorHex = color;
        tile.fontColorHex = '#FFFFFF';
        tile.iconColorHex = '#FFFFFF';
    });
    
    updateSyncStatus(`تم تطبيق لوحة الألوان: ${pal.name} 🎨`, 'success');
}

window.quickAlignText = function(x, y) {
    if (primarySelectedIdx < 0) return;
    pushHistory();
    selectedIndices.forEach(idx => {
        let slot = tileConfig.tiles[idx];
        slot.textX = x;
        slot.textY = y;
    });
    renderCanvas();
    scheduleAutoSync();
};

window.quickSetFontSize = function(sz) {
    if (primarySelectedIdx < 0) return;
    pushHistory();
    selectedIndices.forEach(idx => {
        tileConfig.tiles[idx].fontSize = sz;
    });
    if (document.getElementById('tileFontSize')) document.getElementById('tileFontSize').value = sz;
    renderCanvas();
    scheduleAutoSync();
};

window.quickSetBgColor = function(color) {
    if (primarySelectedIdx < 0) return;
    pushHistory();
    const hex = normalizeHex(color);
    selectedIndices.forEach(idx => {
        tileConfig.tiles[idx].colorHex = hex;
    });
    setColorSelect('tileBgColor', hex, '#334155');
    renderCanvas();
    scheduleAutoSync();
};

window.quickSetFontColor = function(color) {
    if (primarySelectedIdx < 0) return;
    pushHistory();
    const hex = normalizeHex(color);
    selectedIndices.forEach(idx => {
        tileConfig.tiles[idx].fontColorHex = hex;
    });
    setColorSelect('tileFontColor', hex, '#FFFFFF');
    renderCanvas();
    scheduleAutoSync();
};

window.quickSetIconColor = function(color) {
    if (primarySelectedIdx < 0) return;
    pushHistory();
    const hex = normalizeHex(color);
    selectedIndices.forEach(idx => {
        tileConfig.tiles[idx].iconColorHex = hex;
    });
    setColorSelect('tileIconColor', hex, '#FFFFFF');
    renderCanvas();
    scheduleAutoSync();
};

function populateSelects() {
    const sel = document.getElementById('tileAction');
    sel.innerHTML = '';
    tileActionsList.forEach(a => {
        let opt = document.createElement('option');
        opt.value = a.id;
        opt.textContent = a.title;
        sel.appendChild(opt);
    });

    populateFeatureActions('clock_big');

    const iconSel = document.getElementById('tileIconType');
    if (iconSel) {
        iconSel.innerHTML = '';
        iconLibrary.forEach(ic => {
            let opt = document.createElement('option');
            opt.value = ic.id;
            opt.textContent = `${ic.icon} ${ic.title}`;
            iconSel.appendChild(opt);
        });
    }
}

function populateFeatureActions(tileId) {
    const actions = [...featureActionCatalog.default, ...(featureActionCatalog[tileId] || [])];
    ['tileTapAction', 'tileLongPressAction'].forEach(id => {
        const actionSelect = document.getElementById(id);
        if (!actionSelect) return;
        actionSelect.replaceChildren();
        actions.forEach(action => {
            const option = document.createElement('option');
            option.value = action.id;
            option.textContent = action.title;
            actionSelect.appendChild(option);
        });
    });
}

function setupTabs() {
    document.querySelectorAll('[data-tab-target]').forEach(button => {
        button.addEventListener('click', () => activateTab(button.dataset.tabTarget));
    });
}

const tabMetadata = {
    overview: ['نظرة عامة', 'كل ما تحتاجه لإدارة ساعتك في مكان واحد'],
    watchfaces: ['لوحة التطبيق', 'الشاشة التي تظهر عند فتح التطبيق على الساعة'],
    tiles: ['البلاطات', 'رتّب البلاطات وعاين النتيجة قبل إرسالها'],
    presets: ['القوالب', 'تكوينات جاهزة ونُسخ محفوظة من تصاميمك'],
    quran: ['القرآن الكريم', 'البحث والورد وموضع القراءة'],
    locations: ['المواقيت والموقع', 'الصلاة القادمة والمواقع المحفوظة'],
    settings: ['الإعدادات', 'مظهر القارئ والتنبيهات وطريقة الحساب'],
    sync: ['المزامنة', 'الاتصال السحابي مع الساعة']
};

function activateTab(tabName) {
    if (!document.getElementById(`tab-${tabName}`)) return;
    document.querySelectorAll('[data-tab-target]').forEach(button => {
        const active = button.dataset.tabTarget === tabName;
        button.classList.toggle('active', active);
        button.setAttribute('aria-selected', String(active));
    });
    document.querySelectorAll('.tab-page').forEach(page => page.classList.toggle('active', page.dataset.tab === tabName));
    const [title, subtitle] = tabMetadata[tabName] || tabMetadata.overview;
    const titleNode = document.getElementById('currentPageTitle');
    const subtitleNode = document.getElementById('currentPageSubtitle');
    if (titleNode) titleNode.textContent = title;
    if (subtitleNode) subtitleNode.textContent = subtitle;
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// Today's Hijri date and weekday, so the Layer 1 preview stops showing a
// frozen "الجمعة · 18 ربيع الأول". Latin digits to match the watch.
function hijriToday(withYear = false) {
    try {
        return new Intl.DateTimeFormat('ar-SA-u-ca-islamic-umalqura-nu-latn', {
            day: 'numeric', month: 'long', ...(withYear ? { year: 'numeric' } : {})
        }).format(new Date());
    } catch (_) {
        return withYear ? '19 ربيع الأول 1448 هـ' : '19 ربيع الأول';
    }
}
function weekdayToday() {
    try {
        return new Intl.DateTimeFormat('ar', { weekday: 'long' }).format(new Date());
    } catch (_) {
        return 'اليوم';
    }
}

// Resolves a tile's custom label. Blank template keeps the built-in text.
// Mirrored by applyLabelTemplate() in HomeScreen.kt.
function applyLabelTemplate(template, def, tokens = {}) {
    if (!template || !template.trim()) return def;
    const out = template
        .replaceAll('{default}', def)
        .replaceAll('{name}', tokens.name ?? '')
        .replaceAll('{countdown}', tokens.countdown ?? '')
        .replaceAll('{time}', tokens.time ?? '')
        .replaceAll('{value}', tokens.value ?? '')
        .replace(/\s{2,}/g, ' ')
        .trim();
    return out || def;
}

function getPreviewLabel(slot) {
    const now = new Date();
    // The watch shows Latin digits everywhere; the "-u-nu-latn" locale keeps the
    // Arabic month names but stops "٠٦:٥٢" appearing under a face that reads "06:52".
    // Keep these strings identical to HomeScreen.kt's displayTitle map so the
    // studio preview and the watch show the same label for every tile.
    const labels = {
        clock_big: now.toLocaleTimeString('ar-EG-u-nu-latn', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }),
        date_big: now.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' }),
        prayer_countdown: 'المغرب 1 س 24 د',
        prayer_elapsed: 'المغرب 18 د',
        prayer: 'المغرب 18:36',
        prayer_strip_5: 'مواقيت اليوم',
        quran_resume: 'سورة الكهف',
        quran: 'المصحف',
        tasbih: 'سبحان الله 33',
        battery: '78%',
        weather: '24°C',
        qibla: '72°',
        bookmarks: 'العلامات · 12',
        locations: 'بوينس آيرس',
        settings: 'الإعدادات',
        folder_islamic: 'إسلاميات',
        folder_tools: 'الأدوات',
        folder_custom: 'مجلد',
        auto_layout: 'ترتيب جديد'
    };
    const definition = tileActionsList.find(action => action.id === slot.id);
    const base = labels[slot.id] ?? (definition ? definition.title.replace(/^\S+\s+/, '') : slot.id);
    // Same token set the watch offers, with representative values in the studio.
    const tokens = {
        clock_big:        { time: labels.clock_big, value: labels.clock_big },
        date_big:         { name: labels.date_big, value: labels.date_big },
        prayer:           { name: 'المغرب', time: '18:36', value: '18:36' },
        prayer_countdown: { name: 'المغرب', countdown: '1 س 24 د', value: '1 س 24 د' },
        prayer_elapsed:   { name: 'المغرب', countdown: '18 د', value: '18 د' },
        tasbih:           { name: 'سبحان الله', value: '33' },
        qibla:            { value: '72°' },
        battery:          { value: '78%' },
        weather:          { value: '24°C' },
        bookmarks:        { value: '12' },
        locations:        { name: 'بوينس آيرس' },
        quran_resume:     { name: 'سورة الكهف', value: '18' }
    }[slot.id] || { name: base, value: base };
    return applyLabelTemplate(slot.customLabel, base, tokens);
}

function renderTileLayers() {
    const container = document.getElementById('tileLayersList');
    if (!container) return;
    const count = document.getElementById('layersCount');
    if (count) count.textContent = `${tileConfig.tiles.length} عنصر`;
    container.replaceChildren();
    tileConfig.tiles.forEach((slot, index) => {
        const row = document.createElement('button');
        row.type = 'button';
        row.className = `layer-row${selectedIndices.has(index) ? ' selected' : ''}`;
        const handle = document.createElement('span');
        handle.textContent = '⠿';
        const icon = document.createElement('span');
        icon.className = 'layer-icon';
        icon.textContent = getIcon(slot.id, slot.iconType);
        const label = document.createElement('div');
        const title = document.createElement('strong');
        title.textContent = getPreviewLabel(slot);
        const meta = document.createElement('small');
        meta.textContent = `الصف ${Number(slot.rowIndex || 0) + 1} · ${slot.colSpan || 4}/12`;
        label.append(title, meta);
        row.append(handle, icon, label);
        row.addEventListener('click', () => {
            selectedIndices = new Set([index]);
            primarySelectedIdx = index;
            renderCanvas();
            updateEditor();
        });
        container.appendChild(row);
    });
}

// The overview used to print invented numbers (78% battery, "12 آية", a fake
// prayer countdown) that never changed. The browser cannot know the watch's
// battery or compute prayer times, so it now reports only what it genuinely
// knows — and says "—" rather than inventing a value.
const TILE_SHAPE_LABELS = {
    'square-connected': 'مربّعات ملتحمة',
    'square-gapped': 'مربّعات متباعدة',
    oval: 'بيضاوية',
    circle: 'دائرية',
    mixed: 'مختلطة'
};

// Set by syncAll()/pullFromCloud() so the overview can report a real timestamp.
let lastSyncedAt = null;
let lastSyncStorage = null;

function renderOverviewMetrics() {
    const set = (id, value) => { const el = document.getElementById(id); if (el) el.textContent = value; };
    const tiles = tileConfig.tiles?.length || 0;
    const shape = tileConfig.appearance?.tileShape || 'square-connected';
    const tileWord = tiles === 1 ? 'بلاطة' : tiles === 2 ? 'بلاطتان' : tiles <= 10 ? 'بلاطات' : 'بلاطة';
    set('overviewDesign', `${tiles} ${tileWord}`);
    set('overviewDesignMeta', TILE_SHAPE_LABELS[shape] || shape);

    const locationName = (watchSettings.selectedLocationName || '').split(' (')[0] || '—';
    set('overviewLocation', locationName);
    set('overviewLocationMeta', `طريقة الحساب: ${watchSettings.calculationMethod || '—'}`);

    let bookmarks = [];
    try { bookmarks = JSON.parse(localStorage.getItem('quran_bookmarks') || '[]'); } catch (_) {}
    set('overviewBookmarks', String(bookmarks.length));

    const stamp = lastSyncedAt
        ? new Date(lastSyncedAt).toLocaleTimeString('ar-EG-u-nu-latn', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
        : '—';
    set('overviewSync', stamp);
    set('overviewSyncMeta', lastSyncStorage === 'vercel-kv' ? 'محفوظ في السحابة' : lastSyncedAt ? 'مؤقّت — بلا تخزين دائم' : 'لم تتم بعد');

    const presetName = document.getElementById('overviewPresetName');
    if (presetName) presetName.textContent = tiles ? 'تصميمي الحالي' : 'لا يوجد تصميم';
}

function renderOverviewPreview() {
    // Both layers, drawn from the live config the moment the app opens: the
    // face on the left, the tile grid on the right. Each card opens its own
    // editor. The old card invented a "12:45" that belonged to no design.
    const face = document.getElementById('overviewWatchPreview');
    if (face) {
        face.classList.add('overview-face-dial');
        face.innerHTML = typeof buildWatchFaceDialHtml === 'function' ? buildWatchFaceDialHtml() : '';
    }
    const faceName = document.getElementById('overviewFaceName');
    if (faceName) {
        const model = (typeof WATCH_FACE_MODELS !== 'undefined' ? WATCH_FACE_MODELS : [])
            .find(m => m.id === watchFaceConfig.selectedModel);
        faceName.textContent = model?.name || '—';
    }

    const tilesBox = document.getElementById('overviewTilesPreview');
    if (tilesBox) {
        const tiles = tileConfig.tiles || [];
        tilesBox.replaceChildren();
        tiles.forEach(tile => {
            const cell = document.createElement('div');
            cell.className = 'preset-mini-tile';
            cell.style.gridColumn = `span ${tile.colSpan || 12}`;
            cell.style.background = tile.colorHex || '#1E293B';
            cell.style.color = tile.fontColorHex || '#ffffff';
            cell.textContent = getPreviewLabel(tile);
            tilesBox.appendChild(cell);
        });
        if (!tiles.length) {
            const empty = document.createElement('div');
            empty.className = 'preset-mini-tile';
            empty.style.gridColumn = 'span 12';
            empty.textContent = 'لا يوجد تصميم بعد';
            tilesBox.appendChild(empty);
        }
    }
    const tilesCount = document.getElementById('overviewTilesCount');
    if (tilesCount) tilesCount.textContent = `${tileConfig.tiles?.length || 0}`;

    renderOverviewMetrics();
}

// ── CANVAS ENGINE ──
function appendResizeHandles(tileElement, tileIndex) {
    [...RESIZE_EDGES, 'nw', 'ne', 'sw', 'se'].forEach(edge => {
        const handle = document.createElement('button');
        handle.type = 'button';
        handle.className = `tile-resize-handle tile-resize-${edge}`;
        handle.dataset.handle = 'resize-tile';
        handle.dataset.edge = edge;
        handle.dataset.corner = edge;
        handle.dataset.index = tileIndex;
        handle.setAttribute('aria-label', `تغيير الحجم من جهة ${edge}`);
        tileElement.appendChild(handle);
    });
}

// `fast` skips the layers list and overview mini-preview so a live drag
// (text move, font/icon scaling, tile resize) only repaints the watch itself.
function renderCanvas(fast) {
    const canvas = document.getElementById('watchScreenSimulator');
    if (!canvas) return;
    const appearance = getAppearance();
    canvas.className = `watch-screen watch-pattern-${appearance.pattern} icon-palette-${appearance.iconPalette}`;
    canvas.innerHTML = '<div class="safe-area-ring" aria-hidden="true"></div>';
    // fontSize / iconSize are authored as pixels on a 438px watch. Scale them to
    // the current preview width so the studio matches the device 1:1 (the watch
    // applies the mirror of this in HomeScreen.kt: screenWidth / 438).
    const renderScale = Math.max(0.5, Math.min(1.6, (canvas.getBoundingClientRect().width || 438) / 438));
    const insets = getCanvasInsets();
    Object.entries(insets).forEach(([edge, size]) => {
        if (!size) return;
        const guide = document.createElement('div');
        guide.className = `edge-margin-guide edge-${edge}`;
        guide.style.setProperty(`--edge-size`, `${size}%`);
        canvas.appendChild(guide);
    });
    
    // Render Ghost Placeholder if dragging
    if (ghostTargetSlot && dragType === 'grid-tile') {
        const ghost = document.createElement('div');
        ghost.className = 'canvas-ghost-placeholder';
        ghost.style.left = ghostTargetSlot.x + '%';
        ghost.style.top = ghostTargetSlot.y + '%';
        ghost.style.width = ghostTargetSlot.width + '%';
        ghost.style.height = ghostTargetSlot.height + '%';
        canvas.appendChild(ghost);
    }

    tileConfig.tiles.forEach((slot, idx) => {
        const isSelected = selectedIndices.has(idx);
        const isDragging = (dragType === 'grid-tile' && primarySelectedIdx === idx);
        const t = document.createElement('div');
        // Mirror the watch: the studio can pick oval / mixed / circle, so the
        // preview has to show it, not always draw a flat connected square.
        const shape = (tileConfig.appearance?.tileShape) || 'square-connected';
        const shapeClass = shape === 'mixed'
            ? (idx % 3 === 1 ? 'tile-shape-oval' : 'tile-shape-square-connected')
            : `tile-shape-${shape}`;
        const surface = tileSurface(slot.colorHex, shapeClass === 'tile-shape-square-connected');
        t.className = `canvas-tile ${shapeClass}` + (isSelected ? ' selected' : '') + (isDragging ? ' is-dragging' : '');
        t.style.left = slot.x + '%';
        t.style.top = slot.y + '%';
        t.style.width = slot.width + '%';
        t.style.height = slot.height + '%';
        t.style.backgroundColor = surface.bg;
        t.style.borderColor = surface.border;
        t.dataset.index = idx;

        const isFolder = slot.id.startsWith('folder') || Array.isArray(slot.folderItems);
        if (isFolder) {
            t.classList.add('folder-launcher');
            const orb = document.createElement('div');
            orb.className = 'folder-preview-orb';
            // The watch draws a folder as one icon + its name, then opens the
            // full circular launcher on tap. Four shrunken icons with clipped
            // labels never fit a 1.3" tile, so the preview mirrors the device.
            const items = slot.folderItems?.length ? slot.folderItems : ['quran', 'tasbih', 'qibla', 'prayer'];
            const name = getTileDisplayName(slot.id).replace(/^\S+\s+/, '') || 'مجلد';
            orb.innerHTML = `<i>📁</i><b>${name}</b><small>${items.length} عناصر</small>`;
            t.appendChild(orb);
            if (isSelected && idx === primarySelectedIdx) appendResizeHandles(t, idx);
            canvas.appendChild(t);
            return;
        }
        
        // ── 5-Prayer Full Strip Specialized Mode ──
        if (slot.displayStyle === 'prayer_strip_5' || slot.id === 'prayer_strip_5') {
            t.innerHTML = `<div class="prayer-strip-container"><div class="prayer-strip-names"><span>فجر</span><span>ظهر</span><span>عصر</span><span>مغرب</span><span>عشاء</span></div><div class="prayer-strip-times"><span>05:30</span><span>13:00</span><span>16:30</span><span>19:15</span><span>20:45</span></div></div>`;
            if (isSelected && idx === primarySelectedIdx) appendResizeHandles(t, idx);
            canvas.appendChild(t);
            return;
        }

        // Icon
        if((slot.displayStyle === 'both' || slot.displayStyle === 'icon') && slot.displayStyle !== 'color_only' && slot.id !== 'color_only') {
            const i = document.createElement('div');
            i.className = 'canvas-icon' + (slot.iconStyle === 'animated' ? ' animated-icon-pulse' : '');
            const ix = slot.iconX !== undefined ? slot.iconX : 50;
            const iy = slot.iconY !== undefined ? slot.iconY : 30;
            i.style.left = ix + '%';
            i.style.top = iy + '%';
            i.style.transform = `translate(${-ix}%, ${-iy}%)`;
            i.style.fontSize = ((slot.iconSize || 24) * renderScale) + 'px';
            i.textContent = getIcon(slot.id, slot.iconType);
            i.style.color = slot.iconColorHex || '#ffffff';
            i.dataset.index = idx;
            i.dataset.role = 'icon';

            // Scale knob for icon
            if (isSelected && idx === primarySelectedIdx) {
                const knob = document.createElement('div');
                knob.className = 'icon-scale-knob';
                knob.dataset.handle = 'scale-icon';
                knob.dataset.index = idx;
                knob.setAttribute('aria-label', 'تغيير حجم الأيقونة');
                i.appendChild(knob);
            }
            t.appendChild(i);
        }
        
        // Text
        if((slot.displayStyle === 'both' || slot.displayStyle === 'text' || !slot.displayStyle) && slot.displayStyle !== 'color_only' && slot.id !== 'color_only') {
            if (slot.id === 'quran_resume') {
                const resume = document.createElement('div');
                resume.className = 'quran-resume-content quran-resume-line';
                resume.style.color = slot.fontColorHex || '#ffffff';
                resume.style.fontFamily = "'Amiri', serif";
                resume.style.fontSize = (Math.max(9, slot.fontSize || 13) * renderScale) + 'px';
                const meta = document.createElement('strong');
                meta.className = 'quran-resume-meta';
                meta.textContent = 'سورة الكهف · 18 ';
                const textSnippet = document.createTextNode('وَتَحْسَبُهُمْ أَيْقَاظًا وَهُمْ رُقُودٌ وَنُقَلِّبُهُمْ ذَاتَ الْيَمِينِ وَذَاتَ الشِّمَالِ…');
                resume.append(meta, textSnippet);
                t.appendChild(resume);
            } else {
                const txt = document.createElement('div');
                const textAlign = slot.textAlign || 'center';
                txt.className = 'canvas-text align-' + textAlign;
                const tx = slot.textX !== undefined ? slot.textX : 50;
                const ty = slot.textY !== undefined ? slot.textY : 50;
                txt.style.left = tx + '%';
                txt.style.top = ty + '%';
                txt.style.transform = `translate(${-tx}%, ${-ty}%)`;
                txt.style.fontSize = (Math.max(9, (slot.fontSize || 14)) * renderScale) + 'px';
                txt.style.color = slot.fontColorHex || '#ffffff';
                txt.style.textAlign = textAlign;
                
                if (slot.fontFamily === 'Uthmanic' || slot.fontFamily === 'Amiri') txt.style.fontFamily = "'Amiri', serif";
                else if (slot.fontFamily === 'Cairo') txt.style.fontFamily = "'Cairo', sans-serif";
                else if (slot.fontFamily === 'Noto Naskh Arabic') txt.style.fontFamily = "'Noto Naskh Arabic', serif";
                else if (slot.fontFamily === 'Noto Kufi Arabic') txt.style.fontFamily = "'Noto Kufi Arabic', sans-serif";
                else if (slot.fontFamily === 'Aref Ruqaa') txt.style.fontFamily = "'Aref Ruqaa', cursive";
                else txt.style.fontFamily = "'Tajawal', sans-serif";

                if (slot.id === 'clock_big') {
                    // Mirror the watch: HH:MM plus a small gold AM/PM chip, LTR.
                    const now = new Date();
                    txt.style.direction = 'ltr';
                    txt.textContent = now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true }).replace(/\s?[AP]M$/i, '');
                    const ampm = document.createElement('span');
                    ampm.textContent = (now.getHours() < 12 ? ' AM' : ' PM');
                    ampm.style.cssText = 'font-size:0.45em;color:#ffe082;font-weight:700;';
                    txt.appendChild(ampm);
                } else {
                    txt.textContent = getPreviewLabel(slot);
                }
                txt.dataset.index = idx;
                txt.dataset.role = 'text';

                // Scale knob for text
                if (isSelected && idx === primarySelectedIdx) {
                    const knob = document.createElement('div');
                    knob.className = 'text-scale-knob';
                    knob.dataset.handle = 'scale-text';
                    knob.dataset.index = idx;
                    knob.setAttribute('aria-label', 'تغيير حجم الخط');
                    txt.appendChild(knob);
                }
                t.appendChild(txt);
            }
        }

        if (isSelected && idx === primarySelectedIdx) appendResizeHandles(t, idx);
        
        canvas.appendChild(t);
    });
    if (!fast) {
        renderTileLayers();
        renderOverviewPreview();
    }
}

function getIcon(id, iconType) {
    if (iconType && iconType !== 'default') {
        const found = iconLibrary.find(ic => ic.id === iconType);
        if (found) return found.icon;
        // Not a library id — the user typed their own emoji, so show it.
        return iconType;
    }
    if(id.startsWith('folder')) return '📁';
    if(id === 'clock_big') return '⏰';
    if(id === 'date_big') return '📅';
    if(id === 'prayer_countdown') return '⏳';
    if(id === 'prayer_elapsed') return '⌛';
    if(id === 'prayer') return '🕌';
    if(id === 'prayer_strip_5') return '▤';
    if(id === 'tasbih') return '📿';
    if(id === 'weather') return '⛅';
    if(id === 'qibla') return '🕋';
    if(id === 'quran' || id === 'quran_resume') return '📖';
    if(id === 'bookmarks') return '🔖';
    if(id === 'locations') return '📍';
    if(id === 'settings') return '⚙️';
    if(id === 'battery') return '🔋';
    if(id === 'auto_layout') return '✦';
    return '⭐';
}

function updateEditor() {
    const rightPanel = document.getElementById('selectedTileEditorRight');
    const leftPanel = document.getElementById('selectedTileEditorLeft');
    const noMsg = document.getElementById('noTileSelectedMsg');
    const toolbar = document.getElementById('inspectorToolbar');

    if(primarySelectedIdx >= 0 && primarySelectedIdx < tileConfig.tiles.length) {
        if (rightPanel) rightPanel.hidden = false;
        if (leftPanel) leftPanel.hidden = false;
        if (toolbar) toolbar.hidden = false;
        if (noMsg) noMsg.hidden = true;

        let slot = tileConfig.tiles[primarySelectedIdx];
        populateFeatureActions(slot.id);
        const selectedName = document.getElementById('selectedTileName');
        if (selectedName) selectedName.textContent = getPreviewLabel(slot);
        document.getElementById('tileAction').value = slot.id;
        setColorSelect('tileBgColor', slot.colorHex, '#334155');
        setColorSelect('tileFontColor', slot.fontColorHex, '#FFFFFF');
        document.getElementById('tileFontSize').value = slot.fontSize || 14;
        const fontRange = document.getElementById('tileFontSizeRange');
        if (fontRange) fontRange.value = slot.fontSize || 14;
        if(document.getElementById('tileFontFamily')) document.getElementById('tileFontFamily').value = slot.fontFamily || 'Uthmanic';
        document.getElementById('tileDisplayStyle').value = slot.displayStyle || 'text';
        document.getElementById('tileIconStyle').value = slot.iconStyle || 'static';
        const iconSelect = document.getElementById('tileIconType');
        const iconCustom = document.getElementById('tileIconCustom');
        const currentIcon = slot.iconType || 'default';
        const isLibraryIcon = currentIcon === 'default' || iconLibrary.some(ic => ic.id === currentIcon);
        iconSelect.value = isLibraryIcon ? currentIcon : 'default';
        if (iconCustom) iconCustom.value = isLibraryIcon ? '' : currentIcon;
        const labelField = document.getElementById('tileCustomLabel');
        if (labelField) labelField.value = slot.customLabel || '';
        setColorSelect('tileIconColor', slot.iconColorHex, '#FFFFFF');
        if(document.getElementById('tileTapAction')) document.getElementById('tileTapAction').value = [...document.getElementById('tileTapAction').options].some(option => option.value === (slot.tapAction || '')) ? slot.tapAction || '' : '';
        if(document.getElementById('tileLongPressAction')) document.getElementById('tileLongPressAction').value = [...document.getElementById('tileLongPressAction').options].some(option => option.value === (slot.longPressAction || 'quick_edit')) ? slot.longPressAction || 'quick_edit' : 'quick_edit';
        
        const textAlign = slot.textAlign || 'center';
        document.querySelectorAll('[data-text-align]').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.textAlign === textAlign);
        });

        renderFolderItemsEditor(slot);
    } else {
        if (rightPanel) rightPanel.hidden = true;
        if (leftPanel) leftPanel.hidden = true;
        if (toolbar) toolbar.hidden = true;
        if (noMsg) noMsg.hidden = false;
        document.getElementById('folderItemsEditor')?.setAttribute('hidden', '');
    }

    // Always keep mobile toolbar label in sync
    const mobileLabel = document.getElementById('mobileSelectedTileLabel');
    if (mobileLabel) {
        if (primarySelectedIdx >= 0 && primarySelectedIdx < tileConfig.tiles.length) {
            const s = tileConfig.tiles[primarySelectedIdx];
            mobileLabel.textContent = `العنصر المحدد: ${getPreviewLabel(s)} (الصف ${(s.rowIndex || 0) + 1} · عرض ${s.colSpan || 12}/12)`;
        } else {
            mobileLabel.textContent = 'انقر على أي بلاطة لتحديدها وتعديلها';
        }
    }
}

function renderFolderItemsEditor(slot) {
    const editor = document.getElementById('folderItemsEditor');
    const choices = document.getElementById('folderItemsChoices');
    if (!editor || !choices) return;
    const isFolder = slot.id.startsWith('folder');
    editor.toggleAttribute('hidden', !isFolder);
    if (!isFolder) return;

    const defaults = slot.id === 'folder_tools'
        ? ['bookmarks', 'locations', 'settings']
        : ['quran', 'tasbih', 'qibla', 'prayer'];
    if (!Array.isArray(slot.folderItems) || !slot.folderItems.length) slot.folderItems = [...defaults];
    const available = ['quran', 'quran_resume', 'tasbih', 'qibla', 'prayer', 'prayer_strip_5', 'bookmarks', 'locations', 'settings'];
    choices.replaceChildren();
    available.forEach(actionId => {
        const label = document.createElement('label');
        const input = document.createElement('input');
        input.type = 'checkbox';
        input.checked = slot.folderItems.includes(actionId);
        input.addEventListener('change', () => {
            const current = new Set(slot.folderItems);
            if (input.checked && current.size >= 6) {
                input.checked = false;
                updateSyncStatus('يمكن وضع ستة عناصر كحد أقصى داخل المجلد', 'error');
                return;
            }
            if (input.checked) current.add(actionId); else current.delete(actionId);
            slot.folderItems = [...current];
            renderCanvas();
            scheduleAutoSync();
        });
        const text = document.createElement('span');
        text.textContent = `${getIcon(actionId)} ${getPreviewLabel({ id: actionId })}`;
        label.append(input, text);
        choices.appendChild(label);
    });
}

window.addTile = function(side = 'right') {
    pushHistory();
    let newSlot = {
        id: 'prayer', colorHex: '#0E7490', isLive: false,
        colSpan: 4, rowIndex: 0,
        fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24,
        displayStyle: 'text', iconStyle: 'static', iconType: 'default',
        textX: 50, textY: 50, iconX: 50, iconY: 30, fontFamily: 'Uthmanic',
        tapAction: '', longPressAction: 'quick_edit'
    };

    if (side === 'new-row') {
        const maxRow = tileConfig.tiles.reduce((max, tile) => Math.max(max, tile.rowIndex || 0), -1);
        if (maxRow + 1 >= MAX_EDITOR_ROWS) {
            updateSyncStatus('وصلت إلى الحد الأقصى: خمسة صفوف', 'error');
            return;
        }
        newSlot.rowIndex = maxRow + 1;
        newSlot.colSpan = 12;
        tileConfig.tiles.push(newSlot);
    } else if (primarySelectedIdx >= 0 && tileConfig.tiles[primarySelectedIdx]) {
        let selTile = tileConfig.tiles[primarySelectedIdx];
        let r = selTile.rowIndex !== undefined ? selTile.rowIndex : 0;
        newSlot.rowIndex = r;
        
        let rowTiles = tileConfig.tiles.filter(t => t.rowIndex === r);
        let insertPos = tileConfig.tiles.indexOf(selTile);
        if (side === 'right') {
            insertPos += 1;
        }
        tileConfig.tiles.splice(insertPos, 0, newSlot);

        let count = rowTiles.length + 1;
        let perTile = Math.floor(12 / count);
        let extra = 12 % count;
        tileConfig.tiles.filter(t => t.rowIndex === r).forEach((t, i) => {
            t.colSpan = Math.max(3, perTile + (i < extra ? 1 : 0));
        });
    } else {
        let maxRow = tileConfig.tiles.reduce((max, t) => Math.max(max, t.rowIndex || 0), -1);
        newSlot.rowIndex = maxRow + 1;
        newSlot.colSpan = 12;
        tileConfig.tiles.push(newSlot);
    }

    let newIdx = tileConfig.tiles.indexOf(newSlot);
    selectedIndices.clear();
    primarySelectedIdx = newIdx >= 0 ? newIdx : tileConfig.tiles.length - 1;
    selectedIndices.add(primarySelectedIdx);
    validateAndPackGrid();
    renderCanvas();
    updateEditor();
    scheduleAutoSync();
};
function addTile(side) { window.addTile(side); }

function addPrayerStripRow() {
    const maxRow = tileConfig.tiles.reduce((max, tile) => Math.max(max, tile.rowIndex || 0), -1);
    if (maxRow + 1 >= MAX_EDITOR_ROWS) {
        updateSyncStatus('لا توجد مساحة لصف جديد؛ احذف صفًا أو ادمج العناصر', 'error');
        return;
    }
    pushHistory();
    tileConfig.tiles.push({
        id: 'prayer_strip_5', colorHex: '#047857', isLive: true,
        colSpan: 12, rowIndex: maxRow + 1, fontSize: 12,
        fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 18,
        textX: 50, textY: 50, iconX: 50, iconY: 24,
        displayStyle: 'prayer_strip_5', iconStyle: 'static', iconType: 'mosque',
        fontFamily: 'Uthmanic', tapAction: 'prayer_schedule', longPressAction: 'quick_edit'
    });
    primarySelectedIdx = tileConfig.tiles.length - 1;
    selectedIndices = new Set([primarySelectedIdx]);
    validateAndPackGrid();
    renderCanvas();
    updateEditor();
    scheduleAutoSync();
}

function setSelectedRowCompact(compact) {
    if (primarySelectedIdx < 0 || !tileConfig.tiles[primarySelectedIdx]) return;
    pushHistory();
    const row = tileConfig.tiles[primarySelectedIdx].rowIndex || 0;
    tileConfig.rowWeights = { ...(tileConfig.rowWeights || {}), [row]: compact ? .28 : 1 };
    validateAndPackGrid();
    renderCanvas();
    scheduleAutoSync();
}

function toggleCanvasInset(edge) {
    pushHistory();
    const current = getCanvasInsets();
    current[edge] = current[edge] > 0 ? 0 : 8;
    tileConfig.canvasInsets = current;
    validateAndPackGrid();
    renderCanvas();
    scheduleAutoSync();
}

// ── 12-UNIT REFLOW DRAGGING & SIZING ENGINE ──
function setupCanvasEvents() {
    const canvas = document.getElementById('watchScreenSimulator');
    if (!canvas) return;

    function handleStart(clientX, clientY, target, isShift, isCtrl) {
        // 1. Check for Resize Handle or Scale Knob
        const handleEl = target.closest('[data-handle]');
        if (handleEl) {
            const hType = handleEl.dataset.handle;
            const hIdx = parseInt(handleEl.dataset.index);
            if (!isNaN(hIdx) && hIdx >= 0 && hIdx < tileConfig.tiles.length) {
                primarySelectedIdx = hIdx;
                selectedIndices = new Set([hIdx]);
                if (hType === 'resize-tile') {
                    dragType = 'resize-tile';
                    dragResizeCorner = handleEl.dataset.edge || handleEl.dataset.corner || 'se';
                } else if (hType === 'scale-text') {
                    dragType = 'scale-text';
                } else if (hType === 'scale-icon') {
                    dragType = 'scale-icon';
                }
                updateEditor();
                renderCanvas();
                dragStartPointerX = clientX;
                dragStartPointerY = clientY;
                startTilesSnapshot = JSON.parse(JSON.stringify(tileConfig.tiles));
                startRowWeightsSnapshot = JSON.parse(JSON.stringify(tileConfig.rowWeights || {}));
                ghostTargetSlot = null;
                interactionHistoryPushed = false;
                return;
            }
        }

        // 2. Grab the text or icon of the already-selected tile to move it inside
        //    the tile. First click selects the tile; a second press on its label
        //    or glyph starts a nested drag instead of moving the whole tile.
        const roleEl = target.closest('[data-role="text"], [data-role="icon"]');
        if (roleEl && !isShift && !isCtrl) {
            const rIdx = parseInt(roleEl.dataset.index);
            if (!isNaN(rIdx) && rIdx === primarySelectedIdx && selectedIndices.has(rIdx) && selectedIndices.size === 1) {
                dragType = roleEl.dataset.role; // 'text' or 'icon'
                dragStartPointerX = clientX;
                dragStartPointerY = clientY;
                startTilesSnapshot = JSON.parse(JSON.stringify(tileConfig.tiles));
                startRowWeightsSnapshot = JSON.parse(JSON.stringify(tileConfig.rowWeights || {}));
                ghostTargetSlot = null;
                interactionHistoryPushed = false;
                return;
            }
        }

        // 3. Check for Tile Click (Any child element inside canvas-tile)
        const tileEl = target.closest('.canvas-tile') || target.closest('[data-index]');
        if (tileEl && tileEl.dataset.index !== undefined) {
            const clickedIdx = parseInt(tileEl.dataset.index);
            if (!isNaN(clickedIdx) && clickedIdx >= 0 && clickedIdx < tileConfig.tiles.length) {
                dragType = 'grid-tile';
                if (isShift || isCtrl) {
                    if (selectedIndices.has(clickedIdx)) {
                        selectedIndices.delete(clickedIdx);
                        primarySelectedIdx = selectedIndices.size > 0 ? Array.from(selectedIndices)[0] : -1;
                    } else {
                        selectedIndices.add(clickedIdx);
                        primarySelectedIdx = clickedIdx;
                    }
                } else {
                    selectedIndices = new Set([clickedIdx]);
                    primarySelectedIdx = clickedIdx;
                }
                updateEditor();
                renderCanvas();
                dragStartPointerX = clientX;
                dragStartPointerY = clientY;
                startTilesSnapshot = JSON.parse(JSON.stringify(tileConfig.tiles));
                startRowWeightsSnapshot = JSON.parse(JSON.stringify(tileConfig.rowWeights || {}));
                ghostTargetSlot = null;
                interactionHistoryPushed = false;
                return;
            }
        }

        // 4. Clicked empty background inside canvas
        if (target === canvas || target.classList.contains('safe-area-ring') || target.classList.contains('edge-margin-guide')) {
            selectedIndices.clear();
            primarySelectedIdx = -1;
            dragType = null;
            updateEditor();
            renderCanvas();
        }
    }

    // Unified Touch & Pointer Engine with setPointerCapture
    canvas.addEventListener('pointerdown', (e) => {
        e.preventDefault();
        try { canvas.setPointerCapture(e.pointerId); } catch (_) {}
        handleStart(e.clientX, e.clientY, e.target, e.shiftKey, e.ctrlKey || e.metaKey);
    });

    canvas.addEventListener('pointermove', (e) => {
        if (dragType) {
            e.preventDefault();
            onPointerMove(e);
        }
    });

    canvas.addEventListener('pointerup', (e) => {
        if (dragType) {
            onPointerUp();
            try { canvas.releasePointerCapture(e.pointerId); } catch (_) {}
        }
    });

    canvas.addEventListener('pointercancel', (e) => {
        if (dragType) {
            onPointerUp();
            try { canvas.releasePointerCapture(e.pointerId); } catch (_) {}
        }
    });
}

function onTouchMove(e) {
    if (e.touches.length > 0) {
        onPointerMove(e.touches[0]);
        e.preventDefault();
    }
}

function onTouchEnd() {
    onPointerUp();
    document.removeEventListener('touchmove', onTouchMove);
    document.removeEventListener('touchend', onTouchEnd);
}

/**
 * Widens a grid tile by trading units with the neighbour on the dragged side, so
 * the opposite edge stays put and the tile follows the finger. Both spans are
 * read from the drag-start snapshot, so a drag can never compound on itself.
 */
function resizeGridTileWidth(original, side, dx) {
    const insets = getCanvasInsets();
    const usableWidth = Math.max(1, 100 - insets.left - insets.right);
    const row = original.rowIndex || 0;

    const rowIndices = tileConfig.tiles
        .map((tile, index) => ({ tile, index }))
        .filter(entry => (entry.tile.rowIndex || 0) === row)
        .map(entry => entry.index);

    const position = rowIndices.indexOf(primarySelectedIdx);
    if (position < 0) return;

    // Dragging west takes units from the tile on the left, east from the right.
    const neighbourIdx = side === 'w' ? rowIndices[position - 1] : rowIndices[position + 1];
    // The outermost tile of a row has nothing to trade with, so it stays put.
    if (neighbourIdx === undefined) return;

    const minimum = rowIndices.length <= 6 ? 2 : 1;
    const deltaUnits = Math.round((dx * (side === 'w' ? -1 : 1)) / usableWidth * 12);

    const snapshotTarget = startTilesSnapshot[primarySelectedIdx] || original;
    const snapshotNeighbour = startTilesSnapshot[neighbourIdx] || tileConfig.tiles[neighbourIdx];
    const targetBase = Math.max(minimum, Number(snapshotTarget.colSpan) || 4);
    const neighbourBase = Math.max(minimum, Number(snapshotNeighbour.colSpan) || 4);
    const pool = targetBase + neighbourBase;

    const nextTarget = Math.max(minimum, Math.min(pool - minimum, targetBase + deltaUnits));
    tileConfig.tiles[primarySelectedIdx].colSpan = nextTarget;
    tileConfig.tiles[neighbourIdx].colSpan = pool - nextTarget;
    tileConfig.tiles[primarySelectedIdx].manualLayout = false;
    tileConfig.tiles[neighbourIdx].manualLayout = false;
}

/** Resizes the row a tile sits in, always measured from the drag-start weight. */
function resizeGridRowHeight(original, side, dy) {
    const row = original.rowIndex || 0;
    const baseWeight = Math.max(.22, Number(startRowWeightsSnapshot?.[row]) || 1);
    const direction = side === 'n' ? -1 : 1;
    tileConfig.rowWeights = {
        ...(tileConfig.rowWeights || {}),
        [row]: Math.max(.22, Math.min(4, baseWeight + (dy * direction * .045)))
    };
}

function resizeTileWithRatio(slot, original, corner, dx, dy) {
    // Grid tiles resize as a coordinated row: there are never empty cells after a drag.
    if (!original.manualLayout) {
        const isWidthGesture = Math.abs(dx) >= Math.abs(dy);
        if (isWidthGesture) {
            resizeGridTileWidth(original, corner.includes('w') ? 'w' : 'e', dx);
        } else {
            resizeGridRowHeight(original, corner.includes('n') ? 'n' : 's', dy);
        }
        validateAndPackGrid();
        return;
    }
    const insets = getCanvasInsets();
    const ratio = Math.max(.2, (original.width || 20) / Math.max(1, original.height || 20));
    const horizontalDirection = corner.includes('e') ? 1 : -1;
    const verticalDirection = corner.includes('s') ? 1 : -1;
    const delta = Math.abs(dx) > Math.abs(dy) ? dx * horizontalDirection : dy * verticalDirection * ratio;
    const minWidth = 10;
    let width = Math.max(minWidth, original.width + delta);
    let height = width / ratio;

    const maxWidth = corner.includes('e')
        ? 100 - insets.right - original.x
        : original.x + original.width - insets.left;
    const maxHeight = corner.includes('s')
        ? 100 - insets.bottom - original.y
        : original.y + original.height - insets.top;
    width = Math.min(width, maxWidth, maxHeight * ratio);
    height = width / ratio;

    slot.width = Math.round(width * 2) / 2;
    slot.height = Math.round(height * 2) / 2;
    slot.x = corner.includes('w') ? original.x + original.width - slot.width : original.x;
    slot.y = corner.includes('n') ? original.y + original.height - slot.height : original.y;
    slot.manualLayout = true;
    clampManualSlot(slot, insets);
}

function resizeTileFromEdge(slot, original, edge, dx, dy) {
    if (!original.manualLayout) {
        if (edge === 'e' || edge === 'w') {
            resizeGridTileWidth(original, edge, dx);
        } else {
            resizeGridRowHeight(original, edge, dy);
        }
        validateAndPackGrid();
        return;
    }

    const insets = getCanvasInsets();
    if (edge === 'e') slot.width = Math.max(10, Math.min(100 - insets.right - original.x, original.width + dx));
    if (edge === 'w') {
        slot.width = Math.max(10, Math.min(original.x + original.width - insets.left, original.width - dx));
        slot.x = original.x + original.width - slot.width;
    }
    if (edge === 's') slot.height = Math.max(10, Math.min(100 - insets.bottom - original.y, original.height + dy));
    if (edge === 'n') {
        slot.height = Math.max(10, Math.min(original.y + original.height - insets.top, original.height - dy));
        slot.y = original.y + original.height - slot.height;
    }
    slot.manualLayout = true;
    clampManualSlot(slot, insets);
}

function onPointerMove(e) {
    if(primarySelectedIdx < 0 || !dragType || !startTilesSnapshot[primarySelectedIdx]) return;
    const canvasRect = document.getElementById('watchScreenSimulator').getBoundingClientRect();
    
    let orig = startTilesSnapshot[primarySelectedIdx];
    let slot = tileConfig.tiles[primarySelectedIdx];
    
    let dx = ((e.clientX - dragStartPointerX) / canvasRect.width) * 100;
    let dy = ((e.clientY - dragStartPointerY) / canvasRect.height) * 100;

    if (!interactionHistoryPushed) {
        pushHistory();
        interactionHistoryPushed = true;
    }

    // ── Interactive Scale Knobs ──
    if (dragType === 'scale-text') {
        let newSize = Math.round(Math.max(8, Math.min(48, (orig.fontSize || 14) + (dx * 0.6))));
        selectedIndices.forEach(idx => { if (tileConfig.tiles[idx]) tileConfig.tiles[idx].fontSize = newSize; });
        const num = document.getElementById('tileFontSize');
        const range = document.getElementById('tileFontSizeRange');
        if (num) num.value = newSize;
        if (range) range.value = newSize;
        renderCanvas(true);
        return;
    }
    if (dragType === 'scale-icon') {
        let newSize = Math.round(Math.max(14, Math.min(48, (orig.iconSize || 24) + (dx * 0.6))));
        selectedIndices.forEach(idx => { if (tileConfig.tiles[idx]) tileConfig.tiles[idx].iconSize = newSize; });
        renderCanvas(true);
        return;
    }

    if (dragType === 'resize-tile') {
        if (['n', 'e', 's', 'w'].includes(dragResizeCorner)) {
            resizeTileFromEdge(slot, orig, dragResizeCorner, dx, dy);
        } else {
            resizeTileWithRatio(slot, orig, dragResizeCorner, dx, dy);
        }
        renderCanvas(true);
        return;
    }

    // ── Direct Text and Icon Dragging ──
    if (dragType === 'text') {
        let tileWidthPx = ((orig.width || 100) / 100) * canvasRect.width || canvasRect.width;
        let tileHeightPx = ((orig.height || 100) / 100) * canvasRect.height || canvasRect.height;
        let dTx = ((e.clientX - dragStartPointerX) / tileWidthPx) * 100;
        let dTy = ((e.clientY - dragStartPointerY) / tileHeightPx) * 100;
        slot.textX = Math.round(Math.max(12, Math.min(88, (orig.textX || 50) + dTx)));
        slot.textY = Math.round(Math.max(12, Math.min(88, (orig.textY || 50) + dTy)));
        renderCanvas(true);
        return;
    }
    if (dragType === 'icon') {
        let tileWidthPx = ((orig.width || 100) / 100) * canvasRect.width || canvasRect.width;
        let tileHeightPx = ((orig.height || 100) / 100) * canvasRect.height || canvasRect.height;
        let dIx = ((e.clientX - dragStartPointerX) / tileWidthPx) * 100;
        let dIy = ((e.clientY - dragStartPointerY) / tileHeightPx) * 100;
        slot.iconX = Math.round(Math.max(12, Math.min(88, (orig.iconX || 50) + dIx)));
        slot.iconY = Math.round(Math.max(12, Math.min(88, (orig.iconY || 30) + dIy)));
        renderCanvas(true);
        return;
    }

    // ── Direct tile dragging reorders the grid; it never permits overlapping layers. ──
    if (dragType === 'grid-tile') {
        const rows = [...new Set(tileConfig.tiles.map(tile => tile.rowIndex || 0))].sort((a, b) => a - b);
        const targetRow = rows.reduce((closest, row) => {
            const rowY = tileConfig.tiles.find(tile => tile.rowIndex === row)?.y ?? 0;
            const closestY = tileConfig.tiles.find(tile => tile.rowIndex === closest)?.y ?? 0;
            return Math.abs((orig.y + dy) - rowY) < Math.abs((orig.y + dy) - closestY) ? row : closest;
        }, rows[0] ?? 0);
        slot.rowIndex = Math.max(0, Math.min(MAX_EDITOR_ROWS - 1, targetRow));
        slot.manualLayout = false;
        validateAndPackGrid();
        renderCanvas(true);
    }
}

function onPointerUp() {
    if (dragType) {
        validateAndPackGrid();
        if (navigator.vibrate && interactionHistoryPushed) navigator.vibrate(20);

        dragType = null;
        dragResizeCorner = null;
        ghostTargetSlot = null;
        lastHapticSlotKey = '';
        interactionHistoryPushed = false;
        document.removeEventListener('mousemove', onPointerMove);
        document.removeEventListener('mouseup', onPointerUp);
        renderCanvas();
        scheduleAutoSync();
    }
}

// ── PRESETS / LAYOUT PROFILES ENGINE ──
const builtInPresets = {
    'default': [
        { id: 'prayer_countdown', colorHex: '#10B981', isLive: true, colSpan: 12, rowIndex: 0, fontSize: 20, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'animated', iconType: 'hourglass', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'folder_islamic', colorHex: '#0284C7', colSpan: 4, rowIndex: 1, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'folder', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'quran_resume', colorHex: '#0E7490', isLive: true, colSpan: 8, rowIndex: 1, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'quran', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'folder_tools', colorHex: '#EA580C', colSpan: 4, rowIndex: 2, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'folder', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'locations', colorHex: '#F59E0B', colSpan: 4, rowIndex: 2, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'pin', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'settings', colorHex: '#334155', colSpan: 4, rowIndex: 2, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'settings', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'cloud_sync_pull' }
    ],
    'prayer': [
        { id: 'clock_big', colorHex: '#7C3AED', isLive: true, colSpan: 6, rowIndex: 0, fontSize: 26, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'default', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'prayer_countdown', colorHex: '#10B981', isLive: true, colSpan: 6, rowIndex: 0, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'animated', iconType: 'hourglass', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'prayer_strip_5', colorHex: '#0E7490', isLive: false, colSpan: 12, rowIndex: 1, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'prayer_strip_5', iconStyle: 'static', iconType: 'mosque', fontFamily: 'Uthmanic', tapAction: 'prayer', longPressAction: 'quick_edit' },
        { id: 'qibla', colorHex: '#0284C7', isLive: false, colSpan: 6, rowIndex: 2, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'kaaba', fontFamily: 'Uthmanic', tapAction: 'qibla', longPressAction: 'quick_edit' },
        { id: 'tasbih', colorHex: '#F59E0B', isLive: true, colSpan: 6, rowIndex: 2, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'tasbih', fontFamily: 'Uthmanic', tapAction: 'quick_tasbih_increment', longPressAction: 'quick_edit' }
    ],
    'quran': [
        { id: 'quran_resume', colorHex: '#0E7490', isLive: true, colSpan: 12, rowIndex: 0, fontSize: 20, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'quran', fontFamily: 'Uthmanic', tapAction: 'quran_resume', longPressAction: 'quick_edit' },
        { id: 'bookmarks', colorHex: '#7C3AED', isLive: false, colSpan: 6, rowIndex: 1, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'bookmark', fontFamily: 'Uthmanic', tapAction: 'bookmarks', longPressAction: 'quick_edit' },
        { id: 'tasbih', colorHex: '#10B981', isLive: true, colSpan: 6, rowIndex: 1, fontSize: 14, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'tasbih', fontFamily: 'Uthmanic', tapAction: 'quick_tasbih_increment', longPressAction: 'quick_edit' }
    ],
    'minimal': [
        { id: 'clock_big', colorHex: '#7C3AED', isLive: true, colSpan: 12, rowIndex: 0, fontSize: 32, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'static', iconType: 'default', fontFamily: 'Uthmanic', tapAction: '', longPressAction: 'quick_edit' },
        { id: 'prayer_countdown', colorHex: '#10B981', isLive: true, colSpan: 12, rowIndex: 1, fontSize: 20, fontColorHex: '#ffffff', iconColorHex: '#ffffff', iconSize: 24, textX: 50, textY: 50, iconX: 50, iconY: 30, displayStyle: 'text', iconStyle: 'animated', iconType: 'hourglass', fontFamily: 'Uthmanic', tapAction: 'prayer', longPressAction: 'quick_edit' }
    ]
};

window.loadBuiltInPreset = function(name) {
    if (builtInPresets[name]) {
        pushHistory();
        tileConfig.tiles = JSON.parse(JSON.stringify(builtInPresets[name]));
        selectedIndices.clear();
        primarySelectedIdx = tileConfig.tiles.length > 0 ? 0 : -1;
        if (primarySelectedIdx >= 0) selectedIndices.add(primarySelectedIdx);
        validateAndPackGrid();
        renderCanvas();
        updateEditor();
        scheduleAutoSync();
    }
};

function saveCustomPreset(name) {
    const cleanName = name?.trim();
    if (!cleanName) return false;
    const customPresets = JSON.parse(localStorage.getItem('quran_watch_presets') || '{}');
    customPresets[cleanName] = JSON.parse(JSON.stringify(tileConfig));
    localStorage.setItem('quran_watch_presets', JSON.stringify(customPresets));
    renderCustomPresets();
    updateSyncStatus(`تم حفظ القالب: ${cleanName}`, 'success');
    return true;
}

function saveCurrentPresetFromPrompt() {
    activateTab('presets');
    const suggested = `تصميم ${new Date().toLocaleDateString('ar-EG')}`;
    const name = prompt('اكتب اسم القالب الجديد:', suggested);
    if (saveCustomPreset(name)) alert(`✓ تم حفظ القالب "${name.trim()}" بنجاح`);
}

function setupPresetsManager() {
    renderCustomPresets();
    const btnSave = document.getElementById('btnSaveNewPreset');
    if (btnSave) {
        btnSave.addEventListener('click', () => {
            const input = document.getElementById('newPresetName');
            const name = input?.value.trim();
            if (!name) {
                alert("يرجى كتابة اسم للوضع الجديد أولاً.");
                return;
            }
            saveCustomPreset(name);
            if (input) input.value = '';
            alert(`✓ تم حفظ الوضع باسم "${name}" بنجاح!`);
        });
    }
}

function renderCustomPresets() {
    const container = document.getElementById('customPresetsList');
    if (!container) return;
    container.innerHTML = '';
    
    let customPresets = JSON.parse(localStorage.getItem('quran_watch_presets') || '{}');
    let keys = Object.keys(customPresets);
    if (keys.length === 0) {
        const empty = document.createElement('p');
        empty.className = 'empty-custom-presets';
        empty.textContent = 'لا توجد قوالب مخصصة بعد.';
        container.appendChild(empty);
        return;
    }

    keys.forEach(k => {
        const row = document.createElement('div');
        row.className = 'custom-preset-row';
        const title = document.createElement('strong');
        title.textContent = k;
        const apply = document.createElement('button');
        apply.className = 'button';
        apply.textContent = 'تطبيق';
        apply.addEventListener('click', () => window.applyCustomPreset(k));
        const remove = document.createElement('button');
        remove.className = 'icon-button';
        remove.setAttribute('aria-label', `حذف ${k}`);
        remove.textContent = '×';
        remove.addEventListener('click', () => window.deleteCustomPreset(k));
        row.append(title, apply, remove);
        container.appendChild(row);
    });
}

window.applyCustomPreset = function(name) {
    let customPresets = JSON.parse(localStorage.getItem('quran_watch_presets') || '{}');
    if (customPresets[name]) {
        pushHistory();
        const saved = JSON.parse(JSON.stringify(customPresets[name]));
        tileConfig = Array.isArray(saved) ? { ...tileConfig, tiles: saved } : { ...tileConfig, ...saved };
        selectedIndices.clear();
        primarySelectedIdx = tileConfig.tiles.length > 0 ? 0 : -1;
        if (primarySelectedIdx >= 0) selectedIndices.add(primarySelectedIdx);
        validateAndPackGrid();
        renderCanvas();
        updateEditor();
        scheduleAutoSync();
    }
};

window.deleteCustomPreset = function(name) {
    if (confirm(`هل أنت متأكد من حذف القالب "${name}"؟`)) {
        let customPresets = JSON.parse(localStorage.getItem('quran_watch_presets') || '{}');
        delete customPresets[name];
        localStorage.setItem('quran_watch_presets', JSON.stringify(customPresets));
        renderCustomPresets();
    }
};

// ── QURAN SEARCH ENGINE ──
function normalizeArabic(text) {
    if (!text) return '';
    return text
        .replace(/[ؐ-ًؚ-ٰٟۖ-ۜ۟-۪ۨ-ۭ]/g, '')
        .replace(/[إأآٱ]/g, 'ا')
        .replace(/ة/g, 'ه')
        .replace(/ى/g, 'ي')
        .replace(/[^ء-ي0-9a-zA-Z ]/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

async function setupQuranSearch() {
    const resDiv = document.getElementById('quranSearchResults');
    const input = document.getElementById('quranSearchInput');
    if (!input || !resDiv) return;

    try {
        const res = await fetch('quran_uthmani.min.json');
        quranData = await res.json();
        showSearchMessage(resDiv, 'المصحف جاهز للبحث. اكتب كلمة للبدء…');
    } catch(e) {
        console.error("Failed to load Quran JSON:", e);
        showSearchMessage(resDiv, 'تعذر تحميل بيانات المصحف محليًا.');
    }

    input.addEventListener('input', (e) => {
        let q = e.target.value.trim();
        if (q.length < 2) {
            showSearchMessage(resDiv, 'اكتب للبحث في المصحف الشريف…');
            return;
        }

        if (!quranData) {
            showSearchMessage(resDiv, 'جاري تجهيز المصحف…');
            return;
        }

        let searchQ = normalizeArabic(q);
        let matches = [];
        const verses = Array.isArray(quranData.quran)
            ? quranData.quran
            : (quranData.chapters || []).flatMap(chapter =>
                (chapter.verses || []).map(verse => ({ ...verse, chapter: chapter.chapter }))
            );

        for (const verse of verses) {
            const surahName = surahNamesAr[verse.chapter - 1] || `سورة ${verse.chapter}`;
            const normSurah = normalizeArabic(surahName);
            const normVerse = normalizeArabic(verse.text);
            if (normVerse.includes(searchQ) || (normSurah.includes(searchQ) && verse.verse === 1)) {
                matches.push({
                    chapter: verse.chapter,
                    surahName,
                    ayah: verse.verse,
                    text: verse.text
                });
                if (matches.length >= 60) break;
            }
        }

        resDiv.innerHTML = '';
        if (matches.length === 0) {
            showSearchMessage(resDiv, 'لا توجد آيات مطابقة لكلمة البحث.');
            return;
        }

        matches.forEach(m => {
            const el = document.createElement('article');
            el.className = 'search-result-item';
            
            const headRow = document.createElement('div');
            headRow.className = 'result-header-row';
            
            const header = document.createElement('strong');
            header.className = 'result-header';
            header.textContent = `سورة ${m.surahName} · آية ${m.ayah}`;
            
            const btnBookmark = document.createElement('button');
            btnBookmark.type = 'button';
            btnBookmark.className = 'btn-bookmark-action';
            btnBookmark.innerHTML = '<span>🔖</span> إضافة علامة';
            btnBookmark.addEventListener('click', (ev) => {
                ev.stopPropagation();
                addBookmark(m.chapter, m.surahName, m.ayah, m.text);
            });
            
            headRow.append(header, btnBookmark);
            
            const verse = document.createElement('p');
            verse.className = 'result-text';
            verse.textContent = m.text;
            
            el.append(headRow, verse);
            resDiv.appendChild(el);
        });
    });
}

// ── BOOKMARKS MANAGER ──
function getBookmarks() {
    try {
        return JSON.parse(localStorage.getItem('quran_bookmarks') || '[]');
    } catch (e) {
        return [];
    }
}

function saveBookmarks(list) {
    localStorage.setItem('quran_bookmarks', JSON.stringify(list));
    renderBookmarks();
    scheduleAutoSync();
}

function addBookmark(chapter, surahName, ayah, textSnippet) {
    const list = getBookmarks();
    const existing = list.find(b => b.surah === chapter && b.ayah === ayah);
    if (existing) {
        alert(`الآية ${ayah} من سورة ${surahName} محفوظة مسبقاً في الإشارات المرجعية.`);
        return;
    }
    // Name the bookmark after the opening of the verse -- that is what you
    // recognise in a list. The reference is kept as a suffix.
    const opening = (textSnippet || '').trim().split(/\s+/).slice(0, 5).join(' ');
    const reference = `سورة ${surahName} - آية ${ayah}`;
    const defaultName = opening ? `${opening}… (${reference})` : reference;
    const customName = prompt('أدخل اسماً أو ملاحظة لهذه الإشارة المرجعية:', defaultName);
    if (customName === null) return;
    
    const newBm = {
        id: 'bm_' + Date.now() + '_' + Math.random().toString(36).substring(2, 6),
        name: customName.trim() || defaultName,
        surah: chapter,
        surahName: surahName,
        ayah: ayah,
        textSnippet: textSnippet.substring(0, 100),
        timestamp: Date.now()
    };
    list.unshift(newBm);
    saveBookmarks(list);
    updateSyncStatus(`تمت إضافة إشارة مرجعية: ${newBm.name} 🔖`, 'success');
}

function deleteBookmark(id) {
    if (!confirm('هل أنت متأكد من حذف هذه الإشارة المرجعية؟')) return;
    const list = getBookmarks().filter(b => b.id !== id);
    saveBookmarks(list);
    updateSyncStatus('تم حذف الإشارة المرجعية', 'success');
}

function editBookmark(id) {
    const list = getBookmarks();
    const bm = list.find(b => b.id === id);
    if (!bm) return;
    const newName = prompt('تعديل اسم الإشارة المرجعية:', bm.name);
    if (newName !== null && newName.trim()) {
        bm.name = newName.trim();
        saveBookmarks(list);
        updateSyncStatus('تم تعديل اسم الإشارة المرجعية', 'success');
    }
}

function applyBookmarkToReading(surah, surahName, ayah, snippet) {
    const continueSurah = document.getElementById('quranContinueSurah');
    const continueAyah = document.getElementById('quranContinueAyah');
    if (continueSurah) continueSurah.textContent = `سورة ${surahName}`;
    if (continueAyah) continueAyah.textContent = `الآية ${ayah}`;
    updateSyncStatus(`تم تحديد سورة ${surahName} آية ${ayah} للمتابعة على الساعة 📖`, 'success');
    scheduleAutoSync();
}

function renderBookmarks() {
    const container = document.getElementById('bookmarksList');
    const badge = document.getElementById('bookmarksCountBadge');
    if (!container) return;
    
    const list = getBookmarks();
    if (badge) badge.textContent = list.length;
    
    if (list.length === 0) {
        container.innerHTML = `<div class="empty-state compact"><span>🔖</span><small>لا توجد إشارات بعد. اضغط «إضافة علامة» من نتائج البحث.</small></div>`;
        return;
    }
    
    container.innerHTML = '';
    list.forEach(bm => {
        const card = document.createElement('div');
        card.className = 'bookmark-card';
        
        const top = document.createElement('div');
        top.className = 'bookmark-card-top';
        
        const title = document.createElement('span');
        title.className = 'bookmark-title';
        title.textContent = bm.name || `سورة ${bm.surahName} [${bm.ayah}]`;
        
        const meta = document.createElement('span');
        meta.className = 'bookmark-meta';
        meta.textContent = `سورة ${bm.surahName} · آية ${bm.ayah}`;
        
        top.append(title, meta);
        
        const snippet = document.createElement('p');
        snippet.className = 'bookmark-snippet';
        snippet.textContent = `﴿ ${bm.textSnippet} ﴾`;
        
        const actions = document.createElement('div');
        actions.className = 'bookmark-actions';
        
        const btnRead = document.createElement('button');
        btnRead.type = 'button';
        btnRead.className = 'bookmark-btn';
        btnRead.textContent = '📖 متابعة القراءة';
        btnRead.addEventListener('click', () => applyBookmarkToReading(bm.surah, bm.surahName, bm.ayah, bm.textSnippet));
        
        const btnEdit = document.createElement('button');
        btnEdit.type = 'button';
        btnEdit.className = 'bookmark-btn';
        btnEdit.textContent = '✎ تعديل الاسم';
        btnEdit.addEventListener('click', () => editBookmark(bm.id));
        
        const btnDel = document.createElement('button');
        btnDel.type = 'button';
        btnDel.className = 'bookmark-btn danger';
        btnDel.textContent = '⌫ حذف';
        btnDel.addEventListener('click', () => deleteBookmark(bm.id));
        
        actions.append(btnRead, btnEdit, btnDel);
        card.append(top, snippet, actions);
        container.appendChild(card);
    });
}

function showSearchMessage(container, message) {
    container.replaceChildren();
    const node = document.createElement('div');
    node.className = 'text-muted';
    node.textContent = message;
    container.appendChild(node);
}

// ── LOCATIONS TAB ──
function renderLocations() {
    const list = document.getElementById('locationsList');
    if (!list) return;
    list.replaceChildren();

    // ── Section 1: the user's own saved locations (these sync to the watch) ──
    if (savedLocations.length) {
        const head = document.createElement('small');
        head.className = 'locations-subhead';
        head.textContent = `المحفوظة لديك (${savedLocations.length}) — تُزامَن إلى الساعة`;
        list.appendChild(head);
    }
    savedLocations.forEach(loc => {
        const isActive = loc.id === watchSettings.selectedLocationId;
        const card = document.createElement('div');
        card.className = `location-row${isActive ? ' active' : ''}`;
        const pin = document.createElement('span');
        pin.textContent = '⌖';
        const info = document.createElement('div');
        const name = document.createElement('strong');
        name.textContent = loc.name;
        const meta = document.createElement('small');
        meta.textContent = `${Number(loc.latitude).toFixed(3)}, ${Number(loc.longitude).toFixed(3)}`;
        info.append(name, meta);
        const select = document.createElement('button');
        select.type = 'button';
        select.className = 'button';
        select.textContent = isActive ? 'نشط' : 'تفعيل';
        select.addEventListener('click', () => selectLocation({ id: loc.id, name: loc.name, lat: loc.latitude, lng: loc.longitude }));
        const del = document.createElement('button');
        del.type = 'button';
        del.className = 'button danger';
        del.textContent = 'حذف';
        del.addEventListener('click', () => removeSavedLocation(loc.id));
        card.append(pin, info, select, del);
        list.appendChild(card);
    });

    // ── Section 2: preset cities — one tap adds them to the synced list ──
    const presetHead = document.createElement('small');
    presetHead.className = 'locations-subhead';
    presetHead.textContent = 'أضف من قائمة المدن';
    list.appendChild(presetHead);

    const recent = document.getElementById('recentLocations');
    if (recent) {
        recent.replaceChildren();
        const title = document.createElement('small');
        title.textContent = 'آخر المواقع';
        recent.appendChild(title);
        (watchSettings.recentLocationIds || []).slice(0, 3).forEach(id => {
            const loc = argentinaLocations.find(location => location.id === id);
            if (!loc) return;
            const shortcut = document.createElement('button');
            shortcut.type = 'button';
            shortcut.className = 'recent-location-chip';
            shortcut.textContent = loc.name.split(' (')[0];
            shortcut.addEventListener('click', () => selectLocation(loc));
            recent.appendChild(shortcut);
        });
    }

    argentinaLocations.forEach(loc => {
        const card = document.createElement('div');
        card.className = `location-row${loc.id === watchSettings.selectedLocationId ? ' active' : ''}`;
        const pin = document.createElement('span');
        pin.textContent = '⌖';
        const info = document.createElement('div');
        const name = document.createElement('strong');
        name.textContent = loc.name;
        const meta = document.createElement('small');
        meta.textContent = `${loc.lat.toFixed(3)}, ${loc.lng.toFixed(3)} · القبلة ${loc.qibla}`;
        info.append(name, meta);
        const map = document.createElement('a');
        map.className = 'button';
        map.href = `https://maps.google.com/?q=${loc.lat},${loc.lng}`;
        map.target = '_blank';
        map.rel = 'noopener noreferrer';
        map.textContent = 'الخريطة';
        const add = document.createElement('button');
        add.type = 'button';
        add.className = 'button';
        const already = savedLocations.some(l => l.name === loc.name);
        add.textContent = already ? 'مضافة' : 'إضافة';
        add.disabled = already;
        add.addEventListener('click', () => addSavedLocation({ id: loc.id, name: loc.name, latitude: loc.lat, longitude: loc.lng }));
        card.append(pin, info, add, map);
        list.appendChild(card);
    });
}

function selectLocation(loc) {
    watchSettings.selectedLocationId = loc.id;
    watchSettings.selectedLocationName = loc.name;
    watchSettings.selectedLat = loc.lat;
    watchSettings.selectedLng = loc.lng;
    watchSettings.recentLocationIds = [loc.id, ...(watchSettings.recentLocationIds || []).filter(id => id !== loc.id)].slice(0, 3);
    // The active location must be in the synced list so the watch has its coords.
    if (!savedLocations.some(l => l.id === loc.id)) {
        savedLocations.push({ id: loc.id, name: loc.name, latitude: Number(loc.lat), longitude: Number(loc.lng) });
        persistSavedLocations();
    }
    const city = document.getElementById('settingActiveCity');
    if (city) city.value = loc.id;
    renderLocations();
    scheduleAutoSync();
}

// ── SETTINGS TAB ──
function setupSettingsTab() {
    const readerFonts = new Set(['default', 'uthmani', 'amiri', 'naskh', 'kufi', 'tajawal', 'cairo', 'sansserif', 'serif']);
    if (!readerFonts.has(watchSettings.fontFamily)) watchSettings.fontFamily = 'uthmani';
    const range = document.getElementById('settingReaderFontSize');
    const disp = document.getElementById('fontSizeDisplay');
    const prev = document.getElementById('readerFontPreview');

    if (range && disp && prev) {
        range.addEventListener('input', (e) => {
            disp.textContent = e.target.value;
            prev.style.fontSize = e.target.value + 'px';
            watchSettings.fontSize = parseInt(e.target.value);
            scheduleAutoSync();
        });
    }

    const binds = [
        ['settingQuranFont', 'fontFamily'],
        ['settingAyahColor', 'ayahColor'],
        ['settingReaderBg', 'readerBgColor'],
        ['settingReaderTextColor', 'readerTextColor'],
        ['settingAyahCustom', 'customAyahColor'],
        ['settingReaderBgCustom', 'customReaderBgColor'],
        ['settingReaderTextCustom', 'customReaderTextColor'],
        ['settingActiveCity', 'selectedLocationId'],
        ['settingCalcMethod', 'calculationMethod'],
        ['settingNotifications', 'notificationsEnabled'],
        ['settingNotificationVibration', 'notificationVibration'],
        ['settingNotificationFullScreen', 'notificationFullScreen'],
        ['reminderFajr', 'reminderFajr'],
        ['reminderDhuhr', 'reminderDhuhr'],
        ['reminderAsr', 'reminderAsr'],
        ['reminderMaghrib', 'reminderMaghrib'],
        ['reminderIsha', 'reminderIsha'],
        ['settingTilesDefaultMode', 'tilesDefaultMode']
    ];

    binds.forEach(([elemId, propKey]) => {
        const el = document.getElementById(elemId);
        if (el) {
            const applySetting = (e) => {
                let val = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
                if (val === 'true') val = true;
                if (val === 'false') val = false;
                if (propKey.startsWith('reminder')) val = parseInt(val);
                watchSettings[propKey] = val;
                if (['fontFamily', 'ayahColor', 'readerBgColor', 'readerTextColor', 'customAyahColor', 'customReaderBgColor', 'customReaderTextColor'].includes(propKey)) applyReaderPreviewTheme();
                scheduleAutoSync();
            };
            el.addEventListener('change', applySetting);
            if (el.type === 'color') el.addEventListener('input', applySetting);
        }
    });

    const appearance = getAppearance();
    [['settingTileShape', 'tileShape'], ['settingWatchPattern', 'pattern'], ['settingIconPalette', 'iconPalette']].forEach(([id, key]) => {
        const control = document.getElementById(id);
        if (!control) return;
        control.value = appearance[key];
        control.addEventListener('change', event => applyAppearanceControl(key, event.target.value));
    });

    Object.entries({
        settingQuranFont: watchSettings.fontFamily,
        settingReaderFontSize: watchSettings.fontSize,
        settingAyahColor: watchSettings.ayahColor,
        settingReaderBg: watchSettings.readerBgColor,
        settingReaderTextColor: watchSettings.readerTextColor,
        settingAyahCustom: watchSettings.customAyahColor,
        settingReaderBgCustom: watchSettings.customReaderBgColor,
        settingReaderTextCustom: watchSettings.customReaderTextColor,
        settingNotifications: watchSettings.notificationsEnabled,
        settingNotificationVibration: watchSettings.notificationVibration,
        settingNotificationFullScreen: watchSettings.notificationFullScreen,
        reminderFajr: watchSettings.reminderFajr,
        reminderDhuhr: watchSettings.reminderDhuhr,
        reminderAsr: watchSettings.reminderAsr,
        reminderMaghrib: watchSettings.reminderMaghrib,
        reminderIsha: watchSettings.reminderIsha
    }).forEach(([id, value]) => {
        const el = document.getElementById(id);
        if (!el) return;
        if (el.type === 'checkbox') el.checked = Boolean(value);
        else el.value = value;
    });
    if (range && disp && prev) {
        disp.textContent = watchSettings.fontSize;
        prev.style.fontSize = watchSettings.fontSize + 'px';
    }
    applyReaderPreviewTheme();

    const btnSave = document.getElementById('btnSaveSettingsToWatch');
    if (btnSave) {
        btnSave.addEventListener('click', () => syncAll(true));
    }

    const btnReset = document.getElementById('btnResetSettings');
    if (btnReset) {
        btnReset.addEventListener('click', () => {
            if (confirm("هل تريد استعادة جميع الإعدادات إلى الوضع الافتراضي؟")) {
                localStorage.removeItem(LOCAL_DRAFT_KEY);
                location.reload();
            }
        });
    }
}

function applyReaderPreviewTheme() {
    const preview = document.getElementById('readerFontPreview');
    if (!preview) return;
    const backgrounds = { black: '#111214', navy: '#10233f', sepia: '#f2e4c9', forest: '#143d31', slate: '#263341', custom: watchSettings.customReaderBgColor };
    const texts = { white: '#ffffff', ivory: '#fff4d6', mint: '#c8ffe8', golden: '#ffd56a', cyan: '#9ee7ff', custom: watchSettings.customReaderTextColor };
    const ayahs = { yellow: '#ffd60a', green: '#34c759', cyan: '#5ac8fa', rose: '#ff6b9a', custom: watchSettings.customAyahColor };
    const fonts = { uthmani: "'Amiri', serif", amiri: "'Amiri', serif", naskh: "'Noto Naskh Arabic', serif", kufi: "'Noto Kufi Arabic', sans-serif", tajawal: "'Tajawal', sans-serif", cairo: "'Cairo', sans-serif", sansserif: 'sans-serif', serif: 'serif', default: 'inherit' };
    const textColor = texts[watchSettings.readerTextColor] || texts.white;
    const ayahColor = ayahs[watchSettings.ayahColor] || ayahs.yellow;
    preview.style.background = backgrounds[watchSettings.readerBgColor] || backgrounds.black;
    preview.style.color = textColor;
    preview.style.borderColor = ayahColor;
    preview.style.fontFamily = fonts[watchSettings.fontFamily] || fonts.default;
    preview.innerHTML = `<strong style="color:${ayahColor}">سورة الكهف · 18</strong> وَتَحْسَبُهُمْ أَيْقَاظًا وَهُمْ رُقُودٌ`;
}

// ── SYNC ENGINE ──
let autoSyncTimeout = null;
const LOCAL_DRAFT_KEY = 'quran_watch_finder_draft';

function saveLocalDraft() {
    try {
        localStorage.setItem(LOCAL_DRAFT_KEY, JSON.stringify({ tileConfig, watchSettings, savedAt: Date.now() }));
        updateSyncStatus('تم حفظ المسودة محليًا', 'success');
    } catch (error) {
        console.error('Local draft save failed:', error);
        updateSyncStatus('تعذر حفظ المسودة في المتصفح', 'error');
    }
}

function restoreLocalDraft() {
    try {
        const draft = JSON.parse(localStorage.getItem(LOCAL_DRAFT_KEY) || 'null');
        if (draft?.tileConfig?.tiles?.length) tileConfig = draft.tileConfig;
        if (draft?.watchSettings) watchSettings = { ...watchSettings, ...draft.watchSettings };
    } catch (error) {
        console.warn('Ignoring an invalid local draft:', error);
    }
}

// A transient bottom toast. Called from ~14 places and, until now, never
// defined -- every call threw ReferenceError, which the sync path caught and
// reported as "showToast is not defined", making a successful sync look failed.
let toastTimer = null;
function showToast(message) {
    let el = document.getElementById('appToast');
    if (!el) {
        el = document.createElement('div');
        el.id = 'appToast';
        el.className = 'app-toast';
        document.body.appendChild(el);
    }
    el.textContent = message;
    el.classList.add('is-visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('is-visible'), 2600);
}

function updateSyncStatus(message, tone = '') {
    const pill = document.getElementById('syncStatus');
    const feedback = document.getElementById('syncFeedback');
    const sidebar = document.getElementById('sidebarSyncStatus');
    if (pill) pill.lastChild.textContent = ` ${message}`;
    if (feedback) {
        feedback.textContent = message;
        feedback.className = `sync-feedback ${tone}`.trim();
    }
    if (sidebar) sidebar.textContent = message;
}

function scheduleAutoSync() {
    clearTimeout(autoSyncTimeout);
    saveLocalDraft();
    autoSyncTimeout = setTimeout(() => {
        syncAll(false);
    }, 1200);
}

async function syncAll(isManual = false) {
    const pin = document.getElementById('syncPinCode')?.value || '41331';

    tileConfig.version = Date.now();
    const syncedSettings = {
        ...watchSettings,
        prayerReminders: {
            fajr: watchSettings.reminderFajr,
            dhuhr: watchSettings.reminderDhuhr,
            asr: watchSettings.reminderAsr,
            maghrib: watchSettings.reminderMaghrib,
            isha: watchSettings.reminderIsha,
            vibration: watchSettings.notificationVibration,
            fullScreen: watchSettings.notificationFullScreen
        }
    };
    const payload = {
        type: 'FULL_SYNC',
        version: tileConfig.version,
        tilesConfig: tileConfig,
        watchFaceConfig: watchFaceConfig,
        settings: syncedSettings,
        // The watch's importDataJson reconciles both of these against its tables.
        locations: savedLocations.map(l => ({ id: l.id, name: l.name, latitude: l.latitude, longitude: l.longitude })),
        bookmarks: getBookmarks().map(b => ({
            id: b.id,
            surah: b.surah,
            surahNameAr: b.surahName ? `سورة ${b.surahName}` : `سورة ${b.surah}`,
            ayahNumber: b.ayah,
            ayahText: b.textSnippet || '',
            note: b.name || '',
            createdAt: b.timestamp || Date.now()
        }))
    };
    
    saveLocalDraft();
    updateSyncStatus('جاري الاتصال…');

    // Only the cloud relay is used. A direct call to the watch over the LAN is
    // blocked by every browser as mixed content, because this page is served
    // over HTTPS and the watch only speaks plain HTTP on port 41331.
    let cloudOk = false;
    let failure = '';
    try {
        const response = await fetch(`/api/sync?code=${encodeURIComponent(pin)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || `الخادم ردّ بالرمز ${response.status}`);
        cloudOk = true;
        lastSyncedAt = Date.now();
        lastSyncStorage = result.storage || null;
        renderOverviewMetrics();
        // The relay says whether it reached durable storage, so a silent
        // fallback to instance memory is visible instead of looking like success.
        if (result.storage === 'memory') {
            updateSyncStatus('تمت المزامنة، لكن بلا تخزين دائم (اربط Vercel KV)', 'error');
            showToast('⚠ تم الحفظ مؤقتًا فقط — لم يُربط مخزن Vercel KV بعد');
        } else {
            updateSyncStatus('تم الحفظ السحابي بنجاح', 'success');
            showToast('✓ تم حفظ التصميم سحابيًا وجاهز للمزامنة على الساعة');
        }
    } catch (error) {
        failure = error.message;
        console.error('Cloud sync failed:', error);
        updateSyncStatus(`حُفظت المسودة محليًا؛ تعذر الاتصال بالسحابة (${failure})`, 'error');
        showToast('تم حفظ المسودة محليًا في المتصفح');
    }

    if (isManual && !cloudOk) {
        const feedback = document.getElementById('syncFeedback');
        feedback?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
    return { cloudOk, error: failure };
}

function unwrapSyncPayload(response) {
    if (!response || typeof response !== 'object') return null;
    return response.data && typeof response.data === 'object' ? response.data : response;
}

async function pullFromCloud() {
    const pin = document.getElementById('syncPinCode')?.value || '41331';
    updateSyncStatus('جاري جلب النسخة السحابية…');
    try {
        const response = await fetch(`/api/sync?code=${encodeURIComponent(pin)}`);
        if (!response.ok) throw new Error(`Cloud pull failed with ${response.status}`);
        const data = unwrapSyncPayload(await response.json());
        if (!data?.tilesConfig?.tiles) throw new Error('Cloud response has no tile configuration');
        pushHistory();
        tileConfig = data.tilesConfig;
        if (data.settings) watchSettings = { ...watchSettings, ...data.settings };
        // The watch face belongs to the same envelope, so a pull must restore it too.
        if (data.watchFaceConfig) {
            watchFaceConfig = { ...watchFaceConfig, ...data.watchFaceConfig };
            localStorage.setItem('quran_watch_wf_config', JSON.stringify(watchFaceConfig));
        }
        if (Array.isArray(data.locations)) {
            savedLocations = data.locations
                .filter(l => l && l.name && Number.isFinite(Number(l.latitude)))
                .map(l => ({ id: l.id || `loc_web_${Date.now()}`, name: l.name, latitude: Number(l.latitude), longitude: Number(l.longitude) }));
            persistSavedLocations();
            renderLocations();
        }
        if (Array.isArray(data.bookmarks)) {
            // A bookmark added or deleted on the watch arrives here, in the one
            // shape the manager and the payload both speak.
            const merged = data.bookmarks.map(b => ({
                id: b.id || `bm_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
                name: b.note || b.name || '',
                surah: b.surah || b.surahNum || 1,
                surahName: (b.surahNameAr || b.surahName || '').replace(/^سورة\s*/, ''),
                ayah: b.ayahNumber || b.ayahNum || 1,
                textSnippet: b.ayahText || b.textSnippet || '',
                timestamp: b.createdAt || b.timestamp || Date.now()
            }));
            localStorage.setItem('quran_bookmarks', JSON.stringify(merged));
            renderBookmarks();
        }
        selectedIndices.clear();
        primarySelectedIdx = tileConfig.tiles.length > 0 ? 0 : -1;
        if (primarySelectedIdx >= 0) selectedIndices.add(primarySelectedIdx);
        validateAndPackGrid();
        renderCanvas();
        updateEditor();
        saveLocalDraft();
        updateSyncStatus('تم جلب التصميم والإعدادات من السحابة', 'success');
    } catch (error) {
        console.error('Cloud pull error:', error);
        updateSyncStatus('تعذر جلب النسخة السحابية؛ تحقق من الاتصال والرمز', 'error');
    }
}

document.addEventListener('DOMContentLoaded', () => {
    initApp();
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('./sw.js').catch(error => console.warn('PWA registration failed:', error));
    }
});


// ── PRESET MODES WEB ENGINE ──
const WEB_PRESETS = {
    'preset_prayer_strip': {
        tiles: [
            { id: 'clock_big', colorHex: '#6366F1', colSpan: 6, rowIndex: 0, fontSize: 20, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'prayer_countdown', colorHex: '#10B981', colSpan: 6, rowIndex: 0, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'prayer_strip_5', colorHex: '#047857', colSpan: 12, rowIndex: 1, fontSize: 12, displayStyle: 'prayer_strip_5', fontColorHex: '#ffffff' },
            { id: 'folder_islamic', colorHex: '#0284C7', folderItems: ['quran', 'tasbih', 'qibla', 'prayer'], colSpan: 6, rowIndex: 2, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'qibla', colorHex: '#D97706', colSpan: 6, rowIndex: 2, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' }
        ]
    },
    'preset_quran_focus': {
        tiles: [
            { id: 'clock_big', colorHex: '#4F46E5', colSpan: 4, rowIndex: 0, fontSize: 16, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'quran_resume', colorHex: '#0E7490', colSpan: 8, rowIndex: 0, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'folder_islamic', colorHex: '#0284C7', folderItems: ['quran', 'tasbih', 'qibla', 'prayer'], colSpan: 6, rowIndex: 1, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'tasbih', colorHex: '#059669', colSpan: 6, rowIndex: 1, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'bookmarks', colorHex: '#D97706', colSpan: 6, rowIndex: 2, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'settings', colorHex: '#334155', colSpan: 6, rowIndex: 2, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' }
        ]
    },
    'preset_big_clock': {
        tiles: [
            { id: 'clock_big', colorHex: '#7C3AED', colSpan: 12, rowIndex: 0, fontSize: 24, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'date_big', colorHex: '#0284C7', colSpan: 6, rowIndex: 1, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'prayer_countdown', colorHex: '#10B981', colSpan: 6, rowIndex: 1, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'folder_islamic', colorHex: '#0E7490', folderItems: ['quran', 'tasbih', 'qibla', 'prayer'], colSpan: 6, rowIndex: 2, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'settings', colorHex: '#334155', colSpan: 6, rowIndex: 2, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' }
        ]
    },
    'preset_smart_tools': {
        tiles: [
            { id: 'clock_big', colorHex: '#6366F1', colSpan: 6, rowIndex: 0, fontSize: 18, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'prayer_countdown', colorHex: '#10B981', colSpan: 6, rowIndex: 0, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'folder_islamic', colorHex: '#0284C7', folderItems: ['quran', 'tasbih', 'qibla', 'prayer'], colSpan: 6, rowIndex: 1, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'folder_tools', colorHex: '#EA580C', folderItems: ['bookmarks', 'locations', 'settings'], colSpan: 6, rowIndex: 1, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'settings', colorHex: '#334155', colSpan: 6, rowIndex: 2, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' }
        ]
    },
    'preset_color_accent': {
        tiles: [
            { id: 'color_only', colorHex: '#EC4899', colSpan: 4, rowIndex: 0, displayStyle: 'color_only' },
            { id: 'clock_big', colorHex: '#8B5CF6', colSpan: 8, rowIndex: 0, fontSize: 20, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'quran_resume', colorHex: '#06B6D4', colSpan: 8, rowIndex: 1, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'color_only', colorHex: '#F59E0B', colSpan: 4, rowIndex: 1, displayStyle: 'color_only' },
            { id: 'tasbih', colorHex: '#10B981', colSpan: 6, rowIndex: 2, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'settings', colorHex: '#334155', colSpan: 6, rowIndex: 2, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' }
        ]
    }
};

window.applyPresetWeb = function(presetId) {
    if (!WEB_PRESETS[presetId]) return;
    pushHistory();
    let template = WEB_PRESETS[presetId];
    tileConfig.tiles = JSON.parse(JSON.stringify(template.tiles));
    tileConfig.tiles.forEach(t => {
        if (!t.fontColorHex) t.fontColorHex = '#ffffff';
        if (!t.iconColorHex) t.iconColorHex = '#ffffff';
        if (!t.iconSize) t.iconSize = 24;
        if (!t.iconStyle) t.iconStyle = 'static';
        if (!t.iconType) t.iconType = 'default';
        if (!t.fontFamily) t.fontFamily = 'Uthmanic';
        if (!t.textX) t.textX = 50;
        if (!t.textY) t.textY = 50;
        if (!t.iconX) t.iconX = 50;
        if (!t.iconY) t.iconY = 30;
    });
    primarySelectedIdx = 0;
    selectedIndices.clear();
    selectedIndices.add(0);
    validateAndPackGrid();
    renderCanvas();
    updateEditor();
    scheduleAutoSync();
};

window.saveCurrentAsCustomPresetWeb = function() {
    let name = prompt('أدخل اسم القالب المخصص:', 'قالب مخصص ' + (new Date().toLocaleTimeString('ar-EG')));
    if (!name) return;
    let customPresets = JSON.parse(localStorage.getItem('quran_watch_web_custom_presets') || '[]');
    let newCustom = {
        id: 'custom_' + Date.now(),
        title: name,
        icon: '⭐',
        tiles: JSON.parse(JSON.stringify(tileConfig.tiles))
    };
    customPresets.push(newCustom);
    localStorage.setItem('quran_watch_web_custom_presets', JSON.stringify(customPresets));
    alert('تم حفظ القالب بنجاح!');
};


// ════════════════ WATCH FACE STUDIO (LAYER 1) MODULE ════════════════
const WATCH_FACE_MODELS = [
    {
        id: 'ULTRA_DIGITAL_CLASSIC',
        name: 'الرقمي الكلاسيكي',
        desc: 'أرقام رقمية عريضة مع تعقيدات متناسقة وشعار المصحف',
        category: 'digital'
    },
    {
        id: 'CLASSIC_CHRONO_HERITAGE',
        name: 'الكرونوغراف التراثي',
        desc: 'عقارب تناظرية كلاسيكية مع موانئ فرعية للمواقيت والورد',
        category: 'analog'
    },
    {
        id: 'CELESTIAL_SOLAR_ARC',
        name: 'فلكي محيطي (1..12)',
        desc: 'أرقام محيطية دائرية كاملة على إطار الشاشة مع مدار شمسي',
        category: 'celestial'
    },
    {
        id: 'ULTRA_DIGITAL_LATIN_ALERT',
        name: 'الرقمي مع تنبيه الأذان',
        desc: 'عرض رقمي فائق مع قوس تنبيه كهرماني قبل الصلاة بـ 10د',
        category: 'digital'
    },
    {
        id: 'CLASSIC_CHRONO_LATIN_ALERT',
        name: 'الكرونوغراف مع التنبيه',
        desc: 'تصميم تناظري تراثي مع مؤشر التنبيه المسبق للأذان',
        category: 'analog'
    },
    {
        id: 'CELESTIAL_MINIMAL_LATIN_ALERT',
        name: 'فلكي نقي (ساعة فقط)',
        desc: 'رقم ساعة مركزي عملاق بدون أي أرقام دائرية - نقاء تام',
        category: 'celestial'
    },
    {
        id: 'EDGE_TYPOGRAPHY_FULL',
        name: 'الخط العريض الممتد',
        desc: 'استغلال كامل لمساحة الشاشة مع نصوص عربية مطرزة',
        category: 'modern'
    },
    {
        id: 'QURANIC_AMBIENT_ORBIT',
        name: 'المداري القرآني',
        desc: 'مدار إشعاعي ينبض مع ورد القرآن والمصحف الشريف',
        category: 'islamic'
    },
    {
        id: 'SOLAR_HORIZON_FULL',
        name: 'الأفق الشمسي',
        desc: 'حركة تفاعلية تتدرج مع مدار الشمس والشروق والغروب',
        category: 'celestial'
    },
    {
        id: 'FAJR_MIHRAB',
        name: 'محراب الفجر',
        desc: 'وقت هادئ داخل محراب مع الصلوات الخمس وموضع القراءة',
        category: 'islamic'
    },
    {
        id: 'DHIKR_PULSE',
        name: 'نبض الذكر',
        desc: 'حلقة تسبيح تفاعلية مع الطقس والبطارية والصلاة',
        category: 'islamic'
    },
    {
        id: 'QIBLA_SERENITY',
        name: 'بوصلة السكينة',
        desc: 'سهم قبلة مركزي بلا درجات أو تدريجات محيطية',
        category: 'islamic'
    },
    {
        id: 'QURAN_GALLERY',
        name: 'رِواق الآية',
        desc: 'اسم السورة ورقم الآية والمتن في قراءة عربية متصلة',
        category: 'islamic'
    },
    {
        id: 'DAILY_ORBITS',
        name: 'مدارات اليوم',
        desc: 'أقواس داخلية للبطارية والصلاة وضوء النهار والتسبيح',
        category: 'modern'
    },
    {
        id: 'BELIEVER_MOSAIC',
        name: 'فسيفساء المؤمن',
        desc: 'بلاطات متوازنة للطقس والقبلة والقرآن والصلوات',
        category: 'modern'
    }
];

const COMPLICATION_TYPES = [
    { id: 'NEXT_PRAYER', name: 'مواقيت الصلاة (المغرب 18:34)', icon: '🕌' },
    { id: 'BATTERY', name: 'مستوى البطارية (78%)', icon: '🔋' },
    { id: 'HIJRI_DATE', name: 'التقويم الهجري', icon: '🌙' },
    { id: 'GREGORIAN_DATE', name: 'التاريخ الميلادي (17 Sep)', icon: '📅' },
    { id: 'QURAN_RESUME', name: 'موضع المصحف (الكهف: 18)', icon: '📖' },
    { id: 'QIBLA', name: 'اتجاه القبلة', icon: '🕋' },
    { id: 'TASBIH', name: 'المسبحة الإلكترونية (33/33)', icon: '📿' },
    { id: 'WEATHER', name: 'الطقس والحرارة (24°C)', icon: '⛅' },
    { id: 'SUNRISE_SUNSET', name: 'الشروق والغروب (06:12)', icon: '🌅' },
    { id: 'DAILY_ATHKAR', name: 'ورد الأذكار اليومي', icon: '🤲' },
    { id: 'STEP_COUNTER', name: 'عداد الخطوات (غير متاح دون مصدر صحي)', icon: '🚶‍♂️' },
    { id: 'HEART_RATE', name: 'نبضات القلب (غير متاح دون مصدر صحي)', icon: '❤️' },
    { id: 'FASTING_TRACKER', name: 'صيام النوافل والإمساك', icon: '✨' },
    { id: 'PRAYER_ALERT', name: 'تنبيه الصلاة المسبق (باقي 10د)', icon: '🔔' },
    { id: 'HIDDEN', name: 'إخفاء المعلومة (نقاء تام)', icon: '🚫' }
];

// Kept in step with WatchFaceConfig.kt so a fresh install and the web studio
// agree on the starting face. "bottom" is the slot the watch lets you swap by
// tapping, so it defaults to something useful rather than hidden.
let watchFaceConfig = {
    selectedModel: 'ULTRA_DIGITAL_LATIN_ALERT',
    topSlot: 'HIJRI_DATE',
    leftSlot: 'BATTERY',
    rightSlot: 'NEXT_PRAYER',
    bottomSlot: 'QURAN_RESUME',
    slotProfiles: {},
    useLatinDigits: true
};

const WATCH_FACE_DEFAULT_SLOTS = {
    FAJR_MIHRAB: { topSlot: 'HIJRI_DATE', rightSlot: 'NEXT_PRAYER', leftSlot: 'NEXT_PRAYER', bottomSlot: 'QURAN_RESUME' },
    DHIKR_PULSE: { topSlot: 'NEXT_PRAYER', rightSlot: 'WEATHER', leftSlot: 'BATTERY', bottomSlot: 'QURAN_RESUME' },
    QIBLA_SERENITY: { topSlot: 'GREGORIAN_DATE', rightSlot: 'BATTERY', leftSlot: 'WEATHER', bottomSlot: 'NEXT_PRAYER' },
    QURAN_GALLERY: { topSlot: 'HIJRI_DATE', rightSlot: 'HIDDEN', leftSlot: 'HIDDEN', bottomSlot: 'NEXT_PRAYER' },
    DAILY_ORBITS: { topSlot: 'BATTERY', rightSlot: 'TASBIH', leftSlot: 'SUNRISE_SUNSET', bottomSlot: 'NEXT_PRAYER' },
    BELIEVER_MOSAIC: { topSlot: 'WEATHER', rightSlot: 'BATTERY', leftSlot: 'QIBLA', bottomSlot: 'QURAN_RESUME' }
};

function snapshotActiveWatchFaceSlots() {
    return {
        topSlot: watchFaceConfig.topSlot,
        rightSlot: watchFaceConfig.rightSlot,
        leftSlot: watchFaceConfig.leftSlot,
        bottomSlot: watchFaceConfig.bottomSlot
    };
}

function getActiveWatchFaceSlots(modelId = watchFaceConfig.selectedModel) {
    return watchFaceConfig.slotProfiles?.[modelId]
        || WATCH_FACE_DEFAULT_SLOTS[modelId]
        || snapshotActiveWatchFaceSlots();
}

function persistActiveWatchFaceSlots() {
    watchFaceConfig.slotProfiles = {
        ...(watchFaceConfig.slotProfiles || {}),
        [watchFaceConfig.selectedModel]: snapshotActiveWatchFaceSlots()
    };
}

function switchWatchFaceModel(modelId) {
    persistActiveWatchFaceSlots();
    const slots = getActiveWatchFaceSlots(modelId);
    watchFaceConfig = { ...watchFaceConfig, selectedModel: modelId, ...slots };
    persistActiveWatchFaceSlots();
}

function loadWatchFaceConfig() {
    try {
        const saved = localStorage.getItem('quran_watch_wf_config');
        if (saved) {
            watchFaceConfig = { ...watchFaceConfig, ...JSON.parse(saved) };
        }
        if (!watchFaceConfig.slotProfiles || typeof watchFaceConfig.slotProfiles !== 'object') watchFaceConfig.slotProfiles = {};
        persistActiveWatchFaceSlots();
    } catch (e) {
        console.warn('Could not load wf config', e);
    }
}

function saveWatchFaceConfig() {
    try {
        localStorage.setItem('quran_watch_wf_config', JSON.stringify(watchFaceConfig));
        showToast('تم حفظ إعدادات لوحة التطبيق!');
        // This used to POST to a dead fly.dev host and swallow the failure, so the
        // settings never actually left the browser. The relay is the working path.
        scheduleAutoSync();
    } catch (e) {
        console.warn('Could not save wf config', e);
    }
}

function initWatchFaceStudio() {
    loadWatchFaceConfig();
    renderWatchFaceModelCards();
    setupComplicationSelects();
    renderLiveWatchFacePreview();
    setupWatchFaceEvents();
}

function renderWatchFaceModelCards() {
    const container = document.getElementById('wfModelsList');
    if (!container) return;
    
    container.innerHTML = WATCH_FACE_MODELS.map(model => `
        <div class="wf-model-card ${model.id === watchFaceConfig.selectedModel ? 'active' : ''}" data-model-id="${model.id}">
            <div class="wf-model-thumb">
                ${renderThumbnailDial(model.id)}
            </div>
            <div class="wf-model-info">
                <h4>${model.name}</h4>
                <p>${model.desc}</p>
            </div>
        </div>
    `).join('');

    container.querySelectorAll('.wf-model-card').forEach(card => {
        card.addEventListener('click', () => {
            const modelId = card.dataset.modelId;
            switchWatchFaceModel(modelId);
            container.querySelectorAll('.wf-model-card').forEach(c => c.classList.remove('active'));
            card.classList.add('active');
            renderLiveWatchFacePreview();
            setupComplicationSelects();
        });
    });
}

function renderThumbnailDial(modelId) {
    if (modelId.includes('CHRONO')) {
        return `<svg width="40" height="40" viewBox="0 0 40 40">
            <circle cx="20" cy="20" r="18" fill="#111" stroke="#333" stroke-width="1"/>
            <line x1="20" y1="20" x2="20" y2="8" stroke="#f59e0b" stroke-width="1.5" stroke-linecap="round"/>
            <line x1="20" y1="20" x2="28" y2="20" stroke="#fff" stroke-width="1.5" stroke-linecap="round"/>
            <circle cx="20" cy="20" r="2" fill="#f59e0b"/>
        </svg>`;
    } else if (modelId.includes('CELESTIAL')) {
        return `<svg width="40" height="40" viewBox="0 0 40 40">
            <circle cx="20" cy="20" r="18" fill="#0b0f19" stroke="#1e293b" stroke-width="1"/>
            <circle cx="20" cy="20" r="13" fill="none" stroke="#38bdf8" stroke-dasharray="3,3" stroke-width="1"/>
            <text x="20" y="24" fill="#38bdf8" font-size="10" font-weight="bold" text-anchor="middle">12</text>
        </svg>`;
    } else {
        return `<div style="font-size:11px; font-weight:900; color:#fff; font-family:monospace;">12:45</div>`;
    }
}

function setupComplicationSelects() {
    const slots = [
        { id: 'selectTopSlot', iconId: 'topSlotCurrentIcon', slotKey: 'topSlot' },
        { id: 'selectLeftSlot', iconId: 'leftSlotCurrentIcon', slotKey: 'leftSlot' },
        { id: 'selectRightSlot', iconId: 'rightSlotCurrentIcon', slotKey: 'rightSlot' },
        { id: 'selectBottomSlot', iconId: 'bottomSlotCurrentIcon', slotKey: 'bottomSlot' }
    ];

    slots.forEach(slot => {
        const select = document.getElementById(slot.id);
        const iconEl = document.getElementById(slot.iconId);
        if (!select) return;

        select.innerHTML = COMPLICATION_TYPES.map(type => `
            <option value="${type.id}" ${watchFaceConfig[slot.slotKey] === type.id ? 'selected' : ''}>
                ${type.icon} ${type.name}
            </option>
        `).join('');

        const updateIcon = () => {
            const found = COMPLICATION_TYPES.find(t => t.id === watchFaceConfig[slot.slotKey]);
            if (iconEl && found) iconEl.textContent = found.icon;
        };
        updateIcon();

        select.addEventListener('change', (e) => {
            watchFaceConfig[slot.slotKey] = e.target.value;
            persistActiveWatchFaceSlots();
            updateIcon();
            renderLiveWatchFacePreview();
        });
    });
}

function getComplicationPresentation(compId) {
    const comp = COMPLICATION_TYPES.find(c => c.id === compId) || COMPLICATION_TYPES[0];
    let sampleText = '';
    switch (compId) {
        case 'NEXT_PRAYER': sampleText = 'المغرب 1 س 24 د'; break;
        case 'BATTERY': sampleText = '78%'; break;
        case 'HIJRI_DATE': sampleText = hijriToday(); break;
        case 'GREGORIAN_DATE': sampleText = new Date().toLocaleDateString('en-GB', { day: 'numeric', month: 'short' }); break;
        case 'QURAN_RESUME': sampleText = 'الفاتحة · 1'; break;
        case 'QIBLA': sampleText = 'اتجاه القبلة'; break;
        case 'TASBIH': sampleText = '27/33'; break;
        case 'WEATHER': sampleText = '24°C'; break;
        case 'SUNRISE_SUNSET': sampleText = '06:12 · 19:03'; break;
        case 'DAILY_ATHKAR': sampleText = 'أذكار'; break;
        case 'STEP_COUNTER':
        case 'HEART_RATE': sampleText = 'غير متاح'; break;
        case 'FASTING_TRACKER': sampleText = 'صيام'; break;
        case 'PRAYER_ALERT': sampleText = 'باقي 10د'; break;
        default: sampleText = comp.name.split(' ')[0];
    }
    return { ...comp, sampleText };
}

function getComplicationBadgeHtml(slotKey, className) {
    const compId = watchFaceConfig[slotKey];
    if (compId === 'HIDDEN') return '';
    const comp = getComplicationPresentation(compId);

    return `
        <div class="wf-comp-badge ${className}" data-slot-key="${slotKey}" title="${comp.name}">
            <span>${comp.icon}</span>
            <span>${comp.sampleText}</span>
        </div>
    `;
}

function getNewFaceSlotHtml(slotKey, className, expectedId = null, expectedHtml = null) {
    const compId = watchFaceConfig[slotKey];
    if (compId === 'HIDDEN') return '';
    const comp = getComplicationPresentation(compId);
    const content = expectedId === compId && expectedHtml
        ? expectedHtml
        : `<span>${comp.icon}</span><b>${comp.sampleText}</b>`;
    return `<div class="${className}" data-slot-key="${slotKey}" title="انقر للتبديل، واضغط مطولًا للتخصيص">${content}</div>`;
}

const NEW_WATCH_FACE_IDS = new Set(['FAJR_MIHRAB', 'DHIKR_PULSE', 'QIBLA_SERENITY', 'QURAN_GALLERY', 'DAILY_ORBITS', 'BELIEVER_MOSAIC']);

// One source for every value the previews show, mirroring the watch's
// WatchFaceLiveData. Time / date / Hijri are real; the browser has no live
// prayer or weather feed, so those stay representative but consistent across
// all six layouts and match the watch's fallback strings.
function webFaceData() {
    const now = new Date();
    return {
        time: now.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false }),
        hijri: hijriToday(),
        weekdayDay: `${weekdayToday()} ${now.getDate()}`,
        nextPrayerName: 'المغرب',
        // Mirrors PrayerTimesHelper.formatCountdown: Arabic units, never "h"/"m",
        // which used to flip direction inside an otherwise Arabic line.
        countdown: '1 س 24 د',
        prayers: [['فجر', '04:28'], ['ظهر', '12:34'], ['عصر', '15:47'], ['مغرب', '19:08'], ['عشاء', '20:38']],
        tasbihCount: 27, tasbihTarget: 33, dhikr: 'سبحان الله',
        readingSurah: 'الفاتحة', readingAyah: 1,
        ayahText: 'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ',
        battery: '78%', sunrise: '06:12', sunset: '19:03', weatherTemp: '24°C', daylight: '64%'
    };
}

function renderNewWatchFacePreview(model) {
    const d = webFaceData();
    switch (model) {
        case 'FAJR_MIHRAB': return `
            <div class="wf-v2 wf-v2-fajr-mihrab">
                ${getNewFaceSlotHtml('topSlot', 'wf-v2-hijri', 'HIJRI_DATE', `<span>${d.hijri}</span>`)}
                <div class="wf-v2-mihrab"><strong data-action="calendar">${d.time}</strong>${getNewFaceSlotHtml('rightSlot', 'wf-v2-mihrab-slot', 'NEXT_PRAYER', `<span>${d.nextPrayerName} بعد ${d.countdown}</span>`)}</div>
                ${getNewFaceSlotHtml('leftSlot', 'wf-v2-prayers', 'SUNRISE_SUNSET', d.prayers.map(([n, t]) => `<span>${n}<small>${t}</small></span>`).join(''))}
                ${getNewFaceSlotHtml('bottomSlot', 'wf-v2-pill', 'QURAN_RESUME', `<span>📖 ${d.readingSurah} · ${d.readingAyah}</span>`)}
            </div>`;
        case 'DHIKR_PULSE': return `
            <div class="wf-v2 wf-v2-dhikr-pulse">
                ${getNewFaceSlotHtml('topSlot', 'wf-v2-top-pill', 'NEXT_PRAYER', `<span>${d.nextPrayerName} ${d.countdown}</span>`)}<div class="wf-v2-ring"></div>
                ${getNewFaceSlotHtml('leftSlot', 'wf-v2-side wf-v2-left', 'BATTERY', `<span>🔋</span><b>${d.battery}</b>`)}${getNewFaceSlotHtml('rightSlot', 'wf-v2-side wf-v2-right', 'WEATHER', `<span>⛅</span><b>${d.weatherTemp}</b>`)}
                <div class="wf-v2-center" data-action="tasbih"><span>${d.dhikr}</span><strong>${d.time}</strong><em>${d.tasbihCount} / ${d.tasbihTarget}</em></div>
                ${getNewFaceSlotHtml('bottomSlot', 'wf-v2-pill', 'QURAN_RESUME', `<span>📖 ${d.readingSurah} · ${d.readingAyah}</span>`)}
            </div>`;
        case 'QIBLA_SERENITY': return `
            <div class="wf-v2 wf-v2-qibla-serenity">${getNewFaceSlotHtml('topSlot', 'wf-v2-top-pill', 'GREGORIAN_DATE', `<span>${d.weekdayDay}</span>`)}
                ${getNewFaceSlotHtml('leftSlot', 'wf-v2-side wf-v2-left', 'WEATHER', `<span>⛅</span><b>${d.weatherTemp}</b>`)}${getNewFaceSlotHtml('rightSlot', 'wf-v2-side wf-v2-right', 'BATTERY', `<span>🔋</span><b>${d.battery}</b>`)}
                <div class="wf-v2-qibla-arrow" data-action="qibla">➤<span>القبلة</span></div>${getNewFaceSlotHtml('bottomSlot', 'wf-v2-prayer-pill', 'NEXT_PRAYER', `<span>${d.nextPrayerName}</span><b>${d.countdown}</b>`)}
                <div class="wf-v2-sun" data-action="sun">☀ ${d.sunrise} · ${d.sunset} ☾</div></div>`;
        case 'QURAN_GALLERY': return `
            <div class="wf-v2 wf-v2-quran-gallery"><div class="wf-v2-quran-time"><strong data-action="calendar">${d.time}</strong>${getNewFaceSlotHtml('topSlot', 'wf-v2-quran-sub', 'HIJRI_DATE', `<span>${d.hijri}</span>`)}</div>
                ${getNewFaceSlotHtml('leftSlot', 'wf-v2-side wf-v2-left')}${getNewFaceSlotHtml('rightSlot', 'wf-v2-side wf-v2-right')}
                <div class="wf-v2-ayah" data-action="quran"><b>سورة الفاتحة · 1 </b>بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ</div>
                ${getNewFaceSlotHtml('bottomSlot', 'wf-v2-quran-footer', 'NEXT_PRAYER', `<span>${d.nextPrayerName} ${d.countdown}</span>`)}</div>`;
        case 'DAILY_ORBITS': return `
            <div class="wf-v2 wf-v2-daily-orbits"><div class="wf-v2-orbit-ring"></div>
                ${getNewFaceSlotHtml('topSlot', 'wf-v2-orbit-label ol1', 'BATTERY', `<span>🔋 ${d.battery}</span>`)}<div class="wf-v2-orbit-label ol2" data-action="sun">ضوء النهار<b>64%</b></div>
                <div class="wf-v2-orbit-clock" data-action="calendar">${d.time}<small>${d.weekdayDay}</small></div>
                ${getNewFaceSlotHtml('leftSlot', 'wf-v2-orbit-label ol3', 'SUNRISE_SUNSET', `<span>🌅 ${d.sunrise}</span>`)}${getNewFaceSlotHtml('rightSlot', 'wf-v2-orbit-label ol4', 'TASBIH', `<span>📿 ${d.tasbihCount}/${d.tasbihTarget}</span>`)}
                ${getNewFaceSlotHtml('bottomSlot', 'wf-v2-orbit-prayer', 'NEXT_PRAYER', `<span>${d.nextPrayerName} ${d.countdown}</span>`)}</div>`;
        case 'BELIEVER_MOSAIC': return `
            <div class="wf-v2 wf-v2-believer-mosaic">${getNewFaceSlotHtml('topSlot', 'wf-v2-weather', 'WEATHER', `<span>⛅ ${d.weatherTemp}</span>`)}
                <div class="wf-v2-mosaic-row">${getNewFaceSlotHtml('leftSlot', '', 'QIBLA', `<span>🕋</span><b>القبلة</b>`)}<strong data-action="calendar">${d.time}</strong>${getNewFaceSlotHtml('rightSlot', '', 'BATTERY', `<span>🔋</span><b>${d.battery}</b>`)}</div>
                <div class="wf-v2-mosaic-foot">${getNewFaceSlotHtml('bottomSlot', 'wf-v2-pill', 'QURAN_RESUME', `<span>📖 ${d.readingSurah} · ${d.readingAyah}</span>`)}<em data-action="tasbih">📿 ${d.tasbihCount}</em></div></div>`;
        default: return '';
    }
}

/**
 * The dial as HTML, with no listeners attached.
 *
 * The overview shows the same face as the studio; building the markup once
 * means the two can never drift into showing different watches.
 */
function buildWatchFaceDialHtml() {
    const model = watchFaceConfig.selectedModel;
    let dialHtml = '';
    const d = webFaceData();

    // Badges
    const topBadge = getComplicationBadgeHtml('topSlot', 'wf-comp-top');
    const leftBadge = getComplicationBadgeHtml('leftSlot', 'wf-comp-left');
    const rightBadge = getComplicationBadgeHtml('rightSlot', 'wf-comp-right');
    const bottomBadge = getComplicationBadgeHtml('bottomSlot', 'wf-comp-bottom');

    // Central Quran Emblem Button
    const centerEmblem = `<div class="wf-emblem-badge" title="المصحف الشريف (البلاطات المتصلة)">📖</div>`;

    if (NEW_WATCH_FACE_IDS.has(model)) {
        dialHtml = renderNewWatchFacePreview(model);
    } else if (model.includes('CHRONO')) {
        // Chronograph Dial
        dialHtml = `
            <div class="wf-analog-dial">
                <!-- Outer Bezel Numbers 1..12 -->
                ${renderDialHours()}
                ${topBadge}
                ${leftBadge}
                ${rightBadge}
                ${bottomBadge}
                <!-- Hands -->
                <svg style="position:absolute; inset:0; width:100%; height:100%; pointer-events:none;">
                    <!-- Hour Hand -->
                    <line x1="50%" y1="50%" x2="35%" y2="35%" stroke="#ffffff" stroke-width="4.5" stroke-linecap="round"/>
                    <!-- Minute Hand -->
                    <line x1="50%" y1="50%" x2="50%" y2="20%" stroke="#e2e8f0" stroke-width="3" stroke-linecap="round"/>
                    <!-- Second Hand -->
                    <line x1="50%" y1="50%" x2="50%" y2="12%" stroke="#f59e0b" stroke-width="1.5" stroke-linecap="round"/>
                    <circle cx="50%" cy="50%" r="4" fill="#f59e0b"/>
                </svg>
                ${centerEmblem}
            </div>
        `;
    } else if (model.includes('CELESTIAL')) {
        // Celestial Model
        const isCleanMinimal = model.includes('MINIMAL');
        dialHtml = `
            <div class="wf-analog-dial" style="display:flex; flex-direction:column; align-items:center; justify-content:center;">
                ${!isCleanMinimal ? renderDialHours() : ''}
                ${topBadge}
                ${leftBadge}
                ${rightBadge}
                ${bottomBadge}
                <div class="wf-center-clock" style="color: #38bdf8; font-size: ${isCleanMinimal ? '54px' : '42px'};">
                    ${d.time.split(':')[0]}<span style="opacity:0.6; font-size: 28px;">:${d.time.split(':')[1]}</span>
                </div>
                <div style="font-size:11px; color:#94a3b8; margin-top:2px;">${d.weekdayDay} · ${d.hijri}</div>
                ${centerEmblem}
            </div>
        `;
    } else {
        // Digital Models — QURANIC_AMBIENT_ORBIT keeps the verse line; the rest
        // show the next prayer under the clock. All values come from webFaceData.
        const isOrbit = model.includes('QURANIC') || model.includes('ORBIT');
        const subline = isOrbit
            ? `<div style="font-size:10px; color:#F3ECDA; font-family:'Amiri',serif; margin-top:4px; max-width:82%; line-height:1.5;">سورة ${d.readingSurah} · ${d.readingAyah} — ${d.ayahText}</div>`
            : `<div style="font-size:11px; color:#fbbf24; font-weight:600; margin-top:3px;">${d.nextPrayerName} بعد ${d.countdown}</div>`;
        dialHtml = `
            <div style="width:100%; height:100%; display:flex; flex-direction:column; align-items:center; justify-content:center; position:relative;">
                ${topBadge}
                ${leftBadge}
                ${rightBadge}
                ${bottomBadge}
                <div class="wf-center-clock">${d.time}</div>
                ${subline}
                ${centerEmblem}
            </div>
        `;
    }

    return dialHtml;
}

function renderLiveWatchFacePreview() {
    const container = document.getElementById('wfDialPreviewContainer');
    if (!container) return;
    const dialHtml = buildWatchFaceDialHtml();
    container.innerHTML = dialHtml;

    // Every visible slot in both the original and new faces is directly interactive.
    container.querySelectorAll('[data-slot-key]').forEach(badge => {
        badge.addEventListener('click', (e) => {
            e.stopPropagation();
            const slotKey = badge.dataset.slotKey;
            // Cycle complication
            const currentIndex = COMPLICATION_TYPES.findIndex(c => c.id === watchFaceConfig[slotKey]);
            const nextIndex = (currentIndex + 1) % COMPLICATION_TYPES.length;
            watchFaceConfig[slotKey] = COMPLICATION_TYPES[nextIndex].id;
            persistActiveWatchFaceSlots();
            setupComplicationSelects();
            renderLiveWatchFacePreview();
        });
        badge.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            e.stopPropagation();
            const selectId = { topSlot: 'selectTopSlot', leftSlot: 'selectLeftSlot', rightSlot: 'selectRightSlot', bottomSlot: 'selectBottomSlot' }[badge.dataset.slotKey];
            const select = document.getElementById(selectId);
            if (select) {
                select.focus();
                showToast('اختر المعلومة المطلوبة لهذه المساحة');
            }
        });
    });

    const actionMessages = {
        prayers: 'يفتح جدول مواقيت الصلاة على الساعة',
        tasbih: 'النقر يزيد التسبيح، والضغط المطوّل يفتح السبحة',
        qibla: 'يفتح بوصلة القبلة الحية على الساعة',
        quran: 'يفتح الآية كاملة في المصحف',
        calendar: 'يفتح التاريخ والتقويم',
        sun: 'يفتح تفاصيل الشروق والغروب'
    };
    container.querySelectorAll('[data-action]').forEach(item => {
        item.addEventListener('click', (event) => {
            event.stopPropagation();
            showToast(actionMessages[item.dataset.action] || 'هذا العنصر تفاعلي على الساعة');
        });
    });

    const emblem = container.querySelector('.wf-emblem-badge');
    if (emblem) {
        emblem.addEventListener('click', () => {
            // Navigate to Layer 2
            const tilesTab = document.querySelector('[data-tab-target="tiles"]');
            if (tilesTab) tilesTab.click();
        });
    }
}

function renderDialHours() {
    const hours = [12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
    const radius = 105; // px from center
    const center = 130; // center of 260px container
    
    return hours.map((hour, i) => {
        const angle = (i * 30 - 90) * (Math.PI / 180);
        const x = center + radius * Math.cos(angle);
        const y = center + radius * Math.sin(angle);
        return `<div class="wf-dial-number" style="left:${x}px; top:${y}px;">${hour}</div>`;
    }).join('');
}

function setupWatchFaceEvents() {
    const btnSave = document.getElementById('btnSaveWatchFaceToWatch');
    if (btnSave) {
        btnSave.addEventListener('click', () => {
            saveWatchFaceConfig();
        });
    }

    const btnReset = document.getElementById('btnResetSlotsDefault');
    if (btnReset) {
        btnReset.addEventListener('click', () => {
            watchFaceConfig.topSlot = 'GREGORIAN_DATE';
            watchFaceConfig.leftSlot = 'BATTERY';
            watchFaceConfig.rightSlot = 'NEXT_PRAYER';
            watchFaceConfig.bottomSlot = 'HIDDEN';
            persistActiveWatchFaceSlots();
            setupComplicationSelects();
            renderLiveWatchFacePreview();
            showToast('تمت استعادة التوزيع الافتراضي للتعقيدات');
        });
    }

    const btnHideAll = document.getElementById('btnHideAllSlots');
    if (btnHideAll) {
        btnHideAll.addEventListener('click', () => {
            watchFaceConfig.topSlot = 'HIDDEN';
            watchFaceConfig.leftSlot = 'HIDDEN';
            watchFaceConfig.rightSlot = 'HIDDEN';
            watchFaceConfig.bottomSlot = 'HIDDEN';
            persistActiveWatchFaceSlots();
            setupComplicationSelects();
            renderLiveWatchFacePreview();
            showToast('تم إخفاء كافة التعقيدات');
        });
    }
}

// Auto-init on tab click or load
document.addEventListener('DOMContentLoaded', () => {
    initWatchFaceStudio();
    
    // Also listen to tab clicks
    document.querySelectorAll('[data-tab-target]').forEach(btn => {
        btn.addEventListener('click', () => {
            if (btn.dataset.tabTarget === 'watchfaces') {
                setTimeout(renderLiveWatchFacePreview, 50);
            }
        });
    });
});


// ════════════════ ENHANCED REALISTIC PRESETS & BOOKMARKS MODULE ════════════════
const REAL_PRESET_CONFIGS = {
    'classic': {
        name: 'الكلاسيكي المتوازن',
        desc: 'الوقت والصلاة والورد اليومي بتناسق كامل',
        tiles: [
            { id: 'clock_big', colorHex: '#7C3AED', colSpan: 6, rowIndex: 0, fontSize: 22, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'prayer_countdown', colorHex: '#10B981', colSpan: 6, rowIndex: 0, fontSize: 13, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'folder_islamic', colorHex: '#0284C7', colSpan: 4, rowIndex: 1, fontSize: 13, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'quran_resume', colorHex: '#0E7490', colSpan: 8, rowIndex: 1, fontSize: 13, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'locations', colorHex: '#F59E0B', colSpan: 6, rowIndex: 2, fontSize: 13, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'settings', colorHex: '#334155', colSpan: 6, rowIndex: 2, fontSize: 13, displayStyle: 'text', fontColorHex: '#ffffff' }
        ]
    },
    'prayer': {
        name: 'الصلاة أولاً (العد التنازلي والمواقيت)',
        desc: 'تركيز فائق على الصلاة القادمة والشريط الكامل للصلوات',
        tiles: [
            { id: 'prayer_countdown', colorHex: '#10B981', colSpan: 12, rowIndex: 0, fontSize: 18, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'prayer_strip_5', colorHex: '#047857', colSpan: 12, rowIndex: 1, fontSize: 12, displayStyle: 'prayer_strip_5', fontColorHex: '#ffffff' },
            { id: 'clock_big', colorHex: '#6366F1', colSpan: 6, rowIndex: 2, fontSize: 16, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'qibla', colorHex: '#D97706', colSpan: 6, rowIndex: 2, fontSize: 13, displayStyle: 'text', fontColorHex: '#ffffff' }
        ]
    },
    'quran': {
        name: 'الورد القرآني والعبادة',
        desc: 'استكمال القراءة مباشرة مع المسبحة والقبلة',
        tiles: [
            { id: 'quran_resume', colorHex: '#0E7490', colSpan: 12, rowIndex: 0, fontSize: 16, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'tasbih', colorHex: '#D97706', colSpan: 6, rowIndex: 1, fontSize: 13, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'folder_islamic', colorHex: '#0284C7', colSpan: 6, rowIndex: 1, fontSize: 13, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'clock_big', colorHex: '#475569', colSpan: 6, rowIndex: 2, fontSize: 14, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'prayer_countdown', colorHex: '#10B981', colSpan: 6, rowIndex: 2, fontSize: 12, displayStyle: 'text', fontColorHex: '#ffffff' }
        ]
    },
    'minimal': {
        name: 'الحد الأدنى النقي (ساعة وصلاة)',
        desc: 'شاشة صافية بأكبر حجم للأرقام بدون تشتيت',
        tiles: [
            { id: 'clock_big', colorHex: '#3B82F6', colSpan: 12, rowIndex: 0, fontSize: 26, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'prayer_countdown', colorHex: '#10B981', colSpan: 12, rowIndex: 1, fontSize: 15, displayStyle: 'text', fontColorHex: '#ffffff' }
        ]
    },
    'day': {
        name: 'يومي الشامل والمتوازن',
        desc: 'جميع أدواتك ومواقيتك وقرآنك في شاشة واحدة',
        tiles: [
            { id: 'clock_big', colorHex: '#8B5CF6', colSpan: 6, rowIndex: 0, fontSize: 20, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'prayer_countdown', colorHex: '#10B981', colSpan: 6, rowIndex: 0, fontSize: 13, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'quran_resume', colorHex: '#0E7490', colSpan: 6, rowIndex: 1, fontSize: 13, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'folder_islamic', colorHex: '#0284C7', colSpan: 6, rowIndex: 1, fontSize: 13, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'folder_tools', colorHex: '#EA580C', colSpan: 4, rowIndex: 2, fontSize: 12, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'locations', colorHex: '#F59E0B', colSpan: 4, rowIndex: 2, fontSize: 12, displayStyle: 'text', fontColorHex: '#ffffff' },
            { id: 'settings', colorHex: '#334155', colSpan: 4, rowIndex: 2, fontSize: 12, displayStyle: 'text', fontColorHex: '#ffffff' }
        ]
    }
};

function renderPresetsGallery() {
    const gallery = document.querySelector('.preset-gallery');
    if (!gallery) return;

    gallery.innerHTML = Object.keys(REAL_PRESET_CONFIGS).map(key => {
        const p = REAL_PRESET_CONFIGS[key];
        // Generate actual mini-tile preview
        const tilesHtml = p.tiles.map(t => {
            const label = getTileDisplayName(t.id);
            return `<div class="preset-mini-tile" style="grid-column: span ${t.colSpan}; background: ${t.colorHex};">
                ${label}
            </div>`;
        }).join('');

        return `
            <article class="preset-card" data-preset-key="${key}">
                <div class="preset-preview-grid">
                    ${tilesHtml}
                </div>
                <div>
                    <h3>${p.name}</h3>
                    <p>${p.desc}</p>
                </div>
                <button class="button full-width primary" onclick="applyPresetByKey('${key}')">استخدام هذا القالب</button>
            </article>
        `;
    }).join('');
}

function getTileDisplayName(tileId) {
    switch (tileId) {
        case 'clock_big': return '12:45';
        case 'prayer_countdown': return 'المغرب 01:24';
        case 'prayer_strip_5': return '▤ الصلوات 5';
        case 'quran_resume': return '📖 الكهف: 18';
        case 'folder_islamic': return '📁 إسلاميات';
        case 'folder_tools': return '📁 أدوات';
        case 'qibla': return '🕋 القبلة';
        case 'tasbih': return '📿 المسبحة';
        case 'locations': return '⌖ المواقع';
        case 'settings': return '⚙ الإعدادات';
        default: return tileId;
    }
}

function applyPresetByKey(key) {
    const preset = REAL_PRESET_CONFIGS[key];
    if (!preset) return;
    pushHistory();
    tileConfig.tiles = JSON.parse(JSON.stringify(preset.tiles));
    tileConfig.version = Date.now();
    selectedIndices.clear();
    primarySelectedIdx = 0;
    selectedIndices.add(0);
    validateAndPackGrid();
    renderCanvas();
    updateEditor();
    saveLocalDraft();
    showToast(`تم تطبيق قالب «${preset.name}»`);
    
    // Switch to tiles tab
    const tilesTab = document.querySelector('[data-tab-target="tiles"]');
    if (tilesTab) tilesTab.click();
}

// ════════════════ MOBILE QUICK ACTIONS BAR HANDLERS ════════════════
function initMobileQuickActions() {
    const updateLabel = () => {
        const label = document.getElementById('mobileSelectedTileLabel');
        if (!label) return;
        const tile = tileConfig.tiles[primarySelectedIdx];
        if (tile) {
            label.textContent = `العنصر: ${getTileDisplayName(tile.id)} (الصف ${tile.rowIndex + 1} · عرض ${tile.colSpan}/12)`;
        } else {
            label.textContent = 'انقر على أي بلاطة لتحديدها وتعديلها';
        }
    };

    document.getElementById('mBtnMoveLeft')?.addEventListener('click', () => {
        const tile = tileConfig.tiles[primarySelectedIdx];
        if (!tile) return;
        pushHistory();
        // Move within row
        const sameRow = tileConfig.tiles.filter(t => t.rowIndex === tile.rowIndex);
        const idxInRow = sameRow.indexOf(tile);
        if (idxInRow > 0) {
            const prev = sameRow[idxInRow - 1];
            const i1 = tileConfig.tiles.indexOf(tile);
            const i2 = tileConfig.tiles.indexOf(prev);
            const temp = tileConfig.tiles[i1];
            tileConfig.tiles[i1] = tileConfig.tiles[i2];
            tileConfig.tiles[i2] = temp;
        }
        validateAndPackGrid();
        renderCanvas();
        updateLabel();
    });

    document.getElementById('mBtnMoveUp')?.addEventListener('click', () => {
        const tile = tileConfig.tiles[primarySelectedIdx];
        if (!tile || tile.rowIndex <= 0) return;
        pushHistory();
        tile.rowIndex = Math.max(0, tile.rowIndex - 1);
        validateAndPackGrid();
        renderCanvas();
        updateLabel();
    });

    document.getElementById('mBtnMoveDown')?.addEventListener('click', () => {
        const tile = tileConfig.tiles[primarySelectedIdx];
        if (!tile) return;
        pushHistory();
        tile.rowIndex = tile.rowIndex + 1;
        validateAndPackGrid();
        renderCanvas();
        updateLabel();
    });

    document.querySelectorAll('[data-mobile-span]').forEach(btn => {
        btn.addEventListener('click', () => {
            const span = parseInt(btn.dataset.mobileSpan, 10);
            const tile = tileConfig.tiles[primarySelectedIdx];
            if (!tile) return;
            pushHistory();
            tile.colSpan = span;
            validateAndPackGrid();
            renderCanvas();
            updateLabel();
        });
    });

    document.getElementById('mBtnDeleteTile')?.addEventListener('click', () => {
        if (primarySelectedIdx >= 0 && tileConfig.tiles.length > 1) {
            pushHistory();
            tileConfig.tiles.splice(primarySelectedIdx, 1);
            primarySelectedIdx = Math.max(0, primarySelectedIdx - 1);
            selectedIndices.clear();
            selectedIndices.add(primarySelectedIdx);
            validateAndPackGrid();
            renderCanvas();
            updateLabel();
            showToast('تم حذف العنصر');
        }
    });
}

// ════════════════ BOOKMARKS: ONE STORE ════════════════
// There used to be a second bookmark module here with its own array, its own
// localStorage key and its own deleteBookmark(). Both rendered into
// #bookmarksList and both defined the same function names, so the list you saw
// and the list that was synced to the watch were different lists -- adding,
// renaming or deleting never reached the watch. The manager above
// (quran_bookmarks) is the only store now.
function initBookmarksUI() {
    renderBookmarks();

    // Bookmarks are made from a verse you found, not from a surah number and an
    // ayah number typed by hand. The button sends you to the search box; each
    // result carries its own "add bookmark" action.
    const btnOpen = document.getElementById('btnOpenAddBookmarkModal');
    btnOpen?.addEventListener('click', () => {
        const search = document.getElementById('quranSearchInput');
        if (!search) return;
        search.scrollIntoView({ behavior: 'smooth', block: 'center' });
        search.focus();
        showToast('ابحث عن الآية، ثم اضغط «إضافة علامة» بجانبها');
    });
}

// Auto Initialize New Modules
document.addEventListener('DOMContentLoaded', () => {
    renderPresetsGallery();
    initMobileQuickActions();
    initBookmarksUI();
});


// ════════════════ BACKUP & RESTORE MODULE ════════════════
function exportBackupJson() {
    try {
        const backupData = {
            version: Date.now(),
            app: 'Quran Watch 8 Hub',
            timestamp: new Date().toISOString(),
            tileConfig: tileConfig,
            watchFaceConfig: watchFaceConfig,
            watchSettings: watchSettings,
            userBookmarks: getBookmarks(),
            customPresets: typeof customPresets !== 'undefined' ? customPresets : {}
        };

        const jsonStr = JSON.stringify(backupData, null, 2);
        const blob = new Blob([jsonStr], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const dateStr = new Date().toISOString().split('T')[0];
        a.href = url;
        a.download = `quran-watch8-backup-${dateStr}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        showToast('تم تنزيل النسخة الاحتياطية بنجاح 📥');
    } catch (e) {
        console.error('Backup export failed:', e);
        showToast('حدث خطأ أثناء تنزيل النسخة الاحتياطية');
    }
}

function triggerImportBackup() {
    const fileInput = document.getElementById('backupFileInput');
    if (fileInput) {
        fileInput.value = '';
        fileInput.click();
    }
}

function handleBackupFileSelected(event) {
    const file = event.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
        try {
            const data = JSON.parse(e.target.result);
            if (!data || typeof data !== 'object') throw new Error('Invalid JSON structure');

            if (data.tileConfig?.tiles) {
                tileConfig = data.tileConfig;
                localStorage.setItem('quran_watch_tiles', JSON.stringify(tileConfig));
            }
            if (data.watchFaceConfig) {
                watchFaceConfig = { ...watchFaceConfig, ...data.watchFaceConfig };
                localStorage.setItem('quran_watch_wf_config', JSON.stringify(watchFaceConfig));
            }
            if (data.watchSettings) {
                watchSettings = { ...watchSettings, ...data.watchSettings };
                localStorage.setItem('quran_watch_settings', JSON.stringify(watchSettings));
            }
            const restoredBookmarks = data.userBookmarks || data.bookmarks;
            if (Array.isArray(restoredBookmarks)) {
                localStorage.setItem('quran_bookmarks', JSON.stringify(restoredBookmarks));
            }

            // Re-render everything
            selectedIndices.clear();
            primarySelectedIdx = 0;
            selectedIndices.add(0);
            validateAndPackGrid();
            renderCanvas();
            updateEditor();
            renderWatchFaceModelCards();
            setupComplicationSelects();
            renderLiveWatchFacePreview();
            renderBookmarks();
            renderPresetsGallery();

            showToast('✓ تم استيراد واستعادة النسخة الاحتياطية بنجاح!');
        } catch (err) {
            console.error('Import error:', err);
            alert('تعذر استيراد الملف: الرجاء التأكد من اختيار ملف نسخة احتياطية صالح (JSON).');
        }
    };
    reader.readAsText(file);
}

// ════════════════ MOBILE DRAWER TOGGLE ════════════════
// Collapse the sidebar to icons so the designer gets the width back.
// The choice is remembered, because it is a workspace preference, not a mode.
const SIDEBAR_COLLAPSED_KEY = 'quran_watch_sidebar_collapsed';
function initSidebarCollapse() {
    const shell = document.querySelector('.app-shell');
    const btn = document.getElementById('btnCollapseSidebar');
    if (!shell || !btn) return;

    const apply = (collapsed) => {
        shell.classList.toggle('sidebar-collapsed', collapsed);
        btn.textContent = collapsed ? '⟩' : '⟨';
        btn.setAttribute('aria-expanded', String(!collapsed));
        btn.title = collapsed ? 'توسيع الشريط الجانبي' : 'طيّ الشريط الجانبي';
    };

    let collapsed = false;
    try { collapsed = localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1'; } catch (_) {}
    apply(collapsed);

    btn.addEventListener('click', () => {
        collapsed = !shell.classList.contains('sidebar-collapsed');
        apply(collapsed);
        try { localStorage.setItem(SIDEBAR_COLLAPSED_KEY, collapsed ? '1' : '0'); } catch (_) {}
    });
}

function initMobileDrawer() {
    const menuBtn = document.getElementById('mobileMenuBtn');
    const sidebar = document.querySelector('.finder-sidebar');
    const backdrop = document.getElementById('sidebarBackdrop');

    const toggleDrawer = (open) => {
        if (!sidebar) return;
        const isOpen = open !== undefined ? open : !sidebar.classList.contains('mobile-open');
        sidebar.classList.toggle('mobile-open', isOpen);
        if (backdrop) backdrop.classList.toggle('active', isOpen);
    };

    menuBtn?.addEventListener('click', () => toggleDrawer());
    backdrop?.addEventListener('click', () => toggleDrawer(false));

    // Close drawer when any tab button is clicked
    document.querySelectorAll('.finder-sidebar .nav-item').forEach(btn => {
        btn.addEventListener('click', () => {
            toggleDrawer(false);
        });
    });
}

// Attach Backup & Mobile Drawer Handlers on DOM ready
document.addEventListener('DOMContentLoaded', () => {
    initMobileDrawer();
    initSidebarCollapse();

    document.getElementById('btnExportBackupToolbar')?.addEventListener('click', exportBackupJson);
    document.getElementById('btnImportBackupToolbar')?.addEventListener('click', triggerImportBackup);
    document.getElementById('btnExportBackupSettings')?.addEventListener('click', exportBackupJson);
    document.getElementById('btnImportBackupSettings')?.addEventListener('click', triggerImportBackup);
    document.getElementById('backupFileInput')?.addEventListener('change', handleBackupFileSelected);
});

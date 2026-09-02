/** Six implementation proposals for pwa-web/app.js::WATCH_FACE_MODELS. */
const PROPOSED_WATCH_FACE_MODELS = Object.freeze([
  {
    id: 'FAJR_MIHRAB',
    name: 'محراب الفجر',
    desc: 'محراب داخلي يحتضن الوقت والصلاة القادمة والمواقيت والقراءة الأخيرة.',
    category: 'islamic',
    renderer: 'renderFajrMihrabDial',
    defaultSlots: ['HIJRI_DATE', 'SUNRISE_SUNSET', 'NEXT_PRAYER', 'QURAN_RESUME'],
    image: 'design-proposals/watchfaces-v2/images/01-fajr-mihrab.png'
  },
  {
    id: 'DHIKR_PULSE',
    name: 'نبض الذكر',
    desc: 'حلقة تسبيح مركزية مع الصلاة والبطارية والطقس الحقيقي والعودة إلى القرآن.',
    category: 'islamic',
    renderer: 'renderDhikrPulseDial',
    defaultSlots: ['NEXT_PRAYER', 'BATTERY', 'WEATHER', 'QURAN_RESUME'],
    image: 'design-proposals/watchfaces-v2/images/02-dhikr-pulse.png'
  },
  {
    id: 'QIBLA_SERENITY',
    name: 'بوصلة السكينة',
    desc: 'سهم قبلة مركزي واضح بلا أرقام أو تدريجات محيطية.',
    category: 'islamic',
    renderer: 'renderQiblaSerenityDial',
    defaultSlots: ['GREGORIAN_DATE', 'WEATHER', 'BATTERY', 'NEXT_PRAYER'],
    image: 'design-proposals/watchfaces-v2/images/03-qibla-serenity.png'
  },
  {
    id: 'QURAN_GALLERY',
    name: 'رِواق الآية',
    desc: 'واجهة قراءة تجعل اسم السورة ورقم الآية والمتن وحدة واحدة قابلة للالتفاف.',
    category: 'islamic',
    renderer: 'renderQuranGalleryDial',
    defaultSlots: ['HIJRI_DATE', 'HIDDEN', 'HIDDEN', 'NEXT_PRAYER'],
    image: 'design-proposals/watchfaces-v2/images/04-quran-gallery.png'
  },
  {
    id: 'DAILY_ORBITS',
    name: 'مدارات اليوم',
    desc: 'أقواس داخلية قصيرة للبطارية والصلاة وضوء النهار والتسبيح حول ساعة بيضوية كبيرة.',
    category: 'modern',
    renderer: 'renderDailyOrbitsDial',
    defaultSlots: ['BATTERY', 'SUNRISE_SUNSET', 'TASBIH', 'NEXT_PRAYER'],
    image: 'design-proposals/watchfaces-v2/images/05-daily-orbits.png'
  },
  {
    id: 'BELIEVER_MOSAIC',
    name: 'فسيفساء المؤمن',
    desc: 'بلاطات زجاجية بيضوية ومربعة للمعلومات اليومية والعبادية.',
    category: 'modern',
    renderer: 'renderBelieverMosaicDial',
    defaultSlots: ['WEATHER', 'QIBLA', 'BATTERY', 'QURAN_RESUME'],
    image: 'design-proposals/watchfaces-v2/images/06-believer-mosaic.png'
  }
]);

const PHYSICAL_BEZEL_RULE = Object.freeze({
  safeMarginPercent: 12,
  forbidPerimeterHourNumbers: true,
  forbidPerimeterDegreeNumbers: true,
  forbidPerimeterCardinalLetters: true,
  forbidPerimeterTicks: true
});

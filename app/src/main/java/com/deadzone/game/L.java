package com.deadzone.game;

import java.util.HashMap;

final class L {
    static final String[] CODES = {"en", "fa", "ar", "ru"};
    static final String[] NAMES = {"English", "فارسی", "العربية", "Русский"};

    private static final HashMap<String, String[]> T = new HashMap<>();
    private static int idx;

    private L() {
    }

    static void set(String lang) {
        idx = 0;
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(lang)) idx = i;
    }

    static boolean rtl() {
        return idx == 1 || idx == 2;
    }

    static String t(String key) {
        String[] a = T.get(key);
        return a == null ? key : a[idx];
    }

    private static void add(String k, String en, String fa, String ar, String ru) {
        T.put(k, new String[]{en, fa, ar, ru});
    }

    static {
        add("intro",
            "It is night and the dead are everywhere. Stay alive as long as you can.",
            "شب است و مرده‌های متحرک همه‌جا هستند. تا جایی که می‌توانی زنده بمان.",
            "إنه الليل والموتى يتحركون في كل مكان. ابقَ حيًّا قدر ما تستطيع.",
            "Ночь, и мертвецы повсюду. Продержись как можно дольше.");
        add("help",
            "Left stick moves, drag the right side to aim. %1$s shoots, %2$s jumps, %3$s dives (even mid-jump) and %4$s toggles sprint. Headshots do triple damage.",
            "جوی‌استیک چپ برای حرکت، کشیدن انگشت روی سمت راست برای نشانه‌گیری. «%1$s» شلیک، «%2$s» پرش، «%3$s» شیرجه (حتی وسط پرش) و «%4$s» دویدن. شلیک به سر ۳ برابر آسیب دارد.",
            "العصا اليسرى للحركة واسحب الجانب الأيمن للتصويب. «%1$s» إطلاق النار، «%2$s» قفز، «%3$s» غوص (حتى أثناء القفز)، «%4$s» للركض. الإصابة في الرأس تسبب ضررًا ثلاثيًا.",
            "Левый стик — движение, перетаскивание справа — прицеливание. «%1$s» — стрельба, «%2$s» — прыжок, «%3$s» — нырок (даже в прыжке), «%4$s» — бег. Выстрел в голову наносит тройной урон.");
        add("multi",
            "Multiplayer: press HOST and send the 4-letter code to your friends; they type it and press JOIN (up to 4 players).",
            "چندنفره: HOST را بزن و کد ۴ حرفی را برای دوستانت بفرست؛ آن‌ها کد را وارد و JOIN می‌زنند (تا ۴ نفر).",
            "اللعب الجماعي: اضغط HOST وأرسل الرمز المكوّن من 4 أحرف لأصدقائك؛ يكتبونه ويضغطون JOIN (حتى 4 لاعبين).",
            "Мультиплеер: нажми HOST и отправь друзьям код из 4 букв; они вводят его и жмут JOIN (до 4 игроков).");
        add("start",
            "SINGLEPLAYER",
            "تک‌نفره",
            "لاعب واحد",
            "ОДИНОЧНАЯ ИГРА");
        add("s_mb",
            "Motion blur",
            "محو شدگی حرکت",
            "تمويه الحركة",
            "Размытие движения");
        add("multi",
            "Multiplayer: press HOST and send the 4-letter code to your friends; they type it and press JOIN (up to 4 players).",
            "چندنفره: HOST را بزن و کد ۴ حرفی را برای دوستانت بفرست؛ آن‌ها کد را وارد و JOIN می‌زنند (تا ۴ نفر).",
            "اللعب الجماعي: اضغط HOST وأرسل الرمز المكوّن من 4 أحرف لأصدقائك؛ يكتبونه ويضغطون JOIN (حتى 4 لاعبين).",
            "Мультиплеер: нажми HOST и отправь друзьям код из 4 букв; они вводят его и жмут JOIN (до 4 игроков).");
        add("start",
            "SINGLEPLAYER",
            "تک‌نفره",
            "لاعب واحد",
            "ОДИНОЧНАЯ ИГРА");
        add("s_mb",
            "Motion blur",
            "محو شدگی حرکت",
            "تمويه الحركة",
            "Размытие движения");
        add("multi",
            "MULTIPLAYER",
            "چندنفره",
            "متعدد اللاعبين",
            "МУЛЬТИПЛЕЕР");
        add("exit",
            "EXIT",
            "خروج",
            "خروج",
            "ВЫХОД");
        add("p_name",
            "Your name",
            "نام شما",
            "اسمك",
            "Ваше имя");
        add("w_createw",
            "CREATE WORLD",
            "ساخت جهان",
            "إنشاء عالم",
            "СОЗДАТЬ МИР");
        add("w_rename",
            "RENAME",
            "تغییر نام",
            "إعادة تسمية",
            "ПЕРЕИМЕНОВАТЬ");
        add("w_name",
            "World name",
            "نام جهان",
            "اسم العالم",
            "Название мира");
        add("s_srdyn",
            "Dynamic resolution",
            "رزولوشن پویا",
            "دقة ديناميكية",
            "Динамическое разрешение");
        add("s_rd",
            "Render distance",
            "فاصله رندر",
            "مسافة العرض",
            "Дальность прорисовки");
        add("s_rd0",
            "Short",
            "کم",
            "قصيرة",
            "Малая");
        add("s_rd1",
            "Normal",
            "متوسط",
            "عادية",
            "Средняя");
        add("s_rd2",
            "Far",
            "زیاد",
            "بعيدة",
            "Большая");
        add("s_rd3",
            "Ultra",
            "اولترا",
            "فائقة",
            "Ультра");
        add("host",
            "HOST ROOM",
            "ساخت اتاق",
            "إنشاء غرفة",
            "СОЗДАТЬ КОМНАТУ");
        add("join",
            "JOIN",
            "ورود",
            "انضمام",
            "ВОЙТИ");
        add("settings",
            "SETTINGS",
            "تنظیمات",
            "الإعدادات",
            "НАСТРОЙКИ");
        add("resume",
            "RESUME",
            "ادامه",
            "متابعة",
            "ПРОДОЛЖИТЬ");
        add("code",
            "CODE",
            "کد",
            "الرمز",
            "КОД");
        add("restart",
            "RESTART",
            "شروع دوباره",
            "إعادة",
            "ЗАНОВО");
        add("menu",
            "MENU",
            "منو",
            "القائمة",
            "МЕНЮ");
        add("died",
            "YOU DIED",
            "مُردی",
            "لقد متّ",
            "ВЫ ПОГИБЛИ");
        add("s_title",
            "SETTINGS",
            "تنظیمات",
            "الإعدادات",
            "НАСТРОЙКИ");
        add("s_native",
            "Super resolution",
            "رزولوشن بالا",
            "دقة عالية",
            "Суперразрешение");
        add("w_title",
            "WORLDS",
            "جهان‌ها",
            "العوالم",
            "МИРЫ");
        add("w_seed",
            "Seed (empty = random)",
            "سید (خالی = تصادفی)",
            "البذرة (فارغ = عشوائي)",
            "Сид (пусто = случайный)");
        add("w_create",
            "NEW WORLD",
            "جهان جدید",
            "عالم جديد",
            "НОВЫЙ МИР");
        add("w_play",
            "PLAY",
            "بازی",
            "لعب",
            "ИГРАТЬ");
        add("w_del",
            "DELETE",
            "حذف",
            "حذف",
            "УДАЛИТЬ");
        add("w_empty",
            "No saved worlds yet",
            "هنوز جهانی ذخیره نشده",
            "لا توجد عوالم محفوظة",
            "Сохранённых миров пока нет");
        add("w_info",
            "Seed %s · Wave %d · Kills %d",
            "سید %s · موج %d · کشتار %d",
            "البذرة %s · الموجة %d · القتلى %d",
            "Сид %s · Волна %d · Убийств %d");
        add("s_fps",
            "Show FPS",
            "نمایش FPS",
            "عرض FPS",
            "Показать FPS");
        add("s_ping",
            "Show ping",
            "نمایش پینگ",
            "عرض البينغ",
            "Показать пинг");
        add("s_vsync",
            "VSync",
            "VSync",
            "VSync",
            "VSync");
        add("s_lang",
            "Language",
            "زبان",
            "اللغة",
            "Язык");
        add("s_layout",
            "Customize buttons",
            "شخصی‌سازی دکمه‌ها",
            "تخصيص الأزرار",
            "Настроить кнопки");
        add("s_back",
            "BACK",
            "بازگشت",
            "رجوع",
            "НАЗАД");
        add("e_hint",
            "Drag a button to move it. Tap it to select, then use + / − to resize.",
            "برای جابه‌جایی دکمه را بکش. برای تغییر اندازه آن را انتخاب کن و از + / − استفاده کن.",
            "اسحب الزر لتحريكه. اضغط عليه لتحديده ثم استخدم + / − لتغيير الحجم.",
            "Перетащи кнопку, чтобы переместить. Нажми, чтобы выбрать, затем + / − для размера.");
        add("e_reset",
            "RESET",
            "بازنشانی",
            "إعادة ضبط",
            "СБРОС");
        add("e_done",
            "DONE",
            "تمام",
            "تم",
            "ГОТОВО");
        add("b_fire",
            "FIRE",
            "شلیک",
            "إطلاق",
            "ОГОНЬ");
        add("b_jump",
            "JUMP",
            "پرش",
            "قفز",
            "ПРЫЖОК");
        add("b_dive",
            "DIVE",
            "شیرجه",
            "غوص",
            "НЫРОК");
        add("b_run",
            "RUN",
            "دویدن",
            "ركض",
            "БЕГ");
        add("b_rel",
            "R",
            "R",
            "R",
            "R");
        add("h_wave",
            "WAVE",
            "موج",
            "الموجة",
            "ВОЛНА");
        add("h_kills",
            "KILLS",
            "کشته‌ها",
            "القتلى",
            "УБИТО");
        add("h_rel",
            "RELOADING...",
            "در حال بارگذاری...",
            "جارٍ إعادة التعبئة...",
            "ПЕРЕЗАРЯДКА...");
        add("h_noammo",
            "NO AMMO",
            "مهمات تمام شد",
            "لا ذخيرة",
            "НЕТ ПАТРОНОВ");
        add("h_dead",
            "YOU ARE DEAD - NEXT WAVE",
            "مُردی - منتظر موج بعدی",
            "لقد متّ - انتظر الموجة التالية",
            "ВЫ МЕРТВЫ - ЖДИТЕ СЛЕДУЮЩУЮ ВОЛНУ");
        add("h_room",
            "ROOM",
            "اتاق",
            "الغرفة",
            "КОМНАТА");
        add("h_ping",
            "PING",
            "پینگ",
            "بينغ",
            "ПИНГ");
        add("n_wait",
            "Connecting... if the server was asleep this can take up to a minute",
            "در حال اتصال... اگر سرور خواب بوده تا یک دقیقه صبر کن",
            "جارٍ الاتصال... إذا كان الخادم نائمًا فقد يستغرق ذلك حتى دقيقة",
            "Подключение... если сервер спал, это может занять до минуты");
        add("n_host",
            "Creating room... if the server was asleep this can take up to a minute",
            "در حال ساخت اتاق... اگر سرور خواب بوده تا یک دقیقه صبر کن",
            "جارٍ إنشاء الغرفة... إذا كان الخادم نائمًا فقد يستغرق ذلك حتى دقيقة",
            "Создание комнаты... если сервер спал, это может занять до минуты");
        add("n_timeout",
            "Could not connect (timed out)",
            "اتصال برقرار نشد (مهلت تمام شد)",
            "تعذّر الاتصال (انتهت المهلة)",
            "Не удалось подключиться (время вышло)");
        add("n_fail",
            "Could not connect",
            "اتصال برقرار نشد",
            "تعذّر الاتصال",
            "Не удалось подключиться");
        add("n_noroom",
            "Room not found. Check the code and make sure the host keeps the game open",
            "اتاق پیدا نشد. کد را چک کن و مطمئن شو میزبان بازی را باز دارد",
            "الغرفة غير موجودة. تحقق من الرمز وتأكد أن المضيف أبقى اللعبة مفتوحة",
            "Комната не найдена. Проверь код и убедись, что хост не закрыл игру");
        add("n_full",
            "Room is full",
            "اتاق پر است",
            "الغرفة ممتلئة",
            "Комната заполнена");
        add("n_err",
            "Error: ",
            "خطا: ",
            "خطأ: ",
            "Ошибка: ");
        add("n_lost",
            "Connection lost",
            "اتصال قطع شد",
            "انقطع الاتصال",
            "Соединение потеряно");
        add("n_code",
            "Enter a 4-letter code",
            "کد ۴ حرفی را وارد کن",
            "أدخل رمزًا من 4 أحرف",
            "Введи код из 4 букв");
        add("n_hostlost",
            "Lost connection to the host",
            "اتصال به میزبان قطع شد",
            "انقطع الاتصال بالمضيف",
            "Потеряно соединение с хостом");
        add("b_crawl",
            "CRAWL",
            "خزیدن",
            "زحف",
            "ПОЛЗТИ");
        add("h_void",
            "VOID",
            "خلأ",
            "الفراغ",
            "ПУСТОТА");
        add("b_slide",
            "SLIDE",
            "سُر خوردن",
            "انزلاق",
            "СКОЛЬЖ.");
        add("help2",
            "%1$s slides while you run. After a dive you crawl; crawling with run on is faster. Press %2$s to stand up.",
            "«%1$s» هنگام دویدن سُر می‌خوری. بعد از شیرجه به حالت خزیدن می‌روی؛ خزیدن با دویدن روشن سریع‌تر است. برای بلند شدن «%2$s» را بزن.",
            "«%1$s» للانزلاق أثناء الركض. بعد الغوص تزحف؛ الزحف مع تشغيل الركض أسرع. اضغط «%2$s» للوقوف.",
            "«%1$s» — скольжение на бегу. После нырка ты ползёшь; ползти с включённым бегом быстрее. «%2$s» — встать.");
        add("s_autorun",
            "Auto run",
            "دویدن خودکار",
            "ركض تلقائي",
            "Автобег");
    }
}

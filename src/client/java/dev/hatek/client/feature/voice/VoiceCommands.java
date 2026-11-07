package dev.hatek.client.feature.voice;

import dev.hatek.client.feature.account.Account;
import dev.hatek.client.feature.account.AccountManager;
import dev.hatek.client.module.Module;
import dev.hatek.client.module.ModuleManager;
import dev.hatek.client.module.setting.BoolSetting;
import dev.hatek.client.module.setting.ModeSetting;
import dev.hatek.client.module.setting.MultiSetting;
import dev.hatek.client.module.setting.Setting;
import dev.hatek.client.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VoiceCommands {
    private static final String[] WAKE = {"хатек", "хатэк", "хайтек", "патек", "чатек", "хатик",
            "hatek", "хатека", "хате"};

    private static final String[] ON = {"включи", "включить", "вруби", "врубить", "врубай",
            "запусти", "активируй", "подключи", "on", "enable"};
    private static final String[] OFF = {"выключи", "выключить", "выруби", "вырубить", "вырубай",
            "отключи", "убери", "останови", "off", "disable"};
    private static final String[] TOGGLE = {"переключи", "перекл"};
    private static final String[] SET = {"поставь", "поменяй", "измени", "сделай", "установи",
            "задай", "смени", "выстави"};
    private static final String[] NICK = {"ник", "никнейм", "имя", "аккаунт", "ника", "ником"};

    private static final Map<String, String[]> MODULE_WORDS = new LinkedHashMap<>();

    private static final Map<String, String[]> SETTING_WORDS = new LinkedHashMap<>();

    private static final String[] NUMBERS = {"ноль", "один", "два", "три", "четыре", "пять",
            "шесть", "семь", "восемь", "девять", "десять", "одиннадцать", "двенадцать",
            "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать", "семнадцать",
            "восемнадцать", "девятнадцать", "двадцать"};

    static {
        MODULE_WORDS.put("AttackAura", new String[]{"аура", "ауру", "атака", "килл аура", "киллаура"});
        MODULE_WORDS.put("AutoSprint", new String[]{"спринт", "автоспринт", "бег", "автобег"});
        MODULE_WORDS.put("NoJumpDelay", new String[]{"прыжок", "прыжки", "задержка прыжка"});
        MODULE_WORDS.put("AntiBot", new String[]{"антибот", "бот"});
        MODULE_WORDS.put("Criticals", new String[]{"криты", "крит", "критикалс"});
        MODULE_WORDS.put("AutoTotem", new String[]{"тотем", "автототем"});
        MODULE_WORDS.put("Velocity", new String[]{"велосити", "отбрасывание", "отброс"});
        MODULE_WORDS.put("TriggerBot", new String[]{"триггер", "триггербот"});
        MODULE_WORDS.put("Flight", new String[]{"полет", "флай", "летать"});
        MODULE_WORDS.put("NoSlow", new String[]{"нослоу", "замедление"});
        MODULE_WORDS.put("Step", new String[]{"степ", "шаг"});
        MODULE_WORDS.put("Jesus", new String[]{"иисус", "вода"});
        MODULE_WORDS.put("ESP", new String[]{"есп", "эспи", "обводка", "подсветка"});
        MODULE_WORDS.put("Nametags", new String[]{"неймтеги", "таблички", "ники"});
        MODULE_WORDS.put("FullBright", new String[]{"яркость", "свет", "фулбрайт"});
        MODULE_WORDS.put("NoHurtCam", new String[]{"тряска", "нохертками"});
        MODULE_WORDS.put("Ambience", new String[]{"время", "погода"});
        MODULE_WORDS.put("AutoRespawn", new String[]{"респавн", "автореспавн", "возрождение"});
        MODULE_WORDS.put("FastPlace", new String[]{"фастплейс", "быстрая установка"});
        MODULE_WORDS.put("NoFall", new String[]{"нофолл", "падение"});
        MODULE_WORDS.put("Scaffold", new String[]{"скафолд", "мост", "мостик"});
        MODULE_WORDS.put("AutoTool", new String[]{"автотул", "инструмент"});
        MODULE_WORDS.put("AntiAFK", new String[]{"афк", "антиафк"});
        MODULE_WORDS.put("Watermark", new String[]{"вотермарк", "лого", "логотип"});
        MODULE_WORDS.put("Notifications", new String[]{"уведомления", "оповещения"});
        MODULE_WORDS.put("DiscordRPC", new String[]{"дискорд"});

        SETTING_WORDS.put("Attack Range", new String[]{"радиус", "дальность", "радиус атаки", "дистанция"});
        SETTING_WORDS.put("Detect Range", new String[]{"обнаружение", "детект", "радиус обнаружения"});
        SETTING_WORDS.put("Charge Spread", new String[]{"разброс", "разброс заряда"});
        SETTING_WORDS.put("Keep In Water", new String[]{"вода", "в воде"});
        SETTING_WORDS.put("Targets", new String[]{"цели"});
        SETTING_WORDS.put("Checks", new String[]{"проверки"});
        SETTING_WORDS.put("Extra", new String[]{"экстра", "допы", "дополнительно"});
    }

    private VoiceCommands() {
    }

    public static String prompt() {
        StringBuilder out = new StringBuilder("Хатек. Команды: ");
        out.append(String.join(", ", "включи", "выключи", "переключи", "поставь", "поменяй ник на"));
        out.append(". Модули: ");
        StringBuilder modules = new StringBuilder();
        for (String[] words : MODULE_WORDS.values()) {
            if (modules.length() > 0) {
                modules.append(", ");
            }
            modules.append(words[0]);
        }
        out.append(modules).append(", голос. Настройки: ");
        StringBuilder settings = new StringBuilder();
        for (String[] words : SETTING_WORDS.values()) {
            if (settings.length() > 0) {
                settings.append(", ");
            }
            settings.append(words[0]);
        }
        return out.append(settings).append('.').toString();
    }

    public static String handle(String heard) {
        String phrase = normalise(heard);
        if (phrase.isEmpty()) {
            return null;
        }
        String rest = afterWake(phrase);
        if (rest == null) {
            return null;
        }
        if (rest.isBlank()) {
            return "Слушаю";
        }

        List<String> words = new ArrayList<>(List.of(rest.split(" ")));
        String verb = words.get(0);

        double onScore = best(verb, ON);
        double offScore = best(verb, OFF);
        double toggleScore = best(verb, TOGGLE);
        double setScore = best(verb, SET);
        double top = Math.max(Math.max(onScore, offScore), Math.max(toggleScore, setScore));

        if (top < 0.75) {
            return "Не понял: " + rest;
        }
        if (setScore == top) {
            return containsAny(words, NICK) ? nickname(words) : setting(words);
        }
        if (offScore == top) {
            return power(words, Boolean.FALSE);
        }
        if (onScore == top) {
            return power(words, Boolean.TRUE);
        }
        return power(words, null);
    }

    private static double best(String word, String[] options) {
        double best = 0.0;
        for (String option : options) {
            best = Math.max(best, similarity(option, word));
        }
        return best;
    }

    private static String power(List<String> words, Boolean on) {
        words.remove(0);
        Module module = findModule(String.join(" ", words));
        if (module == null) {
            return "Не нашёл модуль: " + String.join(" ", words);
        }
        boolean wanted = on == null ? !module.isEnabled() : on;
        if (module.isEnabled() == wanted) {
            return module.name() + " уже " + (wanted ? "включён" : "выключен");
        }
        module.setEnabled(wanted);
        return module.name() + (wanted ? " включён" : " выключен");
    }

    private static String setting(List<String> words) {
        words.remove(0);
        words.remove("у");

        Module module = findModuleIn(words);
        if (module == null) {
            return "Не нашёл модуль в: " + String.join(" ", words);
        }
        Setting setting = findSetting(module, words);
        if (setting == null) {
            return "Не нашёл настройку у " + module.name();
        }

        String tail = String.join(" ", words);
        if (setting instanceof SliderSetting slider) {
            Double value = number(words);
            if (value == null) {
                return "Не расслышал число для " + setting.name();
            }
            slider.value(value);
            return module.name() + ": " + setting.name() + " = " + trim(slider.value());
        }
        if (setting instanceof BoolSetting bool) {
            boolean wanted = !containsAny(List.of(tail.split(" ")), OFF);
            bool.value(wanted);
            return module.name() + ": " + setting.name() + " " + (wanted ? "вкл" : "выкл");
        }
        if (setting instanceof ModeSetting mode) {
            String best = null;
            double bestScore = 0.0;
            for (String option : mode.options()) {
                double score = similarity(normalise(option), tail);
                if (score > bestScore) {
                    bestScore = score;
                    best = option;
                }
            }
            if (best == null || bestScore < 0.45) {
                return "Не понял режим для " + setting.name();
            }
            mode.value(best);
            return module.name() + ": " + setting.name() + " = " + best;
        }
        if (setting instanceof MultiSetting) {
            return setting.name() + " голосом не меняется - это список галок";
        }
        return "Настройка " + setting.name() + " голосом не поддерживается";
    }

    private static String nickname(List<String> words) {
        if (!AccountManager.canSwitch()) {
            return "Ник меняется только вне игры";
        }
        int at = indexOfAny(words, NICK);
        List<String> tail = new ArrayList<>(words.subList(Math.min(at + 1, words.size()), words.size()));
        tail.remove("на");
        if (tail.isEmpty()) {
            return "На какой ник?";
        }
        String spoken = String.join(" ", tail);

        Account best = null;
        double bestScore = 0.0;
        for (Account account : AccountManager.all()) {
            double score = similarity(normalise(account.name()), spoken);
            double translit = similarity(normalise(account.name()), translit(spoken));
            score = Math.max(score, translit);
            if (score > bestScore) {
                bestScore = score;
                best = account;
            }
        }
        if (best != null && bestScore >= 0.5) {
            return AccountManager.login(best) ? "Ник: " + best.name() : "Не удалось сменить ник";
        }

        String made = translit(spoken.replace(" ", ""));
        if (!Account.valid(made)) {
            return "Ник \"" + spoken + "\" не подходит - добавь его в Accounts руками";
        }
        Account account = AccountManager.add(made);
        return account != null && AccountManager.login(account)
                ? "Ник: " + account.name()
                : "Не удалось сменить ник";
    }

    private static Module findModule(String phrase) {
        Module best = null;
        double bestScore = 0.0;
        String[] words = phrase.split(" ");
        for (Module module : ModuleManager.all()) {
            double score = moduleScore(module, phrase);
            for (String word : words) {
                score = Math.max(score, moduleScore(module, word));
            }
            if (score > bestScore) {
                bestScore = score;
                best = module;
            }
        }
        return bestScore >= 0.5 ? best : null;
    }

    private static Module findModuleIn(List<String> words) {
        Module best = null;
        double bestScore = 0.0;
        int bestAt = -1;
        for (int i = 0; i < words.size(); i++) {
            for (Module module : ModuleManager.all()) {
                double score = moduleScore(module, words.get(i));
                if (score > bestScore) {
                    bestScore = score;
                    best = module;
                    bestAt = i;
                }
            }
        }
        if (bestScore < 0.5) {
            return null;
        }
        words.remove(bestAt);
        return best;
    }

    private static double moduleScore(Module module, String phrase) {
        double best = similarity(normalise(module.name()), phrase);
        String[] words = MODULE_WORDS.get(module.name());
        if (words != null) {
            for (String word : words) {
                best = Math.max(best, similarity(word, phrase));
            }
        }
        return best;
    }

    private static Setting findSetting(Module module, List<String> words) {
        Setting best = null;
        double bestScore = 0.0;
        for (Setting setting : module.settings()) {
            String[] aliases = SETTING_WORDS.get(setting.name());
            for (String word : words) {
                double score = similarity(normalise(setting.name()), word);
                if (aliases != null) {
                    for (String alias : aliases) {
                        score = Math.max(score, similarity(alias, word));
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = setting;
                }
            }
        }
        return bestScore >= 0.5 ? best : null;
    }

    private static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        if (b.contains(a) || a.contains(b)) {
            double ratio = (double) Math.min(a.length(), b.length())
                    / Math.max(a.length(), b.length());
            return 0.6 + 0.35 * ratio;
        }
        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() <= b.length() ? b : a;
        int prefix = 0;
        while (prefix < shorter.length() && shorter.charAt(prefix) == longer.charAt(prefix)) {
            prefix++;
        }
        if (prefix >= 4 && prefix >= shorter.length() - 1) {
            return 0.9;
        }
        int distance = levenshtein(a, b);
        return 1.0 - (double) distance / longer.length();
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static volatile boolean wakeRequired = true;

    public static void wakeRequired(boolean value) {
        wakeRequired = value;
    }

    private static String afterWake(String phrase) {
        if (!wakeRequired) {
            return phrase;
        }
        String[] words = phrase.split(" ");
        int limit = Math.min(3, words.length);
        for (int i = 0; i < limit; i++) {
            for (String wake : WAKE) {
                if (similarity(wake, words[i]) >= 0.66) {
                    StringBuilder rest = new StringBuilder();
                    for (int j = i + 1; j < words.length; j++) {
                        rest.append(words[j]).append(' ');
                    }
                    return rest.toString().trim();
                }
            }
        }
        return null;
    }

    private static Double number(List<String> words) {
        Double whole = null;
        Double fraction = null;
        for (String word : words) {
            Double value = oneNumber(word);
            if (value == null) {
                continue;
            }
            if (whole == null) {
                whole = value;
            } else if (fraction == null) {
                fraction = value;
            }
        }
        if (whole == null) {
            return null;
        }
        if (fraction == null) {
            return whole;
        }
        return whole + fraction / (fraction >= 10.0 ? 100.0 : 10.0);
    }

    private static Double oneNumber(String word) {
        String cleaned = word.replace(',', '.');
        try {
            return Double.valueOf(cleaned);
        } catch (NumberFormatException ignored) {
        }
        for (int i = 0; i < NUMBERS.length; i++) {
            if (similarity(NUMBERS[i], word) >= 0.8) {
                return (double) i;
            }
        }
        return null;
    }

    private static final String CYRILLIC = "абвгдежзийклмнопрстуфхцчшщъыьэюя";
    private static final String[] LATIN = {"a", "b", "v", "g", "d", "e", "zh", "z", "i", "y", "k",
            "l", "m", "n", "o", "p", "r", "s", "t", "u", "f", "h", "c", "ch", "sh", "sch", "", "y",
            "", "e", "yu", "ya"};

    private static String translit(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            int at = CYRILLIC.indexOf(c);
            if (at >= 0) {
                out.append(LATIN[at]);
            } else if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String normalise(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == 'ё') {
                out.append('е');
            } else if (Character.isLetterOrDigit(c)) {
                out.append(c);
            } else if ((c == '.' || c == ',') && decimalPoint(text, out.length())) {
                out.append('.');
            } else {
                out.append(' ');
            }
        }
        return out.toString().replaceAll(" +", " ").trim();
    }

    private static boolean decimalPoint(String text, int at) {
        return at > 0 && at + 1 < text.length()
                && Character.isDigit(text.charAt(at - 1))
                && Character.isDigit(text.charAt(at + 1));
    }

    private static boolean containsAny(List<String> words, String[] options) {
        return indexOfAny(words, options) >= 0;
    }

    private static int indexOfAny(List<String> words, String[] options) {
        for (int i = 0; i < words.size(); i++) {
            for (String option : options) {
                if (similarity(option, words.get(i)) >= 0.8) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public static boolean inMenu() {
        return Minecraft.getInstance().level == null;
    }
}

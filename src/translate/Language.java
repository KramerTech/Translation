package translate;

/**
 * Language - an enum of language codes supported by the Yandex API
 */
public enum Language {
	AFRIKAANS("af"),
	ALBANIAN("sq"),
	ARABIC("ar"),
	AZERBAIJANI("az"),
	BASQUE("eu"),
	BENGALI("bn"),
	BELARUSIAN("be"),
	BULGARIAN("bg"),
	CATALAN("ca"),
	CHINESE_SIMPLIFIED("zh-CN"),
	CHINESE_TRADITIONAL("zh-TW"),
	CROATIAN("hr"),
	CZECH("cs"),
	DANISH("da"),
	DUTCH("nl"),
	ENGLISH("en"),
	ESPERANTO("eo"),
	ESTONIAN("et"),
	FILIPINO("tl"),
	FINNISH("fi"),
	FRENCH("fr"),
	GALICIAN("gl"),
	GEORGIAN("ka"),
	GERMAN("de"),
	GREEK("el"),
	GUJARATI("gu"),
	HAITIAN_CREOLE("ht"),
	HEBREW("iw"),
	HINDI("hi"),
	HUNGARIAN("hu"),
	ICELANDIC("is"),
	INDONESIAN("id"),
	IRISH("ga"),
	ITALIAN("it"),
	JAPANESE("ja"),
	KANNADA("kn"),
	KOREAN("ko"),
	LATIN("la"),
	LATVIAN("lv"),
	LITHUANIAN("lt"),
	MACEDONIAN("mk"),
	MALAY("ms"),
	MALTESE("mt"),
	NORWEGIAN("no"),
	PERSIAN("fa"),
	POLISH("pl"),
	PORTUGUESE("pt"),
	ROMANIAN("ro"),
	RUSSIAN("ru"),
	SERBIAN("sr"),
	SLOVAK("sk"),
	SLOVENIAN("sl"),
	SPANISH("es"),
	SWAHILI("sw"),
	SWEDISH("sv"),
	TAMIL("ta"),
	TELUGU("te"),
	THAI("th"),
	TURKISH("tr"),
	UKRAINIAN("uk"),
	URDU("ur"),
	VIETNAMESE("vi"),
	WELSH("cy"),
	YIDDISH("yi");


    private final String language;
    
    
    private Language(final String pLanguage) {
        language = pLanguage;
    }

    
    public String toString() {
        return language;
    }

}
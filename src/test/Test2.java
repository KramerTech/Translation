package test;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import translate.Language;
import translate.TString;
import translate.TranslationService;
import translate.Translator;

public class Test2 {

	public static final String KEY = ""; //set key
	
	public static void main(String[] args) {
		
		Logger l = LogManager.getLogManager().getLogger("");
		l.setLevel(Level.ALL);
        for (Handler h : l.getHandlers()) {
        	h.setLevel(Level.CONFIG);
           //System.out.println(h.);
        }
		TranslationService.setDefaultDictionaryFolder("test/");
		TranslationService.setDefaultKey(KEY);
		Translator t = new Translator(Language.ENGLISH, Language.FRENCH);
		TString a = null;
		for (int i = 0; i < 101; i++) 
			a = t.translate("You're the absolute worst.");
		t.close();
		
		System.out.println(a);

	}
}

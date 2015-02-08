package translate;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.AccessDeniedException;


/**
 * Wrapper class for connections to the translation service.
 * @author Sean Kramer
 */
public class Translator implements Closeable {

	private static final TString EMPTY = new TString("", "");
	
	private TranslationService service;
	private boolean enabled = true;
	private String key;
	private String appName;
	private String keyName;
	
    /**
     * Constructs a translator that converts from the source language to the
     * default target language. All other defaults are also used.
     * 
     * @param source the language of the original text
     * @throws IOException 
     */
    public Translator(Language source) {
        this(source, TranslationService.defaultTarget);
    }
    
    
    /**
     * Disables all translation functionality. If disabled, whenever a translate request
     * is submitted, a TString marked as translated will be immediately return, with its
     * translate field set equal to an empty string. Useful for testing without API calls
     * without having to restructure your code.
     */
    public void disable() {
    	enabled = false;
    }
    
    
    /** Re-enables translations after a call to disable. */
    public void enable() {
    	enabled = true;
    }
    
    
    /**
     * Constructs a translator that converts from the source language to the
     * requested target language. Defaults will be used for other options.
     * 
     * @param source the language of the original text
     * @param target the output language of the translation
     * @throws IOException 
     */
    public Translator(Language source, Language target) {
    	this(source, target, null, null, null, false);
    }
    
    
    /**
     * Constructs a customizable translator that converts from the source
     * language to the given target language (if applicable). All parameters
     * besides 'source' and 'relative' are optional. Set to null in order to
     * use the default option provided for by the TranslationService.
     * 
     * @param source the language of the original text
     * @param target the output language of the translation (null = default)
     * @param key key used for translation API calls (null = default)
     * @param dictionaryLoc the directory to read/store translation caches (null = default)
     * @param relative True if the dictionaryLoc directory is relative to the
     * default location set in the TranslationService class. False if this path
     * is independent of the default path (or the default path is not set).
     * @throws AccessDeniedException
     */
    public Translator(Language source, Language target, String key,
    		String appName, String dictionaryLoc, boolean relative) {
    	
    	//Help catch silly mistakes
    	if (relative && dictionaryLoc == null)
    		throw new IllegalStateException("'relative' should not be true if no path is provided.");
    	if (relative && TranslationService.defaultLoc == null)
    		throw new IllegalStateException("''relative' is set to true, but the default location has not been set.");
    	
    	//Set defaults if passed null    	
    	target = target == null ? TranslationService.defaultTarget : target;
    	this.key = key == null ? TranslationService.defaultKey : key;
    	this.appName = appName == null ? TranslationService.defaultAppName : appName;
    	
    	//Handle relative paths and such    	
    	if (relative)
    		dictionaryLoc = TranslationService.testPath(TranslationService.defaultLoc + dictionaryLoc);
    	else
    		dictionaryLoc = dictionaryLoc == null ? TranslationService.defaultLoc : TranslationService.testPath(dictionaryLoc);
    	
    	//Validation
    	if (this.key == null) {
    		throw new IllegalStateException("The default API key has not been set. Set it with a call to "
    				+ "TranslationService#setDefaultKey before creating a default instance of Translator.");
    	}
    	if (dictionaryLoc == null) {
    		throw new IllegalStateException("The default dictionary location has not been set. Set it with a call "
    				+ "to TranslationService#setDefaultDictionaryFolder before creating a default instace of Translator.");
    	}
    	
    	//Create string for hash indexing
    	keyName = key + appName;
    	
    	//Get service
    	this.service = TranslationService.get(source, target, dictionaryLoc, keyName, this);
    }

    
    /** Writes the buffered translation cache out to disk. */
    public void flush() {
    	service.flush();
    }
    
    
    /** Defines a cached translation relationship (for creating
     * translation dictionaries from differently formatted data).
     * 
     * @param original string in the source language
     * @param translation translated string in the target language
     */
    public void feed(String original, String translation, boolean overwrite) {
    	if (bad(original) || bad(translation))
    		return;
    	service.feed(original, translation, overwrite);
    }
    
    
    private boolean bad(String s) {
    	if (s == null || s.trim().length() == 0)
    		return true;
    	return false;
    }

    
    /**
     * Submit a string for translation. If it is available in the cache,
     * it will be translated immediately. Otherwise, it will be placed
     * in a buffer, and will be translated with an API call when next
     * that buffer fills. The returned TString should be stored for when
     * it is needed later, giving the translation time to complete. In
     * the meantime, user threads can/should continue operations.
     * 
     * @param original the string to be translated
     * @return a String wrapper with methods to check the status of the string's translation
     */
    public synchronized TString translate(String original) {
    	if (bad(original))
    		return EMPTY;
    	
    	if (!enabled)
    		return new TString(original, "");
    	
    	return service.submit(original, key, appName, keyName);
    }
    
    
    /** Sends the current translation buffer to the translation service
     * immediately, without waiting for it to become full. Thread will
     * block until it gets its translation. */
    public synchronized TString translateNow(String original) {
    	if (bad(original))
    		return EMPTY;
    	
    	if (!enabled)
    		return new TString(original, "");
    	
    	TString result = service.submit(original, key, appName, keyName);
    	if (result.isTranslated())
    		return result;
    	
    	service.translate(key, appName, keyName);
    	result.waitForTranslation();
    	return result;
    	
    	//TODO if waiting on a translation called by a different translator, tell that one to translate immediately too
    	//^May be able to store key, appName, and keyName (or maybe just Translator in TString). Think about when less sleepy.
    	//^That might not work because of close functionality thing. Idk.
    }
    

	@Override
	public void close() {
		service.translate(key, appName, keyName);
		service.close(this);
	}
	
}

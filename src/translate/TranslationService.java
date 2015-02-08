package translate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.google.api.services.translate.Translate;
import com.google.api.services.translate.Translate.Builder;
import com.google.api.services.translate.TranslateRequestInitializer;
import com.google.api.services.translate.model.TranslationsListResponse;
import com.google.api.services.translate.model.TranslationsResource;


/**
 * Main translation settings and logic class. Default properties set
 * via this class will be used when creating Translator objects.
 * 
 * @author Sean Kramer
 */
public class TranslationService {
	
	private static final int GOOGLE_MAX = 2500;
	private static final String NEW_LINE = "/@_/nl/_@/";
	
	private static AtomicInteger cacheCount = new AtomicInteger(0);
	
	private static Logger log = Logger.getLogger(TranslationService.class.toString());
	
	private Hashtable<String, TString> workingSet = new Hashtable<String, TString>();
	private Hashtable<String, List<TString>> submissionSets = new Hashtable<String, List<TString>>();
	private Hashtable<String, MutableInt> submissionCounts = new Hashtable<String, MutableInt>();
	private Hashtable<String, Translate> googleServices = new Hashtable<String, Translate>();
	
	/** Default target language used if no target is provided. */
	protected static Language defaultTarget = Language.ENGLISH;
    private Language target;	// Instance target
    private Language source;	// Instance source
    
    /** Location of the cache dictionaries. */
    protected static String defaultLoc = null;
    private String dictionaryLoc = null;
    
    /** Application name */
    protected static String defaultAppName = "TranslationSuite";
    
    /** API Key */
    protected static String defaultKey = null;
    
    /** Dictionary writer */
    private PrintWriter out;
    
    /** List of translators using this service. Should only be modified
     *  when the lock for the translators objects is held. */
    private List<Translator> usage = new ArrayList<Translator>();
    
    /** Maintains a static Singleton list of translator objects. */
    private static List<TranslationService> translators = new ArrayList<TranslationService>();

    private volatile Integer requestsPending = 0;
    
    
    public static void main(String[] args) {
    	System.out.println("Translation Suite v1.0");
    }
    

    /**
     * Sets the default location for the translation dictionary cache.
     * @param path path to folder location
     */
    public static void setDefaultDictionaryFolder(String path)  {
    	defaultLoc = testPath(path);
    }
    
    
    /**
     * Sets the default name for application.
     * @param name application name
     */
    public static void setDefaultApplicationName(String name)  {
    	defaultAppName = name;
    }
    
    
    /**
     * Sets the default key for the translation API calls.
     * @param key key to use in the calls
     */
    public static void setDefaultKey(String key) {
    	defaultKey = key;
    }
    
    
    /**
     * Verify a cache-folder path
     * @param path path to folder
     * @return corrected path name
     */
    protected static String testPath(String path) {
    	path = path.endsWith("/") ? path : path + "/";
    	File testPermissions = new File(path);
    	boolean err = false;
    	
    	//Verify valid location
    	try {
    		if (!testPermissions.exists())
    			testPermissions.mkdir();
    		else if (!testPermissions.isDirectory())
    			throw new IllegalStateException("Path is not a directory.");
    		if (!testPermissions.canRead() || !testPermissions.canWrite())
    			err = true;
    	} catch (Exception e) {
    		err = true;
    	}
    	
    	//Err if something went wrong
    	if (err) 
    		throw new RuntimeException("Could not read/write in directory.");
    	
   		return path;
    }
    
    
    /**
     * Gets the requested translator
     * 
     * @param source the language of the original text
     * @param target the language of the translated output
     * @param translator 
     * @return an appropriate translator object
     */
    protected static TranslationService get(Language source, Language target,
    		String dictionaryLoc, String keyName, Translator translator) {
    	
    	//Check for translator that already matches desired translation.
    	synchronized (translators) {
    		TranslationService service = null;
	    	for (TranslationService t : translators)
	    		if (t.source.equals(source) && t.target.equals(target) && t.dictionaryLoc.equals(dictionaryLoc))
	    			service = t;
	    	
	    	//If desired translator is not found, create it
	    	if (service == null)
	    		service = new TranslationService(source, target, dictionaryLoc);
	    	
	    	//Update usage list
	    	service.usage.add(translator);
	    	return service;
    	}
    }


    /** Removes the given Translator, and frees the service if no other
     * translators are currently using it. */
	protected void close(Translator t) {
    	synchronized (translators) {
    		usage.remove(t);
    		
    		//Fully close out this translation service
    		if (usage.isEmpty()) {
    			//Clear working set memory
    			workingSet = null;
    			
    			//Remove this instance from the translators list
    			translators.remove(this);
    			
    			//Wait for anything pending
    			synchronized (requestsPending) {
    				while (requestsPending > 0)
    					try {requestsPending.wait();} catch (InterruptedException e) {}
				}
    			
    			//Finish writing the file and close
    			out.close();
    		}
		}
	}
	
	
	private String fileName(Language source, Language target) {
		return source.toString().toUpperCase() + '_' + target.toString().toUpperCase() + ".cache";
	}
	
	
	protected void flush() {
		out.flush();
	}
    
    
	
	private void read(File file) {
		//New dictionary
		if (!file.exists())
			return;
		
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
		} catch (Exception e) {
			throw new RuntimeException("Dictionary file " + file.getAbsolutePath() + " could not be read");
		}
		
		try {
			String read = in.readLine();
			while (read != null) {
				read = read.replaceAll(NEW_LINE, "\n");
				workingSet.put(read.toLowerCase(), new TString(read, in.readLine().replaceAll(NEW_LINE, "\n")));
				read = in.readLine();
			}
		} catch (Exception e) {
			try {in.close();} catch (IOException io) {}
			throw new RuntimeException("The dictionary cache structure appears to be corrupted.");
		}
		
		try {in.close();} catch (IOException io) {}
	}
	
    
	/**
	 * Puts a source-target relationship directly into the
	 * current cache dictionary.
	 * @param original original string
	 * @param translation translated string
	 */
	protected void feed(String original, String translation, boolean overwrite) {
		original = original.replaceAll(NEW_LINE, "\n");
		translation = translation.replaceAll(NEW_LINE, "\n");
		synchronized (workingSet) {
			//Check for existing translation if overwrite is not enabled
			if (!overwrite) {
				TString entry = workingSet.get(original.toLowerCase());
				if (entry != null) {
					//Ignore repeats
					if (translation.equalsIgnoreCase(entry.toString()))
						return;
					
					//Throw exception on differences if overwrite not enabled
					out.flush();
					throw new RuntimeException("Key \"[" + original + "]\" already exists in dictionary as \"["
							+ entry + "]\", not \"[" + translation + "]\"");
				}
			}
			
			//Put in new feed
			workingSet.put(original.toLowerCase(), new TString(original, translation));
			synchronized (out) {
				writePair(original, translation);
			}
		}
	}
	
	
	private void writePair(String a, String b) {
		out.println(a.replace("\n", NEW_LINE));
		out.println(b.replace("\n", NEW_LINE));
	}
	
	
	protected TString submit(String original, String key, String name, String keyName) {
		boolean isNew = false;
		TString translation = null;
		synchronized (workingSet) {
			//Check for existing translation in the cache
			translation = workingSet.get(original.toLowerCase());
			
			//TODO, if translation is incomplete and belongs to a different submission set, then mark that somehow (for translateNow functionality)
			
			//Create a new TString for translation if none exists
			if (translation == null) {
				isNew = true;
				translation = new TString(original);
				
				workingSet.put(original.toLowerCase(), translation);
			} else {
				int c = cacheCount.incrementAndGet();
				if (c % 50 == 0)
					log.config(c + " strings translated from cache");
			}
		}
		
		//If not found, submit a translation request
		if (isNew) {
			MutableInt count;
			synchronized (submissionSets) {
				List<TString> subs = submissionSets.get(keyName);
				if (subs == null) {
					subs =  new ArrayList<TString>();
					submissionSets.put(keyName, subs);
					submissionCounts.put(keyName, new MutableInt());
				}
				subs.add(translation);
				count = submissionCounts.get(keyName);
				count.value += translation.getOriginal().length();
				
				//Translate threshold reached
				if (count.value >= GOOGLE_MAX) {
					submissionCounts.remove(keyName);
					translate(key, name, keyName);
				}
			}
		}
		
		return translation;
	}
	
	
	protected void translate(String key, String name, String keyName) {
		synchronized (submissionSets) {
			//Get set associated with given key
			List<TString> words = submissionSets.get(keyName);
			
			//Ignore translation calls on empty sets
			if (words == null || words.size() == 0)
				return;
			
			//Make room for new translation requests
			submissionSets.remove(keyName);
			
			//Create a google service if it DNE
			Translate service = googleServices.get(keyName);
			if (service == null) {
				service = createGoogleService(key, name);
				googleServices.put(keyName, service);
			}
			
			//Run the network call
			synchronized (requestsPending) {requestsPending++;}
			Request reqRunner = new Request(words, service);
			reqRunner.start();
		}
	}
	
	
	private Translate createGoogleService(String key, String name) {
		Builder builder = null;
		try {
			builder = new Builder(com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
					com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance(), null);
			builder.setTranslateRequestInitializer(new TranslateRequestInitializer(key));
			builder.setApplicationName(name);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return builder.build();
	}
	
	
	/**
	 * Private constructor for creating a TranslationService that will
	 * perform as desired. Should be wrapped by an instance of the
	 * Translator class.
	 * @param source source language
	 * @param target target language
	 * @param dictionaryLoc dictionary cache directory path
	 */
    private TranslationService(Language source, Language target, String dictionaryLoc) {
    	this.source = source;
    	this.target = target;
    	this.dictionaryLoc = dictionaryLoc;
        
    	File file = new File(dictionaryLoc + fileName(source, target));
    	
    	read(file);
    	
    	try {
    		out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8")));
    	} catch (IOException e) {
    		throw new RuntimeException("Could not open file " + file.getName());
    	}
    }


    private class MutableInt {
    	int value = 0;
    }
    
    
	private class Request extends Thread {
		List<TString> words;
		Translate service;
		//Translate backupService;
		
		public Request(List<TString> words, Translate service) {
			this.words = words;
			this.service = service;
		}
		
		public void run() {
			//Populate raw list
			List<String> rawWords = new ArrayList<String>(words.size());
			for (TString t : words)
				rawWords.add(t.getOriginal());
			
			//Make the translation call
			List<TranslationsResource> resources = null;
			try {
				com.google.api.services.translate.Translate.Translations.List translationList =
						service.translations().list(rawWords, target.toString());
				translationList.setSource(source.toString());
				TranslationsListResponse response = translationList.execute();
				resources = response.getTranslations();
			} catch (IOException e) {
				e.printStackTrace();
				//TODO error handling
				return;
			}
			
			//Simple validation of results
			if (words.size() != resources.size()) {
				out.flush();
				throw new RuntimeException("Translation mismatch! Submitted text count does not match translation result.");
			}
				
			//Tell the TStrings about their new translations
			for (int i = 0; i < words.size(); i++)
				words.get(i).setTranslation(resources.get(i).getTranslatedText());
			
			//Write out the new dictionary info
			synchronized (out) {
				for (TString i : words)
					writePair(i.getOriginal(), i.toString());
			}
			
			synchronized (requestsPending) {
				requestsPending.notifyAll();
				requestsPending--;
			}
		}
	}
}





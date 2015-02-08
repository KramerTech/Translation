package translate;

/** A class to hold strings and their translations. */
public class TString {
	
    private String original;
    private String translation = null;

    private Boolean isTranslated = false;


    /**
     * For internal use to create a TString that's pending translation
     * @param original string to be translated
     */
    protected TString(String original) {
        this.original = original;
    }


    /**
     * For internal use to create a TString when the translation is known
     * @param original string to be translated
     */
    protected TString(String original, String translation) {
    	this.original = original;
        this.translation = translation;
        this.isTranslated = true;
    }


    /** Blocks on the isTranslated object until translation completes. */
    protected void waitForTranslation() {
    	synchronized (isTranslated) {
            if (!isTranslated()) {
                try {isTranslated.wait();}
                catch (InterruptedException e) {}
            }
		}
    }


    public boolean equals(Object t) {
    	if (t == null) return false;
    	if (!(t instanceof TString)) return false;
    	TString tt = (TString) t;
    	if (original == null && tt.original != null) return false;
    	if (translation == null && tt.translation != null) return false;
    	return (original.equals(tt.original) && translation.equals(tt.translation));
    }
    
    /**
     * For internal use by the translator service to notify this
     * class of its translation.
     * @param translation the translated string
     */
    protected void setTranslation(String translation) {
    	synchronized (isTranslated) {
            this.translation = translation;
            isTranslated.notifyAll();
            isTranslated = true;
		}
    }


    /**
     * Gets the original string    
     * @return original string (not translation)
     */
    public String getOriginal() {
        return original;
    }


    /**
     * Returns the translated string if the translation has occurred,
     * or null if it the translation is still pending. To automatically
     * block until the translation completes, use the toString() method. 
     * @return the translated string, or null if translation is pending
     */
    public String getNonblockingTranslation() {
        synchronized (isTranslated) {return isTranslated() ? translation : null;}
    }


    /**
     * Waits until the translation is complete if it is still pending,
     * then returns the translated string.
     */
    public String toString() {
        waitForTranslation();
        return translation;
    }


    /**
     * Test whether the translation has completed
     * @return true if the translation is complete, or false if it is still pending
     */
    public boolean isTranslated() {
    	synchronized (isTranslated) {
    		return isTranslated;
    	}
    }
}

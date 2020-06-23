package org.dynmap.markers;

public interface EnterExitMarker {
	class EnterExitText {
		public String title;
		public String subtitle;
	}

    /**
	 * Greeting text, if defined
	 */
    EnterExitText getGreetingText();
	/**
	 * Farewell text, if defined
	 */
    EnterExitText getFarewellText();
	/**
	 * Set greeting text
	 */
    void setGreetingText(String title, String subtitle);
	/**
	 * Set greeting text
	 */
    void setFarewellText(String title, String subtitle);
	/**
	 * Test if point is inside marker volume
	 */
    boolean testIfPointWithinMarker(String worldid, double x, double y, double z);
}

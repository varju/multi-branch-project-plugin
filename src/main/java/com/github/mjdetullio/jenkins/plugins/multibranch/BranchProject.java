package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.io.IOException;

import hudson.model.AbstractProject;
import hudson.model.TopLevelItem;

/**
 * @author Matthew DeTullio
 */
public interface BranchProject extends TopLevelItem {

	/**
	 * Returns whether or not this is the template.
	 *
	 * @return boolean
	 */
	boolean isTemplate();

	/**
	 * Sets whether or not this is the template
	 *
	 * @param isTemplate - true/false
	 * @throws IOException - if problem saving
	 */
	void markTemplate(boolean isTemplate) throws IOException;
}

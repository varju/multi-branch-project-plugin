/*
 * The MIT License
 *
 * Copyright (c) 2014, Matthew DeTullio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import jenkins.model.Jenkins;

/**
 * @author Matthew DeTullio
 */
public class FreeStyleMultiBranchProject extends AbstractMultiBranchProject
		<FreeStyleProject, FreeStyleBuild, FreeStyleBranchProject> {

	private static final String CLASSNAME = FreeStyleMultiBranchProject.class.getName();
	private static final Logger LOGGER = Logger.getLogger(CLASSNAME);

	private static final String UNUSED = "unused";

	/**
	 * Constructor that specifies the {@link ItemGroup} for this project and the
	 * project name.
	 *
	 * @param parent - the project's parent {@link ItemGroup}
	 * @param name   - the project's name
	 */
	public FreeStyleMultiBranchProject(ItemGroup parent, String name) {
		super(parent, name);
	}

	// TODO remove if AbstractMultiBranchProject call to this method stays removed
	@Override
	protected void initTemplate() {
		File templateDir = new File(getRootDir(), "template");
		try {
			if (!(new File(templateDir, "config.xml").isFile())) {
				templateProject = new FreeStyleBranchProject(this, "template");
			} else {
				templateProject = (FreeStyleBranchProject) Items.load(this,
						templateDir);
			}
			templateProject.markTemplate(true);
			templateProject.disable();
		} catch (IOException e) {
			LOGGER.log(Level.WARNING,
					"Failed to load template project " + templateDir, e);
		}
	}

	@Override
	protected FreeStyleBranchProject createNewSubProject(
			AbstractMultiBranchProject parent, String branchName) {
		return new FreeStyleBranchProject(parent, branchName);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public TopLevelItemDescriptor getDescriptor() {
		return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(
				FreeStyleMultiBranchProject.class);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Class<FreeStyleBuild> getBuildClass() {
		return FreeStyleBuild.class;
	}

	/**
	 * Our project's descriptor.
	 */
	@Extension
	public static class DescriptorImpl extends AbstractProjectDescriptor {
		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getDisplayName() {
			return Messages.FreeStyleMultiBranchProject_DisplayName();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public TopLevelItem newInstance(ItemGroup parent, String name) {
			return new FreeStyleMultiBranchProject(parent, name);
		}
	}

	/**
	 * Gives this class an alias for configuration XML.
	 */
	@Initializer(before = InitMilestone.PLUGINS_STARTED)
	@SuppressWarnings(UNUSED)
	public static void registerXStream() {
		Items.XSTREAM.alias("freestyle-multi-branch-project",
				FreeStyleMultiBranchProject.class);
	}
}

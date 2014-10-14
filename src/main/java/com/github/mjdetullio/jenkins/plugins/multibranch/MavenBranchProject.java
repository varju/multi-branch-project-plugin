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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.interceptor.RequirePOST;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import jenkins.model.Jenkins;

/**
 * Note: Will show publishers that aren't usually applicable to Maven projects
 * because of our weird project descriptor mess.
 *
 * @author Matthew DeTullio
 */
public class MavenBranchProject extends MavenModuleSet implements
		BranchProject {

	private static final String CLASSNAME = MavenBranchProject.class.getName();
	private static final Logger LOGGER = Logger.getLogger(CLASSNAME);

	private static final String UNUSED = "unused";

	private volatile boolean template = false;

	/**
	 * Constructor that specifies the {@link ItemGroup} for this project and the
	 * project name.
	 *
	 * @param parent - the project's parent {@link ItemGroup}
	 * @param name   - the project's name
	 */
	public MavenBranchProject(ItemGroup parent, String name) {
		super(parent, name);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onLoad(ItemGroup<? extends Item> parent, String name)
			throws IOException {
		/*
		 * Name parameter should be the directory name, which needs to be
		 * decoded.
		 */
		String decoded = ProjectUtils.rawDecode(name);
		super.onLoad(parent, decoded);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isTemplate() {
		return template;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void markTemplate(boolean isTemplate) throws IOException {
		template = isTemplate;
		save();
	}

	/**
	 * First checks if the parent project is disabled, then this sub-project's
	 * setting.
	 *
	 * @return true if parent project is disabled or this project is disabled
	 */
	@Override
	public boolean isDisabled() {
		return ((MavenMultiBranchProject) getParent()).isDisabled()
				|| super.isDisabled();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(
				MavenModuleSet.class);
	}

	//region AbstractProject mirror

	protected synchronized MavenModuleSetBuild newBuild() throws IOException {
		// make sure we don't start two builds in the same second
		// so the build directories will be different too

		// Hack to set lastBuildStartTime since it is not exposed
		try {
			Field f = AbstractProject.class.getDeclaredField(
					"lastBuildStartTime");
			f.setAccessible(true);

			long timeSinceLast = System.currentTimeMillis() - f.getLong(this);
			if (timeSinceLast < 1000) {
				try {
					Thread.sleep(1000 - timeSinceLast);
				} catch (InterruptedException e) {
					// No-op
				}
			}
			f.setLong(this, System.currentTimeMillis());
		} catch (Throwable e) {
			LOGGER.log(Level.WARNING, "Unable to get/set lastBuildStartTime",
					e);
		}
		// End hack

		try {
			/*
			 * Specify project class (instead of relying on getClass()).  Use of
			 * getClass() in the super-type gets this sub-type.  The build class
			 * doesn't have a constructor for this type -- we want to use the
			 * super-type.
			 */
			MavenModuleSetBuild lastBuild = getBuildClass().getConstructor(
					MavenModuleSet.class).newInstance(this);
			builds.put(lastBuild);
			return lastBuild;
		} catch (InstantiationException e) {
			throw new Error(e);
		} catch (IllegalAccessException e) {
			throw new Error(e);
		} catch (InvocationTargetException e) {
			throw handleInvocationTargetException(e);
		} catch (NoSuchMethodException e) {
			throw new Error(e);
		}
	}

	private IOException handleInvocationTargetException(
			InvocationTargetException e) {
		Throwable t = e.getTargetException();
		if (t instanceof Error) {
			throw (Error) t;
		}
		if (t instanceof RuntimeException) {
			throw (RuntimeException) t;
		}
		if (t instanceof IOException) {
			return (IOException) t;
		}
		throw new Error(t);
	}

	protected MavenModuleSetBuild loadBuild(File dir) throws IOException {
		try {
			/*
			 * Specify project class (instead of relying on getClass()).  Use of
			 * getClass() in the super-type gets this sub-type.  The build class
			 * doesn't have a constructor for this type -- we want to use the
			 * super-type.
			 */
			return getBuildClass().getConstructor(MavenModuleSet.class,
					File.class).newInstance(this, dir);
		} catch (InstantiationException e) {
			throw new Error(e);
		} catch (IllegalAccessException e) {
			throw new Error(e);
		} catch (InvocationTargetException e) {
			throw handleInvocationTargetException(e);
		} catch (NoSuchMethodException e) {
			throw new Error(e);
		}
	}

	//endregion AbstractProject mirror

	/**
	 * Disables renaming of this project type. <p/> Inherited docs: <p/>
	 * {@inheritDoc}
	 */
	@Override
	@RequirePOST
	public void doDoRename(StaplerRequest req, StaplerResponse rsp) {
		throw new UnsupportedOperationException(
				"Renaming sub-projects is not supported.  They should only be added or deleted.");
	}

	/**
	 * Disables configuring of this project type via Stapler. <p/> Inherited
	 * docs: <p/> {@inheritDoc}
	 */
	@Override
	@WebMethod(name = "config.xml")
	public void doConfigDotXml(StaplerRequest req, StaplerResponse rsp)
			throws IOException {
		if (req.getMethod().equals("POST")) {
			// submission
			throw new UnsupportedOperationException(
					"This sub-project configuration cannot be edited directly.");
		}
		super.doConfigDotXml(req, rsp);
	}

	// TODO: Contribute to other OSS projects to make this possible
	//	/**
	//	 * Our descriptor that is simply a duplicate of the normal {@link MavenModuleSet}
	//	 * descriptor.
	//	 */
	//	@Extension
	//	public static class DescriptorImpl extends MavenModuleSet.DescriptorImpl {
	//		/**
	//		 * {@inheritDoc}
	//		 */
	//		@Override
	//		public boolean isInstantiable() {
	//			return false;
	//		}
	//	}

	/**
	 * Gives this class an alias for configuration XML.
	 */
	@Initializer(before = InitMilestone.PLUGINS_STARTED)
	@SuppressWarnings(UNUSED)
	public static void registerXStream() {
		Items.XSTREAM.alias("maven-branch-project", MavenBranchProject.class);
	}
}

package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.io.IOException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.interceptor.RequirePOST;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.maven.MavenModuleSet;
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
	@RequirePOST
	public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) {
		throw new UnsupportedOperationException(
				"This sub-project configuration cannot be edited directly.");
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

package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

/**
 * @author Matthew DeTullio
 */
public class MavenMultiBranchProject extends
		AbstractMultiBranchProject<MavenModuleSet, MavenModuleSetBuild, MavenBranchProject> {

	private static final String CLASSNAME = MavenMultiBranchProject.class.getName();
	private static final Logger LOGGER = Logger.getLogger(CLASSNAME);

	private static final String UNUSED = "unused";

	/**
	 * Constructor that specifies the {@link hudson.model.ItemGroup} for this
	 * project and the project name.
	 *
	 * @param parent - the project's parent {@link hudson.model.ItemGroup}
	 * @param name   - the project's name
	 */
	public MavenMultiBranchProject(ItemGroup parent, String name) {
		super(parent, name);
	}

	@Override
	protected void initTemplate() {
		File templateDir = new File(getRootDir(), "template");
		try {
			if (!(new File(templateDir, "config.xml").isFile())) {
				templateProject = new MavenBranchProject(this, "template");
			} else {
				templateProject = (MavenBranchProject) Items.load(this,
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
	protected MavenBranchProject createNewSubProject(
			AbstractMultiBranchProject parent, String branchName) {
		return new MavenBranchProject(parent, branchName);
	}

	@Override
	protected Class<MavenModuleSetBuild> getBuildClass() {
		return MavenModuleSetBuild.class;
	}

	@Override
	public TopLevelItemDescriptor getDescriptor() {
		return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(
				MavenMultiBranchProject.class);
	}

	/**
	 * Stapler URL binding used by the configure page to check the location of
	 * the POM, alternate settings file, etc - any file.
	 *
	 * @param value - file to check
	 * @return validation of file
	 */
	@SuppressWarnings(UNUSED)
	public FormValidation doCheckFileInWorkspace(@QueryParameter String value)
			throws IOException,
			ServletException {
		MavenModuleSetBuild lb = getLastBuild();
		if (lb != null) {
			FilePath ws = lb.getModuleRoot();
			if (ws != null) {
				return ws.validateRelativePath(value, true, true);
			}
		}
		return FormValidation.ok();
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
			return Messages.MavenMultiBranchProject_DisplayName();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public TopLevelItem newInstance(ItemGroup parent, String name) {
			return new MavenMultiBranchProject(parent, name);
		}
	}

	/**
	 * Gives this class an alias for configuration XML.
	 */
	@Initializer(before = InitMilestone.PLUGINS_STARTED)
	@SuppressWarnings(UNUSED)
	public static void registerXStream() {
		Items.XSTREAM.alias("maven-multi-branch-project",
				MavenMultiBranchProject.class);
	}
}

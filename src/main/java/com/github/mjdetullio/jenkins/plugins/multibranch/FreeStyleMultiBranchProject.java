/*
 * The MIT License
 *
 * Copyright (c) 2014, Matthew DeTullio, Stephen Connolly
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractProject;
import hudson.model.BuildAuthorizationToken;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.JDK;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.listeners.ItemListener;
import hudson.scm.SCMS;
import hudson.security.ACL;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildWrappers;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.util.DescribableList;
import hudson.util.FormApply;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import jenkins.model.BuildDiscarder;
import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;
import jenkins.scm.SCMCheckoutStrategy;
import jenkins.scm.api.SCMSourceDescriptor;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

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
	 * {@inheritDoc}
	 */
	@Override
	public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp)
			throws ServletException, Descriptor.FormException, IOException {
		//region Job mirror
		checkPermission(CONFIGURE);

		description = req.getParameter("description");

		boolean keepDependencies = req.getParameter("keepDependencies") != null;
		// Hack to set keepDependencies since it is not exposed
		try {
			Field f = Job.class.getDeclaredField("keepDependencies");
			f.setAccessible(true);
			f.set(templateProject, keepDependencies);
		} catch (Throwable e) {
			LOGGER.log(Level.WARNING, "Unable to set keepDependencies", e);
		}
		// End hack

		try {
			JSONObject json = req.getSubmittedForm();

			setDisplayName(json.optString("displayNameOrNull"));

			if (json.optBoolean("logrotate")) {
				templateProject.setBuildDiscarder(
						req.bindJSON(BuildDiscarder.class,
								json.optJSONObject("buildDiscarder")));
			} else {
				templateProject.setBuildDiscarder(null);
			}

			/*
			 * Save job properties to the parent project.
			 * Needed for things like project-based matrix authorization so the
			 * parent project's ACL works as desired.
			 */
			DescribableList<JobProperty<?>, JobPropertyDescriptor> t = new DescribableList<JobProperty<?>, JobPropertyDescriptor>(NOOP,getAllProperties());
			t.rebuild(req,json.optJSONObject("properties"),JobPropertyDescriptor.getPropertyDescriptors(this.getClass()));
			properties.clear();
			for (JobProperty p : t) {
				// Hack to set property owner since it is not exposed
				// p.setOwner(this)
				try {
					Field f = JobProperty.class.getDeclaredField(
							"owner");
					f.setAccessible(true);
					f.set(p, this);
				} catch (Throwable e) {
					LOGGER.log(Level.WARNING,
							"Unable to set job property owner", e);
				}
				// End hack
				properties.add(p);
			}

			/*
			 * WARNING: Copy/paste crap
			 *
			 * Save job properties to the template project.
			 * This is more important than the part saving to the parent so
			 * things like parameters 
			 */
 			DescribableList<JobProperty<?>, JobPropertyDescriptor> t2 = new DescribableList<JobProperty<?>, JobPropertyDescriptor>(
					NOOP, templateProject.getAllProperties());
			t2.rebuild(req, json.optJSONObject("properties"),
					JobPropertyDescriptor.getPropertyDescriptors(
							this.getClass()));
			templateProject.getPropertiesList().clear();
			for (JobProperty p : t2) {
				// Hack to set property owner since it is not exposed
				// p.setOwner(templateProject)
				try {
					Field f = JobProperty.class.getDeclaredField(
							"owner");
					f.setAccessible(true);
					f.set(p, templateProject);
				} catch (Throwable e) {
					LOGGER.log(Level.WARNING,
							"Unable to set job property owner", e);
				}
				// End hack

				//noinspection unchecked
				templateProject.addProperty(p);
			}

			submit(req, rsp);

			templateProject.save();
			save();
			ItemListener.fireOnUpdated(templateProject);
			// TODO could this be used to trigger syncBranches()?
			ItemListener.fireOnUpdated(this);

			String newName = req.getParameter("name");
			final ProjectNamingStrategy namingStrategy = Jenkins.getInstance().getProjectNamingStrategy();
			if (newName != null && !newName.equals(name)) {
				// check this error early to avoid HTTP response splitting.
				Jenkins.checkGoodName(newName);
				namingStrategy.checkName(newName);
				rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName,
						"UTF-8"));
			} else {
				if (namingStrategy.isForceExistingJobs()) {
					namingStrategy.checkName(name);
				}
				//noinspection ThrowableResultOfMethodCallIgnored
				FormApply.success(".").generateResponse(req, rsp, null);
			}
		} catch (JSONException e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println(
					"Failed to parse form data. Please report this problem as a bug");
			pw.println("JSON=" + req.getSubmittedForm());
			pw.println();
			e.printStackTrace(pw);

			rsp.setStatus(SC_BAD_REQUEST);
			sendError(sw.toString(), req, rsp, true);
		}
		//endregion Job mirror

		//region AbstractProject mirror
		updateTransientActions();

		// TODO: does this even?
		Set<AbstractProject> upstream = Collections.emptySet();
		if (req.getParameter("pseudoUpstreamTrigger") != null) {
			upstream = new HashSet<AbstractProject>(
					Items.fromNameList(getParent(),
							req.getParameter("upstreamProjects"),
							AbstractProject.class));
		}

		// Hack to execute method convertUpstreamBuildTrigger(upstream);
		try {
			Method m = AbstractProject.class.getDeclaredMethod(
					"convertUpstreamBuildTrigger", Set.class);
			m.setAccessible(true);
			m.invoke(templateProject, upstream);
		} catch (Throwable e) {
			LOGGER.log(Level.WARNING,
					"Unable to execute convertUpstreamBuildTrigger(Set)", e);
		}
		// End hack

		// notify the queue as the project might be now tied to different node
		Jenkins.getInstance().getQueue().scheduleMaintenance();

		// this is to reflect the upstream build adjustments done above
		Jenkins.getInstance().rebuildDependencyGraphAsync();
		//endregion AbstractProject mirror

		// TODO run this separately since it can block completion (user redirect) if unable to fetch from repository
		getSyncBranchesTrigger().run();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void submit(StaplerRequest req, StaplerResponse rsp)
			throws IOException, ServletException, Descriptor.FormException {
		/*
		 * super.submit(req, rsp) is not called because it plays with the
		 * triggers, which we want this class to handle.  Specifically, we want
		 * to set triggers so they can be mirrored to the sub-projects, but we
		 * don't want to start them for this project.  We only want to start the
		 * SyncBranchesTrigger, whose settings we don't get from the list of
		 * other triggers.
		 */
		JSONObject json = req.getSubmittedForm();

		//region AbstractProject mirror
		makeDisabled(req.getParameter("disable") != null);

		JDK jdk = Jenkins.getInstance().getJDK(req.getParameter("jdk"));
		if (jdk == null) {
			jdk = new JDK(null, null);
		}
		templateProject.setJDK(jdk);
		if (req.getParameter("hasCustomQuietPeriod") != null) {
			templateProject.setQuietPeriod(
					Integer.parseInt(req.getParameter("quiet_period")));
		} else {
			templateProject.setQuietPeriod(null);
		}
		Integer scmCheckoutRetryCount = null;
		if (req.getParameter("hasCustomScmCheckoutRetryCount") != null) {
			scmCheckoutRetryCount = Integer.parseInt(
					req.getParameter("scmCheckoutRetryCount"));
		}
		// Hack to set retry count since it is not exposed
		try {
			Field f = AbstractProject.class.getDeclaredField(
					"scmCheckoutRetryCount");
			f.setAccessible(true);
			f.set(templateProject, scmCheckoutRetryCount);
		} catch (Throwable e) {
			LOGGER.log(Level.WARNING, "Unable to set scmCheckoutRetryCount", e);
		}
		// End hack
		templateProject.setBlockBuildWhenDownstreamBuilding(
				req.getParameter("blockBuildWhenDownstreamBuilding") != null);
		templateProject.setBlockBuildWhenUpstreamBuilding(
				req.getParameter("blockBuildWhenUpstreamBuilding") != null);

		if (req.hasParameter("customWorkspace")) {
			templateProject.setCustomWorkspace(Util.fixEmptyAndTrim(
					req.getParameter("customWorkspace.directory")));
		} else {
			templateProject.setCustomWorkspace(null);
		}

		if (json.has("scmCheckoutStrategy")) {
			templateProject.setScmCheckoutStrategy(
					req.bindJSON(SCMCheckoutStrategy.class,
							json.getJSONObject("scmCheckoutStrategy")));
		} else {
			templateProject.setScmCheckoutStrategy(null);
		}

		// Hack to set assignedNode/canRoam since it is not exposed
		// setAssignedLabel does not work as desired, specifically when restricting to master
		String assignedNode = null;
		if (req.getParameter("hasSlaveAffinity") != null) {
			assignedNode = Util.fixEmptyAndTrim(
					req.getParameter("_.assignedLabelString"));
		}
		boolean canRoam = assignedNode == null;
		try {
			Field f = AbstractProject.class.getDeclaredField("assignedNode");
			f.setAccessible(true);
			f.set(templateProject, assignedNode);
		} catch (Throwable e) {
			LOGGER.log(Level.WARNING, "Unable to set assignedNode", e);
		}
		try {
			Field f = AbstractProject.class.getDeclaredField("canRoam");
			f.setAccessible(true);
			f.set(templateProject, canRoam);
		} catch (Throwable e) {
			LOGGER.log(Level.WARNING, "Unable to set canRoam", e);
		}
		// End hack

		templateProject.setConcurrentBuild(
				req.getSubmittedForm().has("concurrentBuild"));

		//noinspection deprecation
		BuildAuthorizationToken authToken = BuildAuthorizationToken.create(req);
		// Hack to set auth token since it is not exposed
		try {
			Field f = AbstractProject.class.getDeclaredField(
					"authToken");
			f.setAccessible(true);
			f.set(templateProject, authToken);
		} catch (Throwable e) {
			LOGGER.log(Level.WARNING, "Unable to set scmCheckoutRetryCount", e);
		}
		// End hack

		templateProject.setScm(SCMS.parseSCM(req, this));

		// Keep triggers meant for sub-projects in a different list
		templateProject.getTriggersList().replaceBy(
				buildDescribable(req, Trigger.for_(this)));

		for (Publisher _t : Descriptor.newInstancesFromHeteroList(req, json,
				"publisher", Jenkins.getInstance().getExtensionList(
						BuildTrigger.DescriptorImpl.class))) {
			BuildTrigger t = (BuildTrigger) _t;
			List<AbstractProject> childProjects;
			SecurityContext orig = ACL.impersonate(ACL.SYSTEM);
			try {
				childProjects = t.getChildProjects((AbstractProject) this);
			} finally {
				SecurityContextHolder.setContext(orig);
			}
			for (AbstractProject downstream : childProjects) {
				downstream.checkPermission(BUILD);
			}
		}
		//endregion AbstractProject mirror

		//region Project mirror
		templateProject.getBuildWrappersList().rebuild(req, json,
				BuildWrappers.getFor(this));
		templateProject.getBuildersList().rebuildHetero(req, json,
				Builder.all(), "builder");
		templateProject.getPublishersList().rebuildHetero(req, json,
				Publisher.all(),
				"publisher");
		//endregion Project mirror

		String syncBranchesCron = json.getString("syncBranchesCron");
		try {
			restartSyncBranchesTrigger(syncBranchesCron);
		} catch (ANTLRException e) {
			throw new IllegalArgumentException(
					"Failed to instantiate SyncBranchesTrigger", e);
		}

		primaryView = json.getString("primaryView");

		JSONObject scmSourceJson = json.optJSONObject("scmSource");
		if (scmSourceJson == null) {
			scmSource = null;
		} else {
			int value = Integer.parseInt(scmSourceJson.getString("value"));
			SCMSourceDescriptor descriptor = getSCMSourceDescriptors(true).get(
					value);
			scmSource = descriptor.newInstance(req, scmSourceJson);
			scmSource.setOwner(this);
		}
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

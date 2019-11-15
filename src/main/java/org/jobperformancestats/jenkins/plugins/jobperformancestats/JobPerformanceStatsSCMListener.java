package org.jobperformancestats.jenkins.plugins.jobperformancestats;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;
import net.sf.json.JSONObject;

/**
 * This class registers an {@link SCMListener} with Jenkins which allows us to create
 * the "Checkout successful" event.
 *
 */
@Extension
public class JobPerformanceStatsSCMListener extends SCMListener {

  private static final Logger logger =  Logger.getLogger(JobPerformanceStatsSCMListener.class.getName());

  /**
   * Invoked right after the source code for the build has been checked out. It will NOT be
   * called if a checkout fails.
   *
   * @param build - Current build
   * @param scm - Configured SCM
   * @param workspace - Current workspace
   * @param listener - Current build listener
   * @param changelogFile - Changelog
   * @param pollingBaseline - Polling
   * @throws Exception if an error is encountered
   */
  @Override
  public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener,
          File changelogFile, SCMRevisionState pollingBaseline) throws Exception {

    if ( JobPerformanceStatsUtilities.isApiKeyNull() ) {
      return;
    }
    String jobName = build.getParent().getFullName();
    String normalizedJobName = JobPerformanceStatsUtilities.normalizeFullDisplayName(jobName);
    HashMap<String,String> tags = new HashMap<String,String>();
    JobPerformanceStatsJobProperty prop = JobPerformanceStatsUtilities.retrieveProperty(build);
    // Process only if job is NOT in blacklist and is in whitelist
    if ( JobPerformanceStatsUtilities.isJobTracked(jobName)
            && prop != null && prop.isEmitOnCheckout() ) {
      logger.fine("Checkout! in onCheckout()");

      // Get the list of global tags to apply
      tags.putAll(JobPerformanceStatsUtilities.getRegexJobTags(jobName));
      
      // Grab environment variables
      EnvVars envVars = new EnvVars();
      try {
        envVars = build.getEnvironment(listener);
        tags = JobPerformanceStatsUtilities.parseTagList(build, listener);
      } catch (IOException e) {
        logger.severe(e.getMessage());
      } catch (InterruptedException e) {
        logger.severe(e.getMessage());
      }

      // Gather pre-build metadata
      JSONObject builddata = new JSONObject();
      builddata.put("hostname", JobPerformanceStatsUtilities.getHostname(envVars)); // string
      builddata.put("job", normalizedJobName); // string
      builddata.put("number", build.number); // int
      builddata.put("result", null); // null
      builddata.put("duration", null); // null
      builddata.put("buildurl", envVars.get("BUILD_URL")); // string
      long starttime = build.getStartTimeInMillis() / JobPerformanceStatsBuildListener.THOUSAND_LONG; // ms to s
      builddata.put("timestamp", starttime); // string

      JobPerformanceStatsEvent evt = new CheckoutCompletedEventImpl(builddata, tags);

      JobPerformanceStatsHttpRequests.sendEvent(evt);
    }
  }
}

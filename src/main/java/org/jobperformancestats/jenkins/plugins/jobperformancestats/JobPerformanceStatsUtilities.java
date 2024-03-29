package org.jobperformancestats.jenkins.plugins.jobperformancestats;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import static org.jobperformancestats.jenkins.plugins.jobperformancestats.JobPerformanceStatsBuildListener.getOS;

public class JobPerformanceStatsUtilities {

  private static final Logger logger =  Logger.getLogger(JobPerformanceStatsSCMListener.class.getName());
  /**
   *
   * @return - The descriptor for the JobPerformanceStats plugin. In this case the global
   *         - configuration.
   */
  public static JobPerformanceStatsBuildListener.DescriptorImpl getJobPerformanceStatsDescriptor() {
    JobPerformanceStatsBuildListener.DescriptorImpl desc = (JobPerformanceStatsBuildListener.DescriptorImpl)Jenkins.getInstance().getDescriptorOrDie(JobPerformanceStatsBuildListener.class);
    return desc;
  }

  /**
   *
   * @return - The hostname configured in the global configuration. Shortcut method.
   */
  public static String getHostName()  {
    return JobPerformanceStatsUtilities.getJobPerformanceStatsDescriptor().getHostname();
  }

  /**
   * Set Hostname for global configuration.
   *
   * @param hostName - A string representing the hostname
   */
  public static void setHostName(String hostName)  {
    JobPerformanceStatsUtilities.getJobPerformanceStatsDescriptor().setHostname(hostName);
  }

  /**
   *
   * @return - The api key configured in the global configuration. Shortcut method.
   */
  public static Secret getApiKey() {
    return JobPerformanceStatsUtilities.getJobPerformanceStatsDescriptor().getApiKey();
  }

  /**
   *
   * Set ApiKey for global configuration.
   * 
   * @param apiKey - A string representing an apiKey
   */
  public static void setApiKey(String apiKey) {
    JobPerformanceStatsUtilities.getJobPerformanceStatsDescriptor().setApiKey(apiKey);
  }

  /**
   *
   * Check if apiKey is null
   *
   * @return boolean - apiKey is null
   */
  public static boolean isApiKeyNull() {
    return Secret.toString(JobPerformanceStatsUtilities.getApiKey()).isEmpty();
  }

  /**
   *
   * @return - The list of excluded jobs configured in the global configuration. Shortcut method.
   */
  public static String getBlacklist() {
    return JobPerformanceStatsUtilities.getJobPerformanceStatsDescriptor().getBlacklist();
  }
  /**
   *
   * @return - The list of included jobs configured in the global configuration. Shortcut method.
   */
  public static String getWhitelist() {
    return JobPerformanceStatsUtilities.getJobPerformanceStatsDescriptor().getWhitelist();
  }

  /**
   *
   * @return - The list of included jobs configured in the global configuration. Shortcut method.
   */
  public static String getGlobalJobTags() {
    return JobPerformanceStatsUtilities.getJobPerformanceStatsDescriptor().getGlobalJobTags();
  }

  /**
   *
   * @return - The target API URL
   */
  public static String getTargetMetricURL()  {
    return JobPerformanceStatsUtilities.getJobPerformanceStatsDescriptor().getTargetMetricURL();
  }

  /**
   * Checks if a jobName is blacklisted, whitelisted, or neither.
   *
   * @param jobName - A String containing the name of some job.
   * @return a boolean to signify if the jobName is or is not blacklisted or whitelisted.
   */
  public static boolean isJobTracked(final String jobName) {
    return !JobPerformanceStatsUtilities.isJobBlacklisted(jobName) && JobPerformanceStatsUtilities.isJobWhitelisted(jobName);
  }

  /**
   * Retrieve the list of tags from the Config file if regex Jobs was checked
   *  @param jobName - A string containing the name of some job
   *  @return - A Map of values containing the key and value of each JobPerformanceStats tag to apply to the metric/event
   */
  public static Map<String,String> getRegexJobTags(String jobName) {
    Map<String,String> tags = new HashMap<String,String>();
    final List<List<String>> globalTags = JobPerformanceStatsUtilities.regexJoblistStringtoList( JobPerformanceStatsUtilities.getGlobalJobTags() );

    logger.fine(String.format("The list of Global Job Tags are: %s", globalTags));

    // Each jobInfo is a list containing one regex, and a variable number of tags
    for (List<String> jobInfo: globalTags) {

      if(jobInfo.isEmpty()) {
        continue;
      }

      Pattern p = Pattern.compile(jobInfo.get(0));
      Matcher m = p.matcher(jobName);
      if(m.matches()) {
        for(int i = 1; i < jobInfo.size(); i++) {
          String[] tagItem = jobInfo.get(i).split(":");
          if(Character.toString(tagItem[1].charAt(0)).equals("$")) {
            try {
              tags.put(tagItem[0], m.group(Character.getNumericValue(tagItem[1].charAt(1))));
            } catch(IndexOutOfBoundsException e) {
              logger.fine(String.format("Specified a capture group that doesn't exist, not applying tag: %s Exception: %s", Arrays.toString(tagItem), e));
            }
          } else {
            tags.put(tagItem[0], tagItem[1]);
          }
        }
      }
    }

    return tags;
  }

  /**
   * Checks if a jobName is blacklisted.
   *
   * @param jobName - A String containing the name of some job.
   * @return a boolean to signify if the jobName is or is not blacklisted.
   */
  public static boolean isJobBlacklisted(final String jobName) {
    final List<String> blacklist = JobPerformanceStatsUtilities.joblistStringtoList( JobPerformanceStatsUtilities.getBlacklist() );
    return blacklist.contains(jobName.toLowerCase());
  }

  /**
   * Checks if a jobName is whitelisted.
   *
   * @param jobName - A String containing the name of some job.
   * @return a boolean to signify if the jobName is or is not whitelisted.
   */
  public static boolean isJobWhitelisted(final String jobName) {
    final List<String> whitelist = JobPerformanceStatsUtilities.joblistStringtoList( JobPerformanceStatsUtilities.getWhitelist() );
    
    // Check if the user config is using regexes
    return whitelist.isEmpty() || whitelist.contains(jobName.toLowerCase());
  }

  /**
   * Converts a blacklist/whitelist string into a String array.
   *
   * @param joblist - A String containing a set of job names.
   * @return a String array representing the job names to be whitelisted/blacklisted. Returns
   *         empty string if blacklist is null.
   */
  private static List<String> joblistStringtoList(final String joblist) {
    List<String> jobs = new ArrayList<>();
    if ( joblist != null ) {
      for (String job: joblist.trim().split(",")) {
          if (!job.isEmpty()) {
              jobs.add(job.trim().toLowerCase());
          }
      }
    }
    return jobs;
  }

    /**
   * Converts a blacklist/whitelist string into a String array.
   * This is the implementation for when the Use Regex checkbox is enabled
   *
   * @param joblist - A String containing a set of job name regexes and tags.
   * @return a String List representing the job names to be whitelisted/blacklisted and its associated tags. 
   *         Returns empty string if blacklist is null.
   */
  private static List<List<String>> regexJoblistStringtoList(final String joblist) {
    List<List<String>> jobs = new ArrayList<>();
    if ( joblist != null  && joblist.length() != 0) {
      for (String job: joblist.split("\\r?\\n")) {
        List<String> jobAndTags = new ArrayList<>();
        for (String item: job.split(",")) {
          if (!item.isEmpty()) {
            jobAndTags.add(item);
          }
        }
        jobs.add(jobAndTags);
      }
    }
    return jobs;
  }

  /**
   * This method parses the contents of the configured JobPerformanceStats tags. If they are present.
   * Takes the current build as a parameter. And returns the expanded tags and their
   * values in a HashMap.
   *
   * Always returns a HashMap, that can be empty, if no tagging is configured.
   *
   * @param run - Current build
   * @param listener - Current listener
   * @return A {@link HashMap} containing the key,value pairs of tags. Never null.
   * @throws IOException if an error occurs when reading from any objects
   * @throws InterruptedException if an interrupt error occurs
   */
  @Nonnull
  public static HashMap<String,String> parseTagList(Run run, TaskListener listener) throws IOException,
          InterruptedException {
    HashMap<String,String> map = new HashMap<String, String>();

    JobPerformanceStatsJobProperty property = JobPerformanceStatsUtilities.retrieveProperty(run);

    // If Null, nothing to retrieve
    if( property == null ) {
      return map;
    }

    String prop = property.getTagProperties();

    if( !property.isTagFileEmpty() ) {
      String dataFromFile = property.readTagFile(run);
      if(dataFromFile != null) {
        for(String tag : dataFromFile.split("\\r?\\n")) {
          String[] expanded = run.getEnvironment(listener).expand(tag).split("=");
          if( expanded.length > 1 ) {
            map.put(expanded[0], expanded[1]);
            logger.fine(String.format("Emitted tag %s:%s", expanded[0], expanded[1]));
          } else {
            logger.fine(String.format("Ignoring the tag %s. It is empty.", tag));
          }
        }
      }
    }

    if( !property.isTagPropertiesEmpty() ) {
      for(String tag : prop.split("\\r?\\n")) {
        String[] expanded = run.getEnvironment(listener).expand(tag).split("=");
        if( expanded.length > 1 ) {
          map.put(expanded[0], expanded[1]);
          logger.fine(String.format("Emitted tag %s:%s", expanded[0], expanded[1]));
        } else {
          logger.fine(String.format("Ignoring the tag %s. It is empty.", tag));
        }
      }
    }

    return map;
  }

  /**
   * Builds extraTags if any are configured in the Job.
   *
   * @param run - Current build
   * @param listener - Current listener
   * @return A {@link HashMap} containing the key,value pairs of tags if any.
   */
  public static HashMap<String,String> buildExtraTags(Run run, TaskListener listener) {
    HashMap<String,String> extraTags = new HashMap<String, String>();
    try {
      extraTags = JobPerformanceStatsUtilities.parseTagList(run, listener);
    } catch (IOException ex) {
      logger.severe(ex.getMessage());
    } catch (InterruptedException ex) {
      logger.severe(ex.getMessage());
    }
    return extraTags;
  }

  /**
   *
   * @param r - Current build.
   * @return - The configured {@link JobPerformanceStatsJobProperty}. Null if not there
   */
  @CheckForNull
  public static JobPerformanceStatsJobProperty retrieveProperty(Run r) {
    JobPerformanceStatsJobProperty property = (JobPerformanceStatsJobProperty)r.getParent()
            .getProperty(JobPerformanceStatsJobProperty.class);
    return property;
  }
  /**
   * Getter function to return either the saved hostname global configuration,
   * or the hostname that is set in the Jenkins host itself. Returns null if no
   * valid hostname is found.
   * <p>
   * Tries, in order:
   *    Jenkins configuration
   *    Jenkins hostname environment variable
   *    Unix hostname via `/bin/hostname -f`
   *    Localhost hostname
   *
   * @param envVars - An EnvVars object containing a set of environment variables.
   * @return a human readable String for the hostname.
   */
  public static String getHostname(final EnvVars envVars) {
    String[] UNIX_OS = {"mac", "linux", "freebsd", "sunos"};

    // Check hostname configuration from Jenkins
    String hostname = JobPerformanceStatsUtilities.getHostName();
    if ( (hostname != null) && isValidHostname(hostname) ) {
      logger.fine(String.format("Using hostname set in 'Manage Plugins'. Hostname: %s", hostname));
      return hostname;
    }

    // Check hostname using jenkins env variables
    if ( envVars.get("HOSTNAME") != null ) {
      hostname = envVars.get("HOSTNAME");
    }
    if ( (hostname != null) && isValidHostname(hostname) ) {
      logger.fine(String.format("Using hostname found in $HOSTNAME host environment variable. "
                                + "Hostname: %s", hostname));
      return hostname;
    }

    // Check OS specific unix commands
    String os = getOS();
    if ( Arrays.asList(UNIX_OS).contains(os) ) {
      // Attempt to grab unix hostname
      try {
        String[] cmd = {"/bin/hostname", "-f"};
        Process proc = Runtime.getRuntime().exec(cmd);
        InputStream in = proc.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder out = new StringBuilder();
        String line;
        while ( (line = reader.readLine()) != null ) {
          out.append(line);
        }
        reader.close();

        hostname = out.toString();
      } catch (Exception e) {
        logger.severe(e.getMessage());
      }

      // Check hostname
      if ( (hostname != null) && isValidHostname(hostname) ) {
        logger.fine(String.format("Using unix hostname found via `/bin/hostname -f`. Hostname: %s",
                                  hostname));
        return hostname;
      }
    }

    // Check localhost hostname
    try {
      hostname = Inet4Address.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      logger.fine(String.format("Unknown hostname error received for localhost. Error: %s", e));
    }
    if ( (hostname != null) && isValidHostname(hostname) ) {
      logger.fine(String.format("Using hostname found via "
                                + "Inet4Address.getLocalHost().getHostName()."
                                + " Hostname: %s", hostname));
      return hostname;
    }

    // Never found the hostname
    if ( (hostname == null) || "".equals(hostname) ) {
      logger.warning("Unable to reliably determine host name. You can define one in "
                     + "the 'Manage Plugins' section under the 'JobPerformanceStats Plugin' section.");
    }
    return null;
  }

  /**
   * Validator function to ensure that the hostname is valid. Also, fails on
   * empty String.
   *
   * @param hostname - A String object containing the name of a host.
   * @return a boolean representing the validity of the hostname
   */
  public static final Boolean isValidHostname(final String hostname) {
    String[] localHosts = {"localhost", "localhost.localdomain",
                           "localhost6.localdomain6", "ip6-localhost"};
    String VALID_HOSTNAME_RFC_1123_PATTERN = "^(([a-zA-Z0-9]|"
                                             + "[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*"
                                             + "([A-Za-z0-9]|"
                                             + "[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$";
    String host = hostname.toLowerCase();

    // Check if hostname is local
    if ( Arrays.asList(localHosts).contains(host) ) {
      logger.fine(String.format("Hostname: %s is local", hostname));
      return false;
    }

    // Ensure proper length
    if ( hostname.length() > JobPerformanceStatsBuildListener.MAX_HOSTNAME_LEN ) {
      logger.fine(String.format("Hostname: %s is too long (max length is %s characters)",
                                hostname, JobPerformanceStatsBuildListener.MAX_HOSTNAME_LEN));
      return false;
    }

    // Check compliance with RFC 1123
    Pattern r = Pattern.compile(VALID_HOSTNAME_RFC_1123_PATTERN);
    Matcher m = r.matcher(hostname);

    // Final check: Hostname matches RFC1123?
    return m.find();
  }

  /**
   * @param daemonHost - The host to check
   *
   * @return - A boolean that checks if the daemonHost is valid
   */
  public static boolean isValidDaemon(final String daemonHost) {
    if(!daemonHost.contains(":")) {
      logger.info("Daemon host does not contain the port seperator ':'");
      return false;
    }

    String hn = daemonHost.split(":")[0];
    String pn = daemonHost.split(":").length > 1 ? daemonHost.split(":")[1] : "";

    if(StringUtils.isBlank(hn)) {
      logger.info("Daemon host part is empty");
      return false;
    }

    //Match ports [1024-65535]
    Pattern p = Pattern.compile("^(102[4-9]|10[3-9]\\d|1[1-9]\\d{2}|[2-9]\\d{3}|[1-5]\\d{4}|6[0-4]"
            + "\\d{3}|65[0-4]\\d{2}|655[0-2]\\d|6553[0-5])$");

    boolean match = p.matcher(pn).find();

    if(!match) {
      logger.info("Port number is invalid must be in the range [1024-65535]");
    }

    return match;
  }

  /**
   * @param targetMetricURL - The API URL which the plugin will report to.
   *
   * @return - A boolean that checks if the targetMetricURL is valid
   */
  public static boolean isValidMetricURL(final String targetMetricURL) {
    if(!targetMetricURL.contains("http")) {
      logger.info("The field must be configured in the form <http|https>://<url>/");
      return false;
    }

    if(StringUtils.isBlank(targetMetricURL)) {
      logger.info("Empty API URL");
      return false;
    }

    return true;
  }

  /**
   * Safe getter function to make sure an exception is not reached.
   *
   * @param data - A JSONObject containing a set of key/value pairs.
   * @param key - A String to be used to lookup a value in the JSONObject data.
   * @return a String representing data.get(key), or "null" if it doesn't exist
   */
  public static String nullSafeGetString(final JSONObject data, final String key) {
    if ( data.get(key) != null ) {
      return data.get(key).toString();
    } else {
      return "null";
    }
  }

  /**
   * Assembles a {@link JSONArray} from metadata available in the
   * {@link JSONObject} builddata. Returns a {@link JSONArray} with the set
   * of tags.
   *
   * @param builddata - A JSONObject containing a builds metadata.
   * @param extra - A list of tags, that are contributed via {@link JobPerformanceStatsJobProperty}.
   * @return a JSONArray containing a specific subset of tags retrieved from a builds metadata.
   */
  public static JSONArray assembleTags(final JSONObject builddata, final Map<String,String> extra) {
    JSONArray tags = new JSONArray();

    tags.add("job:" + builddata.get("job"));
    if ( (builddata.get("node") != null) && JobPerformanceStatsUtilities.getJobPerformanceStatsDescriptor().getTagNode() ) {
      tags.add("node:" + builddata.get("node"));
    }

    if ( builddata.get("result") != null ) {
      tags.add("result:" + builddata.get("result"));
    }

    if ( builddata.get("branch") != null && !extra.containsKey("branch") ) {
      tags.add("branch:" + builddata.get("branch"));
    }

    //Add the extra tags here
    for(Map.Entry entry : extra.entrySet()) {
      tags.add(String.format("%s:%s", entry.getKey(), entry.getValue()));
      logger.info(String.format("Emitted tag %s:%s", entry.getKey(), entry.getValue()));
    }

    return tags;
  }

  /**
   * Converts from a double to a human readable string, representing a time duration.
   *
   * @param duration - A Double with a duration in seconds.
   * @return a human readable String representing a time duration.
   */
  public static String durationToString(final double duration) {
    String output = "(";
    String format = "%.2f";
    if ( duration < JobPerformanceStatsBuildListener.MINUTE ) {
      output = output + String.format(format, duration) + " secs)";
    } else if ( (JobPerformanceStatsBuildListener.MINUTE <= duration)
                && (duration < JobPerformanceStatsBuildListener.HOUR) ) {
      output = output + String.format(format, duration / JobPerformanceStatsBuildListener.MINUTE)
               + " mins)";
    } else if ( JobPerformanceStatsBuildListener.HOUR <= duration ) {
      output = output + String.format(format, duration / JobPerformanceStatsBuildListener.HOUR)
               + " hrs)";
    }

    return output;
  }

  /**
   * Converts the returned String from calling run.getParent().getFullName(),
   * to a String, usable as a tag.
   *
   * @param fullDisplayName - A String object representing a job's fullDisplayName
   * @return a human readable String representing the fullDisplayName of the Job, in a
   *         format usable as a tag.
   */
  public static String normalizeFullDisplayName(final String fullDisplayName) {
    String normalizedName = fullDisplayName.replaceAll("»", "/").replaceAll(" ", "");
    return normalizedName;
  }
}

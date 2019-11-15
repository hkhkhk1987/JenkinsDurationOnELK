package org.jobperformancestats.jenkins.plugins.jobperformancestats;

import java.util.HashMap;

import hudson.model.Hudson;
import hudson.model.Result;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Class that implements the {@link JobPerformanceStatsEvent}. This event produces an event payload with a
 * with a proper description for a finished build.
 */
public class BuildFinishedEventImpl implements JobPerformanceStatsEvent {

  private JSONObject builddata;
  private JSONArray stagebuilddata;
  private JSONArray stepbuilddata;
  private HashMap<String,String> tags;

  public BuildFinishedEventImpl(JSONObject buildData, JSONArray stagebuilddata, JSONArray stepbuilddata, HashMap<String,String> buildTags)  {
    this.builddata = buildData;
    this.stagebuilddata = stagebuilddata;
    this.stepbuilddata = stepbuilddata;
    this.tags = buildTags;
  }

  @Override
  public JSONArray createStagePayload() {
    JSONArray stagepayload = new JSONArray();
    stagepayload = stagebuilddata;
    return stagepayload;
  }

  @Override
  public JSONArray createStepPayload() {
    JSONArray steppayload = new JSONArray();
    steppayload = stepbuilddata;
    return steppayload;
  }
  //Creates the raw json payload for this event.
  @Override
  public JSONObject createPayload() {
    JSONObject payload = new JSONObject();
    // Add event_type to assist in roll-ups
    payload.put("event_type", "build result"); // string
    String hostname = JobPerformanceStatsUtilities.nullSafeGetString(builddata, "hostname");
    String number = JobPerformanceStatsUtilities.nullSafeGetString(builddata, "number");
    String buildurl = JobPerformanceStatsUtilities.nullSafeGetString(builddata, "buildurl");
    String job = JobPerformanceStatsUtilities.nullSafeGetString(builddata, "job");
    //String starttime = JobPerformanceStatsUtilities.nullSafeGetString(builddata, "starttime");
    long starttime = builddata.getLong("starttime");

    long timestamp = builddata.getLong("timestamp");
    String message = "";

    // Build title
    StringBuilder title = new StringBuilder();
    title.append(job).append(" build #").append(number);

    String buildResult = builddata.get("result") != null ? builddata.get("result").toString() : Result.NOT_BUILT.toString() ;

    message = "%%% \n [See results for build #" + number + "](" + buildurl + ") ";
    title.append(" " + buildResult.toLowerCase());



    if (Result.SUCCESS.toString().equals(buildResult)) {
      payload.put("alert_type", "SUCCESS");
      payload.put("priority", "low");
    } else if (Result.UNSTABLE.toString().equals(buildResult)) {
      payload.put("alert_type", "UNSTABLE");
    } else if (Result.ABORTED.toString().equals(buildResult)) {
      payload.put("alert_type", "ABORTED");
    } else if (Result.FAILURE.toString().equals(buildResult)) {
      payload.put("alert_type", "FAILURE");
    }
    
    title.append(" on ").append(hostname);
    // Add duration
    if (builddata.get("duration") != null) {
      message = message + JobPerformanceStatsUtilities.durationToString(builddata.getDouble("duration"));
    }

    // Close markdown
    message = message + " \n %%%";

    // Build payload
    payload.put("title", title.toString());
    payload.put("text", message);
    payload.put("date_happened", timestamp);
    payload.put("host", hostname);
    payload.put("number", number);
    payload.put("buildurl", buildurl);
    payload.put("starttime", starttime);
    payload.put("job", job);
    payload.put("result", builddata.get("result"));
    payload.put("duration", builddata.getDouble("duration"));
    payload.put("tags", JobPerformanceStatsUtilities.assembleTags(builddata, tags));
    payload.put("node", builddata.get("node"));
    payload.put("aggregation_key", job);
    payload.put("source_type_name", "jenkins");

    return payload;
  }
}

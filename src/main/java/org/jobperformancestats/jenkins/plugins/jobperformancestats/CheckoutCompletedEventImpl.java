package org.jobperformancestats.jenkins.plugins.jobperformancestats;

import java.util.HashMap;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * Class that implements the {@link JobPerformanceStatsEvent}. This event produces an event payload with a
 * with a proper description for a completed checkout.
 */
public class CheckoutCompletedEventImpl implements JobPerformanceStatsEvent {

  private JSONObject builddata;
  private HashMap<String,String> tags;

  public CheckoutCompletedEventImpl(JSONObject builddata, HashMap<String,String> tags)  {
    this.builddata = builddata;
    this.tags = tags;
  }

  @Override
  public JSONArray createStagePayload() {
    JSONArray stagepayload = new JSONArray();
    return stagepayload;
  }

  @Override
  public JSONArray createStepPayload() {
    JSONArray steppayload = new JSONArray();
    return steppayload;
  }
  /**
   *
   * @return - A JSON payload. See {@link JobPerformanceStatsEvent#createPayload()}
   */
  @Override
  public JSONObject createPayload() {
    JSONObject payload = new JSONObject();
    // Add event_type to assist in roll-ups
    payload.put("event_type", "build checkout"); // string
    String hostname = JobPerformanceStatsUtilities.nullSafeGetString(builddata, "hostname");
    String number = JobPerformanceStatsUtilities.nullSafeGetString(builddata, "number");
    String buildurl = JobPerformanceStatsUtilities.nullSafeGetString(builddata, "buildurl");
    String job = JobPerformanceStatsUtilities.nullSafeGetString(builddata, "job");
    long timestamp = builddata.getLong("timestamp");
    String message = "";

    // Build title
    StringBuilder title = new StringBuilder();
    title.append(job).append(" build #").append(number);
    title.append(" checkout finished");
    message = "%%% \n [Follow build #" + number + " progress](" + buildurl + ") ";

    title.append(" on ").append(hostname);
    // Add duration
    if (builddata.get("duration") != null) {
      message = message + JobPerformanceStatsUtilities.durationToString(builddata.getDouble("duration"));
    }

    // Close markdown
    message = message + " \n %%%";

    // Build payload
    payload.put("alert_type", "info");
    payload.put("priority", "low");
    payload.put("title", title.toString());
    payload.put("text", message);
    payload.put("date_happened", timestamp);
    payload.put("event_type", builddata.get("event_type"));
    payload.put("host", hostname);
    payload.put("result", builddata.get("result"));
    payload.put("tags", JobPerformanceStatsUtilities.assembleTags(builddata, tags));
    payload.put("aggregation_key", job);
    payload.put("source_type_name", "jenkins");

    return payload;
  }
}

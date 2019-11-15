package org.jobperformancestats.jenkins.plugins.jobperformancestats;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 *
 * Marker interface for JobPerformanceStats events.
 */
public interface JobPerformanceStatsEvent  {
  /**
   *
   * @return The payload for the given event. Events usually have a custom message
   *
   */
  public JSONObject createPayload();
  public JSONArray createStagePayload();
  public JSONArray createStepPayload();
}

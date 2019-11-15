package org.jobperformancestats.jenkins.plugins.jobperformancestats;
import java.io.*;
import hudson.ProxyConfiguration;
import hudson.util.Secret;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import io.prometheus.client.SimpleCollector;
import io.prometheus.client.Summary;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import java.util.Arrays;
import java.io.File;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.PushGateway;
import java.util.*;
import java.text.*;

import org.apache.commons.lang.RandomStringUtils;

/**
 *
 * This class is used to collect all methods that has to do with transmitting
 * data to JobPerformanceStats.
 */
public class JobPerformanceStatsHttpRequests {

  private static final Logger logger =  Logger.getLogger(JobPerformanceStatsHttpRequests.class.getName());
  /**
   * Returns an HTTP url connection given a url object. Supports jenkins configured proxy.
   *
   * @param url - a URL object containing the URL to open a connection to.
   * @return a HttpURLConnection object.
   * @throws IOException if HttpURLConnection fails to open connection
   */
  public static HttpURLConnection getHttpURLConnection(final URL url) throws IOException {
    HttpURLConnection conn = null;
    ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;

    /* Attempt to use proxy */
    if (proxyConfig != null) {
      Proxy proxy = proxyConfig.createProxy(url.getHost());
      if (proxy != null && proxy.type() == Proxy.Type.HTTP) {
        logger.fine("Attempting to use the Jenkins proxy configuration");
        conn = (HttpURLConnection) url.openConnection(proxy);
        if (conn == null) {
          logger.fine("Failed to use the Jenkins proxy configuration");
        }
      }
    } else {
      logger.fine("Jenkins proxy configuration not found");
    }

    /* If proxy fails, use HttpURLConnection */
    if (conn == null) {
      conn = (HttpURLConnection) url.openConnection();
      logger.fine("Using HttpURLConnection, without proxy");
    }

    return conn;
  }

  public static void sendHttpRequest(URL baseurl, String jsonInputString) {
    try {
      String id = RandomStringUtils.randomAlphanumeric(10);
      URL url = new URL (baseurl + id);
      HttpURLConnection con = (HttpURLConnection)url.openConnection();
      con.setRequestMethod("PUT");
      con.setRequestProperty("Content-Type", "application/json; utf-8");
      con.setRequestProperty("Accept", "application/json");
      con.setDoOutput(true);

      try(OutputStream os = con.getOutputStream()) {
        byte[] input = jsonInputString.getBytes("utf-8");
        os.write(input, 0, input.length);
      }
      try(BufferedReader br = new BufferedReader(
              new InputStreamReader(con.getInputStream(), "utf-8"))) {
        StringBuilder response = new StringBuilder();
        String responseLine = null;
        while ((responseLine = br.readLine()) != null) {
          response.append(responseLine.trim());
        }
        System.out.println(response.toString());
      }
      if (con != null) {
        con.disconnect();
      }
    } catch (Exception e) {
      logger.severe(e.toString());
    }
  }

  /**
   * Sends a an event to the JobPerformanceStats API, including the event payload.
   *
   * @param evt - The finished {@link JobPerformanceStatsEvent} to send
   */
  public static void sendEvent(JobPerformanceStatsEvent evt) {
    logger.fine("Sending event");
    try {
      JobPerformanceStatsHttpRequests.put(evt.createPayload(),evt.createStagePayload(),evt.createStepPayload(),JobPerformanceStatsBuildListener.EVENT);
    } catch (Exception e) {
      logger.severe(e.toString());
    }
  }

  /**
   * Posts a given {@link JSONObject} payload to the pushgateway service
   *
   * @param payload - A JSONObject containing a specific subset of a builds metadata.
   * @param type - A String containing the URL subpath pertaining to the type of API post required.
   * @return a boolean to signify the success or failure of the HTTP POST request.
   * @throws IOException if HttpURLConnection fails to open connection
   */
  public static Boolean put(final JSONObject payload, final JSONArray stagepayload,  final JSONArray steppayload, final String type) throws IOException {

    String number = JobPerformanceStatsUtilities.nullSafeGetString(payload, "number");
    String buildurl = JobPerformanceStatsUtilities.nullSafeGetString(payload, "buildurl").replace("%2F", "/");
    String job = JobPerformanceStatsUtilities.nullSafeGetString(payload, "job").replace("%2F", "/");
    String jobstatus = JobPerformanceStatsUtilities.nullSafeGetString(payload, "alert_type");
    String jobduration = JobPerformanceStatsUtilities.nullSafeGetString(payload, "duration");
    String node = JobPerformanceStatsUtilities.nullSafeGetString(payload, "node");

    String elk_hostname = JobPerformanceStatsUtilities.getJobPerformanceStatsDescriptor().getHostname();

    double duration = Double.parseDouble(jobduration);
    Long starttime = payload.getLong("starttime");
    Long timestamp = (starttime)*1000;
    String start_time = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new java.util.Date(timestamp));

    logger.finer("start_time is "+ start_time);

    try {

      logger.finer("Writing to ELK start...");

      Date date = new Date();

      String index_date = new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
      String jsonInputString = "{\"jobstarttime\":\""+ start_time +"\", \"jobname\":\"" + job + "\", \"jobid\":\"" + number + "\", \"jobstatus\":\"" + jobstatus +"\", \"jobduration\":" + duration +", \"joburl\":\"" + buildurl +"\", \"node\":\"" + node +"\", \"type\":\"job\"}";
      URL baseurl = new URL ("http://" + elk_hostname + ":9200/jenkins-" + index_date +"/doc/");

      try {
        JobPerformanceStatsHttpRequests.sendHttpRequest(baseurl, jsonInputString);
      } catch (Exception e) {
        logger.severe(e.toString());
      }

      logger.finer("Writing to ELK finish...");

      try {
        logger.finer("Writing to ELK stage start...");

        for (Object obj : stagepayload) {
          JSONObject jsonObject = (JSONObject) obj;

          logger.finer("stage infos: ..." + jsonObject.toString());

          String stage_jsonInputString = "{\"jobstarttime\":\"" + jsonObject.getJSONObject("starttime").getString("time")+ "\", \"jobname\":\"" + job + "\", \"jobid\":\"" + number + "\", \"joburl\":\"" + buildurl + "\", \"type\":\"stage\", \"name\":\"" + jsonObject.getString("name") + "\", \"status\":\"" + jsonObject.getString("status") + "\", \"duration\":" + jsonObject.getLong("duration") / 1000 + "}";

          try {
            JobPerformanceStatsHttpRequests.sendHttpRequest(baseurl, stage_jsonInputString);
          } catch (Exception e) {
            logger.severe(e.toString());
          }
        }
        logger.finer("Writing to ELK stage finish...");
      } catch (Exception e) {
        logger.severe(String.format("Client error in stage: %s", e.toString()));
        //return false;
      }

      try {

        logger.finer("Writing to ELK step finish...");

        for (Object step_obj : steppayload) {
          JSONObject step_jsonObject = (JSONObject) step_obj;
          logger.finer("step infos: ..." + step_jsonObject.toString());

          String step_jsonInputString = "{\"jobstarttime\":\"" + step_jsonObject.getJSONObject("starttime").getString("time") + "\", \"jobname\":\"" + job + "\", \"jobid\":\"" + number + "\", \"joburl\":\"" + buildurl + "\", \"stagename\":\"" + step_jsonObject.getString("stagename") + "\", \"type\":\"step\", \"name\":\"" + step_jsonObject.getString("name") + "\", \"status\":\"" + step_jsonObject.getString("status") + "\", \"duration\":" + step_jsonObject.getLong("duration") / 1000 + "}";

          try {
            JobPerformanceStatsHttpRequests.sendHttpRequest(baseurl, step_jsonInputString);
          } catch (Exception e) {
            logger.severe(e.toString());
          }

        }
        logger.finer("Writing to ELK step finish...");
      }catch (Exception e) {
        logger.severe(String.format("Client error in step: %s", e.toString()));
        //return false;
      }

      return true;

    } catch (Exception e) {

      logger.severe(String.format("Client error: %s", e.toString()));

      return false;
    }
  }


  /**
   * Posts a given {@link JSONObject} payload to the JobPerformanceStats API, using the
   * user configured apiKey.
   *
   * @param payload - A JSONObject containing a specific subset of a builds metadata.
   * @param type - A String containing the URL subpath pertaining to the type of API post required.
   * @return a boolean to signify the success or failure of the HTTP POST request.
   * @throws IOException if HttpURLConnection fails to open connection
   */
  public static Boolean post(final JSONObject payload, final String type) throws IOException {
    String urlParameters = "?api_key=" + Secret.toString(JobPerformanceStatsUtilities.getApiKey());
    HttpURLConnection conn = null;
    try {
      logger.finer("Setting up HttpURLConnection...");
      conn = JobPerformanceStatsHttpRequests.getHttpURLConnection(new URL(JobPerformanceStatsUtilities.getTargetMetricURL() + type + urlParameters));
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setUseCaches(false);
      conn.setDoInput(true);
      conn.setDoOutput(true);
      OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "utf-8");
      logger.finer("Writing to OutputStreamWriter...");
      wr.write(payload.toString());
      wr.close();
      BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
      StringBuilder result = new StringBuilder();
      String line;
      while ((line = rd.readLine()) != null) {
        result.append(line);
      }
      rd.close();
      JSONObject json = (JSONObject) JSONSerializer.toJSON(result.toString());
      if ("ok".equals(json.getString("status"))) {
        logger.finer(String.format("API call of type '%s' was sent successfully!", type));
        logger.finer(String.format("Payload: %s", payload));
        return true;
      } else {
        logger.fine(String.format("API call of type '%s' failed!", type));
        logger.fine(String.format("Payload: %s", payload));
        return false;
      }
    } catch (Exception e) {
      if (conn.getResponseCode() == JobPerformanceStatsBuildListener.HTTP_FORBIDDEN) {
        logger.severe("Hmmm, your API key may be invalid. We received a 403 error.");
      } else {
        logger.severe(String.format("Client error: %s", e.toString()));
      }
      return false;
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
      return true;
    }
  }

}
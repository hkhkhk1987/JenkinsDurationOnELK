package org.jobperformancestats.jenkins.plugins.jobperformancestats;

import hudson.EnvVars;
import hudson.model.*;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({JobPerformanceStatsHttpRequests.class, JobPerformanceStatsUtilities.class, Jenkins.class})
public class JobPerformanceStatsBuildListenerTest {
    @Mock
    private Jenkins jenkins;

    private JobPerformanceStatsBuildListener jobperformancestatsBuildListener;

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);

        PowerMockito.mockStatic(JobPerformanceStatsUtilities.class);
        when(JobPerformanceStatsUtilities.isJobTracked(anyString())).thenReturn(true);
        when(JobPerformanceStatsUtilities.assembleTags(any(JSONObject.class), any(HashMap.class))).thenReturn(new JSONArray());
        PowerMockito.mockStatic(JobPerformanceStatsHttpRequests.class);

        jobperformancestatsBuildListener = spy(new JobPerformanceStatsBuildListener());
        JobPerformanceStatsBuildListener.DescriptorImpl descriptorMock = descriptor();
        when(JobPerformanceStatsUtilities.getJobPerformanceStatsDescriptor()).thenReturn(descriptorMock);
    }


    @Test
    public void onCompleted_duration_fromRun() throws Exception {
        Run run = run();
        when(run.getDuration()).thenReturn(123000L);

        
        jobperformancestatsBuildListener.onCompleted(run, mock(TaskListener.class));

        JSONObject series = capturePostMetricRequestPayload();
        assertEquals("jenkins.job.duration", series.getString("metric"));
        assertEquals(123L, valueOfFirstPoint(series), 0);
    }

    @Test
    public void onCompleted_duration_computedFromFallbackForPipelineJobs() throws Exception {
        Run run = run();
        when(run.getDuration()).thenReturn(0L); // pipeline jobs always return 0

        jobperformancestatsBuildListener.onCompleted(run, mock(TaskListener.class));
        JSONObject series = capturePostMetricRequestPayload();
        assertEquals("jenkins.job.duration", series.getString("metric"));
        assertNotEquals(0, valueOfFirstPoint(series), 0);
    }



    private Run run() throws Exception {
        Run run = mock(Run.class);
        when(run.getResult()).thenReturn(Result.SUCCESS);
        when(run.getEnvironment(any(TaskListener.class))).thenReturn(envVars());

        Job job = job();
        when(run.getParent()).thenReturn(job);

        return run;
    }

    private Job job() {
        ItemGroup parent = mock(ItemGroup.class);
        when(parent.getFullName()).thenReturn("parent");

        Job job = mock(Job.class);
        when(job.getName()).thenReturn("test-job");
        when(job.getParent()).thenReturn(parent);

        return job;
    }

    private EnvVars envVars() {
        return new EnvVars();
    }

    private JobPerformanceStatsBuildListener.DescriptorImpl descriptor() {
        return mock(JobPerformanceStatsBuildListener.DescriptorImpl.class);
    }

    private JSONObject capturePostMetricRequestPayload() throws IOException {
        PowerMockito.verifyStatic();
        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        JobPerformanceStatsHttpRequests.post(captor.capture(), eq(JobPerformanceStatsBuildListener.METRIC));

        return captor.getValue().getJSONArray("series").getJSONObject(0);
    }

    private double valueOfFirstPoint(JSONObject series) {
        return series.getJSONArray("points").getJSONArray(0).getDouble(1);
    }
}

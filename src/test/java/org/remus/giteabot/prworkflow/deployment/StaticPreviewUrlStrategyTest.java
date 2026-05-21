package org.remus.giteabot.prworkflow.deployment;

import org.junit.jupiter.api.Test;
import org.remus.giteabot.prworkflow.PrWorkflowRun;
import org.remus.giteabot.prworkflow.config.DeploymentTarget;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaticPreviewUrlStrategyTest {

    private DeploymentRequest request(String template, String configJson, int timeoutSeconds) {
        DeploymentTarget target = new DeploymentTarget();
        target.setName("vercel");
        target.setStrategyType(DeploymentStrategyType.STATIC);
        target.setConfigJson(configJson);
        target.setPreviewUrlTemplate(template);
        target.setTimeoutSeconds(timeoutSeconds);
        PrWorkflowRun run = new PrWorkflowRun();
        run.setId(7L);
        return new DeploymentRequest(run, target, "acme", "web", 99L,
                "deadbee", "feat/x",
                "https://bot.acme.io/api/workflow-callback/7/cb");
    }

    @Test
    void rejectsMissingTemplate() {
        StaticPreviewUrlStrategy s = new StaticPreviewUrlStrategy(mock(HttpClient.class), millis -> {});
        DeploymentResult r = s.trigger(request(null, "{}", 30));
        assertThat(r.status()).isEqualTo(DeploymentStatus.REJECTED);
    }

    @Test
    void noProbeMeansImmediateReady() {
        StaticPreviewUrlStrategy s = new StaticPreviewUrlStrategy(mock(HttpClient.class), millis -> {});
        DeploymentResult r = s.trigger(request(
                "https://pr-{prNumber}.preview.acme.io",
                "{\"healthcheckPath\":\"\"}", 30));
        assertThat(r.status()).isEqualTo(DeploymentStatus.READY);
        assertThat(r.previewUrl()).isEqualTo("https://pr-99.preview.acme.io");
    }

    @Test
    void templatePlaceholdersAreResolved() {
        StaticPreviewUrlStrategy s = new StaticPreviewUrlStrategy(mock(HttpClient.class), millis -> {});
        DeploymentResult r = s.trigger(request(
                "https://{branch}.{sha}.pr-{prNumber}.x.io",
                "{\"healthcheckPath\":\"\"}", 30));
        assertThat(r.previewUrl()).isEqualTo("https://feat/x.deadbee.pr-99.x.io");
    }

    @Test
    @SuppressWarnings("unchecked")
    void probeSucceedsImmediately() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        doReturn(response).when(client).send(any(), any());

        StaticPreviewUrlStrategy s = new StaticPreviewUrlStrategy(client, millis -> {});
        DeploymentResult r = s.trigger(request(
                "https://pr-{prNumber}.preview.acme.io",
                "{\"healthcheckPath\":\"/healthz\",\"expectedStatus\":200,\"intervalSeconds\":1}",
                10));
        assertThat(r.status()).isEqualTo(DeploymentStatus.READY);
        assertThat(r.previewUrl()).isEqualTo("https://pr-99.preview.acme.io");
    }

    @Test
    @SuppressWarnings("unchecked")
    void probeRecoversAfterRetries() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> bad = mock(HttpResponse.class);
        when(bad.statusCode()).thenReturn(503);
        HttpResponse<Void> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        doThrow(new IOException("connection refused")).doReturn(bad).doReturn(ok).when(client).send(any(), any());

        StaticPreviewUrlStrategy s = new StaticPreviewUrlStrategy(client, millis -> {});
        DeploymentResult r = s.trigger(request(
                "https://pr-{prNumber}.preview.acme.io",
                "{\"healthcheckPath\":\"/healthz\",\"intervalSeconds\":1}",
                10));
        assertThat(r.status()).isEqualTo(DeploymentStatus.READY);
    }

    @Test
    void probeTimesOut() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doThrow(new IOException("nope")).when(client).send(any(), any());
        StaticPreviewUrlStrategy s = new StaticPreviewUrlStrategy(client, millis -> {});
        DeploymentResult r = s.trigger(request(
                "https://pr-{prNumber}.preview.acme.io",
                "{\"healthcheckPath\":\"/healthz\",\"intervalSeconds\":1}",
                1));
        assertThat(r.status()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(r.errorMessage()).contains("ready");
    }

    @Test
    void emptyConfigJsonStillResolves() {
        StaticPreviewUrlStrategy s = new StaticPreviewUrlStrategy(mock(HttpClient.class), millis -> {});
        DeploymentResult r = s.trigger(request(
                "https://pr-{prNumber}.preview.acme.io",
                "{\"healthcheckPath\":\"\"}", 30));
        assertThat(r.status()).isEqualTo(DeploymentStatus.READY);
    }
}


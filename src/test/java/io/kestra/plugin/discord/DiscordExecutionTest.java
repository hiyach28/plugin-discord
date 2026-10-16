package io.kestra.plugin.discord;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunner;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@KestraTest
public class DiscordExecutionTest extends io.kestra.plugin.discord.AbstractDiscordTest {
    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(DiscordExecutionTest.class.getClassLoader().getResource("flows")));
        this.runner.run();
    }

    @Test
    void flow() throws Exception {
        var failedExecution = runAndCaptureExecution(
            "main-flow-that-fails",
            "discord"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data, 5000);

        assertThat(receivedData, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(receivedData, containsString(failedExecution.getId()));
        assertThat(receivedData, containsString("Failed on task `failed`"));
        assertThat(receivedData, containsString("Final task ID: failed"));
        assertThat(receivedData, containsString("Kestra Discord notification"));

        assertThat(embedFields(receivedData), is(List.of(
            Map.of("name", "Message", "value", "Disk is \"almost\" full"),
            Map.of("name", "env", "value", "prod", "inline", true),
            Map.of("name", "retries", "value", "3", "inline", true)
        )));
    }

    @Test
    void flowSuccessful() throws Exception {
        var execution = runAndCaptureExecution(
            "main-flow-that-succeeds",
            "discord-successful"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data, 5000);

        assertThat(receivedData, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(receivedData, containsString(execution.getId()));
        assertThat(FakeWebhookController.data, not(containsString("Failed on task `failed`")));
        assertThat(FakeWebhookController.data, containsString("Final task ID: success"));
        assertThat(receivedData, containsString("Kestra Discord notification"));

        // customMessage and customFields are not set in this flow, so the embed has no fields
        assertThat(firstEmbed(receivedData), not(hasKey("fields")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstEmbed(String payload) throws Exception {
        Map<String, Object> body = JacksonMapper.ofJson().readValue(payload, Map.class);
        return ((List<Map<String, Object>>) body.get("embeds")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> embedFields(String payload) throws Exception {
        return (List<Map<String, Object>>) firstEmbed(payload).get("fields");
    }
}

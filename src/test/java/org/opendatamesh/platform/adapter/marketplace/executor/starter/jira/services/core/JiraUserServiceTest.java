package org.opendatamesh.platform.adapter.marketplace.executor.starter.jira.services.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraConfig;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.config.JiraDeploymentType;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.exceptions.InternalException;
import org.opendatamesh.platform.adapter.marketplace.executor.starter.marketplace.domain.Party;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// BDMD-5340: Jira marketplace executor
class JiraUserServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JiraClient client = mock(JiraClient.class);
    private final JiraConfig config = mock(JiraConfig.class);

    private JiraUserService service(JiraDeploymentType deployment, String searchResult) throws Exception {
        when(config.deployment()).thenReturn(deployment);
        when(client.platformPath(anyString())).thenAnswer(a -> "/rest/api/2" + a.getArgument(0));
        JsonNode result = mapper.readTree(searchResult);
        when(client.get(anyString(), any())).thenReturn(result);
        return new JiraUserService(client, config);
    }

    private Party user(String identifier) {
        return new Party("user", identifier);
    }

    @Test
    void whenSeveralResultsThenExactEmailMatchPicked() throws Exception {
        JiraUserService service = service(JiraDeploymentType.CLOUD, """
                [{"accountId":"a-1","emailAddress":"jane.other@acme.com","active":true},
                 {"accountId":"a-2","emailAddress":"Jane@Acme.com","active":true}]
                """);

        assertEquals("a-2", service.resolveRequester(user("jane@acme.com")).accountId());
    }

    @Test
    void whenSingleResultWithHiddenEmailThenItIsUsed() throws Exception {
        JiraUserService service = service(JiraDeploymentType.CLOUD, "[{\"accountId\":\"a-3\",\"active\":true},{\"accountId\":\"a-4\",\"active\":false}]");

        JiraUser found = service.resolveRequester(user("jane@acme.com"));

        assertEquals("a-3", found.accountId());
        assertEquals("a-3", service.onBehalfOf(found));
        assertEquals("a-3", service.reporterField(found).get("id").asText());
    }

    @Test
    void whenNoResultThenNotFound() throws Exception {
        JiraUserService service = service(JiraDeploymentType.CLOUD, "[]");

        InternalException e = assertThrows(InternalException.class, () -> service.resolveRequester(user("ghost@acme.com")));

        assertTrue(e.getMessage().contains("Requester ghost@acme.com not found in Jira, or ambiguous (0 matches)"));
    }

    @Test
    void whenAmbiguousThenFails() throws Exception {
        JiraUserService service = service(JiraDeploymentType.CLOUD, "[{\"accountId\":\"a-1\"},{\"accountId\":\"a-2\"}]");

        InternalException e = assertThrows(InternalException.class, () -> service.resolveRequester(user("jane@acme.com")));

        assertTrue(e.getMessage().contains("(2 matches)"));
    }

    @Test
    void whenRequesterIsNotAUserThenFails() throws Exception {
        JiraUserService service = service(JiraDeploymentType.CLOUD, "[]");

        InternalException e = assertThrows(InternalException.class, () -> service.resolveRequester(new Party("team", "t")));

        assertTrue(e.getMessage().contains("require a user requester; got team"));
    }

    @Test
    void whenDataCenterThenUsernameParameterAndNameUsed() throws Exception {
        JiraUserService service = service(JiraDeploymentType.DATA_CENTER, "[{\"name\":\"jdoe\",\"emailAddress\":\"jane@acme.com\"}]");

        JiraUser found = service.resolveRequester(user("jane@acme.com"));

        ArgumentCaptor<Map<String, String>> query = ArgumentCaptor.forClass(Map.class);
        verify(client).get(eq("/rest/api/2/user/search"), query.capture());
        assertEquals("jane@acme.com", query.getValue().get("username"));
        assertEquals("jdoe", service.onBehalfOf(found));
        assertEquals("jdoe", service.reporterField(found).get("name").asText());
    }
}

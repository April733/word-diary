package spring.nexusfuture.word_diary;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FullFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    public void setupInvite() {
        // Ensure there's an invite code available for registration
        jdbcTemplate.update("INSERT INTO wd_invite_code(seq_no, invite_code, member_tier, is_deleted) VALUES (?,?,?,0)", 1, "INVITE-TEST-1", 1);
    }

    @Test
    public void fullFlow_register_createNotebook_addWord_study() {
        // 1. Register
        String registerUrl = baseUrl() + "/api/user/register";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String registerJson = "{\"username\":\"e2e_user\",\"password\":\"pass123\",\"phone\":\"+10000000000\",\"inviteCode\":\"INVITE-TEST-1\"}";
        ResponseEntity<Map> regResp = restTemplate.postForEntity(registerUrl, new HttpEntity<>(registerJson, headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, regResp.getStatusCode());

        // 2. Login
        String loginUrl = baseUrl() + "/api/user/login";
        String loginJson = "{\"username\":\"e2e_user\",\"password\":\"pass123\"}";
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(loginUrl, new HttpEntity<>(loginJson, headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        Map body = loginResp.getBody();
        Assertions.assertNotNull(body);
        Map data = (Map) body.get("data");
        Assertions.assertNotNull(data.get("token"));
        String token = (String) data.get("token");

        // Authorization header for subsequent requests
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // 3. Create notebook
        String createNotebookUrl = baseUrl() + "/api/notebooks";
        String bookJson = "{\"bookName\":\"E2E Book\",\"bookDesc\":\"desc\"}";
        ResponseEntity<Map> bookResp = restTemplate.exchange(createNotebookUrl, HttpMethod.POST, new HttpEntity<>(bookJson, headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, bookResp.getStatusCode());
        Map bookData = (Map) bookResp.getBody().get("data");
        Number notebookIdNum = (Number) bookData.get("id");
        Long notebookId = notebookIdNum.longValue();

        // 4. Add a word to notebook
        String addWordUrl = baseUrl() + "/api/notebooks/" + notebookId + "/words";
        String wordJson = "{\"wordProto\":\"example\",\"usPhonetic\":\"\",\"ukPhonetic\":\"\",\"partSpeechMean\":\"n. 示例\",\"sentenceExample\":\"This is an example.\"}";
        ResponseEntity<Map> addWordResp = restTemplate.exchange(addWordUrl, HttpMethod.POST, new HttpEntity<>(wordJson, headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, addWordResp.getStatusCode());

        // 5. List words and ensure our word exists
        String listWordsUrl = baseUrl() + "/api/notebooks/" + notebookId + "/words";
        ResponseEntity<Map> listResp = restTemplate.exchange(listWordsUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, listResp.getStatusCode());
        Map listBody = listResp.getBody();
        List items = (List) listBody.get("data");
        Assertions.assertTrue(items.size() >= 1);

        // 6. Debug plans to get planId
        String debugPlansUrl = baseUrl() + "/api/study/debug/plans";
        ResponseEntity<Map> plansResp = restTemplate.exchange(debugPlansUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, plansResp.getStatusCode());
        List<Map<String,Object>> plans = (List<Map<String,Object>>) plansResp.getBody().get("data");
        Assertions.assertFalse(plans.isEmpty());
        Long planId = ((Number) plans.get(0).get("planId")).longValue();

        // 7. First learn (mark remembered)
        String firstLearnUrl = baseUrl() + "/api/study/first-learn/" + planId;
        String studyReq = "{\"result\":1}";
        ResponseEntity<Map> studyResp = restTemplate.exchange(firstLearnUrl, HttpMethod.POST, new HttpEntity<>(studyReq, headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, studyResp.getStatusCode());

        // 8. Get records and stats
        String recordsUrl = baseUrl() + "/api/study/records";
        ResponseEntity<Map> recordsResp = restTemplate.exchange(recordsUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, recordsResp.getStatusCode());
        List recs = (List) recordsResp.getBody().get("data");
        Assertions.assertTrue(recs.size() >= 1);

        String statsUrl = baseUrl() + "/api/study/stats";
        ResponseEntity<Map> statsResp = restTemplate.exchange(statsUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, statsResp.getStatusCode());
        Assertions.assertNotNull(statsResp.getBody().get("data"));
    }
}


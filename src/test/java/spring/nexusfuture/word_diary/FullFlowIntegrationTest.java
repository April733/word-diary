package spring.nexusfuture.word_diary;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FullFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    public void setup() {
        // no-op：注册不再依赖邀请码
    }

    @Test
    public void fullFlow_register_createNotebook_addWord_study() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String registerUrl = baseUrl() + "/api/user/register";
        String registerJson = "{\"username\":\"e2e_user\",\"password\":\"pass123\",\"phone\":\"+10000000000\",\"code\":\"000000\",\"registerType\":\"phone\"}";
        ResponseEntity<Map> regResp = restTemplate.postForEntity(registerUrl, new HttpEntity<>(registerJson, headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, regResp.getStatusCode());

        String loginUrl = baseUrl() + "/api/user/login";
        String loginJson = "{\"username\":\"e2e_user\",\"password\":\"pass123\"}";
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(loginUrl, new HttpEntity<>(loginJson, headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        Map body = loginResp.getBody();
        Assertions.assertNotNull(body);
        Map data = (Map) body.get("data");
        Assertions.assertNotNull(data.get("token"));
        String token = (String) data.get("token");

        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        String createNotebookUrl = baseUrl() + "/api/notebooks";
        String bookJson = "{\"bookName\":\"E2E Book\",\"bookDesc\":\"desc\"}";
        ResponseEntity<Map> bookResp = restTemplate.exchange(createNotebookUrl, HttpMethod.POST, new HttpEntity<>(bookJson, headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, bookResp.getStatusCode());
        Map bookData = (Map) bookResp.getBody().get("data");
        Long notebookId = ((Number) bookData.get("id")).longValue();

        String addWordUrl = baseUrl() + "/api/notebooks/" + notebookId + "/words";
        String wordJson = "{\"wordProto\":\"example\",\"usPhonetic\":\"\",\"ukPhonetic\":\"\",\"partSpeechMean\":\"n. 示例\",\"sentenceExample\":\"This is an example.\"}";
        ResponseEntity<Map> addWordResp = restTemplate.exchange(addWordUrl, HttpMethod.POST, new HttpEntity<>(wordJson, headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, addWordResp.getStatusCode());

        String listWordsUrl = baseUrl() + "/api/notebooks/" + notebookId + "/words";
        ResponseEntity<Map> listResp = restTemplate.exchange(listWordsUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, listResp.getStatusCode());
        List items = (List) listResp.getBody().get("data");
        Assertions.assertTrue(items.size() >= 1);

        String openSessionUrl = baseUrl() + "/api/study-today/session/open";
        ResponseEntity<Map> sessionResp = restTemplate.exchange(openSessionUrl, HttpMethod.POST, new HttpEntity<>(headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, sessionResp.getStatusCode());
        Map sessionData = (Map) sessionResp.getBody().get("data");
        Assertions.assertNotNull(sessionData.get("current"));
        Map current = (Map) sessionData.get("current");
        Long wordId = ((Number) current.get("wordId")).longValue();
        String phase = (String) sessionData.get("phase");

        String answerUrl = baseUrl() + "/api/study-today/answer";
        String answerJson = "{\"wordId\":" + wordId + ",\"feedback\":1,\"phase\":\"" + phase + "\"}";
        ResponseEntity<Map> answerResp = restTemplate.exchange(answerUrl, HttpMethod.POST, new HttpEntity<>(answerJson, headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, answerResp.getStatusCode());

        // 第一遍认识不改库；再答一遍才毕业
        ResponseEntity<Map> answer2Resp = restTemplate.exchange(answerUrl, HttpMethod.POST, new HttpEntity<>(answerJson, headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, answer2Resp.getStatusCode());

        String statsUrl = baseUrl() + "/api/study-plan/stats";
        ResponseEntity<Map> statsResp = restTemplate.exchange(statsUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Assertions.assertEquals(HttpStatus.OK, statsResp.getStatusCode());
        Map stats = (Map) statsResp.getBody().get("data");
        Assertions.assertNotNull(stats.get("todayReviewPending"));
        Assertions.assertNotNull(stats.get("todayNewPending"));
    }
}

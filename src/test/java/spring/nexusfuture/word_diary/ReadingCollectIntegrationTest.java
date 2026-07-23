package spring.nexusfuture.word_diary;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import spring.nexusfuture.word_diary.entity.WdBook;
import spring.nexusfuture.word_diary.entity.WdUser;
import spring.nexusfuture.word_diary.mapper.WdBookMapper;
import spring.nexusfuture.word_diary.mapper.WdUserMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ReadingCollectIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WdBookMapper bookMapper;

    @Autowired
    private WdUserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private record AuthContext(Long userId, String token, HttpHeaders headers) {}

    private AuthContext createUserAndLogin(String prefix) {
        String suffix = String.valueOf(System.currentTimeMillis());
        String username = prefix + "_" + suffix;
        String phone = "+1" + suffix.substring(Math.max(0, suffix.length() - 10));

        WdUser user = WdUser.builder()
                .username(username)
                .nickname(username)
                .password(passwordEncoder.encode("pass123"))
                .phone(phone)
                .build();
        userMapper.insert(user);
        Assertions.assertNotNull(user.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(
                baseUrl() + "/api/user/login",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"pass123\"}", headers),
                Map.class);
        Assertions.assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        Map loginData = (Map) loginResp.getBody().get("data");
        Assertions.assertNotNull(loginData);
        String token = (String) loginData.get("token");
        Long userId = ((Number) loginData.get("id")).longValue();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(token);
        return new AuthContext(userId, token, authHeaders);
    }

    private Long createNotebook(HttpHeaders headers, String name) {
        return createNotebook(headers, name, null);
    }

    private Long createNotebook(HttpHeaders headers, String name, Long readingBookId) {
        String body = readingBookId == null
                ? "{\"bookName\":\"" + name + "\",\"bookDesc\":\"from reading\"}"
                : "{\"bookName\":\"" + name + "\",\"bookDesc\":\"from reading\",\"bookId\":" + readingBookId + "}";
        ResponseEntity<Map> notebookResp = restTemplate.exchange(
                baseUrl() + "/api/notebooks",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
        Assertions.assertEquals(HttpStatus.OK, notebookResp.getStatusCode());
        return ((Number) ((Map) notebookResp.getBody().get("data")).get("id")).longValue();
    }

    private Long insertBook(Long userId, String title) {
        WdBook book = WdBook.builder()
                .userId(userId)
                .title(title)
                .author("tester")
                .coverUrl("")
                .filePath("/tmp/" + title.replace(' ', '-') + ".epub")
                .fileType("epub")
                .fileSize(100L)
                .totalChapters(1)
                .currentChapter(0)
                .currentCfi("")
                .progressPercent(BigDecimal.ZERO)
                .lastReadAt(LocalDateTime.now())
                .createBy(userId)
                .createTime(LocalDateTime.now())
                .updateBy(userId)
                .updateTime(LocalDateTime.now())
                .isDeleted(0)
                .build();
        bookMapper.insert(book);
        Assertions.assertNotNull(book.getId());
        return book.getId();
    }

    @Test
    public void linkNotebook_collectWords_fillGapsNoOp() {
        AuthContext auth = createUserAndLogin("collect");
        Long bookId = insertBook(auth.userId(), "Collect Book " + System.currentTimeMillis());
        Long notebookId = createNotebook(auth.headers(), "Collect NB", bookId);

        ResponseEntity<Map> listLinksResp = restTemplate.exchange(
                baseUrl() + "/api/books/" + bookId + "/notebook-links",
                HttpMethod.GET,
                new HttpEntity<>(auth.headers()),
                Map.class);
        Assertions.assertEquals(HttpStatus.OK, listLinksResp.getStatusCode());
        List links = (List) listLinksResp.getBody().get("data");
        Assertions.assertEquals(1, links.size());

        String collectJson = """
                {
                  "notebookId": %d,
                  "sourceSnippet": "A clear strategy helps the team set up goals.",
                  "sourceLocation": "epubcfi(/6/4)",
                  "items": [
                    {
                      "entryType": "word",
                      "wordProto": "strategy",
                      "partSpeechMean": "n. 策略",
                      "usPhonetic": "/ˈstrætədʒi/",
                      "ukPhonetic": "/ˈstrætədʒi/",
                      "sentenceExample": "A clear strategy helps.",
                      "rootAffix": "strat-"
                    },
                    {
                      "entryType": "phrase",
                      "wordProto": "set up",
                      "partSpeechMean": "v. 建立",
                      "sentenceExample": "set up goals"
                    }
                  ]
                }
                """.formatted(notebookId);
        ResponseEntity<Map> collectResp = restTemplate.exchange(
                baseUrl() + "/api/books/" + bookId + "/words/collect",
                HttpMethod.POST,
                new HttpEntity<>(collectJson, auth.headers()),
                Map.class);
        Assertions.assertEquals(HttpStatus.OK, collectResp.getStatusCode());
        Map collectData = (Map) collectResp.getBody().get("data");
        Assertions.assertEquals(2, ((Number) collectData.get("added")).intValue());
        Assertions.assertEquals(0, ((Number) collectData.get("skipped")).intValue());

        ResponseEntity<Map> wordsResp = restTemplate.exchange(
                baseUrl() + "/api/notebooks/" + notebookId + "/words",
                HttpMethod.GET,
                new HttpEntity<>(auth.headers()),
                Map.class);
        Assertions.assertEquals(HttpStatus.OK, wordsResp.getStatusCode());
        List words = (List) wordsResp.getBody().get("data");
        Assertions.assertEquals(2, words.size());

        String collectAgainJson = """
                {
                  "notebookId": %d,
                  "sourceSnippet": "strategy again",
                  "items": [
                    {
                      "entryType": "word",
                      "wordProto": "strategy",
                      "partSpeechMean": "n. 策略",
                      "appendExample": true,
                      "sentenceExample": "New strategy example."
                    },
                    {
                      "entryType": "phrase",
                      "wordProto": "set up",
                      "partSpeechMean": "v. 建立"
                    }
                  ]
                }
                """.formatted(notebookId);
        ResponseEntity<Map> collectAgainResp = restTemplate.exchange(
                baseUrl() + "/api/books/" + bookId + "/words/collect",
                HttpMethod.POST,
                new HttpEntity<>(collectAgainJson, auth.headers()),
                Map.class);
        Assertions.assertEquals(HttpStatus.OK, collectAgainResp.getStatusCode());
        Map againData = (Map) collectAgainResp.getBody().get("data");
        Assertions.assertEquals(0, ((Number) againData.get("added")).intValue());
        Assertions.assertEquals(0, ((Number) againData.get("updated")).intValue());
        Assertions.assertEquals(2, ((Number) againData.get("skipped")).intValue());

        List wordsAfter = (List) restTemplate.exchange(
                baseUrl() + "/api/notebooks/" + notebookId + "/words",
                HttpMethod.GET,
                new HttpEntity<>(auth.headers()),
                Map.class).getBody().get("data");
        Long strategyId = null;
        for (Object item : wordsAfter) {
            Map w = (Map) item;
            if ("strategy".equalsIgnoreCase(String.valueOf(w.get("wordProto")))) {
                strategyId = ((Number) w.get("id")).longValue();
                break;
            }
        }
        Assertions.assertNotNull(strategyId);
        ResponseEntity<Map> fillResp = restTemplate.exchange(
                baseUrl() + "/api/notebooks/" + notebookId + "/words/ai-fill-gaps",
                HttpMethod.POST,
                new HttpEntity<>("{\"wordIds\":[" + strategyId + "]}", auth.headers()),
                Map.class);
        Assertions.assertEquals(HttpStatus.OK, fillResp.getStatusCode());
        Map fillData = (Map) fillResp.getBody().get("data");
        Assertions.assertEquals(0, ((Number) fillData.get("updated")).intValue());
    }

    @Test
    public void collect_withoutBookOwnership_shouldFail() {
        AuthContext auth = createUserAndLogin("nolink");
        Long notebookId = createNotebook(auth.headers(), "NoLink NB");
        Long bookId = insertBook(auth.userId(), "NoLink Book " + System.currentTimeMillis());

        String collectJson = """
                {
                  "notebookId": %d,
                  "items": [
                    {"entryType":"word","wordProto":"hello","partSpeechMean":"int. 你好"}
                  ]
                }
                """.formatted(notebookId);
        ResponseEntity<Map> collectResp = restTemplate.exchange(
                baseUrl() + "/api/books/" + bookId + "/words/collect",
                HttpMethod.POST,
                new HttpEntity<>(collectJson, auth.headers()),
                Map.class);
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, collectResp.getStatusCode());
        Map body = collectResp.getBody();
        Assertions.assertNotNull(body);
        Assertions.assertTrue(String.valueOf(body.get("message")).contains("单词本"));
    }

    @Test
    public void createNotebookForBook_listsUnderThatBookOnly() {
        AuthContext auth = createUserAndLogin("own");
        Long bookA = insertBook(auth.userId(), "Book A " + System.currentTimeMillis());
        Long bookB = insertBook(auth.userId(), "Book B " + System.currentTimeMillis());
        Long notebookId = createNotebook(auth.headers(), "Owned by A", bookA);

        ResponseEntity<Map> listA = restTemplate.exchange(
                baseUrl() + "/api/books/" + bookA + "/notebook-links",
                HttpMethod.GET,
                new HttpEntity<>(auth.headers()),
                Map.class);
        Assertions.assertEquals(1, ((List) listA.getBody().get("data")).size());

        ResponseEntity<Map> listB = restTemplate.exchange(
                baseUrl() + "/api/books/" + bookB + "/notebook-links",
                HttpMethod.GET,
                new HttpEntity<>(auth.headers()),
                Map.class);
        Assertions.assertTrue(((List) listB.getBody().get("data")).isEmpty());

        // 独立创建的单词本不应出现在本书列表
        createNotebook(auth.headers(), "Standalone");
        ResponseEntity<Map> listA2 = restTemplate.exchange(
                baseUrl() + "/api/books/" + bookA + "/notebook-links",
                HttpMethod.GET,
                new HttpEntity<>(auth.headers()),
                Map.class);
        Assertions.assertEquals(1, ((List) listA2.getBody().get("data")).size());
        Assertions.assertEquals(notebookId.intValue(),
                ((Number) ((Map) ((List) listA2.getBody().get("data")).get(0)).get("notebookId")).intValue());
    }
}

package fa.training.restapi.sercurity;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BlackListService {

    private final Map<String, LocalDateTime> blackList = new ConcurrentHashMap<>();

    public void revokeToken(String token, LocalDateTime expirationTime) {
        blackList.put(token, expirationTime);
    }

    public boolean isTokenBlacklisted(String token) {
        return blackList.containsKey(token);
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanUpBlacklist() {
        LocalDateTime now = LocalDateTime.now();
        blackList.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
package com.mmrtr.lol.tools.riotmock.web;

import com.mmrtr.lol.tools.riotmock.ratelimit.LimitSpec;
import com.mmrtr.lol.tools.riotmock.ratelimit.RateLimitEngine;
import com.mmrtr.lol.tools.riotmock.ratelimit.SlidingPeakTracker;
import com.mmrtr.lol.tools.riotmock.world.LabWorld;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 실험 중에 한도와 epoch 를 바꾸는 조작 API. */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final RateLimitEngine engine;
    private final SlidingPeakTracker tracker;
    private final LabWorld world;

    public AdminController(RateLimitEngine engine, SlidingPeakTracker tracker, LabWorld world) {
        this.engine = engine;
        this.tracker = tracker;
        this.world = world;
    }

    @GetMapping("/limits")
    public LimitsBody limits() {
        return new LimitsBody(engine.appLimit(), new TreeMap<>(engine.methodLimits()), engine.defaultMethodLimit());
    }

    /** 일부만 보내도 된다. 하나라도 틀리면 400 이고 아무것도 바꾸지 않는다. */
    @PutMapping("/limits")
    public synchronized LimitsBody updateLimits(@RequestBody LimitsBody body) {
        if (body.app() != null) {
            LimitSpec.parseAll(body.app());
        }
        if (body.defaultMethod() != null) {
            LimitSpec.parseAll(body.defaultMethod());
        }
        if (body.methods() != null) {
            body.methods().forEach((key, limit) -> {
                if (!RiotEndpoint.isKnownKey(key)) {
                    throw new IllegalArgumentException("알 수 없는 엔드포인트 키: " + key);
                }
                LimitSpec.parseAll(limit);
            });
        }

        if (body.app() != null) {
            engine.setAppLimit(body.app());
            List<Integer> windows = LimitSpec.parseAll(body.app()).stream().map(LimitSpec::windowSeconds).toList();
            tracker.trackWindows(windows);
        }
        if (body.defaultMethod() != null) {
            engine.setDefaultMethodLimit(body.defaultMethod());
        }
        if (body.methods() != null) {
            body.methods().forEach(engine::setMethodLimit);
        }
        return limits();
    }

    @PostMapping("/reset")
    public synchronized Map<String, Integer> reset(@RequestBody(required = false) ResetBody body) {
        int next = body == null || body.epoch() == null ? world.epoch() + 1 : body.epoch();
        world.setEpoch(next);
        engine.reset();
        tracker.reset();
        return Map.of("epoch", next);
    }

    @GetMapping("/epoch")
    public Map<String, Integer> epoch() {
        return Map.of("epoch", world.epoch());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", String.valueOf(e.getMessage())));
    }

    public record LimitsBody(String app, Map<String, String> methods, String defaultMethod) {
    }

    public record ResetBody(Integer epoch) {
    }
}

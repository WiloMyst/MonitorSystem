package org.example.monitorsystem.modules.ai.service;

import reactor.core.publisher.Flux;

public interface IRagService {
    Flux<String> smartChat(String question);
}
